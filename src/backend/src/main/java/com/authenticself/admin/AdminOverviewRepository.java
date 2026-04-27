package com.authenticself.admin;

import com.authenticself.admin.dto.ColorCount;
import com.authenticself.admin.dto.DateCount;
import com.authenticself.admin.dto.DateKrw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregation queries for the admin overview (UC-03-admin-overview FR-9 /
 * AC-30 / AC-31).
 * <p>
 * Every query uses native SQL (the {@code nativeQuery=true} flag on each
 * {@code @Query}) because:
 * <ul>
 *   <li>MySQL-specific {@code GROUP BY DATE(col)} is simpler as native SQL
 *       than as JPQL.</li>
 *   <li>All aggregations are read-only and cross-table; JPQL would require
 *       disjoint root entities and would not buy portability.</li>
 * </ul>
 *
 * <p>The {@code (:windowStart IS NULL OR col &gt;= :windowStart)} idiom is
 * used on every method that accepts a window-start parameter so the same
 * query body supports both the {@code ALL} (no-filter) and {@code LAST_*}
 * (filter) branches — AC-31 asserts this.
 *
 * <p>This interface formally extends {@link JpaRepository} over an
 * arbitrary aggregate root so Spring registers it as a repository proxy;
 * none of the inherited CRUD methods are ever called from production
 * code. The root is picked as the
 * {@link com.authenticself.domain.User} for convenience — a dedicated
 * empty aggregate would be ceremony with no benefit.
 */
@Repository
public interface AdminOverviewRepository extends JpaRepository<com.authenticself.domain.User, String> {

    // =================================================================
    // Users tile
    // =================================================================

    /**
     * Count {@code users} rows with a given role, optionally restricted
     * to rows created on/after {@code windowStart} (FR-5).
     */
    @Query(value =
            "SELECT COUNT(*) FROM users " +
            "WHERE role = :role " +
            "  AND (:windowStart IS NULL OR created_at >= :windowStart)",
            nativeQuery = true)
    long countUsersInRoleAndWindow(@Param("role") String role,
                                   @Param("windowStart") Instant windowStart);

    /**
     * Count new signups in the window — equivalent to
     * {@code countUsersInRoleAndWindow('USER', windowStart) +
     * countUsersInRoleAndWindow('ADMIN', windowStart)} for non-null window,
     * but expressed as a single query to satisfy FR-9 / AC-30 directly.
     */
    @Query(value =
            "SELECT COUNT(*) FROM users " +
            "WHERE (:windowStart IS NULL OR created_at >= :windowStart)",
            nativeQuery = true)
    long countNewSignups(@Param("windowStart") Instant windowStart);

    /**
     * Count distinct user ids that have a {@code spaces} row whose
     * {@code uploaded_at} falls in the window — the "active user"
     * definition per FR-5 / AC-14.
     */
    @Query(value =
            "SELECT COUNT(DISTINCT user_id) FROM spaces " +
            "WHERE (:windowStart IS NULL OR uploaded_at >= :windowStart)",
            nativeQuery = true)
    long countActiveUsers(@Param("windowStart") Instant windowStart);

    /**
     * Group user signups by calendar day (Asia/Seoul offset applied in
     * the service layer via {@code windowStart} / {@code windowEnd}
     * bounds). Callers densify the result into a zero-filled array.
     * <p>
     * Returns rows of {@code (date, count)} — mapped to
     * {@link DateCount} by the Spring Data interface-projection
     * mechanism.
     */
    @Query(value =
            "SELECT DATE(CONVERT_TZ(created_at, '+00:00', :tzOffset)) AS day, COUNT(*) AS cnt " +
            "FROM users " +
            "WHERE (:windowStart IS NULL OR created_at >= :windowStart) " +
            "GROUP BY day " +
            "ORDER BY day ASC",
            nativeQuery = true)
    List<Object[]> groupSignupsByDayRaw(@Param("windowStart") Instant windowStart,
                                        @Param("tzOffset")    String tzOffset);

    // =================================================================
    // Rooms tile
    // =================================================================

    @Query(value =
            "SELECT COUNT(*) FROM spaces " +
            "WHERE (:windowStart IS NULL OR uploaded_at >= :windowStart)",
            nativeQuery = true)
    long countSpaces(@Param("windowStart") Instant windowStart);

    /**
     * Returns rows of {@code (status, count)}; service layer pivots into
     * the enum-keyed map and fills zero buckets for missing statuses.
     */
    @Query(value =
            "SELECT status AS status_key, COUNT(*) AS cnt FROM spaces " +
            "WHERE (:windowStart IS NULL OR uploaded_at >= :windowStart) " +
            "GROUP BY status",
            nativeQuery = true)
    List<Object[]> countSpacesByStatusRaw(@Param("windowStart") Instant windowStart);

    /**
     * Returns rows of {@code (style, count)}; NULL styles are excluded
     * (a {@code WHERE style IS NOT NULL} predicate is applied) so
     * ANALYZED rows with no style do NOT appear in any bucket — matches
     * AC-35.
     */
    @Query(value =
            "SELECT style AS style_key, COUNT(*) AS cnt FROM spaces " +
            "WHERE style IS NOT NULL " +
            "  AND (:windowStart IS NULL OR uploaded_at >= :windowStart) " +
            "GROUP BY style",
            nativeQuery = true)
    List<Object[]> countSpacesByStyleRaw(@Param("windowStart") Instant windowStart);

