# Verification Report: DB-schema-init
**Verdict**: PASS-WITH-WARNINGS
**Iteration**: v1
**Date**: 2026-04-17

## 0. Environment caveats
This is a fresh Windows host with **no Gradle wrapper generated**, **no Docker
daemon**, and **no MySQL instance** reachable. Per the task's environment
notes, all Step-2 / Step-3 verifications that would normally execute a build
or spin Testcontainers have been performed as **static/manual review** against
the source files. Items so verified are tagged `(static review)` below;
items that would normally be automated but could not run in this environment
are tagged `(would-be automated — tool unavailable)`.

## 1. AC Coverage Matrix

| AC ID | Status | Evidence (file:line or test name) |
|-------|--------|------------------------------------|
| AC-1  | PASS  | `V1__init_schema.sql` L11, L24, L38, L58 (4 CREATE TABLEs) + test `V1InitSchemaMigrationTest.ac1_tablesPresent` L85-99 |
| AC-2  | PASS  | `V1__init_schema.sql` L11-19 (users cols incl. PK, UNIQUE email, TS defaults); test `ac2_usersColumns` L106-112 |
| AC-3  | PASS  | `V1__init_schema.sql` L38-50 (spaces cols incl. `analysis_date DATETIME`); test `ac3_spacesColumns` L119-126 |
| AC-4  | PASS  | `V1__init_schema.sql` L24-31 (furniture 4 cols all VARCHAR NOT NULL); test `ac4_furnitureColumns` L133-138 |
| AC-5  | PASS  | `V1__init_schema.sql` L58-75 (`price INT`, `status ENUM('Active','Purchased') DEFAULT 'Active'`); test `ac5_wishlistColumns` L145-166 |
| AC-6  | PASS  | `V1__init_schema.sql` L17, L29, L45, L67 (pk_* constraints); test `ac6_primaryKeys` L173-178 |
| AC-7  | PASS  | `V1__init_schema.sql` L47-49 (`fk_spaces_user`); test `ac7_spacesFkRejectsOrphan` L185-197 checks errorCode 1452 |
| AC-8  | PASS  | `V1__init_schema.sql` L69-71 (`fk_wishlist_user`); test `ac8_wishlistFkUser` L204-218 checks errorCode 1452 |
| AC-9  | PASS  | `V1__init_schema.sql` L72-74 (`fk_wishlist_furniture`); test `ac9_wishlistFkFurniture` L225-238 checks errorCode 1452 |
| AC-10 | PASS  | `V1__init_schema.sql` L64 (native `ENUM('Active','Purchased')`); test `ac10_statusEnumEnforced` L245-267 sets STRICT sql_mode and exercises `'Pending'` reject + `'Active'`/`'Purchased'` accept |
| AC-11 | PASS  | `V1__init_schema.sql` L64 (`NOT NULL DEFAULT 'Active'`); test `ac11_defaultStatus` L274-289 |
| AC-12 | PASS  | `V1__init_schema.sql` L46 (`INDEX idx_spaces_user_id (user_id)`); test `ac12_idxSpacesUserId` L296-298 + helper `assertNonUniqueIndex` L482-496 (asserts Non_unique=1) |
| AC-13 | PASS  | `V1__init_schema.sql` L68 (`INDEX idx_wishlist_user_id (user_id)`); test `ac13_idxWishlistUserId` L305-307 |
| AC-14 | PASS  | File at `src/backend/src/main/resources/db/migration/V1__init_schema.sql` is the **only** file in that directory (verified via `ls`, 1 match: `V1__init_schema.sql`); test `ac14_flywayVersioning` L314-336 asserts `containsExactly("V1__init_schema.sql")` and `flyway_schema_history` row `(version='1', description='init schema', success=1)`. Filename matches regex `V\d+__[A-Za-z0-9_]+\.sql`. |
| AC-15 | PASS  | Inspection of `V1__init_schema.sql`: all column identifiers are lowercase snake_case (`user_id`, `created_at`, `main_color`, `analysis_date`, `furniture_id`, `wishlist_id`, etc.); no `[A-Z]` on any column. Test `ac15_snakeCase` L343-361 enforces the same regex. |
| AC-16 | PASS  | `V1__init_schema.sql` L19, L31, L50, L75 — **every** `CREATE TABLE` ends with `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`; test `ac16_collation` L368-385 checks all 4 tables |
| AC-17 | PASS (with minor note) | 4 files exist under `src/backend/src/main/java/com/authenticself/domain/`: `User.java`, `Space.java`, `Furniture.java`, `Wishlist.java`. Each carries `@Entity` + `@Table(name="<snake>")`: User.java L17-19, Space.java L17-19, Furniture.java L11-13, Wishlist.java L18-20. Tests `exactlyFourEntityFiles` L397-407 + `eachEntityHasEntityAndTableAnnotations` L409-422. Java compile step itself is (would-be automated — tool unavailable); a manual read of all four entity files shows valid Java 17 + Jakarta Persistence syntax (balanced braces, valid imports `jakarta.persistence.*`, valid annotation arguments). See Warning W1. |

