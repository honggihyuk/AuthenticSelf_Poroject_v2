-- =====================================================================
-- AuthenticSelf V5 — seed furniture catalog
-- PRD refs: §3 DB design, §6 UC-01 step 5
--
-- Seeds >= 24 rows (6 per category x 4 categories = 24; this migration
-- ships 28 rows to cover edge-case branches of the scorer):
--
--   * at least one MODERN / SIMPLE / CLASSIC / SCANDINAVIAN / INDUSTRIAL
--     + one multi-tag row per category (FR-16 style coverage).
--   * at least one oversize row per category (max(width_cm,length_cm) > 400)
--     so the FR-5 hard-fit-exclusion branch is covered (AC-26).
--   * at least one row per category whose colorHex has hue delta > 120
--     from a typical beige room (#E8D9B0) so the FR-6 "clashing" branch
--     is covered.
--   * prices vary within each category so the FR-9 tie-breakers are
--     meaningful.
--   * furniture_id values follow f_<category>_<nnn> with left-padded 3
--     digit suffix (FR-16) for lexicographic-tie-break consistency.
--
-- Every PreferredStyle enum value (except CURRENT) appears in at least
-- one row's style_tags across the whole catalog (AC-26b).
-- =====================================================================

-- ---------------------------------------------------------------------
-- DESK (7 rows incl. 1 oversize, 1 clashing color)
-- ---------------------------------------------------------------------
INSERT INTO furniture
    (furniture_id, name, type, style, size, price, image_url, color_hex,
     width_cm, length_cm, height_cm, style_tags)
VALUES
    ('f_desk_001', 'Oslo Slim Desk',       'desk', 'MODERN',       '120x60x74',
     189000, 'https://cdn.example.com/f_desk_001.jpg', '#F3E6D2',
     120, 60, 74,  'MODERN'),
    ('f_desk_002', 'Bergen Scandi Desk',   'desk', 'SCANDINAVIAN', '140x70x74',
     215000, 'https://cdn.example.com/f_desk_002.jpg', '#E8D9B0',
     140, 70, 74,  'SCANDINAVIAN'),
    ('f_desk_003', 'Victorian Oak Desk',   'desk', 'CLASSIC',      '160x80x76',
     310000, 'https://cdn.example.com/f_desk_003.jpg', '#5A3A1E',
     160, 80, 76,  'CLASSIC'),
    ('f_desk_004', 'Foundry Iron Desk',    'desk', 'INDUSTRIAL',   '140x70x74',
     245000, 'https://cdn.example.com/f_desk_004.jpg', '#3C3C3C',
     140, 70, 74,  'INDUSTRIAL'),
    ('f_desk_005', 'White Minimal Desk',   'desk', 'SIMPLE',       '100x55x72',
     155000, 'https://cdn.example.com/f_desk_005.jpg', '#FFFFFF',
     100, 55, 72,  'SIMPLE'),
    ('f_desk_006', 'Aarhus Multi Desk',    'desk', 'MODERN',       '130x65x74',
     205000, 'https://cdn.example.com/f_desk_006.jpg', '#DED2B0',
     130, 65, 74,  'MODERN,SCANDINAVIAN'),
    -- oversize (hard-fit branch) + clashing color (hueDelta > 120 vs #E8D9B0)
    ('f_desk_099', 'Conference Giant Desk','desk', 'INDUSTRIAL',   '420x120x78',
     890000, 'https://cdn.example.com/f_desk_099.jpg', '#106090',
     420, 120, 78, 'INDUSTRIAL');

-- ---------------------------------------------------------------------
-- BED (7 rows incl. 1 oversize, 1 clashing color)
-- ---------------------------------------------------------------------
INSERT INTO furniture
    (furniture_id, name, type, style, size, price, image_url, color_hex,
     width_cm, length_cm, height_cm, style_tags)
VALUES
    ('f_bed_001',  'Aurora Modern Bed',    'bed',  'MODERN',       '220x160x90',
     680000, 'https://cdn.example.com/f_bed_001.jpg',  '#EDE0C8',
     220, 160, 90,  'MODERN'),
    ('f_bed_002',  'Fjord Scandi Bed',     'bed',  'SCANDINAVIAN', '210x150x85',
     720000, 'https://cdn.example.com/f_bed_002.jpg',  '#F2E8D2',
     210, 150, 85,  'SCANDINAVIAN'),
    ('f_bed_003',  'Heritage Frame Bed',   'bed',  'CLASSIC',      '230x170x110',
     890000, 'https://cdn.example.com/f_bed_003.jpg',  '#5A3A1E',
     230, 170, 110, 'CLASSIC'),
    ('f_bed_004',  'Forge Industrial Bed', 'bed',  'INDUSTRIAL',   '220x160x95',
     740000, 'https://cdn.example.com/f_bed_004.jpg',  '#2E2E2E',
     220, 160, 95,  'INDUSTRIAL'),
    ('f_bed_005',  'Linen Simple Bed',     'bed',  'SIMPLE',       '200x150x80',
     610000, 'https://cdn.example.com/f_bed_005.jpg',  '#F0F0F0',
     200, 150, 80,  'SIMPLE'),
    ('f_bed_006',  'Stockholm Hybrid Bed', 'bed',  'MODERN',       '220x160x85',
     665000, 'https://cdn.example.com/f_bed_006.jpg',  '#E2D6B4',
     220, 160, 85,  'MODERN,SIMPLE'),
    -- oversize + clashing color
    ('f_bed_099',  'Emperor Suite Bed',    'bed',  'CLASSIC',      '420x240x120',
     1890000, 'https://cdn.example.com/f_bed_099.jpg', '#0A7A88',
     420, 240, 120, 'CLASSIC');

-- ---------------------------------------------------------------------
-- CHAIR (7 rows incl. 1 oversize, 1 clashing color)
-- ---------------------------------------------------------------------
INSERT INTO furniture
    (furniture_id, name, type, style, size, price, image_url, color_hex,
     width_cm, length_cm, height_cm, style_tags)
VALUES
    ('f_chair_001', 'Noir Modern Chair',   'chair', 'MODERN',       '55x55x80',
     89000,  'https://cdn.example.com/f_chair_001.jpg', '#2B2B2B',
     55, 55, 80,  'MODERN'),
    ('f_chair_002', 'Nordic Light Chair',  'chair', 'SCANDINAVIAN', '60x60x82',
     99000,  'https://cdn.example.com/f_chair_002.jpg', '#F1E2C4',
     60, 60, 82,  'SCANDINAVIAN'),
    ('f_chair_003', 'Regency Arm Chair',   'chair', 'CLASSIC',      '58x58x92',
     125000, 'https://cdn.example.com/f_chair_003.jpg', '#5A3A1E',
     58, 58, 92,  'CLASSIC'),
    ('f_chair_004', 'Rivet Steel Chair',   'chair', 'INDUSTRIAL',   '55x55x86',
     119000, 'https://cdn.example.com/f_chair_004.jpg', '#202020',
     55, 55, 86,  'INDUSTRIAL'),
    ('f_chair_005', 'Blank Simple Chair',  'chair', 'SIMPLE',       '50x50x80',
     79000,  'https://cdn.example.com/f_chair_005.jpg', '#FFFFFF',
     50, 50, 80,  'SIMPLE'),
    ('f_chair_006', 'Studio Hybrid Chair', 'chair', 'MODERN',       '55x55x82',
     105000, 'https://cdn.example.com/f_chair_006.jpg', '#3A3A3A',
     55, 55, 82,  'MODERN,INDUSTRIAL'),
    -- oversize (longer side > 400) + clashing color
    ('f_chair_099','Throne Banquet Chair', 'chair', 'CLASSIC',      '420x90x120',
     490000, 'https://cdn.example.com/f_chair_099.jpg', '#0A7A88',
     420, 90, 120, 'CLASSIC');

-- ---------------------------------------------------------------------
-- LIGHTING (7 rows incl. 1 oversize (via height), 1 clashing color)
-- ---------------------------------------------------------------------
INSERT INTO furniture
    (furniture_id, name, type, style, size, price, image_url, color_hex,
     width_cm, length_cm, height_cm, style_tags)
VALUES
    ('f_lighting_001', 'Pola Modern Lamp',       'lighting', 'MODERN',       '35x35x160',
     135000, 'https://cdn.example.com/f_lighting_001.jpg', '#F4EAD0',
     35, 35, 160, 'MODERN'),
    ('f_lighting_002', 'Lyra Scandi Lamp',       'lighting', 'SCANDINAVIAN', '30x30x150',
     125000, 'https://cdn.example.com/f_lighting_002.jpg', '#FFFFFF',
     30, 30, 150, 'SCANDINAVIAN'),
    ('f_lighting_003', 'Empress Crystal Lamp',   'lighting', 'CLASSIC',      '40x40x170',
     165000, 'https://cdn.example.com/f_lighting_003.jpg', '#8A6A3A',
     40, 40, 170, 'CLASSIC'),
    ('f_lighting_004', 'Edison Cage Lamp',       'lighting', 'INDUSTRIAL',   '35x35x160',
     145000, 'https://cdn.example.com/f_lighting_004.jpg', '#2A2A2A',
     35, 35, 160, 'INDUSTRIAL'),
    ('f_lighting_005', 'Dot Simple Lamp',        'lighting', 'SIMPLE',       '30x30x140',
     99000,  'https://cdn.example.com/f_lighting_005.jpg', '#EEEEEE',
     30, 30, 140, 'SIMPLE'),
    ('f_lighting_006', 'Helsinki Hybrid Lamp',   'lighting', 'MODERN',       '32x32x155',
     115000, 'https://cdn.example.com/f_lighting_006.jpg', '#E2D6B4',
     32, 32, 155, 'MODERN,SIMPLE'),
    -- oversize (longer side > 400) + clashing color
    ('f_lighting_099','Atrium Pendant Fixture',  'lighting', 'INDUSTRIAL',   '420x120x240',
     490000, 'https://cdn.example.com/f_lighting_099.jpg', '#0A7A88',
     420, 120, 240, 'INDUSTRIAL');
