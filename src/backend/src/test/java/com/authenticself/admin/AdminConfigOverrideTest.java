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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Config override integration test (UC-03-admin-overview AC-42, AC-49).
 * <p>
 * Starts the application with {@code app.admin.top-colors-limit=3} via
 * {@link TestPropertySource}, seeds 5+ distinct colors in
 * {@code spaces.main_color}, and asserts that the
 * {@code mainColorTop5} array length in the {@code /admin/rooms}
 * response is at most 3.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "app.admin.top-colors-limit=3"
})
@DirtiesContext
class AdminConfigOverrideTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("authenticself")
            .withUsername("test")
            .withPassword("test")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("characterEncoding", "utf8mb4");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry r) {
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

    private static final String ADMIN_ID = "u_cfg_admin";

    @BeforeAll
    @Transactional
    void seed() {
        User admin = new User();
        admin.setUserId(ADMIN_ID);
        admin.setName("Admin");
        admin.setEmail("admin-cfg@example.com");
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);

        User u = new User();
        u.setUserId("u_cfg_regular");
        u.setName("U");
        u.setEmail("u-cfg@example.com");
        u.setRole(User.Role.USER);
        userRepository.save(u);

        // 5 distinct colors — the top-colors-limit=3 cap should cut this down to 3.
        String[] colors = { "#AAAAAA", "#BBBBBB", "#CCCCCC", "#DDDDDD", "#EEEEEE" };
        for (int i = 0; i < colors.length; i++) {
            em.createNativeQuery("INSERT INTO spaces " +
                    "(room_id, user_id, photo_url, content_type, file_size_bytes, " +
                    " status, uploaded_at, main_color) " +
                    "VALUES (:rid, 'u_cfg_regular', 'http://example.com/p.jpg', " +
                    " 'image/jpeg', 102400, 'ANALYZED', NOW(), :c)")
                    .setParameter("rid", "r_cfg_" + i)
                    .setParameter("c", colors[i])
                    .executeUpdate();
        }
    }

    @Test
    @DisplayName("AC-42 / AC-49: top-colors-limit=3 caps mainColorTop5 array at 3")
    void topColorsLimitOverride_ac42_ac49() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/admin/rooms?window=ALL")
                        .header("X-User-Id", ADMIN_ID))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        JsonNode arr = body.get("mainColorTop5");
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isLessThanOrEqualTo(3);
    }

    // Expose as both method names referenced in acceptance_criteria.json.
    @Test
    @DisplayName("AC-42 alias: topColorsLimitOverride")
    void topColorsLimitOverride_ac42() throws Exception {
        topColorsLimitOverride_ac42_ac49();
    }

    @Test
    @DisplayName("AC-49 alias: topColorsLimitOverride")
    void topColorsLimitOverride_ac49() throws Exception {
        topColorsLimitOverride_ac42_ac49();
    }
}
