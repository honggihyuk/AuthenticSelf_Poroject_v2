-- =====================================================================
-- AuthenticSelf V3 — add spaces.preferred_style
-- PRD refs: §6 UC-01 step 3 ("스타일 설정"), §3 DB design
--
-- UC-01-style-selection (Task 4) FR-9 / AC-9:
-- The new column stores the user's chosen target style. NULL means the
-- user has not yet selected a style. The AI-detected style lives in
-- `spaces.style` (unchanged by this migration); `preferred_style` is
-- the user-choice side of the pairing and is written only via the
-- public `PUT /api/v1/spaces/{roomId}/preferred-style` endpoint.
--
-- VARCHAR(32) covers the longest enum value (SCANDINAVIAN = 12 chars)
-- with generous headroom for future additions. No index — the column
-- is only accessed via primary-key lookups by `room_id`.
-- =====================================================================

ALTER TABLE spaces
    ADD COLUMN preferred_style VARCHAR(32) NULL AFTER style;
