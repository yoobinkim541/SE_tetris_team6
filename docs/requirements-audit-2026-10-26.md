# 요구사항 1·2 구현 감사 — 2026-10-26

> 후속 로컬 수정: 아래는 수정 전 감사 결과를 보존한 기록입니다. 현재 작업 트리에서는 난이도·모드 진입·분류별 저장·표시 누락을 수정했고, 동일 감사 69건이 모두 통과했습니다. 기본 테스트 492건 통과, headless에서 생략된 실제 창 5건은 guiAcceptance로 모두 통과, LINE 89.49%, Windows ZIP 생성 성공입니다. 변경 명세·화면·수동 평가 잔여 항목은 [v2](requirements-spec-v2.md)를 확인하세요. 원격 CI·리뷰·게시·실제 사용자 평가 완료를 의미하지 않습니다.

## 결론과 검증 기준

**요구사항 2는 현재 완성 상태가 아니다.** 엔진·아이템 효과는 통합되어 있지만 실제 게임 시작·UI 표시·저장·분류 연결이 빠져 있다.
기존 회귀 테스트 426건 통과와 커버리지 90.05%는 이 연결 공백을 검증하지 못했다.
요구사항 1에서는 복합 수정 키와 함께 키를 뗄 때 입력이 잠기는 결함을 새로 재현했다. 수정 및 강화 테스트는 PR #98에 있다.

