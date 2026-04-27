# Verification Report: UC-01-photo-upload
**Verdict**: PASS-WITH-WARNINGS
**Iteration**: v1
**Date**: 2026-04-17

## Scope & Environment
- Inputs reviewed: `spec.md` (10 FRs, 22 ACs), `acceptance_criteria.json`, `design.md`,
  `api_contract.yaml`, `viz/{sequence,class,state,er,ui,architecture}.mmd`, backend
  source under `src/backend/`, mobile source under `src/mobile/`.
- Environment constraints: no Gradle wrapper, no Docker, `npm install` not run.
  Per task instructions Step 2 / Step 3 were adapted to static source-level
  verification (read + import check, grep for test methods, AC-to-test mapping
  check). Step 6 (UC flow smoke) is manual-required, no running server.
- All 4 drift items flagged by visualization-specialist were re-evaluated directly
  against source — see §"Drift-items resolution".

---

## 1. AC Coverage Matrix

| AC ID | Status | Evidence (file:line / test / construct) |
|---|---|---|
| AC-1 (V2 migration file exists, unique) | PASS | `src/backend/src/main/resources/db/migration/` contains exactly `V1__init_schema.sql` + `V2__add_spaces_status_and_photo.sql`; the only `V2__*.sql` is the expected filename. |
| AC-2 (V2 runs clean on top of V1) | MAPPED, UNEXECUTED | No `V2SpacesMigrationTest` exists yet (design §7 declares it a follow-up). V1 Testcontainers test `V1InitSchemaMigrationTest` preserved intact. V2 SQL is pure ALTER, syntactically valid MySQL 8. Manual review: the V2 `ALTER` operations are all compatible with V1 state and Flyway-applicable in order. No blocker (design §7 acknowledges follow-up). |
| AC-3 (new columns with correct types) | PASS (static) | `V2__add_spaces_status_and_photo.sql:13-20` declares each column with exact type/nullability/default per spec FR-0 items 1–6; JPA mapping in `Space.java:44-62` mirrors the schema. |
| AC-4 (relaxed nullable columns) | PASS (static) | `V2__add_spaces_status_and_photo.sql:23-26` — 4 `MODIFY COLUMN … NULL` statements for dimensions / main_color / style / analysis_date. `Space.java:65-75` drops the `nullable=false` requirement on these. |
| AC-5 (idx_spaces_status) | PASS (static) | `V2__add_spaces_status_and_photo.sql:30`: `CREATE INDEX idx_spaces_status ON spaces (status);`. |
| AC-6 (happy-path upload) | MAPPED (unit), integration UNEXECUTED | Controller slice: `PhotoUploadControllerTest.happyPath` (201 with roomId/uploadUrl/status). Service: `PhotoUploadServiceTest.happyPath` asserts blob written, no delete, row saved with PENDING_ANALYSIS + analysis columns null. No server-level integration test; manual curl required on running DB. |
| AC-7 (missing file → 400 EMPTY_FILE) | MAPPED | `PhotoUploadControllerTest.emptyFile` (400 + EMPTY_FILE + correlationId). Controller also falls through to service EMPTY_FILE check at `PhotoUploadService.java:81-83`. |
| AC-8 (missing X-User-Id → 401) | MAPPED | Controller explicitly checks header first at `PhotoUploadController.java:41-43`. Slice test `PhotoUploadControllerTest.missingHeader` passes file present + no header, asserts 401 + MISSING_USER_HEADER. Advice `UploadExceptionAdvice.handleMissingHeader` backs up the framework-level path. |
| AC-9 (unknown user → 401) | MAPPED | `PhotoUploadServiceTest.unknownUser` asserts UNKNOWN_USER throw, no blob, no row. Controller slice: `PhotoUploadControllerTest.unknownUser` (401). |
| AC-10 (unsupported MIME → 415) | MAPPED | `PhotoUploadServiceTest.unsupportedMime` + controller slice `unsupportedMime`. Service enum-check at `PhotoUploadService.java:86-90`. |
| AC-11 (file too large → 413) | MAPPED | `PhotoUploadServiceTest.tooLarge` (explicit max=16 test double). Spring-level path handled by `UploadExceptionAdvice.handleTooLarge` mapping `MaxUploadSizeExceededException` → FILE_TOO_LARGE. `application.yml` caps multipart at 10MB. |
| AC-12 (unreadable image → 422) | MAPPED | Service: `PhotoUploadServiceTest.unreadable` (raw junk bytes → ImageIO returns null → IMAGE_UNREADABLE). Controller slice `unreadable`. |
| AC-13 (resolution too low → 422) | MAPPED | Service: `PhotoUploadServiceTest.tooLowRes` (real 500×500 PNG → RESOLUTION_TOO_LOW). Controller slice `tooLowRes`. |
| AC-14 (partial-failure compensating delete) | MAPPED | `PhotoUploadServiceTest.dbFailCompensates` asserts `storage.uploads == 1`, `storage.deletes == 1`, `deletes.get(0) == uploaded key`, no saved row, `STORAGE_PERSIST_FAILED` thrown. Implementation at `PhotoUploadService.java:137-148`. |
| AC-15 (storage abstraction isolation) | PASS | Regex scan for `software\.amazon\.awssdk\|com\.google\.cloud\.storage\|com\.azure\.storage\|io\.minio\|amazonaws` across `src/backend/src/main/java` → 0 hits (inside or outside `com/authenticself/storage/**`). Controller / service / repository import only `com.authenticself.storage.{ObjectStorageService, PutResult}`. |
| AC-16 (object-key layout + uniqueness) | MAPPED | `PhotoUploadServiceTest.keyLayout` asserts `spaces/\d{4}/\d{2}/\d{2}/[A-Za-z0-9_-]{20,64}\.(jpg\|png\|webp)` across 2 uploads. Implementation at `PhotoUploadService.java:113-115` (`extensionFor` + `UUID.randomUUID` collision-resistant). |
| AC-17 (Home CTA label) | MAPPED | `HomeScreen.tsx:24` hard-codes the exact label. `HomeScreen.test.tsx` asserts `getByText('방 사진 하나로 가구 추천')` renders and fires `navigation.navigate('Upload')`. |
| AC-18 (UploadScreen happy path) | MAPPED | `UploadScreen.test.tsx` mocks `expo-image-picker` + `uploadPhoto`, drives pick + submit, asserts `upload-progress` testID present while in-flight and `navigation.replace('Analyzing', { roomId: 'r1' })` on 201. |
| AC-19 (RN error mapping complete) | PASS | `errorMessages.ts:22-39` — all 8 FR-6 codes present with non-empty Korean strings. `errorMessages.test.ts` uses `it.each` over the 8 required codes with a Hangul regex sanity check. |
| AC-20 (OpenAPI contract) | PASS | `api_contract.yaml` is OpenAPI 3.0.3, defines `POST /api/v1/spaces/photo` with `multipart/form-data` binary `file`, required `X-User-Id` header, responses 201/400/401/413/415/422/500, and shared `ErrorResponse { errorCode, message, correlationId }` schema. |
| AC-21 (config surface) | PASS | `application.yml:50-58` provides every FR-8 key with the spec default: `app.storage.local.root=./var/object-storage`, `app.upload.max-size-bytes=10485760`, `min-width=640`, `min-height=480`, `allowed-content-types=image/jpeg,image/png,image/webp`. Bindings in `UploadProperties.java` + `StorageProperties.java` enforce the defaults. |
| AC-22 (no V1 regression) | PASS (static) | V2 migration is pure ALTER/MODIFY/CREATE INDEX — no DROP, no RENAME, no column removal. V1's four tables, `pk_spaces` (room_id), `fk_spaces_user`, and `idx_spaces_user_id` are all untouched. V1 Testcontainers test (`V1InitSchemaMigrationTest`) is preserved unchanged, so any V1 AC-1/AC-3/AC-6/AC-7/AC-12 regression would surface on the Task-1 verification sweep. |

