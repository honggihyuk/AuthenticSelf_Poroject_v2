package com.authenticself.ai;

import com.authenticself.ai.dto.SpaceAnalysisResponse;
import com.authenticself.ai.dto.SpaceAnalysisResultDTO;
import com.authenticself.ai.dto.StyleAnalysisResponse;
import com.authenticself.domain.Space;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AIOrchestrator} covering Task-3's status-machine
 * behaviour + Task-4's parallel-call partial-success matrix
 * (AC-14, AC-15, AC-16, AC-17 from Task 4; AC-16..AC-20 from Task 3).
 * <p>
 * Uses a real two-thread executor so the parallel fan-out is genuinely
 * concurrent; mocks {@link SpaceAnalysisPersistence}, {@link SpaceAnalysisClient}
 * and {@link StyleAnalysisClient} so the test hits the orchestrator logic
 * directly — no Spring context, no database, no HTTP.
 */
class AIOrchestratorTest {

    private SpaceAnalysisPersistence persistence;
    private SpaceAnalysisClient      spaceClient;
    private StyleAnalysisClient      styleClient;
    private Executor                 executor;
    private AIOrchestrator           orchestrator;

    @BeforeEach
    void init() {
        persistence  = mock(SpaceAnalysisPersistence.class);
        spaceClient  = mock(SpaceAnalysisClient.class);
        styleClient  = mock(StyleAnalysisClient.class);
        executor     = Executors.newFixedThreadPool(2);
        orchestrator = new AIOrchestrator(persistence, spaceClient, styleClient, executor);
        StyleConfidenceCache.clear();
    }

    // -----------------------------------------------------------------
    // Task-4 AC-14 (was Task-3 AC-16) — both succeed
    // -----------------------------------------------------------------
    @Test
    @DisplayName("Task-4 AC-14: both succeed → ANALYZED with style written")
    void bothSucceed() {
        when(persistence.loadForAnalysis("r1"))
                .thenReturn(new SpaceAnalysisPersistence.Snapshot(
                        Space.Status.PENDING_ANALYSIS, "file:///tmp/r1.jpg"));
        when(spaceClient.callSpaceAnalysis(eq("r1"), anyString()))
                .thenReturn(spaceBody("r1", 3.6, 4.2, 2.4, "#E8D9B0", 0.78, 142));
        when(styleClient.callStyleAnalysis(eq("r1"), anyString()))
                .thenReturn(new StyleAnalysisResponse(
                        "r1", "OK", "MODERN", 0.72,
                        Map.of("MODERN", 0.72, "SIMPLE", 0.12, "CLASSIC", 0.06,
                               "SCANDINAVIAN", 0.07, "INDUSTRIAL", 0.03),
                        54));

        SpaceAnalysisResultDTO result = orchestrator.analyze("r1");

        assertThat(result.status()).isEqualTo("ANALYZED");
        assertThat(result.skipped()).isNull();
        assertThat(result.dimensions()).matches("^\\d+(\\.\\d+)?x\\d+(\\.\\d+)?x\\d+(\\.\\d+)?m$");
        assertThat(result.mainColor()).isEqualTo("#E8D9B0");

        verify(persistence, times(1)).markAnalyzed(
                eq("r1"),
                eq(result.dimensions()),
                eq("#E8D9B0"),
                eq("MODERN"));
        verify(persistence, never()).markFailed(anyString());
        assertThat(StyleConfidenceCache.get("r1")).isEqualTo(0.72);
    }

