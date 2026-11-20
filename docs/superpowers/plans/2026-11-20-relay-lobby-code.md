# 중계 서버 + 로비 코드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서로 다른 LAN의 두 PC가 Oracle 중계 서버를 거쳐 로비 코드 하나로 P2P 대전한다.

**Architecture:** 같은 JAR 안에 `tetris.relay`(중계 서버와 공용 핸드셰이크)를 둔다. 게임 쪽은 `PeerEstablisher`에 중계 경유 시도(`hostRelay`/`joinRelay`)를 추가한다. 이 시도는 기존 시도와 같은 `AttemptState`(취소·기한·HELLO)를 그대로 쓴다. 수립 뒤는 기존 `PeerLink` 경로다. 로비·화면은 새 진입점만 추가하고, LAN 경로는 바꾸지 않는다.

**Tech Stack:** Java 21(가상 스레드), Swing, JUnit 5, Gradle Wrapper, Oracle Linux 9 + systemd.

**Spec:** `docs/superpowers/specs/2026-11-20-relay-global-ip-design.md` (v2, 로비 코드)

## Global Constraints

- 브랜치 `challenge/relay-global-ip`. develop·main 병합 금지, PR 생성 안 함(push만).
- LAN(IP) 모드 동작·테스트 불변. 기존 `PeerConnection`·프로토콜 메시지·`LobbySnapshot` 필드 불변.
- 핸드셰이크 줄: UTF-8, `\n` 종료, 최대 64바이트, 바이트 단위로 읽고 이후 바이트를 버리지 않는다.
- 소켓 I/O는 EDT 밖, 상태 변경·콜백은 dispatcher에서.
- 테스트: loopback만, `@Timeout(30)`, 시간 판정은 주입 시계, 실제 `%APPDATA%`에 쓰지 않음.
- 검증: `.\gradlew.bat check --no-daemon` (LINE ≥ 70%).

## Review Focus

1. 호스트가 코드를 받은 뒤 취소 → 서버 방이 바로 닫히고, 같은 코드로 JOIN하면 `NO_ROOM`.
2. 서버 재시작 중 접속 → "중계 서버에 연결할 수 없습니다", 화면이 INITIAL로 돌아감.
3. 경기 중 서버가 죽음 → 기존 끊김 안내로 P2P 첫 화면.
4. 코드에 공백·소문자·하이픈 섞어 입력 → 정상 입장.
5. 인터넷 모드 접속 후 최근 IP 목록 → 변화 없음.

---

### Task 1: 공용 핸드셰이크와 로비 코드 (`tetris.relay`)

**Files:** Create `src/main/java/tetris/relay/LobbyCode.java`, `RelayLine.java`; Test `src/test/java/tetris/relay/RelayLineTest.java`, `LobbyCodeTest.java`

**Interfaces — Produces:**
- `LobbyCode`: `ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"`, `LENGTH = 6`, `static String generate(Random)`, `static Optional<String> normalize(String input)`(공백·`-` 제거, 대문자, 길이·문자 검사), `static String display(String code)` → `K7Q-M2X`.
- `RelayLine`: `MAX_BYTES = 64`; `static String read(InputStream in) throws IOException` — `\n`까지 한 바이트씩 읽음, 64바이트 초과·EOF는 `IOException`; `static void write(OutputStream out, String line)` — `line + "\n"` UTF-8 후 flush.

- [ ] 테스트: 코드 길이·문자 집합, normalize(`" k7q-m2x "`→`K7QM2X`, `O`·`0` 포함·길이 틀림 → empty), display; RelayLine이 `OK\n` 뒤에 붙은 프레임 바이트를 스트림에 남긴다(ByteArrayInputStream으로 확인), 65바이트 거부, EOF 거부.
- [ ] 실패 확인 → 구현 → PASS → 커밋 `Feat: [challenge] 로비 코드와 중계 핸드셰이크 줄 읽기`.

### Task 2: 중계 서버 (`tetris.relay.RelayServer`)

**Files:** Create `RelayServer.java`, `RelayLimits.java`, `Room.java`(package-private); Test `src/test/java/tetris/relay/RelayServerTest.java`

