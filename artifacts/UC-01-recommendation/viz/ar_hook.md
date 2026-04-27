# AR placement hook — forward-compatibility note

**Consumer:** Task 8 (`AR-furniture-placement`) — not implemented here.
This file documents the handshake between the Task-5 recommendation output
and the Task-8 AR preview so the downstream agent can diagram and wire
its scene config without re-reading this task's source.

**PRD refs:** PRD §4 (Unity AR Foundation), PRD §6 UC-01 "미리보기" aspiration.

---

## 1. Data contract exposed by this task

Each `RecommendationItem` in the Spring response already carries everything
the AR scene needs to load and scale a 3-D placeholder of the furniture:

| field          | type                  | AR usage                                       |
|----------------|-----------------------|------------------------------------------------|
| `furnitureId`  | `string`              | Stable key -> maps to `Resources/furniture/{id}.prefab` OR `{id}.glb` |
| `name`         | `string`              | Label panel above the placed model              |
| `type`         | `desk`\|`bed`\|`chair`\|`lighting` | Decides default placement height / anchor type |
| `price`        | `integer KRW`         | Label panel (same format as the card, `₩` prefix) |
| `imageUrl`     | `string \| null`      | Thumbnail on the preview chip (fallback to grey placeholder when null) |
| `fitScore`     | `float`               | Badge "매칭 NN%" rendered as a billboard on the model |
| `scoreBreakdown.*` | four floats       | Optional debug overlay (not in v1) |
| `rationale`    | `string` (Korean ≤120) | Caption under the label panel |

Plus, the **catalog row itself** (persisted by V4/V5) carries the physical
dimensions:

| catalog field | type        | AR usage                                                 |
|---------------|-------------|----------------------------------------------------------|
| `widthCm`     | `integer`   | `Vector3.x` of the placeholder mesh (÷100 for metres)    |
| `lengthCm`    | `integer`   | `Vector3.z` (÷100 for metres)                            |
| `heightCm`    | `integer`   | `Vector3.y` (÷100 for metres)                            |
| `colorHex`    | `"#RRGGBB"` | Tint applied to the placeholder material (fallback when no textured prefab exists) |

The AR preview will read these from a **separate** lightweight endpoint — NOT
from the recommendation payload — so the mobile list doesn't have to round-trip
the full catalog. Suggested endpoint (for Task 8 to implement, out-of-scope
here):

```
GET /api/v1/furniture/{furnitureId}
-> { furnitureId, name, type, widthCm, lengthCm, heightCm,
     colorHex, imageUrl, price }
```

Backed by `FurnitureRepository.findById(...)`.

---

## 2. Recommended scene graph (Task 8 will actually build this)

```
UnityARScene
 ├─ ARSession
 ├─ ARSessionOrigin
 │   ├─ ARCamera
 │   ├─ ARPlaneManager           (detect floor / wall planes)
 │   └─ ARRaycastManager
 ├─ FurniturePlacementController (MonoBehaviour)
 │   · input: { furnitureId, widthCm, lengthCm, heightCm, colorHex }
 │   · on tap -> Raycast against ARPlaneManager
 │   · on hit -> Instantiate FurnitureAnchor prefab at hit.pose
 ├─ FurnitureAnchor              (template prefab)
 │   · MeshFilter / MeshRenderer (swap with per-type mesh at runtime)
 │   · BillboardLabel (name + ₩price + 매칭 NN%)
 │   · scale.x = widthCm / 100f
 │   · scale.y = heightCm / 100f
 │   · scale.z = lengthCm / 100f
 │   · material.color = HexToColor(colorHex)
 └─ UIOverlayCanvas
     · "다시 측정" button           (re-raycast / clear placements)
     · "삭제" button per anchor
```

**Scale factor:** uniform cm→m (`scale = dim / 100f`) matches the V5 seed
data which lists dimensions in centimetres for the scorer. Unity's world is
in metres, so no additional conversion beyond ÷100 is required.

**Anchor-type defaults by `type`:**
- `desk`, `bed`, `chair` -> snap to horizontal plane (floor).
- `lighting` -> snap to nearest vertical plane OR a raised floor-plane
  offset, depending on the specific fixture (Task 8's heuristic).

---

## 3. Touch-to-place interaction outline

```csharp
// Pseudo-code for Task 8 (illustrative only; not compiled here).
void Update() {
    if (Input.touchCount == 0) return;
    var touch = Input.GetTouch(0);
    if (touch.phase != TouchPhase.Began) return;

    if (_raycastManager.Raycast(touch.position, _hits, TrackableType.PlaneWithinPolygon)) {
        var pose = _hits[0].pose;
        var anchor = Instantiate(FurnitureAnchorPrefab, pose.position, pose.rotation);
        anchor.ApplyFurniture(current);      // sets scale + tint + label
    }
}
```

The `current` payload is populated by the RN bridge sending a
`FurnitureItem` snapshot over to Unity via the existing Unity-as-a-Library
plumbing (out of scope for this task).

---

## 4. Handoff checklist for Task 8 viz

The Task 8 `visualization-specialist` invocation should:

1. Reuse the field mapping in §1 verbatim.
2. Reuse the scene graph in §2 as the starting point and extend it with
   the lighting/wall heuristic.
3. Add the touch-to-place sequence diagram.
4. Diagram the bridge between RN `RecommendationScreen` ("AR 미리보기"
   button — not present yet) and the Unity scene's inbound message.

No source file in THIS task is modified by Task 8 — the AR preview is a
net-new screen + Unity scene, not a mutation of `RecommendationScreen`.
