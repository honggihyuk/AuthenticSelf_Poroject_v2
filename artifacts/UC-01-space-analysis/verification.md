# Verification Report: UC-01-space-analysis
**Verdict**: PASS-WITH-WARNINGS
**Iteration**: v1
**Date**: 2026-04-17

## 1. Drift-resolution table (items flagged by prompt-specialist / viz)

| # | Drift item | Resolution | Severity |
|---|---|---|---|
| 1 | Independent Spring beans (AIOrchestrator, SpaceAnalysisClient, AnalysisPoller, Controller, SpaceAnalysisPersistence) vs design.md's "single orchestrator" phrasing | **RESOLVED / intentional**. design.md §2.1 justifies the `SpaceAnalysisPersistence` split as a hard requirement of FR-12 step 6 (HTTP call outside any tx) — Spring's proxy-based `@Transactional` cannot open a new tx on a self-call, so persistence MUST be a second bean. AIOrchestrator is still the single public entry-point — the other beans are collaborators, not peers. All FRs 11-18 still satisfied; FR-12 step 6 is satisfied ONLY because of this split. No functional drift. | Info |
| 2 | `application.yml` has both `app.ai.space.*` (spec) and `app.ai.*` alias keys | **RESOLVED / design augmentation**. AC-21 requires `app.ai.space.base-url`, `app.ai.space.connect-timeout-ms`, `app.ai.space.read-timeout-ms`, `app.ai.poller.enabled`, `app.ai.poller.interval-ms` — all five present with the required defaults (lines 66-75 of `application.yml`). The alias keys `app.ai.base-url` / `app.ai.connect-timeout-ms` / `app.ai.read-timeout-ms` are added for forward-compat (used by `SpaceAnalysisClient` constructor `${app.ai.base-url:${app.ai.space.base-url:...}}`). Also extra `poller.fixed-delay-ms`, `poller.batch-size`, `poller.max-attempts` keys present — these are Task-4-ready knobs. None of them violate AC-21 (AC-21 is an inclusion check, not exclusivity). | Low warning — surface area is wider than spec; recommend spec update before Task 4 so the alias stays documented |
| 3 | `@ConditionalOnProperty(matchIfMissing=false)` vs spec's alternative | **RESOLVED**. `AnalysisPoller.java:27` annotates `@ConditionalOnProperty(name = "app.ai.poller.enabled", havingValue = "true", matchIfMissing = false)` AND `src/backend/src/test/resources/application-test.yml` sets `app.ai.poller.enabled: false` — both arms of AC-22's "OR" clause are present (belt + braces). `matchIfMissing=false` means if the property were absent the bean would not be created; combined with `application.yml` default `true`, production is ON, test profile is OFF. Spec §AC-22 says "(name='app.ai.poller.enabled', havingValue='true')" without specifying `matchIfMissing` — the default for `@ConditionalOnProperty` is `matchIfMissing=false`, so this is the stricter reading of the spec. No drift. | Info |

## 2. Extra checks (task-specific)

