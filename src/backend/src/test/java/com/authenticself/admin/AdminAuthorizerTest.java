package com.authenticself.admin;

import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito-only unit test for {@link AdminAuthorizer}
 * (UC-03-admin-overview AC-7).
 * <p>
 * Verifies the three-stage failure order (missing header → unknown user
 * → not-admin) plus the happy path. The order matters: a {@code null}
 * header must never reach the repository (that would hit the DB
 * unnecessarily), and an unknown user must never be probed for a role.
 */
class AdminAuthorizerTest {

    @Test
    @DisplayName("AC-7 branch 1: null header → MISSING_USER_HEADER (400); repo never called")
    void nullHeader_ac7() {
        UserRepository repo = mock(UserRepository.class);
        AdminAuthorizer auth = new AdminAuthorizer(repo);

        assertThatThrownBy(() -> auth.requireAdmin(null))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.MISSING_USER_HEADER));

        verify(repo, never()).findById(any(String.class));
    }

    @Test
    @DisplayName("AC-7 branch 1: blank header → MISSING_USER_HEADER")
    void blankHeader_ac7() {
        UserRepository repo = mock(UserRepository.class);
        AdminAuthorizer auth = new AdminAuthorizer(repo);

        assertThatThrownBy(() -> auth.requireAdmin("   "))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.MISSING_USER_HEADER));
        verify(repo, never()).findById(any(String.class));
    }

    @Test
    @DisplayName("AC-7 branch 2: unknown user id → USER_NOT_FOUND (404)")
    void unknownUser_ac7() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById("u_nope")).thenReturn(Optional.empty());
        AdminAuthorizer auth = new AdminAuthorizer(repo);

        assertThatThrownBy(() -> auth.requireAdmin("u_nope"))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("AC-7 branch 3: role=USER → NOT_ADMIN (403)")
    void nonAdminRole_ac7() {
        UserRepository repo = mock(UserRepository.class);
        User u = new User();
        u.setUserId("u_user");
        u.setRole(User.Role.USER);
        when(repo.findById("u_user")).thenReturn(Optional.of(u));
        AdminAuthorizer auth = new AdminAuthorizer(repo);

        assertThatThrownBy(() -> auth.requireAdmin("u_user"))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.NOT_ADMIN));
    }

    @Test
    @DisplayName("AC-7 branch 4: role=ADMIN → returns normally (no throw)")
    void adminRole_ac7_fourBranches() {
        UserRepository repo = mock(UserRepository.class);
        User u = new User();
        u.setUserId("u_admin");
        u.setRole(User.Role.ADMIN);
        when(repo.findById("u_admin")).thenReturn(Optional.of(u));
        AdminAuthorizer auth = new AdminAuthorizer(repo);

        assertThatCode(() -> auth.requireAdmin("u_admin"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-7 alias: single consolidated fourBranches scenario")
    void fourBranches_ac7() {
        // Same coverage as the four tests above, packed into one so the
        // AC-7 "fourBranches_ac7" method-name target in the spec
        // resolves to a real test method by identity.
        UserRepository repo = mock(UserRepository.class);
        AdminAuthorizer auth = new AdminAuthorizer(repo);

        // 1) null
        assertThatThrownBy(() -> auth.requireAdmin(null))
                .isInstanceOf(AdminException.class);
        // 2) unknown
        when(repo.findById("u_unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.requireAdmin("u_unknown"))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.USER_NOT_FOUND));
        // 3) user role
        User regular = new User();
        regular.setUserId("u_r"); regular.setRole(User.Role.USER);
        when(repo.findById("u_r")).thenReturn(Optional.of(regular));
        assertThatThrownBy(() -> auth.requireAdmin("u_r"))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.NOT_ADMIN));
        // 4) admin role
        User adm = new User();
        adm.setUserId("u_a"); adm.setRole(User.Role.ADMIN);
        when(repo.findById("u_a")).thenReturn(Optional.of(adm));
        assertThatCode(() -> auth.requireAdmin("u_a")).doesNotThrowAnyException();
    }
}
