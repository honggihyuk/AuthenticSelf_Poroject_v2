# UC-01-recommendation — viz artifact index

This directory contains the visualization artifacts produced by the
visualization-specialist for Task 5 (UC-01-recommendation). All diagrams
are inline Mermaid / ASCII — no external renderers or image hosts.

## Files

| File              | Type                              | Primary FR/AC anchors |
|-------------------|-----------------------------------|-----------------------|
| `sequence.mmd`    | Mermaid `sequenceDiagram`         | end-to-end RN -> Spring -> Python -> MySQL including cache HIT, NO_FIT, and 502 branches; FR-11..FR-25 |
| `architecture.mmd`| Mermaid `flowchart LR`            | component diagram with DTO / route / table annotations on every edge; FR-13, FR-14, FR-19, FR-25 |
| `scoring_flow.mmd`| Mermaid `flowchart TD`            | per-item cross-validation scorer: size/style/color/conflict gates, NO_FIT decision; FR-5..FR-10, FR-12 |
| `ui.md`           | ASCII + Mermaid state diagram     | RecommendationScreen states: loading / normal / empty-section / NO_FIT / error; FR-23, AC-44..AC-50 |
| `er.mmd`          | Mermaid `erDiagram`               | recommendation slice of the DB schema: furniture V4/V5 columns + spaces read-only columns; FR-15, FR-16, FR-17 |
| `ar_hook.md`      | narrative + ASCII scene graph     | forward-compatibility handshake for Task 8 (AR-furniture-placement) |
| `README.md`       | this file                         | index + open questions |

## Diagram coverage vs. spec

- **Sequence:** fully visualized — happy path (cache MISS then HIT),
  PREFERRED_STYLE_NOT_SET 409, ANALYSIS_NOT_READY 409, SPACE_NOT_FOUND /
  ACCESS_DENIED, INVALID_TOP_N 400, 502 AI_SERVICE_UNAVAILABLE,
  NO_FIT_ANY_CATEGORY 200+warning, wishlist stub.
- **Architecture:** fully visualized — all new Spring/Python modules
  (RecommendationOrchestrator, RecommendationClient, route+scorer+schemas,
  V4/V5 migrations) colored as NEW, existing modules colored as REUSED.
- **Scoring:** the four sub-scores + aggregate + rationale + NO_FIT are
  all on one page with FR/AC legend.
- **UI:** loading / normal / empty-section / NO_FIT / four error flavours
  + wishlist toast + state diagram; every testID in the screen file is
  referenced.
- **ER:** V4 delta + V5 seed row count (28) are explicit; the read-only
  vs. write columns on `spaces` are annotated; cardinalities from PRD §7
  are preserved.

## Open questions carried from `design.md §7` / `§6`

The verification-specialist should treat these as non-blockers but surface
them in its report:

1. **`preferredStyle` as OPTIONAL route param (not required).** `FR-24`
   asks for a required param; the design kept it optional to avoid
   regressing Task-4's `replace('Recommendation', { roomId })` test.
   Screen prefers the server-echoed `preferredStyle` in
   `RecommendationResponse`. See `ui.md §10`. Non-blocking.

2. **Correlation-ID propagation Spring -> Python.** NFR calls for an
   `X-Correlation-Id` header; Python logs currently use `roomId` as a
   proxy. Flagged as a future infra task.

3. **`space_detected_objects` not yet persisted.** Spring sends
   `Collections.emptyList()` for `space.detectedObjects` per FR-18 step 8.
   The Python scorer handles this correctly (every `objectConflict` is
   `1.00`). A future `UC-01-detected-objects-persist` task will flip the
   Spring side from `emptyList()` to a DB read — no Python change
   required.

4. **Catalog pagination.** `FurnitureRepository.findAll()` returns all
   28 rows (well under the 500-row to-thread cutoff). Production with a
   catalog of 500+ items would need paginated retrieval + per-batch
   scoring. Listed in spec §8 out-of-scope.

## Inconsistencies flagged during diagramming

- **None load-bearing.** One editorial note: `design.md §2.2` describes
  the cache as a `ConcurrentHashMap` with explicit TTL semantics — the
  source at `RecommendationOrchestrator.java` matches this exactly
  (`Map<CacheKey, CachedEntry>` with `expiresAt` field). Verified.

- **V5 row count: 28 (not 24).** `spec.md` + `design.md §6` explicitly
  note 28 rows; the source file has exactly 28 `INSERT ... VALUES`
  row tuples (verified via `grep '^    (''f_'`). Strictly additive to
  AC-25's `>= 24` bar; AC-26's oversize + clashing-color coverage is
  preserved.

- **`Furniture` entity keeps Task-1 `style` and `size` columns alongside
  the new `styleTags` CSV.** Confirmed in `Furniture.java`. The scorer
  reads `styleTagsList()` and ignores `style` / `size`; this is
  intentional (V1 baseline columns are preserved for backward
  compatibility with any yet-to-be-written Task-1 consumers).
