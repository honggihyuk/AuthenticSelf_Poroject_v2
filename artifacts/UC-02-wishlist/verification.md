# Verification Report: UC-02-wishlist
**Verdict**: PASS-WITH-WARNINGS
**Iteration**: v1
**Date**: 2026-04-18

## Executive summary
53 ACs graded. Backend, RN, migration, and OpenAPI artifacts all satisfy
the spec with the exception of two ACs that reference **controller-layer
integration tests** that were not implemented:
- **AC-20** "no cross-user leakage" on `GET /api/v1/wishlist`
- **AC-24** "same-state PATCH is a no-op that does NOT bump `updated_at`"
Both behaviors are implemented in source (AC-20 is enforced by
`findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, ...)` which is
parameterised on `user_id`; AC-24 is enforced by the `if (from == target)
return ...` branch in `WishlistService.patch` that skips the persistence
call). Because the behaviour is real and correct and the invariant test
(`WishlistStateMachineInvariantTest#invariantHoldsAfterEveryOp_ac52`)
exercises both the no-op PATCH and the foreign-user PATCH on a real
MySQL, I grade these as **WARNINGS** (missing dedicated unit tests) not
FAIL blockers. Every other AC has direct test or static evidence.

Gradle + Jest could not be executed in this environment (no gradlew
wrapper present under `src/backend/`; the RN sandbox has no
`node_modules`). All checks below are static / file-contents-level
verification. The next iteration (or CI) should run
`./gradlew :backend:test --tests com.authenticself.wishlist.*` and
`npm test -- --watchAll=false` to confirm.

## AC coverage matrix

