# Task Spec: UC-01-recommendation

## 1. Goal
Close the PRD §6 UC-01 steps 4–5 "교차 검증 & 추천" block: (a) add a Python `POST /recommend/furniture` endpoint that consumes the persisted space analysis + the user's `preferred_style` and cross-validates them against a seeded `Furniture` catalog using a deterministic scoring function (size-fit, style-tag match, color harmony, conflict with detected objects); (b) extend the Spring backend with a `RecommendationOrchestrator` + `GET /api/v1/spaces/{roomId}/recommendations` public endpoint that assembles the request from the `spaces` row, calls Python, caches the response, and returns a ranked list of furniture items to the RN client; (c) give the React Native client a `RecommendationScreen` that fetches and displays the ranked recommendations with a stub "Add to wishlist" affordance (the actual wishlist write lives in UC-02).

## 2. Source (PRD section)
- **PRD §6 UC-01 기본 흐름 step 4** — "AI 시스템이 분석된 공간 데이터와 사용자가 원하는 스타일을 교차 검증." Drives FR-6..FR-10 (the scoring / cross-validation function).
- **PRD §6 UC-01 기본 흐름 step 5** — "선정된 스타일과 공간에 어울리는 책상/침대/의자/조명 등 가구를 추천하여 사용자에게 제공." Drives FR-3 (category coverage), FR-9 (per-category top-N), FR-17 (RN list UI).
- **PRD §8 sequence diagram (마지막 반환 단계)** — 추천 결과가 Spring을 경유해 RN 클라이언트로 내려간다. Drives FR-13 (Spring endpoint) and FR-18 (RN fetch).
- **PRD §9 시스템 아키텍처** — `AI Orchestrator (Spring)` → `스타일 & 추천 서버 (Python)`. In this project the style-and-recommend role shares the same FastAPI process as space-analysis (Task 3 / Task 4 decision). Drives FR-1 (sibling route on existing service).
- **PRD §4 기술 스택** — Python (TensorFlow/PyTorch cited). For this task real ML weights remain **out of scope**; scoring is a deterministic rule-based function (same placeholder-model hygiene rule as Task 3 FR-5 and Task 4 FR-4). Drives NFR "No new heavy ML deps."
- **PRD §3 DB 설계 — `furniture`** — this task EXTENDS the Task-1 schema with columns required to score recommendations (`name`, `price`, `image_url`, `color_hex`, `width_cm`, `length_cm`, `height_cm`, `style_tags`). Drives FR-4 "Schema delta V4."
- **PRD §7 클래스 다이어그램** — `Recommendation` aggregate referenced; this task does NOT introduce a new DB table for recommendations — results are computed on demand and cached in-memory (see FR-14).

## 3. Actors & Preconditions
- **Primary actor (backend)**: the Spring `RecommendationOrchestrator` component (new in this task). It sits next to the Task-3/Task-4 `AIOrchestrator` under `com.authenticself.ai` and reuses the same `aiExecutor` thread pool bean.
- **Primary actor (mobile)**: the React Native client's `RecommendationScreen`, navigated to from `StyleSelectionScreen` on a successful `PUT .../preferred-style` (Task 4 FR-18).
- **Secondary actor (Python)**: the existing FastAPI service at `src/ai/` — gains one new route (`/recommend/furniture`) and one new analyzer module (`src/ai/app/analyzers/recommender.py`). Reuses the placeholder-model hygiene rule from Task 3.
- **Tertiary actor**: the user — views a ranked list of furniture suggestions and optionally taps "Add to wishlist" (stub event in this task; the actual wishlist write is implemented in UC-02).
- **Preconditions**:
  - Tasks 1–4 are merged:
    - V1 (base schema), V2 (upload columns + status), V3 (preferred_style) migrations applied.
    - `spaces` row has `status='ANALYZED'`, `dimensions` string populated, `main_color` hex populated, `style` (AI-detected) populated OR null, `preferred_style` populated (the user has completed Task 4's screen).
  - Python `POST /analyze/space` + `POST /analyze/style` routes are live; their outputs have been persisted.
  - Spring `SpaceController` exposes `GET /api/v1/spaces/{roomId}` and `PUT .../preferred-style` (Task 4 FR-14) — this task reuses the same `X-User-Id` auth pattern.
  - The `furniture` catalog is seeded via Flyway `V5__seed_furniture_catalog.sql` (see FR-5) — at least 24 rows across the four PRD categories (`desk`, `bed`, `chair`, `lighting`).
  - RN app has `StyleSelectionScreen` and a placeholder `"Recommendation"` route name registered in the navigation stack (Task 4 FR-18 pre-wired it).

## 4. Functional Requirements

### Python AI service — new recommendation route

**FR-1 (Register `POST /recommend/furniture`)** — Add a new FastAPI router mounted at `/recommend/furniture` in `src/ai/app/main.py`. The route MUST live in its own module `src/ai/app/routes/recommend.py` (mirroring the `routes/space.py` and `routes/style.py` patterns). The OpenAPI output after this task must contain all three routes: `/analyze/space`, `/analyze/style`, `/recommend/furniture`.

**FR-2 (Endpoint contract — `POST /recommend/furniture`)** — Request body:
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "userId": "u_01HXY...",
  "space": {
    "dimensions": { "widthM": 3.6, "lengthM": 4.2, "heightM": 2.4, "areaM2": 15.12 },
    "mainColor": "#E8D9B0",
    "detectedStyle": "MODERN",
    "detectedObjects": [
      { "type": "bed",   "bboxNorm": [0.10, 0.40, 0.55, 0.95], "confidence": 0.82 },
      { "type": "chair", "bboxNorm": [0.70, 0.60, 0.85, 0.95], "confidence": 0.66 }
    ]
  },
  "preferredStyle": "MODERN",
  "catalog": [
    {
      "furnitureId": "f_desk_001",
      "type": "desk",
      "name": "Oslo Slim Desk",
      "styleTags": ["MODERN", "SCANDINAVIAN"],
      "widthCm": 120, "lengthCm": 60, "heightCm": 74,
      "colorHex": "#F3E6D2",
      "price": 189000,
      "imageUrl": "https://cdn.example.com/f_desk_001.jpg"
    }
  ],
  "topNPerCategory": 3
}
```
Constraints / validation:
- `roomId` — `^[A-Za-z0-9_-]{1,64}$` (same rule as `/analyze/space`).
- `userId` — non-empty string, ≤ 64 chars.
- `space.dimensions.{widthM,lengthM,heightM}` — all `> 0.0`.
- `space.mainColor` — `^#[0-9A-Fa-f]{6}$`.
- `space.detectedStyle` — one of `MODERN | SIMPLE | CLASSIC | SCANDINAVIAN | INDUSTRIAL` OR `null` (style analysis may have failed — Task 4 FR-12).
- `space.detectedObjects` — array of zero or more objects; each `type` is a free-form string (desk/bed/chair/lighting/window/door/…); `bboxNorm` is `[x1,y1,x2,y2]` all in `[0,1]`.
- `preferredStyle` — one of `CURRENT | MODERN | SIMPLE | CLASSIC | SCANDINAVIAN | INDUSTRIAL`.
- `catalog` — non-empty array; each item carries the nine fields above. The Spring side is the source of truth for which catalog subset is passed in (currently "all rows," see FR-11).
- `topNPerCategory` — integer `1..10`, default `3`.

