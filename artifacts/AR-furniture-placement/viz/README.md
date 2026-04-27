# AR-furniture-placement — viz artifact index

Visualization artifacts for Task 8 (AR-furniture-placement). All
diagrams are inline Mermaid / ASCII — no external renderers or image
hosts. Style and naming follow
`artifacts/UC-02-wishlist/viz/` (most recent sibling).

## Files

| File                  | Type                         | Primary FR/AC anchors |
|-----------------------|------------------------------|-----------------------|
| `sequence.mmd`        | Mermaid `sequenceDiagram`    | Five flows on one canvas — entry+load / placed+wishlist / cancelled / four error branches / teardown; FR-1 / FR-5..FR-11 / AC-7 / AC-17..AC-32 / AC-44 |
| `architecture.mmd`    | Mermaid `flowchart LR`       | RN screens → bridge (`bridge.ts` + `ARWebView`) → WebView host → `placement.html` → `<model-viewer>` @3.5.0 → native AR overlay; bundled GLBs; UC-02 wishlist reuse; NEW vs REUSED modules color-coded; FR-2 / FR-3 / FR-11..FR-13 / FR-18 |
| `state_machine.mmd`   | Mermaid `stateDiagram-v2`    | `loading → live → {placed \| cancelled \| error} → [*]` with triggering message + AC per transition; "one-way" terminal states annotated; FR-5..FR-8 / AC-21 / AC-22 / AC-25..AC-32 |
| `bridge_contract.md`  | Markdown + JSON examples     | Human-readable rendering of `api_contract.yaml` — inbound `load`/`unload`, outbound `placed`/`cancelled`/`error` (+ reserved `ready`), the four `ArErrorCode` values mapped to Korean copy; `bridgeVersion:1` + "non-HTTP contract" note prominent; FR-3 / FR-11 / FR-18 / AC-9 / AC-19 / AC-38 / AC-43 / AC-44 |
| `ui.md`               | ASCII + Mermaid flowchart    | `RecommendationScreen` ItemCard with new `btn-ar-{fid}`; ARPlacementScreen in each state (loading / live / placed+Alert / error fallback); 5-branch toast decision table; screen-reader accessibility matrix; FR-1 / FR-5..FR-9 / AC-7 / AC-17..AC-32 / AC-39 |
| `unity_future_v2.md`  | Markdown (< 30 lines of plan)| UaaL v2 migration roadmap preserving `bridgeVersion:1`; protects PRD §4 Unity parity commitment; addresses spec §12 open question #4 |
| `README.md`           | this file                    | index + carry-forward deviations + deltas spotted + open questions forwarded |

## Diagram coverage vs. spec

- **Sequence:** fully visualized end-to-end. Five rect blocks cover
  entry+load, placement (incl. native AR overlay dispatch + wishlist
  add with all five toast branches), cancelled, the four error codes
  (plus the RN-side decode-throw branch), and teardown. UC-02
  `addToWishlist(...)` reuse is shown explicitly as a REUSED
  participant.
- **Architecture:** fully visualized. All four layers drawn: RN
  screens / bridge types / WebView+HTML scene / bundled assets. Every
  NEW module (bridge.ts, ARWebView.tsx, ARPlacementScreen.tsx,
  placement.html, four GLBs, react-native-webview dep) is
  dashed-blue; REUSED modules (`api/wishlist.ts`, `settings.ts`,
  `api/client.ts` ApiError) are grey. The native AR overlay is drawn
  as a distinct pink node OUTSIDE the WebView DOM.
- **State machine:** fully visualized using source enum
  `'loading' | 'live' | 'placed' | 'cancelled' | 'error'`. Every
  transition pinned to the AC that tests it; "one-way terminal"
  semantics of `error`/`placed`/`cancelled` annotated.
- **Bridge contract:** every message schema from
  `api_contract.yaml` rendered with a JSON example and its HTML-side
  emitter / RN-side consumer. The reserved `ArReadyMessage` is
  called out as NOT emitted in v1.