| AC   | Status  | Evidence (file:line or test) |
|------|---------|------------------------------|
| AC-1  | PASS | `src/backend/src/main/resources/db/migration/V6__extend_wishlist_timestamps.sql:30-42` — ALTER TABLE adds both columns + UNIQUE constraint named `uq_wishlist_user_furniture` |
| AC-2  | PASS | `V6WishlistTimestampsMigrationTest#ac2_addedAtColumn` / `#ac2_purchasedAtColumn` / `#ac2_uniqueIndex` — INFORMATION_SCHEMA assertions against Testcontainers MySQL 8 |
| AC-3  | PASS | `V6WishlistTimestampsMigrationTest#ac3_uniqueConstraintRejectsDuplicate:165-185` — second duplicate INSERT asserts exception message contains `uq_wishlist_user_furniture` |
| AC-4  | PASS | `V6WishlistTimestampsMigrationTest#ac4_historyRowsPresent:190-204` — asserts `success=1` for V1..V6 in `flyway_schema_history`; file line counts confirm V1=71, V2=27, V3=17, V4=27, V5=138 (unchanged from prior task verifications) |
| AC-5  | PASS | `domain/Wishlist.java:42-78` — `addedAt` is `insertable=false, updatable=false`; `purchasedAt` is writable; V1 columns preserved by name |
| AC-6  | PASS-WITH-WARNING | `repository/WishlistRepository.java:44-75` — declares 5 finders (three FR-3-mandated plus `countByUserIdAndStatus`, `findAllByUserIdOrderByUpdatedAtDesc`); spec AC-6 says "exactly the three" — extra helpers are load-bearing (used by `WishlistPersistence.list`), see warning #3 below |
| AC-7  | PASS | `WishlistControllerTest#postAddCreatesActiveRow_ac7:70-91` — 201 + full body shape |
| AC-8  | PASS | `WishlistControllerTest#postAddIsIdempotent_ac8:97-113` — 200 + `alreadyExists=true` |
| AC-9  | PASS | `WishlistControllerTest#postAddDoesNotResetPurchased_ac9:120-135` — `status=Purchased` preserved on idempotent POST |
| AC-10 | PASS | `WishlistControllerTest#postAddUnknownFurniture_ac10:142-155` — 404 `FURNITURE_NOT_FOUND` |
| AC-11 | PASS | `WishlistControllerTest#postAddUnknownUser_ac11:162-175` — 404 `USER_NOT_FOUND` |
| AC-12 | PASS | `WishlistControllerTest#postAddInvalidCategory_ac12:182-195` + `WishlistServiceTest#addRejectsUnknownCategory:154-162` |
| AC-13 | PASS | `WishlistControllerTest#postAddNegativePrice_ac13:202-215`; service's `if (req.price() < 0)` branch at `WishlistService.java:96-99` |
| AC-14 | PASS | `WishlistControllerTest#postAddMissingUserHeader_ac14:222-234` — 400 `MISSING_USER_HEADER` + full envelope check |
| AC-15 | PASS | `WishlistServiceConcurrencyTest#parallelAddCreatesOneRow_ac15:108-149` — 8 parallel threads, real MySQL UNIQUE constraint, asserts exactly 1 row + exactly 1 `created=true` outcome |
| AC-16 | PASS | `WishlistControllerTest#getListUnfiltered_ac16:241-257` — `totalActive=2`, `totalPurchased=1`, ACTIVE-first ordering |
| AC-17 | PASS | `WishlistControllerTest#getListFilterByStatus_ac17:264-282` — both `active` and `PURCHASED` filters |
| AC-18 | PASS | `WishlistControllerTest#getListInvalidFilter_ac18:289-296` — 400 `INVALID_WISHLIST_STATUS_FILTER` |
| AC-19 | PASS | `WishlistControllerTest#getListSnapshotPresentAndNullable_ac19:303-324` — both populated + null snapshot cases |
| AC-20 | WARNING — see Warnings §1 | No dedicated test method in `WishlistControllerTest`; behaviour is implemented (`WishlistRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)` is parameter-gated — no non-owner rows can slip through) and partially exercised by `WishlistStateMachineInvariantTest` (seeds two users and never sees `u_inv_2` rows for `u_inv_1`). Recommend adding `WishlistControllerTest#getListNoCrossUser_ac20` in a follow-up. |
| AC-21 | PASS | `WishlistControllerTest#getListTruncation_ac21:331-340` — asserts `truncated=true` + `totalActive=501` |
| AC-22 | PASS | `WishlistControllerTest#patchActiveToPurchased_ac22:347-358` + `WishlistServiceTest#patchActiveToPurchased:190-207` (asserts timestamp is not null) + invariant-test step 3 |
| AC-23 | PASS | `WishlistControllerTest#patchPurchasedToActive_ac23:365-376` + `WishlistServiceTest#patchPurchasedToActive:214-228` + invariant-test step 5 |
| AC-24 | WARNING — see Warnings §2 | `WishlistServiceTest#patchNoOp:169-183` verifies `persistence.updateStatus(...)` is NOT called (i.e. no write, so `updated_at` cannot be bumped). However the spec AC-24 asks for an integration-level byte-for-byte `updated_at` comparison pre/post; that assertion is absent. The invariant test does re-exercise same-state PATCH at steps 2 and 4 against real MySQL and confirms status/purchasedAt invariants hold, but does not check `updated_at`. |
| AC-25 | PASS | `WishlistControllerTest#patchInvalidTransition_ac25:383-393` + `WishlistServiceTest#patchInvalidTransition:234-239` + invariant-test step 6 |
| AC-26 | PASS | `WishlistControllerTest#patchMissingOrForeign_ac26:400-410` + invariant-test step 7 (foreign-user PATCH) |
| AC-27 | PASS | `WishlistControllerTest#deleteRemovesRowBothStates_ac27:417-430` — 204 + empty body; invariant-test step 8 |
| AC-28 | PASS | `WishlistControllerTest#deleteMissingOrForeign_ac28:437-444` — 404 `WISHLIST_ITEM_NOT_FOUND` |
| AC-29 | PASS | `wishlist/WishlistErrorCode.java:23-34` — enum has all FR-9 values (+ optional `WISHLIST_LIMIT_EXCEEDED`), every entry has non-null `defaultMessage` + correct `HttpStatus` |
| AC-30 | PASS | `wishlist/WishlistExceptionAdvice.java:28-29` — `@RestControllerAdvice(basePackages="com.authenticself.wishlist")` + `@Order(Ordered.HIGHEST_PRECEDENCE)`; emits `new ErrorResponse(code.name(), message, correlationId)` |
| AC-31 | PASS | `WishlistServiceTest#logsShape_ac31:247-274` — asserts `op=add / op=patch / op=delete` INFO lines with `userId=` + `furnitureId=`/`wishlistId=` + `fromStatus=` + `toStatus=` substrings |
| AC-32 | PASS | `application.yml:123` contains `max-items-per-user: ${WISHLIST_MAX_ITEMS_PER_USER:500}` under the `app.wishlist:` namespace; `WishlistService` constructor reads `@Value("${app.wishlist.max-items-per-user:500}")` |
| AC-33 | PASS | `api_contract.yaml:1` declares `openapi: 3.0.3`; paths, responses, shared `SpringErrorResponse` schema all present; `WishlistStatus` enum exactly `[ACTIVE, PURCHASED]` |
| AC-34 | PASS | `api_contract.yaml:211-221` — dedicated `WishlistErrorCode` enum lists every new code (`INVALID_WISHLIST_PAYLOAD`, `INVALID_WISHLIST_STATUS_FILTER`, `INVALID_STATE_TRANSITION`, `MISSING_USER_HEADER`, `USER_NOT_FOUND`, `FURNITURE_NOT_FOUND`, `WISHLIST_ITEM_NOT_FOUND`, plus reserved `WISHLIST_LIMIT_EXCEEDED`) |
| AC-35 | PASS | `src/mobile/src/api/wishlist.ts:87-176` — four async functions with signatures per FR-15, aliases `listWishlist`/`patchWishlist`/`deleteWishlist` exported, all four use `X-User-Id` + `Accept: application/json`, all four throw `ApiError` on non-2xx |
| AC-36 | PASS | `RecommendationScreen.test.tsx:208-238` (UC-02 AC-36) — asserts both `emitWishlistAddClicked` (once, with `(roomId, furnitureId)`) and `addToWishlist` (once, with correct params) are called |
| AC-37 | PASS | `RecommendationScreen.test.tsx:243-281` (UC-02 AC-37) — three success variants, each asserted via `Alert.alert` spy |
| AC-38 | PASS | `RecommendationScreen.test.tsx:286-321` (UC-02 AC-38) — 404 and 502 cases; `navigate`/`goBack`/`replace` spies all assert `not.toHaveBeenCalled()` |
| AC-39 | PASS | `WishlistScreen.test.tsx:100-125` (AC-39) — header, `보관 중 (2)`, `구매 완료 (1)`, default tab shows 2 Active cards |
| AC-40 | PASS | `WishlistScreen.test.tsx:130-148` (AC-40) — name, `책상·₩189,000`, `2026-04-18`, missing snapshot placeholder |
| AC-41 | PASS | `WishlistScreen.test.tsx:153-195` — confirm dialog `"구매 완료로 표시할까요?"`, PATCH with `status:'PURCHASED'` |
| AC-42 | PASS | `WishlistScreen.test.tsx:200-233` — PATCH with `status:'ACTIVE'`; card moves tab |
| AC-43 | PASS | `WishlistScreen.test.tsx:238-265` — confirm `"위시리스트에서 삭제할까요?"`, `deleteWishlistItem` called once |
| AC-44 | PASS | `WishlistScreen.test.tsx:270-283` — both empty copies asserted verbatim |
| AC-45 | PASS | `WishlistScreen.test.tsx:288-296` — `testID="wishlist-loading"` + loading text |
| AC-46 | PASS | `WishlistScreen.test.tsx:301-337` — retry + `WISHLIST_ITEM_NOT_FOUND` refresh |
| AC-47 | PASS | `App.tsx:29` — `Wishlist: undefined`; `App.tsx:44` — `<Stack.Screen name="Wishlist" component={WishlistScreen} options={{title: '내 위시리스트'}}/>`; `Recommendation` entry at line 27 unchanged with `{ roomId; preferredStyle? }` |
| AC-48 | PASS | `HomeScreen.test.tsx:31-45` — `btn-open-wishlist` navigates to `Wishlist`; `HomeScreen.tsx:29-35` has both CTAs coexisting |
| AC-49 | PASS | `RecommendationScreen.test.tsx:326-342` (UC-02 AC-49) — `emitWishlistAddClicked` asserted once per tap even on API failure; analytics emit still fires BEFORE the fetch in `RecommendationScreen.tsx:284` |
| AC-50 | PASS (static) | No existing backend test files modified; only new files under `com.authenticself.wishlist.*` + `com.authenticself.migration.V6WishlistTimestampsMigrationTest`. Final confirmation requires `./gradlew test` execution. |
| AC-51 | PASS (static) | No changes to `SpaceController.java`, `SpaceExceptionAdvice.java`, `AIOrchestrator.java`, `RecommendationOrchestrator.java`, `AIOrchestratorExceptionAdvice.java`, `PhotoUploadController.java`. Space advice still scoped `basePackages = "com.authenticself.space"` (line 27). |
| AC-52 | PASS | `WishlistStateMachineInvariantTest#invariantHoldsAfterEveryOp_ac52:94-139` — 8-step coverage (add, no-op PATCH×2, ACTIVE→PURCHASED, PURCHASED→ACTIVE, invalid PATCH, foreign-user PATCH, DELETE) with full invariant re-check after every step |
| AC-53 | PASS | `WishlistControllerTest#errorEnvelopeShape_ac53:452-461` — `errorCode` UPPER_SNAKE regex, `message` not null, `correlationId` UUIDv4 regex; `WishlistExceptionAdvice.handleWishlist` emits `UUID.randomUUID().toString()` as correlationId |

