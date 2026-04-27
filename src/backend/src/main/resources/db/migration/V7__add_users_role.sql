-- =====================================================================
-- AuthenticSelf V7 — add users.role for admin gating
-- PRD refs: §2 Overview, §3 DB design, §6 UC-03 (admin dashboard)
-- UC-03-admin-overview FR-1 / AC-1 / AC-2 / AC-3 / AC-4 / AC-5
--
-- Adds a role ENUM column to the V1 `users` table. The new column carries
-- the two-value set {'USER','ADMIN'} natively at the MySQL level so any
-- non-enum literal insert is rejected by the engine (AC-3) — the same
-- defence the `wishlist.status` and `spaces.status` ENUMs already give.
--
-- Columns:
--   role   ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'
--       — every existing row gets 'USER' (the DB default is applied via
--         the ALTER below); every new row without an explicit role also
--         gets 'USER'. 'ADMIN' is a product-visible role granted only by
--         the manual bootstrap documented below.
--
-- Index:
--   idx_users_role (role)
--       — supports the per-request role check behind the admin gate and
--         the role-scoped aggregations in AdminOverviewRepository (the
--         Users tile filters by role). Volume is tiny today; an index is
--         still cheap and consistent with the idx_wishlist_user_id style.
--
-- -----------------------------------------------------------------------
-- ADMIN BOOTSTRAP (do NOT auto-seed — run manually, per D-1):
-- -----------------------------------------------------------------------
--   UPDATE users SET role='ADMIN' WHERE email='<operator@example.com>';
-- -----------------------------------------------------------------------
-- The operator runs the statement above (replacing the email literal)
-- against the production DB to grant the first admin. This migration
-- MUST NOT insert any fixture user — PII in VCS is explicitly out of
-- scope. A test-only seed is provided under
-- src/backend/src/test/resources/ for integration tests.
--
-- V1..V6 migration files are NOT touched (AC-5 / AC-47 regression guard).
-- =====================================================================

ALTER TABLE users
    ADD COLUMN role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' AFTER email;

-- Belt-and-braces backfill: the DB-level DEFAULT above already assigns
-- 'USER' to existing rows at ALTER time, but an explicit UPDATE makes the
-- migration idempotent under any edge case where a future MySQL version
-- interprets the DEFAULT differently. This is a no-op today.
UPDATE users SET role = 'USER' WHERE role IS NULL;

CREATE INDEX idx_users_role ON users(role);
