package com.authenticself.auth;

import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice test for {@link AuthController} (UC-SECURE-AUTH
 * AC-10 / AC-11 / AC-12).
 *
 * <p>Loads the controller plus its advice and the {@link AuthService},
 * {@link JwtService}, and a real {@link BCryptPasswordEncoder} via the
 * inner config. The {@link UserRepository} is mocked so the test
 * controls the user-row state row-by-row.
 *
 * <p>The {@code passwordEncoder} bean is wrapped in a Spy so AC-11 can
 * assert that BCrypt's matches(...) is invoked exactly once on BOTH the
 * unknown-user and wrong-password branches (FR-10 timing-attack
 * mitigation).
 */
@WebMvcTest(AuthController.class)
@Import({AuthControllerTest.TestBeans.class, AuthExceptionAdvice.class})
@TestPropertySource(properties = {
        "app.auth.jwt.secret=test-secret-please-replace-in-production-32chars"
})
class AuthControllerTest {

    private static final String SECRET = "test-secret-please-replace-in-production-32chars";

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper json;

    @MockBean UserRepository userRepository;

    @SpyBean PasswordEncoder passwordEncoder;

    // -----------------------------------------------------------------
    // AC-10 — login happy path emits real HS256 JWT.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-10: POST /auth/login → 200 with {token, userId, role, name}; token is a valid JWT")
    void loginHappyPath_ac10() throws Exception {
        User u = mkUser("user", "일반사용자", User.Role.USER, "1234");
        when(userRepository.findById("user")).thenReturn(Optional.of(u));

        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "user", "password", "1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("user")))
                .andExpect(jsonPath("$.role",   is("USER")))
                .andExpect(jsonPath("$.name",   is("일반사용자")))
                .andExpect(jsonPath("$.token",  notNullValue()))
                .andReturn();

        // Parse the JWT and assert it carries the expected claims. We
        // parse with the SAME fixed clock (2026-04-18) the AuthService
        // used when issuing — otherwise the wall-clock exp check would
        // race the actual run time.
        String token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant testNow = Instant.parse("2026-04-18T09:00:00Z");
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(testNow))
                .build()
                .parseSignedClaims(token);
        assertThat(jws.getPayload().getSubject()).isEqualTo("user");
        assertThat(jws.getPayload().get("role", String.class)).isEqualTo("USER");
    }

    @Test
    @DisplayName("AC-10: admin login → 200 with role=ADMIN, name='관리자'")
    void loginHappyPath_admin_ac10() throws Exception {
        User u = mkUser("admin", "관리자", User.Role.ADMIN, "1234");
        when(userRepository.findById("admin")).thenReturn(Optional.of(u));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "admin", "password", "1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("admin")))
                .andExpect(jsonPath("$.role",   is("ADMIN")))
                .andExpect(jsonPath("$.name",   is("관리자")));
    }

    // -----------------------------------------------------------------
    // AC-11 — wrong password and unknown user both 401 INVALID_CREDENTIALS;
    //         BCrypt matches(..) invoked exactly once on each branch.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-11: wrong password → 401 INVALID_CREDENTIALS; BCrypt matches called once")
    void wrongPassword_ac11() throws Exception {
        User u = mkUser("user", "일반사용자", User.Role.USER, "1234");
        when(userRepository.findById("user")).thenReturn(Optional.of(u));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "user", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.message",   is("아이디 또는 비밀번호가 올바르지 않습니다.")));

        verify(passwordEncoder, times(1)).matches(eq("wrong"), any());
    }

    @Test
    @DisplayName("AC-11: unknown user → 401 INVALID_CREDENTIALS; BCrypt matches still called once (timing-safe)")
    void unknownUser_ac11() throws Exception {
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "nonexistent", "password", "anything"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("INVALID_CREDENTIALS")));

        verify(passwordEncoder, times(1)).matches(eq("anything"), any());
    }

    // -----------------------------------------------------------------
    // AC-12 — missing fields → 400 MISSING_FIELDS.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-12: empty body → 400 MISSING_FIELDS")
    void emptyBody_ac12() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("MISSING_FIELDS")));
    }

    @Test
    @DisplayName("AC-12: blank username + blank password → 400 MISSING_FIELDS")
    void blankCreds_ac12() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", "");
        body.put("password", "");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("MISSING_FIELDS")));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private static User mkUser(String userId, String name, User.Role role, String plainPassword) {
        User u = new User();
        u.setUserId(userId);
        u.setName(name);
        u.setEmail(userId + "@example.com");
        u.setRole(role);
        u.setPasswordHash(new BCryptPasswordEncoder(10).encode(plainPassword));
        return u;
    }

    /** Real {@link AuthService} + real {@link JwtService} + real BCrypt + fixed Clock. */
    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {

        @org.springframework.context.annotation.Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-04-18T09:00:00Z"), ZoneOffset.UTC);
        }

        @org.springframework.context.annotation.Bean
        JwtService jwtService(Clock c) {
            return new JwtService(SECRET, c);
        }

        @org.springframework.context.annotation.Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(10);
        }

        @org.springframework.context.annotation.Bean
        AuthService authService(
                UserRepository repo,
                PasswordEncoder enc,
                JwtService jwt
        ) {
            return new AuthService(repo, enc, jwt);
        }
    }
}
