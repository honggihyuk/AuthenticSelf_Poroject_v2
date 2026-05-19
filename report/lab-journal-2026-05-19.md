# 실습 일지 — 2026-05-19

AuthenticSelf 프로젝트에 오늘 추가한 기능과 그 동작 방식을 정리한다. 모바일 웹 접속 지원,
추천/위시리스트 도선 정리, 로그인 + 관리자/이용자 역할 분리, 관리자 운영 대시보드까지
네 갈래의 슬라이스를 한꺼번에 진행했다.

---

## 1. 오늘의 결과물 (한눈에)

| # | 기능 | 구성 |
|---|---|---|
| 1 | 폰 Safari로 웹 번들 접속 | 모바일 `settings.ts`가 페이지 호스트를 읽어 API base URL 자동 해석 |
| 2 | 추천 화면 ↔ 위시리스트 도선 | `내 위시리스트 보기` 진입 버튼 추가, 카드 버튼 폰트 축소 |
| 3 | 데모용 로그인 + 역할 라우팅 | Backend `auth` 패키지, Frontend `AuthContext`, 3-way 조건부 라우팅 |
| 4 | 관리자 대시보드 (P0) | `/api/v1/admin/overview` 4개 타일 전체를 KPI + 분포 막대 + 시간 윈도우 토글로 시각화 |

---

## 2. 폰 Safari 웹 접속 지원

### 무엇을 만들었나
모바일 앱을 Expo Go 네이티브 클라이언트가 아닌 **폰 브라우저에서 직접 실행**할 수 있게 한다.
`settings.ts`가 페이지가 서빙된 호스트를 자동 인식해서 API 호출을 같은 호스트의 18080
포트로 라우팅한다.

### 어떻게 동작하나

```ts
function resolveWebBaseUrl(): string {
  if (Platform.OS !== 'web' || typeof window === 'undefined') return configuredBaseUrl;
  const host = window.location.hostname;
  if (host === 'localhost' || host === '127.0.0.1' || host === '') return configuredBaseUrl;
  return `${window.location.protocol}//${host}:${BACKEND_PORT}`;
}
```

- 데스크탑에서 `http://localhost:8082` 접속 → API는 `http://localhost:18080` (기존과 동일)
- 폰 Safari에서 `http://10.200.73.48:8082` 접속 → API는 자동으로 `http://10.200.73.48:18080`
- React Native 네이티브 클라이언트(Expo Go)는 web 분기가 거짓이라 `app.json`의 값을 그대로 사용

### 사용자 시나리오
1. PC에서 `node node_modules\expo\bin\cli start --port 8082` 실행
2. PC LAN IP 조회 (`Get-NetIPAddress -AddressFamily IPv4`)
3. 폰 Safari에서 `http://<PC-LAN-IP>:8082` 입력 → 같은 Wi-Fi 망에서 즉시 동작
4. 코드 1줄도 안 고치고 사진 업로드/분석/추천/AR까지 전체 흐름 수행 가능

---

## 3. 추천 ↔ 위시리스트 도선

### 무엇을 만들었나
- `Button` 컴포넌트에 `labelStyle` prop을 추가해 폰트 override 가능
- 추천 카드 안 두 버튼(`위시리스트에 추가`, `AR로 배치`)을 17px → 14px로 축소
- 추천 화면 상단(헤더 직후 우측 정렬)에 `내 위시리스트 보기` 버튼 추가

### 어떻게 동작하나

```tsx
// Button.tsx
export interface ButtonProps extends Omit<PressableProps, 'style' | 'children'> {
  label: string;
  variant?: ButtonVariant;
  size?: ButtonSize;
  fullWidth?: boolean;
  disabled?: boolean;
  labelStyle?: TextStyle;   // 신규: 폰트/색 override 슬롯
}
```

```tsx
// RecommendationScreen.tsx — 헤더 직후
<View style={styles.headerActions}>
  <Button
    label="내 위시리스트 보기"
    variant="secondary"
    size="sm"
    onPress={() => navigation.navigate('Wishlist')}
    labelStyle={{ fontSize: 13 }}
  />
</View>
```

`labelStyle`은 호환성을 위해 optional. 기존 사용처(HomeScreen, LoginScreen 등) 모두 무수정 동작.

