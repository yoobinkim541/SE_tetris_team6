# 3차 요구사항 추적 (A 트랙 관리)

- 원문: `TeamProject_Req3.pdf` (페이지 = PDF 페이지)
- 원본 추적표: Notion "3차 요구사항 · 원문·인수 기준·평가 추적". 이 문서는 ID를 그대로 쓰고 **코드 계약 위치**를 덧붙인다.
- 계약 코드: `src/main/java/tetris/feature/versus/api` · A 테스트: `src/test/java/tetris/versus/core`
- 검증 후보는 추가할 테스트 이름이며 통과 결과가 아니다. ✅ 표시만 현재 테스트가 있다.

| ID | 원문 | 인수 기준 | 담당 / 작업 | 계약 | 검증 |
|---|---|---|---|---|---|
| R3-MENU | p2~3,8 | 시작 메뉴에서 로컬 대전·P2P 각각 진입 | B / S8-B1, S9-B2 | — | VersusMenuTest |
| R3-MODES | p3,8 | 일반·아이템·시간제한 3종. P2P는 호스트만 선택 | A·B·C / S9-A1, S9-B1, S9-C1 | `BattleMode`, `MatchConfig` | ✅ MatchContractTest |
| R3-ATTACK | p4 | 2줄 이상 삭제 시 공격. 삭제 전 행에서 방금 고정한 블록 칸을 비운 형태 | A / S7-A2 | `AttackRowExtractor`, `AttackRows`, `LockResult` | ✅ AttackContractTest, AttackRowsTest, AttackPublicationTest, AttackBoundaryEngineTest |
| R3-ORDER | p4~6 | 먼저 대기열 표시. Lock·삭제 뒤 다음 Spawn 전에 아래쪽 삽입 | A / S8-A1 | `SpawnBoundary`, `MatchEngine` pulse 순서 | ✅ GarbageInsertionTest, AttackBoundaryEngineTest |
| R3-CAP | p6 | 최대 10줄. 기존 아래에 추가, 넘치면 최하단 버림, 10줄이면 무시 | A / S7-A2 | `PendingAttackQueue`, `PlayerView.pendingRows` | ✅ MatchContractTest, PendingAttackQueueTest, AttackPublicationTest, AttackBoundaryEngineTest |
| R3-GARBAGE | p5 | 형태 유지, 기존 칸 위로 이동, 회색으로 구분 | A·B / S8-A1, S8-B2 | `VersusRules.ATTACK_CELL`=9 | GarbageInsertionTest, VersusRenderingTest |
| R3-LOSS | p5 | 쌓을 수 없으면 상대 승리. 양쪽 같은 결과 | A·B / S8-A2, S8-B2 | `MatchResult`(TOPPED_OUT) | ✅ MatchContractTest, MatchResultTest, RematchLifecycleTest |
| R3-NOSCORE | p5 | 대전 기록은 스코어보드에 저장하지 않음 | B / S9-B1 | `MatchResult`는 onGameOver 경로를 쓰지 않음 | VersusNoScorePersistenceTest |
| R3-TIME | p3 | 마감 시 점수 높은 쪽 승리. 그 전 쌓기 불가도 종료 | A·B / S9-A1, S9-B1 | `MatchConfig.timeLimitMillis`, `MatchClock`, `DueTimeClock`, TIME_UP | ✅ DueTimeClockTest, TimedMatchTest |
| R3-ITEM | p3,5 | 아이템 대전에서 기존 아이템 등장·효과 유지 | A / S9-A2 | `BattleMode.itemsEnabled()` | ItemBattleTest |
| R3-HOST | p8 | 호스트/클라이언트, IP 표시·입력 | C·B / S8-C1, S9-B2 | C 로비 계약 | ConnectionFlowTest |
| R3-READY | p8 | 연결 안내, 호스트 모드 선택, 양쪽 시작 후 시작 | C·A / S8-C2, S9-C1 | C 로비 계약 + `MatchConfig` | ReadyHandshakeTest |
| R3-LIVE | p9 | 두 PC 모두 양쪽 보드·블록·점수 실시간 표시 | C·B / S9-C2, S9-B2, S10-B1 | `MatchSnapshot`, `BattleClient` | PeerSnapshotTest |
| R3-LATENCY | p9 | 동일 LAN 입력→표시 200 ms 이하 | C·B / S11-C1, S11-B2 | — | LanLatencyAcceptance |
| R3-REMATCH | p9 | 끝나면 연결 유지한 로비로, 양쪽 시작 후 새 판 | A·C / S10-A1, S10-C2 | `MatchEngine.start` 새 matchId | ✅ RematchLifecycleTest, RematchLobbyTest |
| R3-LAG | p10 | 지연 표시, 팀이 기준 정의 | C·B / S10-C1, S10-B2 | — | NetworkHealthTest |
| R3-DISCONNECT | p10 | 끊기면 오류 후 P2P 첫 화면 | C·B / S10-C1, S10-B2 | — | DisconnectFlowTest |
| R3-RECENT | p10 | 최근 성공 IP 저장 | C·B / S10-C2, S10-B2 | — | RecentPeerRepositoryTest |
| R3-NETTEST | p10 | 접속 절차 자동 테스트 | C / S7-C2~S11-C2 | — | loopback 통합 테스트 |
| R3-REGRESSION | p13~14 | 1·2차 전체 회귀 | A·B / S11-A1, S11-B1 | `SpawnBoundary.IMMEDIATE`로 싱글 무변경 | ✅ 기존 test·guiAcceptance, RandomInputStressTest(싱글 0..8), VersusStressTest(대전 0..9) |
| R3-COVERAGE | p13 | 전체 LINE ≥ 70% | C / S11-C2 | — | `gradlew check` 게이트 |
| R3-ITEMSPEC | p13 | 팀 아이템 명세, 대전 차이 명시 | A / S11-A2 | — | #75 / PR #111 문서 대조 |
| R3-DEMO | p14 | 느린 네트워크, 다른 팀 조작 시연 준비 | 전원 / S12 | — | 두 PC 리허설 |

도전 과제(컴퓨터 대전, 채팅, 다인 대전)는 필수 완료 후 별도 challenge 브랜치에서만 하고 main에 병합하지 않는다 (p12).
