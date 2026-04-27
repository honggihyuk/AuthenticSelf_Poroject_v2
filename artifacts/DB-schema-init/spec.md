# Task Spec: DB-schema-init

## 1. Goal
Establish the foundational MySQL schema for AuthenticSelf — the four core tables (`users`, `spaces`, `furniture`, `wishlist`) and their relationships — delivered as a Flyway V1 migration so every downstream use case (UC-01 photo/analysis persistence, UC-02 wishlist, UC-03 admin dashboard) has a stable, versioned data layer to build against.

## 2. Source (PRD section)
- PRD §3 "데이터베이스(DB) 설계 방향" — entity-level field list for Furniture / Space / Wishlist tables.
- PRD §7 "클래스 다이어그램" — authoritative attribute types and cardinalities:
  - `User (1) ——analyzes—— (0..*) Space`
  - `User (1) ——owns—— (0..*) Wishlist`
  - `Wishlist (0..*) ——contains—— (1) Furniture`
- PRD §4 "기술 스택" — Backend: Spring Framework + MySQL (confirms RDBMS target).
- PRD §6 UC-02 — `Status` enum values: `Active (보관 중)` / `Purchased (구매 완료)`.

## 3. Actors & Preconditions
- **Actor**: Backend developer / CI pipeline running Flyway against a MySQL 8 instance.
- **Preconditions**:
  - Spring Boot 3.x project scaffold exists at `src/backend/`.
  - MySQL 8 server is reachable via `spring.datasource.url` (empty schema `authenticself` already created).
  - Flyway dependency (`org.flywaydb:flyway-core` + `flyway-mysql`) is on the classpath.
  - No prior migration files exist under `src/main/resources/db/migration/`.

## 4. Functional Requirements

**FR-1 (users table)** — Create table `users` with columns:
- `user_id` VARCHAR(64) NOT NULL, PRIMARY KEY
- `name` VARCHAR(100) NOT NULL
- `email` VARCHAR(255) NOT NULL, UNIQUE
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

**FR-2 (spaces table)** — Create table `spaces` with columns:
- `room_id` VARCHAR(64) NOT NULL, PRIMARY KEY
- `user_id` VARCHAR(64) NOT NULL
- `dimensions` VARCHAR(100) NOT NULL — AI-estimated room size string (e.g. "3.2m x 4.1m x 2.4m")
- `main_color` VARCHAR(32) NOT NULL — extracted dominant color (hex or named)
- `style` VARCHAR(64) NOT NULL — analyzed style (Modern / Simple / Classic / …)
- `analysis_date` DATETIME NOT NULL — timestamp of AI analysis run
- FK `fk_spaces_user` on `user_id` → `users.user_id` (ON DELETE CASCADE, ON UPDATE CASCADE)
- INDEX `idx_spaces_user_id` on `(user_id)`

**FR-3 (furniture table)** — Create table `furniture` with columns:
- `furniture_id` VARCHAR(64) NOT NULL, PRIMARY KEY
- `type` VARCHAR(32) NOT NULL — category (desk / bed / chair / lighting)
- `style` VARCHAR(64) NOT NULL — recommendation-key style
- `size` VARCHAR(100) NOT NULL — physical dimensions
- INDEX `idx_furniture_type_style` on `(type, style)` — supports UC-01 recommendation cross-validation lookup

**FR-4 (wishlist table)** — Create table `wishlist` with columns:
- `wishlist_id` VARCHAR(64) NOT NULL, PRIMARY KEY
- `user_id` VARCHAR(64) NOT NULL
- `furniture_id` VARCHAR(64) NOT NULL
- `category` VARCHAR(32) NOT NULL
- `price` INT NOT NULL — matches PRD §7 `Integer price`
- `status` ENUM('Active','Purchased') NOT NULL DEFAULT 'Active'
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
- FK `fk_wishlist_user` on `user_id` → `users.user_id` (ON DELETE CASCADE, ON UPDATE CASCADE)
- FK `fk_wishlist_furniture` on `furniture_id` → `furniture.furniture_id` (ON DELETE RESTRICT, ON UPDATE CASCADE)
- INDEX `idx_wishlist_user_id` on `(user_id)`

**FR-5 (Flyway migration file)** — Ship the schema as a single Flyway migration file at:
`src/backend/src/main/resources/db/migration/V1__init_schema.sql`
- Filename matches Flyway convention `V<version>__<description>.sql`.
- File encoding UTF-8, terminates every statement with `;`.
- No `CREATE DATABASE` / `USE` statements (Spring manages the schema binding).

