-- =====================================================================
-- AuthenticSelf V4 — extend furniture catalog for UC-01-recommendation
-- PRD refs: §3 DB design, §6 UC-01 step 5
--
-- Adds the eight columns required by the scorer (FR-4 / FR-15):
--   name, price, image_url, color_hex, width_cm, length_cm, height_cm,
--   style_tags
--
-- Defaults on NOT NULL columns exist only so the ALTER succeeds on
-- already-seeded dev/test DBs — V5 immediately replaces every row.
--
-- style_tags is a comma-separated list (e.g. "MODERN,SCANDINAVIAN").
-- VARCHAR keeps the dev story simple and matches the Task-1 convention
-- of VARCHAR-heavy columns.
--
-- price is INT (KRW, integer won) — matches Task-1 wishlist.price INT.
-- color_hex is VARCHAR(7) to hold "#RRGGBB".
-- =====================================================================

ALTER TABLE furniture
    ADD COLUMN name         VARCHAR(100)  NOT NULL DEFAULT ''         AFTER furniture_id,
    ADD COLUMN price        INT           NOT NULL DEFAULT 0          AFTER size,
    ADD COLUMN image_url    VARCHAR(512)  NULL                        AFTER price,
    ADD COLUMN color_hex    VARCHAR(7)    NOT NULL DEFAULT '#CCCCCC'  AFTER image_url,
    ADD COLUMN width_cm     INT           NOT NULL DEFAULT 0          AFTER color_hex,
    ADD COLUMN length_cm    INT           NOT NULL DEFAULT 0          AFTER width_cm,
    ADD COLUMN height_cm    INT           NOT NULL DEFAULT 0          AFTER length_cm,
    ADD COLUMN style_tags   VARCHAR(255)  NOT NULL DEFAULT ''         AFTER height_cm;
