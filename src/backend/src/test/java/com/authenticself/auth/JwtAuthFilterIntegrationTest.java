package com.authenticself.auth;

import com.authenticself.test.AuthTestHelper;
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
 * Full-stack integration test for {@link JwtAuthFilter} covering
 * UC-SECURE-AUTH AC-17 — a valid Bearer + matching X-User-Id reaches
 * the controller; a missing Bearer is rejected with 401 INVALID_TOKEN.
 *
 * <p>Requires Testcontainers — excluded from the
 * {@code -PskipTestcontainers=true} fast-CI mode.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "app.auth.jwt.secret=test-secret-please-replace-in-production-32chars",
        "app.cors.allowed-origins=http://localhost:8082"
})
class JwtAuthFilterIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("authenticself")
            .withUsername("test")
            .withPassword("test")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true");

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
    @Autowired AuthTestHelper auth;

    @PersistenceContext
    EntityManager em;

    @BeforeAll
    void seed() {
        // DemoUserBootstrap has already seeded the 'user' row on context start.
        // Seed a single space owned by 'user' so we can exercise the happy path.
        em.createNativeQuery("INSERT INTO spaces " +
                "(room_id, user_id, photo_url, content_type, file_size_bytes, " +
                " status, uploaded_at, style, main_color) " +
                "VALUES " +
                "('r_jwt_int', 'user', 'http://example.com/p.jpg', " +
                " 'image/jpeg', 102400, 'ANALYZED', NOW(), 'MODERN', '#AAAAAA')")
                .executeUpdate();
    }

    // -----------------------------------------------------------------
    // AC-17 — authorized request passes.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-17: valid Bearer + matching X-User-Id → 200 from /api/v1/spaces/{roomId}")
    @Transactional
    void authorizedRequestPasses_ac17() throws Exception {
        var headers = auth.authHeaders("user", "USER");
        MvcResult r = mvc.perform(get("/api/v1/spaces/{roomId}", "r_jwt_int")
                        .header("Authorization", headers.get("Authorization"))
                        .header("X-User-Id",     headers.get("X-User-Id")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("roomId").asText()).isEqualTo("r_jwt_int");
    }

    // -----------------------------------------------------------------
    // AC-17 — missing Authorization → 401 INVALID_TOKEN.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-17: no Authorization header → 401 INVALID_TOKEN")
    void unauthorizedRequestRejected_ac17() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/spaces/{roomId}", "r_jwt_int")
                        .header("X-User-Id", "user"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("errorCode").asText()).isEqualTo("INVALID_TOKEN");
    }
}
