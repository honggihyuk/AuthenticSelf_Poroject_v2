package com.authenticself.admin;

import com.authenticself.admin.dto.AdminOverviewResponse;
import com.authenticself.admin.dto.RoomsTile;
import com.authenticself.admin.dto.SalesTile;
import com.authenticself.admin.dto.UsersTile;
import com.authenticself.admin.dto.WishlistTile;
import com.authenticself.domain.Space;
import com.authenticself.repository.UserRepository;
import com.authenticself.space.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth-specific MockMvc slice test for {@link AdminController}
 * (UC-03-admin-overview AC-8, AC-26, AC-27).
 * <p>
 * Every endpoint is exercised with the three failure branches:
 * <ul>
 *   <li>Missing {@code X-User-Id}  → 400 {@code MISSING_USER_HEADER}.</li>
 *   <li>Unknown {@code X-User-Id}  → 404 {@code USER_NOT_FOUND}.</li>
 *   <li>Non-admin {@code X-User-Id} → 403 {@code NOT_ADMIN}.</li>
 * </ul>
 * Plus AC-8: the authorizer is invoked exactly once per happy-path
 * request.
 */
@WebMvcTest(AdminController.class)
@Import(AdminExceptionAdvice.class)
class AdminControllerAuthTest {

    @Autowired MockMvc mvc;
    @MockBean  AdminAuthorizer       authorizer;
    @MockBean  AdminOverviewService  service;
    @MockBean  UserRepository        userRepository;

    private static final OffsetDateTime T0 =
            OffsetDateTime.of(2026, 4, 18, 9, 14, 22, 0, ZoneOffset.ofHours(9));

    private static final String[] PATHS = {
            "/api/v1/admin/overview",
            "/api/v1/admin/users",
            "/api/v1/admin/rooms",
            "/api/v1/admin/wishlist",
            "/api/v1/admin/sales"
    };

    // =================================================================
    // AC-8 — authorizer invoked exactly once on happy path.
    // =================================================================
    @Test
    @DisplayName("AC-8: authorizer called first on every endpoint (exactly once per request)")
    void authorizerCalledFirst_ac8() throws Exception {
        doNothing().when(authorizer).requireAdmin(eq("u_admin"));
        stubAllTiles();

        for (String p : PATHS) {
            mvc.perform(get(p + "?window=ALL").header("X-User-Id", "u_admin"))
                    .andExpect(status().isOk());
        }
        verify(authorizer, times(PATHS.length)).requireAdmin(eq("u_admin"));
    }

    // =================================================================
    // AC-26 — non-admin → 403 NOT_ADMIN on every endpoint.
    // =================================================================
    @Test
    @DisplayName("AC-26: non-admin caller → 403 NOT_ADMIN on every endpoint")
    void nonAdminGets403OnEveryEndpoint_ac26() throws Exception {
        doThrow(new AdminException(AdminErrorCode.NOT_ADMIN))
                .when(authorizer).requireAdmin(eq("u_user"));

        for (String p : PATHS) {
            mvc.perform(get(p + "?window=ALL").header("X-User-Id", "u_user"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode",     is("NOT_ADMIN")))
                    .andExpect(jsonPath("$.message",       notNullValue()))
                    .andExpect(jsonPath("$.correlationId", matchesPattern("^[0-9a-fA-F-]{36}$")));
        }
    }

    // =================================================================
    // AC-27 — missing + unknown user paths.
    // =================================================================
    @Test
    @DisplayName("AC-27: missing header → 400 MISSING_USER_HEADER; unknown user → 404 USER_NOT_FOUND")
    void missingHeaderAndUnknownUser_ac27() throws Exception {
        doThrow(new AdminException(AdminErrorCode.MISSING_USER_HEADER))
                .when(authorizer).requireAdmin(null);
        doThrow(new AdminException(AdminErrorCode.USER_NOT_FOUND))
                .when(authorizer).requireAdmin(eq("u_nope"));

        for (String p : PATHS) {
            mvc.perform(get(p + "?window=ALL"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("MISSING_USER_HEADER")));
            mvc.perform(get(p + "?window=ALL").header("X-User-Id", "u_nope"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode", is("USER_NOT_FOUND")));
        }
    }

    // -----------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------
    private void stubAllTiles() {
        Map<Space.Status, Long> stat = new EnumMap<>(Space.Status.class);
        for (Space.Status v : Space.Status.values()) stat.put(v, 0L);
        Map<Style, Long> style = new EnumMap<>(Style.class);
        for (Style v : Style.values()) style.put(v, 0L);
        Map<String, Long> ws = new LinkedHashMap<>();
        ws.put("ACTIVE", 0L); ws.put("PURCHASED", 0L);
        Map<String, Long> cat = new LinkedHashMap<>();
        cat.put("desk", 0L); cat.put("bed", 0L); cat.put("chair", 0L); cat.put("lighting", 0L);

        UsersTile u = new UsersTile(0, 0, 0, 0, List.of());
        RoomsTile r = new RoomsTile(0, stat, style, List.of());
        WishlistTile w = new WishlistTile(0, ws, cat, 0.0);
        SalesTile s = new SalesTile(0, 0, 0, cat, List.of());

        when(service.nowOffset()).thenReturn(T0);
        when(service.overview(any(), any())).thenReturn(
                new AdminOverviewResponse("ALL", T0, u, r, w, s));
        when(service.users(any(),    any())).thenReturn(u);
        when(service.rooms(any(),    any())).thenReturn(r);
        when(service.wishlist(any(), any())).thenReturn(w);
        when(service.sales(any(),    any())).thenReturn(s);
    }
}