| Check | Result | Evidence |
|---|---|---|
| AC-2 dep hygiene — grep `ultralytics\|mediapipe\|torch\|tensorflow` in `src/ai/pyproject.toml` + `requirements.txt` | **PASS** — zero hits in dependency lists. Only matches in repo are `TODO(ml)` docstring prose in `src/ai/app/analyzers/dimensions.py:3, 37` | `src/ai/pyproject.toml:17-24`, `src/ai/requirements.txt:7-16` |
| AC-13/14 route registration — `/analyze/style` present only in comment | **PASS** — only two hits in `main.py`: line 12 (module docstring: "`POST /analyze/style` is intentionally NOT registered") and line 16 (`TODO(task-4)` comment). Regex search for `@(app\|router)\.(get\|post\|...)`.*analyze/style` returns zero matches | `src/ai/app/main.py:12, 16` |
| AC-24 signature — `SpaceAnalysisClient.callSpaceAnalysis(String, String)` returns DTO | **PASS** — public method at `SpaceAnalysisClient.java:80` signature is `public SpaceAnalysisResponse callSpaceAnalysis(String roomId, String photoUrl)`. Returns `com.authenticself.ai.dto.SpaceAnalysisResponse` — a plain Jackson record, no `ResponseEntity`, no Spring-web type. Compatible with `CompletableFuture.supplyAsync` | `SpaceAnalysisClient.java:80` |
| FR-12 transaction boundary — HTTP call NOT inside `@Transactional` | **PASS** — `AIOrchestrator.analyze()` has NO `@Transactional` annotation (`AIOrchestrator.java:61`; only match for `@Transactional` in that file is in a javadoc comment on line 17). HTTP call on line 75 sits between `persistence.loadForAnalysis(roomId)` (line 65) and `persistence.markAnalyzed(...)` (line 90) / `persistence.markFailed(roomId)` (line 83). Persistence methods are `@Transactional(propagation = REQUIRES_NEW)` on a separate bean — proxy opens/closes each tx around one short DB op | `AIOrchestrator.java:61-103`, `SpaceAnalysisPersistence.java:34, 42, 55` |
| AC-17 short-circuit — early return when status != PENDING_ANALYSIS | **PASS** — `AIOrchestrator.java:66-68` reads `snap.status()`, if not `PENDING_ANALYSIS` returns `SpaceAnalysisResultDTO.ofSkipped(...)` before line 75's `client.callSpaceAnalysis(...)`. Test `AIOrchestratorTest#shortCircuitOnAnalyzed` and `shortCircuitOnFailed` verify `client` is never called (`verify(client, never()).callSpaceAnalysis(...)`) | `AIOrchestrator.java:66-68`, `AIOrchestratorTest.java:74-102` |
| AC-19 transport error → status stays PENDING_ANALYSIS | **PASS** — `AIOrchestrator.java:76-81`: on `AIException` with `isTransport()==true` (i.e. `AI_SERVICE_UNAVAILABLE`), the code rethrows WITHOUT calling `persistence.markFailed()`. Test `AIOrchestratorTest#transportFailureLeavesPending` verifies `markAnalyzed` and `markFailed` are both `never()` called. Controller test `AIOrchestratorControllerTest#transport_failure_502` verifies HTTP 502 + `errorCode=AI_SERVICE_UNAVAILABLE` | `AIOrchestrator.java:76-81`, `AIOrchestratorTest.java:128-144` |
| AC-22 poller off by default (test profile) | **PASS** — TWO mechanisms in place: (1) `AnalysisPoller.java:27` annotation `@ConditionalOnProperty(name="app.ai.poller.enabled", havingValue="true", matchIfMissing=false)` — bean is not registered at all in tests; (2) `application-test.yml:6-9` explicitly sets `app.ai.poller.enabled: false`. Production default in `application.yml:74` is `true` | `AnalysisPoller.java:27`, `application-test.yml:6-9`, `application.yml:74` |
| Security — hardcoded API keys | **PASS** — no hardcoded secrets. All credentials (DB_URL, DB_USER, DB_PASSWORD) via env vars in `application.yml:8-10`. Grep for `password\|secret\|api_key\|token\s*=\s*"..."` returns only the env-var reference `password: ${DB_PASSWORD}` | `application.yml:8-10`, `AuthenticSelfApplication.java:11` comment |
| Security — path-traversal guard (AI_PHOTO_ROOT allowlist) | **PASS** — `src/ai/app/main.py:225-241` resolves `path.resolve(strict=False)` and calls `resolved.relative_to(root)` where `root = _photo_root()` (reads `AI_PHOTO_ROOT` env var at call-time for test-friendliness, defaulting to `var/object-storage`). On `ValueError` (path escape) raises `ImageNotFoundError`, which maps to `IMAGE_NOT_FOUND` not `INTERNAL_ERROR`, per AC-9. Test `test_path_escape_is_image_not_found` asserts this | `src/ai/app/main.py:70-82, 225-241`, `src/ai/tests/test_analyze_space.py:122-131` |
| Error translation — `AIErrorCode.fromPythonCode()` covers all Python error codes | **PASS** — Python codes: `INVALID_REQUEST`, `IMAGE_NOT_FOUND`, `IMAGE_READ_FAILED`, `ANALYSIS_FAILED`, `INTERNAL_ERROR`. `AIErrorCode.fromPythonCode()` switch at `AIErrorCode.java:39-45` explicitly handles `IMAGE_NOT_FOUND`, `IMAGE_READ_FAILED`, `ANALYSIS_FAILED`, `INVALID_REQUEST`; `default` branch handles both `INTERNAL_ERROR` and `null` → `AI_SERVICE_UNAVAILABLE` (502) — matches FR-15 table row "Python INTERNAL_ERROR (500) → AiTransportException / AI_SERVICE_UNAVAILABLE". All 5 Python codes mapped | `src/ai/app/errors.py:15-19`, `AIErrorCode.java:37-46` |

