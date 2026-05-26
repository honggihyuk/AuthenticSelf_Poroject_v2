# Task Spec: UC-SECURE-AUTH

## 1. Goal
Replace three pre-production weak spots with a production-grade auth and CORS posture, without altering the frozen mobile login contract. Specifically: (a) swap the demo `AuthService` base64 stub token for a real HS256 JWT plus BCrypt-hashed passwords in `users.password_hash`; (b) remove the wildcard `@CrossOrigin(origins="*")` from `SpaceController:42`, `WishlistController:42`, `AdminController:39` and replace with a single env-driven `CorsConfig`; (c) externalize every sensitive `application.yml` key (DB credentials, AI URL, storage root, JWT secret, CORS whitelist) to environment variables, documented in a new `docs/deployment/env.md`. The RN `AuthContext.tsx` requires zero source changes for the happy path; the `token` field's bytes change from base64 stub to a real JWT, but the response envelope `{token, userId, role, name}` is preserved verbatim.

## 2. Scope
- **In scope**:
  - New Flyway migration **`V11__add_users_password_hash.sql`** (V8–V10 are already taken; see §5 D-3).
  - BCrypt encoder bean (`BCryptPasswordEncoder`, strength 10) + `DemoUserBootstrap` upsert of `1234`'s hash for `admin` and `user` (idempotent — only sets when column is null/empty).
  - `JwtService` (issue + verify HS256 JWT, secret from `APP_AUTH_JWT_SECRET`, TTL 7 days, claims `{sub, role, name, iat, exp}`).
  - `AuthService` rewrite: BCrypt verify + JWT issuance. Frontend response envelope unchanged.
  - New `JwtAuthFilter` (servlet filter) that validates `Authorization: Bearer <jwt>` on every `/api/v1/**` request other than `/api/v1/auth/login`, and asserts that `X-User-Id` matches the JWT `sub` claim (D-1 decision below).
  - New `CorsConfig implements WebMvcConfigurer` bean; removal of all three `@CrossOrigin(origins="*")` annotations (including the one on `AuthController:22` for hygiene).
  - `application.yml` retrofit: every sensitive key uses `${VAR:default}` syntax; JWT secret has **no default** (fail-fast on startup if unset or < 32 bytes).
  - `docs/deployment/env.md` — single source of truth for the required env catalog.
  - New `AuthErrorCode.INVALID_TOKEN` (401) + `USER_ID_MISMATCH` (401) error-code constants.
  - Backend unit + integration tests (MockMvc) for JWT happy path, expired token, bad signature, malformed token, CORS allow + deny, mismatched `X-User-Id` vs `sub`, startup-failure on missing JWT secret.
  - Mobile test mock for the new `Authorization: Bearer` header on existing API calls IF the existing tests already assert on outgoing headers (FR-BC-2).
- **Out of scope** (see §6 for full list): refresh tokens; replacing `X-User-Id` with JWT-derived identity in controllers (only the filter cross-validates them); password-rotation flow; account self-service signup; OAuth/social login; secret rotation tooling; HTTP-only cookie session; CSRF token middleware.

## 3. Functional Requirements

### Backend — JWT issuance & verification

**FR-1 (JWT library)** — Add `io.jsonwebtoken:jjwt-api:0.12.6`, `io.jsonwebtoken:jjwt-impl:0.12.6` (runtime), `io.jsonwebtoken:jjwt-jackson:0.12.6` (runtime) to `src/backend/build.gradle`. No other runtime dependency may be added by this task.

**FR-2 (`JwtService` — issue)** — New `com.authenticself.auth.JwtService` Spring `@Service` with `String issue(String userId, String role, String name)`:
- Algorithm: **HS256**. Secret: `APP_AUTH_JWT_SECRET` env var (min 32 bytes / 256 bits — enforced at bean construction; fail with `IllegalStateException("APP_AUTH_JWT_SECRET must be set and >= 32 bytes; see docs/deployment/env.md")` if missing or shorter).
- Claims set: `sub=userId`, `role=<USER|ADMIN>`, `name=<user.name>`, `iat=<now>`, `exp=<now + 604800s>` (exactly 7 days).
- Header: `{alg: HS256, typ: JWT}`.
- Returns compact-serialized JWT string.