### 사용자 시나리오
- 추천 결과를 보다가 위시리스트로 바로 이동 → 카드별 `구매 완료` / `삭제` 동작 확인
- 좁은 폰 화면에서도 두 버튼이 한 줄에 깔끔하게 들어감

---

## 4. 로그인 + 관리자/이용자 역할 분리

PoC 단계의 데모 인증을 단일 슬라이스로 구현. 진짜 JWT 도입 시 응답 envelope만 유지하면
프론트엔드 수정 0으로 호환되도록 설계.

### 4-1. 백엔드 — `com.authenticself.auth` 패키지

| 파일 | 역할 |
|---|---|
| `AuthController` | `POST /api/v1/auth/login` 단일 엔드포인트 |
| `AuthService` | 하드코딩 자격증명 검증 + base64 토큰 stub 발급 |
| `AuthErrorCode` / `AuthException` / `AuthExceptionAdvice` | 400 `MISSING_FIELDS`, 401 `INVALID_CREDENTIALS` 표준 `ErrorResponse` 변환 |
| `DemoUserBootstrap` | `CommandLineRunner` — 부팅 시 `admin`/`user` 두 행 idempotent upsert |
| `dto/LoginRequest`, `dto/LoginResponse` | 요청/응답 record |

**자격증명 매트릭스 (하드코딩):**

| username | password | role |
|---|---|---|
| `admin` | `1234` | ADMIN |
| `user`  | `1234` | USER |

**응답 envelope (JWT 호환):**
```json
{ "token": "<base64 stub>", "userId": "admin", "role": "ADMIN", "name": "관리자" }
```

**`DemoUserBootstrap` 동작:**
- 부팅 시 `users` 테이블에 두 행을 `existsById()` 체크 후 없을 때만 INSERT
- 이미 존재하면 손대지 않음 → 운영자가 수동으로 role을 바꿔도 보존
- `AdminAuthorizer.requireAdmin()`이 `X-User-Id` 헤더로 User 행을 lookup하므로 이 시드가 없으면 admin API가 항상 404

### 4-2. 프론트엔드 — 인증 컨텍스트

| 파일 | 역할 |
|---|---|
| `src/auth/storage.ts` | 저장소 추상화 — web `window.localStorage` / native 메모리 fallback. AsyncStorage 의존성 미추가 |
| `src/auth/AuthContext.tsx` | `{state, isLoggedIn, login(), logout()}` Context + `useAuth()` 훅 |
| `src/api/auth.ts` | `login()` fetch 클라이언트 (`ApiError`로 4xx 분기) |
| `src/screens/LoginScreen.tsx` | ID/PW input + 로그인 버튼 + 실패 토스트 + 데모 자격증명 힌트 |

**`AuthContext` 데이터 흐름:**

```
[mount] readPersisted() ──→ localStorage 'authenticself.auth.v1' 동기 복원
                              │
                              ├── 있음: state = {token, userId, role, name}
                              │         settings.userId ← state.userId
                              │
                              └── 없음: state = null

[login(u, p)] apiLogin() ─→ saveItem() + syncUserIdToSettings() + setState()
[logout()]    removeItem() + syncUserIdToSettings('') + setState(null)
```

**중요 결정 — `settings.userId` 자동 동기화:**
기존 7개 화면(`Upload`, `Analyzing`, `StyleSelection`, `Recommendation`, `Wishlist`,
`ARPlacement`, `Objects`)이 `settings.userId`를 직접 읽고 있었다. 모두 `useAuth()`로
refactor하는 대신, AuthContext가 로그인/로그아웃 시 `settings.userId`도 함께 갱신.
조건부 스택 안에서만 이 화면들이 mount되므로 값이 빈 문자열인 상태에서 화면이 열리는
일은 발생하지 않는다.

### 4-3. `App.tsx` — 조건부 Stack.Screen 라우팅

```tsx
function RootNavigator() {
  const { isLoggedIn, state } = useAuth();

  if (!isLoggedIn) {
    return (
      <Stack.Navigator initialRouteName="Login">
        <Stack.Screen name="Login" component={LoginScreen} />
      </Stack.Navigator>
    );
  }
  if (state?.role === 'ADMIN') {
    return (
      <Stack.Navigator initialRouteName="AdminDashboard">
        <Stack.Screen name="AdminDashboard" component={AdminDashboardScreen} />
      </Stack.Navigator>
    );
  }
  return (
    <Stack.Navigator initialRouteName="Home">
      {/* Home / Upload / Analyzing / StyleSelection / Recommendation /
          Wishlist / ARPlacement / Objects */}
    </Stack.Navigator>
  );
}
```

