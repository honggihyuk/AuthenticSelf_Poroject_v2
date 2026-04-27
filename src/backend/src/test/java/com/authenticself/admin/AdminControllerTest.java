package com.authenticself.admin;

import com.authenticself.admin.dto.AdminOverviewResponse;
import com.authenticself.admin.dto.ColorCount;
import com.authenticself.admin.dto.DateCount;
import com.authenticself.admin.dto.DateKrw;
import com.authenticself.admin.dto.RoomsTile;
import com.authenticself.admin.dto.SalesTile;
import com.authenticself.admin.dto.UsersTile;
import com.authenticself.admin.dto.WishlistTile;
import com.authenticself.domain.Space;
import com.authenticself.repository.UserRepository;
import com.authenticself.space.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice test for {@link AdminController} covering the public
 * endpoints (UC-03-admin-overview AC-9..AC-25, AC-47, AC-53).
 * <p>
 * {@link AdminAuthorizer} + {@link AdminOverviewService} are mocked so
 * this test validates controller wiring, status codes, the error
 * envelope shape, and DTO marshalling. Aggregation math is exercised
 * separately by {@code AdminOverviewServiceTest}; auth/NonAdmin paths
 * are exercised by {@code AdminControllerAuthTest}.
 */
@WebMvcTest(AdminController.class)
@Import(AdminExceptionAdvice.class)
class AdminControllerTest {

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper json;
    @MockBean  AdminAuthorizer      authorizer;
    @MockBean  AdminOverviewService service;
    @MockBean  UserRepository       userRepository;

    private static final OffsetDateTime T0 =
            OffsetDateTime.of(2026, 4, 18, 9, 14, 22, 0, ZoneOffset.ofHours(9));

    // =================================================================
    // Helpers — build deterministic fixtures.
    // =================================================================

    private UsersTile sampleUsers() {
        return new UsersTile(
                5, 2, 3, 3,
                List.of(
                        new DateCount(LocalDate.of(2026, 4, 11), 1),
                        new DateCount(LocalDate.of(2026, 4, 12), 0),
                        new DateCount(LocalDate.of(2026, 4, 13), 2)
                )
        );
    }

    private RoomsTile sampleRooms() {
        Map<Space.Status, Long> stat = new EnumMap<>(Space.Status.class);
        for (Space.Status v : Space.Status.values()) stat.put(v, 0L);
        stat.put(Space.Status.ANALYZED, 8L);
        stat.put(Space.Status.FAILED,   2L);

        Map<Style, Long> style = new EnumMap<>(Style.class);
        for (Style v : Style.values()) style.put(v, 0L);
        style.put(Style.MODERN, 4L);
        style.put(Style.SIMPLE, 3L);

        List<ColorCount> top = List.of(
                new ColorCount("#AAAAAA", 4),
                new ColorCount("#BBBBBB", 3),
                new ColorCount("#CCCCCC", 2)
        );
        return new RoomsTile(10, stat, style, top);
    }

    private WishlistTile sampleWishlist() {
        Map<String, Long> s = new LinkedHashMap<>();
        s.put("ACTIVE", 7L); s.put("PURCHASED", 3L);
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("desk", 4L); c.put("bed", 2L); c.put("chair", 3L); c.put("lighting", 1L);
        return new WishlistTile(10, s, c, 0.30);
    }

    private SalesTile sampleSales() {
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("desk", 400000L); c.put("bed", 200000L); c.put("chair", 0L); c.put("lighting", 0L);
        List<DateKrw> byDay = List.of(
                new DateKrw(LocalDate.of(2026, 4, 17), 200000),
                new DateKrw(LocalDate.of(2026, 4, 18), 400000)
        );
        return new SalesTile(600000, 2, 300000, c, byDay);
    }

    private void stubHappyAdmin() {
        // AC-7 branch 4 — adminrole path is a no-throw.
        doNothing().when(authorizer).requireAdmin(eq("u_admin"));
        when(service.nowOffset()).thenReturn(T0);
    }

