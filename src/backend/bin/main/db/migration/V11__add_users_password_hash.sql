-- =====================================================================
-- AuthenticSelf V11 — add users.password_hash for BCrypt-backed login
-- PRD refs: §3 DB design, §2 Overview (auth posture)
-- UC-SECURE-AUTH FR-6 / FR-7 / FR-8 / AC-7 / AC-8 / AC-19
--
-- Adds a NOT NULL `password_hash` column to the V1 `users` table so the
-- demo login flow can verify credentials against a BCrypt digest instead
-- of the previous hardcoded `1234` map (AuthService base64 stub).
--
-- Three steps inside this one file:
--   (1) ADD COLUMN password_hash VARCHAR(72) NULL AFTER role
--       — NULL initially so any pre-existing row survives the ALTER.
--   (2) UPDATE existing rows to the literal placeholder sentinel
--       '__BOOTSTRAP_PLACEHOLDER__'. DemoUserBootstrap overwrites this
--       placeholder on next boot. The placeholder is intentionally NOT
--       a valid BCrypt hash so no login attempt against an un-bootstrapped
--       row can succeed — BCryptPasswordEncoder.matches(..., placeholder)
--       always returns false, closing the security gap even if the
--       bootstrap step is silently skipped.
--   (3) MODIFY COLUMN ... NOT NULL — DB-level enforcement so any future
--       INSERT that omits the field fails fast (NOT NULL invariant).
--
-- Column length = 72: BCrypt's `$2a$10$<22-char-salt><31-char-digest>`
-- output is exactly 60 chars; 72 leaves headroom for the `$2b$` / `$2y$`
-- variants and a future strength-bump prefix without another ALTER.
--
-- V1..V10 migration files are NOT touched (AC-19 regression guard).
-- =====================================================================

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(72) NULL AFTER role;

UPDATE users
    SET password_hash = '__BOOTSTRAP_PLACEHOLDER__'
    WHERE password_hash IS NULL;

ALTER TABLE users
    MODIFY COLUMN password_hash VARCHAR(72) NOT NULL;
