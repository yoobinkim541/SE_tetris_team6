# 2차 요구사항 구현 명세

2026-10-26 로컬 구현 기준. 기본 규칙은 [v1.2](requirements-spec-v1.2.md)를 따르며 이 문서는 난이도·아이템·기록 분류의 변경을 정의한다. 최초 결함 근거는 [감사 기록](requirements-audit-2026-10-26.md)이다. 이 문서는 교수자 원문이나 실제 사용자 평가를 대체하지 않는다.

## 사용자 경로

규칙 ID는 테스트·PR·Notion에서 동일하게 사용한다.

| 규칙 ID | 인수 조건 | 직접 검증 |
|---|---|---|
| DIF-1/2/3/5 | 난이도 선택·저장·일반 게임 적용·판 중 고정 | RequirementsDataAuditTest, RequirementsVisualAuditTest, ProductionWiringAuditTest |
| GEN-4 | 실제 게임에서 난이도별 가중치 생성기 사용 | WeightedBlockGeneratorTest, ProductionWiringAuditTest |
| MOD-2/4 | ITEM은 NORMAL, 다음 아이템·D/S 잔여 수 표시 | ItemPendingTest, RequirementsVisualAuditTest |
| ANI-2/5 | CLEARING 입력 정지·150ms·흰색 칸 표시·Pause 유지 | ClearingAnimationTest, RequirementsVisualAuditTest |
| SBD-2-1 | 네 분류 각각 Top 10·동점 뒤 삽입·재실행 보존 | ScoreboardCategoryTest, RequirementsDataAuditTest |
| SBD-2-2 | 좌우 분류 전환·해당 분류 순위 강조 복원 | ScoreboardNavigationTest |
| PER-2-1 | 난이도·분류 저장, 레거시 기본값, 무효 필드 격리 | RequirementsDataAuditTest, PersistenceTest |
| RND-7/8 | 일반 칸 O·아이템 흰 바탕 검은 문자·W 회색, 현재/NEXT 공통 | OrdinaryGlyphAuditTest, RequirementsVisualAuditTest |
| ITL/ITB/ITD/ITS/ITW | 아래 5종 아이템 효과·점수·수명 | LineClearItemTest, BombItemTest, DoubleScoreItemTest, SlowItemTest, WeightCrushTest |
| KEY-7/8 | 모든 수정 키 조합에서 release, 포커스 이탈 시 Pause·눌림 초기화 | StrictKeyReleaseTest, ProductionWiringAuditTest |

메인 메뉴는 일반 게임 시작, 아이템 모드, 설정, 스코어보드, 종료의 5개 항목이다. 설정의 화면 크기 다음 행에서 난이도를 좌우 또는 Enter로 순환한다. 변경 즉시 저장하며 Reset Settings는 NORMAL을 복원한다. 일반 게임은 새 게임 시작 당시 설정을 사용하고 Restart는 해당 판의 모드·난이도를 유지한다. 아이템 모드는 설정과 무관하게 NORMAL이다.

## 난이도

| 값 | I 가중치 | 다른 6종 각각 | 레벨당 간격 감소 |
|---|---:|---:|---:|
| EASY | 12 | 10 | 160ms |
| NORMAL | 10 | 10 | 200ms |
| HARD | 8 | 10 | 240ms |

실제 게임은 WeightedBlockGenerator를 사용한다. 간격 하한은 250ms이며 계산은 기존 LevelPolicy/Difficulty에 위임한다. settings.json은 difficulty 문자열을 저장한다. 필드가 없는 기존 파일은 NORMAL이며 무효 필드는 경고 후 NORMAL로 대체하고 다른 설정은 살린다.

## 아이템

평가·플레이용 상세 규칙과 스크린샷은 [아이템 모드 명세서](item-specification.md)를 참고한다.

| 종류 | 표시 | 동작 |
|---|---|---|
| L | 흰 칸·검은 L | 해당 행 삭제, 자연 완성 행과 합산 |
| B | 흰 칸·검은 B | 중심 3×3의 실제 삭제 칸당 10점, 행 삭제와 중복 제외 |
| D | 흰 칸·검은 D | 획득 Lock과 다음 10 Lock의 행 보너스 2배, 재획득 시 10으로 갱신 |
| S | 흰 칸·검은 S | 다음 성공 Spawn 10개 간격 1.5배, 재획득 시 갱신 |
| W | 회색·W | 회전 불가, 하강 중 분쇄하고 바닥에 값 8로 고정 |

누적 삭제 줄의 10줄 경계마다 NEXT 채움에 아이템 1개를 예약한다. 9→13처럼 경계를 건너도 한 번 예약한다. 다섯 종류는 균등하며 L/B/D/S는 일반 블록의 채워진 칸 하나에 무작위 부착한다. 일반 칸은 O로 그린다. 현재 블록과 NEXT에 같은 렌더러를 사용하며 두 팔레트에서 아이템 표시는 동일하다. V와 아이템 모드 추가 난이도는 보류 항목이다.

