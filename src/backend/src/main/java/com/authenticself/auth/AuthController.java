package com.authenticself.auth;

import com.authenticself.auth.dto.LoginRequest;
import com.authenticself.auth.dto.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the demo login flow.
 *
 * <p>Single endpoint {@code POST /api/v1/auth/login}. Credentials are
 * verified by {@link AuthService} against the BCrypt-hashed
 * {@code users.password_hash} column (UC-SECURE-AUTH FR-10); the
 * response carries a signed HS256 JWT plus {@code role}/{@code userId}
 * for the frontend to route on.
 *
 * <p>CORS is handled centrally by
 * {@link com.authenticself.web.CorsConfig} (UC-SECURE-AUTH FR-CORS-2);
 * no per-controller cross-origin annotation is needed.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody(required = false) LoginRequest body) {
        return ResponseEntity.ok(service.login(body));
    }
}
