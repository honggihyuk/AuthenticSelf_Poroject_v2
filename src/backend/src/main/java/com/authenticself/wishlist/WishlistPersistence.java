package com.authenticself.wishlist;

import com.authenticself.domain.Furniture;
import com.authenticself.domain.Wishlist;
import com.authenticself.repository.FurnitureRepository;
import com.authenticself.repository.UserRepository;
import com.authenticself.repository.WishlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Narrow persistence collaborator for {@link WishlistService}
 * (UC-02-wishlist FR-3 / FR-4).
 * <p>
 * Lives in a SEPARATE bean so every mutation goes through the Spring AOP
 * proxy — same rationale as
 * {@link com.authenticself.ai.SpaceAnalysisPersistence} (Task-4 FR-12:
 * self-invocation within a service would bypass the proxy and defeat
 * {@code @Transactional}).
 *
 * <p>Every write method opens its OWN transaction ({@code REQUIRES_NEW}).
 * The wishlist surface has no outbound HTTP calls, but the separation is
 * kept so later orchestration (e.g. analytics side-effects) can be added
 * in the service without ever nesting inside a DB transaction.
 */
@Component
public class WishlistPersistence {

    private final WishlistRepository wishlists;
    private final FurnitureRepository furniture;
    private final UserRepository users;

    @PersistenceContext
    private EntityManager em;

    public WishlistPersistence(
            WishlistRepository wishlists,
            FurnitureRepository furniture,
            UserRepository users
    ) {
        this.wishlists = wishlists;
        this.furniture = furniture;
        this.users = users;
    }

    // ==================================================================
    // Existence checks — cheap read-only
    // ==================================================================
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean userExists(String userId) {
        return users.existsById(userId);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Furniture> findFurniture(String furnitureId) {
        return furniture.findById(furnitureId);
    }

    // ==================================================================
    // Reads — by (userId, furnitureId) or (wishlistId, userId)
    // ==================================================================
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Wishlist> findByUserIdAndFurnitureId(String userId, String furnitureId) {
        return wishlists.findByUserIdAndFurnitureId(userId, furnitureId);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Wishlist> findByWishlistIdAndUserId(String wishlistId, String userId) {
        return wishlists.findByWishlistIdAndUserId(wishlistId, userId);
    }

    // ==================================================================
    // List + counts — used by GET /api/v1/wishlist (FR-6)
    // ==================================================================

    /**
     * Snapshot of a user's list + per-state totals. Performed in a single
     * read-only transaction so the returned counts are internally
     * consistent with the returned rows.
     */
    public record ListSnapshot(List<Wishlist> rows, long totalActive, long totalPurchased) {}

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public ListSnapshot list(String userId, Wishlist.Status filter, int cap) {
        long totalActive    = wishlists.countByUserIdAndStatus(userId, Wishlist.Status.Active);
        long totalPurchased = wishlists.countByUserIdAndStatus(userId, Wishlist.Status.Purchased);

        List<Wishlist> rows;
        if (filter == null) {
            rows = wishlists.findAllByUserIdOrderByUpdatedAtDesc(userId);
        } else {
            rows = wishlists.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, filter);
        }

        // Stable tie-break on wishlist_id ASC for deterministic snapshots
        // when two rows share an updated_at (common in fast tests).
        rows = rows.stream()
                .sorted(Comparator
                        .comparing(Wishlist::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Wishlist::getWishlistId))
                .collect(Collectors.toList());

        if (rows.size() > cap) {
            rows = rows.subList(0, cap);
        }
        return new ListSnapshot(rows, totalActive, totalPurchased);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<String, Furniture> findFurnitureByIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<String, Furniture> out = new HashMap<>();
        for (Furniture f : furniture.findAllById(ids)) {
            out.put(f.getFurnitureId(), f);
        }
        return out;
    }

    // ==================================================================
    // Writes — add / update / delete. Each opens its own transaction.
    // ==================================================================

    /**
     * Insert a new Active wishlist row. Caller is responsible for building
     * the row with a fresh UUID + {@code status=Active}; DB defaults fill
     * {@code added_at}, {@code created_at}, {@code updated_at}.
     *
     * <p>Propagates {@link DataIntegrityViolationException} unchanged so
     * the service can translate it to the idempotent-hit 200 response.
     *
     * <p>After the INSERT we {@code flush()} + {@code findById()} so the
     * JPA-managed entity has the DB-populated {@code added_at /
     * created_at / updated_at} timestamps (which are mapped with
     * {@code insertable=false}). Without this refresh, the controller
     * response DTO would carry {@code addedAt=null} (AC-7 regression).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Wishlist insert(Wishlist newRow) {
        Wishlist saved = wishlists.saveAndFlush(newRow);
        // Force a reload so the DB-populated `added_at / created_at /
        // updated_at` fields (mapped with insertable=false) are
        // hydrated onto the managed entity. saveAndFlush alone leaves
        // them null.
        em.refresh(saved);
        return saved;
    }

    /**
     * Persist a status + purchasedAt transition against an existing row.
     * Always returns the updated row (re-fetched so the managed snapshot
     * is fresh for the response DTO).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Wishlist updateStatus(String wishlistId, Wishlist.Status newStatus,
                                 java.time.LocalDateTime purchasedAt) {
        Wishlist row = wishlists.findById(wishlistId)
                .orElseThrow(() -> new WishlistException(WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND));
        row.setStatus(newStatus);
        row.setPurchasedAt(purchasedAt);
        Wishlist saved = wishlists.saveAndFlush(row);
        em.refresh(saved);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteById(String wishlistId) {
        wishlists.deleteById(wishlistId);
    }

    /**
     * Re-load a row by id (used after a no-op PATCH so the response DTO
     * carries the original updated_at without the service mutating it).
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Wishlist> findById(String wishlistId) {
        return wishlists.findById(wishlistId);
    }
}
