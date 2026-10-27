# 평가 체크리스트 80항목 대조·보완 결과

기준일: 2026-10-27. 대상: `fix/checklist-compliance` 브랜치. 원본: [Requirements_CheckList_Intermediate_26 / 시트 1](https://docs.google.com/spreadsheets/d/1847XgHhucPiS1QZ23Hl1oCWT2MuSXmjq2V7wriGUftM/edit?gid=1911909482). 읽은 범위는 A1:G180이며 실제 요구사항은 80개다. [원문 스냅샷](checklist-source-2026-10-27.json)을 보존했다. 원본 시트는 수정하지 않았고, 원본의 O 표시는 이번 감사 결과로 사용하지 않았다.

## 판정 범위

- **구현·근거 확인:** 코드 경로와 아래 테스트/실행 증거가 있다. 모든 사용 환경과 실제 사람의 시각·조작 평가를 보증하지 않는다.
- **부분 검증:** 자동 검증은 수행했지만 원문의 하드웨어 조건 전체를 재현하지 못했다.
- **외부 확인 필요:** 도구 대체 허용 또는 실제 팀 관리 자료 확인이 남아 있다.

80개 중 77개는 구현·근거를 확인했다. **6-4·6-5는 최소 사양 장비에서의 검증이 남았고, 6-7은 Trello/Notion 관리 증빙 확인이 남는다.** '전부 충족'으로 표시하지 않는다. 색맹 사용자 관점의 용이성, 실제 OS 키보드/IME, 마우스 더블클릭은 소프트웨어 경로 검증과 구분한다.

## 발견 및 보완

1. **2-19/20/21 확률:** 감사 시작 시 develop에는 기존 I 가중치12/10/8이 남아 있었다. 이 방식의 실제 확률은16.67%/14.29%/11.76%로 정확한 상대 ±20%가 아니었다. PR #110의 수정 커밋을 통합해 합계210의 가중치(EASY I36·나머지29, NORMAL 각30, HARD I24·나머지31)와 독립 기대 확률 테스트를 적용했다. 실제 확률은 각각6/35,1/7,4/35다. 원래 구현의 엄밀한 확률 테스트 실패는 PR #110에서 재현했다.
2. **2-12/13·6-5/10 검증 공백:** 기존 스트레스의 평균1ms 검사는 개별 최대 지연을 입증하지 못했다. 각 엔진 명령의 최댓값을1초 미만으로 검사하고, 일반/아이템 모드의7개 키를334ms 간격으로3회 입력해 실제 바인딩→EDT→모델→렌더링 경로의 상태 변화와 개별1초 미만을 검사했다. 실제 OS 키보드 이벤트를 보낸 실측은 아니다.
3. **2-29~31·3-5~7 인수 공백:** 실제 세션에서 Hard Drop으로 Game Over에 도달한 뒤 실제 Enter 바인딩·이름 필드 제출로 점수판·분류·강조·저장·메뉴 복귀를 검사한다. 순위 미진입 경로도 검사한다. GameOver에서 Enter를 누르는 기존 안내 흐름은 유지한다.
4. **6-4 자원 증거:** 패키지 설치 디렉터리의500,000,000바이트 한도를 실행 전 검사한다. 한도를1바이트로 주면 거부되는 부정 검증도 수행했다. EXE 창 프로세스의 시작 메모리를 관측한다. 별도 `resourceAcceptance`는 Java heap256MB로 실제 JFrame·입력 지연·24만 명령 스트레스를 실행하며 Windows CI에 연결했다. OS 전체RAM1GB 장비 인증으로 표현하지 않는다.
5. **5-12/14/16 명세:** develop에 병합된 [독립 아이템 명세](item-specification.md)를 B/D/S의 제출 근거로 확인했다.
6. **Java21 검증 도중의 JVM 종료:** 한 차례 GUI 검증 JVM의 C2 컴파일러가 JaCoCo의 ClassReader 코드를 컴파일하던 중 네이티브 접근 위반으로 종료됐다. 이 위치만으로 근본 원인을 단정하지 않는다. 기본 test의 계측·LINE70% 게이트는 유지하고, 커버리지 집계에 쓰지 않는 별도 GUI·자원·감사 태스크의 중복 계측을 제거했다. 초기화 스크립트에서는 플러그인 적용 이후 태스크 확장을 설정한다. 재검증 결과는 아래 집계에 반영하며 원본 진단은 로컬 build/verification에 보존했다.

## 80항목 대조표

근거의 테스트 클래스는 `src/test/java/tetris/{feature,ui}` 및 `audit/java/tetris/{feature,ui}`에 있다. 테스트 결과는 동일 요구사항에 중복으로 연결되며 항목별로 별도 사람 평가를 수행했다는 의미가 아니다.

| 번호 | 원문 요구사항 | 판정 | 구현·검증 근거와 한계 |
|---|---|---|---|
| 1-1 | 메뉴 화면 상단에 게임의 이름이 표시 | 구현·근거 확인 | MainMenuPanel·Messages / MenuPanelsTest·ProductionWiringAuditTest |
| 1-2 | 게임 시작 메뉴 표시 | 구현·근거 확인 | MainFrame 시작 메뉴 / actualMainMenuContainsFiveEntries |
| 1-3 | 아이템모드 게임 시작 메뉴 표시 | 구현·근거 확인 | 실제 ITEM 메뉴 실행 / actualItemMenuActionStartsItemAtNormalDifficulty |
| 1-4 | 설정 메뉴 표시 | 구현·근거 확인 | MainFrame.openSettings / MenuPanelsTest |
| 1-5 | 스코어 보드 표시 | 구현·근거 확인 | MainFrame.openScoreboard / ScoreboardNavigationTest |
| 1-6 | 게임 종료 메뉴 표시 | 구현·근거 확인 | MainFrame.exitApplication / 메뉴 콜백 검사; 실제 EXE 정상 닫기 |
| 1-7 | 키보드를 이용하여 메뉴 사이를 이동 | 구현·근거 확인 | MainMenuPanel 키 바인딩 / MenuPanelsTest |
| 1-8 | 엔터 키를 이용하여 메뉴 선택 또는 다른 키를 사용하면 사용가능한 키를 표시 | 구현·근거 확인 | 메뉴 Enter 바인딩·하단 안내 / MenuPanelsTest·MessagesAndPaletteTest |
| 1-9 | 각 메뉴 선택시 알맞은 동작이 실행 | 구현·근거 확인 | 실제 메뉴→CLASSIC/ITEM·설정/기록 연결 / ProductionWiringAuditTest·MenuPanelsTest |
| 1-10 | 메뉴가 차후 추가되기 쉬운 구조로 설계 | 구현·근거 확인 | List<MenuItem>·label/action 기반 메뉴 구성 / MainMenuPanel·MainFrame 코드 확인 |
| 2-1 | 20줄, 10칸의 보드가 존재 | 구현·근거 확인 | BoardTest·RequirementsVisualAuditTest / WIDTH=10, HEIGHT=20 |
| 2-2 | 다음 블럭을 확인할 수 있는 부분이 존재 | 구현·근거 확인 | GameSnapshot.nextBlock·GamePanel.drawNext / ItemSnapshotTest·RenderingTest |
| 2-3 | 현재 점수를 확인할 수 있는 부분이 존재 | 구현·근거 확인 | GameSnapshot.score·GamePanel.drawInfo / RequirementsVisualAuditTest |
| 2-4 | 2-2에는 2-1의 보드에 새로운 현재 블럭이 생성될 때마다 그 다음에 생성될 블럭이 정확히 표시됨 | 구현·근거 확인 | NextBlockQueue·Spawn / BlockGenerationTest·ItemPendingTest |
| 2-5 | 2-3은 실시간으로 바뀌는 점수를 표시 | 구현·근거 확인 | GameListener→GamePanel.onChanged / ChecklistInputLatencyTest·RequirementsVisualAuditTest |
| 2-6 | 삭제되는 줄이 잘 확인될 수 있는 표시가 존재함 | 구현·근거 확인 | 150ms CLEARING·흰 삭제 칸 / ClearingAnimationTest·RequirementsVisualAuditTest |
| 2-7 | 보드에 현재 블럭이 생성되고 나면, 이 블럭은 아무 조작없이도 1초에 1줄씩 밑으로 떨어짐 | 구현·근거 확인 | LevelPolicy 초기 간격1000ms / LevelPolicyTest·LockPipelineTest |
| 2-8 | 일정 수 이상의 블럭이 생성되었거나, 줄이 삭제되었으면 떨어지는 속도가 증가 | 구현·근거 확인 | 블록·행 카운터에 따른 속도 증가 / LevelPolicyTest·StrictLifecycleAuditTest |
| 2-9 | 현재 블럭을 키보드로 좌, 우, 아래 한 칸씩 이동이 가능해야 함 | 구현·근거 확인 | 좌우·Soft Drop / MoveTest·ChecklistInputLatencyTest |
| 2-10 | 현재 블럭을 시계방향으로 90도 회전시킬 수 있어야 함 | 구현·근거 확인 | 시계 방향 회전 / BlockTest·SpawnAndRotationTest·ChecklistInputLatencyTest |
| 2-11 | 현재 블럭을 한 번에 끝까지 밑으로 떨어뜨릴 수 있어야 함 | 구현·근거 확인 | Hard Drop / HardDropTest·ChecklistInputLatencyTest |
| 2-12 | 키 입력이 1초에 3번 이하로 반복되는 경우, 해당 키 입력이 모두 정확히 블럭의 움직임에 반영되어야 함 | 구현·근거 확인 | 334ms 간격 press/release 3회·7동작·2모드 / ChecklistInputLatencyTest |
| 2-13 | 2-12의 반복된 키 입력과 블럭의 움직임 사이에 발생하는 지연은 1초 미만이어야 함 | 구현·근거 확인 | EDT 대기→바인딩→모델→오프스크린 렌더링 각각 <1s / ChecklistInputLatencyTest (OS 키보드 지연은 별도) |
| 2-14 | 게임 도중 게임을 일시 중단할 수 있는 키가 존재해야 함 | 구현·근거 확인 | Pause 키 / GameSessionFlowTest·KeyBindingManagerTest·ChecklistInputLatencyTest |
| 2-15 | 게임이 일시 중단된 상태에서 다시 재개할 수 있는 키가 존재해야 함 | 구현·근거 확인 | Resume 키 / GameSessionFlowTest·StrictLifecycleAuditTest |
| 2-16 | 2-14, 2-15의 상태에서 OS의 프로세스 종료 등을 제외하고 게임 자체의 종료 방법이 존재해야 함 | 구현·근거 확인 | Q 확인·Pause 메뉴 Quit / GameSessionFlowTest·MenuPanelsTest·StrictLifecycleAuditTest |
| 2-17 | 총 7가지의 테트리스 기본 블럭이 모두 존재해야 함 | 구현·근거 확인 | BlockType.ordinary 7종·모양 / BlockTest·BlockGenerationTest |
| 2-18 | 다음에 나타날 블럭은 normal 난이도에서 무작위로 결정되어야 함 | 구현·근거 확인 | NORMAL 1/7 실제 생성 / WeightedBlockGeneratorTest |
| 2-19 | Easy 난이도에서 I형 블럭이 20% 더 자주 등장해야 함 | 구현·근거 확인 | EASY I=6/35 (NORMAL×1.2) / 정확한 비율 및 독립 기대 분포 테스트 |
| 2-20 | Hard 난이도에서 I형 블럭이 20% 덜 등장해야 함 | 구현·근거 확인 | HARD I=4/35 (NORMAL×0.8) / 정확한 비율 및 독립 기대 분포 테스트 |
| 2-21 | 2-18, 2-19, 2-20의 확률을 확인할 수 있는 단위 테스트가 존재해야 함 | 구현·근거 확인 | WeightedBlockGeneratorTest·WeightedRandomTest / 요구사항 번호 표시·고정 시드·분포 검사 |
| 2-22 | 각각의 블럭은 모두 다른 색상을 가져야 함 | 구현·근거 확인 | 기본/색맹 각각 7개 서로 다른 색 / MessagesAndPaletteTest |
| 2-23 | 유사한 모양을 지닌 블럭은 구분이 용이하도록 대비되는 색상을 가져야 함 | 구현·근거 확인 | S/Z·J/L 등 대비 팔레트 구성 / BlockPalette 코드·MessagesAndPaletteTest (시각적 용이성은 사용자 평가) |
| 2-24 | 색맹모드를 선택한 경우 적녹생맥 및 청황색맹도 블럭 구분이 쉽도록 블럭 표시가 바뀌어야 함 | 구현·근거 확인 | 색맹 설정 시 Okabe-Ito 계열 팔레트로 변경 / RenderingTest·MessagesAndPaletteTest (실제 적녹/청황 색각 사용자 검증과 구분) |
| 2-25 | 블럭이 1칸 떨어질 때마다 한 단위의 점수 획득 | 구현·근거 확인 | 자동/Soft 하강1칸×Level / ScoringPolicyTest·MoveTest |
| 2-26 | 2-25에서 획득하는 점수는 자동으로 떨어지거나 조작을 통해 떨어지더라도 차이가 없어야 함. | 구현·근거 확인 | tick/moveDown 공통 하강 경로 / ScoringPolicyTest·LockPipelineTest |
| 2-27 | 2-8에 따라 블럭이 떨어지는 속도가 빨라지면 추가 점수를 부여해야 함 | 구현·근거 확인 | Level에 따른 칸당 점수 증가 / ScoringPolicyTest·LevelPolicyTest |
| 2-28 | 그 외 정해진 기준에 따라 점수를 가산/감산하는 방식이 반드시 1개 이상 존재 | 구현·근거 확인 | 행 삭제 보너스100/300/500/800 / ScoringPolicyTest·ModifierSessionTest |
| 2-29 | 더 이상 블럭을 쌓을 수 없게 되면 게임이 종료되어야 함 | 구현·근거 확인 | Spawn 충돌 Game Over / LockPipelineTest·ProductionWiringAuditTest 실제 세션 |
| 2-30 | 게임 종료시 3-1의 스코어 보드를 표시함 | 구현·근거 확인 | GameOver Enter→이름(진입 시)→해당 분류 점수판 / 신규 qualifying·nonqualifying 실제 창 경로 테스트 |
| 2-31 | 스코어 보드가 닫히면 현재 게임을 종료하고 시작 메뉴로 이동하거나 프로그램을 종료함 | 구현·근거 확인 | 점수판 Main Menu/Exit / ProductionWiringAuditTest·ScoreboardNavigationTest |
| 3-1 | 스코어 보드는 점수가 높은 순으로 이름 및 점수를 보여줘야 함 | 구현·근거 확인 | 점수 내림차순·이름 표시 / ScoreboardTest·RenderingTest |
| 3-2 | 역대 순위를 최소 상위 10개 이상 보여줘야 함 | 구현·근거 확인 | 4개 분류 각각 Top10 / ScoreboardCategoryTest |
| 3-3 | 스코어 보드에서는 각 점수가 어떤 난이도에서 기록된 것인지 확인할 수 있어야 함 | 구현·근거 확인 | CLASSIC EASY/NORMAL/HARD·ITEM NORMAL 탭 / ScoreboardNavigationTest |
| 3-4 | 스코어 보드에서는 각 점수가 어떤 모드(일반/아이템)에서 기록된 것인지 확인할 수 있어야 함 | 구현·근거 확인 | CLASSIC/ITEM 분류 표시 / ScoreboardNavigationTest·RequirementsVisualAuditTest |
| 3-5 | 2-30에서 현재 게임 점수가 순위 내에 있어 기록이 필요한 경우, 이름을 입력 받아 점수를 저장 | 구현·근거 확인 | 진입 시 이름 입력·비진입 생략 / 신규 ProductionWiringAuditTest |
| 3-6 | 3-5에서 이름을 입력 받고 나면 스코어 보드가 업데이트 되고, 입력된 이름 및 기록한 점수를 강조하여 보여줌 | 구현·근거 확인 | 등록 순위 강조·분류 전환 복원 / ProductionWiringAuditTest·ScoreboardNavigationTest |
| 3-7 | 스코어 보드는 4-3에서 초기화되기 전에는 프로그램이 종료되어도 점수를 저장하여 기록을 유지해야 함 | 구현·근거 확인 | JSON 기록 재로드 / PersistenceTest·RequirementsDataAuditTest·ProductionWiringAuditTest |
| 4-1 | 테트리스 게임의 윈도우 크기를 최소 3가지 이상의 미리 정의된 크기로 변경하는 기능 존재 | 구현·근거 확인 | SMALL/MEDIUM/LARGE·화면 맞춤 / ScreenSizeFitTest·ProductionWiringAuditTest |
| 4-2 | 게임 조작을 위해 사용될 키를 설정하는 기능 존재 | 구현·근거 확인 | 7개 키 설정·중복/예약키 거부 / KeyMapTest·MenuPanelsTest·KeyBindingManagerTest |
| 4-3 | 스코어 보드 기록 초기화하는 기능 존재 | 구현·근거 확인 | 전체 분류 점수 초기화 / ScoreboardCategoryTest·MenuPanelsTest·PersistenceTest |
| 4-4 | 2-24를 위한 색맹모드 선택 기능 존재 | 구현·근거 확인 | 색맹 ON/OFF 설정 / MenuPanelsTest·RenderingTest |
| 4-5 | 설정은 프로그램이 종료되더라도 유지되어야 함 | 구현·근거 확인 | 설정·키·난이도 JSON 재로드 / PersistenceTest·RequirementsDataAuditTest |
| 4-6 | 설정을 초기화할 방법이 존재해야 함 | 구현·근거 확인 | Reset Settings / KeyMapTest·MenuPanelsTest·PersistenceTest |
| 5-1 | 10개의 줄을 삭제할 때마다 아이템이 나타나야 함 | 구현·근거 확인 | 누적10줄 경계마다 새 NEXT에 1개 예약 / ItemPendingTest·StrictLifecycleAuditTest |
| 5-2 | 2-2에도 다음 블럭으로 아이템이 포함된 블럭이나 아이템이 표시됨 | 구현·근거 확인 | 현재·NEXT 아이템 표시 / ItemSnapshotTest·RequirementsVisualAuditTest |
| 5-3 | 각 아이템은 구분이 쉽도록 특징적인 모양이나 서로 다른 문자를 이용해 표시 | 구현·근거 확인 | L/B/D/S 문자와 W 특수 모양 / RequirementsVisualAuditTest·WeightBlockTest |
| 5-4 | 문자 'L'로 표시되는 줄 삭제 아이템이 존재 | 구현·근거 확인 | LINE_CLEAR 문자L / LineClearItemTest·RequirementsVisualAuditTest |
| 5-5 | 5-4 아이템이 포함된 블럭이 바닥에 닿거나 더 이상 움직이지 못하게 되면, L이 있는 줄을 모두 삭제함 | 구현·근거 확인 | Lock 시 L행 삭제 / LineClearItemTest·ItemEffectHookTest |
| 5-6 | 5-4 아이템의 L은 블럭에서 무작위 위치에 표시됨 | 구현·근거 확인 | 채워진4칸 중 균등 부착·회전 유지 / BlockTest·ItemBlockGeneratorTest |
| 5-7 | 5-4 아이템으로 삭제되는 줄에 대해서도 기존 방식대로 점수가 계산되어야 함 | 구현·근거 확인 | 자연 완성 행과 합산·중복 제거·기존 행점수 / ItemEffectHookTest·ModifierSessionTest |
| 5-8 | 너비가 4칸인 무게추 아이템이 존재해야 함 | 구현·근거 확인 | 너비4·높이2 무게추 / WeightBlockTest |
| 5-9 | 5-8 아이템이 떨어지며 지나치는 모든 블럭을 삭제해야 함 | 구현·근거 확인 | 경로의 고정 칸 분쇄 / WeightCrushTest |
| 5-10 | 5-8 아이템은 처음에는 좌우로 움직일 수 있으나, 바닥이나 다른 블럭에 닿으면 더 이상 좌우로 움직일 수 없어야 함 | 구현·근거 확인 | 접촉 전 좌우 허용·분쇄 중 잠금 / WeightCrushTest |
| 5-11 | 직접 설계/구현한 아이템1이 잘 동작해야 함 | 구현·근거 확인 | 자체 아이템1 B(폭탄) / BombItemTest |
| 5-12 | 5-11 아이템에 대한 명세를 Trello나 기타 문서 등으로 제시할 수 있어야 함 | 구현·근거 확인 | 아이템 명세 §3.2·검증 화면 / docs/item-specification.md |
| 5-13 | 직접 설계/구현한 아이템2가 잘 동작해야 함 | 구현·근거 확인 | 자체 아이템2 D(점수2배) / DoubleScoreItemTest·GameModifiersLifecycleTest |
| 5-14 | 5-13 아이템에 대한 명세를 Trello나 기타 문서 등으로 제시할 수 있어야 함 | 구현·근거 확인 | 아이템 명세 §3.3·검증 화면 / docs/item-specification.md |
| 5-15 | 직접 설계/구현한 아이템3이 잘 동작해야 함 | 구현·근거 확인 | 자체 아이템3 S(슬로우) / SlowItemTest·ModifierSessionTest |
| 5-16 | 5-15 아이템에 대한 명세를 Trello나 기타 문서 등으로 제시할 수 있어야 함 | 구현·근거 확인 | 아이템 명세 §3.4·검증 화면 / docs/item-specification.md |
| 6-1 | 게임은 Java 21 버전이 설치된 윈도우즈 11 또는 macOS 26이상 OS에서 실행할 수 있어야 함 | 구현·근거 확인 | Windows11 Home·Temurin21.0.12.1에서 전체/GUI/인수·EXE 검증; macOS는 이번 검증 대상 아님(원문 OR) |
| 6-2 | 작성된 코드를 손쉽게 빌드하고 아이콘이 포함된 실행파일로 패키징하여 배포할 수 있어야 함 | 구현·근거 확인 | Gradle packageApp·.ico·JRE 포함 ZIP / 실제 Java21 jpackage 빌드 |
| 6-3 | 별다른 명령어의 입력 등이 없이 GUI를 이용하는 OS에서 마우스 클릭으로 게임이 실행가능해야 함 | 구현·근거 확인 | EXE 직접 실행·실제 창 생성·정상 종료0 / scripts/verify-package.ps1 (OS 더블클릭 동작 자체는 수동 확인) |
| 6-4 | 1.2GHz이상 프로세서, 1GB RAM, 500MB의 여유 디스크 공간을 지닌 PC에서 오류없이 실행 가능해야 함 | 부분 검증 | 설치 크기500,000,000B 게이트·256MB Java heap 테스트·시작 메모리 관측; 1.2GHz/총RAM1GB 실기 미검증 |
| 6-5 | 6-4의 환경에서 게임의 모든 동작 및 사용자 조작이 1초 미만의 지연만 발생하고 이루어져야 함 | 부분 검증 | 개별 엔진+EDT/렌더링 <1s·256MB heap 검증; 6-4 최소 장비에서 같은 결과인지는 미검증 |
| 6-6 | 개발단계에서 각 버전들은 VCS를 이용하여 관리되어야 함 | 구현·근거 확인 | Git 커밋·브랜치·태그 / origin/develop·main·PR 이력 |
| 6-7 | 프로젝트 진행상황이 Trello에서 관리되고, 각 팀원별 업무 분담을 확인할 수 있어야 함 | 외부 확인 필요 | README에 Notion 프로젝트 링크·GitHub A/B/C 담당 이슈. 원문 Trello 대체 허용 및 최신 Notion 업무분담 실자료 미검증 |
| 6-8 | 프로젝트에서 발생한 이슈가 하나 이상 GitHub에 등록되어 처리 상황을 확인할 수 있어야 함 | 구현·근거 확인 | GitHub 이슈 #37/#38 및 #41~#75와 처리 상태·연결 PR 존재 |
| 6-9 | 작성된 코드를 검증할 수 있는 단위 테스트가 존재하여, Line Coverage가 작성한 코드의 50% 이상이 되어야 함 | 구현·근거 확인 | JaCoCo LINE ≥70% 게이트(요구50%보다 강화) / build.gradle·전체 check |
| 6-10 | 비기능적 요구사항의 충족여부를 검증할 수 있는 자동실행 가능한 테스트가 하나 이상 존재해야 함 | 구현·근거 확인 | RandomInputStressTest·ChecklistInputLatencyTest·resourceAcceptance·패키지 스모크 자동 실행 |

## 검증 결과와 재현

| Java 21 / Windows 11 검증 | 결과 |
|---|---|
| 기본 test/check | 897 실행, 888 통과, headless GUI 9 생략, 실패/오류 0 |
| guiAcceptance | 실제 JFrame 9 통과, 생략/실패 0 |
| requirementsAudit | 73 통과, 생략/실패 0 |
| resourceAcceptance | heap 256MB에서 23 통과, 생략/실패 0 |
| LINE 커버리지 | 1,986 / 2,204줄 = 90.11%, 70% 게이트 통과 |
| 제한 heap 입력 최대 지연 | CLASSIC 24.945ms / ITEM 5.532ms (각 21회, OS 키보드 전달 제외) |
| Java 21 Windows 패키지 설치 크기 | 160,792,669바이트, 500,000,000바이트 이내 |
| Java 21 EXE | 실제 창 생성·정상 종료 코드 0, 시작 시 관측 메모리 약 123MB |

이 수치는 이번 로컬 검증 결과이며 새 브랜치의 GitHub CI 성공을 미리 의미하지 않는다.

Java21·Windows11에서 실행한 상세 집계와 패키지 해시는 [검증 결과 JSON](checklist-verification-2026-10-27.json)에 기록한다. `build/reports/tests/{test,guiAcceptance,resourceAcceptance,requirementsAudit}/index.html`에 실행 보고서가 생성된다. 기본 test에서 GUI 테스트를 생략한 수와 별도 guiAcceptance에서의 통과 수를 구분한다.

```powershell
# JDK 21을 JAVA_HOME으로 지정한 Windows 11에서 실행
.\gradlew.bat test check guiAcceptance resourceAcceptance releaseJar packageApp -I audit/requirements-audit.init.gradle requirementsAudit -PauditGui=true --no-daemon
.\scripts\verify-package.ps1
```

`resourceAcceptance`의256MB는 Java heap 상한이다. JVM 네이티브 메모리와 OS 메모리까지256MB라는 뜻은 아니다. 패키지 스모크 메모리는 시작 시점의 관측이며 전체 플레이 최대치가 아니다. CI 성공과 실제 최소 장비 인증을 혼동하지 않는다.

## 남은 확인과 평가 절차

| 대상 | 완료하려면 필요한 증거 |
|---|---|
| 6-4 | 1.2GHz 이상 CPU·총RAM1GB·여유500MB를 가진 최소 사양 환경에서 ZIP/EXE 실행, 모드별 플레이·저장·종료, 오류 여부와 환경 기록 |
| 6-5 | 같은 환경에서 입력·낙하·삭제·메뉴·저장 지연의1초 미만 여부 확인. 현재 자동 지연 검증은 실행한 호스트/CI 범위 |
| 6-7 | 실제 프로젝트 보드의 팀원별 업무분담·진행 상태 제시. Notion으로 대체 가능한지 확인; Trello 필수라면 팀이 사용할 Trello 보드 준비 |
| 사람 평가 | [사용자2명 평가 기록지](user-playtest.md)에 실제 결과 기록. 이80항목 시트 자체의 별도 번호는 아니지만 기존 프로젝트 제출 조건에 남아 있음 |
| 원문 해석 | [교수자 문의 기록](professor-confirmation.md). 이슈 종료 또는 문서 작성이 문의 발송·승인을 뜻하지 않음 |

이번 브랜치의 변경은 리뷰·병합 전이며, 기존 공개 릴리스가 이 수정까지 포함한다고 표시하지 않는다. develop/main 반영과 새 패키지 배포는 별도 단계다.


