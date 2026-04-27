# Follow-up D-5 — `AR-furniture-placement-unity-v2` Task Stub

**Status**: **BLOCKED** — pending D-1 sign-off (see `02_unity_downgrade_signoff.md`)
**Date**: 2026-04-20
**Source**: deferred from `AR-furniture-placement` D-1 decision (WebView over Unity UaaL)
**Companion**: `artifacts/AR-furniture-placement/viz/unity_future_v2.md` (migration roadmap)

---

## 1. Why this task exists

PRD §4 originally specified "Unity AR Foundation". Iteration-1 chose `<model-viewer>` + WebView (D-1 Option C) to ship within CI constraints. This stub captures the work required IF/WHEN the product owner chooses Option B on `02_unity_downgrade_signoff.md` ("reject the downgrade, schedule Unity rework").

## 2. Entry criteria (all must be true before this task kicks off)

- [ ] Product owner has countersigned `02_unity_downgrade_signoff.md` Option B (or Option C)
- [ ] Unity 2022 LTS or 2023 LTS license procured for all dev machines + CI runners
- [ ] CI can execute `unity -batchmode -runTests -projectPath src/unity/ -testResults unity-test-results.xml` and export to JUnit XML
- [ ] Android CI runner has IL2CPP + NDK + ARM64 toolchain installed
- [ ] iOS CI runner has Xcode 15+ with ARKit capability
- [ ] A dedicated mobile engineer with Unity + RN native module experience assigned as owner
- [ ] 3D asset pipeline delivers real GLB / Unity prefab files (not 128-byte stubs)

## 3. Scope (if kicked off)

This task is a **like-for-like swap of the rendering engine**, not a feature expansion. The bridge contract (`bridgeVersion: 1`) is the hard interface — everything above it must remain untouched.

### In scope
- New `src/unity/` project with AR Foundation, ARCore XR plugin, ARKit XR plugin
- `FurniturePlacementController.cs` + `FurnitureAnchor` prefab (scene graph per `viz/ar_hook.md §2`)
- `UnityBridge.cs` implementing the `OutboundARMessage` contract + consuming `InboundARMessage`
- New RN component `src/mobile/src/ar/ARUnityView.tsx` with identical `forwardRef<ARWebViewHandle, ARWebViewProps>` surface (prop `onMessage`, handle `inject`)
- Native module wiring — Android `settings.gradle` + `app/build.gradle`; iOS `Podfile` + framework linking
- Flyway V8 migration adding `furniture.model_url` column (promoted from D-2 "per-type default")
- Unity Test Framework EditMode + PlayMode tests for `FurniturePlacementController` + `UnityBridge` marshaling
- Feature flag `ar.engine = 'webview' | 'unity'` defaulted to `webview` until rollout

### Out of scope
- Visual fidelity upgrades (custom shaders, IBL, shadow catcher) — sibling task
- Multi-furniture placement — sibling task
- Persistent anchors (Cloud Anchors / ARWorldMap) — sibling task
- Measurement tools — sibling task
- `bridgeVersion: 2` — if needed, is its own task after v2 ships

## 4. Forward-compat guarantees from v1

These facts from iteration-1 are the "API the v2 task must honor":

| Contract | Source | How v2 honors |
|---|---|---|
| `bridgeVersion: 1` on every inbound + outbound message | `src/mobile/src/ar/bridge.ts` | Unity emits same field; RN-side decode accepts `{1,2}` during cutover window |
| `event ∈ {load, unload, ready, placed, cancelled, error}` | `api_contract.yaml` | Unity implements all, including `ready` which was reserved-but-not-emitted in v1 |
| `OutboundARErrorCode` enum: `AR_NOT_SUPPORTED, AR_CAMERA_DENIED, AR_MODEL_LOAD_FAILED, AR_SESSION_ERROR` | `bridge.ts` | Unity emits same strings for the same root causes |
| Pose shape: `{x, y, z, yaw}` in meters + radians | spec §9 D-4 | Unity populates from `ARAnchor.pose` |
| RN state machine states `loading | live | placed | cancelled | error` | `ARPlacementScreen.tsx` | Untouched — ARUnityView swaps for ARWebView |
| `unload` message fires at unmount (AC-29 fix) | `ARPlacementScreen.tsx:200-220` capture-closure pattern | Same pattern, same ref semantics |
| Wishlist handoff flow (`Alert.alert` → `addToWishlist`) | `ARPlacementScreen.tsx:223-260` | Engine-agnostic; unchanged |