    /**
     * Top-N main colors by count, tie-broken by color ASC. NULL colors
     * are excluded (FR-6 / AC-19).
     * <p>
     * {@code LIMIT} is bound via a parameter so the
     * {@code app.admin.top-colors-limit} override (AC-49) resolves
     * without SQL injection risk. MySQL accepts parameterised
     * {@code LIMIT :n} as of 8.0 with positional binding through
     * Hibernate.
     */
    @Query(value =
            "SELECT main_color AS color, COUNT(*) AS cnt FROM spaces " +
            "WHERE main_color IS NOT NULL " +
            "  AND (:windowStart IS NULL OR uploaded_at >= :windowStart) " +
            "GROUP BY main_color " +
            "ORDER BY cnt DESC, color ASC " +
            "LIMIT :lim",
            nativeQuery = true)
    List<Object[]> topMainColorsRaw(@Param("windowStart") Instant windowStart,
                                    @Param("lim") int limit);

    // =================================================================
    // Wishlist tile
    // =================================================================

    @Query(value =
            "SELECT COUNT(*) FROM wishlist " +
            "WHERE (:windowStart IS NULL OR added_at >= :windowStart)",
            nativeQuery = true)
    long countWishlist(@Param("windowStart") Instant windowStart);

    /**
     * Returns rows of {@code (dbStatus, count)} with DB casing
     * ({@code Active} / {@code Purchased}); service layer translates to
     * REST casing {@code ACTIVE} / {@code PURCHASED}.
     */
    @Query(value =
            "SELECT status AS status_key, COUNT(*) AS cnt FROM wishlist " +
            "WHERE (:windowStart IS NULL OR added_at >= :windowStart) " +
            "GROUP BY status",
            nativeQuery = true)
    List<Object[]> countWishlistByStatusRaw(@Param("windowStart") Instant windowStart);

    @Query(value =
            "SELECT category AS cat, COUNT(*) AS cnt FROM wishlist " +
            "WHERE (:windowStart IS NULL OR added_at >= :windowStart) " +
            "GROUP BY category",
            nativeQuery = true)
    List<Object[]> countWishlistByCategoryRaw(@Param("windowStart") Instant windowStart);

    // =================================================================
    // Sales tile
    // =================================================================

    /**
     * SUM of {@code wishlist.price} across PURCHASED rows with
     * {@code purchased_at} in the window. {@code COALESCE} ensures the
     * MySQL {@code SUM} of an empty set returns 0 rather than NULL.
     */
    @Query(value =
            "SELECT COALESCE(SUM(price), 0) FROM wishlist " +
            "WHERE status = 'Purchased' " +
            "  AND purchased_at IS NOT NULL " +
            "  AND (:windowStart IS NULL OR purchased_at >= :windowStart)",
            nativeQuery = true)
    long sumSalesKrw(@Param("windowStart") Instant windowStart);

    @Query(value =
            "SELECT COUNT(*) FROM wishlist " +
            "WHERE status = 'Purchased' " +
            "  AND purchased_at IS NOT NULL " +
            "  AND (:windowStart IS NULL OR purchased_at >= :windowStart)",
            nativeQuery = true)
    long countPurchasedItems(@Param("windowStart") Instant windowStart);

    @Query(value =
            "SELECT category AS cat, COALESCE(SUM(price), 0) AS krw FROM wishlist " +
            "WHERE status = 'Purchased' " +
            "  AND purchased_at IS NOT NULL " +
            "  AND (:windowStart IS NULL OR purchased_at >= :windowStart) " +
            "GROUP BY category",
            nativeQuery = true)
    List<Object[]> sumSalesByCategoryRaw(@Param("windowStart") Instant windowStart);

    @Query(value =
            "SELECT DATE(CONVERT_TZ(purchased_at, '+00:00', :tzOffset)) AS day, " +
            "       COALESCE(SUM(price), 0) AS krw " +
            "FROM wishlist " +
            "WHERE status = 'Purchased' " +
            "  AND purchased_at IS NOT NULL " +
            "  AND (:windowStart IS NULL OR purchased_at >= :windowStart) " +
            "GROUP BY day " +
            "ORDER BY day ASC",
            nativeQuery = true)
    List<Object[]> sumSalesByDayRaw(@Param("windowStart") Instant windowStart,
                                    @Param("tzOffset")    String tzOffset);

    /**
     * For the densification of the {@code salesByDay} series under
     * {@code window=ALL}: returns the earliest {@code purchased_at} as an
     * {@code Instant}, or {@code null} when no purchases exist.
     * <p>
     * Used by the service only — callers do not need to know this method
     * exists. It is declared here (rather than inline) to keep all admin
     * queries in a single file.
     */
    @Query(value =
            "SELECT MIN(purchased_at) FROM wishlist " +
            "WHERE status = 'Purchased' AND purchased_at IS NOT NULL",
            nativeQuery = true)
    Instant minPurchasedAt();

    /**
     * For the densification of the {@code signupsByDay} series under
     * {@code window=ALL}: earliest {@code users.created_at}, or
     * {@code null} when the table is empty.
     */
    @Query(value =
            "SELECT MIN(created_at) FROM users",
            nativeQuery = true)
    Instant minUserCreatedAt();
}
