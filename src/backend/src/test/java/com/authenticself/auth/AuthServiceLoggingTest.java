package com.authenticself.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.authenticself.auth.dto.LoginRequest;
import com.authenticself.auth.dto.LoginResponse;
import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Captures SLF4J / Logback output from {@link AuthService} to enforce
 * UC-SECURE-AUTH AC-18: exactly one INFO line per success / WARN per
 * failure, never the plaintext password, never the BCrypt hash, never
 * the issued JWT.
 */
class AuthServiceLoggingTest {

    private static final String SECRET = "test-secret-please-replace-in-production-32chars";

    private UserRepository  userRepository;
    private PasswordEncoder encoder;
    private JwtService      jwt;
    private AuthService     service;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        encoder        = new BCryptPasswordEncoder(10);
        jwt            = new JwtService(SECRET,
                Clock.fixed(Instant.parse("2026-04-18T09:00:00Z"), ZoneOffset.UTC));
        service        = new AuthService(userRepository, encoder, jwt);

        Logger root = (Logger) LoggerFactory.getLogger(AuthService.class);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        Logger root = (Logger) LoggerFactory.getLogger(AuthService.class);
        root.detachAppender(appender);
    }

    @Test
    @DisplayName("AC-18: success → exactly one INFO line matching op=login userId=\\S+ result=success")
    void successEmitsOneInfoLine_ac18() {
        User u = new User();
        u.setUserId("user");
        u.setName("일반사용자");
        u.setEmail("u@example.com");
        u.setRole(User.Role.USER);
        u.setPasswordHash(encoder.encode("1234"));
        when(userRepository.findById("user")).thenReturn(Optional.of(u));

        LoginResponse resp = service.login(new LoginRequest("user", "1234"));
        assertThat(resp.userId()).isEqualTo("user");

        List<ILoggingEvent> events = appender.list;
        long infoSuccessCount = events.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .filter(e -> e.getFormattedMessage().matches(".*op=login userId=\\S+ result=success.*"))
                .count();
        assertThat(infoSuccessCount).isEqualTo(1);

        assertNoSecretsLogged(events, resp.token(), "1234", u.getPasswordHash());
    }

    @Test
    @DisplayName("AC-18: wrong password → exactly one WARN line matching op=login ... result=failure errorCode=INVALID_CREDENTIALS")
    void failureEmitsOneWarnLine_ac18() {
        User u = new User();
        u.setUserId("user");
        u.setName("일반사용자");
        u.setRole(User.Role.USER);
        u.setEmail("u@example.com");
        u.setPasswordHash(encoder.encode("1234"));
        when(userRepository.findById("user")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("user", "wrong")))
                .isInstanceOf(AuthException.class);

        List<ILoggingEvent> events = appender.list;
        long warnFailures = events.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage()
                        .matches(".*op=login userId=\\S+ result=failure errorCode=INVALID_CREDENTIALS.*"))
                .count();
        assertThat(warnFailures).isEqualTo(1);

        assertNoSecretsLogged(events, null, "wrong", u.getPasswordHash());
    }

    @Test
    @DisplayName("AC-18: missing-fields → WARN line with errorCode=MISSING_FIELDS")
    void missingFieldsEmitsWarn_ac18() {
        assertThatThrownBy(() -> service.login(new LoginRequest(null, null)))
                .isInstanceOf(AuthException.class);

        List<ILoggingEvent> events = appender.list;
        long warnMissing = events.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("errorCode=MISSING_FIELDS"))
                .count();
        assertThat(warnMissing).isEqualTo(1);
    }

    /** Helper: assert no log message contains any of the given secrets. */
    private static void assertNoSecretsLogged(
            List<ILoggingEvent> events, String token, String password, String hash) {
        for (ILoggingEvent e : events) {
            String msg = e.getFormattedMessage();
            assertThat(msg).as("log line: %s", msg).doesNotContain(password);
            if (token != null)  assertThat(msg).as("token leaked").doesNotContain(token);
            if (hash != null)   assertThat(msg).as("hash leaked").doesNotContain(hash);
        }
    }
}