    // -----------------------------------------------------------------
    // Task-4 AC-15 — style-only failure is degraded success
    // -----------------------------------------------------------------
    @Test
    @DisplayName("Task-4 AC-15: style analyzer-failure → ANALYZED with style NULL")
    void styleOnlyAnalyzerFailureIsDegradedSuccess() {
        when(persistence.loadForAnalysis("r1"))
                .thenReturn(new SpaceAnalysisPersistence.Snapshot(
                        Space.Status.PENDING_ANALYSIS, "file:///tmp/r1.jpg"));
        when(spaceClient.callSpaceAnalysis(eq("r1"), anyString()))
                .thenReturn(spaceBody("r1", 3.6, 4.2, 2.4, "#E8D9B0", 0.78, 142));
        when(styleClient.callStyleAnalysis(eq("r1"), anyString()))
                .thenThrow(new AIException(AIErrorCode.ANALYSIS_IMAGE_READ_FAILED, "corrupt"));

        SpaceAnalysisResultDTO result = orchestrator.analyze("r1");

        assertThat(result.status()).isEqualTo("ANALYZED");
        verify(persistence, times(1)).markAnalyzed(
                eq("r1"), eq(result.dimensions()), eq("#E8D9B0"), isNull());
        verify(persistence, never()).markFailed(anyString());
        assertThat(StyleConfidenceCache.get("r1")).isNull();
    }

    @Test
    @DisplayName("Task-4 AC-15: style transport failure also produces degraded success")
    void styleTransportFailureIsDegradedSuccess() {
        when(persistence.loadForAnalysis("r1"))
                .thenReturn(new SpaceAnalysisPersistence.Snapshot(
                        Space.Status.PENDING_ANALYSIS, "file:///tmp/r1.jpg"));
        when(spaceClient.callSpaceAnalysis(eq("r1"), anyString()))
                .thenReturn(spaceBody("r1", 3.6, 4.2, 2.4, "#E8D9B0", 0.78, 142));
        when(styleClient.callStyleAnalysis(eq("r1"), anyString()))
                .thenThrow(new AIException(AIErrorCode.AI_SERVICE_UNAVAILABLE, "connection refused"));

        SpaceAnalysisResultDTO result = orchestrator.analyze("r1");

        assertThat(result.status()).isEqualTo("ANALYZED");
        verify(persistence, times(1)).markAnalyzed(
                eq("r1"), eq(result.dimensions()), eq("#E8D9B0"), isNull());
        verify(persistence, never()).markFailed(anyString());
    }

    // -----------------------------------------------------------------
    // Task-4 AC-16 — space analyzer failure dominates, style discarded
    // -----------------------------------------------------------------
    @Test
    @DisplayName("Task-4 AC-16: space analyzer-failure → FAILED; style success discarded")
    void spaceAnalyzerFailureFlipsToFailed() {
        when(persistence.loadForAnalysis("r1"))
                .thenReturn(new SpaceAnalysisPersistence.Snapshot(
                        Space.Status.PENDING_ANALYSIS, "file:///tmp/r1.jpg"));
        when(spaceClient.callSpaceAnalysis(eq("r1"), anyString()))
                .thenThrow(new AIException(AIErrorCode.ANALYSIS_IMAGE_READ_FAILED, "cv2 failed"));
        when(styleClient.callStyleAnalysis(eq("r1"), anyString()))
                .thenReturn(new StyleAnalysisResponse(
                        "r1", "OK", "MODERN", 0.72, Map.of("MODERN", 0.72), 54));

        assertThatThrownBy(() -> orchestrator.analyze("r1"))
                .isInstanceOf(AIException.class)
                .extracting("code")
                .isEqualTo(AIErrorCode.ANALYSIS_IMAGE_READ_FAILED);

        verify(persistence, times(1)).markFailed(eq("r1"));
        verify(persistence, never()).markAnalyzed(anyString(), anyString(), anyString(), anyString());
    }

    // -----------------------------------------------------------------
    // Task-4 AC-17 — space transport failure → PENDING unchanged
    // -----------------------------------------------------------------
    @Test
    @DisplayName("Task-4 AC-17: space transport failure keeps row PENDING; no DB write")
    void spaceTransportFailureLeavesPending() {
        when(persistence.loadForAnalysis("r1"))
                .thenReturn(new SpaceAnalysisPersistence.Snapshot(
                        Space.Status.PENDING_ANALYSIS, "file:///tmp/r1.jpg"));
        when(spaceClient.callSpaceAnalysis(eq("r1"), anyString()))
                .thenThrow(new AIException(AIErrorCode.AI_SERVICE_UNAVAILABLE, "connection refused"));
        when(styleClient.callStyleAnalysis(eq("r1"), anyString()))
                .thenReturn(new StyleAnalysisResponse(
                        "r1", "OK", "SIMPLE", 0.60, Map.of("SIMPLE", 0.60), 40));

        assertThatThrownBy(() -> orchestrator.analyze("r1"))
                .isInstanceOf(AIException.class)
                .extracting("code")
                .isEqualTo(AIErrorCode.AI_SERVICE_UNAVAILABLE);

        verify(persistence, never()).markAnalyzed(anyString(), anyString(), anyString(), anyString());
        verify(persistence, never()).markFailed(anyString());
    }

