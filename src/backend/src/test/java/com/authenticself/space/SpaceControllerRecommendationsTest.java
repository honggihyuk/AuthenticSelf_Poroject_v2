package com.authenticself.space;

import com.authenticself.ai.AIErrorCode;
import com.authenticself.ai.AIException;
import com.authenticself.ai.RecommendationOrchestrator;
import com.authenticself.ai.SpaceAnalysisPersistence;
import com.authenticself.ai.dto.RecommendationItem;
import com.authenticself.ai.dto.RecommendationResponse;
import com.authenticself.ai.dto.ScoreBreakdown;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link SpaceController}'s
 * {@code GET /api/v1/spaces/{roomId}/recommendations} endpoint and the
 * cache-invalidation hook on {@code PUT .../preferred-style}
 * (UC-01-recommendation AC-33..AC-37, AC-39 subset).
 *
 * <p>The orchestrator itself is mocked — full end-to-end happy path +
 * cache-hit behaviour is exercised by {@link RecommendationOrchestratorTest}.
 */
@WebMvcTest(SpaceController.class)
@Import(SpaceExceptionAdvice.class)
@TestPropertySource(properties = {
        "app.recommendation.default-top-n=3"
})
class SpaceControllerRecommendationsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SpaceAnalysisPersistence persistence;
    @MockBean RecommendationOrchestrator orchestrator;

    private RecommendationResponse happyResponse;

    @BeforeEach
    void init() {
        happyResponse = new RecommendationResponse(
                "r1",
                "OK",
                "MODERN",
                "2026-04-18T09:14:22Z",
                new RecommendationResponse.Recommendations(
                        List.of(deskItem("f_desk_001", 0.87)),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                null,
                18
        );
    }

    private RecommendationItem deskItem(String id, double fitScore) {
        return new RecommendationItem(
                id, "Mock Desk", "desk", 189000,
                "https://cdn.example.com/" + id + ".jpg",
                fitScore,
                new ScoreBreakdown(1.0, 1.0, 0.85, 1.0),
                "모던 스타일 일치"
        );
    }

    // -----------------------------------------------------------------
    // AC-28 — happy path, cacheHit=false, preferredStyle echoed
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-28: GET recommendations 200 with cacheHit=false + all four keys")
    void ac28_happyPath() throws Exception {
        when(orchestrator.recommend(eq("r1"), eq("u1"), anyInt()))
                .thenReturn(new RecommendationOrchestrator.OrchestratorResult(
                        happyResponse, "MODERN", false));

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId",                 is("r1")))
                .andExpect(jsonPath("$.status",                 is("OK")))
                .andExpect(jsonPath("$.resolvedStyle",          is("MODERN")))
                .andExpect(jsonPath("$.preferredStyle",         is("MODERN")))
                .andExpect(jsonPath("$.cacheHit",               is(false)))
                .andExpect(jsonPath("$.warning",                nullValue()))
                .andExpect(jsonPath("$.recommendations.desk",   notNullValue()))
                .andExpect(jsonPath("$.recommendations.bed",    notNullValue()))
                .andExpect(jsonPath("$.recommendations.chair",  notNullValue()))
                .andExpect(jsonPath("$.recommendations.lighting", notNullValue()));
    }

    // -----------------------------------------------------------------
    // AC-29 — cacheHit on the second call (simulated)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-29: cacheHit=true on second call")
    void ac29_cacheHit() throws Exception {
        when(orchestrator.recommend(eq("r1"), eq("u1"), anyInt()))
                .thenReturn(new RecommendationOrchestrator.OrchestratorResult(
                        happyResponse, "MODERN", true));

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHit", is(true)))
                .andExpect(jsonPath("$.generatedAt", is("2026-04-18T09:14:22Z")));
    }

    // -----------------------------------------------------------------
    // AC-31 — 409 ANALYSIS_NOT_READY (orchestrator raises SpaceException)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-31: orchestrator raises ANALYSIS_NOT_READY → 409")
    void ac31_analysisNotReady() throws Exception {
        doThrow(new SpaceException(SpaceErrorCode.ANALYSIS_NOT_READY))
                .when(orchestrator).recommend(eq("r1"), eq("u1"), anyInt());

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ANALYSIS_NOT_READY")));
    }

    // -----------------------------------------------------------------
    // AC-32 — 409 PREFERRED_STYLE_NOT_SET
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-32: PREFERRED_STYLE_NOT_SET → 409")
    void ac32_preferredStyleNotSet() throws Exception {
        doThrow(new SpaceException(SpaceErrorCode.PREFERRED_STYLE_NOT_SET))
                .when(orchestrator).recommend(eq("r1"), eq("u1"), anyInt());

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("PREFERRED_STYLE_NOT_SET")));
    }

    // -----------------------------------------------------------------
    // AC-33 — 403 SPACE_ACCESS_DENIED
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-33: SPACE_ACCESS_DENIED → 403")
    void ac33_accessDenied() throws Exception {
        doThrow(new SpaceException(SpaceErrorCode.SPACE_ACCESS_DENIED))
                .when(orchestrator).recommend(eq("r1"), eq("u2"), anyInt());

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("SPACE_ACCESS_DENIED")))
                .andExpect(jsonPath("$.recommendations").doesNotExist());
    }

    // -----------------------------------------------------------------
    // AC-34 — 404 SPACE_NOT_FOUND
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-34: SPACE_NOT_FOUND → 404")
    void ac34_notFound() throws Exception {
        doThrow(new SpaceException(SpaceErrorCode.SPACE_NOT_FOUND))
                .when(orchestrator).recommend(eq("ghost"), eq("u1"), anyInt());

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "ghost")
                        .header("X-User-Id", "u1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("SPACE_NOT_FOUND")));
    }

    // -----------------------------------------------------------------
    // AC-35 — 400 INVALID_TOP_N (boundary cases)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-35: topNPerCategory=11 → 400 INVALID_TOP_N")
    void ac35_topNTooLarge() throws Exception {
        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1")
                        .param("topNPerCategory", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_TOP_N")));

        verify(orchestrator, never()).recommend(any(), any(), anyInt());
    }

    @Test
    @DisplayName("AC-35: topNPerCategory=0 → 400 INVALID_TOP_N")
    void ac35_topNZero() throws Exception {
        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1")
                        .param("topNPerCategory", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_TOP_N")));
    }

    @Test
    @DisplayName("AC-35: topNPerCategory=-1 → 400 INVALID_TOP_N")
    void ac35_topNNegative() throws Exception {
        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1")
                        .param("topNPerCategory", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_TOP_N")));
    }

    // -----------------------------------------------------------------
    // AC-36 — 502 AI_SERVICE_UNAVAILABLE
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-36: orchestrator throws AI_SERVICE_UNAVAILABLE → 502")
    void ac36_aiUnavailable() throws Exception {
        doThrow(new AIException(AIErrorCode.AI_SERVICE_UNAVAILABLE, "boom"))
                .when(orchestrator).recommend(eq("r1"), eq("u1"), anyInt());

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode", is("AI_SERVICE_UNAVAILABLE")));
    }

    // -----------------------------------------------------------------
    // AC-37 — 422 CATALOG_EMPTY surfaced from Python
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-37: orchestrator throws CATALOG_EMPTY → 422")
    void ac37_catalogEmpty() throws Exception {
        doThrow(new AIException(AIErrorCode.CATALOG_EMPTY, "empty"))
                .when(orchestrator).recommend(eq("r1"), eq("u1"), anyInt());

        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1")
                        .header("X-User-Id", "u1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("CATALOG_EMPTY")));
    }

    // -----------------------------------------------------------------
    // Missing X-User-Id → 400 MISSING_USER_HEADER (regression guard)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("GET recommendations without X-User-Id → 400 MISSING_USER_HEADER")
    void missingUser() throws Exception {
        mvc.perform(get("/api/v1/spaces/{roomId}/recommendations", "r1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("MISSING_USER_HEADER")));
    }

    // -----------------------------------------------------------------
    // FR-20 — PUT preferred-style invalidates the cache
    // -----------------------------------------------------------------
    @Test
    @DisplayName("FR-20: PUT preferred-style calls orchestrator.invalidate(roomId)")
    void fr20_invalidateOnPut() throws Exception {
        var before = new com.authenticself.domain.Space();
        before.setRoomId("r1");
        before.setUserId("u1");
        before.setStatus(com.authenticself.domain.Space.Status.ANALYZED);
        before.setPhotoUrl("file:///tmp/r1.jpg");
        before.setContentType("image/jpeg");
        before.setFileSizeBytes(1234L);
        before.setUploadedAt(java.time.LocalDateTime.now());
        when(persistence.loadForRead("r1")).thenReturn(before);

        var after = new com.authenticself.domain.Space();
        after.setRoomId("r1");
        after.setUserId("u1");
        after.setStatus(com.authenticself.domain.Space.Status.ANALYZED);
        after.setPhotoUrl("file:///tmp/r1.jpg");
        after.setContentType("image/jpeg");
        after.setFileSizeBytes(1234L);
        after.setUploadedAt(java.time.LocalDateTime.now());
        after.setPreferredStyle(PreferredStyle.SIMPLE);
        when(persistence.setPreferredStyle(eq("r1"), eq(PreferredStyle.SIMPLE)))
                .thenReturn(after);

        mvc.perform(put("/api/v1/spaces/{roomId}/preferred-style", "r1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("preferredStyle", "SIMPLE"))))
                .andExpect(status().isOk());

        verify(orchestrator).invalidate("r1");
    }

    // -----------------------------------------------------------------
    // FR-20 negative — invalidation NOT called when PUT fails
    // -----------------------------------------------------------------
    @Test
    @DisplayName("FR-20: PUT with pending status does NOT invalidate the cache")
    void fr20_noInvalidateOnFailedPut() throws Exception {
        var pending = new com.authenticself.domain.Space();
        pending.setRoomId("r1");
        pending.setUserId("u1");
        pending.setStatus(com.authenticself.domain.Space.Status.PENDING_ANALYSIS);
        pending.setPhotoUrl("file:///tmp/r1.jpg");
        pending.setContentType("image/jpeg");
        pending.setFileSizeBytes(1234L);
        pending.setUploadedAt(java.time.LocalDateTime.now());
        when(persistence.loadForRead("r1")).thenReturn(pending);

        mvc.perform(put("/api/v1/spaces/{roomId}/preferred-style", "r1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("preferredStyle", "SIMPLE"))))
                .andExpect(status().isConflict());

        verify(orchestrator, never()).invalidate(any());
    }

    // ---- local static import helper -----------------------------------
    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
