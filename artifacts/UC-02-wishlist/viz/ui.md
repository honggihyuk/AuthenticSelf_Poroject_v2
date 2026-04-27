# UC-02-wishlist — UI mocks

Low-fidelity ASCII mocks for every user-visible state of the two screens
touched by Task 6: `WishlistScreen.tsx` (new, FR-17 / AC-39..AC-46) and
`RecommendationScreen.tsx` (add-to-wishlist button evolution, FR-16 /
AC-36..AC-38).

All strings are copied from the source files — **if this mock and the
source disagree, the source wins** and the delta is flagged in
`README.md §Deltas`.

Sources of truth:
- `src/mobile/src/screens/WishlistScreen.tsx`
- `src/mobile/src/screens/RecommendationScreen.tsx`
- `src/mobile/src/api/wishlist.ts`

---

## 1. WishlistScreen — loading state (initial mount)

```
+----------------------------------------------------+
| [testID=wishlist-loading]                          |
|                                                    |
|                       (spinner)                    |
|                                                    |
|           위시리스트를 불러오는 중이에요...          |
|                                                    |
+----------------------------------------------------+
```
Anchors: FR-17 / AC-39 / AC-42.

---

## 2. WishlistScreen — default load, Active tab with items

Fetched via `GET /api/v1/wishlist` (no `status=` filter) on mount.
`totalActive=3`, `totalPurchased=1` for this mock.

```
+----------------------------------------------------+
| [testID=wishlist-screen]                           |
|                                                    |
|  내 위시리스트                                     |
|                                                    |
|  +-------------------+ +-------------------+       |
|  | [tab-btn-active]  | | [tab-btn-purchased]      |
|  |  보관 중 (3)      | |  구매 완료 (1)     |       |
|  |  (selected)       | |                   |       |
|  +-------------------+ +-------------------+       |
|                                                    |
|  [tab-content-active]                              |
|  +-----------------------------------------------+ |
|  | [wishlist-card-wl_001]                        | |
|  | +--------+ 모던 책상 600                        | |
|  | | (img)  | 책상 · ₩189,000                     | |
|  | +--------+ 2026-04-18                         | |
|  |   [btn-mark-purchased-wl_001] 구매 완료        | |
|  |   [btn-delete-wl_001]         삭제             | |
|  +-----------------------------------------------+ |
|                                                    |
|  +-----------------------------------------------+ |
|  | [wishlist-card-wl_002]                        | |
|  | +--------+ 러그 플로어 램프                       | |
|  | | (img)  | 조명 · ₩79,000                       | |
|  | +--------+ 2026-04-17                         | |
|  |   [btn-mark-purchased-wl_002] 구매 완료        | |
|  |   [btn-delete-wl_002]         삭제             | |
|  +-----------------------------------------------+ |
|                                                    |
|  +-----------------------------------------------+ |
|  | [wishlist-card-wl_003]                        | |
|  | +--------+ 에어리 의자                          | |
|  | | (img)  | 의자 · ₩129,000                     | |
|  | +--------+ 2026-04-16                         | |
|  |   [btn-mark-purchased-wl_003] 구매 완료        | |
|  |   [btn-delete-wl_003]         삭제             | |
|  +-----------------------------------------------+ |
+----------------------------------------------------+
```
Anchors: FR-17 / AC-39 / AC-40 / AC-43.
Each card renders `furnitureSnapshot.name`, `CATEGORY_LABELS[category]`,
`formatKrw(price)`, then `formatYmd(addedAt)` which slices the
`YYYY-MM-DD` prefix out of the server's `LocalDateTime` string.

> **Delta from task brief**: the brief asked for a relative
> "3일 전 저장" timestamp. Actual source renders an ABSOLUTE
> `YYYY-MM-DD`. Flagged in `README.md §Deltas`.

---

## 3. WishlistScreen — Purchased tab with items

Tab switch is local — tapping `tab-btn-purchased` just calls
`setTab('purchased')`. No additional network call; the original GET
already returned rows of both statuses.

