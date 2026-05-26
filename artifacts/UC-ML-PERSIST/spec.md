# Task Spec: UC-ML-PERSIST

## 1. Goal
Persist the YOLO furniture detections that `/analyze/space` already computes (but currently discards), feed those real detections into the furniture recommender in place of the hardcoded empty list, and render them as a bounding-box overlay in the admin Space-detail debug view. This is a wiring + persistence task across 4 layers — **no new ML and no second YOLO inference call**.

## 2. Source (PRD section)
- PRD §6 UC-01 step 2 (space analysis: dimensions/color/style + object detection) and step 8 (cross-validate against detected objects before recommending).
- PRD §6 UC-03 (admin dashboard — Rooms detail view).
- PRD §3 / §7 DB design (`spaces` table). This task adds one column.
- Implements the previously-stubbed "FR-18 step 8" contract that `RecommendationOrchestrator` and `RecommendationRequest.DetectedObject` were explicitly scaffolded for (see code comments at `RecommendationOrchestrator.java:147` and `RecommendationRequest.java:44-49`).

## 3. Actors & Preconditions
- **Actors**: AI service (FastAPI), Spring backend (`AIOrchestrator` + `RecommendationOrchestrator`), React Native admin client.
- **Preconditions**:
  - Tasks UC-01-space-analysis and UC-01-recommendation are complete; `/analyze/space` runs YOLO at `src/ai/app/analyzers/dimensions.py:97` (`det = self._yolo.detect(image_path)`), returning a `DetectionResult{detections:[Detection{label,bbox(px),confidence}], image_width, image_height}` from `src/ai/app/analyzers/yolo_detector.py`.
  - Latest applied Flyway migration is `V11__add_users_password_hash.sql`. Next free version is **V12**.
  - `spaces` table currently has no JSON/text column for detections; `Space` entity (`src/backend/.../domain/Space.java`) has only identity/file/dimensions/main_color/style/preferred_style/analysis_date/status columns.
  - `Space.Status {PENDING_ANALYSIS, ANALYZED, FAILED}` exists; analysis persistence happens in `SpaceAnalysisPersistence.markAnalyzed(...)`.

## 4. Functional Requirements (numbered, atomic)

### AI layer (Python)
- **FR-1**: Add a `detections` field to `SpaceAnalysisResult` (`src/ai/app/analyzers/base.py`). It carries the list already produced at `dimensions.py:97`/`102` (`det.detections`). Default `[]` so color-only / style-only analyzers that leave it unset stay valid.
- **FR-2**: `DimensionsEstimator.analyze` (`dimensions.py:130` return site) MUST populate `detections` from the **same** `det = self._yolo.detect(...)` result already computed at line 97. It MUST NOT add a second `detect()` call, MUST NOT call `/analyze/objects`, and MUST NOT change the existing `_pick_reference`/scale logic. (Hard constraint — zero extra inference.)
- **FR-3**: The `/analyze/space` success body (`SpaceAnalysisResponse` in `src/ai/app/schemas.py`, composed at `main.py:280`) MUST include a `detections: List[DetectedObject]` field plus `imageWidth` and `imageHeight` integers (needed downstream to normalize bboxes). Reuse the existing `DetectedObject` shape `{label:str, bbox:[x1,y1,x2,y2] absolute px, confidence:float}` already defined in `schemas.py:153`. Do not invent a new shape. `imageWidth`/`imageHeight` come from the `DetectionResult` (or the color/depth pass) without re-running YOLO.

### DB layer (Flyway)
- **FR-4**: New migration `V12__add_spaces_ai_detections.sql` adds a single nullable column `ai_detections JSON NULL` to `spaces`. No other schema change. Existing rows get `NULL`.

