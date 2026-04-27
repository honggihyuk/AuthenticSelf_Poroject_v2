# Unity UaaL v2 — migration roadmap (forward-compat note)

PRD §4 mandates "Unity AR Foundation" for the preview layer. This
task (`AR-furniture-placement`) picked **Option C** per D-1 —
`<model-viewer>` in `react-native-webview` — because Unity-as-a-Library
infra (Unity 2022 LTS + AR Foundation + `unity -batchmode -runTests`
CI) is not present in this repo. The v2 task is reserved to swap the
embedding strategy **without touching the bridge contract.**

## Migration plan (future task `AR-furniture-placement-unity-v2`)

1. **Ship the Unity UaaL shell**
   - `src/unity/` project w/ AR Foundation, ARCore XR plugin, ARKit XR
     plugin; Player Settings targeting iOS 13+ / Android 7+.
   - Scene graph per UC-01-recommendation's
     `viz/ar_hook.md §2`: `ARSession` + `ARSessionOrigin`
     (`ARCamera`, `ARPlaneManager`, `ARRaycastManager`) +
     `FurniturePlacementController` + `FurnitureAnchor` prefab.

2. **Keep `bridgeVersion: 1` during rollout**
   - The Unity side implements the SAME message contract
     (see `bridge_contract.md` / `api_contract.yaml`) — inbound
     `load`/`unload`, outbound `placed`/`cancelled`/`error` with the
     same four `OutboundARErrorCode` values.
   - Unity reads the inbound JSON via the UaaL
     `UnityFramework.sendMessageToGO` plumbing; posts outbound via
     `UnityBridge.Emit("UnityMessage", json)` which RN receives as a
     native event.
   - Decode/encode helpers in `bridge.ts` are reused as-is.

3. **Swap `ARWebView.tsx` in-place**
   - Drop-in replacement `ARUnityView.tsx` with the same
     `forwardRef<ARWebViewHandle, ARWebViewProps>` surface (prop
     `onMessage`, handle `inject`). `ARPlacementScreen.tsx` does not
     change beyond one import.

4. **Promote `modelUrl` to catalog-driven assets**
   - Add `furniture.model_url` column via Flyway V8 (deferred from D-2).
   - RN resolves `modelUrl` from the `RecommendationItem` directly
     instead of `MODEL_URL_BY_TYPE[item.type]`.
   - Legacy four-asset fallback kept as the last-resort default
     during the rollout.

5. **Upgrade pose fidelity**
   - Compute real `{x,y,z,yaw}` inside Unity from the placed anchor's
     `pose.position` + `pose.rotation` and emit on `placed`.
   - RN side is already ready — the bridge field exists in v1.

6. **Emit `ready` (currently reserved)**
   - `ArReadyMessage` is already declared in `api_contract.yaml`.
     Unity emits it when the scene loads + AR session initialises —
     RN can use this to hide a spinner if one is ever introduced.

## Cutover strategy

- Dual-decoder week: `decodeOutbound` accepts `bridgeVersion in {1, 2}`
  for one release cycle (`x-bridge-version-policy` in
  `api_contract.yaml` already names this as the fallback plan).
- Feature flag: `ar.engine = 'webview' | 'unity'`, default `webview`,
  flipped per user segment once CI coverage lands.
- Regression guard: the existing mobile test suite (`bridge.test.ts`
  + `ARPlacementScreen.test.tsx`) stays green against both engines —
  `jest.mock('../src/ar/ARWebView', ...)` becomes
  `jest.mock('../src/ar/ARUnityView', ...)` with identical
  `__emit(msg)` shape.

The v1 contract is the integration boundary. Everything above the
bridge (screen state machine, Alert copy, wishlist handoff) is
engine-agnostic by construction.
