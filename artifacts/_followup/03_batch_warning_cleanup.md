# Follow-up (3) — Batch Warning Cleanup

**Date**: 2026-04-18
**Scope**: harvest all PASS-WITH-WARNINGS items from the 8 verification reports
+ Jest J-1..J-5 + AC-29 unload ref-null; classify; apply cheap fixes; defer the
rest. Hard constraints: no edits to spec.md / design.md / api_contract.yaml /
verification.md / V1–V7 migrations; no public-contract breakage; no Jest /
Gradle destructive operations.

## 1. Harvest table (consolidated warnings)

Buckets: **CC** = CHEAP-COSMETIC, **CD** = CHEAP-CODE, **EXP** = EXPENSIVE,
**DP** = DEFER-PRODUCT.

| # | Origin (task) | ID | Summary | Bucket | Action |
|---|---|---|---|---|---|
| W1 | DB-schema-init | W1 | AC-17 `./gradlew compileJava` not run (env: no JDK/Gradle) | EXP | Deferred — toolchain setup out of batch scope |
| W2 | DB-schema-init | W2 | Testcontainers V1 migration test not executed (no Docker) | EXP | Deferred — CI responsibility |
| W3 | DB-schema-init | W3 | `Wishlist.java` `columnDefinition="ENUM(...)"` may clash with `ddl-auto=validate` | CD | Deferred — touches shared domain entity referenced by 3+ later tasks; requires verification against Flyway validator run (not available in env) |
| W4 | DB-schema-init | (info) | `application.yml` `MySQL8Dialect` deprecated in Hibernate 6.x | CC | Deferred — same shared file; config change safer to schedule as a dedicated `dialect-cleanup` task so Spring startup can be smoke-tested |
| W5 | UC-01-photo-upload | Drift #1 | `UNKNOWN_USER` Korean copy differs between `UploadErrorCode.java:14` and `errorMessages.ts:27-28` | CD | Deferred — product/UX copy decision; RN table is the user-facing one, backend string surfaces in logs; cross-team sign-off recommended |
| W6 | UC-01-photo-upload | Drift #3 | spec FR-3 says `put(...)`; code uses `upload(...)` | DP | Deferred — spec file is immutable per batch constraints |
| W7 | UC-01-photo-upload | Follow-up | `V2SpacesMigrationTest` not yet written | EXP | Deferred — requires Testcontainers + Docker + new test class (>20 LOC) |
| W8 | UC-01-space-analysis | Smell #1 | Extra `app.ai.*` alias keys and `poller.fixed-delay-ms` in `application.yml` beyond AC-21's required 5 | DP | Deferred — forward-compat knobs, spec update owner call |
| W9 | UC-01-space-analysis | Smell #2 | `AnalysisPoller.java:47` uses alias chain `fixed-delay-ms:interval-ms:15000` | DP | Deferred — same, cross-task config surface |
| W10 | UC-01-space-analysis | Smell #3 | `processing_ms` floor-to-1 in `src/ai/app/main.py:272` hides clock misconfiguration | CC | Deferred — defensive code, documented, removal would flake AC-4 on fast test images |
| W11 | UC-01-style-selection | Drift #3 | `@Deprecated` 3-arg `markAnalyzed` loses `@Transactional(REQUIRES_NEW)` on self-invocation | CD | Deferred — touches transactional semantics; safer as dedicated cleanup (verify no Task-3 test depends on the 3-arg overload first) |
| W12 | UC-01-style-selection | Drift #7 | `SpaceExceptionAdvice.handleUnreadable → INVALID_PREFERRED_STYLE` would misclassify future `@RequestBody` types | CC | Deferred — needs code comment but advice file edit touches sibling-task package advice chain |
| W13 | UC-01-style-selection | AC-23 | No explicit idempotent-PUT test | CD | Deferred — 3-line MockMvc add is cheap but requires Gradle to confirm green; env-blocked |
| W14 | UC-01-style-selection | Drift/rename | `viz/ui.md` should be `viz/ui.mmd` | DP | Deferred — artifacts/ files immutable per batch constraints |
| W15 | UC-01-style-selection | Smell | `@CrossOrigin(origins="*")` on public space endpoints | DP | Deferred — production CORS tightening is deployment-time decision |
| W16 | UC-01-style-selection | Smell | `StyleConfidenceCache` unbounded `ConcurrentHashMap` | EXP | Deferred — LRU bound is a real code change; spec §8 lists as out-of-scope |
| W17 | UC-01-style-selection | Number | `pollSchedule` delay(5) = 5062 vs spec 5063 (1ms round drift) | CC | Deferred — test uses `toBeCloseTo` tolerance; spec number can't be amended |
| W18 | UC-01-recommendation | W#1 | `preferredStyle` route param optional vs. FR-24 required | DP | Deferred — design rationale documented; changing regresses Task-4 AC-52 |
| W19 | UC-01-recommendation | W#2 | `MISSING_USER_HEADER @ 400` vs spec FR-19 `UNKNOWN_USER @ 401` | DP | Deferred — cross-endpoint-family refactor, spec-owner decision |
| W20 | UC-01-recommendation | W#3 | Windows-Korean-path `cv2.imread` Python tests fail | EXP | Deferred — OpenCV upstream limitation on non-ASCII paths; workaround (`np.fromfile`+`cv2.imdecode`) is a real 10–20 LOC refactor in `analyzers/color.py` + `analyzers/dimensions.py` and touches immutable Task-3 source |
| W21 | UC-02-wishlist | Warning §1 | AC-20 cross-user GET leakage integration test missing | EXP | Deferred — requires Testcontainers test class (>20 LOC + Docker + Gradle env) |
| W22 | UC-02-wishlist | Warning §2 | AC-24 same-state PATCH `updated_at` byte-compare test missing | EXP | Deferred — same (Testcontainers-dependent) |
| W23 | UC-02-wishlist | Warning §3 | `WishlistRepository` declares 5 finders vs spec's 3 | DP | Deferred — spec file immutable; all 5 are load-bearing |
| W24 | UC-03-admin-overview | AC-30 | Repository `@Query` method-name `_Raw` suffix drift | DP | Deferred — AC text permits |
| W25 | UC-03-admin-overview | W-1 | `AdminOverviewRepository` marker-entity pattern | DP | Deferred — preserved per AC-60(a) |
| W26 | UC-03-admin-overview | W-2 | FR-5 totalUsers/totalAdmins window semantics | DP | Deferred — spec-code agree, semantic nit |
| W27 | UC-03-admin-overview | W-4 | `MISSING_USER_HEADER` duplicated across 3 enums | DP | Deferred per AC-60(e) |
| W28 | UC-03-admin-overview | W-5 | Integration-test seed 10% of NFR scale | DP | Deferred — preserved per AC-60(b), nightly test is product follow-up |
| W29 | UC-03-admin-overview | W-6 | `UsersTileResponse` Javadoc references `@JsonUnwrapped` but record uses flat fields (with now-unused import) | **CC** | **FIX APPLIED** — rewrote Javadoc to describe actual flat-record shape; removed unused `com.fasterxml.jackson.annotation.JsonUnwrapped` import |
| W30 | UC-03-admin-overview | W-7 | `Space.Status` 3-value vs spec 4-value | DP | Deferred per AC-60(c) |
| W31 | UC-03-admin-overview | W-8 | Repository method count 14 (FR-9) vs 17 (actual) | DP | Deferred — AC-30 count tolerant |
| W32 | UC-03-admin-overview | W-9 | Repository returns `List<Object[]>` not typed projections | DP | Deferred per AC-60(d) |
| W33 | UC-03-admin-overview | **W-15** | Unused `import java.sql.Date;` at `AdminOverviewService.java:21` (left behind after iter-2 Edit 4 removed the bare-type `instanceof Date` branch) | **CC** | **FIX APPLIED** — removed the unused import; `java.sql.Date` is still used fully-qualified on L347 |
| W34 | AR-furniture-placement | Dev-1 | `htmlBuilder.ts` not created | DP | Deferred — spec-allowed; no AC references it |
| W35 | AR-furniture-placement | Dev-2 | `fallback.tsx` inlined into screen | DP | Deferred — spec-allowed |
| W36 | AR-furniture-placement | Viz-4 | State-name drift vs brief (`loading/live/placed/cancelled/error` vs `ar_active/committed/...`) | DP | Deferred — ACs behaviour-based, naming not pinned |
| W37 | AR-furniture-placement | Viz-5 | `AR_READY` reserved-but-not-emitted | DP | Deferred — forward-compat |
| W38 | AR-furniture-placement | Viz-6 | Load injection via `useEffect` not `onLoadStart` | DP | Deferred — semantic spec satisfied |
| W39 | AR-furniture-placement | Viz-7 | "AR을 준비하고 있어요…" loading copy omitted | CD | Deferred — product/UX copy decision, needs sign-off |
| W40 | AR-furniture-placement | Handshake-8 | Field names in meters (`widthM`) not centimeters | DP | Deferred — spec FR-3 says meters |
| W41 | AR-furniture-placement | W-1 | `window.ReactNativeWebView.postMessage(...)` split into `rnWV = ...; rnWV.postMessage(...)` | CC | Deferred — functionally equivalent; rewriting back would be stylistic churn with test risk |
| W42 | AR-furniture-placement | **W-2** | AC-42 grep-negative for `google-analytics/gtag/facebook.com/amplitude/mixpanel` matches the descriptive comment in `placement.html:11-12` | **CC** | **FIX APPLIED** — rewrote the comment to describe the negative assertion without listing the tracker names literally; grep now returns 0 matches |
| W43 | AR-furniture-placement | W-3 | Spec §12 Q4 Option-C Unity downgrade PO sign-off | DP | Already addressed via `artifacts/_followup/02_unity_downgrade_signoff.md` |
| W44 | AR-furniture-placement | W-4 | `node_modules` not installed at verification time (blocks `tsc --noEmit` + `npm test`) | EXP | Deferred — now resolved in follow-up (1); not a code defect |
| J-1 | Jest suite | J-1 | `UploadScreen.test.tsx:27` TS parameter-property in jest.mock factory | CC | **FIX APPLIED EARLIER this session** — replaced `constructor(public httpStatus: number, body: any)` with explicit assignment; verified passing in `__tests__/UploadScreen.test.tsx` |
| J-2 | Jest suite | J-2 | `ARPlacementScreen.test.tsx:31` `type Handle` alias inside jest.mock factory | CC | **FIX APPLIED EARLIER this session** — removed `type Handle`; inlined `React.Ref<{ inject: (s: string) => void }>`; compile fixed |
| J-3 | Jest suite | J-3 | `RecommendationScreen.test.tsx` AC-45 "Found multiple elements with text: 매칭 87%" | CD | Deferred per explicit task instruction (investigate only). **Root cause**: `makeItem()` helper at test.tsx:44 defaults `fitScore = 0.87`. `twoItemsPerCategory()` (line 62) overrides `fitScore` only on the two desk cards; the six cards for bed/chair/lighting fall through to the default 0.87, so 7 of 8 cards render the text "매칭 87%". **This is a test fixture defect, NOT a production bug.** Prod code at `RecommendationScreen.tsx:308` correctly renders the formatted fitScore per card. Recommended fix shape: narrow the assertion with `within(getByTestId('card-f_desk_001')).getByText('매칭 87%')`, or set distinct fitScore values (0.87 / 0.80 / 0.75 / 0.70) on the non-desk categories in the fixture. |
| J-4 | Jest suite | J-4 | `AnalyzingScreen.test.tsx` AC-31 polling timeout | CD → re-classified to CD-defer | Deferred — initial hypothesis was default-5000ms timeout; applied `jest.setTimeout(15000)` + reverted after local run showed the real failure is `Received: 5, Expected: >= 6` (the fake-timer loop advances only 5 polls given the test's advance schedule vs. backoff). Proper fix is either (a) extend the advance loop by one step, or (b) adjust the assertion to `>= 5`. Needs product/spec decision on the "6 calls within ~13s" promise. Reverted setTimeout edit to avoid masking. |
| J-5 | Jest suite | J-5 | `StyleSelectionScreen.test.tsx` AC-28 cold-start timeout | CD → re-classified to CD-defer | Deferred — same investigation: local run shows real failure is `Found multiple elements with text: /Modern/` (the screen renders "Modern" as both a style button AND inside the AI-detection badge). Not a timeout; fixture/assertion scoping issue. Recommended fix shape: `within(getByTestId('ai-badge')).getByText(/Modern/)` or `getAllByText(/Modern/)` with length assertion. |
| J-6 | AR follow-up | AC-29 | `ARPlacementScreen.tsx:200-217` `useEffect` cleanup calls `webviewRef.current?.inject(...)` but ref is null at unmount; `unload` message never sent at runtime | EXP | Deferred per explicit task instruction — real React-timing bug, needs careful design (capture inject function in a separate closure-scoped ref that survives the WebView unmount, or hoist the inject-on-unload into an effect that holds a stable snapshot). Local test confirms: `expect(afterUnmount.length).toBeGreaterThan(afterMount.length)` fails (afterUnmount=1, afterMount=1). Schedule as new task `AR-furniture-placement-unload-fix-v2`. |

**Total warnings harvested**: 50 (44 from verification reports + 6 Jest/AR items)

## 2. Fixes applied

### Fix 1 — W-33 (UC-03-admin-overview): remove unused `java.sql.Date` import

**File**: `src/backend/src/main/java/com/authenticself/admin/AdminOverviewService.java`

**Diff**:
```diff
 import java.math.BigDecimal;
 import java.math.RoundingMode;
-import java.sql.Date;
 import java.sql.Timestamp;
```

**Explanation**: iter-2 Edit 4 removed the bare-type `if (v instanceof Date d)`
branch at L350, leaving `import java.sql.Date;` with zero referents (the
remaining `instanceof java.sql.Date d` on L347 is fully-qualified). AC-58
now stays PASS with the import drift resolved.

**Verification**: Grep confirms `java.sql.Date` still used on L347 (fully-qualified);
no other `\bDate\b` references in the file. Java compile not runnable in this
env (Gradle/JDK absent — documented constraint). No call-site risk: imports
are scope-local.

### Fix 2 — W-29 (UC-03-admin-overview): correct `UsersTileResponse` Javadoc + remove unused import

**File**: `src/backend/src/main/java/com/authenticself/admin/dto/UsersTileResponse.java`

**Diff**:
```diff
 package com.authenticself.admin.dto;

-import com.fasterxml.jackson.annotation.JsonUnwrapped;
-
 import java.time.OffsetDateTime;
 import java.util.List;

 /**
  * Standalone wire response for {@code GET /api/v1/admin/users}.
  * <p>
  * Top-level {@code window} and {@code generatedAt} precede the tile-
- * specific payload (AC-53), and the tile fields are flattened at the
- * top level via {@link JsonUnwrapped} so the body shape matches the
- * FR-5 spec exactly (not a nested {@code users: {...}} object — that is
- * the shape used by {@link AdminOverviewResponse}).
+ * specific payload (AC-53), and the tile fields are flattened as
+ * top-level record components (not via {@code @JsonUnwrapped}) so the
+ * body shape matches the FR-5 spec exactly — not a nested
+ * {@code users: {...}} object, which is the shape used by
+ * {@link AdminOverviewResponse}.
  *
- * <p>The nested {@link UsersTile} is identical to the value inlined
- * under {@code users} in the master overview response — this is the
- * mechanism that satisfies the AC-33 byte-identical invariant across the
- * two endpoint shapes.
+ * <p>The byte-for-byte field set here mirrors the inlined {@link UsersTile}
+ * payload under {@code users} in the master overview response; the
+ * static factory {@link #of(String, OffsetDateTime, UsersTile)} is the
+ * mechanism that satisfies the AC-33 byte-identical invariant across the
+ * two endpoint shapes.
  */
```

**Explanation**: the record never used `@JsonUnwrapped` — field names are
declared directly on the record header. The Javadoc @link referred to a
type that wasn't imported-for-use (only for Javadoc), and the import was
otherwise unused. Wire shape unchanged; only Javadoc/import cleanup.

**Verification**: Java compile unavailable in env. Zero code-path changes — a
Javadoc annotation reference was replaced with a `@code` text reference. No
AC-12 / AC-33 / AC-53 test depends on the import list.

### Fix 3 — W-42 (AR-furniture-placement): neutralize tracker names in HTML comment

**File**: `src/mobile/assets/ar/placement.html`

**Diff**:
```diff
   <!--
-    AC-12 — pinned @google/model-viewer CDN URL, exact version 3.5.0.
+    AC-12 — pinned model-viewer CDN URL, exact version 3.5.0.
     The only external URL loaded by this scene. No analytics, no trackers.
-    AC-42 asserts there is no google-analytics / gtag / facebook.com /
-    amplitude / mixpanel reference in this file.
+    AC-42 asserts there is no third-party analytics or event-tracking
+    script reference in this file (see spec FR for the enumerated list).
   -->
```

**Explanation**: AC-42 is a strict `grep_negative` for
`google-analytics|gtag|facebook.com|amplitude|mixpanel`. The original
comment listed those names verbatim, causing a grep-level false positive
noted in verification W-2. Rewriting the comment to describe the assertion
without naming the trackers removes the ambiguity.

**Verification**:
```
$ grep -E 'google-analytics|gtag|facebook.com|amplitude|mixpanel' placement.html
(no matches)
```
AC-42 now strictly PASSes rather than PASS-WITH-WARNING.

### Verification commands actually run

| Command | Exit | Result |
|---|---|---|
| `Grep \bDate\b AdminOverviewService.java` | — | 1 match (fully-qualified L347), confirms import removal safe |
| `Grep google-analytics\|gtag\|facebook.com\|amplitude\|mixpanel placement.html` | — | 0 matches, confirms AC-42 now strict-clean |
| `cd src/mobile && npx jest --watchAll=false --testPathPattern "UploadScreen\|ARPlacementScreen"` | 1 | UploadScreen: 1/1 PASS (confirms J-1 prior fix still green). ARPlacementScreen: 18/19 PASS — 1 fail is the pre-identified J-6 unload ref-null (expected, deferred per task instruction). |
| `cd src/mobile && npx jest --watchAll=false --testPathPattern "RecommendationScreen"` | 1 | 13/14 PASS — 1 fail is J-3 fixture-duplicate-87% (expected). |
| `cd src/mobile && npx jest --watchAll=false --testPathPattern "AnalyzingScreen\|StyleSelectionScreen"` | 1 | Revealed J-4 and J-5 are NOT default-timeout issues — they are real assertion failures. Timeout bump reverted. Re-classified as CD-defer. |

No Python fixes were in scope (W-20 is the only Python warning and it's
EXPENSIVE — OpenCV non-ASCII path workaround touching immutable Task-3
source). No Spring Java behaviour changed; only javadoc+imports.

## 3. Deferred items

See the harvest table for the full list (44 of 50 rows). Key high-value
next-step recommendations:

- **New task `AR-furniture-placement-unload-fix-v2`** — design a safe
  ref-capture strategy for J-6 / AC-29 so `unload` reliably fires at unmount.
  Candidate shape: hoist `webviewRef.current.inject` into a
  `useRef<(s: string) => void>()` that gets written in the WebView's
  `onMessage` handler, so cleanup can call the cached function even when
  the WebView's own ref is torn down first.
- **New task `recommendation-fixture-fix`** — tighten
  `RecommendationScreen.test.tsx` AC-45 (J-3) and AC-28 (J-5) assertions
  to scope via `within(testID)` or distinguish fitScore across categories.
  Also extend AnalyzingScreen AC-31 (J-4) advance loop to deliver the
  6th poll, or soften the assertion to `>= 5` if spec permits.
- **New task `V2SpacesMigrationTest`** — implement W-7 once Docker + Gradle
  are available (covers W-7, W-21, W-22, W-13 in one Testcontainers pass).
- **`MISSING_USER_HEADER` 400-vs-401 family refactor** — W-19 calls for a
  single spec/code alignment pass across Space / Wishlist / Admin error
  enums; defer to `spec-owner-decision` item list.
- **Shared spec-hygiene pass** — align W-5 (UNKNOWN_USER copy), W-6 (put vs.
  upload), W-8/W-9 (`app.ai.*` alias documentation), W-14 (ui.md → ui.mmd),
  W-18 (preferredStyle optional vs. required) in a dedicated
  `spec-hygiene-v2` sweep where artifact-file edits are permitted (this
  batch explicitly forbade them).
- **Dialect / CORS / CrossOrigin** — W-4, W-15 staged for a
  `production-hardening` task near launch.

## 4. Jest test cleanup summary

| Item | Status | Where |
|---|---|---|
| J-1 UploadScreen.test.tsx TS parameter-property syntax | **FIXED** in this session (earlier turn) | test file, not artifacts |
| J-2 ARPlacementScreen.test.tsx `type Handle` inside jest.mock | **FIXED** in this session (earlier turn) | test file, not artifacts |
| J-3 RecommendationScreen AC-45 duplicate "매칭 87%" | **INVESTIGATED + DEFERRED** per task instruction. Root cause: test fixture `makeItem()` default `fitScore=0.87` applied to 6 of 8 cards. Not a prod bug. Recommended fix shape documented above (J-3 row). |
| J-4 AnalyzingScreen AC-31 timeout | **INVESTIGATED + DEFERRED** — not a timeout; real `Received: 5, Expected: >= 6` schedule mismatch. Reverted tentative `jest.setTimeout(15000)` add after local run exposed true failure. |
| J-5 StyleSelectionScreen AC-28 timeout | **INVESTIGATED + DEFERRED** — not a timeout; `Found multiple elements with text: /Modern/`. Same revert as J-4. |
| J-6 / AC-29 ARPlacementScreen unload ref-null | **DEFERRED** per explicit task instruction. New task recommended. |

## 5. Metrics

| Metric | Count |
|---|---|
| Total warnings harvested | 50 |
| — CHEAP-COSMETIC (CC) | 8 |
| — CHEAP-CODE (CD) | 10 |
| — EXPENSIVE (EXP) | 10 |
| — DEFER-PRODUCT (DP) | 22 |
| Fixes applied in-session | 5 (J-1, J-2, W-29, W-33, W-42) |
| — of which already fixed earlier this session | 2 (J-1, J-2) |
| — new fixes this turn | 3 (W-29, W-33, W-42) |
| Deferred (all buckets combined) | 45 |
| — queued as new follow-up tasks | 5 (unload-fix-v2, recommendation-fixture-fix, V2SpacesMigrationTest, MISSING_USER_HEADER-refactor, spec-hygiene-v2) |
| — queued as product-decision | multiple (W-6 put/upload, W-14 ui.md rename, W-18 preferredStyle-required, W-39 AR loading copy, etc.) |
| — queued as production-hardening (dialect, CORS) | 2 |
| CRITICAL findings requiring orchestrator attention | 1 — J-6/AC-29 `unload` ref-null is a **real runtime bug** in shipped code at `ARPlacementScreen.tsx:200-217`; the verification report PASSED it only because static inspection matched the useEffect-cleanup shape. On a developer machine the unload message is never delivered, potentially leaking WebView/model-viewer resources between AR sessions. Schedule `AR-furniture-placement-unload-fix-v2` before production. |

---

**Summary**: 3 cheap cosmetic fixes landed (unused import, Javadoc drift,
HTML-comment grep noise) plus confirmed 2 prior-session test fixes. 1
CRITICAL production bug (AC-29 unload) surfaced but deferred per task
instruction. All CHEAP-CODE and EXPENSIVE warnings deferred with
recommended next-step task names. No artifact files modified. No V1–V7
migrations touched. No cross-task public contracts broken.
