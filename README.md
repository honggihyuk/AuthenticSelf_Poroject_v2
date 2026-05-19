# AuthenticSelf

공간 분석 및 가구 추천 서비스 — React Native(Expo) 모바일, Spring Boot 백엔드, Python(FastAPI) AI 서비스, MySQL DB로 구성된 멀티 모듈 프로젝트.

전체 설계와 작업 흐름은 `CLAUDE.md` 와 `artifacts/<task-id>/` 산출물 참조.

---

## 1. 프로젝트 구성

| 모듈 | 경로 | 스택 | 기본 포트 |
|---|---|---|---|
| Backend API | `src/backend` | Java 17, Spring Boot 3.2, Gradle, Flyway, JPA | `18080` |
| AI Service | `src/ai` | Python 3.11+, FastAPI, OpenCV, NumPy, Pillow | `8001` |
| Mobile App | `src/mobile` | React Native 0.73, Expo SDK 50, TypeScript | `8082` (Expo/Metro) |
| Database | (외부) | MySQL 8.x | `3306` |

> Windows + WSL/Hyper-V 환경에서는 호스트 네트워킹 서비스가 8080/8081/8090 을 점유하는 경우가 있어 기본 포트를 위와 같이 올려 잡았습니다.

---

## 2. 사전 요구사항 (Local PC)

운영체제는 macOS / Linux / Windows(WSL2 권장) 모두 지원합니다. 아래 도구가 PATH에 있어야 합니다.

### 필수
- **Java 17** (Temurin / OpenJDK 17). `java -version` 으로 확인.
- **Python 3.11 이상**. `python3 --version`.
- **Node.js 18 LTS 이상** + npm. `node -v`, `npm -v`.
- **MySQL 8.x** (로컬 설치 또는 Docker 컨테이너).
- **Git**.

### 권장
- **Docker / Docker Desktop** — MySQL 컨테이너 실행 및 백엔드 통합 테스트(Testcontainers)에 필요.
- **Expo Go 앱** (Android/iOS) — 실기기에서 모바일 앱 미리보기.
- **Android Studio / Xcode** — 시뮬레이터 / 에뮬레이터로 모바일 실행 시.

### Windows 사용자 안내
- WSL2(Ubuntu 22.04 권장) 환경에서 백엔드/AI 서비스를 실행하면 Docker, 경로 분리자, 줄바꿈 이슈가 가장 적습니다.
- Expo는 Windows 네이티브 또는 WSL 어디서든 가능하나, USB 연결 디버깅은 Windows 측에서 더 안정적입니다.

---

## 3. 저장소 클론

```bash
git clone https://github.com/honggihyuk/AuthenticSelf_Poroject_v2.git
cd AuthenticSelf_Poroject_v2
```

---

## 4. 데이터베이스 준비 (MySQL 8)

### 옵션 A — Docker 로 빠르게 실행 (권장)

```bash
docker run -d --name authenticself-mysql \
  -e MYSQL_ROOT_PASSWORD=rootroot \
  -e MYSQL_DATABASE=authentic_db \
  -p 3306:3306 \
  mysql:8.0
```

### 옵션 B — 로컬 설치한 MySQL 사용

```sql
CREATE DATABASE authentic_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
-- 데모 단계에서는 root 계정으로 직접 접속합니다. 별도 user 생성은 선택.
```

DB 이름은 **`authentic_db`** (단수형), 데모용 자격증명은 **`root` / `rootroot`** 으로 통일되어 있습니다. 변경하려면 5-1 환경변수에서 함께 바꿔주세요.

스키마 마이그레이션은 백엔드가 기동될 때 **Flyway** 가 `src/backend/src/main/resources/db/migration/V*__*.sql` 을 자동 적용하므로 별도 DDL 작업은 필요 없습니다. 부팅 시 `DemoUserBootstrap` 이 `admin` / `user` 두 행을 `users` 테이블에 자동 시드합니다 (로그인용).

---

## 5. 환경 변수

각 모듈 디렉터리에 `.env` 파일을 만들거나 셸에서 export 하세요. 백엔드는 표준 Spring 환경변수 체인을 사용하므로 `application.yml` 의 `${VAR}` 키 어디든 동일한 방식으로 override 가능합니다.

### 5-1. Backend — 셸 환경변수

PowerShell (Windows):
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/authentic_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
$env:DB_USER="root"
$env:DB_PASSWORD="rootroot"
```

bash (macOS/Linux/WSL):
```bash
export DB_URL="jdbc:mysql://localhost:3306/authentic_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
export DB_USER="root"
export DB_PASSWORD="rootroot"
```

AI 서비스 / 업로드 저장 경로는 `application.yml` 기본값(`http://localhost:8001`, `./var/object-storage`)이 그대로 동작하므로 별도 override 가 필요 없습니다. 다른 호스트나 경로로 띄울 경우에만 `APP_AI_BASE_URL`, `APP_STORAGE_LOCAL_ROOT` 등을 export 하세요.

> ⚠️ `characterEncoding=UTF-8` 표기 필수. `utf8mb4` 는 JDBC 가 거부합니다.