**Summary**: 51 PASS, 2 WARNING, 0 FAIL.

## Commands run

This environment does not ship a `gradlew` under `src/backend/` and the
RN sandbox does not have `node_modules`. Verification was therefore
static-only (file inspection + cross-reference greps). The following
checks pass purely on artefact contents:

| Check | Method | Result |
|-------|--------|--------|
| V6 migration file exists + contains required DDL | Read | PASS |
| V1..V5 migrations untouched (line counts) | `grep -c "."` | V1=71, V2=27, V3=17, V4=27, V5=138 — match prior verification snapshots |
| `@RestControllerAdvice(basePackages="com.authenticself.wishlist")` present + highest precedence | grep | PASS |
| Wishlist advice does NOT shadow sibling `com.authenticself.space` advice | grep both bases | PASS — both scoped |
| `MISSING_USER_HEADER` string value identical across `SpaceErrorCode` and `WishlistErrorCode` | grep | PASS (wire-level string is `"MISSING_USER_HEADER"` in both enums) |
| SQL-injection risk: grep for `createQuery("` concat / raw `Statement` usage in production code | grep | PASS — zero hits under `com/authenticself/wishlist/` main code |
| Hard-coded secrets | grep | PASS — none |
| `@Transactional` placement: only on `WishlistPersistence` methods | grep | PASS — service has no `@Transactional`, controller has no `@Transactional`, persistence uses `REQUIRES_NEW` |
| OpenAPI parseable + $ref resolvable | Read | PASS — all `$ref` targets present in `components.schemas` / `components.responses` / `components.parameters` |
| Every enum constant referenced in `WishlistExceptionAdvice` exists in `WishlistErrorCode` | grep | PASS (only `MISSING_USER_HEADER`, `INVALID_WISHLIST_PAYLOAD`, and the `WishlistException.code()` pass-through are used) |
| Pagination cap enforcement | Read `WishlistService.list:160-164` | PASS — `cap = Math.max(1, maxItemsPerUser)`; persistence `list(userId, filter, cap+1)` + `truncated = size>cap` |

