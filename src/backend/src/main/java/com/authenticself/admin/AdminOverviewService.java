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
import com.authenticself.domain.User;
import com.authenticself.space.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the four admin-tile payloads from
 * {@link AdminOverviewRepository} results (UC-03-admin-overview FR-10 /
 * AC-32 / AC-33 / AC-51).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Resolve {@link TimeWindow} into a repository-ready {@link Instant}
 *       via {@link WindowResolver} (AC-32).</li>
 *   <li>Run one SQL query per aggregate; no caching (D-3); no background
 *       job. JPA's auto-generated read-only transaction wraps each call.</li>
 *   <li>Pivot {@code List&lt;Object[]&gt;} rows into the enum-keyed /
 *       string-keyed maps that the DTOs expose, filling zero-count
 *       buckets for every FR-defined key set.</li>
 *   <li>Densify "by day" arrays across the full window (zero-filled
 *       days included — AC-15 / AC-25).</li>
 *   <li>Emit exactly one INFO log line per handled operation (AC-51);
 *       never log email / name (AC-41).</li>
 * </ul>
 *
 * <p>TODO(UC-03-admin-overview upgrade path, D-3): wrap each tile call
 * in a 60-second TTL cache keyed by {@code (windowKey, tile)} once the
 * NFR latency bound is exceeded on a realistic dataset. Ticket:
 * {@code UC-03-admin-overview/caching}.
 */
@Service
public class AdminOverviewService {

    private static final Logger log = LoggerFactory.getLogger(AdminOverviewService.class);

    private final AdminOverviewRepository repo;
    private final WindowResolver          windowResolver;
    private final Clock                   clock;
    private final ZoneId                  zoneId;
    private final int                     topColorsLimit;

    public AdminOverviewService(
            AdminOverviewRepository repo,
            WindowResolver windowResolver,
            Clock clock,
            @Value("${app.admin.time-zone:Asia/Seoul}") String zoneIdKey,
            @Value("${app.admin.top-colors-limit:5}") int topColorsLimit
    ) {
        this.repo = repo;
        this.windowResolver = windowResolver;
        this.clock = clock;
        this.zoneId = ZoneId.of(zoneIdKey);
        this.topColorsLimit = topColorsLimit;
    }

    // =================================================================
    // Public entry points
    // =================================================================

    public AdminOverviewResponse overview(String userId, TimeWindow window) {
        long t0 = System.currentTimeMillis();
        Instant ws = windowResolver.resolve(window);
        UsersTile    u = buildUsersTile(ws);
        RoomsTile    r = buildRoomsTile(ws);
        WishlistTile w = buildWishlistTile(ws);
        SalesTile    s = buildSalesTile(ws);
        AdminOverviewResponse body = new AdminOverviewResponse(
                window.name(), nowOffset(), u, r, w, s);
        logInfo("overview", window, userId, System.currentTimeMillis() - t0);
        return body;
    }

    public UsersTile users(String userId, TimeWindow window) {
        long t0 = System.currentTimeMillis();
        Instant ws = windowResolver.resolve(window);
        UsersTile out = buildUsersTile(ws);
        logInfo("users", window, userId, System.currentTimeMillis() - t0);
        return out;
    }

    public RoomsTile rooms(String userId, TimeWindow window) {
        long t0 = System.currentTimeMillis();
        Instant ws = windowResolver.resolve(window);
        RoomsTile out = buildRoomsTile(ws);
        logInfo("rooms", window, userId, System.currentTimeMillis() - t0);
        return out;
    }

    public WishlistTile wishlist(String userId, TimeWindow window) {
        long t0 = System.currentTimeMillis();
        Instant ws = windowResolver.resolve(window);
        WishlistTile out = buildWishlistTile(ws);
        logInfo("wishlist", window, userId, System.currentTimeMillis() - t0);
        return out;
    }

    public SalesTile sales(String userId, TimeWindow window) {
        long t0 = System.currentTimeMillis();
        Instant ws = windowResolver.resolve(window);
        SalesTile out = buildSalesTile(ws);
        logInfo("sales", window, userId, System.currentTimeMillis() - t0);
        return out;
    }