Success response (HTTP 200):
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "status": "OK",
  "resolvedStyle": "MODERN",
  "generatedAt": "2026-04-18T09:14:22Z",
  "recommendations": {
    "desk":     [ /* up to topNPerCategory RecommendationItem objects */ ],
    "bed":      [ ... ],
    "chair":    [ ... ],
    "lighting": [ ... ]
  },
  "processingMs": 18
}
```
Each `RecommendationItem`:
```json
{
  "furnitureId": "f_desk_001",
  "name": "Oslo Slim Desk",
  "type": "desk",
  "price": 189000,
  "imageUrl": "https://cdn.example.com/f_desk_001.jpg",
  "fitScore": 0.87,
  "scoreBreakdown": {
    "sizeFit":       0.95,
    "styleMatch":    1.00,
    "colorHarmony":  0.78,
    "objectConflict": 1.00
  },
  "rationale": "모던 스타일 일치, 공간에 여유롭게 들어맞음, 컬러 조화 양호."
}
```
- `fitScore` is in `[0.0, 1.0]`, rounded to 2 dp.
- `scoreBreakdown` keys are fixed: exactly `sizeFit`, `styleMatch`, `colorHarmony`, `objectConflict`. Each value is in `[0.0, 1.0]` rounded to 2 dp.
- `rationale` is a short Korean string (≤ 120 chars). It is composed deterministically from which sub-scores passed thresholds (see FR-10).
- For each category, the list is sorted by `fitScore` DESC. Ties are broken by `price` ASC (cheaper first), then by `furnitureId` ASC for full determinism.

**FR-3 (Category coverage)** — The response `recommendations` object MUST contain exactly the four keys `desk`, `bed`, `chair`, `lighting` (PRD §6 step 5). If the input `catalog` contains zero items of a category, the value for that key is an empty array `[]` (NOT missing). No extra top-level keys are allowed under `recommendations`.

**FR-4 (`resolvedStyle` derivation)** — The recommender MUST compute `resolvedStyle` by:
- If `preferredStyle == "CURRENT"`: `resolvedStyle = space.detectedStyle` if non-null, else `"MODERN"` (documented default fallback — rationale: `detectedStyle` may be null when Task 4's style analysis transport-failed; we must still be able to rank).
- Else: `resolvedStyle = preferredStyle`.

This value is echoed in the response and drives the `styleMatch` sub-score (FR-7).

**FR-5 (Scoring: `sizeFit`)** — For each catalog item, compute:
- Room interior footprint (plan view): `roomW = space.dimensions.widthM * 100` cm, `roomL = space.dimensions.lengthM * 100` cm. Room interior height: `roomH = space.dimensions.heightM * 100` cm.
- For the item `i` with footprint `(iW, iL, iH)` cm:
  - **Hard fit**: the longer of `(iW, iL)` MUST be `≤ min(roomW, roomL) - 30` cm (30 cm clearance reserve), AND `iH ≤ roomH - 20` cm. If EITHER constraint is violated, `sizeFit = 0.0` AND the item is a "hard fail" — it is EXCLUDED from the final ranked list (NOT returned with score 0). Rationale: surfacing unfit items confuses the user.
  - **Soft fit** (when hard fit passes): `footprintRatio = (iW * iL) / (roomW * roomL)`. Score: `sizeFit = 1.0` if `footprintRatio ≤ 0.20` (takes ≤ 20% of floor); `sizeFit = 0.7` if `0.20 < footprintRatio ≤ 0.35`; `sizeFit = 0.4` if `0.35 < footprintRatio ≤ 0.50`; `sizeFit = 0.1` if `footprintRatio > 0.50`. Bed is an exception: the ratio bands shift up by one tier (a bed is expected to be large, so `0.35` still scores `1.0`).

**FR-6 (Scoring: `colorHarmony`)** — Between `space.mainColor` (hex) and `item.colorHex` (hex):
- Convert both to HSL.
- `hueDelta = min(|h1 - h2|, 360 - |h1 - h2|)` in degrees.
- `colorHarmony = 1.0` if `hueDelta ≤ 15` (analogous / same), `0.85` if `hueDelta ≤ 30`, `0.70` if `hueDelta ≤ 60`, `0.55` if `hueDelta ≤ 120`, `0.40` otherwise (clashing). Cap at `1.0`.
- If either hex is a near-neutral (saturation `s < 0.15`), bump the score by `+0.15` (max 1.0) — neutrals coexist with most palettes.

**FR-7 (Scoring: `styleMatch`)** — Between `resolvedStyle` and `item.styleTags` (array of strings):
- `styleMatch = 1.00` if `resolvedStyle ∈ item.styleTags`.
- Else `0.50` if there is a documented "compatible pair" hit (see the compatibility table below).
- Else `0.10` (not compatible; item almost surely drops out of the top-N but may still appear if nothing else matches).

Compatibility table (symmetric, intentionally small):
| A | B |
|---|---|
| `MODERN` | `SCANDINAVIAN` |
| `MODERN` | `SIMPLE` |
| `SIMPLE` | `SCANDINAVIAN` |
| `CLASSIC` | `INDUSTRIAL` |

`CURRENT` is never a value of `resolvedStyle` (FR-4 substitutes it).

**FR-8 (Scoring: `objectConflict`)** — For each catalog item of type `t`:
- If `space.detectedObjects` contains any entry whose `type == t` with `confidence ≥ 0.5`, set `objectConflict = 0.20` — a similar piece already exists in the room, so recommending another is redundant.
- Otherwise `objectConflict = 1.00`.
- Exception: lighting type NEVER triggers a conflict penalty even if a lighting fixture is detected (rationale: users often want additional/replacement lighting; the PRD explicitly lists lighting as a category to recommend). So for `item.type == "lighting"`, `objectConflict` is always `1.00`.

**FR-9 (Aggregate `fitScore` + ranking)** — `fitScore = round(0.35 * sizeFit + 0.30 * styleMatch + 0.20 * colorHarmony + 0.15 * objectConflict, 2)`. Weights MUST sum to 1.0 and MUST be the values above (verifiable in source). Per-category ranking: filter out hard-fit failures (FR-5), then sort by `fitScore` DESC, `price` ASC, `furnitureId` ASC; take top `topNPerCategory`.

**FR-10 (Rationale string)** — `rationale` is composed from a fixed dictionary:
- If `styleMatch == 1.00`: append `"<resolvedStyle label> 스타일 일치"`.
- If `styleMatch == 0.50`: append `"호환 스타일"`.
- If `sizeFit >= 0.7`: append `"공간에 여유롭게 들어맞음"`; elif `sizeFit >= 0.4`: append `"공간에 딱 맞음"`; else `"공간이 다소 빠듯함"`.
- If `colorHarmony >= 0.85`: append `"컬러 조화 우수"`; elif `colorHarmony >= 0.55`: append `"컬러 조화 양호"`; else `"컬러 대비 강함"`.
- If `objectConflict == 0.20`: append `"(기존 가구와 중복 가능성)"`.
Segments are joined with `", "` then the result is trimmed to ≤ 120 chars (suffix `…` if truncated). Label map: `MODERN→모던 / SIMPLE→심플 / CLASSIC→클래식 / SCANDINAVIAN→스칸디나비안 / INDUSTRIAL→인더스트리얼`.

**FR-11 (Async + thread pool — Python)** — The `/recommend/furniture` handler MUST be declared `async def`. The scoring function is pure CPU and may run inline on the event loop for catalogs up to 500 items (the seeded catalog in FR-17 is 24–48 items — well below). For any catalog with `len > 500`, the handler MUST offload scoring via `asyncio.to_thread(...)` (same pattern as `/analyze/space`). This is load-bearing because Spring's `RecommendationOrchestrator` runs its call on the shared `aiExecutor` thread; a blocked Python event loop would still hurt concurrency there.

**FR-12 (Error contract — Python side)** — Reuse the Task 3 / Task 4 error-envelope shape (`{errorCode, message, roomId?}`). New error codes introduced:
| errorCode | HTTP | Trigger |
|---|---|---|
| `INVALID_REQUEST` | 422 | Pydantic validation failure (e.g. `dimensions` missing, bad enum value) |
| `CATALOG_EMPTY` | 422 | `catalog` is `[]` — no items to rank |
| `NO_FIT_ANY_CATEGORY` | 200 | All items failed hard-fit; response still 200 but every category array is empty (see note) |
| `RECOMMENDATION_FAILED` | 422 | Unexpected exception inside the scorer (caught, logged, translated) |
| `INTERNAL_ERROR` | 500 | Truly unexpected |

NOTE on `NO_FIT_ANY_CATEGORY`: this is **NOT an HTTP error**. When every item is hard-fit-excluded, the response is 200 with all four category arrays empty AND a top-level `warning: "NO_FIT_ANY_CATEGORY"` field. The Spring side surfaces this to the RN client so the UI can show "맞는 가구가 없습니다" instead of an error dialog.

### Spring backend — RecommendationOrchestrator + public endpoint

**FR-13 (New package members)** — Under `com.authenticself.ai`:
- `RecommendationOrchestrator` — Spring `@Service`. Public API: `RecommendationResult recommend(String roomId, String userId)`. Internally: loads the `Space` row, loads all `Furniture` rows (or a filtered subset — see FR-16), assembles the Python request, calls `RecommendationClient`, post-processes (e.g. strip any ineligible categories), and returns the DTO.
- `RecommendationClient` — typed HTTP client, sibling of `SpaceAnalysisClient` and `StyleAnalysisClient`. Single public method `RecommendationResponse callRecommend(RecommendationRequest req)`. Timeouts: connect 2 s, read 15 s (catalog scoring is fast but we leave headroom — env-overridable via `app.ai.recommend.connect-timeout-ms` / `app.ai.recommend.read-timeout-ms`).
- `dto/RecommendationRequest` / `dto/RecommendationResponse` / `dto/RecommendationItem` / `dto/ScoreBreakdown` — Jackson-bound records mirroring the Python contract (FR-2).
- `RecommendationException` (+ subclasses `CatalogEmptyException`, `RecommendationFailedException`, `RecommendationTransportException`) — unchecked; maps 1:1 to Python error codes plus a transport bucket.

**FR-14 (Caching — in-memory per roomId + preferredStyle)** — Use Spring's `@Cacheable` (or a simple `ConcurrentHashMap<String, CachedEntry>` under `RecommendationOrchestrator` — Design Agent's choice) with cache key = `(roomId, preferredStyle)` and TTL 10 min. Rationale: the RN client may re-fetch on screen re-mount; the catalog is static within a session. Invalidation: any `PUT /api/v1/spaces/{roomId}/preferred-style` MUST invalidate all cache entries for that `roomId` (implemented in `SpaceController.updatePreferredStyle`). Cache MISS triggers a full Python round trip; HIT returns the cached `RecommendationResponse` as-is (including the original `generatedAt` timestamp).

**FR-15 (DB schema delta — `furniture` catalog columns)** — Add Flyway migration `V4__extend_furniture_catalog.sql`:
```sql
ALTER TABLE furniture
    ADD COLUMN name         VARCHAR(100)  NOT NULL DEFAULT ''      AFTER furniture_id,
    ADD COLUMN price        INT           NOT NULL DEFAULT 0       AFTER size,
    ADD COLUMN image_url    VARCHAR(512)  NULL                      AFTER price,
    ADD COLUMN color_hex    VARCHAR(7)    NOT NULL DEFAULT '#CCCCCC' AFTER image_url,
    ADD COLUMN width_cm     INT           NOT NULL DEFAULT 0       AFTER color_hex,
    ADD COLUMN length_cm    INT           NOT NULL DEFAULT 0       AFTER width_cm,
    ADD COLUMN height_cm    INT           NOT NULL DEFAULT 0       AFTER length_cm,
    ADD COLUMN style_tags   VARCHAR(255)  NOT NULL DEFAULT ''      AFTER height_cm;
