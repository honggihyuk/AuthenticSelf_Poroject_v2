package com.authenticself.wishlist.dto;

import com.authenticself.domain.Furniture;
import com.authenticself.domain.Wishlist;

import java.time.LocalDateTime;

/**
 * Single wishlist item emitted by {@code POST /api/v1/wishlist},
 * {@code GET /api/v1/wishlist} and {@code PATCH /api/v1/wishlist/{id}}.
 * <p>
 * Shape matches §7 of the spec: the V1 wishlist columns plus the V6
 * timestamps plus a {@code furnitureSnapshot} joined from the current
 * {@code furniture} row (nullable — a dangling row whose catalog item was
 * removed is still listable so the user can delete it).
 *
 * <p>{@code status} keeps the DB casing ({@code Active} / {@code Purchased})
 * exactly — the RN client reads both the DB casing on the response and
 * the UPPER_SNAKE casing on the PATCH request body. This mirrors the
 * spec §7 table and AC-7 / AC-22.
 *
 * <p>{@code alreadyExists} is a response-only flag emitted by POST; the
 * GET + PATCH paths always serialise it as {@code false} so the schema
 * is uniform. Jackson preserves the boolean ordering below.
 */
public record WishlistItemResponse(
        String        wishlistId,
        String        userId,
        String        furnitureId,
        String        category,
        Integer       price,
        String        status,
        LocalDateTime addedAt,
        LocalDateTime purchasedAt,
        FurnitureSnapshot furnitureSnapshot,
        boolean       alreadyExists
) {

    /**
     * Read-only subset of a {@link Furniture} row rendered on wishlist
     * cards. {@code imageUrl} is nullable (catalog row may have it as
     * NULL); the whole snapshot is {@code null} if the catalog row has
     * been expunged for the wishlist row.
     */
    public record FurnitureSnapshot(
            String name,
            String imageUrl,
            String colorHex,
            String type
    ) {
        public static FurnitureSnapshot from(Furniture f) {
            if (f == null) return null;
            return new FurnitureSnapshot(
                    f.getName(),
                    f.getImageUrl(),
                    f.getColorHex(),
                    f.getType()
            );
        }
    }

    /**
     * Build a response DTO from a persisted {@link Wishlist} row + an
     * optional {@link Furniture} snapshot.
     */
    public static WishlistItemResponse of(Wishlist w, Furniture f, boolean alreadyExists) {
        return new WishlistItemResponse(
                w.getWishlistId(),
                w.getUserId(),
                w.getFurnitureId(),
                w.getCategory(),
                w.getPrice(),
                w.getStatus() != null ? w.getStatus().name() : null,
                w.getAddedAt(),
                w.getPurchasedAt(),
                FurnitureSnapshot.from(f),
                alreadyExists
        );
    }
}
