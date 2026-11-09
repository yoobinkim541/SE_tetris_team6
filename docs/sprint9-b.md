# Sprint 9 B: 3모드 대전과 P2P 일반 대전 첫 판

S9-B1(3모드 선택·대전 기록 저장 차단)과 S9-B2(P2P 접속·로비와 첫 판)를 구현했다. 착수 시점(develop `792beaa`)에 선행 작업 S9-A1/A2(엔진)와 S9-C1/C2(네트워크)가 모두 Backlog였으므로, B의 완료 조건(실제 엔진과 실제 소켓으로 한 판)에 필요한 범위까지 같은 브랜치에서 구현했다. A·C 소유 파일 변경은 각 트랙 담당자 리뷰가 필요하다.

## 구현

### 엔진 (S9-A1, S9-A2 범위)

- `LocalMatchEngine`이 CLASSIC·ITEM·TIMED를 모두 시작한다.
- TIMED(D-05, D-06): 시작 시각 + 제한 시간이 마감이다. 전체 Pause 동안 마감이 멈추고 Resume 때 멈춘 시간만큼 미룬다. 마감과 같거나 늦게 엔진에 도착한 입력은 버리고, 마감 시각 이후의 낙하·삭제 tick은 실행하지 않는다. 같은 pulse에서 쌓기 불가가 확정되면 TOPPED_OUT이 TIME_UP보다 우선한다. TIME_UP은 그때까지 정산된 점수만 비교하므로 CLEARING 중인 줄 삭제 보너스는 들어가지 않는다. 동점은 무승부. 남은 시간은 스냅샷 `remainingMillis`(시간 제한이 없으면 -1).
- ITEM: 두 세션을 아이템 모드로 돌린다. 생성기·지속 효과(D·S)·10줄 아이템 예약·점수는 세션마다 따로다. 공격 행은 기존 규칙(D-09) 그대로 자연 완성 행과 L 행의 합집합이며, B·W의 칸 삭제는 행으로 세지 않는다.
- `MatchSnapshot.PlayerView`에 `items`(20×10, 0 또는 L·B·D·S 문자)를 더해 대전 화면이 아이템 칸을 표시한다. 기존 4인자 생성자는 그대로 쓸 수 있다.

### 네트워크 (S9-C1, S9-C2 범위)

- `tetris.feature.network.match`: `HostBattleClient`(호스트 권위, 엔진은 호스트에서만 계산), `ClientBattleClient`(STATE 표시 전용), `DispatcherPulse`(EDT에 16 ms tick).
- 핸드오프: 양쪽 READY → 호스트 START → 호스트 UI가 `battleClient()`를 부르면 엔진 시작(STARTING → PLAYING). 클라이언트는 START를 받는 즉시 수신기를 만든다.
- 입력 검증: 호스트는 현재 matchId의 `player=TWO` INPUT만 받는다. 위조 playerId·이전 matchId는 버리고, 순번 중복·역순은 엔진이 버린다. Pause는 누가 보내든 도착 순서대로 바로 적용한다(승인 대기 없음).
- STATE: 약 30 Hz 일정, 단계 변화는 즉시. 마지막 STATE 뒤 RESULT 1회. 클라이언트는 더 큰 revision만 반영한다.
- START: 호스트는 클라이언트 READY를 받은 뒤에만 START를 보내므로, 그 사이 클라이언트가 준비를 취소했더라도 같은 설정 revision의 START면 따라 들어간다(호스트 혼자 경기하는 일 방지).
- 로비: 경기가 끝나면 RESULT, `returnToLobby()`로 같은 연결의 LOBBY로 돌아간다(F-07). RESULT 화면에 있는 동안 온 상대의 CONFIG·READY도 받아 둔다. 연결이 끊기면 경기도 닫고 INITIAL.

### 화면 (S9-B1, S9-B2)