```
+----------------------------------------------------+
| [testID=wishlist-screen]                           |
|                                                    |
|  내 위시리스트                                     |
|                                                    |
|  +-------------------+ +-------------------+       |
|  | [tab-btn-active]  | | [tab-btn-purchased]      |
|  |  보관 중 (3)      | |  구매 완료 (1)     |       |
|  |                   | |  (selected)       |       |
|  +-------------------+ +-------------------+       |
|                                                    |
|  [tab-content-purchased]                           |
|  +-----------------------------------------------+ |
|  | [wishlist-card-wl_004]                        | |
|  | +--------+ 미니멀 침대 퀸                         | |
|  | | (img)  | 침대 · ₩450,000                     | |
|  | +--------+ 2026-04-15                         | |
|  |  [btn-unmark-purchased-wl_004]                | |
|  |                        보관 중으로 되돌리기      | |
|  |  [btn-delete-wl_004]   삭제                    | |
|  +-----------------------------------------------+ |
+----------------------------------------------------+
```
Anchors: FR-17 / AC-43 / AC-52.
Card label `보관 중으로 되돌리기` (not the shorter `되돌리기` in the
task brief — source has the full phrase).

---

## 4. WishlistScreen — empty states

### 4a. Active tab empty

```
+----------------------------------------------------+
| [testID=wishlist-screen]                           |
|  내 위시리스트                                     |
|                                                    |
|  +-------------------+ +-------------------+       |
|  |  보관 중 (0)      | |  구매 완료 (0)    |       |
|  |  (selected)       | |                   |       |
|  +-------------------+ +-------------------+       |
|                                                    |
|  [tab-content-active]                              |
|                                                    |
|                                                    |
|           [testID=empty-active]                    |
|         아직 저장된 가구가 없어요.                  |
|                                                    |
|                                                    |
+----------------------------------------------------+
```
Anchor: AC-44.

### 4b. Purchased tab empty

```
+----------------------------------------------------+
| [testID=wishlist-screen]                           |
|  내 위시리스트                                     |
|                                                    |
|  +-------------------+ +-------------------+       |
|  |  보관 중 (2)      | |  구매 완료 (0)    |       |
|  |                   | |  (selected)       |       |
|  +-------------------+ +-------------------+       |
|                                                    |
|  [tab-content-purchased]                           |
|                                                    |
|           [testID=empty-purchased]                 |
|   아직 구매 완료로 표시된 가구가 없어요.            |
|                                                    |
+----------------------------------------------------+
```
Anchor: AC-45.

> **Delta from task brief**: the brief suggested
> `"구매한 가구가 아직 없어요."` — source uses
> `"아직 구매 완료로 표시된 가구가 없어요."` (grep-verified at
> `WishlistScreen.tsx:297`). Source wins; flagged in
> `README.md §Deltas`.

---

## 5. WishlistScreen — error state (initial GET failed)

Any non-2xx on the initial `getWishlist` resolves into the `error`
branch of the screen (no toast, full-page retry):

```
+----------------------------------------------------+
| [testID=wishlist-error]                            |
|                                                    |
|                                                    |
|          위시리스트를 불러오지 못했어요.            |
|                                                    |
|                                                    |
|          +---------------------------+             |
|          | [btn-wishlist-retry]      |             |
|          |        다시 시도           |             |
|          +---------------------------+             |
|                                                    |
+----------------------------------------------------+
```
Anchor: AC-46.

Matrix of possible `ApiError` codes reaching this branch:

| HTTP | errorCode                      | Root cause                          |
|------|--------------------------------|-------------------------------------|
| 400  | `MISSING_USER_HEADER`          | RN forgot to send `X-User-Id`       |
| 400  | `INVALID_WISHLIST_STATUS_FILTER` | (only if `?status=` is malformed — not possible from this screen, which sends no filter) |
| 404  | `USER_NOT_FOUND`               | stub-auth userId not in `users`     |
| 5xx  | (any)                          | Network / backend failure           |

---

## 6. WishlistScreen — per-action error toasts

In-tab errors are handled as toasts (Android `ToastAndroid.SHORT`;
iOS / other platforms fall back to `Alert.alert('', msg)`):

