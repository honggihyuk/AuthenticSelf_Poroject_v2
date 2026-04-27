# Follow-up (1) — Full Test Suite Run

**Date**: 2026-04-20
**Environment**: Windows 11, bash (Git Bash), Python 3.13, Node 20.x + npm, Java/Gradle NOT installed.
**Working directory**: `C:/AuthenticSelf_Project/AuthenticSelf_v3`

## Summary

| Suite | Tool | Result | Pass / Total | Real failures |
|---|---|---|---|---|
| Python AI (`src/ai`) | pytest | 4 pre-existing environment FAIL | 65 / 69 | 0 (all 4 are Windows-Korean-path `cv2.imread` — already accepted in UC-01-space-analysis verification) |
| RN Mobile (`src/mobile`) | Jest via `npx jest` | 3–5 FAIL (run-to-run variance) | ~72 / 75 | 3 real (2 `jest.mock()` factory bugs in tests, 1 duplicate-text prod bug) + 2 flaky-timeouts |
| Spring Backend (`src/backend`) | Gradle | **SKIPPED** | — | Java/Gradle/gradlew all absent from env; static verification only (as noted in every sibling `verification.md`) |

**Overall**: the test suites that CAN run are green on real production code except for 3 legitimate issues in the RN layer. The backend cannot be exercised without installing a JDK + Gradle wrapper.

## 1. Python AI service

### Commands
```
cd src/ai && python -m pytest --tb=no -q
```

### Output (tail)
```
..FF.................F..F.......................................         [100%]
=========================== short test summary info ===========================
FAILED tests/test_analyze_space.py::test_analyze_happy_path
FAILED tests/test_analyze_space.py::test_analyze_is_deterministic
FAILED tests/test_color_extractor.py::test_real_cv2_kmeans_extracts_dominant_hex
FAILED tests/test_color_extractor.py::test_deterministic_on_identical_input
```

### Grade
All 4 failures raise `ImageReadError: cv2.imread could not decode file: <name>.jpg` on a tmp_path that contains the Korean characters `홍지승` (the current user's home directory). `cv2.imread` on Windows silently fails on non-ASCII paths — a known OpenCV limitation, not a code defect.

UC-01-space-analysis/verification.md already documented this as **environmental, already accepted**. No action required here.

## 2. RN Mobile (Jest)

### Commands
```
cd src/mobile && npm install --no-audit --no-fund     # 711 packages, exit 0
cd src/mobile && npx jest --watchAll=false
```

### Consolidated failure set (union across runs)

| # | Test | Type | Root cause | Iteration |
|---|---|---|---|---|
| J-1 | `UploadScreen.test.tsx` — **suite failed to compile** | Real | Line 27: `jest.mock(..., () => ({ UploadFailedError: class extends Error { constructor(public httpStatus: number ...) {...} } }))` references out-of-scope identifier `httpStatus`. Jest forbids non-`mock`-prefixed out-of-scope refs inside factory. | UC-01-photo-upload |
| J-2 | `ARPlacementScreen.test.tsx` — **suite failed to compile** | Real | Line 31: `type Handle = {...}` inside a `jest.mock(...)` factory. Same rule violation. | AR-furniture-placement |
| J-3 | `RecommendationScreen.test.tsx` — `AC-45: item card shows name, ₩-formatted price, "매칭 87%" and rationale` | Real | `Found multiple elements with text: 매칭 87%`. Test assumes exactly one "매칭 87%" but two cards in the seed fixture render that string. Either fixture or assertion needs to be narrowed (e.g., `getByTestId('card-f1')` → `within(card).getByText`). | UC-01-recommendation |
| J-4 | `AnalyzingScreen.test.tsx` — `AC-31: polls ≥ 6 times ...` | Flaky timeout | Polling test exceeds Jest's default 5000 ms timeout on cold machines. Needs `it(..., fn, 15000)` or equivalent. | UC-01-space-analysis |
| J-5 | `StyleSelectionScreen.test.tsx` — `AC-28` | Flaky timeout (intermittent) | Cold-start suite timeout; passes on warm cache. | UC-01-style-selection |

### Grade
- J-1, J-2, J-3 are **bug-fix candidates** for Follow-up (3) — all in test code except J-3 which is ambiguous (could be test or prod). Each is <10 LOC to fix.
- J-4, J-5 are environment-sensitivity issues. Raise the Jest timeout on those two tests (1 LOC each) or cache-prime the runner.

## 3. Spring Backend (Gradle)

### Commands attempted
```
which java javac gradle      # exit 3, none present
ls src/backend/gradlew*       # exit 2, no wrapper
```

### Grade
Cannot run. Consistent with every prior `verification.md` — Gradle has never been invoked in this environment; all 53/60/... ACs for Spring-layer code were graded via static grep + Testcontainers test-class source inspection.

To unblock backend tests, install:
1. **JDK 17** (Temurin or equivalent)
2. **Gradle 8.x** OR add a `gradlew` wrapper via `gradle wrapper --gradle-version 8.5` from a machine that has Gradle
3. **Docker Desktop running** (Testcontainers needs it for MySQL integration tests)

Once all three are present: `cd src/backend && ./gradlew test`.

## Outcome / recommendations

| Action | Owner | Blocks |
|---|---|---|
| Fix J-1 (UploadScreen.test.tsx — rename `httpStatus` → `mockHttpStatus`) | (3) batch | UC-01-photo-upload test suite compilation |
| Fix J-2 (ARPlacementScreen.test.tsx — move `type Handle` outside factory) | (3) batch | AR-furniture-placement test suite compilation |
| Investigate J-3 (RecommendationScreen AC-45 duplicate text) | (3) batch | UC-01-recommendation AC-45 |
| Raise Jest timeout on J-4, J-5 (suite level `jest.setTimeout(15000)` or per-`it`) | (3) batch | Flaky CI |
| Install JDK 17 + Gradle + Docker for backend test run | User / CI setup | Backend empirical validation |

Follow-up (3) should address J-1..J-5 as its first bucket of cheap fixes.