React Navigation은 자식 `Stack.Screen` 리스트가 바뀌면 history를 자동 reset. Android
뒤로가기로 로그인 화면이 다시 노출될 일이 구조적으로 없다.

### 4-4. 사용자 시나리오

```
┌─ 비로그인 ────────────────────────────────────┐
│ LoginScreen                                    │
│   아이디: [ admin ]                            │
│   비밀번호: [ ●●●● ]                            │
│   [ 로그인 ]                                   │
│   힌트: admin / 1234, user / 1234              │
└────────────────────────────────────────────────┘
        │ admin/1234                  │ user/1234
        ▼                             ▼
┌─ ADMIN ──────────────────┐  ┌─ USER ──────────────────────┐
│ 관리자 대시보드           │  │ Home → Upload → Analyzing → │
│ (§5 참조)                 │  │ StyleSelection → Recommend  │
│                           │  │ → AR / Wishlist             │
│ [ 로그아웃 ]              │  │ [ 로그아웃 ]                 │
└───────────────────────────┘  └─────────────────────────────┘
```

- localStorage 영속화로 새로고침 후에도 로그인 상태 유지
- 로그아웃 시 storage 비움 + `settings.userId = ''` → 다음 마운트는 AuthStack
- HomeScreen 부제에 `${state.name}님` 표시

---

## 5. 관리자 대시보드 (P0)

### 5-1. 무엇을 만들었나

기존엔 카운트 3개(이용자/방/위시리스트 totals)만 표시했다. P0에서 백엔드가 이미 내려주는
**4개 타일 전체**를 풀어내어 KPI + 가로 막대 그래프 + 시간 윈도우 토글로 시각화.

### 5-2. 백엔드 계약 (변경 없음, 클라이언트만 확장)

`GET /api/v1/admin/overview?window={LAST_7D|LAST_30D|ALL}` + 헤더 `X-User-Id: admin`

응답 구조:
```
{
  "window": "LAST_30D",
  "generatedAt": "2026-05-19T13:24:26+09:00",
  "users":    { totalUsers, totalAdmins, newSignups, activeUsers, signupsByDay[] },
  "rooms":    { totalSpaces, statusDistribution{}, styleDistribution{}, mainColorTop5[] },
  "wishlist": { totalItems, statusDistribution{}, categoryDistribution{}, conversionRate },
  "sales":    { totalSalesKrw, purchasedItemCount, averageOrderKrw, salesByCategory{}, salesByDay[] }
}
```

### 5-3. 화면 구성

```
┌─ ADMIN ──────────────────────────────────────────┐
│ 관리자님 환영합니다                              │
│ 전체 운영 현황을 한눈에 확인하세요.              │
│                                                  │
│ [ 7일 │ 30일* │ 전체 ]   ← 윈도우 토글           │
│                                                  │
│ 생성 2026-05-19 13:24 · 기간 LAST_30D            │
│                                                  │
│ ┌─ 이용자 ────────────────────────────────────┐ │
│ │  누적 이용자 [2]    관리자 [1]              │ │
│ │  기간 내 신규 가입 [3]  활동 유저 [2]       │ │
│ └─────────────────────────────────────────────┘ │
│                                                  │
│ ┌─ 방 분석 ───────────────────────────────────┐ │
│ │  누적 [17]   분석 실패율 [41.2%]            │ │
│ │  분석 상태                                  │ │
│ │    분석 완료 ██████████ 10                  │ │
│ │    분석 실패 ███████     7                  │ │
│ │    대기 중               0                  │ │
│ │  선호 스타일                                │ │
│ │    SCANDINAVIAN ██████████ 10               │ │
│ │    MODERN                  0                │ │
│ │    ...                                      │ │
│ │  주요 색상 Top 5                            │ │
│ │    [●#C8BFB5 5] [●#D9D2CB 3] ...            │ │
│ └─────────────────────────────────────────────┘ │
│                                                  │
│ ┌─ 위시리스트 ────────────────────────────────┐ │
│ │  총 [6]   구매 전환율 [0.0%]                │ │
│ │  상태  보관 중 ██████ 6  구매 완료   0      │ │
│ │  카테고리  책상 ███ 3  조명 ██ 2  ...       │ │
│ └─────────────────────────────────────────────┘ │
│                                                  │
│ ┌─ 매출 ──────────────────────────────────────┐ │
│ │  총 매출 [₩0]   구매 건수 [0]               │ │
│ │  평균 객단가 [₩0]                           │ │
│ │  카테고리별 매출 (전부 ₩0)                  │ │
│ └─────────────────────────────────────────────┘ │
│                                                  │
│ 운영 작업                                        │
│ [ 야간 배치 수동 트리거 (placeholder) ]          │
│                                                  │
│ [ 로그아웃 ]                                     │
└──────────────────────────────────────────────────┘
```

