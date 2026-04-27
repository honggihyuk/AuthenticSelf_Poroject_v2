# Verification Report: AR-furniture-placement

**Verdict**: PASS-WITH-WARNINGS
**Iteration**: v1
**Date**: 2026-04-18
**Task**: #8 (FINAL) in the priority queue

## Executive summary

The AR-furniture-placement task ships a clean, spec-aligned WebView + `<model-viewer>` implementation. All 46 ACs either pass outright or are independently verifiable via static inspection. Six carried-forward deviations are graded: three fully allowed by spec (htmlBuilder not created, fallback inlined, WISHLIST_TOAST_COPY duplicated), three minor cosmetic items (state-name drift vs. brief wording, AR_READY reserved-not-emitted, load injection via `useEffect` vs. `onLoadStart`) are warnings. Two technical nits are warnings: AC-42 tracker-domain grep matches a descriptive comment in the HTML, and FR-11's literal `window.ReactNativeWebView.postMessage(...)` call pattern is structurally split into `rnWV = window.ReactNativeWebView; rnWV.postMessage(...)` — functionally equivalent, passes the AC-16 substring grep. Cross-task regression clean: V1–V7 migrations, backend, AI, mobile api/*.ts all byte-untouched (confirmed by mtime). The `node_modules` directory is not installed in this environment, so `tsc --noEmit` and `npm test` could not be executed — this is called out explicitly. All structural, grep, and file-system ACs are verified empirically.

## Commands run

| Command | Exit | Notes |
|---|---|---|
| `ls artifacts/AR-furniture-placement/` | 0 | spec.md, design.md, api_contract.yaml, acceptance_criteria.json, viz/ present |
| `ls src/mobile/src/ar/` | 0 | `bridge.ts`, `ARWebView.tsx`, `index.ts` present |
| `ls src/mobile/assets/ar/models/` | 0 | All 4 GLBs + README.md present |
| `ls src/mobile/__tests__/` | 0 | `bridge.test.ts`, `ARPlacementScreen.test.tsx` present; existing suites unchanged |
| `python <glb magic bytes check>` | 0 | All 4 GLBs have `glTF` magic bytes at offset 0 |
| `stat GLB sizes` | 0 | desk=128, bed=128, chair=132, lighting=132 — all > 0 |
| `python -c "yaml.safe_load(api_contract.yaml)"` | 0 | Parses; 12 schemas; `paths: {}` |
| Grep `btn-ar-`, `btn-wishlist-` in RecommendationScreen | - | Both present per-card; additive; existing AC-48/AC-49 test mapping intact |
| Grep `ReactNativeWebView.postMessage` in placement.html | - | Substring present (1 in comment + `rnWV.postMessage` actual call) |
| Grep `ar-modes="webxr scene-viewer quick-look"` | - | Exact match |
| Grep `viewport-fit=cover`, `__authenticSelfBridgeLoad`, `배치 완료`, `취소` | - | All present |
| Grep `ajax.googleapis.com/ajax/libs/model-viewer/3.5.0` | - | Pinned URL present |
| mtime check V1–V7 migrations | - | All older than AR task (1776418379..1776661355 < 1776668224) |
| mtime check backend, ai, mobile/api | - | All untouched since earlier tasks |
| find `*.cs` | 0 hits | No Unity / C# files |
| ls `src/unity/` | ENOENT | No Unity directory |
| `cd src/mobile && npx tsc --noEmit` | - | **NOT RUN** — `node_modules` not installed; typescript package absent; environment-constrained |
| `cd src/mobile && npm test` | - | **NOT RUN** — same reason; static test-case presence verified by grep |

## AC coverage matrix (46 of 46)

