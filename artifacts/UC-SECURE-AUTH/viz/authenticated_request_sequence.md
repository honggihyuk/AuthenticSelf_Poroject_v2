%% AuthenticSelf - UC-SECURE-AUTH: JwtAuthFilter on a subsequent authenticated request
%% Anchors: FR-3 / FR-11 / FR-12 / D-1 / AC-13 / AC-14 / AC-15
%% Source of truth:
%%   src/backend/src/main/java/com/authenticself/auth/JwtAuthFilter.java
%%   src/backend/src/main/java/com/authenticself/auth/JwtService.java
%%   src/backend/src/main/java/com/authenticself/auth/PasswordEncoderConfig.java  (filter registration + order)
%%   src/backend/src/main/java/com/authenticself/controller/dto/ErrorResponse.java

# Authenticated request sequence — `GET /api/v1/wishlist`

This diagram visualizes how `JwtAuthFilter` gates every non-login
`/api/v1/**` request (FR-11). It covers the happy path (signature OK +
`X-User-Id == JWT.sub` cross-check per D-1) and the three rejection
branches: missing/malformed Bearer, signature/expiry/parse failure, and
`X-User-Id` mismatch. The filter sits at
`Ordered.HIGHEST_PRECEDENCE + 100` — after Spring's
`CharacterEncodingFilter` but before any business filter, the
`DispatcherServlet`, and every `@RestControllerAdvice` (which are
post-dispatch by definition).

```mermaid
sequenceDiagram
    autonumber
    participant RN as React Native client<br/>(api/wishlist.ts)
    participant CE as CharacterEncodingFilter<br/>(Spring default, order ≈ -100)
    participant JF as JwtAuthFilter<br/>order = HIGHEST_PRECEDENCE + 100<br/>FR-11
    participant JW as JwtService.verify<br/>FR-3
    participant DS as DispatcherServlet<br/>(post-filter, pre-controller)
    participant WC as WishlistController<br/>@RequestHeader X-User-Id
    participant Adv as @RestControllerAdvice<br/>(only runs after dispatch)

    Note over RN,Adv: Filter order: CharacterEncodingFilter → JwtAuthFilter → DispatcherServlet → controllers / advice<br/>JwtAuthFilter.setOrder(Ordered.HIGHEST_PRECEDENCE + 100)  — PasswordEncoderConfig.jwtAuthFilterRegistration()

    RN->>CE: GET /api/v1/wishlist<br/>Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.<body>.<sig><br/>X-User-Id: user
    CE-->>JF: forward (utf-8 normalized)

    JF->>JF: uri.startsWith("/api/v1/")<br/>uri != "/api/v1/auth/login"<br/>method != OPTIONS<br/>→ enforce
    JF->>JF: read Authorization header<br/>require prefix "Bearer " (case-sensitive)

    alt header missing OR no "Bearer " prefix OR empty token (AC-13)
        JF->>Adv: (advice NOT invoked — filter writes envelope directly)
        JF-->>RN: 401 ErrorResponse{<br/>  errorCode: "INVALID_TOKEN",<br/>  message: "유효하지 않은 인증 토큰입니다.",<br/>  correlationId: UUIDv4<br/>}
    else token present
        JF->>JW: verify(token)
        alt bad signature / expired / malformed / missing claim (AC-13 / AC-14)
            JW-->>JF: throw AuthException(INVALID_TOKEN)
            JF-->>RN: 401 ErrorResponse{ INVALID_TOKEN, msg, correlationId }
        else verification ok
            JW-->>JF: JwtVerification{ userId, role, name, expiresAt }
            JF->>JF: read X-User-Id header
            alt X-User-Id missing or blank (AC-13)
                JF-->>RN: 401 ErrorResponse{ INVALID_TOKEN, msg, correlationId }<br/>%% collapsed to INVALID_TOKEN for uniform UX
            else X-User-Id != verification.userId() (D-1 cross-check, AC-14)
                JF-->>RN: 401 ErrorResponse{<br/>  errorCode: "USER_ID_MISMATCH",<br/>  message: "사용자 식별자가 일치하지 않습니다.",<br/>  correlationId: UUIDv4<br/>}
            else X-User-Id == JWT.sub (happy path, AC-15)
                JF->>JF: req.setAttribute(<br/>  "authVerification", verification)<br/>%% stash for future controller refactor (D-1)
                JF->>DS: chain.doFilter(req, res)
                DS->>WC: dispatch GET /api/v1/wishlist
                WC->>WC: @RequestHeader("X-User-Id") userId<br/>%% controllers still read header directly today
                WC-->>RN: 200 OK { items: [...] }
            end
        end
    end

    Note over JF,Adv: NFR — token logged as first 8 chars + "..."  (mask), never full bytes.<br/>WARN line shape: "auth filter reject code={} correlationId={} token={}"  (AC-13 log capture)

    %% ----------------------------------------------------------------
    %% Legend (FR / AC traceability)
    %% ----------------------------------------------------------------
    %% Filter applies to every non-login /api/v1/** ........ FR-11 / AC-15
    %% Bearer prefix mandatory ............................. FR-11 (case-sensitive)
    %% JwtService.verify failure → INVALID_TOKEN ........... FR-3  / FR-12 / AC-13
    %% X-User-Id == JWT.sub cross-check .................... D-1   / FR-11 / AC-14
    %% Mismatch → USER_ID_MISMATCH ......................... FR-12
    %% Standard ErrorResponse envelope + UUIDv4 ............ FR-11 / matches *ExceptionAdvice
    %% Order = HIGHEST_PRECEDENCE + 100 .................... PasswordEncoderConfig
    %% OPTIONS preflight bypasses the filter ............... see cors_preflight.md
```