- **UI:** every `testID` emitted by the source (`ar-webview`,
  `ar-fallback`, `btn-ar-back`, `btn-ar-{fid}`, `btn-wishlist-{fid}`,
  `card-{fid}`) appears on the mocks. The 5-branch
  `WISHLIST_TOAST_COPY` decision matrix is rendered as a table.
- **Unity v2:** the forward-compat plan preserves `bridgeVersion:1`,
  reuses the state machine as-is, and names the v2 task ID
  (`AR-furniture-placement-unity-v2`) so product sign-off is
  trackable.

## Design-specialist deviations carried forward (§13 of design.md)

These are intentional non-blockers from the implementation — reported
here for the verification-specialist's awareness.

1. **`htmlBuilder.ts` was not created.** The task brief mentioned a
   builder that takes `{furnitureId, modelUrl, roomDimensions}`. The
   shipped architecture ships a STATIC `placement.html` asset plus an
   inbound `load` bridge message that carries those fields at runtime.
   A builder would either duplicate the bridge payload or produce
   per-furniture HTML that must be re-injected — both strictly worse
   than the bundled-asset approach. All AC-10..AC-12 / AC-15 / AC-16
   / AC-42 / AC-44 grep assertions target the static file directly.
   **Impact on diagrams:** `architecture.mmd` draws `placement.html`
   as a bundled ASSET (yellow), not as builder output. `sequence.mmd`
   shows the `inject(loadPayload)` edge carrying the per-furniture
   fields.

2. **`fallback.tsx` is inlined into `ARPlacementScreen.tsx`.** The
   `status === 'error'` render branch is a 15-JSX-line inline block,
   not a separate component. AC-26 / AC-27 / AC-32 all test against
   the inlined render identically.
   **Impact on diagrams:** `ui.md §6` explicitly calls this out;
   `architecture.mmd` does not draw a `fallback.tsx` node.

3. **`WISHLIST_TOAST_COPY` is duplicated, not imported** from
   `RecommendationScreen.tsx`. D-9 rationale: AC-31 asserts
   byte-for-byte string equality, not reference equality. The five
   strings live in `ARPlacementScreen.tsx` as a local `as const`
   object. If either copy drifts, AC-31 fails — which is the intent.
   **Impact on diagrams:** `architecture.mmd` labels
   `ARPlacementScreen.tsx` with "WISHLIST_TOAST_COPY (dup per D-9)".

## Spec open questions (carried forward from spec §12)

Non-blocking per design-specialist. The verification-specialist
should surface them but NOT gate on them.

1. **Pose fidelity** — v1 emits the literal `{x:0,y:0,z:0,yaw:0}`.
   Future task reads `viewer.cameraTarget` / `viewer.cameraOrbit` and
   populates real pose without changing the bridge contract
   (`pose` is already a first-class field on `ArPlacedMessage`).

2. **Model-viewer CDN vs. bundled** — v1 uses the pinned
   `ajax.googleapis.com` CDN. Offline-only builds would need to
   inline the minified UMD. The HTML contract survives either choice.

3. **Placeholder GLB provenance** — four 128-byte minimal valid-magic
   GLBs are committed; `models/README.md` documents the upgrade path.
   Production-asset licensing is a product-side decision outside this
   task's scope.

4. **Unity parity commitment (CRITICAL — product sign-off required).**
   Spec §12 Q4 and design.md §13 both flag: **"Option C is a PRD
   downgrade from Unity AR Foundation. Product-owner sign-off needed
   before shipping."** The `unity_future_v2.md` file in this viz
   directory names the v2 migration path (`AR-furniture-placement-unity-v2`)
   preserving `bridgeVersion:1`, so the v1 ship is safely reversible.
   Verification-specialist should surface this as a PASS-WITH-WARNINGS
   flag, not a PASS.

## Deltas spotted between design.md / api_contract.yaml and actual source

