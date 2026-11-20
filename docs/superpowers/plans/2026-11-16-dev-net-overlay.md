# 개발자 모드: P2P 연결 상태 표시 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정의 "개발자: 연결 상태 표시"를 켜면 P2P 로비와 온라인 대전 화면에 실제 게임 연결의 상태·RTT·수신 간격·수신 빈도를 0.5초마다 표시한다.

**Architecture:** 연결 통계는 `SocketLobbyClient`가 이미 처리하는 heartbeat(PING/PONG)와 수신 프레임에서 모아 불변 `LinkStats`로 돌려준다(`LobbyClient.linkStats()` default 메서드). 화면 문자열은 순수 함수 `NetDebugText.lines(LinkStats)`가 만들고, `MainFrame`의 Swing `Timer`(500ms)가 켜져 있을 때만 읽어 로비 패널·대전 패널에 넘긴다. 설정은 기존 `Settings`/`JsonSettingsRepository` 패턴으로 저장한다.

**Tech Stack:** Java 21, Swing, JUnit 5, Jackson, Gradle Wrapper.

**Spec:** 사용자 요청(이 대화): 디버깅용, 설정에서 켜고 끔, 상대와의 연결 상태를 UI로 확인. 기존 판정 기준은 `docs/req3/s10-c-network.md`·`decisions.md`(heartbeat 500ms).

## Global Constraints

- 브랜치 `feature/dev-net-overlay`(origin/develop `3100a32` 기준, 이미 체크아웃됨). PR은 만들지 않는다. 커밋 후 `git push -u origin feature/dev-net-overlay`만 한다.
- 기본값은 **꺼짐**. 꺼져 있으면 타이머를 만들지 않고 화면도 지금과 똑같다(기존 화면 테스트가 그대로 통과해야 한다).
- 이전 `settings.json`(필드 없음)은 경고 없이 꺼짐으로 읽고, boolean이 아니면 그 필드만 꺼짐 + 경고 1개(기존 `colorBlindMode` 처리와 같은 방식·문구 형식).
- 통계 수집은 dispatcher(EDT) 스레드에서만, 메모리는 고정 크기(RTT 최근 20개, 수신 시각 최근 1초).
- 수정 금지: `feature/versus/**`, `feature/game/**`, 네트워크 프로토콜(새 메시지 종류 추가 금지).
- 테스트는 `@Timeout(30)`, 사용자 `%APPDATA%`에 쓰지 않는다, `Thread.sleep`으로 순서를 맞추지 않는다.
- 커밋 메시지 `Feat: [dev] …`, 한국어 주석.
- 검증: `.\gradlew.bat check --no-daemon` (LINE ≥ 70%).

## Review Focus

1. 개발자 모드를 켠 채로 P2P 화면을 나가거나 연결이 끊긴 뒤 → 타이머가 멈추고 예외 없음.
2. 연결 전(INITIAL/HOSTING/CONNECTING) → `linkStats()`가 비어 있고 화면에 "연결 없음" 한 줄.
3. PONG이 아직 없을 때 → RTT 칸이 `-`, 0이나 음수 표시 없음.
4. 설정 초기화(Reset Settings) → 개발자 모드도 꺼짐.
5. 로컬 대전·싱글 게임 화면 → 오버레이가 절대 나타나지 않음.

---

### Task 1: 연결 통계 `LinkStats`

**Files:** Create `src/main/java/tetris/feature/network/lobby/LinkStats.java`; Modify `lobby/LobbyClient.java`, `lobby/SocketLobbyClient.java`; Test `src/test/java/tetris/versus/network/LinkStatsTest.java`

**Interfaces — Produces:**
- `public record LinkStats(LobbySnapshot.Role role, String peerAddress, LinkHealth.Status status, long lastRttMillis, long minRttMillis, long avgRttMillis, long maxRttMillis, int rttSamples, long millisSinceLastFrame, int framesLastSecond, int statesLastSecond, long pingsSent, long pongsReceived)` — RTT 값이 없으면 -1, `rttSamples`는 0~20.
- `LobbyClient`: `default Optional<LinkStats> linkStats() { return Optional.empty(); }`
- `SocketLobbyClient.linkStats()`: 활성 연결(LOBBY 이후)이 있을 때만 값. `peerAddress`는 `PeerLink.peerAddress()`.

