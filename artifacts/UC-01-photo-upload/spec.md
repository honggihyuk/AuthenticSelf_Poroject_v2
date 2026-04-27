# Task Spec: UC-01-photo-upload

## 1. Goal
Enable a user to upload a single room photo from the React Native app to the Spring backend so that the image is validated, stored in object storage, and a new `spaces` row is created in `PENDING_ANALYSIS` state — producing the `roomId` that the downstream AI space-analysis task (UC-01-space-analysis) will consume. On validation or analysis-failure conditions described in PRD §6 UC-01 alt-flow 1-a, return an explicit error code so the RN client can surface the "재업로드 요청" (please re-upload) UX.

## 2. Source (PRD section)
- **PRD §6 UC-01 기본 흐름 step 1** — 사용자가 '방 사진 하나로 가구 추천' 기능을 시작하고 방 사진을 업로드.
- **PRD §6 UC-01 대안 흐름 1-a** — 화질이 너무 낮거나 방 구조를 파악할 수 없으면 분석 실패 메시지 + 재업로드 요청. (This task covers the *upload-time* early rejection — low resolution, unreadable image, bad MIME, oversize. The *analysis-time* rejection is UC-01-space-analysis.)
- **PRD §9 시스템 아키텍처** — Object Storage (이미지 파일 저장) + Spring Backend (API Gateway & Controller + AI Orchestrator) + MySQL.
- **PRD §3 DB 설계 / §7 클래스 다이어그램** — `Space (roomId, dimensions, mainColor, style, analysisDate)` — this task creates the row and fills the identity+ownership columns; analysis columns stay NULL until UC-01-space-analysis completes.
- **PRD §4 기술 스택** — Spring Boot + MySQL backend, React Native frontend.

## 3. Actors & Preconditions
- **Primary actor**: End-user on the React Native mobile app choosing "방 사진 하나로 가구 추천" and picking a photo from camera roll / camera.
- **Secondary actor**: Spring Boot backend (UC-01 API Gateway & Controller), `ObjectStorageService` (local-FS impl in dev).
- **Preconditions**:
  - Task `DB-schema-init` is merged (`users`, `spaces` tables exist via Flyway V1).
  - Spring Boot project scaffold exists at `src/backend/` from Task 1.
  - A row exists in `users` for the uploader (real auth is out of scope — a header-based user stub `X-User-Id` identifies the caller; if missing → HTTP 401).
  - React Native project scaffold exists (or is bootstrapped as part of this task) at `src/mobile/`.
  - MySQL 8 reachable; local object-storage root directory writable by the backend process.

## 4. Functional Requirements

### Schema delta (RESOLVED — option (a) chosen)

**FR-0 (Flyway V2 schema delta)** — The Task-1 `spaces` schema does not support the "uploaded but not yet analyzed" state. This task MUST ship a new Flyway migration file:

- Path: `src/backend/src/main/resources/db/migration/V2__add_spaces_status_and_photo.sql`
- Operations (all in one migration, atomic):
  1. `ALTER TABLE spaces ADD COLUMN photo_url VARCHAR(512) NOT NULL AFTER user_id` — storage-layer URL/key returned by `ObjectStorageService.put()`.
  2. `ALTER TABLE spaces ADD COLUMN original_filename VARCHAR(255) NULL AFTER photo_url` — the client-supplied filename (diagnostics; nullable).
  3. `ALTER TABLE spaces ADD COLUMN content_type VARCHAR(64) NOT NULL AFTER original_filename` — validated MIME (e.g. `image/jpeg`).
  4. `ALTER TABLE spaces ADD COLUMN file_size_bytes BIGINT NOT NULL AFTER content_type` — validated byte length.
  5. `ALTER TABLE spaces ADD COLUMN status ENUM('PENDING_ANALYSIS','ANALYZED','FAILED') NOT NULL DEFAULT 'PENDING_ANALYSIS' AFTER file_size_bytes`.
  6. `ALTER TABLE spaces ADD COLUMN uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status`.
  7. `ALTER TABLE spaces MODIFY COLUMN dimensions    VARCHAR(100) NULL;`
  8. `ALTER TABLE spaces MODIFY COLUMN main_color    VARCHAR(32)  NULL;`
  9. `ALTER TABLE spaces MODIFY COLUMN style         VARCHAR(64)  NULL;`
  10. `ALTER TABLE spaces MODIFY COLUMN analysis_date DATETIME     NULL;` — NULL until analysis completes (overrides Task 1 NOT NULL; PRD §3 lists `analysisDate` as an analysis output).
  11. `CREATE INDEX idx_spaces_status ON spaces(status);` — supports the AI orchestrator polling `WHERE status='PENDING_ANALYSIS'`.