- 대전 메뉴: 로컬 일반 / 로컬 아이템 / 로컬 시간제한 / P2P 대전(같은 LAN) / 메인 메뉴.
- 경기 화면 위쪽에 모드 이름과 남은 시간(`남은 시간 m:ss`, 올림)을 표시하고, 아이템 칸은 흰 바탕 위 문자로 그린다. 결과 화면은 모드 이름과 승패·사유·두 점수를 보여 준다. 대전 결과는 이름 입력·스코어보드 저장으로 가지 않는다.
- 로컬 설정: 일반은 설정 난이도, 아이템은 아이템 규칙(NORMAL), 시간제한은 180초·일반 블록·NORMAL.
- P2P 화면: 포트와 "호스트로 대기"(대기 중 이 PC의 LAN IP·포트 표시), 상대 IP와 "접속", 접속 상태, 상대 주소와 내 쪽(P1 왼쪽/P2 오른쪽), 호스트만 고를 수 있는 모드, 양쪽이 눌러야 하는 "게임 시작"(다시 누르면 준비 취소), 취소·나가기·뒤로. 취소한 뒤 늦게 끝난 접속 결과는 화면을 덮지 않는다. 연결 실패 이유(주소 형식, 포트 사용 중, 상대 미대기, 시간 초과 등)를 안내한다.
- 온라인 경기: 각 창은 자기 참가자만 P1 키(방향키/Space)로 조작하고, 공통 P(일시정지)를 쓴다. 창 포커스가 빠져도 두 PC의 경기를 자동으로 멈추지 않는다. 기권은 모달 확인창 대신 Q를 3초 안에 두 번 누른다(첫 Q는 안내만 띄우고 경기를 멈추지 않으며, 다른 키를 누르면 취소). 확인창이 떠 있는 동안 상대가 Resume하면 입력을 못 하는 채로 블록이 떨어지기 때문이다(D-03에서 누구나 Pause/Resume 가능). 기권 결과는 호스트가 정한 것을 기다린다. 결과 화면 Enter는 같은 연결의 로비로, Esc는 메인 메뉴(연결 종료). 경기 중 연결이 끊기면 "상대와 연결이 끊어졌습니다."와 함께 로비 화면으로 돌아온다. 결과가 나온 뒤 상대가 나가면 결과 화면은 그대로 두고, "로비로"를 누르면 "상대가 나갔습니다." 안내가 보인다.

## 검증과 재현

JDK 21.0.12 (Temurin), Gradle wrapper. 이 PC는 메모리가 작아 `-I ../low-memory.init.gradle`(테스트 heap 128 MB)로 실행했다.

```powershell
.\gradlew.bat check guiAcceptance releaseJar --no-daemon
```

- `check`: 테스트 1,139개 중 1,122 통과 / 0 실패 / 17 생략, LINE 4,174/4,654 (89.69%).
- `guiAcceptance`(실제 Windows 창): BattleMainFrameTest 6개 중 5 통과 / OS 키 주입 1 생략, ProductionWiringAuditTest 9 통과, BattleLifecycleTest 1 통과.
- `releaseJar`: `build/libs/SE-Tetris.jar`에 새 클래스 포함 확인.

자동 검증 범위:

