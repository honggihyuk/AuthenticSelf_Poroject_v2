# AuthenticSelf

공간 분석 및 가구 추천 서비스 — React Native(Expo) 모바일, Spring Boot 백엔드, Python(FastAPI) AI 서비스, MySQL DB로 구성된 멀티 모듈 프로젝트.

전체 설계와 작업 흐름은 `CLAUDE.md` 와 `artifacts/<task-id>/` 산출물 참조.

---

## 1. 프로젝트 구성

| 모듈 | 경로 | 스택 | 기본 포트 |
|---|---|---|---|
| Backend API | `src/backend` | Java 17, Spring Boot 3.2, Gradle, Flyway, JPA | `8080` |
| AI Service | `src/ai` | Python 3.11+, FastAPI, OpenCV, NumPy, Pillow | `8001` |
| Mobile App | `src/mobile` | React Native 0.73, Expo SDK 50, TypeScript | Expo dev server |
| Database | (외부) | MySQL 8.x | `3306` |

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
  -e MYSQL_ROOT_PASSWORD=rootpw \
  -e MYSQL_DATABASE=authenticself \
  -e MYSQL_USER=app \
  -e MYSQL_PASSWORD=apppw \
  -p 3306:3306 \
  mysql:8.0
```

### 옵션 B — 로컬 설치한 MySQL 사용

```sql
CREATE DATABASE authenticself
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
CREATE USER 'app'@'%' IDENTIFIED BY 'apppw';
GRANT ALL PRIVILEGES ON authenticself.* TO 'app'@'%';
FLUSH PRIVILEGES;
```

스키마 마이그레이션은 백엔드가 기동될 때 **Flyway** 가 `src/backend/src/main/resources/db/migration/V*__*.sql` 을 자동 적용하므로 별도 DDL 작업은 필요 없습니다.

---

## 5. 환경 변수

각 모듈 디렉터리에 `.env` 파일을 만들거나 셸에서 export 하세요. 백엔드는 표준 Spring 환경변수 체인을 사용하므로 `application.yml` 의 `${VAR}` 키 어디든 동일한 방식으로 override 가능합니다.

### 5-1. Backend — `src/backend/.env` (또는 셸 export)

```bash
# DB 접속
export DB_URL="jdbc:mysql://localhost:3306/authenticself?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4"
export DB_USER="app"
export DB_PASSWORD="apppw"

# AI 서비스 위치 (포트 8001 로컬 가정)
export APP_AI_BASE_URL="http://localhost:8001"
export APP_AI_SPACE_BASE_URL="http://localhost:8001"
export APP_AI_STYLE_BASE_URL="http://localhost:8001"
export APP_AI_RECOMMEND_BASE_URL="http://localhost:8001"

# 업로드 파일 저장 경로 (디폴트: ./var/object-storage)
export APP_STORAGE_LOCAL_ROOT="./var/object-storage"
```

### 5-2. AI Service — `src/ai/.env` (옵션)

기본 동작에 필요한 환경변수는 없습니다. 포트 변경은 uvicorn 인자(`--port`)로 지정.

### 5-3. Mobile — `src/mobile/app.json`

`extra.apiBaseUrl` 값이 백엔드 주소입니다. 기본 `http://localhost:8080` 로 설정되어 있으며 실기기 테스트 시에는 PC 의 LAN IP(예: `http://192.168.0.10:8080`)로 바꿔야 합니다.

---

## 6. 실행 방법

총 3개 프로세스(백엔드 / AI / 모바일)를 별도 터미널에서 띄웁니다. 데이터베이스가 먼저 떠 있어야 합니다.

### 6-1. AI Service 실행

```bash
cd src/ai
python3 -m venv .venv
source .venv/bin/activate           # Windows: .venv\Scripts\activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

기동 후 헬스체크:

```bash
curl http://localhost:8001/health
```

### 6-2. Backend 실행

```bash
cd src/backend
# 위 5-1 환경변수가 export 된 상태여야 함
./gradlew bootRun                    # Windows: gradlew.bat bootRun
```

기동 후 헬스체크:

```bash
curl http://localhost:8080/actuator/health  # 또는 정의된 헬스 엔드포인트
```

### 6-3. Mobile 실행

```bash
cd src/mobile
npm install
npm start                            # Expo Dev Server 시작
```

- Expo CLI 가 띄워주는 QR 을 **Expo Go** 앱으로 스캔하면 실기기에서 즉시 확인.
- Android 에뮬레이터: 새 터미널에서 `npm run android`.
- iOS 시뮬레이터(macOS 한정): `npm run ios`.
- 웹 브라우저: `npm run web`.

> 실기기 테스트 시 휴대폰과 PC 가 같은 Wi-Fi 망에 있어야 하며, `app.json` 의 `extra.apiBaseUrl` 을 PC LAN IP로 바꿔야 합니다.

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
| 백엔드 부팅 시 `Flyway migration failed` | DB가 비어있지 않을 때 발생. `DROP DATABASE authenticself; CREATE DATABASE ...` 후 재시도 |
| `Communications link failure` | DB 호스트/포트 불일치. `DB_URL` 의 호스트/포트 확인 |
| `./gradlew: Permission denied` (Linux/macOS) | `chmod +x ./gradlew` |
| `port 8001 already in use` | 다른 프로세스가 사용 중. `lsof -i :8001` 로 PID 확인 후 종료 |
| 모바일에서 백엔드 호출 실패 | `app.json` 의 `apiBaseUrl` 이 `localhost` 로 되어 있으면 실기기에서 닿지 않음. PC LAN IP 로 변경 |
| Testcontainers 가 Docker 를 못 찾음 | Docker Desktop 실행 여부, WSL 통합 활성화 확인. 또는 `-PskipTestcontainers=true` |
| Windows 에서 Gradle 빌드 깨짐 | WSL2 Ubuntu 에서 실행 권장 |

---

## 10. 참고

- 작업 큐와 우선순위, 에이전트 역할은 `CLAUDE.md` 참조.
- PRD 원문은 루트의 `공간 분석 및 가구 추천 프로젝트 구조화.pdf`.
- 각 use-case 의 API 스펙: `artifacts/<task-id>/api_contract.yaml`.
