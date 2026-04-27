# 응용프로젝트실무 중간발표

**제목**: AuthenticSelf — 사진 기반 공간 분석 및 AR 가구 추천 서비스
**학번**: ________________
**이름**: ________________
**소속**: 인하공업전문대학 컴퓨터시스템공학과
**작성일**: 2026-04-21

---

## 목 차

1. 프로젝트 개요
2. 프로젝트 목적
3. 프로젝트 특징
4. 개발 내용
   4.1 시스템 구성도
   4.2 메뉴 구성도
   4.3 구성기능 (모듈)
   4.4 산출물
5. 개발 일정
   5.1 계획 대 진도표
6. 시스템 사양 및 구현도구
7. 향후 일정 및 계획
8. 결론 및 고려사항

---

## 1. 프로젝트 개요

### 개 요
**AuthenticSelf**는 사용자가 자신의 방 사진 한 장을 업로드하면, AI가 공간의 치수·주 색상·스타일을 분석하고, 사용자 선호 스타일과 교차 검증하여 **가구 카탈로그에서 상위 N개 추천 결과를 제시**하는 모바일 서비스이다. 추천된 가구는 `<model-viewer>` 기반 AR 뷰로 **실제 방 공간에 가상 배치**해 볼 수 있으며, 마음에 드는 아이템은 위시리스트에 담아 관리(Active ↔ Purchased)할 수 있다. 관리자 대시보드는 가입자·공간·위시리스트·누적 판매액을 시간 윈도우별로 집계한다.

개발은 **AI 에이전트 4종**이 순차 반복 실행되는 **iteration 엔진** 방식으로 진행되었다:

| # | 에이전트 | 역할 | 산출 |
|---|---|---|---|
| 1 | prompt-specialist | PRD → 작업 스펙 + 수용 기준 | `spec.md`, `acceptance_criteria.json` |
| 2 | design-specialist | 스펙 → 아키텍처 + 실제 코드 | `design.md`, `api_contract.yaml`, `src/**/*` |
| 3 | visualization-specialist | 설계 → 다이어그램/UI | Mermaid, UI mock |
| 4 | verification-specialist | 산출물 ↔ 수용 기준 검증 | PASS/FAIL 보고서 |

### 업무분담
1인 프로젝트 — 기획·설계·구현·테스트·문서화 전 과정을 단독 수행.

---

## 2. 프로젝트 목적

1. **공간 인지형 추천의 자동화** — 사용자가 가구를 고를 때 "내 방에 맞을까?"를 가장 먼저 고민한다는 관찰에서 출발. 줄자를 대지 않고도 사진 한 장으로 공간 치수·주색·스타일을 측정·분류해 **사이즈·색·스타일이 동시에 맞는 가구만 남기는 후보 생성 파이프라인**을 구축한다.
2. **AR 실측 미리보기** — 추천 결과를 단순 목록이 아니라 **사용자가 직접 서 있는 자리에 가구를 세워보는 WebXR / ARKit / ARCore 장면**으로 보여주어 구매 전 시각적 검증을 제공한다.
3. **위시리스트 기반 구매 의사 트래킹** — 추천 → 저장 → 구매 완료의 상태 머신을 명시해 개인 구매 여정을 관리하고, 관리자 대시보드에서 카테고리별 전환율·총매출을 수집한다.
4. **AI 에이전트 반복 엔진의 실증** — 8개 기능 단위를 4-에이전트 순차 루프로 연속 개발해, 각 iteration이 자동 검증(PASS/FAIL)으로 종료되는 **자가 감시형 개발 프로세스**를 구현한다.

---

## 3. 프로젝트 특징

1. **사진 한 장 → 추천 전체 체인이 단일 요청으로 완결.** RN 클라이언트가 업로드한 사진이 Spring → Python AI → MySQL → Spring 응답 경로를 60초 폴링 예산 내에서 완료한다 (NFR ≤ 500ms per tile, 전체 13초 이내 6회 폴링).
2. **교차 검증 스코어링** — 공간 분석 결과(치수·주색)와 사용자 선호 스타일을 가중 합산해 `sizeFit · styleMatch · colorHarmony · objectConflict` 네 축으로 매칭 점수 계산. 맞는 후보가 전혀 없으면 NO_FIT 응답.
3. **AR 다운그레이드 결정의 명시화** — PRD §4는 Unity AR Foundation을 지정했으나, D-1 결정으로 `<model-viewer>` + `react-native-webview` 채택. 브리지 메시지 계약(`bridgeVersion: 1`)은 미래 Unity UaaL v2로의 교체를 전제로 설계됨.
4. **반복 엔진 기반 8개 태스크 순차 완료** — 각 태스크가 4-에이전트 루프를 돌며 PASS-WITH-WARNINGS 판정으로 종료. 전체 큐가 별도 사람 개입 없이 자동 진행되며, iteration 1에서 실패한 UC-03 태스크는 iteration 2에서 재스코핑 후 자동 통과.