    /** Current wall-clock moment at the configured zone (AC-53). */
    public OffsetDateTime nowOffset() {
        return OffsetDateTime.ofInstant(Instant.now(clock), zoneId);
    }

    // =================================================================
    // Tile builders
    // =================================================================

    private UsersTile buildUsersTile(Instant ws) {
        long totalUsers  = repo.countUsersInRoleAndWindow(User.Role.USER.name(),  ws);
        long totalAdmins = repo.countUsersInRoleAndWindow(User.Role.ADMIN.name(), ws);
        long newSignups  = repo.countNewSignups(ws);
        long activeUsers = repo.countActiveUsers(ws);

        List<DateCount> signupsByDay = densifyDateCount(
                repo.groupSignupsByDayRaw(ws, zoneOffsetString()),
                ws,
                repo.minUserCreatedAt());

        return new UsersTile(totalUsers, totalAdmins, newSignups, activeUsers, signupsByDay);
    }

    private RoomsTile buildRoomsTile(Instant ws) {
        long totalSpaces = repo.countSpaces(ws);

        // statusDistribution — zero-fill every Space.Status value (AC-16 / AC-34).
        Map<Space.Status, Long> statusDist = new EnumMap<>(Space.Status.class);
        for (Space.Status v : Space.Status.values()) statusDist.put(v, 0L);
        for (Object[] row : repo.countSpacesByStatusRaw(ws)) {
            Space.Status key = parseSpaceStatus(asString(row[0]));
            if (key != null) {
                statusDist.put(key, statusDist.getOrDefault(key, 0L) + asLong(row[1]));
            }
        }

        // styleDistribution — zero-fill every Style value (AC-17).
        Map<Style, Long> styleDist = new EnumMap<>(Style.class);
        for (Style v : Style.values()) styleDist.put(v, 0L);
        for (Object[] row : repo.countSpacesByStyleRaw(ws)) {
            Style key = Style.fromNullable(asString(row[0]));
            if (key != null) {
                styleDist.put(key, styleDist.getOrDefault(key, 0L) + asLong(row[1]));
            }
        }

        // top-N main colors — already sorted by the SQL query.
        List<ColorCount> topColors = new ArrayList<>();
        for (Object[] row : repo.topMainColorsRaw(ws, topColorsLimit)) {
            topColors.add(new ColorCount(asString(row[0]), asLong(row[1])));
        }

        return new RoomsTile(totalSpaces, statusDist, styleDist, topColors);
    }

