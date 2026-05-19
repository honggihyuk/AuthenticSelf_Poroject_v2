package com.authenticself.auth;

import com.authenticself.auth.dto.LoginRequest;
import com.authenticself.auth.dto.LoginResponse;
import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Demo-grade login service for the AuthenticSelf PoC.
 *
 * <p>Credentials are hardcoded ({@code admin/1234}, {@code user/1234})
 * and the returned token is a base64-encoded JSON blob, NOT a signed JWT.
 * A real implementation would replace {@link #validate(String, String)}
 * with a password-hash check and {@link #encodeToken} with JWT signing —
 * the response envelope ({@link LoginResponse}) is intentionally shaped
 * to absorb that change without forcing a frontend rewrite.
 */
@Service
public class AuthService {

    /** username → (password, userId). userId matches the {@code users.user_id} row seeded by DemoUserBootstrap. */
    private static final Map<String, Credential> CREDS = Map.of(
            "admin", new Credential("1234", "admin"),
            "user",  new Credential("1234", "user")
    );

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest req) {
        if (req == null
                || req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().isBlank()) {
            throw new AuthException(AuthErrorCode.MISSING_FIELDS);
        }
        Credential expected = CREDS.get(req.username());
        if (expected == null || !expected.password.equals(req.password())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Optional<User> userRow = userRepository.findById(expected.userId);
        if (userRow.isEmpty()) {
            // Shouldn't happen — DemoUserBootstrap upserts both rows at startup —
            // but if it does, fail closed rather than silently mis-routing.
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        User u = userRow.get();
        String token = encodeToken(u.getUserId(), u.getRole().name());
        return new LoginResponse(token, u.getUserId(), u.getRole().name(), u.getName());
    }

    private static String encodeToken(String userId, String role) {
        String json = "{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private record Credential(String password, String userId) {}
}
