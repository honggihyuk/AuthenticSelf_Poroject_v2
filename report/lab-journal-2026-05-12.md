# 실습 일지 — 2026-05-12

AuthenticSelf 프로젝트의 하루 작업 기록. 환경 기동 → 분석 실패 진단 → 실 ML 통합 →
모바일 시각화 → Phase A(큐레이션 카탈로그) → Phase B(Naver 쇼핑 확장) 까지의 변경
이력, 부딪힌 이슈, 해결책을 시간 순으로 정리한다.

---

## 1. 환경 기동 (모든 서버 실행)

세 서비스를 동시에 띄우는 과정에서 Windows + WSL/Hyper-V 환경의 포트 이슈 다수.

| 서비스 | 최종 포트 | 1차 시도 결과 |
|---|---|---|
| AI (FastAPI) | **8001** | OK |
| Backend (Spring Boot) | **18080** | 8080은 `wslrelay.exe`, 8090은 `svchost`(Hyper-V Host Networking)가 동적 점유 → 18080으로 우회 |
| Mobile (Expo/Metro) | **8082** | 8081은 `svchost` 점유. `npm start`, `npx expo start` 둘 다 “expo not recognized” — `node_modules/.bin/expo`가 unix 쉘 shim만 존재. `node node_modules\expo\bin\cli start` 직접 호출로 우회 |

**Backend 시행착오**
- `application.yml`의 예시 `DB_URL`이 `characterEncoding=utf8mb4`로 적혀 있는데, Java의
  `String.lookupCharset`가 이를 거부 → `UnsupportedEncodingException`. JDBC client
  charset은 `UTF-8`이 정답 (MySQL 컬럼 charset과 다름).
- 자격증명 자동 추측 실패 — 실제 값 `root` / `rootroot`, DB명 `authentic_db`.

**메모리에 저장**: 다음 세션에서 동일 시행착오를 피하도록
`.claude/.../memory/dev_servers.md` 로 기동 명령과 포트 우회 노하우 영구 기록.

---

## 2. 사진 업로드 401 → u_dev 시드

- 증상: `POST /api/v1/spaces/photo` → 401 Unauthorized
- 원인: `PhotoUploadService`가 `users.existsById(userId)`로 검증, Flyway가 막 마이그레이션한 신규 DB라
  users 테이블이 비어 있어 `u_dev`(Mobile `settings.userId`) 미존재 → `UNKNOWN_USER` → 401
- 조치: `INSERT INTO users (user_id, name, email, role) VALUES ('u_dev', 'Dev User', 'dev@authenticself.local', 'USER')`

---

## 3. 분석 실패 진단 → 실 ML 통합

### 3-1. 경로 불일치 해결

- 증상: `/analyze/space` 422 `IMAGE_NOT_FOUND` (`path escape rejected`)
- 원인: Backend는 `src/backend/var/object-storage/`에 저장, AI는 작업 디렉토리 기준
  `src/var/object-storage`를 보고 있음 → 서로 다른 폴더
- 조치: AI 서비스를 `AI_PHOTO_ROOT=C:\...\src\backend\var\object-storage`로 재기동 → placeholder 결과로 200 OK 회복

### 3-2. ML 의존성 설치

CPU 전용:
- `torch 2.11.0+cpu`, `torchvision 0.26.0+cpu` (PyTorch CPU index)
- `ultralytics 8.4.48` (YOLOv8)
- `timm 1.0.27` (MiDaS 백본 의존)
- `opencv-python`이 `opencv-python-headless`를 덮어쓰며 `cv2.pyd` 파일 잠금 → 설치 실패 → AI 프로세스
  중지 후 재설치로 해결.

### 3-3. 분석기 코드 교체

| 파일 | 변경 |
|---|---|
| `src/ai/app/analyzers/yolo_detector.py` | **신규** — Ultralytics YOLOv8n 래퍼, lazy singleton |
| `src/ai/app/analyzers/depth.py` | **신규** — MiDaS_small 래퍼 |
| `src/ai/app/analyzers/dimensions.py` | placeholder(SHA-256) → YOLO 가구 폭 ref로 m/px 스케일 + MiDaS depth 비율로 lengthM |
| `src/ai/app/analyzers/style.py` | placeholder(SHA-256) → HSV 히스토그램 + 5개 룰 (SCANDINAVIAN/MODERN/CLASSIC/SIMPLE/INDUSTRIAL) |
| `src/ai/app/schemas.py` | `DetectedObject`, `ObjectsAnalyzeRequest`, `ObjectsAnalysisResponse` 추가 |
| `src/ai/app/routes/objects.py` | **신규** — `POST /analyze/objects` |
| `src/ai/app/main.py` | 새 router 등록 |