**수집 규칙 (모두 `clock.nowMillis()` 기준, 연결이 새로 수립되면 전부 초기화):**
- PING 송신마다 `pingsSent++`. 알려진 nonce의 PONG을 받으면 `pongsReceived++`, RTT를 최근 20개 고리 버퍼에 넣음(평균은 정수 나눗셈).
- 모든 수신 프레임: 마지막 수신 시각 갱신, 수신 시각을 덱에 넣고 1000ms보다 오래된 것은 제거. STATE면 STATE용 덱에도 같은 방식. `framesLastSecond`/`statesLastSecond`는 조회 시점에 1000ms 창으로 다시 잘라 센다.
- `millisSinceLastFrame` = now − 마지막 수신 시각(수신 없으면 연결 시각 기준).

- [ ] **Step 1: 실패하는 테스트** (`LinkHealthLobbyTest`의 가짜 시계·수동 pulse·원시 상대 패턴 재사용)
  - `연결_전에는_통계가_없다`
  - `PONG_전에는_RTT가_모두_-1이다` (pingsSent ≥ 1, pongsReceived 0)
  - `PONG_RTT로_최근_최소_평균_최대를_계산한다` (RTT 10, 20, 60 → last 60, min 10, avg 30, max 60, samples 3)
  - `RTT는_최근_20개만_유지한다` (25회 → samples 20, 가장 오래된 5개 제외된 min)
  - `1초_창의_수신_프레임과_STATE를_센다` (t=0에 3프레임, t=900에 STATE 2, t=1500 조회 → frames 2, states 2)
  - `마지막_수신_후_경과_시간` (수신 t=100, 조회 t=450 → 350)
  - `재접속하면_통계가_초기화된다`
- [ ] **Step 2:** `.\gradlew.bat test --tests "tetris.versus.network.LinkStatsTest" --no-daemon -PskipCoverage` → 컴파일 실패 확인.
- [ ] **Step 3:** 구현. **Step 4:** 같은 명령 PASS, `tetris.versus.network.*` 전체 PASS.
- [ ] **Step 5:** 커밋 `Feat: [dev] 연결 통계 LinkStats 조회`.

### Task 2: 표시 문자열 `NetDebugText`

**Files:** Create `src/main/java/tetris/ui/versus/NetDebugText.java`; Test `src/test/java/tetris/versus/ui/NetDebugTextTest.java`

**Interfaces — Produces:** `public static List<String> NetDebugText.lines(Optional<LinkStats> stats)`

**정확한 문구:**
- 비어 있으면 `List.of("[DEV] 연결 없음")`.
- 있으면 4줄:
  1. `[DEV] 호스트 · 192.168.0.11 · 정상` (역할: HOST→호스트, CLIENT→클라이언트; 상태: NORMAL→정상, LAGGING→지연, LOST→끊김, PEER_LEFT→상대 나감, NONE→확인 중)
  2. `RTT 12ms (최소 3 / 평균 8 / 최대 21, 20회)` — 표본 없으면 `RTT - (응답 없음)`
  3. `마지막 수신 120ms 전 · 수신 31/s · STATE 30/s`
  4. `PING 140 / PONG 139`
- [ ] **Step 1: 실패하는 테스트**: 빈 값, 호스트 정상 전체 4줄 정확 일치, 표본 0개, 각 상태 한국어, 클라이언트 표기.
- [ ] **Step 2:** 실패 확인. **Step 3:** 구현. **Step 4:** PASS. **Step 5:** 커밋 `Feat: [dev] 연결 상태 표시 문구`.

### Task 3: 설정 항목과 저장

**Files:** Modify `feature/setting/Settings.java`, `feature/save/JsonSettingsRepository.java`, `ui/SettingPanel.java`, `ui/Messages.java`; Test 기존 `src/test/java/tetris/feature/PersistenceTest.java`에 추가, `src/test/java/tetris/ui/MenuPanelsTest.java`(또는 SettingPanel을 다루는 기존 테스트 파일)에 추가

**Interfaces — Produces:** `Settings.isDevNetworkOverlay()`, `Settings.setDevNetworkOverlay(boolean)`; JSON 필드 `"devNetworkOverlay"`; `SettingPanel`의 새 항목 `DEV_NET_OVERLAY`(COLOR_BLIND 바로 뒤), 메시지 키 `settings.devNetOverlay` = `"개발자: 연결 상태 표시"`, 값은 기존 `settings.on`/`settings.off`.