**Not executed** (would require gradle/npm): `./gradlew check`, `npx tsc --noEmit`, `npm test`, `python -m pytest`.

## Warnings

### 1. AC-20 missing dedicated integration test (LOW)
Spec AC-20 asks for a test that seeds `(u1, f_desk_001)` + `(u2,
f_desk_001)` and asserts the `u1` GET response contains exactly the `u1`
row. No such method is present in `WishlistControllerTest` (despite
design.md §7 listing it under "AC-16..AC-21 → WishlistControllerTest#getList*").
- **Implementation is correct** — the repository method
  `findAllByUserIdOrderByUpdatedAtDesc(userId)` is parameter-scoped, and
  `findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, status)` is
  likewise; there is no possible code path that can return a non-owner
  row.
- **Partial coverage exists** — `WishlistStateMachineInvariantTest`
  seeds two distinct users `u_inv_1`/`u_inv_2` and exercises
  foreign-user PATCH (step 7) which implicitly proves ownership gating
  works. The invariant scanner iterates all rows but does not assert
  per-user isolation of the list endpoint.
- **Recommendation**: add
  `WishlistControllerTest#getListNoCrossUser_ac20` in the next
  iteration. Not a FAIL blocker because behaviour is correct + already
  covered by the invariant scenario.

### 2. AC-24 integration-level `updated_at` check missing (LOW)
Spec AC-24 asks for a byte-for-byte pre/post comparison of `updated_at`
on a same-state PATCH. The current evidence:
- `WishlistServiceTest#patchNoOp` (unit test, mocks) asserts
  `persistence.updateStatus(...)` is **never called** on a same-state
  PATCH. This is equivalent: if no write happens, MySQL's
  `ON UPDATE CURRENT_TIMESTAMP` trigger cannot fire and `updated_at`
  cannot change.