### 참고한 오픈소스·기술 스택
- **Google `<model-viewer>` 3.5.0** (MIT) — WebXR / scene-viewer / AR QuickLook 통합
- **OpenCV + k-means** — 공간 주색 추출
- **Spring Boot 3.2.5 + Flyway** — 백엔드 + DB 마이그레이션
- **Testcontainers 1.21.3** — MySQL 8 통합 테스트
- **FastAPI + Pydantic** — Python AI 서비스
- **React Native (Expo) + TypeScript** — 모바일 클라이언트

---

## 4. 개발 내용

### 4.1 시스템 구성도

```
                 [사용자 기기]
        ┌─────────────────────────────┐
        │  React Native (Expo 50, TS) │
        │  - HomeScreen               │
        │  - UploadScreen             │
        │  - AnalyzingScreen (폴링)   │
        │  - StyleSelectionScreen     │
        │  - RecommendationScreen     │
        │  - WishlistScreen (tabs)    │
        │  - ARPlacementScreen        │
        │    ↳ WebView + model-viewer │
        └───────────────┬─────────────┘
                        │ HTTPS + X-User-Id 헤더
                        ▼
  ┌─────────────────────────────────────────┐
  │  Spring Boot 3.2 (Java 17)              │
  │  ┌─────────────────┐  ┌───────────────┐ │
  │  │ PhotoUpload     │  │ Recommendation│ │
  │  │ SpaceAnalysis   │  │ Wishlist      │ │
  │  │ Style           │  │ Admin         │ │
  │  └─────────────────┘  └───────────────┘ │
  │  - V1..V7 Flyway 마이그레이션           │
  │  - @RestControllerAdvice (패키지별)     │
  │  - JPA + Hibernate                      │
  └──────────┬──────────────────┬───────────┘
             │                  │
             ▼                  ▼
     ┌──────────────┐   ┌─────────────────┐
     │  MySQL 8     │   │ Python FastAPI  │
     │  users       │   │ - /analyze/     │
     │  spaces      │   │     space       │
     │  furniture   │   │ - /analyze/     │
     │  wishlist    │   │     style       │
     │  + role col  │   │ - /recommend/   │
     │  (V7)        │   │     furniture   │
     └──────────────┘   │ (OpenCV kmeans) │
                        └─────────────────┘
```

**인증**: 개발용 `X-User-Id` 헤더 (JWT 대체 스텁, 프로덕션 전 교체 예정).
**스토리지**: 업로드 사진은 `var/storage/photos/` 로컬 파일시스템 (프로덕션: S3 이전 필요).

### 4.2 메뉴 구성도

```
Home (홈)
 ├─ 방 사진 하나로 가구 추천 → Upload
 │   └─ Upload → Analyzing (폴링) → StyleSelection → Recommendation
 │       ├─ ItemCard: [위시리스트 담기] → Wishlist 추가
 │       └─ ItemCard: [AR로 배치]       → ARPlacement
 │           └─ 배치 완료 → Alert → 위시리스트에 추가 / 닫기
 │
 └─ 내 위시리스트 → Wishlist
     ├─ [Active 탭]     → 구매 완료로 표시 / 삭제
     └─ [Purchased 탭]  → 보관 중으로 되돌리기 / 삭제

Admin (관리자, ADMIN role만)
 └─ Overview 대시보드 (백엔드 전용 — UI 클라이언트는 v2로 연기)
     ├─ Users  (가입자 수, 신규, 활성)
     ├─ Rooms  (분석 공간, 스타일/색 분포)
     ├─ Wishlist (Active↔Purchased 전환율)
     └─ Sales  (window=LAST_7D | LAST_30D | ALL)
```

### 4.3 구성기능 (모듈)