### 3-4. torch.hub 신뢰 프롬프트 차단

- 증상: 첫 MiDaS 로드 시 `intel-isl/MiDaS`가 내부적으로 `rwightman/gen-efficientnet-pytorch`를
  hub.load — `trust_repo=True` 인자를 중첩 호출에 전파하지 않아 `y/N` stdin 프롬프트로 uvicorn이 hang
- 조치 (이중 안전망):
  1. `~/.cache/torch/hub/trusted_list`에 두 repo + `isl-org_MiDaS` 사전 등록
  2. `torch.hub._validate_not_a_forked_repo` + `_check_repo_is_trusted` monkey-patch
  3. `builtins.input` 도 `lambda *_: "y"`로 stub

### 3-5. 검증

```
/analyze/objects  cold 23s / warm ~2s   chair x3, laptop, bowl x2 검출
/analyze/space    cold 68s / warm 1.7s  2.64 x 5.29 x 2.4 m, conf 0.57
/analyze/style    ~50ms                 SCANDINAVIAN 0.64
```

---

## 4. 모바일 YOLO bbox 오버레이

Mobile은 Backend(18080)와만 통신 → Backend에 AI 프록시 추가.

| 영역 | 파일 |
|---|---|
| Backend | `ai/dto/ObjectsAnalysisRequest.java`, `ObjectsAnalysisResponse.java` |
| Backend | `ai/ObjectsAnalysisClient.java` (`StyleAnalysisClient` 패턴 그대로) |
| Backend | `space/SpaceController.java` — `GET /api/v1/spaces/{roomId}/objects` |
| Mobile | `src/api/objects.ts` — `getRoomObjects()` |
| Mobile | `src/screens/ObjectsScreen.tsx` — 색상별 bbox + 검출 리스트 + 신뢰도 + 픽셀 좌표 |
| Mobile | `App.tsx` — `Objects` route + `photoUri?` 파라미터 |
| Mobile | `UploadScreen` / `AnalyzingScreen` — nav로 `photoUri` 전달 |
| Mobile | `StyleSelectionScreen` — “객체 검출 보기” 버튼 |

검증: `Home → Upload → Analyzing → StyleSelection → ObjectsScreen` 흐름에서 원본 사진 위에
색상 박스 오버레이가 정확한 좌표로 표시됨.

---

## 5. Phase A — 큐레이션 카탈로그 + AR 코어 (V8 ~ V9)

### 5-1. 첫 슬라이스 (베드 1 + 의자 1)

| 단계 | 파일/명령 |
|---|---|
| Flyway V8 | `db/migration/V8__add_furniture_model_url.sql` — `model_url VARCHAR(512) NULL` |
| Static 서빙 | `config/StaticAssetsWebConfig.java` — `/static/furniture/**` 1y immutable 캐시 |
| 도메인 | `domain/Furniture.java` — `modelUrl` 컬럼 |
| API DTO | `ai/dto/RecommendationItem.java` — `modelUrl` 필드 + `withModelUrl()` |
| 컨트롤러 | `space/SpaceController.java` — Python 응답 후 DB lookup으로 modelUrl 주입 (Python 컨트랙트 무변경) |
| Mobile | `api/spaces.ts` — `modelUrl?`, `ARPlacementScreen` — `item.modelUrl` 우선 / `MODEL_URL_BY_TYPE` fallback |
| Mobile | `RecommendationScreen` — 상대 URL 자동 보정 + “비슷한 실제 상품” 가로 스크롤 placeholder (Phase B anchor) |

E2E 결과: `f_bed_001`, `f_chair_001` 두 행이 `/static/furniture/...` 경로로 노출.

### 5-2. 28건 전체 + 자산 워크플로 (V9)

| 산출물 | 내용 |
|---|---|
| `var/static/furniture/<id>.jpg` x28 | Pillow로 색상 swatch + 치수 + 라벨 placeholder JPG 생성 |
| `var/static/furniture/<id>.glb` x28 | type-matched 빈 GLB 사본 (Sketchfab CC0로 교체 대기) |
| `db/migration/V9__seed_curated_assets.sql` | 28건 UPDATE + 자산 swap 체크리스트 헤더 주석 |
| `raw_models/README.md` + `.gitignore` | Sketchfab CC0 → compress → static dir 워크플로 |

