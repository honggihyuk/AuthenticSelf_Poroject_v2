-- V8: Phase A — curated 3D model URLs for AR placement.
-- model_url is nullable; rows without a 3D asset still render their 2D image_url.
ALTER TABLE furniture
    ADD COLUMN model_url VARCHAR(512) NULL AFTER image_url;
