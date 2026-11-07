# S8 C 트랙: 연결 수립·핸드셰이크(S8-C1)와 소켓 로비(S8-C2)

- 소유: C 네트워크. 코드: `src/main/java/tetris/feature/network`, 테스트: `src/test/java/tetris/versus/network`
- 근거: [#127 S8-C1](https://github.com/yoobinkim541/SE_tetris_team6/issues/127), [#128 S8-C2](https://github.com/yoobinkim541/SE_tetris_team6/issues/128), 규칙 R3-HOST, R3-READY, R3-MODES, R3-NETTEST, `docs/req3/lobby-contract.md`, `docs/req3/network-protocol.md`, `docs/req3/decisions.md` ("C 소유 결정: 연결 시도 5초")

## 1. 구성 요소

| 클래스 | 책임 |
|---|---|
| `NetworkDefaults` | 기본 포트(`47123`)와 연결·핸드셰이크 제한 시간(`5초`) 단일 정의 |
| `PeerAddress` | 점 4개 10진수 IPv4 문자열(앞자리 불필요한 0·`0.0.0.0`·`255.255.255.255`·호스트명·IPv6 거부)과 포트(`1..65535`) 검증. DNS 조회 없음 |
| `LanAddresses` | 비활성·루프백·가상·터널 어댑터와 IPv6·링크 로컬(`169.254/16`) 제외 후 사설 IPv4(`10/8`, `172.16/12`, `192.168/16`) 우선 정렬 |
| `PeerEstablisher` | 호스트 1회 `accept`, 클라이언트 `connect`, 양방향 `HELLO` 역할 교환, 타임아웃, 취소 |
| `PeerLink` | 수립된 연결 한 세대(`PeerConnection` + `PeerRole` + 상대 IPv4 주소). `attach` 전 도착한 메시지·종료 통지 순서 보장 버퍼 |
| `LobbyPayloads` | `READY`(`{"ready":boolean}`)와 `START`(`{}`, 봉투 `matchId`·`configRevision` 필수) payload 변환 |
| `SocketLobbyClient` | `LobbyClient` 소켓 구현. 설정 revision 동기화, 양측 `READY` 확인, 호스트 단일 `START` 발행 및 `matchId` 보존 |

## 2. 실패 이유 (`EstablishFailure` / `EstablishException`)

| `EstablishFailure` | 발생 조건 | 완료 시점 |
|---|---|---|
| `INVALID_ADDRESS` | IPv4 점 4개 형식이 아니거나 허용되지 않는 주소(`0.0.0.0`, `255.255.255.255`, 앞자리 `0`, 호스트명, IPv6), 포트 범위 위반 | 호출 직후 즉시 완료(스레드 미시작) |
| `PORT_IN_USE` | `ServerSocket` `setReuseAddress(false)` 바인드 시 `BindException` | 호출 직후 즉시 완료 |
| `REFUSED` | 상대 포트가 닫혀 있어 `ConnectException` 발생 | dispatcher 스레드 비동기 완료 |
| `TIMEOUT` | 클라이언트 TCP 접속 `5초` 초과(`SocketTimeoutException`) 또는 소켓 연결 후 상대 `HELLO` 미수신 `5초` 초과 | dispatcher 스레드 비동기 완료 |
| `PROTOCOL_ERROR` | 첫 메시지가 `HELLO`가 아니거나, `HELLO` payload가 잘못되었거나, 같은 역할(`HOST↔HOST`, `CLIENT↔CLIENT`)이거나, 프레임 규격 위반 | dispatcher 스레드 비동기 완료 |
| `IO_ERROR` | `HELLO` 완료 전 상대 연결 종료(EOF) 또는 그 외 소켓 입출력 오류 | 바인드 외에는 dispatcher 스레드 비동기 완료 |

## 3. 타임아웃 범위

- **클라이언트 TCP 접속 (`Socket.connect`)**: 기본 `5초` (`NetworkDefaults.CONNECT_TIMEOUT`).
- **HELLO 핸드셰이크**: TCP 연결 성립(호스트 `accept` 직후 / 클라이언트 `connect` 직후) 시점부터 공유 데몬 타이머로 기본 `5초` 측정. 만료 시 소켓·`PeerConnection`을 먼저 닫은 뒤 `TIMEOUT`으로 완료한다.
- **호스트 `accept` 대기**: 별도 타임아웃 없이 상대가 접속하거나 `Attempt.cancel()`이 호출될 때까지 데몬 스레드에서 대기한다. 첫 접속을 수락하는 즉시 `ServerSocket`을 닫아 두 번째 접속을 차단한다.

## 4. 스레드·취소·세대 격리 규칙

- 블로킹 소켓 `accept`·`connect`·`read`·`write`는 모두 데몬 스레드에서 실행한다.
- 모든 상태 확정(`CompletableFuture` 완료)과 `PeerLink` 메시지·종료 전달은 주입한 `dispatcher`(앱에서는 Swing EDT) 한 스레드에서만 실행한다.
- 각 `Attempt`는 독립된 `AttemptState`와 `AtomicBoolean settled`를 가진다.
  - 성공·실패·취소 중 가장 먼저 `settled`를 선점한 한 번만 결과를 확정한다.
  - `Attempt.cancel()`은 아직 확정되지 않은 시도의 `ServerSocket`·`Socket`·`PeerConnection`·타이머를 즉시 정리하고 `future.cancel(false)`로 취소한다.
  - 취소 이후 늦게 깨어난 이전 시도의 워커 스레드나 타이머는 자기 소켓만 닫고 종료하므로, 새로 수립된 연결 세대(`PeerLink`)를 닫거나 덮어쓰지 못한다.
  - 이미 성공해 `PeerLink`로 넘어간 시도에 대해 `Attempt.cancel()`을 호출해도 활성 `PeerLink`는 닫히지 않는다.

## 5. 소켓 로비 프로토콜 (`LobbyPayloads` · `SocketLobbyClient`, S8-C2)

- **수명 주기**: `INITIAL` → `HOSTING`/`CONNECTING` → `LOBBY`(`peerIdentity = "<peerIp>#<연결번호>"`) → `STARTING`. 연결 실패·취소·`leave()`·상대 연결 종료 시 `INITIAL`로 복귀하고 `matchId`를 초기화한다. `close()`는 반복 호출에 안전하며 이후 명령·구독은 `IllegalStateException`을 던진다.
- **설정 권위와 revision**:
  - `LOBBY`의 호스트만 `selectConfig(MatchConfig)`를 호출할 수 있다(클라이언트 호출은 `IllegalStateException`, 클라이언트가 와이어로 보낸 `CONFIG`는 호스트가 무시).
  - 설정을 고를 때마다 `configRevision`이 1씩 증가(`1..Integer.MAX_VALUE`, 상한 초과 시 `IllegalStateException`)하고 양쪽 `localReady`·`peerReady`가 모두 `false`로 초기화된다.
- **준비(`READY`)와 시작(`START`)**:
  - `ready(configRevision, value)`는 현재 `configRevision`과 일치할 때만 허용하며 `LobbyPayloads.readyMessage`를 전송한다.
  - 수신한 `READY`는 상태가 `LOBBY`이고 봉투 `configRevision`이 현재 값과 같을 때만 `peerReady`에 반영하고, 이전 revision이나 `STARTING` 이후 메시지는 무시한다.
  - 호스트는 `LOBBY`에서 `localReady && peerReady`가 성립하는 순간 `UUID`로 `matchId`를 한 번 생성해 `START(matchId, configRevision)`를 전송하고 `STARTING`으로 전이한다. 이후 추가 `READY`/`CONFIG` 수신은 무시하고 로컬 `selectConfig`/`ready`는 `IllegalStateException`으로 거부한다.
  - 클라이언트는 `LOBBY`에서 현재 `configRevision`과 일치하는 `START`를 양쪽 준비 완료 상태로 처음 받았을 때만 `matchId`를 저장하고 `STARTING`으로 전이한다. 중복 `START`나 다른 revision의 `START`는 무시한다.
- **연결 세대 격리**: `PeerLink`마다 전용 리스너 인스턴스를 연결해, `leave()` 직후 재접속했을 때 이전 연결의 지연된 `onClosed`나 메시지가 새 세대 로비 상태를 덮어쓰지 못한다.

## 6. 테스트 대응표

| 요구사항 / 검토 항목 | 테스트 클래스 · 메서드 |
|---|---|
| IPv4 문자열 공백 제거·포트 경계·잘못된 형식·DNS 미사용 (R3-HOST) | `PeerAddressTest` (4건) |
| 다중 LAN·가상/터널 어댑터 제외·사설 대역 우선 정렬·루프백 제외 (R3-HOST) | `LanAddressesTest` (4건) |
| 호스트 대기·클라이언트 접속·HELLO 교환·역할·상대 주소 확인 (R3-HOST, R3-NETTEST) | `PeerEstablisherTest#호스트와_클라이언트가_HELLO를_교환하고_역할과_주소를_얻는다` |
| 핸드셰이크 직후 `attach` 전 도착 메시지 순서 보존 | `PeerEstablisherTest#수립_직후_attach_전에_온_메시지도_순서대로_받는다` |
| 결과 완료 스레드 == 주입한 dispatcher | `PeerEstablisherTest#결과는_dispatcher_스레드에서_완료된다` |
| 접속 거부 / 주소 오류 / 포트 충돌 / 5초(주입 300ms) 타임아웃 | `PeerEstablisherTest#닫힌_포트에_접속하면_REFUSED`, `잘못된_주소는_즉시_INVALID_ADDRESS`, `사용_중인_포트로_호스트하면_PORT_IN_USE`, `HELLO가_오지_않으면_timeout_후_TIMEOUT이고_소켓을_닫는다` |
| 같은 역할 HELLO·HELLO 전 연결 종료·다른 메시지 거부 및 소켓 정리 | `PeerEstablisherTest#같은_역할의_HELLO는_PROTOCOL_ERROR`, `HELLO_전에_상대가_바로_끊으면_IO_ERROR이고_다른_메시지를_보내면_PROTOCOL_ERROR` |
| 대기 중 호스트 취소·포트 즉시 재사용·이전 시도 타이머 세대 격리·스레드 종료 | `PeerEstablisherTest#대기_중인_호스트를_취소하면_future가_취소되고_포트를_다시_쓸_수_있다`, `취소한_이전_시도의_타이머가_새_연결에_영향을_주지_않는다`, `수립_후_close하면_두_스레드가_끝난다` |
| `READY`·`START` 프레임 왕복 및 잘못된 payload 거부 (R3-READY) | `LobbyPayloadsTest` (5건) |
| 로비 진입·설정 동기화·모드 변경 시 양측 준비 해제·클라이언트 설정 변경 거부/무시 (R3-MODES, R3-READY) | `SocketLobbyClientTest#호스트와_클라이언트가_LOBBY에_들어가고_stage가_완료된다`, `호스트_설정이_클라이언트에_전달되고_양쪽_준비가_풀린다`, `클라이언트는_설정을_바꿀_수_없고_호스트는_클라이언트가_보낸_CONFIG를_무시한다` |
| 한쪽 준비 시작 금지·이전 revision `READY` 무시·단일 `START` 및 동일 `matchId`·중복 `START` 무시 (R3-READY) | `SocketLobbyClientTest#한쪽만_준비하면_시작하지_않는다`, `이전_revision의_READY는_무시한다`, `양쪽_준비되면_START가_한_번_오고_matchId가_같다`, `중복_START는_무시한다` |
| 준비 중 단절·접속 실패 복귀·`cancel`/`leave`/`close` 중복 호출 및 구독 정리 | `SocketLobbyClientTest#준비_중_상대가_끊기면_INITIAL로_돌아간다`, `연결_실패면_INITIAL로_돌아가고_stage가_EstablishException으로_끝난다`, `close를_두_번_불러도_안전하고_이후_명령은_IllegalStateException` |

## 7. 남은 일

- **경기 세션 핸드오프 (`battleClient()`)**: 현재 `SocketLobbyClient#battleClient()`는 `Optional.empty()`를 반환하고 `matchId()`만 확정해 두며, 실제 경기 클라이언트 연결(`STARTING → PLAYING → RESULT → LOBBY`)은 S9-C1에서 이어받는다.
- **두 PC 실제 LAN 실측**: S11-C1에서 별도 두 머신 환경으로 지연·방화벽·어댑터 선택을 검증한다.
- **최근 접속 성공 IP 저장 (`recent-peers.json`)**: S10-C2에서 구현한다. 본 단계에서도 핸드셰이크까지 성공한 `PeerLink`만 `peerAddress()`를 노출하고 실패한 시도는 주소를 반환하거나 저장하는 경로가 없으므로, 잘못된 주소나 접속 실패 IP가 저장되지 않는다.
