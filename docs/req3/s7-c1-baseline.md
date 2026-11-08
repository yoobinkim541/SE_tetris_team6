# S7-C1 기준선 기록 (3차 대전모드 착수 시점)

- 작업: [#118 S7-C1 최신 develop 기준선과 전송 DTO 설계](https://github.com/yoobinkim541/SE_tetris_team6/issues/118)
- 규칙 ID: R3-REGRESSION, R3-NETTEST
- 기록일: 2026-11-07. 이 문서의 수치는 아래 기준 커밋에서 직접 실행한 결과이며, 10/26·10/27 보고서 수치를 옮긴 것이 아니다.

## 1. 기준 커밋

| 항목 | 값 |
|---|---|
| 기준 | `origin/develop` `553da8368bb60b82ca893c80f7ce2a4f2afe07a3` (2026-10-27, PR #112 병합) |
| 3차 작업 브랜치 | `feature/req3-s7-c1`는 위 커밋에서 분기 |
| `origin/main` | `427f28e` (PR #109 README 병합). develop에 없는 커밋 3개, develop만 가진 커밋 7개 |
| 열린 develop→main PR | #113, `README.md` 충돌로 병합 불가 상태 (CI는 성공) |

3차 기능은 develop 기준으로 개발한다. main 기준으로 분기하면 아래 PR #110~#112 변경이 빠진다.

## 2. 로컬 이력과 develop 차이

계획 당시 비교 대상으로 적은 로컬 커밋 `dad8168`은 해시만 다르고 내용은 develop에 이미 있다.

| 로컬 | develop 대응 | 확인 방법 |
|---|---|---|
| `dad8168` (run.bat·README) | `473220d` | `git diff dad8168 473220d` 차이 없음 |
| `feature/c-work2` 끝 `77292c5` (PR #106·#107 작업) | `96c292b` (PR #107 병합) | `git diff 77292c5 96c292b` 차이 없음 |

develop은 `96c292b` 뒤에 다음 7커밋을 더 가진다.

| PR | 내용 | 3차 영향 |
|---|---|---|
| #110 | I 블록 상대 확률 정확화 (요구사항 2-18~2-21) | 엔진 회귀 기준 |
| #111 | `docs/item-specification.md` 아이템 5종 명세 | R3-ITEM·R3-ITEMSPEC 근거 문서 |
| #112 | 체크리스트 80항목 감사, `ChecklistInputLatencyTest`, `resourceAcceptance` 태스크, 패키지 설치 크기 게이트 | 3차 회귀 명령에 포함 |

### 보존한 로컬 변경

`C:/GitHub/SE_tetris_team6` worktree(`feature/c-work2`)에 커밋하지 않은 개인 변경 2개(`docs/requirements-spec-v2.md`, `src/main/java/tetris/feature/game/Difficulty.java`)와 stash 1개가 있다. 이 작업은 별도 worktree에서 진행했고 그 파일들을 수정·이동하지 않았다.

## 3. 기존 자료 확인

| 항목 | 위치 | 상태 |
|---|---|---|
| `resourceAcceptance` | `build.gradle` (256MB heap, `ProductionWiringAuditTest`·`ChecklistInputLatencyTest`·`RandomInputStressTest`) | develop에 있음. CI `windows-acceptance` 잡이 `check guiAcceptance resourceAcceptance releaseJar packageApp` 실행 |
| 아이템 명세 | `docs/item-specification.md` (PR #111) | develop에 있음 |
| 6-4 최소 사양 실행 | `docs/checklist-compliance-2026-10-27.md` | 부분 검증. 설치 크기 게이트·256MB heap만 확인, 1.2GHz/총 RAM 1GB 실기 미검증 |
| 6-5 같은 환경 1초 미만 | 같은 문서 | 부분 검증. 실행 호스트/CI 기준만 확인 |
| 6-7 업무분담 관리 | 같은 문서 | 외부 확인 필요. Trello 대신 Notion 허용 여부 미확인 |

6-7 증빙 자료 위치(3차): Notion 프로젝트 대시보드의 Sprint Tasks(트랙·담당·상태), GitHub 이슈 #114~#119(`sprint-7`·`track-A/B/C` 라벨, 담당자 지정). Trello 대체 허용 여부는 Notion R3-Q03에서 확인한다. 이 기록만으로 6-4·6-5·6-7을 완료 처리하지 않는다.

## 4. 기준 검증 결과

환경: Windows 11 (10.0, amd64), Temurin JDK 25.0.2 (컴파일 `options.release = 21`), Gradle Wrapper 9.7.1. CI는 Temurin 21·Gradle 8.10.2를 쓰므로 JDK 차이로 인한 결과 차이는 CI에서 다시 확인한다.

| 명령 | 결과 |
|---|---|
| `gradlew clean check guiAcceptance resourceAcceptance --no-daemon` | 성공 (종료 코드 0) |
| `test` | 897 실행 / 888 통과 / 9 생략 / 실패 0 |
| LINE coverage (`check` 게이트 70%) | 90.11% (1986 / 2204) |
| `guiAcceptance` | 9 실행 / 9 통과 |
| `resourceAcceptance` | 23 실행 / 23 통과 |
| `gradlew -I audit/requirements-audit.init.gradle requirementsAudit -PauditGui=true --no-daemon` | 73 실행 / 73 통과 |

생략 9건은 `test` 태스크가 headless로 실행할 때 건너뛰는 실제 창 검사이며, 같은 검사는 `guiAcceptance`·`requirementsAudit -PauditGui=true`에서 실행했다.

실행하지 않은 것: `releaseJar packageApp`, `scripts/verify-package.ps1`(패키지·EXE 검증은 이번 작업이 바꾸지 않는다), 실제 두 PC·최소 사양 장비 검증.