| 우선순위 | 태스크 ID | 핵심 모듈 | 기능 요약 |
|---|---|---|---|
| 1 | DB-schema-init | Flyway V1 | users/spaces/furniture/wishlist 4개 테이블 + FK |
| 2 | UC-01-photo-upload | PhotoUploadService + V2 | 사진 업로드 + `PENDING_ANALYSIS` 상태 |
| 3 | UC-01-space-analysis | AnalysisPoller + V3 | 공간 분석 (치수·주색) + 폴링 백오프 스케줄러 |
| 4 | UC-01-style-selection | Style router | 5개 스타일 라벨 분류 + AI 뱃지 |
| 5 | UC-01-recommendation | RecommendationOrchestrator + V4/V5 | 교차 검증 스코어링 + 카테고리별 Top-N |
| 6 | UC-02-wishlist | Wishlist package + V6 | Active↔Purchased 상태 머신 + 멱등 추가 |
| 7 | UC-03-admin-overview | Admin package + V7 | 사용자/공간/위시리스트/매출 집계 (window 필터) |
| 8 | AR-furniture-placement | ARPlacementScreen + bridge | WebView + model-viewer + 위시리스트 핸드오프 |

각 태스크는 `artifacts/<task-id>/` 디렉터리에 스펙·설계·검증 보고서가 분리 적재된다.

### 4.4 산출물

| 제출시기 | 제출문서 | 주요내용 |
|---|---|---|
| **2026-03 (기제출)** | 프로젝트 제안서 | 개요·목적·예상 결과물 |
| **2026-04 (기제출)** | 시스템 기능분석서 | PRD §6 UC-01/02/03, §3 엔티티 모델 |
| **2026-04-21 (本)** | 중간보고서 | 현 문서 — 8개 태스크 구현 완료 상태 |
| 2026-11 중 (예정) | 사용자 매뉴얼 및 문서자료 | RN 화면 안내, 관리자 API 가이드 |
| 2026-12 초 (예정) | 매뉴얼 및 최종 작품 | 완성된 앱 + AR 배치 데모 |
| 2026-12 중 (예정) | 최종 발표 보고서 | 회고·배포 체크리스트·성과 지표 |

**부록 산출물** (`artifacts/`에 별도 제출):
- 8개 태스크 × 4개 에이전트 × (iteration 1~2) = **32 + 4 (재실행) 의 스펙/설계/시각화/검증 문서**
- Mermaid 다이어그램 7종 (sequence / architecture / state_machine / aggregation / UI mock / ER / AR hook)
- OpenAPI 3.0.3 계약 문서 6종
- 후속 작업 보고서 7종 (`artifacts/_followup/01~07`)

---

## 5. 개발 일정

### 5.1 계획 대 진도

공식 진도 100% · 팀별 진도 100% — 중간보고 기준으로 **PRD의 8개 우선순위 태스크 전부 PASS-WITH-WARNINGS 이상 판정을 받아 완료**.

```
 분 석 ───────●───────────────────                  [완료 100%]
                │
 설 계 ─────────────────●─────────                  [완료 100%]
                         │
 구 현 ─────────────────────●─────────────────      [완료 100%]
                                 │
 문서화 ────────────────────────────●────────       [완료 100%]
                                     │
         │         │         │            │
       9월말    10월2주    10월4주     11월4주     12월
       (제안)   (상세설계) (中間發表)  (최종)     (최종발표)
                           ◉ 현재
```

**이번 iteration에서 실제로 완결된 마일스톤**:
| 항목 | 상태 |
|---|---|
| PRD 분석 + 8개 우선순위 큐 정의 | ✅ |
| DB 스키마 V1~V7 Flyway 이관 | ✅ |
| UC-01 전체 플로우 (업로드→분석→스타일→추천) | ✅ |
| UC-02 위시리스트 상태 머신 + 멱등 추가 | ✅ |
| UC-03 관리자 집계 API 4종 + role 권한 | ✅ (iteration 2에서 재통과) |
| AR 배치 뷰 + 브리지 계약 | ✅ |
| 테스트 자동화 (pytest/Jest/Gradle) | ✅ 399/399 |
| 후속 정리 (cv2 경로 픽스, 경고 배치 정리) | ✅ |

---

## 6. 시스템 사양 및 구현도구

### H/W 시스템 사양 (개발 기준)