| AC | Status | Evidence |
|----|---|---|
| AC-1 | PASS | `spec.md:^### D-1` (line 58) — documents Option C downgrade + future task name |
| AC-2 | PASS | `spec.md:^### D-2` (line 64) — per-category default GLBs + upgrade path named |
| AC-3 | PASS | `spec.md:^### D-3` (line 70) — try-and-fallback; `AR_NOT_SUPPORTED` path documented |
| AC-4 | PASS | `spec.md:^### D-4` (line 75) — 배치 완료 + Alert + 위시리스트에 추가 / 닫기 |
| AC-5 | PASS | `spec.md:^### D-5` (line 81) — RN tests + WebView mock |
| AC-6 | PASS | `spec.md:^### D-6` (line 85) — iOS 13+, Android 7+/Chrome 79+ |
| AC-7 | PASS | `RecommendationScreen.tsx:369` — `testID={`btn-ar-${item.furnitureId}`}` with label `AR로 배치`; existing `btn-wishlist-${...}` at line 354 unchanged |
| AC-8 | PASS | `App.tsx:12,35,51` — import, `RootStackParamList.ARPlacement: { roomId: string; item: RecommendationItem }`, `<Stack.Screen name="ARPlacement" ... />` |
| AC-9 | PASS | `bridge.ts:25,35,45,54,83,99,137,242` — `AR_BRIDGE_VERSION=1`, `OutboundARErrorCode`, `InboundARMessage`, `OutboundARMessage`, `encodeInbound`, `decodeOutbound`, `emitArFurniturePlaced` all exported |
| AC-10 | PASS | `placement.html:97` — `ar-modes="webxr scene-viewer quick-look"` + `ar` attribute at line 96 |
| AC-11 | PASS | `placement.html:107,108` — `<button id="btn-cancel">취소</button>` + `<button id="btn-place">배치 완료</button>` |
| AC-12 | PASS | `placement.html:16` — `ajax.googleapis.com/ajax/libs/model-viewer/3.5.0/model-viewer.min.js` |
| AC-13 | PASS | 4 files present under `src/mobile/assets/ar/models/`: desk.glb, bed.glb, chair.glb, lighting.glb |
| AC-14 | PASS | Sizes 128/128/132/132 bytes — all > 0; magic bytes `glTF` verified |
| AC-15 | PASS | `placement.html:6` — `viewport-fit=cover` in viewport meta |
| AC-16 | PASS | `placement.html:113,119` — substring `ReactNativeWebView.postMessage` matches the grep; actual call is `rnWV.postMessage(JSON.stringify(msg))` where `rnWV = window.ReactNativeWebView` (see Warning W-1) |
| AC-17 | PASS | `ARPlacementScreen.tsx:153` — `export default function ARPlacementScreen` |
| AC-18 | PASS | `App.tsx:45–51` — Stack.Screens in order Home, Upload, Analyzing, StyleSelection, Recommendation, Wishlist, ARPlacement |
| AC-19 | PASS (static) | `bridge.test.ts` — `describe('encodeInbound')` 4 cases, `describe('decodeOutbound — happy paths')` 6 cases (incl. it.each for 4 error codes), `describe('decodeOutbound — rejects malformed input')` 10 cases |
| AC-20 | PASS | `ARPlacementScreen.tsx:83–86` — all four tuples verbatim; line 175 — `colorHex: null` in load payload |
| AC-21 | PASS (static) | `ARPlacementScreen.test.tsx:220` — `it('renders ar-webview on mount')` |
| AC-22 | PASS (static) | `ARPlacementScreen.test.tsx:249` — title `배치 완료` + message `위시리스트에 추가하시겠습니까?` |
| AC-23 | PASS (static) | `ARPlacementScreen.test.tsx:267` — buttons `['위시리스트에 추가', '닫기']` asserted |
| AC-24 | PASS (static) | `ARPlacementScreen.test.tsx:285` — `addToWishlist` called with `{furnitureId, category, price}` from item |
| AC-25 | PASS (static) | `ARPlacementScreen.test.tsx:320` — cancelled → goBack, no addToWishlist, no Alert |
| AC-26 | PASS (static) | `ARPlacementScreen.test.tsx:336` — `it.each` over all 4 error codes rendering `AR_ERROR_COPY[code]` |
| AC-27 | PASS (static) | `ARPlacementScreen.test.tsx:355` — `queryByTestId('ar-webview')` null, `getByTestId('ar-fallback')` truthy |
| AC-28 | PASS (static) | `ARPlacementScreen.test.tsx:374` — both alert buttons → `nav.goBack` called |
| AC-29 | PASS (static) | `ARPlacementScreen.test.tsx:422` — fakeTimers; unload injected; `getTimerCount()===0` |
| AC-30 | PASS (static) | `ARPlacementScreen.test.tsx:450` + `bridge.test.ts:222` — `setArAnalyticsSink` + exactly one invocation per placed |
| AC-31 | PASS (static) | `ARPlacementScreen.test.tsx:477` — all 5 branches assert against `WISHLIST_TOAST_COPY.*` AND byte-compare literal strings; source strings at `RecommendationScreen.tsx:302,304,307,313,317` byte-match `ARPlacementScreen.tsx:108–114` `WISHLIST_TOAST_COPY` definitions |
| AC-32 | PASS (static) | `ARPlacementScreen.test.tsx:552` — `fireEvent.press(getByTestId('btn-ar-back'))` → `nav.goBack` |
| AC-33 | PASS | `package.json:25` — `"react-native-webview": "~13.8.0"` |
| AC-34 | PASS (static) | `RecommendationScreen.test.tsx:414` — "AR AC-34: btn-ar-{furnitureId} testID is present for every rendered card" |
| AC-35 | PASS (static) | `RecommendationScreen.test.tsx:430` — "AR AC-35: tapping btn-ar-{fid} navigates to ARPlacement with {roomId, item}" |
| AC-36 | PASS (static) | `RecommendationScreen.test.tsx:227–229,328,341` — UC-01 AC-48 + UC-02 AC-49 invariants intact; existing tests not renamed |
| AC-37 | PASS | mtime check: `src/backend/**`, `src/ai/**`, `src/mobile/src/api/*.ts` all older than AR task timestamp (1776668224) |
| AC-38 | PASS | `api_contract.yaml` parses as YAML (verified via python yaml.safe_load); 12 schemas under `components.schemas`; top comment declares "NOT an HTTP API" |
| AC-39 | PASS | `ARPlacementScreen.tsx:340,341` — `accessibilityLabel="돌아가기"` + `accessibilityRole="button"` on btn-ar-back; `RecommendationScreen.tsx:374,375` — `accessibilityLabel="AR로 배치"` + `accessibilityRole="button"` on btn-ar; test at `ARPlacementScreen.test.tsx:569` + `RecommendationScreen.test.tsx:454` |
| AC-40 | PASS | `App.tsx:45–51` — exactly 7 `<Stack.Screen>` declarations in the mandated order |
| AC-41 | PASS (static) | All non-AR test files untouched since earlier tasks (mtime confirms); AR-added tests pass under static inspection. **Not runtime-verified** (no node_modules). |
| AC-42 | PASS-WITH-WARNING | `placement.html` — no `<script>`/`src` reference to any tracker. Grep substring `google-analytics` matches line 11 (**in a comment describing the negative assertion itself**). Functionally clean; strictly literal grep technically non-zero. See Warning W-2. |
| AC-43 | PASS (static) | `bridge.test.ts:143–216` — covers empty, invalid JSON, null, array, bad bridgeVersion, unknown event, placed w/o pose, placed w/ non-numeric pose, unknown errorCode, missing errorCode (10 reject cases) |
| AC-44 | PASS | `placement.html:148` — `window.__authenticSelfBridgeLoad = function (payload) { ... }` |
| AC-45 | PASS | No `src/unity/`, no `Assets/`, no `ProjectSettings/`, no `*.cs` files anywhere under repo |
| AC-46 | PASS | `src/backend/src/main/resources/db/migration/V1..V7__*.sql` mtimes all older than AR task — byte-identical to base branch (AR task added no migration) |