**Interfaces — Produces:**
- `record RelayLimits(int maxConnections, int maxPendingHandshakes, int newConnectionsPerIpPerSecond, Duration handshakeDeadline, int maxWaitingRooms, Duration roomWaitLimit, int maxPairedRooms, Duration idleLimit, Duration stalledWriteLimit, Duration maxSessionLength, int noRoomPerIpPerMinute)`, `DEFAULT = (300, 64, 5, 5s, 100, 10min, 100, 60s, 10s, 3h, 10)`.
- `RelayServer(int port, RelayLimits limits, LongSupplier nanoClock, Random random)`; `start()`, `int localPort()`, `close()`, `int openConnections()`, `int waitingRooms()`, `int pairedRooms()`; `static void main(String[] args)` (포트 인자, 기본 47123, 시스템 시계, `SecureRandom`).
- 응답 줄: 설계 §5 표 그대로(`ROOM <code>`, `OK`, `PAIRED`, `ERR NO_ROOM|ROOM_FULL|SLOW_DOWN|BAD_REQUEST|FULL`).

**동작:** 설계 §6 그대로. 연결마다 가상 스레드 1개가 핸드셰이크를 처리하고, 짝 맺으면 방향별 pump 가상 스레드 2개를 둔다. 주기 점검(1초)이 만료·무활동·막힌 쓰기·수명을 검사한다(테스트는 `sweep()`을 직접 호출할 수 있게 package-private). 방 정리는 한 번만.

- [ ] 테스트(실제 loopback 소켓, `RelayLine` 사용)
  - 호스트 `ROOM` 수신, JOIN `OK`·호스트 `PAIRED`, 이후 양방향 바이트 그대로
  - `OK` 직후 붙은 바이트 보존: 클라이언트가 `JOIN` 줄과 바이트 10개를 한 번에 쓰면 호스트가 `PAIRED` 뒤 그 10바이트를 받음
  - 없는 코드 `NO_ROOM`, 두 번째 JOIN `ROOM_FULL`, 동시 JOIN 2개 중 정확히 하나 `OK`
  - 호스트가 끊으면 방 사라짐 → 같은 코드 `NO_ROOM`; 한쪽 끊으면 상대도 EOF
  - 잘못된 줄·버전 `BAD_REQUEST`, 65바이트 줄 즉시 끊김
  - 가짜 시계로 5초 핸드셰이크 기한(한 바이트씩 보내도 끊김), 10분 대기 만료, 60초 무활동
  - `NO_ROOM` 11번째 `SLOW_DOWN`, 대기 방 한도 초과 `FULL`, 연결 한도 초과 즉시 닫힘
  - 읽지 않는 상대로 막힌 쓰기 → stalledWriteLimit 뒤 정리(가짜 시계)
  - 100회 접속·종료 반복 후 `openConnections()==0`, 방 0
- [ ] 실패 확인 → 구현 → PASS(3회 `--rerun`) → 커밋 `Feat: [challenge] 로비 코드 중계 서버`.

### Task 3: 게임 쪽 중계 경유 연결 수립

**Files:** Modify `feature/network/PeerEstablisher.java`, `EstablishFailure.java`; Test `src/test/java/tetris/versus/network/RelayEstablishTest.java`

**Interfaces — Produces:**
- `EstablishFailure`에 `RELAY_UNAVAILABLE, NO_ROOM, ROOM_FULL, SLOW_DOWN, SERVER_FULL, ROOM_EXPIRED` 추가.
- `Attempt hostRelay(InetSocketAddress relay, Consumer<String> onRoomCode)` — 워커: 접속(기한 timeout) → `HOST v1` → `ROOM` 수신(기한 timeout) → `onRoomCode`를 dispatcher로 → `SO_TIMEOUT 0`으로 `PAIRED` 대기 → `beginHandshake`(기존 HELLO 기한). 서버가 대기 중 닫으면 `ROOM_EXPIRED`.
- `Attempt joinRelay(InetSocketAddress relay, String code)` — normalize 실패는 즉시 `INVALID_ADDRESS`. 접속 → `JOIN v1 <code>` → `OK`(기한 timeout) → `beginHandshake`.
- `AttemptState`에 `displayPeer`(null이면 기존 `socket.getInetAddress()`); 중계는 `"인터넷 · " + LobbyCode.display(code)`.
- 취소는 기존 `cancel()`이 소켓을 닫아 워커의 블로킹 읽기를 깨운다. 늦은 `ROOM` 콜백은 `settled`면 버린다.