**Rationale for choice (a) over (b)**: the `spaces` row inherently has two lifecycle phases ("uploaded" vs "analyzed") and §6 UC-01 explicitly separates them across steps 1 and 2. Without a `status` column the AI orchestrator in §9 cannot discover new rows to process; we would need an out-of-band queue for something the DB can model natively. The four analysis columns (`dimensions`, `main_color`, `style`, `analysis_date`) MUST become nullable for the same reason.

### API

**FR-1 (Endpoint)** — Expose `POST /api/v1/spaces/photo` on the Spring Boot backend.
- Content-Type: `multipart/form-data`.
- Parts:
  - `file` (required) — binary image payload.
- Headers:
  - `X-User-Id: <user_id>` (required; header-based user stub — real auth is out of scope).
- Success response: HTTP **201 Created**, JSON body `{ "roomId": string, "uploadUrl": string, "status": "PENDING_ANALYSIS" }`.

**FR-2 (File validation — pre-storage, in-memory)** — Before writing to object storage the controller MUST validate:
- `file` is present and non-empty → else `400 EMPTY_FILE`.
- `file.contentType ∈ {"image/jpeg","image/png","image/webp"}` → else `415 UNSUPPORTED_MEDIA_TYPE`.
- `file.size ≤ 10 * 1024 * 1024` (10 MB) → else `413 FILE_TOO_LARGE`.
- Image is decodable by `javax.imageio.ImageIO.read(...)` (catches corrupt/truncated streams masquerading as images) → else `422 IMAGE_UNREADABLE`.
- Decoded resolution `width ≥ 640 AND height ≥ 480` → else `422 RESOLUTION_TOO_LOW` (this pre-empts the "화질이 너무 낮은" case of §6 UC-01 1-a at upload time).

**FR-3 (Object storage — interface-driven)** — Introduce a Java interface `ObjectStorageService` at `src/backend/src/main/java/com/authenticself/storage/ObjectStorageService.java` with at minimum:
```
PutResult put(String objectKey, byte[] bytes, String contentType);
byte[]    get(String objectKey);
void      delete(String objectKey);
boolean   exists(String objectKey);
```
where `PutResult` carries `{ objectKey, url, sizeBytes }`. Provide a `LocalFileSystemObjectStorageService` implementation under `src/backend/src/main/java/com/authenticself/storage/local/` that:
- Writes to a root directory configured by `app.storage.local.root` (default `./var/object-storage`).
- Uses object keys of the form `spaces/{yyyy}/{MM}/{dd}/{roomId}{ext}` where `ext` derives from the validated content-type.
- Returns `url` = `file://<absolute-path>` for local dev.
- **Forbidden**: no direct `software.amazon.awssdk.*` / `com.google.cloud.storage.*` imports in the controller or service layer. All call sites bind to the interface only.

**FR-4 (Database insert)** — After a successful `ObjectStorageService.put(...)` call the service MUST insert one row into `spaces` with:
- `room_id` = ULID/UUID generated server-side (application layer; 26–36 chars; `VARCHAR(64)` fits).
- `user_id` = value from `X-User-Id` header (must exist in `users` — else `401 UNKNOWN_USER`; no auto-provisioning).
- `photo_url` = `PutResult.url`.
- `original_filename` = client-supplied filename (nullable-safe).
- `content_type` = validated MIME.
- `file_size_bytes` = validated size.
- `status` = `'PENDING_ANALYSIS'`.
- `uploaded_at` = now (UTC).
- `dimensions`, `main_color`, `style`, `analysis_date` = NULL.

