# S7-C2 두 프로세스 TCP 송수신 실험

- 작업: [#119 S7-C2 두 프로세스 TCP 송수신 실험](https://github.com/yoobinkim541/SE_tetris_team6/issues/119)
- 규칙 ID: R3-NETTEST, R3-LIVE
- 선행: #118 S7-C1 전송 프레임 계약 (`docs/req3/network-protocol.md`)
- 기록일: 2026-11-07. **단일 PC loopback 결과이며, 두 PC LAN에서 입력→양쪽 화면 표시 200ms 이하(R3-LATENCY, S11-C1) 합격을 뜻하지 않는다.**

## 1. 구현

| 파일 | 역할 |
|---|---|
| `feature/network/PeerConnection` | 연결된 소켓 하나의 송수신. 수신·송신 스레드 각 1개, 송신 대기열 256, 콜백은 주입한 dispatcher(앱에서는 EDT)로 전달 |
| `feature/network/PeerListener` | `onMessage(Message)`, `onClosed(CloseReason, detail)` |
| `feature/network/CloseReason` | LOCAL / PEER_CLOSED / IO_ERROR / PROTOCOL_ERROR / SEND_BACKLOG |
| `src/test/.../TcpProbe` | 실험 클라이언트. 테스트가 별도 JVM 프로세스로 실행 |

연결 규칙:

- `send`는 대기열에 넣기만 하고 바로 돌아온다. 직렬화·쓰기는 송신 스레드에서 한다.
- 상대가 읽지 않아 대기열(256)이 차면 기다리지 않고 `SEND_BACKLOG`로 닫는다. EDT를 막거나 대기열을 무제한 늘리지 않는다.
- 잘못된 프레임을 받으면 그 프레임과 뒤 바이트는 넘기지 않고(앞서 도착한 정상 메시지는 넘긴다), 대기 중인 송신을 버린 뒤 ERROR 하나만 보내고 닫는다(최대 1초 대기, ERROR 설명은 256자까지).
- 종료 통지는 연결마다 한 번이고, `onClosed` 뒤에는 어떤 원인으로 닫혔든 `onMessage`가 오지 않는다. 상대 EOF 전에 받은 메시지는 모두 넘긴 뒤 `PEER_CLOSED`를 알린다.
- `close()` 뒤에는 이미 받아 dispatcher 큐에 있던 메시지도 넘기지 않는다. 연결 객체 하나를 연결 세대로 보고, 이전 연결의 메시지가 새 화면에 섞이지 않게 한다.
- dispatcher가 작업을 거부하거나 송수신 중 예상하지 못한 예외가 나도 소켓을 닫고 두 스레드를 끝낸다.
- 접속 대기(accept)·접속(connect)과 그 취소는 S8-C1에서 구현한다. 이 실험은 테스트에서 `ServerSocket`/`Socket`을 직접 연다.

## 2. 실험 절차

`TwoProcessTcpTest`가 테스트 JVM을 호스트로, `TcpProbe`를 별도 JVM 클라이언트로 실행한다. 엔진은 쓰지 않는다.

1. 클라이언트 → HELLO(CLIENT), 호스트 → HELLO(HOST)
2. 호스트 → 가짜 STATE 30개 (두 플레이어 20×10 보드·점수, 프레임 1,051 B). 클라이언트가 순서와 내용을 값으로 비교
3. 클라이언트 → PING 200회를 하나씩 보내고 PONG을 받을 때마다 `System.nanoTime()`으로 RTT 계산
4. 클라이언트 → LEAVE, 호스트가 닫고 클라이언트는 PEER_CLOSED 확인, 양쪽 스레드 종료 확인
5. 별도 사례: HELLO만 보낸 클라이언트 프로세스를 강제 종료 → 호스트가 PEER_CLOSED 또는 IO_ERROR로 닫고 스레드 종료, 같은 포트를 다시 열 수 있는지 확인

```powershell
.\gradlew.bat test --tests "tetris.versus.network.*" --no-daemon
```

## 3. 결과

환경: Windows 11 (amd64), Temurin JDK 25.0.2 (`release 21`), 같은 PC 두 JVM, `127.0.0.1`.

`--rerun`으로 5회 연속 실행, 매회 42건 통과 / 실패 0 (`FrameCodecTest` 27, `PeerConnectionTest` 13, `TwoProcessTcpTest` 2).

| 회차 | RTT min (µs) | p50 | p95 | max |
|---|---|---|---|---|
| 1 | 179 | 419 | 1,159 | 2,100 |
| 2 | 170 | 407 | 1,097 | 2,488 |
| 3 | 177 | 359 | 748 | 3,035 |
| 4 | 189 | 350 | 893 | 2,331 |
| 5 | 180 | 340 | 886 | 3,245 |

RTT는 PING→PONG 왕복(프레이밍·JSON·스레드 전달 포함)이며, 입력→호스트 엔진 반영→두 화면 표시 지연이 아니다. max는 200회 중 단일 측정치이고, 예열(JIT)·OS 스케줄링 영향을 구분하지 않았다.

## 4. 자동 검증 범위

| 완료 조건 (#119) | 근거 |
|---|---|
| 실제 loopback socket 성공 | `PeerConnectionTest` 연결·순서 보존, `TwoProcessTcpTest` 두 프로세스 왕복 |
| 부분 수신·합친 프레임 처리 | 한 바이트씩 쓰기, 20프레임 한 번에 쓰기 (`PeerConnectionTest`), 프레임 경계 어긋남 (`FrameCodecTest`) |
| 실패·취소 후 포트/스레드 해제 | 읽기 대기 중 `close`, 상대 EOF, 프로토콜 오류, 송신 대기열 초과, dispatcher 거부, 상대 프로세스 강제 종료 후 `awaitTermination`과 포트 재사용 |
| 블로킹 작업 EDT 밖 | 콜백이 EDT dispatcher에서만 실행, 상대가 읽지 않아도 EDT의 `send` 한 번이 100ms 안에 반환 |
| 왕복 지연 초기 관측, 200ms 합격과 구분 | 3절 표와 이 문서 머리말 |
| 실험 코드 재사용 | 실험이 그대로 기본 `test`의 통합 테스트로 남는다 |

## 5. 남은 일

- 두 PC LAN, 느린 네트워크, 패킷 분할을 일부러 만든 환경은 측정하지 않았다 (S10-C1, S11-C1).
- heartbeat·지연·단절 판정은 아직 없다. 상대가 응답 없이 연결만 유지하면 현재는 감지하지 못한다 (S10-C1).
- STATE 합치기·30Hz 상한은 S9-C2에서 송신 대기열 정책으로 추가한다.
