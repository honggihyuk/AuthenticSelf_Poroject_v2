-- V10 — Phase B cache table.
--
-- One row per (curated furniture, external candidate). Populated by
-- SimilarProductsBatch nightly: for each curated row, query Naver
-- Shopping for similar products and upsert the top-N into this table.
--
-- The read path (GET /api/v1/furniture/{id}/similar) is a single
-- index lookup on (furniture_id, similarity_score DESC, fetched_at DESC).
--
-- similarity_score is on [0.0, 1.0]. Phase B Step 1 deliberately stores
-- a constant 1.0 — Naver's own search ranking is used as the ordering
-- until CLIP scoring lands in a later iteration.

CREATE TABLE furniture_similar_cache (
    furniture_id      VARCHAR(64)   NOT NULL,
    source            VARCHAR(32)   NOT NULL,                                   -- e.g. 'NAVER'
    external_id       VARCHAR(128)  NOT NULL,                                   -- Naver productId
    rank_order        INT           NOT NULL,                                   -- 1..N within (furniture_id, source)
    title             VARCHAR(512)  NOT NULL,
    external_url      VARCHAR(1024) NOT NULL,
    image_url         VARCHAR(1024) NOT NULL,
    price             INT           NULL,
    mall_name         VARCHAR(255)  NULL,
    similarity_score  DECIMAL(4, 3) NOT NULL DEFAULT 1.000,
    fetched_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        TIMESTAMP     NOT NULL,

    PRIMARY KEY (furniture_id, source, external_id),
    KEY ix_similar_lookup (furniture_id, similarity_score DESC, rank_order),
    CONSTRAINT fk_similar_furniture
        FOREIGN KEY (furniture_id) REFERENCES furniture (furniture_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
