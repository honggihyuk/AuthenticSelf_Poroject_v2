# Follow-up D-4 — Production Hardening Checklist

**Status**: DRAFT — pre-deployment checklist
**Date**: 2026-04-20
**Scope**: changes required before the first production cutover. Each item cites the file, severity, and the recommended fix.

---

## Severity legend

| Mark | Meaning |
|---|---|
| 🔴 | Blocker — MUST fix before production traffic |
| 🟠 | High — ship within first post-launch sprint |
| 🟡 | Medium — tech debt; schedule within first quarter |

## 1. Spring Boot / MySQL / Hibernate

### 🔴 H-1 — `MySQL8Dialect` is deprecated in Hibernate 6.x
**File**: `src/backend/src/main/resources/application.yml:19`
```yaml
spring:
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect   # deprecated
```
**Problem**: Hibernate 6.x auto-detects the dialect; pinning `MySQL8Dialect` triggers a deprecation warning and will break in Hibernate 7. Also fails `--fail-on-warning` CI profiles.

**Fix**:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        # dialect is auto-detected from the JDBC URL in Hibernate 6.x+
        jdbc:
          time_zone: Asia/Seoul
```
Drop the `dialect` key entirely. Verify Spring Boot startup + V1..V7 Flyway run + a smoke test POST to `/api/v1/spaces/photo`.

**Effort**: ~5 min + smoke test.

### 🔴 H-2 — `@CrossOrigin(origins = "*")` on three controllers
**Files**:
- `src/backend/src/main/java/com/authenticself/space/SpaceController.java:42`
- `src/backend/src/main/java/com/authenticself/wishlist/WishlistController.java:42`
- `src/backend/src/main/java/com/authenticself/admin/AdminController.java:39`

**Problem**: wildcard origin permits any domain to call these endpoints with credentials. In production this is a CSRF + credential-leak vector.

**Fix**: replace with a narrow CORS config bean:
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .allowedHeaders("X-User-Id", "Content-Type", "Accept")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```
Remove the per-controller `@CrossOrigin`. Configure `app.cors.allowed-origins` per environment (prod: `https://app.authenticself.example`; dev: `http://localhost:8081`).

**Effort**: 1h (new config class + env-specific values + smoke test).

### 🟠 H-3 — Admin bootstrap SQL not automated
**File**: `src/backend/src/main/resources/db/migration/V7__add_users_role.sql`

**Problem**: admin-role assignment relies on a manual `UPDATE users SET role='ADMIN' WHERE email=?` run. In production, bootstrap must be scripted + idempotent.

**Fix**: add an `app.admin.bootstrap-email` config and a `CommandLineRunner` that promotes the matching user on startup (no-op if already ADMIN). Or document a first-deploy SQL script under `docs/deployment/`.

**Effort**: 2h + dev-ops runbook.

### 🟡 H-4 — `ddl-auto: validate` fine, but schema drift detection is passive
**File**: `src/backend/src/main/resources/application.yml:16`

**Problem**: Flyway applies migrations, Hibernate validates the schema on boot. Any drift between JPA entities and the DB schema raises an exception at startup — good, but late. CI should run an integration test that boots Spring Boot against the migrated schema on every PR.

**Fix**: add a `@SpringBootTest` class under `src/backend/src/test/java/com/authenticself/smoke/SchemaValidationTest.java` that starts the full context against Testcontainers MySQL. Confirms V1–V7 + all JPA entities align.

**Effort**: 1–2h once Docker + JDK are in CI.

## 2. RN Mobile

### 🟠 M-1 — `originWhitelist={['*']}` on AR WebView
**File**: `src/mobile/src/ar/ARWebView.tsx:151`

**Problem**: permits the WebView to load any URL. Only `file://` + the local `assets/ar/placement.html` should be allowed.

**Fix**: narrow to `originWhitelist={['file://*', 'about:*']}`. Test on both iOS and Android — `file://` scheme varies per platform.

**Effort**: 30 min + device smoke test.

### 🟠 M-2 — `<model-viewer>` loaded from Google CDN
**File**: `src/mobile/assets/ar/placement.html`

**Problem**: runtime dependency on `ajax.googleapis.com`. Offline launch fails; CDN availability is a production risk.

**Fix (two options)**:
- (a) Self-host `model-viewer.min.js` under `assets/ar/vendor/model-viewer.min.js` and reference it via relative path.
- (b) Bundle via `metro` resolver so the asset is part of the RN bundle.

Option (a) is simpler; ~20 KB gzipped.

**Effort**: 1h (download pinned version, commit, update HTML script src).

### 🟡 M-3 — AR placeholder GLBs are empty stubs
**Files**: `src/mobile/assets/ar/models/{desk,bed,chair,lighting}.glb`

**Problem**: commit-time placeholder (128-132 bytes, valid glTF header, empty scene). Production AR view will render nothing visible.

**Fix**: replace with real GLB assets produced by the 3D asset pipeline. Keep file size reasonable (< 2 MB each) for bundle size.

**Effort**: asset-production cycle (design team).

### 🟡 M-4 — `settings.userId` is a dev stub
**File**: `src/mobile/src/settings.ts` (check)

**Problem**: `X-User-Id` header comes from a hardcoded stub, not a real auth flow.

**Fix**: integrate with the production auth provider (Firebase, Supabase, custom JWT). See D-3 (`05_missing_user_header_alignment.md`) for the related 400/401 decision.

**Effort**: 3–5 days (dedicated auth integration task).

## 3. Observability / Operations