### Backend layer (Spring)
- **FR-5**: Map the new column on the `Space` entity as a nullable `String ai_detections` (raw JSON text) with `columnDefinition = "JSON"`, leaving all existing fields untouched.
- **FR-6**: Extend `SpaceAnalysisResponse` DTO (`src/backend/.../ai/dto/SpaceAnalysisResponse.java`) to deserialize the new `detections`, `imageWidth`, `imageHeight` fields. (`@JsonIgnoreProperties(ignoreUnknown=true)` is already present, so existing builds keep working; new fields must be added explicitly to be consumed.)
- **FR-7**: On the analysis happy path (`AIOrchestrator.analyze` → `SpaceAnalysisPersistence.markAnalyzed`), persist the detections JSON onto `Space.ai_detections`. Add a `markAnalyzed(...)` overload (or extend the existing 4-arg one) that accepts the serialized detections payload; the persisted JSON MUST include the detection list and the image dimensions so the recommender transform (FR-9) can normalize bboxes. When the analyzer returned an empty detection list, persist `[]` (or leave NULL) — both are valid and must round-trip without error.
- **FR-8**: Persistence MUST NOT change the FAILED / transport-failure paths: `markFailed` and the PENDING-stays-PENDING transport branch leave `ai_detections` NULL.
- **FR-9**: In `RecommendationOrchestrator.buildRequest(...)`, replace `Collections.emptyList()` (currently at `RecommendationOrchestrator.java:246`, "FR-18 step 8 — always [] for now") with the persisted detections read from `Space.ai_detections`. The orchestrator MUST transform each persisted detection `{label, bbox:[x1,y1,x2,y2] px, confidence}` into the recommender DTO `RecommendationRequest.DetectedObject{type, bboxNorm:[4 normalized 0..1 floats], confidence}` by (a) mapping the COCO/YOLO `label` to a catalog `type` and (b) dividing bbox coordinates by the persisted `imageWidth`/`imageHeight`. See §5 for the mapping contract.
- **FR-10**: Cache-key behaviour is unchanged. The recommendation cache stays keyed on `(roomId, preferredStyle)`; because detections are fixed per analyzed room, no new cache dimension is needed.

### Admin layer (React Native)
- **FR-11**: In the admin Space-detail / debug view, overlay the persisted detection bounding boxes on top of the original room photo. Boxes MUST be positioned using the absolute-pixel `bbox` scaled by the displayed image size relative to `imageWidth`/`imageHeight` (the proven pattern already in `src/mobile/src/screens/ObjectsScreen.tsx:60-117` — reuse it; do not invent a new overlay algorithm). Each box shows `label` and `confidence%`.
- **FR-12**: When `ai_detections` is NULL or `[]` (pre-migration rows, or rooms where YOLO found nothing), the admin view renders the photo with no boxes and an empty-state hint — no crash, no error.

## 5. Data Contract

### Inputs / Outputs — AI `/analyze/space` (success body, extended)
Existing fields unchanged; three fields added:
```json
{
  "roomId": "01HZ...",
  "status": "OK",
  "dimensions": { "widthM": 3.6, "lengthM": 4.2, "heightM": 2.4, "areaM2": 15.12 },
  "mainColor": "#A1B2C3",
  "confidence": 0.78,
  "processingMs": 812,
  "imageWidth": 1280,
  "imageHeight": 960,
  "detections": [
    { "label": "chair", "bbox": [120.0, 340.5, 410.2, 880.0], "confidence": 0.91 },
    { "label": "bed",   "bbox": [600.0, 200.0, 1240.0, 900.0], "confidence": 0.74 }
  ]
}
```
- `detections[]` reuses `DetectedObject` from `src/ai/app/schemas.py:153` verbatim: `label:str`, `bbox:(x1,y1,x2,y2)` absolute pixels (xyxy), `confidence:float` in [0,1].
- Empty when YOLO finds nothing: `"detections": []`.