## 5. Iteration-1 deliverables that v2 can reuse

- `bridge.ts` — message schemas + `encodeInbound` / `decodeOutbound` helpers (100% reusable)
- `ARPlacementScreen.tsx` — screen state machine, Alert copy, wishlist handoff (~95% reusable; only the ARWebView import swaps to ARUnityView)
- `api_contract.yaml` — bridge-contract YAML (100% reusable — Unity reads the same schemas)
- Test harness pattern — `jest.mock('../src/ar/ARWebView', ...)` becomes `jest.mock('../src/ar/ARUnityView', ...)` with identical `__emit(msg)` shape
- Error copy map `AR_ERROR_COPY` — 100% reusable
- Placeholder GLBs — v2 replaces with real assets but file-layout pattern unchanged

## 6. Iteration-1 deliverables that v2 REPLACES

- `src/mobile/assets/ar/placement.html` — no longer needed; Unity renders the scene
- `src/mobile/assets/ar/models/{desk,bed,chair,lighting}.glb` — replaced by Unity prefabs loaded from AssetBundle or bundled Resources
- `src/mobile/src/ar/ARWebView.tsx` — replaced by `ARUnityView.tsx`
- `@google/model-viewer` + `react-native-webview` dependencies — removed from `package.json` (or kept as fallback under the feature flag)

## 7. Estimated effort

Given the entry criteria are met and all five unknowns are closed:

| Phase | Days |
|---|---|
| Unity project scaffold + AR Foundation setup + one prefab placing on floor plane | 3–5 |
| UaaL native-module integration (Android + iOS) + RN bridge wiring | 5–7 |
| Unity Test Framework tests (EditMode + PlayMode) + CI integration | 3–5 |
| Flyway V8 `furniture.model_url` + RN client wiring | 1–2 |
| QA + rollout under feature flag | 2–3 |
| **Total** | **14–22 engineer-days** |

Compare: iteration-1 WebView implementation was 1 iteration loop (~2 days of agent orchestration).

## 8. Risk matrix

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Unity license procurement slips | Medium | Blocks kickoff | Start procurement when Option B is signed; use Unity Personal for PoC if team size permits |
| `bridgeVersion: 1` proves too narrow for Unity's real needs | Low | Forces v2 contract | Reserve `bridgeVersion: 2` as an escape hatch; document divergences in spec §7 |
| 3D asset pipeline slips | Medium | Unity ships but renders stubs | Keep v1 GLB pipeline as fallback; Unity can load GLTF via `glTFast` plugin for parity with WebView assets |
| CI for Unity doesn't exist | **High** | Task cannot start | Pre-work: provision CI runner with Unity license before kickoff |
| Regression in WebView path during feature-flag cutover | Low | User-visible bug | Both engines live behind flag for 1–2 release cycles; kill-switch tooling |

## 9. Open questions for the spec-owner when/if kickoff begins

1. **Which Unity LTS version** — 2022 LTS (maturity) vs 2023 LTS (latest AR Foundation features)?
2. **`bridgeVersion: 2` now or later** — should we bake a v2 contract upfront to take advantage of Unity-specific features (animations, physics, multi-anchor)?
3. **Feature-flag scope** — server-side (`GET /api/v1/config`) or client-local (`settings.arEngine`)?
4. **GLB vs FBX vs Unity native prefab** — asset format decision affects the V8 migration contents
5. **Timeline** — is this a "before-launch" task or a "post-launch sprint 2" task? Affects whether to ship WebView to production and migrate, or hold launch for Unity

## 10. Decision capture

```
Status as of 2026-04-20:  BLOCKED on D-1 sign-off

☐ Entry criteria §2 items 1-7 all satisfied
☐ Task owner assigned: __________________________
☐ Kickoff date: __________
☐ Target cutover date: __________

Signed by: __________________________  Date: __________
```

## 11. Reference

- `02_unity_downgrade_signoff.md` — D-1 decision memo (this task is the Option B outcome)
- `artifacts/AR-furniture-placement/viz/unity_future_v2.md` — migration roadmap (authoritative)
- `artifacts/AR-furniture-placement/api_contract.yaml` — bridge contract v1
- `artifacts/AR-furniture-placement/spec.md` §5 D-1 — rationale for WebView choice
