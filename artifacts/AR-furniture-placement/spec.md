# Task Spec: AR-furniture-placement

## 1. Goal
Close PRD §4 ("Unity AR / AR Foundation 뷰") and the UC-01 "미리보기" aspiration referenced in §6 by adding an in-app AR placement surface reachable from `RecommendationScreen`: (a) a new RN route `ARPlacementScreen` that receives `{roomId, furnitureId}` and renders a `<WebView>`-hosted scene using Google's `<model-viewer>` (with WebXR-on-supported-devices, 3D-viewer-on-others) so a user can "place" a chosen recommendation on the detected floor and, on confirmation, either return to the recommendation list or hand off to the existing UC-02 wishlist-add flow; (b) a minimal RN↔scene message bridge (typed, versioned) whose incoming payload is `{furnitureId, modelUrl, roomDimensions, bridgeVersion}` and whose outgoing events are `placed | cancelled | error`; (c) a capability-fallback strategy (try-and-fallback per D-3) that renders a Korean-text fallback panel with a "돌아가기" button when the scene reports `AR_NOT_SUPPORTED | AR_CAMERA_DENIED | AR_MODEL_LOAD_FAILED | AR_SESSION_ERROR`; (d) zero backend migration and zero admin/UC-01/UC-02 endpoint mutation (strictly additive per D-2). The "AR로 배치" button is a net-new per-card affordance on `RecommendationScreen` whose addition is additive and regression-tested against UC-01 AC-48 and UC-02 AC-49.

## 2. Source (PRD section)
- **PRD §4 기술 스택 — "Unity AR Foundation"**. This spec implements the AR surface per D-1 Option C (`<model-viewer>` embedded in a WebView hosted by RN) rather than a true Unity-as-a-Library build. Rationale in §5 D-1.
- **PRD §6 UC-01 기본 흐름 (미리보기 aspiration)** — the PRD text suggests showing recommended furniture in the user's space; this task is that preview surface.
- **PRD §9 시스템 아키텍처 — 모바일(React Native + Unity)**. The diagram shows Unity as the 3-D preview layer; this task preserves the architectural *role* (3-D preview owned by a sandboxed rendering subsystem reachable via a message bridge from RN) while choosing a lower-friction runtime per D-1.
- **PRD §7 클래스 다이어그램** — `Furniture` has `type, style, size`. The AR scene reads `widthCm/lengthCm/heightCm/colorHex` from the catalog row (already persisted by UC-01-recommendation V4/V5) — via the `GET /api/v1/furniture/{furnitureId}` endpoint that UC-01-recommendation's `ar_hook.md` §1 reserved for this task. (This task does NOT add that endpoint — see D-2; the catalog info already reaches RN through the `RecommendationItem` record plus the `widthCm/lengthCm/heightCm/colorHex` columns that are *already* in the recommendation response. The AR client reads straight from the in-memory `RecommendationItem` passed via nav params — no new GET round-trip.)
- **PRD §6 UC-02 steps 2–3** — the wishlist add flow re-used from the AR placement confirmation screen (no new contract; AR confirm simply calls `addToWishlist(...)` from `src/mobile/src/api/wishlist.ts`).

## 3. Actors & Preconditions
- **Primary actor (user)**: an authenticated RN client user who has already reached `RecommendationScreen` with at least one non-empty category.
- **Primary actor (mobile)**: a new React Native screen `ARPlacementScreen.tsx` under `src/mobile/src/screens/` that embeds a `<WebView>` rendering a bundled HTML asset, plus a new "AR로 배치" `Pressable` added to every `ItemCard` on `RecommendationScreen` (additive — no existing test-ID is removed).
- **Secondary actor (bridge)**: a typed, versioned message-bridge module `src/mobile/src/ar/bridge.ts` that marshals `InboundARMessage` / `OutboundARMessage` through `window.ReactNativeWebView.postMessage` ↔ `<WebView>.injectJavaScript`.
- **Tertiary actor (HTML scene)**: a bundled asset `src/mobile/assets/ar/placement.html` that loads `<model-viewer>` (pinned version — see FR-11), interprets an inbound `load` message, renders the GLB, and posts `placed` / `cancelled` / `error` back.
- **Preconditions**:
  - Tasks 1–7 merged. UC-01-recommendation's `RecommendationItem` shape is stable (type: `FurnitureType`; dimensional data is NOT present on the item — only on the catalog row). See D-2 for how this task gets dimensions without a new backend call.
  - UC-02-wishlist's `addToWishlist(...)` client is importable from `src/mobile/src/api/wishlist.ts` unchanged.
  - The RN app already has `react-native-webview` OR will add it as a direct dependency (FR-12). Version pinned in spec.
  - Navigation stack in `src/mobile/App.tsx` does NOT already contain an `ARPlacement` route (design-specialist adds it additively).
  - Dev `settings.userId` stub still satisfies UC-02's `X-User-Id` header for any wishlist call made from AR.