**FR-5 (Transactional boundary / cleanup on partial failure)** — If `ObjectStorageService.put()` succeeds but the subsequent `INSERT INTO spaces` fails, the service MUST invoke `ObjectStorageService.delete(objectKey)` to remove the orphan blob before propagating the error. The HTTP response MUST be `500 STORAGE_PERSIST_FAILED` with a correlation ID; the DB row MUST NOT exist; the blob MUST NOT exist. (Two-phase ordering: blob first, DB second, compensating delete on DB failure — acknowledged non-XA tradeoff; safer than DB-first because the blob write is the only non-repeatable side-effect.)

**FR-6 (Error-code contract — machine-readable)** — Every non-2xx response body is JSON:
```json
{ "errorCode": "RESOLUTION_TOO_LOW", "message": "…human-readable…", "correlationId": "…" }
```
Error codes introduced by this task, each bound to an HTTP status:
| errorCode | HTTP | Trigger |
|---|---|---|
| `EMPTY_FILE` | 400 | Missing/empty multipart part |
| `MISSING_USER_HEADER` | 401 | `X-User-Id` absent |
| `UNKNOWN_USER` | 401 | `X-User-Id` not in `users` |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-type not in allow-list |
| `FILE_TOO_LARGE` | 413 | > 10 MB |
| `IMAGE_UNREADABLE` | 422 | `ImageIO.read` returns null / throws |
| `RESOLUTION_TOO_LOW` | 422 | width < 640 or height < 480 |
| `STORAGE_PERSIST_FAILED` | 500 | Object storage or DB failure after validation |