검증: `image_url LIKE /static/%` = **28/28**, `model_url LIKE /static/%` = **28/28**,
4개 카테고리 jpg + glb 전부 HTTP 200.

### 5-3. Quick Win — 404 핸들러

- 증상: `/static/furniture/nope.jpg` → 500 `STORAGE_PERSIST_FAILED`. `UploadExceptionAdvice`의
  `@ExceptionHandler(Exception.class)` catch-all이 `NoResourceFoundException`까지 잡아 5xx로 매핑.
- 조치: `@ExceptionHandler(NoResourceFoundException.class)` 추가 → `{errorCode:"NOT_FOUND", message:"리소스를 찾을 수 없습니다."}` 본문과 함께 404 반환.
- 검증: 정상 경로 200 유지, 누락 경로 404로 회귀 없음.

### 5-4. GLB 압축 워크플로

`scripts/compress-glb.sh` — `npx --yes gltf-pipeline@latest` 자동 fetch + Draco 양자화
(position 14 / normal 10 / texcoord 12 bit). 멱등성, 디렉토리 일괄. 작은 입력은 Draco 오버헤드로
오히려 늘어남 — 실 Sketchfab 50–200 MB 자산에 적용 시 80~95% 감소 기대.

---

## 6. Phase B — Naver 쇼핑 확장 (V10)

CLIP은 후속 iteration으로 보류, 우선 Naver 결과 수집·저장·표시에 집중.

### 6-1. 스키마 + 클라이언트 + 배치

| 영역 | 파일 |
|---|---|
| DB | `V10__furniture_similar_cache.sql` — `(furniture_id, source, external_id)` PK + 랭킹 인덱스 + `utf8mb4_unicode_ci` 통일 |
| 도메인 | `domain/FurnitureSimilarCache.java` + `IdClass` PK |
| 레포지토리 | `repository/FurnitureSimilarCacheRepository.java` — `findByFurnitureIdRanked`, `deleteByFurnitureIdAndSource` |
| 외부 API | `external/NaverShoppingClient.java` — `isConfigured()` 가드 + DTO |
| 배치 | `batch/SimilarProductsBatch.java` — `@Scheduled(cron="0 0 3 * * *")` 매일 03:00, env 토글, rate-limit |
| 영속화 | `batch/SimilarProductsPersistence.java` — 분리된 빈으로 `@Transactional(REQUIRES_NEW)` 보장 |
| 공개 API | `controller/FurnitureSimilarController.java` — `GET /similar` + `POST /admin/similar-products/refresh` |
| Mobile API | `api/similar.ts` |
| Mobile UI | `RecommendationScreen.tsx` — 카드 마운트 시 fetch, 빈 상태 → “준비 중”, 결과 있으면 가로 스크롤 Naver 카드 |

### 6-2. 자격증명 핸들링

- `src/backend/src/main/resources/application-local.yml.example` (placeholder, 커밋 가능)
- `application-local.yml` (실 키, `.gitignore` 등재)
- `SPRING_PROFILES_ACTIVE=local` 환경변수로 활성화

### 6-3. 부딪힌 버그 두 가지

1. **`InvalidDataAccessApiUsageException: Executing an update/delete query`**
   - 원인: `runOnce() → refreshOne()`이 같은 클래스 내부 호출이라 Spring AOP 프록시 미적용 → `@Modifying` DELETE 쿼리가 트랜잭션 컨텍스트 밖에서 실행
   - 조치: persistence 로직을 `SimilarProductsPersistence` 별도 빈으로 분리, `@Transactional(REQUIRES_NEW)` 적용. 외부 빈 호출이라 프록시 가동.

2. **한글 query 이중 인코딩**
   - 증상: 첫 batch에서 28행 중 26행이 `returned=0`. 같은 키워드를 cURL로 직접 호출하면 결과 정상.
   - 원인: `UriComponentsBuilder.encode().toUriString()`로 이미 percent-encoded된 문자열을 `RestClient.uri(String)`에 넘기면, RestClient가 URI 템플릿으로 간주하고 `%`를 `%25`로 재인코딩 → Naver 매칭 0건.
   - 조치: `restClient.get().uri(uriBuilder -> uriBuilder.path(...).queryParam("query", query)...)` 람다 빌더 패턴. 인코딩이 한 번만 발생.

