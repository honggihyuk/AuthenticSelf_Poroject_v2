# AR bridge contract (non-HTTP) — human-readable rendering

**`bridgeVersion: 1`** — every message carries the literal `1`.
`decodeOutbound` (in `src/mobile/src/ar/bridge.ts`) throws on mismatch;
the thrown error is routed by `ARWebView.onDecodeError` to
`ARPlacementScreen.handleError('AR_SESSION_ERROR')` (FR-3 contract-drift
guard). Future bump is a breaking-change task.

> This is a **non-HTTP** contract. `api_contract.yaml` is shaped like
> OpenAPI 3.0.3 for reviewer familiarity but `paths: {}` is
> intentionally empty — every schema lives under
> `components.schemas`. No new REST endpoint is introduced by this task
> (FR-17 / FR-18 / AC-37).

**Transport (per `x-bridge-transport` in `api_contract.yaml`):**
- **Inbound (RN → scene):** `WebView.injectJavaScript(...)` invokes
  `window.__authenticSelfBridgeLoad(<json-string>)` inside the HTML.
  `ARWebView.inject(bridgeJson)` wraps this in a safe IIFE that swallows
  any throw (teardown cleanliness — FR-10).
- **Outbound (scene → RN):**
  `window.ReactNativeWebView.postMessage(JSON.stringify(msg))` in the
  HTML `<script>` block. RN receives it as
  `ev.nativeEvent.data` and runs `decodeOutbound(raw)` which
  shape-narrows to `OutboundARMessage`.

Anchors: FR-3 / FR-11 / FR-18 / AC-9 / AC-16 / AC-19 / AC-38 / AC-43 / AC-44.

---

## Inbound (RN → scene)

Encoded by `encodeInbound(msg: InboundARMessage): string`. Pure JSON;
no Date/Math usage; identical input → identical output (AC-19).

### 1. `load` — tell the scene which GLB to render

Sent once on mount via `useEffect` in `ARPlacementScreen`.

```json
{
  "bridgeVersion": 1,
  "event": "load",
  "furnitureId": "f_desk_001",
  "modelUrl": "asset:///ar/models/desk.glb",
  "roomDimensions": { "widthM": 1.20, "lengthM": 0.60, "heightM": 0.74 },
  "colorHex": null
}
```

Fields:

| field | type | source | note |
|-------|------|--------|------|
| `bridgeVersion` | `1` (literal) | constant | AC-9 export check. |
| `event` | `'load'` | constant | required |
| `furnitureId` | `string` | `route.params.item.furnitureId` | stable catalog key |
| `modelUrl` | `string` | `MODEL_URL_BY_TYPE[item.type]` | one of four `asset:///ar/models/{type}.glb` strings |
| `roomDimensions` | `{widthM, lengthM, heightM}` \| `null` | `DEFAULT_DIMENSIONS_BY_TYPE[item.type]` | per-type default tuple (FR-4 / AC-20) |
| `colorHex` | `string` (`#RRGGBB`) \| `null` | always `null` in v1 | placeholder GLB has baked material |

HTML-side handler (`window.__authenticSelfBridgeLoad`, AC-44):

1. Bridge-version check; mismatch → `postError('AR_SESSION_ERROR', 'bridgeVersion mismatch')`.
2. `viewer.setAttribute('src', modelUrl)`.
3. `setTimeout(0)` → probe `viewer.canActivateAR`; `false` →
   `postError('AR_NOT_SUPPORTED', 'canActivateAR=false')`.
4. On the `<model-viewer>` `'load'` event → `btnPlace.disabled = false`.

### 2. `unload` — teardown on RN unmount

Sent from `ARPlacementScreen` cleanup `useEffect` (FR-10 / AC-29). No
`setTimeout`/`setInterval`, no pending Alerts — `jest.getTimerCount()`
stays at `0` after unmount.

```json
{ "bridgeVersion": 1, "event": "unload" }
```

HTML-side handler: `viewer.removeAttribute('src'); btnPlace.disabled = true`.

---

## Outbound (scene → RN)

Decoded by `decodeOutbound(raw: string): OutboundARMessage`. Throws on
malformed input; every throw branch is covered by `bridge.test.ts` (AC-43).

### 1. `placed` — user tapped "배치 완료"

Emitted by the HTML overlay's `#btn-place` click handler.

