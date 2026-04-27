-- =====================================================================
-- AuthenticSelf V2 — add upload columns + analysis-state columns to spaces
-- PRD refs: §6 UC-01 step 1 (upload) vs step 2 (analysis),
--           §9 architecture (AI orchestrator reads status='PENDING_ANALYSIS')
--
-- This migration extends the Task-1 `spaces` table to support the
-- two lifecycle phases: (1) uploaded, (2) analyzed. The four original
-- analysis columns become NULLable because they are only populated
-- during UC-01-space-analysis.
-- =====================================================================

-- New columns (upload-time metadata) ----------------------------------
ALTER TABLE spaces
    ADD COLUMN photo_url         VARCHAR(512) NOT NULL                              AFTER user_id,
    ADD COLUMN original_filename VARCHAR(255) NULL                                  AFTER photo_url,
    ADD COLUMN content_type      VARCHAR(64)  NOT NULL                              AFTER original_filename,
    ADD COLUMN file_size_bytes   BIGINT       NOT NULL                              AFTER content_type,
    ADD COLUMN status            ENUM('PENDING_ANALYSIS','ANALYZED','FAILED')
                                 NOT NULL DEFAULT 'PENDING_ANALYSIS'                AFTER file_size_bytes,
    ADD COLUMN uploaded_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP    AFTER status;

-- Relax analysis columns: they're only populated after UC-01-space-analysis -------
ALTER TABLE spaces MODIFY COLUMN dimensions    VARCHAR(100) NULL;
ALTER TABLE spaces MODIFY COLUMN main_color    VARCHAR(32)  NULL;
ALTER TABLE spaces MODIFY COLUMN style         VARCHAR(64)  NULL;
ALTER TABLE spaces MODIFY COLUMN analysis_date DATETIME     NULL;

-- Index on status so the AI orchestrator can efficiently poll
-- `WHERE status='PENDING_ANALYSIS'`.
CREATE INDEX idx_spaces_status ON spaces (status);