## 4. In-scope / Out-of-scope

### In scope
- New RN route `ARPlacement` with typed params `{roomId: string, item: RecommendationItem}`.
- New "AR로 배치" button on every `ItemCard` in `RecommendationScreen` (additive, per-card).
- Bundled HTML + JS asset implementing the scene via `<model-viewer>` (WebXR AR on supported devices; interactive 3-D viewer fallback on others).
- Typed message bridge (`InboundARMessage` / `OutboundARMessage`) exported from `src/mobile/src/ar/bridge.ts` with a mandatory `bridgeVersion: 1` field on every message.
- Try-and-fallback capability detection per D-3: always navigate; the scene reports `AR_NOT_SUPPORTED` via an outbound `error` message; RN renders a Korean fallback panel with a "돌아가기" button.
- Placement confirmation flow per D-4: an on-screen "배치 완료" button in the HTML overlay triggers the `placed` outbound event with `{pose: {x,y,z,yaw}}`; RN navigates back to `RecommendationScreen` AND opens an Alert (iOS) / Toast (Android) offering "위시리스트에 추가" — tapping "예" calls the existing `addToWishlist(...)` client. (See FR-8 for exact UX.)
- Error handling: any outbound `error` event surfaces a Korean message keyed by error code and re-shows the fallback panel with "돌아가기".
- Scene teardown: on navigation away (back press, explicit "돌아가기", or unmount), the screen sends an inbound `unload` message and the WebView is unmounted cleanly — verified by FR-10 and AC-29.
- A dedicated test suite `src/mobile/__tests__/ARPlacementScreen.test.tsx` + `src/mobile/__tests__/bridge.test.ts` that mocks the WebView and asserts every branch of the state machine (load / placed / cancelled / each error code / teardown).
- Small "AR placement-related copy" surface added to `src/mobile/src/api/errorMessages.ts` OR a new sibling `src/mobile/src/ar/errorMessages.ts` (design-specialist picks one; spec mandates Korean copy keyed by error code).

### Out of scope
- Full Unity-as-a-Library integration (`@azesmway/react-native-unity` or equivalent) — deferred. D-1 picks Option C.
- Photorealistic lighting / IBL / shadow catcher — `<model-viewer>` default lighting only.
- Multi-furniture simultaneous placement — one item per AR session.
- Persistent placement across sessions (Cloud Anchors, saved scenes).
- Collision detection with real-world objects beyond the floor plane.
- Measurement / ruler overlays.
- Model upload by users (catalog is read-only; AR consumes resolved model URLs only).
- Shared / collaborative AR.
- Admin analytics of placements (`UC-03-admin-overview` does not consume AR data).
- Any new backend endpoint, new DB migration, new controller, new service, or new Python route.
- Modification of `App.tsx` beyond registering a single new `ARPlacement` route. All other routes untouched.
- Modification of `RecommendationScreen.tsx` beyond adding the new per-card button (additive; existing `btn-wishlist-*` testIDs stay).
- Modification of any file under `src/backend/`, `src/ai/`, `src/mobile/src/api/spaces.ts`, `src/mobile/src/api/wishlist.ts`, or `src/mobile/src/api/client.ts`.
- Any Flyway migration V1..V7 — they are immutable by cross-task regression guard (AC-41).
- Real payment / checkout flow (same as UC-02).

## 5. Decisions (D-1 … D-6)

### D-1 — RN↔scene embedding strategy
**Chosen: Option C — `<model-viewer>` (Google, MIT-licensed) hosted inside `react-native-webview`.**
- Rationale: Option A (UaaL) requires Unity 2022 LTS + AR Foundation + a CI pipeline that can `unity -batchmode -runTests`, which is infrastructure this repo does not have and which inflates this iteration's scope past a single loop. Option B (deep-link to a standalone Unity APK/IPA) makes the RN↔scene contract fragile (URL-scheme races, sandbox issues) and doubles the build surface. Option C delivers a product-quality "place a recommended item on a floor plane" experience on every device via `<model-viewer>`'s auto-detected WebXR-on-capable-devices-+-interactive-3-D-fallback-otherwise behavior, requires zero native-module surgery, and is unit-testable end-to-end at the RN layer via a mocked WebView.
- Consequence: "AR" in this iteration means **"WebXR-on-capable-devices, interactive 3-D viewer on others"**. This is an explicit product downgrade versus the PRD's literal "Unity AR Foundation" wording — documented here and re-asserted in AC-1 so reviewers cannot miss it. A future task `AR-furniture-placement-unity-v2` is reserved to swap the embedding strategy to UaaL when CI supports it; the bridge message contract (FR-3) is designed to survive that swap unchanged.
- Pinned deps: `react-native-webview@13.x` (already-in-RN-ecosystem, BSD-compatible), `@google/model-viewer@3.5.x` (via CDN `<script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/3.5.0/model-viewer.min.js">` pinned to exact version in the bundled HTML — CDN URL is the one officially published by Google; the spec pins the version string verbatim and AC-12 grep-verifies it).