```json
{
  "bridgeVersion": 1,
  "event": "placed",
  "pose": { "x": 0, "y": 0, "z": 0, "yaw": 0 }
}
```

v1 pose is the literal `{0,0,0,0}` (FR-11 note). A future task can
populate this from `<model-viewer>`'s `cameraTarget` / `cameraOrbit`
without changing the bridge contract. RN triggers the
`Alert.alert('배치 완료', '위시리스트에 추가하시겠습니까?', […])` flow
(FR-7 / AC-22 / AC-23).

### 2. `cancelled` — user tapped "취소"

Emitted by the HTML overlay's `#btn-cancel` click handler.

```json
{ "bridgeVersion": 1, "event": "cancelled" }
```

RN calls `navigation.goBack()` immediately; no Alert, no
`addToWishlist` (AC-25).

### 3. `error` — scene-side fault

Emitted by four HTML-side hooks:

| Hook | `errorCode` |
|------|-------------|
| `viewer.canActivateAR === false` OR capability probe catch | `AR_NOT_SUPPORTED` |
| `window.error` listener matches `/NotAllowedError|camera|permission/` | `AR_CAMERA_DENIED` |
| `<model-viewer>` `error` event (model fetch/parse fail) | `AR_MODEL_LOAD_FAILED` |
| `ar-status=failed`, scene init throw, or RN-side decode throw | `AR_SESSION_ERROR` |

```json
{
  "bridgeVersion": 1,
  "event": "error",
  "errorCode": "AR_NOT_SUPPORTED",
  "message": "canActivateAR=false"
}
```

`message` is optional (documentational; not surfaced to the user).

### 4. `ready` — reserved for v2 (NOT emitted in v1)

Present in `api_contract.yaml` under `components.schemas.ArReadyMessage`
so a v2 scene can add it without bumping `bridgeVersion`. The current
HTML scene does NOT emit this; the current RN side does NOT handle it
(`decodeOutbound` throws `unknown event=ready`). **This is a reserved
extension point, not a live message.**

---

## `OutboundARErrorCode` → Korean copy (AR_ERROR_COPY)

Exported from `ARPlacementScreen.tsx` as `AR_ERROR_COPY: Record<OutboundARErrorCode, string>`
so tests can match by reference, not by substring (FR-6 / AC-26).

| `errorCode` | Korean copy (rendered in `ar-fallback`) |
|-------------|-----------------------------------------|
| `AR_NOT_SUPPORTED`     | `이 기기에서는 AR 미리보기가 지원되지 않습니다.` |
| `AR_CAMERA_DENIED`     | `카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요.` |
| `AR_MODEL_LOAD_FAILED` | `3D 모델을 불러오지 못했습니다. 네트워크를 확인해주세요.` |
| `AR_SESSION_ERROR`     | `AR 세션에 문제가 발생했습니다. 다시 시도해주세요.` |

Fallback UI: `<Text>{AR_ERROR_COPY[code]}</Text>` + a `Pressable` with
`testID='btn-ar-back'`, `accessibilityLabel='돌아가기'`,
`accessibilityRole='button'`, label `돌아가기`. Tapping it calls
`navigation.goBack()` (AC-32 / AC-39).

---

## Type exports (referenced by the contract)

From `src/mobile/src/ar/bridge.ts` (AC-9 asserts these exports):

```ts
export const AR_BRIDGE_VERSION = 1 as const;

export type OutboundARErrorCode =
  | 'AR_NOT_SUPPORTED'
  | 'AR_CAMERA_DENIED'
  | 'AR_MODEL_LOAD_FAILED'
  | 'AR_SESSION_ERROR';

export type InboundARMessage =
  | InboundARLoadMessage
  | InboundARUnloadMessage;

export type OutboundARMessage =
  | OutboundARPlacedMessage
  | OutboundARCancelledMessage
  | OutboundARErrorMessage;

export function encodeInbound(msg: InboundARMessage): string;
export function decodeOutbound(raw: string): OutboundARMessage;
```

Plus an analytics stub whose shape mirrors
`emitWishlistAddClicked` in `spaces.ts` (D-8 / AC-30):

```ts
export function emitArFurniturePlaced(
  roomId: string,
  furnitureId: string,
  pose: { x: number; y: number; z: number; yaw: number },
): void;
export function setArAnalyticsSink(sink: (ev: ArAnalyticsEvent) => void): void;
```