Drawing the diagrams surfaced the following points. Per the task rule
("trust the source"), diagrams follow the source; deltas are recorded
here for the verification-specialist.

1. **State-name mismatch in the task brief vs. the source enum.** The
   task-brief draft referred to states `LOADING / AR_ACTIVE / PLACED
   / COMMITTED / ERROR / NOT_SUPPORTED`. The actual source enum in
   `ARPlacementScreen.tsx:122` is
   `'loading' | 'live' | 'placed' | 'cancelled' | 'error'`. Notably:
   - There is NO `ar_active` state — the source uses `live`.
   - There is NO `committed` state — the source transitions directly
     from `placed` → `[*]` via `navigation.goBack()` on Alert
     dismissal.
   - There is NO dedicated `not_supported` state — the source uses
     `status='error'` with `errorCode='AR_NOT_SUPPORTED'`.
   Design.md §5 matches the source (`live` is correct there). Diagrams
   follow the source. **Severity: diagram naming only.**

2. **`AR_READY` is reserved, NOT emitted.** The task brief asked the
   sequence diagram to show "HTML posts `AR_READY` → state becomes
   `ar_active`". The source HTML (`placement.html:115-237`) does NOT
   emit a `ready` message; there is no corresponding handler in
   `ARPlacementScreen.tsx`. The schema
   `ArReadyMessage` exists in `api_contract.yaml` (line 129-141) but
   is annotated "Future extension — not emitted in v1". Diagrams draw
   the `loading → live` transition as triggered by the RN-side
   `useEffect` + `setStatus('live')`, NOT by a `ready` message.
   **Severity: diagram naming only.**

3. **Inbound `load` message is synchronously injected from
   `useEffect`, not `onLoadStart`.** FR-5 wording said "`onLoadStart`:
   sends the inbound `load` message". The source
   (`ARPlacementScreen.tsx:185-197`) sends the load message from a
   mount `useEffect`, not from the WebView's `onLoadStart` prop.
   Rationale implied by design-specialist: the forwardRef handle is
   available synchronously at first render, and a real `onLoadStart`
   call adds a race against the HTML scene's own `DOMContentLoaded`.
   The bridge's `window.__authenticSelfBridgeLoad` hook is idempotent
   w.r.t. ordering — if the HTML has not yet finished loading when
   `inject` is called, the wrapper's `try{}` swallow handles it;
   RN will see `AR_SESSION_ERROR` surface if the bridge truly breaks.
   Diagrams follow the source. **Severity: doc wording only.**

4. **API-contract `paths: {}` vs. "not HTTP" comment.** The
   `api_contract.yaml` top-level comment says "this is a bridge
   contract, not an HTTP contract" (matches spec FR-18) BUT the file
   still carries an `openapi: 3.0.3` header + `info` block. That is
   intentional (reviewer-familiarity framing) and `paths: {}` makes
   it effectively inert as HTTP — AC-38 only asserts YAML-parse. The
   `bridge_contract.md` human rendering calls this out prominently.
   **Severity: none — design-specialist's FR-18 intent is honored.**

5. **`ARWebView` has a deferred `require('react-native-webview')`
   fallback that renders a plain `<View>` when the native module is
   missing.** This is not mentioned in the task brief but IS
   mentioned in design.md §13. It exists so `jest --watch` works on
   dev machines without the native module installed; tests mock the
   module so the fallback is never hit in CI. Diagrams do not draw a
   separate node for this fallback — it is an implementation detail
   hidden under `ARWebView.tsx`. **Severity: none.**

6. **`WISHLIST_TOAST_COPY` byte-for-byte parity is asserted by AC-31
   but is also visible in source comparison.** Reading
   `RecommendationScreen.tsx:302-317` side-by-side with
   `ARPlacementScreen.tsx:108-114` confirms the five strings match
   exactly:
   - `위시리스트에 담았어요.`
   - `이미 위시리스트에 있어요.`
   - `이미 구매 완료로 표시된 항목이에요.`
   - `위시리스트에 추가하지 못했어요.`
   - `네트워크 오류로 추가하지 못했어요.`
   Diagrams quote these verbatim. **Severity: none — intended
   duplication per D-9.**

