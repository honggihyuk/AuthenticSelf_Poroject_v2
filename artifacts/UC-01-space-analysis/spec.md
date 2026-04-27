# Task Spec: UC-01-space-analysis

## 1. Goal
Analyze a room photo that has already been uploaded (UC-01-photo-upload produced a `spaces` row in `PENDING_ANALYSIS`) and extract space data — dimensions, main color, and an analysis-confidence score — by invoking a dedicated **Python FastAPI** service from the Spring backend's new **AI Orchestrator**. On success, persist the results and flip the row to `ANALYZED`; on failure, flip to `FAILED` (surfacing the PRD §6 UC-01 alt-flow 1-a "재업로드 요청" case to the client). This task delivers the **`/analyze/space`** endpoint end-to-end and the Spring-side integration; the sibling `/analyze/style` endpoint is scoped here only as a sibling placeholder for Task 4.

## 2. Source (PRD section)
- **PRD §6 UC-01 기본 흐름 step 2** — "AI 시스템이 업로드된 사진을 분석하여 방 크기, 주요 컬러, 기존 가구 모양 등의 공간 데이터를 파악하고 추출."
- **PRD §6 UC-01 대안 흐름 1-a** — low quality / unparseable image → analysis failure → re-upload request (this task surfaces analysis-time failure as `status='FAILED'` + a structured error code; upload-time rejection is UC-01-photo-upload).
- **PRD §8 sequence diagram** — `par [AI 병렬 처리]` block: the AI Orchestrator fans out space analysis and style recognition in parallel. This spec must not make choices that block Task 4 from integrating via `CompletableFuture.allOf(...)` on the Spring side.
- **PRD §4 기술 스택** — Python, YOLO (ultralytics), MediaPipe, OpenCV, LayoutNet, FastAPI.
- **PRD §3 DB 설계** — `spaces` table columns `dimensions`, `main_color`, `style`, `analysis_date` are the targets this task fills (except `style`, which stays NULL — that is Task 4's output).
- **PRD §9 시스템 아키텍처** — `AI Orchestrator (Spring)` → `공간 분석 서버 (Python FastAPI)` + `스타일 & 추천 서버 (Python)`.

## 3. Actors & Preconditions
- **Primary actor**: the Spring backend's new `AIOrchestrator` component, triggered either (a) on demand via an internal REST endpoint `POST /internal/v1/spaces/{roomId}/analyze` or (b) by a `@Scheduled` poller that picks up `spaces.status='PENDING_ANALYSIS'` rows.
- **Secondary actor**: the Python FastAPI service at `src/ai/` exposing `POST /analyze/space` and `GET /health`.
- **Tertiary actor**: the React Native client — out of scope for this task; it will poll or be notified in a later task. This task exposes *no* new public REST endpoint (the internal endpoint is for operational/test use only; not CORS-exposed; not in `/api/v1/*`).
- **Preconditions**:
  - `DB-schema-init` (Task 1) and `UC-01-photo-upload` (Task 2) are merged. `spaces` table has `photo_url`, `status`, and the four nullable analysis columns (`dimensions`, `main_color`, `style`, `analysis_date`) — no schema change in this task.
  - At least one row exists in `spaces` with `status='PENDING_ANALYSIS'` and a readable `photo_url` of the form `file://<absolute-path>` (per UC-01-photo-upload FR-3).
  - Python 3.11+ available at build/run time; uvicorn able to bind a local port (default 8001).
  - The Spring process can reach the Python service over HTTP (default `http://localhost:8001`).

## 4. Functional Requirements

### Python AI service (new — lives under `src/ai/`)

**FR-1 (FastAPI service scaffold)** — Create a new Python package at `src/ai/` (sibling of `src/backend/` and `src/mobile/`) with:
- `src/ai/pyproject.toml` (or `requirements.txt` — choice left to Design Agent) declaring at minimum: `fastapi>=0.110`, `uvicorn[standard]>=0.27`, `pydantic>=2.5`, `numpy`, `opencv-python-headless`, `pillow`. **`ultralytics`, `mediapipe`, `torch`, and any LayoutNet-style weights MUST NOT be runtime dependencies in this task** — they are placeholders (see FR-5). If declared at all, they must be optional extras (e.g. `[project.optional-dependencies].ml = [...]`).
- `src/ai/app/main.py` — FastAPI `app` instance, registers `/health` and the `/analyze/space` router, configures Pydantic-v2-safe JSON response class.
- `src/ai/app/__init__.py` and a Python-module layout that lets `uvicorn app.main:app --reload` start the service from `src/ai/`.
- Service runs on port `8001` by default (override via env var `AI_SERVICE_PORT`).

**FR-2 (Health endpoint)** — `GET /health` returns HTTP 200 JSON `{ "status": "ok", "service": "space-analysis", "version": "<semver>" }`. No auth. No dependency on analyzers being warm.

**FR-3 (Endpoint contract — `POST /analyze/space`)** — Request body JSON:
```json
{ "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "photoUrl": "file:///C:/AuthenticSelf_Project/AuthenticSelf_v3/var/object-storage/spaces/2026/04/17/01HXYB2K9VQWM4P3ZT6N5C8DAE.jpg" }
```
Success response (HTTP 200):
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "status": "OK",
  "dimensions": { "widthM": 3.6, "lengthM": 4.2, "heightM": 2.4, "areaM2": 15.12 },
  "mainColor": "#E8D9B0",
  "confidence": 0.78,
  "processingMs": 142
}
```
Request validation (Pydantic v2):
- `roomId` non-empty string, 1–64 chars, `^[A-Za-z0-9_-]+$`.
- `photoUrl` non-empty string starting with `file://` (other schemes out of scope in this task).
Validation failures → HTTP 422 with body per FR-7.

