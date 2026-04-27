# Task Spec: UC-03-admin-overview

## Iteration 2 delta

Iteration 1 landed 49/53 ACs PASS; one compile-blocker in `AdminOverviewIntegrationTest.java` (a dead `Random rng = new Random(42);` declaration with no `import java.util.Random;`) collapsed AC-23/AC-35/AC-39/AC-50 and short-circuited the backend integration suite. Iteration 2 is surgical: it fixes the blocker plus four non-blocking cleanups that the verification agent surfaced while the file was open. No existing FR (FR-1..FR-15) and no existing AC (AC-1..AC-53) is renumbered, deleted, or re-scoped. Only AC-39 and AC-50 get a single field update (`target_file_or_test`) per FR-20; everything else in iteration 2 is append-only (new FR-16..FR-20, new AC-54..AC-60).

**The 10 iteration-1 design choices explicitly preserved** (see AC-60 for the guard): marker-entity pattern on `AdminOverviewRepository extends JpaRepository<User, String>`; `MISSING_USER_HEADER` enum constant duplicated across Admin/Space/Wishlist error-code enums (wire-value byte-identical, intentional per D-5 loose-coupling); integration-test seed at ≈10% of NFR scale (100u/500s/1000w vs 1000u/5000s/10000w — full-scale latency harness is a separate future task); `UsersTileResponse` `@JsonUnwrapped` Javadoc vs flat record (cosmetic; wire shape correct); `Space.Status` 3-value enum (`PENDING_ANALYSIS`/`ANALYZED`/`FAILED`) vs FR-6 illustrative 4-value JSON (AC-34 defers to the runtime enum); repository method count drift (FR-9 says 14, source has 17 — AC-30 permits semantic equivalence); repository returns `List<Object[]>` with service-layer pivot via `asString`/`asLong`/`asLocalDate`. Iteration 2 MUST NOT re-open any of these.

### Iteration 2 new FRs

**FR-16 (blocker B-1 — mandatory compile fix)** — The file `src/backend/src/test/java/com/authenticself/admin/AdminOverviewIntegrationTest.java` MUST compile. The dead declaration `Random rng = new Random(42);` at line ~108 MUST be removed entirely. `import java.util.Random;` MUST NOT be added (the variable is never referenced; removing the declaration is the resolution verification flagged as preferred). No other change to the file's compilation unit is permitted by this FR. Grep-verifiable (AC-54), static-compile-verifiable (AC-55).

**FR-17 (cleanup C-1 — seed commit semantics)** — `AdminOverviewIntegrationTest#seed()` (method annotated `@BeforeAll` under `@TestInstance(PER_CLASS)`) MUST persist its seeded rows such that they remain visible to subsequent `@Test` methods in the same class. Spring test transactions roll back by default, so the iteration-1 code risks losing the seed. **Mandated fix**: remove the `@Transactional` annotation from `seed()` (native INSERTs auto-commit in the absence of a transaction wrapper). Do NOT use `@Commit` as an alternative — pick one resolution and stick with it for test predictability. The change MUST be scoped to this single method; the annotation must NOT be removed from, or added to, any other test class in the `com.authenticself.admin` package. Grep-verifiable (AC-56).

**FR-18 (cleanup C-2 — deterministic iteration order)** — `AdminOverviewService.CATEGORY_KEYS` (line ~316) MUST have deterministic iteration order across JVM restarts. `Set.of(...)` randomises iteration order per JVM, which risks non-deterministic key ordering in the `categoryDistribution` / `salesByCategory` JSON objects across deployments (same-JVM determinism per AC-50 is unaffected, but cross-deploy is not). **Mandated fix**: change `private static final Set<String> CATEGORY_KEYS = Set.of("desk","bed","chair","lighting");` to `private static final List<String> CATEGORY_KEYS = List.of("desk", "bed", "chair", "lighting");`. The four values and their order are fixed: `"desk"`, `"bed"`, `"chair"`, `"lighting"`. All existing call-sites (the iteration uses it in enhanced-for loops over string keys) MUST continue to compile and operate against a `List<String>` without further refactor. Grep-verifiable (AC-57).

**FR-19 (cleanup C-3 — dead branch deletion)** — `AdminOverviewService#asLocalDate` (around lines 349-350) contains two `instanceof Date` branches where `Date` is imported on line 21 as `java.sql.Date`. The second branch `if (v instanceof Date d) return d.toLocalDate();` is dead code (identical type, already handled by the preceding `java.sql.Date` branch). **Mandated fix**: delete the second `if (v instanceof Date d) return d.toLocalDate();` branch entirely. Exactly one `instanceof Date` branch MUST remain in `asLocalDate`. No other change to the method. Grep-verifiable (AC-58).