| Trigger                                      | Toast copy                     | Side-effect             |
|----------------------------------------------|-------------------------------|-------------------------|
| PATCH returns 404 `WISHLIST_ITEM_NOT_FOUND`  | `이미 삭제된 항목이에요.`     | `void fetchOnce()` re-sync |
| PATCH returns any other error                | `상태를 변경하지 못했어요.`   | -                       |
| DELETE returns 404 `WISHLIST_ITEM_NOT_FOUND` | `이미 삭제된 항목이에요.`     | `void fetchOnce()` re-sync |
| DELETE returns any other error               | `삭제하지 못했어요.`         | -                       |

```
+----------------------------------------------------+
|                                                    |
|  [ ... wishlist card list ... ]                    |
|                                                    |
|                                                    |
+----------------------------------------------------+
          +---------------------------+
          |  이미 삭제된 항목이에요.    |   <--- ToastAndroid.SHORT
          +---------------------------+
```
Anchor: AC-42 / AC-43.

---

## 7. RecommendationScreen — add-button toast evolution (FR-16)

Task 6 replaces the UC-01 stub toast `"위시리스트 기능은 준비 중입니다."`
with four real outcomes. The analytics `emitWishlistAddClicked` MUST
still fire synchronously before the network call (AC-49 invariant from
UC-01 AC-48, preserved).

### 7a. Loading (network in flight)

```
+------------- ItemCard --------------+
| (img)  모던 책상 600                  |
|        ₩189,000           [매칭 85%]  |
|        따뜻한 톤 ...                  |
|        +---------------------------+  |
|        | [위시리스트에 추가]         |<-- tap
|        +---------------------------+  |
+--------------------------------------+
(no spinner; RN button stays tappable — AC-37
 permits repeated taps; server idempotency handles it)
```

### 7b. Success — fresh insert  (201)

```
        +---------------------------+
        |  위시리스트에 담았어요.     |   <--- ToastAndroid.SHORT
        +---------------------------+
```
`resp.alreadyExists === false`.

### 7c. Idempotent hit — already in Active  (200)

```
        +---------------------------+
        |  이미 위시리스트에 있어요.  |
        +---------------------------+
```
`resp.alreadyExists === true && resp.status === 'Active'`.

### 7d. Idempotent hit — already Purchased  (200)

```
        +---------------------------------+
        |  이미 구매 완료로 표시된 항목이에요. |
        +---------------------------------+
```
`resp.alreadyExists === true && resp.status === 'Purchased'`.

### 7e. Error — USER_NOT_FOUND / FURNITURE_NOT_FOUND  (404)

```
        +---------------------------------+
        |  위시리스트에 추가하지 못했어요.     |
        +---------------------------------+
```

### 7f. Error — any other failure (network, 5xx, shape mismatch)

```
        +-------------------------------------+
        |  네트워크 오류로 추가하지 못했어요.     |
        +-------------------------------------+
```

Decision tree mirrored from `RecommendationScreen.tsx:279-312`:

```mermaid
flowchart TD
    tap[User taps 위시리스트에 추가] --> emit[emitWishlistAddClicked<br/>analytics sink - AC-49]
    emit --> call[addToWishlist POST /api/v1/wishlist]
    call --> ok{resp ok?}
    ok -- "201  alreadyExists=false" --> t1[위시리스트에 담았어요.]
    ok -- "200  alreadyExists=true<br/>status=Active" --> t2[이미 위시리스트에 있어요.]
    ok -- "200  alreadyExists=true<br/>status=Purchased" --> t3[이미 구매 완료로 표시된 항목이에요.]
    ok -- "ApiError 404 USER_NOT_FOUND<br/>or FURNITURE_NOT_FOUND" --> t4[위시리스트에 추가하지 못했어요.]
    ok -- "other (network / 5xx / shape)" --> t5[네트워크 오류로 추가하지 못했어요.]
```

Anchors: FR-16 / AC-36 / AC-37 / AC-38 / AC-49.

---

## 8. HomeScreen entry point (FR-19)

`btn-open-wishlist` is a simple navigation button — no UI state of its
own:

```
+----------------------------------------------------+
| HomeScreen                                         |
|   ...                                              |
|   +------------------------------------------+     |
|   | [testID=btn-open-wishlist]               |     |
|   |              내 위시리스트                 |     |
|   +------------------------------------------+     |
+----------------------------------------------------+
```
On press: `navigation.navigate('Wishlist')`.  Anchor: AC-47.