**FR-7 (React Native client screen)** — Deliver two screens in the RN app:
- `HomeScreen` with a primary CTA button labeled `방 사진 하나로 가구 추천` (verbatim PRD §6 wording) that navigates to `UploadScreen`.
- `UploadScreen` that:
  - Uses `react-native-image-picker` (or equivalent — implementation choice left to Design Agent) to let the user pick / capture a photo.
  - Shows a thumbnail preview + file size + "업로드" button.
  - On submit, POSTs `multipart/form-data` to `POST /api/v1/spaces/photo` with `X-User-Id` header (value from a dev-only `settings` module; real auth is out of scope).
  - Displays a determinate upload-progress indicator (0 – 100 %) driven by the XHR/fetch upload-progress event.
  - On 2xx: navigates to a placeholder `AnalyzingScreen` and passes `roomId` via route params (that screen's full behavior is UC-01-space-analysis — stub only here).
  - On 4xx/5xx: maps `errorCode` → Korean user-facing message and shows "다시 시도" button that returns to the picker. The mapping table MUST live in a single module `src/mobile/src/api/errorMessages.ts` (exact relative path) keyed by the `errorCode` strings from FR-6.

**FR-8 (Configuration surface)** — New Spring Boot properties (in `application.yml`), all overridable via env vars:
- `app.storage.local.root` (default `./var/object-storage`).
- `app.upload.max-size-bytes` (default `10485760`).
- `app.upload.min-width`  (default `640`).
- `app.upload.min-height` (default `480`).
- `app.upload.allowed-content-types` (default `image/jpeg,image/png,image/webp`).

**FR-9 (Observability — minimum)** — Controller MUST log one INFO line per successful upload including `roomId`, `userId`, `contentType`, `fileSizeBytes`, `elapsedMs`; one WARN line per 4xx with `errorCode`; one ERROR line per 5xx with the correlation ID and stack trace. No logging of the raw image bytes. No logging of full `photo_url` (path-only OK).

## 5. Data Contract

### Inputs
`POST /api/v1/spaces/photo` — `multipart/form-data`.

| Part / Header | Type | Required | Constraints |
|---|---|---|---|
| `file` | binary | yes | MIME ∈ {jpeg, png, webp}; ≤ 10 MB; decodable; ≥ 640 × 480 |
| `X-User-Id` (header) | string | yes | must exist in `users.user_id` |

### Outputs — success (201)
```json
{
  "roomId": "01HXYB2K9VQWM4P3ZT6N5C8DAE",
  "uploadUrl": "file:///C:/AuthenticSelf_Project/AuthenticSelf_v3/var/object-storage/spaces/2026/04/17/01HXYB2K9VQWM4P3ZT6N5C8DAE.jpg",
  "status": "PENDING_ANALYSIS"
}
```

### Outputs — error (4xx/5xx)
```json
{
  "errorCode": "RESOLUTION_TOO_LOW",
  "message": "업로드한 사진의 해상도가 너무 낮습니다. 640x480 이상 이미지를 올려주세요.",
  "correlationId": "9b1e4c12-3e61-4b4f-9c54-a7b7a1dbe0f7"
}
```

### DB entities touched
- `spaces` — one INSERT per successful request. Columns set: `room_id`, `user_id`, `photo_url`, `original_filename`, `content_type`, `file_size_bytes`, `status='PENDING_ANALYSIS'`, `uploaded_at`. Columns left NULL: `dimensions`, `main_color`, `style`, `analysis_date` (filled in UC-01-space-analysis).
- `users` — SELECT-only (existence check for `X-User-Id`).

### Object storage layout
- Key pattern: `spaces/{yyyy}/{MM}/{dd}/{roomId}.{jpg|png|webp}`
- Dev root: `./var/object-storage` (gitignored).

## 6. Non-Functional Requirements

- **Performance**: P95 upload-handling latency (validation + storage write + DB insert) ≤ 1500 ms for a 5 MB image on a local dev machine. Backend must stream-validate, not buffer the whole file twice.
- **Concurrency**: endpoint safe under concurrent uploads from the same user — `room_id` generation is application-layer and collision-resistant; DB PK uniqueness is the last-line defense.
- **Security**:
  - No path traversal: object key built from `roomId` and a fixed prefix; never from the client-supplied `originalFilename`.
  - Content-type sniffing: trust the `ImageIO.read` probe over the client-declared MIME when mismatched (reject mismatch as `UNSUPPORTED_MEDIA_TYPE`).
  - Max in-memory part size enforced by `spring.servlet.multipart.max-file-size=10MB` and `max-request-size=11MB`.
  - No PII in logs; `correlationId` uses UUIDv4.
- **Error handling**: every checked failure path returns the FR-6 structured envelope. Uncaught exceptions go through a single `@ControllerAdvice` that assigns `STORAGE_PERSIST_FAILED` + correlation ID.
- **Idempotency / retry behavior**: not idempotent (each successful POST creates a new `spaces` row). The RN client MUST NOT retry a 2xx silently. A 5xx is retryable by the user via the UX button — server-side retry logic is out of scope.
- **Storage abstraction**: no SDK-specific types (S3, GCS) may appear outside `com.authenticself.storage.<provider>` subpackages. Controller, service, and repository layers import only the `ObjectStorageService` interface.
- **Cleanup on crash**: if the JVM crashes between `put()` and `INSERT`, an orphan blob may remain. Out of scope to reconcile in this task; noted for future "storage GC" task.
- **i18n**: user-facing messages in Korean (PRD is Korean-language). Error-code strings stay English/UPPER_SNAKE for machine matching.

## 7. Acceptance Criteria

**AC-1 (V2 migration file exists)** — *Given* the repo, *when* listing `src/backend/src/main/resources/db/migration/`, *then* a file named exactly `V2__add_spaces_status_and_photo.sql` exists and is the only `V2__*.sql`.

**AC-2 (V2 runs clean on top of V1)** — *Given* a fresh MySQL 8 schema with V1 applied, *when* Spring Boot starts with Flyway enabled, *then* `flyway_schema_history` contains two rows (`version=1`, `version=2`), both `success=1`.

**AC-3 (spaces new columns)** — *Given* V2 has run, *when* `SHOW COLUMNS FROM spaces` is executed, *then* the result contains `photo_url VARCHAR(512) NOT NULL`, `original_filename VARCHAR(255) NULL`, `content_type VARCHAR(64) NOT NULL`, `file_size_bytes BIGINT NOT NULL`, `status ENUM('PENDING_ANALYSIS','ANALYZED','FAILED') NOT NULL DEFAULT 'PENDING_ANALYSIS'`, `uploaded_at DATETIME NOT NULL`.

**AC-4 (spaces relaxed columns)** — *Given* V2 has run, *when* `SHOW COLUMNS FROM spaces` is executed, *then* `dimensions`, `main_color`, `style`, `analysis_date` all show `Null=YES`.

**AC-5 (idx_spaces_status)** — *Given* V2 has run, *when* `SHOW INDEX FROM spaces WHERE Key_name='idx_spaces_status'` is executed, *then* exactly one row returns with `Column_name='status'`.

**AC-6 (happy-path upload)** — *Given* a user `u1` exists in `users` and a 2 MB 1920×1080 JPEG, *when* the RN client (or an equivalent test) sends `POST /api/v1/spaces/photo` with `X-User-Id: u1` and the file part, *then* the response is HTTP 201, body contains `roomId` (non-empty ≤64 chars), `uploadUrl` (non-empty), `status:"PENDING_ANALYSIS"`; a file exists at the object-storage path; one row exists in `spaces` with matching `room_id`, `user_id='u1'`, `status='PENDING_ANALYSIS'`, `dimensions IS NULL`, `analysis_date IS NULL`.

**AC-7 (missing file)** — *Given* the endpoint, *when* POSTing with no `file` part, *then* response is HTTP 400 and body `errorCode='EMPTY_FILE'`.

**AC-8 (missing user header)** — *Given* the endpoint, *when* POSTing a valid file but no `X-User-Id` header, *then* response is HTTP 401 and body `errorCode='MISSING_USER_HEADER'`.

**AC-9 (unknown user)** — *Given* no row `user_id='ghost'` in `users`, *when* POSTing with header `X-User-Id: ghost`, *then* response is HTTP 401 and body `errorCode='UNKNOWN_USER'`; no `spaces` row inserted; no blob written.

**AC-10 (unsupported MIME)** — *Given* a 500 KB file whose content-type is `image/gif`, *when* POSTed, *then* response is HTTP 415 and body `errorCode='UNSUPPORTED_MEDIA_TYPE'`.

**AC-11 (file too large)** — *Given* a 12 MB JPEG, *when* POSTed, *then* response is HTTP 413 and body `errorCode='FILE_TOO_LARGE'`; no blob written; no DB row.

**AC-12 (unreadable image)** — *Given* a file with `.jpg` extension and `image/jpeg` MIME whose bytes are random garbage, *when* POSTed, *then* response is HTTP 422 and body `errorCode='IMAGE_UNREADABLE'`.

**AC-13 (resolution too low)** — *Given* a 500×500 PNG, *when* POSTed, *then* response is HTTP 422 and body `errorCode='RESOLUTION_TOO_LOW'`.

**AC-14 (partial-failure cleanup)** — *Given* a test double where `ObjectStorageService.put()` succeeds but the `spaces` INSERT throws, *when* the endpoint is invoked with a valid file, *then* (i) response is HTTP 500 with `errorCode='STORAGE_PERSIST_FAILED'`, (ii) `ObjectStorageService.delete(objectKey)` was called exactly once, (iii) `spaces` table has no new row.

**AC-15 (storage abstraction)** — *Given* the backend source tree, *when* searching `src/backend/src/main/java/**/*.java` outside `com/authenticself/storage/**`, *then* there are zero matches for the regex `(software\.amazon\.awssdk|com\.google\.cloud\.storage|com\.azure\.storage)`. Controller / service / repository layers import only `com.authenticself.storage.ObjectStorageService`.

**AC-16 (key layout)** — *Given* two consecutive successful uploads from the same user on the same day, *when* inspecting the object-storage root, *then* the keys match the regex `spaces/\d{4}/\d{2}/\d{2}/[A-Za-z0-9_-]{20,64}\.(jpg|png|webp)` and are distinct (collision-safe).

**AC-17 (RN HomeScreen CTA label)** — *Given* the RN app, *when* rendering `HomeScreen`, *then* a button with exact visible label `방 사진 하나로 가구 추천` exists and navigates to `UploadScreen` on press.

**AC-18 (RN UploadScreen happy path — component test)** — *Given* a mocked upload API returning 201 `{roomId:"r1", ...}`, *when* the user picks a valid image and taps "업로드", *then* (i) a progress indicator is rendered while the request is in flight, (ii) navigation to `AnalyzingScreen` occurs with route param `roomId='r1'`.

**AC-19 (RN error mapping)** — *Given* the module `src/mobile/src/api/errorMessages.ts`, *when* imported, *then* it exports a mapping object whose keys include each of the eight `errorCode` values in FR-6 and whose values are non-empty Korean strings.

**AC-20 (OpenAPI contract)** — *Given* the artifact `artifacts/UC-01-photo-upload/api_contract.yaml`, *when* parsed as OpenAPI 3.0.x, *then* it defines `POST /api/v1/spaces/photo` with `multipart/form-data` request body containing a binary `file` part, a required `X-User-Id` header, responses 201 / 400 / 401 / 413 / 415 / 422 / 500, and references a shared `ErrorResponse` schema with fields `errorCode`, `message`, `correlationId`.

**AC-21 (config surface)** — *Given* `application.yml`, *when* parsed, *then* keys `app.storage.local.root`, `app.upload.max-size-bytes`, `app.upload.min-width`, `app.upload.min-height`, `app.upload.allowed-content-types` are all present with the defaults in FR-8.

**AC-22 (no existing-schema regression)** — *Given* V2 has run, *when* re-running the Task-1 verification suite against `spaces`, *then* AC-1/AC-3/AC-6/AC-7/AC-12 from `DB-schema-init` still pass (table exists, PK on `room_id`, FK to `users` still enforced, `idx_spaces_user_id` still present, all existing Task-1 columns still exist).

## 8. Out of Scope
- Running the actual AI space analysis (dimension / color / style extraction) — that is **UC-01-space-analysis** (Task 3). This task only creates the row and leaves analysis columns NULL.
- Style selection UI — **UC-01-style-selection** (Task 4).
- Furniture recommendation logic / cross-validation — **UC-01-recommendation** (Task 5).
- Wishlist CRUD (Task 6), admin dashboard (Task 7), AR placement (Task 8).
- Real authentication / JWT / OAuth — only a header-based `X-User-Id` stub is in scope.
- User registration / sign-up flow — pre-existing user row is a precondition.
- S3 / GCS / Azure object-storage implementations — only the interface + local-FS impl. Real cloud impls are a future task.
- CDN-signed retrieval URLs — `uploadUrl` in the response is just the storage reference; playback of the image is not tested.
- Virus scanning / NSFW moderation — not mentioned in PRD.
- Image re-encoding / thumbnail generation / EXIF stripping — none unless a later task introduces them.
- Concurrent multi-file upload (batch) — endpoint accepts exactly one `file` part.
- Orphan-blob reconciliation after JVM crash — noted for a future "storage GC" task.
- Internationalization infrastructure for the RN error mapping — Korean strings hardcoded is acceptable for this task.

## 9. Dependencies
- **Hard**: `DB-schema-init` (Task 1) — this task's V2 migration patches the Task-1 `spaces` table.
- **Consumed by**: `UC-01-space-analysis` (Task 3) reads `spaces WHERE status='PENDING_ANALYSIS'` and writes back `dimensions/main_color/style/analysis_date` + flips `status='ANALYZED'` or `'FAILED'`. That consumer contract must not be broken by this spec — confirmed by the schema delta in FR-0.
- **No dependency on**: Tasks 4–8.
