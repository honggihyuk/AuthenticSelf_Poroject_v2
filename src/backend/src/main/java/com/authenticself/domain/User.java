package com.authenticself.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA mapping for the {@code users} table (PRD §3, §7).
 * <p>
 * Task 1 (DB-schema-init) seeded only the identity columns ({@code userId},
 * {@code name}, {@code email}) plus {@code createdAt} / {@code updatedAt}.
 * Task 7 (UC-03-admin-overview) V7 migration adds the {@link Role} column —
 * strictly additive, no change to any pre-existing field's mapping
 * (UC-03-admin-overview AC-6 / AC-48).
 *
 * <p>Default role on a fresh instance is {@link Role#USER} so unit tests
 * that construct a {@code User} POJO do not NPE and so the JPA-merged
 * column value is consistent with the DB default (V7's
 * {@code ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'}).
 */
@Entity
@Table(name = "users")
public class User {

    /**
     * Two-value enum backing the {@code users.role} column introduced in
     * V7. {@link Role#ADMIN} is granted only by the manual bootstrap SQL
     * documented in {@code V7__add_users_role.sql}.
     *
     * <p>Mirrors {@link com.authenticself.domain.Space.Status} — the enum
     * constant names are the wire-format literals persisted verbatim by
     * JPA ({@link EnumType#STRING}).
     */
    public enum Role {
        USER,
        ADMIN
    }

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    // UC-03-admin-overview (Task 7) FR-2 / AC-6 — role column added in V7.
    // Default value on a fresh Java instance is Role.USER so constructors
    // that do not explicitly set the role still produce a valid row.
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16,
            columnDefinition = "ENUM('USER','ADMIN')")
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // ---------- accessors ------------------------------------------------

    public String getUserId()           { return userId; }
    public void   setUserId(String v)   { this.userId = v; }

    public String getName()             { return name; }
    public void   setName(String v)     { this.name = v; }

    public String getEmail()            { return email; }
    public void   setEmail(String v)    { this.email = v; }

    public Role getRole()               { return role; }
    public void setRole(Role v)         { this.role = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
