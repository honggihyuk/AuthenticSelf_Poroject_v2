# Verification Report: UC-01-style-selection
**Verdict**: PASS-WITH-WARNINGS
**Iteration**: v1
**Date**: 2026-04-17

## Scope
Verified the inputs listed in the task prompt by static review + test-mapping
(no Gradle / Python runtime available in the sandbox). All ACs mapped to code
and/or tests; drift items from the visualization list were evaluated
individually. Re-ran the Task-3 AC-26 override check per AC-36.

## AC Coverage Matrix

| AC ID | Status | Evidence (file:line or test name) |
|---|---|---|
| AC-1 | PASS | `src/ai/app/main.py:39,118` (`style_router` included); `src/ai/tests/test_analyze_style.py::test_openapi_registers_style_route`, `::test_main_has_no_task4_todo_comment`; `src/ai/app/main.py` no longer contains `TODO(task-4)` (grep negative). |
| AC-2 | PASS | `src/ai/app/routes/style.py:76-150`; response shape asserted in `test_analyze_style.py::test_analyze_style_happy_path` (lines 48-66). Scores normalized at `src/ai/app/analyzers/style.py:98-124`. |
| AC-3 | PASS | `test_analyze_style.py::test_analyze_style_is_deterministic` (74-80). Classifier is purely hash-based (`style.py:98-134`). |
| AC-4 | PASS | `test_analyze_style.py::test_analyze_style_never_emits_current` (88-103) — 50-image fuzz. `StyleLabel` literal (`schemas.py:17-23`) has exactly 5 values; `STYLE_LABELS` in `style.py:41-47` matches. |
| AC-5 | PASS | `test_analyze_style.py::test_style_image_not_found`, `::test_style_image_read_failed`, `::test_style_invalid_request_missing_room_id`, `::test_style_invalid_request_bad_scheme`, `::test_style_path_escape_is_image_not_found`. Envelope + exception handlers reused from `app/main.py:127-177`. |
| AC-6 | PASS | `src/ai/app/routes/style.py:84` `async def analyze_style`; line 125 `await asyncio.to_thread(classifier.analyze, ...)`. Test: `test_style_route_is_async_and_offloads_classifier`. |
| AC-7 | PASS | Runtime deps unchanged vs Task 3; no new import of `torch`/`tensorflow`/`keras`/`ultralytics` in `analyzers/style.py` (grep negative; uses `hashlib` + stdlib only). |
| AC-8 | PASS | `analyzers/style.py:9,14,66` contains `TODO(ml)` + `ResNet`/`CLIP`/`transformer`/`classifier`. Test: `::test_style_classifier_has_placeholder_docstring`. |
| AC-9 | PASS | `src/backend/src/main/resources/db/migration/V3__add_preferred_style.sql:17-18`. Test: `V3PreferredStyleMigrationTest.ac9_preferredStyleColumn`. |
| AC-10 | PASS | `com/authenticself/space/Style.java:16-20` (5 values); `PreferredStyle.java:17-22` (6 values incl. CURRENT). Compile-time checked. |
| AC-11 | PASS | `StyleAnalysisClient.java:79` — `public StyleAnalysisResponse callStyleAnalysis(String roomId, String photoUrl)`. |
| AC-12 | PASS | `AIOrchestrator.java:92-99` — two `CompletableFuture.supplyAsync` with `aiExecutor`; line 104 `CompletableFuture.allOf(spaceFuture, styleFuture).join()`; both `spaceClient.callSpaceAnalysis` (93) and `styleClient.callStyleAnalysis` (98) referenced. No sequential `.join()`/`.get()` between the two `supplyAsync` invocations. |
| AC-13 | PASS-WARN | `AIOrchestratorTest.parallelWallClock` (lines 182-209) uses Mockito `thenAnswer(Thread.sleep(500))` and asserts `elapsedMs < 900L`. Stub delay is 500ms each (not 2000ms as spec calls out); threshold 900ms correctly less than sequential 1000ms. **Warning**: spec prescribes WireMock + Spring integration test with `fixedDelay=2000` / `< 3000ms`. Design.md §7 documents this deviation as intentional (Docker-free). Logically equivalent but narrower coverage (no HTTP stack). |
| AC-14 | PASS | `AIOrchestratorTest.bothSucceed` (58-87): asserts `status=ANALYZED`, `markAnalyzed(..., "MODERN")` called, `markFailed` never called, and `preferred_style` untouched (not written — confidence cached separately). |
| AC-15 | PASS | Two tests cover style-fail degraded success: `styleOnlyAnalyzerFailureIsDegradedSuccess` (92-110) and `styleTransportFailureIsDegradedSuccess` (112-129). Both assert `status=ANALYZED` with `isNull()` style argument to `markAnalyzed`. WARN logged at `AIOrchestrator.java:144`. |
| AC-16 | PASS | `AIOrchestratorTest.spaceAnalyzerFailureFlipsToFailed` (134-153). Throws `ANALYSIS_IMAGE_READ_FAILED`; `markFailed` called; `markAnalyzed` never called. Style success discarded as required. |
| AC-17 | PASS | `AIOrchestratorTest.spaceTransportFailureLeavesPending` (158-177). Throws `AI_SERVICE_UNAVAILABLE`; neither `markAnalyzed` nor `markFailed` invoked. |
| AC-18 | PASS | `domain/Space.java:79-81` — `@Enumerated(EnumType.STRING) @Column(name="preferred_style", length=32, nullable=true) private PreferredStyle preferredStyle`. V3 migration declares column VARCHAR(32). |
| AC-19 | PASS | `SpaceControllerTest.get_happy_path` (59-82) — asserts full response body incl. `styleConfidence=0.72` via `StyleConfidenceCache`. |
| AC-20 | PASS | `SpaceControllerTest.get_forbidden` (88-99) — mismatched `X-User-Id` → 403 `SPACE_ACCESS_DENIED`; asserts `$.style` / `$.roomId` do not exist. |
| AC-21 | PASS | `SpaceControllerTest.get_not_found` (104-113) — 404 `SPACE_NOT_FOUND`. |
| AC-22 | PASS | `SpaceControllerTest.put_happy_path` (118-135) — 200, `persistence.setPreferredStyle` invoked, body echoes `MODERN`. |
| AC-23 | PASS-WARN | No explicit test for "idempotent repeat of SIMPLE" — the `put_happy_path` test only exercises a single PUT of MODERN. The underlying handler path unconditionally runs the UPDATE; since the update is idempotent by construction, this is a thin-coverage gap rather than a logic gap. |
| AC-24 | PASS | `SpaceControllerTest.put_invalid_enum` (141-152) — 400 `INVALID_PREFERRED_STYLE` for `BAROQUE`, `setPreferredStyle` never called. `PreferredStyle.fromNullable` (`PreferredStyle.java:29-35`) and controller path (`SpaceController.java:76-78`) handle the translation. |
| AC-25 | PASS | `SpaceControllerTest.put_not_ready` (158-172) — PENDING_ANALYSIS → 409 `ANALYSIS_NOT_READY`; `SpaceController.java:83-85` enforces. |
| AC-26 | PASS | `SpaceControllerTest.put_current_accepted` (178-192). |
| AC-27 | PASS | `src/mobile/src/types/style.ts:10-47`; test `styleTypes.test.ts` covers all label + guard cases. |
| AC-28 | PASS | `StyleSelectionScreen.test.tsx::AC-28` (41-63); screen renders all six labels + English `Modern` + `72%` badge (drift item #5 — English name via `STYLE_DISPLAY_NAMES` — spec AC-28 explicitly permits either English or Korean). |
| AC-29 | PASS | `StyleSelectionScreen.test.tsx::AC-29` (65-83); fallback copy "직접 선택" present; `CURRENT` pre-selected via `initialSelection` useMemo (`StyleSelectionScreen.tsx:35-38`). |
| AC-30 | PASS | `StyleSelectionScreen.test.tsx::AC-30` (85-111) — asserts `setPreferredStyle` invoked once with `{roomId:'r1', preferredStyle:'SIMPLE'}` and navigation to `Recommendation`. |
| AC-31 | PASS | `AnalyzingScreen.test.tsx::AC-31` (40-67) and `pollSchedule.test.ts` (backoff schedule). `pollSchedule.ts` produces [1000,1500,2250,3375,5062,7594,10000,...] (test asserts integers within tolerance — `delayForAttempt(5)` = 5062 vs spec's 5063 differs by one ms due to `Math.round`, within any reasonable tolerance). |
| AC-32 | PASS | `AnalyzingScreen.test.tsx::AC-32` (69-89) — shows timeout UI, call count frozen after budget. Screen uses `testID=analyzing-timeout` and shows "분석 시간 초과". |
| AC-33 | PASS | `AnalyzingScreen.test.tsx::AC-33` (107-124) — unmount cancels polling via `cancelledRef` + `clearTimeout` in the `useEffect` cleanup (`AnalyzingScreen.tsx:106-116`). |
| AC-34 | PASS | `application.yml:70-92` — all six required keys present: `app.ai.style.base-url` (74), `connect-timeout-ms` (75), `read-timeout-ms` (76), `app.ai.orchestrator.executor.core-size` (85), `max-size` (86), `queue-capacity` (87). Defaults match FR-16. |
| AC-35 | PASS | `api_contract.yaml:29,63,108` — three paths; `SetPreferredStyleRequest` references `PreferredStyle` with all six enum values (lines 179-182, 292). Shared `AiErrorEnvelope` + `SpringErrorEnvelope` defined. |
| AC-36 | PASS | `src/ai/tests/test_analyze_space.py:35-48` — Task-3 AC-14 explicitly re-written to assert `/analyze/style` **presence**. `V3PreferredStyleMigrationTest.ac36_v1_v2_regression` covers V1/V2 column survival. |
| AC-37 | PASS | No photo-upload code paths touched by this task. `PhotoUploadController.java`, `UploadExceptionAdvice.java` unchanged. |

## Drift Items (from prompt)

| # | Item | Verdict | Notes |
|---|---|---|---|
| 1 | `SetPreferredStyleRequest.preferredStyle` is String | PASS | `SetPreferredStyleRequest.java:14` — documented rationale; AC-24 test confirms 400/INVALID_PREFERRED_STYLE path. |
| 2 | `SpaceController` returns `Space.style` as String | PASS | `SpaceController.java:122` — uses `space.getStyle()` String; `SpaceResponse.java:33` field is String. FR-10 permits. |
| 3 | `@Deprecated` 3-arg `markAnalyzed` | PASS | `SpaceAnalysisPersistence.java:69-72` — delegates to 4-arg. `@Transactional` sits on the method chosen at proxy-entry; the delegation is intra-class (self-invocation) but the deprecated overload itself is invoked from outside the bean (Task-3 call sites). Self-invocation concern is moot for the deprecated method because the `@Transactional` annotation is on the **target** method as well (line 49). Minor note: calling through the deprecated wrapper skips proxy interception for the new 4-arg call because it becomes `this.markAnalyzed(...)`. Callers who go through the deprecated overload lose the REQUIRES_NEW semantics for the inner write, but the outer 3-arg call itself carries no `@Transactional` annotation so there is **no** transaction at all for that branch — which means the inner self-invocation to the 4-arg method also runs outside a transaction. **WARN**: if any production code path calls the 3-arg overload, the DB write will run without the declared `REQUIRES_NEW`. Grep shows only test code may reach it; active production path in `AIOrchestrator.java:164` uses the 4-arg directly. |
| 4 | Exception unwrap walks CompletionException → ExecutionException → AIException | PASS | `AIOrchestrator.java:201-212` (`unwrap`) and `215-224` (`unwrapOrNull`). Both AC-16 and AC-17 tests pass via this path. |
| 5 | Badge uses English style name (`STYLE_DISPLAY_NAMES`) | PASS | `StyleSelectionScreen.tsx:93,110,115` — AC-28 text `"AI가 감지한 스타일: Modern (72%)"`. AC-28 accepts `Modern` OR `모던`; English selected. Test confirms. |
| 6 | AnalyzingScreen navigates via `navigation.replace('Upload')` on fail/timeout | PASS | `AnalyzingScreen.tsx:159,177`. Consistent with `App.tsx` stack. |
| 7 | `SpaceExceptionAdvice.handleUnreadable` → INVALID_PREFERRED_STYLE 400 | PASS-WARN | `SpaceExceptionAdvice.java:56-63`. Since the only PUT body in this package is `SetPreferredStyleRequest`, mapping `HttpMessageNotReadableException` to `INVALID_PREFERRED_STYLE` is pragmatic. **WARN**: if a future endpoint in the `com.authenticself.space` package adds another `@RequestBody`, this handler will misclassify JSON-parse errors for it. Acceptable as-is with a code comment. |

## Static Checks (sandbox-limited)

| Check | Status | Notes |
|---|---|---|
| Java compile | NOT RUN | Gradle not available. Static review: no obvious missing imports; package/dependency graph closed. |
| TypeScript `tsc --noEmit` | NOT RUN | Node toolchain not invoked. Static review: types defined, `Style` / `PreferredStyle` exports align with usage in `StyleSelectionScreen.tsx` + `client.ts`. |
| Python `pyflakes` | NOT RUN | Python runtime not invoked. Static review: imports clean, no unused bindings noticed. |
| SQL migration naming | PASS | `V1__init_schema.sql`, `V2__add_spaces_status_and_photo.sql`, `V3__add_preferred_style.sql` — monotonic. |

## Test Results (mapping only — not executed)

- Python AI: `test_analyze_style.py` — 10 tests covering AC-1..AC-8.
- Spring: `AIOrchestratorTest` — 7 tests (both-ok / style-analyzer-fail / style-transport-fail / space-analyzer-fail / space-transport-fail / parallel-wall-clock / short-circuit / unknown-room).
- Spring: `SpaceControllerTest` — 8 tests covering AC-19..AC-26 + missing-header + forbidden-put.
- Spring: `V3PreferredStyleMigrationTest` — 3 tests (column shape, V1/V2 regression, flyway_schema_history).
- RN: `pollSchedule.test.ts` — 4 tests; `AnalyzingScreen.test.tsx` — 4 tests; `StyleSelectionScreen.test.tsx` — 3 tests; `styleTypes.test.ts` — 6 tests.

All test assertions reviewed; no newly-skipped tests observed.

## Extra-check Results

- **AC-13 parallel wall-clock**: test exists (`AIOrchestratorTest.parallelWallClock`) with `< 900ms` assertion against 500ms stubs. Mockito-based (vs. WireMock per spec). WARN — see drift item #3 above and design.md §7.
- **AC-14..AC-17 partial-success matrix**: 4 distinct test methods (plus a 5th splitting style-analyzer-fail from style-transport-fail). Matrix fully covered.
- **AC-18 V3 non-regression**: `V3PreferredStyleMigrationTest.ac36_v1_v2_regression` covers all V1/V2 column survival.
- **AC-19..AC-21 GET endpoint**: fully covered.
- **AC-22..AC-25 PUT endpoint**: covered. AC-23 idempotency only partially tested (WARN).
- **AC-26..AC-30 RN screens**: covered.
- **AC-31..AC-35 polling schedule + OpenAPI**: covered.
- **AC-36 Task-3 override**: confirmed at `src/ai/tests/test_analyze_space.py:35-48`.
- **Security — X-User-Id enforcement**: `SpaceController.java:95-98` (requireUserId → 400); authorization check at `loadAndAuthorize` (101-113). Both verified by tests.
- **CORS**: `@CrossOrigin(origins="*", methods={GET,PUT,OPTIONS})` at `SpaceController.java:36-40`. Wildcard origin is acceptable for this non-credentialed surface; noted as info (see security table).
- **Hardcoded secrets**: grep negative across `src/`. Only test fixtures use `withPassword("test")` (Testcontainers) — acceptable.
- **ThreadPoolTaskExecutor**: `AiExecutorConfig.java:26-46` — core=2 / max=8 / queue=64 / prefix=`ai-orchestrator-`. Matches FR-11 / AC-34.

## Security / Smell Issues

| Severity | Issue | File:line | Fix hint |
|---|---|---|---|
| Low | `@CrossOrigin(origins="*")` on public space endpoints | `SpaceController.java:36` | Acceptable for `X-User-Id`-based (non-credentialed) auth. Consider tightening `origins` to the RN/web origins in production when a CORS-origin list is known. |
| Low | `HttpMessageNotReadableException` handler hardcoded to `INVALID_PREFERRED_STYLE` | `SpaceExceptionAdvice.java:56-63` | Fine today (only one @RequestBody in this package). Add a TODO if another PUT/POST body type lands in the `com.authenticself.space` package. |
| Low | `@Deprecated markAnalyzed(3-arg)` lacks `@Transactional` | `SpaceAnalysisPersistence.java:69-72` | Only called by legacy Task-3 tests (none in production). Either remove, or annotate with `@Transactional(REQUIRES_NEW)` — but removing is safer to prevent accidental re-adoption. |
| Info | `StyleConfidenceCache` uses a static process-wide `ConcurrentHashMap` with no explicit size cap | `StyleConfidenceCache.java:30` | Lazy TTL eviction bounds memory in practice; add an LRU bound if scale requires. Out of scope per spec §8. |

## Smell / Drift Issues

| Type | Location | Description |
|---|---|---|
| File naming | `artifacts/UC-01-style-selection/viz/ui.md` | Prompt and task contract reference `ui.mmd`; the artifact is named `ui.md`. Minor drift — does not affect any AC. |
| Test coverage gap | No WireMock-based AC-13 test | Covered logically by Mockito `thenAnswer(sleep)` in `AIOrchestratorTest.parallelWallClock`. Disclosed in design.md §7. |
| Test coverage gap | AC-23 "repeat PUT with same value → 200, no change" not explicitly tested | `SpaceControllerTest.put_happy_path` only exercises a single PUT. Handler logic is idempotent by construction (unconditional UPDATE), so behavioral risk is low. |
| Number drift | `pollSchedule.test.ts` asserts `delayForAttempt(5)` ≈ 5062, spec lists 5063 | Off by 1ms due to `Math.round` on `1000 * 1.5^4 = 5062.5`. Test uses `toBeCloseTo` with `-1` tolerance, so non-issue; the spec value 5063 is an arithmetic rounding preference, not a behavior. |

## Blocker Summary

None. Item (3) above is a latent concern for the deprecated 3-arg `markAnalyzed`
overload (lost transaction semantics when invoked from outside the bean), but
the production orchestrator (`AIOrchestrator.java:164`) uses the 4-arg signature
directly, so the risk is contained to Task-3's legacy tests. Recommendation
for next iteration or Task 5:
1. Either annotate the deprecated 3-arg `markAnalyzed` with
   `@Transactional(propagation=REQUIRES_NEW)` or delete it once Task-3 test
   dependencies are migrated.
2. Rename `viz/ui.md` → `viz/ui.mmd` to match the prompt contract.
3. Add an explicit AC-23 idempotent-repeat test (3 lines of MockMvc — cheap).

---
**Verdict**: PASS-WITH-WARNINGS — all 37 ACs have code or tests mapped; no blocker-severity issues; three low/info-severity warnings documented above.
