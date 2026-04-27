# UC-03-admin-overview — viz artifact index

Visualization artifacts for Task 7 (UC-03-admin-overview). All diagrams
are inline Mermaid / Markdown — no external renderers or image hosts.
Style and naming follow `artifacts/UC-02-wishlist/viz/` and
`artifacts/UC-01-recommendation/viz/`.

## Files

| File                   | Type                            | Primary FR/AC anchors |
|------------------------|---------------------------------|-----------------------|
| `sequence.mmd`         | Mermaid `sequenceDiagram`       | Four flows on one canvas: (A) happy path `/overview` with full fan-out to 14 aggregation queries; (B) missing `X-User-Id` → 400 `MISSING_USER_HEADER`; (C) unknown user → 404 `USER_NOT_FOUND`; (D) non-admin → 403 `NOT_ADMIN` and invalid window → 400 `INVALID_TIME_WINDOW`; FR-3..FR-11 / AC-7 / AC-8 / AC-9 / AC-11 / AC-26 / AC-27 / AC-32 / AC-33 / AC-53 |
| `architecture.mmd`     | Mermaid `flowchart LR`          | HTTP client → `AdminController` → `AdminAuthorizer` / `TimeWindow` / `WindowResolver` → `AdminOverviewService` → `AdminOverviewRepository` → MySQL tables; NEW Task-7 modules dashed-blue; REUSED Task-1..6 modules grey; RN admin / admin SPA drawn as red "NOT BUILT" placeholders (D-2); FR-1..FR-15 |
| `aggregation_flow.mmd` | Mermaid `flowchart TB`          | Per-tile (Users / Rooms / Wishlist / Sales) fan-out showing which @Query method runs, how `ws` flows through `(:windowStart IS NULL OR col >= :windowStart)`, zero-fill + densification, conversion-rate divide-by-zero guard, snapshot-price invariant; FR-5..FR-10 / AC-15 / AC-17..AC-25 / AC-31 / AC-35 / AC-50 |
| `state_machine.mmd`    | Mermaid `stateDiagram-v2`       | `REQUEST_RECEIVED → HEADER_CHECK → USER_LOOKUP → ROLE_CHECK → AUTHORIZED → WINDOW_PARSE → SERVICE_CALL` with each of the 4 terminal rejections (401/404/403/400) and test-method pins; AC-7 / AC-8 / AC-11 / AC-26 / AC-27 |
| `ui.md`                | curl + JSON samples             | NO GUI mock (per D-2). curl invocations for each endpoint × each window; pretty-printed JSON happy-path and error-envelope samples; Postman outline; future-client block marked NOT BUILT; FR-4..FR-8 / AC-9..AC-25 / AC-47 |
| `er.mmd`               | Mermaid `erDiagram`             | V7 delta on `users` (new `role` column + `idx_users_role`), plus existing `spaces` / `wishlist` / `furniture` columns that aggregation queries READ; per-column [AR/AW/OW] access annotations; PRD §7 cardinalities preserved; FR-1 / FR-9 / AC-1..AC-6 |
| `README.md`            | this file                       | Index + deltas spotted + design-specialist deviations carried forward + open questions |

## Diagram coverage vs. spec

- **Sequence:** fully visualized end-to-end. Covers happy path with the
  full 14-query fan-out (4 tiles in `par` blocks) plus the four rejection
  paths. All four `AdminErrorCode` values surface on the canvas with
  their HTTP status, Korean wire message, and correlationId UUID.
- **Architecture:** fully visualized. Every NEW admin-package class /
  DTO / migration is dashed-blue; reused beans (UserRepository,
  ErrorResponse record) are grey. D-2's deferral is drawn as two red
  "NOT BUILT" nodes with dashed, non-wired edges to the contract.
- **Aggregation flow:** fully visualized. All 14 repository methods
  (including the two `min*At` helpers for `window=ALL` densification)
  appear exactly once, wired to their service-layer composition. The
  AC-23 snapshot-price invariant is called out on the sales subgraph.
