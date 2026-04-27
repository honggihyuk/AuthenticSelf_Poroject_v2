package com.authenticself.admin;

import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Admin-role gate for the {@code /api/v1/admin/*} surface
 * (UC-03-admin-overview FR-3 / AC-7 / AC-8 / AC-26 / AC-27).
 *
 * <p>Every admin controller handler MUST call {@link #requireAdmin(String)}
 * as its first statement — verified by {@code AdminControllerAuthTest}
 * (AC-8). The three failure branches are raised in the exact order below
 * so the caller always sees the most specific error first:
 * <ol>
 *   <li>{@code null} / blank header &rarr; {@link AdminErrorCode#MISSING_USER_HEADER} (400).</li>
 *   <li>Header refers to an unknown user &rarr; {@link AdminErrorCode#USER_NOT_FOUND} (404).</li>
 *   <li>User exists but role &ne; {@link User.Role#ADMIN} &rarr; {@link AdminErrorCode#NOT_ADMIN} (403).</li>
 * </ol>
 *
 * <p>Implemented as a Spring {@link Component} bean (not a static helper)
 * because (a) it depends on a managed {@link UserRepository}, and (b) a
 * Mockito spy on the bean is the cleanest way to satisfy AC-8's
 * "requireAdmin invoked exactly once per request" assertion.
 *
 * <p>NO PII logging — only the opaque {@code userId} is ever emitted in
 * log lines; email / name never are (AC-41).
 */
@Component
public class AdminAuthorizer {

    private final UserRepository userRepository;

    public AdminAuthorizer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Throw {@link AdminException} unless the caller is a valid admin
     * user. See class Javadoc for the precise failure order.
     *
     * @param userIdHeader raw value of the {@code X-User-Id} header
     * @throws AdminException with one of {@link AdminErrorCode#MISSING_USER_HEADER},
     *     {@link AdminErrorCode#USER_NOT_FOUND}, or
     *     {@link AdminErrorCode#NOT_ADMIN}.
     */
    public void requireAdmin(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new AdminException(AdminErrorCode.MISSING_USER_HEADER);
        }
        Optional<User> user = userRepository.findById(userIdHeader);
        if (user.isEmpty()) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
        User.Role role = user.get().getRole();
        if (role != User.Role.ADMIN) {
            throw new AdminException(AdminErrorCode.NOT_ADMIN);
        }
    }
}