```
- `style_tags` stores a comma-separated list (e.g. `"MODERN,SCANDINAVIAN"`). No JSON column — MySQL 8's JSON type works, but CSV keeps dev simple and matches the Task-1 convention of VARCHAR-heavy columns.
- `price` is `INT` (KRW, integer won — matches Task 1 `wishlist.price INT`).
- `color_hex` is `VARCHAR(7)` to hold `#RRGGBB`.
- Defaults on NOT NULL columns exist only to allow the ALTER on already-seeded dev/test DBs; the seed migration (FR-16) replaces every row with real values.

**FR-16 (Flyway V5 seed data)** — Add `V5__seed_furniture_catalog.sql` with at least **24 rows** (six per category × four categories) chosen to exercise every scoring branch:
- At least one `MODERN` desk, one `SCANDINAVIAN` desk, one `CLASSIC` desk, one `INDUSTRIAL` desk, one `SIMPLE` desk, one multi-tag desk (e.g. `"MODERN,SCANDINAVIAN"`).
- Same pattern for `bed`, `chair`, `lighting`.
- At least one item per category MUST be intentionally oversized (`widthCm > 400` or `heightCm > 260`) so the `sizeFit` hard-fail branch (FR-5) has coverage.
- At least one item per category MUST have a color that yields `hueDelta > 120` against a typical room `main_color` like `#E8D9B0` (a beige) — e.g. a deep cyan — so the `colorHarmony == 0.40` branch has coverage.
- Prices must vary across each category so ranking tie-breakers (FR-9) are meaningful.
- `furniture_id` values follow the pattern `f_<category>_<nnn>` (e.g. `f_desk_001`, `f_bed_012`) — numeric suffix is left-zero-padded to 3 digits for lexicographic-tie-break consistency.

**FR-17 (Furniture entity update)** — Extend the existing `Furniture` JPA entity (Task 1 FR-8 stub) with the eight new fields from FR-15. Add a `List<String> getStyleTagsList()` helper that splits `style_tags` on `,` and trims whitespace. Add `FurnitureRepository extends JpaRepository<Furniture, String>` if not already present; add query method `List<Furniture> findAll()` is sufficient (no pagination needed for the 24-row catalog; revisit in a future scaling task).