### 🟠 O-1 — No health check endpoint
**Problem**: backend has no `/actuator/health` exposed. Kubernetes / load balancer liveness + readiness probes need one.

**Fix**: add `spring-boot-starter-actuator` to `build.gradle`, expose `health` + `info` endpoints. Restrict `/actuator/**` to internal networks.

**Effort**: 30 min.

### 🟠 O-2 — Structured logging not configured
**Problem**: controllers/services use `slf4j` + `@Slf4j` but output is plain text. Production observability needs JSON logs for ingestion.

**Fix**: add Logback JSON encoder (`logstash-logback-encoder`) + profile-specific `logback-spring.xml`. Mark `production` profile to emit JSON; keep plain text in `dev`.

**Effort**: 1h.

### 🟠 O-3 — Correlation ID plumbing
**Current**: `ErrorResponse.correlationId` is generated per-exception in `ExceptionAdvice` classes. There's no upstream MDC propagation, so correlation IDs are not visible in logs outside the advice.

**Fix**: add a servlet filter that reads `X-Request-Id` (or generates one) and stuffs it into MDC for the duration of the request. Log lines then carry the same ID the client sees in `ErrorResponse`.

**Effort**: 1h + add filter test.

## 4. Security

### 🔴 S-1 — No rate limiting
**Problem**: every endpoint accepts unlimited traffic. Photo-upload endpoint especially is a DoS / storage-abuse vector (up to 10 MB per request).

**Fix**: add bucket-based rate limiting via `bucket4j-spring-boot-starter` or a reverse-proxy config (nginx/CloudFront). Upload endpoint: 10 req/min/user. Recommendation endpoint: 30 req/min/user. Admin: 60 req/min.

**Effort**: 2–3h for bucket4j; less if handled at the gateway layer.

### 🔴 S-2 — Photo storage path is a local filesystem
**Problem**: uploaded photos go to `var/storage/photos/...` on the app server. Not scalable, not durable, lost on container restart.

**Fix**: swap to S3 (or equivalent) via `spring-cloud-aws`. Pre-sign upload URLs if the backend fronts them. Add a bucket lifecycle policy (delete after N days).

**Effort**: 1 day for full S3 integration incl. IAM + bucket policy.

### 🟠 S-3 — No secret management
**Problem**: `application.yml` holds DB credentials + `app.ai.service-url` as plain text. Production values must come from env vars or a secret store.

**Fix**: externalize every sensitive key via `${VAR_NAME}` placeholders. Document required env vars in `docs/deployment/env.md`. Use Kubernetes Secrets / AWS Parameter Store / Vault for deployment.

**Effort**: 1h cataloguing + platform setup.

### 🟡 S-4 — Upload content-type trust
**Problem**: `PhotoUploadService` trusts the client-supplied `contentType` header. A malicious client could upload executable content labeled `image/jpeg`.

**Fix**: server-side content-type verification via magic-byte inspection (Apache Tika or `java.net.URLConnection.guessContentTypeFromStream`) before persisting.

**Effort**: 2h + tests.

## 5. Python AI service

### 🟠 P-1 — No ASGI server configured for prod
**Current**: `uvicorn app.main:app --reload` in dev. Production needs a managed ASGI runner with workers + graceful shutdown.

**Fix**: deploy with `gunicorn -w 4 -k uvicorn.workers.UvicornWorker app.main:app`. Bind to a unix socket + reverse-proxy via nginx.

**Effort**: 30 min + container image tweak.

### 🟡 P-2 — OpenCV non-ASCII path fix landed (D-1), but dimensions.py has a stub
**Files**: `src/ai/app/analyzers/color.py`, `dimensions.py`

**Color status**: FIXED via `np.fromfile + cv2.imdecode` in D-1 (this follow-up round).
**Dimensions status**: stub — `TODO(ml)` docstring per iter-1 space-analysis verification. Real LayoutNet / YOLO integration is a product decision.

**Fix**: scope as part of ML integration task; out of hardening batch.

### 🟠 P-3 — CORS + auth on AI service
**Problem**: the Python service trusts any caller. If it's reachable outside the cluster, it's a DoS vector.

**Fix**: bind the AI service to the cluster-internal network only (ClusterIP in K8s). Add mutual TLS or an API-key header if the AI service must be reachable externally.

**Effort**: platform decision.

## 6. Observability of the WebView/AR layer

### 🟡 AR-1 — No unload-event telemetry
**Problem**: the AC-29 fix ensures `unload` fires, but we don't emit telemetry. Leaks would go unnoticed.

**Fix**: wire `emitArFurniturePlaced` (already exists) to the same analytics sink as the wishlist add. Log `unload` + `error` events with furnitureId + duration.

**Effort**: 1h once telemetry sink is chosen.

## 7. Summary — blocker count

| Severity | Count | Examples |
|---|---|---|
| 🔴 Blocker | 4 | H-1 dialect, H-2 CORS, S-1 rate limit, S-2 S3 |
| 🟠 High | 8 | H-3/O-1/O-2/O-3/M-1/M-2/P-1/S-3/P-3 |
| 🟡 Medium | 5 | H-4/M-3/M-4/S-4/AR-1/P-2 |

**Recommended pre-launch cut line**: resolve all 4 blockers + at least O-1 (health check) and S-3 (secrets) before opening production traffic. Everything else can ship in the first post-launch sprint.

## 8. References

- `artifacts/_followup/03_batch_warning_cleanup.md` — warning harvest
- `artifacts/_followup/05_missing_user_header_alignment.md` — D-3 proposal (S-related)
- All 8 `artifacts/<task-id>/verification.md` §"Warnings" sections
