# Task Spec: UC-02-wishlist

## 1. Goal
Replace the `RecommendationScreen` "위시리스트에 추가" stub (UC-01-recommendation AC-48) with a real end-to-end wishlist flow: (a) Spring REST endpoints to add / list / transition / delete wishlist items, backed by the existing `wishlist` MySQL table plus a V6 migration that adds the two timestamps the PRD state machine demands; (b) a React Native `WishlistScreen` with two filterable sections (`ACTIVE` / `PURCHASED`) and a one-tap "구매 완료" state transition; (c) a deterministic state machine (`ACTIVE ↔ PURCHASED`) with idempotent add semantics and an explicit illegal-transition error. The "위시리스트에 추가" button now calls the real backend; no other UC-01 surface changes.

## 2. Source (PRD section)
- **PRD §6 UC-02 기본 흐름 steps 1–3** — "사용자가 추천받은 가구 아이템의 상세 정보(카테고리, 가격 등)를 확인 / 마음에 드는 가구를 위시리스트에 담음 / 시스템은 위시리스트 DB에 해당 아이템을 추가하고, 현재 상태(Status)를 'Active (보관 중)'으로 설정." Drives FR-1..FR-4 (add endpoint + persistence + default status).
- **PRD §6 UC-02 기본 흐름 steps 4–5** — "사용자가 해당 가구의 구매를 결정하고 결제를 진행 / 결제가 완료되면 시스템은 해당 아이템의 상태를 'Purchased (구매 완료)'로 업데이트." This task implements **only the state update half** (user-marked, no real payment — see §8 Out of Scope). Drives FR-7..FR-8 (state transition endpoint).
- **PRD §3 DB 설계 — 위시리스트 DB** — `(user_id, furniture_id, category, price, status)` + the enum `Active | Purchased`. Drives FR-13 (schema delta for timestamps) and the DB entity columns.
- **PRD §7 class diagram** — `Wishlist` class exposes `updateStatus(newStatus: String)`; `User` has `addToWishlist(furnitureId: String)` and `purchaseItem(wishlistId: String)`. The public REST surface (FR-5..FR-10) mirrors these two user-level actions plus a list and a delete.
- **PRD §2 Overview / UC-03** — Admin dashboard `Wishlist` tile will aggregate over the rows this task writes. This task does NOT implement that aggregation (see §8); it only guarantees the rows are queryable.
- **PRD §9 시스템 아키텍처** — `Space & Wishlist Service (비즈니스 로직)` on Spring + MySQL. This task lands the "Wishlist Service" half of that box; no Python / AI service is involved.

## 3. Actors & Preconditions
- **Primary actor (user)**: an authenticated RN client user (identity carried by the `X-User-Id` header — same convention as UC-01-photo-upload / UC-01-style-selection / UC-01-recommendation).
- **Primary actor (backend)**: a new `WishlistService` + `WishlistController` under `com.authenticself.wishlist` (new package). Reuses `UserRepository`, `FurnitureRepository`, the shared `ErrorResponse` envelope, and the existing `@RestControllerAdvice` ordering pattern.
- **Secondary actor (mobile)**: the existing `RecommendationScreen` (its stub button now calls `addToWishlist(...)` — see FR-18) and a new top-level `WishlistScreen` reachable from `HomeScreen` (FR-19).
- **Preconditions**:
  - Tasks 1–5 are merged:
    - V1 base schema (incl. `wishlist` table with `ENUM('Active','Purchased')`).
    - V2 spaces status, V3 preferred-style, V4 furniture catalog columns, V5 furniture seed (24+ rows).
  - `RecommendationScreen` is navigable and can render real recommendation cards (UC-01-recommendation FR-23).
  - Spring `X-User-Id` convention is in force on every public endpoint (shared `MISSING_USER_HEADER` error code).
  - The `Wishlist` JPA entity stub exists under `com.authenticself.domain.Wishlist` (Task 1 FR-8); this task extends it with the two new timestamps (FR-14) and keeps the existing column mappings intact.
  - RN `settings.userId` stub is the same one UC-01 tasks rely on.