### Persisted shape — `spaces.ai_detections` (JSON, nullable)
Backend serializes a self-describing envelope so bbox normalization is reproducible without re-reading the image:
```json
{
  "imageWidth": 1280,
  "imageHeight": 960,
  "detections": [
    { "label": "chair", "bbox": [120.0, 340.5, 410.2, 880.0], "confidence": 0.91 }
  ]
}
```
- `NULL` for: rows created before this migration, FAILED analyses, and transport-failure (PENDING) rows.
- `detections: []` (with valid `imageWidth`/`imageHeight`) is allowed for "analysis succeeded, zero objects".

### Transform — persisted detection → recommender DTO (FR-9)
- `RecommendationRequest.DetectedObject` (`src/backend/.../ai/dto/RecommendationRequest.java:50`) is `{type:String, bboxNorm:List<Double>(4), confidence:Double}`. The Python recommender consumes the mirror `DetectedObjectModel` (`src/ai/app/schemas_reco.py:49`, `bboxNorm` 0..1) and matches conflicts via `det.type == item.type` in `score_object_conflict` (`src/ai/app/analyzers/recommender.py:245`), where `item.type ∈ {desk, bed, chair, lighting}`.
- **Coordinate transform**: `bboxNorm = [x1/imageWidth, y1/imageHeight, x2/imageWidth, y2/imageHeight]`, each clamped to [0,1].
- **Label→type mapping** (so `score_object_conflict` can fire on real catalog types): at minimum COCO `chair → chair`, COCO `bed → bed`. COCO labels with no catalog equivalent (e.g. `tv`, `potted plant`, `couch`) are passed through with their raw label as `type` (harmless — they simply never equal a catalog `item.type`). `desk` and `lighting` have no direct COCO class, so no detection will map to them — that is acceptable for this task and MUST be noted, not "fixed" with new ML. The mapping table lives in the backend transform; it is data, not a model.

### DB entities touched
- `spaces` (PRD §3): add `ai_detections JSON NULL`. No other table touched.

## 6. Non-Functional Requirements
- **Performance**: zero additional model inference. `/analyze/space` latency MUST be unchanged within noise (the YOLO call at `dimensions.py:97` already runs). Persisting one extra JSON column adds one column write to the existing `markAnalyzed` UPDATE — no extra round-trip.
- **Backward compatibility**: pre-migration rows (`ai_detections IS NULL`) MUST flow through analysis read, recommendation, and admin view without error. A NULL/empty detection list maps to an empty recommender `detectedObjects` list — i.e. identical behaviour to today's hardcoded `[]`.
- **Security**: `ai_detections` is internal/debug data; the overlay is admin-only (UC-03). No PII. JSON is generated server-side from typed objects (no user-supplied JSON written to the column).
- **Error handling**: malformed/legacy JSON in `ai_detections` MUST degrade to an empty detection list for recommendation (log a warning) rather than failing the recommendation request.
- **Contract stability**: AI response additions are additive; the existing Spring `SpaceAnalysisResponse` keeps `@JsonIgnoreProperties(ignoreUnknown=true)` so an AI deploy ahead of a backend deploy does not break.

## 7. Acceptance Criteria (the Verification Agent will test these)