- [ ] **Step 1: 실패하는 테스트**
  - Persistence: 저장 후 다시 읽으면 true 유지; 필드 없는 이전 파일 → false, 경고 0개; `"devNetworkOverlay":"yes"` → false, 경고 1개, 다른 필드는 유지.
  - Settings: `resetToDefaults()` 후 false.
  - SettingPanel: 해당 항목에서 Enter(기존 COLOR_BLIND 토글 테스트와 같은 방식) → 값 반전, `onChanged` 호출.
- [ ] **Step 2:** 실패 확인. **Step 3:** 구현(기존 colorBlind 코드 경로를 그대로 따른다). **Step 4:** PASS + 기존 설정·화면 테스트 전부 PASS.
- [ ] **Step 5:** 커밋 `Feat: [dev] 설정에 개발자 연결 상태 표시 항목`.

### Task 4: 화면 연결

**Files:** Modify `ui/versus/BattlePanel.java`, `ui/versus/P2pLobbyPanel.java`, `ui/MainFrame.java`; Test `src/test/java/tetris/versus/ui/NetDebugOverlayTest.java`

**Interfaces:**
- Consumes: Task 1 `LobbyClient.linkStats()`, Task 2 `NetDebugText.lines`, Task 3 `Settings.isDevNetworkOverlay()`.
- Produces: `BattlePanel.setDebugLines(List<String>)`/`debugLines()`; `P2pLobbyPanel.setDebugLines(List<String>)`/`debugLines()`.

**동작:**
- `BattlePanel`: 줄이 비어 있지 않으면 보드 아래 왼쪽에 작은 고정폭 글꼴(기존 글꼴 크기의 80%)·반투명 검은 배경으로 그린다. 기존 `connectionNotice`와 겹치지 않는 위치.
- `P2pLobbyPanel`: 맨 아래에 `JLabel`(HTML 줄바꿈) 한 개, 비어 있으면 숨김.
- `MainFrame`: P2P 로비를 만들 때 쓰는 `LobbyClient`를 필드로 보관. `settings.isDevNetworkOverlay()`가 true일 때만 `javax.swing.Timer(500)` 시작 → `NetDebugText.lines(lobby.linkStats())`를 로비 패널과(온라인 대전 중이면) 대전 패널에 설정. `closeP2p()`와 설정 변경으로 꺼질 때 타이머 정지·줄 비움. 로컬 대전·싱글 화면에는 절대 설정하지 않음.
- [ ] **Step 1: 실패하는 테스트** (헤드리스에서 패널 객체만 사용)
  - `BattlePanel`: `setDebugLines` 후 `debugLines()` 일치, 빈 목록이면 그리기 생략(오프스크린 `BufferedImage` 렌더 두 번 비교: 빈 목록 렌더 == 설정 전 렌더).
  - `P2pLobbyPanel`: 줄 설정 시 라벨 보임, 빈 목록이면 숨김.
  - `MainFrame` 연결(기존 `BattleMainFrameTest`·`P2pLobbyControllerTest`의 가짜 LobbyClient 패턴): 설정 꺼짐 → 로비 패널 `debugLines()` 비어 있음; 설정 켜짐 + 가짜 `linkStats()` → 타이머 1회 실행 후(Timer 콜백을 직접 호출하거나 `SwingUtilities.invokeAndWait`로 대기) 첫 줄이 `[DEV]`로 시작; P2P 종료 뒤 타이머 `isRunning()` false.
- [ ] **Step 2:** 실패 확인. **Step 3:** 구현. **Step 4:** PASS.
- [ ] **Step 5:** `.\gradlew.bat check --no-daemon` PASS(LINE ≥ 70%), 커밋 `Feat: [dev] P2P 로비·대전 화면에 연결 상태 오버레이`, `git push -u origin feature/dev-net-overlay`.

## 최종 보고

`docs/superpowers/plans/2026-11-16-dev-net-overlay.report.md`(커밋하지 않음): 작업별 커밋, Ruling, 테스트 명령과 XML 기준 결과, 최종 check와 LINE %, 수동 확인 방법(설정에서 켜기 → P2P 접속 → 화면 확인).