### D-2 — Model source
**Chosen: Option B — per-category default GLB models, four in total.**
- Rationale: zero backend change (no new column, no new migration, no new endpoint), zero data-seed coupling, fully offline-testable. Each of the four `FurnitureType` values (`desk | bed | chair | lighting`) maps to one bundled GLB asset at `src/mobile/assets/ar/models/{desk|bed|chair|lighting}.glb`. The asset is scaled uniformly by the scene using the `RecommendationItem.scoreBreakdown` + item type (dimension data is NOT in the recommendation item, so scaling uses a **per-type default dimension tuple** documented in FR-4). Upgrade path to Option A (a new `furniture.model_url` V8 column) is reserved for `AR-furniture-placement-unity-v2`; the bridge's `modelUrl` field already accommodates the future case.
- Consequence for the bridge: `modelUrl` is resolved RN-side from `item.type` → `../assets/ar/models/{type}.glb`, marshalled through the bridge as an absolute `file://` URI or `asset://` URI depending on platform (design-specialist picks and documents).
- Placeholder asset policy: because committing real 3-D GLB binaries is heavy, design-specialist MAY commit **four 1-KiB placeholder GLB files** (valid header, empty scene) plus a `README.md` in `src/mobile/assets/ar/models/` stating "replace with production assets before launch". AC-13 asserts presence of all four files, AC-14 asserts file size > 0 bytes (placeholder is fine; production blocker is out-of-scope for this iteration).

### D-3 — AR availability detection
**Chosen: Option B — try-and-fallback.**
- Rationale: zero native module, zero backend call. The scene loads in the WebView and the HTML script attempts `document.querySelector('model-viewer').canActivateAR` (the `<model-viewer>` API surface for WebXR capability). On `false` OR any init exception, the HTML posts `{event:"error", errorCode:"AR_NOT_SUPPORTED"}` upstream via `window.ReactNativeWebView.postMessage`. The RN screen catches the message, hides the WebView, and renders the fallback panel.
- Consequence: on devices that DO support WebXR (ARCore / ARKit browsers per D-6), the user sees a real floor-plane AR session. On devices that don't, they see the fallback Korean copy + "돌아가기". The interactive 3-D viewer (non-AR) IS still rendered under-the-hood by `<model-viewer>` — FR-7 specifies whether the fallback panel hides it outright (chosen: yes, to keep the UX unambiguous on v1; the 3-D viewer affordance is a future knob).

### D-4 — Placement confirmation flow
**Chosen: "배치 완료" floating button inside the HTML scene overlay.**
- UI: a fixed-position button at bottom-center of the HTML scene, Korean label "배치 완료". Tapping emits `{event:"placed", pose:{x,y,z,yaw}, bridgeVersion:1}`. A secondary "취소" button at top-left emits `{event:"cancelled", bridgeVersion:1}` and returns to `RecommendationScreen` without any further prompt.
- Post-commit behaviour: RN navigates back to `RecommendationScreen` AND surfaces an `Alert.alert(...)` (cross-platform) with title "배치 완료" and two buttons: "위시리스트에 추가" / "닫기". Tapping "위시리스트에 추가" invokes `addToWishlist(...)` using the existing UC-02 client (with the `furnitureId`, `category=item.type`, `price=item.price` taken from the same item that drove the AR session). The result surfaces the same toast set as `RecommendationScreen.onAddToWishlist` ("위시리스트에 담았어요.", "이미 위시리스트에 있어요.", etc.) — we literally reuse the helper where possible (FR-9).
- No screenshot-to-gallery affordance this iteration (out of scope).

### D-5 — Test strategy
**Chosen: Option C — RN-layer tests only, mock the WebView.**
- Rationale: deterministic, runs in CI, covers every branch of the state machine. Because D-1 picked Option C, there is no Unity C# code to unit-test — the HTML scene is static asset code which is grep-verifiable for structural presence (CDN URL, version, overlay buttons, postMessage payload shape) but not subject to headless-browser JS execution in this iteration. AC-10 and AC-11 assert the HTML's structural content via grep; AC-21..AC-32 assert RN behaviour via `@testing-library/react-native` with a mocked `<WebView>` component that exposes an `emit(msg)` method to simulate scene→RN events.

### D-6 — Platform targets
**Chosen: iOS 13+ (Safari WebView) and Android 7+ (Chrome WebView ≥ 79).**
- Rationale: matches `<model-viewer>` 3.5.x documented support matrix. WebXR AR quick-look on iOS uses AR Quick Look (ARKit-backed); WebXR AR on Android uses ARCore via Chrome. Devices below these thresholds transparently fall through to the interactive 3-D viewer, then to the Korean fallback panel if even that fails.
- The HTML scene MUST declare `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">` (AC-15).
- Both platforms reach the scene through the same bundled HTML — no platform-specific HTML branching.

