# AI Pipeline Real State Audit (2026-05-26)

## Executive Summary

The April 2026 follow-up document (06_production_hardening_checklist.md section P-2) claimed dimensions.py was a stub with TODO(ml). That claim is stale. Commit aa1fa29 (AS_03) replaced the hash-based stub with a real YOLOv8+MiDaS hybrid heuristic. This audit verifies the actual state of all AI analyzers.

### Key Finding

Five of seven components are already ML (L2: pretrained models). Two are heuristic/rule-based (L1). None are stubs (L0). The gap is not missing ML — it is that YOLO detections are run but their output is not persisted or consumed downstream.

---

## Analyzer State Matrix

| File | Purpose | Implementation | Level | Type | Evidence | Next Step |
|---|---|---|---|---|---|---|
| base.py | Protocol + result type | Pydantic dataclass | L0 | Struct | Lines 16-36 | N/A |
| color.py | Dominant color extractor | OpenCV k-means (5 clusters) | L2 | Learned | cv2.kmeans call, _K=5 | Already ML |
| depth.py | Monocular depth estimator | MiDaS small torch model | L2 | Learned | torch.hub.load intel-isl/MiDaS | Already ML |
| dimensions.py | Room size estimator | YOLOv8n + MiDaS ratio | L2+L1 | Hybrid | yolo_detector + typical widths | Already ML |
| style.py | Style classifier | HSV histogram + 5 rules | L1 | Heuristic | Lines 111-122 hardcoded if-else | Train ResNet/CLIP |
| yolo_detector.py | Furniture detection | YOLOv8n COCO nano | L2 | Learned | YOLO(_MODEL_NAME) | Already ML |
| recommender.py | Ranking scorer | Four weighted rules | L1 | Heuristic | Lines 302-395 fixed weights | Train LambdaMART |

---

## Answer to Five Specific Questions

### 1. Actual API Contract: POST /analyze/space

Backend receives exactly these fields (SpaceAnalysisResponse schema):

```
roomId: string
status: "OK"
dimensions.widthM: float
dimensions.lengthM: float  
dimensions.heightM: float
dimensions.areaM2: float
mainColor: #RRGGBB
confidence: float [0,1]
processingMs: integer
```

MySQL persistence (V2__add_spaces_status_and_photo.sql):
- dimensions -> VARCHAR(100) stored as string
- main_color -> VARCHAR(32) as hex
- style -> VARCHAR(64) (from separate style analyzer)
- analysis_date -> DATETIME

Only widthM, lengthM, heightM are stored; areaM2 is computed client-side.

### 2. Recommender Input & Determinism

Recommender receives:
- space.dimensions (widthM, lengthM, heightM)
- space.mainColor (hex)
- space.detectedStyle (optional, from style analyzer)
- space.detectedObjects (YOLO detections with label, bbox, confidence)
- preferredStyle (user choice including "CURRENT")
- catalog (furniture items)

Determinism: Pure function with hardcoded weights (lines 302-395 recommender.py). No randomness.

YOLO detections are only used by score_object_conflict (line 245-259) to flag if a same-type object exists with confidence >= 0.5 (penalty 0.20 if true). Full bbox data is not used for ranking.

### 3. Gap: YOLO Output vs Persistence

YOLO runs and returns DetectionResult with pixel bboxes, but:

- Backend receives detectedObjects in SpaceBlock (schemas_reco.py line 61)
- Backend does NOT store YOLO detections anywhere
- No MySQL column exists for objects in spaces table
- Only the binary "same-type conflict yes/no" is used

Result: YOLO inference happens every time but zero persistence or reuse.

### 4. Current Test Coverage

Test files (7 files, 1094 total lines):
- test_analyze_space.py (136 lines): /analyze/space endpoint
- test_analyze_style.py (177 lines): /analyze/style endpoint
- test_color_extractor.py (57 lines): ColorExtractor unit tests
- test_recommender_unit.py (323 lines): scoring functions
- test_recommend_route.py (347 lines): /recommend/furniture endpoint
- conftest.py (54 lines): fixtures

Gaps:
- No unit test for DimensionsEstimator (model loads slow)
- No unit test for DepthEstimator (MiDaS load slow)
- No unit test for YOLODetector (model load slow)
- No test for StyleClassifier

### 5. dimensions.py Stub History

April version (commit 47ae256): Hash-based stub reading file bytes, SHA-256, extracting four slices to map to [2.5-6.0)m range. Comment says TODO(ml): replace with real LayoutNet/YOLO.

Current version (commit aa1fa29 AS_03): Real implementation. Loads YOLOv8n + MiDaS. Picks reference furniture object from YOLO (line 102), calculates meters-per-pixel (lines 110-113), uses depth ratio for length (lines 115-119). Fallback 3.5m x 3.5m if no reference found.

April doc did not catch this change (written April 20, code changed May 12).

---

## Downstream Consumption

| Field | Received | Persisted | Recommender | Frontend | Status |
|---|---|---|---|---|---|
| dimensions | yes | yes | yes | yes | Live |
| mainColor | yes | yes | yes | yes | Live |
| detectedStyle | yes | yes | yes | yes | Live |
| detectedObjects[] | yes | NO | partial (conflict only) | no | Dead weight |
| confidence | yes | NO | no | no | Dead weight |

---

## Upgrade Candidates (not blockers)

- style.py: HSV rules -> ResNet/CLIP (2-3 weeks, better accuracy)
- recommender.py: hardcoded weights -> learning-to-rank (4-6 weeks, learned ranking)
- yolo_detector.py: COCO -> fine-tuned on furniture (6-8 weeks, if data exists)
- Add MySQL column for YOLO detections (4 hours, audit trail + re-recommendation)

---

## Conclusion

The AI pipeline is a working L2+L1 hybrid, not a stub farm. The real debt is not missing ML—it is missing persistence of YOLO detections and missing learned models for style and ranking. The April follow-up's P-2 claim is out of date.