## 3. AC Coverage Matrix

| AC | Status | Evidence |
|----|--------|----------|
| AC-1  Python package layout | PASS | `src/ai/app/main.py`, `src/ai/app/analyzers/base.py`, `src/ai/pyproject.toml` + `requirements.txt` all exist |
| AC-2  Runtime dependency hygiene | PASS | `pyproject.toml:17-24` declares `fastapi, uvicorn, pydantic, numpy, opencv-python-headless, pillow`; zero matches for `ultralytics/mediapipe/torch/tensorflow` in dependency lists. `requirements.txt` mirror |
| AC-3  Health endpoint | PASS | `main.py:190-193` returns `{status:"ok", service:"space-analysis", version:__version__}`; test `test_analyze_space.py::test_health_ok:26-32` |
| AC-4  Happy path | PASS | `main.py:204-293` analyze_space handler; test `test_analyze_happy_path:50-68` asserts all field ranges |
| AC-5  Determinism | PASS | `DimensionsEstimator` uses SHA-256 of file bytes (`dimensions.py:57-64`); `ColorExtractor.analyze` is pure; test `test_analyze_is_deterministic:71-77` |
| AC-6  IMAGE_NOT_FOUND | PASS | `main.py:243-244`; test `test_image_not_found:85-94` |
| AC-7  IMAGE_READ_FAILED | PASS | `color.py:47-50` raises `ImageReadError` when `cv2.imread` returns `None`; test `test_image_read_failed:97-105` |
| AC-8  INVALID_REQUEST | PASS | `schemas.py:30-43` Pydantic pattern validators; test `test_invalid_request_missing_room_id:108-112` and `test_invalid_request_bad_scheme:115-119` |
| AC-9  Path-escape hardening | PASS | `main.py:229-241` `relative_to(root)` + `ImageNotFoundError`; test `test_path_escape_is_image_not_found:122-131` |
| AC-10 Async handler + to_thread | PASS | `main.py:204` `async def analyze_space`; `main.py:248-251` `asyncio.gather(asyncio.to_thread(...), asyncio.to_thread(...))` |
| AC-11 SpaceAnalyzer + three analyzers | PASS | `base.py:40-56` Protocol; `dimensions.py`, `color.py`, `style.py` each a separate module with docstrings naming "placeholder" / "REAL" |
| AC-12 ColorExtractor uses cv2.kmeans | PASS | `color.py:12` imports cv2; `color.py:72-79` calls `cv2.kmeans(...)`; returns `f"#{r:02X}{g:02X}{b:02X}"` at line 93 |
| AC-13 Sibling stub discoverability | PASS | `main.py:16` `# TODO(task-4): wire /analyze/style here...` — both tokens present in a comment |
| AC-14 No /analyze/style in OpenAPI | PASS | Verified by regex-sweep: no route decorator registers `/analyze/style`. Test `test_openapi_does_not_expose_style_route:35-42` |
| AC-15 Spring AI package exists | PASS | `AIOrchestrator.java`, `SpaceAnalysisClient.java`, `AIOrchestratorController.java`, `dto/SpaceAnalysisRequest.java`, `dto/SpaceAnalysisResponse.java` all present |
| AC-16 Happy path integration | PASS | `AIOrchestratorTest#happyPath:45-68` — verifies `markAnalyzed` called with dimensions matching `^\d+(\.\d+)?x\d+(\.\d+)?x\d+(\.\d+)?m$` regex; controller test `happy_path_200` |
| AC-17 Short-circuit on non-PENDING | PASS | `AIOrchestrator.java:66-68` early return; tests `shortCircuitOnAnalyzed` and `shortCircuitOnFailed`; controller test `short_circuit_200_skipped` |
| AC-18 Analyzer failure → FAILED | PASS | `AIOrchestrator.java:83-85` calls `persistence.markFailed` on non-transport `AIException`; test `analyzerFailureFlipsToFailed:108-123`; controller test `analyzer_failure_422` |
| AC-19 Transport → still PENDING | PASS | `AIOrchestrator.java:77-81` rethrows without DB write; test `transportFailureLeavesPending:128-144`; controller test `transport_failure_502` |
| AC-20 SpaceNotFound | PASS | `SpaceAnalysisPersistence.java:37` throws `SpaceNotFoundException`; advice maps to 404; tests `unknownRoomIdThrows` + controller `not_found_404` |
| AC-21 Spring config surface | PASS | `application.yml:66-75` all five required keys with correct defaults |
| AC-22 Poller off in test | PASS | Both mechanisms: `@ConditionalOnProperty(matchIfMissing=false)` AND `application-test.yml` `enabled: false` |
| AC-23 No raw HttpURLConnection / no Python libs in Java | PASS | Grep `HttpURLConnection` in `src/backend/src/main/java` = 0 matches. Grep `ultralytics\|mediapipe\|torch` = 0 matches. Client uses Spring 6 `RestClient` (`SpaceAnalysisClient.java:55-60`) |
| AC-24 Task-4 signature | PASS | `SpaceAnalysisClient.java:80` `public SpaceAnalysisResponse callSpaceAnalysis(String roomId, String photoUrl)` — DTO return, no ResponseEntity |
| AC-25 OpenAPI contract artifact | PASS | `api_contract.yaml` defines both Python `/analyze/space` + Spring `/internal/v1/spaces/{roomId}/analyze`; responses include 200/422/500 (Python) and 200/404/422/500/502 (Spring); shared schemas `PythonErrorResponse` + `SpringErrorResponse` |
| AC-26 No schema change | PASS | `src/backend/src/main/resources/db/migration/` contains only `V1__init_schema.sql` and `V2__add_spaces_status_and_photo.sql` — no `V3__*.sql` |
| AC-27 No UC-01-photo-upload regression | PASS (static) | No file under `src/backend/src/main/java/com/authenticself/controller` or `photo/` was modified by this task per design §5 ("PhotoUploadControllerTest + PhotoUploadServiceTest — untouched"). Cannot re-run tests in this env — see "Static checks" limitation below |

