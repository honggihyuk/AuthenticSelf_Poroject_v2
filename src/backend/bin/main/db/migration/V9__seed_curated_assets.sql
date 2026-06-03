-- V9 — Phase A asset pipeline seed.
--
-- Points every catalog row at /static/furniture/<furniture_id>.{jpg,glb}.
-- The files are physically served by `StaticAssetsWebConfig` from
-- src/backend/var/static/furniture/. Until a real Sketchfab CC0 model is
-- dropped into place, each row points at a per-row PLACEHOLDER asset
-- (auto-generated JPG + type-matched stub GLB).
--
-- ## Asset swap checklist (do in any order, one row at a time)
--
-- For each row below:
--   1) Drop the new <id>.glb into raw_models/ (any size — Sketchfab raw is OK)
--   2) Run: scripts/compress-glb.sh raw_models/<id>.glb
--   3) Move the resulting <id>.compressed.glb to src/backend/var/static/furniture/<id>.glb
--   4) Replace src/backend/var/static/furniture/<id>.jpg with a real render
--   5) (no DB change needed — the URL is already pointing at the right path)
--
-- The 099-suffixed rows (Emperor / Throne / Conference / Atrium) are
-- deliberately oversized stress-test entries from the original seed.
-- They follow the same swap procedure.

UPDATE furniture SET image_url='/static/furniture/f_bed_001.jpg',      model_url='/static/furniture/f_bed_001.glb'      WHERE furniture_id='f_bed_001';
UPDATE furniture SET image_url='/static/furniture/f_bed_002.jpg',      model_url='/static/furniture/f_bed_002.glb'      WHERE furniture_id='f_bed_002';
UPDATE furniture SET image_url='/static/furniture/f_bed_003.jpg',      model_url='/static/furniture/f_bed_003.glb'      WHERE furniture_id='f_bed_003';
UPDATE furniture SET image_url='/static/furniture/f_bed_004.jpg',      model_url='/static/furniture/f_bed_004.glb'      WHERE furniture_id='f_bed_004';
UPDATE furniture SET image_url='/static/furniture/f_bed_005.jpg',      model_url='/static/furniture/f_bed_005.glb'      WHERE furniture_id='f_bed_005';
UPDATE furniture SET image_url='/static/furniture/f_bed_006.jpg',      model_url='/static/furniture/f_bed_006.glb'      WHERE furniture_id='f_bed_006';
UPDATE furniture SET image_url='/static/furniture/f_bed_099.jpg',      model_url='/static/furniture/f_bed_099.glb'      WHERE furniture_id='f_bed_099';

UPDATE furniture SET image_url='/static/furniture/f_chair_001.jpg',    model_url='/static/furniture/f_chair_001.glb'    WHERE furniture_id='f_chair_001';
UPDATE furniture SET image_url='/static/furniture/f_chair_002.jpg',    model_url='/static/furniture/f_chair_002.glb'    WHERE furniture_id='f_chair_002';
UPDATE furniture SET image_url='/static/furniture/f_chair_003.jpg',    model_url='/static/furniture/f_chair_003.glb'    WHERE furniture_id='f_chair_003';
UPDATE furniture SET image_url='/static/furniture/f_chair_004.jpg',    model_url='/static/furniture/f_chair_004.glb'    WHERE furniture_id='f_chair_004';
UPDATE furniture SET image_url='/static/furniture/f_chair_005.jpg',    model_url='/static/furniture/f_chair_005.glb'    WHERE furniture_id='f_chair_005';
UPDATE furniture SET image_url='/static/furniture/f_chair_006.jpg',    model_url='/static/furniture/f_chair_006.glb'    WHERE furniture_id='f_chair_006';
UPDATE furniture SET image_url='/static/furniture/f_chair_099.jpg',    model_url='/static/furniture/f_chair_099.glb'    WHERE furniture_id='f_chair_099';

UPDATE furniture SET image_url='/static/furniture/f_desk_001.jpg',     model_url='/static/furniture/f_desk_001.glb'     WHERE furniture_id='f_desk_001';
UPDATE furniture SET image_url='/static/furniture/f_desk_002.jpg',     model_url='/static/furniture/f_desk_002.glb'     WHERE furniture_id='f_desk_002';
UPDATE furniture SET image_url='/static/furniture/f_desk_003.jpg',     model_url='/static/furniture/f_desk_003.glb'     WHERE furniture_id='f_desk_003';
UPDATE furniture SET image_url='/static/furniture/f_desk_004.jpg',     model_url='/static/furniture/f_desk_004.glb'     WHERE furniture_id='f_desk_004';
UPDATE furniture SET image_url='/static/furniture/f_desk_005.jpg',     model_url='/static/furniture/f_desk_005.glb'     WHERE furniture_id='f_desk_005';
UPDATE furniture SET image_url='/static/furniture/f_desk_006.jpg',     model_url='/static/furniture/f_desk_006.glb'     WHERE furniture_id='f_desk_006';
UPDATE furniture SET image_url='/static/furniture/f_desk_099.jpg',     model_url='/static/furniture/f_desk_099.glb'     WHERE furniture_id='f_desk_099';

UPDATE furniture SET image_url='/static/furniture/f_lighting_001.jpg', model_url='/static/furniture/f_lighting_001.glb' WHERE furniture_id='f_lighting_001';
UPDATE furniture SET image_url='/static/furniture/f_lighting_002.jpg', model_url='/static/furniture/f_lighting_002.glb' WHERE furniture_id='f_lighting_002';
UPDATE furniture SET image_url='/static/furniture/f_lighting_003.jpg', model_url='/static/furniture/f_lighting_003.glb' WHERE furniture_id='f_lighting_003';
UPDATE furniture SET image_url='/static/furniture/f_lighting_004.jpg', model_url='/static/furniture/f_lighting_004.glb' WHERE furniture_id='f_lighting_004';
UPDATE furniture SET image_url='/static/furniture/f_lighting_005.jpg', model_url='/static/furniture/f_lighting_005.glb' WHERE furniture_id='f_lighting_005';
UPDATE furniture SET image_url='/static/furniture/f_lighting_006.jpg', model_url='/static/furniture/f_lighting_006.glb' WHERE furniture_id='f_lighting_006';
UPDATE furniture SET image_url='/static/furniture/f_lighting_099.jpg', model_url='/static/furniture/f_lighting_099.glb' WHERE furniture_id='f_lighting_099';
