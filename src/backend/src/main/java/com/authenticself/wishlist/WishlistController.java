package com.authenticself.wishlist;

import com.authenticself.wishlist.dto.AddWishlistRequest;
import com.authenticself.wishlist.dto.PatchWishlistRequest;
import com.authenticself.wishlist.dto.WishlistItemResponse;
import com.authenticself.wishlist.dto.WishlistListResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST surface for the user wishlist (UC-02-wishlist FR-5..FR-8).
 * <ul>
 *   <li>{@code POST   /api/v1/wishlist}                — add (idempotent on
 *       {@code (userId, furnitureId)}; 201 on insert, 200 on idempotent hit).</li>
 *   <li>{@code GET    /api/v1/wishlist?status=...}     — list filtered by
 *       {@code ACTIVE} / {@code PURCHASED} (optional, case-insensitive).</li>
 *   <li>{@code PATCH  /api/v1/wishlist/{wishlistId}}   — state transition
 *       ({@code ACTIVE ↔ PURCHASED}); no-op on same-state PATCH.</li>
 *   <li>{@code DELETE /api/v1/wishlist/{wishlistId}}   — hard delete (allowed
 *       in both states).</li>
 * </ul>
 *
 * <p>Every endpoint requires an {@code X-User-Id} header; a missing header
 * maps to 400 {@code MISSING_USER_HEADER} via the shared
 * {@link WishlistExceptionAdvice#handleMissingHeader} path — identical
 * semantics to the {@code SpaceController} convention.
 */
@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    // -----------------------------------------------------------------
    // POST — add (FR-5)
    // -----------------------------------------------------------------
    @PostMapping
    public ResponseEntity<WishlistItemResponse> add(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody(required = false) AddWishlistRequest body
    ) {
        requireUserId(userId);
        if (body == null) {
            throw new WishlistException(WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
        }
        WishlistService.Outcome<WishlistItemResponse> out = service.add(userId, body);
        HttpStatus status = out.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(out.body());
    }

    // -----------------------------------------------------------------
    // GET — list (FR-6)
    // -----------------------------------------------------------------
    @GetMapping
    public ResponseEntity<WishlistListResponse> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "status", required = false) String statusFilter
    ) {
        requireUserId(userId);
        return ResponseEntity.ok(service.list(userId, statusFilter));
    }

    // -----------------------------------------------------------------
    // PATCH — state transition (FR-7 / FR-10)
    // -----------------------------------------------------------------
    @PatchMapping("/{wishlistId}")
    public ResponseEntity<WishlistItemResponse> patch(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable("wishlistId") String wishlistId,
            @RequestBody(required = false) PatchWishlistRequest body
    ) {
        requireUserId(userId);
        String targetStatus = body != null ? body.status() : null;
        return ResponseEntity.ok(service.patch(userId, wishlistId, targetStatus));
    }

    // -----------------------------------------------------------------
    // DELETE — hard delete (FR-8)
    // -----------------------------------------------------------------
    @DeleteMapping("/{wishlistId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable("wishlistId") String wishlistId
    ) {
        requireUserId(userId);
        service.delete(userId, wishlistId);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------
    private static void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new WishlistException(WishlistErrorCode.MISSING_USER_HEADER);
        }
    }
}