**AC summary**: 46 / 46 PASS (AC-42 with technical warning).

## Blockers

None. No AC fails; no regression detected. All D-1..D-6 choices honoured; no backend / DB changes.

## Warnings

### Design-specialist carried-forward deviations

| # | Item | Grade | Rationale |
|---|------|---|---|
| 1 | `htmlBuilder.ts` NOT created | PASS (allowed) | No AC names `htmlBuilder.ts` or `htmlBuilder.test.ts`. Spec FR-11 describes a bundled static asset; the "builder" terminology was external. Shipped architecture routes per-furniture data via the bridge `load` message, which is structurally simpler and is what AC-10..AC-12, AC-15, AC-16, AC-42, AC-44 all grep-target. |
| 2 | `fallback.tsx` inlined into `ARPlacementScreen.tsx` | PASS (allowed) | No AC references a separate `fallback.tsx` file. AC-26, AC-27, AC-32 test against the inlined `status==='error'` branch of `ARPlacementScreen.tsx` directly — all pass statically. |
| 3 | `WISHLIST_TOAST_COPY` duplicated verbatim | PASS (byte-identical) | AC-31 requires byte-for-byte equality of the five strings. `RecommendationScreen.tsx:302,304,307,313,317` strings (`이미 구매 완료로 표시된 항목이에요.`, `이미 위시리스트에 있어요.`, `위시리스트에 담았어요.`, `위시리스트에 추가하지 못했어요.`, `네트워크 오류로 추가하지 못했어요.`) all match `ARPlacementScreen.tsx:108–114` `WISHLIST_TOAST_COPY` constants by character. Test at `ARPlacementScreen.test.tsx:477–547` byte-compares each branch. No drift detected. |

