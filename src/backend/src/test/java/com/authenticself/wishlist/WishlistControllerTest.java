package com.authenticself.wishlist;

import com.authenticself.domain.Furniture;
import com.authenticself.domain.Wishlist;
import com.authenticself.wishlist.dto.WishlistItemResponse;
import com.authenticself.wishlist.dto.WishlistListResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice test for {@link WishlistController} covering the public
 * endpoints (UC-02-wishlist AC-7..AC-28, AC-53).
 * <p>
 * The {@link WishlistService} is mocked — this test validates controller
 * wiring, status codes, error-envelope shape, and DTO marshalling. The
 * service's idempotency + state-machine internals are exercised in
 * {@link WishlistServiceTest} and the concurrency scenario in
 * {@link WishlistServiceConcurrencyTest}.
 */
@WebMvcTest(WishlistController.class)
@Import(WishlistExceptionAdvice.class)
class WishlistControllerTest {

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper json;
    @MockBean  WishlistService service;

    private static final LocalDateTime T0 = LocalDateTime.parse("2026-04-18T09:14:22");

    @BeforeEach
    void init() {
        // no-op — every test re-stubs the service.
    }

    // =================================================================
    // POST — AC-7 happy path (201 new row)
    // =================================================================
    @Test
    @DisplayName("AC-7: POST creates an Active row → 201, alreadyExists=false")
    void postAddCreatesActiveRow_ac7() throws Exception {
        WishlistItemResponse dto = sampleItem("w1", "u1", "f_desk_001", "desk", 189000, "Active", null, false);
        when(service.add(eq("u1"), any())).thenReturn(new WishlistService.Outcome<>(dto, true));

        mvc.perform(post("/api/v1/wishlist")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "f_desk_001",
                                "category",    "desk",
                                "price",       189000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wishlistId",    is("w1")))
                .andExpect(jsonPath("$.userId",        is("u1")))
                .andExpect(jsonPath("$.furnitureId",   is("f_desk_001")))
                .andExpect(jsonPath("$.category",      is("desk")))
                .andExpect(jsonPath("$.price",         is(189000)))
                .andExpect(jsonPath("$.status",        is("Active")))
                .andExpect(jsonPath("$.addedAt",       notNullValue()))
                .andExpect(jsonPath("$.purchasedAt",   nullValue()))
                .andExpect(jsonPath("$.alreadyExists", is(false)));
    }

    // =================================================================
    // POST — AC-8 idempotent on existing Active row → 200, alreadyExists=true
    // =================================================================
    @Test
    @DisplayName("AC-8: POST is idempotent on existing Active row → 200 alreadyExists=true")
    void postAddIsIdempotent_ac8() throws Exception {
        WishlistItemResponse dto = sampleItem("w1", "u1", "f_desk_001", "desk", 189000, "Active", null, true);
        when(service.add(eq("u1"), any())).thenReturn(new WishlistService.Outcome<>(dto, false));

        mvc.perform(post("/api/v1/wishlist")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "f_desk_001",
                                "category",    "desk",
                                "price",       189000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlistId",    is("w1")))
                .andExpect(jsonPath("$.status",        is("Active")))
                .andExpect(jsonPath("$.alreadyExists", is(true)));
    }

    // =================================================================
    // POST — AC-9 idempotent on Purchased; status is NOT reset
    // =================================================================
    @Test
    @DisplayName("AC-9: POST does not reset a Purchased row → 200 status=Purchased")
    void postAddDoesNotResetPurchased_ac9() throws Exception {
        WishlistItemResponse dto = sampleItem("w1", "u1", "f_desk_001", "desk", 189000, "Purchased", T0.plusDays(1), true);
        when(service.add(eq("u1"), any())).thenReturn(new WishlistService.Outcome<>(dto, false));

        mvc.perform(post("/api/v1/wishlist")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "f_desk_001",
                                "category",    "desk",
                                "price",       189000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status",        is("Purchased")))
                .andExpect(jsonPath("$.alreadyExists", is(true)))
                .andExpect(jsonPath("$.purchasedAt",   notNullValue()));
    }

    // =================================================================
    // POST — AC-10 unknown furniture → 404 FURNITURE_NOT_FOUND
    // =================================================================
    @Test
    @DisplayName("AC-10: POST with unknown furnitureId → 404 FURNITURE_NOT_FOUND")
    void postAddUnknownFurniture_ac10() throws Exception {
        when(service.add(eq("u1"), any()))
                .thenThrow(new WishlistException(WishlistErrorCode.FURNITURE_NOT_FOUND));

        mvc.perform(post("/api/v1/wishlist")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "ghost",
                                "category",    "desk",
                                "price",       100))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("FURNITURE_NOT_FOUND")));
    }