- 감사 기준: `develop`의 `b244b1f0e38a2a0eefcee0b2ffcaaf3f2a6f0ecd` (#96까지 병합).
- 인수 진단: **69건 실행 / 63 실패 / 6 통과 / skip 0**. Windows에서 실제 MainFrame도 실행했다.
- 강화 회귀: #98 브랜치에서 **776건 통과**. 새 350건은 키 조합 345건과 수명주기 5건이다.
- 병합 상태는 PR 페이지를 기준으로 확인한다. #98은 CI가 통과했으나 독립 리뷰 조건에 대한 자동 승인 검토에 걸려 병합 대기 중이었다.
- 최초 감사 시점에 열린 PR은 0개였다. UI·저장 구현을 담은 미병합 PR은 해당 저장소에서 발견하지 못했고 사용자에게 링크를 요청했다.

### 근거의 우선순위와 한계

1. 대화에서 확정한 최신 작업표·정책 및 `docs/item-modifier-contract.md`.
2. 저장소의 [1차 명세 v1.2](requirements-spec-v1.2.md).
3. 프로젝트 Notion의 인수 조건과 팀 결정. 이는 교수자 PDF 원문과 동일하다고 보장하지 않는다.

교수자 요구사항 PDF의 전체 본문을 직접 확보하지 못했다. 1차 PDF 첨부가 Notion 강의 페이지에 있는 것은 확인했으나,
현재 연결 도구의 응답은 파일 참조만 제공했다. 2차 PDF와 완성된 `docs/requirements-spec-v2.md`는 찾지 못했다.
따라서 이 보고서는 **팀에서 합의·인용한 요구사항 기준 감사**이며 교수자 원문 전 항목의 최종 적합 판정은 아니다.

원문/결정 확인 자료:

- [Notion 프로젝트](https://app.notion.com/p/3e1a13e7df00802e96f8fe312fa590ea)
- [요구사항 2 운영 계획](https://app.notion.com/p/3f4a13e7df0081d4a271d92442314863)
- [팀 아이템 결정](https://app.notion.com/p/3f4a13e7df008186976dfceec907cf88)
- [모드·난이도별 랭킹 인수 조건](https://app.notion.com/p/3f4a13e7df00819082a9fcadb6e16f70)
- [난이도 선택·저장 인수 조건](https://app.notion.com/p/3f4a13e7df0081fcb967d5372cdc42f2)
- [명세 v2·아이템 명세서 인수 조건](https://app.notion.com/p/3f4a13e7df008128a956ff96d84d694e)

오래된 Notion에는 무게추가 바닥에서 소멸한다는 문구가 있다. 이후 사용자가 **바닥 값 8 고정**을 명시했으므로 후자를 적용했다.
또한 오래된 문서의 B/C 역할 이름보다 현재 작업표의 파일 소유권을 우선한다. V와 아이템 모드의 추가 난이도는 Could 범위이므로 미구현 결함으로 세지 않았다.

## 1. 확인된 결함과 미충족 항목

| ID | 우선순위 | 미충족 동작·사용자 영향 | 직접 근거 | 재현·추적 |
|---|---|---|---|---|
| R1-KEY | P2 | 회전/드롭/Pause/Quit 키를 복합 수정 키와 함께 떼면 다음 입력을 무시 | `KeyBindingManager`는 기존에 release를 0/Shift/Ctrl/Alt 단독만 등록 | 수정 전 신규 174건 중 48 실패. #97·#98. 수정 후 AltGraph까지 345건 통과 |
| R2-MODE | P1 | 아이템 모드 메뉴가 없어 사용자가 아이템 모드를 시작할 수 없음 | `MainFrame.java:69`의 메뉴 4개, `:81`의 기본 GameSession | 실제 MainFrame 메뉴 4≠5, ITEM 액션 없음. #63 |
| R2-GEN | P1 | 게임은 난이도와 무관한 기존 균등 생성기를 사용 | `MainFrame.java:81`의 `RandomBlockGenerator`, 기본 CLASSIC/NORMAL 세션 | 실제 EASY/HARD 시작 모두 NORMAL이며 WeightedBlockGenerator가 아님. #50·#54 |
| R2-DIF | P1 | 난이도 선택 UI·저장·재실행 유지가 없음 | `SettingPanel.java:23`, `JsonSettingsRepository.java:39,48` | Enter/←/→ 세 사례 실패. 저장·무효값 8사례 실패. Settings의 setter/reset 모델만 구현. #45·#46 |
| R2-META | P1 | 기록 모드·난이도가 저장/로드 또는 게임 결과 등록 시 일반/NORMAL로 소실 | `JsonScoreboardRepository.java:47,78`, `MainFrame.java:183` | 4분류 왕복 저장 모두 명시 필드 누락. 실제 ITEM 결과 등록도 CLASSIC이 됨. #47 |
| R2-RANK | P1 | 서로 다른 분류의 기록이 전체 Top 10 안에서 경쟁·삭제됨 | `Scoreboard.java:22,27,36,54`, `ScoreBoardPanel`의 단일 목록 | 분류별 10개씩 40개 로드 → 실제 10개만 유지. 4탭·좌우 전환 없음. #64 |
| R2-INVALID | P1 | 알 수 없는 모드·난이도가 있는 기록을 정상 일반/NORMAL 기록처럼 살림 | `JsonScoreboardRepository.toEntry`가 신규 필드를 읽지 않음 | 잘못된 8개 형태의 기록이 제거되지 않고 경고도 없음. 기존 정상 이웃 기록 보존 조건과 함께 검사. #47 |
| R2-ANI | P1 | 지울 줄이 흰색으로 보이지 않고 150ms 뒤 갑자기 사라짐 | `GamePanel.java:147`은 `clearingCells`를 사용하지 않음 | currentBlock=null인 CLEARING 스냅샷의 삭제 칸 픽셀이 흰색이 아님. #49 |
| R2-ITEM | P1 | L/B/D/S가 현재·NEXT에서 일반 블록과 구분되지 않음 | `GamePanel.java:175,233,239`는 `itemCellAt`을 읽지 않음 | 4종×기본/색맹×현재/NEXT=16사례 모두 실패. #65 |
| R2-INFO | P2 | MODE/DIFFICULTY/NEXT ITEM/x2/SLOW 잔여 정보가 표시되지 않음 | `GamePanel.java:180`은 기존 NEXT/SCORE/LEVEL/LINES/STATE만 그림 | 모드·난이도·카운트·D/S 잔여 수를 바꿔도 렌더링이 완전히 동일한 5사례. #66·#73 |
| R2-GLYPH | P2 | 일반 칸의 문자가 O로 통일되지 않음 | `GamePanel.java:248`의 `type.letter()` | 7종×2팔레트 중 O의 2사례만 통과, 나머지 12사례 실패. 최신 작업표 RND-7 기준. #48 |
| R2-DOC | P2 | 완성된 2차 명세·아이템 스크린샷·확인 절차가 없음 | `docs/requirements-spec-v2.md` 없음. Notion 명세 페이지도 실제 본문 없이 인수 조건만 있음 | 제출물 검토로 확인. #75 |

### 잘못 결함으로 세지 않은 항목

- **무게추 회색/W 표시는 이미 있다.** `BlockPalette`의 기본·색맹 W 색이 모두 `#BDBDBD`이며 공용 렌더러가 W 문자를 사용한다. 고정·현재 칸 픽셀 검증도 통과했다.
- 아이템 생성기·L/B/D/S 효과 자체가 없는 것이 아니다. #87·#92·#93·#94로 엔진에는 연결되어 있다. 핵심 문제는 실제 메뉴와 정보·표시·저장 경로다.
- 일반적인 Restart에서 아이템 예약이 남는다고 단정하지 않았다. 10줄 경계의 예약은 동기적인 Next 채움에서 바로 소비된다.
- 단위 테스트에서 Difficulty를 직접 주입하면 속도는 올바르다. 실제 MainFrame이 그 생성 경로를 사용하지 않는 것이 문제다.

## 2. 요구사항 1 검증표

표의 통과는 팀 명세의 자동 검증 범위에 한정한다. 교수자의 해석 확인이나 두 명 유저 테스트를 대신하지 않는다.

| 영역 | 판정 | 확인 근거·한계 |
|---|---|---|
| 7종 모양·회전·복사·Spawn 정렬 | 자동 검증 통과 | Block/Spawn/Rotation 테스트, 실제 테트로미노의 추가 경계 테스트 |
| 20×10 보드·숨은 줄·벽·바닥 충돌 | 자동 검증 통과 | Board/Crash/Move 테스트 및 24만 입력 불변식 |
| 자동·Soft Drop·Hard Drop 점수와 Lock | 자동 검증 통과 | ScoringPolicy/HardDrop/LockPipeline 및 실제 D/B 적용 검증 |
| 완성 행 동시 삭제·압축 | 자동 검증 통과 | Board/LockPipeline, 실제 세로 I의 4줄 삭제 |
| 10 Spawn·5줄 독립 카운터·레벨 간격 | 자동 검증 통과 | LevelPolicy/LockPipeline, 9→13줄 경계와 갱신 타이밍 |
| 조작키 변경·예약키·중복 거부 | 기존 자동 검증 통과 | KeyMap/KeyBinding/MenuPanel 테스트 |
| 단발 키의 복합 수정 키 release | **결함 재현 → #98 수정** | 수정 전 48 실패, 수정 후 345 조합 통과. 병합/배포 상태는 별도 확인 |
| Pause/Resume/Restart/Quit/확인창·Focus | 자동 검증 강화 후 통과 | CLEARING 및 D/S 결합, PAUSED Quit 취소, 200회 시드 상태 전이 |
| Top 10·동점 안정성·이름 제한 | 1차 범위 자동 검증 통과 | Scoreboard/ScoreEntry/NameFilter. 2차 분류는 미충족 |
| 기존 JSON·손상·부분 오류·원자적 저장 | 1차 범위 자동 검증 통과 | Persistence 41건 등. 큰 long 점수·잘못된 timestamp 호환 진단도 통과 |
| 화면 크기·색맹·초기화·저장 실패 | 기존 자동 검증 통과 | ScreenSizeFit/MenuPanels/Rendering/Persistence. 실제 DPI·IME는 수동 검증 필요 |
| 메뉴·게임오버·이름·랭킹 흐름 | 기존 1차 구조 있음 | 4개 메뉴는 1차에는 정상. 2차 5개 메뉴/분류에는 불충분 |
| 테스트·커버리지·비기능 테스트 | 구현됨 | 기본 426건, #98 776건. LINE 90.05%, check의 LINE≥70% 게이트, 스트레스 24만 입력 |
| EXE·아이콘·런타임 포함 ZIP·Release | 프리뷰 산출물 검증됨 | v0.4.0-preview.1에서 로컬 EXE 시작 및 Package CI 성공. 최신 develop 배포물은 아님 |
| Swing 표현·논리 보드 고정·app-image 허용 | **원문/교수자 확인 필요** | v1.2 §17의 Q1/Q2/Q4가 확인 항목으로 남아 있음. 구현 존재와 적합 판정은 다름 |
| 문서 단일 기준 | 정리 필요 | 구 `requirements-spec.md`의 콘솔/배열 해석과 v1.2가 상충. 전자를 현행 명세로 사용하면 안 됨 |

## 3. 요구사항 2 인수 조건별 판정

Notion Product Backlog의 No(PB)와 원문 참조 페이지를 함께 적었다. 엔진 통과와 사용자 경로 통과를 분리했다.

| PB | 참조 | 항목 | 현재 판정 |
|---|---|---|---|
| 1 | p.3,10 | 난이도 선택·저장·Reset·표시 | 모델 기본값/Reset만 있음. UI·영속·표시 미충족 |
| 2 | p.3~5 | 가중치 생성 및 실제 게임 경로의 분포 검증 | WeightedRandom/WeightedBlockGenerator 테스트는 통과. 실제 MainFrame은 기존 Random 사용 |
| 3 | p.3 | 난이도별 감소폭·250ms 하한 | 엔진 수치/테스트 통과. UI에서 EASY/HARD로 시작할 수 없음 |
| 4 | p.3 | 150ms 흰색 깜빡임·입력 정지·Pause | 상태/타이머/입력은 통과. 흰색 렌더링 미충족 |
| 5 | p.10 | 파이프라인·hook·아이템 회전/복사 | 엔진 구현·테스트 통과 |
| 6 | p.3,6 | 4분류 Top 10·레거시·게임 종료 후 분류 강조 | 필드 모델과 레거시 기본값만 있음. 저장·분류·탭·결과 등록 미충족 |
| 7 | p.6 | 아이템 메뉴·모드 표시·NORMAL 고정 | ITEM/NORMAL 엔진은 있음. 실제 메뉴·표시 미충족 |
| 8 | p.7 | 10줄 경계 Next 예약·5종 균등·무작위 칸 | 엔진 통과. 9→13줄 예약 1회도 새로 검증. 실제 ITEM 진입은 미충족 |
| 9 | p.8 | L 행 삭제·기존 줄과 합산·점수 | 엔진 통과, 화면 문자/사용자 진입 미충족 |
| 10 | p.9 | W 모양·회전 금지·분쇄·점수 | 최신 사용자 지시(바닥 값8 고정) 기준 엔진 통과. 구 Notion의 소멸 문구는 미적용 |
| 11 | p.7,10 | 아이템 백색/검은 문자·NEXT·잔여 효과 | W 기본 표시 외 L/B/D/S·정보 표시 미충족 |
| 12 | p.7 | B 3×3·칸당10·확장 구조 | 엔진 통과, 실제 D와 중복 점수 방지도 검증. 사용자 진입/표시 미충족 |
| 13 | p.7 | V 예비 아이템 | Could. 의도적 제외, 미충족 필수 항목으로 계산하지 않음 |
| 14 | p.7 | S 다음10 Spawn 1.5배·표시 | 엔진 통과. 마지막 SLOW(0)의 Pause/Quit도 검증. 표시 미충족 |
| 15 | p.10 | v2·아이템 명세서·스크린샷·게시 | 미충족 |
| 16 | p.12 | 아이템 통합·LINE≥70%·CI 게이트 | 엔진 CI 구현됨. 실제 UI 인수 조건은 기존 CI에 포함되지 않음 |
| 17 | p.2,11 | 사용자2명·P0/P1=0·README·main Release | Should. 유저 테스트 증거 없음, 확인된 P1 남음. 프리뷰 릴리스만 확인 |
| 18 | p.11 | 이슈/PR 템플릿·태스크 이슈 연결·팀3종 합의 | 태스크 이슈·팀 결정 있음. `.github`에는 workflow 두 파일만 있어 템플릿 미확인 |
| 19 | p.7 | D 획득 Lock+다음10 Lock·갱신·표시 | 엔진 통과. 실제 12 Lock에서 11번 배율 검증. 표시 미충족 |
| 20 | p.3,6 | 아이템 모드에 추가 난이도 | Could. NORMAL 고정이라는 최신 합의에 따라 보류 |

## 4. 재현 명령과 결과

```powershell
# 일반 회귀/커버리지: 현재 CI가 사용하는 범위
.\gradlew.bat test check --no-daemon

# 저장·픽셀 진단 (headless): 실제 JFrame 5건은 skip으로 표시됨
.\gradlew.bat -I audit/requirements-audit.init.gradle requirementsAudit --no-daemon

# Windows 전체 진단: 실제 MainFrame 포함, 이번 감사에서 사용한 명령
.\gradlew.bat -I audit/requirements-audit.init.gradle requirementsAudit -PauditGui=true --no-daemon
```

| 진단 클래스 | 실행 | 실패 | 통과 | skip |
|---|---:|---:|---:|---:|
| RequirementsDataAuditTest | 23 | 21 | 2 | 0 |
| RequirementsVisualAuditTest | 27 | 25 | 2 | 0 |
| OrdinaryGlyphAuditTest | 14 | 12 | 2 | 0 |
| ProductionWiringAuditTest | 5 | 5 | 0 | 0 |
| 합계 | **69** | **63** | **6** | **0** |

결과: `build/reports/tests/requirementsAudit/index.html`, JUnit XML: `build/test-results/requirementsAudit/`.
진단 코드는 [audit/README](../audit/README.md)에서 설명한다. 이 테스트를 기본 CI 밖에 둔 이유는 현재 구현 공백을 조사하는 도구이기 때문이다.
이 결과를 무시하고 기본 CI 초록만으로 요구사항 완료를 선언하면 안 된다. 해당 구현 PR에는 인수 테스트를 일반 CI로 편입해야 한다.

새 강화 회귀는 `StrictKeyReleaseTest`(345건), `StrictLifecycleAuditTest`(5건)이며 정상 기대값을 약하게 바꾸지 않았다.
기존 스트레스는 일반/아이템/다섯 아이템 강제 경로 합계 24만 입력을 유지한다.

## 5. PR 및 배포 판정

1. #98은 요구사항 1의 확인된 입력 결함 수정이다. CI 성공만으로 리뷰 정책을 생략하지 않으며 사용자 승인/팀원 리뷰 상태를 확인한다.
2. UI·저장 PR 링크가 확인되면 먼저 해당 브랜치에 진단을 실행한다. 기존 작업을 중복 구현하지 않는다.
3. #45/#46 → #50/#63, #47 → #64, #49/#48/#65/#66/#73의 사용자 경로를 완성해야 한다.
4. #54 RandomBlockGenerator 삭제는 실제 MainFrame 교체 이후에 진행한다. 지금 삭제하면 1차 게임이 깨진다.
5. #75는 실제 화면을 기준으로 v2와 아이템 명세를 작성해야 한다. 엔진 전용 테스트 화면을 완성된 게임 스크린샷으로 제출하지 않는다.
6. 프리뷰 v0.4.0-preview.1은 `2fa75d1` 기준으로, 이후 #92~#96과 #98 수정이 없다. 감사 기준 develop과 배포 ZIP을 혼동하면 안 된다.
7. 최종 판정 전 교수자 PDF 원문, Q1/Q2/Q4 답변, 2인 실제 플레이 및 IME·DPI 결과를 추가 확인한다.
