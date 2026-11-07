# 3차 요구사항 — 대전 모드 (A 트랙 정리)

- 근거: `TeamProject_Req3.pdf`, 이슈 [#114](https://github.com/yoobinkim541/SE_tetris_team6/issues/114)
- 규칙 ID 중 R3-MODES·R3-ATTACK·R3-TIME·R3-CAP은 Notion/이슈의 ID다. `*` 표시가 붙은 ID는 이 문서에서 임시로 붙였으므로 Notion ID가 확정되면 맞춘다.
- 계약 코드: `src/main/java/tetris/feature/versus/api` · 테스트: `src/test/java/tetris/versus/api`

| ID | 원문 요약 (슬라이드) | 수용 기준 | 계약 / 담당 |
|---|---|---|---|
| R3-MODES | 시작 메뉴에서 대전 선택. 일반·아이템·시간제한 중 하나 (p3) | 대전 모드 3종을 고를 수 있고, 싱글 `GameMode`와 섞이지 않는다 | `VersusMode`, `MatchConfig` · A |
| R3-ATTACK | 2줄 이상 지우면, 지운 줄에서 **마지막 블록 칸을 뺀** 모양이 상대에게 간다. 대기 영역에 표시되고, 현재 블록 처리 뒤 다음 Spawn 전에 보드 아래에 추가된다. 회색으로 표시 (p4–5) | 1줄 삭제는 공격 없음. 넘어간 줄의 빈칸은 마지막 블록 모양. 추가는 Spawn 직전에만 | `VersusRules.MIN_ATTACK_LINES`, `ATTACK_CELL`, `PreSpawnHook` · A(S7-A2), 표시 B |
| R3-CAP | 최대 10줄. 여러 번의 공격은 기존 줄 아래에 붙는다. 이미 10줄이면 무시하고, 합쳐서 10줄을 넘으면 가장 아래쪽을 잘라 낸다 (p6) | 대기열은 항상 0–10줄 | `VersusRules.MAX_PENDING_ROWS`, `PlayerView.pendingRows` · A(S7-A2) |
| R3-TIME | 시간제한 모드: 블록을 더 쌓지 못하면 끝나는 조건에 더해, 타이머가 끝나면 점수가 높은 쪽이 승리 (p3) | 남은 시간 표시, 0이 되면 점수 비교 | `MatchConfig.timeLimitMillis`, `MatchClock`, `MatchResult.EndReason.TIME_UP` · A |
| R3-RESULT* | 쌓지 못한 쪽이 패배. 승자를 화면에 표시. 대전은 스코어보드에 기록하지 않음 (p5) | 승자 표시, 스코어보드 미기록 | `MatchResult`, `MatchSnapshot.result` · A, 표시 B |
| R3-INPUT* | 한 창에서 보드 2개면 키 입력을 창 전체에서 받아 각 보드로 나눔 (p7) | 두 사람 키가 동시에 동작 | `PlayerCommand` · B(S7-B2 완료) |
| R3-P2P-LOBBY* | 서버/클라이언트 선택, 서버는 IP 표시 후 대기, 클라이언트는 IP 입력, 연결되면 안내, 서버가 모드 선택, 둘 다 '게임 시작'을 누르면 시작 (p8) | 단계별 안내 문구, 모드 선택은 호스트만 | `LobbyClient`, `LobbySnapshot`, `LobbyPhase` · 구현 C, 화면 B |
| R3-P2P-SYNC* | 양쪽 화면 모두 표시. 같은 LAN에서 입력→표시 지연 200 ms 이하 (p9) | 지연 측정 테스트 | `BattleClient` + C 프로토콜 STATE · C |
| R3-P2P-END* | 끝나면 승패 표시 후 연결 직후의 대기 상태로 돌아가 다시 시작 가능 (p9) | FINISHED → `LobbyPhase.CONNECTED` | `LobbyClient`, `FakeLobbyClient.matchFinished` · C |
| R3-P2P-NET* | 지연 시 '랙' 표시, 일정 시간 끊기면 오류 후 P2P 첫 화면. 기준은 팀이 정의 (p10) | `LobbyPhase.FAILED` → IDLE | C (S10-C1) |
| R3-P2P-RECENT* | 최근 접속 IP 저장, 클라이언트가 골라 입력 (p10) | 최신순 목록 | `LobbySnapshot.recentHosts` · C |
| R3-P2P-TEST* | 네트워크 접속 과정 자동화 테스트 (p10) | CI에서 실행 | C (S7-C2 진행 중) |
| R3-NFR* | 기말 Line Coverage 70%, 리뷰·리팩토링·버전관리 평가, 2차 아이템 명세 평가 (p13) | `gradlew check` 커버리지 게이트 | 전원 |

도전 과제(컴퓨터 대전, 채팅, 다인 대전)는 원문대로 **별도 branch에서만** 개발하고 main에 merge하지 않는다 (p12).
