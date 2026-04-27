# Follow-up D-3 — `MISSING_USER_HEADER` 400/401 Alignment Proposal

**Status**: DRAFT — awaiting spec-owner decision
**Date**: 2026-04-20
**Source warning**: W-19 (UC-01-recommendation verification §"Warnings")
**Prepared by**: orchestrator (main Claude session)

---

## 1. Current state — four enums, two HTTP statuses

| Package | Enum constant | HTTP status | Korean message | Emitted from |
|---|---|---|---|---|
| `com.authenticself.service.UploadErrorCode` | `MISSING_USER_HEADER` | **401 UNAUTHORIZED** | `"사용자 인증 정보가 없습니다. 앱을 다시 시작해주세요."` | `PhotoUploadController`, `PhotoUploadService` |
| `com.authenticself.space.SpaceErrorCode` | `MISSING_USER_HEADER` | **400 BAD_REQUEST** | `"사용자 인증 정보가 없습니다."` | `SpaceController` (analyze/style/recommend endpoints) |
| `com.authenticself.wishlist.WishlistErrorCode` | `MISSING_USER_HEADER` | **400 BAD_REQUEST** | `"사용자 인증 정보가 없습니다."` | `WishlistController` |
| `com.authenticself.admin.AdminErrorCode` | `MISSING_USER_HEADER` | **400 BAD_REQUEST** | `"사용자 인증 정보가 없습니다."` | `AdminAuthorizer` (all admin endpoints) |

**The drift**: Upload (Task-2) chose 401 + longer copy. Every later task (Tasks 3-7) chose 400 + the short copy. Client-visible error code string is byte-identical across all four; HTTP status and message differ only for Upload.

## 2. How the drift happened

- **Task-2 (UC-01-photo-upload)** was the first task to introduce an error envelope. The PRD §6 UC-01 specs treated the missing user header as an "auth failure" (needs re-login), so 401 + restart-the-app copy was chosen.
- **Task-3 (UC-01-space-analysis)** introduced `SpaceErrorCode.MISSING_USER_HEADER` and re-graded the error as "client sent a malformed request" (missing required header = validation failure), landing on 400.
- **Tasks 4-7** copied the 400 pattern verbatim from Task-3, enforcing consistency within the Space/Wishlist/Admin family.

Upload was never harmonized because:
- Iteration-5 verification flagged the drift as a pre-existing Task-2 inconsistency, not a Task-5 regression
- UC-02-wishlist D-5 accepted a "duplicate MISSING_USER_HEADER across 3 enums, byte-identical wire values" pattern
- AC-60 (scope-guard) preserved the duplication as intentional

## 3. Semantic question the spec-owner must answer

> **When a client sends a request without the `X-User-Id` header, is that a 400 (client sent a malformed request) or a 401 (authentication required)?**

### Arguments for **400 BAD_REQUEST** (current majority)

- `X-User-Id` is a **required parameter of the request contract**, not a bearer/JWT/session token. Missing it is a shape violation, not an authentication failure.
- No authentication challenge header (`WWW-Authenticate`) is returned with these 400s — a 401 response traditionally expects one.
- `X-User-Id` is ALWAYS populated by the client's `settings` module; a missing header indicates a bug in the client, not an expired session.
- All modern REST style guides (Google, Microsoft) prefer 400 for missing required parameters.

### Arguments for **401 UNAUTHORIZED** (Upload's position)

- Philosophically, `X-User-Id` IS a thin form of authentication — it identifies the caller. RFC 7235 says 401 applies "if the request lacks valid credentials for the target resource".
- The client UX ideally differentiates "retry this request" (400) from "re-login and retry" (401). If `X-User-Id` comes from an identity source that can go stale, 401 triggers a re-auth flow; 400 triggers an error toast.
- User-visible message for Upload already mentions "앱을 다시 시작해주세요" — implying a stronger remediation action than a generic 400 toast.

### My recommendation

