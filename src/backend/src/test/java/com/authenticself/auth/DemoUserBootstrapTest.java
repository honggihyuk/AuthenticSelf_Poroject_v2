package com.authenticself.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DemoUserBootstrap} covering UC-SECURE-AUTH
 * AC-9 (idempotent hash upsert, no plaintext logging) and FR-BC-4
 * (operator-set BCrypt hashes survive restart).
 */
class DemoUserBootstrapTest {

    private UserRepository       repo;
    private PasswordEncoder      encoder;
    private DemoUserBootstrap    bootstrap;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        repo      = mock(UserRepository.class);
        encoder   = new BCryptPasswordEncoder(10);
        bootstrap = new DemoUserBootstrap(repo, encoder);

        Logger root = (Logger) LoggerFactory.getLogger(DemoUserBootstrap.class);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        Logger root = (Logger) LoggerFactory.getLogger(DemoUserBootstrap.class);
        root.detachAppender(appender);
    }

    // -----------------------------------------------------------------
    // AC-9, first boot — placeholder gets overwritten with BCrypt.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-9: first boot — placeholder password_hash → overwritten with BCrypt('1234')")
    void firstBoot_overwritesPlaceholder_ac9() {
        User admin = mkUser("admin", "관리자", User.Role.ADMIN, DemoUserBootstrap.PLACEHOLDER);
        User user  = mkUser("user",  "일반사용자", User.Role.USER,  DemoUserBootstrap.PLACEHOLDER);
        when(repo.findById("admin")).thenReturn(Optional.of(admin));
        when(repo.findById("user")).thenReturn(Optional.of(user));

        bootstrap.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo, times(2)).save(saved.capture());
        for (User u : saved.getAllValues()) {
            assertThat(u.getPasswordHash()).startsWith("$2a$");
            assertThat(encoder.matches("1234", u.getPasswordHash())).isTrue();
        }
        assertNoPlaintextLogged();
    }

    // -----------------------------------------------------------------
    // AC-9, second boot — existing BCrypt hash preserved.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-9 / FR-BC-4: second boot — operator-set BCrypt hash NOT overwritten")
    void secondBoot_preservesOperatorHash() {
        String operatorHash = new BCryptPasswordEncoder(10).encode("operator-chose-this");
        User admin = mkUser("admin", "관리자", User.Role.ADMIN, operatorHash);
        User user  = mkUser("user",  "일반사용자", User.Role.USER,  operatorHash);
        when(repo.findById("admin")).thenReturn(Optional.of(admin));
        when(repo.findById("user")).thenReturn(Optional.of(user));

        bootstrap.run();

        verify(repo, never()).save(any());
        assertThat(admin.getPasswordHash()).isEqualTo(operatorHash);
        assertThat(user.getPasswordHash()).isEqualTo(operatorHash);
    }

    // -----------------------------------------------------------------
    // AC-9, missing row — insert with BCrypt hash.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-9: missing row → INSERT with BCrypt-hashed default password")
    void missingRow_insertsWithHash_ac9() {
        when(repo.findById("admin")).thenReturn(Optional.empty());
        when(repo.findById("user")).thenReturn(Optional.empty());

        bootstrap.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo, times(2)).save(saved.capture());
        for (User u : saved.getAllValues()) {
            assertThat(u.getPasswordHash()).startsWith("$2a$");
            assertThat(encoder.matches("1234", u.getPasswordHash())).isTrue();
        }
        assertNoPlaintextLogged();
    }

    // -----------------------------------------------------------------
    // FR-9 — null and blank are both "needs bootstrap"
    // -----------------------------------------------------------------
    @Test
    @DisplayName("FR-9: needsBootstrap(null/blank/placeholder/garbage) → true")
    void needsBootstrap_returnsTrueForNonBcryptValues() {
        assertThat(DemoUserBootstrap.needsBootstrap(null)).isTrue();
        assertThat(DemoUserBootstrap.needsBootstrap("")).isTrue();
        assertThat(DemoUserBootstrap.needsBootstrap("  ")).isTrue();
        assertThat(DemoUserBootstrap.needsBootstrap(DemoUserBootstrap.PLACEHOLDER)).isTrue();
        assertThat(DemoUserBootstrap.needsBootstrap("garbage-value")).isTrue();
    }

    @Test
    @DisplayName("FR-9: needsBootstrap(BCrypt-prefixed) → false")
    void needsBootstrap_returnsFalseForBcryptHashes() {
        assertThat(DemoUserBootstrap.needsBootstrap("$2a$10$ABC")).isFalse();
        assertThat(DemoUserBootstrap.needsBootstrap("$2b$10$ABC")).isFalse();
        assertThat(DemoUserBootstrap.needsBootstrap("$2y$10$ABC")).isFalse();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private static User mkUser(String userId, String name, User.Role role, String hash) {
        User u = new User();
        u.setUserId(userId);
        u.setName(name);
        u.setEmail(userId + "@example.com");
        u.setRole(role);
        u.setPasswordHash(hash);
        return u;
    }

    private void assertNoPlaintextLogged() {
        // At least one log line should have been emitted, and none may contain "1234".
        List<ILoggingEvent> events = appender.list;
        verify(repo, atLeastOnce()).findById(any(String.class));
        for (ILoggingEvent e : events) {
            assertThat(e.getFormattedMessage())
                    .as("plaintext password 1234 must not appear in log: %s",
                            e.getFormattedMessage())
                    .doesNotContain("1234");
        }
    }
}
