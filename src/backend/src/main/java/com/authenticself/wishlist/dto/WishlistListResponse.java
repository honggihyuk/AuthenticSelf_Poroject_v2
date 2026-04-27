package com.authenticself.wishlist.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/v1/wishlist} (UC-02-wishlist FR-6).
 * <p>
 * {@code truncated} is emitted only when the service capped the list at
 * {@code app.wishlist.max-items-per-user} (AC-21). Otherwise the field is
 * {@code false}.
 */
public record WishlistListResponse(
        List<WishlistItemResponse> items,
        int                        totalActive,
        int                        totalPurchased,
        boolean                    truncated
) {
}
