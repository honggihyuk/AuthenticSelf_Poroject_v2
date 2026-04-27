package com.authenticself.ai;

import com.authenticself.ai.dto.RecommendationRequest;
import com.authenticself.ai.dto.RecommendationResponse;
import com.authenticself.domain.Furniture;
import com.authenticself.domain.Space;
import com.authenticself.repository.FurnitureRepository;
import com.authenticself.repository.SpaceRepository;
import com.authenticself.space.PreferredStyle;
import com.authenticself.space.SpaceErrorCode;
import com.authenticself.space.SpaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates a recommendation request for a single roomId
 * (UC-01-recommendation FR-13 / FR-18).
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Load the {@code spaces} row; 404 / 403 / 409 guard rails.</li>
 *   <li>Check the in-memory cache keyed on {@code (roomId, preferredStyle)}
 *       (FR-14). HIT returns the cached payload verbatim (including
 *       {@code generatedAt}).</li>
 *   <li>MISS: load the full {@code furniture} catalog, build the Python
 *       request (detectedObjects is always {@code []} per FR-18 step 8),
 *       invoke {@link RecommendationClient} on the {@code aiExecutor}
 *       thread pool via {@link CompletableFuture#supplyAsync}.</li>
 *   <li>Cache the response on success, re-throw on failure.</li>
 * </ol>
 *
 * <h3>Cache invalidation</h3>
 * A {@code PUT /api/v1/spaces/{roomId}/preferred-style} MUST call
 * {@link #invalidate(String)} so stale recommendations keyed on the old
 * preferredStyle are evicted (FR-20 / AC-30).
 */
@Service
public class RecommendationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RecommendationOrchestrator.class);

    /** Pattern matching {@code "3.6x4.2x2.4m"}  (Task 3 FR-12 dimension format). */
    private static final Pattern DIM_PATTERN = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)?)x(\\d+(?:\\.\\d+)?)x(\\d+(?:\\.\\d+)?)(?:m)?\\s*$");

    private final SpaceRepository       spaces;
    private final FurnitureRepository   furniture;
    private final RecommendationClient  client;
    private final Executor              aiExecutor;

    private final long cacheTtlMs;
    private final int  maxEntries;

    /**
     * In-memory (roomId, preferredStyle) -> CachedEntry cache (FR-14).
     * {@link ConcurrentHashMap} is sufficient for per-JVM needs;
     * horizontal scaling with Redis is documented as a future task.
     */
    private final Map<CacheKey, CachedEntry> cache = new ConcurrentHashMap<>();

    public RecommendationOrchestrator(
            SpaceRepository spaces,
            FurnitureRepository furniture,
            RecommendationClient client,
            @Qualifier("aiExecutor") Executor aiExecutor,
            @Value("${app.recommendation.cache.ttl-seconds:600}")   long cacheTtlSeconds,
            @Value("${app.recommendation.cache.max-entries:1024}") int maxEntries
    ) {
        this.spaces = spaces;
        this.furniture = furniture;
        this.client = client;
        this.aiExecutor = aiExecutor;
        this.cacheTtlMs = cacheTtlSeconds * 1000L;
        this.maxEntries = maxEntries;
    }

    /**
     * Public entry point invoked by {@link com.authenticself.space.SpaceController}.
     * <p>
     * Throws {@link SpaceException} for 4xx user-facing errors (404/403/409),
     * and {@link AIException} for downstream (Python 4xx/5xx + transport).
     */
    public OrchestratorResult recommend(String roomId, String userId, int topNPerCategory) {
        long t0 = System.nanoTime();

        // -----------------------------------------------------------------
        // Step 1–4 — load + authorize + status guards.
        // -----------------------------------------------------------------
        Space space = spaces.findById(roomId)
                .orElseThrow(() -> new SpaceException(SpaceErrorCode.SPACE_NOT_FOUND));

        if (!Objects.equals(userId, space.getUserId())) {
            throw new SpaceException(SpaceErrorCode.SPACE_ACCESS_DENIED);
        }
        if (space.getStatus() != Space.Status.ANALYZED) {
            throw new SpaceException(SpaceErrorCode.ANALYSIS_NOT_READY);
        }
        PreferredStyle preferredStyle = space.getPreferredStyle();
        if (preferredStyle == null) {
            throw new SpaceException(SpaceErrorCode.PREFERRED_STYLE_NOT_SET);
        }

        // -----------------------------------------------------------------
        // Step 5 — cache check (FR-14 / AC-29).
        // -----------------------------------------------------------------
        CacheKey key = new CacheKey(roomId, preferredStyle.name());
        CachedEntry cached = getFromCache(key);
        if (cached != null) {
            long totalMs = (System.nanoTime() - t0) / 1_000_000L;
            log.info(
                    "recommend cache-hit roomId={} userId={} preferredStyle={} totalMs={}",
                    roomId, userId, preferredStyle, totalMs);
            return new OrchestratorResult(cached.response(), preferredStyle.name(), true);
        }

        // -----------------------------------------------------------------
        // Step 6 — load the full catalog.
        // -----------------------------------------------------------------
        List<Furniture> catalog = furniture.findAll();

        // -----------------------------------------------------------------
        // Step 7 — parse dimensions.
        // -----------------------------------------------------------------
        double[] dim = parseDimensions(space.getDimensions());
        if (dim == null) {
            log.error("recommend dimensions malformed roomId={} raw={}", roomId, space.getDimensions());
            throw new AIException(AIErrorCode.RECOMMENDATION_FAILED,
                    "Space dimensions malformed: " + space.getDimensions());
        }

        // -----------------------------------------------------------------
        // Step 8 — detected objects always [] (FR-18 step 8 / AC-38).
        // Step 9 — build request + call Python on the aiExecutor pool.
        // -----------------------------------------------------------------
        RecommendationRequest req = buildRequest(
                roomId,
                userId,
                preferredStyle.name(),
                space.getStyle(),            // AI-detected (nullable)
                space.getMainColor(),
                dim,
                catalog,
                topNPerCategory
        );

        long pythonStart = System.nanoTime();
        RecommendationResponse response;
        try {
            response = CompletableFuture
                    .supplyAsync(() -> client.callRecommend(req), aiExecutor)
                    .join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof AIException ae) {
                throw ae;
            }
            throw new AIException(AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Recommendation call failed: " + cause.getClass().getSimpleName(),
                    cause);
        }

        long pythonMs = (System.nanoTime() - pythonStart) / 1_000_000L;
        long totalMs = (System.nanoTime() - t0) / 1_000_000L;

        // -----------------------------------------------------------------
        // Step 10 — cache + return.
        // -----------------------------------------------------------------
        putToCache(key, response);
        log.info(
                "recommend ok roomId={} userId={} preferredStyle={} catalogSize={} pythonMs={} totalMs={} cacheHit=false",
                roomId, userId, preferredStyle, catalog.size(), pythonMs, totalMs);

        return new OrchestratorResult(response, preferredStyle.name(), false);
    }

    /**
     * Invalidate every cache entry associated with {@code roomId}
     * (FR-20 / AC-30). Called by {@link com.authenticself.space.SpaceController}
     * after a successful preferred-style PUT.
     */
    public void invalidate(String roomId) {
        if (roomId == null) return;
        int before = cache.size();
        cache.keySet().removeIf(k -> roomId.equals(k.roomId()));
        int after = cache.size();
        if (before != after) {
            log.info("recommend cache invalidate roomId={} evicted={}",
                    roomId, before - after);
        }
    }

    // =====================================================================
    // helpers (package-private for tests)
    // =====================================================================

    /** Convert {@code "3.6x4.2x2.4m"} to {@code [3.6, 4.2, 2.4]}; null on fail. */
    static double[] parseDimensions(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = DIM_PATTERN.matcher(raw);
        if (!m.matches()) return null;
        try {
            double w = Double.parseDouble(m.group(1));
            double l = Double.parseDouble(m.group(2));
            double h = Double.parseDouble(m.group(3));
            if (w <= 0 || l <= 0 || h <= 0) return null;
            return new double[] { w, l, h };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Build the Python request body from the loaded row + catalog. */
    static RecommendationRequest buildRequest(
            String roomId,
            String userId,
            String preferredStyle,
            String detectedStyle,
            String mainColor,
            double[] dim,
            List<Furniture> catalog,
            int topNPerCategory
    ) {
        RecommendationRequest.Dimensions dims = new RecommendationRequest.Dimensions(
                dim[0], dim[1], dim[2],
                Math.round(dim[0] * dim[1] * 100.0) / 100.0);

        RecommendationRequest.Space space = new RecommendationRequest.Space(
                dims,
                mainColor,
                detectedStyle,                        // may be null (FR-4)
                Collections.emptyList()               // FR-18 step 8 — always [] for now
        );

        List<RecommendationRequest.CatalogItem> items = new ArrayList<>(catalog.size());
        for (Furniture f : catalog) {
            items.add(new RecommendationRequest.CatalogItem(
                    f.getFurnitureId(),
                    f.getType(),
                    f.getName(),
                    f.getStyleTagsList(),
                    f.getWidthCm(),
                    f.getLengthCm(),
                    f.getHeightCm(),
                    f.getColorHex(),
                    f.getPrice(),
                    f.getImageUrl()
            ));
        }

        return new RecommendationRequest(
                roomId, userId, space, preferredStyle, items, topNPerCategory);
    }

    // --- cache internals -------------------------------------------------

    private CachedEntry getFromCache(CacheKey key) {
        CachedEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiresAt()) {
            cache.remove(key, entry);
            return null;
        }
        return entry;
    }

    private void putToCache(CacheKey key, RecommendationResponse response) {
        // Crude bound — evict the first entry when the cap is hit. This is
        // fine for a per-JVM dev cache; production would want a proper LRU.
        if (cache.size() >= maxEntries) {
            var it = cache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        cache.put(key, new CachedEntry(response, System.currentTimeMillis() + cacheTtlMs));
    }

    /** Test-only — drop every cached entry. */
    public void clearCacheForTest() {
        cache.clear();
    }

    // --- public result wrapper ------------------------------------------

    /** Wrapper so the controller can stitch {@code cacheHit} into the response. */
    public record OrchestratorResult(
            RecommendationResponse response,
            String preferredStyle,
            boolean cacheHit
    ) { }

    // --- private cache types ---------------------------------------------

    private record CacheKey(String roomId, String preferredStyle) { }

    private record CachedEntry(RecommendationResponse response, long expiresAt) { }
}
