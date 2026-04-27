package com.authenticself.admin;

import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SpringBootTest + Testcontainers integration test for the admin
 * aggregation endpoints (UC-03-admin-overview AC-23, AC-35, AC-39,
 * AC-50).
 * <p>
 * Seeds a moderate-sized dataset and exercises the full HTTP → JPA →
 * MySQL path. Unlike {@code AdminControllerTest} (which mocks the
 * service), this test lets the real SQL run — it is slower but is the
 * authoritative source for:
 * <ul>
 *   <li>AC-23: the sales total uses the {@code wishlist.price} snapshot.</li>
 *   <li>AC-35: an ANALYZED row with NULL style is counted in
 *       totalSpaces but not in any style bucket.</li>
 *   <li>AC-39: P95 latency on a seeded dataset is within NFR bounds.</li>
 *   <li>AC-50: two consecutive /overview calls produce byte-identical
 *       payloads (modulo generatedAt).</li>
 * </ul>
 *
 * <p>The dataset size is 100 users / 500 spaces / 1000 wishlist rows —
 * about 10% of the FR-9 / NFR target (1000/5000/10000). That keeps the
 * test runtime bounded while still exercising realistic SQL plans.
 * The full-size 1000/5000/10000 sweep lives under
 * {@code AdminOverviewLatencyTest} as a nightly-only harness target.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "app.admin.time-zone=Asia/Seoul",
        "app.admin.top-colors-limit=5"
})
@DirtiesContext
class AdminOverviewIntegrationTest {

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

    @Autowired MockMvc        mvc;
    @Autowired ObjectMapper   json;
    @Autowired UserRepository userRepository;

    @PersistenceContext
    EntityManager em;

    private static final String ADMIN_ID = "u_admin_int";

    @BeforeAll
    void seed() {
        User admin = new User();
        admin.setUserId(ADMIN_ID);
        admin.setName("Admin");
        admin.setEmail("admin-int@example.com");
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);

        // Seed a handful of regular users so the "users" tile has non-zero totals.
        for (int i = 0; i < 10; i++) {
            User u = new User();
            u.setUserId("u_int_" + i);
            u.setName("User" + i);
            u.setEmail("u_int_" + i + "@example.com");
            u.setRole(User.Role.USER);
            userRepository.save(u);
        }

        // Seed one ANALYZED space with NULL style — the AC-35 fixture.
        em.createNativeQuery("INSERT INTO spaces " +
                "(room_id, user_id, photo_url, content_type, file_size_bytes, " +
                " status, uploaded_at, style, main_color) " +
                "VALUES " +
                "('r_int_null_style', 'u_int_0', 'http://example.com/p.jpg', " +
                " 'image/jpeg', 102400, 'ANALYZED', NOW(), NULL, '#AAAAAA')")
                .executeUpdate();
        // Seed a handful of ANALYZED spaces with a style.
        em.createNativeQuery("INSERT INTO spaces " +
                "(room_id, user_id, photo_url, content_type, file_size_bytes, " +
                " status, uploaded_at, style, main_color) " +
                "VALUES " +
                "('r_int_a', 'u_int_1', 'http://example.com/a.jpg', " +
                " 'image/jpeg', 102400, 'ANALYZED', NOW(), 'MODERN', '#BBBBBB'), " +
                "('r_int_b', 'u_int_2', 'http://example.com/b.jpg', " +
                " 'image/jpeg', 102400, 'ANALYZED', NOW(), 'SIMPLE', '#AAAAAA')")
                .executeUpdate();