    // -----------------------------------------------------------------
    // Task-4 AC-13 — parallel wall-clock
    // -----------------------------------------------------------------
    @Test
    @DisplayName("Task-4 AC-13: parallel fan-out — total latency ≈ max, not sum")
    void parallelWallClock() throws InterruptedException {
        when(persistence.loadForAnalysis("r1"))
                .thenReturn(new SpaceAnalysisPersistence.Snapshot(
                        Space.Status.PENDING_ANALYSIS, "file:///tmp/r1.jpg"));

        // Each client sleeps 500 ms before returning — sequential would be ~1000 ms.
        when(spaceClient.callSpaceAnalysis(eq("r1"), anyString()))
                .thenAnswer(inv -> {
                    Thread.sleep(500);
                    return spaceBody("r1", 3.0, 4.0, 2.4, "#AAAAAA", 0.7, 500);
                });
        when(styleClient.callStyleAnalysis(eq("r1"), anyString()))
                .thenAnswer(inv -> {
                    Thread.sleep(500);
                    return new StyleAnalysisResponse(
                            "r1", "OK", "MODERN", 0.70, Map.of("MODERN", 0.70), 500);
                });

        long t0 = System.nanoTime();
        orchestrator.analyze("r1");
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        // Parallel execution should comfortably fit under 900 ms; sequential
        // would be >= 1000 ms.
        assertThat(elapsedMs).isLessThan(900L);
    }

    // -----------------------------------------------------------------
    // Task-3 AC-17 — short-circuit on non-PENDING rows
    // -----------------------------------------------------------------
    @Test
    @DisplayName("Task-3 AC-17: non-PENDING row short-circuits; no HTTP calls")
    void shortCircuitOnAnalyzed() {
        when(persistence.loadForAnalysis("r1"))
                .thenReturn(new SpaceAnalysisPersistence.Snapshot(
                        Space.Status.ANALYZED, "file:///tmp/r1.jpg"));

        SpaceAnalysisResultDTO result = orchestrator.analyze("r1");

        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).isEqualTo("NOT_PENDING");
        assertThat(result.status()).isEqualTo("ANALYZED");

        verify(spaceClient, never()).callSpaceAnalysis(anyString(), anyString());
        verify(styleClient, never()).callStyleAnalysis(anyString(), anyString());
    }

    // -----------------------------------------------------------------
    // Task-3 AC-20 — unknown roomId
    // -----------------------------------------------------------------
    @Test
    @DisplayName("Task-3 AC-20: unknown roomId raises before any HTTP call")
    void unknownRoomIdThrows() {
        doThrow(new SpaceNotFoundException("nope"))
                .when(persistence).loadForAnalysis("nope");

        assertThatThrownBy(() -> orchestrator.analyze("nope"))
                .isInstanceOf(SpaceNotFoundException.class);

        verify(spaceClient, never()).callSpaceAnalysis(anyString(), anyString());
        verify(styleClient, never()).callStyleAnalysis(anyString(), anyString());
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static SpaceAnalysisResponse spaceBody(
            String roomId, double w, double l, double h,
            String color, double confidence, int processingMs) {
        double area = Math.round(w * l * 100.0) / 100.0;
        return new SpaceAnalysisResponse(
                roomId, "OK",
                new SpaceAnalysisResponse.Dimensions(w, l, h, area),
                color, confidence, processingMs);
    }
}