**FR-4 (Analyzer interface — pluggable)** — Define a Python `Protocol` (or abstract base class) `SpaceAnalyzer` in `src/ai/app/analyzers/base.py`:
```python
class SpaceAnalyzer(Protocol):
    def analyze(self, image_path: Path) -> SpaceAnalysisResult: ...
```
where `SpaceAnalysisResult` is a Pydantic model with fields `dimensions: Dimensions`, `mainColor: str` (hex `^#[0-9A-Fa-f]{6}$`), `confidence: float` (0.0–1.0). The endpoint composes three concrete analyzers:
- `DimensionsEstimator` — **placeholder** (see FR-5) emulating LayoutNet.
- `ColorExtractor` — **real implementation** (see FR-6) using OpenCV k-means on a downscaled image.
- `StyleClassifier` — **placeholder** that returns a fixed confidence; this task does **not** use it at the endpoint (style output is NOT written to the response), but the class must exist so Task 4 can wire it into `/analyze/style` without refactoring.
The three analyzers must be injected via a lightweight DI surface (simple factory in `src/ai/app/analyzers/__init__.py` or FastAPI `Depends`) so a test can substitute a fake.

**FR-5 (Placeholder analyzers — deterministic)** — `DimensionsEstimator` and `StyleClassifier` MUST be placeholder implementations that:
- Do **NOT** import `ultralytics`, `mediapipe`, `torch`, `tensorflow`, or any model-weights file.
- Return **deterministic** output given the same input image path — so tests are reproducible. Determinism is achieved by hashing the image bytes (e.g. sha256 of the file) and mapping that into a bounded output space (e.g. width ∈ [2.5, 6.0] m, length ∈ [2.5, 6.0] m, height = 2.4 m fixed, confidence ∈ [0.6, 0.95]). The hash-to-value mapping must be documented in the analyzer's docstring.
- Each class must have a clear `TODO(ml)` docstring referencing "replace with real LayoutNet/YOLO inference in a future task" so the placeholder status is obvious.
- `areaM2` is always `round(widthM * lengthM, 2)`.

**FR-6 (ColorExtractor — real implementation)** — Use OpenCV + numpy:
- Load the image via `cv2.imread(str(image_path))` (or `cv2.imdecode` after reading bytes — choose to ensure Windows path safety).
- If load returns `None` → raise `ImageReadError`.
- Downscale so the longer edge ≤ 256 px (speed).
- Convert BGR→RGB.
- Run `cv2.kmeans` with `K=5`, `criteria=(EPS+MAX_ITER, 10, 1.0)`, `attempts=3`, `flags=KMEANS_PP_CENTERS`.
- Pick the cluster with the **largest pixel count** (by label histogram), convert its centroid to `#RRGGBB` uppercase hex. That is `mainColor`.
- `confidence` for the color component = (size of dominant cluster) / (total pixels), clamped to [0.0, 1.0], rounded to 2 dp. This value feeds the aggregate `confidence` calculation in FR-9.

