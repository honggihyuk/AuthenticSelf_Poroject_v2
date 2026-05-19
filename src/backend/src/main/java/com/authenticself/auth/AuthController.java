package com.authenticself.auth;

import com.authenticself.auth.dto.LoginRequest;
import com.authenticself.auth.dto.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the demo login flow.
 *
 * <p>Single endpoint {@code POST /api/v1/auth/login}. Hardcoded creds are
 * defined in {@link AuthService}; the response carries an opaque token plus
 * {@code role} / {@code userId} for the frontend to route on.
 */
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.POST, RequestMethod.OPTIONS
})
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
