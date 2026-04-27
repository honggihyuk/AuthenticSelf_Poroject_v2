package com.authenticself.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA mapping for the {@code wishlist} table (PRD §3, §7, §6 UC-02).
 * <p>
 * {@link Status} mirrors the native MySQL {@code ENUM('Active','Purchased')};
 * default value {@code Active} is enforced at the DB level (see V1 migration).
 *
 * <p>UC-02-wishlist (Task 6) FR-2 / AC-5 extensions:
 * <ul>
 *   <li>{@link #addedAt} — {@code DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP}
 *       (V6). Immutable from JPA ({@code insertable=false, updatable=false});
 *       the DB default populates it on insert.</li>
 *   <li>{@link #purchasedAt} — {@code DATETIME NULL} (V6). Writable by the
 *       service: set on {@code Active → Purchased}, cleared on
 *       {@code Purchased → Active}.</li>
 * </ul>
 *
 * <p>V1 column mappings ({@code wishlistId}, {@code userId}, {@code furnitureId},
 * {@code category}, {@code price}, {@code status}, {@code createdAt},
 * {@code updatedAt}) remain present with identical names — the extension is
 * purely additive per FR-14 / AC-51.
 */
@Entity
@Table(name = "wishlist")
public class Wishlist {

    public enum Status {
        Active,
        Purchased
    }

    @Id
    @Column(name = "wishlist_id", nullable = false, length = 64)
    private String wishlistId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "furniture_id", nullable = false, length = 64)
    private String furnitureId;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('Active','Purchased')")
    private Status status;

    // V6 — immutable "first added" timestamp. DB default populates it; JPA
    // must never write it (insertable=false, updatable=false) so the column
    // round-trips safely through hibernate.ddl-auto=validate.
    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime addedAt;

    // V6 — nullable "transitioned to Purchased" timestamp. Writable so the
    // service can set it on Active → Purchased and clear it on
    // Purchased → Active.
    @Column(name = "purchased_at")
    private LocalDateTime purchasedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // ---------- accessors ------------------------------------------------

    public String getWishlistId()           { return wishlistId; }
    public void   setWishlistId(String v)   { this.wishlistId = v; }

    public String getUserId()           { return userId; }
    public void   setUserId(String v)   { this.userId = v; }

    public String getFurnitureId()           { return furnitureId; }
    public void   setFurnitureId(String v)   { this.furnitureId = v; }

    public String getCategory()           { return category; }
    public void   setCategory(String v)   { this.category = v; }

    public Integer getPrice()           { return price; }
    public void    setPrice(Integer v)  { this.price = v; }

    public Status getStatus()           { return status; }
    public void   setStatus(Status v)   { this.status = v; }

    public LocalDateTime getAddedAt()          { return addedAt; }
    // No setter for addedAt — DB default + insertable=false/updatable=false
    // make the column read-only from JPA's perspective (AC-5).

    public LocalDateTime getPurchasedAt()                { return purchasedAt; }
    public void          setPurchasedAt(LocalDateTime v) { this.purchasedAt = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
