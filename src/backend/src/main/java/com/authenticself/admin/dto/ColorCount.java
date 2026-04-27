package com.authenticself.admin.dto;

/**
 * One row of the {@code mainColorTop5} list
 * (UC-03-admin-overview FR-6 / AC-19).
 * <p>
 * Serialised as {@code {"color":"#E8D9B0","count":60}}. Ordering is
 * DESC by {@code count}, tie-broken ASC by {@code color} so the output
 * is deterministic for a fixed DB state (AC-50).
 */
public record ColorCount(String color, long count) {
}