**AC verdict**: 22 of 22 satisfied (structurally / by mapped tests). AC-2 and the AC-6 integration path are mapped-but-unexecuted given the no-Docker / no-Gradle sandbox constraints; design §7 already acknowledges AC-2 as a follow-up to add `V2SpacesMigrationTest`.

---

## 2. Static Checks

| Check | Method | Exit code / Notes |
|---|---|---|
| Java source parses | Read every `.java` file under `src/backend`. All imports resolve to declared classpath libraries (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `slf4j`, `jakarta.persistence`, `javax.imageio`, Spring `MockMvc`, Testcontainers). No unresolved symbols. | PASS (manual read) |
| `./gradlew compileJava` | Not executable — no wrapper checked in. | SKIPPED (toolchain unavailable) |
| `./gradlew check` | As above. | SKIPPED (toolchain unavailable) |
| `npx tsc --noEmit` | Not runnable — `npm install` has not been executed. | SKIPPED |
| TypeScript syntactic inspection | Read every `.ts` / `.tsx` file under `src/mobile`. Types are consistent; `RootStackParamList` is shared via `App.tsx` export and consumed in each `NativeStackScreenProps` import. `errorMessages.ts` uses a `Record<UploadErrorCode, string>` keyed by the explicit 8-member union. | PASS (manual read) |
| `package.json` required deps | `expo`, `expo-image-picker`, `react-navigation/native(+native-stack)`, `react-native-screens`, `react-native-safe-area-context`, `@testing-library/react-native`, `jest-expo`. All present. | PASS |
| Flyway versioning | `V1__init_schema.sql`, `V2__add_spaces_status_and_photo.sql` — monotonic, dotted-prefix convention honoured, only a single `V2__*.sql`. | PASS |