- **AC-1** (DB migration applies): Given a database at V11, When Flyway runs `V12__add_spaces_ai_detections.sql`, Then migration succeeds, `spaces.ai_detections` exists, is of JSON type, and is nullable.
- **AC-2** (existing rows unaffected): Given `spaces` rows that existed before V12, When V12 is applied, Then those rows still load and their `ai_detections` is `NULL`; no existing column value changes.
- **AC-3** (AI surfaces detections): Given a photo containing detectable furniture, When `POST /analyze/space` returns 200, Then the body includes `detections[]` where each element has `label`, `bbox` (4 numbers), `confidence` in [0,1], plus integer `imageWidth`/`imageHeight`.
- **AC-4** (no extra inference — hard constraint): Given the `/analyze/space` code path, When the dimensions analyzer runs, Then `YOLODetector.detect` is invoked exactly once per analyze call (the existing `dimensions.py:97` call) and the `detections` field is populated from that same result; no call to `/analyze/objects` and no second `detect()` is introduced. Verifiable by spying/counting `detect` invocations in a unit/integration test.
- **AC-5** (empty detection path): Given a photo where YOLO finds nothing, When `/analyze/space` returns 200, Then `detections` is `[]` (not absent, not null) and `imageWidth`/`imageHeight` are still present.
- **AC-6** (backend persists on callback): Given a PENDING space and a `/analyze/space` response containing detections, When `AIOrchestrator.analyze` completes the happy path, Then `spaces.ai_detections` for that room contains a JSON envelope with `imageWidth`, `imageHeight`, and the detection list.
- **AC-7** (FAILED/transport paths untouched): Given a space-analyzer 422 (FAILED) or a transport failure (stays PENDING), When analysis runs, Then `ai_detections` remains `NULL` and the status transition matches existing behaviour.
- **AC-8** (recommender receives real detections): Given an analyzed space with non-empty persisted detections, When a recommendation is requested, Then `RecommendationRequest.space.detectedObjects` sent to Python is non-empty and is NOT `Collections.emptyList()`; each item has a `type`, a 4-element `bboxNorm` with every value in [0,1], and a `confidence` in [0,1].
- **AC-9** (transform correctness): Given a persisted detection `{label:"chair", bbox:[128,96,256,192], confidence:0.9}` with `imageWidth=1280,imageHeight=960`, When the orchestrator builds the request, Then it emits `{type:"chair", bboxNorm:[0.1,0.1,0.2,0.2], confidence:0.9}` (values within 1e-3).
- **AC-10** (conflict signal wired): Given a persisted `chair` detection with confidence ≥ 0.5 and a catalog `chair` item, When recommendation runs against the real Python `score_object_conflict`, Then the chair item's object-conflict factor is the penalty (0.20) rather than 1.00 — proving the real signal (not the dead binary flag) reaches the scorer.
- **AC-11** (admin overlay renders): Given a space with persisted detections, When the admin Space-detail view renders, Then one bounding box per detection is drawn over the photo at normalized coordinates `bbox/imageDims` scaled to the displayed image, each labelled with `label` + `confidence%` (reusing the `ObjectsScreen` overlay pattern).
- **AC-12** (backward-compatible end to end): Given a space with `ai_detections = NULL`, When recommendation runs AND the admin view renders, Then recommendation succeeds with an empty `detectedObjects` list (today's behaviour) and the admin view shows the photo with zero boxes and an empty-state hint — no crash anywhere.
- **AC-13** (malformed JSON degrades safely): Given a space whose `ai_detections` contains malformed/legacy JSON, When recommendation runs, Then the orchestrator logs a warning and proceeds with an empty `detectedObjects` list instead of failing the request.

## 8. Out of Scope
- Any new or retrained ML model; any change to YOLO weights, thresholds, or the `_pick_reference` scale logic.
- A second YOLO inference call, or routing `/analyze/space` through `/analyze/objects`.
- New `spaces` columns beyond the single `ai_detections JSON`.
- Adding `desk`/`lighting` detection capability (COCO has no such classes) — documented gap, not built here.
- Re-keying the recommendation cache on detections, Redis, or any cache-layer change.
- Storing the image itself, cropping, or generating overlay images server-side (overlay is client-rendered).
- Any non-admin (end-user) detection UI.
- Citing or computing any analysis "failure rate" metric — explicitly not part of this spec.

## 9. Dependencies
- `UC-01-space-analysis` (provides the `/analyze/space` endpoint and the YOLO call being reused) — must be complete.
- `UC-01-recommendation` (provides `RecommendationOrchestrator`, `RecommendationRequest.DetectedObject`, and the Python `score_object_conflict`) — must be complete.
- `DB-schema-init` through V11 must be applied before V12.
- Admin layer (FR-11/FR-12) depends on FR-3/FR-5/FR-7 landing first (needs persisted detections + imageDims to render); it can be split into a follow-up task ID if iteration scope is tight, but is in-scope here.
