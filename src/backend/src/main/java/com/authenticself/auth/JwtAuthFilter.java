package com.authenticself.auth;

import com.authenticself.controller.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Servlet filter that gates every non-login {@code /api/v1/**} request
 * on a valid HS256 JWT plus an {@code X-User-Id} header that matches the
 * token's {@code sub} claim (UC-SECURE-AUTH FR-11 / AC-13 / AC-14 / AC-15).
 *
 * <p>Three skip cases — the filter chain proceeds untouched:
 * <ol>
 *   <li>request URI is exactly {@code /api/v1/auth/login} (the only
 *       unauthenticated endpoint),</li>
 *   <li>request URI is outside the {@code /api/v1/} prefix (future-proof
 *       for Actuator / static assets),</li>
 *   <li>method is {@code OPTIONS} — let the CORS preflight through so
 *       browsers see the {@code Access-Control-*} response headers
 *       before the actual request is issued.</li>
 * </ol>
 *
 * <p>Otherwise: pull {@code Authorization: Bearer <jwt>}, verify it via
 * {@link JwtService#verify(String)}, then cross-check {@code X-User-Id}
 * against the verified {@code sub}. Mismatch → 401
 * {@code USER_ID_MISMATCH}; missing Bearer / missing X-User-Id /
 * verification failure → 401 {@code INVALID_TOKEN}. All rejections emit
 * the project-standard {@link ErrorResponse} envelope with a freshly
 * generated UUIDv4 correlationId so the wire shape matches what every
 * other {@code *ExceptionAdvice} produces.
 *
 * <p>Security log hygiene (FR-11, NFR security / AC-13): the token is
 * never logged in full — only the first 8 characters followed by
 * {@code ...} make it into the WARN line. No password, no JWT body, no
 * BCrypt bytes ever appear in any log line.
 *
 * <p>Identity stash: on success, the verified {@link JwtVerification} is
 * placed in the {@link #ATTR_AUTH_VERIFICATION} request attribute.
 * Controllers do NOT read it today (D-1 — they still rely on
 * {@code @RequestHeader("X-User-Id")}). The attribute is reserved for
 * the future {@code AUTH-CONTROLLER-REFACTOR} task.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** Endpoint that bypasses the filter entirely (FR-11, AC-15). */
    public static final String LOGIN_PATH = "/api/v1/auth/login";

    /** Prefix the filter polices; anything outside it skips the chain. */
    public static final String API_PREFIX = "/api/v1/";

    /** Bearer scheme prefix (case-sensitive per FR-11). */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Request attribute key used to stash the verified identity. */
    public static final String ATTR_AUTH_VERIFICATION = "authVerification";

    private final JwtService    jwtService;
    private final ObjectMapper  objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService   = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {
        String uri    = req.getRequestURI();
        String method = req.getMethod();

        // Skip cases — see class Javadoc.
        if (!uri.startsWith(API_PREFIX)
                || LOGIN_PATH.equals(uri)
                || "OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            reject(res, AuthErrorCode.INVALID_TOKEN, mask(null));
            return;
        }
        String token = auth.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            reject(res, AuthErrorCode.INVALID_TOKEN, mask(null));
            return;
        }

        JwtVerification verification;
        try {
            verification = jwtService.verify(token);
        } catch (AuthException ex) {
            reject(res, ex.code(), mask(token));
            return;
        }

        String headerUserId = req.getHeader("X-User-Id");
        if (headerUserId == null || headerUserId.isBlank()) {
            // FR-11: missing X-User-Id collapses to INVALID_TOKEN so the
            // client UX is uniform with bad-Bearer.
            reject(res, AuthErrorCode.INVALID_TOKEN, mask(token));
            return;
        }
        if (!headerUserId.equals(verification.userId())) {
            reject(res, AuthErrorCode.USER_ID_MISMATCH, mask(token));
            return;
        }

        // Stash for the future controller refactor (D-1).
        req.setAttribute(ATTR_AUTH_VERIFICATION, verification);
        chain.doFilter(req, res);
    }

    /** Write the standard {@link ErrorResponse} envelope and stop the chain. */
    private void reject(HttpServletResponse res, AuthErrorCode code, String maskedToken)
            throws IOException {
        String correlationId = UUID.randomUUID().toString();
        log.warn("auth filter reject code={} correlationId={} token={}",
                code, correlationId, maskedToken);
        res.setStatus(code.httpStatus().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = new ErrorResponse(code.name(), code.defaultMessage(), correlationId);
        objectMapper.writeValue(res.getOutputStream(), body);
    }

    /** Token mask for log output — first 8 chars + ellipsis, never the full token. */
    private static String mask(String token) {
        if (token == null || token.isEmpty()) return "<none>";
        if (token.length() <= 8) return token + "...";
        return token.substring(0, 8) + "...";
    }
}