- 엔진(`BattleModesEngineTest`): 남은 시간·Pause 정지, 마감 직전/정각 입력, 마감 정각 tick, CLEARING 중 마감, 같은 pulse의 쌓기 불가 우선, 동점 무승부, 시간제한의 일반 공격, 자연+L=2행 / 같은 행=1행 / B·W=0행, 공격 칸 아이템 없음, D·S 효과와 10줄 예약의 세션 분리, 아이템 표시.
- 네트워크(`P2pMatchTest`, 실제 loopback 소켓): READY→핸드오프→클라이언트 입력이 호스트의 P2에만 반영→같은 기권 결과·revision·점수→양쪽 RESULT→같은 연결 LOBBY. 클라이언트 Pause의 즉시 적용과 호스트 기준 남은 시간. 위조 playerId·이전 matchId·중복 순번 거부. 오래된 revision·다른 경기 STATE/RESULT 무시. STATE 약 30 Hz와 RESULT 1회. 경기 중 연결 종료 시 정리.
- 화면(`BattleModesUiTest`, `P2pLobbyControllerTest`): 모드별 설정·이름·시간 문자열, 아이템 칸 그리기, 결과 문구. 호스트 대기 주소 표시와 기본 모드 설정, 클라이언트 모드 편집 불가, 준비 토글과 1회 핸드오프, 취소 뒤 늦은 실패 무시, 오류 안내, 연결 끊김 안내, 결과 후 로비 복귀, 온라인 키 맵.
- 실제 창(`BattleMainFrameTest`): 세 로컬 모드를 메뉴로 열어 실제 엔진으로 결과까지 가도 미리 저장해 둔 스코어보드 파일 바이트가 같다. **두 MainFrame 창이 실제 소켓으로 접속 → 양쪽 게임 시작 → 클라이언트 창의 실제 Swing 키 이벤트가 호스트의 오른쪽 보드만 움직임 → 호스트 기권 → 두 창 모두 같은 결과 → 클라이언트가 같은 로비로 복귀**, 스코어보드 파일 미생성.

창 렌더링(printAll): [호스트 로비](req3/screenshots/s9-b2-host-lobby.png), [클라이언트 경기](req3/screenshots/s9-b2-client-match.png), [클라이언트 결과](req3/screenshots/s9-b2-client-result.png).

### 지연 관측 (S9-C2)

같은 PC loopback, 실제 `DispatcherPulse`와 시스템 시계: 클라이언트 입력 → 호스트 화면 19~25 ms, 클라이언트 화면 38~41 ms (실행마다 조금씩 다름) (`P2pMatchTest.firstInputReachesBothScreens...`, 200 ms 미만 단언). 두 PC LAN 실측과 200 ms 초과 원인 분석은 S11 범위다.

### 최종 리뷰 반영

브랜치 전체를 별도 리뷰한 결과 치명 결함은 없었고, 중요 3건을 실패하는 테스트를 먼저 만든 뒤 고쳤다.

- 결과 직후 상대가 나가면 결과 화면이 지워지거나 결과가 표시되지 않던 문제 → `P2pLobbyControllerTest.peerLeavingAfterTheResultKeepsTheResultScreen`
- START가 오가는 사이 클라이언트가 준비를 취소하면 호스트만 경기하던 문제 → `P2pMatchTest.clientFollowsTheHostStartEvenIfItsLastVoteWasWithdrawnInFlight`
- 온라인 기권 확인창이 떠 있는 동안 상대 Resume으로 내 보드만 떨어지던 문제 → `BattleScreenControllerTest.onlineForfeit*`

남겨 둔 개선점(사소): 접속 포트 0 입력 시 IP 오류로 안내됨, HOSTING 화면을 그릴 때마다 LAN 주소를 다시 조회함, 로비 구독자 콜백 예외 시 대기 중 발행이 버려짐, 온라인 결과를 "승리/패배"(내 기준)로 보여 주면 더 읽기 쉬움, 연결 끊김 뒤 pulse 스레드 종료를 직접 확인하는 테스트 없음.

## 남은 인수 경계

- 두 대의 실제 PC LAN 대전, 물리 키보드 동시 입력, 방화벽 허용 안내는 실기 확인이 필요하다(S11). 이 문서의 P2P 검증은 한 PC의 두 창·loopback이다.
- 재대전 정책(같은 설정 재시작·설정 변경 후 재준비), 지연·끊김 표시(heartbeat), 최근 IP 목록은 S10 범위다.
- A·C 소유 파일(`feature/versus`, `feature/network`) 변경은 각 트랙 담당자의 리뷰가 필요하다.