    private WishlistTile buildWishlistTile(Instant ws) {
        long total = repo.countWishlist(ws);

        // statusDistribution — translate DB casing to REST UPPER_SNAKE;
        // seed both keys with zero so the output map is stable.
        Map<String, Long> statusDist = new LinkedHashMap<>();
        statusDist.put("ACTIVE",    0L);
        statusDist.put("PURCHASED", 0L);
        for (Object[] row : repo.countWishlistByStatusRaw(ws)) {
            String dbKey = asString(row[0]);
            String key = "Purchased".equalsIgnoreCase(dbKey) ? "PURCHASED" : "ACTIVE";
            statusDist.put(key, statusDist.getOrDefault(key, 0L) + asLong(row[1]));
        }

        // categoryDistribution — zero-fill the 4-value set.
        Map<String, Long> catDist = new LinkedHashMap<>();
        for (String c : CATEGORY_KEYS) catDist.put(c, 0L);
        for (Object[] row : repo.countWishlistByCategoryRaw(ws)) {
            String key = asString(row[0]);
            if (key != null && catDist.containsKey(key)) {
                catDist.put(key, catDist.getOrDefault(key, 0L) + asLong(row[1]));
            }
        }

        double conversionRate = 0.0;
        if (total > 0) {
            long purchased = statusDist.getOrDefault("PURCHASED", 0L);
            conversionRate = BigDecimal.valueOf((double) purchased / total)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return new WishlistTile(total, statusDist, catDist, conversionRate);
    }

    private SalesTile buildSalesTile(Instant ws) {
        long totalKrw   = repo.sumSalesKrw(ws);
        long count      = repo.countPurchasedItems(ws);
        long averageKrw = count == 0 ? 0 : Math.round((double) totalKrw / count);

        Map<String, Long> byCat = new LinkedHashMap<>();
        for (String c : CATEGORY_KEYS) byCat.put(c, 0L);
        for (Object[] row : repo.sumSalesByCategoryRaw(ws)) {
            String key = asString(row[0]);
            if (key != null && byCat.containsKey(key)) {
                byCat.put(key, byCat.getOrDefault(key, 0L) + asLong(row[1]));
            }
        }

        List<DateKrw> byDay = densifyDateKrw(
                repo.sumSalesByDayRaw(ws, zoneOffsetString()),
                ws,
                repo.minPurchasedAt());

        return new SalesTile(totalKrw, count, averageKrw, byCat, byDay);
    }

    // =================================================================
    // Densification helpers
    // =================================================================

    /**
     * For {@code LAST_*} windows: dense range is {@code [windowStart, today]}.
     * For {@code ALL}: dense range is {@code [min(created_at), today]} or
     * empty when no users exist (FR-5 / AC-15).
     */
    private List<DateCount> densifyDateCount(List<Object[]> raw,
                                             Instant windowStart,
                                             Instant fallbackEarliest) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Object[] row : raw) {
            LocalDate d = asLocalDate(row[0]);
            long cnt    = asLong(row[1]);
            if (d != null) byDate.put(d, cnt);
        }
        LocalDate from;
        LocalDate to = windowResolver.today();
        if (windowStart != null) {
            from = windowStart.atZone(zoneId).toLocalDate();
        } else if (fallbackEarliest != null) {
            from = fallbackEarliest.atZone(zoneId).toLocalDate();
        } else {
            return List.of();  // window=ALL, empty table → empty array
        }
        List<DateCount> out = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            out.add(new DateCount(cursor, byDate.getOrDefault(cursor, 0L)));
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    private List<DateKrw> densifyDateKrw(List<Object[]> raw,
                                         Instant windowStart,
                                         Instant fallbackEarliest) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Object[] row : raw) {
            LocalDate d = asLocalDate(row[0]);
            long krw    = asLong(row[1]);
            if (d != null) byDate.put(d, krw);
        }
        LocalDate from;
        LocalDate to = windowResolver.today();
        if (windowStart != null) {
            from = windowStart.atZone(zoneId).toLocalDate();
        } else if (fallbackEarliest != null) {
            from = fallbackEarliest.atZone(zoneId).toLocalDate();
        } else {
            return List.of();
        }
        List<DateKrw> out = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            out.add(new DateKrw(cursor, byDate.getOrDefault(cursor, 0L)));
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    // =================================================================
    // Utilities
    // =================================================================

    /** Keys for both {@code categoryDistribution} and {@code salesByCategory}. */
    private static final List<String> CATEGORY_KEYS = List.of("desk", "bed", "chair", "lighting");

    /**
     * MySQL {@code CONVERT_TZ} wants a {@code '+09:00'}-style string. The
     * service keeps this as a single source of truth so every query that
     * does timezone-aware day grouping stays aligned.
     */
    private String zoneOffsetString() {
        ZoneOffset off = zoneId.getRules().getOffset(Instant.now(clock));
        return off.getId();
    }

    private static Space.Status parseSpaceStatus(String raw) {
        if (raw == null) return null;
        try {
            return Space.Status.valueOf(raw);
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static LocalDate asLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDate d)     return d;
        if (v instanceof Timestamp t)     return t.toLocalDateTime().toLocalDate();
        if (v instanceof String s)        return LocalDate.parse(s);
        throw new IllegalStateException("Unsupported date type: " + v.getClass());
    }

    /**
     * Emit exactly one INFO log line per handled operation (AC-51). No
     * PII: only the opaque {@code userId} is included (AC-41).
     */
    private void logInfo(String op, TimeWindow window, String userId, long durationMs) {
        log.info("op={} window={} userId={} durationMs={}",
                op, window.name(), userId, durationMs);
    }
}
