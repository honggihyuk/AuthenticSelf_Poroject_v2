%% AuthenticSelf - UC-SECURE-AUTH: POST /api/v1/auth/login (happy + sad)
%% Anchors: FR-2 / FR-3 / FR-6 / FR-10 / FR-BC-1 / AC-10 / AC-11 / AC-12
%% Source of truth:
%%   src/mobile/src/auth/AuthContext.tsx                                 (frozen — FR-BC-1)
%%   src/mobile/src/api/auth.ts                                          (frozen)
%%   src/backend/src/main/java/com/authenticself/auth/AuthController.java
%%   src/backend/src/main/java/com/authenticself/auth/AuthService.java
%%   src/backend/src/main/java/com/authenticself/auth/JwtService.java
%%   src/backend/src/main/java/com/authenticself/repository/UserRepository.java

# Login sequence — POST /api/v1/auth/login

This diagram visualizes the end-to-end login flow added by UC-SECURE-AUTH
FR-2 (JWT issuance), FR-6 (BCrypt encoder bean), FR-10 (`AuthService`
rewrite), and FR-BC-1 (mobile contract frozen). It mirrors the design.md
§2 sequence and pins the timing-safe BCrypt branch on the unknown-user
path. The `D-1 cross-validation` arrow is annotated as a comment for
context — that filter only runs on subsequent authenticated requests
(see `authenticated_request_sequence.md`).

```mermaid
sequenceDiagram
    autonumber
    actor User as User<br/>(taps Sign In)
    participant RN as LoginScreen<br/>(src/mobile/.../LoginScreen.tsx)<br/>frozen — FR-BC-1
    participant API as apiLogin()<br/>(src/mobile/src/api/auth.ts)
    participant JF as JwtAuthFilter<br/>(skips /auth/login)<br/>FR-11
    participant Ctrl as AuthController<br/>POST /api/v1/auth/login<br/>FR-BC-1
    participant Svc as AuthService<br/>FR-10
    participant UR as UserRepository<br/>findById(userId)
    participant PE as BCryptPasswordEncoder<br/>strength=10 — FR-6
    participant JW as JwtService.issue<br/>HS256 — FR-2 / FR-4
    participant AC as AuthContext<br/>(saveItem → AsyncStorage)<br/>frozen — FR-BC-1

    Note over RN,AC: %% D-1 cross-validation: X-User-Id == JWT.sub<br/>%% applies to subsequent requests only; not on /auth/login.

    User->>RN: enter username + password, tap Sign In
    RN->>API: apiLogin({ username, password })
    API->>JF: POST /api/v1/auth/login<br/>Content-Type: application/json<br/>{ username, password }
    JF-->>JF: uri == "/api/v1/auth/login" → bypass<br/>(JwtAuthFilter skip case, AC-15)
    JF->>Ctrl: forward (filter chain proceeds)
    Ctrl->>Svc: login(LoginRequest)

    alt missing username or password (AC-10)
        Svc-->>Ctrl: throw AuthException(MISSING_FIELDS)
        Ctrl-->>API: 400 ErrorResponse{ MISSING_FIELDS, msg, correlationId }
        API-->>RN: rejected — show "아이디와 비밀번호를 입력해주세요."
    else fields present
        Svc->>UR: findById(username)
        alt unknown user (timing-safe — FR-10)
            UR-->>Svc: Optional.empty()
            Svc->>PE: matches(password, DUMMY_HASH)<br/>(constant precomputed BCrypt; ~80ms)
            PE-->>Svc: false (always)
            Svc-->>Ctrl: throw AuthException(INVALID_CREDENTIALS)
            Ctrl-->>API: 401 ErrorResponse{ INVALID_CREDENTIALS, msg, correlationId }
            API-->>RN: rejected — uniform UX vs wrong-password
        else user exists
            UR-->>Svc: Optional.of(User{ userId, name, role, passwordHash })
            Svc->>PE: matches(req.password, user.passwordHash)
            alt password mismatch
                PE-->>Svc: false
                Svc-->>Ctrl: throw AuthException(INVALID_CREDENTIALS)
                Ctrl-->>API: 401 ErrorResponse{ INVALID_CREDENTIALS, msg, correlationId }
            else password matches
                PE-->>Svc: true
                Svc->>JW: issue(userId, role.name(), name)
                JW->>JW: now = clock.instant()<br/>exp = now + 604800s<br/>(TTL invariant FR-4 / AC-4)
                JW->>JW: Jwts.builder()<br/>.subject(userId).claim(role).claim(name)<br/>.issuedAt(now).expiration(exp)<br/>.signWith(key, HS256).compact()
                JW-->>Svc: jwt = "eyJhbGciOiJIUzI1NiJ9.<body>.<sig>"
                Svc->>Svc: log.info("op=login userId={} result=success")<br/>(NFR — no password, no token bytes)
                Svc-->>Ctrl: LoginResponse{ token, userId, role, name }
                Ctrl-->>API: 200 { token, userId, role, name }<br/>%% envelope byte-identical to pre-task contract (FR-BC-1)
                API-->>RN: { token, userId, role, name }
                RN->>AC: setSession({ token, userId, role, name })
                AC->>AC: saveItem(STORAGE_KEY, JSON.stringify(session))<br/>%% AsyncStorage persistence — token bytes change<br/>%% from Base64(JSON) to JWT but field name unchanged
                AC-->>RN: session persisted
                RN-->>User: navigate to Home (role-based)
            end
        end
    end

    %% ----------------------------------------------------------------
    %% Legend (FR / AC traceability)
    %% ----------------------------------------------------------------
    %% LoginRequest/LoginResponse envelope unchanged ........ FR-BC-1
    %% BCryptPasswordEncoder(10) bean ....................... FR-6
    %% AuthService.login rewrite ............................ FR-10 / AC-10..AC-12
    %% Timing-safe DUMMY_HASH match ......................... FR-10
    %% JwtService.issue (HS256, exp = iat + 604800) ......... FR-2 / FR-4 / AC-3 / AC-4
    %% No password / no token in logs ....................... NFR (AC-18)
    %% JwtAuthFilter bypass for /auth/login ................. FR-11 / AC-15
    %% AuthContext / api/auth.ts untouched .................. FR-BC-1 / FR-BC-2
```
