package com.authenticself.wishlist;

import com.authenticself.domain.Furniture;
import com.authenticself.domain.Wishlist;
import com.authenticself.wishlist.dto.AddWishlistRequest;
import com.authenticself.wishlist.dto.WishlistItemResponse;
import com.authenticself.wishlist.dto.WishlistListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates wishlist writes / reads for the public REST surface
 * (UC-02-wishlist FR-4 .. FR-8, FR-10, FR-11).
 * <p>
 * Stateless bean: all mutable state lives in MySQL, reached via
 * {@link WishlistPersistence}. The service only:
 * <ul>
 *   <li>validates the request payload (FR-4 / FR-5);</li>
 *   <li>resolves idempotency (FR-4 — existing row or UNIQUE-constraint
 *       collision both collapse to the 200 alreadyExists=true path);</li>
 *   <li>enforces the state machine on PATCH (FR-7 / FR-10);</li>
 *   <li>emits one INFO log per successful mutation (FR-11 / AC-31).</li>
 * </ul>
 *
 * <p>No outbound HTTP; no {@code @Transactional} on the service itself —
 * {@link WishlistPersistence} opens REQUIRES_NEW per DB op, preserving
 * the sibling-task convention of "never hold a JDBC connection outside
 * a narrow persistence bean."
 */
@Service
public class WishlistService {

