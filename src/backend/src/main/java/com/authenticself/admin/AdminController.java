package com.authenticself.admin;

import com.authenticself.admin.dto.AdminOverviewResponse;
import com.authenticself.admin.dto.RoomsTileResponse;
import com.authenticself.admin.dto.SalesTileResponse;
import com.authenticself.admin.dto.UsersTileResponse;
import com.authenticself.admin.dto.WishlistTileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST surface for the admin overview
 * (UC-03-admin-overview FR-4..FR-8).
 * <ul>
 *   <li>{@code GET /api/v1/admin/overview?window=...} — master aggregate
 *       with all four tiles (FR-4).</li>
 *   <li>{@code GET /api/v1/admin/users?window=...}    — Users tile (FR-5).</li>
 *   <li>{@code GET /api/v1/admin/rooms?window=...}    — Rooms tile (FR-6).</li>
 *   <li>{@code GET /api/v1/admin/wishlist?window=...} — Wishlist tile (FR-7).</li>
 *   <li>{@code GET /api/v1/admin/sales?window=...}    — Sales tile (FR-8).</li>
 * </ul>
 *
 * <p>Every handler calls {@link AdminAuthorizer#requireAdmin(String)} as
 * its first statement (FR-3 / AC-8). Window parsing happens on the next
 * line — a bad window value raises 400 {@code INVALID_TIME_WINDOW}
 * without ever touching the DB (FR-4 / AC-11).
 *
 * <p>No {@code @Transactional} on the controller or service (D-3) — every
 * aggregation query runs inside JPA's implicit read-only transaction.
 */
@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.OPTIONS
})
public class AdminController {

    private final AdminAuthorizer      authorizer;
    private final AdminOverviewService service;

    public AdminController(AdminAuthorizer authorizer, AdminOverviewService service) {
        this.authorizer = authorizer;
        this.service    = service;
    }

    // -----------------------------------------------------------------
    // Master endpoint (FR-4 / AC-9 / AC-10 / AC-33 / AC-53)
    // -----------------------------------------------------------------
    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewResponse> overview(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "window",     required = false) String windowRaw
    ) {
        authorizer.requireAdmin(userId);
        TimeWindow window = TimeWindow.parse(windowRaw);
        return ResponseEntity.ok(service.overview(userId, window));
    }

    // -----------------------------------------------------------------
    // Users tile (FR-5 / AC-12..AC-15)
    // -----------------------------------------------------------------
    @GetMapping("/users")
    public ResponseEntity<UsersTileResponse> users(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "window",     required = false) String windowRaw
    ) {
        authorizer.requireAdmin(userId);
        TimeWindow window = TimeWindow.parse(windowRaw);
        return ResponseEntity.ok(
                UsersTileResponse.of(window.name(), service.nowOffset(),
                        service.users(userId, window)));
    }

    // -----------------------------------------------------------------
    // Rooms tile (FR-6 / AC-16..AC-19)
    // -----------------------------------------------------------------
    @GetMapping("/rooms")
    public ResponseEntity<RoomsTileResponse> rooms(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "window",     required = false) String windowRaw
    ) {
        authorizer.requireAdmin(userId);
        TimeWindow window = TimeWindow.parse(windowRaw);
        return ResponseEntity.ok(
                RoomsTileResponse.of(window.name(), service.nowOffset(),
                        service.rooms(userId, window)));
    }

    // -----------------------------------------------------------------
    // Wishlist tile (FR-7 / AC-20 / AC-21)
    // -----------------------------------------------------------------
    @GetMapping("/wishlist")
    public ResponseEntity<WishlistTileResponse> wishlist(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "window",     required = false) String windowRaw
    ) {
        authorizer.requireAdmin(userId);
        TimeWindow window = TimeWindow.parse(windowRaw);
        return ResponseEntity.ok(
                WishlistTileResponse.of(window.name(), service.nowOffset(),
                        service.wishlist(userId, window)));
    }

    // -----------------------------------------------------------------
    // Sales tile (FR-8 / AC-22..AC-25)
    // -----------------------------------------------------------------
    @GetMapping("/sales")
    public ResponseEntity<SalesTileResponse> sales(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "window",     required = false) String windowRaw
    ) {
        authorizer.requireAdmin(userId);
        TimeWindow window = TimeWindow.parse(windowRaw);
        return ResponseEntity.ok(
                SalesTileResponse.of(window.name(), service.nowOffset(),
                        service.sales(userId, window)));
    }
}
