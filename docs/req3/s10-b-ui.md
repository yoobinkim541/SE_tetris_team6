# S10 B 트랙: P2P 두 보드·3모드 표시(S10-B1)와 지연·끊김·최근 IP·재대전 안내(S10-B2)

- 소유: B 화면·입력. 코드: `src/main/java/tetris/ui/versus`, `src/main/java/tetris/ui/MainFrame.java`(대전 연결부)
- 근거: [#155 S10-B1](https://github.com/yoobinkim541/SE_tetris_team6/issues/155), [#156 S10-B2](https://github.com/yoobinkim541/SE_tetris_team6/issues/156),
  규칙 R3-LIVE, R3-MODES, R3-TIME, R3-LAG, R3-DISCONNECT, R3-RECENT, R3-REMATCH
- C 계약: `LobbyClient#subscribeHealth`, `LinkHealth`, `SocketLobbyClient#recentPeers` ([s10-c-network.md](s10-c-network.md))

## 1. 경기 화면 (S10-B1)

| 항목 | 로컬 대전 (viewer 없음) | P2P 호스트 (viewer=P1) | P2P 클라이언트 (viewer=P2) |
|---|---|---|---|
| 보드 제목 | `P1` / `P2` | `P1 (나)`·강조 테두리 / `P2 (상대)` | `P1 (상대)` / `P2 (나)`·강조 테두리 |
| 상태 줄 | `경기 중`, `일시정지`, `P2 승리 · 시간 종료` | 결과를 `승리!` / `패배` / `무승부`로 | 같음 (자기 관점) |
| 결과 화면 점수 | `P1: n   P2: m` | `P1 (나): n   P2 (상대): m` | `P1 (상대): n   P2 (나): m` |
| 범례 | 아이템 대전에서만 `L 줄 삭제 · B 폭탄 · D 점수 2배 · S 느리게 · W 무게추` | 같음 | 같음 |

- 일시정지·준비(COUNTDOWN) 동안 두 보드를 어둡게 덮고 크게 표시한다. 일시정지는 누가 눌러도 두 보드가 함께 풀린다(D-03).
- 키 입력은 기존대로 자기 참가자만 보낸다. 호스트는 `HostBattleClient`가 P1로, 클라이언트는 `ClientBattleClient`가 P2로 바꿔 보낸다.
- 화면 문구를 한국어로 통일했다 (`Countdown/Playing/Paused` → `곧 시작합니다/경기 중/일시정지`, `Next/Queue` → `다음/공격 대기`).

## 2. 연결 상태 안내 (S10-B2)

| 로비 `LinkHealth` | 로비 화면 | 경기 화면 |
|---|---|---|
| `NORMAL` | 평소 안내 | 안내 없음 |
| `LAGGING` | 안내 끝에 `네트워크 지연 중 (응답 Nms)` | 위쪽 가운데 주황색 같은 문구. 회복(`NORMAL`)되면 지운다 |
| `PEER_LEFT` | P2P 첫 화면, `상대가 나갔습니다.` (일반 색) | 경기 중이면 첫 화면으로 이동 |
| `LOST` | P2P 첫 화면, `상대와 연결이 끊어졌습니다. 같은 LAN인지, 방화벽을 확인한 뒤 다시 접속하세요.` (경고색) | 경기 중이면 첫 화면으로 이동 |

- 로비는 INITIAL 스냅샷을 먼저, 종료 상태(`PEER_LEFT`/`LOST`)를 나중에 알린다. 화면은 먼저 `상대와 연결이 끊어졌습니다.`를 띄우고 종료 상태가 오면 문구를 구체화한다.
- 결과가 이미 나온 뒤 상대가 나가면 결과 화면은 그대로 두고, `로비로`/`다시 대전`을 누르면 첫 화면 안내가 보인다.
- 리뷰 중 발견: 기존에는 `leave()`가 소켓만 닫아 상대가 항상 `LOST`를 받았다. C 브랜치에서 LEAVE를 보낸 뒤 닫도록 고쳤다.

## 3. 최근 IP와 재대전 (S10-B2)

- `최근 IP` 목록: 화면을 열 때 `recent-peers.json`(최신순 최대 5개)을 한 번 읽는다. 고르면 상대 IP 칸에 들어간다. 파일을 못 읽어도 직접 입력으로 접속할 수 있다.
- 클라이언트로 접속에 성공(LOBBY 진입)하면 그 주소를 목록 맨 앞으로 옮긴다. 실패·취소·호스트는 바꾸지 않는다. 디스크 저장은 로비(C)가 별도 스레드에서 한다.
- 결과 화면(P2P): `다시 대전`(Enter) / `로비로`(L) / `메인 메뉴`(Esc).
  - `로비로`: 같은 연결의 로비로 돌아가 `새 판입니다. 이전 판 준비는 해제되었습니다.`를 보인다.
  - `다시 대전`: 로비로 돌아가 이 PC의 준비를 바로 켠다. 상대도 `게임 시작`을 누르면 새 matchId로 시작한다.
- 화면이 바뀌면 그 상태에서 먼저 누를 곳에 포커스를 둔다: 첫 화면은 상대 IP 칸, 로비는 `게임 시작`, 대기·접속 중은 `취소`.

## 4. 검증

| 완료 조건 | 테스트 |
|---|---|
| 자기/상대 playerId 표시, 호스트·클라이언트 양 관점, 3모드×단계 그리기 | `BattlePerspectiveTest` |
| 한국어 상태 문구 | `BattleCommonApiTest#statusCoversEveryPhaseAndResultReason` |
| 지연 표시·해제, 끊김과 정상 나가기 구분, 장애 시 첫 화면·포커스 | `P2pConnectionFlowTest` |
| 최근 IP 표시·선택·성공 시 갱신·5개 상한·파일 오류 | `P2pConnectionFlowTest` |
| 결과 뒤 로비 복귀(이전 준비 해제)와 다시 대전, 상대가 나간 뒤 다시 대전 | `P2pConnectionFlowTest` |
| LEAVE 송신, 연결 뒤 cancel이 heartbeat 유지 (C) | `LinkHealthLobbyTest` |

## 5. 남은 한계

- 실제 두 PC·물리 키보드에서의 지연 표시와 끊김 흐름은 S11-B2/S11-C1 실측에서 확인한다. 위 검증은 가짜 로비와 loopback 소켓 기준이다.
