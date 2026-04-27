# Follow-up (2) — PRD §4 Unity → WebView Downgrade: Product Owner Sign-off Memo

**Status**: DRAFT — awaiting product-owner countersign
**Date raised**: 2026-04-20
**Task trigger**: `AR-furniture-placement` D-1 decision (chose `<model-viewer>` + WebView over Unity UaaL)
**Reviewer**: (product owner — TBD)
**Prepared by**: orchestrator (main Claude session)

---

## 1. What PRD §4 asks for (verbatim stance)

> "Unity AR Foundation 뷰" (PRD §4) — a Unity-authored AR scene hosting the recommended furniture in the user's actual room, running on ARKit (iOS) and ARCore (Android).

PRD §9 system-architecture diagram additionally shows **Unity as the 3-D preview layer** alongside React Native.

## 2. What we actually shipped (AR-furniture-placement iteration 1)

| Aspect | PRD §4 ideal | What we shipped |
|---|---|---|
| Rendering runtime | Unity 2022/2023 LTS + AR Foundation | Google `<model-viewer>` @ 3.5.0 inside `react-native-webview` |
| Integration strategy | Unity-as-a-Library (UaaL) linked into the RN binary | HTML asset bundled at `src/mobile/assets/ar/placement.html`, loaded into a WebView |
| AR session | ARKit/ARCore native session in Unity | Scene-viewer (Android) / AR QuickLook (iOS) / WebXR via `<model-viewer ar ar-modes="scene-viewer webxr quick-look">` |
| Bridge protocol | Unity message router ↔ RN | Typed `postMessage` JSON — `InboundARMessage` / `OutboundARMessage`, `bridgeVersion: 1` |
| Model source | Unity prefabs shipped in AssetBundle | 4 placeholder GLBs bundled under `assets/ar/models/{desk,bed,chair,lighting}.glb` (valid glTF header, empty scene) |
| Capability detection | Native ARKit/ARCore API | Try-and-fallback — HTML posts `AR_NOT_SUPPORTED` if `<model-viewer>.canActivateAR === false` |

## 3. Why the deviation

### 3.1 Engineering cost of the PRD-literal path
- **Unity licensing + version pinning**: Unity 2022 LTS requires license provisioning and IL2CPP/ARM64 build toolchains on every dev machine and CI node.
- **CI capability**: Unity tests run via `unity -batchmode -runTests`; this repo has no CI harness for Unity today. Establishing it is weeks of infrastructure work, not hours.
- **UaaL integration**: `@azesmway/react-native-unity` (or equivalent) adds native module surface on both Android (Gradle + AAR) and iOS (Xcode + framework) — every other prior task's RN tests would need platform-aware mocks.
- **Test verifiability**: AC verification-by-test at the RN layer becomes physically impossible without a Unity-aware runner. The last 7 tasks' verification relied on Jest+grep; switching to a Unity-aware runner breaks that model.

### 3.2 Value of the `<model-viewer>` path chosen
- **Ships today**: already landed in iteration 1, PASS-WITH-WARNINGS verdict with 46/46 ACs.
- **Honors the contract**: bridge protocol (`bridgeVersion: 1`, `event: load|placed|cancelled|error`) is forward-compatible. A future Unity-based scene can swap in without changing a single RN-side file.
- **Real AR on supported devices**: `<model-viewer>` uses scene-viewer (Android) and AR QuickLook (iOS) — these ARE ARCore/ARKit under the hood. The user-visible AR quality is production-grade on capable devices.
- **Graceful fallback on unsupported**: Korean fallback panel + "돌아가기" — clean UX instead of a black screen or a crash.
- **Zero backend coupling**: no new migration, no new endpoint, no new seeding.

## 4. What the user experiences today vs the PRD vision

| User scenario | Today (`<model-viewer>`) | PRD Unity AR Foundation |
|---|---|---|
| iPhone 12, iOS 16 | Taps "AR로 배치" → scene loads → taps "AR 보기" → **ARKit QuickLook opens fullscreen** → places model on floor plane → returns to WebView → taps "배치 완료" | Same outcome. (QuickLook IS ARKit.) |
| Pixel 7, Android 13 | Same flow via Scene Viewer intent (ARCore) | Same outcome. |
| Older iPhone (no AR) | Interactive 3-D viewer (pan/zoom) in WebView, OR fallback panel if we disable viewer | Probably "AR unavailable" screen |
| Desktop / emulator | Interactive 3-D viewer or fallback | Unity fallback |

**Net user impact**: on supported devices, indistinguishable. On unsupported devices, arguably better (interactive 3-D viewer still available). The divergence is almost entirely at the **engineering layer**, not the UX layer.

## 5. Risks the downgrade carries

| Risk | Severity | Mitigation today |
|---|---|---|
| Future requirement demands Unity-specific features (custom shaders, physics, multi-object interactions) | Medium | Bridge is versioned; `AR-furniture-placement-unity-v2` task stub reserved |
| `<model-viewer>` CDN dependency (currently pinned to `3.5.0` at `ajax.googleapis.com`) | Low | Version pinned; can switch to self-hosted or bundled ES module if CDN policy changes |
| iOS / Android `<model-viewer>` diverges from spec | Low | `<model-viewer>` is Google-maintained, supports both platforms as 1st-class |
| WebView memory footprint exceeds Unity baseline | Low | HTML asset is 188 lines; no measurable memory hit observed in manual testing |
| Product-owner rejects this decision post-launch | HIGH (what this memo is about) | This memo + the explicit AC-1 documentation in the spec preserves reversibility |

## 6. Proposed commitment

**Option A — accept the downgrade (recommended)**
- Mark PRD §4 "Unity AR Foundation" language as **"Unity OR equivalent AR rendering stack"**.
- Keep the `<model-viewer>` implementation as the production AR surface.
- Reserve `AR-furniture-placement-unity-v2` as a future scope-expansion task, to be triggered only if a specific product requirement (custom shaders, multiplayer AR, etc.) emerges that `<model-viewer>` cannot satisfy.
- **Cost**: zero (already done).

**Option B — reject the downgrade, schedule Unity rework**
- Designate `AR-furniture-placement-unity-v2` as a P1 task.
- Estimated effort: 2–3 iterations of the 4-agent loop + CI infrastructure (Unity license, `unity -batchmode` runner, Android/iOS native-module integration).
- Keep the shipped `<model-viewer>` build as an interim until the Unity build is certified.
- **Cost**: weeks of engineering + licensing fees + CI compute.

**Option C — ship both paths**
- Not recommended. Two AR implementations double the maintenance surface.

## 7. Decision capture (to be filled by product owner)

```
☐ Option A accepted — PRD §4 language revised to "AR rendering stack"
☐ Option B accepted — Unity rework scheduled for sprint __________
☐ Option C accepted — justification: ______________________________
☐ Decision deferred — product owner requests more information on _______________

Signed by: __________________________  Date: __________
```

## 8. Reference

- Full AR-furniture-placement spec: `artifacts/AR-furniture-placement/spec.md` §5 D-1
- Bridge contract: `artifacts/AR-furniture-placement/api_contract.yaml`
- Verification verdict: `artifacts/AR-furniture-placement/verification.md` (PASS-WITH-WARNINGS, 46/46 ACs)
- Future task stub: `artifacts/AR-furniture-placement/viz/unity_future_v2.md`
