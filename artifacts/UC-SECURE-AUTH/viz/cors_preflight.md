%% AuthenticSelf - UC-SECURE-AUTH: CORS preflight allowed vs denied origins
%% Anchors: FR-CORS-1 / FR-CORS-2 / FR-CORS-3 / AC-CORS-3
%% Source of truth:
%%   src/backend/src/main/java/com/authenticself/web/CorsConfig.java
%%   application.yml — app.cors.allowed-origins
%%   docs/deployment/env.md — APP_CORS_ALLOWED_ORIGINS

# CORS preflight — allowed vs denied origin

This diagram covers the `OPTIONS /api/v1/spaces/photo` preflight from
both an allowed origin (`http://localhost:8082`, the Metro dev origin
in the default `app.cors.allowed-origins`) and a denied origin
(`https://evil.example`). The allowed path returns 200 with the full
`Access-Control-*` response-header set per `CorsConfig`; the denied
path returns 403 (Spring's default reject-by-omission). Per the
`JwtAuthFilter` skip list, `OPTIONS` preflights bypass the JWT filter
so the browser receives the headers before issuing the real request
(FR-11). This visualizes FR-CORS-1 / FR-CORS-3 and AC-CORS-3.

```mermaid
sequenceDiagram
    autonumber
    participant Br as Browser<br/>(fetch with credentials)
    participant Mw as Spring CORS preflight handler<br/>(from CorsConfig — FR-CORS-1)
    participant JF as JwtAuthFilter<br/>%% skipped: method == OPTIONS
    participant Ctrl as SpaceController<br/>POST /api/v1/spaces/photo

    Note over Br,Ctrl: Allow-list (dev default): http://localhost:8082, http://localhost:8081<br/>Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS<br/>Allowed headers: X-User-Id, Authorization, Content-Type, Accept<br/>Exposed: X-Request-Id   ·   Credentials: true   ·   Max-Age: 3600

    %% ============================================================
    %% (A) Allowed origin
    %% ============================================================
    rect rgba(212,237,218,0.30)
    Note over Br,Ctrl: (A) Allowed origin — Origin: http://localhost:8082  (FR-CORS-3 happy path)
    Br->>Mw: OPTIONS /api/v1/spaces/photo<br/>Origin: http://localhost:8082<br/>Access-Control-Request-Method: POST<br/>Access-Control-Request-Headers: X-User-Id, Authorization, Content-Type
    Mw->>Mw: origin in allowedOrigins → match<br/>method "POST" in allowedMethods<br/>headers subset of allowedHeaders
    Mw-->>Br: 200 OK<br/>Access-Control-Allow-Origin: http://localhost:8082<br/>Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS<br/>Access-Control-Allow-Headers: X-User-Id, Authorization, Content-Type, Accept<br/>Access-Control-Allow-Credentials: true<br/>Access-Control-Max-Age: 3600<br/>Access-Control-Expose-Headers: X-Request-Id
    Note over Br: Preflight cached for 3600s.<br/>Real POST is issued next.
    Br->>JF: POST /api/v1/spaces/photo<br/>Origin: http://localhost:8082<br/>Authorization: Bearer ...   X-User-Id: user
    Note right of JF: OPTIONS-only bypass does NOT apply here —<br/>this is a real POST; JwtAuthFilter enforces auth.
    JF->>Ctrl: forward (auth headers valid)
    Ctrl-->>Br: 200 { roomId, status: "PENDING_ANALYSIS" }
    end

    %% ============================================================
    %% (B) Denied origin
    %% ============================================================
    rect rgba(248,215,218,0.30)
    Note over Br,Ctrl: (B) Denied origin — Origin: https://evil.example  (FR-CORS-3 deny path)
    Br->>Mw: OPTIONS /api/v1/spaces/photo<br/>Origin: https://evil.example<br/>Access-Control-Request-Method: POST<br/>Access-Control-Request-Headers: X-User-Id, Authorization
    Mw->>Mw: origin NOT in allowedOrigins<br/>→ reject preflight
    Mw-->>Br: 403 Forbidden<br/>(no Access-Control-Allow-Origin header)
    Note over Br: Browser interprets the missing<br/>Access-Control-Allow-Origin as a deny.<br/>The real POST is NEVER issued —<br/>fetch() rejects with a CORS error.
    end

    %% ============================================================
    %% (C) OPTIONS preflight + JwtAuthFilter skip note
    %% ============================================================
    Note over Br,JF: JwtAuthFilter.doFilterInternal — third skip case:<br/>  if ("OPTIONS".equalsIgnoreCase(method)) chain.doFilter(req, res);<br/>so the CORS preflight is never gated on Authorization. Real (non-OPTIONS) requests<br/>still pass through JwtAuthFilter (see authenticated_request_sequence.md).

    %% ----------------------------------------------------------------
    %% Legend (FR / AC traceability)
    %% ----------------------------------------------------------------
    %% Single CorsConfig source of CORS truth ............... FR-CORS-1 / AC-CORS-1
    %% All @CrossOrigin annotations removed ................. FR-CORS-2 / AC-CORS-2
    %% Allow-list driven by APP_CORS_ALLOWED_ORIGINS ........ FR-CORS-1 (env-driven)
    %% Preflight allow / deny enforcement ................... FR-CORS-3 / AC-CORS-3
    %% OPTIONS bypasses JwtAuthFilter ....................... FR-11 skip case
```
