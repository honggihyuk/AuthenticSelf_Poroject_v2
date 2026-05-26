package com.authenticself.ai;

import com.authenticself.ai.dto.SpaceAnalysisResponse;
import com.authenticself.ai.dto.SpaceAnalysisResultDTO;
import com.authenticself.ai.dto.StyleAnalysisResponse;
import com.authenticself.domain.Space;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Orchestrates UC-01 space-and-style analysis for a single roomId
 * (Task-3 FR-11/FR-12 + Task-4 FR-11/FR-12/FR-17).
 *
 * <h3>Parallel fan-out (Task-4 FR-11 / AC-12 / AC-13)</h3>
 * Space and style downstream calls are launched on the {@code aiExecutor}
 * pool in parallel via {@link CompletableFuture#supplyAsync}, then joined
 * with {@link CompletableFuture#allOf}. The total orchestrator wall-clock
 * is {@code max(t_space, t_style) + overhead} rather than
 * {@code t_space + t_style} (AC-13).
 *
 * <h3>Partial-success matrix (Task-4 FR-12 / AC-14..AC-17)</h3>
 * <ul>
 *   <li><b>Both succeed</b> → {@code status=ANALYZED},
 *       write {@code dimensions}, {@code mainColor}, {@code style},
 *       {@code analysisDate=now()} (AC-14).</li>
 *   <li><b>Space OK, style any failure (transport or analyzer)</b> →
 *       {@code status=ANALYZED}, {@code style=NULL} — the user still
 *       picks a preferred style on the next screen. Log WARN (AC-15).</li>
 *   <li><b>Space analyzer failure (422)</b> → {@code status=FAILED};
 *       the style outcome is discarded (AC-16). Rethrow the mapped
 *       {@link AIException} so the controller returns 4xx.</li>
 *   <li><b>Space transport failure</b> → status STAYS
 *       {@code PENDING_ANALYSIS}; rethrow so the poller retries (AC-17).</li>
 * </ul>
 *
 * <h3>Transaction boundary is load-bearing.</h3>
 * The HTTP calls to Python run <em>outside</em> any transaction so the DB
 * connection is not held during the network round-trip. Persistence
 * happens in {@link SpaceAnalysisPersistence} — a SEPARATE bean whose
 * methods are all {@code @Transactional(REQUIRES_NEW)} so the Spring AOP
 * proxy actually opens / closes each transaction around a single short
 * DB operation.
 */
@Service
public class AIOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrator.class);

    private final SpaceAnalysisPersistence persistence;
    private final SpaceAnalysisClient      spaceClient;
    private final StyleAnalysisClient      styleClient;
    private final Executor                 aiExecutor;

    public AIOrchestrator(
            SpaceAnalysisPersistence persistence,
            SpaceAnalysisClient spaceClient,
            StyleAnalysisClient styleClient,
            @Qualifier("aiExecutor") Executor aiExecutor
    ) {
        this.persistence = persistence;
        this.spaceClient = spaceClient;
        this.styleClient = styleClient;
        this.aiExecutor  = aiExecutor;
    }

    /**
     * Public entry-point invoked by the internal controller and the poller.
     *
     * @throws SpaceNotFoundException if no row exists for {@code roomId}.
     * @throws AIException on space analyzer or transport failure (style-only
     *         failures are logged and swallowed per FR-12).
     */
    public SpaceAnalysisResultDTO analyze(String roomId) {
        long t0 = System.nanoTime();

        // ---- load the row (READ-ONLY tx via proxy) -----------------------
        SpaceAnalysisPersistence.Snapshot snap = persistence.loadForAnalysis(roomId);
        if (snap.status() != Space.Status.PENDING_ANALYSIS) {
            return SpaceAnalysisResultDTO.ofSkipped(roomId, snap.status().name());
        }

        log.info("ai.analyze start roomId={} photo={}", roomId, basename(snap.photoUrl()));

        // ---- PARALLEL FAN-OUT (Task-4 FR-11 / AC-12 / AC-13) -------------
        final long spaceStart = System.nanoTime();
        CompletableFuture<SpaceAnalysisResponse> spaceFuture = CompletableFuture.supplyAsync(
                () -> spaceClient.callSpaceAnalysis(roomId, snap.photoUrl()),
                aiExecutor);

        final long styleStart = System.nanoTime();
        CompletableFuture<StyleAnalysisResponse> styleFuture = CompletableFuture.supplyAsync(
                () -> styleClient.callStyleAnalysis(roomId, snap.photoUrl()),
                aiExecutor);

        // allOf does NOT rethrow; it completes exceptionally when any child
        // fails. We handle each future's outcome individually below.
        try {
            CompletableFuture.allOf(spaceFuture, styleFuture).join();
        } catch (CompletionException ignored) {
            // Swallow — we inspect per-future outcomes next.
        } catch (RuntimeException ignored) {
            // Defensive — same policy.
        }

        // ---- outcome A: space future --------------------------------------
        SpaceAnalysisResponse spaceResponse;
        try {
            spaceResponse = spaceFuture.get();
        } catch (Exception ex) {
            AIException ae = unwrap(ex);
            if (ae.isTransport()) {
                // FR-12 "space failed (transport)" — row stays PENDING,
                // style outcome is discarded. Rethrow so the controller
                // maps to 502 and the poller retries next tick.
                log.error("ai.analyze space transport failure roomId={} code={}",
                        roomId, ae.code());
                throw ae;
            }
            // FR-12 "space failed (analyzer)" — flip to FAILED, discard style.
            persistence.markFailed(roomId);
            log.warn("ai.analyze space analyzer failure roomId={} code={}",
                    roomId, ae.code());
            throw ae;
        }

        // ---- outcome B: style future --------------------------------------
        String detectedStyle = null;
        Double detectedConfidence = null;
        long styleMs = (System.nanoTime() - styleStart) / 1_000_000L;
        if (styleFuture.isCompletedExceptionally()) {
            try {
                styleFuture.get();
            } catch (Exception ex) {
                AIException ae = unwrapOrNull(ex);
                String code = (ae != null) ? ae.code().name() : "UNKNOWN";
                // FR-12 "style-only failure is degraded success" — log WARN,
                // leave style NULL, keep space response.
                log.warn("ai.analyze style failed (degraded-success) roomId={} code={}",
                        roomId, code);
            }
        } else {
            try {
                StyleAnalysisResponse sr = styleFuture.get();
                if (sr != null && sr.style() != null) {
                    detectedStyle = sr.style();
                    detectedConfidence = sr.confidence();
                }
            } catch (Exception ex) {
                // Shouldn't reach here given isCompletedExceptionally() is false,
                // but be defensive.
                log.warn("ai.analyze style read unexpectedly failed roomId={} exc={}",
                        roomId, ex.toString());
            }
        }

        // ---- persist happy-path result (WRITE tx via proxy) --------------
        // UC-ML-PERSIST FR-7 — serialize the YOLO detections envelope so the
        // recommender (FR-9) + admin overlay (FR-11) can consume them. Null
        // when the response carries no image dims; an empty detection list
        // with valid dims still round-trips.
        String dimensionString = formatDimensions(spaceResponse);
        String aiDetectionsJson = AiDetectionsCodec.toEnvelopeJson(spaceResponse);
        persistence.markAnalyzed(
                roomId,
                dimensionString,
                spaceResponse.mainColor(),
                detectedStyle,
                aiDetectionsJson);

        // Cache the style confidence for the subsequent GET /api/v1/spaces
        // round-trip (FR-14 allows a short-lived in-memory cache).
        if (detectedStyle != null && detectedConfidence != null) {
            StyleConfidenceCache.put(roomId, detectedConfidence);
        }

        long spaceMs = (System.nanoTime() - spaceStart) / 1_000_000L;
        long totalMs = (System.nanoTime() - t0) / 1_000_000L;
        int processingMs = spaceResponse.processingMs() != null
                ? spaceResponse.processingMs()
                : (int) totalMs;

        log.info(
                "ai.analyze ok roomId={} spaceMs={} styleMs={} totalMs={} dimensions={} mainColor={} style={} confidence={}",
                roomId, spaceMs, styleMs, totalMs,
                dimensionString, spaceResponse.mainColor(),
                detectedStyle, spaceResponse.confidence());

        return SpaceAnalysisResultDTO.ofAnalyzed(
                roomId, dimensionString, spaceResponse.mainColor(), processingMs);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    /**
     * Unwrap a {@link CompletionException} to its {@link AIException} cause
     * (FR-12). Non-AIException causes are wrapped into a transport-level
     * AIException so the caller can always branch on {@code isTransport()}.
     */
    private static AIException unwrap(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException ce && ce.getCause() != null) {
            cause = ce.getCause();
        }
        if (cause instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null) {
            cause = ee.getCause();
        }
        if (cause instanceof AIException ae) return ae;
        return new AIException(AIErrorCode.AI_SERVICE_UNAVAILABLE,
                "AI call failed: " + cause.getClass().getSimpleName(), cause);
    }

    /** Same as {@link #unwrap(Throwable)} but returns null when the cause is not an {@link AIException}. */
    private static AIException unwrapOrNull(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException ce && ce.getCause() != null) {
            cause = ce.getCause();
        }
        if (cause instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null) {
            cause = ee.getCause();
        }
        return (cause instanceof AIException ae) ? ae : null;
    }

    private static String formatDimensions(SpaceAnalysisResponse r) {
        SpaceAnalysisResponse.Dimensions d = r.dimensions();
        return stripZeros(d.widthM()) + "x" + stripZeros(d.lengthM()) + "x" + stripZeros(d.heightM()) + "m";
    }

    private static String stripZeros(Double v) {
        if (v == null) return "0";
        double rounded = Math.round(v * 10.0) / 10.0;
        if (rounded == Math.floor(rounded)) {
            return Integer.toString((int) rounded);
        }
        return Double.toString(rounded);
    }

    private static String basename(String photoUrl) {
        if (photoUrl == null) return "<null>";
        int slash = Math.max(photoUrl.lastIndexOf('/'), photoUrl.lastIndexOf('\\'));
        return slash >= 0 ? photoUrl.substring(slash + 1) : photoUrl;
    }
}