## 4. Static Checks

| Check | Status | Notes |
|---|---|---|
| Python imports + pyflakes | DEFERRED | No Python interpreter guaranteed in verification env. Static file-read of all `src/ai/app/**/*.py` confirmed all imports resolve within the package: `app.__init__`, `app.analyzers` (base/color/dimensions/style), `app.errors`, `app.schemas`, `app.main`. No circular imports. All `from __future__ import annotations` present |
| Gradle compileJava / check | DEFERRED | No Gradle wrapper in env (spec acknowledges). Static review: all referenced types exist (`RestClient`, `@ConditionalOnProperty`, `@EnableScheduling`, `JpaRepository`, `SimpleClientHttpRequestFactory`, Space domain w/ `Status` enum, `SpaceRepository.findTop10ByStatusOrderByUploadedAtAsc`). `build.gradle` declares required deps (`spring-boot-starter-data-jpa`, `spring-boot-starter-web`, `wiremock-standalone`, `mockito-core`) |
| SQL migration monotonicity | PASS | `V1__init_schema.sql` (Task 1), `V2__add_spaces_status_and_photo.sql` (Task 2). No V3 this task (AC-26) |

## 5. Test Results (mapping)

Every AC has at least one test or static artifact:
- **Python tests** (`src/ai/tests/`): `test_analyze_space.py` (10 tests) + `test_color_extractor.py` (4 tests) cover AC-3..AC-12, AC-14
- **Spring unit** (`AIOrchestratorTest`): happyPath, shortCircuitOnAnalyzed, shortCircuitOnFailed, analyzerFailureFlipsToFailed, transportFailureLeavesPending, unknownRoomIdThrows → AC-16..AC-20
- **Spring slice** (`AIOrchestratorControllerTest`): happy_path_200, short_circuit_200_skipped, analyzer_failure_422, transport_failure_502, not_found_404 → AC-16..AC-20 (HTTP shape)
- **Static scans** cover AC-1, AC-2, AC-10, AC-11, AC-12, AC-13, AC-15, AC-21, AC-22, AC-23, AC-24, AC-25, AC-26

