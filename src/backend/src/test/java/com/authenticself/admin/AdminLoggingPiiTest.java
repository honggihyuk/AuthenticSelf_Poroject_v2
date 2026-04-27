package com.authenticself.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.authenticself.domain.User;
import com.authenticself.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logging-PII regression test (UC-03-admin-overview AC-41).
 * <p>
 * Seeds a user with a known email / name, sweeps every admin endpoint,
 * and asserts that the captured log lines from {@link AdminController},
 * {@link AdminOverviewService}, and {@link AdminAuthorizer} do NOT
 * contain the email or name substring.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class AdminLoggingPiiTest {

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
    @Autowired UserRepository userRepository;

    private ListAppender<ILoggingEvent> appender;
    private Logger controllerLog;
    private Logger serviceLog;
    private Logger authorizerLog;

    private static final String ADMIN_ID = "u_pii_admin";
    private static final String SEEDED_EMAIL = "pii-victim@example.com";
    private static final String SEEDED_NAME  = "PiiVictim";

    @BeforeAll
    void setUp() {
        User admin = new User();
        admin.setUserId(ADMIN_ID);
        admin.setName(SEEDED_NAME);
        admin.setEmail(SEEDED_EMAIL);
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);

        appender = new ListAppender<>();
        appender.start();
        controllerLog = (Logger) LoggerFactory.getLogger(AdminController.class);
        serviceLog    = (Logger) LoggerFactory.getLogger(AdminOverviewService.class);
        authorizerLog = (Logger) LoggerFactory.getLogger(AdminAuthorizer.class);
        controllerLog.addAppender(appender); controllerLog.setLevel(Level.DEBUG);
        serviceLog.addAppender(appender);    serviceLog.setLevel(Level.DEBUG);
        authorizerLog.addAppender(appender); authorizerLog.setLevel(Level.DEBUG);
    }

    @AfterAll
    void tearDown() {
        controllerLog.detachAppender(appender);
        serviceLog.detachAppender(appender);
        authorizerLog.detachAppender(appender);
    }

    @Test
    @DisplayName("AC-41: no log line emitted by admin classes contains email or name")
    void noEmailOrNameInLogs_ac41() throws Exception {
        for (String p : new String[] {
                "/api/v1/admin/overview",
                "/api/v1/admin/users",
                "/api/v1/admin/rooms",
                "/api/v1/admin/wishlist",
                "/api/v1/admin/sales"
        }) {
            mvc.perform(get(p + "?window=ALL").header("X-User-Id", ADMIN_ID))
                    .andExpect(status().isOk());
        }

        for (ILoggingEvent e : appender.list) {
            String msg = e.getFormattedMessage();
            assertThat(msg)
                    .as("AC-41: log line must not contain seeded email")
                    .doesNotContain(SEEDED_EMAIL);
            assertThat(msg)
                    .as("AC-41: log line must not contain seeded name")
                    .doesNotContain(SEEDED_NAME);
        }
    }
}