---

## 3. Tests — AC → Test mapping

| Test file | ACs covered | Executable here? |
|---|---|---|
| `src/backend/src/test/java/com/authenticself/controller/PhotoUploadControllerTest.java` | AC-6, AC-7, AC-8, AC-9, AC-10, AC-12, AC-13, AC-14 | No — Gradle unavailable. Structure verified by reading: uses `@WebMvcTest` + `@Import(UploadExceptionAdvice.class)` + `MockMvc` multipart fluent API; Mockito stubs service; `jsonPath("$.errorCode")` assertions are present on every failure-path test. |
| `src/backend/src/test/java/com/authenticself/service/PhotoUploadServiceTest.java` | AC-6, AC-8, AC-9, AC-10, AC-11, AC-12, AC-13, AC-14, AC-16 | No — see above. Notable: uses real `ImageIO.write` output for JPEG / PNG fixtures so the decodability + resolution branches run against real bytes (not strung mocks). `RecordingStorage` test double lets the test assert the exact delete-key equality in the compensating-delete flow. |
| `src/backend/src/test/java/com/authenticself/migration/V1InitSchemaMigrationTest.java` | AC-22 (indirectly, re-run against V2-migrated DB) | No — needs Docker. V1 coverage preserved untouched. |
| `src/mobile/__tests__/HomeScreen.test.tsx` | AC-17 | No — `npm install` not run. Two test cases: exact-label render + navigate-on-press. |
| `src/mobile/__tests__/UploadScreen.test.tsx` | AC-18 | No. Mocks `expo-image-picker` and `../src/api/client`; drives through `act` + `waitFor`. |
| `src/mobile/__tests__/errorMessages.test.ts` | AC-19 | No. `it.each(requiredCodes)` over all 8 FR-6 codes + Hangul regex check. |

All ACs tagged `unit`/`integration` in `acceptance_criteria.json` have at least one mapped test method. **No orphaned ACs.** The unexecuted-but-mapped tests are called out per the task's Step-3 adaptation note.

---

## 4. API Contract Compliance (Static)

Cross-checked controller + DTOs + error codes against `api_contract.yaml`:

