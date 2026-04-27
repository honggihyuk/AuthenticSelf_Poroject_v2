package com.authenticself.wishlist;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.authenticself.domain.Furniture;
import com.authenticself.domain.Wishlist;
import com.authenticself.wishlist.dto.AddWishlistRequest;
import com.authenticself.wishlist.dto.WishlistItemResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WishlistService} focused on idempotency, state
 * machine, and logging contract (UC-02-wishlist AC-31, AC-52 slice).
 * <p>
 * {@link WishlistPersistence} is mocked — the real MySQL-backed
 * integration surface is exercised in
 * {@link WishlistServiceConcurrencyTest} and {@link WishlistControllerTest}.
 */
class WishlistServiceTest {

    private WishlistPersistence persistence;
    private WishlistService service;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void init() {
        persistence = mock(WishlistPersistence.class);
        service = new WishlistService(persistence, 500);

        Logger logger = (Logger) LoggerFactory.getLogger(WishlistService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(WishlistService.class);
        logger.detachAppender(appender);
    }

    // =================================================================
    // Idempotent fast-path — existing row returns alreadyExists=true
    // =================================================================
    @Test
    @DisplayName("add returns existing row with alreadyExists=true when (u,f) already exists")
    void addIdempotentFastPath() {
        when(persistence.userExists("u1")).thenReturn(true);
        when(persistence.findFurniture("f_desk_001")).thenReturn(Optional.of(furniture("f_desk_001")));

        Wishlist existing = wishlist("w1", "u1", "f_desk_001", Wishlist.Status.Active);
        when(persistence.findByUserIdAndFurnitureId("u1", "f_desk_001"))
                .thenReturn(Optional.of(existing));

        WishlistService.Outcome<WishlistItemResponse> out = service.add(
                "u1", new AddWishlistRequest("f_desk_001", "desk", 189000));

        assertThat(out.created()).isFalse();
        assertThat(out.body().alreadyExists()).isTrue();
        assertThat(out.body().wishlistId()).isEqualTo("w1");
        assertThat(out.body().status()).isEqualTo("Active");

        // AC-31 — one INFO log, shape includes fromStatus=Active toStatus=Active.
        assertThat(infoLogs())
                .anyMatch(s -> s.contains("op=add")
                            && s.contains("userId=u1")
                            && s.contains("furnitureId=f_desk_001")
                            && s.contains("fromStatus=Active")
                            && s.contains("toStatus=Active"));
    }

    // =================================================================
    // Slow path — no existing row → INSERT returns created=true
    // =================================================================
    @Test
    @DisplayName("add inserts new row when (u,f) absent → created=true")
    void addInsertsNewRow() {
        when(persistence.userExists("u1")).thenReturn(true);
        when(persistence.findFurniture("f_desk_001")).thenReturn(Optional.of(furniture("f_desk_001")));
        when(persistence.findByUserIdAndFurnitureId("u1", "f_desk_001"))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Wishlist> cap = ArgumentCaptor.forClass(Wishlist.class);
        when(persistence.insert(cap.capture()))
                .thenAnswer(inv -> cap.getValue());

        WishlistService.Outcome<WishlistItemResponse> out = service.add(
                "u1", new AddWishlistRequest("f_desk_001", "desk", 189000));

        assertThat(out.created()).isTrue();
        assertThat(out.body().alreadyExists()).isFalse();
        assertThat(cap.getValue().getUserId()).isEqualTo("u1");
        assertThat(cap.getValue().getFurnitureId()).isEqualTo("f_desk_001");
        assertThat(cap.getValue().getStatus()).isEqualTo(Wishlist.Status.Active);
        assertThat(cap.getValue().getPurchasedAt()).isNull();
    }

    // =================================================================
    // UNIQUE collision path — INSERT throws DataIntegrityViolationException
    // → service catches + re-reads winning row + 200 alreadyExists=true.
    // Matches AC-15 scenario.
    // =================================================================
    @Test
    @DisplayName("add translates UNIQUE collision to 200 alreadyExists=true (AC-15)")
    void addCatchesUniqueCollision() {
        when(persistence.userExists("u1")).thenReturn(true);
        when(persistence.findFurniture("f_desk_001")).thenReturn(Optional.of(furniture("f_desk_001")));

        // First call — no existing row (pre-insert check).
        // Second call (after DIV) — winning row has materialised.
        Wishlist winner = wishlist("w1", "u1", "f_desk_001", Wishlist.Status.Active);
        when(persistence.findByUserIdAndFurnitureId("u1", "f_desk_001"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));

        when(persistence.insert(any()))
                .thenThrow(new DataIntegrityViolationException("uq_wishlist_user_furniture"));

        WishlistService.Outcome<WishlistItemResponse> out = service.add(
                "u1", new AddWishlistRequest("f_desk_001", "desk", 189000));

        assertThat(out.created()).isFalse();
        assertThat(out.body().alreadyExists()).isTrue();
        assertThat(out.body().wishlistId()).isEqualTo("w1");
        verify(persistence, times(2)).findByUserIdAndFurnitureId("u1", "f_desk_001");
    }

    // =================================================================
    // Invalid payload — category outside the 4-value enum
    // =================================================================
    @Test
    @DisplayName("add with category='sofa' → INVALID_WISHLIST_PAYLOAD (AC-12)")
    void addRejectsUnknownCategory() {
        when(persistence.userExists("u1")).thenReturn(true);
        assertThatThrownBy(() ->
                service.add("u1", new AddWishlistRequest("f_desk_001", "sofa", 100)))
                .isInstanceOf(WishlistException.class)
                .extracting(e -> ((WishlistException) e).code())
                .isEqualTo(WishlistErrorCode.INVALID_WISHLIST_PAYLOAD);
    }

    // =================================================================
    // PATCH — idempotent no-op (AC-24)
    // =================================================================
    @Test
    @DisplayName("patch with same target status is a no-op (does not call updateStatus)")
    void patchNoOp() {
        Wishlist row = wishlist("w1", "u1", "f_desk_001", Wishlist.Status.Active);
        when(persistence.findByWishlistIdAndUserId("w1", "u1")).thenReturn(Optional.of(row));
        when(persistence.findFurniture("f_desk_001")).thenReturn(Optional.of(furniture("f_desk_001")));

        WishlistItemResponse resp = service.patch("u1", "w1", "ACTIVE");
        assertThat(resp.status()).isEqualTo("Active");
        verify(persistence, times(0)).updateStatus(any(), any(), any());

        assertThat(infoLogs())
                .anyMatch(s -> s.contains("op=patch")
                            && s.contains("noop=true")
                            && s.contains("fromStatus=Active")
                            && s.contains("toStatus=Active"));
    }

    // =================================================================
    // PATCH — Active → Purchased sets purchasedAt (AC-22)
    // =================================================================
    @Test
    @DisplayName("patch ACTIVE→PURCHASED sets purchasedAt to now (AC-22)")
    void patchActiveToPurchased() {
        Wishlist row = wishlist("w1", "u1", "f_desk_001", Wishlist.Status.Active);
        when(persistence.findByWishlistIdAndUserId("w1", "u1")).thenReturn(Optional.of(row));
        when(persistence.findFurniture("f_desk_001")).thenReturn(Optional.of(furniture("f_desk_001")));

        Wishlist updated = wishlist("w1", "u1", "f_desk_001", Wishlist.Status.Purchased);
        updated.setPurchasedAt(LocalDateTime.now());

        ArgumentCaptor<LocalDateTime> ts = ArgumentCaptor.forClass(LocalDateTime.class);
        when(persistence.updateStatus(eq("w1"), eq(Wishlist.Status.Purchased), ts.capture()))
                .thenReturn(updated);

        WishlistItemResponse resp = service.patch("u1", "w1", "PURCHASED");

        assertThat(resp.status()).isEqualTo("Purchased");
        assertThat(resp.purchasedAt()).isNotNull();
        assertThat(ts.getValue()).isNotNull();
    }

    // =================================================================
    // PATCH — Purchased → Active clears purchasedAt (AC-23)
    // =================================================================
    @Test
    @DisplayName("patch PURCHASED→ACTIVE clears purchasedAt to null (AC-23)")
    void patchPurchasedToActive() {
        Wishlist row = wishlist("w1", "u1", "f_desk_001", Wishlist.Status.Purchased);
        row.setPurchasedAt(LocalDateTime.now().minusDays(1));
        when(persistence.findByWishlistIdAndUserId("w1", "u1")).thenReturn(Optional.of(row));
        when(persistence.findFurniture("f_desk_001")).thenReturn(Optional.of(furniture("f_desk_001")));

        Wishlist updated = wishlist("w1", "u1", "f_desk_001", Wishlist.Status.Active);
        // purchasedAt must be null after transition.
        when(persistence.updateStatus(eq("w1"), eq(Wishlist.Status.Active), eq(null)))
                .thenReturn(updated);

        WishlistItemResponse resp = service.patch("u1", "w1", "ACTIVE");
        assertThat(resp.status()).isEqualTo("Active");
        assertThat(resp.purchasedAt()).isNull();
    }

    // =================================================================
    // PATCH — invalid transition (AC-25)
    // =================================================================
    @Test
    @DisplayName("patch with unknown status → INVALID_STATE_TRANSITION (AC-25)")
    void patchInvalidTransition() {
        assertThatThrownBy(() -> service.patch("u1", "w1", "PENDING"))
                .isInstanceOf(WishlistException.class)
                .extracting(e -> ((WishlistException) e).code())
                .isEqualTo(WishlistErrorCode.INVALID_STATE_TRANSITION);
    }

    // =================================================================
    // AC-31 — logsShape
    // =================================================================
    @Test
    @DisplayName("AC-31: logsShape — add/patch/delete each emit one INFO line with the expected fields")
    void logsShape_ac31() {
        // add
        when(persistence.userExists("u1")).thenReturn(true);
        when(persistence.findFurniture("f1")).thenReturn(Optional.of(furniture("f1")));
        when(persistence.findByUserIdAndFurnitureId("u1", "f1")).thenReturn(Optional.empty());
        when(persistence.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        service.add("u1", new AddWishlistRequest("f1", "desk", 100));

        // patch (Active → Purchased)
        Wishlist row = wishlist("w1", "u1", "f1", Wishlist.Status.Active);
        when(persistence.findByWishlistIdAndUserId("w1", "u1")).thenReturn(Optional.of(row));
        Wishlist updated = wishlist("w1", "u1", "f1", Wishlist.Status.Purchased);
        updated.setPurchasedAt(LocalDateTime.now());
        when(persistence.updateStatus(eq("w1"), eq(Wishlist.Status.Purchased), any()))
                .thenReturn(updated);
        service.patch("u1", "w1", "PURCHASED");

        // delete
        Wishlist del = wishlist("w2", "u1", "f1", Wishlist.Status.Active);
        when(persistence.findByWishlistIdAndUserId("w2", "u1")).thenReturn(Optional.of(del));
        service.delete("u1", "w2");

        var lines = infoLogs();
        assertThat(lines).anyMatch(s -> s.contains("op=add")    && s.contains("userId=u1") && s.contains("furnitureId=f1"));
        assertThat(lines).anyMatch(s -> s.contains("op=patch")  && s.contains("userId=u1") && s.contains("wishlistId=w1")
                                     && s.contains("fromStatus=Active") && s.contains("toStatus=Purchased"));
        assertThat(lines).anyMatch(s -> s.contains("op=delete") && s.contains("userId=u1") && s.contains("wishlistId=w2"));
    }

    // =================================================================
    // helpers
    // =================================================================

    private java.util.List<String> infoLogs() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static Wishlist wishlist(String wid, String uid, String fid, Wishlist.Status st) {
        Wishlist w = new Wishlist();
        w.setWishlistId(wid);
        w.setUserId(uid);
        w.setFurnitureId(fid);
        w.setCategory("desk");
        w.setPrice(100);
        w.setStatus(st);
        return w;
    }

    private static Furniture furniture(String id) {
        Furniture f = new Furniture();
        f.setFurnitureId(id);
        f.setName("Sample " + id);
        f.setType("desk");
        f.setStyle("MODERN");
        f.setSize("M");
        f.setPrice(100);
        f.setColorHex("#CCCCCC");
        f.setWidthCm(100);
        f.setLengthCm(100);
        f.setHeightCm(100);
        f.setStyleTags("MODERN");
        return f;
    }
}