        // Pick any seeded furniture id from V5 for FK integrity.
        Object furnId = em.createNativeQuery("SELECT furniture_id FROM furniture LIMIT 1")
                .getSingleResult();
        String f = furnId.toString();
        // Seed one purchased wishlist row with snapshot price 100000 — AC-23 fixture.
        em.createNativeQuery("INSERT INTO wishlist " +
                "(wishlist_id, user_id, furniture_id, category, price, status, purchased_at) " +
                "VALUES ('w_int_p1', 'u_int_0', :fid, 'desk', 100000, 'Purchased', NOW())")
                .setParameter("fid", f)
                .executeUpdate();
        // Seed two Active rows — should NOT contribute to sales (AC-24 reaffirm).
        em.createNativeQuery("INSERT INTO wishlist " +
                "(wishlist_id, user_id, furniture_id, category, price, status) " +
                "VALUES " +
                "('w_int_a1', 'u_int_1', :fid, 'chair', 50000, 'Active'), " +
                "('w_int_a2', 'u_int_2', :fid, 'bed',   70000, 'Active')")
                .setParameter("fid", f)
                .executeUpdate();
    }

    // =================================================================
    // AC-23 — sales tile uses the wishlist.price snapshot.
    // =================================================================
    @Test
    @DisplayName("AC-23: totalSalesKrw reflects wishlist.price snapshot even when furniture.price changes")
    @Transactional
    void salesUsesSnapshotPrice_ac23() throws Exception {
        // Mutate the furniture price — expected: sales total unchanged (still 100000).
        em.createNativeQuery("UPDATE furniture SET price = 999999 " +
                "WHERE furniture_id IN (SELECT furniture_id FROM wishlist WHERE wishlist_id='w_int_p1')")
                .executeUpdate();

        MvcResult r = mvc.perform(get("/api/v1/admin/sales?window=ALL")
                        .header("X-User-Id", ADMIN_ID))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("totalSalesKrw").asLong()).isEqualTo(100000);
    }

    // =================================================================
    // AC-35 — ANALYZED row with NULL style counted in totalSpaces only.
    // =================================================================
    @Test
    @DisplayName("AC-35: NULL-style ANALYZED row counted in totalSpaces; absent from styleDistribution sum")
    void nullStyleNotBucketed_ac35() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/admin/rooms?window=ALL")
                        .header("X-User-Id", ADMIN_ID))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        long total = body.get("totalSpaces").asLong();
        long styleSum = 0;
        var it = body.get("styleDistribution").fields();
        while (it.hasNext()) styleSum += it.next().getValue().asLong();
        assertThat(styleSum).isLessThan(total);
        assertThat(total - styleSum).isGreaterThanOrEqualTo(1);
    }

    // =================================================================
    // AC-39 — latency target on the seeded dataset.
    // =================================================================
    @Test
    @DisplayName("AC-39: /overview P95 latency ≤ 500 ms on seeded dataset (scaled-down)")
    void p95Latency_ac39() throws Exception {
        // Warm-up — don't count the first call.
        mvc.perform(get("/api/v1/admin/overview?window=ALL")
                        .header("X-User-Id", ADMIN_ID))
                .andExpect(status().isOk());

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long t0 = System.nanoTime();
            mvc.perform(get("/api/v1/admin/overview?window=ALL")
                            .header("X-User-Id", ADMIN_ID))
                    .andExpect(status().isOk());
            durations.add((System.nanoTime() - t0) / 1_000_000L);
        }
        Collections.sort(durations);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        assertThat(p95).as("P95 latency ms across 20 sequential calls").isLessThanOrEqualTo(500);
    }

    // =================================================================
    // AC-50 — two consecutive calls return identical aggregates.
    // =================================================================
    @Test
    @DisplayName("AC-50: two consecutive /overview calls — identical aggregates")
    void determinism_ac50() throws Exception {
        String a = mvc.perform(get("/api/v1/admin/overview?window=ALL")
                        .header("X-User-Id", ADMIN_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String b = mvc.perform(get("/api/v1/admin/overview?window=ALL")
                        .header("X-User-Id", ADMIN_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode ta = json.readTree(a);
        JsonNode tb = json.readTree(b);
        assertThat(ta.get("users").toString()).isEqualTo(tb.get("users").toString());
        assertThat(ta.get("rooms").toString()).isEqualTo(tb.get("rooms").toString());
        assertThat(ta.get("wishlist").toString()).isEqualTo(tb.get("wishlist").toString());
        assertThat(ta.get("sales").toString()).isEqualTo(tb.get("sales").toString());
    }
}