**FR-7 (Error contract — Python side)** — Every non-2xx JSON body:
```json
{ "errorCode": "IMAGE_NOT_FOUND", "message": "…", "roomId": "…optional…" }
```
Error codes introduced:
| errorCode | HTTP | Trigger |
|---|---|---|
| `INVALID_REQUEST` | 422 | Pydantic validation failed (missing/blank `roomId` or `photoUrl`, bad URL scheme) |
| `IMAGE_NOT_FOUND` | 422 | `photoUrl` is well-formed but the resolved path does not exist on disk |
| `IMAGE_READ_FAILED` | 422 | File exists but `cv2.imread` returned `None` / Pillow cannot open it |
| `ANALYSIS_FAILED` | 422 | One of the analyzers threw an unexpected exception (caught, logged, translated) |
| `INTERNAL_ERROR` | 500 | Truly unexpected (outside the analyzer pipeline) |

**FR-8 (Async + thread pool)** — The endpoint handler MUST be declared `async def` and run each analyzer via `await asyncio.to_thread(analyzer.analyze, path)` (or an equivalent run-in-executor pattern). Rationale: OpenCV releases the GIL but is still CPU-bound; offloading to threads keeps the event loop responsive for the parallel calls Task 4 will make.

**FR-9 (Aggregate confidence)** — The top-level `confidence` returned in the response is `round((dim_conf + color_conf) / 2, 2)` where `dim_conf` is `DimensionsEstimator`'s confidence and `color_conf` is `ColorExtractor`'s cluster-dominance ratio from FR-6. The style classifier's confidence is **not** mixed in here (style is Task 4's response).

**FR-10 (Sibling-endpoint placeholder declaration)** — `src/ai/app/main.py` MUST include a `TODO(task-4)` comment stub (no route registered, no handler) documenting that `POST /analyze/style` will be added in UC-01-style-selection. The OpenAPI output from this task must NOT include `/analyze/style`. This FR is purely a discoverability anchor for Task 4.

### Spring backend (extends existing `src/backend/`)

