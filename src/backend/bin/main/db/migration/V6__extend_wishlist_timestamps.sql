-- =====================================================================
-- AuthenticSelf V6 — extend wishlist with state-machine timestamps
-- PRD refs: §3 DB design, §6 UC-02 step 5 (Active → Purchased transition)
-- UC-02-wishlist FR-1 / AC-1 / AC-2 / AC-3 / AC-52
--
-- Adds two DATETIME columns to the V1 `wishlist` table + one UNIQUE
-- constraint on (user_id, furniture_id) that is the concurrency
-- primitive behind the service's idempotent add (FR-4 + AC-15).
--
-- Columns:
--   added_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
--       — records when the row first entered the wishlist. Immutable
--         for the lifetime of the row (service never writes it;
--         DB default handles the initial value).
--   purchased_at  DATETIME NULL
--       — set when the row transitions to status='Purchased'; cleared
--         to NULL when transitioning back to 'Active' (FR-7).
--
-- Unique:
--   uq_wishlist_user_furniture (user_id, furniture_id)
--       — enforces "same user cannot double-add the same furniture".
--         Parallel POSTs for the same pair resolve to a single row;
--         the losing insert surfaces as DataIntegrityViolationException
--         which the service catches and translates to a 200 idempotent
--         response returning the existing row (AC-8 / AC-15).
--
-- V1..V5 migration files are NOT touched (AC-4 / AC-51 regression guard).
-- =====================================================================

ALTER TABLE wishlist
    ADD COLUMN added_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status,
    ADD COLUMN purchased_at DATETIME NULL                               AFTER added_at;

-- Seed added_at for any pre-existing rows from their created_at, so
-- analytics queries that use added_at do not see a wall-clock jump for
-- rows that were inserted before V6.
UPDATE wishlist
   SET added_at = created_at
 WHERE added_at IS NULL OR added_at > created_at;

ALTER TABLE wishlist
    ADD CONSTRAINT uq_wishlist_user_furniture UNIQUE (user_id, furniture_id);
