# Backend Environment Variables

Single source of truth for the required environment variables that
`src/backend/src/main/resources/application.yml` consumes (UC-SECURE-AUTH
FR-SEC-1 / FR-SEC-4 / AC-SEC-4).

Two of the entries below have **no development default** and the Spring
context will fail to start without them. These are flagged in the "Has
Dev Default?" column and called out again at the bottom of this file.

## Env catalog

| Env Var                    | Purpose                                                                                                              | Example                                                                                                        | Has Dev Default? |
|----------------------------|----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|------------------|
| `DB_URL`                   | JDBC URL for the MySQL instance. Read by `spring.datasource.url` (application.yml line 8).                          | `jdbc:mysql://localhost:3306/authenticself?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4`         | no               |
| `DB_USER`                  | MySQL username. Read by `spring.datasource.username` (application.yml line 9).                                       | `authenticself`                                                                                                | no               |
| `DB_PASSWORD`              | MySQL password. Read by `spring.datasource.password` (application.yml line 10). **No default — fails closed.**       | `<value-from-secrets-manager>`                                                                                 | **no**           |
| `APP_AI_BASE_URL`          | Base URL for the Python FastAPI AI service. Read by `app.ai.base-url` (application.yml line 92).                    | `http://localhost:8001`                                                                                        | yes              |
| `APP_STORAGE_LOCAL_ROOT`   | Filesystem root for the local object-storage adapter. Read by `app.storage.local.root` (application.yml line 64).   | `./var/object-storage`                                                                                         | yes              |
| `APP_AUTH_JWT_SECRET`      | HS256 signing secret for issued JWTs. **Min 32 bytes UTF-8.** Read by `app.auth.jwt.secret` (application.yml line 56). **No default — fails fast on startup.** | `<random-32-or-more-byte-string-from-secrets-manager>`                                                          | **no**           |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated allow-list of CORS origins. Read by `app.cors.allowed-origins` (application.yml line 58).            | `https://app.authenticself.example.com,https://staging.authenticself.example.com`                              | yes              |

## Critical: no-default keys

The following keys have **no development default**. Production deploys
that omit them will fail to start (this is intentional — fail-fast over
silent insecurity):

- **`APP_AUTH_JWT_SECRET`** — the `JwtService` Spring bean constructor
  throws `IllegalStateException` containing the literal substring
  `APP_AUTH_JWT_SECRET` and a pointer back to this file when the env var
  is missing or shorter than 32 bytes UTF-8.
- **`DB_PASSWORD`** — Spring's datasource auto-configuration fails when
  the password placeholder resolves to nothing; the application context
  never reaches "started" state.

## Local development quick-start

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/authenticself?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4'
$env:DB_USER = 'root'
$env:DB_PASSWORD = '<your-local-mysql-password>'
$env:APP_AUTH_JWT_SECRET = 'dev-secret-please-replace-in-production-32chars'
# APP_AI_BASE_URL, APP_STORAGE_LOCAL_ROOT, APP_CORS_ALLOWED_ORIGINS use
# the application.yml defaults — set them only if your topology differs.
```