## 6. Functional Requirements

### Mobile — entry point

**FR-1 (Per-card "AR로 배치" button on `RecommendationScreen`)** — Inside `ItemCard` (defined in `src/mobile/src/screens/RecommendationScreen.tsx`), add a second `Pressable` next to the existing `btn-wishlist-{furnitureId}` Pressable. Requirements:
- TestID: `btn-ar-{furnitureId}`.
- Label: `AR로 배치`.
- `onPress`: `navigation.navigate('ARPlacement', { roomId, item })` where `navigation` is obtained via `useNavigation()` or threaded as a prop (design-specialist picks; the existing `ItemCard` does not currently have `navigation` — a prop must be threaded from the parent or a `useNavigation()` hook added). The spec picks **prop-threaded** for parity with the existing `roomId` prop threading.
- Additive: the existing `btn-wishlist-{furnitureId}` Pressable, its `onAddToWishlist` handler, and AC-48 / AC-49 regressions MUST remain green (AC-36).
- No new dependency; no re-order of the existing `Pressable`.

### Mobile — new route

**FR-2 (Register `ARPlacement` route)** — In `src/mobile/App.tsx`, extend `RootStackParamList` with:
```ts
ARPlacement: { roomId: string; item: RecommendationItem };
```
and register `<Stack.Screen name="ARPlacement" component={ARPlacementScreen} options={{ title: 'AR 배치' }} />`. No other route is modified; no `initialRouteName` change; the `Home | Upload | Analyzing | StyleSelection | Recommendation | Wishlist | ARPlacement` order is mandated by AC-18 (append-at-end, after `Wishlist`).

**FR-3 (Message bridge — types)** — Create `src/mobile/src/ar/bridge.ts` exporting:
```ts
export const AR_BRIDGE_VERSION = 1 as const;

export type InboundARMessage =
  | {
      bridgeVersion: typeof AR_BRIDGE_VERSION;
      event: 'load';
      furnitureId: string;
      modelUrl: string;
      roomDimensions: { widthM: number; lengthM: number; heightM: number } | null;
      colorHex: string | null;
    }
  | { bridgeVersion: typeof AR_BRIDGE_VERSION; event: 'unload' };

export type OutboundARErrorCode =
  | 'AR_NOT_SUPPORTED'
  | 'AR_CAMERA_DENIED'
  | 'AR_MODEL_LOAD_FAILED'
  | 'AR_SESSION_ERROR';

export type OutboundARMessage =
  | {
      bridgeVersion: typeof AR_BRIDGE_VERSION;
      event: 'placed';
      pose: { x: number; y: number; z: number; yaw: number };
    }
  | { bridgeVersion: typeof AR_BRIDGE_VERSION; event: 'cancelled' }
  | {
      bridgeVersion: typeof AR_BRIDGE_VERSION;
      event: 'error';
      errorCode: OutboundARErrorCode;
      message?: string;
    };

export function encodeInbound(msg: InboundARMessage): string;
export function decodeOutbound(raw: string): OutboundARMessage;
```
- `encodeInbound` JSON-stringifies the message.
- `decodeOutbound` JSON-parses and validates: rejects (throws) if `bridgeVersion !== 1`, or `event` is not one of the three, or required fields for the event are missing. AC-19 exercises every branch.
- No runtime schema library — hand-rolled narrowing (same convention as `spaces.ts`).

**FR-4 (Dimension resolution for the scene)** — `ARPlacementScreen` resolves the dimensions passed to the bridge **without a backend call**:
- Per-type default dimension tuple (hard-coded in the screen module):
  - `desk`: `{widthM: 1.20, lengthM: 0.60, heightM: 0.74}`
  - `bed`: `{widthM: 1.60, lengthM: 2.00, heightM: 0.45}`
  - `chair`: `{widthM: 0.55, lengthM: 0.55, heightM: 0.90}`
  - `lighting`: `{widthM: 0.30, lengthM: 0.30, heightM: 1.50}`
- These defaults are an explicit approximation. AC-20 grep-asserts the four tuples verbatim. A comment in the screen explains the upgrade path to real catalog dimensions (future `furniture.model_url` column or `GET /api/v1/furniture/{id}` round-trip).
- `colorHex` passed to the scene is `null` for this iteration (placeholder GLB has its own baked material). AC-20 asserts `colorHex: null` in the inbound `load` message.

### Mobile — `ARPlacementScreen`