**FR-20 (cleanup C-4 — acceptance-criteria target-file corrections)** — `acceptance_criteria.json` AC-39 and AC-50 have `target_file_or_test` paths that point to files (`AdminOverviewLatencyTest.java`, `AdminDeterminismTest`) that do not exist; the referenced methods actually live inside `AdminOverviewIntegrationTest.java`. **Mandated fix**: update AC-39 `target_file_or_test` to `src/backend/src/test/java/com/authenticself/admin/AdminOverviewIntegrationTest.java#p95Latency_ac39`; update AC-50 `target_file_or_test` to `src/backend/src/test/java/com/authenticself/admin/AdminOverviewIntegrationTest.java#determinism_ac50`. **Do NOT change AC-39 / AC-50's `id`, `fr_ref`, `layer`, `description`, or `verify_by` fields** — semantic coverage is unchanged; only the pointer is being corrected. Self-consistency verifiable (AC-59).

### Iteration 2 scope guard

The entire iteration-2 changeset must be five surgical edits: (1) delete one dead `Random` declaration, (2) remove one `@Transactional` annotation from `seed()`, (3) swap one `Set.of` to `List.of`, (4) delete one dead `instanceof Date` branch, (5) correct two `target_file_or_test` JSON fields. Any change beyond that scope (new endpoints, new DTO fields, new error codes, new migrations, new ACs on preserved design choices, renames) is forbidden — design-specialist must stop and escalate rather than expand scope.

## 1. Goal
Close PRD §6 UC-03: give admin-role users a read-only analytics overview across four tiles — **Users / Rooms / Wishlist / Sales** — by (a) adding a V7 Flyway migration that introduces a `users.role ENUM('USER','ADMIN')` column with the bootstrap guidance for the first admin, (b) adding a single aggregation endpoint `GET /api/v1/admin/overview` (plus four per-tile endpoints for drill-down convenience) that cross-reads `users` / `spaces` / `wishlist` / `furniture`, gates on the `ADMIN` role via the existing `X-User-Id` header (403 `NOT_ADMIN` for non-admins), and supports a time-window filter (`window=LAST_7D|LAST_30D|ALL`, timezone `Asia/Seoul`), and (c) — per D-2 decision below — deferring the admin **client** surface (no RN admin screens, no admin web SPA) to a future task; this iteration is backend-only. No existing migration is touched; no UC-01 / UC-02 endpoint contract is altered for non-admin callers.

## 2. Source (PRD section)
- **PRD §2 Overview** — "관리자는 사용자 관리, 공간/위시리스트 현황, 매출 통계를 하나의 대시보드에서 확인한다."
- **PRD §6 UC-03 기본 흐름** — "관리자가 대시보드에 접속 / 시스템이 Users / Rooms / Wishlist / Sales 지표를 집계해서 보여줌 / 필터(기간/스타일 등)를 걸어 재조회." Drives FR-3..FR-7 (four tiles), FR-8 (time-window filter).
- **PRD §3 DB 설계** — `User(userId,name,email,…)`, `Space(roomId,dimensions,mainColor,style,analysisDate,…,status)`, `Furniture(furnitureId,type,style,size,price,…)`, `Wishlist(wishlistId,userId,furnitureId,category,price,status,added_at,purchased_at)`. The admin aggregations are pure `COUNT` / `GROUP BY` / `SUM` on these columns — no new business table.
- **PRD §7 클래스 다이어그램 / §2** — introduces the "Admin" role implicitly (by referring to an administrator). This spec lands the `role` column on the existing `users` row — no new `admin_users` table (see D-1).
- **PRD §4 기술 스택** — Spring Framework + MySQL backend; aggregation queries are native SQL via JPA `@Query`. No Python / AI involvement.
- **PRD §9 시스템 아키텍처** — `API Gateway & Controller (Spring)` layer only. No new external service.