삭제 대상은 150ms CLEARING 동안 흰색으로 보이며 이동 입력이 정지한다. Pause/Resume은 해당 상태와 효과 수명을 유지한다. 정보 영역은 MODE, DIFFICULTY, NEXT ITEM, x2, SLOW 잔여 수를 표시한다. SLOW 0은 현재 블록까지 적용되고 다음 Spawn에 만료한다. 상세 파이프라인은 [아이템 효과 계약](item-modifier-contract.md)을 따른다.

## 기록

CLASSIC EASY / CLASSIC NORMAL / CLASSIC HARD / ITEM NORMAL의 네 분류에 각각 Top 10을 유지한다. 좌우 키로 분류를 바꾼다. 게임 종료 후 해당 분류의 진입 여부를 판단하고 이름 등록 직후 해당 분류와 순위를 강조한다. 동점은 기존 기록 뒤에 삽입하며 가득 찬 분류의 10위와 동점이면 진입하지 않는다. Reset Scoreboard는 전체 분류를 지운다.

scoreboard.json은 각 기록의 mode·difficulty를 저장한다. 두 필드가 없는 기존 기록은 CLASSIC NORMAL로 읽는다. 잘못된 분류 값은 그 기록만 버리고 경고한다. ITEM 기록은 NORMAL로 정규화한다. long 점수와 기존 선택 필드의 호환 규칙을 유지한다.

## 재현 및 화면

```powershell
.\gradlew.bat test check guiAcceptance --no-daemon
.\gradlew.bat -I audit/requirements-audit.init.gradle requirementsAudit -PauditGui=true -PauditScreenshots=true --no-daemon
```

기본 check에 저장·픽셀 인수 테스트를 포함한다. Windows 패키징은 실제 JFrame 메뉴·생성기·결과 저장 연결도 guiAcceptance로 검증한다. LINE 70% 게이트를 유지한다. 보고서는 build/reports/tests 및 build/reports/jacoco에 생성된다.

build/screenshots의 PNG는 실제 GamePanel에 결정적인 상태를 넣어 생성한 렌더링 검증 화면이다. 실제 플레이 증거와 구분한다. LINE_CLEAR/BOMB/DOUBLE_SCORE/SLOW의 current·next 및 default·colorblind 파일로 표시를 비교할 수 있다.

보존한 검증 화면: [L](images/requirements-v2/LINE_CLEAR-current-default.png), [B](images/requirements-v2/BOMB-current-default.png), [D](images/requirements-v2/DOUBLE_SCORE-current-default.png), [S](images/requirements-v2/SLOW-current-default.png), [W](images/requirements-v2/W-current-default.png), [색맹 NEXT](images/requirements-v2/LINE_CLEAR-next-colorblind.png), [삭제 애니메이션](images/requirements-v2/CLEARING-next-default.png).

2026-10-26 최종 로컬 재검증: 기본 테스트 861건 중 854 통과·실제 창 7건은 headless 기본 실행에서 생략. guiAcceptance에서 해당 7건 모두 통과. 별도 감사 테스트 71건 모두 통과(최초 69건 중 63건 실패에서 수정 후 포커스·배율 2건 추가). LINE 커버리지 89.78%. releaseJar 및 Windows packageApp 생성 성공. EXE 창 생성·정상 종료(코드 0) 확인. 실제 사용자 평가·원격 CI·main Release 성공을 의미하지 않는다.

develop의 #98 입력·수명주기 수정도 통합했다. 스코어보드 분류 탐색·강조 복원 테스트와 최소 셀 12~20px의 두 지속 효과/조작 안내 겹침 방지 검증을 추가했다. Java UI 배율 100%/150%/200%의 실제 창 검증은 통과했으며 운영체제 배율·IME 수동 평가는 별도다. 전체 증거는 [검증 보고서](validation-report-2026-10-26.md)를 확인한다.

## 제출 전 남은 확인

- 서로 다른 사용자 2명의 실제 플레이 결과, 발견 결함과 재검증 기록
- 한글 IME, Windows DPI, 포커스 이탈, 배포 EXE의 수동 확인
- 교수자 원문과 v1.2 Q1/Q2/Q4의 최종 해석 확인
- 팀원 리뷰·병합, main 기준 Release (Notion 명세 게시 완료)

위 항목은 코드 자동 검증 통과와 별개이며 실제로 수행하기 전 완료로 표시하지 않는다.

[사용자 A/B 평가 기록지](user-playtest.md), [교수자 문의 초안](professor-confirmation.md), [최종 릴리스 확인표](release-checklist.md)를 준비했다. 실제 사용자 평가와 교수자 문의는 사용자의 미진행 답변을 반영했으며, 결과를 임의로 채우지 않았다.