    // =================================================================
    // POST — AC-11 unknown user → 404 USER_NOT_FOUND
    // =================================================================
    @Test
    @DisplayName("AC-11: POST with unknown X-User-Id → 404 USER_NOT_FOUND")
    void postAddUnknownUser_ac11() throws Exception {
        when(service.add(eq("ghost"), any()))
                .thenThrow(new WishlistException(WishlistErrorCode.USER_NOT_FOUND));

        mvc.perform(post("/api/v1/wishlist")
                        .header("X-User-Id", "ghost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "f_desk_001",
                                "category",    "desk",
                                "price",       100))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("USER_NOT_FOUND")));
    }

    // =================================================================
    // POST — AC-12 invalid category → 400
    // =================================================================
    @Test
    @DisplayName("AC-12: POST with category='sofa' → 400 INVALID_WISHLIST_PAYLOAD")
    void postAddInvalidCategory_ac12() throws Exception {
        when(service.add(eq("u1"), any()))
                .thenThrow(new WishlistException(WishlistErrorCode.INVALID_WISHLIST_PAYLOAD));

        mvc.perform(post("/api/v1/wishlist")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "f_desk_001",
                                "category",    "sofa",
                                "price",       100))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_WISHLIST_PAYLOAD")));
    }

    // =================================================================
    // POST — AC-13 negative price → 400
    // =================================================================
    @Test
    @DisplayName("AC-13: POST with price=-1 → 400 INVALID_WISHLIST_PAYLOAD")
    void postAddNegativePrice_ac13() throws Exception {
        when(service.add(eq("u1"), any()))
                .thenThrow(new WishlistException(WishlistErrorCode.INVALID_WISHLIST_PAYLOAD));

        mvc.perform(post("/api/v1/wishlist")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "f_desk_001",
                                "category",    "desk",
                                "price",       -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_WISHLIST_PAYLOAD")));
    }

    // =================================================================
    // POST — AC-14 missing header → 400 MISSING_USER_HEADER + envelope shape
    // =================================================================
    @Test
    @DisplayName("AC-14: POST without X-User-Id → 400 MISSING_USER_HEADER envelope shape")
    void postAddMissingUserHeader_ac14() throws Exception {
        mvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "furnitureId", "f_desk_001",
                                "category",    "desk",
                                "price",       100))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode",     is("MISSING_USER_HEADER")))
                .andExpect(jsonPath("$.message",       notNullValue()))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
        verify(service, never()).add(any(), any());
    }

    // =================================================================
    // GET — AC-16 unfiltered list returns both states with counts
    // =================================================================
    @Test
    @DisplayName("AC-16: GET unfiltered → 200 totalActive=2 totalPurchased=1 items ACTIVE-first")
    void getListUnfiltered_ac16() throws Exception {
        WishlistItemResponse a1 = sampleItem("w1", "u1", "f_desk_001", "desk", 100, "Active", null, false);
        WishlistItemResponse a2 = sampleItem("w2", "u1", "f_bed_001",  "bed",  200, "Active", null, false);
        WishlistItemResponse p1 = sampleItem("w3", "u1", "f_chair_001","chair",300, "Purchased", T0, false);
        WishlistListResponse resp = new WishlistListResponse(List.of(a1, a2, p1), 2, 1, false);

        when(service.list(eq("u1"), eq(null))).thenReturn(resp);

        mvc.perform(get("/api/v1/wishlist").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActive",    is(2)))
                .andExpect(jsonPath("$.totalPurchased", is(1)))
                .andExpect(jsonPath("$.truncated",      is(false)))
                .andExpect(jsonPath("$.items[0].status", is("Active")))
                .andExpect(jsonPath("$.items[1].status", is("Active")))
                .andExpect(jsonPath("$.items[2].status", is("Purchased")));
    }

    // =================================================================
    // GET — AC-17 filter ACTIVE / PURCHASED (case-insensitive)
    // =================================================================
    @Test
    @DisplayName("AC-17: GET ?status=active returns only Active rows (case-insensitive)")
    void getListFilterByStatus_ac17() throws Exception {
        WishlistItemResponse a1 = sampleItem("w1", "u1", "f_desk_001", "desk", 100, "Active", null, false);
        when(service.list(eq("u1"), eq("active")))
                .thenReturn(new WishlistListResponse(List.of(a1), 1, 0, false));
        when(service.list(eq("u1"), eq("PURCHASED")))
                .thenReturn(new WishlistListResponse(
                        List.of(sampleItem("w3", "u1", "f_chair_001", "chair", 300, "Purchased", T0, false)),
                        0, 1, false));

        mvc.perform(get("/api/v1/wishlist?status=active").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status", is("Active")))
                .andExpect(jsonPath("$.items.length()",  is(1)));

        mvc.perform(get("/api/v1/wishlist?status=PURCHASED").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status", is("Purchased")))
                .andExpect(jsonPath("$.items.length()",  is(1)));
    }

    // =================================================================
    // GET — AC-18 invalid filter → 400 INVALID_WISHLIST_STATUS_FILTER
    // =================================================================
    @Test
    @DisplayName("AC-18: GET ?status=PENDING → 400 INVALID_WISHLIST_STATUS_FILTER")
    void getListInvalidFilter_ac18() throws Exception {
        when(service.list(eq("u1"), eq("PENDING")))
                .thenThrow(new WishlistException(WishlistErrorCode.INVALID_WISHLIST_STATUS_FILTER));

        mvc.perform(get("/api/v1/wishlist?status=PENDING").header("X-User-Id", "u1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_WISHLIST_STATUS_FILTER")));
    }

    // =================================================================
    // GET — AC-19 furnitureSnapshot present; nullable
    // =================================================================
    @Test
    @DisplayName("AC-19: furnitureSnapshot object present (non-null + nullable cases)")
    void getListSnapshotPresentAndNullable_ac19() throws Exception {
        WishlistItemResponse withSnap = new WishlistItemResponse(
                "w1", "u1", "f_desk_001", "desk", 189000, "Active", T0, null,
                new WishlistItemResponse.FurnitureSnapshot("Oslo Slim Desk",
                        "https://cdn.example.com/img.jpg", "#F3E6D2", "desk"),
                false);
        WishlistItemResponse noSnap = new WishlistItemResponse(
                "w2", "u1", "f_gone", "desk", 1000, "Active", T0, null,
                null,
                false);
        when(service.list(eq("u1"), eq(null)))
                .thenReturn(new WishlistListResponse(List.of(withSnap, noSnap), 2, 0, false));

        mvc.perform(get("/api/v1/wishlist").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].furnitureSnapshot.name",     is("Oslo Slim Desk")))
                .andExpect(jsonPath("$.items[0].furnitureSnapshot.imageUrl", is("https://cdn.example.com/img.jpg")))
                .andExpect(jsonPath("$.items[0].furnitureSnapshot.colorHex", is("#F3E6D2")))
                .andExpect(jsonPath("$.items[0].furnitureSnapshot.type",     is("desk")))
                .andExpect(jsonPath("$.items[1].furnitureSnapshot",          nullValue()))
                .andExpect(jsonPath("$.items[1].wishlistId",                  is("w2")));
    }

    // =================================================================
    // GET — AC-21 truncation flag
    // =================================================================
    @Test
    @DisplayName("AC-21: GET with >max returns truncated=true")
    void getListTruncation_ac21() throws Exception {
        WishlistItemResponse dto = sampleItem("w1", "u1", "f_desk_001", "desk", 100, "Active", null, false);
        when(service.list(eq("u1"), eq(null)))
                .thenReturn(new WishlistListResponse(List.of(dto), 501, 0, true));

        mvc.perform(get("/api/v1/wishlist").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated",  is(true)))
                .andExpect(jsonPath("$.totalActive", is(501)));
    }

    // =================================================================
    // PATCH — AC-22 Active → Purchased
    // =================================================================
    @Test
    @DisplayName("AC-22: PATCH ACTIVE→PURCHASED → 200 status=Purchased, purchasedAt set")
    void patchActiveToPurchased_ac22() throws Exception {
        WishlistItemResponse dto = sampleItem("w1", "u1", "f_desk_001", "desk", 100, "Purchased", T0, false);
        when(service.patch(eq("u1"), eq("w1"), eq("PURCHASED"))).thenReturn(dto);

        mvc.perform(patch("/api/v1/wishlist/{id}", "w1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "PURCHASED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status",      is("Purchased")))
                .andExpect(jsonPath("$.purchasedAt", notNullValue()));
    }

    // =================================================================
    // PATCH — AC-23 Purchased → Active
    // =================================================================
    @Test
    @DisplayName("AC-23: PATCH PURCHASED→ACTIVE → 200 status=Active, purchasedAt=null")
    void patchPurchasedToActive_ac23() throws Exception {
        WishlistItemResponse dto = sampleItem("w1", "u1", "f_desk_001", "desk", 100, "Active", null, false);
        when(service.patch(eq("u1"), eq("w1"), eq("ACTIVE"))).thenReturn(dto);

        mvc.perform(patch("/api/v1/wishlist/{id}", "w1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status",      is("Active")))
                .andExpect(jsonPath("$.purchasedAt", nullValue()));
    }

    // =================================================================
    // PATCH — AC-25 invalid state → 400
    // =================================================================
    @Test
    @DisplayName("AC-25: PATCH status=PENDING → 400 INVALID_STATE_TRANSITION")
    void patchInvalidTransition_ac25() throws Exception {
        when(service.patch(eq("u1"), eq("w1"), eq("PENDING")))
                .thenThrow(new WishlistException(WishlistErrorCode.INVALID_STATE_TRANSITION));

        mvc.perform(patch("/api/v1/wishlist/{id}", "w1")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "PENDING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATE_TRANSITION")));
    }

    // =================================================================
    // PATCH — AC-26 missing / foreign → 404
    // =================================================================
    @Test
    @DisplayName("AC-26: PATCH missing / foreign → 404 WISHLIST_ITEM_NOT_FOUND")
    void patchMissingOrForeign_ac26() throws Exception {
        when(service.patch(eq("u1"), eq("nope"), any()))
                .thenThrow(new WishlistException(WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND));

        mvc.perform(patch("/api/v1/wishlist/{id}", "nope")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "PURCHASED"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("WISHLIST_ITEM_NOT_FOUND")));
    }

    // =================================================================
    // DELETE — AC-27 204 no body from both states
    // =================================================================
    @Test
    @DisplayName("AC-27: DELETE returns 204 (empty body) from Active or Purchased")
    void deleteRemovesRowBothStates_ac27() throws Exception {
        mvc.perform(delete("/api/v1/wishlist/{id}", "w1").header("X-User-Id", "u1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mvc.perform(delete("/api/v1/wishlist/{id}", "w3").header("X-User-Id", "u1"))
                .andExpect(status().isNoContent());

        // Both distinct wishlistIds routed to service.delete exactly
        // once each — verifies path binding without colliding on the
        // cumulative invocation counter.
        verify(service).delete("u1", "w1");
        verify(service).delete("u1", "w3");
    }

    // =================================================================
    // DELETE — AC-28 missing / foreign → 404
    // =================================================================
    @Test
    @DisplayName("AC-28: DELETE missing / foreign → 404 WISHLIST_ITEM_NOT_FOUND")
    void deleteMissingOrForeign_ac28() throws Exception {
        org.mockito.Mockito.doThrow(new WishlistException(WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND))
                .when(service).delete(eq("u1"), eq("ghost"));

        mvc.perform(delete("/api/v1/wishlist/{id}", "ghost").header("X-User-Id", "u1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("WISHLIST_ITEM_NOT_FOUND")));
    }

    // =================================================================
    // AC-53 — error envelope shape (errorCode UPPER_SNAKE, message non-blank,
    //          correlationId UUIDv4-ish).
    // =================================================================
    @Test
    @DisplayName("AC-53: error envelope has {errorCode, message, correlationId} UUIDv4")
    void errorEnvelopeShape_ac53() throws Exception {
        mvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", matchesPattern("^[A-Z][A-Z0-9_]+$")))
                .andExpect(jsonPath("$.message",   notNullValue()))
                .andExpect(jsonPath("$.correlationId",
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));
    }

    // =================================================================
    // helpers
    // =================================================================

    private static WishlistItemResponse sampleItem(
            String wishlistId, String userId, String furnitureId, String category,
            int price, String status, LocalDateTime purchasedAt, boolean alreadyExists) {
        Wishlist w = new Wishlist();
        w.setWishlistId(wishlistId);
        w.setUserId(userId);
        w.setFurnitureId(furnitureId);
        w.setCategory(category);
        w.setPrice(price);
        w.setStatus(Wishlist.Status.valueOf(status));
        w.setPurchasedAt(purchasedAt);
        // addedAt is read-only from JPA — we use reflection via the
        // dto factory for the test double.
        Furniture f = new Furniture();
        f.setFurnitureId(furnitureId);
        f.setName("Sample " + furnitureId);
        f.setType(category);
        f.setImageUrl("https://cdn.example.com/" + furnitureId + ".jpg");
        f.setColorHex("#F3E6D2");

        // Build the DTO directly to populate addedAt (bypasses the
        // entity's insertable=false constraint for assertion purposes).
        return new WishlistItemResponse(
                wishlistId, userId, furnitureId, category, price, status,
                T0,                       // addedAt
                purchasedAt,
                WishlistItemResponse.FurnitureSnapshot.from(f),
                alreadyExists
        );
    }
}
