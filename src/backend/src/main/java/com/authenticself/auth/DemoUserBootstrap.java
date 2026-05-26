package com.authenticself.auth;

import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Ensures the demo {@code admin} and {@code user} rows exist on every boot
 * AND that each carries a BCrypt-hashed password (UC-SECURE-AUTH FR-9 /
 * AC-9 / FR-BC-4).
 *
 * <p>The login flow returns these {@code userId}s and the
 * {@link com.authenticself.admin.AdminAuthorizer} validates them against
 * the {@code users} table — so without these rows, every admin call
 * would 404 with {@code USER_NOT_FOUND}.
 *
 * <p>Idempotent on two axes:
 * <ol>
 *   <li><strong>Row existence</strong>: only inserts if {@code userId} is
 *       missing.</li>
 *   <li><strong>Password hash</strong>: overwrites the {@code password_hash}
 *       column when it is {@code null}, blank, or equal to the V11
 *       placeholder {@code __BOOTSTRAP_PLACEHOLDER__}. If the column
 *       already holds a valid BCrypt hash (prefix {@code $2a$} /
 *       {@code $2b$} / {@code $2y$}), the bootstrap MUST NOT overwrite
 *       it — operator-set passwords survive restarts (FR-BC-4 / AC-9).</li>
 * </ol>
 *
 * <p>Security: the plaintext default password ({@code "1234"}) is never
 * logged at any level. The bootstrap emits at most one INFO line per row.
 */
@Component
public class DemoUserBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserBootstrap.class);

    /** Placeholder written by V11 migration; treated as "needs bootstrap". */
    static final String PLACEHOLDER = "__BOOTSTRAP_PLACEHOLDER__";

    /** Plaintext password seeded for the demo rows. NEVER logged. */
    private static final String DEFAULT_PASSWORD = "1234";

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUserBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensure("admin", "관리자", "admin@authenticself.local", User.Role.ADMIN);
        ensure("user",  "일반사용자", "user@authenticself.local",  User.Role.USER);
    }

    private void ensure(String userId, String name, String email, User.Role role) {
        Optional<User> existing = userRepository.findById(userId);
        if (existing.isPresent()) {
            User u = existing.get();
            String hash = u.getPasswordHash();
            if (needsBootstrap(hash)) {
                u.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
                userRepository.save(u);
                log.info("demo user password bootstrapped userId={}", userId);
            } else {
                log.info("demo user already present userId={}", userId);
            }
            return;
        }
        User u = new User();
        u.setUserId(userId);
        u.setName(name);
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        userRepository.save(u);
        log.info("demo user inserted userId={} role={}", userId, role);
    }

    /**
     * A password_hash column needs a bootstrap-time overwrite when it is
     * null, blank, or the V11 placeholder. A value that already starts
     * with one of the canonical BCrypt prefixes is left untouched
     * (FR-BC-4 — operator-set passwords survive restarts).
     */
    static boolean needsBootstrap(String hash) {
        if (hash == null || hash.isBlank())   return true;
        if (PLACEHOLDER.equals(hash))         return true;
        if (hash.startsWith("$2a$"))          return false;
        if (hash.startsWith("$2b$"))          return false;
        if (hash.startsWith("$2y$"))          return false;
        // Anything else (corrupted / unknown scheme) — rebuild.
        return true;
    }
}