## 3. Actors & Preconditions
- **Primary actor (admin user)**: an existing user row whose `role = 'ADMIN'` (after V7). Identity carried by the `X-User-Id` header — same convention as every prior task. No JWT/OAuth (out of scope, consistent with every upstream task).
- **Primary actor (backend)**: a new `AdminController` + `AdminOverviewService` under a new package `com.authenticself.admin`. Reuses `UserRepository`, `SpaceRepository`, `WishlistRepository`, `FurnitureRepository`, the shared `ErrorResponse` envelope, and the `@RestControllerAdvice` ordering pattern established by `SpaceExceptionAdvice` / `WishlistExceptionAdvice`.
- **Non-actor (client)**: no RN screen, no admin web SPA in this iteration (see D-2). The contract is consumable by any HTTP client for manual / future-task use.
- **Preconditions**:
  - Tasks 1–6 merged. V1..V6 migrations applied. All four business tables exist with the columns prior tasks specified. `users.user_id` is the shared PK.
  - `X-User-Id` header convention active on every public endpoint (shared `MISSING_USER_HEADER` error code already present in both `SpaceErrorCode` and `WishlistErrorCode`).
  - `spaces.status` enum is the 4-value set `UPLOADED | ANALYZING | ANALYZED | ANALYSIS_FAILED` per UC-01-photo-upload + UC-01-space-analysis (REST-layer UPPER_SNAKE; DB literal casing is the same — see UC-01-photo-upload FR-0 and UC-01-space-analysis's updates). *Aligned aliasing note:* UC-01-photo-upload V2 defined the ENUM as `('PENDING_ANALYSIS','ANALYZED','FAILED')`. AC-16 below asserts whichever enum set is actually present at migration-head; design-specialist must key the test against the current `Space.Status` Java enum, not a hard-coded literal list. If the product owner renamed the enum in a later iteration, AC-16 follows the Java enum — this is a ground-truth test, not a literal-string test.
  - `wishlist` rows populate `added_at` (V6 default `CURRENT_TIMESTAMP`) and `purchased_at` (non-null only when `status='Purchased'`) — the sales tile depends on this invariant (UC-02-wishlist AC-52 already proves it).
  - `furniture.price` is populated for every seeded row (UC-01-recommendation V4/V5). Sales tile treats a missing `furniture` row (should never happen due to FK RESTRICT on `wishlist.furniture_id`) as `0` contribution and logs a WARN.

## 4. In-scope / Out-of-scope summary
- **In scope**: V7 migration (role column + index + bootstrap note); `AdminController` (5 endpoints: `/overview`, `/users`, `/rooms`, `/wishlist`, `/sales`); 403 gating; time-window filter (`LAST_7D | LAST_30D | ALL`); response shapes for every tile (counts, distributions, conversion rate, sales total); OpenAPI contract; non-admin regression guard (every UC-01 / UC-02 endpoint behaves unchanged for a non-admin caller).
- **Out of scope** (see §8): real payment / checkout; admin mutation (creating / editing / deleting users or furniture or spaces); real-time push (WebSocket); AR analytics; re-recommendation based on admin decisions; email/CSV/PDF export; drill-down list endpoints (top-N users / top-N rooms); cursor pagination; admin client (no RN screen, no admin web SPA this iteration — deferred).

## 5. Decisions (D-1 … D-5)

### D-1 — Admin identity & role model
**Chosen: `users.role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'` + reuse `X-User-Id` header + 403 `NOT_ADMIN` on non-admin callers.**
- Rationale: matches the established Flyway-plus-ENUM idiom of every prior migration (V1 `wishlist.status`, V2 `spaces.status`). A separate `admin_users` table would duplicate PK / FK plumbing and break PRD §3's single-user-entity design. Reusing `X-User-Id` keeps the mobile/API surface single-headered — the RN client never learns of admin existence. Non-admin callers hitting any `/api/v1/admin/*` path receive **HTTP 403** with `errorCode = "NOT_ADMIN"` (new code; distinct from 404 because the endpoint *does* exist and leaking its existence is acceptable — admins are a product-visible concept per PRD §2).
- **Bootstrap**: V7 inserts **zero** admin rows (no fixture PII). The first admin is created by the operator running a single SQL statement documented in the migration header comment: `UPDATE users SET role='ADMIN' WHERE email=?`. FR-2 below restates this rule. No auto-seed of a user row.

### D-2 — Admin surface
**Chosen: Option C — backend-only this iteration. Admin client (RN admin tab or admin web SPA) is deferred to a future task `UC-03-admin-client`.**
- Rationale: the downstream value of this iteration is a stable aggregation contract. A client built before the contract is verified would need rework; a client built in the same iteration forces scope-inflation (auth routing, RN feature-flag on `role`, a new navigation stack entry). PRD §6 UC-03 says "대시보드" without pinning to a specific client, so deferring is spec-consistent. A follow-up task is already named (`UC-03-admin-client`) so the orchestrator can pick it up without re-planning.
- Consequence for ACs: no RN / admin-web AC exists in this spec. One explicit AC (AC-45) asserts "no client code was written" (git-diff guard) so a misbehaving design agent cannot silently ship half a client.

### D-3 — Aggregation implementation strategy
**Chosen: Option A — SQL aggregation queries run on every request (native `COUNT` / `SUM` / `GROUP BY` via JPA `@Query`).**
- Rationale: expected DB volumes for the v1 product are small (PRD §5 "개인 사용자 대상"). One read-through is simpler than cache-invalidation logic and matches the correctness-over-speed stance from prior tasks. Noted upgrade path: if P95 on the seeded 1k/5k/10k dataset exceeds the NFR, the next iteration may add a 60s TTL in-memory cache keyed by `(windowKey, requestedTileSet)` — explicitly *not* built now. Aggregation queries are co-located in `AdminOverviewRepository` (one method per tile) for test-focused design.

### D-4 — Time-window handling
**Chosen: Discrete enum `window ∈ { LAST_7D, LAST_30D, ALL }` on the query string; default `ALL`. Timezone fixed at `Asia/Seoul`.**
- Rationale: discrete windows keep the contract test matrix small (3 values × 5 endpoints = 15 integration tests, all seedable), and match the "대시보드" UX convention of preset ranges. Arbitrary `from/to` (ISO dates) is a superset the team can add later without breaking — it would be a new optional query param. Timezone is hard-coded `Asia/Seoul` (`ZoneId.of("Asia/Seoul")`) per prior-task convention (UC-01-photo-upload sequence-diagram time is `KST`). Window semantics:
  - `LAST_7D` → `[now - 7 days, now]` in `Asia/Seoul`, inclusive of the upper bound.
  - `LAST_30D` → `[now - 30 days, now]`.
  - `ALL` → no filter (`SELECT … WHERE 1=1`).
- Window field that each tile filters on:
  - **Users.newSignups**: `users.created_at`.
  - **Users.activeUsers**: "uploaded a photo in the last 30 days" — `spaces.uploaded_at` (this is the **active-user** definition; window=ALL means "ever uploaded"; window=LAST_7D means "uploaded in last 7 days").
  - **Rooms.totalSpaces + distributions**: `spaces.uploaded_at` for the count/inclusion filter; `spaces.analysis_date` is NOT used as the filter dim (would exclude uploads-that-never-analyzed from the ANALYZING/FAILED buckets).
  - **Wishlist.totalItems + breakdowns + conversion**: `wishlist.added_at`.
  - **Sales.totalKrw**: `wishlist.purchased_at` (only `status='Purchased'` rows contribute). Rows whose `purchased_at` is NULL are excluded from sales, by definition.

### D-5 — Pagination of drill-down lists
**Chosen: No drill-down list endpoints in this iteration. Every `/api/v1/admin/{tile}` endpoint returns aggregates only (counts / distributions / totals). Top-N user / top-N room drill-downs are deferred to a future task `UC-03-admin-drilldown`.**
- Rationale: scope-contain. The PRD §6 UC-03 text says "확인" (view) not "탐색" (explore). Top-N lists raise privacy questions (do we show emails?) best handled when the admin client is built (D-2). FR-3..FR-7 therefore return scalar/distribution shapes only — no `items[]` arrays. Consequence: no pagination required, no cursor design.

## 6. Functional Requirements

### Backend — schema + role

**FR-1 (V7 migration — `users.role`)** — Add Flyway migration `V7__add_users_role.sql` under `src/backend/src/main/resources/db/migration/`. The migration MUST:
- `ALTER TABLE users ADD COLUMN role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' AFTER email;`
- `CREATE INDEX idx_users_role ON users(role);` — supports the per-request `role == 'ADMIN'` check and future `WHERE role='USER'` aggregations (Users tile).
- NOT drop / rename any existing column. V1..V6 stay byte-for-byte unchanged (FR-13 regression guard).
- MUST be idempotent under Flyway's single-run semantics; re-running the app after V7 is applied MUST NOT re-run or error.
- Header comment MUST document the admin-bootstrap SQL statement: `UPDATE users SET role='ADMIN' WHERE email='<operator@example.com>';`. No operator row is inserted by the migration itself.

**FR-2 (`User` entity extension)** — Extend `com.authenticself.domain.User` additively:
- Add a JPA-mapped enum `Role { USER, ADMIN }` as a nested type on `User` (mirroring `Space.Status`).
- Add `@Enumerated(EnumType.STRING) @Column(name = "role", nullable = false, length = 16, columnDefinition = "ENUM('USER','ADMIN')") private Role role;` with a getter `getRole()` and a setter `setRole(Role)`. Default value on new-row construction is `Role.USER` so unit tests that construct a `User` POJO do not NPE.
- Do NOT change any existing field / column mapping. Test files for earlier tasks remain green.

**FR-3 (Admin authorization filter)** — Introduce a thin `AdminAuthorizer` component under `com.authenticself.admin.AdminAuthorizer`:
- Method signature: `void requireAdmin(String userIdHeader)`.
- Behaviour:
  - `userIdHeader == null || isBlank` → throw `AdminException(AdminErrorCode.MISSING_USER_HEADER)` (maps to HTTP 400, reuses the shared Korean message).
  - `userRepository.findById(userIdHeader).isEmpty()` → throw `AdminException(AdminErrorCode.USER_NOT_FOUND)` (404).
  - `user.getRole() != Role.ADMIN` → throw `AdminException(AdminErrorCode.NOT_ADMIN)` (403).
  - Else return normally.
- Every admin endpoint MUST call `requireAdmin(...)` as the first statement of the controller method (before any repository access that is not the admin check itself). Verified by AC-21..AC-25.

### Backend — endpoints

**FR-4 (`GET /api/v1/admin/overview?window=...` — master endpoint)** — Register the controller `com.authenticself.admin.AdminController` at `/api/v1/admin`. The master endpoint:
- Headers: `X-User-Id` required (400 `MISSING_USER_HEADER` on absence — reuse the shared code).
- Query param `window` (case-insensitive; default `ALL`; must be one of `LAST_7D | LAST_30D | ALL`; any other value → 400 `INVALID_TIME_WINDOW`).
- Response HTTP 200 body shape:
  ```json
  {
    "window": "LAST_30D",
    "generatedAt": "2026-04-18T09:14:22+09:00",
    "users":    { /* UsersTile — see FR-5 */ },
    "rooms":    { /* RoomsTile — see FR-6 */ },
    "wishlist": { /* WishlistTile — see FR-7 */ },
    "sales":    { /* SalesTile — see FR-8 */ }
  }
  ```
- The four sub-objects MUST equal the responses of the per-tile endpoints (FR-5..FR-8) called with the same `window` — enforced by AC-33.

**FR-5 (`GET /api/v1/admin/users?window=...` — Users tile)** — Response shape:
```json
{
  "window": "LAST_30D",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalUsers":     42,      // COUNT(*) FROM users WHERE role='USER'  within window on users.created_at
  "totalAdmins":    2,       // COUNT(*) FROM users WHERE role='ADMIN' within window on users.created_at
  "newSignups":     7,       // COUNT(*) FROM users WHERE created_at >= windowStart
  "activeUsers":    12,      // COUNT(DISTINCT user_id) FROM spaces WHERE uploaded_at >= windowStart
  "signupsByDay": [
      { "date": "2026-04-11", "count": 1 },
      { "date": "2026-04-12", "count": 0 },
      ...
  ]
}
```
- `signupsByDay` is an array of dense date rows (one entry per calendar day in the window, ordered ascending; zero-count days included); for `window=ALL` the array covers `[earliestUser.created_at, today]` or is empty if no users exist.
- `totalUsers` / `totalAdmins` for `window=ALL` → unfiltered count (no date predicate).
- `activeUsers` definition is **"uploaded a photo within the window"** — documented in `AdminOverviewService` Javadoc and on this spec line. For `window=ALL`, `activeUsers` = count of distinct user_ids that have any `spaces` row ever.

**FR-6 (`GET /api/v1/admin/rooms?window=...` — Rooms tile)** — Response shape:
```json
{
  "window": "LAST_30D",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalSpaces": 210,
  "statusDistribution": {
      "UPLOADED":         0,
      "ANALYZING":        0,
      "ANALYZED":         198,
      "ANALYSIS_FAILED":  12
  },
  "styleDistribution": {
      "MODERN":       80,
      "SIMPLE":       40,
      "CLASSIC":      30,
      "SCANDINAVIAN": 35,
      "INDUSTRIAL":   13
  },
  "mainColorTop5": [
      { "color": "#E8D9B0", "count": 60 },
      { "color": "#FFFFFF", "count": 48 },
      ...
  ]
}
```
- Window filter applies on `spaces.uploaded_at`.
- `statusDistribution` keys are **exactly** the set of values in the current `Space.Status` Java enum. Any extra/missing key → design-specialist must either update the spec or break AC-34. If ANALYZED rows have null `style` (upstream Task 3 partial-success case), they are counted in `totalSpaces` and in `statusDistribution['ANALYZED']` but NOT in any `styleDistribution` key — the sum of `styleDistribution` values MAY be less than `totalSpaces` (AC-35 documents this).
- `styleDistribution` keys are **exactly** the 5-value `Style` enum (`MODERN,SIMPLE,CLASSIC,SCANDINAVIAN,INDUSTRIAL`) regardless of how many rows fall in each bucket; zero-count buckets MUST be present with value `0`. Sum of `styleDistribution` values ≤ `totalSpaces`.
- `mainColorTop5` MUST be the top 5 `main_color` values by count, ordered DESC by count, ties broken by `color ASC`. If fewer than 5 distinct values exist, the array is shorter (no padding). NULL `main_color` is excluded.
- Sum of `statusDistribution` values MUST equal `totalSpaces` (AC-36 — invariant).

**FR-7 (`GET /api/v1/admin/wishlist?window=...` — Wishlist tile)** — Response shape:
```json
{
  "window": "LAST_30D",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalItems": 340,
  "statusDistribution": {
      "ACTIVE":     270,
      "PURCHASED":  70
  },
  "categoryDistribution": {
      "desk":     120,
      "bed":       45,
      "chair":     95,
      "lighting":  80
  },
  "conversionRate": 0.21
}
```
- Window filter applies on `wishlist.added_at`.
- `statusDistribution` keys are exactly `ACTIVE` and `PURCHASED` (REST-layer UPPER_SNAKE — the enum is translated from the DB literals `Active` / `Purchased` per UC-02-wishlist FR-6 convention). Sum of values MUST equal `totalItems`.
- `categoryDistribution` keys are exactly the 4-value set `{ desk, bed, chair, lighting }` (lowercase, matching the `wishlist.category` values and UC-02-wishlist FR-5 validation). Zero-count buckets MUST be present with value `0`. Sum of values MUST equal `totalItems`.
- `conversionRate = statusDistribution.PURCHASED / totalItems`, rounded to 2 dp. **When `totalItems == 0`, `conversionRate = 0.0` (no divide-by-zero).** `0.0 ≤ conversionRate ≤ 1.0` always.

**FR-8 (`GET /api/v1/admin/sales?window=...` — Sales tile)** — Response shape:
```json
{
  "window": "LAST_30D",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalSalesKrw":   5940000,      // SUM(wishlist.price) WHERE status='Purchased' AND purchased_at within window
  "purchasedItemCount": 33,        // COUNT(*) WHERE status='Purchased' within window
  "averageOrderKrw": 180000,       // round(totalSalesKrw / purchasedItemCount); 0 when count=0
  "salesByCategory": {
      "desk":     1200000,
      "bed":     3200000,
      "chair":    540000,
      "lighting": 1000000
  },
  "salesByDay": [
      { "date": "2026-04-11", "krw": 240000 },
      ...
  ]
}
```
- The sales total is **SUM(`wishlist.price`)** (the snapshot price at add-time, per UC-02-wishlist FR-4), NOT `furniture.price`, so historical sales do not re-value when the catalog price changes. This is documented in `AdminOverviewService` Javadoc and enforced by AC-37.
- Window filter applies on `wishlist.purchased_at`; rows with `status='Active'` (and thus `purchased_at IS NULL`) are excluded (AC-38).
- `averageOrderKrw` uses integer division `Math.round((double)totalSalesKrw / purchasedItemCount)`; when `purchasedItemCount == 0`, `averageOrderKrw = 0` and `totalSalesKrw = 0`.
- `salesByCategory` keys are exactly the 4-value category set; zero-buckets present with value `0`. Sum of values MUST equal `totalSalesKrw`.
- `salesByDay` is dense (one entry per calendar day in the window, zero-krw days included, ascending). For `window=ALL`, the array covers `[earliestPurchasedAt, today]` or is empty if no purchases exist.

**FR-9 (Repository surface)** — Introduce `com.authenticself.admin.AdminOverviewRepository` as a Spring `@Repository` (interface + `@Query`-annotated methods) with one method per aggregation:
- `long countUsersInRoleAndWindow(Role, Instant windowStart)` (null windowStart → no filter).
- `long countNewSignups(Instant windowStart)`.
- `long countActiveUsers(Instant windowStart)`.
- `List<DateCount> groupSignupsByDay(Instant windowStart, Instant windowEnd)`.
- `long countSpaces(Instant windowStart)`.
- `Map<Space.Status, Long> countSpacesByStatus(Instant windowStart)` (implemented as a query returning `List<StatusCount>` and assembled into the map in the service layer).
- `Map<Style, Long> countSpacesByStyle(Instant windowStart)` (same pattern).
- `List<ColorCount> topMainColors(Instant windowStart, int limit)`.
- `long countWishlist(Instant windowStart)`.
- `Map<String, Long> countWishlistByRestStatus(Instant windowStart)` (keys returned as DB-casing `Active`/`Purchased`; service translates to REST-casing).
- `Map<String, Long> countWishlistByCategory(Instant windowStart)`.
- `long sumSalesKrw(Instant windowStart)` / `long countPurchasedItems(Instant windowStart)` / `Map<String, Long> sumSalesByCategory(Instant windowStart)` / `List<DateKrw> sumSalesByDay(Instant windowStart, Instant windowEnd)`.
- All `Instant windowStart` parameters are nullable; when null, the query omits the date predicate (native SQL `WHERE (:windowStart IS NULL OR column >= :windowStart)` pattern).
- Each method MUST have an integration test against a seeded Testcontainers MySQL — verified by AC-39..AC-44.

**FR-10 (Service — `AdminOverviewService`)** — Orchestrates the repository calls per tile:
- Accepts `window: TimeWindow` enum (`LAST_7D | LAST_30D | ALL`) and translates it into an `Instant windowStart` at `Asia/Seoul` midnight boundaries for day-aligned queries:
  - `LAST_7D` → `ZonedDateTime.now(Asia/Seoul).minusDays(7).toInstant()`.
  - `LAST_30D` → `ZonedDateTime.now(Asia/Seoul).minusDays(30).toInstant()`.
  - `ALL` → `null`.
- Clock is injectable (Spring `Clock` bean, default `Clock.system(Asia/Seoul)`) — AC-42 uses a fixed-clock test to make window math deterministic.
- Composes `OverviewResponse` (one-shot call for `/overview`) and `{Users,Rooms,Wishlist,Sales}Tile` DTOs (per-tile endpoints). The per-tile outputs inside `/overview` MUST be byte-identical to the corresponding `/admin/{tile}` response body under the same `window`, verified by AC-33.
- Log one INFO line per request: `op=overview|users|rooms|wishlist|sales window=LAST_30D userId=<opaque> durationMs=<n>`. No PII beyond `userId` (opaque). WARN on any FK-RESTRICT violation in sales aggregation (should be impossible; logged defensively).

**FR-11 (Error envelope + `@RestControllerAdvice`)** — New exception types under `com.authenticself.admin`:
- `AdminException(AdminErrorCode code, String messageOverrideOrNull)`.
- `AdminErrorCode` enum (shape mirrors `WishlistErrorCode`):
  - `MISSING_USER_HEADER`     (400) — reuses shared Korean message.
  - `INVALID_TIME_WINDOW`     (400) — "기간 필터 값이 올바르지 않습니다. (LAST_7D, LAST_30D, ALL)".
  - `USER_NOT_FOUND`          (404).
  - `NOT_ADMIN`               (403) — "관리자 권한이 필요합니다." (new code introduced by this task).
- `AdminExceptionAdvice` at `@RestControllerAdvice(basePackages = "com.authenticself.admin")` with `@Order(Ordered.HIGHEST_PRECEDENCE)`. Emits `new ErrorResponse(code.name(), message, correlationId)` — identical pattern to `WishlistExceptionAdvice` (UC-02-wishlist AC-30). `correlationId` is a UUIDv4, identical formatting to prior advice classes.

**FR-12 (OpenAPI contract)** — Write `artifacts/UC-03-admin-overview/api_contract.yaml` covering the five new paths (`GET /api/v1/admin/overview`, `/admin/users`, `/admin/rooms`, `/admin/wishlist`, `/admin/sales`), the `TimeWindow` enum (`LAST_7D | LAST_30D | ALL`), every response schema (`OverviewResponse`, `UsersTile`, `RoomsTile`, `WishlistTile`, `SalesTile`), and every error envelope. The error schema MUST reference the shared `SpringErrorResponse { errorCode, message, correlationId }` shape used by `UC-02-wishlist/api_contract.yaml`. The file MUST parse as OpenAPI 3.0.x — AC-46 validates.

**FR-13 (No cross-task regression — additive extension only)** — No existing file under `src/backend/src/main/resources/db/migration/V1..V6*.sql` may be modified. No existing Java controller (`PhotoUploadController`, `SpaceController`, `AIOrchestratorController`, `WishlistController`) / service / DTO may be modified other than the strictly additive `User.Role` extension (FR-2). The `ErrorResponse` record and all prior exception-advice classes stay untouched. Non-admin callers hitting any UC-01 / UC-02 endpoint MUST observe byte-identical behaviour (AC-48 — cross-task regression test).

**FR-14 (Configuration surface)** — Extend `application.yml` with:
- `app.admin.time-zone: "Asia/Seoul"` (default; env override `ADMIN_TIME_ZONE`).
- `app.admin.top-colors-limit: 5` (default; int; env override `ADMIN_TOP_COLORS_LIMIT`) — used by FR-6 `mainColorTop5`. Overriding MUST reflect in the response array length (AC-49).
- No cache keys in this task (D-3).

**FR-15 (Admin client deferral — explicit non-requirement)** — No RN screen, no admin web SPA, no new file under `src/mobile/` or `src/admin/` may be introduced by this task. The design-specialist MUST NOT create a `src/admin/` directory. The navigation stack in `src/mobile/App.tsx` MUST NOT gain an admin route. AC-45 enforces this as a git-diff guard. The client surface is deferred to a future task `UC-03-admin-client`.

## 7. Non-Functional Requirements

- **Latency (backend, P95, seeded dataset: 1000 users / 5000 spaces / 10000 wishlist rows / 24 furniture rows)**:
  - `GET /api/v1/admin/overview` (master): ≤ 500 ms.
  - `GET /api/v1/admin/{users,rooms,wishlist,sales}`: ≤ 200 ms each.
  - Measured on the Testcontainers MySQL configuration used by existing integration tests.
- **Idempotency**: every endpoint is a pure GET; repeated calls return identical shape and (modulo `generatedAt`) identical values for `window=ALL`. Verified by AC-50.
- **Concurrency**: a concurrent admin request while a non-admin user is writing (POST /wishlist, PUT /preferred-style) MUST NOT block either caller beyond the per-row lock duration. No new long-held locks introduced.
- **Error envelope conformance**: every non-2xx response matches the shared `ErrorResponse(errorCode, message, correlationId)` record. No new envelope shape is introduced (AC-47).
- **Security**:
  - Every endpoint requires `X-User-Id` (400 `MISSING_USER_HEADER` on absence).
  - Non-admin callers (role=`USER`) receive 403 `NOT_ADMIN` (AC-21..AC-25) — one AC per endpoint.
  - Unknown `X-User-Id` receives 404 `USER_NOT_FOUND` (AC-26). (Existence of the admin endpoint path is not hidden; see D-1 rationale.)
  - No PII beyond `userId` (opaque) is logged. Email / name MUST NOT appear in any log line emitted by `AdminController` / `AdminOverviewService` / `AdminAuthorizer`.
- **Data retention**: no data mutation by this task — read-only.
- **Determinism**: for a fixed clock and fixed DB state, every response is deterministic (tie-break on `mainColorTop5` by `color ASC`, dense day arrays include zero-count days, distribution-map iteration order is stable by enum ordinal). Verified by AC-51.
- **i18n**: all user-visible strings in the response (e.g. Korean error messages) are in Korean; enum values (window, status, style) stay ASCII UPPER_SNAKE on the REST surface.
- **No new dependency**: no new runtime dependency added to `build.gradle`. Tests reuse existing Testcontainers + Spring Boot Starter Data JPA.

## 8. Out of Scope
- **Admin client** — RN admin tab / admin web SPA (deferred to `UC-03-admin-client`; see D-2).
- **Drill-down list endpoints** — top-N users, top-N rooms, per-user breakdown (deferred to `UC-03-admin-drilldown`; see D-5).
- **Mutation endpoints** — create/edit/delete users, furniture, spaces, wishlist rows. Admin can view but not act. (A future `UC-03-admin-moderation` task may add them.)
- **Arbitrary `from=YYYY-MM-DD&to=...` query** — only the 3-value `window` enum is supported (D-4).
- **Real-time push** — no WebSocket, no SSE; every read is polling.
- **Export** — no PDF / CSV / XLSX endpoint.
- **Real payment / checkout** — sales is still "sum of PURCHASED wishlist snapshot prices" per UC-02-wishlist's user-marked semantics (same as UC-02 §8).
- **Role hierarchy** — no `SUPER_ADMIN`, no role-based scoping within the admin surface; a `role='ADMIN'` user sees every tile and every window.
- **Auto-seeding the first admin** — V7 does NOT insert a row; operator runs a documented SQL UPDATE (D-1 bootstrap).
- **Rate limiting / throttling** — consistent with upstream tasks; no gateway enforcement.
- **Audit log** — no `admin_access_log` table; admin reads are logged via normal application logs only.
- **Cache / TTL** — not in this iteration (D-3 upgrade path only).
- **Metrics endpoint** — no Prometheus / Micrometer integration in this task.

## 9. Data Model & Contract

### DB — `users` post-V7
```
user_id    VARCHAR(64) PK
name       VARCHAR(100) NOT NULL
email      VARCHAR(255) NOT NULL UNIQUE
role       ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'   -- NEW in V7
created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
INDEX idx_users_role (role)                               -- NEW in V7
```

### REST — `GET /api/v1/admin/overview?window=LAST_30D`
Success (HTTP 200) — aggregates of FR-5..FR-8, keyed under `users|rooms|wishlist|sales`. See FR-4 for the full shape. `generatedAt` is an ISO-8601 string with `+09:00` offset.

### REST — error envelope (unchanged from sibling tasks)
```json
{ "errorCode": "NOT_ADMIN", "message": "관리자 권한이 필요합니다.", "correlationId": "b1e..." }
```

### Error codes introduced by this task
| Code | HTTP | When |
|---|---|---|
| `MISSING_USER_HEADER` | 400 | `X-User-Id` absent — **reused** shared code, not new |
| `INVALID_TIME_WINDOW` | 400 | `window` is not in `{LAST_7D, LAST_30D, ALL}` |
| `USER_NOT_FOUND`      | 404 | `X-User-Id` does not match any `users.user_id` |
| `NOT_ADMIN`           | 403 | authenticated user's `role != ADMIN` — **new** |

## 10. Dependencies
- **Hard**:
  - `DB-schema-init` (Task 1) — `users`, `spaces`, `furniture`, `wishlist` tables. Columns consumed: `users.{user_id,email,created_at}`, `spaces.{room_id,user_id,uploaded_at,status,style,main_color}` (post-V2), `wishlist.{user_id,furniture_id,category,price,status,added_at,purchased_at}` (post-V6), `furniture.{furniture_id,price}` (post-V4/V5).
  - `UC-01-photo-upload` (Task 2) — `spaces.status` + `spaces.uploaded_at` columns; the `Space.Status` Java enum. Read-only.
  - `UC-01-space-analysis` (Task 3) — `spaces.style` and `spaces.analysis_date` populated on ANALYZED rows. Read-only.
  - `UC-01-style-selection` (Task 4) — the 5-value `Style` enum (`MODERN,SIMPLE,CLASSIC,SCANDINAVIAN,INDUSTRIAL`) used in `styleDistribution`.
  - `UC-01-recommendation` (Task 5) — `furniture.price` (though sales uses the `wishlist.price` snapshot — see FR-8); needed so FK RESTRICT on `wishlist.furniture_id` is populated.
  - `UC-02-wishlist` (Task 6) — V6 `added_at` / `purchased_at` columns; the `ACTIVE ↔ PURCHASED` state invariant (UC-02-wishlist AC-52). Without UC-02 FR-10's invariant, FR-8's "purchased_at IS NOT NULL ⇔ status=Purchased" assumption is not safe.
- **Consumed by**: `UC-03-admin-client` (future — will render these endpoints), `UC-03-admin-drilldown` (future — will extend with top-N lists).
- **No dependency on**: `AR-furniture-placement` — independent.

## 11. Open Questions
1. **Active-user definition** — PRD §6 UC-03 does not define "active". This spec uses "uploaded a photo within the window". An alternative (uploaded OR wishlisted OR marked-purchased) is reasonable but would triple the JOIN cost; flagged for product-owner review. No AC depends on the alternative definition.
2. **Sales — use `wishlist.price` snapshot vs current `furniture.price`** — FR-8 picks the snapshot (consistent with UC-02-wishlist FR-4). If the product owner wants "live catalog value of purchased items", a follow-up task swaps `SUM(w.price)` for `SUM(f.price)` — no migration needed.
3. **Admin self-visibility in Users tile** — `totalUsers` counts `role='USER'` rows only; `totalAdmins` counts `role='ADMIN'`. PRD does not specify; this split is the least-surprising default. If the product owner wants `totalUsers` to include admins, AC-27 is updated to match — single-line change.
4. **Top-N main colors — hex vs name bucketing** — `spaces.main_color` is a hex string per UC-01-space-analysis FR-6. Two near-identical colors (`#E8D9B0` vs `#E8D9B1`) count as distinct. A clustered bucketing is out of scope; flagged.