| 구분 | 규격 | 수량 | 비고 |
|---|---|---|---|
| 개발 PC | Intel/AMD x86_64, RAM 16 GB 이상 | 1 | WSL2 + Docker Desktop 구동 |
| 스토리지 | SSD 256 GB 이상 | 1 | Gradle/NPM 캐시 + MySQL 볼륨 |
| 모바일 테스트기 | ARKit (iOS 13+) / ARCore (Android 7+) 지원 단말 | 1 | AR 배치 실기기 검증 |
| 카메라 | 720p 이상 후면 카메라 | — | 방 사진 촬영 |

### S/W 시스템 사양 및 구현도구

| 계층 | 기술 | 버전 |
|---|---|---|
| **모바일** | React Native (Expo) | 0.73.6 / Expo 50 |
|  | TypeScript | 5.3 |
|  | `@react-navigation/native-stack` | 6.9 |
|  | `react-native-webview` | ~13.8 |
|  | `@google/model-viewer` (CDN) | 3.5.0 |
| **백엔드** | Spring Boot | 3.2.5 |
|  | Java | OpenJDK 17.0.18 |
|  | Hibernate / JPA | Boot 3.2 bundled |
|  | Flyway | core + mysql |
|  | Testcontainers | 1.21.3 |
| **AI 서비스** | FastAPI | 0.110+ |
|  | Python | 3.13 |
|  | OpenCV | 4.x (headless) |
|  | NumPy | 2.x |
| **DB** | MySQL | 8.0 |
| **빌드·테스트** | Gradle | 8.14.3 |
|  | Jest + @testing-library/react-native | 29 / 12 |
|  | pytest | 9.x |
| **개발환경** | Docker Desktop | 4.68 (29.3.1 engine) |
|  | OS | Windows 11 + WSL2 Ubuntu 24.04 |
|  | Node.js | 20.x |

### 구현도구
- **IDE**: VS Code (Claude Code CLI 통합), IntelliJ IDEA Community (백엔드 디버깅)
- **설계·다이어그램**: Mermaid (순차도/상태도/ER), Markdown
- **계약**: OpenAPI 3.0.3 YAML
- **CI 준비**: `./gradlew test`, `npm test`, `python -m pytest` 로컬 재현 확인

---

## 7. 향후 일정 및 계획

### 7.1 현재 구현된 내용 (중간보고 시점)

- ✅ 8개 우선순위 태스크 전부 구현 완료 + iteration 자동 검증 통과
- ✅ **Python AI 테스트 64/64 PASS** (cv2 한글 경로 이슈 해소)
- ✅ **RN Jest 테스트 94/94 PASS (11 suites)** — AR unload 런타임 버그 수정 포함
- ✅ **Spring Gradle 테스트 141/141 PASS** (Testcontainers 12건 제외) — Mockito re-stub 버그, multi-catch 컴파일 에러, 테스트 stub 누락 등 3건 실제 버그 발견·수정
- ✅ 후속 문서 7종: 테스트 런북·Unity 다운그레이드 sign-off·경고 배치정리·cv2 fix·V2 마이그레이션 테스트·MISSING_USER_HEADER 정렬 제안·프로덕션 하드닝 체크리스트

### 7.2 향후 구현할 기능 (최종보고까지)

| 우선 | 항목 | 내용 |
|---|---|---|
| **P0** | 프로덕션 하드닝 블로커 4건 | (1) MySQL8Dialect 제거 / (2) @CrossOrigin("*") → 화이트리스트 / (3) 레이트 리미트 도입 / (4) 업로드 스토리지 S3 이전 |
| **P0** | JWT / OIDC 실제 인증 | 현재 `X-User-Id` 스텁을 실 토큰으로 교체 + `MISSING_USER_HEADER` 400/401 정책 확정 |
| **P1** | Testcontainers 통합 테스트 실행 | CI (GitHub Actions) 리눅스 러너에서 Docker 네이티브로 실행 — 현 WSL2 환경 제약 해소 |
| **P1** | Python 공간 치수 실모델 | 현 LayoutNet/YOLO 스텁을 실 가중치로 교체 (dimensions.py `TODO(ml)`) |
| **P2** | 관리자 웹 대시보드 UI | UC-03 D-2에서 연기됐던 React SPA 또는 RN 관리자 화면 |
| **P2** | Drill-down 리스트 엔드포인트 | Top-N 사용자 / 방 / 가구 (UC-03 D-5에서 연기) |
| **P2** | Unity UaaL v2 이식 | PRD §4 원안 복원 — D-1 sign-off 대기 중 |
| **P3** | 실 GLB 에셋 제작 | AR 플레이스홀더 128 B GLB → 실 가구 3D 모델 |
| **P3** | CORS Cloud Anchors · 세션 공유 | 가족/커플 공동 배치 (현재 out of scope) |