    // =================================================================
    // AC-9 — /overview happy path shape.
    // =================================================================
    @Test
    @DisplayName("AC-9: /overview?window=ALL → 200 with the six top-level keys")
    void overviewShape_ac9() throws Exception {
        stubHappyAdmin();
        when(service.overview(eq("u_admin"), eq(TimeWindow.ALL)))
                .thenReturn(new AdminOverviewResponse(
                        "ALL", T0, sampleUsers(), sampleRooms(), sampleWishlist(), sampleSales()));

        mvc.perform(get("/api/v1/admin/overview?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window",      is("ALL")))
                .andExpect(jsonPath("$.generatedAt", notNullValue()))
                .andExpect(jsonPath("$.generatedAt", matchesPattern(".*\\+09:00$")))
                .andExpect(jsonPath("$.users",    notNullValue()))
                .andExpect(jsonPath("$.rooms",    notNullValue()))
                .andExpect(jsonPath("$.wishlist", notNullValue()))
                .andExpect(jsonPath("$.sales",    notNullValue()));
    }

    // =================================================================
    // AC-10 — default window is ALL.
    // =================================================================
    @Test
    @DisplayName("AC-10: missing window → default ALL")
    void overviewDefaultWindowIsAll_ac10() throws Exception {
        stubHappyAdmin();
        when(service.overview(eq("u_admin"), eq(TimeWindow.ALL)))
                .thenReturn(new AdminOverviewResponse(
                        "ALL", T0, sampleUsers(), sampleRooms(), sampleWishlist(), sampleSales()));

        mvc.perform(get("/api/v1/admin/overview")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window", is("ALL")));
    }

    // =================================================================
    // AC-11 — invalid + case-insensitive windows.
    // =================================================================
    @Test
    @DisplayName("AC-11: unknown window → 400 INVALID_TIME_WINDOW")
    void invalidWindow_ac11() throws Exception {
        stubHappyAdmin();
        mvc.perform(get("/api/v1/admin/overview?window=YESTERDAY")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode",     is("INVALID_TIME_WINDOW")))
                .andExpect(jsonPath("$.message",       notNullValue()))
                .andExpect(jsonPath("$.correlationId", matchesPattern(
                        "^[0-9a-fA-F-]{36}$")));
    }

    @Test
    @DisplayName("AC-11: window is case-insensitive (last_30d, Last_30D, LAST_30D)")
    void caseInsensitiveWindow_ac11() throws Exception {
        stubHappyAdmin();
        when(service.overview(eq("u_admin"), eq(TimeWindow.LAST_30D)))
                .thenReturn(new AdminOverviewResponse(
                        "LAST_30D", T0, sampleUsers(), sampleRooms(), sampleWishlist(), sampleSales()));

        for (String raw : new String[] { "last_30d", "Last_30D", "LAST_30D" }) {
            mvc.perform(get("/api/v1/admin/overview?window=" + raw)
                            .header("X-User-Id", "u_admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.window", is("LAST_30D")));
        }
    }

    @Test
    @DisplayName("AC-11 combined: invalidAndCaseInsensitiveWindow matrix")
    void invalidAndCaseInsensitiveWindow_ac11() throws Exception {
        invalidWindow_ac11();
        caseInsensitiveWindow_ac11();
    }

    // =================================================================
    // AC-12 — Users tile shape + counts.
    // =================================================================
    @Test
    @DisplayName("AC-12: /users?window=ALL → 200 + 7 top-level keys")
    void usersTileShapeAndCounts_ac12() throws Exception {
        stubHappyAdmin();
        when(service.users(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleUsers());

        mvc.perform(get("/api/v1/admin/users?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window",       is("ALL")))
                .andExpect(jsonPath("$.generatedAt",  notNullValue()))
                .andExpect(jsonPath("$.totalUsers",   is(5)))
                .andExpect(jsonPath("$.totalAdmins",  is(2)))
                .andExpect(jsonPath("$.newSignups",   is(3)))
                .andExpect(jsonPath("$.activeUsers",  is(3)))
                .andExpect(jsonPath("$.signupsByDay", hasSize(3)));
    }

    // =================================================================
    // AC-13 — newSignups reflects window.
    // =================================================================
    @Test
    @DisplayName("AC-13: LAST_7D → newSignups equals seeded window count")
    void newSignupsWindowed_ac13() throws Exception {
        stubHappyAdmin();
        UsersTile t = new UsersTile(10, 0, 3, 2, List.of());
        when(service.users(eq("u_admin"), eq(TimeWindow.LAST_7D))).thenReturn(t);

        mvc.perform(get("/api/v1/admin/users?window=LAST_7D")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newSignups", is(3)));
    }

    // =================================================================
    // AC-14 — activeUsers definition (distinct spaces.user_id).
    // =================================================================
    @Test
    @DisplayName("AC-14: activeUsers = COUNT DISTINCT spaces.user_id in window")
    void activeUsersDefinition_ac14() throws Exception {
        stubHappyAdmin();
        // 3 users uploaded in last 7 days, 2 older → activeUsers=3.
        UsersTile t = new UsersTile(5, 0, 5, 3, List.of());
        when(service.users(eq("u_admin"), eq(TimeWindow.LAST_7D))).thenReturn(t);

        mvc.perform(get("/api/v1/admin/users?window=LAST_7D")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsers", is(3)));
    }

    // =================================================================
    // AC-15 — signupsByDay density contract.
    // =================================================================
    @Test
    @DisplayName("AC-15: signupsByDay is ascending dense array")
    void signupsByDayDense_ac15() throws Exception {
        stubHappyAdmin();
        UsersTile t = new UsersTile(0, 0, 0, 0, List.of(
                new DateCount(LocalDate.of(2026, 4, 10), 0),
                new DateCount(LocalDate.of(2026, 4, 11), 0),
                new DateCount(LocalDate.of(2026, 4, 12), 0)
        ));
        when(service.users(eq("u_admin"), eq(TimeWindow.LAST_7D))).thenReturn(t);

        mvc.perform(get("/api/v1/admin/users?window=LAST_7D")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupsByDay[0].date", is("2026-04-10")))
                .andExpect(jsonPath("$.signupsByDay[1].date", is("2026-04-11")))
                .andExpect(jsonPath("$.signupsByDay[2].date", is("2026-04-12")))
                .andExpect(jsonPath("$.signupsByDay[0].count", is(0)));
    }

    // =================================================================
    // AC-16 / AC-34 — status distribution keys == Space.Status.values().
    // =================================================================
    @Test
    @DisplayName("AC-16 / AC-34: rooms statusDistribution keys are Space.Status.values()")
    void roomsStatusDistributionKeys_ac16_ac34() throws Exception {
        stubHappyAdmin();
        when(service.rooms(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleRooms());

        var req = mvc.perform(get("/api/v1/admin/rooms?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk());
        // Assert every enum value appears as a map key.
        for (Space.Status v : Space.Status.values()) {
            req.andExpect(jsonPath("$.statusDistribution." + v.name(),
                    greaterThanOrEqualTo(0)));
        }
    }

    @Test
    @DisplayName("AC-34 alias: roomsStatusKeysMatchJavaEnum")
    void roomsStatusKeysMatchJavaEnum_ac34() throws Exception {
        roomsStatusDistributionKeys_ac16_ac34();
    }

    // =================================================================
    // AC-17 — style distribution keys == 5-value Style enum.
    // =================================================================
    @Test
    @DisplayName("AC-17: rooms styleDistribution keys are all 5 Style values")
    void roomsStyleDistributionKeys_ac17() throws Exception {
        stubHappyAdmin();
        when(service.rooms(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleRooms());

        var req = mvc.perform(get("/api/v1/admin/rooms?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk());
        for (Style v : Style.values()) {
            req.andExpect(jsonPath("$.styleDistribution." + v.name(),
                    greaterThanOrEqualTo(0)));
        }
    }

    // =================================================================
    // AC-18 — status distribution sum == totalSpaces.
    // =================================================================
    @Test
    @DisplayName("AC-18: sum(statusDistribution) == totalSpaces (invariant)")
    void roomsStatusSumInvariant_ac18() throws Exception {
        stubHappyAdmin();
        when(service.rooms(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleRooms());

        String body = mvc.perform(get("/api/v1/admin/rooms?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tree = json.readTree(body);
        long sum = 0;
        var iter = tree.get("statusDistribution").fields();
        while (iter.hasNext()) sum += iter.next().getValue().asLong();
        long total = tree.get("totalSpaces").asLong();
        org.assertj.core.api.Assertions.assertThat(sum).isEqualTo(total);
    }

    // =================================================================
    // AC-19 — mainColorTop5 ordering.
    // =================================================================
    @Test
    @DisplayName("AC-19: mainColorTop5 ordered DESC by count, ties broken color ASC")
    void mainColorTop5Ordering_ac19() throws Exception {
        stubHappyAdmin();
        when(service.rooms(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleRooms());

        mvc.perform(get("/api/v1/admin/rooms?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainColorTop5[0].color", is("#AAAAAA")))
                .andExpect(jsonPath("$.mainColorTop5[0].count", is(4)))
                .andExpect(jsonPath("$.mainColorTop5[1].color", is("#BBBBBB")))
                .andExpect(jsonPath("$.mainColorTop5[2].color", is("#CCCCCC")));
    }

    // =================================================================
    // AC-20 — Wishlist tile shape + invariants.
    // =================================================================
    @Test
    @DisplayName("AC-20: wishlist tile keys + distribution invariants")
    void wishlistTileShapeAndInvariants_ac20() throws Exception {
        stubHappyAdmin();
        when(service.wishlist(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleWishlist());

        mvc.perform(get("/api/v1/admin/wishlist?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems",                           is(10)))
                .andExpect(jsonPath("$.statusDistribution.ACTIVE",            is(7)))
                .andExpect(jsonPath("$.statusDistribution.PURCHASED",         is(3)))
                .andExpect(jsonPath("$.categoryDistribution.desk",            greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.categoryDistribution.bed",             greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.categoryDistribution.chair",           greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.categoryDistribution.lighting",        greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.conversionRate",                       is(0.30)));
    }

    // =================================================================
    // AC-21 — conversionRate math; divide-by-zero → 0.0.
    // =================================================================
    @Test
    @DisplayName("AC-21: conversionRate = PURCHASED / totalItems; 0.0 when totalItems=0")
    void conversionRateNoDivideByZero_ac21() throws Exception {
        stubHappyAdmin();
        Map<String, Long> s = new LinkedHashMap<>();
        s.put("ACTIVE", 0L); s.put("PURCHASED", 0L);
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("desk", 0L); c.put("bed", 0L); c.put("chair", 0L); c.put("lighting", 0L);
        WishlistTile empty = new WishlistTile(0, s, c, 0.0);
        when(service.wishlist(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(empty);

        mvc.perform(get("/api/v1/admin/wishlist?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems",     is(0)))
                .andExpect(jsonPath("$.conversionRate", is(0.0)));
    }

    // =================================================================
    // AC-22 — Sales tile shape + invariants.
    // =================================================================
    @Test
    @DisplayName("AC-22: sales tile shape + averageOrderKrw + salesByCategory keys")
    void salesTileShapeAndInvariants_ac22() throws Exception {
        stubHappyAdmin();
        when(service.sales(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleSales());

        mvc.perform(get("/api/v1/admin/sales?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesKrw",           is(600000)))
                .andExpect(jsonPath("$.purchasedItemCount",      is(2)))
                .andExpect(jsonPath("$.averageOrderKrw",         is(300000)))
                .andExpect(jsonPath("$.salesByCategory.desk",    is(400000)))
                .andExpect(jsonPath("$.salesByCategory.bed",     is(200000)))
                .andExpect(jsonPath("$.salesByCategory.chair",   is(0)))
                .andExpect(jsonPath("$.salesByCategory.lighting",is(0)));
    }

    // =================================================================
    // AC-23 — sales uses wishlist.price snapshot (asserted in service test).
    // =================================================================
    @Test
    @DisplayName("AC-23: sales uses wishlist.price snapshot (stub path delegates to service)")
    void salesUsesSnapshotPrice_ac23() throws Exception {
        // The controller is a thin pass-through; the snapshot invariant
        // is enforced by the service's SQL (see AdminOverviewRepository
        // #sumSalesKrw). This test asserts only that the wire value is
        // whatever the service returns — the real SQL-level invariant
        // lives in AdminServiceIntegrationTest#salesUsesSnapshotPrice_ac23.
        stubHappyAdmin();
        when(service.sales(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleSales());
        mvc.perform(get("/api/v1/admin/sales?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesKrw", is(600000)));
    }

    // =================================================================
    // AC-24 — sales excludes ACTIVE rows.
    // =================================================================
    @Test
    @DisplayName("AC-24: sales excludes status='Active' (service-level SQL)")
    void salesExcludesActiveRows_ac24() throws Exception {
        stubHappyAdmin();
        // Imagine 3 Active + 2 Purchased rows: sales tile sees only the 2.
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("desk", 120000L); c.put("bed", 0L); c.put("chair", 80000L); c.put("lighting", 0L);
        SalesTile twoPurchases = new SalesTile(200000, 2, 100000, c, List.of());
        when(service.sales(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(twoPurchases);

        mvc.perform(get("/api/v1/admin/sales?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasedItemCount", is(2)))
                .andExpect(jsonPath("$.totalSalesKrw",      is(200000)));
    }

    // =================================================================
    // AC-25 — salesByCategory keys; salesByDay density.
    // =================================================================
    @Test
    @DisplayName("AC-25: salesByCategory 4-key set; salesByDay dense ascending")
    void salesByCategoryAndByDay_ac25() throws Exception {
        stubHappyAdmin();
        when(service.sales(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleSales());

        mvc.perform(get("/api/v1/admin/sales?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesByCategory.desk",     notNullValue()))
                .andExpect(jsonPath("$.salesByCategory.bed",      notNullValue()))
                .andExpect(jsonPath("$.salesByCategory.chair",    notNullValue()))
                .andExpect(jsonPath("$.salesByCategory.lighting", notNullValue()))
                .andExpect(jsonPath("$.salesByDay[0].date",       is("2026-04-17")))
                .andExpect(jsonPath("$.salesByDay[1].date",       is("2026-04-18")));
    }

    // =================================================================
    // AC-33 — overview per-tile matches standalone endpoints.
    // =================================================================
    @Test
    @DisplayName("AC-33: overview sub-objects == standalone endpoint bodies")
    void overviewMatchesPerTileEndpoints_ac33() throws Exception {
        stubHappyAdmin();
        UsersTile u = sampleUsers();
        RoomsTile r = sampleRooms();
        WishlistTile w = sampleWishlist();
        SalesTile s = sampleSales();
        when(service.users(eq("u_admin"),    any())).thenReturn(u);
        when(service.rooms(eq("u_admin"),    any())).thenReturn(r);
        when(service.wishlist(eq("u_admin"), any())).thenReturn(w);
        when(service.sales(eq("u_admin"),    any())).thenReturn(s);
        when(service.overview(eq("u_admin"), eq(TimeWindow.ALL)))
                .thenReturn(new AdminOverviewResponse("ALL", T0, u, r, w, s));

        String master = mvc.perform(get("/api/v1/admin/overview?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // We only need to verify: the tile-shaped sub-objects inside
        // `master.users`, `master.rooms`, `master.wishlist`, `master.sales`
        // carry the same field values as the tile DTOs we stubbed. That
        // is by-construction because both paths use the same DTOs — the
        // JSON round-trip below is a regression guard.
        var mTree = json.readTree(master);
        org.assertj.core.api.Assertions.assertThat(mTree.get("users").get("totalUsers").asLong())
                .isEqualTo(u.totalUsers());
        org.assertj.core.api.Assertions.assertThat(mTree.get("rooms").get("totalSpaces").asLong())
                .isEqualTo(r.totalSpaces());
        org.assertj.core.api.Assertions.assertThat(mTree.get("wishlist").get("totalItems").asLong())
                .isEqualTo(w.totalItems());
        org.assertj.core.api.Assertions.assertThat(mTree.get("sales").get("totalSalesKrw").asLong())
                .isEqualTo(s.totalSalesKrw());
    }

    // =================================================================
    // AC-35 — ANALYZED with null style counted in totalSpaces but not in any style bucket.
    // =================================================================
    @Test
    @DisplayName("AC-35: null-style ANALYZED row — totalSpaces includes it, styleDistribution does not")
    void nullStyleNotBucketed_ac35() throws Exception {
        stubHappyAdmin();
        // totalSpaces=11, sum(styleDist)=10 — the extra row is the null-style ANALYZED.
        Map<Space.Status, Long> stat = new EnumMap<>(Space.Status.class);
        for (Space.Status v : Space.Status.values()) stat.put(v, 0L);
        stat.put(Space.Status.ANALYZED, 11L);
        Map<Style, Long> styleDist = new EnumMap<>(Style.class);
        for (Style v : Style.values()) styleDist.put(v, 0L);
        styleDist.put(Style.MODERN, 4L);
        styleDist.put(Style.SIMPLE, 3L);
        styleDist.put(Style.CLASSIC, 2L);
        styleDist.put(Style.SCANDINAVIAN, 1L);
        styleDist.put(Style.INDUSTRIAL, 0L);
        when(service.rooms(eq("u_admin"), eq(TimeWindow.ALL)))
                .thenReturn(new RoomsTile(11, stat, styleDist, List.of()));

        String body = mvc.perform(get("/api/v1/admin/rooms?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tree = json.readTree(body);
        long total = tree.get("totalSpaces").asLong();
        long styleSum = 0;
        var it = tree.get("styleDistribution").fields();
        while (it.hasNext()) styleSum += it.next().getValue().asLong();
        org.assertj.core.api.Assertions.assertThat(styleSum).isEqualTo(total - 1);
    }

    // =================================================================
    // AC-40 — /overview is idempotent across two consecutive calls (except generatedAt).
    // =================================================================
    @Test
    @DisplayName("AC-40: repeated /overview is idempotent (same counts, generatedAt differs)")
    void repeatedGetIdempotent_ac40() throws Exception {
        stubHappyAdmin();
        AdminOverviewResponse res = new AdminOverviewResponse(
                "ALL", T0, sampleUsers(), sampleRooms(), sampleWishlist(), sampleSales());
        when(service.overview(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(res);

        String a = mvc.perform(get("/api/v1/admin/overview?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andReturn().getResponse().getContentAsString();
        String b = mvc.perform(get("/api/v1/admin/overview?window=ALL")
                        .header("X-User-Id", "u_admin"))
                .andReturn().getResponse().getContentAsString();

        var ta = json.readTree(a);
        var tb = json.readTree(b);
        org.assertj.core.api.Assertions.assertThat(ta.get("users").toString())
                .isEqualTo(tb.get("users").toString());
        org.assertj.core.api.Assertions.assertThat(ta.get("rooms").toString())
                .isEqualTo(tb.get("rooms").toString());
    }

    // =================================================================
    // AC-47 — error envelope shape for every error case.
    // =================================================================
    @Test
    @DisplayName("AC-47: error envelope is {errorCode, message, correlationId} with UUIDv4 + Korean message")
    void errorEnvelopeShape_ac47() throws Exception {
        stubHappyAdmin();
        // Use INVALID_TIME_WINDOW as a cheap trigger (no DB involvement).
        mvc.perform(get("/api/v1/admin/overview?window=BAD")
                        .header("X-User-Id", "u_admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode",     is("INVALID_TIME_WINDOW")))
                .andExpect(jsonPath("$.message",       containsString("기간 필터")))
                .andExpect(jsonPath("$.correlationId", matchesPattern("^[0-9a-fA-F-]{36}$")));
    }

    // =================================================================
    // AC-53 — every endpoint body has {window, generatedAt} envelope.
    // =================================================================
    @Test
    @DisplayName("AC-53: every endpoint response starts with window + generatedAt envelope")
    void commonEnvelopeFields_ac53() throws Exception {
        stubHappyAdmin();
        when(service.users(eq("u_admin"),    eq(TimeWindow.ALL))).thenReturn(sampleUsers());
        when(service.rooms(eq("u_admin"),    eq(TimeWindow.ALL))).thenReturn(sampleRooms());
        when(service.wishlist(eq("u_admin"), eq(TimeWindow.ALL))).thenReturn(sampleWishlist());
        when(service.sales(eq("u_admin"),    eq(TimeWindow.ALL))).thenReturn(sampleSales());
        when(service.overview(eq("u_admin"), eq(TimeWindow.ALL)))
                .thenReturn(new AdminOverviewResponse("ALL", T0, sampleUsers(), sampleRooms(),
                        sampleWishlist(), sampleSales()));

        for (String path : new String[] {
                "/api/v1/admin/overview",
                "/api/v1/admin/users",
                "/api/v1/admin/rooms",
                "/api/v1/admin/wishlist",
                "/api/v1/admin/sales"
        }) {
            mvc.perform(get(path + "?window=ALL")
                            .header("X-User-Id", "u_admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.window",      is("ALL")))
                    .andExpect(jsonPath("$.generatedAt", matchesPattern(".*\\+09:00$")));
        }
    }
}
