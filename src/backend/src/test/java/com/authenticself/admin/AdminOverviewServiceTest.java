package com.authenticself.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.authenticself.admin.dto.AdminOverviewResponse;
import com.authenticself.admin.dto.RoomsTile;
import com.authenticself.admin.dto.SalesTile;
import com.authenticself.admin.dto.UsersTile;
import com.authenticself.admin.dto.WishlistTile;
import com.authenticself.domain.Space;
import com.authenticself.space.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mockito-only unit test for {@link AdminOverviewService}
 * (UC-03-admin-overview AC-32, AC-40, AC-51 + several count /
 * distribution invariants).
 * <p>
 * The {@link AdminOverviewRepository} is mocked so every test focuses
 * on the Java-side math: map zero-filling, densification, divide-by-
 * zero conversion rate, log-line shape. Real SQL behaviour is
 * exercised in {@code AdminServiceIntegrationTest}.
 */
class AdminOverviewServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** Fixed "now" — matches the WindowResolverTest anchor for AC-32. */
    private static final Instant NOW_KST =
            LocalDate.of(2026, 4, 18).atStartOfDay(KST).toInstant();

    private AdminOverviewRepository repo;
    private WindowResolver          windowResolver;
    private AdminOverviewService    service;
    private Clock                   clock;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger                       serviceLogger;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW_KST, KST);
        repo  = mock(AdminOverviewRepository.class);
        windowResolver = new WindowResolver(clock, "Asia/Seoul");
        service = new AdminOverviewService(
                repo, windowResolver, clock, "Asia/Seoul", 5);

        // Attach a list appender to capture INFO logs for AC-51 assertions.
        serviceLogger = (Logger) LoggerFactory.getLogger(AdminOverviewService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
        serviceLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
    }

    // =================================================================
    // AC-32 — windowStart values (indirect via the resolver).
    // =================================================================
    @Test
    @DisplayName("AC-32: fixed clock → LAST_7D windowStart = 2026-04-11 KST")
    void fixedClockWindowMath_ac32() {
        Instant last7 = windowResolver.resolve(TimeWindow.LAST_7D);
        assertThat(last7).isEqualTo(
                LocalDate.of(2026, 4, 11).atStartOfDay(KST).toInstant());
        Instant last30 = windowResolver.resolve(TimeWindow.LAST_30D);
        assertThat(last30).isEqualTo(
                LocalDate.of(2026, 3, 19).atStartOfDay(KST).toInstant());
        assertThat(windowResolver.resolve(TimeWindow.ALL)).isNull();
    }

    // =================================================================
    // Users tile shape.
    // =================================================================
    @Test
    @DisplayName("Users tile zero-fills signupsByDay across the window")
    void usersTile_densifies() {
        when(repo.countUsersInRoleAndWindow(eq("USER"), isNull())).thenReturn(5L);
        when(repo.countUsersInRoleAndWindow(eq("ADMIN"), isNull())).thenReturn(2L);
        when(repo.countNewSignups(isNull())).thenReturn(7L);
        when(repo.countActiveUsers(isNull())).thenReturn(3L);
        when(repo.minUserCreatedAt()).thenReturn(
                LocalDate.of(2026, 4, 15).atStartOfDay(KST).toInstant());
        when(repo.groupSignupsByDayRaw(any(), anyString())).thenReturn(List.of(
                new Object[] { java.sql.Date.valueOf("2026-04-16"), 2L },
                new Object[] { java.sql.Date.valueOf("2026-04-18"), 5L }
        ));

        UsersTile t = service.users("u_admin", TimeWindow.ALL);
        assertThat(t.totalUsers()).isEqualTo(5);
        assertThat(t.totalAdmins()).isEqualTo(2);
        assertThat(t.newSignups()).isEqualTo(7);
        assertThat(t.activeUsers()).isEqualTo(3);
        // Dense: 2026-04-15 → 2026-04-18 inclusive = 4 days.
        assertThat(t.signupsByDay()).hasSize(4);
        assertThat(t.signupsByDay().get(0).date()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(t.signupsByDay().get(0).count()).isZero();
        assertThat(t.signupsByDay().get(1).count()).isEqualTo(2);
        assertThat(t.signupsByDay().get(2).count()).isZero();
        assertThat(t.signupsByDay().get(3).count()).isEqualTo(5);
    }

    // =================================================================
    // Rooms tile — zero-fills every Space.Status + Style key.
    // =================================================================
    @Test
    @DisplayName("Rooms tile fills zero buckets for every Space.Status + Style enum value")
    void roomsTile_zeroFills_andTopColors() {
        when(repo.countSpaces(isNull())).thenReturn(10L);
        when(repo.countSpacesByStatusRaw(isNull())).thenReturn(List.of(
                new Object[] { "ANALYZED",        8L },
                new Object[] { "FAILED",          2L }
        ));
        when(repo.countSpacesByStyleRaw(isNull())).thenReturn(List.of(
                new Object[] { "MODERN", 4L },
                new Object[] { "SIMPLE", 3L }
        ));
        when(repo.topMainColorsRaw(isNull(), eq(5))).thenReturn(List.of(
                new Object[] { "#AAAAAA", 4L },
                new Object[] { "#BBBBBB", 3L }
        ));

        RoomsTile r = service.rooms("u_admin", TimeWindow.ALL);
        for (Space.Status v : Space.Status.values()) {
            assertThat(r.statusDistribution()).containsKey(v);
        }
        for (Style v : Style.values()) {
            assertThat(r.styleDistribution()).containsKey(v);
        }
        // Sum of statusDistribution equals totalSpaces (AC-18 invariant at unit level).
        long statSum = r.statusDistribution().values().stream().mapToLong(Long::longValue).sum();
        assertThat(statSum).isEqualTo(r.totalSpaces());
        // Sum of styleDistribution is at most totalSpaces (AC-17 / AC-35).
        long styleSum = r.styleDistribution().values().stream().mapToLong(Long::longValue).sum();
        assertThat(styleSum).isLessThanOrEqualTo(r.totalSpaces());
        // Top colors preserve SQL order.
        assertThat(r.mainColorTop5().get(0).color()).isEqualTo("#AAAAAA");
    }

    // =================================================================
    // Wishlist tile — conversionRate math.
    // =================================================================
    @Test
    @DisplayName("Wishlist tile conversionRate = PURCHASED / totalItems, 2 dp")
    void wishlist_conversionRateRounding() {
        when(repo.countWishlist(isNull())).thenReturn(100L);
        when(repo.countWishlistByStatusRaw(isNull())).thenReturn(List.of(
                new Object[] { "Active",    70L },
                new Object[] { "Purchased", 30L }
        ));
        when(repo.countWishlistByCategoryRaw(isNull())).thenReturn(List.of(
                new Object[] { "desk", 40L },
                new Object[] { "chair", 60L }
        ));

        WishlistTile w = service.wishlist("u_admin", TimeWindow.ALL);
        assertThat(w.totalItems()).isEqualTo(100);
        assertThat(w.statusDistribution().get("ACTIVE")).isEqualTo(70L);
        assertThat(w.statusDistribution().get("PURCHASED")).isEqualTo(30L);
        assertThat(w.categoryDistribution()).containsKeys("desk", "bed", "chair", "lighting");
        assertThat(w.categoryDistribution().get("bed")).isZero();
        assertThat(w.conversionRate()).isEqualTo(0.30);
    }

    @Test
    @DisplayName("Wishlist tile conversionRate = 0.0 when totalItems = 0 (AC-21)")
    void wishlist_conversionRateDivideByZero_ac21() {
        when(repo.countWishlist(isNull())).thenReturn(0L);
        when(repo.countWishlistByStatusRaw(isNull())).thenReturn(List.of());
        when(repo.countWishlistByCategoryRaw(isNull())).thenReturn(List.of());

        WishlistTile w = service.wishlist("u_admin", TimeWindow.ALL);
        assertThat(w.totalItems()).isZero();
        assertThat(w.conversionRate()).isEqualTo(0.0);
        // Sum of statusDistribution values == totalItems (= 0).
        long sum = w.statusDistribution().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(w.totalItems());
    }

    // =================================================================
    // Sales tile — averageOrderKrw handling.
    // =================================================================
    @Test
    @DisplayName("Sales tile averageOrderKrw = round(total / count); 0 when count=0")
    void sales_averageRoundingAndZero() {
        // Happy scenario: 5 purchases totalling 1,000,000 KRW → avg=200,000.
        when(repo.sumSalesKrw(isNull())).thenReturn(1_000_000L);
        when(repo.countPurchasedItems(isNull())).thenReturn(5L);
        when(repo.sumSalesByCategoryRaw(isNull())).thenReturn(List.of(
                new Object[] { "desk", 600_000L },
                new Object[] { "bed",  400_000L }
        ));
        when(repo.sumSalesByDayRaw(any(), anyString())).thenReturn(List.of());
        when(repo.minPurchasedAt()).thenReturn(null);

        SalesTile s = service.sales("u_admin", TimeWindow.ALL);
        assertThat(s.totalSalesKrw()).isEqualTo(1_000_000);
        assertThat(s.purchasedItemCount()).isEqualTo(5);
        assertThat(s.averageOrderKrw()).isEqualTo(200_000);
        // Sum of salesByCategory equals totalSalesKrw.
        long catSum = s.salesByCategory().values().stream().mapToLong(Long::longValue).sum();
        assertThat(catSum).isEqualTo(s.totalSalesKrw());

        // Edge: no purchases.
        when(repo.sumSalesKrw(isNull())).thenReturn(0L);
        when(repo.countPurchasedItems(isNull())).thenReturn(0L);
        when(repo.sumSalesByCategoryRaw(isNull())).thenReturn(List.of());
        SalesTile empty = service.sales("u_admin", TimeWindow.ALL);
        assertThat(empty.averageOrderKrw()).isZero();
        assertThat(empty.totalSalesKrw()).isZero();
    }

    // =================================================================
    // AC-51 — exactly one INFO log line per operation.
    // =================================================================
    @Test
    @DisplayName("AC-51: one INFO line per op (op=... window=... userId=... durationMs=...)")
    void logsShape_ac51() {
        stubOverviewRepo();
        service.overview("u_admin", TimeWindow.ALL);

        var events = new ArrayList<>(logAppender.list);
        events.removeIf(e -> e.getLevel() != Level.INFO);
        assertThat(events).hasSize(1);
        String msg = events.get(0).getFormattedMessage();
        assertThat(msg).matches(
                "op=(overview|users|rooms|wishlist|sales) " +
                "window=(LAST_7D|LAST_30D|ALL) " +
                "userId=\\S+ durationMs=\\d+");
        // AC-41 — no PII (email / name) in log line.
        assertThat(msg.toLowerCase()).doesNotContain("@example.com");
    }

    // =================================================================
    // AC-40 — same input → same response (modulo generatedAt).
    // =================================================================
    @Test
    @DisplayName("AC-40: two consecutive calls are byte-identical except generatedAt")
    void overviewIdempotent_ac40() {
        stubOverviewRepo();
        AdminOverviewResponse a = service.overview("u_admin", TimeWindow.ALL);
        AdminOverviewResponse b = service.overview("u_admin", TimeWindow.ALL);

        assertThat(a.users()).isEqualTo(b.users());
        assertThat(a.rooms()).isEqualTo(b.rooms());
        assertThat(a.wishlist()).isEqualTo(b.wishlist());
        assertThat(a.sales()).isEqualTo(b.sales());
        assertThat(a.window()).isEqualTo(b.window());
        // generatedAt is derived from the same fixed clock, so it matches
        // here too — not an AC requirement, but a useful determinism check.
        assertThat(a.generatedAt()).isEqualTo(b.generatedAt());
    }

    // =================================================================
    // Helpers
    // =================================================================

    private void stubOverviewRepo() {
        when(repo.countUsersInRoleAndWindow(anyString(), any())).thenReturn(3L);
        when(repo.countNewSignups(any())).thenReturn(3L);
        when(repo.countActiveUsers(any())).thenReturn(2L);
        when(repo.groupSignupsByDayRaw(any(), anyString())).thenReturn(List.of());
        when(repo.minUserCreatedAt()).thenReturn(null);

        when(repo.countSpaces(any())).thenReturn(0L);
        when(repo.countSpacesByStatusRaw(any())).thenReturn(List.of());
        when(repo.countSpacesByStyleRaw(any())).thenReturn(List.of());
        when(repo.topMainColorsRaw(any(), anyInt())).thenReturn(List.of());

        when(repo.countWishlist(any())).thenReturn(0L);
        when(repo.countWishlistByStatusRaw(any())).thenReturn(List.of());
        when(repo.countWishlistByCategoryRaw(any())).thenReturn(List.of());

        when(repo.sumSalesKrw(any())).thenReturn(0L);
        when(repo.countPurchasedItems(any())).thenReturn(0L);
        when(repo.sumSalesByCategoryRaw(any())).thenReturn(List.of());
        when(repo.sumSalesByDayRaw(any(), anyString())).thenReturn(List.of());
        when(repo.minPurchasedAt()).thenReturn(null);
    }
}
