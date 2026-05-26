# Environment variable matrix — UC-SECURE-AUTH

This matrix reproduces the env-var catalog from spec FR-SEC-1, cross-walked
against the production `application.yml` consumer line and current
default behavior on a clean dev box. Source of truth: `docs/deployment/env.md`
(AC-SEC-4) and `src/backend/src/main/resources/application.yml`. Each
row's "Has default?" matches the spec table verbatim; the "Current
default behavior" column reflects what happens on a fresh `./gradlew
bootRun` with no env vars exported; the "Production guidance" column
calls out the safe production setting.

## Catalog

| Env Var | application.yml key | Current default behavior (no env exported) | Has default? | Production guidance |
|---------|---------------------|--------------------------------------------|--------------|---------------------|
| `DB_URL` | `spring.datasource.url` | Spring fails to resolve placeholder → context start fails. (Spec FR-SEC-1 notes this is acceptable "fails closed" behavior — leave as no-default.) | no | Set to the prod JDBC URL with SSL on (`useSSL=true&requireSSL=true`). Never commit. |
| `DB_USER` | `spring.datasource.username` | Same as `DB_URL` — context start fails. | no | Per-environment service account; rotate via secrets manager. |
| `DB_PASSWORD` | `spring.datasource.password` | Context start fails — datasource auto-config rejects an unresolved placeholder. **Intentionally fails closed.** | **no** | Inject from secrets manager (AWS Secrets Manager / Vault). Never log, never commit. |
| `APP_AI_BASE_URL` | `app.ai.base-url` | Falls back to `http://localhost:8001` (the FastAPI dev port). | yes | Set to the prod AI service URL (`https://ai.authenticself.internal`). Only HTTPS. |
| `APP_STORAGE_LOCAL_ROOT` | `app.storage.local.root` | Falls back to `./var/object-storage` under the backend working dir. | yes | Set to a dedicated volume path (e.g. `/var/lib/authenticself/object-storage`) with the app user as owner. See MEMORY.md `ai_photo_root_mismatch` for the cross-service alignment requirement. |
| `APP_AUTH_JWT_SECRET` | `app.auth.jwt.secret` | `JwtService` constructor throws `IllegalStateException("APP_AUTH_JWT_SECRET must be set and >= 32 bytes; see docs/deployment/env.md")` → context start fails. (FR-SEC-3 / AC-SEC-3 — fail-fast invariant.) | **no** | Generate 32+ random bytes from `/dev/urandom` per environment; inject from secrets manager. Rotate quarterly. Never share between dev and prod. |
| `APP_CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | Falls back to `http://localhost:8082,http://localhost:8081` (Metro 8082 + legacy 8081 per `dev_servers.md`). | yes | Set to the comma-separated list of production origins (e.g. `https://app.authenticself.example.com`). Never include `*`. |

## Fail-fast summary (no-default keys)

Two keys above have **no development default**. Production deployments
that omit them will not start — by design.

| Key | Failure mode | Test pin |
|-----|--------------|----------|
| `APP_AUTH_JWT_SECRET` | `JwtService` bean construction throws `IllegalStateException`; Spring context never reaches "started". Message contains `APP_AUTH_JWT_SECRET` and a pointer to `docs/deployment/env.md`. | `JwtSecretStartupFailureTest` (AC-SEC-3) + `JwtServiceTest#failFast_*` (3 cases). |
| `DB_PASSWORD` | Spring Boot's datasource auto-config rejects the unresolved placeholder; context start fails. No JWT-specific message — generic Spring placeholder error. | Implicit: any startup smoke test fails without the env var. |

## Cross-reference

- Spec FR-SEC-1 table: `artifacts/UC-SECURE-AUTH/spec.md`
- Single source-of-truth doc: `docs/deployment/env.md`
- `application.yml`: `src/backend/src/main/resources/application.yml`
- AC pins: AC-SEC-1, AC-SEC-2, AC-SEC-3, AC-SEC-4

## Notes for operators

- `docs/deployment/env.md` is hand-maintained (per FR-SEC-4 — no CI lint
  enforces parity with `application.yml`). When a new sensitive key is
  added, update both files in the same commit.
- The dev defaults exist to keep `./gradlew bootRun` working on a
  developer laptop without forcing every contributor to export 7 env
  vars. They are **not safe for production**; prod deploys MUST set
  every `APP_*` value to a prod-appropriate string (the validator on
  `APP_AUTH_JWT_SECRET` enforces a minimum length, but does not detect
  "you accidentally used the dev secret in prod" — that is an operator
  hygiene concern).
- The two no-default keys are deliberately listed twice (once in the
  catalog, once in the fail-fast summary) so operators reading the doc
  top-down still hit the warning if they skim the matrix.