**FR-6 (Table / column naming)** — All table and column names MUST be `snake_case`. Mixed-case or camelCase identifiers are rejected.

**FR-7 (Storage engine & charset)** — Every `CREATE TABLE` must specify `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` to support Korean text (PRD is Korean-language domain).

**FR-8 (JPA entity scaffolding hooks — stubs only)** — Provide empty Java entity files at
`src/backend/src/main/java/com/authenticself/domain/{User,Space,Furniture,Wishlist}.java`
with `@Entity` + `@Table(name="…")` + PK field + `@Column` annotations ONLY (no business logic, no service methods, no repositories). Class names in `PascalCase`, field names in `camelCase` mapping to snake_case columns via `@Column(name=…)`.

## 5. Data Contract

### Inputs
Not applicable — this task produces schema DDL, not request/response APIs.

### Outputs
- One SQL migration file (see FR-5).
- Four JPA entity stub files (see FR-8).
- Post-migration, running `SHOW TABLES FROM authenticself;` must return exactly: `flyway_schema_history`, `furniture`, `spaces`, `users`, `wishlist` (5 rows).

### DB entities touched (per PRD §3)
- **User**: `userId`, `name`, `email` → table `users`.
- **Space**: `roomId`, `dimensions`, `mainColor`, `style`, `analysisDate` → table `spaces` (plus `user_id` FK — required because §7 shows `User 1 — 0..* Space` and PRD §3 describes Space as owned by User even though the FK column is not spelled out).
- **Furniture**: `furnitureId`, `type`, `style`, `size` → table `furniture`.
- **Wishlist**: `wishlistId`, `userId`, `furnitureId`, `category`, `price`, `status` → table `wishlist`.

### Example: row inserted into `wishlist`
```json
{
  "wishlist_id": "w_01HXY...",
  "user_id": "u_01HXY...",
  "furniture_id": "f_01HXY...",
  "category": "chair",
  "price": 129000,
  "status": "Active",
  "created_at": "2026-04-17T09:12:33Z",
  "updated_at": "2026-04-17T09:12:33Z"
}
```

## 6. Non-Functional Requirements
- **Performance**: indexes on `spaces(user_id)` and `wishlist(user_id)` must exist — admin dashboard queries (UC-03) aggregate by user and must not full-scan. Composite `furniture(type, style)` supports UC-01 recommendation lookups.
- **Integrity**: all cross-table references enforced by FK constraints (no orphan `spaces`/`wishlist` rows). Status values constrained by the native `ENUM` type — inserting any other string MUST fail at the DB level.
- **Idempotency**: Flyway versioning guarantees V1 runs exactly once; re-running the app must not recreate tables or mutate `flyway_schema_history`.
- **Character safety**: utf8mb4 charset so Korean strings in `style` / `main_color` / `category` are stored without mojibake.
- **Error handling**: if the migration fails mid-way, Flyway marks the version as failed in `flyway_schema_history` and aborts Spring Boot startup — developer must manually remediate (no silent partial schema).
- **Security**: no seed/admin credentials are inserted by this migration; `users` table stores no password column (auth scope is out of this task).

## 7. Acceptance Criteria

**AC-1** — *Given* a fresh empty MySQL 8 schema `authenticself`, *when* Spring Boot starts with Flyway enabled, *then* `information_schema.tables` contains exactly these four business tables: `users`, `spaces`, `furniture`, `wishlist` (in addition to `flyway_schema_history`).

**AC-2** — *Given* the migration has run, *when* `SHOW COLUMNS FROM users` is executed, *then* the result contains columns `user_id` (varchar, NOT NULL, PRI), `name` (varchar, NOT NULL), `email` (varchar, NOT NULL, UNI), `created_at`, `updated_at`.

**AC-3** — *Given* the migration has run, *when* `SHOW COLUMNS FROM spaces` is executed, *then* the result contains columns `room_id` (PK), `user_id`, `dimensions`, `main_color`, `style`, `analysis_date` (DATETIME) with correct NOT NULL flags.

**AC-4** — *Given* the migration has run, *when* `SHOW COLUMNS FROM furniture` is executed, *then* the result contains columns `furniture_id` (PK), `type`, `style`, `size`, all NOT NULL, all VARCHAR.

