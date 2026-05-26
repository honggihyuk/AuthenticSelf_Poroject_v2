package com.authenticself.web;

import com.authenticself.controller.PhotoUploadController;
import com.authenticself.service.PhotoUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc test for {@link CorsConfig} covering UC-SECURE-AUTH AC-CORS-3.
 *
 * <p>Uses {@link PhotoUploadController} as the dispatcher target because
 * its {@code POST /api/v1/spaces/photo} matches the preflight URL the
 * spec calls out. The service it depends on is mocked — the test only
 * exercises the CORS preflight (OPTIONS), which never reaches the
 * controller method.
 */
@WebMvcTest(PhotoUploadController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:8082,http://localhost:8081"
})
class CorsConfigTest {

    @Autowired MockMvc            mvc;
    @MockBean  PhotoUploadService service;

    // -----------------------------------------------------------------
    // AC-CORS-3 — allowed origin preflight returns 200 with the
    //             expected Access-Control-* headers.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-CORS-3: OPTIONS /api/v1/spaces/photo from allowed origin → 200 + Access-Control headers")
    void preflightFromAllowedOrigin_ac_cors_3() throws Exception {
        mvc.perform(options("/api/v1/spaces/photo")
                        .header("Origin", "http://localhost:8082")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, X-User-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8082"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-User-Id")));
    }

    // -----------------------------------------------------------------
    // AC-CORS-3 — unlisted origin: either 403 OR 200 with NO
    //             Access-Control-Allow-Origin header (Spring default).
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-CORS-3: OPTIONS from unlisted origin → 403 (Spring's CORS deny)")
    void preflightFromUnlistedOrigin_ac_cors_3() throws Exception {
        mvc.perform(options("/api/v1/spaces/photo")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    String acao = result.getResponse().getHeader("Access-Control-Allow-Origin");
                    boolean ok = (sc == 403) || (sc == 200 && acao == null);
                    if (!ok) {
                        throw new AssertionError("Expected 403 OR (200 with no ACAO header). Got status=" + sc
                                + " acao=" + acao);
                    }
                });
    }
}
