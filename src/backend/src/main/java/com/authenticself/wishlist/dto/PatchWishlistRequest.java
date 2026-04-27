package com.authenticself.wishlist.dto;

/**
 * Request body for {@code PATCH /api/v1/wishlist/{wishlistId}}
 * (UC-02-wishlist FR-7). Accepts the upper-case REST enum
 * ({@code ACTIVE} / {@code PURCHASED}); anything else raises
 * {@code INVALID_STATE_TRANSITION} in the service.
 */
public record PatchWishlistRequest(
        String status
) {
}