- **State machine:** fully visualized. Each transition carries the test
  method name that pins it (AC-7 / AC-8 / AC-11 / AC-26 / AC-27). The
  post-auth `WINDOW_PARSE` stage is included so the ordering guarantee
  "bad window on non-admin caller returns 403, not 400" is explicit.
- **UI (HTTP sample):** every endpoint × every window value has a curl
  sample and JSON body. Every `AdminErrorCode` has an error-envelope
  sample. Per D-2 there is no screen mock.
- **ER:** V7 delta + `idx_users_role` + every column's admin-package
  access class [AR/AW/OW] are annotated. PRD §7 cardinalities
  (User 1—0..* Space, User 1—0..* Wishlist, Wishlist 0..*—1 Furniture)
  are preserved.

## Deltas spotted between design.md / api_contract.yaml and the actual source

Drawing the diagrams surfaced the following mismatches. Per the task
rule ("trust the source"), the diagrams follow the source and I record
the deltas here for the verification-specialist.

1. **`UsersTileResponse` Javadoc claims `@JsonUnwrapped` but the record
   does NOT use it.** The Javadoc at `UsersTileResponse.java:10-17`
   says "tile fields are flattened at the top level via
   `@JsonUnwrapped`", but the record instead declares the seven fields
   directly (`window, generatedAt, totalUsers, totalAdmins, newSignups,
   activeUsers, signupsByDay`) and the `of(...)` factory copies from
   `UsersTile`. Net wire shape is identical to the intent — the
   diagrams reflect the factual record shape. **Severity: doc-comment
   only.** (Same pattern applies to the other three `*TileResponse`
   records, though their Javadoc is less explicit.)

2. **`Space.Status` enum values are `{PENDING_ANALYSIS, ANALYZED, FAILED}`
   (3 values)** — matches api_contract.yaml's `SpaceStatus` enum schema
   exactly. The spec FR-6 narrative illustrates
   `{UPLOADED, ANALYZING, ANALYZED, ANALYSIS_FAILED}` (4 values), but
   the spec's own §3 Preconditions explicitly flags the aliasing and
   AC-16 / AC-34 ground-truth the test against
   `Space.Status.values()` rather than a hard-coded string list.
   Diagrams (architecture.mmd, ui.md samples, er.mmd) use the
   **actual 3-value enum**. **Severity: spec narrative vs source
   intentional — covered by AC-34.**

3. **`AdminOverviewService` depends on `Style` from
   `com.authenticself.space.Style`, NOT a new enum.** The design.md
   §"Aggregation queries" description mentions `Style` as if it is in
   the admin package; the source imports
   `com.authenticself.space.Style` and uses `Style.values()` +
   `Style.fromNullable(...)`. Architecture diagram correctly shows
   `Style` as reused from UC-01. **Severity: doc wording.**

