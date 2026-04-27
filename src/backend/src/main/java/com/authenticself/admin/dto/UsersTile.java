package com.authenticself.admin.dto;

import java.util.List;

/**
 * Users-tile response payload (UC-03-admin-overview FR-5 / AC-12..AC-15).
 * <p>
 * Keys:
 * <ul>
 *   <li>{@code totalUsers}  — {@code COUNT(*) FROM users WHERE role='USER'}
 *       (window-filtered on {@code users.created_at} for LAST_*).</li>
 *   <li>{@code totalAdmins} — {@code COUNT(*) FROM users WHERE role='ADMIN'}
 *       (window-filtered on {@code users.created_at} for LAST_*).</li>
 *   <li>{@code newSignups}  — {@code COUNT(*) FROM users WHERE created_at &gt;= windowStart}.</li>
 *   <li>{@code activeUsers} — {@code COUNT(DISTINCT user_id) FROM spaces
 *       WHERE uploaded_at &gt;= windowStart}.</li>
 *   <li>{@code signupsByDay} — dense ascending series; zero-count days
 *       included.</li>
 * </ul>
 */
public record UsersTile(
        long totalUsers,
        long totalAdmins,
        long newSignups,
        long activeUsers,
        List<DateCount> signupsByDay
) {
}
