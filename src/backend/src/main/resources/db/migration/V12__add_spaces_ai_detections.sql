-- =====================================================================
-- AuthenticSelf V12 — add spaces.ai_detections for persisted YOLO output
-- PRD refs: §6 UC-01 step 2 / step 8, §6 UC-03 (admin Rooms detail), §3 DB
-- UC-ML-PERSIST FR-4 / AC-1 / AC-2
--
-- Persists the YOLO furniture detections that POST /analyze/space already
-- computes (but previously discarded) so the recommender can cross-validate
-- against detected objects (UC-01 step 8) and the admin debug view can render
-- a bounding-box overlay (UC-03).
--
-- One single, nullable JSON column. The backend writes a self-describing
-- envelope:
--   { "imageWidth": 1280, "imageHeight": 960,
--     "detections": [ { "label": "chair", "bbox": [x1,y1,x2,y2], "confidence": 0.91 } ] }
-- so bbox normalization is reproducible without re-reading the image.
--
-- Backward compatibility (AC-2): the column is NULL for every pre-existing
-- row. No backfill, no DEFAULT, no change to any other column. NULL is a
-- first-class state meaning "no persisted detections" — the recommender maps
-- it to an empty detectedObjects list (identical to the prior hardcoded []),
-- and the admin overlay renders an empty-state hint.
--
-- V1..V11 migration files are NOT touched.
-- =====================================================================

ALTER TABLE spaces
    ADD COLUMN ai_detections JSON NULL AFTER analysis_date;