4. **`AdminOverviewRepository` declares TWO extra helper methods
   beyond the FR-9 enumeration of 14** — `minUserCreatedAt()` and
   `minPurchasedAt()`. Both are load-bearing for `window=ALL`
   densification (they supply the fallback earliest bound when the
   window is unbounded). AC-30 wording is permissive ("exact
   spellings permitted to vary by one underscore as long as
   semantics match"), so this is consistent with AC-30's intent but
   widens the count from 14 to 16. Diagrams show all 16 methods and
   annotate the two helpers explicitly. **Severity: spec wording.**

5. **Repository uses `List<Object[]>` return types, not `List<DateCount>`
   / `Map<Space.Status, Long>` as FR-9 wording suggests.** Design.md
   §11 calls this out as an intentional deviation ("MySQL's JDBC
   driver maps DATE / SUM with broader runtime types than an
   interface projection accepts"). The service layer does the type
   coercion via `asString` / `asLong` / `asLocalDate` helpers. The
   aggregation-flow diagram reflects the `Object[]` return shape on
   every `*Raw` method. **Severity: intentional deviation,
   already documented in design.md §11.**

6. **`AdminOverviewRepository extends JpaRepository<User, String>`
   without a declared marker entity.** The design.md §11 flags this as
   a non-blocker — the `User` root is never used for CRUD, but Spring
   Data requires a concrete root for proxy generation. Architecture
   diagram notes this on the `Repo` node. **Severity: intentional
   deviation, already documented.**

7. **`MISSING_USER_HEADER` string value is duplicated across
   `AdminErrorCode`, `WishlistErrorCode`, and `SpaceErrorCode`** — by
   design (UC-02-wishlist D-5 pattern carried forward). The
   architecture diagram labels the `AdminErrorCode` node with
   "(reused value)" for MISSING_USER_HEADER and USER_NOT_FOUND,
   "(NEW)" for INVALID_TIME_WINDOW and NOT_ADMIN. **Severity:
   intentional deviation, already documented in design.md §11.**

8. **`AdminExceptionAdvice.handleMissingHeader` is reachable only
   defensively.** Because `AdminController` declares
   `X-User-Id` as `required=false` AND calls `authorizer.requireAdmin`
   as its first statement, the framework-level
   `MissingRequestHeaderException` never fires in production — the
   in-method check throws `AdminException(MISSING_USER_HEADER)` first.
   Same pattern as UC-02-wishlist's `WishlistExceptionAdvice`. Sequence
   diagram shows only the `requireAdmin` path; architecture diagram
   marks the framework handler as "defensive-only" in the Javadoc
   quoted on the node. **Severity: non-blocker, matches sibling
   convention.**

9. **Integration-test seed size is 10% of the NFR target.** Design.md
   §"Test-only admin seed" / §9 uses 100 users / 500 spaces / 1000
   wishlist rows for integration tests, while the NFR P95 target
   (AC-39) is measured against 1000 / 5000 / 10000. The ui.md sample
   bodies use the larger NFR figures; the verification script will
   drive either size depending on the test class. **Severity:
   intentional — the 10% integration-test fixture keeps the test
   runtime manageable.**

## Open questions carried from design.md §10

Non-blocking per design-specialist — the verification-specialist
should surface them in its report but NOT gate on them.

1. **Active-user definition** (spec §11.1) — current implementation
   uses "uploaded a photo within the window" (filter on
   `spaces.uploaded_at`). Alternative "uploaded OR wishlisted OR
   purchased" is a single SQL change in `countActiveUsers` + adds two
   UNION legs. Flagged for product-owner review; no AC depends on the
   alternative definition.

2. **Sales — snapshot vs catalog price** (spec §11.2) — current
   implementation uses `wishlist.price` (snapshot at add-time).
   Swapping to `furniture.price` is a single SQL change in
   `sumSalesKrw` + `sumSalesByCategoryRaw`. AC-23 pins the snapshot
   semantics.

3. **Admin self-visibility in Users tile** (spec §11.3) — `totalUsers`
   counts `role='USER'` rows only; `totalAdmins` counts
   `role='ADMIN'`. If the product owner wants `totalUsers` to include
   admins, AC-12's fixture changes by one line.

4. **Top-N color bucketing** (spec §11.4) — `spaces.main_color` is a
   hex string. Near-identical hex values count distinct. Clustered
   bucketing is out of scope for this iteration.

## Conventions

- Korean wire messages are quoted verbatim from `AdminErrorCode.java`
  — DO NOT normalize spacing or punctuation.
- Repository method names are quoted verbatim from
  `AdminOverviewRepository.java` (including the `_Raw` suffix that
  design-specialist used for pivot queries).
- Mermaid `sequenceDiagram` uses `<br/>` for line breaks inside
  participant labels (Mermaid does not support `\n` in that position).
- Every diagram legend is co-located (inside the diagram or as a
  trailing comment block).
- No emojis are used anywhere.

## Iteration 2 delta

No diagram redraw needed; architecture unchanged. The 5 surgical edits
touch only test-harness plumbing and one collection-type declaration —
no endpoint, DTO, error code, sequence step, aggregation edge, or ER
column was affected. `sequence.mmd`, `architecture.mmd`,
`aggregation_flow.mmd`, `state_machine.mmd`, `ui.md`, and `er.mmd`
remain accurate as-drawn.

### The 5 surgical edits

| # | File : line | Before | After | Diagram impact |
|---|-------------|--------|-------|----------------|
| 1 | `AdminOverviewIntegrationTest.java` : was 108 | `Random rng = new Random(42);` | (deleted) | none — test-only dead code, never appeared on any diagram |
| 2 | `AdminOverviewIntegrationTest.java` : was 99 | `@Transactional` on `seed()` | (annotation removed) | none — `@BeforeAll` seed commit is a test-harness concern, not part of the request flow |
| 3 | `AdminOverviewService.java` : 315 | `Set<String> CATEGORY_KEYS = Set.of(...)` | `List<String> CATEGORY_KEYS = List.of(...)` | none — `aggregation_flow.mmd` shows the 4 categories (`desk, bed, chair, lighting`); determinism guarantee is now source-true |
| 4 | `AdminOverviewService.java` : was 350 | `if (v instanceof Date d) return d.toLocalDate();` (bare `Date` branch) | (branch deleted; fully-qualified `java.sql.Date` branch kept) | none — `asLocalDate` is an internal type-coercion helper; `aggregation_flow.mmd` never unpacked it |
| 5 | (verification only) `acceptance_criteria.json` vs test file | n/a | pointers `p95Latency_ac39` @ line 200 and `determinism_ac50` @ line 224 confirmed matching | none |

### Iteration-1 deltas — status after iteration 2

- **RESOLVED by Edit 3:** Delta 4-aliased concern "`Set.of(...)` for
  `CATEGORY_KEYS` — iteration-order non-determinism across JVM
  restarts". Now a `List.of(...)` with a fixed element order
  (`desk, bed, chair, lighting`); deterministic iteration is
  source-guaranteed, not implementation-accidental.
- **RESOLVED by Edit 4:** any concern tied to the dead `bare-Date`
  `instanceof` branch in `asLocalDate()` (duplicate of the
  preceding `java.sql.Date` branch due to `import java.sql.Date`
  aliasing `Date`). The duplicate branch is gone; one `Date`
  `instanceof` check remains.
- **RESOLVED by Edit 1:** any concern about the unused
  `Random rng = new Random(42)` in the integration test fixture
  (deterministic seed was declared but never consumed).
- **RESOLVED by Edit 2:** seeded rows now persist past `@BeforeAll`
  because the method-level `@Transactional` that rolled them back is
  removed; `salesUsesSnapshotPrice_ac23`'s method-level
  `@Transactional` is deliberately retained (correct scope for an
  UPDATE-then-GET write-side assertion).
- **PRESERVED per AC-60 (still listed above, unchanged):**
  Delta 1 (`UsersTileResponse` Javadoc vs flat record — doc-comment
  only); Delta 2 (`Space.Status` 3-value enum vs spec narrative's
  4 values — covered by AC-34); Delta 3 (`Style` lives in
  `com.authenticself.space`, not `admin` — doc wording); Delta 4
  (repository has 16 methods vs FR-9's stated 14 — two `min*At`
  helpers for `window=ALL` densification; intentional);
  Delta 5 (`List<Object[]>` returns vs projection interfaces —
  intentional JDBC pragma, design.md §11); Delta 6
  (`JpaRepository<User, String>` marker-entity pattern — intentional);
  Delta 7 (`MISSING_USER_HEADER` string duplication across sibling
  error enums — intentional, carries UC-02-wishlist D-5 pattern);
  Delta 8 (framework-level `handleMissingHeader` is defensive-only —
  non-blocker); Delta 9 (integration-test seed is ~10 % of NFR
  target — intentional; separate test class drives the full NFR).