**FR-11 (New package `com.authenticself.ai`)** — Create:
- `com.authenticself.ai.AIOrchestrator` — Spring `@Service`. Public API: `AnalysisOutcome analyze(String roomId)`. This is the entry point called by the internal REST controller (FR-13) and the scheduled poller (FR-14).
- `com.authenticself.ai.SpaceAnalysisClient` — typed HTTP client (Spring `RestClient` or `WebClient` — Design Agent's choice, but MUST NOT use raw `HttpURLConnection`). Single method `SpaceAnalysisResponse callSpaceAnalysis(String roomId, String photoUrl)`. Timeout: connect 2 s, read 10 s (configurable via `app.ai.space.read-timeout-ms`).
- `com.authenticself.ai.dto.SpaceAnalysisRequest` / `SpaceAnalysisResponse` / `AiErrorResponse` — Jackson-bound records.
- `com.authenticself.ai.AiServiceException` (+ subclasses `AiImageNotFoundException`, `AiImageReadFailedException`, `AiAnalysisFailedException`, `AiTransportException`) — unchecked; maps 1:1 to the Python error codes plus a transport/timeout bucket.

**FR-12 (AIOrchestrator flow)** — `AIOrchestrator.analyze(roomId)`:
1. Load `Space` by `roomId` via `SpaceRepository` — if missing, throw `SpaceNotFoundException` (HTTP 404 from FR-13).
2. If `space.status != PENDING_ANALYSIS` → short-circuit: return an outcome `{skipped:true, reason:"NOT_PENDING"}` without calling Python.
3. Call `SpaceAnalysisClient.callSpaceAnalysis(roomId, space.photoUrl)`.
4. On success response: update `space.dimensions = "<widthM>x<lengthM>x<heightM>m"` (string format — column is `VARCHAR(100)`; exact format: three decimals joined by `x`, unit suffix `m`, e.g. `"3.6x4.2x2.4m"`), `space.mainColor = response.mainColor`, `space.analysisDate = now()`, `space.status = ANALYZED`. Persist via `spaceRepository.save(space)`. **Do NOT write `space.style` — that is Task 4.**
5. On any `AiServiceException`: set `space.status = FAILED`, `space.analysisDate = now()`, persist, re-throw (the controller maps it).
6. The DB update MUST be inside a single `@Transactional` method; the HTTP call MUST be *outside* the transaction boundary (call first, then open a transaction to save) to avoid holding a DB connection for the network round-trip.

**FR-13 (Internal trigger endpoint)** — New controller `com.authenticself.ai.AIOrchestratorController`:
- `POST /internal/v1/spaces/{roomId}/analyze` (header `X-User-Id` **not** required — this is internal/ops). 
- Delegates to `AIOrchestrator.analyze(roomId)`.
- Success response HTTP 200 body `{ "roomId": "...", "status": "ANALYZED", "dimensions": "3.6x4.2x2.4m", "mainColor": "#E8D9B0", "processingMs": 142 }`.
- Short-circuit response (already analyzed) HTTP 200 body `{ "roomId": "...", "status": "<current>", "skipped": true, "reason": "NOT_PENDING" }`.
- Failure response: maps exception → errorCode per table below, HTTP status mirrors Python FR-7 (422 for analysis/image failures; 502 for transport failures when the Python service is unreachable; 500 for internal; 404 for unknown roomId).
- Error envelope reuses the Task-2 `{errorCode, message, correlationId}` shape (FR-6 of UC-01-photo-upload) by extending the same `@ControllerAdvice`.
- The path prefix `/internal/v1/*` MUST NOT be CORS-allowed and MUST NOT appear in the public OpenAPI contract.

**FR-14 (Scheduled poller — optional but on by default)** — `com.authenticself.ai.AIAnalysisPoller`:
- `@Scheduled(fixedDelayString = "${app.ai.poller.interval-ms:15000}")` — default 15 s, configurable.
- Gated by `app.ai.poller.enabled` (boolean, default `true` in `dev`, easily overridable to `false` in tests).
- Per tick: `spaceRepository.findTop10ByStatusOrderByUploadedAtAsc(Space.Status.PENDING_ANALYSIS)` → for each, call `AIOrchestrator.analyze(roomId)` inside a try/catch that logs and moves on (a single bad row must not stall the queue).
- Must be disabled in the integration-test profile (`@ActiveProfiles("test")`) so tests don't race the poller.

**FR-15 (Error-code mapping — Spring side)** —
| Python errorCode / condition | Spring exception | HTTP | Spring errorCode |
|---|---|---|---|
| `IMAGE_NOT_FOUND` (422) | `AiImageNotFoundException` | 422 | `ANALYSIS_IMAGE_NOT_FOUND` |
| `IMAGE_READ_FAILED` (422) | `AiImageReadFailedException` | 422 | `ANALYSIS_IMAGE_READ_FAILED` |
| `ANALYSIS_FAILED` (422) | `AiAnalysisFailedException` | 422 | `ANALYSIS_FAILED` |
| `INVALID_REQUEST` (422) | `AiAnalysisFailedException` | 422 | `ANALYSIS_FAILED` |
| connect/read timeout, ConnectException | `AiTransportException` | 502 | `AI_SERVICE_UNAVAILABLE` |
| Python `INTERNAL_ERROR` (500) | `AiTransportException` | 502 | `AI_SERVICE_UNAVAILABLE` |
| `SpaceNotFoundException` | — | 404 | `SPACE_NOT_FOUND` |

**FR-16 (Configuration surface — Spring)** — New keys in `application.yml`, all env-overridable:
- `app.ai.space.base-url` (default `http://localhost:8001`).
- `app.ai.space.connect-timeout-ms` (default `2000`).
- `app.ai.space.read-timeout-ms` (default `10000`).
- `app.ai.poller.enabled` (default `true`).
- `app.ai.poller.interval-ms` (default `15000`).

**FR-17 (Parallel-call readiness — forward NFR)** — `AIOrchestrator.analyze(roomId)` MUST be designed so that a future Task-4 change can call space + style analysis in parallel via `CompletableFuture.allOf(spaceFuture, styleFuture).join()` (PRD §8 `par` block). Concretely: `SpaceAnalysisClient.callSpaceAnalysis(...)` must be a pure HTTP call with no hidden side effects (no DB writes, no static state), and must accept `roomId` + `photoUrl` only (no implicit thread-local). This task does NOT implement the parallel fan-out itself — it just guarantees the shape is compatible. The Design Agent should note this as a forward requirement.

**FR-18 (Observability)** — Each `AIOrchestrator.analyze` invocation logs one INFO line at start (roomId, photoUrl path-only), one INFO on success (roomId, elapsedMs, dimensions, mainColor, confidence), one WARN on analyzer failure (roomId, errorCode), one ERROR on transport failure (roomId, remote URL, exception class). No image bytes in logs. Python side: one INFO log per request with `roomId` + duration; one WARN per 4xx; one ERROR per 5xx.

## 5. Data Contract

### Python `POST /analyze/space`
**Request**
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "photoUrl": "file:///C:/.../01HXYB2K9VQWM4P3ZT6N5C8DAE.jpg"
}
```
**Response — 200**
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "status": "OK",
  "dimensions": { "widthM": 3.6, "lengthM": 4.2, "heightM": 2.4, "areaM2": 15.12 },
  "mainColor": "#E8D9B0",
  "confidence": 0.78,
  "processingMs": 142
}
```
**Response — 422 (example)**
```json
{ "errorCode": "IMAGE_NOT_FOUND",
  "message": "Resolved path does not exist: /.../missing.jpg",
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE" }
```