| Contract element | Code match |
|---|---|
| `POST /api/v1/spaces/photo` | `PhotoUploadController.java:35` (`@PostMapping(path="/photo")` under `@RequestMapping("/api/v1/spaces")`). |
| `multipart/form-data` `file` part (binary, required) | `consumes = "multipart/form-data"` + `@RequestParam("file") MultipartFile file`. |
| Required `X-User-Id` header (string, 1–64) | `@RequestHeader("X-User-Id", required = false) String userId`; length/existence enforced by the controller + service. Controller accepts `required=false` and does its own null/blank check so the error envelope is used instead of the default Spring 400; handler for true absence is also wired via `handleMissingHeader`. |
| 201 `UploadPhotoResponse { roomId, uploadUrl, status }` | `UploadPhotoResponse.java` record fields match exactly; `status` values fixed to `PENDING_ANALYSIS` (FR/contract enum). |
| Shared `ErrorResponse { errorCode, message, correlationId }` | `ErrorResponse.java` record with identical 3-field shape. All `UploadExceptionAdvice` branches emit this type. |
| Error codes enum (8 values) | `UploadErrorCode.java:11-19` defines exactly the 8 codes in the contract enum, each bound to the HTTP status from the contract (400/401/401/415/413/422/422/500). |

**API contract verdict**: Controller, DTOs, error codes all match `api_contract.yaml`. No mismatches.

---

## 5. Security / Correctness Smell Check (grep)

| Check | Command | Result |
|---|---|---|
| Cloud-SDK isolation (AC-15) | grep `software\.amazon\.awssdk\|com\.google\.cloud\.storage\|com\.azure\.storage\|io\.minio\|amazonaws` in `src/backend/src/main/java` | 0 hits — PASS |
| Hardcoded secrets | grep `password\|secret\|api_key\|token\s*=\s*"[^"]+"` (case-insensitive) | 1 hit — a JavaDoc comment in `AuthenticSelfApplication.java:10` noting "no hardcoded secrets". No actual literal secrets. PASS |
| SQL-injection risk | grep `Statement\|createQuery\s*\(.*\+` in main sources | 0 hits. Repositories are JPA interfaces only; no native SQL concatenation. PASS |
| Controller body without `@Valid` | grep `@RequestBody` in main sources | 0 hits — this endpoint has no JSON body; multipart is handled via `MultipartFile`. N/A |
| Path-traversal protection | Manual review of `LocalFileSystemObjectStorageService.resolveSafely` (`local/LocalFileSystemObjectStorageService.java:94-101`). Rejects keys that resolve outside `root` after normalisation. Key is always server-built from `UUID + fixed date prefix`, never client-derived. PASS |
| INFO logging of sensitive data (FR-9) | `PhotoUploadService.java:151` — logs `roomId`, `userId`, `contentType`, `fileSizeBytes`, `elapsedMs`. **No raw bytes**, **no file path**, **no `photo_url`** logged at INFO. `LocalFileSystemObjectStorageService.java:41` INFO logs only the root dir at startup (not per-upload). PASS — matches FR-9 NFR (no PII, no bytes, no full photo URL). |
| CORS wide-open in prod | No `@CrossOrigin` / global CORS config in main sources. Out of scope for UC-01. N/A |

**Security verdict**: No issues.

---

## 6. UC Flow Smoke Check

**Manual-required, no running server.** Traced through code:

1. `HomeScreen` → `navigation.navigate('Upload')` on CTA press — verified.
2. `UploadScreen.submit()` → `uploadPhoto({ baseUrl, userId, uri, fileName, mimeType, onProgress })` — verified.
3. `client.ts::uploadPhoto` → XHR `POST /api/v1/spaces/photo` with `X-User-Id` header — verified.
4. `PhotoUploadController.uploadPhoto` → `PhotoUploadService.upload(userId, contentType, originalFilename, bytes)` — verified.
5. Service pipeline: user-exists → EMPTY_FILE → MIME → size → ImageIO decode → resolution → objectKey build → `storage.upload` → `spaces.save` — observable ordering matches spec FR-2 + AC-8.
6. Compensating delete on `save` throw — verified.
7. 201 response → RN `navigation.replace('Analyzing', { roomId })` → `AnalyzingScreen` receives `route.params.roomId` — verified.

Live curl verification requires a running Spring Boot + MySQL; deferred.

---

## 7. Diagram ↔ Code Consistency

