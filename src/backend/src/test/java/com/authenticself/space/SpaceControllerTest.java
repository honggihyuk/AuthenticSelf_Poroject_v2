package com.authenticself.space;

import com.authenticself.ai.RecommendationOrchestrator;
import com.authenticself.ai.SpaceAnalysisPersistence;
import com.authenticself.ai.SpaceNotFoundException;
import com.authenticself.ai.StyleConfidenceCache;
import com.authenticself.domain.Space;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link SpaceController} (Task-4 AC-19..AC-26).
 */
@WebMvcTest(SpaceController.class)
@Import(SpaceExceptionAdvice.class)
class SpaceControllerTest {

    @Autowired MockMvc        mvc;
    @Autowired ObjectMapper   json;
    @MockBean  SpaceAnalysisPersistence persistence;
    @MockBean  RecommendationOrchestrator recommendationOrchestrator;

    @BeforeEach
    void init() {
        StyleConfidenceCache.clear();
    }

    @AfterEach
    void clear() {
        StyleConfidenceCache.clear();
    }

    // -----------------------------------------------------------------
    // GET — AC-19 happy path
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-19: GET /api/v1/spaces/{roomId} returns 200 with the full state")
    void get_happy_path() throws Exception {
        Space s = makeSpace("r1", "u1", Space.Status.ANALYZED);
        s.setDimensions("3.6x4.2x2.4m");
        s.setMainColor("#E8D9B0");
        s.setStyle("MODERN");
        s.setAnalysisDate(LocalDateTime.parse("2026-04-17T10:23:15"));
        s.setUploadedAt(LocalDateTime.parse("2026-04-17T10:23:03"));
        when(persistence.loadForRead("r1")).thenReturn(s);
        StyleConfidenceCache.put("r1", 0.72);

        mvc.perform(get("/api/v1/spaces/{roomId}", "r1").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId",          is("r1")))
                .andExpect(jsonPath("$.status",          is("ANALYZED")))
                .andExpect(jsonPath("$.style",           is("MODERN")))
                .andExpect(jsonPath("$.styleConfidence", is(0.72)))
                .andExpect(jsonPath("$.preferredStyle",  nullValue()))
                .andExpect(jsonPath("$.dimensions",      is("3.6x4.2x2.4m")))
                .andExpect(jsonPath("$.mainColor",       is("#E8D9B0")))
                .andExpect(jsonPath("$.analysisDate",    notNullValue()))
                .andExpect(jsonPath("$.uploadedAt",      notNullValue()));
    }

    // -----------------------------------------------------------------
    // GET — AC-20 forbidden
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-20: mismatched X-User-Id → 403 SPACE_ACCESS_DENIED, no row data")
    void get_forbidden() throws Exception {
        Space s = makeSpace("r1", "u1", Space.Status.ANALYZED);
        s.setStyle("MODERN");
        when(persistence.loadForRead("r1")).thenReturn(s);

        mvc.perform(get("/api/v1/spaces/{roomId}", "r1").header("X-User-Id", "u2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("SPACE_ACCESS_DENIED")))
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.roomId").doesNotExist());
    }

    // -----------------------------------------------------------------
    // GET — AC-21 not found
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-21: unknown roomId → 404 SPACE_NOT_FOUND")
    void get_not_found() throws Exception {
        doThrow(new SpaceNotFoundException("ghost"))
                .when(persistence).loadForRead("ghost");

        mvc.perform(get("/api/v1/spaces/{roomId}", "ghost").header("X-User-Id", "u1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("SPACE_NOT_FOUND")));
    }

    // -----------------------------------------------------------------
    // PUT — AC-22 happy path
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-22: PUT preferred-style → 200, row updated, response echoes value")
    void put_happy_path() throws Exception {
        Space before = makeSpace("r1", "u1", Space.Status.ANALYZED);
        when(persistence.loadForRead("r1")).thenReturn(before);
        Space after = makeSpace("r1", "u1", Space.Status.ANALYZED);
        after.setPreferredStyle(PreferredStyle.MODERN);
        when(persistence.setPreferredStyle(eq("r1"), eq(PreferredStyle.MODERN))).thenReturn(after);

        mvc.perform(put("/api/v1/spaces/{roomId}/preferred-style", "r1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("preferredStyle", "MODERN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredStyle", is("MODERN")));

        verify(persistence).setPreferredStyle(eq("r1"), eq(PreferredStyle.MODERN));
    }

    // -----------------------------------------------------------------
    // PUT — AC-24 invalid enum value
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-24: invalid enum value → 400 INVALID_PREFERRED_STYLE, no update")
    void put_invalid_enum() throws Exception {
        mvc.perform(put("/api/v1/spaces/{roomId}/preferred-style", "r1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("preferredStyle", "BAROQUE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_PREFERRED_STYLE")));

        verify(persistence, org.mockito.Mockito.never())
                .setPreferredStyle(any(), any());
    }

    // -----------------------------------------------------------------
    // PUT — AC-25 not ready
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-25: PUT while PENDING_ANALYSIS → 409 ANALYSIS_NOT_READY")
    void put_not_ready() throws Exception {
        Space pending = makeSpace("r1", "u1", Space.Status.PENDING_ANALYSIS);
        when(persistence.loadForRead("r1")).thenReturn(pending);

        mvc.perform(put("/api/v1/spaces/{roomId}/preferred-style", "r1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("preferredStyle", "MODERN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ANALYSIS_NOT_READY")));

        verify(persistence, org.mockito.Mockito.never())
                .setPreferredStyle(any(), any());
    }

    // -----------------------------------------------------------------
    // PUT — AC-26 CURRENT is accepted
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-26: PUT preferredStyle=CURRENT → 200, accepted as user pick")
    void put_current_accepted() throws Exception {
        Space before = makeSpace("r1", "u1", Space.Status.ANALYZED);
        when(persistence.loadForRead("r1")).thenReturn(before);
        Space after = makeSpace("r1", "u1", Space.Status.ANALYZED);
        after.setPreferredStyle(PreferredStyle.CURRENT);
        when(persistence.setPreferredStyle(eq("r1"), eq(PreferredStyle.CURRENT))).thenReturn(after);

        mvc.perform(put("/api/v1/spaces/{roomId}/preferred-style", "r1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("preferredStyle", "CURRENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredStyle", is("CURRENT")));
    }

    // -----------------------------------------------------------------
    // PUT — forbidden (owner mismatch)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("PUT with mismatched user → 403 SPACE_ACCESS_DENIED; no write")
    void put_forbidden() throws Exception {
        Space s = makeSpace("r1", "u1", Space.Status.ANALYZED);
        when(persistence.loadForRead("r1")).thenReturn(s);

        mvc.perform(put("/api/v1/spaces/{roomId}/preferred-style", "r1")
                        .header("X-User-Id", "u2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("preferredStyle", "SIMPLE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("SPACE_ACCESS_DENIED")));

        verify(persistence, org.mockito.Mockito.never())
                .setPreferredStyle(any(), any());
    }

    // -----------------------------------------------------------------
    // GET — missing X-User-Id header → 400
    // -----------------------------------------------------------------
    @Test
    @DisplayName("GET without X-User-Id → 400 MISSING_USER_HEADER")
    void get_missing_header() throws Exception {
        mvc.perform(get("/api/v1/spaces/{roomId}", "r1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("MISSING_USER_HEADER")));
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static Space makeSpace(String roomId, String userId, Space.Status status) {
        Space s = new Space();
        s.setRoomId(roomId);
        s.setUserId(userId);
        s.setPhotoUrl("file:///tmp/" + roomId + ".jpg");
        s.setContentType("image/jpeg");
        s.setFileSizeBytes(12345L);
        s.setStatus(status);
        s.setUploadedAt(LocalDateTime.parse("2026-04-17T10:23:03"));
        return s;
    }
}