    private static final Logger log = LoggerFactory.getLogger(WishlistService.class);

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "desk", "bed", "chair", "lighting"
    );

    private final WishlistPersistence persistence;
    private final int maxItemsPerUser;

    public WishlistService(
            WishlistPersistence persistence,
            @Value("${app.wishlist.max-items-per-user:500}") int maxItemsPerUser
    ) {
        this.persistence = persistence;
        this.maxItemsPerUser = maxItemsPerUser;
    }

    // =================================================================
    // POST /api/v1/wishlist  — FR-4 / FR-5
    // =================================================================

    /**
     * Add a furniture item to the user's wishlist.
     *
     * <p>Idempotency is a two-layer guarantee:
     * <ol>
     *   <li>Fast path: {@link WishlistPersistence#findByUserIdAndFurnitureId}
     *       returns an existing row → return it with {@code alreadyExists=true}.
     *       Status / addedAt / price are NEVER mutated by this path.</li>
     *   <li>Slow path: two concurrent POSTs both miss the fast path and
     *       attempt INSERT. The UNIQUE constraint
     *       {@code uq_wishlist_user_furniture} (V6) rejects the losing
     *       INSERT with {@link DataIntegrityViolationException}; we catch
     *       it and re-read the winning row, returning it with
     *       {@code alreadyExists=true} (AC-15).</li>
     * </ol>
     */
    public Outcome<WishlistItemResponse> add(String userId, AddWishlistRequest req) {
        // --- payload validation (FR-5 / AC-12 / AC-13) -----------------
        if (req == null
                || req.furnitureId() == null || req.furnitureId().isBlank()
                || req.category() == null    || req.category().isBlank()
                || req.price() == null) {
            logError("add", userId, null, WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
            throw new WishlistException(WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
        }
        if (!ALLOWED_CATEGORIES.contains(req.category())) {
            logError("add", userId, req.furnitureId(), WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
            throw new WishlistException(WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
        }
        if (req.price() < 0) {
            logError("add", userId, req.furnitureId(), WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
            throw new WishlistException(WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
        }

        // --- user / furniture existence (FR-4 / AC-10 / AC-11) --------
        if (!persistence.userExists(userId)) {
            logError("add", userId, req.furnitureId(), WishlistErrorCode.USER_NOT_FOUND);
            throw new WishlistException(WishlistErrorCode.USER_NOT_FOUND);
        }
        Furniture f = persistence.findFurniture(req.furnitureId())
                .orElse(null);
        if (f == null) {
            logError("add", userId, req.furnitureId(), WishlistErrorCode.FURNITURE_NOT_FOUND);
            throw new WishlistException(WishlistErrorCode.FURNITURE_NOT_FOUND);
        }

        // --- idempotent fast-path ------------------------------------
        Optional<Wishlist> existing = persistence.findByUserIdAndFurnitureId(userId, req.furnitureId());
        if (existing.isPresent()) {
            Wishlist row = existing.get();
            log.info("op=add userId={} furnitureId={} fromStatus={} toStatus={} resultHttp=200 alreadyExists=true",
                    userId, req.furnitureId(), row.getStatus(), row.getStatus());
            return new Outcome<>(WishlistItemResponse.of(row, f, true), /*created=*/false);
        }

        // --- slow path: INSERT; catch UNIQUE collision ----------------
        Wishlist fresh = new Wishlist();
        fresh.setWishlistId(UUID.randomUUID().toString());
        fresh.setUserId(userId);
        fresh.setFurnitureId(req.furnitureId());
        fresh.setCategory(req.category());
        fresh.setPrice(req.price());
        fresh.setStatus(Wishlist.Status.Active);
        fresh.setPurchasedAt(null);

        try {
            Wishlist saved = persistence.insert(fresh);
            log.info("op=add userId={} furnitureId={} fromStatus=(none) toStatus=Active resultHttp=201 alreadyExists=false",
                    userId, req.furnitureId());
            return new Outcome<>(WishlistItemResponse.of(saved, f, false), /*created=*/true);
        } catch (DataIntegrityViolationException ex) {
            // AC-15 — concurrent POST lost the UNIQUE race. Re-read the
            // winning row and return it with alreadyExists=true.
            Wishlist winning = persistence.findByUserIdAndFurnitureId(userId, req.furnitureId())
                    .orElseThrow(() -> ex); // shouldn't happen; propagate if it does.
            log.info("op=add userId={} furnitureId={} fromStatus={} toStatus={} resultHttp=200 alreadyExists=true race=true",
                    userId, req.furnitureId(), winning.getStatus(), winning.getStatus());
            return new Outcome<>(WishlistItemResponse.of(winning, f, true), /*created=*/false);
        }
    }

    // =================================================================
    // GET /api/v1/wishlist  — FR-6
    // =================================================================

    public WishlistListResponse list(String userId, String statusFilterRaw) {
        if (!persistence.userExists(userId)) {
            logError("list", userId, null, WishlistErrorCode.USER_NOT_FOUND);
            throw new WishlistException(WishlistErrorCode.USER_NOT_FOUND);
        }

        Wishlist.Status filter = parseStatusFilter(statusFilterRaw);

        int cap = Math.max(1, maxItemsPerUser);
        WishlistPersistence.ListSnapshot snap = persistence.list(userId, filter, cap + 1);

        boolean truncated = snap.rows().size() > cap;
        List<Wishlist> rows = truncated ? snap.rows().subList(0, cap) : snap.rows();

        // When no filter is requested, the spec wants ACTIVE-first ordering
        // with each subset sorted by updated_at DESC (already done). Split +
        // reconcat preserves the pattern.
        if (filter == null) {
            List<Wishlist> active = new ArrayList<>();
            List<Wishlist> purchased = new ArrayList<>();
            for (Wishlist w : rows) {
                if (w.getStatus() == Wishlist.Status.Active) active.add(w);
                else purchased.add(w);
            }
            rows = new ArrayList<>(active.size() + purchased.size());
            rows.addAll(active);
            rows.addAll(purchased);
        }

        // Single IN-lookup joins the current furniture rows for snapshot
        // rendering. Missing catalog rows → null furnitureSnapshot.
        Set<String> furnitureIds = new HashSet<>();
        for (Wishlist w : rows) furnitureIds.add(w.getFurnitureId());
        Map<String, Furniture> byId = persistence.findFurnitureByIds(furnitureIds);

        List<WishlistItemResponse> items = new ArrayList<>(rows.size());
        for (Wishlist w : rows) {
            Furniture f = byId.get(w.getFurnitureId());
            items.add(WishlistItemResponse.of(w, f, /*alreadyExists=*/false));
        }

        return new WishlistListResponse(
                items,
                (int) snap.totalActive(),
                (int) snap.totalPurchased(),
                truncated
        );
    }

    private static Wishlist.Status parseStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String up = raw.trim().toUpperCase();
        return switch (up) {
            case "ACTIVE"    -> Wishlist.Status.Active;
            case "PURCHASED" -> Wishlist.Status.Purchased;
            default          -> throw new WishlistException(WishlistErrorCode.INVALID_WISHLIST_STATUS_FILTER);
        };
    }

    // =================================================================
    // PATCH /api/v1/wishlist/{id}  — FR-7 / FR-10
    // =================================================================

    public WishlistItemResponse patch(String userId, String wishlistId, String newStatusRaw) {
        Wishlist.Status target = parseTargetStatus(newStatusRaw);

        Wishlist row = persistence.findByWishlistIdAndUserId(wishlistId, userId)
                .orElseThrow(() -> {
                    logError("patch", userId, wishlistId, WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND);
                    return new WishlistException(WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND);
                });

        Wishlist.Status from = row.getStatus();

        // AC-24 — idempotent no-op when the target equals the current.
        // Do NOT touch updated_at / purchasedAt; re-read for a fresh
        // managed snapshot and return the existing row.
        if (from == target) {
            Furniture f = persistence.findFurniture(row.getFurnitureId()).orElse(null);
            log.info("op=patch userId={} wishlistId={} fromStatus={} toStatus={} resultHttp=200 noop=true",
                    userId, wishlistId, from, target);
            return WishlistItemResponse.of(row, f, /*alreadyExists=*/false);
        }

        LocalDateTime purchasedAt;
        if (target == Wishlist.Status.Purchased) {
            purchasedAt = LocalDateTime.now(ZoneOffset.UTC);
        } else {
            purchasedAt = null; // Purchased → Active clears the timestamp
        }
        Wishlist updated = persistence.updateStatus(wishlistId, target, purchasedAt);
        Furniture f = persistence.findFurniture(updated.getFurnitureId()).orElse(null);
        log.info("op=patch userId={} wishlistId={} fromStatus={} toStatus={} resultHttp=200",
                userId, wishlistId, from, target);
        return WishlistItemResponse.of(updated, f, /*alreadyExists=*/false);
    }

    private static Wishlist.Status parseTargetStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new WishlistException(WishlistErrorCode.INVALID_STATE_TRANSITION);
        }
        String up = raw.trim().toUpperCase();
        return switch (up) {
            case "ACTIVE"    -> Wishlist.Status.Active;
            case "PURCHASED" -> Wishlist.Status.Purchased;
            default          -> throw new WishlistException(WishlistErrorCode.INVALID_STATE_TRANSITION);
        };
    }

    // =================================================================
    // DELETE /api/v1/wishlist/{id}  — FR-8
    // =================================================================

    public void delete(String userId, String wishlistId) {
        Wishlist row = persistence.findByWishlistIdAndUserId(wishlistId, userId)
                .orElseThrow(() -> {
                    logError("delete", userId, wishlistId, WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND);
                    return new WishlistException(WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND);
                });
        persistence.deleteById(row.getWishlistId());
        log.info("op=delete userId={} wishlistId={} fromStatus={} toStatus=(removed) resultHttp=204",
                userId, wishlistId, row.getStatus());
    }

    // =================================================================
    // Helpers
    // =================================================================

    private static void logError(String op, String userId, String id, WishlistErrorCode code) {
        log.warn("op={} userId={} target={} errorCode={} correlationId=pending",
                op, userId, id, code);
    }

    /**
     * Service-layer return wrapper indicating whether a NEW row was
     * written (controller picks 201) or an existing row was returned
     * (controller picks 200). Keeps the HTTP status decision in the
     * controller layer where it belongs.
     */
    public record Outcome<T>(T body, boolean created) {}
}