## 4. In-scope / Out-of-scope summary
- **In scope**: add / list / mark-purchased / un-mark-purchased / delete endpoints; wishlist table timestamp columns; RN `WishlistScreen` with two segment tabs; real wire-up of `RecommendationScreen`'s add button; a V6 Flyway migration; per-task `@ControllerAdvice` for the new error codes; OpenAPI contract additions.
- **Out of scope** (deferred to later tasks — see §8 for the full list): admin aggregation/analytics (UC-03); real payment flow; recommendation re-ranking using purchase history; AR preview of a wishlisted item (AR-furniture-placement); wishlist sharing / export; wishlist-level notifications; price-change tracking.

## 5. Functional Requirements

### Backend — schema + entity

**FR-1 (V6 migration — wishlist timestamps)** — Add Flyway migration `V6__extend_wishlist_timestamps.sql` under `src/backend/src/main/resources/db/migration/`. The migration MUST:
- ADD COLUMN `added_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP` (records when the row entered `Active`; seeded from `created_at` for any pre-existing rows).
- ADD COLUMN `purchased_at DATETIME     NULL` (records when the row transitioned to `Purchased`; NULL while `Active`).
- ADD a UNIQUE constraint `uq_wishlist_user_furniture` on `(user_id, furniture_id)` so the same user cannot double-add the same `furnitureId` (idempotency — FR-3).
- Not drop / rename any existing column. `created_at` and `updated_at` from V1 stay as-is; `added_at` is a semantic re-expression that also lets analytics separate "first added" from any future row update timestamp.

**FR-2 (`Wishlist` entity extension)** — Extend `com.authenticself.domain.Wishlist` with two JPA `@Column` fields `addedAt` (`LocalDateTime`, maps to `added_at`, NOT NULL, read-only from JPA — DB default handles the write) and `purchasedAt` (`LocalDateTime`, nullable, maps to `purchased_at`, writable). Preserve the existing `Status` enum (`Active`, `Purchased`) and the native MySQL `ENUM` column definition. Add setters for `status`, `purchasedAt`, and `userId`/`furnitureId`/`category`/`price` on first insert; all other accessors are getters.

**FR-3 (Repository + uniqueness)** — Add `com.authenticself.repository.WishlistRepository extends JpaRepository<Wishlist, String>` with methods:
- `Optional<Wishlist> findByUserIdAndFurnitureId(String userId, String furnitureId)` — used for idempotency check on add.
- `List<Wishlist> findAllByUserIdAndStatusOrderByUpdatedAtDesc(String userId, Wishlist.Status status)` — drives the filtered list.
- `Optional<Wishlist> findByWishlistIdAndUserId(String wishlistId, String userId)` — used by PATCH / DELETE to authorize the owner (no row leakage on mismatched IDs).

### Backend — service + REST surface