### 6-4. 검증

- 자격증명 적용 후 batch 재실행: `scanned=28, updated=28, failed=0, inserted=280`
- 카테고리별 균등: bed 70 / chair 70 / desk 70 / lighting 70 (각 행 top-10)
- `GET /api/v1/furniture/f_bed_001/similar` → 10건, Naver 실 상품(가격·몰명·상세 링크 포함)
- 약 14초 (행간 250 ms rate-limit + Naver 라운드트립 28회)
- Mobile RecommendationScreen에서 placeholder “준비 중” → 실 상품 가로 스크롤로 자동 교체, 탭 시 Naver 링크 외부 브라우저로 이동

---

## 7. 운영 메모

### 다음 세션에서 동일 환경 빠르게 복원

```powershell
# AI
$env:AI_PHOTO_ROOT="C:\Users\82104\Documents\GitHub\AuthenticSelf_Poroject_v2\src\backend\var\object-storage"
cd src/ai
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001 --host 0.0.0.0

# Backend (Phase B 자격증명 포함)
$env:DB_URL="jdbc:mysql://localhost:3306/authentic_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
$env:DB_USER="root"; $env:DB_PASSWORD="rootroot"
$env:SPRING_PROFILES_ACTIVE="local"
cd src/backend
.\gradlew.bat bootRun --no-daemon --args="--server.port=18080"

# Mobile
cd src/mobile
node node_modules\expo\bin\cli start --port 8082
```

### 자산 swap 사이클 (코드 변경 0)

```bash
# 1. Sketchfab CC0 다운로드 → 카탈로그 id로 리네이밍
mv ~/Downloads/sofa_modern.glb  raw_models/f_chair_006.glb

# 2. 압축
./scripts/compress-glb.sh raw_models/f_chair_006.glb

# 3. 자리로 이동
mv raw_models/f_chair_006.compressed.glb  src/backend/var/static/furniture/f_chair_006.glb
mv your_render.jpg                         src/backend/var/static/furniture/f_chair_006.jpg

# DB 변경 없음 — 다음 /recommendations 호출에서 곧바로 반영
```

### Phase B 즉시 갱신 (야간 대기 없이)

```bash
curl -X POST http://localhost:18080/api/v1/admin/similar-products/refresh
```

---

## 8. 누적 산출물 정리

### 신규 / 변경된 마이그레이션
- `V8__add_furniture_model_url.sql`
- `V9__seed_curated_assets.sql`
- `V10__furniture_similar_cache.sql` (collation 정렬로 한 번 재시도)

### Backend 신규 파일
- `config/StaticAssetsWebConfig.java`
- `ai/ObjectsAnalysisClient.java` + `ai/dto/ObjectsAnalysisRequest.java` + `ai/dto/ObjectsAnalysisResponse.java`
- `domain/FurnitureSimilarCache.java`
- `repository/FurnitureSimilarCacheRepository.java`
- `external/NaverShoppingClient.java`
- `batch/SimilarProductsBatch.java` + `batch/SimilarProductsPersistence.java`
- `controller/FurnitureSimilarController.java`

### AI 서비스 신규 파일
- `app/analyzers/yolo_detector.py`
- `app/analyzers/depth.py`
- `app/routes/objects.py`

### Mobile 신규 파일
- `src/api/objects.ts`
- `src/api/similar.ts`
- `src/screens/ObjectsScreen.tsx`

### 인프라/자산
- `scripts/compress-glb.sh`
- `raw_models/` (gitignored 작업 폴더 + README)
- `src/backend/var/static/furniture/` × 28 jpg + 28 glb (placeholder)
- `application-local.yml.example` + `.gitignore` 갱신

---

## 9. 남은 일감 (우선순위)

1. **실 Sketchfab CC0 자산 점진 교체** — 코드 0, DB 0, 자산만 교체
2. **CLIP 도입** (Phase B v2) — image-to-image 유사도로 Naver 결과 재정렬, `similarity_score` 컬럼 사용
3. **모바일 폴리시** — RecommendationScreen 빈 상태 카피, 가격 단위 통일, Naver 카드 hotlink 차단 시 backend 프록시
4. **운영 관찰성** — 배치 메트릭(성공/실패/소요시간) Micrometer 노출
5. **모바일 네이티브 referrer 대응** — 현재는 web의 `referrerPolicy`만 가능, iOS/Android에서 403 발생 시 backend 프록시 캐싱 필요