**Pick 400 BAD_REQUEST** and align Upload to match.

Rationale:
1. `X-User-Id` is a dev-stub header. Real production will replace it with a JWT/OIDC token whose missing/invalid state is a genuine 401. Pre-emptively using 401 on the stub makes it harder to distinguish "header missing" (client bug) from "token expired" (user action) when real auth lands.
2. Four vs. one — the 400 camp already has the majority. Flipping one enum is less risky than flipping three.
3. Test + verification rework is smaller: Upload has ~4 existing tests referencing the 401; the other three families have ~12+ tests each referencing the 400.
4. Keeps the wire contract uniform for clients — they only key on the `errorCode` string today, but a uniform status simplifies future request-envelope middleware (e.g., a universal 401 → re-login handler).

## 4. Proposed changes if D-3 accepted

### Code edits (~15 LOC total)
1. `src/backend/src/main/java/com/authenticself/service/UploadErrorCode.java:13`
   ```diff
   - MISSING_USER_HEADER (HttpStatus.UNAUTHORIZED, "사용자 인증 정보가 없습니다. 앱을 다시 시작해주세요."),
   + MISSING_USER_HEADER (HttpStatus.BAD_REQUEST,  "사용자 인증 정보가 없습니다."),
   ```
2. Update `UploadExceptionAdvice` Javadoc if it pins the 401 semantic
3. Update the RN client's `errorMessages.ts` to drop the "앱을 다시 시작해주세요" suffix for `MISSING_USER_HEADER` (copy source of truth stays in Korean copy table)

### Test updates
4. `src/backend/src/test/java/com/authenticself/controller/PhotoUploadControllerTest` — flip assertions from `status().isUnauthorized()` to `status().isBadRequest()` (grep pattern: `isUnauthorized.*MISSING_USER_HEADER`)
5. `src/mobile/__tests__/UploadScreen.test.tsx` — any error-copy assertion referencing "앱을 다시 시작해주세요"

### Spec/AC updates
6. `artifacts/UC-01-photo-upload/spec.md` — FR text mentioning 401 for missing header → 400
7. `artifacts/UC-01-photo-upload/acceptance_criteria.json` — AC-8 (`"response is HTTP 401 with JSON body errorCode='MISSING_USER_HEADER'"`) → 400

These last two artifact edits are normally forbidden by the 4-agent loop (iter-1 verdicts immutable). A dedicated `spec-hygiene-v2` cycle is the proper venue.

## 5. Proposed changes if D-3 rejected (keep Upload at 401)

### Code edits
1. Align Space/Wishlist/Admin to 401 — invert the reasoning above, ~3 files × 1 LOC each.

### Test updates (larger)
2. Every controller test, service test, and exception-advice test across 3 packages (Space, Wishlist, Admin) + their RN counterparts gets the 400 → 401 flip. Estimated 20–30 test methods.

### Spec/AC updates
3. Artifact edits in 5 spec files + 5 AC files.

This is the more expensive path and delays closure of the drift.

## 6. Decision capture

```
☐ Option 400 accepted — Upload flips to 400, spec-hygiene-v2 cleans up artifacts
☐ Option 401 accepted — Space/Wishlist/Admin flip to 401, spec-hygiene-v2 applies artifact changes
☐ Decision deferred — spec-owner requests more information on ________________

Signed by: __________________________  Date: __________
```

## 7. Dependencies

- spec-hygiene-v2 task (D-3 is its first queued item)
- 4-agent iteration loop reopened (or a manual override on spec/AC immutability for this specific drift)
- Client release window (RN `errorMessages.ts` is bundled with the mobile app; any copy change requires an app update)

## 8. Reference

- iter-1 UC-01-recommendation verification §Warnings: "W-19 `MISSING_USER_HEADER @ 400` vs spec FR-19 `UNKNOWN_USER @ 401`"
- Follow-up (3) harvest table row W-19
- UC-02-wishlist design §D-5 (the enum-duplication convention)
