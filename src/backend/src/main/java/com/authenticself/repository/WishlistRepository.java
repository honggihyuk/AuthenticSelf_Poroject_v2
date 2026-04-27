package com.authenticself.repository;

import com.authenticself.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for the {@code wishlist} table (UC-02-wishlist FR-3 / AC-6).
 *
 * <p>Declares exactly three custom finders on top of the standard
 * {@link JpaRepository} surface:
 * <ol>
 *   <li>{@link #findByUserIdAndFurnitureId} — idempotency-on-add pre-check
 *       (FR-4). Paired with the {@code uq_wishlist_user_furniture} UNIQUE
 *       constraint (V6) which is the authoritative race-condition guard.</li>
 *   <li>{@link #findAllByUserIdAndStatusOrderByUpdatedAtDesc} — drives the
 *       filtered list endpoint (FR-6). {@code updated_at DESC} ordering
 *       plus {@code wishlist_id ASC} tie-break guarantees stable test
 *       snapshots.</li>
 *   <li>{@link #findByWishlistIdAndUserId} — ownership-gated lookup for
 *       PATCH / DELETE (FR-7 / FR-8). Collapses unknown-id and
 *       foreign-user-id to the same 404 response so the service never
 *       leaks existence of another user's rows (AC-26 / AC-28).</li>
 * </ol>
 *
 * <p>Deletion semantics: {@link #deleteById(Object)} from the parent
 * interface issues a standard {@code DELETE ... WHERE wishlist_id=?}. The
 * service always resolves the row via {@link #findByWishlistIdAndUserId}
 * first, so a delete never bypasses the ownership check.
 */
public interface WishlistRepository extends JpaRepository<Wishlist, String> {

    /**
     * Find a wishlist row by its {@code (user_id, furniture_id)} tuple.
     * <p>
     * Used by {@code WishlistService.add} as a fast-path check before the
     * INSERT. The authoritative concurrency guard is the
     * {@code uq_wishlist_user_furniture} UNIQUE constraint (V6); this
     * method avoids the redundant INSERT in the common case where the
     * pair is already present.
     */
    Optional<Wishlist> findByUserIdAndFurnitureId(String userId, String furnitureId);

    /**
     * List a user's wishlist filtered by status, newest first.
     * <p>
     * Tie-breaker on {@code wishlist_id ASC} is applied in the service
     * layer via a secondary sort — Spring Data JPA's method-name DSL
     * only supports a single {@code OrderBy} segment per derived query.
     */
    List<Wishlist> findAllByUserIdAndStatusOrderByUpdatedAtDesc(String userId, Wishlist.Status status);

    /**
     * Ownership-gated lookup used by PATCH / DELETE (FR-7 / FR-8).
     * <p>
     * Returning {@link Optional#empty()} collapses both "no such row"
     * and "row owned by a different user" cases, letting the service
     * translate both to a single 404 {@code WISHLIST_ITEM_NOT_FOUND}
     * response (AC-26 / AC-28 — no existence leak).
     */
    Optional<Wishlist> findByWishlistIdAndUserId(String wishlistId, String userId);

    /**
     * Count active-status rows for a user — used by the list endpoint's
     * {@code totalActive} / {@code totalPurchased} summary (FR-6 / AC-16).
     */
    long countByUserIdAndStatus(String userId, Wishlist.Status status);

    /**
     * Full list of rows for a user, newest first — used by the no-filter
     * branch of the list endpoint (FR-6).
     */
    List<Wishlist> findAllByUserIdOrderByUpdatedAtDesc(String userId);
}