7. **`ARWebView.tsx`'s `WebView` style background is `#111`, HTML
   `background:#111`, and `ARPlacementScreen` container
   `backgroundColor:'#111'`.** Three-way alignment confirms no visual
   flash when the scene mounts. `ui.md` reflects this. **Severity:
   none.**

## `ar_hook.md` handshake audit (from UC-01-recommendation viz)

The UC-01-recommendation `viz/ar_hook.md` drafted a forward-compat
handshake targeted at THIS task. Cross-check against the actual
implementation:

| `ar_hook.md` claim | Honored? | Notes |
|--------------------|----------|-------|
| `furnitureId` threaded through to AR | **YES** | `InboundARLoadMessage.furnitureId`; sourced from `route.params.item.furnitureId` |
| `type` (desk/bed/chair/lighting) threaded through | **YES** | used to resolve `MODEL_URL_BY_TYPE` + `DEFAULT_DIMENSIONS_BY_TYPE` |
| Dimensions (`widthCm` / `lengthCm` / `heightCm`) read from the catalog row | **NO — v1 stubs via per-type defaults.** | `ar_hook.md §1` said "the AR preview will read these from a separate lightweight endpoint — `GET /api/v1/furniture/{id}`". The actual v1 implementation does NOT make that call; it uses `DEFAULT_DIMENSIONS_BY_TYPE` per FR-4 / D-2. Unit is METRES not centimetres (`widthM` not `widthCm`). |
| `colorHex` field reserved | **YES, sent as `null`** in v1 (FR-4). The bridge field exists on the inbound message; a future task can populate it from the catalog. |
| Unity scene graph (`ARSession` / `ARSessionOrigin` / `FurniturePlacementController`) | **NO — Option C picked per D-1.** | The task picked `<model-viewer>` over Unity UaaL. The `unity_future_v2.md` in this directory preserves the scene-graph plan for the v2 task. |
| Touch-to-place C# outline | **NO — superseded.** | `<model-viewer>`'s built-in AR overlay handles native-side placement; the HTML scene simply posts `{event:'placed', pose:{0,0,0,0}}` when the user confirms via the overlay button. |
| Unity-bound `FurnitureItem` snapshot over RN bridge | **Partial.** | The v1 bridge carries the equivalent payload (`ArLoadModelMessage` with `furnitureId`, `modelUrl`, `roomDimensions`, `colorHex`) — shape is compatible with a v2 Unity-engine swap. Field NAMES differ from `ar_hook.md`'s sketch (`widthM` vs `widthCm`) because units were chosen for model-viewer's metric default. |
| `bridgeVersion` field on every message | **YES — `bridgeVersion: 1`** on every inbound and outbound. Not explicitly named in `ar_hook.md` (v1 addition, forward-compatible). |

**Net:** the `ar_hook.md` handshake's SHAPE is honored — per-type
model resolution, `furnitureId` + dimensions + `colorHex` on the
inbound payload, bridge-based handoff — but the implementation took
D-1 + D-2 shortcuts (WebView instead of Unity, per-type defaults
instead of catalog dimensions). The v2 task (`unity_future_v2.md`)
closes both gaps while preserving `bridgeVersion:1`.

## Conventions

- Korean UI strings are quoted verbatim from the source files — DO NOT
  normalize spacing or punctuation.
- `testID=...` tokens are quoted verbatim from the screen files so the
  verification tests can cross-reference them.
- Mermaid `sequenceDiagram` uses `<br/>` for line breaks inside
  participant labels (Mermaid does not support `\n` in that position).
- State-name casing in the diagrams follows the SOURCE enum
  (`'loading' | 'live' | 'placed' | 'cancelled' | 'error'`) — NOT
  the task-brief draft's SCREAMING_SNAKE variants.
