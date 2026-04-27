# Task Spec: UC-01-style-selection

## 1. Goal
Close the PRD §8 "AI 병렬 처리" block and the PRD §6 UC-01 step-3 "스타일 설정" UX: (a) add a sibling `POST /analyze/style` endpoint to the existing Python AI service that classifies an uploaded room photo into one of five interior styles with a confidence score; (b) extend the Spring `AIOrchestrator` to fan out `/analyze/space` + `/analyze/style` in parallel via `CompletableFuture.allOf(...)` and persist both outputs on the `spaces` row; (c) give the React Native client a `StyleSelectionScreen` that polls the analysis state, shows the AI-detected style as a suggestion badge, and lets the user pick the target style (`CURRENT | MODERN | SIMPLE | CLASSIC | SCANDINAVIAN | INDUSTRIAL`) for the downstream recommendation task.

## 2. Source (PRD section)
- **PRD §6 UC-01 기본 흐름 step 3** — "사용자는 '현재 디자인 그대로', '모던(Modern)', '심플(Simple)' 등 본인이 원하는 인테리어 방향(스타일)을 설정." Drives the `StyleSelectionScreen` + `preferred_style` persistence.
- **PRD §8 sequence diagram (`par [AI 병렬 처리]` block)** — space analysis and style recognition must be initiated concurrently and joined before the RN client is notified. Drives FR-11 (`CompletableFuture.allOf`).
- **PRD §4 기술 스택** — Python-side style inference belongs alongside `/analyze/space` in the same FastAPI service (Task 3 already scaffolded `StyleClassifier` as a placeholder; this task wires it to an HTTP route).
- **PRD §3 DB 설계 — `spaces.style`** — VARCHAR(64) NULL column already reserved for the AI-detected style output. This task also adds one new column `spaces.preferred_style VARCHAR(32) NULL` (see §5 "Schema delta" below for the decision rationale).
- **PRD §9 시스템 아키텍처** — `AI Orchestrator (Spring)` fans out to `공간 분석 서버 (Python FastAPI)` and `스타일 & 추천 서버 (Python)`. In this project the two Python roles share one FastAPI process (Task 3 decision); style is a sibling route on the same service.

## 3. Actors & Preconditions
- **Primary actor (backend)**: the Spring `AIOrchestrator` component (introduced in Task 3). This task extends it from a single-downstream caller to a parallel-fan-out caller.
- **Primary actor (mobile)**: the React Native client's `StyleSelectionScreen`, invoked after the `AnalyzingScreen` observes `status='ANALYZED'` via polling.
- **Secondary actor (Python)**: the existing FastAPI service at `src/ai/` — gains one new route and reuses Task-3's `StyleClassifier` placeholder.
- **Tertiary actor**: the user — picks one of five target styles (or sticks with "현재 디자인 그대로" / `CURRENT`).
- **Preconditions**:
  - `DB-schema-init` (Task 1), `UC-01-photo-upload` (Task 2), and `UC-01-space-analysis` (Task 3) are merged. V2 schema is in place (`spaces.style` nullable VARCHAR).
  - The Python service runs Task 3's `/analyze/space` and `/health` routes and has the `StyleClassifier` placeholder class defined (Task 3 FR-4 / FR-5).
  - Spring has `SpaceAnalysisClient` + `AIOrchestrator` + `AIOrchestratorController` (Task 3 FR-11..FR-13). This task adds `StyleAnalysisClient` as a sibling.
  - The RN app (src/mobile/) already has `AnalyzingScreen.tsx` (Task 2/3 output) and a navigation stack — this task adds a new screen and a navigation edge.

## 4. Functional Requirements

### Python AI service (new route on the existing service)

**FR-1 (Register `POST /analyze/style`)** — Add a new FastAPI router mounted at `/analyze/style` in `src/ai/app/main.py`. The route MUST live in its own module `src/ai/app/routes/style.py` (mirroring the `routes/space.py` pattern established in Task 3). The existing `TODO(task-4)` comment stub placed by Task 3 FR-10 is REMOVED as part of this task.

**FR-2 (Endpoint contract — `POST /analyze/style`)** — Request body is identical in shape to `/analyze/space`:
```json
{ "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "photoUrl": "file:///C:/AuthenticSelf_Project/AuthenticSelf_v3/var/object-storage/spaces/2026/04/17/01HXYB2K9VQWM4P3ZT6N5C8DAE.jpg" }
```
Success response (HTTP 200):
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "status": "OK",
  "style": "MODERN",
  "confidence": 0.72,
  "scores": {
    "MODERN":       0.72,
    "SIMPLE":       0.12,
    "CLASSIC":      0.06,
    "SCANDINAVIAN": 0.07,
    "INDUSTRIAL":   0.03
  },
  "processingMs": 64
}
```
Constraints:
- `style` enum values: exactly `MODERN | SIMPLE | CLASSIC | SCANDINAVIAN | INDUSTRIAL`. `CURRENT` is a user-only choice and MUST NEVER appear as an AI output.
- `scores` MUST contain all five style keys and sum to a value in `[0.99, 1.01]` (float-rounding tolerance).
- `style` MUST equal the `argmax` of `scores`.
- `confidence` MUST equal `scores[style]`, rounded to 2 dp.
- Request validation rules are identical to `/analyze/space` (FR-3 of Task 3): `roomId` is `^[A-Za-z0-9_-]{1,64}$`; `photoUrl` must start with `file://`.