### 5-2. AI Service — `AI_PHOTO_ROOT` (필수)

AI 서비스는 백엔드가 저장한 사진의 경로를 검증하기 위해 저장소 루트를 알아야 합니다. **백엔드의 `APP_STORAGE_LOCAL_ROOT` 와 동일한 절대 경로** 를 `AI_PHOTO_ROOT` 로 지정해야 합니다.

PowerShell:
```powershell
$env:AI_PHOTO_ROOT="C:\path\to\repo\src\backend\var\object-storage"
```

bash:
```bash
export AI_PHOTO_ROOT="$(pwd)/src/backend/var/object-storage"
```

이 값이 잘못되면 분석 단계에서 `IMAGE_NOT_FOUND` 로 실패하며 모바일은 "분석 실패" 화면을 보게 됩니다.

### 5-3. Mobile — `src/mobile/app.json` 과 자동 호스트 해석

`extra.apiBaseUrl` 의 기본값은 `http://localhost:18080` 입니다. 다만 **웹에서 실기기 Safari/Chrome 으로 접속할 때는 자동 해석이 동작합니다** — `settings.ts` 가 `window.location.hostname` 을 읽어 동일 호스트의 18080 포트로 API 호출을 라우팅합니다. 따라서 폰 브라우저로 `http://<PC-LAN-IP>:8082` 페이지를 열면 API 도 `http://<PC-LAN-IP>:18080` 으로 자동 전송됩니다.

Expo Go 등 네이티브 클라이언트로 띄울 때는 `app.json` 의 `extra.apiBaseUrl` 을 PC LAN IP(예: `http://192.168.0.10:18080`)로 수정하세요.

---

## 6. 실행 방법

총 3개 프로세스(백엔드 / AI / 모바일)를 별도 터미널에서 띄웁니다. 데이터베이스가 먼저 떠 있어야 합니다.

### 6-1. AI Service 실행

최초 1회 — 가상환경 + 의존성:
```bash
cd src/ai
python -m venv .venv
.venv\Scripts\activate              # macOS/Linux/WSL: source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
```

기동 — **`AI_PHOTO_ROOT` 환경변수 설정 필수**:

PowerShell:
```powershell
cd src\ai
$env:AI_PHOTO_ROOT="C:\path\to\repo\src\backend\var\object-storage"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001 --host 0.0.0.0
```

bash:
```bash
cd src/ai
export AI_PHOTO_ROOT="$(pwd)/../backend/var/object-storage"
.venv/bin/python -m uvicorn app.main:app --port 8001 --host 0.0.0.0
```

기동 후 헬스체크:
```bash
curl http://localhost:8001/health
```

### 6-2. Backend 실행

PowerShell (Windows):
```powershell
cd src\backend
$env:DB_URL="jdbc:mysql://localhost:3306/authentic_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
$env:DB_USER="root"; $env:DB_PASSWORD="rootroot"
.\gradlew.bat bootRun --no-daemon --args="--server.port=18080"
```

bash (macOS/Linux/WSL):
```bash
cd src/backend
export DB_URL="jdbc:mysql://localhost:3306/authentic_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
export DB_USER="root"; export DB_PASSWORD="rootroot"
./gradlew bootRun --no-daemon --args="--server.port=18080"
```

기동 로그에 다음이 보이면 정상:
```
LocalFileSystemObjectStorageService ready at .../src/backend/var/object-storage
Tomcat started on port 18080
DemoUserBootstrap : demo user inserted userId=admin role=ADMIN
DemoUserBootstrap : demo user inserted userId=user  role=USER
```

로그인 smoke test:
```bash
curl -X POST http://localhost:18080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"1234"}'
```

### 6-3. Mobile 실행

최초 1회:
```bash
cd src/mobile
npm install
```

기동 (Windows — `npm start` / `npx expo` 가 깨지는 환경 우회):
```powershell
cd src\mobile
node node_modules\expo\bin\cli start --port 8082
```

bash (macOS/Linux/WSL):
```bash
cd src/mobile
node node_modules/expo/bin/cli start --port 8082
```

- 실기기 Safari/Chrome: `http://<PC-LAN-IP>:8082` 직접 접속 (Expo Go 버전 이슈가 있을 때의 우회 경로).
- Expo Go 앱: 같은 Wi-Fi 망에서 `exp://<PC-LAN-IP>:8082` 또는 QR 스캔.
- Android 에뮬레이터: `npm run android`.
- iOS 시뮬레이터(macOS 한정): `npm run ios`.
- 데스크탑 브라우저: `npm run web`.

### 6-4. 데모 로그인 계정

부팅 시 자동 시드되는 두 계정으로 화면이 분기됩니다.

| 아이디 | 비밀번호 | 역할 | 진입 화면 |
|---|---|---|---|
| `admin` | `1234` | ADMIN | 관리자 대시보드 (`AdminDashboardScreen`) |
| `user`  | `1234` | USER  | 일반 사용자 홈 (`HomeScreen`) |