**Summary**: 17 / 17 ACs mapped to both implementation evidence and at least
one `@Test` method. No AC left unimplemented or untested.

## 2. Static Checks

| Check | Exit code | Notes |
|-------|-----------|-------|
| `./gradlew compileJava` | — | (would-be automated — tool unavailable). Manual review only: all four entity files and `AuthenticSelfApplication.java` use `jakarta.persistence.*` imports consistent with Spring Boot 3.2.5 declared in `build.gradle`. No syntax errors found by inspection. |
| `./gradlew check` | — | (would-be automated — tool unavailable). |
| SQL migration naming convention | PASS | (static review) `ls src/backend/src/main/resources/db/migration/` returns exactly `V1__init_schema.sql`. Matches Flyway regex `V\d+__[A-Za-z0-9_]+\.sql`. FR-5 / AC-14 satisfied. |
| SQL table count | PASS | (static review) `grep -c 'CREATE TABLE'` on `V1__init_schema.sql` = 4 (users, furniture, spaces, wishlist). |
| SQL ENUM syntax | PASS | (static review) Line 64: `status ENUM('Active','Purchased') NOT NULL DEFAULT 'Active'` — native MySQL ENUM as required by AC-10. |
| SQL FK clauses | PASS | (static review) 3 FKs present: `fk_spaces_user` (CASCADE/CASCADE), `fk_wishlist_user` (CASCADE/CASCADE), `fk_wishlist_furniture` (RESTRICT/CASCADE) — matches FR-2, FR-4, design decision D4. |
| Index names | PASS | (static review) `idx_spaces_user_id`, `idx_wishlist_user_id`, `idx_furniture_type_style` — all present and named per spec. |
| ENGINE / CHARSET / COLLATE | PASS | (static review) All 4 `CREATE TABLE` statements terminate with `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` (lines 19, 31, 50, 75). AC-16 satisfied. |
| No `CREATE DATABASE` / `USE` | PASS | (static review) `grep -i 'create database\|^use '` on the migration file returns no hits. FR-5 satisfied. |
| `;` terminators | PASS | (static review) Every statement terminates with `;`. |
| snake_case identifiers | PASS | (static review) No uppercase letter appears in any column name; the `size` column (FR-3) is a MySQL reserved-ish word but is valid unquoted. |

## 3. Test Results

Test suite was **not executed** in this environment (no Docker, no Gradle
wrapper) — would-be automated. Test **structure** reviewed statically:

- Total test methods: 18 (`ac1_tablesPresent` … `ac16_collation` = 16, plus
  nested `Ac17EntityStubs.exactlyFourEntityFiles` and
  `Ac17EntityStubs.eachEntityHasEntityAndTableAnnotations`).
- **AC coverage**: every AC-1…AC-17 has at least one `@Test` with a matching
  `acN_` / `Ac17…` name (verified via grep in the test file).
- Skipped tests: 0.
- Notable: `ac10_statusEnumEnforced` explicitly sets
  `SET SESSION sql_mode='STRICT_ALL_TABLES,…'` before asserting ENUM
  rejection — matches AC-10's requirement that a non-enum insert fails
  at the DB level under strict mode (MySQL 8 default).
- Notable: `assertNonUniqueIndex` helper asserts `Non_unique=1` and exactly
  one row per `Key_name`, correctly covering AC-12 / AC-13's stricter form.

**Blocker**: none. Full automated test execution deferred to the first CI
run where Docker is available. Recommend running
`cd src/backend && ./gradlew clean test` once Docker is up.

## 4. API contract compliance

N/A — `api_contract.yaml` is an explicit placeholder (`paths: {}`, version
`0.0.0`) per spec (schema-only task). No controllers exist to check. **PASS**
by vacuous truth.

## 5. Security Issues

| Severity | Issue | File:line | Fix hint |
|----------|-------|-----------|----------|
| None     | No hardcoded credentials found | `application.yml` L8-10 uses `${DB_URL}` / `${DB_USER}` / `${DB_PASSWORD}` env vars only | — |
| None     | No string-concatenation SQL in Java main code (the migration test uses string-concatenated literal values for seed inserts inside Testcontainers — acceptable for a test-only, no-user-input context) | — | — |
| None     | No `@RequestBody` / controllers to validate | — | — |
| None     | No CORS wildcard config | — | — |

Grep results:
- `password|secret|api_key|token\s*=\s*"[^"]+"` → 0 non-test hits in `src/backend`.
- `jdbc:mysql://` → 1 hit: a comment-only example in `application.yml` L7 (not a live credential or literal URL). Acceptable.
- `Statement|createQuery("... + ` → 0 hits in `src/main` (only in test code).

