package com.authenticself.test;

import com.authenticself.auth.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test fixture helper centralising JWT issuance for backend integration
 * tests (UC-SECURE-AUTH FR-BC-3 / AC-BC-3).
 *
 * <p>Spring-injected via the live {@link JwtService} bean so the issued
 * tokens are byte-identical to what the running app would emit — no
 * separate secret, no manual HMAC, no test-only signing key drift.
 *
 * <p>Usage in a {@code @SpringBootTest}:
 * <pre>{@code
 * @Autowired AuthTestHelper auth;
 *
 * @Test void example() {
 *     mvc.perform(get("/api/v1/spaces/{id}", "r1")
 *             .headers(toHttpHeaders(auth.authHeaders("user", "USER"))))
 *         .andExpect(status().isOk());
 * }
 * }</pre>
 */
@Component
public class AuthTestHelper {

    private final JwtService jwtService;

    @Autowired
    public AuthTestHelper(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /** Issue a JWT for the given {@code userId} / {@code role} via the live bean. */
    public String issueTokenFor(String userId, String role) {
        return jwtService.issue(userId, role, userId);
    }

    /**
     * Returns the two headers every authenticated request needs:
     * {@code Authorization: Bearer <jwt>} plus the cross-validated
     * {@code X-User-Id} (UC-SECURE-AUTH FR-11).
     */
    public Map<String, String> authHeaders(String userId, String role) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer " + issueTokenFor(userId, role));
        h.put("X-User-Id", userId);
        return h;
    }
}
