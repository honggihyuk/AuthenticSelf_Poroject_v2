# Verification Report: UC-ML-PERSIST
**Verdict**: PASS-WITH-WARNINGS
**Iteration**: v1
**Date**: 2026-05-26

Independent re-verification of the implementation against `spec.md` /
`acceptance_criteria.json`. Builds and tests were re-run (not trusted from the
Design Agent's self-report). MySQL-dependent ACs (AC-1, AC-2) are verified by
static inspection because no live DB is available in this environment — the
migration is additive/nullable, so this is low-risk.

## AC Coverage Matrix

| AC ID | Status | Evidence (file:line or test name) |
|-------|--------|-----------------------------------|
| AC-1 (V12 applies, JSON nullable) | PASS (static) | `V12__add_spaces_ai_detections.sql:26-27` — single `ADD COLUMN ai_detections JSON NULL`; next free version after V11 (migration dir lists V1..V12, monotonic). Entity `Space.java:98` maps `columnDefinition="JSON"` so `ddl-auto=validate` (`application.yml:16`) matches. No live MySQL run available. |
| AC-2 (existing rows unaffected) | PASS (static) | `V12...sql` has no DEFAULT, no backfill, no other column touched; AFTER `analysis_date`. Additive nullable column leaves pre-existing rows' values intact and `ai_detections=NULL`. |
| AC-3 (AI surfaces detections) | PASS | `test_analyze_space.py:80-88` asserts `imageWidth`/`imageHeight` ints + `detections[]` shape (label/4-num bbox/confidence∈[0,1]). Surfaced in `main.py:289-291` from `dim_result`. Run: 29 AI tests passed. |
| AC-4 (no extra inference — HARD) | PASS | `test_dimensions_detections.py::test_ac4_detect_called_exactly_once` asserts `yolo.calls == 1` (line 71) and detections populated from same result. Source: `dimensions.py:98` is the ONLY `self._yolo.detect(...)`; detections re-shaped at `dimensions.py:136-139`. Grep confirms no `/analyze/objects` / second `detect()`. Ran verbosely: PASSED. |
| AC-5 (empty detection path) | PASS | `test_dimensions_detections.py::test_ac5_empty_detections_still_carries_dims` (`detections==[]`, dims present) + `test_analyze_space.py:82-83`. `base.py:46` default `[]`. |
| AC-6 (backend persists envelope) | PASS | `AIOrchestratorTest::persistsDetectionsEnvelope` (line 263) captures JSON to `markAnalyzed`, asserts `imageWidth:1280`/`imageHeight:960`/`label:chair`/`confidence:0.9`. Source: `AIOrchestrator.java:168-174`. |
| AC-7 (FAILED/transport untouched) | PASS | `AIOrchestratorTest::spaceAnalyzerFailureFlipsToFailed` (line 139, verifies `markAnalyzed` never called, `markFailed` once) + `::spaceTransportFailureLeavesPending` (line 167, no write at all). Source: `AIOrchestrator.java:123,126`; `markFailed` leaves `ai_detections` NULL (`SpaceAnalysisPersistence.java:96-103`). |
| AC-8 (recommender gets real detections) | PASS | `RecommendationOrchestratorTest::ac8_persistedDetectionsReachPython` (line 288): captured `detectedObjects` size 1, type `chair`, 4-elem bboxNorm∈[0,1], confidence∈[0,1]. Source: `RecommendationOrchestrator.java:249-250,158` — `Collections.emptyList()` removed. |
| AC-9 (transform correctness) | PASS | `RecommendationOrchestratorTest::ac9_transformCorrectness` (line 317) + `AiDetectionsCodecTest::transform_normalizesAndMapsChair` (line 62): bbox[128,96,256,192]@1280x960 → [0.1,0.1,0.2,0.2] within 1e-3. Source: `AiDetectionsCodec.java:154-160`. |
| AC-10 (conflict signal wired) | PASS | `test_recommend_route.py::test_chair_detection_drives_object_conflict_ac10` (line 355): real `score_object_conflict` returns `objectConflict==0.20` for chair w/ conf 0.9 (baseline test confirms 1.00 with no detection). Ran verbosely: PASSED. |
| AC-11 (admin overlay renders) | PASS | `AdminSpaceDetailScreen.test.tsx` AC-11 (line 56): one box per detection (`bbox-0`,`bbox-1`, no `bbox-2`), labels `chair`/`91%`/`bed`/`74%`. Source: `DetectionOverlay.tsx:79-99` (geometry cloned from `ObjectsScreen.tsx:59-121`); reads via `getAdminSpaceDetail` → `GET /api/v1/spaces/{roomId}` (`admin.ts:121`). 3/3 jest tests passed. |
| AC-12 (backward-compatible E2E) | PASS | Recommender: `RecommendationOrchestratorTest::ac12_nullDetectionsAreEmptyList` (line 263) + `AiDetectionsCodecTest::transform_nullDegradesToEmpty` (line 117). Overlay: `AdminSpaceDetailScreen.test.tsx` two AC-12 cases (NULL + empty array → empty-state, photo renders, no crash). |
| AC-13 (malformed JSON degrades) | PASS | `RecommendationOrchestratorTest::ac13_malformedJsonDegrades` (line 343, response OK + empty list) + `AiDetectionsCodecTest::transform_malformedDegradesToEmpty` (line 125). Source: `AiDetectionsCodec.java:127-130` (try/catch → empty + WARN). |

All 13 ACs satisfied.

## Static Checks

| Check | Command | Exit code | Notes |
|-------|---------|-----------|-------|
| Backend compile + test | `gradlew test --tests com.authenticself.ai.* --rerun-tasks` | 0 | BUILD SUCCESSFUL (5m40s), compileJava clean. |
| TypeScript | `tsc --noEmit` (src/mobile) | 0 | No type errors. |
| AI tests | `pytest test_dimensions_detections test_analyze_space test_recommend_route` | 0 | 29 passed. |
| SQL migration naming | inspection | — | `V12__add_spaces_ai_detections.sql`; V1..V12 monotonic, V12 is next after V11. |

## Test Results
- **AI (Python)**: 29 / 29 passed / 0 failed / 0 skipped.
- **Backend (Spring) `com.authenticself.ai.*`**: 38 / 38 passed / 0 failed / 0 skipped
  (AiDetectionsCodecTest 10, AIOrchestratorTest 9, AIOrchestratorControllerTest 5,
  RecommendationOrchestratorTest 14).
- **Mobile (RN) AdminSpaceDetailScreen**: 3 / 3 passed / 0 failed / 0 skipped.
- Failing tests: none in scope.
- Newly-introduced skipped tests: none.

## API Contract Compliance
- `POST /analyze/space` success body now carries `imageWidth`/`imageHeight`/`detections[]`
  (`schemas.py:92-94`, reusing existing `DetectedObject` `schemas.py:164`). Matches
  `api_contract.yaml` SpaceAnalysisResponse. No divergent shape.
- `GET /api/v1/spaces/{roomId}` adds nullable `aiDetections` envelope
  (`SpaceResponse.java:44`, populated via `SpaceController.java:258`). Matches
  contract `SpaceResponse.aiDetections` → `AiDetectionsEnvelope`. No new endpoint
  (design decision #5), consistent with contract ("No new endpoints introduced").
- Recommender DTO `RecommendationRequest.DetectedObject{type,bboxNorm,confidence}`
  unchanged in shape (`RecommendationRequest.java:50-54`); only the list is now
  populated. Matches contract `RecoDetectedObject`.

## Security Issues

| Severity | Issue | File:line | Fix hint |
|----------|-------|-----------|----------|
| (none) | No SQL string-concat, no `Statement`, no hardcoded secrets in new AI code | grep over `src/backend/.../ai` clean | — |

Notes: `ai_detections` JSON is server-generated from typed objects (`AiDetectionsCodec.toEnvelopeJson`), never user-supplied. The admin overlay reads through the existing owner-scoped `GET /api/v1/spaces/{roomId}` (no new auth surface, no cross-user leak). Malformed/legacy JSON degrades to empty list (no injection/parse-crash path).

## Smell / Drift Issues

| Type | Location | Severity | Description |
|------|----------|----------|-------------|
| Doc drift | `dimensions.py:132` comment ("line ~97") | Low | Actual YOLO call is line 98. Prose only; behavior correct (single call confirmed). Pre-flagged as non-blocking. |
| Doc drift | `SpaceResponse.java:25-28` Javadoc says `@JsonInclude(NON_ABSENT)` but annotation is `ALWAYS` | Low | Stale comment. `ALWAYS` still emits null `aiDetections`, which is the intended behavior (AC-12 admin null path works). Pre-flagged as non-blocking. |
| Stale Javadoc | `RecommendationOrchestrator.java:41` ("detectedObjects is always [] per FR-18 step 8") | Low | Class-level Javadoc not updated after FR-9; inline Step-8 comment (lines 145-148) is correct and the code passes real detections. No behavioral impact. |
| Stale Javadoc | `RecommendationRequest.java:44-49` ("Spring always sends an empty list") | Low | Superseded by FR-9; shape unchanged so contract-correct. No behavioral impact. |
| Documented gap | `AiDetectionsCodec.LABEL_TO_TYPE` (`AiDetectionsCodec.java:57-60`) | Info | Only `chair`/`bed` map; `desk`/`lighting` have no COCO class. Intentional, spec §8 out-of-scope. Confirmed non-blocking. |
| Pre-existing failure | `test_analyze_style.py::test_style_classifier_has_placeholder_docstring` | Info | `style.py` last changed in commit `aa1fa29` (not this task); `git diff HEAD` empty. Unrelated to UC-ML-PERSIST — not attributed to this task. |

## Blocker Summary (goes to Prompt Agent on retry)
None. No blocking issues found.

## Diagram ↔ Code Consistency
Every participant in `design.md` §2 Mermaid flow exists in code:
`YOLODetector.detect` (`yolo_detector.py:64`), `DimensionsEstimator.analyze`
(`dimensions.py:94`), `AIOrchestrator.analyze` (`AIOrchestrator.java:79`),
`AiDetectionsCodec.toEnvelopeJson/toDetectedObjects/parseEnvelope`
(`AiDetectionsCodec.java:76/119/173`), `SpaceAnalysisPersistence.markAnalyzed`
(5-arg, `SpaceAnalysisPersistence.java:69`), `RecommendationOrchestrator.buildRequest`
(`RecommendationOrchestrator.java:230`), `score_object_conflict`
(`recommender.py`), `AdminSpaceDetailScreen` + `DetectionOverlay`
(`mobile/src/...`). No drift.

## Notes on the 6 brief-critical checks
1. **Zero extra YOLO inference**: CONFIRMED — single `detect()` at `dimensions.py:98`,
   AC-4 test asserts `== 1` and passes. No `/analyze/objects`/second `detect()`.
2. **Detections in `/analyze/space` response**: CONFIRMED — `{label,bbox,confidence}`
   reuses existing `DetectedObject` (`schemas.py:164`) + envelope `imageWidth/imageHeight`.
3. **Persistence**: CONFIRMED — V12 next-after-V11, `JSON NULL`, additive/no-backfill;
   `Space.aiDetections` maps it; `SpaceAnalysisPersistence` stores envelope on callback.
4. **Recommender wiring**: CONFIRMED — `Collections.emptyList()` removed; persisted +
   transformed detections (normalized bbox + COCO→catalog via `AiDetectionsCodec`) reach
   the request; AC-8 non-empty list verified.
5. **Backward compatibility**: CONFIRMED — null `ai_detections` → recommender `[]`
   (AC-12) and admin empty-state (AC-12 RN tests), no crash.
6. **Admin RN overlay**: CONFIRMED — boxes at correct coords reusing ObjectsScreen
   geometry, reads via existing `GET /api/v1/spaces/{roomId}`.
