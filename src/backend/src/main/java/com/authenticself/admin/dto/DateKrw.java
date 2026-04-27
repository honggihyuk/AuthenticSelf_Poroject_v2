package com.authenticself.admin.dto;

import java.time.LocalDate;

/**
 * One row of the {@code salesByDay} dense array
 * (UC-03-admin-overview FR-8 / AC-25).
 * <p>
 * Serialised as {@code {"date":"2026-04-11","krw":240000}}. Ordering is
 * ascending by {@code date}. The {@code krw} field is the sum of
 * {@code wishlist.price} across rows whose {@code purchased_at} falls on
 * that calendar day (Asia/Seoul).
 */
public record DateKrw(LocalDate date, long krw) {
}