### Spring `POST /internal/v1/spaces/{roomId}/analyze`
**Success — 200**
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "status": "ANALYZED",
  "dimensions": "3.6x4.2x2.4m",
  "mainColor": "#E8D9B0",
  "processingMs": 142
}
```
**Short-circuit — 200**
```json
{ "roomId": "...", "status": "ANALYZED", "skipped": true, "reason": "NOT_PENDING" }
```
**Failure — 422/502/404/500** — reuses the Task-2 `{ errorCode, message, correlationId }` envelope.

### DB entities touched
- `spaces` — UPDATE only. Columns written by this task: `dimensions`, `main_color`, `analysis_date`, `status` (→ `ANALYZED` or `FAILED`). Columns NOT written: `style` (Task 4), `user_id`, `photo_url`, `original_filename`, `content_type`, `file_size_bytes`, `uploaded_at`, `room_id`.
- **No schema change.** V2 already provided nullability + status ENUM + `idx_spaces_status`.

## 6. Non-Functional Requirements

- **Performance**: Python `/analyze/space` P95 latency on a 1920×1080 JPEG ≤ 800 ms on a local dev machine with placeholder analyzers. `processingMs` in the response must be a real wall-clock measurement, not a constant.
- **Determinism**: identical `photoUrl` → identical response body (except `processingMs`). Required for the verification agent's reproducibility checks.
- **Concurrency**: endpoint handler safe under concurrent requests — analyzers must not share mutable state across requests (re-instantiate per request, or guarantee thread-safety). OpenCV k-means is called in `asyncio.to_thread`, so GIL contention is acceptable.
- **Network isolation**: the Python service has **no** outbound network calls. It reads only from the local filesystem (the `file://` URL). Verification agent MUST be able to run the service offline.
- **Security**:
  - `photoUrl` MUST be parsed and path-normalized. If the resolved path escapes a configured allow-list root (default: same as the backend's `app.storage.local.root`, passed via env `AI_PHOTO_ROOT`), the service returns `IMAGE_NOT_FOUND` (not `INTERNAL_ERROR` — the client must not distinguish "outside root" from "not present"). This prevents a compromised Spring process from asking the AI service to read `/etc/passwd`.
  - No logging of full absolute paths at INFO; only filename + size.
  - `/internal/v1/*` on the Spring side is not CORS-enabled, not in public OpenAPI, and MUST be blocked at the ingress/reverse-proxy in production (out-of-scope enforcement, in-scope documentation).
- **Error handling**: every non-2xx response on both sides is the structured envelope; uncaught exceptions on the Python side go through a single FastAPI `exception_handler` that returns `INTERNAL_ERROR` + HTTP 500; on the Spring side a single `@ControllerAdvice` handles `AiServiceException` subclasses.
- **Transport failure isolation**: if the Python service is down, the Spring side must degrade cleanly: HTTP 502 + `AI_SERVICE_UNAVAILABLE` within the configured read-timeout (default 10 s). The DB row MUST NOT be flipped to `FAILED` for transient transport errors — only for analyzer-level failures. Transport errors leave `status='PENDING_ANALYSIS'` so the poller retries next tick. (Trade-off accepted: a permanently-unreachable Python service produces infinite retries. A retry budget is a future task.)
- **Idempotency**: `POST /analyze/space` is pure (no side effects) — safe to retry. `POST /internal/v1/spaces/{roomId}/analyze` is idempotent via the FR-12 step 2 short-circuit: re-calling on an `ANALYZED` row returns 200 with `skipped:true` and does not re-run analysis or mutate DB.
- **No real ML weights in this task**: verification agent will assert that `ultralytics`, `mediapipe`, `torch`, `tensorflow` are absent from the *runtime* `requirements.txt` / `pyproject.toml` `[project.dependencies]`. Optional extras are allowed.
- **i18n**: Python error `message` strings may be English (internal surface); the Spring controller translates to Korean-safe user messages if needed, but this task only requires English internal messages and any-language Spring messages (Task 4 will unify).

## 7. Acceptance Criteria

**AC-1 (Python package layout)** — *Given* the repo, *when* listing `src/ai/`, *then* the directory exists with at least `app/main.py`, `app/analyzers/base.py`, and either `pyproject.toml` or `requirements.txt`.

**AC-2 (Runtime dependency hygiene)** — *Given* `src/ai/pyproject.toml` or `src/ai/requirements.txt`, *when* scanning its runtime dependency section, *then* NONE of `ultralytics`, `mediapipe`, `torch`, `tensorflow`, or `layoutnet` appear; `fastapi`, `uvicorn`, `pydantic` (>=2), `numpy`, `opencv-python-headless`, and `pillow` all DO appear.

**AC-3 (Health endpoint)** — *Given* the Python service started via `uvicorn app.main:app --port 8001`, *when* `GET /health` is called, *then* response is HTTP 200 and JSON body matches schema `{status:"ok", service:"space-analysis", version:<non-empty string>}`.

**AC-4 (Analyze — happy path, real image)** — *Given* a real 1920×1080 JPEG saved under the AI photo-root and the service is up, *when* `POST /analyze/space` is called with `{roomId:"test-ac4", photoUrl:"file://<absolute>"}`, *then* response is HTTP 200; body fields are all present and conform: `roomId=="test-ac4"`, `status=="OK"`, `dimensions.widthM` in [2.5, 6.0], `dimensions.lengthM` in [2.5, 6.0], `dimensions.heightM==2.4`, `dimensions.areaM2 == round(widthM*lengthM, 2)`, `mainColor` matches `^#[0-9A-F]{6}$`, `confidence` in [0.0, 1.0], `processingMs > 0`.

**AC-5 (Analyze — determinism)** — *Given* the same test JPEG, *when* `POST /analyze/space` is called twice with identical request bodies, *then* both responses have identical `dimensions`, `mainColor`, and `confidence` (only `processingMs` may differ).

**AC-6 (Analyze — IMAGE_NOT_FOUND)** — *Given* a `photoUrl` pointing to a path that does not exist, *when* POSTed, *then* response is HTTP 422 with JSON body `errorCode=="IMAGE_NOT_FOUND"` and `roomId` echoed.

**AC-7 (Analyze — IMAGE_READ_FAILED)** — *Given* a file on disk whose bytes are NOT a valid image (e.g., a text file renamed to `.jpg`), *when* POSTed, *then* response is HTTP 422 with JSON body `errorCode=="IMAGE_READ_FAILED"`.

**AC-8 (Analyze — INVALID_REQUEST)** — *Given* a request body missing `roomId` OR whose `photoUrl` does not start with `file://`, *when* POSTed, *then* response is HTTP 422 and `errorCode=="INVALID_REQUEST"`.

**AC-9 (Analyze — path-escape hardening)** — *Given* the env var `AI_PHOTO_ROOT` points to `<root>`, *when* `photoUrl` resolves to a path outside that root (e.g. via `..` traversal), *then* response is HTTP 422 with `errorCode=="IMAGE_NOT_FOUND"` (not `INTERNAL_ERROR`) and the file is NOT read.

**AC-10 (Async handler)** — *Given* the source file registering `POST /analyze/space`, *when* scanning for the handler definition, *then* it is declared with `async def` AND the analyzer invocation uses either `asyncio.to_thread`, `loop.run_in_executor`, or `starlette.concurrency.run_in_threadpool`.

**AC-11 (SpaceAnalyzer protocol + three analyzers)** — *Given* `src/ai/app/analyzers/`, *when* listed, *then* (a) `base.py` defines `SpaceAnalyzer` as a `Protocol` or ABC with an `analyze(image_path)` method; (b) concrete classes `DimensionsEstimator`, `ColorExtractor`, `StyleClassifier` each exist in their own module; (c) each has a docstring naming it "placeholder" (for Dimensions/Style) or "real" (for Color).

**AC-12 (ColorExtractor uses OpenCV k-means)** — *Given* `src/ai/app/analyzers/color_extractor.py`, *when* grepping, *then* the file imports `cv2` AND calls `cv2.kmeans(` AND returns a hex color matching `^#[0-9A-F]{6}$`.

**AC-13 (Sibling stub discoverability)** — *Given* `src/ai/app/main.py`, *when* grepping for `TODO(task-4)`, *then* at least one match exists AND the string `/analyze/style` appears in a comment.

**AC-14 (No `/analyze/style` in OpenAPI)** — *Given* the Python service up, *when* `GET /openapi.json` is fetched, *then* the `paths` object contains `/analyze/space` and `/health` but NOT `/analyze/style`.

**AC-15 (Spring AI package exists)** — *Given* `src/backend/src/main/java/com/authenticself/ai/`, *when* listed, *then* files `AIOrchestrator.java`, `SpaceAnalysisClient.java`, `AIOrchestratorController.java`, and a DTO folder (`dto/`) with `SpaceAnalysisRequest.java` and `SpaceAnalysisResponse.java` all exist.

**AC-16 (AIOrchestrator happy path — integration)** — *Given* (a) a `spaces` row with `roomId='r1'`, `status='PENDING_ANALYSIS'`, and a real photo on disk at `photo_url`, and (b) the Python service running (or mocked via WireMock returning the AC-4 success body), *when* `POST /internal/v1/spaces/r1/analyze` is called, *then* response is HTTP 200 with `status=="ANALYZED"`; the `spaces` row now has `status='ANALYZED'`, non-null `dimensions` matching `^\d+(\.\d+)?x\d+(\.\d+)?x\d+(\.\d+)?m$`, non-null `main_color` matching `^#[0-9A-F]{6}$`, and non-null `analysis_date`; `style` is still NULL.

**AC-17 (Short-circuit on non-PENDING)** — *Given* a `spaces` row with `status='ANALYZED'`, *when* `POST /internal/v1/spaces/{roomId}/analyze` is called, *then* response is HTTP 200 with `skipped==true` and `reason=="NOT_PENDING"`; the Python service MUST NOT have been called (verify via WireMock request count == 0); the DB row is unchanged.

**AC-18 (Analyzer failure → status=FAILED)** — *Given* a PENDING row and a mocked Python service returning the AC-7 `IMAGE_READ_FAILED` body, *when* the orchestrator runs, *then* Spring response is HTTP 422 with `errorCode=="ANALYSIS_IMAGE_READ_FAILED"`; the `spaces` row now has `status='FAILED'` and a non-null `analysis_date`; `dimensions` and `main_color` remain NULL.

**AC-19 (Transport failure → status stays PENDING)** — *Given* a PENDING row and a Python service that is unreachable (no WireMock stub, port closed), *when* the orchestrator runs, *then* Spring response is HTTP 502 with `errorCode=="AI_SERVICE_UNAVAILABLE"`; the `spaces` row status is STILL `PENDING_ANALYSIS`; `analysis_date` is still NULL.

**AC-20 (SpaceNotFound)** — *Given* no `spaces` row with `room_id='nope'`, *when* `POST /internal/v1/spaces/nope/analyze` is called, *then* response is HTTP 404 with `errorCode=="SPACE_NOT_FOUND"`; no HTTP call is made to the Python service.

**AC-21 (Spring configuration surface)** — *Given* `src/backend/src/main/resources/application.yml`, *when* parsed, *then* keys `app.ai.space.base-url`, `app.ai.space.connect-timeout-ms`, `app.ai.space.read-timeout-ms`, `app.ai.poller.enabled`, `app.ai.poller.interval-ms` are all present with defaults `http://localhost:8001`, `2000`, `10000`, `true`, `15000` respectively.

**AC-22 (Poller disabled in test profile)** — *Given* the Spring test profile, *when* the context starts, *then* either `app.ai.poller.enabled=false` is set in the test configuration OR `AIAnalysisPoller` is annotated `@ConditionalOnProperty(name="app.ai.poller.enabled", havingValue="true")` — verifiable by inspecting the source.

**AC-23 (No raw HttpURLConnection / no leaked Python libs)** — *Given* the backend source tree, *when* grepping `src/backend/src/main/java/**/*.java`, *then* (a) zero matches for `java.net.HttpURLConnection` outside test sources; (b) zero matches for `ultralytics`, `mediapipe`, `torch` (i.e., Python libs must not leak into a Java string literal by accident).

**AC-24 (Task-4 readiness — signature)** — *Given* `SpaceAnalysisClient.java`, *when* inspecting its public method(s), *then* `callSpaceAnalysis(String roomId, String photoUrl)` takes exactly those two parameters and returns a DTO (no Spring-web-specific types like `ResponseEntity` in the signature) — so a future `styleClient.callStyleAnalysis(...)` can be invoked in parallel via `CompletableFuture.supplyAsync`.

**AC-25 (OpenAPI contract artifact)** — *Given* the artifact `artifacts/UC-01-space-analysis/api_contract.yaml`, *when* parsed as OpenAPI 3.0.x, *then* it defines both (a) the Python `POST /analyze/space` with request schema `{roomId, photoUrl}` and success schema `{roomId, status, dimensions{widthM,lengthM,heightM,areaM2}, mainColor, confidence, processingMs}`, and (b) the Spring `POST /internal/v1/spaces/{roomId}/analyze`; both error envelopes are defined as shared schemas; responses include 200, 422, 502 (Spring), 404 (Spring), and 500.

**AC-26 (No schema change)** — *Given* the repo at this task's merge, *when* listing `src/backend/src/main/resources/db/migration/`, *then* no `V3__*.sql` file was added by this task (V3 is reserved for a later task).

**AC-27 (No regression on UC-01-photo-upload)** — *Given* the full verification suite, *when* re-running UC-01-photo-upload's AC-1..AC-22, *then* all previously-passing ACs still pass (no behavior change to the upload endpoint, error envelope, or object-storage abstraction).

## 8. Out of Scope
- **Style recognition endpoint (`POST /analyze/style`)** — UC-01-style-selection (Task 4). Only a placeholder class and a `TODO(task-4)` comment are in scope here.
- **Cross-validation between space analysis + style** — Task 5 (UC-01-recommendation).
- **Real ML model weights, GPU inference, ultralytics/YOLO/LayoutNet/MediaPipe runtime** — intentional: placeholders with deterministic outputs. Installing real weights is a future task.
- **Parallel fan-out of space + style from AIOrchestrator** — Task 4 will extend `AIOrchestrator` to use `CompletableFuture.allOf(...)` once `/analyze/style` exists. This task only guarantees shape compatibility (FR-17).
- **Public / user-facing endpoint for analysis polling** — no `/api/v1/spaces/{roomId}/analysis` exposed to the RN client; the client's UX for "analyzing…" is Task 4/5.
- **Retry budget, circuit breaker, back-pressure** — only basic timeout + single-attempt per poller tick is in scope.
- **Schema changes** — V2 already provides everything we need. No V3 migration this task.
- **Authentication / authorization** on `/internal/v1/*` — internal endpoint; production must block at ingress, but this task doesn't implement AuthN/Z (same stance as UC-01-photo-upload).
- **Admin dashboard, wishlist, AR** — Tasks 6–8.
- **Virus scanning / NSFW** — not in PRD.
- **Message queue / async job system (Kafka, SQS, etc.)** — the poller pattern is sufficient for this task; a queue is a future infra task.
- **Observability backends (Prometheus, OpenTelemetry exporters)** — only JVM/stdout logging is in scope; exporters are a future ops task.

## 9. Dependencies
- **Hard**: `DB-schema-init` (Task 1) and `UC-01-photo-upload` (Task 2). Requires the V2 schema (nullable analysis columns, `status` ENUM, `idx_spaces_status`) and the `Space` JPA entity.
- **Consumed by**: `UC-01-style-selection` (Task 4) — will add a sibling `POST /analyze/style` in the same Python service and extend `AIOrchestrator` to call both in parallel; depends on FR-10, FR-17, and AC-24 of this task.
- **Consumed by**: `UC-01-recommendation` (Task 5) — will read `spaces.dimensions` and `spaces.main_color` written by this task.
- **No dependency on**: Tasks 6–8.