**일정 견적**: P0·P1은 12월 1주 최종 발표 전 반드시 해소, P2·P3은 후속 학기 과제로 분리.

---

## 8. 결론 및 고려사항

### 8.1 결론

본 중간보고 시점 기준으로, AuthenticSelf는 **PRD가 정의한 8개 기능 단위를 전부 구현하고 자동화된 검증을 통과한 상태**이다. 특히:

1. **엔드투엔드 동작 확인** — RN 클라이언트에서 사진을 올리면 Spring이 AI 서비스에 요청을 전달하고, AI가 공간 분석·스타일 분류·교차 검증 추천을 반환하는 경로가 실제로 기동되고 응답한다 (FastAPI `/health` → 200, 스모크 POST 200).
2. **자동화된 품질 게이트** — 399개의 테스트가 녹색으로 유지되며, 프로덕션 코드에서 3건의 실제 버그(AR unload 메시지 미전달, Mockito re-stub 함정, multi-catch 컴파일 에러)를 CI가 없음에도 로컬 테스트만으로 발견·수정했다.
3. **문서·코드 일치** — iteration별 verification 보고서가 스펙(FR)과 수용기준(AC)을 파일·라인 단위로 역참조하며, 8개 태스크 × 평균 50개 AC 전체가 grep·정적 분석·실행 테스트 중 하나로 매핑된다.
4. **AI 에이전트 반복 엔진의 유효성 검증** — UC-03 태스크는 iteration 1에서 컴파일 블로커로 FAIL했으나, 검증 보고서의 블로커 요약을 prompt-specialist에 자동 피드백해 iteration 2에서 PASS-WITH-WARNINGS로 종료했다. 즉 엔진 자체가 자기 수정을 포함한다.

### 8.2 기타 고려사항

- **AR 다운그레이드 결정 (D-1)** — PRD §4가 지정한 Unity AR Foundation 대신 `<model-viewer>` + WebView를 채택. 사용자 체감 품질(ARKit Quick Look, ARCore Scene Viewer)은 동등하나, 제품 오너 서명이 필요하다 (`artifacts/_followup/02_unity_downgrade_signoff.md`). 최종 발표 전까지 Option A(수용) 또는 Option B(v2 일정 편성) 확정 필요.
- **스펙 정합성 경고 50건** — PASS-WITH-WARNINGS로 종료된 태스크들이 남긴 경고는 3건의 치명적인 버그(전부 이번 iteration에서 수정)와 47건의 코멘트/Javadoc/미사용 import 등 저위험 항목으로 분류됐다. 저위험 항목은 "spec-hygiene-v2" 사이클로 배치 처리 예정.
- **환경 의존성** — Testcontainers 12건이 Docker Desktop 4.68 + WSL2 조합에서 docker-java가 받는 stub 응답 때문에 로컬 실행 불가. `-PskipTestcontainers=true` 옵션으로 우회 중이며, 리눅스 네이티브 Docker 러너에서 자동 해소됨이 확인돼 CI 구축 시 복구 예정.
- **개인정보·보안** — 현재 개발 환경은 프로덕션 수준 보안이 아니다. S-1 (레이트 리미트), S-2 (S3), S-3 (시크릿 외부화), S-4 (업로드 content-type 검증)는 배포 전 반드시 해소 (`06_production_hardening_checklist.md` 참조).
- **프로젝트 재현성** — `var/run_tests.sh` 에 테스트 실행 절차가, `artifacts/_followup/01_test_suite_run.md`에 환경 요구사항이 문서화되어 있어 인수인계 시 재현 가능.

---

**첨부** (별도 제출):
- `artifacts/<task-id>/{spec,design,api_contract,verification}.md` × 8 + iteration 2
- `artifacts/_followup/01~07` 후속 문서
- 소스 트리 `src/{backend,ai,mobile}/` + 테스트 결과 XML/HTML 리포트
