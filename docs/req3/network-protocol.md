# P2P 전송 프로토콜 v1 (C 트랙)

- 소유: C 네트워크. 코드: `src/main/java/tetris/feature/network/protocol`, 테스트: `src/test/java/tetris/versus/network`
- 근거: [#118 S7-C1](https://github.com/yoobinkim541/SE_tetris_team6/issues/118), Notion R3-D08(호스트 권위, TCP 길이 접두 JSON, 버전·연결 세대·설정 revision·matchId·sequence; 아직 Open)
- 상태: **계약 v1 제안**. A의 `MatchConfig`·`PlayerCommand`·`MatchSnapshot`(S7-A1)과 B의 로비 화면 요구가 확정되면 아래 "합의 대기" 항목을 갱신한다.

## 1. 프레임

TCP 스트림 위에 프레임을 이어 보낸다. `read()` 경계는 프레임 경계와 무관하므로 받는 쪽은 바이트를 모아 완성된 프레임만 꺼낸다(`FrameDecoder`).

```
+----------------------+---------------------------------+
| length (4B, big-end) | body: UTF-8 JSON 객체 (length B) |
+----------------------+---------------------------------+
```

| 규칙 | 값 |
|---|---|
| 길이 접두 | 부호 있는 32bit big-endian. 1 이상 |
| 최대 본문 | 65,536 B (`WireFormat.MAX_FRAME_BYTES`). 보내는 쪽도 초과 메시지를 인코딩하지 않는다 |
| 초과 판정 시점 | 길이 접두 4바이트만 받은 시점. 본문을 기다리거나 버퍼에 쌓지 않는다 |
| JSON | 객체 하나. 뒤에 남는 토큰이 있으면 거부 |

## 2. 봉투 필드

```json
{"version":1,"sequence":12,"type":"STATE","matchId":"m-7","configRevision":3,"payload":{...}}
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| `version` | int | `WireFormat.VERSION`(=1)과 같아야 한다 |
| `sequence` | long | 연결·방향마다 1부터 1씩 증가. 누락·중복·역순은 거부 |
| `type` | string | 아래 종류 중 하나 |
| `matchId` | string, 생략 가능 | 경기 메시지는 필수. 있으면 빈 문자열 금지 |
| `configRevision` | int | 0 이상. 0은 설정 전. CONFIG·READY·START는 1 이상 |
| `payload` | object | 종류별 내용. 항상 객체 |

버전·순번은 `FrameEncoder`가 붙이고 `FrameDecoder`가 검사한 뒤 떼어 낸다. 앱 코드는 `Message(type, matchId, configRevision, payload)`만 다룬다.

### 연결 세대는 봉투에 넣지 않는다 (R3-D08과 다른 점)

TCP 연결 하나가 곧 한 세대다. 순번이 연결마다 1부터 시작하므로 다른 연결의 바이트가 섞이면 순번 검사에서 걸린다. 이전 연결의 수신 스레드·타임아웃이 새 연결을 닫지 못하게 하는 검사는 **로컬** 문제이므로, 연결 객체(`PeerConnection` 인스턴스)를 세대로 보고 그 연결의 콜백만 처리한다(S7-C2, S10-C2에서 사용). 와이어에 세대 번호를 따로 두면 양쪽이 같은 번호를 합의하는 절차가 추가로 필요하다.

## 3. 메시지 종류

| 종류 | matchId | configRevision ≥ 1 | 전달 | payload 소유 |
|---|---|---|---|---|
| HELLO | - | - | 유실 없음 | C: `{"role":"HOST"\|"CLIENT"}` |
| CONFIG | - | 필수 | 유실 없음 | A `MatchConfig` (합의 대기) |
| READY | - | 필수 | 유실 없음 | 합의 대기 |
| START | 필수 | 필수 | 유실 없음 | 합의 대기 |
| INPUT | 필수 | - | 유실 없음 | A `PlayerCommand` (합의 대기) |
| STATE | 필수 | - | 최신 값으로 합칠 수 있음 | A `MatchSnapshot` 전송용 DTO (합의 대기) |
| RESULT | 필수 | - | 유실 없음 | 합의 대기 |
| PING / PONG | - | - | 유실 없음 | C: `{"nonce":long,"sentAtNanos":long}`, PONG은 받은 PING을 그대로 반환 |
| LEAVE | - | - | 유실 없음 | C: `{"reason":string}` |
| ERROR | - | - | 보내고 바로 종료 | C: `{"code":ErrorCode,"detail":string}` |

- `sentAtNanos`는 보낸 PC의 `System.nanoTime()`이다. 다른 PC의 시계와 비교하지 않고 RTT 계산에만 쓴다.
- STATE를 합치는 송신 큐 정책과 30Hz 상한은 S9-C2에서 구현한다. v1 코덱은 모든 종류를 순서대로 보낸다.
- 네트워크로 `Block` 객체를 그대로 직렬화하지 않는다. STATE는 보드 값·좌표·종류·아이템만 담은 제한된 DTO로 만든다.

## 4. 오류 정책

받은 프레임이 아래 중 하나면 **엔진·UI에 넘기지 않고** 그 연결을 더 읽지 않는다. 가능하면 ERROR를 보낸 뒤 연결을 닫는다.

| `ErrorCode` | 조건 |
|---|---|
| `MALFORMED_FRAME` | 길이 접두 ≤ 0, JSON 구문 오류, 객체가 아닌 본문 |
| `FRAME_TOO_LARGE` | 길이 접두 > 65,536 |
| `UNSUPPORTED_VERSION` | version ≠ 1 |
| `UNKNOWN_TYPE` | 정의되지 않은 type |
| `INVALID_FIELD` | 필드 누락·타입 불일치, matchId·configRevision 규칙 위반, payload가 객체 아님 |
| `SEQUENCE_GAP` | sequence ≠ 직전 + 1 |

- 위반 프레임 **앞에** 도착한 정상 메시지는 위반을 알리기 전에 넘긴다(`FrameDecoder.feed(..., sink)`). 한 번의 read에 함께 들어왔는지와 관계없이 결과가 같다.
- 오류를 한 번 만난 디코더는 이후 입력을 받지 않는다(`IllegalStateException`). 오류 뒤 바이트를 다시 해석해 엉뚱한 프레임을 만들지 않기 위해서다.
- LEAVE는 정상 종료, EOF는 즉시 종료, 일정 시간 무응답 판정(heartbeat 500ms, 지연·5초 단절)은 S10-C1에서 이 프레임 위에 구현한다.
- payload 내부 검증(예: `Hello.from`)도 같은 `ProtocolException(INVALID_FIELD)`를 쓴다.

## 5. 스레드 규칙 (S7-C2 구현 기준)

- 소켓 connect·read·write, JSON 직렬화는 EDT에서 하지 않는다.
- 연결마다 수신 스레드 1개(디코더 소유)와 송신 스레드 1개(인코더 소유)를 둔다. 인코더·디코더는 스레드 안전하지 않다.
- 수신한 메시지와 종료 통지는 주입한 dispatcher(실제 앱은 EDT)로 넘긴다. 엔진 상태는 EDT 한 곳에서만 바꾼다.

## 6. 합의 대기

| 항목 | 상대 | 상태 |
|---|---|---|
| CONFIG·INPUT·STATE·RESULT payload 모양 | A (S7-A1 계약 v1) | 미합의. A 계약 확정 후 DTO 변환기를 추가 |
| 로비 상태·행동 API (`LobbyClient`, `LobbySnapshot`) | B | 미합의. S8-C2에서 구현 |
| GameClock due-time과 STATE 발행 시점 | A | 미합의 |
| R3-D08 결정 기록 (연결 세대를 와이어에 넣지 않는 선택 포함) | 팀 | Notion Discussion Open |