**FR-3 (Wire `StyleClassifier` placeholder from Task 3)** — The route handler MUST use the existing `StyleClassifier` class from `src/ai/app/analyzers/style_classifier.py` (created as a placeholder in Task 3 FR-4/FR-5). This task does NOT introduce a new analyzer class — it only adds a route that calls the existing one. The analyzer is injected via the same DI surface as `/analyze/space` (FastAPI `Depends` or factory).

**FR-4 (StyleClassifier deterministic stub behavior)** — `StyleClassifier.analyze(image_path)` MUST:
- Be deterministic: same image bytes → same scores → same `style`.
- Be implemented by computing `sha256(bytes)` of the image file, mapping the first 20 hex chars into five float values in `[0, 1]`, normalizing them to sum to 1.0, and returning `argmax` as the `style`. The hash-to-score mapping must be documented in the class docstring.
- NOT import `torch`, `tensorflow`, `keras`, or any model-weights file (same runtime-dependency hygiene as Task 3 FR-5 + AC-2).
- Have a `TODO(ml)` docstring explicitly stating "replace with real style classifier (ResNet/CLIP/etc.) in a future task" so the placeholder status is obvious.

**FR-5 (Error contract — reuse Task-3 envelope)** — `/analyze/style` reuses the exact same error-envelope schema defined in Task 3 FR-7:
```json
{ "errorCode": "IMAGE_NOT_FOUND", "message": "…", "roomId": "…optional…" }
```
with identical error codes (`INVALID_REQUEST`, `IMAGE_NOT_FOUND`, `IMAGE_READ_FAILED`, `ANALYSIS_FAILED`, `INTERNAL_ERROR`) and identical HTTP status codes. Rationale: the Spring side handles both routes with one error-mapping table (FR-13 below).

**FR-6 (Async handler)** — The route handler MUST be declared `async def` and MUST run `StyleClassifier.analyze(path)` via `asyncio.to_thread(...)` (or `run_in_threadpool`) — identical concurrency pattern to `/analyze/space` (Task 3 FR-8). This is load-bearing for FR-11: the two Python calls run concurrently on the Python side as well (each in its own thread), so a single-threaded event-loop block on `/analyze/space` does not starve `/analyze/style`.

**FR-7 (Same path-escape hardening)** — `photoUrl` resolution uses the same `AI_PHOTO_ROOT`-based allow-list as Task 3 FR-security. A path escaping the root returns `IMAGE_NOT_FOUND` 422 (not `INTERNAL_ERROR`).

### Spring backend — parallel fan-out + user's preferred style

**FR-8 (New package members)** — Under `com.authenticself.ai`:
- `StyleAnalysisClient` — typed HTTP client, sibling of `SpaceAnalysisClient`. Single public method `StyleAnalysisResponse callStyleAnalysis(String roomId, String photoUrl)`. Same transport library and same timeouts as `SpaceAnalysisClient` (connect 2 s, read 10 s, env-overridable via `app.ai.style.connect-timeout-ms` / `app.ai.style.read-timeout-ms`).
- `dto/StyleAnalysisRequest` / `dto/StyleAnalysisResponse` — Jackson-bound records. `StyleAnalysisResponse` fields: `roomId`, `status`, `style`, `confidence`, `scores` (Map<String, Double>), `processingMs`.
- `dto/StyleScores` — optional typed wrapper if the Design Agent prefers it to `Map<String, Double>`; either is acceptable.
- Enum `com.authenticself.space.Style` with values `MODERN, SIMPLE, CLASSIC, SCANDINAVIAN, INDUSTRIAL` — canonical AI-output set.
- Enum `com.authenticself.space.PreferredStyle` with values `CURRENT, MODERN, SIMPLE, CLASSIC, SCANDINAVIAN, INDUSTRIAL` — canonical user-choice set. The only difference vs `Style` is the extra `CURRENT` value. Both enums stringify to their uppercase names for JSON and for DB persistence.

**FR-9 (DB schema delta — new column `spaces.preferred_style`)** — Add Flyway migration `V3__add_preferred_style.sql`:
```sql
ALTER TABLE spaces
    ADD COLUMN preferred_style VARCHAR(32) NULL AFTER style;
```
- VARCHAR(32) is sufficient (longest enum value `SCANDINAVIAN` = 12 chars + headroom).
- NULL means "user has not picked yet" (expected default after analysis completes).
- No index required (per-row lookup by `room_id` only; no aggregate queries on `preferred_style` in this task).
- The column is NOT added to `UC-01-space-analysis` via an amended migration — this task owns V3. Rationale: Task 3 explicitly disclaimed schema changes (AC-26 of Task 3).

**FR-10 (Space entity update)** — Extend the existing `Space` JPA entity with field `PreferredStyle preferredStyle` (nullable), annotated `@Enumerated(EnumType.STRING) @Column(name="preferred_style", length=32, nullable=true)`. Existing `style` field (String, nullable) stays as-is — it's populated by the AI path, so String is safest given a future where `Style` evolves faster than the schema. (The Design Agent may choose to make `style` an `@Enumerated(EnumType.STRING) Style` if they accept the forward-compat risk; either is acceptable — encoded in AC-18 below.)

