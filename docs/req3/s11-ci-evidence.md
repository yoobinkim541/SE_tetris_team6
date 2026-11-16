# S11-C2 CI 테스트·커버리지 증거와 대전 GUI 인수 선택

- 소유: C 네트워크·CI. 수정: `build.gradle`(guiAcceptance 필터), `.github/workflows/{ci,package}.yml`, `scripts/coverage-summary.ps1`
- 근거: [#160 S11-C2](https://github.com/yoobinkim541/SE_tetris_team6/issues/160), 규칙 R3-COVERAGE·R3-NETTEST·R3-REGRESSION
- 관련: `docs/req3/s11-lan-measurement.md`(두 PC 200ms 측정 절차), `docs/req3/s10-c-network.md`(heartbeat 판정)

## 1. 어디서 무엇이 실행되는가

| 경로 | 실행 명령 | 포함 내용 | 증거 산출물 |
|---|---|---|---|
| 로컬 개발 | `.\gradlew.bat check` | `test` + `jacocoTestReport` + `jacocoTestCoverageVerification`(LINE ≥ 70%) | `build/test-results/test/TEST-*.xml`, `build/reports/jacoco/test/jacocoTestReport.xml` |
| CI `windows-acceptance` | `gradle check guiAcceptance resourceAcceptance releaseJar packageApp --no-daemon` | 아래 표의 네 태스크 전체 | `build/reports/tests`, `build/verification/test-summary.json`, `build/verification/package-smoke.json`, `build/distributions/SE-Tetris-windows.zip`, `build/libs/SE-Tetris.jar` |
| CI `test` (ubuntu) | `gradle check --no-daemon` | `test` + 커버리지 게이트. 실패가 있으면 check가 실패한다 | `build/reports/tests/test`, `build/reports/jacoco/test/html` |
| CI `package` | `gradle check guiAcceptance releaseJar packageApp --no-daemon` | 패키지 + 인수 + 커버리지 증거 | `build/verification/{package-smoke,test-summary}.json`, zip, jar |

### 태스크별 선택 클래스

| Gradle 태스크 | 선택 규칙 | 클래스 |
|---|---|---|
| `test` (기본) | `audit/java` 포함 전체 테스트, `java.awt.headless=true` | 97개 스위트, 1,172개 테스트 |
| `guiAcceptance` | `includeTestsMatching` 4개, `java.awt.headless=false`, `audit.gui=true`, JaCoCo 비활성(중복 계측 회피) | `tetris.ui.ProductionWiringAuditTest`, `tetris.ui.BattleMainFrameTest`, `tetris.versus.ui.BattleLifecycleTest`, **`tetris.versus.ui.BattleDemoAcceptanceTest`(S11-C2 추가)** |
| `resourceAcceptance` | `includeTestsMatching` 3개, `maxHeapSize=256m`, 실제 창 | `tetris.ui.ProductionWiringAuditTest`, `tetris.ui.ChecklistInputLatencyTest`, `tetris.feature.RandomInputStressTest` |
| `releaseJar` / `packageApp` | `SE-Tetris.jar` / jpackage app-image zip | `build/libs/SE-Tetris.jar`, `build/distributions/SE-Tetris-windows.zip` |

`BattleDemoAcceptanceTest`는 실제 대전 미리보기 창에서 두 플레이어의 이동·일시정지·강제 낙하·종료를 키 이벤트로 시연하고, 화면을 `build/reports/sprint7-demo.png`로 남긴다. 기존에는 어떤 태스크에도 선택되지 않아 CI에서 한 번도 실행되지 않았다. 이제 `guiAcceptance`가 선택하므로 `check guiAcceptance`에서 실제로 돈다.

## 2. 증거 요약 파일 (`scripts/coverage-summary.ps1`)

```powershell
powershell -File scripts/coverage-summary.ps1
# 기본값: -ResultsDir build/test-results/test -Jacoco build/reports/jacoco/test/jacocoTestReport.xml
#         -Out build/verification/test-summary.json -MinLine 0.70
```

산출 JSON:

| 키 | 의미 |
|---|---|
| `commit` | `git rev-parse HEAD`. 실패하면 `unknown` |
| `generatedAt` | UTC 생성 시각 |
| `tests.total/passed/failed/skipped/suites` | JUnit XML `TEST-*.xml` 합계 |
| `line.covered/total/ratio` | JaCoCo XML의 `LINE` 카운터 그대로(임의 제외 없음) |
| `gateMinLine` | 게이트 기준(기본 `0.70`) |
| `gatePassed` | `failed == 0 && ratio >= gateMinLine` |
| `notVerified` | `["실제 두 PC LAN 200ms", "최소 사양 장비(6-4·6-5)"]` — CI로 대신할 수 없는 항목 |

동작 규칙:

- 테스트 XML이 **없으면** 0으로 채우지 않고 종료 코드 **1**. 빈 임시 폴더를 주면 이 경로를 확인할 수 있다.
- JaCoCo XML이 없으면 종료 코드 **1**.
- `failed > 0` 또는 `ratio < MinLine`이면 JSON은 쓰고 종료 코드 **1**.
- 정상이면 종료 코드 **0**.

## 3. 로컬 실행 결과 (2026-11-10, Windows 10.0.26300, JDK 21)

```text
.\gradlew.bat check --no-daemon        → BUILD SUCCESSFUL
.\gradlew.bat check guiAcceptance --no-daemon → BUILD SUCCESSFUL
powershell -File scripts/coverage-summary.ps1  → 종료 0
```

| 항목 | 값 | 출처 |
|---|---|---|
| 테스트 | 1,172개 (통과 1,155, 실패 0, 생략 17), 스위트 97 | `build/test-results/test/TEST-*.xml` 합계 |
| 라인 커버리지 | 4,428 / 4,912 = **90.15%** | `jacocoTestReport.xml` LINE 카운터 |
| 게이트 | 70% 이상 → 통과 | `jacocoTestCoverageVerification` |
| `guiAcceptance` | 4개 스위트 17개 테스트(생략 1), 실패 0 | `build/test-results/guiAcceptance/TEST-*.xml` |
| 빈 결과 폴더 | 종료 코드 1, "테스트 결과 XML이 없습니다" | 직접 실행 |
| JaCoCo 경로 오류 | 종료 코드 1, "JaCoCo XML이 없습니다" | 직접 실행 |

생략 17건은 `@EnabledIfSystemProperty(audit.gui=true)` 밖에서 도는 GUI 전용 테스트와 저장소 경로 의존 테스트로, `guiAcceptance`·`resourceAcceptance`에서 별도 실행된다.

## 4. 커버리지 수치를 올리기 위한 임의 제외는 없다

- `guiAcceptance`·`resourceAcceptance`는 `jacoco.enabled = false`다. 중복 계측으로 Java 21에서 크래시가 난 이력이 있어 이미 그렇게 두었고, 이번에 바꾸지 않았다. 커버리지는 기본 `test` 태스크만 측정한다.
- `coverage-summary.ps1`은 JaCoCo가 낸 `LINE` 카운터를 그대로 옮긴다. 클래스·패키지 제외, 임계값 완화, 분모 축소를 하지 않는다.
- `skipCoverage` Gradle 속성은 기존 로컬 편의 기능이며 CI 명령에는 쓰지 않는다.

## 5. 미검증 항목 (통과와 별개)

| 항목 | 상태 | 필요한 것 |
|---|---|---|
| 실제 두 PC LAN 200ms | **미검증** | `docs/req3/s11-lan-measurement.md` 절차로 두 PC·120fps 영상 측정 |
| 최소 사양 장비(6-4·6-5) | **미검증** | 해당 사양 장비에서 `resourceAcceptance`와 실제 실행 |
| 패키지 두 PC 실행 | **미검증** | `SE-Tetris-windows.zip`을 두 PC에 풀어 P2P 접속 확인 |
| CI 실행 결과 링크 | 이 문서 작성 시점의 로컬 결과만 기록 | PR 푸시 뒤 Actions 실행 URL |

CI 통과는 코드·커버리지 회귀 증거일 뿐이며, 위 네 항목을 대신하지 않는다.
