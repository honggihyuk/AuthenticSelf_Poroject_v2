package com.authenticself.auth;

import com.authenticself.auth.dto.LoginRequest;
import com.authenticself.auth.dto.LoginResponse;
import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Login service for the AuthenticSelf surface
 * (UC-SECURE-AUTH FR-10 / AC-10 / AC-11 / AC-12).
 *
 * <p>Replaces the prior demo PoC that compared plaintext against a
 * hardcoded {@code CREDS} map and emitted a base64-stub token. The
 * rewrite verifies the request password against {@code users.password_hash}
 * via {@link PasswordEncoder#matches(CharSequence, String)} and issues a
 * real HS256 JWT via {@link JwtService}. The response envelope
 * ({@link LoginResponse}) is byte-identical to the prior contract so the
 * mobile {@code AuthContext} is frozen (FR-BC-1).
 *
 * <p>Timing-attack mitigation: when the request username does not match
 * any row, the service still runs one
 * {@link PasswordEncoder#matches(CharSequence, String)} call against a
 * constant precomputed BCrypt hash ({@link #DUMMY_HASH}). Both the
 * unknown-user path and the wrong-password path then take the same ~80ms
 * BCrypt round-trip and return the same {@code INVALID_CREDENTIALS} wire
 * code — so a remote attacker cannot distinguish "unknown username" from
 * "wrong password" by timing alone.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Constant BCrypt hash used as the comparison target on the
     * unknown-user branch (FR-10 timing-attack mitigation). Generated
     * once by {@code BCryptPasswordEncoder(10).encode(<random-string>)};
     * the plaintext is intentionally not recoverable. The hash byte
     * length is the standard 60-char BCrypt output, well within the V11
     * {@code VARCHAR(72)} column width.
     */
    private static final String DUMMY_HASH =
            "$2a$10$abcdefghijklmnopqrstuuG6JpRkN7gtZNkbcPwTaC7TkR4xVQzPm";

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService      jwtService;

    public AuthService(
            UserRepository  userRepository,
            PasswordEncoder passwordEncoder,
            JwtService      jwtService
    ) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
    }

    public LoginResponse login(LoginRequest req) {
        if (req == null
                || req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().isBlank()) {
            log.warn("op=login userId={} result=failure errorCode={}",
                    req == null ? "<null>" : safe(req.username()),
                    AuthErrorCode.MISSING_FIELDS);
            throw new AuthException(AuthErrorCode.MISSING_FIELDS);
        }

        String attempted = req.username();
        Optional<User> userRow = userRepository.findById(attempted);

        if (userRow.isEmpty()) {
            // Timing-safe: still run one BCrypt match against a dummy
            // hash so the unknown-user branch costs the same as the
            // wrong-password branch (FR-10).
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            log.warn("op=login userId={} result=failure errorCode={}",
                    safe(attempted), AuthErrorCode.INVALID_CREDENTIALS);
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        User u = userRow.get();
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            log.warn("op=login userId={} result=failure errorCode={}",
                    safe(attempted), AuthErrorCode.INVALID_CREDENTIALS);
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtService.issue(u.getUserId(), u.getRole().name(), u.getName());
        log.info("op=login userId={} result=success", u.getUserId());
        return new LoginResponse(token, u.getUserId(), u.getRole().name(), u.getName());
    }

    /** Defensive: strip whitespace / control chars before logging the attempted userId. */
    private static String safe(String s) {
        if (s == null) return "<null>";
        return s.replaceAll("[\\r\\n\\t]", "_");
    }
}
