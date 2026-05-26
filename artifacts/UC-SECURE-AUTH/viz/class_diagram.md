%% AuthenticSelf - UC-SECURE-AUTH: class diagram of the auth/web slice
%% Anchors: FR-2 / FR-3 / FR-6 / FR-9 / FR-10 / FR-11 / FR-12 / FR-CORS-1
%% Source of truth:
%%   src/backend/src/main/java/com/authenticself/auth/*.java
%%   src/backend/src/main/java/com/authenticself/web/CorsConfig.java
%%   src/backend/src/main/java/com/authenticself/domain/User.java

# Class diagram — UC-SECURE-AUTH auth & web slice

This diagram shows the new auth classes added by UC-SECURE-AUTH and how
they wire together. `AuthController` delegates to `AuthService`, which
composes `UserRepository`, the Spring `PasswordEncoder` bean
(`BCryptPasswordEncoder` strength=10), and `JwtService`. The new
`JwtAuthFilter` depends only on `JwtService` and `ObjectMapper`.
`PasswordEncoderConfig` is the single `@Configuration` that produces
both the `PasswordEncoder` bean and the `FilterRegistrationBean` that
pins `JwtAuthFilter` to `Ordered.HIGHEST_PRECEDENCE + 100` and
URL-pattern `/api/v1/*`. `DemoUserBootstrap` upserts the demo `admin` /
`user` rows on every boot via `UserRepository` + `PasswordEncoder`.
`JwtVerification` (record) and `AuthErrorCode` (enum) sit on the side
as immutable wire / contract surfaces.

```mermaid
classDiagram
    direction LR

    %% =====================================================
    %% Controllers + service layer
    %% =====================================================
    class AuthController {
        -AuthService service
        +ResponseEntity~LoginResponse~ login(LoginRequest)
    }

    class AuthService {
        -UserRepository userRepository
        -PasswordEncoder passwordEncoder
        -JwtService jwtService
        -static String DUMMY_HASH
        +LoginResponse login(LoginRequest)
    }

    class UserRepository {
        <<JpaRepository~User,String~>>
        +Optional~User~ findById(String)
        +User save(User)
    }

    class PasswordEncoder {
        <<interface — spring-security-crypto>>
        +String encode(CharSequence)
        +boolean matches(CharSequence, String)
    }

    class BCryptPasswordEncoder {
        +BCryptPasswordEncoder(int strength)
    }

    BCryptPasswordEncoder ..|> PasswordEncoder

    class JwtService {
        -SecretKey key
        -Clock clock
        +static long TTL_SECONDS = 604800
        -static int MIN_SECRET_BYTES = 32
        +JwtService(String secret, Clock clock)
        +String issue(String userId, String role, String name)
        +JwtVerification verify(String token)
    }

    class JwtVerification {
        <<record>>
        +String userId
        +String role
        +String name
        +Instant expiresAt
    }

    class JwtAuthFilter {
        <<extends OncePerRequestFilter>>
        -JwtService jwtService
        -ObjectMapper objectMapper
        +static String LOGIN_PATH = "/api/v1/auth/login"
        +static String API_PREFIX = "/api/v1/"
        +static String ATTR_AUTH_VERIFICATION = "authVerification"
        #void doFilterInternal(req, res, chain)
        -void reject(res, code, masked)
        -static String mask(String)
    }

    %% =====================================================
    %% Configuration
    %% =====================================================
    class PasswordEncoderConfig {
        <<@Configuration>>
        +PasswordEncoder passwordEncoder()
        +FilterRegistrationBean~JwtAuthFilter~ jwtAuthFilterRegistration(JwtService, ObjectMapper)
    }

    class CorsConfig {
        <<@Configuration implements WebMvcConfigurer>>
        -List~String~ allowedOrigins
        +addCorsMappings(CorsRegistry)
    }

    class FilterRegistrationBean {
        <<Spring Boot>>
        +setFilter(Filter)
        +addUrlPatterns(String...)
        +setOrder(int)
        +setName(String)
    }

    %% =====================================================
    %% Domain + bootstrap
    %% =====================================================
    class User {
        <<@Entity users>>
        +String userId
        +String name
        +String email
        +Role role
        +String passwordHash
    }

    class DemoUserBootstrap {
        <<@Component CommandLineRunner>>
        -UserRepository userRepository
        -PasswordEncoder passwordEncoder
        +static String PLACEHOLDER = "__BOOTSTRAP_PLACEHOLDER__"
        -static String DEFAULT_PASSWORD = "1234"
        +run(String[])
        -ensure(userId, name, email, role)
        +static boolean needsBootstrap(String hash)
    }

    %% =====================================================
    %% Side panel — enums / records
    %% =====================================================
    class AuthErrorCode {
        <<enum>>
        MISSING_FIELDS      : 400
        INVALID_CREDENTIALS : 401
        INVALID_TOKEN       : 401
        USER_ID_MISMATCH    : 401
    }
    class AuthException {
        <<RuntimeException>>
        +AuthErrorCode code()
    }
    class LoginRequest { <<record>> +username +password }
    class LoginResponse { <<record>> +token +userId +role +name }
    class ErrorResponse { <<record>> +errorCode +message +correlationId }

    %% =====================================================
    %% Wiring
    %% =====================================================
    AuthController --> AuthService
    AuthService --> UserRepository
    AuthService --> PasswordEncoder
    AuthService --> JwtService
    AuthService ..> AuthException
    AuthService ..> LoginResponse
    JwtAuthFilter --> JwtService
    JwtAuthFilter ..> AuthErrorCode : INVALID_TOKEN / USER_ID_MISMATCH
    JwtAuthFilter ..> ErrorResponse : writes envelope
    JwtService ..> JwtVerification : returns
    JwtService ..> AuthException   : throws on failure
    JwtService o-- "Clock" : injected
    PasswordEncoderConfig ..> PasswordEncoder : @Bean
    PasswordEncoderConfig ..> FilterRegistrationBean : @Bean
    PasswordEncoderConfig ..> JwtAuthFilter : registers
    DemoUserBootstrap --> UserRepository
    DemoUserBootstrap --> PasswordEncoder
    DemoUserBootstrap ..> User
    UserRepository ..> User
```

## FR / AC traceability

| FR / AC | Pinned by |
|---------|-----------|
| FR-2 / FR-4 | `JwtService.issue` + `TTL_SECONDS = 604800` |
| FR-3       | `JwtService.verify` returning `JwtVerification`; failure → `AuthException(INVALID_TOKEN)` |
| FR-6       | `PasswordEncoderConfig.passwordEncoder()` returns `new BCryptPasswordEncoder(10)` |
| FR-8       | `User.passwordHash` field |
| FR-9 / FR-BC-4 | `DemoUserBootstrap.needsBootstrap` guard, `PLACEHOLDER` const |
| FR-10      | `AuthService.login` + `DUMMY_HASH` (timing-safe) |
| FR-11      | `JwtAuthFilter` + `FilterRegistrationBean` (order, URL pattern) |
| FR-12      | `AuthErrorCode.{INVALID_TOKEN, USER_ID_MISMATCH}` |
| FR-CORS-1  | `CorsConfig implements WebMvcConfigurer` |
| FR-SEC-3   | `JwtService` constructor `IllegalStateException` on missing/short secret |