**FR-18 (RecommendationOrchestrator flow)** — `RecommendationOrchestrator.recommend(roomId, userId)`:
1. Load `Space` by `roomId` via `SpaceRepository`. If missing, throw `SpaceNotFoundException` (404).
2. Verify `space.userId == userId`; else throw `ForbiddenSpaceAccessException` (403).
3. Verify `space.status == ANALYZED`; else throw `AnalysisNotReadyException` (409) with errorCode `ANALYSIS_NOT_READY`.
4. Verify `space.preferredStyle != null`; else throw `PreferredStyleNotSetException` (409) with errorCode `PREFERRED_STYLE_NOT_SET` — the user must have completed Task 4 first.
5. Check the cache (FR-14). On HIT, return the cached response.
6. On MISS, load the full `furniture` catalog via `FurnitureRepository.findAll()`.
7. Parse `space.dimensions` (stored as `"widthMxlengthMxheightMm"` per Task 3 FR-12 step 4) into numeric `widthM/lengthM/heightM`. If parse fails, throw `SpaceDimensionsMalformedException` (500 — should not occur given Task 3's regex; defensive only).
8. Detected objects: for this task, Spring MUST pass an **empty array** `[]` for `space.detectedObjects` unless a new `space_detected_objects` table exists (it does not; Task 3 did not persist per-object detections). Rationale: `objectConflict` scoring still runs but always scores `1.00` — this is acceptable for v1. A future task (`UC-01-detected-objects-persist`) will populate this field; the Python side must handle the empty-array case as specified in FR-8.
9. Build `RecommendationRequest` and call `RecommendationClient.callRecommend(req)` on the `aiExecutor` thread pool (`CompletableFuture.supplyAsync(..., aiExecutor)` + `.join()`).
10. On Python success → cache the response, return it.
11. On `RecommendationTransportException` / `RecommendationFailedException`, do NOT cache. Re-throw; the controller maps to HTTP.

**FR-19 (Public endpoint)** — New endpoint on `com.authenticself.space.SpaceController` (or a new `RecommendationController` — Design Agent's choice, as long as the path below is served):
- `GET /api/v1/spaces/{roomId}/recommendations`
- Headers: `X-User-Id: <user_id>` (required; 401 if missing; 403 if mismatch with `space.user_id`).
- Query param `topNPerCategory`: integer, `1..10`, default `3`. Forwarded to Python.
- Success response (HTTP 200): same shape as Python `RecommendationResponse` (FR-2), with one additional field the Python side does NOT return:
  ```json
  {
    "roomId": "...",
    "status": "OK",
    "resolvedStyle": "MODERN",
    "preferredStyle": "MODERN",
    "generatedAt": "...",
    "cacheHit": false,
    "recommendations": { "desk": [...], "bed": [...], "chair": [...], "lighting": [...] },
    "warning": null,
    "processingMs": 18
  }
  ```
- `cacheHit` is true when FR-14's cache returned the payload.
- `warning` mirrors the Python `warning` field (string or null).
- Error responses (standard Spring envelope `{errorCode, message, correlationId}`):
  - 400 — `INVALID_TOP_N` (out of 1..10).
  - 401 — `UNKNOWN_USER` (X-User-Id missing).
  - 403 — `SPACE_ACCESS_DENIED`.
  - 404 — `SPACE_NOT_FOUND`.
  - 409 — `ANALYSIS_NOT_READY` (status != ANALYZED).
  - 409 — `PREFERRED_STYLE_NOT_SET`.
  - 422 — `CATALOG_EMPTY`, `RECOMMENDATION_FAILED`.
  - 502 — `AI_SERVICE_UNAVAILABLE` (transport failure on `/recommend/furniture`).

**FR-20 (Cache invalidation on preferred-style change)** — In `SpaceController.updatePreferredStyle` (Task 4 FR-14 `PUT`), after the DB UPDATE succeeds, invoke `recommendationOrchestrator.invalidate(roomId)`. This must evict any cached entry regardless of the preferredStyle value it was keyed on. A unit test covers: PUT → GET → verify `cacheHit == false`.

**FR-21 (Configuration surface — Spring)** — Extend `application.yml` with keys (all env-overridable):
- `app.ai.recommend.base-url` — defaults to the same value as `app.ai.space.base-url` (same FastAPI process).
- `app.ai.recommend.connect-timeout-ms` (default `2000`).
- `app.ai.recommend.read-timeout-ms` (default `15000`).
- `app.recommendation.cache.ttl-seconds` (default `600`).
- `app.recommendation.cache.max-entries` (default `1024`).
- `app.recommendation.default-top-n` (default `3`).

**FR-22 (Observability)** — `RecommendationOrchestrator.recommend` logs one INFO line with `roomId`, `userId`, `preferredStyle`, `catalogSize`, `pythonMs`, `totalMs`, `cacheHit`. Error paths log WARN/ERROR with the errorCode and HTTP status returned.

### React Native client — RecommendationScreen

**FR-23 (New screen `RecommendationScreen.tsx`)** — Create `src/mobile/src/screens/RecommendationScreen.tsx`:
- Receives route param `{ roomId: string, preferredStyle: PreferredStyle }` via the navigation stack (passed from `StyleSelectionScreen` after a successful `PUT .../preferred-style`).
- On mount: calls `getRecommendations(roomId, topNPerCategory=3)` (new method in the API client — FR-25).
- Renders a header showing `"<PreferredStyleLabel> 스타일 추천"` (e.g. `"모던 스타일 추천"`) using the label map from Task 4 FR-20.
- Renders four section headers in order: `"책상" | "침대" | "의자" | "조명"`. Each section renders its respective recommendations array.
- For each `RecommendationItem`, renders a card with:
  - `imageUrl` (use `<Image source={{uri: imageUrl}} />`; if load fails, show a grey placeholder).
  - `name` (primary text).
  - `price` formatted as `"₩<price.toLocaleString('ko-KR')>"` (e.g. `"₩189,000"`).
  - `fitScore` as a percentage badge: `"매칭 <round(fitScore*100)>%"`.
  - `rationale` as a small secondary text (2-line truncate).
  - A `"위시리스트에 추가"` button (stub — on tap it emits an analytics event `wishlist_add_clicked` with `{roomId, furnitureId}` and shows a toast `"위시리스트 기능은 준비 중입니다."`. The real wishlist write lands in UC-02 / Task 6).
- Empty-section state: if any category array is empty, render a subtle placeholder `"<카테고리>는 현재 추천할 가구가 없습니다."` under that section header.
- Full-empty state (all four categories empty AND `response.warning == "NO_FIT_ANY_CATEGORY"`): render a full-screen message `"이 공간에 딱 맞는 가구를 찾지 못했습니다. 다른 스타일을 선택해 보시겠어요?"` with a `"스타일 다시 선택"` button that `navigation.goBack()`s to `StyleSelectionScreen`.
- Loading state: while the fetch is in flight, show a spinner and the text `"추천을 준비하고 있어요..."`.
- Error state: on 4xx/5xx, show an inline error screen with the user-friendly message below and a `"다시 시도"` button that re-invokes the fetch. Mapping:
  - 409 `ANALYSIS_NOT_READY` → `"분석이 아직 끝나지 않았어요. 잠시 후 다시 시도해주세요."`
  - 409 `PREFERRED_STYLE_NOT_SET` → `"스타일을 먼저 선택해주세요."` with a `"스타일 선택"` button that `navigation.goBack()`s.
  - 502 `AI_SERVICE_UNAVAILABLE` → `"추천 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."`
  - Other → `"추천을 가져오지 못했습니다."`

**FR-24 (Navigation wiring)** — Replace the placeholder `"Recommendation"` route name registered in Task 4 FR-18 with the real `RecommendationScreen`. Route params type:
```typescript
type RecommendationRouteParams = {
  roomId: string;
  preferredStyle: PreferredStyle;
};
```

**FR-25 (API client — RN)** — Add to `src/mobile/src/api/spaces.ts`:
```typescript
export type ScoreBreakdown = {
  sizeFit: number;
  styleMatch: number;
  colorHarmony: number;
  objectConflict: number;
};
export type RecommendationItem = {
  furnitureId: string;
  name: string;
  type: 'desk' | 'bed' | 'chair' | 'lighting';
  price: number;
  imageUrl: string | null;
  fitScore: number;
  scoreBreakdown: ScoreBreakdown;
  rationale: string;
};
export type RecommendationResponse = {
  roomId: string;
  status: 'OK';
  resolvedStyle: Style;
  preferredStyle: PreferredStyle;
  generatedAt: string;
  cacheHit: boolean;
  recommendations: {
    desk:     RecommendationItem[];
    bed:      RecommendationItem[];
    chair:    RecommendationItem[];
    lighting: RecommendationItem[];
  };
  warning: 'NO_FIT_ANY_CATEGORY' | null;
  processingMs: number;
};
export async function getRecommendations(
  roomId: string,
  topNPerCategory = 3,
): Promise<RecommendationResponse> { /* GET /api/v1/spaces/{roomId}/recommendations?topNPerCategory=... */ }
```
The method MUST send header `X-User-Id` from the existing auth stub and return-type-narrow the backend response via a runtime shape check (e.g. a lightweight assertion function — no heavy runtime-validation lib required).

## 5. Data Contract

### Python `POST /recommend/furniture`
See FR-2 for full success / error examples.

### Spring `GET /api/v1/spaces/{roomId}/recommendations`
**Success — 200** — see FR-19 body.
**400** — `INVALID_TOP_N`.
**401** — `UNKNOWN_USER`.
**403** — `SPACE_ACCESS_DENIED`.
**404** — `SPACE_NOT_FOUND`.
**409** — `ANALYSIS_NOT_READY` | `PREFERRED_STYLE_NOT_SET`.
**422** — `CATALOG_EMPTY` | `RECOMMENDATION_FAILED`.
**502** — `AI_SERVICE_UNAVAILABLE`.

### DB entities touched
- `furniture` — ALTER + seed. V4 adds eight columns (`name`, `price`, `image_url`, `color_hex`, `width_cm`, `length_cm`, `height_cm`, `style_tags`); V5 populates 24+ rows.
- `spaces` — READ-only from this task (`user_id`, `status`, `dimensions`, `main_color`, `style`, `preferred_style`).
- **No new tables.** Recommendations are not persisted (cached in-memory only — FR-14). Persisting recommendations is a future task, likely tied to UC-03 admin analytics.

### Dependency on upstream IDs
- `roomId` — primary key into `spaces`, flows from UC-01-photo-upload → -space-analysis → -style-selection → here.
- `userId` — enforced via `X-User-Id` header; matched against `space.user_id`.
- `styleId` (i.e. `preferredStyle`) — sourced from `space.preferred_style` written by Task 4 FR-14 `PUT`.

## 6. Non-Functional Requirements

- **Python `POST /recommend/furniture` P95 latency**: ≤ 200 ms for a catalog of ≤ 50 items. Scoring is O(n) with constant per-item work; no ML model inference in v1.
- **Spring `GET /api/v1/spaces/{roomId}/recommendations` P95 latency**:
  - Cache HIT: ≤ 30 ms (pure DB short-circuit + in-memory read + JSON serialize).
  - Cache MISS: ≤ 500 ms end-to-end (DB load + Python call + cache write).
- **Determinism**: identical inputs (`space`, `preferredStyle`, `catalog`, `topNPerCategory`) → identical `recommendations` output, including identical `fitScore` values to 2 dp and identical ranking order. This is enforced by the fixed tie-breakers in FR-9 and the pure-function scoring in FR-5..FR-10.
- **No new heavy ML deps**: `torch`, `tensorflow`, `keras`, `transformers`, `ultralytics` MUST NOT appear in `src/ai/pyproject.toml` runtime deps after this task (re-run Task 3 AC-2 unchanged). Color conversion (hex → HSL) uses stdlib `colorsys` — no new dependency.
- **Ranking invariants (testable — see AC-10..AC-13)**:
  - For every item in `recommendations[*]`, `item.styleTags` includes `resolvedStyle` OR the `(resolvedStyle, some-tag-of-item)` pair is in FR-7's compatibility table OR the item's `styleMatch` is ≥ 0.10 (trivially true). Strong form: for every **category's top-1** item, `styleMatch >= 0.50`.
  - For every item in `recommendations[*]`, the hard-fit constraint (FR-5) passes: `max(widthCm, lengthCm) ≤ min(roomW, roomL) - 30` AND `heightCm ≤ roomH - 20`.
  - Per category, the array is sorted by `fitScore` DESC; for any two adjacent items with equal `fitScore`, `price` is ASC.
  - Each array length is `≤ topNPerCategory`.
- **Security**:
  - `GET /api/v1/spaces/{roomId}/recommendations` requires `X-User-Id` and enforces `space.user_id == X-User-Id` (403 on mismatch).
  - `/recommend/furniture` is an internal Python route (not directly exposed via the Spring API gateway); callable only from Spring's internal network. Production ingress MUST block it (same stance as `/analyze/space`).
  - No PII leaves Spring → Python: only `userId` (opaque string) and `roomId` are sent. The catalog data is non-sensitive.
- **Partial-success resilience**: a style-analysis failure upstream (Task 4 FR-12: `space.style == NULL`) does NOT break recommendations. FR-4's `CURRENT` fallback handles it; if `preferredStyle != CURRENT`, the missing `detectedStyle` is simply ignored.
- **Cache safety**: the in-memory cache (FR-14) is per-JVM. Horizontal scaling requires externalizing (Redis) — out of scope; documented as a future task.
- **Observability**: correlation IDs flow from Spring to Python via the header `X-Correlation-Id` (reuses the Task-3 error-envelope `correlationId`). Python logs MUST include this field on every log line emitted during request handling.
- **i18n**: user-visible strings (rationale, RN copy) are Korean per PRD wording. Enum values stay ASCII uppercase English.
- **Currency**: prices stored and transmitted in integer KRW. No localization/FX conversion in v1. RN formats as `"₩<n>"` with Korean number grouping (via `toLocaleString('ko-KR')`).

## 7. Acceptance Criteria

**AC-1 (Python — route registered)** — *Given* the Python service started via uvicorn, *when* `GET /openapi.json` is fetched, *then* `paths` contains `/analyze/space`, `/analyze/style`, AND `/recommend/furniture` — all three.

**AC-2 (Python — happy path)** — *Given* a valid request body with a 24-item catalog and a room of `widthM=4, lengthM=4, heightM=2.4`, `preferredStyle="MODERN"`, `detectedStyle="MODERN"`, `detectedObjects=[]`, *when* `POST /recommend/furniture` is called, *then* response is HTTP 200; body has `status=="OK"`, `resolvedStyle=="MODERN"`, `recommendations` has exactly the four keys `desk`, `bed`, `chair`, `lighting`, each array has length `≤ 3`, every item has all nine required fields (`furnitureId`, `name`, `type`, `price`, `imageUrl`, `fitScore`, `scoreBreakdown`, `rationale`; plus the four sub-score keys under `scoreBreakdown`), `processingMs > 0`.

**AC-3 (Python — determinism)** — *Given* an identical request body, *when* `POST /recommend/furniture` is called three times, *then* every non-`processingMs` field of the response is byte-identical across all three responses (including ordering within each category's array and the 2-dp `fitScore` values).

**AC-4 (Python — four category keys)** — *Given* a catalog with ZERO items of type `bed`, *when* `POST /recommend/furniture` is called, *then* `recommendations.bed` is `[]` (present, empty) AND the other three category keys are still present AND no additional keys appear under `recommendations`.

**AC-5 (Python — hard-fit exclusion)** — *Given* a catalog containing a desk of `widthCm=500, lengthCm=200, heightCm=100` and a room of `widthM=3, lengthM=3, heightM=2.4`, *when* `POST /recommend/furniture` is called, *then* that desk does NOT appear in `recommendations.desk` (hard-fit failure: `max(500,200)=500 > 300-30=270`).

**AC-6 (Python — soft-fit banding)** — *Given* a desk that consumes 15% of floor footprint, *when* scored, *then* its `scoreBreakdown.sizeFit == 1.0`. *Given* a desk at 25% footprint, *then* `sizeFit == 0.7`. *Given* a desk at 45% footprint, *then* `sizeFit == 0.4`. (Testable by hand-crafted catalog rows + a known room size.)

**AC-7 (Python — style match exact)** — *Given* `resolvedStyle="MODERN"` and a desk with `styleTags=["MODERN","SCANDINAVIAN"]`, *when* scored, *then* `scoreBreakdown.styleMatch == 1.00`.

**AC-8 (Python — style match compatible-pair)** — *Given* `resolvedStyle="MODERN"` and a desk with `styleTags=["SIMPLE"]`, *when* scored, *then* `scoreBreakdown.styleMatch == 0.50` (FR-7 table: MODERN↔SIMPLE).

**AC-9 (Python — style match incompatible)** — *Given* `resolvedStyle="MODERN"` and a desk with `styleTags=["CLASSIC"]`, *when* scored, *then* `scoreBreakdown.styleMatch == 0.10`.

**AC-10 (Python — color harmony analogous)** — *Given* `space.mainColor="#E8D9B0"` and an item with `colorHex="#E8C9A0"` (close hue), *when* scored, *then* `scoreBreakdown.colorHarmony >= 0.85`.

**AC-11 (Python — color harmony clashing)** — *Given* `space.mainColor="#E8D9B0"` (beige, hue ≈ 40°) and an item with `colorHex="#106090"` (deep blue-cyan, hue ≈ 200°), *when* scored, *then* `scoreBreakdown.colorHarmony <= 0.55`.

**AC-12 (Python — object conflict)** — *Given* `detectedObjects=[{"type":"bed","bboxNorm":[0.1,0.4,0.6,0.9],"confidence":0.82}]` and a catalog with two beds and two desks, *when* scored, *then* every bed in `recommendations.bed` has `scoreBreakdown.objectConflict == 0.20`; every desk has `objectConflict == 1.00`.

**AC-13 (Python — lighting never penalized for conflict)** — *Given* `detectedObjects=[{"type":"lighting","bboxNorm":[...],"confidence":0.9}]` and a lighting item in the catalog, *when* scored, *then* that lighting item's `scoreBreakdown.objectConflict == 1.00` (FR-8 exception).

**AC-14 (Python — ranking sort + tie-breaker)** — *Given* two items in the same category with identical `fitScore`, *when* ranked, *then* the one with the lower `price` appears first; if prices tie, the one with the lexicographically lower `furnitureId` appears first.

**AC-15 (Python — fitScore weights)** — *Given* the Python source `src/ai/app/analyzers/recommender.py`, *when* grepped, *then* the file contains literal numeric weights `0.35` (sizeFit), `0.30` (styleMatch), `0.20` (colorHarmony), `0.15` (objectConflict), and no other weight set that also sums to 1.0 appears.

**AC-16 (Python — `resolvedStyle` CURRENT→detectedStyle)** — *Given* `preferredStyle="CURRENT"` and `detectedStyle="INDUSTRIAL"`, *when* `POST /recommend/furniture` is called, *then* response `resolvedStyle == "INDUSTRIAL"`.

**AC-17 (Python — `resolvedStyle` CURRENT→fallback)** — *Given* `preferredStyle="CURRENT"` and `detectedStyle=null`, *when* `POST /recommend/furniture` is called, *then* response `resolvedStyle == "MODERN"` (FR-4 documented default).

**AC-18 (Python — `resolvedStyle` explicit wins)** — *Given* `preferredStyle="SIMPLE"` and `detectedStyle="INDUSTRIAL"`, *when* `POST /recommend/furniture` is called, *then* response `resolvedStyle == "SIMPLE"` (explicit user choice overrides detected).

**AC-19 (Python — NO_FIT_ANY_CATEGORY)** — *Given* a tiny room (`widthM=1.5, lengthM=1.5`) and a catalog where every item's longer dimension is ≥ 140 cm, *when* `POST /recommend/furniture` is called, *then* response is HTTP 200, every category array is empty `[]`, `warning == "NO_FIT_ANY_CATEGORY"`.

**AC-20 (Python — error envelope: CATALOG_EMPTY)** — *Given* a request with `catalog=[]`, *when* posted, *then* response is HTTP 422 with body `{errorCode:"CATALOG_EMPTY", message:<string>, roomId:<string>}`.

**AC-21 (Python — error envelope: INVALID_REQUEST)** — *Given* a request with `preferredStyle="BAROQUE"`, *when* posted, *then* response is HTTP 422 with `errorCode:"INVALID_REQUEST"`.

**AC-22 (Python — async handler)** — *Given* `src/ai/app/routes/recommend.py`, *when* reading, *then* the handler is declared `async def`.

**AC-23 (Python — no new heavy ML deps)** — *Given* `src/ai/pyproject.toml` (or `requirements.txt`), *when* re-running Task 3 AC-2, *then* it still passes — `torch`, `tensorflow`, `keras`, `transformers`, `ultralytics` absent from runtime deps.

**AC-24 (V4 migration present)** — *Given* `src/backend/src/main/resources/db/migration/`, *when* listed, *then* a file matching `V4__extend_furniture_catalog.sql` exists AND its content includes `ALTER TABLE furniture`, `ADD COLUMN name`, `ADD COLUMN price`, `ADD COLUMN image_url`, `ADD COLUMN color_hex`, `ADD COLUMN width_cm`, `ADD COLUMN length_cm`, `ADD COLUMN height_cm`, `ADD COLUMN style_tags`.

**AC-25 (V5 seed migration present + row count)** — *Given* the migration directory, *when* listed, *then* `V5__seed_furniture_catalog.sql` exists; after boot, `SELECT COUNT(*) FROM furniture` returns `>= 24` AND there are `>= 6` rows per `type` for each of `desk`, `bed`, `chair`, `lighting`.

**AC-26 (V5 seed covers branches)** — *Given* the seeded catalog, *when* querying, *then* (a) at least one row per type has `max(width_cm, length_cm) > 400` (oversize coverage for FR-5 hard-fail); (b) at least one row per type has `style_tags` containing exactly one tag from each of the five styles across the full catalog (every enum value appears in at least one row's `style_tags`).

**AC-27 (Spring — RecommendationClient shape)** — *Given* `RecommendationClient.java`, *when* inspecting, *then* it has a public method `RecommendationResponse callRecommend(RecommendationRequest req)` with exactly one parameter of type `RecommendationRequest` and the same DTO-return pattern as `SpaceAnalysisClient` + `StyleAnalysisClient`.

**AC-28 (Spring — happy path, cache MISS)** — *Given* a `spaces` row for `u1` with `roomId='r1'`, `status='ANALYZED'`, `dimensions='4.0x4.0x2.4m'`, `main_color='#E8D9B0'`, `style='MODERN'`, `preferred_style='MODERN'`, and a seeded `furniture` catalog, *when* `GET /api/v1/spaces/r1/recommendations` is called with `X-User-Id: u1`, *then* response is HTTP 200; body has `cacheHit == false`, `resolvedStyle == "MODERN"`, `preferredStyle == "MODERN"`, all four category arrays present, and `warning == null`.

**AC-29 (Spring — cache HIT on second call)** — *Given* AC-28 just ran, *when* the identical request is made again, *then* response is HTTP 200 with `cacheHit == true` AND `generatedAt` equals the timestamp from the first response (same cached payload) AND `pythonMs` is either absent or 0 (no second Python call — verifiable by WireMock stub call count remaining at 1).

**AC-30 (Spring — cache invalidation on preferred-style change)** — *Given* the cache is populated (AC-28 ran once), *when* `PUT /api/v1/spaces/r1/preferred-style {"preferredStyle":"SIMPLE"}` is executed, THEN `GET /api/v1/spaces/r1/recommendations` is called, *then* the second GET has `cacheHit == false` AND `resolvedStyle == "SIMPLE"` (the Python stub was called a second time — verifiable by WireMock call count == 2).

**AC-31 (Spring — 409 ANALYSIS_NOT_READY)** — *Given* a `spaces` row with `status='PENDING_ANALYSIS'`, *when* the recommendations GET is called, *then* response is HTTP 409 with `errorCode == "ANALYSIS_NOT_READY"`; no Python call is made.

**AC-32 (Spring — 409 PREFERRED_STYLE_NOT_SET)** — *Given* a `spaces` row with `status='ANALYZED'` but `preferred_style IS NULL`, *when* the recommendations GET is called, *then* response is HTTP 409 with `errorCode == "PREFERRED_STYLE_NOT_SET"`; no Python call is made.

**AC-33 (Spring — 403 on X-User-Id mismatch)** — *Given* a `spaces` row owned by `u1`, *when* the recommendations GET is called with `X-User-Id: u2`, *then* response is HTTP 403 with `errorCode == "SPACE_ACCESS_DENIED"`; no recommendation data is leaked.

**AC-34 (Spring — 404 on unknown roomId)** — *Given* no `spaces` row for `roomId='ghost'`, *when* the recommendations GET is called, *then* response is HTTP 404 with `errorCode == "SPACE_NOT_FOUND"`.

**AC-35 (Spring — 400 on INVALID_TOP_N)** — *Given* AC-28 setup, *when* the GET is called with `?topNPerCategory=11` (or `0`, or negative), *then* response is HTTP 400 with `errorCode == "INVALID_TOP_N"`.

**AC-36 (Spring — 502 on Python transport failure)** — *Given* AC-28 setup but the Python service port closed (or WireMock returning connection-reset), *when* the GET is called, *then* response is HTTP 502 with `errorCode == "AI_SERVICE_UNAVAILABLE"` AND the cache is NOT populated for this `(roomId, preferredStyle)` key.

**AC-37 (Spring — Python error mapping: CATALOG_EMPTY)** — *Given* a seeded catalog deleted to zero rows (test harness truncates `furniture`), *when* the GET runs (or WireMock returns `{errorCode:"CATALOG_EMPTY"}` on the Python stub), *then* Spring responds HTTP 422 with `errorCode == "CATALOG_EMPTY"`.

**AC-38 (Spring — empty detectedObjects array)** — *Given* AC-28 setup, *when* Spring assembles the Python request, *then* the request body's `space.detectedObjects` is `[]` (empty array, not missing, not null). Verifiable by intercepting the WireMock request body and JSON-asserting `$.space.detectedObjects == []`.

**AC-39 (Spring — ranking invariant: top-1 styleMatch ≥ 0.50)** — *Given* AC-28 setup, *when* inspecting the response, *then* for each of the four categories' first (top-1) item, `scoreBreakdown.styleMatch >= 0.50` (strong ranking invariant from NFR).

**AC-40 (Spring — ranking invariant: hard-fit on every returned item)** — *Given* AC-28 setup and room dimensions `4.0x4.0x2.4`, *when* inspecting every item in the response across all four categories, *then* for each item: `max(item width, item length in cm) <= 400 - 30 == 370` AND `item heightCm <= 240 - 20 == 220`. (Hard-fit guarantee from FR-5.)

**AC-41 (Spring — ranking invariant: sort order)** — *Given* AC-28 setup, *when* inspecting each category array, *then* `fitScore[i] >= fitScore[i+1]` for all adjacent pairs; for any adjacent pair with equal `fitScore`, `price[i] <= price[i+1]`.

**AC-42 (Spring — `Furniture` entity extended)** — *Given* `Furniture.java`, *when* inspecting its fields, *then* the class has fields `name` (String), `price` (int/Integer), `imageUrl` (String, nullable), `colorHex` (String), `widthCm` (int), `lengthCm` (int), `heightCm` (int), `styleTags` (String), each annotated `@Column(name=<snake_case>)`; AND a public method `List<String> getStyleTagsList()` exists.

**AC-43 (Spring — application.yml keys)** — *Given* `src/backend/src/main/resources/application.yml`, *when* parsed, *then* keys `app.ai.recommend.base-url`, `app.ai.recommend.connect-timeout-ms`, `app.ai.recommend.read-timeout-ms`, `app.recommendation.cache.ttl-seconds`, `app.recommendation.cache.max-entries`, `app.recommendation.default-top-n` all present with the FR-21 defaults.

**AC-44 (RN — RecommendationScreen exists with four sections)** — *Given* `src/mobile/src/screens/RecommendationScreen.tsx` rendered in a test with a mocked `getRecommendations` returning two items per category and `preferredStyle='MODERN'`, *when* the render completes, *then* the output contains all four Korean section headers `"책상"`, `"침대"`, `"의자"`, `"조명"`, contains the text `"모던 스타일 추천"`, and renders 8 item cards total.

**AC-45 (RN — item card content)** — *Given* AC-44 setup and an item with `name="Oslo Slim Desk"`, `price=189000`, `fitScore=0.87`, `rationale="모던 스타일 일치, 공간에 여유롭게 들어맞음"`, *when* rendered, *then* the card contains the substrings `"Oslo Slim Desk"`, `"₩189,000"`, `"매칭 87%"`, and the rationale text (possibly truncated).

**AC-46 (RN — empty section placeholder)** — *Given* a mocked response where `recommendations.bed = []`, *when* rendered, *then* the bed section renders the placeholder `"침대는 현재 추천할 가구가 없습니다."` (or FR-23's specified copy); no bed cards are rendered.

**AC-47 (RN — full-empty NO_FIT state)** — *Given* a mocked response with all four arrays empty and `warning == "NO_FIT_ANY_CATEGORY"`, *when* rendered, *then* the screen contains the substring `"딱 맞는 가구를 찾지 못했"` AND a `"스타일 다시 선택"` button; tapping the button invokes `navigation.goBack()` exactly once.

**AC-48 (RN — wishlist stub)** — *Given* AC-45 setup, *when* the user taps the `"위시리스트에 추가"` button on an item, *then* the analytics stub receives an event `wishlist_add_clicked` with `{roomId, furnitureId}` exactly once AND a toast appears with copy `"위시리스트 기능은 준비 중입니다."`; no network call to any `/wishlist` endpoint occurs (verifiable via spy on fetch / API client).

**AC-49 (RN — 409 PREFERRED_STYLE_NOT_SET → back to style picker)** — *Given* a mocked 409 response with `errorCode == "PREFERRED_STYLE_NOT_SET"`, *when* the screen mounts, *then* the error UI shows `"스타일을 먼저 선택해주세요."` AND a `"스타일 선택"` button; tapping it invokes `navigation.goBack()` once.

**AC-50 (RN — loading spinner)** — *Given* a never-resolving mocked `getRecommendations`, *when* the screen is mounted and 500 ms pass on a fake timer, *then* the screen renders a spinner AND the text `"추천을 준비하고 있어요..."`.

**AC-51 (OpenAPI contract artifact)** — *Given* `artifacts/UC-01-recommendation/api_contract.yaml`, *when* parsed as OpenAPI 3.0.x, *then* it defines: (a) Python `POST /recommend/furniture` with full request/response schemas per FR-2; (b) Spring `GET /api/v1/spaces/{roomId}/recommendations` with the extended response schema from FR-19 (including `cacheHit`, `preferredStyle`, `warning`); (c) all error envelopes; (d) all four `type` enum values (`desk`, `bed`, `chair`, `lighting`) appear in the item schema.

**AC-52 (No regression on UC-01-style-selection)** — *Given* the full verification suite, *when* re-running UC-01-style-selection AC-1..AC-37, *then* all pass. Specifically: AC-9 (V3 migration present) still holds; this task's V4 and V5 migrations run AFTER V3 without collision.

**AC-53 (No regression on UC-01-space-analysis and UC-01-photo-upload)** — *Given* the full verification suite, *when* re-running the prior three tasks' ACs, *then* all pass. This task makes no changes to `/analyze/space`, `/analyze/style`, the upload endpoint, or the V1..V3 migrations.

## 8. Out of Scope
- **Real ML recommendation models** (collaborative filtering, matrix factorization, embedding-based retrieval) — scoring is deterministic rule-based. Replacing it is a future task (`ml-recommender-v1`).
- **Persisting recommendations** — results live in-memory cache only. A `recommendations` table + history for analytics is a future task, likely bundled with UC-03.
- **Wishlist writes** — the `"위시리스트에 추가"` button is a stub that emits an analytics event + toast. The actual `POST /api/v1/wishlist` endpoint + DB insert is Task 6 (UC-02-wishlist).
- **Persisting per-space `detectedObjects`** — Task 3 did NOT add a `space_detected_objects` table; this task sends an empty `detectedObjects=[]` on every request. A future task (`UC-01-detected-objects-persist`) will:
  1. Add a new table,
  2. Have `/analyze/space` populate it,
  3. Have Spring forward the real detected objects to `/recommend/furniture`.
  At that point `objectConflict` stops being a trivial `1.00` on every item.
- **Distributed cache** — the in-memory cache is per-JVM. Redis externalization is a future infra task.
- **Pagination** of the furniture catalog — `FurnitureRepository.findAll()` returns the full table (24–48 rows). Paginated retrieval + streaming scoring is a future scaling task.
- **Price filtering / budget constraint** — the user cannot set a max price in this task. A future `UC-01-budget-filter` task would add a `maxPrice` query param to the endpoint.
- **Multi-language recommendations** — `rationale` and RN copy are Korean only.
- **A/B testing of weights** — the `0.35/0.30/0.20/0.15` weights are hardcoded. A config-driven / experiment-framework version is out of scope.
- **Image-similarity-based recommendations** — no CNN embeddings or visual similarity. Color harmony is a single HSL hue-delta bucket.
- **Admin-side recommendation analytics** — UC-03 will read aggregate recommendation usage; this task does not emit any analytics to the admin backend beyond the observability logs (FR-22).
- **AR preview of a recommended item** — Task 8 (AR-furniture-placement) consumes the `furnitureId` + `widthCm/lengthCm/heightCm` fields seeded by this task but implements its own preview flow.
- **Authorization beyond `X-User-Id`** — no JWT/OAuth introduced; same stance as upstream UC-01 tasks.

## 9. Dependencies
- **Hard**:
  - `DB-schema-init` (Task 1) — base `furniture`, `spaces`, `users` tables.
  - `UC-01-photo-upload` (Task 2) — V2 migration with `spaces.status`, `photo_url`, etc.
  - `UC-01-space-analysis` (Task 3) — `/analyze/space` endpoint, `spaces.dimensions` + `main_color` written, `AIOrchestrator` + `aiExecutor` bean.
  - `UC-01-style-selection` (Task 4) — V3 migration with `spaces.preferred_style`, `/analyze/style` route, `PUT .../preferred-style` endpoint, `Style` + `PreferredStyle` enums, `StyleSelectionScreen`, placeholder `"Recommendation"` route name.
- **Consumed by**:
  - `UC-02-wishlist` (Task 6) — the `"위시리스트에 추가"` button's real implementation; consumes `furnitureId` + `price` fields seeded by this task's V5.
  - `UC-03-admin-overview` (Task 7) — admin dashboard will surface catalog stats + (future) recommendation analytics.
  - `AR-furniture-placement` (Task 8) — AR preview consumes the `widthCm/lengthCm/heightCm` + `imageUrl` fields added by this task's V4/V5.
- **No dependency on**: none of Tasks 6/7/8 must be merged before this task.
