package com.authenticself.wishlist.dto;

/**
 * Request body for {@code POST /api/v1/wishlist} (UC-02-wishlist FR-5).
 * <p>
 * All three fields are client-supplied so the service does not have to
 * re-query {@code furniture} just to echo back {@code type} / {@code price}.
 * This keeps the write path a single SELECT + single INSERT and preserves
 * historical price at add-time (FR-4 rationale — {@code wishlist.price} is
 * a snapshot, not a live reference).
 *
 * <p>Fields are boxed (not primitive) so Jackson emits a clean 400
 * {@code INVALID_WISHLIST_PAYLOAD} for an absent {@code price} rather than
 * silently binding zero.
 */
public record AddWishlistRequest(
        String  furnitureId,
        String  category,
        Integer price
) {
}