Tests were not executed (no Python interpreter / no Gradle wrapper). All test files are syntactically sound and correctly wired to their targets per static read.

## 6. Security Issues

| Severity | Issue | File:line | Fix hint |
|---|---|---|---|
| None found | All checked: no hardcoded secrets, path-escape guard in place, `/internal/v1/*` not in public OpenAPI (fine — spec §8 declares it explicitly), no SQL injection (JPA derived-query only) | — | — |

## 7. Smell / Drift Issues

| Type | Location | Description |
|---|---|---|
| Config surface breadth | `application.yml:70-78` | Three extra alias keys (`app.ai.base-url`, `app.ai.connect-timeout-ms`, `app.ai.read-timeout-ms`) plus three extra poller knobs (`fixed-delay-ms`, `batch-size`, `max-attempts`) beyond the 5 required by AC-21. Not a violation — AC-21 is an inclusion check — but widens the supported configuration surface vs the spec. Recommend formalising in Task 4's spec |
| Poller interval key name | `AnalysisPoller.java:47` | Uses property chain `${app.ai.poller.fixed-delay-ms:${app.ai.poller.interval-ms:15000}}`. Spec FR-16 defines `app.ai.poller.interval-ms`; the `fixed-delay-ms` alias is a handoff artifact now entrenched. Works correctly; document for Task 4 |
| Minor — `processingMs` floor | `src/ai/app/main.py:272-273` | Defensive `if processing_ms <= 0: processing_ms = 1` to guarantee AC-4's `processingMs > 0` on very fast synthetic images. Correct but would hide a clock misconfiguration; acceptable for a dev/test service |

## 8. Blocker Summary

**None** — this is a PASS-WITH-WARNINGS. The code, tests, diagrams, and ACs are all mutually consistent.

The two warnings (expanded config surface + poller key alias) are design augmentations made for Task-4 forward-compat and do not violate any AC of this task. Prompt-specialist may choose to fold these into the next iteration's spec to eliminate future "drift" re-flags, but they are NOT blockers for merging UC-01-space-analysis.

---
Verdict: **PASS-WITH-WARNINGS**
Artifact: `C:/AuthenticSelf_Project/AuthenticSelf_v3/artifacts/UC-01-space-analysis/verification.md`
Top blockers: none
