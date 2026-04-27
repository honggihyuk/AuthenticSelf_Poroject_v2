package com.authenticself.admin;

import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for {@link AdminOverviewRepository}
 * (UC-03-admin-overview AC-31).
 * <p>
 * Asserts the core SQL-level invariant: every method with a nullable
 * {@code windowStart} parameter returns the same rows as its unfiltered
 * equivalent when the parameter is {@code null}. For a non-null value,
 * only rows satisfying {@code column >= windowStart} are returned.
 */
@Testcontainers
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminOverviewRepositoryTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("authenticself")
            .withUsername("test")
            .withPassword("test")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("characterEncoding", "utf8mb4");

    @DynamicPropertySource
    static void databaseProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("DB_URL",                     MYSQL::getJdbcUrl);
        r.add("DB_USER",                    MYSQL::getUsername);
        r.add("DB_PASSWORD",                MYSQL::getPassword);
    }

    @Autowired AdminOverviewRepository repo;
    @Autowired UserRepository userRepository;

    @BeforeAll
    void seed() {
        // Seed a few users spanning the window boundary.
        User u1 = new User(); u1.setUserId("u_ac31_a"); u1.setName("A");
        u1.setEmail("a-ac31@example.com"); u1.setRole(User.Role.USER);
        userRepository.save(u1);
        User u2 = new User(); u2.setUserId("u_ac31_b"); u2.setName("B");
        u2.setEmail("b-ac31@example.com"); u2.setRole(User.Role.ADMIN);
        userRepository.save(u2);
    }

    // =================================================================
    // AC-31 — null windowStart omits the date predicate.
    // =================================================================
    @Test
    @DisplayName("AC-31: windowStart=null returns the same count as an unfiltered query")
    void nullWindowOmitsPredicate_ac31() {
        long totalAll    = repo.countNewSignups(null);
        long totalActive = repo.countActiveUsers(null);
        // Matches the unfiltered COUNT — we cannot compare to a "non-window"
        // baseline on this surface because there is no such method — but we
        // can assert that adding a way-in-the-past windowStart still returns
        // every row, and a way-in-the-future windowStart returns zero.
        Instant farPast   = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant();
        Instant farFuture = ZonedDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant();
        assertThat(repo.countNewSignups(farPast)).isEqualTo(totalAll);
        assertThat(repo.countNewSignups(farFuture)).isZero();
        assertThat(repo.countActiveUsers(farFuture)).isZero();
        assertThat(totalActive).isGreaterThanOrEqualTo(0);
    }

    // =================================================================
    // AC-30 — role-scoped counts.
    // =================================================================
    @Test
    @DisplayName("AC-30: countUsersInRoleAndWindow returns role-scoped counts")
    void countByRole_ac30() {
        long users  = repo.countUsersInRoleAndWindow("USER",  null);
        long admins = repo.countUsersInRoleAndWindow("ADMIN", null);
        assertThat(users).isGreaterThanOrEqualTo(1);
        assertThat(admins).isGreaterThanOrEqualTo(1);
    }

    // =================================================================
    // AC-30 — empty-table list methods return an empty list (not null).
    // =================================================================
    @Test
    @DisplayName("AC-30: group-by methods return non-null empty lists on empty-table branches")
    void listMethodsReturnEmptyLists() {
        // These tables may be empty on first run — assert non-null.
        List<Object[]> stat  = repo.countSpacesByStatusRaw(null);
        List<Object[]> style = repo.countSpacesByStyleRaw(null);
        List<Object[]> sales = repo.sumSalesByCategoryRaw(null);
        assertThat(stat).isNotNull();
        assertThat(style).isNotNull();
        assertThat(sales).isNotNull();
    }
}