**FR-4 (`WishlistService.add`)** — `add(String userId, AddRequest req)` accepts `{ furnitureId, category, price }` (client supplies all three so the service does NOT re-query `furniture` just to echo `type`/`price` for the wishlist row — keeps the write path small and matches PRD §3 where `wishlist.category` and `wishlist.price` are duplicated from `furniture` at add-time so historical price is preserved even if the catalog row's price later changes).
- Verify the user exists (`UserRepository.existsById(userId)` — else throw `UserNotFoundException` → 404 `USER_NOT_FOUND`; test fixtures must seed the user first).
- Verify the furniture exists (`FurnitureRepository.existsById(furnitureId)` — else throw `FurnitureNotFoundException` → 404 `FURNITURE_NOT_FOUND`).
- Validate `category ∈ { desk, bed, chair, lighting }` and `price >= 0` — else 400 `INVALID_WISHLIST_PAYLOAD`.
- Idempotency: if `findByUserIdAndFurnitureId(userId, furnitureId)` returns a row, return that existing row (HTTP 200, NOT 201) with the response body flagging `alreadyExists: true`. Do NOT change its `status`, `addedAt`, or `price` (a previously-`Purchased` row is NOT reset to `Active`; user must explicitly un-mark via FR-8).
- On insert, generate `wishlistId = UUID.randomUUID().toString()` (same convention as `PhotoUploadService`'s `roomId`), set `status = Active`, and let MySQL populate `added_at` / `created_at` / `updated_at` via DB defaults. Response HTTP 201 with `alreadyExists: false`.

**FR-5 (`POST /api/v1/wishlist` — public REST)** — Register a new controller under `com.authenticself.wishlist.WishlistController` at `POST /api/v1/wishlist`:
- Headers: `X-User-Id: <user_id>` (required; 400 `MISSING_USER_HEADER` if missing — reuse the existing code).
- Request body: `{ "furnitureId": string, "category": "desk"|"bed"|"chair"|"lighting", "price": int }`. `Content-Type: application/json`.
- Success (HTTP 201 new or 200 existing): `{ wishlistId, userId, furnitureId, category, price, status: "Active"|"Purchased", addedAt, purchasedAt, alreadyExists: boolean }`.
- Error codes introduced: `INVALID_WISHLIST_PAYLOAD` (400), `USER_NOT_FOUND` (404), `FURNITURE_NOT_FOUND` (404). Envelope shape is the shared `{errorCode, message, correlationId}`.

**FR-6 (`GET /api/v1/wishlist?status=ACTIVE|PURCHASED`)** — List endpoint:
- Headers: `X-User-Id` required.
- Query `status` optional; if absent, return both states concatenated (ACTIVE first, then PURCHASED, each sub-list sorted by `updatedAt DESC`). If present, MUST be exactly `ACTIVE` or `PURCHASED` (case-insensitive on input; reject other values with 400 `INVALID_WISHLIST_STATUS_FILTER`). The service maps `ACTIVE → Wishlist.Status.Active` / `PURCHASED → Wishlist.Status.Purchased` — the REST surface uses UPPER_SNAKE to match every other public enum in this project; the DB keeps the V1 casing (`Active`/`Purchased`) unchanged.
- Response (HTTP 200): `{ "items": [ <WishlistItem>... ], "totalActive": int, "totalPurchased": int }`. Each `WishlistItem` has the fields from FR-5 plus a derived `furnitureSnapshot: { name, imageUrl, colorHex, type }` joined from the current `furniture` row (read-only snapshot — if the catalog row has been deleted, `furnitureSnapshot` is `null` and `wishlistId` is still returned so the user can delete the dangling entry).
- List MUST be bounded: if an authenticated user somehow accumulates more than 500 rows, return the 500 most-recent by `updated_at DESC` and include `truncated: true`. (No cursor pagination in v1 — see §8.)

**FR-7 (`PATCH /api/v1/wishlist/{wishlistId}` — state transition)** — Marks an item purchased or undoes a purchase:
- Headers: `X-User-Id` required.
- Request body: `{ "status": "ACTIVE" | "PURCHASED" }`.
- Ownership check: `findByWishlistIdAndUserId(wishlistId, userId)` MUST return the row; else 404 `WISHLIST_ITEM_NOT_FOUND` (deliberately NOT 403 to avoid leaking the existence of items owned by other users — same stance as the `SPACE_ACCESS_DENIED` rationale in UC-01-recommendation).
- Allowed transitions (state machine — FR-10):
  - `Active → Purchased` — sets `status=Purchased`, `purchasedAt=NOW()`.
  - `Purchased → Active` — sets `status=Active`, `purchasedAt=NULL`.
  - `Active → Active` or `Purchased → Purchased` — **idempotent no-op**, returns HTTP 200 with the unchanged row. Does NOT bump `updated_at` (test asserts this by capturing `updated_at` pre/post).
  - Any other value (e.g. `"PENDING"`) — 400 `INVALID_STATE_TRANSITION`.

**FR-8 (`DELETE /api/v1/wishlist/{wishlistId}`)** — Removes an item from the wishlist:
- Headers: `X-User-Id` required.
- Ownership check identical to FR-7.
- Success: HTTP 204 with empty body.
- DELETE is valid in BOTH states — a `Purchased` row can be deleted to clean up history. The PRD does not forbid this; there is no archive table in scope (see §8).
- 404 `WISHLIST_ITEM_NOT_FOUND` on missing / non-owned ID (same rationale as FR-7).

**FR-9 (Error envelope + `@RestControllerAdvice`)** — New exception types under `com.authenticself.wishlist`:
- `WishlistException(WishlistErrorCode code, String messageOverrideOrNull)`
- `WishlistErrorCode` enum (shape mirrors `SpaceErrorCode`):
  - `INVALID_WISHLIST_PAYLOAD` (400)
  - `INVALID_WISHLIST_STATUS_FILTER` (400)
  - `INVALID_STATE_TRANSITION` (400)
  - `MISSING_USER_HEADER` (400) — delegated to the shared handler (re-raises the `SpaceErrorCode` / `UploadErrorCode` equivalent; the RN client does not care which package emitted the 400 `MISSING_USER_HEADER`)
  - `USER_NOT_FOUND` (404)
  - `FURNITURE_NOT_FOUND` (404)
  - `WISHLIST_ITEM_NOT_FOUND` (404)
  - `WISHLIST_LIMIT_EXCEEDED` (409) — reserved for the > 500 rows case if the server enforces a hard cap (optional; see FR-6 soft truncation). Not required to be raised in v1.
- New `WishlistExceptionAdvice` at `@RestControllerAdvice(basePackages = "com.authenticself.wishlist")` with `@Order(Ordered.HIGHEST_PRECEDENCE)` — consistent with `SpaceExceptionAdvice`. Emits `new ErrorResponse(code.name(), message, correlationId)`.

**FR-10 (State machine — formal)** — States are `ACTIVE`, `PURCHASED`. Transitions:
| from → to | trigger | side effect |
|---|---|---|
| (none) → `ACTIVE` | `POST /api/v1/wishlist` (FR-5) | insert row, `added_at=NOW()`, `purchased_at=NULL` |
| `ACTIVE` → `PURCHASED` | `PATCH` with `status=PURCHASED` | `purchased_at=NOW()` |
| `PURCHASED` → `ACTIVE` | `PATCH` with `status=ACTIVE` | `purchased_at=NULL` |
| `ACTIVE` → `ACTIVE` | `PATCH` with `status=ACTIVE` (already Active) | no-op, `updated_at` untouched |
| `PURCHASED` → `PURCHASED` | `PATCH` with `status=PURCHASED` (already Purchased) | no-op, `updated_at` untouched |
| `ACTIVE` → (removed) | `DELETE` (FR-8) | hard delete |
| `PURCHASED` → (removed) | `DELETE` (FR-8) | hard delete |
| any → anything-else | PATCH with unknown enum | 400 `INVALID_STATE_TRANSITION`, row untouched |

**FR-11 (Observability)** — `WishlistService` logs one INFO line per write with `op ∈ {add,patch,delete}`, `userId`, `furnitureId|wishlistId`, `fromStatus`, `toStatus`, and `resultHttp`. Error paths log WARN with `errorCode + correlationId`. No PII beyond userId (opaque string) is logged.

**FR-12 (Configuration surface)** — Extend `application.yml` with `app.wishlist.max-items-per-user` (default `500`, int, env-overridable). The service reads this at construction; FR-6 truncation uses it. No new `app.wishlist.cache.*` keys — list requests always hit MySQL in v1.

**FR-13 (OpenAPI contract)** — Write `artifacts/UC-02-wishlist/api_contract.yaml` covering the four new REST paths (`POST /api/v1/wishlist`, `GET /api/v1/wishlist`, `PATCH /api/v1/wishlist/{wishlistId}`, `DELETE /api/v1/wishlist/{wishlistId}`), every error envelope, and the two new enum schemas (`WishlistStatus = ACTIVE | PURCHASED` on the REST surface; `FurnitureType` reused from UC-01-recommendation). The file MUST parse as OpenAPI 3.0.x.

**FR-14 (No cross-task regression — additive extension only)** — No existing file under `src/backend/src/main/resources/db/migration/V1..V5*.sql` may be modified. No existing Java controller / service / DTO may be modified other than strictly additive extensions (e.g., adding a getter on `Wishlist`, adding a field on `application.yml`). The `SpaceController` and its tests must remain untouched.

### Mobile — RecommendationScreen wire-up + new WishlistScreen

**FR-15 (`addToWishlist` API client method)** — Add to `src/mobile/src/api/wishlist.ts` (new file):
```ts
export type WishlistStatus = 'ACTIVE' | 'PURCHASED';
export type WishlistItem = {
  wishlistId: string;
  userId: string;
  furnitureId: string;
  category: 'desk' | 'bed' | 'chair' | 'lighting';
  price: number;
  status: 'Active' | 'Purchased';  // DB-casing, matches V1 enum
  addedAt: string;
  purchasedAt: string | null;
  furnitureSnapshot: { name: string; imageUrl: string | null; colorHex: string; type: string } | null;
};
export async function addToWishlist(opts: { baseUrl; userId; furnitureId; category; price }): Promise<WishlistItem & { alreadyExists: boolean }>;
export async function listWishlist(opts: { baseUrl; userId; status?: WishlistStatus }): Promise<{ items: WishlistItem[]; totalActive: number; totalPurchased: number; truncated?: boolean }>;
export async function patchWishlist(opts: { baseUrl; userId; wishlistId; status: WishlistStatus }): Promise<WishlistItem>;
export async function deleteWishlist(opts: { baseUrl; userId; wishlistId }): Promise<void>;
```
All four methods reuse `ApiError` from `src/mobile/src/api/client.ts` and the `X-User-Id` + `Accept: application/json` header pattern. No new runtime-validation library.

**FR-16 (`RecommendationScreen` — wire the stub to the real API)** — Modify `ItemCard.onAddToWishlist` in `src/mobile/src/screens/RecommendationScreen.tsx`:
- Keep the existing `emitWishlistAddClicked(roomId, furnitureId)` analytics call (UC-01-recommendation AC-48 — regression guard).
- After the analytics emit, call `addToWishlist({ baseUrl, userId, furnitureId, category: item.type, price: item.price })`.
- On success:
  - If `alreadyExists === false` and `status === 'Active'`: toast `"위시리스트에 담았어요."`.
  - If `alreadyExists === true` and the returned row is `status === 'Active'`: toast `"이미 위시리스트에 있어요."`.
  - If `alreadyExists === true` and the returned row is `status === 'Purchased'`: toast `"이미 구매 완료로 표시된 항목이에요."`.
- On failure:
  - `ApiError` with `FURNITURE_NOT_FOUND` or `USER_NOT_FOUND` → toast `"위시리스트에 추가하지 못했어요."`.
  - Any other error → toast `"네트워크 오류로 추가하지 못했어요."`.
- The button MUST NOT change the screen's navigation — tapping it stays on `RecommendationScreen`. This preserves UC-01-recommendation AC-44/AC-45/AC-47 expectations.

**FR-17 (New `WishlistScreen.tsx`)** — Create `src/mobile/src/screens/WishlistScreen.tsx`:
- Receives no route params (reached from `HomeScreen` — FR-19).
- On mount: calls `listWishlist({ baseUrl, userId })` (no filter; gets both states).
- UI: a header `"내 위시리스트"` + a segmented control / two-tab switcher (`"보관 중 (<totalActive>)"` / `"구매 완료 (<totalPurchased>)"`). Default tab: `"보관 중"`.
- Each item card renders `furnitureSnapshot.name`, `furnitureSnapshot.imageUrl` (grey placeholder on load fail — reuse the UC-01 card pattern), category label from the `CATEGORY_LABELS` map in `RecommendationScreen`, price in `"₩<toLocaleString('ko-KR')>"`, and the `addedAt` date formatted as `"YYYY-MM-DD"`. If `furnitureSnapshot == null`, render `"원본 상품이 삭제되었습니다."` + a disabled delete-only card.
- Per-card actions:
  - On an `Active` card: a `"구매 완료"` button → confirms via `Alert.alert` (`"구매 완료로 표시할까요?"` / "확인"/"취소") → on confirm, PATCH status=`PURCHASED` → on success, optimistically move the card to the `"구매 완료"` tab (reorder the local state by inserting at the top of the purchased list + removing from active list).
  - On a `Purchased` card: a `"보관 중으로 되돌리기"` button with identical confirm flow → PATCH status=`ACTIVE`.
  - Both cards: a `"삭제"` button → confirm (`"위시리스트에서 삭제할까요?"`) → DELETE → remove from local state.
- Empty-tab state: `"아직 저장된 가구가 없어요."` (ACTIVE) / `"아직 구매 완료로 표시된 가구가 없어요."` (PURCHASED).
- Loading state: spinner + `"위시리스트를 불러오는 중이에요..."`.
- Error state: `"위시리스트를 불러오지 못했어요."` + `"다시 시도"` button (re-invokes the fetch). `FURNITURE_NOT_FOUND` on a PATCH / DELETE → toast `"이미 삭제된 항목이에요."` and refresh the list.

**FR-18 (Navigation wiring)** — Extend `src/mobile/App.tsx`'s `RootStackParamList` with `Wishlist: undefined` and register the screen:
```tsx
<Stack.Screen name="Wishlist" component={WishlistScreen} options={{ title: '내 위시리스트' }} />
```
This is additive; existing routes (including the `Recommendation: { roomId; preferredStyle? }` entry from UC-01-recommendation FR-24) stay untouched.

**FR-19 (HomeScreen entry point)** — Add a `"위시리스트 보기"` button to `src/mobile/src/screens/HomeScreen.tsx` that `navigation.navigate('Wishlist')`s. The button must not displace the existing `"방 사진 업로드"` CTA (PRD §1 primary action). Additive only — no existing button / layout constant / test fixture is removed.

**FR-20 (No cross-task mobile regression)** — The existing `RecommendationScreen` tests (`__tests__/RecommendationScreen.test.tsx`) continue to pass with ZERO modifications other than (a) a new mock for `addToWishlist` and (b) a new assertion that the stub-analytics event `wishlist_add_clicked` still fires (AC-48 of UC-01-recommendation is preserved by FR-16). Previously-passing assertions (AC-44..AC-47, AC-49, AC-50) remain green.

## 6. Non-Functional Requirements

- **Latency (backend, P95)**:
  - `POST /api/v1/wishlist`: ≤ 150 ms (one SELECT for idempotency + one INSERT).
  - `GET /api/v1/wishlist` for ≤ 100 items: ≤ 200 ms (one indexed SELECT + a small IN-query JOIN for `furnitureSnapshot`).
  - `PATCH` / `DELETE`: ≤ 100 ms.
- **Idempotency**: `POST` is idempotent per `(userId, furnitureId)` (FR-4). `PATCH` is idempotent per target state (FR-7 no-op branches).
- **Concurrency**: two parallel `POST`s for the same `(userId, furnitureId)` MUST result in exactly one row — enforced by the `uq_wishlist_user_furniture` constraint (FR-1); the second insert surfaces as a `DataIntegrityViolationException` which the service catches + translates to a 200 idempotent-hit response.
- **Error envelope conformance**: every non-2xx response matches the shared `ErrorResponse(errorCode, message, correlationId)` record used by UC-01-photo-upload / UC-01-style-selection / UC-01-recommendation. No new envelope shape is introduced.
- **Security**:
  - Every endpoint requires `X-User-Id`. Without it, 400 `MISSING_USER_HEADER` (reuses the shared code — the RN error mapping already recognises it).
  - Ownership is enforced on `GET` / `PATCH` / `DELETE`; a user cannot observe or mutate another user's rows (uses `findByWishlistIdAndUserId`). Unknown-ID-vs-foreign-ID is deliberately collapsed to `WISHLIST_ITEM_NOT_FOUND` (404) to avoid existence leaks.
  - No PII beyond `userId` (opaque) is stored or logged on the wishlist surface.
- **Data retention**: `DELETE` is a hard delete. No soft-delete / archive in v1 (listed in §8).
- **Pagination & caps**: FR-6 enforces a soft cap of `app.wishlist.max-items-per-user` (default 500) via truncation — NOT via insert rejection. Cursor pagination is explicitly future work.
- **Determinism**: `GET` ordering is `updated_at DESC`; ties are broken by `wishlist_id ASC` so test snapshots are stable.
- **i18n**: all user-visible strings are Korean. Enum values (both REST `ACTIVE|PURCHASED` and DB `Active|Purchased`) stay ASCII.
- **No new dependency**: no new runtime dependency added to `build.gradle` or `package.json`. If tests need one (e.g., `@DataJpaTest` for `WishlistRepositoryTest`), it uses the existing Testcontainers + Spring Boot Starter Data JPA already present.

## 7. Data Contract

### DB — wishlist table post-V6
```
wishlist_id   VARCHAR(64)  PK
user_id       VARCHAR(64)  NOT NULL                             -- FK users.user_id  CASCADE
furniture_id  VARCHAR(64)  NOT NULL                             -- FK furniture.furniture_id  RESTRICT
category      VARCHAR(32)  NOT NULL                             -- desk|bed|chair|lighting
price         INT          NOT NULL                             -- KRW integer won, snapshot at add time
status        ENUM('Active','Purchased') NOT NULL DEFAULT 'Active'
added_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   -- NEW in V6
purchased_at  DATETIME     NULL                                 -- NEW in V6
created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP   -- V1, unchanged
updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP  -- V1, unchanged
UNIQUE KEY uq_wishlist_user_furniture (user_id, furniture_id)   -- NEW in V6
INDEX idx_wishlist_user_id (user_id)                            -- V1, unchanged
```

### REST — `POST /api/v1/wishlist`
Request:
```json
{ "furnitureId": "f_desk_001", "category": "desk", "price": 189000 }
```
Success (201 or 200-idempotent):
```json
{
  "wishlistId": "8b0f9a4e-....",
  "userId": "u1",
  "furnitureId": "f_desk_001",
  "category": "desk",
  "price": 189000,
  "status": "Active",
  "addedAt": "2026-04-18T09:14:22",
  "purchasedAt": null,
  "alreadyExists": false
}
```

### REST — `GET /api/v1/wishlist?status=ACTIVE`
```json
{
  "items": [ /* WishlistItem[] with furnitureSnapshot */ ],
  "totalActive": 3,
  "totalPurchased": 1,
  "truncated": false
}
```

### REST — `PATCH /api/v1/wishlist/{wishlistId}`
Request: `{ "status": "PURCHASED" }` → Response 200 with updated `WishlistItem`.

### REST — `DELETE /api/v1/wishlist/{wishlistId}` → 204 no body.

### Error responses — shared envelope
```json
{ "errorCode": "WISHLIST_ITEM_NOT_FOUND", "message": "위시리스트 항목을 찾을 수 없습니다.", "correlationId": "b1..." }
```

## 8. Out of Scope
- **Admin aggregation / analytics** — UC-03-admin-overview will read these rows; this task provides no dashboard or aggregate endpoint.
- **Real payment / checkout** — `Purchased` is user-marked only. No payment gateway, no order table, no receipt.
- **Recommendation re-ranking using purchase history** — the recommender (UC-01-recommendation) does NOT read the wishlist in v1. A future task (`ml-recommender-v2`) may.
- **AR placement preview** — AR-furniture-placement consumes `furnitureId` + dimensions; it does not integrate with the wishlist.
- **Wishlist sharing / export / CSV** — future.
- **Notifications** (price drop, stock alert) — future.
- **Price-change tracking** — `wishlist.price` is a snapshot at add time and does NOT auto-update when the catalog price changes. A future `UC-02-price-watch` task may add a `priceAtAdd` vs `currentPrice` split.
- **Soft delete / archive** — DELETE is hard-delete in v1.
- **Cursor pagination** — FR-6 caps at 500 with truncation; a `?cursor=` design is future work.
- **Bulk operations** — no "mark all purchased" or "delete all". UC-03 admin may later add bulk-delete for moderation.
- **Wishlist-level sort controls** (price ASC/DESC, alpha) — v1 sort is fixed at `updated_at DESC`.
- **Multi-device sync** — rows are user-scoped so sync is trivial-by-construction; no additional sync layer.
- **Authentication beyond `X-User-Id`** — no JWT/OAuth (same stance as every upstream task).

## 9. Dependencies
- **Hard**:
  - `DB-schema-init` (Task 1) — `wishlist` table + `ENUM('Active','Purchased')` + FK to `users` (CASCADE) + FK to `furniture` (RESTRICT) + `idx_wishlist_user_id`.
  - `UC-01-photo-upload` (Task 2) — `X-User-Id` convention, `ErrorResponse` envelope, `@RestControllerAdvice` ordering pattern.
  - `UC-01-recommendation` (Task 5) — `furniture` catalog columns (`name`, `image_url`, `color_hex`, `type`) so `furnitureSnapshot` can be rendered; `RecommendationScreen.ItemCard` + `emitWishlistAddClicked` so FR-16 can wire the real API call without replacing the analytics emit.
- **Consumed by**:
  - `UC-03-admin-overview` (Task 7) — admin `Wishlist` tile aggregates `SELECT status, COUNT(*) FROM wishlist GROUP BY status` and `SUM(price) WHERE status='Purchased'` (Sales tile).
- **No dependency on**: Task 8 (AR-furniture-placement) — independent.

## 10. Open Questions (PRD-ambiguous — flagged rather than guessed)
1. **`Purchased → Active` un-mark**: PRD §6 UC-02 step 5 describes "결제 완료 → Purchased" unidirectionally. Is the user allowed to undo? This spec permits it (FR-7 `PATCH PURCHASED → ACTIVE`) because the PRD does not explicitly forbid it and the class diagram's `updateStatus(newStatus: String)` signature is state-agnostic. If the product owner wants a one-way transition, delete FR-7's `PURCHASED → ACTIVE` row + AC-23 + AC-24 in a follow-up iteration; no migration impact.
2. **Delete on `Purchased`**: PRD does not state whether a purchased item stays forever. This spec allows delete (FR-8) to match the class diagram's lack of an archive method. A product decision to forbid delete on `Purchased` would add a new 409 `PURCHASED_ITEMS_READONLY` code.
3. **`category` source of truth**: PRD §3 stores `category` on the wishlist row; PRD §7 does not duplicate `type` from `Furniture`. This spec accepts the client-supplied `category` on POST (FR-5) but validates it against the 4-value enum; it does NOT verify `wishlist.category == furniture.type` at insert time (a cheap consistency check we could add). If the verifier finds this looseness unacceptable, a follow-up AC can require server-side reconciliation.
4. **Unique `(user_id, furniture_id)` constraint**: PRD is silent. FR-1 adds this because the PRD §7 `addToWishlist(furnitureId)` method cannot produce a deterministic `Wishlist` aggregate without it. If the product owner wants "users can wishlist the same furniture multiple times (e.g. two of them for a twin room)", drop the UNIQUE in V6 and relax FR-4 to always insert. This is the only schema decision that would require a V7 to reverse.