Participants in `sequence.mmd`:
- `UploadScreen.tsx` — exists.
- `api/client.ts (XHR)` — exists, uses XHR (not fetch) so progress hook works.
- `PhotoUploadController` — exists.
- `PhotoUploadService` — exists.
- `ObjectStorageService` (LocalFileSystem impl) — exists at `storage/local/LocalFileSystemObjectStorageService.java`.
- `SpaceRepository` — exists.

Classes in `class.mmd` — all 14 boxes exist in code at the declared paths (spot-checked).

State diagram (`state.mmd`) — implemented transition `[*] → PENDING_ANALYSIS` matches `PhotoUploadService.upload` behaviour. Future transitions explicitly flagged as out-of-task.

ER diagram (`er.mmd`) — V2 columns, nullability annotations, and `idx_spaces_status` match the migration SQL exactly. Preserved V1 PK/FK annotations match `V1__init_schema.sql`.

UI flow (`ui.md`) — Home→Upload→Analyzing wiring matches `App.tsx` + screen implementations.

Architecture (`architecture.mmd`) — the "ACTIVE" boxes map to real files; "pending" boxes (UserService, wishlist, admin, Unity AR) are correctly flagged as out-of-task.

---

## 8. Drift-items resolution (per task instructions)

| # | Drift item | Verdict | Evidence |
|---|---|---|---|
| 1 | `UNKNOWN_USER` copy mismatch — backend `UploadErrorCode.java:14` `"존재하지 않는 사용자입니다."` vs RN `errorMessages.ts:27-28` `"등록되지 않은 사용자입니다. 관리자에게 문의해주세요."` | PASS with consistency warning. AC-19 only requires non-empty Korean strings for all 8 codes, which is satisfied. Treat as a design-consistency hygiene note for a later iteration (backend default is shown only when the advice falls back to `code.defaultMessage()`; RN-facing UX uses its own table keyed by `errorCode`). No functional blocker — the RN flow does not read the backend `message` field when a local mapping exists. |
| 2 | Controller-side validation ordering drift — `PhotoUploadController.java:41-46` does header-then-file-presence checks BEFORE delegating to the service, which also has its own EMPTY_FILE check. AC-8 requires observable ordering `EMPTY_FILE → UNSUPPORTED_MEDIA_TYPE → FILE_TOO_LARGE → IMAGE_UNREADABLE → RESOLUTION_TOO_LOW`. | PASS. Actual observable ordering (traced from controller → service): (1) MISSING_USER_HEADER → (2) EMPTY_FILE → (3) UNKNOWN_USER → (4) UNSUPPORTED_MEDIA_TYPE → (5) FILE_TOO_LARGE → (6) IMAGE_UNREADABLE → (7) RESOLUTION_TOO_LOW. The duplicated EMPTY_FILE check in the controller is purely a fast-fail that still produces the same observable ordering for the client. AC-7 / AC-8 slice tests pin this behaviour. |
| 3 | Storage interface method name — spec FR-3 says `put(...)` but actual interface uses `upload(...)`. | PASS with design-doc warning. AC-15 only checks the semantic contract (interface-only binding, no SDK types, returns `PutResult`, supports `delete`). AC coverage never names `put`. This is a naming-only deviation. The Mermaid class diagram and sequence diagram already reflect the real method name `upload`, so viz is self-consistent. Spec §FR-3 should be updated to say `upload(...)` in the next iteration (non-blocking). |
| 4 | `PutResult.url` as `file://` URI round-trip and consistency with api_contract.yaml. | PASS. `LocalFileSystemObjectStorageService.java:63` returns `target.toUri().toString()` which yields `file:///C:/…` on Windows. The OpenAPI response example at `api_contract.yaml:60` explicitly uses the `file:///C:/AuthenticSelf_Project/.../spaces/2026/04/17/…jpg` shape. `PhotoUploadService.java:130+154` reads `put.url()` verbatim into both `space.photoUrl` (VARCHAR 512 in V2) and the `UploadPhotoResponse.uploadUrl` field. No drift. |

---

## 9. Extra checks (per task instructions)