**FR-3 (`JwtService` — verify)** — `JwtVerification verify(String token) throws AuthException` returning `{userId, role, name, expiresAt}`:
- Validates signature against the same secret used in FR-2.
- Validates `exp` against `Clock` (injectable — defaults to system clock at `Asia/Seoul`, consistent with `TimeConfig`).
- On any failure (`SignatureException`, `ExpiredJwtException`, `MalformedJwtException`, missing required claim) → throw `AuthException(AuthErrorCode.INVALID_TOKEN)`.
- `JwtVerification` is a Java `record { String userId, String role, String name, Instant expiresAt }`.

**FR-4 (TTL invariant)** — `JwtService.issue(...)` MUST produce a token where `exp - iat == 604800` (7 days in seconds, exact). Verified by AC-4.

**FR-5 (Statelessness)** — Two `JwtService` instances constructed with the same `APP_AUTH_JWT_SECRET` MUST mutually verify tokens issued by the other. Simulates a backend restart; AC-5 covers this.

### Backend — BCrypt password storage

**FR-6 (`PasswordEncoder` bean)** — Add a `@Configuration` class `com.authenticself.auth.PasswordEncoderConfig` exposing `@Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }`. Strength = 10 (Spring default; documented in the bean's Javadoc). Requires adding `org.springframework.security:spring-security-crypto` (transitively via `spring-security-core` is acceptable; a direct `spring-security-crypto` dependency is preferred to avoid pulling the full Spring Security filter chain we do not use). Pick exactly one approach in design; no Spring Security `SecurityFilterChain` is introduced (we manage auth via our own `JwtAuthFilter`).

**FR-7 (V11 migration — `users.password_hash`)** — Add `src/backend/src/main/resources/db/migration/V11__add_users_password_hash.sql`:
- Step 1 (within the same SQL file): `ALTER TABLE users ADD COLUMN password_hash VARCHAR(72) NULL AFTER role;`
- Step 2: Backfill the demo rows with a placeholder sentinel so step 3 can apply NOT NULL — but `DemoUserBootstrap` will overwrite this on first boot. The migration uses: `UPDATE users SET password_hash = '__BOOTSTRAP_PLACEHOLDER__' WHERE password_hash IS NULL;` (the literal `__BOOTSTRAP_PLACEHOLDER__` is intentionally non-BCrypt so `BCryptPasswordEncoder.matches(...)` returns false for any plaintext — no security regression even if bootstrap silently fails). The header comment MUST explicitly state: "`DemoUserBootstrap` overwrites this placeholder on next boot. The placeholder is intentionally NOT a valid BCrypt hash so no login attempt against an un-bootstrapped row can succeed."
- Step 3: `ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(72) NOT NULL;`
- Column length is exactly **72** because BCrypt's `$2a$10$` output is 60 chars; 72 gives forward-compat headroom for the `$2b$` / `$2y$` variants and any future strength-bump prefix.
- V1..V10 stay byte-for-byte unmodified (regression guard AC-19).

**FR-8 (`User` entity extension)** — Add `@Column(name = "password_hash", nullable = false, length = 72) private String passwordHash;` plus `getPasswordHash() / setPasswordHash(String)`. Strictly additive; existing fields' annotations stay byte-identical. No `@JsonIgnore` is needed because `User` is never serialized to the wire (response uses `LoginResponse`).

**FR-9 (`DemoUserBootstrap` — idempotent hash upsert)** — Extend `DemoUserBootstrap.ensure(...)` so that, when an existing row's `password_hash` is null, empty, or equal to the V11 placeholder `__BOOTSTRAP_PLACEHOLDER__`, it sets `password_hash = passwordEncoder.encode("1234")` and persists. When the column already holds a valid BCrypt hash (starts with `$2a$` / `$2b$` / `$2y$`), the bootstrap MUST NOT overwrite it — operator-set passwords survive restarts. Idempotency verified by AC-9.

**FR-10 (`AuthService` rewrite)** — Replace the hardcoded `CREDS` map and `encodeToken` method:
- Resolve the `User` row by `username` (the request `username` MUST match `users.user_id` — preserves the existing PoC contract where login `username` is the userId. **No `users.username` column is introduced.**)
- `passwordEncoder.matches(req.password(), user.getPasswordHash())` → on false, throw `AuthException(INVALID_CREDENTIALS)` (same wire code as today; no error-message change).
- Missing fields → `AuthException(MISSING_FIELDS)` (unchanged from today; same code path).
- On success, call `jwtService.issue(user.getUserId(), user.getRole().name(), user.getName())` and return `new LoginResponse(token, user.getUserId(), user.getRole().name(), user.getName())` (envelope unchanged).
- Timing-safe behavior: when `userRepository.findById(username).isEmpty()`, the service MUST still execute one dummy `passwordEncoder.matches(req.password(), DUMMY_HASH)` call so login-time leaks neither "unknown user" vs "wrong password" — both paths return `INVALID_CREDENTIALS`. The dummy hash is a constant precomputed BCrypt of a random string, declared as a `static final` field with a Javadoc explaining the timing-attack mitigation.

### Backend — JWT auth filter

**FR-11 (`JwtAuthFilter`)** — New `com.authenticself.auth.JwtAuthFilter extends OncePerRequestFilter` registered via `FilterRegistrationBean` in `PasswordEncoderConfig` (or sibling `@Configuration`). Behavior:
- Skip filter chain entirely when `request.requestURI` is `/api/v1/auth/login` (login is the only unauthenticated endpoint) OR is outside `/api/v1/**` (Actuator, static — none today, future-proof).
- Read `Authorization` header. If absent or does not start with `Bearer ` (case-sensitive on the scheme), respond **401** with `{errorCode: "INVALID_TOKEN", message, correlationId}` (shared `ErrorResponse` envelope) and stop the chain.
- Extract token, call `jwtService.verify(...)`. On `AuthException(INVALID_TOKEN)`, respond 401 with the same envelope.
- Read `X-User-Id` header. If absent, respond 401 `INVALID_TOKEN` (the filter does not differentiate between missing token and missing user id — both map to the same client UX). If present and not equal to `verification.userId()` (the JWT `sub`), respond **401** with `{errorCode: "USER_ID_MISMATCH", message, correlationId}`. This is the cross-check required by D-1.
- On success, stash the verified userId/role into a request attribute (`AUTH_VERIFICATION`) — controllers do NOT read it today (D-1: backward compat); the attribute is reserved for the future controller refactor.
- Order: filter must run **before** controller dispatch and **before** existing `@RestControllerAdvice` exception handlers (which are post-dispatch by definition). Use `FilterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 100)` so it sits below Spring's `CharacterEncodingFilter` but above any business filter.
- The filter's 401 response MUST emit a UUIDv4 `correlationId` so it matches the `ErrorResponse` shape produced elsewhere (consistent with `WishlistExceptionAdvice` / `AdminExceptionAdvice` precedent).

**FR-12 (`AuthErrorCode` extension)** — Add `INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다.")` and `USER_ID_MISMATCH(HttpStatus.UNAUTHORIZED, "사용자 식별자가 일치하지 않습니다.")` to `AuthErrorCode`. Existing `MISSING_FIELDS` and `INVALID_CREDENTIALS` constants remain byte-identical.

### Backend — CORS

**FR-CORS-1 (`CorsConfig` bean)** — New `com.authenticself.web.CorsConfig implements WebMvcConfigurer`:
```java
@Value("${app.cors.allowed-origins}")
private List<String> allowedOrigins;

@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins.toArray(new String[0]))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("X-User-Id", "Authorization", "Content-Type", "Accept")
            .exposedHeaders("X-Request-Id")
            .allowCredentials(true)
            .maxAge(3600);
}
```
- Property `app.cors.allowed-origins` is a comma-separated list read via Spring SpEL list-binding. Env var: `APP_CORS_ALLOWED_ORIGINS`.
- **Dev default**: `http://localhost:8082,http://localhost:8081` (covers Metro 8082 and the older 8081 that `dev_servers.md` / runtime-verification.md reference).
- Allowed methods explicitly include `PUT` (used by `SpaceController.setPreferredStyle`) — the existing per-controller `@CrossOrigin` already permitted it; preserve that.

**FR-CORS-2 (`@CrossOrigin` removal)** — Delete the `@CrossOrigin(origins = "*", ...)` annotation block from each of:
- `src/backend/src/main/java/com/authenticself/space/SpaceController.java` (lines 53-57)
- `src/backend/src/main/java/com/authenticself/wishlist/WishlistController.java` (lines 42-45)
- `src/backend/src/main/java/com/authenticself/admin/AdminController.java` (lines 39-41)
- `src/backend/src/main/java/com/authenticself/auth/AuthController.java` (lines 22-24)

Remove the imports of `CrossOrigin` and `RequestMethod` if and only if those symbols become unused after the deletion (post-edit cleanup; static check). No other change to these files.

**FR-CORS-3 (preflight enforcement)** — `OPTIONS /api/v1/spaces/photo` from an **unlisted** origin returns 403 (or 200 with no `Access-Control-Allow-Origin` header — Spring's default is to omit the header, which the browser treats as deny). From a **listed** origin, returns 200 with `Access-Control-Allow-Origin: <origin>`, `Access-Control-Allow-Methods` including `POST`, and `Access-Control-Allow-Headers` including `X-User-Id, Authorization`. MockMvc test covers both paths (AC-CORS-3).

### Backend — Secrets externalization

**FR-SEC-1 (`application.yml` retrofit)** — Every sensitive key MUST use `${VAR_NAME:dev-default}` syntax. The required env vars and their defaults:

| Property key | Env var | Dev default | Has default? |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:mysql://localhost:3306/authenticself?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4` | yes (dev only) |
| `spring.datasource.username` | `DB_USER` | `root` | yes (dev only) |
| `spring.datasource.password` | `DB_PASSWORD` | (no default — leave blank in dev placeholder) | **no** |
| `app.ai.base-url` | `APP_AI_BASE_URL` | `http://localhost:8001` | yes |
| `app.storage.local.root` | `APP_STORAGE_LOCAL_ROOT` | `./var/object-storage` | yes |
| `app.auth.jwt.secret` | `APP_AUTH_JWT_SECRET` | (no default — fail-fast on missing) | **no** |
| `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:8082,http://localhost:8081` | yes |

The current `application.yml` already uses `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}` with NO defaults — that is acceptable as-is for those three (the spec only **mandates** they remain env-driven). The verification recommendation is to keep `DB_PASSWORD` without a default (current behavior — fails closed) and add a dev default to `DB_URL` / `DB_USER` only if we want local startup to JustWork without env setup. **Design specialist picks one consistent approach per the existing code path; do not regress.**

**FR-SEC-2 (no literal passwords)** — `grep -E "(password|secret|pass)\s*[:=]" src/backend/src/main/resources/application.yml` MUST find zero literal credential values. The only allowed forms are `${ENV:default}` placeholders. AC-SEC-2 enforces this with a regex.

**FR-SEC-3 (JWT secret fail-fast)** — On Spring Boot startup, if `APP_AUTH_JWT_SECRET` is unset OR shorter than 32 bytes (UTF-8 length), the application context MUST fail to start with an `IllegalStateException` whose message contains the literal substring `APP_AUTH_JWT_SECRET` AND the literal substring `docs/deployment/env.md`. This failure must occur during `JwtService` bean construction (eager) — not lazily on first request. AC-SEC-3 boots a test context with the env var unset and asserts the failure.

**FR-SEC-4 (`docs/deployment/env.md`)** — New file `docs/deployment/env.md`. MUST contain a markdown table with columns `Env Var | Purpose | Example | Has Dev Default?` covering every row in FR-SEC-1's table. Each row links to the `application.yml` line that consumes it. The file MUST explicitly call out that `APP_AUTH_JWT_SECRET` and `DB_PASSWORD` have no default and prod deployment will fail without them. Generation by hand is fine; no tooling required.

### Backward compatibility

**FR-BC-1 (Mobile contract — frozen)** — `src/mobile/src/auth/AuthContext.tsx` and `src/mobile/src/api/auth.ts` MUST NOT be modified by this task. The login flow's request shape (`{username, password}`) and response shape (`{token, userId, role, name}`) stay byte-identical. Only the `token` field's bytes change from `Base64(JSON)` to a JWT — both are strings; both are persisted to AsyncStorage by the existing `saveItem(STORAGE_KEY, ...)` call without parsing.

**FR-BC-2 (Existing mobile jest suite stays green)** — All 94 currently-passing tests under `src/mobile/__tests__/` continue to pass. The only permitted change is: IF a test mocks the login response, the mock value may stay as any non-empty string (the test never inspected JWT internals). No test file may add a new assertion against JWT shape. If — and only if — a test currently asserts on outgoing `Authorization` headers for non-login API calls, the test mock for the new bearer prefix may be added.

**FR-BC-3 (Existing backend tests stay green)** — All 141 backend tests under `src/backend/src/test/` continue to pass under `./gradlew test -PskipTestcontainers`. Tests that previously sent `X-User-Id` to a non-login endpoint MUST be updated to also include a valid `Authorization: Bearer <jwt>` header. A new test helper `com.authenticself.test.AuthTestHelper` MUST be introduced that exposes:
- `String issueTokenFor(String userId, String role)` — issues a token via the live `JwtService` bean (Spring-injected in `@SpringBootTest`).
- `Map<String, String> authHeaders(String userId, String role)` — convenience: returns `{Authorization: "Bearer <jwt>", X-User-Id: <userId>}`.

This helper centralizes the test-fixture change; the spec does NOT enumerate every test class to update (design specialist runs the suite, fixes the failures, lists the touched test files in `design.md`).

**FR-BC-4 (`DemoUserBootstrap` idempotent on restart)** — Repeated app boots MUST NOT clobber an operator-set BCrypt hash (FR-9 invariant). Verified by AC-9.

## 4. Non-Functional Requirements

- **JWT verification overhead**: < 5ms p99 added to authenticated requests (informational — no test enforces this; called out for design review).
- **No new external HTTP dependency** beyond `jjwt-api/impl/jackson` 0.12.x and (transitively or directly) `spring-security-crypto`. Specifically: no full Spring Security filter chain, no `spring-boot-starter-security` (which would auto-configure many filters this spec does NOT want).
- **DB column NOT NULL** enforced at the DB level (FR-7 V11 step 3). Backfill via `DemoUserBootstrap` on first boot is the operational answer for the demo rows; the placeholder approach (FR-7 step 2) keeps the migration self-contained — no operator step needed between deploying V11 and the next app boot.
- **Logging**: `AuthService.login(...)` logs INFO `op=login userId=<userId> result=success` on success and WARN `op=login userId=<usernameAttempted> result=failure errorCode=<code>` on failure. No password, no token, no hash ever logged. `JwtAuthFilter` logs WARN on every 401 with `correlationId + errorCode + maskedToken` where masked = first 8 chars + `...`.
- **Determinism**: `JwtService.issue(...)` with a fixed `Clock` produces a deterministic `iat` and `exp`. Signature is deterministic under HS256 (HMAC is). Two calls with the same `(userId, role, name)` and the same fixed clock produce byte-identical JWTs. AC-5 covers this.
- **Error envelope conformance**: every 401 / 400 emitted by `JwtAuthFilter` and `AuthExceptionAdvice` (if a new one is introduced — design choice) matches `ErrorResponse{errorCode, message, correlationId}` — same record used by `SpaceExceptionAdvice` / `WishlistExceptionAdvice` / `AdminExceptionAdvice`.
- **Security — no token in logs**: `JwtAuthFilter`, `JwtService`, `AuthService` MUST NOT log the full token at any level. AC-13 enforces with a log capture.
- **Security — no password in logs**: bootstrap MUST NOT log the plaintext `"1234"` even at DEBUG level. AC-9 covers.

## 5. Decisions (D-1 … D-3)

### D-1 — `X-User-Id` retention vs replacement
**Chosen: Option (a) — keep `X-User-Id` AS WELL AS `Authorization: Bearer`, but `JwtAuthFilter` cross-validates that `X-User-Id == JWT.sub`. Mismatch → 401 `USER_ID_MISMATCH`.**

Rationale: `X-User-Id` is the principal source of identity on every endpoint in `SpaceController` / `WishlistController` / `AdminController` (each handler reads `@RequestHeader("X-User-Id")` directly as the first parameter). Replacing it with JWT-derived identity is a 7+ controller refactor that touches every request-method signature, every controller test, and the `AdminAuthorizer` flow. That is **out of scope** for this hardening task (the project-level note in the prompt explicitly defers it). The cross-validation closes the security gap (a forged `X-User-Id` is now rejected unless it matches the signed JWT `sub`) without changing controller contracts — minimum blast radius. The filter stashes the verified userId in a request attribute so the future refactor can switch over without changing wire shape.

### D-2 — No refresh token / no `/auth/refresh` endpoint
**Chosen: Access token only, 7 days, no refresh.**

Rationale: this is a hardening task, not a session-redesign task. Refresh tokens require a token store, a revocation list, and a new endpoint — all out of scope. 7 days matches the prompt's pre-decided knob; users re-login weekly on the mobile app, which the existing UX already supports (login screen is reachable from logout).

### D-3 — Flyway version number
**Chosen: V11 (not V8).**

Rationale: the prompt suggested `V8__add_users_password_hash.sql`, but `V8__add_furniture_model_url.sql`, `V9__seed_curated_assets.sql`, and `V10__furniture_similar_cache.sql` already exist in `src/backend/src/main/resources/db/migration/`. Flyway forbids version-number collisions. The next free integer is **V11**, so the file is `V11__add_users_password_hash.sql`. The semantic content matches the prompt's V8 spec exactly; only the version prefix changes.

## 6. Out of Scope

- **Replacing `X-User-Id` with JWT-derived identity in controllers** — deferred to a future task `AUTH-CONTROLLER-REFACTOR`. The filter cross-checks but does not replace.
- **Refresh tokens / `/api/v1/auth/refresh`** — see D-2.
- **Token revocation / blacklist** — JWT is stateless; a logged-out token remains valid until `exp`. Acceptable trade-off for 7-day TTL.
- **Account self-service signup** — no `POST /api/v1/auth/register`; demo `admin` / `user` accounts remain the only login identities.
- **Password rotation flow** — no `POST /api/v1/auth/password`. Operator changes a password by running `UPDATE users SET password_hash = '<bcrypt>' WHERE user_id = ?`.
- **OAuth / social login / SSO** — out of scope.
- **HTTP-only cookie sessions / CSRF middleware** — the RN client uses bearer tokens in localStorage-equivalent (AsyncStorage); cookies are not in the threat model.
- **Spring Security filter chain** (`spring-boot-starter-security`) — explicitly NOT added. We use a single hand-rolled `OncePerRequestFilter` to keep the filter graph small and predictable.
- **Rate limiting on `/auth/login`** — out of scope; tracked separately in `06_production_hardening_checklist.md` S-1.
- **Multi-secret rotation / kid header** — single-secret HS256 only. A future `AUTH-KEY-ROTATION` task may add `kid` + multi-secret resolution.
- **Production CORS values in source** — `application.yml` ships the dev default; prod values come from env vars at deploy time, never committed.
- **`docs/deployment/env.md` automation** — hand-maintained; no CI lint that diffs it against `application.yml`.

## 7. References

### Source files cited
- `src/backend/src/main/java/com/authenticself/auth/AuthService.java:1-67` — current base64 stub to be replaced.
- `src/backend/src/main/java/com/authenticself/auth/AuthController.java:22-24` — `@CrossOrigin(origins = "*")` to remove.
- `src/backend/src/main/java/com/authenticself/auth/DemoUserBootstrap.java:32-51` — `ensure(...)` method to extend (FR-9).
- `src/backend/src/main/java/com/authenticself/auth/AuthErrorCode.java:7-8` — enum to extend with `INVALID_TOKEN` / `USER_ID_MISMATCH`.
- `src/backend/src/main/java/com/authenticself/space/SpaceController.java:53-57` — `@CrossOrigin` block to remove.
- `src/backend/src/main/java/com/authenticself/wishlist/WishlistController.java:42-45` — `@CrossOrigin` block to remove.
- `src/backend/src/main/java/com/authenticself/admin/AdminController.java:39-41` — `@CrossOrigin` block to remove.
- `src/backend/src/main/resources/application.yml:5-11, 53, 78` — sensitive keys to externalize (DB credentials, AI base URL, storage root).
- `src/backend/src/main/resources/db/migration/V7__add_users_role.sql` — pattern reference for V11 (idiomatic `ALTER TABLE` + index + header comment style).
- `src/backend/src/main/java/com/authenticself/domain/User.java:39-66` — entity to extend with `passwordHash` field (FR-8).
- `src/mobile/src/auth/AuthContext.tsx:67-82` — frozen happy path (FR-BC-1).
- `src/mobile/src/api/auth.ts:22-40` — frozen request/response contract.

### Follow-up checklist
- `artifacts/_followup/06_production_hardening_checklist.md` §H-2 (CORS wildcard), §S-3 (secrets), §Auth-#1 (the project-level JWT/BCrypt deficit).

### Style precedent
- `artifacts/UC-02-wishlist/spec.md` — section heading conventions, FR numbering style.
- `artifacts/UC-03-admin-overview/spec.md` — `Decisions (D-N)` block format, `Out of Scope` exhaustiveness norm, error-code table style.
- `artifacts/UC-03-admin-overview/acceptance_criteria.json` — JSON structure for ACs (`id`, `fr_ref`, `layer`, `description`, `verify_by`, `target_file_or_test`).

### PRD
- `공간 분석 및 가구 추천 프로젝트 구조화.pdf` — not a UC task; this hardening spec does not cite a specific UC section. Auth is mentioned only obliquely in §2 (관리자 / 사용자 구분) and §3 (`User` entity); this task implements the production posture those entries imply but do not formalize.