### 5-4. 구성 요소

| 컴포넌트 | 책임 |
|---|---|
| `AdminDashboardScreen` | 최상위 — 윈도우 state, fetch, 4개 타일 + 운영 작업 + 로그아웃 footer |
| `WINDOWS` 세그먼티드 컨트롤 | `LAST_7D / LAST_30D / ALL` 토글, 변경 시 useEffect로 자동 refetch |
| `UsersCard`, `RoomsCard`, `WishlistCard`, `SalesCard` | 타일별 카드 — KPI 행 + subHeading + 분포 막대 |
| `Kpi` | 큰 숫자 + 라벨 한 셀. `value`가 string이면 `formatKrw` 등 포맷 결과 그대로 표시 |
| `DistributionBars` | `Array<[key, value]>` + 선택적 `labelMap`, `formatValue`. max값을 100% 기준으로 정규화한 가로 막대 N줄 |
| `colorChip` | hex 색상 스와치 원 + 라벨 + 카운트 (Rooms 타일의 Top5 색상) |

### 5-5. 기술 결정

- **차트 라이브러리 미사용** — 분포는 `View width: ${pct}%`만으로 표현. 의존성 0개 추가.
- **분포 컴포넌트 단일화** — `DistributionBars` 하나로 5가지 분포(rooms status/style,
  wishlist status/category, sales by category) 전부 렌더. `labelMap`으로 한글 라벨 매핑,
  `formatValue`로 카테고리별 매출처럼 ₩ 포맷이 필요한 경우만 override.
- **계산 지표 노출** — 백엔드가 안 주는 `failedPct = FAILED / totalSpaces`를 클라이언트에서
  계산해 KPI로. 17건 중 7건 FAILED라는 운영 신호가 즉시 보인다.

### 5-6. 사용자 시나리오

```
admin/1234 로그인 → 대시보드 진입 → 디폴트 LAST_30D 데이터 fetch
   │
   ├── "7일" 탭 → setWindowSel('LAST_7D') → useEffect 트리거 → refetch → 화면 갱신
   │
   ├── "운영 작업 → 야간 배치 수동 트리거" → 현재는 "준비 중" 안내 Alert (placeholder)
   │
   └── "로그아웃" → AuthContext.logout() → storage 비움 → 다음 렌더에서 LoginScreen
```

---

## 6. README.md 운영 가이드 현행화

다른 사람이 클론 받아 동일 환경을 구성할 수 있도록 다음 5개 섹션을 업데이트:

| 섹션 | 변경 |
|---|---|
| 1. 프로젝트 구성 | 기본 포트를 실제 운영값(18080, 8082)으로 정정 |
| 4. DB | 이름 `authentic_db`, 계정 `root/rootroot`, `DemoUserBootstrap` 시드 안내 |
| 5-1. Backend env | PowerShell/bash 양쪽 명령 |
| 5-2. AI env | `AI_PHOTO_ROOT` 필수 사용법 신설 |
| 5-3. Mobile | `settings.ts` 자동 호스트 해석 동작 명시 |
| 6-1~6-3 실행 | Windows PowerShell 명령, 백엔드 정상 부팅 로그 예시, 로그인 smoke test curl |
| 6-4 데모 계정 (신설) | admin/1234 ADMIN, user/1234 USER + 진입 화면 매핑 |
| 6-5 방화벽 (신설) | 8082 / 18080 인바운드 룰 명령 |
| 9. FAQ | 6개 항목 추가 |

---

## 7. 검증 결과