**FR-5 (Screen shell)** — Create `src/mobile/src/screens/ARPlacementScreen.tsx`:
- Reads `route.params` (`roomId`, `item`) and `navigation` from `NativeStackScreenProps<RootStackParamList, 'ARPlacement'>`.
- Internal state: `status ∈ 'loading' | 'live' | 'placed' | 'cancelled' | 'error'`; `errorCode: OutboundARErrorCode | null`.
- Initial render: a `<View>` hosting a `<WebView>` element sourced from the bundled `placement.html` asset via `source={{ uri: placementHtmlUri }}`, where `placementHtmlUri` is resolved by the design-specialist's bundling strategy (platform-specific `file://` or `asset://`).
- `onLoadStart`: sends the inbound `load` message via `webviewRef.current?.injectJavaScript(...)` with the bridge-encoded JSON payload, wrapped in a JS handler the HTML scene pre-installs. TestID on the WebView: `ar-webview`.
- `onMessage`: parses via `decodeOutbound`; routes to a handler per event (FR-6..FR-8).
- `onBack` / `navigation.beforeRemove`: sends the `unload` inbound message, then allows navigation to proceed (FR-10).

**FR-6 (Error handling path)** — When the screen receives `{event:'error', errorCode:<C>}`:
- Set `status='error'` and `errorCode=<C>`.
- Hide the WebView (conditional render, not merely opacity=0 — AC-27 asserts the `ar-webview` testID disappears).
- Render a fallback panel with testID `ar-fallback` containing:
  - A Korean message keyed by `errorCode`:
    - `AR_NOT_SUPPORTED` → "이 기기에서는 AR 미리보기가 지원되지 않습니다."
    - `AR_CAMERA_DENIED` → "카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요."
    - `AR_MODEL_LOAD_FAILED` → "3D 모델을 불러오지 못했습니다. 네트워크를 확인해주세요."
    - `AR_SESSION_ERROR` → "AR 세션에 문제가 발생했습니다. 다시 시도해주세요."
  - A `Pressable` with testID `btn-ar-back` and label "돌아가기" whose `onPress` calls `navigation.goBack()`.
- The four copies above MUST be defined as exported constants (e.g. `AR_ERROR_COPY: Record<OutboundARErrorCode, string>`) so tests can match by reference, not by substring. Verified by AC-26.