세션은 웹의 경우 `localStorage` 에 저장되어 새로고침 후에도 유지됩니다. 로그아웃 버튼은 각 진입 화면 하단에 있습니다.

### 6-5. Windows 방화벽 (실기기 접속 시)

PC 의 LAN IP 로 폰에서 접속하려면 8082(Metro) / 18080(Backend) 인바운드를 열어야 합니다. 관리자 PowerShell:
```powershell
New-NetFirewallRule -DisplayName "AuthenticSelf Metro 8082"   -Direction Inbound -Protocol TCP -LocalPort 8082  -Action Allow -Profile Private,Domain
New-NetFirewallRule -DisplayName "AuthenticSelf Backend 18080" -Direction Inbound -Protocol TCP -LocalPort 18080 -Action Allow -Profile Private,Domain
```
공용 Wi-Fi 에서는 작업 종료 후 `Remove-NetFirewallRule -DisplayName "..."` 으로 닫는 걸 권장합니다.

---

## 7. 테스트 실행

### Backend
```bash
cd src/backend
./gradlew test                              # 전체 테스트 (Docker 필요 — Testcontainers)
./gradlew test -PskipTestcontainers=true    # Docker 없는 환경
```

### AI Service
```bash
cd src/ai
source .venv/bin/activate
pytest
```

### Mobile
```bash
cd src/mobile
npm test
npm run typecheck
```

---

## 8. 디렉터리 구조

```
AuthenticSelf_Poroject_v2/
├── CLAUDE.md                       # 에이전트 반복 루프 정의 (오케스트레이터 규약)
├── README.md                       # 본 문서
├── src/
│   ├── backend/                    # Spring Boot API
│   ├── ai/                         # FastAPI 분석/추천 서비스
│   └── mobile/                     # React Native (Expo) 앱
├── artifacts/                      # 작업별 spec / design / verification 산출물
│   └── <task-id>/
│       ├── spec.md
│       ├── acceptance_criteria.json
│       ├── design.md
│       ├── verification.md
│       └── viz/                    # 다이어그램, UI 목업
├── var/                            # 보조 스크립트 + 업로드 저장 루트
│   ├── object-storage/             # 백엔드 로컬 업로드 디폴트 경로
│   └── run_tests.sh
└── 공간 분석 및 가구 추천 프로젝트 구조화.pdf   # PRD
```

---

## 9. 자주 발생하는 문제

| 증상 | 원인 / 해결 |
|---|---|
| 백엔드 부팅 시 `Flyway migration failed` | DB가 비어있지 않을 때 발생. `DROP DATABASE authentic_db; CREATE DATABASE authentic_db ...` 후 재시도 |
| `Communications link failure` | DB 호스트/포트 불일치. `DB_URL` 의 호스트/포트 확인 |
| 백엔드는 떴는데 모바일에서 "분석 실패" 화면이 뜸 | AI 서비스가 `AI_PHOTO_ROOT` 없이 떠 있는 경우. 5-2 참고해서 환경변수와 함께 재기동 |
| `./gradlew: Permission denied` (Linux/macOS) | `chmod +x ./gradlew` |
| `port 8001/18080/8082 already in use` | 다른 프로세스가 사용 중. Windows: `Get-NetTCPConnection -LocalPort <port>` 로 PID 확인 후 종료. macOS/Linux: `lsof -i :<port>` |
| Windows 에서 `Tomcat initialized` 이후 멈춤 / `bind` 에러 | 기본 8080/8081/8090 을 `wslrelay.exe` / `svchost` 가 점유. 18080 같은 상위 포트로 변경 |
| Windows 에서 `npm start` / `npx expo` 가 "not recognized" | `node_modules/.bin/expo` 가 unix shim 만 제공. `node node_modules\expo\bin\cli start --port 8082` 직접 호출 |
| 폰 Safari 에서 `localhost` API 호출이 실패 | 폰 입장의 `localhost` = 폰 자신. 5-3 의 자동 호스트 해석을 사용하거나 `app.json` 을 LAN IP 로 수정 |
| Expo Go 버전 비호환 | Safari/Chrome 으로 `http://<PC-LAN-IP>:8082` 직접 접속 (웹 번들 자동 동작) |
| 로그인 화면에서 "아이디 또는 비밀번호가 올바르지 않습니다" | 데모 자격증명은 6-4 표 참조 (`admin/1234`, `user/1234` 만 동작) |
| `로그인은 됐는데 관리자 API 가 USER_NOT_FOUND` | `DemoUserBootstrap` 이 시드를 실패했을 가능성. 백엔드 로그에 `demo user inserted` 라인이 있는지 확인 |
| Testcontainers 가 Docker 를 못 찾음 | Docker Desktop 실행 여부, WSL 통합 활성화 확인. 또는 `-PskipTestcontainers=true` |

---

## 10. 참고

- 작업 큐와 우선순위, 에이전트 역할은 `CLAUDE.md` 참조.
- PRD 원문은 루트의 `공간 분석 및 가구 추천 프로젝트 구조화.pdf`.
- 각 use-case 의 API 스펙: `artifacts/<task-id>/api_contract.yaml`.
