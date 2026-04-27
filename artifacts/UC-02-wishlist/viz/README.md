# UC-02-wishlist — viz artifact index

Visualization artifacts for Task 6 (UC-02-wishlist). All diagrams are
inline Mermaid / ASCII — no external renderers or image hosts.
Style and naming follow `artifacts/UC-01-recommendation/viz/`.

## Files

| File                | Type                              | Primary FR/AC anchors |
|---------------------|-----------------------------------|-----------------------|
| `sequence.mmd`      | Mermaid `sequenceDiagram`         | three flows on one canvas: POST add (idempotent fast-path + UNIQUE race), GET list + tab switch, PATCH Active <-> Purchased incl. INVALID_STATE_TRANSITION; FR-4..FR-10 / AC-7..AC-28 |
| `architecture.mmd`  | Mermaid `flowchart LR`            | RN screens -> `wishlist.ts` -> `WishlistController` -> `WishlistService` -> `WishlistPersistence` -> MySQL `wishlist`/`users`/`furniture`; DTO annotations on every edge; NEW vs. REUSED modules color-coded; FR-13 / FR-14 / FR-18 / FR-19 |
| `state_machine.mmd` | Mermaid `stateDiagram-v2`         | ACTIVE <-> PURCHASED with `purchased_at` set/clear semantics, same-state no-op, rejected transitions -> INVALID_STATE_TRANSITION, DELETE terminal sink; FR-7 / FR-10 / AC-22..AC-25 / AC-52 |
| `ui.md`             | ASCII + Mermaid flowchart         | WishlistScreen loading / Active-with-items / Purchased-with-items / per-tab empty / error page / per-action toast matrix, plus RecommendationScreen add-button toast evolution (5 branches); FR-16 / FR-17 / AC-36..AC-46 |
| `er.mmd`            | Mermaid `erDiagram`               | post-V6 `wishlist` table with `uq_wishlist_user_furniture` UNIQUE + `added_at` / `purchased_at` columns and per-column RO/RW annotations; edges to `users` + `furniture`; FR-1 / FR-2 / AC-1..AC-6 |
| `README.md`         | this file                         | index + deltas spotted + open questions carried forward |

## Diagram coverage vs. spec

- **Sequence:** fully visualized end-to-end. Covers POST fresh (201),
  POST idempotent fast-path (200), POST UNIQUE-race slow-path (200),
  GET mount (200), PATCH real transition (200), PATCH same-state no-op
  (200), PATCH unknown-status -> 400 `INVALID_STATE_TRANSITION`, PATCH
  foreign ownership -> 404 `WISHLIST_ITEM_NOT_FOUND`, all
  400/404 guard branches on POST, plus the analytics-emit invariant
  (AC-49) preserved on `RecommendationScreen`.
- **Architecture:** fully visualized. Every NEW bean / entity / migration
  / RN module is dashed-blue, every REUSED one (Task-1 User /
  Task-5 Furniture / shared `api/client.ts`) is grey. All edges carry
  DTO / route / SQL annotations.
- **State machine:** fully visualized. The AC-52 invariant
  (`status <=> purchased_at IS NULL`) is spelled out on both states.
- **UI:** every `testID` emitted by `WishlistScreen.tsx` appears on the
  mocks. The five-way toast decision tree in `RecommendationScreen` is
  rendered as a Mermaid flowchart.
- **ER:** the V6 delta + the `uq_wishlist_user_furniture` UNIQUE + every
  column's mutability class are annotated. PRD section 7 cardinalities
  (User 1 -- 0..* Wishlist, Wishlist 0..* -- 1 Furniture) are preserved.

## Deltas spotted between design.md / api_contract.yaml and actual source

Drawing the diagrams surfaced the following mismatches. Per the task
rule ("trust the source"), the diagrams follow the source and I record
the deltas here for the verification-specialist.

1. **Empty-state copy for the Purchased tab** — task brief expected
   `"구매한 가구가 아직 없어요."`; actual source at
   `WishlistScreen.tsx:297` renders
   `"아직 구매 완료로 표시된 가구가 없어요."`. The Active-tab copy
   (`"아직 저장된 가구가 없어요."`) matches the brief.
   Diagrams follow the source. **Severity: cosmetic.**

2. **Purchased-card action label** — task brief shortened it to
   `"되돌리기"`; actual source at `WishlistScreen.tsx:381` renders
   `"보관 중으로 되돌리기"`. Diagrams follow the source.
   **Severity: cosmetic.**