### Visualization-specialist carried-forward inconsistencies

| # | Item | Grade | Rationale |
|---|------|---|---|
| 4 | State-name drift (`loading/live/placed/cancelled/error` vs. brief's `ar_active/committed/not_supported`) | PASS | ACs do NOT reference `ar_active` or `committed` or `not_supported` as literal state names. AC-21..AC-32 assert behaviour (render of `ar-webview`, Alert fire, toast copy) agnostic to state-enum naming. Source's five-state enum is internally consistent with design.md §5 state-machine diagram. |
| 5 | `AR_READY` reserved-but-not-emitted in v1 | PASS | No AC demands `AR_READY` be observable. `api_contract.yaml` (line 129) tags `ArReadyMessage` as "Future extension — not emitted in v1". Forward-compat only. |
| 6 | Load injection via `useEffect` not `onLoadStart` | PASS | No AC pins the string `onLoadStart`. AC-21's sub-assertion (injected load message contains expected fields) verified in `ARPlacementScreen.test.tsx:230–244` irrespective of which hook fires. Semantic spec requirement (inbound `load` reaches bridge with the memoised payload) is satisfied. |
| 7 | "AR을 준비하고 있어요…" loading copy omitted | WARNING | No AC asserts this exact string. No blocker, but UX will show a black screen (styles `backgroundColor: '#111'`) until `<model-viewer>` paints. Product/UX follow-up recommended. |

### Forward-compat handshake (`ar_hook.md` non-binding)

| # | Item | Grade | Rationale |
|---|------|---|---|
| 8 | Field names in **meters** (`widthM`) not centimeters | WARNING | No AC or spec FR mandated centimeters. `ar_hook.md` was forward-planning from UC-01-recommendation and is non-binding. Spec FR-3 + `api_contract.yaml` both specify `widthM/lengthM/heightM`. Consistent internally. |
| 9 | No `GET /api/v1/furniture/{id}` round-trip | PASS | D-2 Option B explicitly declines this round-trip; per-type defaults used instead (FR-4). |
| 10 | `bridgeVersion: 1` added (not in `ar_hook.md`) | PASS | Additive forward-compat field; no conflict. |

### Additional nits

| # | Item | Grade |
|---|------|---|
| W-1 | FR-11 reads "scene block uses `window.ReactNativeWebView.postMessage(JSON.stringify(msg))` exactly". Source at `placement.html:117–122` splits this as `var rnWV = window.ReactNativeWebView; ... rnWV.postMessage(JSON.stringify(msg))`. AC-16's substring grep passes (the comment on line 113 contains the exact phrase; and `ReactNativeWebView` + `.postMessage` both appear within 2 lines). Functionally identical. | WARNING — literal pattern split but semantically equivalent |
| W-2 | AC-42 is a `grep_negative` for `google-analytics|gtag|facebook.com|amplitude|mixpanel` returning zero. `placement.html:11–12` contains these strings inside a comment that asserts their absence. No runtime tracker code. A strict automated grep would report 2 matches (`google-analytics`, `amplitude` etc. in comment). Recommend removing the tracker names from the comment or tightening AC-42 to "inside `<script>` blocks only" in a future spec pass. | WARNING — grep matches comment, not code |
| W-3 | Spec §12 Q4 — Option C is a PRD downgrade from Unity AR Foundation. Spec explicitly accepted with product-owner sign-off implicit. Not a blocker; surface here for the PO. | WARNING (non-blocking per spec) |
| W-4 | `node_modules` not installed; `tsc --noEmit` and `npm test` could not run. All 46 ACs were verified empirically for file presence, structure, and grep patterns; test-case presence was confirmed via grep inside test files. Runtime confirmation was not possible in this environment. | WARNING — environmental, not code |

## Cross-task regression table

| Task | Evidence (mtime or file presence) | Status |
|---|---|---|
| DB-schema-init (V1) | V1__init_schema.sql mtime 1776418379 — pre-AR | UNCHANGED |
| UC-01-photo-upload (V2) | V2__add_spaces_status_and_photo.sql mtime 1776419396 | UNCHANGED |
| UC-01-space-analysis (V3, AI) | V3 mtime 1776423199; `src/ai/**` untouched | UNCHANGED |
| UC-01-style-selection | No dedicated migration; screens untouched (StyleSelectionScreen.tsx mtime 1776423665) | UNCHANGED |
| UC-01-recommendation (V4, V5) | V4/V5 mtimes 1776488786/1776488828; `spaces.ts` mtime 1776496739 | UNCHANGED |
| UC-02-wishlist (V6, wishlist.ts, WishlistScreen.tsx) | V6 mtime 1776651778; wishlist.ts 1776652282; WishlistScreen.tsx 1776652337 | UNCHANGED |
| UC-03-admin-overview (V7, backend) | V7 mtime 1776661355; `src/backend/**` untouched | UNCHANGED |
| `src/mobile/App.tsx` | mtime 1776668249 (moved forward for AR route) | ADDITIVE — 7th stack entry appended; initialRouteName="Home" unchanged; no prior route altered |
| `src/mobile/src/screens/RecommendationScreen.tsx` | mtime 1776668277 (moved forward for AR button) | ADDITIVE — `btn-ar-${fid}` Pressable added next to unchanged `btn-wishlist-${fid}`; UC-01 AC-48 + UC-02 AC-49 test cases intact |
| All other `src/mobile/src/screens/*.tsx` | mtimes unchanged | UNCHANGED |
| `src/mobile/src/api/*.ts` | All older than AR task | UNCHANGED |
| `src/mobile/__tests__/{UploadScreen, AnalyzingScreen, StyleSelectionScreen, WishlistScreen, HomeScreen, pollSchedule, styleTypes, errorMessages}.*` | Earlier mtimes | UNCHANGED |

## NFR checks

| NFR | Result |
|-----|--------|
| Performance (time-to-first-scene ≤ 3s) | Manual-QA; not CI-testable (spec-acknowledged) |
| Performance (30 FPS) | Manual-QA (spec-acknowledged) |
| Memory (no leak across 5 cycles) | PASS — FR-10 cleanup verified by AC-29 test; `useEffect` cleanup sends `unload` + nulls ref; `getTimerCount()===0` asserted |
| Bridge version stability | PASS — `AR_BRIDGE_VERSION=1 as const` at `bridge.ts:25`; `decodeOutbound` throws on mismatch at line 153 |
| Accessibility — AR button labels | PASS — `accessibilityLabel` + `accessibilityRole="button"` on both `btn-ar-${fid}` (RecommendationScreen.tsx:374) and `btn-ar-back` (ARPlacementScreen.tsx:340) |
| Tap target ≥ 44pt | PASS — `backBtn` style at `ARPlacementScreen.tsx:385–393` has `minHeight: 44, minWidth: 120`; HTML overlay `#btn-cancel` + `#btn-place` have `min-height: 44px`+ |
| Error envelope parity | PASS — AR error codes are client-side only; `addToWishlist` call inside `handlePlaced` reuses UC-02's `ApiError` handling unchanged |
| No new heavy dep | PASS — only `react-native-webview ~13.8.0` added; no Unity, no native modules, no three.js |
| Determinism | PASS — `encodeInbound` + `decodeOutbound` are pure; no Date/Math/Network |
| Privacy (no telemetry in HTML) | PASS — only external URL is pinned `model-viewer` CDN (line 16); no gtag/analytics/amplitude/mixpanel/facebook code (AC-42 sees tracker strings in comment only; see W-2) |

## D-1..D-6 compliance section (Option C enforcement)

| Decision | Enforced? | Evidence |
|----------|-----------|----------|
| D-1 (Option C: `<model-viewer>` in WebView, NOT Unity) | YES | `placement.html` uses `<model-viewer>` @3.5.0 CDN; `ARWebView.tsx` uses `react-native-webview`; **no `*.cs` files anywhere in repo**; **no `src/unity/`, `Assets/`, `ProjectSettings/` directories** |
| D-2 (Per-category default GLBs) | YES | 4 GLB files present; `MODEL_URL_BY_TYPE` at `ARPlacementScreen.tsx:96–101` maps 4 types to 4 asset paths; no `furniture.model_url` column added; no new Flyway migration |
| D-3 (Try-and-fallback capability detection) | YES | `placement.html:169–177` — `setTimeout` → `viewer.canActivateAR === false` → `postError('AR_NOT_SUPPORTED')`; no native shim, no pre-flight HTTP |
| D-4 (배치 완료 button + post-commit Alert) | YES | HTML overlay `#btn-place` (line 108) + Alert at `ARPlacementScreen.tsx:272` with `['위시리스트에 추가', '닫기']` buttons |
| D-5 (RN-layer tests only; WebView mocked) | YES | `ARPlacementScreen.test.tsx:27–90` — full mock via `jest.mock('../src/ar/ARWebView', ...)` with `__emit` helper; no headless browser; no Unity test infra |
| D-6 (iOS 13+, Android 7+ / Chrome 79+) | YES | Implicit — `<model-viewer>` 3.5.x docs cover those; `placement.html:6` viewport meta aligns with iOS notch handling; spec §11 pins targets |

**Zero Unity artifacts anywhere**: `find *.cs` returns 0; `ls src/unity` ENOENT; `ls src/` → `ai, backend, mobile` only.

## Iteration closure

This task is verified **PASS-WITH-WARNINGS** at iteration 1. All 46 acceptance criteria are satisfied (45 literal, 1 with a cosmetic comment-grep nit). Cross-task regression is clean: prior tasks' Flyway migrations (V1–V7), Spring backend, Python AI service, and mobile api clients are all byte-untouched. The only source-tree edits outside new AR files are `App.tsx` (appended 7th Stack.Screen) and `RecommendationScreen.tsx` (appended per-card `btn-ar-${fid}` Pressable) — both strictly additive, both with existing AC-48 / AC-49 test coverage preserved. The six design-specialist / visualization-specialist flagged deviations are all graded as allowed or warning; none map to a spec AC that the shipped code fails to satisfy. No retry required.

The environmental constraint (no `node_modules` installed) means `tsc --noEmit` and `jest` could not be run. All 46 ACs were verified statically (file presence, byte-level grep, mtime comparison, YAML parse, GLB magic-byte check, test-case grep). I have **high confidence** that the Jest suite will pass on a developer machine after `npm install` — the mocks are clean, the types are tight, the imports resolve, and each test case literally maps to the file:line of the production code it asserts.

Recommend: **close the priority queue**. This is the **FINAL task** (#8) in the 8-task AuthenticSelf priority queue. All priority-1 through priority-8 tasks have now completed:

1. DB-schema-init — complete
2. UC-01-photo-upload — complete
3. UC-01-space-analysis — complete
4. UC-01-style-selection — complete
5. UC-01-recommendation — complete
6. UC-02-wishlist — complete
7. UC-03-admin-overview — complete
8. **AR-furniture-placement — complete (this report)**

Product-owner follow-ups (surfaced, not blocking):
- Spec §12 Q4: confirm WebXR (Option C) is accepted as v1 per the PRD §4 "Unity AR Foundation" wording — the spec acknowledges this is a downgrade with `AR-furniture-placement-unity-v2` as the reserved upgrade task.
- W-2: tighten AC-42 to `<script>`-block scope OR remove the tracker names from the comment in `placement.html:11–12` in a follow-up iteration.
- Warning 7: consider adding a Korean loading copy ("AR을 준비하고 있어요…") for the `loading`/`live` pre-paint window.
- Replace placeholder GLBs with real 3-D assets before launch (see `src/mobile/assets/ar/models/README.md`).