**FR-11 (Parallel AI fan-out in AIOrchestrator)** — Replace the single `client.callSpaceAnalysis(...)` call in `AIOrchestrator.analyze(roomId)` with:
```java
CompletableFuture<SpaceAnalysisResponse> spaceFuture =
    CompletableFuture.supplyAsync(() -> spaceClient.callSpaceAnalysis(roomId, photoUrl), aiExecutor);
CompletableFuture<StyleAnalysisResponse> styleFuture =
    CompletableFuture.supplyAsync(() -> styleClient.callStyleAnalysis(roomId, photoUrl), aiExecutor);
CompletableFuture.allOf(spaceFuture, styleFuture).join();  // wraps exceptions in CompletionException
```
- Both calls MUST be initiated before either `.get()` / `.join()` — verifiable by inspecting source (no sequential `client.call...()` invocations outside an executor).
- `aiExecutor` is a dedicated `ThreadPoolExecutor` bean with min 2 / max 8 threads, queue 64, named `ai-orchestrator-`, registered as a Spring `@Bean`. This isolates AI I/O from the Tomcat request threadpool.
- The total orchestrator latency MUST be `max(t_space, t_style)` not `t_space + t_style`. Verification AC uses WireMock with `--fixedDelay` on both stubs to check wall-clock.
- The overall timeout is the `max` of the two clients' read-timeouts (both currently 10 s). If either future throws, the outcome follows FR-12.