3. **Card timestamp format** — task brief asked for relative
   `"3일 전 저장"` / `"2일 전 구매"` copy; actual source uses
   `formatYmd(item.addedAt)` which emits an ABSOLUTE
   `YYYY-MM-DD` slice of the server's `LocalDateTime` string
   (`WishlistScreen.tsx:70-77` + `:362`). There is also no
   `purchasedAt` timestamp rendered on Purchased cards — the card
   still shows `addedAt` regardless of tab. **Severity: real UX
   gap.** If the AC requires a separate "구매" timestamp on the
   Purchased card this will fail verification; the AC as written
   (AC-43) does not mandate a relative format so I treat it as
   non-blocking but flag it for the verifier.

4. **`WISHLIST_ALREADY_EXISTS` does not exist** — the task brief asked
   the sequence diagram to "contrast" the idempotent 200 branch with a
   `WISHLIST_ALREADY_EXISTS` error-code branch. That code is NOT in
   `WishlistErrorCode.java` (verified against the source — the only
   409 entry is the reserved `WISHLIST_LIMIT_EXCEEDED`). The sequence
   diagram accordingly shows only the 200 idempotent branch and
   includes an explicit note that duplicates never surface as 409.

5. **RN add-button toast has FOUR success/idempotent branches, not
   three** — `RecommendationScreen.tsx:293-301` differentiates
   `alreadyExists && status === 'Purchased'` -> `"이미 구매 완료로
   표시된 항목이에요."` from `alreadyExists && status === 'Active'`
   -> `"이미 위시리스트에 있어요."`. Plus two error toasts
   (`"위시리스트에 추가하지 못했어요."` for 404s and
   `"네트워크 오류로 추가하지 못했어요."` for anything else). The
   `ui.md` §7 decision tree renders all five branches.

6. **Extra `WishlistRepository` finders** — design.md §6 describes
   `WishlistRepository` as "3 specified methods + 2 helpers"; the
   source declares exactly **5** query methods (`findByUserIdAndFurnitureId`,
   `findAllByUserIdAndStatusOrderByUpdatedAtDesc`,
   `findByWishlistIdAndUserId`, `countByUserIdAndStatus`,
   `findAllByUserIdOrderByUpdatedAtDesc`). All five are used by
   `WishlistPersistence`. No dead code; the "3 + 2" framing in
   design.md is narrative only. **Severity: design-doc wording.**

7. **Controller short-circuits before the advice `handleMissingHeader`
   can fire** — `WishlistController` declares the `X-User-Id` header
   as `required=false` and calls `requireUserId(userId)` in every
   handler, which throws `WishlistException(MISSING_USER_HEADER)`
   first. The `MissingRequestHeaderException` handler in
   `WishlistExceptionAdvice` is therefore defensive-only (reachable
   only if a future method forgets the explicit check). Architecture
   diagram marks it as such; sequence diagram only shows the
   `requireUserId` path. This matches design-decision D-5.

## Open questions carried from design.md section 10

Non-blocking per design-specialist — the verification-specialist
should surface them in its report but NOT gate on them.

1. **AC-48 toast-copy evolution** — UC-01-recommendation AC-48 spelled
   `"위시리스트 기능은 준비 중입니다."`. UC-02-wishlist intentionally
   replaces that text with the five real outcomes (see `ui.md §7`).
   UC-02 AC-49 preserves only the `emitWishlistAddClicked` invariant
   from UC-01 AC-48, not the toast copy. Design-specialist already
   flagged this; diagrams reflect the new behaviour.

2. **`MISSING_USER_HEADER` duplicated across packages** — the string
   value is identical to `SpaceErrorCode.MISSING_USER_HEADER`; the
   wishlist enum defines its own entry rather than importing the
   space enum. Design-decision D-5. Diagrams note it on the
   `WishlistErrorCode` node but do not draw an edge between the two
   enums (there is none — they are duplicated by value).

3. **Extra repository helpers beyond FR-3's spec** — see delta #6.
   The two "helpers" (`countByUserIdAndStatus` +
   `findAllByUserIdOrderByUpdatedAtDesc`) are load-bearing (they
   power `totalActive`/`totalPurchased` counts and the no-filter
   GET branch). Recommend verification-specialist treat this as
   "design.md wording to update" rather than a gap.

## Conventions

- Korean UI strings are quoted verbatim from the source files — DO NOT
  normalize spacing or punctuation.
- `testID=...` tokens are quoted verbatim from the screen files so the
  verification tests can cross-reference them.
- Mermaid `sequenceDiagram` uses `<br/>` for line breaks inside
  participant labels (Mermaid does not support `\n` in that position).
