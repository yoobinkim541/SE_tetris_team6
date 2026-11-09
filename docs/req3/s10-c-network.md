# S10 C 트랙: heartbeat·지연·끊김 판정(S10-C1)

- 소유: C 네트워크. 코드: `src/main/java/tetris/feature/network`, 테스트: `src/test/java/tetris/versus/network`
- 근거: [#152 S10-C1](https://github.com/yoobinkim541/SE_tetris_team6/issues/152), 규칙 R3-LAG, R3-DISCONNECT, `docs/req3/decisions.md` ("C 소유 결정"), `docs/req3/network-protocol.md`

## 1. 구성 요소

| 클래스 | 책임 |
|---|---|
| `HeartbeatPolicy` | heartbeat 주기(`500ms`), 지연 RTT(`>200ms` 3회), 지연 무수신(`≥1,500ms`), 끊김 무수신(`≥5,000ms`), 회복 RTT(`≤150ms` 3회) 임계치 record 및 `DEFAULT` 상수 |
| `ConnectionHealth` | 시계를 주입받아 정상 프레임 수신 시각·RTT로 `NORMAL` / `LAGGING` / `LOST`를 판정하는 순수 상태 기계 |
| `LinkHealth` | UI/소비자 구독에 전달하는 연결 건강 상태(`NONE`, `NORMAL`, `LAGGING`, `LOST`, `PEER_LEFT`)와 최근 RTT(`rttMillis`, 없으면 `-1`) |
| `LobbyClient#subscribeHealth` | 현재 `LinkHealth`를 즉시 1회 전달하고 이후 `status`가 바뀔 때만 알리는 default 메서드 |
| `SocketLobbyClient` | 연결이 `LOBBY`로 수립될 때마다 전용 heartbeat `MatchPulse`를 시작해 `PING`을 보내고, 모든 정상 수신 프레임과 `PONG` RTT를 `ConnectionHealth`에 반영 |

## 2. 지연·회복·끊김 판정 표 (`HeartbeatPolicy.DEFAULT` · `ConnectionHealth`)

| 전이 / 판정 | 조건 | 비고 |
|---|---|---|
| 초기 상태 | 연결이 `LOBBY`에 진입한 시각(`nowMillis`) | `NORMAL`, `lastRttMillis = -1` |
| 프레임 수신 (`onFrame`) | 완성된 정상 프레임이 `LinkListener.onMessage`에 도착 | 무수신 기준 시각을 `nowMillis`로 갱신. 부분 바이트(길이 접두 미완성 등)는 디코더 버퍼에 머물러 무수신 시간을 갱신하지 않음 |
| RTT 반영 (`onRtt`) | 현재 연결에서 보낸 `nonce`(최대 16개 보관)와 일치하는 `PONG` 수신 | 무수신 기준 시각 갱신 + `rtt > 200ms`면 `high++`/`low=0`, `rtt ≤ 150ms`면 `low++`/`high=0`, `151..200ms`면 둘 다 `0`. 모르는·지난 `nonce`의 `PONG`은 RTT에 쓰지 않고 프레임 수신으로만 반영 |
| `NORMAL → LAGGING` | 연속 `rtt > 200ms` 3회 이상 **또는** 무수신 시간(`now - lastActivityAt`) `≥ 1,500ms` | 전이 시 `high`/`low` 카운터 `0`으로 초기화 |
| `LAGGING → NORMAL` | 연속 `rtt ≤ 150ms` 3회 이상 **그리고** 무수신 시간 `< 1,500ms` | 전이 시 `high`/`low` 카운터 `0`으로 초기화 |
| `* → LOST` | 무수신 시간 `≥ 5,000ms` | 되돌릴 수 없음. 이후 `onFrame`/`onRtt`/`evaluate` 호출은 상태를 바꾸지 않으며 소켓을 닫고 `INITIAL`로 복귀 |

## 3. 연결 종료 상태 표 (`LinkHealth.Status`)

한 연결 세대(`PeerLink`)당 종료 상태는 최대 한 번만 발행되며, 모든 종료 경로에서 그 연결의 heartbeat pulse 핸들을 닫고 보류 중인 `nonce` 표를 비운다.

| 종료 원인 | `LobbySnapshot.State` | `LinkHealth.Status` 발행 | 비고 |
|---|---|---|---|
| 상대가 `LEAVE` 전송 | `INITIAL` | `PEER_LEFT` (1회) | 정상 나가기 안내 구분 |
| 상대 소켓 종료(`PEER_CLOSED` EOF) / `IO_ERROR` | `INITIAL` | `LOST` (1회) | 예기치 않은 연결 끊김 |
| 상대가 읽지 않아 송신 대기열(256개) 초과 (`SEND_BACKLOG`) | `INITIAL` | `LOST` (1회) | EDT 블로킹 없이 즉시 종료 |
| 상대 `ERROR` 수신 또는 프로토콜 위반(`PROTOCOL_ERROR`) | `INITIAL` | `LOST` (1회) | 위반 시 `ERROR` 프레임 송신 시도 후 종료 |
| 5초 무수신 heartbeat 판정 `LOST` | `INITIAL` | `LOST` (1회) | 부분 프레임만 오는 경우 포함 |
| 이쪽에서 `leave()` / `cancel()` / `close()` 호출 | `INITIAL` | 발행 없음 (`NONE`으로 복귀) | 로컬 의도 종료이므로 끊김 경고를 띄우지 않음 |

## 4. B 트랙(`ui`)의 `subscribeHealth` 사용법

- 모든 콜백은 `SocketLobbyClient`에 주입한 `dispatcher`(앱에서는 Swing EDT)에서 실행된다.
- 구독 즉시 현재 `LinkHealth`(미연결 시 `LinkHealth.NONE`)가 1회 전달되고, 이후에는 **`status`가 바뀔 때만** 호출된다. 최신 수치 RTT가 필요하면 `SocketLobbyClient#health()`로 읽을 수 있다.

```java
LobbyClient.Subscription healthSub = lobbyClient.subscribeHealth(health -> {
    switch (health.status()) {
        case NORMAL -> hideLagBanner();
        case LAGGING -> showLagBanner(health.rttMillis());
        case LOST -> showDisconnectDialog("연결이 끊어졌습니다.");
        case PEER_LEFT -> showDisconnectDialog("상대방이 나갔습니다.");
        case NONE -> hideLagBanner();
    }
});
```

## 5. 테스트 대응표

| 요구사항 / 검토 항목 | 테스트 클래스 · 메서드 |
|---|---|
| 호스트 원격 입력 순번 중복 거부·조기 단계 변화 STATE 뒤 30Hz 주기 유지 (#150 후속) | `P2pMatchTest#hostIgnoresDuplicateRemotePauseSequence`, `earlyPhaseChangeStateDoesNotDelayTheNextPeriodicState`, `hostRejectsForgedPlayerAndStaleMatchInputs` |
| 가짜 시계 기준 RTT·무수신 지연(200ms 3회, 1,500ms)·회복(150ms 3회)·끊김(5,000ms) 경계 및 불변성 | `ConnectionHealthTest` (10건) |
| 로비 진입 시 `NORMAL` 및 heartbeat `PING` 송신, `PONG` RTT 지연·회복 전이 | `LinkHealthLobbyTest#연결되면_NORMAL을_알리고_heartbeat가_PING을_보낸다`, `PONG_RTT가_3회_200ms를_넘으면_LAGGING_150ms이하_3회면_NORMAL` |
| 부분 바이트 수신 시 무수신 미갱신·5초 후 `LOST`, 모르는 `nonce` `PONG` RTT 미반영 | `LinkHealthLobbyTest#부분_프레임만_오면_5초_뒤_LOST로_끊기고_INITIAL이다`, `모르는_nonce의_PONG은_RTT에_쓰지_않는다` |
| `LEAVE`(`PEER_LEFT`) vs EOF/송신 큐 초과(`LOST`) 단일 발행 vs 로컬 `leave()`(`NONE` 무발행), 재접속 후 이전 heartbeat 격리 | `LinkHealthLobbyTest#상대가_LEAVE하면_PEER_LEFT를_한_번_알린다`, `상대가_소켓을_닫으면_LOST를_한_번_알린다`, `상대가_읽지_않아_송신_큐가_넘치면_LOST를_한_번_알린다`, `이쪽이_leave하면_발행_없이_NONE이고_heartbeat가_멈춘다`, `재접속_뒤_이전_연결의_heartbeat는_새_연결에_영향이_없다` |

## 6. 남은 일

- **최근 접속 주소 저장소와 재대전 검증**: S10-C2 (#153)에서 이어서 구현한다.
- **두 PC 실제 LAN 실측**: S11-C1에서 별도 두 PC 환경으로 지연·끊김·재대전을 실측한다.