- [ ] 테스트(로컬 `RelayServer` 사용): 두 시도가 HELLO까지 수립·역할·`peerAddress()=="인터넷 · XXX-XXX"`; 코드 콜백이 dispatcher 스레드; 없는 코드 `NO_ROOM`; 서버 없음 `RELAY_UNAVAILABLE`; 코드 받은 뒤 취소 → future 취소, 서버 `waitingRooms()==0`; 서버가 대기 방을 만료시키면 `ROOM_EXPIRED`; 수립 후 메시지 왕복.
- [ ] 실패 확인 → 구현 → PASS → 기존 `tetris.versus.network.*` 전부 PASS → 커밋 `Feat: [challenge] 중계 서버 경유 연결 수립`.

### Task 4: 로비 클라이언트

**Files:** Modify `feature/network/lobby/LobbyClient.java`(default 메서드), `SocketLobbyClient.java`; Test `src/test/java/tetris/versus/network/RelayLobbyTest.java`

**Interfaces — Produces:**
- `LobbyClient`: `default CompletionStage<Void> hostOnline() { throw new UnsupportedOperationException(); }`, `default CompletionStage<Void> joinOnline(String code) { … }`, `default Optional<String> roomCode() { return Optional.empty(); }`, `default Subscription subscribeRoomCode(Consumer<String> l) { return () -> {}; }`.
- `SocketLobbyClient`: 생성자 인자에 `InetSocketAddress relayAddress` 추가(기존 생성자는 `RelayAddress.fromSystemProperty()` 기본값 `158.179.174.70:47123`). `hostOnline()`은 HOSTING, `joinOnline()`은 CONNECTING을 발행하고 기존 `onAttemptCompleted`로 합류. 중계로 수립된 링크는 `recentPeers.record`를 호출하지 않는다(시도에 `relay` 플래그).
- `RelayAddress.parse("host:port")` IPv4 숫자만.

- [ ] 테스트: 두 `SocketLobbyClient`가 로컬 서버로 LOBBY → selectConfig → 양쪽 ready → START → 경기(HostBattleClient 주입 패턴은 `P2pMatchTest` 재사용) → RESULT → returnToLobby → 재대전; 코드 구독이 HOSTING 동안 코드 1회; 최근 IP 저장소에 기록 0건; 취소 시 INITIAL과 서버 방 0.
- [ ] 실패 확인 → 구현 → PASS → 커밋 `Feat: [challenge] 로비 클라이언트 인터넷 방 만들기·코드 입장`.

### Task 5: 화면

**Files:** Modify `ui/versus/P2pLobbyPanel.java`, `ui/versus/P2pLobbyController.java`; Test 기존 `P2pLobbyControllerTest`에 추가

- 패널: "인터넷 방 만들기" 버튼, "코드" 입력 칸 + "코드로 입장" 버튼 행 추가(기존 행 아래). `Actions`에 `hostOnline()`, `joinOnline(String code)`.
- 컨트롤러: HOSTING 문구가 인터넷 모드면 `"인터넷 방 대기 중 · 코드 K7Q-M2X (친구에게 알려 주세요)"`(코드 받기 전 `"인터넷 방 만드는 중…"`). `failureText`에 설계 §7.2 문구 추가. 인터넷 모드 연결에서 끊김·TIMEOUT 안내는 `"중계 서버 연결이 끊겼습니다. 다시 시도하세요."`.
- [ ] 테스트: 가짜 LobbyClient로 버튼 → 호출, 코드 수신 → 문구, 각 실패 코드 → 문구, LAN 문구 불변.
- [ ] 실패 확인 → 구현 → PASS → `.\gradlew.bat check --no-daemon` PASS → 커밋 `Feat: [challenge] P2P 화면에 인터넷 방 만들기·코드 입장`.

### Task 6: 배포와 실측

**Files:** Create `scripts/deploy-relay.ps1`, `scripts/tetris-relay.service`; Modify 설계 문서 §8 결과

- [ ] `.\gradlew.bat releaseJar` → 스크립트: `scp`로 JAR·유닛 업로드(`~/.ssh/tetris_relay`, `opc@158.179.174.70`), 서버에 `java-21-openjdk-headless` 설치(없을 때만), 기존 SSH 터널 점유 정리, `systemctl enable --now tetris-relay`, `ss -ltn`으로 47123 확인.
- [ ] 이 PC에서 실제 서버로 두 게임 인스턴스(같은 PC 두 JVM) 연결 확인 → 성공·RTT 기록.
- [ ] 서버 RSS·FD 기록. 커밋 `Feat: [challenge] 중계 서버 배포 스크립트와 실측`, push.
- [ ] 사람 확인 항목: 다른 LAN(핫스팟) 두 PC 3판, p95 RTT.
