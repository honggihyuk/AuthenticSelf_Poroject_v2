# AR placeholder GLB models

These four binaries are **placeholders** committed for the
`AR-furniture-placement` task (iteration 1). Each file is a
spec-conformant but near-empty glTF-binary container:

- 12-byte GLB header (magic `glTF`, version 2, total length)
- one JSON chunk with `{ asset, scene: 0, scenes: [{ nodes: [] }] }`

They load in `<model-viewer>` without error but render nothing
visible. This is intentional — the AR test surface only needs to
confirm that the scene reaches the `load` event + the `btn-place`
overlay becomes enabled. Actual 3-D content is a product-side asset
decision.

## Upgrade path

Two future replacements, in priority order:

1. **Production placeholder models** — a designer-supplied, type-
   correct placeholder per `FurnitureType` (desk / bed / chair /
   lighting) with a baked neutral-grey material. Drop-in replace each
   file; no code change.

2. **Catalog-driven models** — a future task
   `AR-furniture-placement-unity-v2` adds a `furniture.model_url`
   column (V8 migration). `ARPlacementScreen.tsx` then resolves
   `modelUrl` from the `RecommendationItem` payload instead of the
   per-type lookup table. Zero message-bridge contract change
   (`modelUrl` is already a first-class field in `InboundARMessage`).

## Filenames (canonical)

| Type       | Path                                         |
|------------|----------------------------------------------|
| `desk`     | `src/mobile/assets/ar/models/desk.glb`       |
| `bed`      | `src/mobile/assets/ar/models/bed.glb`        |
| `chair`    | `src/mobile/assets/ar/models/chair.glb`      |
| `lighting` | `src/mobile/assets/ar/models/lighting.glb`   |

`ARPlacementScreen.ts` maps `RecommendationItem.type -> these paths`
via the `MODEL_URL_BY_TYPE` table. Changing a filename requires
updating both ends.