### 백엔드
```powershell
# 로그인 성공
POST /api/v1/auth/login {"username":"admin","password":"1234"}
→ 200 {"token":"eyJ1c2VySWQ...","userId":"admin","role":"ADMIN","name":"관리자"}

# 로그인 실패
POST /api/v1/auth/login {"username":"admin","password":"wrong"}
→ 401 {"errorCode":"INVALID_CREDENTIALS","correlationId":"..."}

# 관리자 overview
GET /api/v1/admin/overview?window=LAST_30D + X-User-Id:admin
→ 200, users.totalUsers=2, rooms.totalSpaces=17, rooms.statusDistribution={ANALYZED:10,FAILED:7,PENDING_ANALYSIS:0}
```

### 부팅 로그
```
DemoUserBootstrap : demo user inserted userId=admin role=ADMIN
DemoUserBootstrap : demo user inserted userId=user  role=USER
Tomcat started on port 18080
```

### 모바일
- `tsc --noEmit` : 에러 0
- `jest` : 94/94 통과 (HomeScreen 3개 테스트를 `<AuthProvider>` wrap)
- 폰 Safari: admin/1234 → 관리자 대시보드, user/1234 → 기존 Home flow, 새로고침 후에도 세션 유지

---

## 8. 누적 산출물

### 백엔드 신규 파일
- `auth/AuthController.java`
- `auth/AuthService.java`
- `auth/AuthErrorCode.java` / `AuthException.java` / `AuthExceptionAdvice.java`
- `auth/DemoUserBootstrap.java`
- `auth/dto/LoginRequest.java` / `auth/dto/LoginResponse.java`

### 모바일 신규 파일
- `src/auth/AuthContext.tsx` / `src/auth/storage.ts`
- `src/api/auth.ts` / `src/api/admin.ts`
- `src/screens/LoginScreen.tsx`
- `src/screens/AdminDashboardScreen.tsx`

### 모바일 수정 파일
- `App.tsx` — AuthProvider wrap + 조건부 Stack.Screen
- `src/settings.ts` — userId mutable + web 호스트 자동 해석
- `src/components/Button.tsx` — `labelStyle` prop
- `src/screens/HomeScreen.tsx` — 사용자 이름 표시 + 로그아웃 버튼
- `src/screens/RecommendationScreen.tsx` — 카드 버튼 폰트 축소 + 위시리스트 진입 버튼
- `__tests__/HomeScreen.test.tsx` — `<AuthProvider>` wrap

### 문서
- `README.md` 현행화 (DB명/계정/포트, 데모 계정, 방화벽, FAQ)
- `report/lab-journal-2026-05-19.md` (본 문서)

---

## 9. 다음 단계

| # | 우선순위 | 작업 |
|---|---|---|
| 1 | P1 | AdminDashboard 일별 추세 미니차트 (`signupsByDay`, `salesByDay`) — victory-native 또는 SVG |
| 2 | P2 | 분석 실패 진단 화면 (FAILED roomId + 오류 코드 목록), 가구 카탈로그 관리 |
| 3 | P3 | 야간 배치 placeholder를 `POST /api/v1/admin/similar-products/refresh` 실엔드포인트 연결, CSV export |
| 4 | 보안 | 진짜 JWT 도입 — 응답 envelope 유지, 토큰만 RS256/HS256으로 교체 (프론트 수정 0) |
| 5 | UX | 데모 자격증명 힌트는 실서비스 단계에서 LoginScreen에서 제거 |
| 6 | 플랫폼 | 네이티브 빌드에서 세션 영속화 필요 시 AsyncStorage 도입 (현재 메모리 fallback) |

---

## 10. 부록 — 동일 환경 빠른 복원

```powershell
# AI
$env:AI_PHOTO_ROOT="C:\Users\82104\Documents\GitHub\AuthenticSelf_Poroject_v2\src\backend\var\object-storage"
cd src/ai
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001 --host 0.0.0.0

# Backend
$env:DB_URL="jdbc:mysql://localhost:3306/authentic_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
$env:DB_USER="root"; $env:DB_PASSWORD="rootroot"
cd src/backend
.\gradlew.bat bootRun --no-daemon --args="--server.port=18080"

# Mobile
cd src/mobile
node node_modules\expo\bin\cli start --port 8082
```

폰 접속: 같은 Wi-Fi → `http://<PC-LAN-IP>:8082` Safari/Chrome. 로그인:
`admin/1234` (관리자 대시보드) 또는 `user/1234` (일반 사용자 홈).