**FR-12 (Partial-success semantics)** — When `CompletableFuture.allOf` completes:
- **Both succeeded** → `space.status = ANALYZED`, write `dimensions`, `main_color`, `style`, `analysis_date = now()`. (Here `style` is the AI-detected `style` string, e.g. `"MODERN"`.)
- **Space succeeded, style failed** → `space.status = ANALYZED`, write `dimensions`, `main_color`, `analysis_date = now()`; leave `style` NULL. Log a WARN with the style error. Rationale: per the task brief, the user's manual style pick is still possible without an AI suggestion, so space analysis success is sufficient to unblock the UX.
- **Space failed (transport), style either** → `space.status` STAYS `PENDING_ANALYSIS` (transport error is retryable, per Task 3 FR-15's transport-failure rule); re-throw `AiTransportException`.
- **Space failed (analyzer error, not transport), style either** → `space.status = FAILED`, `analysis_date = now()`; re-throw the mapped `AiServiceException`. (Space is the primary signal; if the image is unreadable, the style output is meaningless anyway.)
- **Space succeeded, style transport-failed** → treated same as "style failed" above: `status = ANALYZED`, `style = NULL`, log WARN. Transport failure on style alone MUST NOT block the user flow.
- Exception unwrapping: since the join happens via `CompletableFuture.allOf(...).join()`, the orchestrator MUST unwrap `CompletionException.getCause()` before matching on `AiServiceException` subtypes.

**FR-13 (Error-code mapping — extend Task-3 table)** — The Python error codes on `/analyze/style` use the exact same mapping table as `/analyze/space` (Task 3 FR-15), but with different Spring-side error codes for the "both failed" case:
| Python errorCode / condition (on `/analyze/style`) | Spring exception | HTTP | Spring errorCode |
|---|---|---|---|
| `IMAGE_NOT_FOUND` (422) | `AiImageNotFoundException` | 422 | `ANALYSIS_IMAGE_NOT_FOUND` |
| `IMAGE_READ_FAILED` (422) | `AiImageReadFailedException` | 422 | `ANALYSIS_IMAGE_READ_FAILED` |
| `ANALYSIS_FAILED` (422) | `AiAnalysisFailedException` | 422 | `ANALYSIS_FAILED` |
| transport/timeout | `AiTransportException` | 502 | `AI_SERVICE_UNAVAILABLE` |
Note: a style-only failure is NOT surfaced as an error response by the internal orchestrator controller (it's degraded-success per FR-12); these mappings apply only when the space call also fails, or when the style call is invoked standalone via the controller's retry path (out of scope — reserved for a future task).

**FR-14 (New public endpoints — state query + preferred-style write)** — Two new REST endpoints under `/api/v1/spaces/{roomId}` (these ARE CORS-enabled and ARE in the public OpenAPI — unlike Task 3's `/internal/v1/*`):
- **`GET /api/v1/spaces/{roomId}`** — returns the current analysis state for the RN client's polling. Response body:
  ```json
  {
    "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
    "status": "ANALYZED",                    // PENDING_ANALYSIS | ANALYZED | FAILED
    "dimensions": "3.6x4.2x2.4m",            // null if not yet analyzed
    "mainColor": "#E8D9B0",                  // null if not yet analyzed
    "style": "MODERN",                       // null if style failed or not yet analyzed
    "styleConfidence": 0.72,                 // null if style is null; from a transient field (see below)
    "preferredStyle": null,                  // user has not picked yet
    "analysisDate": "2026-04-17T10:23:15Z",  // null if still pending
    "uploadedAt":  "2026-04-17T10:23:03Z"
  }
  ```
  - `styleConfidence` is NOT persisted in V3 — only `style` (the argmax) is. For this task, `styleConfidence` is returned in the same HTTP round-trip that the orchestrator wrote `style` (cached in an in-memory write-through map keyed by `roomId`, TTL 15 min) OR is simply omitted / null on subsequent reads. The Design Agent picks one; either is acceptable, but the field MUST be present in the response schema (nullable).
  - Authorization: header `X-User-Id` required (mirrors UC-01-photo-upload); the endpoint returns 403 if the `spaces` row's `user_id` does not match.
  - 404 if no `spaces` row for `roomId`.
- **`PUT /api/v1/spaces/{roomId}/preferred-style`** — request body `{ "preferredStyle": "MODERN" }` (validated against the six-value `PreferredStyle` enum; 400 on unknown value). Response 200 with the same body shape as `GET`. Side effect: single DB UPDATE on `spaces.preferred_style`.
  - Authorization: same `X-User-Id` check as GET.
  - 409 if `spaces.status != 'ANALYZED'` (user may not pick a preferred style until analysis finishes — rationale: prevents setting a preference on a row that will later be flipped to `FAILED`; also matches PRD §6 step-3 flow where style selection follows successful analysis).
  - Idempotent: the same PUT can be repeated; latest value wins.

**FR-15 (Controller + routing)** — Controller `com.authenticself.space.SpaceController` (create if not present) owns both public endpoints. It sits next to the existing UC-01-photo-upload controller. The `/internal/v1/spaces/{roomId}/analyze` endpoint from Task 3 is NOT modified by this task.

**FR-16 (Configuration surface — Spring)** — Extend `application.yml` with keys (all env-overridable):
- `app.ai.style.base-url` — defaults to the same value as `app.ai.space.base-url` (same FastAPI process).
- `app.ai.style.connect-timeout-ms` (default `2000`).
- `app.ai.style.read-timeout-ms` (default `10000`).
- `app.ai.orchestrator.executor.core-size` (default `2`).
- `app.ai.orchestrator.executor.max-size` (default `8`).
- `app.ai.orchestrator.executor.queue-capacity` (default `64`).

**FR-17 (Observability — parallel path)** — `AIOrchestrator.analyze` logs a single INFO line with `roomId`, `spaceMs`, `styleMs`, `totalMs` on success (where `totalMs ≈ max(spaceMs, styleMs)` — the verification agent will assert `totalMs < spaceMs + styleMs - margin`). Partial-success (style-only failure) logs one WARN with `roomId` + style errorCode, and the success INFO still fires for space.

### React Native client — StyleSelectionScreen + polling

**FR-18 (New screen `StyleSelectionScreen.tsx`)** — Create `src/mobile/src/screens/StyleSelectionScreen.tsx`:
- Receives route param `{ roomId: string, aiDetectedStyle: Style | null, aiDetectedConfidence: number | null }` via the navigation stack (the previous screen — `AnalyzingScreen` — passes these when it observes `status='ANALYZED'`).
- Renders a list of six radio-button-like options: `CURRENT (현재 디자인 그대로)`, `MODERN (모던)`, `SIMPLE (심플)`, `CLASSIC (클래식)`, `SCANDINAVIAN (스칸디나비안)`, `INDUSTRIAL (인더스트리얼)`. Korean labels are the primary user-facing text per PRD wording; enum value is the identifier.
- When `aiDetectedStyle` is non-null, renders a suggestion badge on that row with text `"AI가 감지한 스타일: <StyleLabel> (<confidence%>)"` (e.g. `"AI가 감지한 스타일: Modern (72%)"`). Confidence is formatted as `round(aiDetectedConfidence * 100)`%.
- When `aiDetectedStyle` is null (style analysis failed), renders a subtle fallback note `"AI 스타일 감지 실패 — 직접 선택해주세요."` and no badge. The screen still works; `CURRENT` is pre-selected.
- On user tap on the "Next" / "다음" button: calls `PUT /api/v1/spaces/{roomId}/preferred-style` with the chosen enum value; on 200, navigates to the next screen (`RecommendationScreen` — not implemented in this task; use a placeholder route name `"Recommendation"`); on 4xx/5xx, shows an inline error toast and keeps the user on the screen.

**FR-19 (AnalyzingScreen polling)** — Modify the existing `src/mobile/src/screens/AnalyzingScreen.tsx` to:
- Poll `GET /api/v1/spaces/{roomId}` after upload completes.
- Use **exponential backoff**: first interval 1 s, multiplier 1.5, capped at 10 s per interval. Total polling budget 60 s wall-clock. Clamp per the task's technical-requirement note.
- On `status='ANALYZED'`: navigate to `StyleSelectionScreen` with `{roomId, aiDetectedStyle: response.style, aiDetectedConfidence: response.styleConfidence}`.
- On `status='FAILED'`: navigate to an error screen (reuse `PhotoUploadErrorScreen` from Task 2 or show inline `"분석 실패. 다시 시도해주세요."` — Design Agent's choice).
- On 60 s wall-clock timeout with still-`PENDING_ANALYSIS`: show an `"분석 시간 초과"` error UI with a retry button (which simply restarts the polling loop) and a "다시 업로드" button (navigates back to upload). Crucially, the timeout does NOT mark the row `FAILED` — the backend poller may still complete analysis later; this is purely a client-side UX timeout.
- The poll itself MUST be cancelled on screen unmount (React `useEffect` cleanup).

**FR-20 (Shared enum + type)** — Define the `Style` and `PreferredStyle` TypeScript types in `src/mobile/src/types/style.ts`:
```typescript
export type Style = 'MODERN' | 'SIMPLE' | 'CLASSIC' | 'SCANDINAVIAN' | 'INDUSTRIAL';
export type PreferredStyle = Style | 'CURRENT';
export const PREFERRED_STYLE_LABELS: Record<PreferredStyle, string> = {
  CURRENT: '현재 디자인 그대로',
  MODERN: '모던',
  SIMPLE: '심플',
  CLASSIC: '클래식',
  SCANDINAVIAN: '스칸디나비안',
  INDUSTRIAL: '인더스트리얼',
};
```
This is the single source of truth for the RN side; the Spring enum values must exactly match (case-sensitive `MODERN`, not `Modern`).

**FR-21 (API client — RN)** — Add two methods to the existing RN API client module (if not present, create `src/mobile/src/api/spaces.ts`):
- `getSpace(roomId: string): Promise<SpaceStateResponse>` — calls `GET /api/v1/spaces/{roomId}` with `X-User-Id` header sourced from the existing auth stub.
- `setPreferredStyle(roomId: string, preferredStyle: PreferredStyle): Promise<SpaceStateResponse>` — calls `PUT /api/v1/spaces/{roomId}/preferred-style`.

## 5. Data Contract

### Python `POST /analyze/style`
See FR-2 above for full success / error examples.

### Spring `GET /api/v1/spaces/{roomId}`
**Success — 200** — see FR-14 body.
**403** — X-User-Id mismatch.
**404** — unknown roomId.

### Spring `PUT /api/v1/spaces/{roomId}/preferred-style`
**Request**
```json
{ "preferredStyle": "MODERN" }
```
**Success — 200** — same shape as GET, with `preferredStyle` now populated.
**400** — unknown enum value (errorCode `INVALID_PREFERRED_STYLE`).
**409** — status != ANALYZED (errorCode `ANALYSIS_NOT_READY`).

### DB entities touched
- `spaces` — UPDATE only. New column `preferred_style` (VARCHAR(32) NULL) added by V3 migration.
  - Columns written by the orchestrator in this task: `style` (new — previously NULL), plus all columns Task 3 already wrote.
  - Columns written by `PUT .../preferred-style` in this task: `preferred_style`.
- **Schema change**: V3 migration adds `preferred_style`. Documented in FR-9.

## 6. Non-Functional Requirements

- **Parallelism (concretizes PRD §8)**: `AIOrchestrator.analyze` wall-clock latency MUST be `≤ max(t_space, t_style) + 150 ms` overhead (executor submit + join). Verification uses WireMock with `--fixedDelay=2000` on both stubs and asserts total < 3000 ms.
- **Python `POST /analyze/style` P95 latency**: ≤ 300 ms on a 1920×1080 JPEG with the placeholder classifier (it's hash-based — dominated by file I/O + sha256, not vision work).
- **Determinism (Python side)**: identical `photoUrl` → identical `style` + identical `scores` (except `processingMs`). The `scores` object MUST be deterministic to the 2nd decimal.
- **Concurrency safety (Python)**: `StyleClassifier` instances must not share mutable state across requests. The class is re-instantiated per request OR its `analyze` method is purely functional.
- **Partial-success resilience**: style-only failure never blocks the user from reaching `StyleSelectionScreen`. This is the UX contract that the `CURRENT` option + "AI 감지 실패" fallback depend on.
- **Transport-failure isolation (style)**: a style transport failure with a healthy space call leaves `spaces.status='ANALYZED'` and `spaces.style=NULL`. The user can still pick a preferred style and proceed. Out of scope: retry budget / backfill job to re-run style later.
- **Security**:
  - `/api/v1/spaces/{roomId}` and `/api/v1/spaces/{roomId}/preferred-style` require `X-User-Id` header and enforce `space.user_id == X-User-Id` (403 on mismatch).
  - `/analyze/style` inherits Task 3's path-escape hardening (`AI_PHOTO_ROOT` allow-list).
  - No new CORS surfaces beyond the two `/api/v1/spaces/*` additions; Task 3's `/internal/v1/*` is untouched.
- **Error envelope consistency**: both new public endpoints use the Task-2 `{errorCode, message, correlationId}` envelope (extended via the same `@ControllerAdvice`).
- **No real ML weights**: same runtime-dependency hygiene rule as Task 3 FR-5 / AC-2 applies — `torch`, `tensorflow`, `keras`, `transformers` MUST NOT appear in the runtime deps after this task (verified by re-running Task 3's AC-2 unchanged).
- **RN polling non-functional**:
  - Exponential backoff schedule: [1s, 1.5s, 2.25s, 3.38s, 5.06s, 7.59s, 10s, 10s, 10s, ...] capped at 10 s per interval.
  - Total 60 s wall-clock budget, measured from the first poll.
  - Battery-aware: polling stops on screen unmount AND on app background (React Native `AppState` listener).
  - Network failures during polling are retried silently until the 60 s budget elapses.
- **i18n**: User-visible strings in the RN screen are Korean (per PRD wording). Enum values and API strings stay in ASCII uppercase English.

## 7. Acceptance Criteria

**AC-1 (Python — `/analyze/style` route registered)** — *Given* the Python service started via uvicorn, *when* `GET /openapi.json` is fetched, *then* `paths` contains both `/analyze/space` and `/analyze/style`; the Task-3 `TODO(task-4)` comment referencing `/analyze/style` no longer appears in `src/ai/app/main.py`.

**AC-2 (Python — `/analyze/style` happy path)** — *Given* a valid JPEG and a running service, *when* `POST /analyze/style {roomId:"t2", photoUrl:"file://..."}` is called, *then* response is HTTP 200; body fields conform: `roomId=="t2"`, `status=="OK"`, `style ∈ {MODERN,SIMPLE,CLASSIC,SCANDINAVIAN,INDUSTRIAL}`, `confidence ∈ [0.0, 1.0]`, `scores` is an object with exactly five keys matching the enum, `sum(scores.values()) ∈ [0.99, 1.01]`, `argmax(scores) == style`, `scores[style] == confidence` (2 dp), `processingMs > 0`.

**AC-3 (Python — determinism)** — *Given* the same test JPEG, *when* `POST /analyze/style` is called twice with identical request bodies, *then* both responses have identical `style`, identical `confidence`, and identical `scores` (only `processingMs` may differ).

**AC-4 (Python — `CURRENT` never emitted)** — *Given* any valid image input across a sample of at least 50 hash-varied inputs (generated by fuzzing the bytes), *when* `POST /analyze/style` is called, *then* `style` is NEVER `"CURRENT"` — the five-value enum is a strict superset of possible outputs.

**AC-5 (Python — error envelopes identical to Task 3)** — *Given* a missing file, an unreadable file, and an invalid request body, *when* each is POSTed to `/analyze/style`, *then* response errorCodes are `IMAGE_NOT_FOUND`, `IMAGE_READ_FAILED`, `INVALID_REQUEST` with HTTP 422 — exactly matching Task 3 AC-6/AC-7/AC-8 behavior.

**AC-6 (Python — async handler)** — *Given* `src/ai/app/routes/style.py`, *when* reading it, *then* the handler is `async def` AND the `StyleClassifier.analyze(...)` invocation is wrapped in `asyncio.to_thread` / `run_in_threadpool` / `loop.run_in_executor`.

**AC-7 (Python — no new heavy ML deps)** — *Given* `src/ai/pyproject.toml` or `requirements.txt`, *when* re-running Task 3 AC-2, *then* it still passes (no `torch`, `tensorflow`, `keras`, `ultralytics` in runtime deps).

**AC-8 (Python — StyleClassifier has placeholder docstring)** — *Given* `src/ai/app/analyzers/style_classifier.py`, *when* grepping, *then* the file contains both `TODO(ml)` and a reference to a future real model (string match on "ResNet", "CLIP", "transformer", or "classifier" — Design Agent's choice, but the docstring must name at least one).

**AC-9 (V3 migration present)** — *Given* `src/backend/src/main/resources/db/migration/`, *when* listed, *then* a file matching `V3__add_preferred_style.sql` exists AND its content includes `ALTER TABLE spaces` + `ADD COLUMN preferred_style` + `VARCHAR(32)` + `NULL`.

**AC-10 (Spring — Style and PreferredStyle enums)** — *Given* the Spring source, *when* searching for `enum Style` and `enum PreferredStyle`, *then* (a) both exist; (b) `Style` has exactly `{MODERN, SIMPLE, CLASSIC, SCANDINAVIAN, INDUSTRIAL}`; (c) `PreferredStyle` has exactly those five plus `CURRENT`; (d) `CURRENT` is NOT in `Style`.

**AC-11 (Spring — StyleAnalysisClient shape)** — *Given* `StyleAnalysisClient.java`, *when* inspecting its public method, *then* method signature is `StyleAnalysisResponse callStyleAnalysis(String roomId, String photoUrl)` — exactly two params, same DTO-return pattern as `SpaceAnalysisClient` (Task 3 AC-24).

**AC-12 (Spring — parallel fan-out in AIOrchestrator)** — *Given* `AIOrchestrator.java`, *when* grepping, *then* the file contains ALL of: `CompletableFuture`, `supplyAsync`, `allOf`, and references to BOTH `spaceClient.callSpaceAnalysis` AND `styleClient.callStyleAnalysis`. No sequential `.join()` / `.get()` call appears between the two `supplyAsync` invocations.

**AC-13 (Integration — wall-clock parallelism)** — *Given* a Spring integration test with WireMock stubs for `/analyze/space` and `/analyze/style`, both returning a 200 after `fixedDelay=2000` ms, *when* `POST /internal/v1/spaces/{roomId}/analyze` is called, *then* the total response time is `< 3000 ms` (proving parallel — sequential would be ≥ 4000 ms).

**AC-14 (Integration — both succeed, DB fully written)** — *Given* a PENDING `spaces` row and both WireMock stubs returning success, *when* the orchestrator runs, *then* the row has `status='ANALYZED'`, non-null `dimensions`, `main_color`, `style` (matching one of the five enum values), and `analysis_date`; `preferred_style` is STILL NULL.

**AC-15 (Integration — style-only failure is degraded success)** — *Given* a PENDING row, the space stub returning 200, and the style stub returning `IMAGE_READ_FAILED` 422, *when* the orchestrator runs, *then* the row has `status='ANALYZED'`, non-null `dimensions`, `main_color`, `analysis_date`, but `style` IS NULL; the Spring response is HTTP 200 (NOT an error) and contains a log-observable WARN with the style errorCode.

**AC-16 (Integration — space-failure dominates outcome)** — *Given* a PENDING row, the space stub returning `IMAGE_READ_FAILED` 422, and the style stub returning 200, *when* the orchestrator runs, *then* the row has `status='FAILED'`; `style` is still NULL; the Spring response is HTTP 422 with `errorCode=='ANALYSIS_IMAGE_READ_FAILED'`. (The style success result is discarded — we do not persist style on a FAILED row.)

**AC-17 (Integration — space-transport-failure keeps status PENDING)** — *Given* a PENDING row, the space port closed, and the style stub returning 200, *when* the orchestrator runs, *then* the row has `status='PENDING_ANALYSIS'` (unchanged), `style` is NULL, `analysis_date` is NULL; Spring response is HTTP 502 with `errorCode=='AI_SERVICE_UNAVAILABLE'`.

**AC-18 (Space entity — preferredStyle field)** — *Given* `Space.java`, *when* inspecting its fields, *then* a `preferredStyle` field exists, annotated `@Enumerated(EnumType.STRING)`, mapped to column `preferred_style`, nullable=true, length=32 (or default for @Enumerated — acceptable as long as the column definition in V3 is VARCHAR(32)).

**AC-19 (Public GET endpoint — happy path)** — *Given* a `spaces` row for user `u1` with `roomId='r1'`, `status='ANALYZED'`, `style='MODERN'`, `preferred_style=NULL`, *when* `GET /api/v1/spaces/r1` is called with header `X-User-Id: u1`, *then* response is HTTP 200 with body where `roomId=='r1'`, `status=='ANALYZED'`, `style=='MODERN'`, `preferredStyle==null`, and the schema contains fields `dimensions`, `mainColor`, `analysisDate`, `uploadedAt`, `styleConfidence` (last may be null).

**AC-20 (Public GET — forbidden)** — *Given* the same row, *when* `GET /api/v1/spaces/r1` is called with `X-User-Id: u2`, *then* response is HTTP 403 with `errorCode` indicating a forbidden access (e.g. `FORBIDDEN` or `SPACE_ACCESS_DENIED`); no row data is leaked.

**AC-21 (Public GET — not found)** — *Given* no `spaces` row for `roomId='ghost'`, *when* `GET /api/v1/spaces/ghost` is called, *then* response is HTTP 404 with `errorCode=='SPACE_NOT_FOUND'`.

**AC-22 (Public PUT preferred-style — happy path)** — *Given* a `spaces` row for `u1`, `roomId='r1'`, `status='ANALYZED'`, *when* `PUT /api/v1/spaces/r1/preferred-style` is called with `X-User-Id: u1` and body `{"preferredStyle":"MODERN"}`, *then* response is HTTP 200; the row now has `preferred_style='MODERN'`; the response body echoes `preferredStyle:"MODERN"`.

**AC-23 (Public PUT — idempotent)** — *Given* the setup of AC-22 after the first PUT, *when* the same PUT is repeated with a different value `"SIMPLE"`, *then* response is HTTP 200 and the row now has `preferred_style='SIMPLE'`; a third PUT with `"SIMPLE"` returns 200 with no change.

**AC-24 (Public PUT — invalid enum)** — *Given* the setup of AC-22, *when* PUT is called with body `{"preferredStyle":"BAROQUE"}`, *then* response is HTTP 400 with `errorCode=='INVALID_PREFERRED_STYLE'`; the row is unchanged.

**AC-25 (Public PUT — status not ready)** — *Given* a `spaces` row with `status='PENDING_ANALYSIS'`, *when* PUT is called with a valid enum value, *then* response is HTTP 409 with `errorCode=='ANALYSIS_NOT_READY'`; the row's `preferred_style` is STILL NULL.

**AC-26 (Public PUT — `CURRENT` accepted)** — *Given* a `spaces` row with `status='ANALYZED'`, *when* PUT is called with `{"preferredStyle":"CURRENT"}`, *then* response is HTTP 200 and `preferred_style='CURRENT'` — confirming `CURRENT` is a valid user pick.

**AC-27 (RN — shared enum module)** — *Given* `src/mobile/src/types/style.ts`, *when* read, *then* it exports `Style` and `PreferredStyle` types matching the Spring enums exactly (five + six values respectively) AND exports `PREFERRED_STYLE_LABELS` with the six Korean labels specified in FR-20.

**AC-28 (RN — StyleSelectionScreen exists with correct options)** — *Given* `src/mobile/src/screens/StyleSelectionScreen.tsx`, *when* rendered in a test with `aiDetectedStyle="MODERN"` and `aiDetectedConfidence=0.72`, *then* the rendered output contains all six Korean labels from `PREFERRED_STYLE_LABELS` AND contains the substring `"AI가 감지한 스타일"` AND contains `"Modern"` or `"모던"` AND contains `"72%"`.

**AC-29 (RN — suggestion-badge fallback when style is null)** — *Given* the same screen rendered with `aiDetectedStyle=null`, *when* inspected, *then* the screen does NOT contain `"AI가 감지한 스타일"`; it DOES contain the fallback substring `"직접 선택"` (or similar copy per FR-18); the `CURRENT` option is pre-selected.

**AC-30 (RN — submit calls PUT)** — *Given* the screen rendered with any valid `aiDetectedStyle`, *when* the user selects `"SIMPLE"` and taps the Next button (simulated via a test utility), *then* the RN API client's `setPreferredStyle(roomId, "SIMPLE")` is invoked exactly once.

**AC-31 (RN — AnalyzingScreen polls with exponential backoff)** — *Given* a mocked `getSpace` that returns `status='PENDING_ANALYSIS'` five times then `status='ANALYZED'` on the sixth call, *when* `AnalyzingScreen` mounts, *then* (a) `getSpace` is called at least six times; (b) the intervals between consecutive calls (measured with a fake timer) are non-decreasing and each ≤ 10 000 ms; (c) after the sixth call, navigation to `StyleSelectionScreen` fires with route params containing `roomId` and `aiDetectedStyle`.

**AC-32 (RN — polling 60 s timeout)** — *Given* a mocked `getSpace` that always returns `status='PENDING_ANALYSIS'`, *when* fake time advances past 60 000 ms, *then* polling stops, and the UI shows a timeout error (substring `"시간 초과"` or an equivalent error component); no further `getSpace` calls occur after 60 s.

**AC-33 (RN — polling cancelled on unmount)** — *Given* the same mock as AC-32, *when* the screen unmounts after 5 s of polling, *then* no additional `getSpace` calls fire after unmount (verified by spy call count frozen).

**AC-34 (Spring — configuration surface extended)** — *Given* `src/backend/src/main/resources/application.yml`, *when* parsed, *then* keys `app.ai.style.base-url`, `app.ai.style.connect-timeout-ms`, `app.ai.style.read-timeout-ms`, `app.ai.orchestrator.executor.core-size`, `app.ai.orchestrator.executor.max-size`, `app.ai.orchestrator.executor.queue-capacity` are all present with the defaults specified in FR-16.

**AC-35 (OpenAPI contract artifact)** — *Given* `artifacts/UC-01-style-selection/api_contract.yaml`, *when* parsed as OpenAPI 3.0.x, *then* it defines: (a) Python `POST /analyze/style` with request/response schemas per FR-2; (b) Spring `GET /api/v1/spaces/{roomId}` with the `SpaceStateResponse` schema; (c) Spring `PUT /api/v1/spaces/{roomId}/preferred-style` with the request + response schemas; (d) all shared error envelopes; (e) all six `PreferredStyle` enum values in the relevant request-body schema.

**AC-36 (No regression on UC-01-space-analysis)** — *Given* the full verification suite, *when* re-running UC-01-space-analysis AC-1..AC-27, *then* all previously-passing ACs still pass — specifically AC-16 (orchestrator happy path now has BOTH space and style writes but still flips to `ANALYZED`), AC-17 (short-circuit still works), AC-18 (analyzer-failure → FAILED still works for space), AC-19 (space transport → PENDING still works), AC-26 (now intentionally breaks: V3 migration DOES exist — this is the ONE exception, and must be explicitly re-documented as overridden by this task).

**AC-37 (No regression on UC-01-photo-upload)** — *Given* the full verification suite, *when* re-running UC-01-photo-upload AC-1..AC-22, *then* all pass — this task makes no changes to the upload endpoint or error envelope.

## 8. Out of Scope
- **Real ML models** for style classification — placeholder remains deterministic hash-based. Replacing it is a future task (`ml-style-classifier-v1`).
- **Actual furniture recommendation** — Task 5 (UC-01-recommendation) consumes `spaces.style` and `spaces.preferred_style` written by this task.
- **Style confidence persisted in the DB** — only `spaces.style` (the argmax) is persisted. `styleConfidence` is at most an in-memory cache on the Spring side and may be null on GETs after process restart. Persisting it is a future task if analytics require it.
- **Backfill job** for rows where style analysis failed transiently — no retry path for style-only failures; user manually picks from the UI. Future task: a scheduled "style-reanalysis" poller.
- **AR / 3D preview of selected style** — Task 8.
- **Admin visibility** of per-user style preferences — Task 7.
- **Wishlist integration with style** — Task 6 may optionally filter wishlist by `preferred_style`, but that is scoped in Task 6, not here.
- **AuthN / AuthZ** beyond the existing `X-User-Id` header pattern — no JWT/OAuth introduced here; same stance as UC-01-photo-upload / UC-01-space-analysis.
- **Internationalization beyond Korean** — RN labels are hardcoded Korean strings; an i18n lib is a future task.
- **Rate limiting** on the new public endpoints — future infra task.
- **React Native state management library** (Redux / Zustand / etc.) — this task uses local React state + props-through-navigation. A global state refactor is a separate task.
- **Push notifications** for "analysis complete" — the RN client polls; a push-based flow is out of scope.

## 9. Dependencies
- **Hard**: `DB-schema-init` (Task 1), `UC-01-photo-upload` (Task 2), `UC-01-space-analysis` (Task 3). Specifically requires Task 3's `StyleClassifier` placeholder class, `AIOrchestrator`, `SpaceAnalysisClient`, and the `spaces.style` nullable column.
- **Consumed by**: `UC-01-recommendation` (Task 5) — will read `spaces.style` AND `spaces.preferred_style` written by this task to drive cross-validated furniture recommendations.
- **Consumed by**: `UC-02-wishlist` (Task 6) — may read `spaces.preferred_style` for style-aware wishlist filtering.
- **Consumed by**: `UC-03-admin-overview` (Task 7) — admin dashboard may surface style-preference analytics.
- **No dependency on**: Task 8 (AR).