**FR-7 (Placed event handler)** — When the screen receives `{event:'placed', pose}`:
- Set `status='placed'`.
- Fire-and-forget analytics stub (optional; design-specialist may emit `ar_furniture_placed` event via a sink identical in shape to UC-01's `emitWishlistAddClicked`, named `emitArFurniturePlaced(roomId, furnitureId, pose)`, exported from `src/mobile/src/ar/bridge.ts` OR from `src/mobile/src/api/spaces.ts` — pick one; AC-30 grep-asserts the function exists and is called exactly once per placed event).
- Show an `Alert.alert(...)` with:
  - Title: `배치 완료`.
  - Message: `위시리스트에 추가하시겠습니까?`.
  - Buttons (cross-platform): `[{ text: '위시리스트에 추가', onPress: ... }, { text: '닫기', style: 'cancel' }]`.
  - On "위시리스트에 추가" press: call `addToWishlist({baseUrl: settings.apiBaseUrl, userId: settings.userId, furnitureId: item.furnitureId, category: item.type, price: item.price})` — identical call shape to `RecommendationScreen.onAddToWishlist` (FR-9 mandates shared logic).
  - On alert dismissal (both buttons), always: `navigation.navigate('Recommendation', {roomId})`. This returns the user to the list. If `goBack()` would yield the same result (route stack is `Home > Recommendation > ARPlacement`), `navigation.goBack()` is acceptable — design-specialist picks. AC-28 asserts post-dismissal the screen is unmounted.

**FR-8 (Cancelled event handler)** — When the screen receives `{event:'cancelled'}`:
- Set `status='cancelled'`.
- Immediately `navigation.goBack()`. No Alert, no toast. AC-25 asserts this.

**FR-9 (Wishlist reuse — zero duplication)** — The AR placement's post-commit wishlist-add MUST call `addToWishlist(...)` from `src/mobile/src/api/wishlist.ts`. The failure toasts (`네트워크 오류로 추가하지 못했어요.`, `위시리스트에 추가하지 못했어요.`, `이미 위시리스트에 있어요.`, `이미 구매 완료로 표시된 항목이에요.`, `위시리스트에 담았어요.`) MUST match the strings already used by `RecommendationScreen.ItemCard.onAddToWishlist` byte-for-byte. Design-specialist MAY extract a shared helper `addToWishlistWithToast(...)` into a small module (e.g. `src/mobile/src/ar/wishlistShim.ts` OR `src/mobile/src/screens/helpers/wishlistAdd.ts`) OR inline-duplicate the logic; either is acceptable. AC-31 asserts the toast strings match.

**FR-10 (Scene teardown)** — On `navigation.beforeRemove` event (React Navigation's unmount hook) OR on unmount via `useEffect` cleanup, the screen MUST:
1. Send the `{event:'unload', bridgeVersion:1}` inbound message to the WebView via `injectJavaScript`.
2. Clear any pending Alert handler references.
3. Null the `webviewRef`.
- The teardown MUST NOT throw even if the WebView is already unmounted. AC-29 simulates back-nav + checks that no pending timers or listeners remain (via `jest.useFakeTimers()` + `expect(jest.getTimerCount()).toBe(0)`).

### Mobile — HTML scene

**FR-11 (Bundled HTML asset)** — Create `src/mobile/assets/ar/placement.html`. Requirements (structural; no runtime JS-execution assertion in this iteration):
- Pinned `<model-viewer>` CDN include: `<script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/3.5.0/model-viewer.min.js"></script>`. AC-12 greps for this exact URL.
- `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">` (AC-15).
- A `<model-viewer id="viewer" ar ar-modes="webxr scene-viewer quick-look" camera-controls touch-action="pan-y" style="width:100vw;height:100vh">` element. AC-10 greps for the `ar` attribute and the three `ar-modes` keywords.
- An overlay `<div id="overlay">` containing:
  - A `<button id="btn-place">배치 완료</button>`.
  - A `<button id="btn-cancel">취소</button>`.
  - AC-11 greps for both Korean strings verbatim.
- An inline `<script>` block that:
  - Installs `window.__authenticSelfBridgeLoad` (the inbound hook RN calls via `injectJavaScript`).
  - On `load`: sets `<model-viewer src={modelUrl}>`; on model load error dispatches `{event:'error',errorCode:'AR_MODEL_LOAD_FAILED'}`; on `canActivateAR === false` dispatches `{event:'error',errorCode:'AR_NOT_SUPPORTED'}`.
  - `btn-place.onclick`: posts `{bridgeVersion:1,event:'placed',pose:{x:0,y:0,z:0,yaw:0}}` (v1 allows a static pose; upgrading to a real pose is a future task).
  - `btn-cancel.onclick`: posts `{bridgeVersion:1,event:'cancelled'}`.
- The `<script>` block uses `window.ReactNativeWebView.postMessage(JSON.stringify(msg))` exactly. AC-16 greps for the string `ReactNativeWebView.postMessage`.
- No inline bundling of `<model-viewer>` library source (CDN only, pinned version).

**FR-12 (react-native-webview dependency)** — Add `react-native-webview` at `~13.8.0` (or the latest 13.x within the RN version's compat matrix; design-specialist pins an exact version) to `src/mobile/package.json` `dependencies`. AC-33 asserts presence. No other dependency is added by this task.

**FR-13 (Placeholder GLB assets)** — Commit four placeholder GLB files:
- `src/mobile/assets/ar/models/desk.glb`
- `src/mobile/assets/ar/models/bed.glb`
- `src/mobile/assets/ar/models/chair.glb`
- `src/mobile/assets/ar/models/lighting.glb`
Plus `src/mobile/assets/ar/models/README.md` documenting their placeholder nature and the upgrade path. AC-13/AC-14 assert presence and non-zero size.

### Mobile — tests

**FR-14 (RN tests — bridge unit)** — `src/mobile/__tests__/bridge.test.ts` covers `encodeInbound` and `decodeOutbound` for every event variant (3 inbound × shape assertion; 3 outbound × shape assertion + 4 error code variants on the `error` branch). Covers reject-on-wrong-bridgeVersion, reject-on-missing-fields, reject-on-invalid-event-name. Mapped to AC-19.

**FR-15 (RN tests — screen state machine)** — `src/mobile/__tests__/ARPlacementScreen.test.tsx` covers the full state machine. The WebView MUST be mocked as a component exposing an `emit(msg: string)` method that simulates `onMessage` being called with `{nativeEvent:{data:msg}}`. The test mocks `Alert.alert` and the `addToWishlist` client. Covers:
- Renders `ar-webview` on mount (AC-21).
- `emit` of a `placed` message triggers the Alert with the expected title / message / buttons (AC-22 + AC-23).
- Tapping "위시리스트에 추가" in the Alert calls `addToWishlist` with `{furnitureId, category, price}` from the route param's `item` (AC-24).
- `emit` of `cancelled` triggers `navigation.goBack` and does not call `addToWishlist` (AC-25).
- `emit` of each of the four error codes renders the correct Korean copy + `btn-ar-back` (AC-26 — one assertion per code).
- `emit` of `error` hides the `ar-webview` testID (AC-27).
- After Alert dismissal, the screen unmounts (AC-28).
- After unmount, no pending timers remain (AC-29 — teardown cleanliness).
- Exactly one `emitArFurniturePlaced` call per `placed` event (AC-30).
- Toast strings reused from `RecommendationScreen` match byte-for-byte (AC-31).
- Back press on error panel triggers `navigation.goBack` (AC-32).

**FR-16 (RN tests — RecommendationScreen additive regression)** — `src/mobile/__tests__/RecommendationScreen.test.tsx` is EXTENDED (additive append only — no existing test renamed / deleted / re-scoped) with:
- `btn-ar-{furnitureId}` testID is present for every rendered card (AC-34).
- Tapping `btn-ar-{furnitureId}` calls `navigation.navigate('ARPlacement', {roomId, item})` with the card's own item (AC-35).
- Existing AC-48 analytics emit + AC-49 wishlist add regression assertions REMAIN GREEN (AC-36 cross-task guard — verifier runs the full suite).

### Cross-task integrity

**FR-17 (No backend changes)** — Zero file under `src/backend/`, `src/ai/`, or `src/mobile/src/api/` is modified. No Flyway migration file under `src/backend/src/main/resources/db/migration/V1..V7*.sql` is modified. AC-37 is a git-diff guard (verifier runs `git diff --stat task/AR-furniture-placement src/backend src/ai src/mobile/src/api`). Empty diff passes.

**FR-18 (No new API contract)** — No OpenAPI endpoint is added. The `api_contract.yaml` produced by this task documents the **message-bridge** (non-HTTP) as a YAML describing the inbound / outbound payload schemas — a human reference, not an OpenAPI document. The file MUST parse as YAML (AC-38). No pretense of being OpenAPI 3.x — a top-level comment in the file explicitly states "this is a bridge contract, not an HTTP contract".

**FR-19 (Navigation regression)** — Registering `ARPlacement` MUST NOT change the `initialRouteName`, MUST NOT reorder the existing six `<Stack.Screen>` declarations, and MUST NOT alter the `Home / Upload / Analyzing / StyleSelection / Recommendation / Wishlist` route names or their component wiring. AC-40 grep-asserts the expected seven screen names in order.

**FR-20 (Cross-task full-suite pass)** — The existing jest test runs (`UploadScreen`, `AnalyzingScreen`, `StyleSelectionScreen`, `RecommendationScreen`, `WishlistScreen`, `HomeScreen`, `pollSchedule`, `styleTypes`, `errorMessages`) MUST all stay green with zero modifications. AC-41 runs the full mobile test suite and expects exit 0.

## 7. Non-Functional Requirements

- **Performance — time-to-first-scene**: on a supported mid-range device (D-6 threshold), the HTML scene should be visible (first paint of the `<model-viewer>` element) within 3 seconds of navigating into `ARPlacementScreen` on a warm cache. Not CI-testable this iteration; documented as a manual-QA target (NFR-1; no AC gates release on this).
- **Performance — 30 FPS interaction**: `<model-viewer>` delivers 60 FPS on target devices; no task-side tuning. Manual-QA only (NFR-2).
- **Memory — no leak across 5 AR cycles**: enter → place → back → re-enter 5×. The screen MUST send `unload` and clear refs per FR-10. AC-29 proves the timer / listener count is zero after each unmount (the "5 cycles" is a manual amplification; the test asserts the *mechanism* is clean).
- **Bridge version stability**: `AR_BRIDGE_VERSION` is a literal `1` in `src/mobile/src/ar/bridge.ts`; `decodeOutbound` throws on mismatch (AC-19). Future bump is a breaking-change task.
- **Accessibility**:
  - "AR로 배치" button MUST expose `accessibilityLabel="AR로 배치"` and `accessibilityRole="button"`; tap target ≥ 44pt (AC-39).
  - "돌아가기" button on the fallback panel MUST expose the same (AC-39 covers both in one test).
  - Korean screen-reader labels — no English in user-facing copy.
- **Error envelope parity**: AR client-side error codes are SCREAMING_SNAKE_CASE, matching sibling tasks' convention. They are NOT backend `errorCode` values — the AR client does not reuse the shared `ErrorResponse` envelope because the scene is not making HTTP calls. The wishlist call inside FR-7 reuses UC-02's envelope via `addToWishlist`'s existing `ApiError` handling unchanged.
- **No new heavy dependency**: only `react-native-webview` is added. No Unity, no three.js-as-RN-native-module, no native modules beyond what `react-native-webview` itself already brings.
- **Determinism**: FR-15 tests use synchronous `webview.emit(msg)` — no real timers, no real animation, no real network. `decodeOutbound` is pure; `encodeInbound` is pure. AC-19 is deterministic.
- **Privacy**: the HTML scene does NOT send any telemetry, does NOT load any third-party analytics script (the `<model-viewer>` CDN is the only external URL). AC-42 greps the HTML for any occurrence of `google-analytics`, `gtag`, `facebook.com`, `amplitude`, `mixpanel` and asserts zero matches.

## 8. Out of Scope
- Unity-as-a-Library integration — deferred to `AR-furniture-placement-unity-v2` (D-1 rationale).
- `furniture.model_url` column + V8 migration — deferred to the same future task (D-2 rationale).
- Real catalog-driven dimensions — current implementation uses per-type default tuples (FR-4).
- Pose capture (real world → AR pose) — `placed` message carries `{0,0,0,0}` pose in v1 (FR-11 note).
- Screenshot / save-to-gallery on placement.
- Persistent anchors / Cloud Anchors.
- Measurement / ruler overlay.
- Multi-item placement.
- Collision detection beyond the plane.
- User-uploaded models.
- Shared / collaborative sessions.
- Admin analytics of AR usage.
- New backend endpoint (no `GET /api/v1/furniture/{id}`, no model-URL streaming endpoint).
- Any change to UC-01 / UC-02 / UC-03 endpoint contracts.
- Any Flyway migration.
- Native-module surgery for capability detection (Option B: always-try, no pre-flight check).
- iOS < 13, Android < 7, or devices without a WebView engine.

## 9. Data Model & Contract

### DB — unchanged
No migration. No schema delta. Zero DB change.

### REST — unchanged
No new HTTP endpoint. The AR scene consumes `RecommendationItem` already reachable via UC-01-recommendation's existing `GET /api/v1/spaces/{roomId}/recommendations` and hands off to UC-02-wishlist's existing `POST /api/v1/wishlist` through the shared `addToWishlist` client. Byte-identical to those tasks.

### Bridge contract (non-HTTP; see `api_contract.yaml` per FR-18)

**Inbound (RN → scene):**
```json
{
  "bridgeVersion": 1,
  "event": "load",
  "furnitureId": "f_desk_001",
  "modelUrl": "asset:///ar/models/desk.glb",
  "roomDimensions": { "widthM": 3.6, "lengthM": 4.2, "heightM": 2.4 },
  "colorHex": null
}
```
```json
{ "bridgeVersion": 1, "event": "unload" }
```

**Outbound (scene → RN):**
```json
{ "bridgeVersion": 1, "event": "placed", "pose": { "x": 0, "y": 0, "z": 0, "yaw": 0 } }
```
```json
{ "bridgeVersion": 1, "event": "cancelled" }
```
```json
{ "bridgeVersion": 1, "event": "error", "errorCode": "AR_NOT_SUPPORTED", "message": "canActivateAR=false" }
```

### Error codes introduced by this task (client-side only — not in any HTTP envelope)
| Code | Emitted when |
|------|--------------|
| `AR_NOT_SUPPORTED` | `<model-viewer>.canActivateAR === false`, OR scene init exception before any model load |
| `AR_CAMERA_DENIED` | User denies camera permission when AR session starts (browser-dispatched `DOMException`) |
| `AR_MODEL_LOAD_FAILED` | `<model-viewer>` `error` event fires on model fetch/parse |
| `AR_SESSION_ERROR` | Catch-all for session-dies-mid-use (WebXR `sessionend` with abnormal reason, OR any uncaught exception inside the scene script) |

## 10. Dependencies
- **Hard (complete)**:
  - `UC-01-recommendation` (Task 5) — supplies `RecommendationItem` shape; the entry point is on `RecommendationScreen`.
  - `UC-02-wishlist` (Task 6) — supplies `addToWishlist` client, reused inside FR-7 without modification.
- **Soft (informational only)**:
  - `UC-01-space-analysis` (Task 3) — roomDimensions sourced from the `spaces` row (this task currently stubs via per-type defaults per D-2; if a future task adds real dimensions to the bridge payload, `UC-01-space-analysis`'s `dimensions` column is the source).
- **No dependency on**: `DB-schema-init`, `UC-01-photo-upload`, `UC-01-style-selection`, `UC-03-admin-overview`. None of their contracts are touched.
- **Consumed by**: future `AR-furniture-placement-unity-v2` (the bridge contract carries forward unchanged); `UC-03-admin-drilldown` is NOT a consumer (no AR-usage analytics in this iteration).

## 11. Platform targets (see D-6)
- **iOS 13+** via Safari WKWebView hosted by `react-native-webview@13.x`. WebXR AR delegates to AR Quick Look (ARKit).
- **Android 7+** with Chrome 79+ installed. WebXR AR delegates to ARCore via `<model-viewer>` scene-viewer fallback.
- **Below threshold or no AR hardware**: capability check at scene init emits `AR_NOT_SUPPORTED`; fallback Korean panel renders.

## 12. Open Questions
1. **Pose fidelity** — FR-11 allows a static pose `{0,0,0,0}` in v1. PRD doesn't demand real pose capture for the preview; flagged so a future task can upgrade the scene-side JS to compute real pose from `<model-viewer>`'s `cameraOrbit` / `cameraTarget`.
2. **Model-viewer CDN vs. bundled** — the CDN include simplifies packaging but adds a network dependency. If a product decision mandates fully-offline AR, a future task inlines the library; the HTML contract survives unchanged.
3. **Placeholder GLB vs. real assets** — FR-13 commits placeholder GLBs. The question of production-asset provenance / licensing is a product-side decision, flagged but out of this task's scope.
4. **Unity parity commitment** — the PRD's §4 wording is Unity AR Foundation. D-1 chose WebXR with a documented upgrade path. Product-owner sign-off on "WebXR is sufficient for v1" is implicitly required; flagged here so the verifier can call it out if sign-off has not happened.
