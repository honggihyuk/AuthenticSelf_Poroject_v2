package com.authenticself.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link JwtAuthFilter} covering UC-SECURE-AUTH
 * FR-11 / FR-12 (AC-13 / AC-14 / AC-15).
 *
 * <p>The filter is exercised directly with mocked servlet objects so the
 * test suite can run without standing up the full Spring web context.
 */
class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-please-replace-in-production-32chars";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-04-18T09:00:00Z"), ZoneOffset.UTC);

    private JwtService     jwt;
    private JwtAuthFilter  filter;
    private ObjectMapper   json;

    @BeforeEach
    void setUp() {
        json   = new ObjectMapper();
        jwt    = new JwtService(SECRET, CLOCK);
        filter = new JwtAuthFilter(jwt, json);
    }

    // -----------------------------------------------------------------
    // AC-15 — login endpoint bypasses the filter entirely.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-15: POST /api/v1/auth/login bypasses filter — no Authorization required")
    void loginEndpointBypassesFilter_ac15() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200); // default; filter did not set 401
    }

    // -----------------------------------------------------------------
    // AC-13 — missing Bearer → 401 INVALID_TOKEN.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-13: missing Authorization header → 401 INVALID_TOKEN, masked log")
    void missingBearer_ac13() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/spaces/r1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        JsonNode body = json.readTree(res.getContentAsString());
        assertThat(body.get("errorCode").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(body.get("correlationId").asText()).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    @DisplayName("AC-13: Authorization without Bearer prefix → 401 INVALID_TOKEN")
    void wrongAuthScheme_ac13() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/spaces/r1");
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(json.readTree(res.getContentAsString()).get("errorCode").asText())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("AC-13: expired token → 401 INVALID_TOKEN")
    void expiredToken_ac13() throws ServletException, IOException {
        JwtService pastIssuer = new JwtService(
                SECRET, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        String expired = pastIssuer.issue("user", "USER", "name");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/spaces/r1");
        req.addHeader("Authorization", "Bearer " + expired);
        req.addHeader("X-User-Id", "user");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(json.readTree(res.getContentAsString()).get("errorCode").asText())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("AC-13: bad signature → 401 INVALID_TOKEN")
    void badSignature_ac13() throws ServletException, IOException {
        JwtService otherIssuer = new JwtService(
                "another-32byte-secret-for-bad-sig-cases-zzzz", CLOCK);
        String foreign = otherIssuer.issue("user", "USER", "name");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/spaces/r1");
        req.addHeader("Authorization", "Bearer " + foreign);
        req.addHeader("X-User-Id", "user");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    // -----------------------------------------------------------------
    // AC-14 — X-User-Id mismatch vs JWT sub.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-14: X-User-Id != JWT sub → 401 USER_ID_MISMATCH")
    void userIdMismatch_ac14() throws ServletException, IOException {
        String token = jwt.issue("user", "USER", "name");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/spaces/r1");
        req.addHeader("Authorization", "Bearer " + token);
        req.addHeader("X-User-Id", "someone-else");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        JsonNode body = json.readTree(res.getContentAsString());
        assertThat(body.get("errorCode").asText()).isEqualTo("USER_ID_MISMATCH");
    }

    @Test
    @DisplayName("AC-14: missing X-User-Id → 401 INVALID_TOKEN (not USER_ID_MISMATCH)")
    void missingXUserId_ac14() throws ServletException, IOException {
        String token = jwt.issue("user", "USER", "name");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/spaces/r1");
        req.addHeader("Authorization", "Bearer " + token);
        // No X-User-Id header.
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(json.readTree(res.getContentAsString()).get("errorCode").asText())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("AC-14: matching X-User-Id and JWT sub → chain proceeds, identity stashed")
    void happyPath_ac14() throws ServletException, IOException {
        String token = jwt.issue("user", "USER", "name");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/spaces/r1");
        req.addHeader("Authorization", "Bearer " + token);
        req.addHeader("X-User-Id", "user");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
        Object stashed = req.getAttribute(JwtAuthFilter.ATTR_AUTH_VERIFICATION);
        assertThat(stashed).isInstanceOf(JwtVerification.class);
        assertThat(((JwtVerification) stashed).userId()).isEqualTo("user");
    }

    // -----------------------------------------------------------------
    // Future-proof: non-/api/v1 path skips filter.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("FR-11: non-/api/v1 path skips filter")
    void nonApiPathSkips() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    // -----------------------------------------------------------------
    // FR-11: OPTIONS preflight skips filter so CORS can answer.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("FR-11: OPTIONS preflight on /api/v1/** skips filter")
    void optionsSkipsFilter() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/v1/spaces/photo");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }
}
