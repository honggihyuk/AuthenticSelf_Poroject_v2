package com.authenticself.auth;

import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the demo {@code admin} and {@code user} rows exist on every boot.
 *
 * <p>The login flow returns these {@code userId}s and the
 * {@link com.authenticself.admin.AdminAuthorizer} validates them against
 * the {@code users} table — so without these rows, every admin call would
 * 404 with {@code USER_NOT_FOUND}. Idempotent upsert: only inserts if the
 * row is missing, leaves existing rows untouched so a hand-edited role is
 * preserved across reboots.
 */
@Component
public class DemoUserBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserBootstrap.class);

    private final UserRepository userRepository;

    public DemoUserBootstrap(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensure("admin", "관리자", "admin@authenticself.local", User.Role.ADMIN);
        ensure("user",  "일반사용자", "user@authenticself.local",  User.Role.USER);
    }

    private void ensure(String userId, String name, String email, User.Role role) {
        if (userRepository.existsById(userId)) {
            log.info("demo user already present userId={}", userId);
            return;
        }
        User u = new User();
        u.setUserId(userId);
        u.setName(name);
        u.setEmail(email);
        u.setRole(role);
        userRepository.save(u);
        log.info("demo user inserted userId={} role={}", userId, role);
    }
}