`.gitignore` review: excludes `.env`, `.env.*` (whitelists `.env.example`),
`build/`, `.gradle/`, `*.pem`, `*.key`, `node_modules/`, `__pycache__/`,
Unity `[Ll]ibrary/` etc. Covers all required exclusions.

## 6. UC flow smoke check

**Manual-required** — this task has no user-facing UC flow. The only runtime
flow is Spring Boot startup → Flyway applies V1 → Hibernate validates stubs.
`sequence.mmd` documents exactly that, and no running server is available in
this environment for a curl/smoke run. Marked **manual-required**; the
migration test in `V1InitSchemaMigrationTest` is the authoritative
end-to-end verification and must be run once Docker is available.

## 7. Diagram ↔ code consistency (Step 7)

- `viz/er.mmd`: 4 entities (users / spaces / furniture / wishlist) — match code.
  FK relationships `users ||--o{ spaces`, `users ||--o{ wishlist`,
  `furniture ||--o{ wishlist` correctly match `V1__init_schema.sql` FK clauses.
  Column names in the ER diagram match the SQL exactly.
- `viz/class.mmd`: class names `User`, `Space`, `Furniture`, `Wishlist` match
  files under `com.authenticself.domain/`. Fields / types / `@Column` names
  match source. `Status` enum nested inside `Wishlist` matches
  `Wishlist.java` L22-25.
- `viz/sequence.mmd`: participants `AuthenticSelfApplication`, `Flyway`,
  `classpath:db/migration`, `MySQL 8`, `Hibernate`, `@Entity stubs` all
  exist in the code (entrypoint class, Flyway auto-config, migration file,
  entity classes). No drift.
- `viz/architecture.mmd`: active nodes (Spring app, Flyway, JPA stubs,
  Hibernate validate, MySQL + 5 tables) all exist; pending nodes
  (controllers/services/repos/AI/FE) are correctly styled as `:::pending`.
  No false "active" claim.

## Smell / Drift Issues

| Type | Location | Description |
|------|----------|-------------|
| Minor / drift | `Wishlist.java` L43-45 | `@Column(columnDefinition = "ENUM('Active','Purchased')")` is stub-friendly but may cause Hibernate-validate mismatches on some driver/dialect combos (Hibernate's schema validator compares `columnDefinition` as free text). Since `ddl-auto=validate` is set (application.yml L16), if validation later trips on this, the fix is to drop `columnDefinition` and rely on `@Enumerated(EnumType.STRING)` alone. Not a blocker at this scope (AC-17 tests only check `@Entity` + `@Table(name=…)` via source text). |
| Minor / env | Gradle wrapper | `design.md §7` already flags this: no `gradlew` / `gradlew.bat` / `gradle/wrapper/` are generated in the repo. CI must run `gradle wrapper --gradle-version 8.7` once, or invoke a system Gradle. Not a code defect — documented deviation. |
| Informational | `application.yml` L19 | `dialect: org.hibernate.dialect.MySQL8Dialect` is deprecated in Hibernate 6.x (Spring Boot 3.2.5 ships Hibernate 6.4). `MySQLDialect` is preferred. Non-blocking; explicit override is not required and will be auto-selected if omitted. |

## Warnings (justify PASS-WITH-WARNINGS)

- **W1** (AC-17, W1 referenced above): AC-17 requires that the module
  **compiles**. Compilation was verified by **manual code review only** in
  this environment (no Gradle/Java toolchain executed). All four entity
  source files import `jakarta.persistence.*` correctly, use annotations
  whose argument shapes are valid for that API, and declare fields with
  types present in the JDK 17 stdlib (`String`, `Integer`, `LocalDateTime`,
  plus the inner `Wishlist.Status` enum). No syntactic error was
  identifiable. A green `./gradlew compileJava` is still required before
  merge to formally close AC-17.
- **W2**: Full Testcontainers run of `V1InitSchemaMigrationTest` was not
  executed (no Docker). Test structure is correct, but the
  implementation-vs-live-MySQL behavioural checks (especially AC-7/8/9
  errorCode 1452, AC-10 ENUM rejection, AC-16 collation) are currently
  evidence-by-inspection only. A green CI run must be treated as the final
  closure gate.
- **W3** (drift): `Wishlist.java` uses `columnDefinition = "ENUM(...)"`
  which may interact oddly with `ddl-auto=validate`. Monitor on first run.

## Blocker Summary (goes to Prompt Agent on retry)

No blockers. All ACs have code + test evidence. Only follow-up items, none
requiring a re-iteration:

1. **(operational, not a code change)** Run
   `cd src/backend && ./gradlew clean test` with Docker up to formally
   close W1 + W2. If the run is green, flip this report to **PASS**.
2. **(nice-to-have, not required)** Consider dropping `columnDefinition`
   from `Wishlist.status` and `dialect: MySQL8Dialect` from
   `application.yml` to remove two deprecation / validator-mismatch
   footguns before UC-01 tasks build on this scaffold.