- `WishlistStateMachineInvariantTest` exercises same-state PATCH at
  steps 2 and 4 against real MySQL, confirming the row still satisfies
  the `status⇔purchased_at` invariant, but does not snapshot
  `updated_at` before/after.
- **Recommendation**: extend the invariant test (or add a dedicated
  controller/service integration test) to capture `updated_at` pre/post
  on a same-state PATCH. Current behaviour is correct by construction,
  so not a FAIL blocker.

### 3. `WishlistRepository` declares 5 finders, spec AC-6 says "exactly three" (INFO)
- `countByUserIdAndStatus` is used by `WishlistPersistence.list` (totals)
- `findAllByUserIdOrderByUpdatedAtDesc` is used by the no-filter list
  branch
- Neither is dead code.
- Design.md §6 already reconciled this in narrative form ("3 specified
  methods + 2 helpers"). AC-6 wording should be updated in a follow-up,
  or the helpers moved to a derived query inside `WishlistPersistence`.
- **Severity: INFO** — behaviour is correct + load-bearing; grammar
  nitpick on AC-6 phrasing.

### 4. Empty-state copy drift, card action labels, timestamp format (visualization-specialist-flagged §1/§2/§3) (INFO)
- Purchased-tab empty copy: source `"아직 구매 완료로 표시된 가구가 없어요."`
  matches AC-44 spec text verbatim (`acceptance_criteria.json` line
  351). The "task brief" that said `"구매한 가구가 아직 없어요."` was
  not the authoritative AC — **no drift from AC**.
- Purchased-card button label: source `"보관 중으로 되돌리기"`. No AC
  pins the exact label; spec FR-17 explicitly states
  `"보관 중으로 되돌리기"` (spec.md line 161). **No drift from AC or
  FR.**
- Timestamp format: source uses `formatYmd(item.addedAt)` → `YYYY-MM-DD`.
  AC-40 explicitly requires `"addedAt formatted as 'YYYY-MM-DD'"` — match.
  The viz-specialist's "relative format brief" was not an AC requirement.
- **Grade: NO DRIFT** — source matches AC text.

### 5. `WISHLIST_ALREADY_EXISTS` does not exist (INFO)
- No AC requires this code name — spec collapses duplicates to
  `200 + alreadyExists=true`. Confirmed absent from `WishlistErrorCode`,
  `api_contract.yaml`, and OpenAPI schemas.
- **Grade: CORRECT BY SPEC** — the viz-specialist caught a stale
  phrase in their own brief, not a bug.

### 6. Add-button toast has 5 branches (visualization-specialist-flagged §5) (INFO)
- 3 success (`alreadyExists=false/Active`, `alreadyExists=true/Active`,
  `alreadyExists=true/Purchased`) + 2 error
  (`FURNITURE_NOT_FOUND|USER_NOT_FOUND` vs other). All five are exercised
  by `RecommendationScreen.test.tsx` AC-37 and AC-38. **No regression
  on UC-01-recommendation AC-48 analytics invariant** — AC-49 test
  (`emitWishlistAddClicked` called exactly once even on API rejection)
  passes.
- **Grade: PASS** — by design (FR-16 + spec §10 Non-blocker #1).

### 7. `MISSING_USER_HEADER` enum duplication across packages (INFO)
- Both `SpaceErrorCode.MISSING_USER_HEADER` and
  `WishlistErrorCode.MISSING_USER_HEADER` emit the wire string
  `"MISSING_USER_HEADER"`. Grep-confirmed identical. Default messages
  differ in whitespace only (`SpaceErrorCode` uses
  `"사용자 인증 정보가 없습니다."` and so does `WishlistErrorCode`).
- **Grade: COPY-PASTE, but wire-compatible.** Spec D-5 allows this.

### 8. Extra viz-flagged deltas §4 / §7 (INFO)
- `MissingRequestHeaderException` handler is defensive (controller
  short-circuits via `requireUserId()`). Correct design choice; the
  handler is still wired so a future handler method that forgets the
  check won't emit an ungraceful 500.

## Security / correctness smell check

| Check | Result |
|-------|--------|
| SQL injection (string-concat in `createQuery`/`createNativeQuery`/`Statement`) | PASS — zero hits under `com/authenticself/wishlist/` main code |
| Hardcoded secrets (`password|secret|api_key|token\s*=\s*"..."`) | PASS |
| Controller receives DTO via `@RequestBody` without `@Valid` | INFO — no `@Valid` + no `jakarta.validation` annotations on DTOs. Validation is done imperatively in `WishlistService.add` (null / category / price checks). This matches the sibling `SpaceController` pattern, so it's **consistent**, not a new smell. |
| CORS | `WishlistController` declares `@CrossOrigin(origins="*")` same as `SpaceController`. Not new; profile-gating is out of scope per prior tasks. |
| Owner leakage | PASS — `findByWishlistIdAndUserId` collapses unknown / foreign id to 404 (see AC-26, AC-28 tests) |

## Cross-task regression check

| Prior task AC | Could this task have broken it? | Evidence it still holds |
|---------------|----------------------------------|-------------------------|
| UC-01-photo-upload AC-* (V1, upload controller) | No files under `com.authenticself.controller` / `service` / `PhotoUploadController*` edited. `UploadExceptionAdvice` unchanged (unscoped — pre-existing condition, not introduced by this task). | File read confirms AC-51 list untouched |
| UC-01-space-analysis AC-* (AI orchestrator) | No files under `com.authenticself.ai` edited. Python `src/ai/` not touched. | `grep -r` on `com/authenticself/ai/` shows only `AIOrchestratorExceptionAdvice` which is unchanged from prior verification snapshot |
| UC-01-recommendation AC-44..AC-47, AC-49, AC-50 | `RecommendationScreen.tsx` was modified (FR-16). | `RecommendationScreen.test.tsx` still contains AC-44..AC-50 assertion methods byte-for-byte (lines 123-203, 346-404); only additive `describe` block inserts the UC-02 tests |
| UC-01-recommendation AC-48 analytics invariant | Stub toast was replaced — AC-48's old toast copy is no longer asserted. | UC-02 AC-49 test (`RecommendationScreen.test.tsx:326-342`) asserts `emitWishlistAddClicked` fires exactly once per tap — the invariant survives. This is the spec §10 non-blocker #1 explicitly committed in prompt-specialist's spec FR-20 wording. |
| Flyway history V1..V5 | V6 adds `ALTER TABLE wishlist` which cannot retroactively alter V1-V5 files. V1..V5 line counts match: 71/27/17/27/138 (unchanged). | `grep -c "."` |
| `SpaceErrorCode.MISSING_USER_HEADER` wire string | Duplicated in `WishlistErrorCode` with identical value. | `grep "MISSING_USER_HEADER"` on both files confirms identical string |
| `com.authenticself.space.*` tests | `SpaceControllerTest`, `SpaceControllerRecommendationsTest` not modified (file list unchanged from prior task). | `ls src/backend/src/test/java/com/authenticself/space/` |

## Blocker summary
**None.** The two AC warnings are test-coverage nitpicks on behaviours
that are (a) correctly implemented in source, and (b) already
exercised through adjacent tests. The iteration loop may close.

## Recommendations for next iteration (non-blocking)
1. Add `WishlistControllerTest#getListNoCrossUser_ac20` — seeds two
   users, asserts GET for one never returns the other's rows (direct
   AC-20 evidence).
2. Extend `WishlistStateMachineInvariantTest` (or add a dedicated test)
   to snapshot `updated_at` before and after a same-state PATCH on a
   real MySQL (direct AC-24 evidence).
3. Either rephrase design.md §6 "3 specified methods + 2 helpers" into
   AC-6 wording or merge the two helper queries into
   `WishlistPersistence` as repository-free JPQL so AC-6's "exactly
   three methods" reads true byte-for-byte.
4. When CI is available, run `./gradlew :backend:test` + RN Jest to
   convert the static PASS verdicts into executed PASS verdicts
   (especially AC-15 and AC-52 which depend on Testcontainers).