**AC-5** — *Given* the migration has run, *when* `SHOW COLUMNS FROM wishlist` is executed, *then* column `price` has type `int`, column `status` has type `enum('Active','Purchased')` with default `'Active'`, and columns `wishlist_id`, `user_id`, `furniture_id`, `category` all exist NOT NULL.

**AC-6 (PK)** — *Given* each business table, *when* querying `information_schema.table_constraints WHERE constraint_type='PRIMARY KEY'`, *then* there is exactly one PRIMARY KEY per table on the expected column (`user_id`, `room_id`, `furniture_id`, `wishlist_id` respectively).

**AC-7 (FK spaces→users)** — *Given* the migration has run, *when* attempting `INSERT INTO spaces(room_id, user_id, …) VALUES('r1','u_does_not_exist',…)`, *then* MySQL rejects the row with error 1452 (foreign key constraint fails).

**AC-8 (FK wishlist→users)** — *Given* the migration has run, *when* attempting to insert a `wishlist` row whose `user_id` does not exist in `users`, *then* MySQL rejects with FK error 1452.

**AC-9 (FK wishlist→furniture)** — *Given* the migration has run, *when* attempting to insert a `wishlist` row whose `furniture_id` does not exist in `furniture`, *then* MySQL rejects with FK error 1452.

**AC-10 (Status enum)** — *Given* an existing user `u1` and furniture `f1`, *when* executing `INSERT INTO wishlist(…, status) VALUES(…, 'Pending')`, *then* MySQL rejects with a data-truncation / invalid-enum-value error. Inserting `'Active'` and `'Purchased'` both succeed.

**AC-11 (Default status)** — *Given* an existing user/furniture pair, *when* inserting a wishlist row without specifying `status`, *then* the stored value is `'Active'`.

**AC-12 (Index on spaces.user_id)** — *Given* the migration has run, *when* querying `SHOW INDEX FROM spaces WHERE Key_name='idx_spaces_user_id'`, *then* exactly one row is returned with `Column_name='user_id'`.

**AC-13 (Index on wishlist.user_id)** — *Given* the migration has run, *when* querying `SHOW INDEX FROM wishlist WHERE Key_name='idx_wishlist_user_id'`, *then* exactly one row is returned with `Column_name='user_id'`.

**AC-14 (Flyway versioning convention)** — *Given* the repository, *when* listing `src/backend/src/main/resources/db/migration/`, *then* exactly one file matches `V1__*.sql`, named precisely `V1__init_schema.sql`. After a successful boot, `flyway_schema_history` contains one row with `version='1'`, `description='init schema'`, `success=1`.

**AC-15 (snake_case naming)** — *Given* all four business tables, *when* listing every column name in `information_schema.columns`, *then* no column name matches the regex `[A-Z]` (i.e. no uppercase letters — all snake_case).

**AC-16 (utf8mb4 charset)** — *Given* each business table, *when* querying `information_schema.tables` for `TABLE_COLLATION`, *then* every row shows `utf8mb4_unicode_ci`.

**AC-17 (JPA entity stubs)** — *Given* the project source tree, *when* checking
`src/backend/src/main/java/com/authenticself/domain/`, *then* exactly four files exist: `User.java`, `Space.java`, `Furniture.java`, `Wishlist.java`, each annotated with `@Entity` and `@Table(name="…")` pointing at the correct snake_case table, and each compiles (`./gradlew compileJava` / `mvn compile` succeeds).

## 8. Out of Scope
- Authentication, password storage, OAuth flows, JWT on the `users` table.
- Seed / reference data (no furniture catalog rows, no admin user).
- Repository interfaces (`JpaRepository`), services, controllers, DTOs.
- Business methods from the class diagram: `uploadPhoto()`, `selectStyle()`, `addToWishlist()`, `purchaseItem()`, `analyzeSpace()`, `updateStatus()`, `getDetails()` — these belong to later UC tasks.
- Sales / order / payment tables — UC-03 "Sales" metric will be a separate migration (V2+).
- Image/photo storage schema (object storage is external per §9 architecture).
- Soft-delete columns, audit/history tables.

## 9. Dependencies
- None. This is the root migration; all subsequent UC tasks (UC-01 analysis persistence, UC-02 wishlist CRUD, UC-03 dashboard aggregations) depend on this task, not vice versa.
