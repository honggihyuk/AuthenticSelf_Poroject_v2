package com.authenticself.ai;

import com.authenticself.ai.dto.SpaceAnalysisResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link AIOrchestratorController} exercising
 * the three response families: happy, analyzer-failure, transport-failure,
 * and space-not-found.
 */
@WebMvcTest(AIOrchestratorController.class)
@Import(AIOrchestratorExceptionAdvice.class)
class AIOrchestratorControllerTest {

    @Autowired MockMvc        mvc;
    @Autowired ObjectMapper   json;
    @MockBean  AIOrchestrator orchestrator;

    // -----------------------------------------------------------------
    // Happy path — AC-16
    // -----------------------------------------------------------------
    @Test
    @DisplayName("POST /internal/v1/spaces/{roomId}/analyze → 200 with ANALYZED DTO")
    void happy_path_200() throws Exception {
        when(orchestrator.analyze("r1"))
                .thenReturn(SpaceAnalysisResultDTO.ofAnalyzed("r1", "3.6x4.2x2.4m", "#E8D9B0", 142));

        mvc.perform(post("/internal/v1/spaces/{roomId}/analyze", "r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId", is("r1")))
                .andExpect(jsonPath("$.status", is("ANALYZED")))
                .andExpect(jsonPath("$.dimensions", is("3.6x4.2x2.4m")))
                .andExpect(jsonPath("$.mainColor", is("#E8D9B0")))
                .andExpect(jsonPath("$.processingMs", is(142)));
    }

    // -----------------------------------------------------------------
    // Short-circuit — AC-17
    // -----------------------------------------------------------------
    @Test
    @DisplayName("short-circuit: already-ANALYZED row → 200 with skipped=true")
    void short_circuit_200_skipped() throws Exception {
        when(orchestrator.analyze("r1"))
                .thenReturn(SpaceAnalysisResultDTO.ofSkipped("r1", "ANALYZED"));

        mvc.perform(post("/internal/v1/spaces/{roomId}/analyze", "r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped", is(true)))
                .andExpect(jsonPath("$.reason", is("NOT_PENDING")));
    }

    // -----------------------------------------------------------------
    // Analyzer failure — AC-18
    // -----------------------------------------------------------------
    @Test
    @DisplayName("analyzer failure → 422 with ANALYSIS_IMAGE_READ_FAILED")
    void analyzer_failure_422() throws Exception {
        when(orchestrator.analyze("r1"))
                .thenThrow(new AIException(AIErrorCode.ANALYSIS_IMAGE_READ_FAILED, "cv2 failed"));

        mvc.perform(post("/internal/v1/spaces/{roomId}/analyze", "r1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("ANALYSIS_IMAGE_READ_FAILED")))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    // -----------------------------------------------------------------
    // Transport failure — AC-19
    // -----------------------------------------------------------------
    @Test
    @DisplayName("transport failure → 502 with AI_SERVICE_UNAVAILABLE")
    void transport_failure_502() throws Exception {
        when(orchestrator.analyze("r1"))
                .thenThrow(new AIException(AIErrorCode.AI_SERVICE_UNAVAILABLE, "connection refused"));

        mvc.perform(post("/internal/v1/spaces/{roomId}/analyze", "r1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode", is("AI_SERVICE_UNAVAILABLE")))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    // -----------------------------------------------------------------
    // Space not found — AC-20
    // -----------------------------------------------------------------
    @Test
    @DisplayName("unknown roomId → 404 with SPACE_NOT_FOUND; no further calls")
    void not_found_404() throws Exception {
        when(orchestrator.analyze("nope"))
                .thenThrow(new SpaceNotFoundException("nope"));

        mvc.perform(post("/internal/v1/spaces/{roomId}/analyze", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("SPACE_NOT_FOUND")));

        verify(orchestrator).analyze(eq("nope"));
    }
}
