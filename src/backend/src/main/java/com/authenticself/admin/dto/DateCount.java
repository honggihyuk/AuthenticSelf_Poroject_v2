package com.authenticself.admin.dto;

import java.time.LocalDate;

/**
 * One row of the {@code signupsByDay} dense array
 * (UC-03-admin-overview FR-5 / AC-15).
 * <p>
 * Serialised as {@code {"date":"2026-04-11","count":1}} via Jackson's
 * record-component support. Ordering is ascending by {@code date}.
 */
public record DateCount(LocalDate date, long count) {
}