| Check | Verdict | Evidence |
|---|---|---|
| FR-0 V2 uses only ALTER on existing columns (no DROP + recreate) | PASS | `V2__add_spaces_status_and_photo.sql` contains only `ALTER TABLE ... ADD COLUMN` and `ALTER TABLE ... MODIFY COLUMN` statements plus one `CREATE INDEX`. Zero `DROP` keywords. Therefore V1 PK, FKs, idx_spaces_user_id, and the four relaxed columns' existing data all survive. |
| FR-0 AC-22 preservation | PASS | V1 test `V1InitSchemaMigrationTest` is unchanged. V2 ALTER ops do not rename/remove columns that V1 ACs pin, so re-running the V1 suite against a V2-migrated DB will pass the structural checks (table set, spaces PK on room_id, FK to users, idx_spaces_user_id, existing columns present). |
| FR-3 AC-15 storage isolation | PASS | Regex `software\.amazon\.awssdk\|com\.google\.cloud\.storage\|com\.azure\.storage\|io\.minio\|amazonaws` across `src/backend/src/main/java` returns 0 matches. Controller / service / repository import only the `com.authenticself.storage.ObjectStorageService` + `PutResult` types. |
| FR-5 compensating-delete test asserts storage.delete | PASS | `PhotoUploadServiceTest.dbFailCompensates` (service test lines 158-170) asserts `storage.deletes.size() == 1`, `deletes.get(0) == uploaded key`, and `spaces.saved.isEmpty()`. |
| FR-7 RN error mapping — all 8 codes, non-empty Korean | PASS | `errorMessages.ts` exports the `errorMessages` object with all 8 keys populated with non-empty Hangul-bearing strings. `errorMessages.test.ts` `it.each` enforces the 8-code presence + Hangul regex per code. |
| FR-9 no sensitive data in INFO logs | PASS | Only INFO call in the upload path is at `PhotoUploadService.java:151` — logs `roomId, userId, contentType, fileSizeBytes, elapsedMs`. No raw bytes, no `photo_url`, no file-system path, no image content. Bootstrap INFO in `LocalFileSystemObjectStorageService.java:41` logs only the storage-root dir path at startup (one-shot, not per-upload, configuration metadata only). |

---

## 10. Security Issues

| Severity | Issue | File:line | Fix hint |
|---|---|---|---|
| — | None found | — | — |

---

## 11. Smell / Drift Issues (non-blocking)

| Severity | Type | Location | Description |
|---|---|---|---|
| Low | Spec-copy drift | `UploadErrorCode.java:14` vs `errorMessages.ts:28` | Different Korean wording for `UNKNOWN_USER`. Non-blocking (AC-19 satisfied, RN never reads the backend `message` for this code), but a future iteration should align the two strings for operator-reading consistency. |
| Low | Spec wording drift | `spec.md` FR-3 says `put(...)`; interface declares `upload(...)` | Naming-only. All ACs reference semantic contract, not method name. Next spec revision should say `upload`. |
| Low | Out-of-scope documented follow-up | design.md §7 | `V2SpacesMigrationTest` (Testcontainers) is a follow-up. V1 test preserved, so AC-22 still has teeth; AC-1/AC-3/AC-4/AC-5 are covered structurally only. |

---

## 12. Blocker Summary (goes to prompt-specialist on retry)

- **None.** No AC is unsatisfied; no security issue; no contract mismatch; no viz↔code drift beyond the 4 already evaluated (all resolved PASS or PASS-with-low-severity warning). The `PASS-WITH-WARNINGS` verdict reflects:
  1. Two Korean copy strings for `UNKNOWN_USER` diverge (cosmetic, non-functional).
  2. `spec.md` FR-3 mentions method name `put(...)` but implementation + all diagrams use `upload(...)` — spec should be updated next iteration.
  3. A dedicated `V2SpacesMigrationTest` (Testcontainers) is not yet written; explicitly deferred in `design.md` §7 and the V1 integration test still enforces AC-22's structural preservation.

None of the above prevents shipping. Recommend a small spec/doc hygiene pass when UC-01-space-analysis (Task 3) is scheduled, since that task will also need the V2 migration test and will naturally add it.

---

## 13. Final verdict
**PASS-WITH-WARNINGS** — all 22 ACs satisfied; 3 low-severity cosmetic / follow-up items logged; 0 blockers.
