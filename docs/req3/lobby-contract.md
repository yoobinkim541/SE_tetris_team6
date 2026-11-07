# P2P 로비 계약 v1 (C 소유)

- 코드: `src/main/java/tetris/feature/network/lobby` (`LobbyClient`, `LobbySnapshot`, `LobbyPhase`)
- 근거: [#118 S7-C1](https://github.com/yoobinkim541/SE_tetris_team6/issues/118) 완료 조건 "B와 LobbyClient 상태/행동 API", `docs/req3/decisions.md` "C 소유 결정"의 로비 상태, A의 초안(커밋 `ad53fab`)
- 사용: B가 P2P 접속·로비 화면(S9-B2)에서 호출하고 그린다. 실제 소켓 구현은 S8-C1 [#127](https://github.com/yoobinkim541/SE_tetris_team6/issues/127)(접속), S8-C2 [#128](https://github.com/yoobinkim541/SE_tetris_team6/issues/128)(준비·시작)

## 1. 단계

```
INITIAL ──host()──▶ HOSTING ───상대 접속──┐
   │                                        ▼
   └──connect(addr)──▶ CONNECTING ──성공──▶ LOBBY ──양쪽 ready(expectedConfigRevision)──▶ IN_MATCH
                                            ▲                          │
                                            └──────경기 종료(F-07)─────┘
어느 단계든 오류·단절 ──▶ FAILED ──cancel()──▶ INITIAL
HOSTING·CONNECTING·LOBBY·IN_MATCH ──cancel()──▶ INITIAL (연결 해제)
```

`decisions.md`의 `LOBBY → STARTING → PLAYING → RESULT → LOBBY` 중 STARTING·PLAYING·RESULT는 `IN_MATCH` 하나로 묶었다. 경기 안의 카운트다운·진행·결과는 `MatchSnapshot.phase()`(COUNTDOWN/PLAYING/FINISHED)가 이미 나타내므로, 같은 상태를 두 곳에서 따로 바꾸지 않기 위해서다.

## 2. 호출 규칙

| 호출 | 허용 단계 | 결과 |
|---|---|---|
| `host()` | INITIAL, FAILED | HOSTING, `localAddresses`에 LAN 주소 후보 |
| `connect(address)` | INITIAL, FAILED | CONNECTING. 형식 오류·거부·5초 초과는 FAILED와 안내 문구 |
| `cancel()` | 전부 | INITIAL. 연결이 있으면 끊는다(로비 떠나기, D-03) |
| `configure(config)` | LOBBY, 호스트만 | `configRevision` + 1, 양쪽 준비 해제 (R3-MODES) |
| `ready(expectedConfigRevision)` | LOBBY, 설정 있음 | 내 준비 표시. 양쪽 모두 준비되면 호스트가 START를 한 번 보내고 IN_MATCH (R3-READY) |
| `battle()` | IN_MATCH | 경기 포트 `BattleClient` |
| `subscribe(listener)` | 닫히기 전 | 현재 스냅샷을 바로 한 번 받고, 이후 바뀔 때마다 EDT에서 받는다 |

- 모든 메서드는 EDT에서 불러도 막히지 않는다. 결과는 반환값이 아니라 다음 스냅샷으로 알린다.
- 허용되지 않은 단계의 호출은 `IllegalStateException`이다. 화면은 단계에 맞는 버튼만 켠다.
- 클라이언트의 `configure`, 이전 revision에 대한 READY, 중복 START는 S8-C2에서 거부·무시한다.

## 3. `LobbySnapshot` 규칙

생성자가 검사하므로 화면은 조합을 다시 확인하지 않아도 된다(`LobbySnapshotTest`).

| 필드 | 규칙 |
|---|---|
| `role` (`PeerRole`) | INITIAL은 null, HOSTING은 HOST, CONNECTING은 CLIENT, LOBBY·IN_MATCH는 필수, FAILED는 무관 |
| `localAddresses` | HOSTING일 때 표시할 내 LAN 주소 후보. 그 외 빈 목록 |
| `peerAddress` | CONNECTING이면 접속 대상, LOBBY·IN_MATCH면 상대 주소 |
| `config`, `configRevision` | revision ≥ 1일 때만 설정이 있다. 0은 설정 전 |
| `selfReady`, `peerReady` | LOBBY에서 설정이 있을 때만 true 가능 |
| `recentHosts` | 최근 접속 성공 주소, 최신순 (S10-C2에서 채움) |
| `message` | 진행·오류 안내. 없으면 빈 문자열 |

목록은 생성 시 복사하고 수정할 수 없다.

## 4. A 초안(`ad53fab`)과 다른 점

| 초안 | v1 | 이유 |
|---|---|---|
| `tetris.feature.versus.api` | `tetris.feature.network.lobby` | 로비는 C 소유 (`decisions.md`) |
| `IDLE`, `CONNECTED` | `INITIAL`, `LOBBY` | `decisions.md` 로비 상태 이름 |
| `boolean host` | `PeerRole role` | 전송 프로토콜과 같은 역할 타입. 단계별 역할 규칙 검사 |
| `hostAddress` 하나 | `localAddresses` 목록 + `peerAddress` | 호스트 PC에 LAN·가상 어댑터가 여러 개일 수 있다 (S8-C1) |
| IN_MATCH에서 준비 true | 준비는 LOBBY에서만 | 경기가 시작되면 준비 표시는 의미가 없고, 경기 후 LOBBY로 돌아올 때 다시 눌러야 한다 |

`FakeLobbyClient`는 이 PR에 넣지 않았다. B가 화면을 먼저 만들 때 필요하면 S8-C2 구현과 함께 소켓 없는 가짜를 제공한다.


## Integration safeguards

This is the single canonical lobby contract. It uses protocol `PeerRole`,
`LobbyPhase` and the shared versus API `MatchConfig`/`BattleClient`; no parallel
Role/State or battle API is retained. `IN_MATCH` delegates countdown, playing and
results to `MatchSnapshot.phase()`.

`ready(int expectedConfigRevision)` refers to the configuration displayed to the
consumer. The implementation rejects stale revisions before sending READY and
validates incoming wire revisions too. The immutable `configured` helper is
host-only in LOBBY, increments the int revision with checked overflow, and clears
both votes even for identical settings. `readyAtRevision` validates the local
vote and preserves the peer vote. Revision zero means no configuration; READY
requires a positive revision. Integer.MAX_VALUE cannot wrap on selection.

All operations return promptly on EDT. An injected dispatcher delivers ordered
snapshots on EDT, current snapshot first; initial delivery is immediate when
subscribe is called on EDT. An off-EDT subscription dispatches initial delivery
before later updates. Subscription close and client close are idempotent and
prevent new callbacks after returning. Client close cancels outstanding work and
releases listeners; other calls after close throw IllegalStateException. Cancel
releases a pending attempt or established match and returns INITIAL. No public
completion-stage API is retained: outcomes are delivered through snapshots.

`LobbyContractTest` ports the previous consumer acceptance scenarios to this API.
Its fake checks consumer lifecycle examples and private pending-work cancellation;
it does not verify real sockets, EDT dispatch, actual transport cancellation or
match handoff. Real immutable snapshot/configuration validation is tested directly.
S8 transport integration remains necessary. Arbitrary address reachability is
not promised. Address candidates must be nonblank, are only listed in HOSTING;
CONNECTING/LOBBY/IN_MATCH require a nonblank peer address, other phases have none.
