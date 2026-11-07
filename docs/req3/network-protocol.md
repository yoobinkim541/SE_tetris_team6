# P2P 전송 프로토콜 v1 (C 트랙)

- 소유: C 네트워크. 코드: `src/main/java/tetris/feature/network/protocol`, 테스트: `src/test/java/tetris/versus/network`
- 근거: [#118 S7-C1](https://github.com/yoobinkim541/SE_tetris_team6/issues/118), Notion R3-D08(호스트 권위, TCP 길이 접두 JSON, 버전·연결 세대·설정 revision·matchId·sequence), `docs/req3/decisions.md` "C 소유 결정"
- 상태: **v1 확정.** 프레임·봉투·오류 정책은 C 소유 결정으로 확정한다. 경기 payload(CONFIG·INPUT·STATE·RESULT)는 A의 계약 v1(`tetris.feature.versus.api`, PR #122)에 맞춘 `MatchPayloads`로 변환한다. READY·START payload는 로비 프로토콜(S8-C2, #128)에서 정한다.

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
| CONFIG | - | 필수 | 유실 없음 | A `MatchConfig` ↔ `MatchPayloads` (3.1) |
| READY | - | 필수 | 유실 없음 | S8-C2 (#128)에서 정의 |
| START | 필수 | 필수 | 유실 없음 | S8-C2 (#128)에서 정의 |
| INPUT | 필수 | - | 유실 없음 | A `PlayerCommand` ↔ `MatchPayloads` (3.1) |
| STATE | 필수 | - | 최신 값으로 합칠 수 있음 | A `MatchSnapshot` ↔ `MatchPayloads` (3.1) |
| RESULT | 필수 | - | 유실 없음 | A `MatchResult` ↔ `MatchPayloads` (3.1) |
| PING / PONG | - | - | 유실 없음 | C: `{"nonce":long,"sentAtNanos":long}`, PONG은 받은 PING을 그대로 반환 |
| LEAVE | - | - | 유실 없음 | C: `{"reason":string}` |
| ERROR | - | - | 보내고 바로 종료 | C: `{"code":ErrorCode,"detail":string}` |

- `sentAtNanos`는 보낸 PC의 `System.nanoTime()`이다. 다른 PC의 시계와 비교하지 않고 RTT 계산에만 쓴다.
- STATE를 합치는 송신 큐 정책과 30Hz 상한은 S9-C2에서 구현한다. v1 코덱은 모든 종류를 순서대로 보낸다.
- 네트워크로 `Block`·`GameSession` 객체를 그대로 직렬화하지 않는다. STATE는 계약 `MatchSnapshot`의 칸 값·Next·점수·대기열·단계·결과만 보낸다.

### 3.1 경기 payload (`MatchPayloads`)

| 종류 | payload | 비고 |
|---|---|---|
| CONFIG | `{"mode":"TIMED","difficulty":"NORMAL","timeLimitMillis":180000}` | 봉투 `configRevision`은 로비가 설정을 바꿀 때마다 올린 값 |
| INPUT | `{"player":"ONE","action":"MOVE_LEFT","pressed":true,"sequence":12}` | `action`은 `GameAction` 이름 |
| STATE | `{"revision":41,"remainingMillis":95000,"phase":"PLAYING","result":null,"one":{...},"two":{...}}` | 플레이어: `{"cells":[[..10]×20],"next":[[..]],"score":1200,"pendingRows":[[..10]×0~10]}` |
| RESULT | `{"winner":"ONE","reason":"FORFEIT"}` | 무승부는 `"winner":null` |

- matchId는 봉투에만 싣는다. STATE를 읽을 때 봉투의 matchId로 `MatchSnapshot`을 만든다.
- 받은 쪽은 JSON 타입(정수·불리언·문자열·배열)을 먼저 엄격히 검사하고, 계약 생성자로 다시 만들어 값 범위를 검증한다. 보드 20×10, 칸 0~9(9 = 공격 칸, D-04), Next 최대 4×4, 대기열 0~10줄(값 0 또는 9), 결과는 FINISHED일 때만, TIMED만 제한 시간 > 0, 기권은 승자 필수. 어긋나면 `INVALID_FIELD`.
- 프레임 크기(실측): 경기 중 STATE 1,188 B, 최악(두 보드 전부 9, Next 4×4, 대기열 10줄씩, 결과 포함) 1,802 B. 64 KiB 상한의 3% 미만이다.

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

## 6. 합의 상태

| 항목 | 상대 | 상태 |
|---|---|---|
| CONFIG·INPUT·STATE·RESULT payload 모양 | A (계약 v1, #122) | 반영. `MatchPayloads`가 계약 타입만 사용 |
| GameClock due-time과 STATE 발행 | A (D-07) | C는 tick을 보내지 않는다. 호스트가 pulse 결과 `MatchSnapshot`을 STATE로 보낸다. 발행 주기·합치기는 S9-C2 |
| 로비 상태·행동 API (`LobbyClient`, `LobbySnapshot`) | B | C 소유 계약을 별도 PR로 정의. 구현은 S8-C1(#127)·S8-C2(#128) |
| R3-D08 결정 기록 | 팀 | `decisions.md`가 네트워크 정책을 C 소유로 정리. 연결 세대를 봉투에 넣지 않는 선택(2절)을 Notion Discussion에 반영 필요 |

## 최신 develop 통합 인수

PR #129의 MatchPayloads를 경기 와이어 형식의 단일 변환기로 사용한다. 기존 CommonWireContractTest도 이 변환기를 경유하도록 연결한다. 로비 포트와 S7 마감 기록은 lobby-contract.md 및 s7-closeout.md에서 관리하며, 실제 사람 승인 대신 사용자 위임 AI 대리 리뷰라는 기존 인수 방식을 유지한다.
