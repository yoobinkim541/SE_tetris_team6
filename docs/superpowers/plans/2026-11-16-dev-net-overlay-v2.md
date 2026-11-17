# 개발자 모드 통합·대전 화면 표시·#188 후속 수정 Implementation Plan (v2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 개발자 모드를 하나로 합친다(설정 토글 또는 `--dev`). 켜면 P2P 로비(#188 `DevRttPanel`)와 온라인 대전 화면 모두에 연결 상태 4줄이 0.5초마다 표시된다. #188 리뷰 후속 2건도 고친다.

**Architecture:** `DevMode.enabled()` = `--dev`/시스템 속성 **또는** 설정 `devNetworkOverlay`. 로비에서는 #188 `DevRttPanel`이 기존 RTT 통계 아래에 `NetDebugText` 줄을 같이 보여 주고, 온라인 대전에서는 `BattlePanel.setDebugLines`로 같은 줄을 그린다. 갱신은 WIP의 `NetDebugOverlay`(Swing Timer 500ms)가 담당한다. 별도 로비 라벨(`P2pLobbyPanel.setDebugLines`)은 만들지 않는다.

**Spec:** 사용자 결정(이 대화): #188 위에 #188에 없는 것만 추가, 대전 화면 표시, #188 후속 2건(RTT를 이쪽 메모리에 저장한 송신 시각으로 통일, 자동 기록 최근 200개 제한). 기존 계획 `docs/superpowers/plans/2026-11-16-dev-net-overlay.md`의 Task 1~3은 완료(커밋 `c474e9a`, `c779c18`, `0662b6b`), Task 4는 WIP 커밋 `2779ab1`(컴파일 안 됨)에서 이 계획으로 대체한다.

## Global Constraints

- 브랜치 `feature/dev-net-overlay`(origin/develop + #188 `1e1959b` 위로 재정렬 완료). 이 계획 끝에 **PR을 연다**: base `develop`, `.github/pull_request_template.md` 형식, 본문에 "#188 후속 2건 반영" 명시. force push 금지, 병합 금지.
- 개발자 모드가 꺼져 있으면(기본) 기존 화면·동작과 똑같다. 기존 테스트 전부 통과.
- 수정 금지: `feature/versus/**`, `feature/game/**`, 프로토콜 메시지 종류·필드.
- 테스트: `@Timeout(30)`, 실제 `%APPDATA%`에 쓰지 않음, `Thread.sleep`으로 순서를 맞추지 않음. 테스트가 `DevMode` 상태를 바꾸면 `@AfterEach`에서 원복.
- 커밋 `Feat: [dev] …`/`Fix: [dev] …`, 한국어 주석. 검증 `.\gradlew.bat check --no-daemon`(LINE ≥ 70%).

## Review Focus

1. 설정에서 끄면 → 열려 있는 대전 화면 줄이 바로 사라지고 타이머 정지.
2. `--dev`로 켠 상태에서 설정 토글이 꺼짐이어도 → 개발자 모드 유지(둘 중 하나라도 켜짐이면 켜짐).
3. 로컬 대전·싱글 게임 → 어떤 줄도 표시되지 않음.
4. 송신 기록이 없는 PONG(모르는 nonce·위조 sentAtNanos) → 표본·RTT 모두 반영 안 됨.
5. 표본 200개 초과 후 "200회 측정" → 측정 중에는 200개 제한으로 측정 표본이 잘리지 않음.

---

### Task 5: 개발자 모드 스위치 통합

**Files:** Modify `src/main/java/tetris/DevMode.java`, `Main.java`, `ui/MainFrame.java`(설정 변경 콜백); Test `src/test/java/tetris/DevModeTest.java`

**Interfaces — Produces:** `DevMode.enabled()` = `flagEnabled || settingEnabled`; `public static void setSettingEnabled(boolean)`; 기존 `enableFrom(String[])`는 `flagEnabled`만 바꾼다; 테스트용 `set(boolean)`은 두 값을 함께 초기화하도록 유지.
- `Main.launch`: 설정 로드 직후 `DevMode.setSettingEnabled(settings.isDevNetworkOverlay())`. 창 제목 `[개발자 모드]`는 `MainFrame`이 현재 상태로 갱신(설정 변경 시에도).
- `MainFrame.onSettingsChanged`: `DevMode.setSettingEnabled(settings.isDevNetworkOverlay())` 후 제목 갱신, 오버레이 갱신(Task 7 `refresh`).
- [ ] **Step 1: 실패하는 테스트** `flag만_켜도_켜진다`, `설정만_켜도_켜진다`, `flag가_켜져_있으면_설정을_꺼도_켜져_있다`, `둘_다_꺼지면_꺼진다`.
- [ ] **Step 2:** `.\gradlew.bat test --tests "tetris.DevModeTest" --no-daemon -PskipCoverage` 실패 확인. **Step 3:** 구현. **Step 4:** PASS. **Step 5:** 커밋 `Feat: [dev] 설정 토글과 --dev를 하나의 개발자 모드로 통합`.

### Task 6: #188 후속 — RTT 기준 통일과 자동 기록 제한

**Files:** Modify `feature/network/lobby/SocketLobbyClient.java`(`sendPing`, `publishRtt`, PONG 처리), `ui/versus/DevRttPanel.java`; Test `src/test/java/tetris/versus/network/LinkHealthLobbyTest.java`, `src/test/java/tetris/versus/ui/DevRttPanelTest.java`

**결정:**
- `pendingPings`의 값을 `record SentPing(long millis, long nanos)`로 바꾼다(송신 시 `clock.nowMillis()`와 `System.nanoTime()` 둘 다 저장). PONG 처리: 저장된 항목이 있을 때만 상태 판정(`millis`)과 표본(`nanos`)을 만든다. 표본의 `inputNanos`는 **저장된 nanos**, 수신은 `System.nanoTime()`(작으면 저장값으로 올림). PONG의 `sentAtNanos`는 쓰지 않는다.
- `DevRttPanel`: 측정 중이 아닐 때(자동 기록) 표본은 최근 `AUTO_SAMPLE_LIMIT = 200`개만 유지(오래된 것부터 제거). "200회 측정" 중에는 제한하지 않는다. 측정은 `probeRtt`가 보낸 PING에 대한 PONG 200개가 모이면 끝난 것으로 보고 자동 기록으로 돌아간다(측정 중 들어온 heartbeat 표본도 그대로 포함 — 이번 범위에서 구분하지 않음).
- [ ] **Step 1: 실패하는 테스트**
  - `LinkHealthLobbyTest`: `PONG의_sentAtNanos를_바꿔도_표본_RTT는_이쪽_저장_시각_기준이다`(원시 상대가 PONG의 `sentAtNanos`를 0으로 바꿔 보냄 → 표본 `inputNanos`가 0이 아니고 `localMillis()`가 수 초 이하), `저장되지_않은_nonce의_PONG은_표본을_만들지_않는다`.
  - `DevRttPanelTest`: 자동 표본 250개 → `samples().size()==200`이고 첫 표본이 51번째 것; 측정 버튼 후 표본 250개 → 250개 유지.
- [ ] **Step 2:** 실패 확인. **Step 3:** 구현. **Step 4:** `tetris.versus.network.*`, `tetris.versus.ui.DevRttPanelTest` PASS. **Step 5:** 커밋 `Fix: [dev] RTT 표본을 이쪽 송신 기록 기준으로 계산하고 자동 기록을 200개로 제한 (#188 후속)`.

### Task 7: 로비·대전 화면 연결

**Files:** Modify `ui/versus/NetDebugOverlay.java`(WIP 수정), `ui/versus/DevRttPanel.java`, `ui/MainFrame.java`; 이미 있는 `ui/versus/BattlePanel.java` WIP 변경은 유지; Test `src/test/java/tetris/versus/ui/NetDebugOverlayTest.java`(WIP를 새 구조에 맞게 수정)

**Interfaces:**
- `DevRttPanel.setDebugLines(List<String>)`/`debugLines()`: RTT 통계 아래 한 개의 `JLabel`(HTML 줄바꿈), 비면 숨김.
- `NetDebugOverlay`: 생성자에서 `Settings` 대신 `BooleanSupplier enabled`(MainFrame은 `DevMode::enabled`)를 받는다. 대상 화면은 `Supplier<DevRttPanel>`, `Supplier<BattlePanel>`(온라인 대전 중일 때만 non-null), `Supplier<LobbyClient>`. `refresh()`: 켜져 있고 P2P가 열려 있으면 타이머 시작, 아니면 `stopAndClear()`. `tick()`은 WIP 그대로(`NetDebugText.lines(lobby.linkStats())`를 두 화면에 설정).
- `MainFrame`: `openP2pLobby`에서 `lobby`를 필드로 보관하고, 패널 생성 조건을 `DevMode.enabled()`로(#188 그대로) 둔다. `startOnlineBattle` 후 `overlay.refresh()`, `closeP2p`에서 `stopAndClear()`. 로컬 대전 `BattlePanel`은 공급자에 절대 넘기지 않는다. 설정 변경 시 `refresh()`.
- `P2pLobbyPanel`은 수정하지 않는다(WIP에서 이미 되돌림).
- [ ] **Step 1: 실패하는 테스트** (헤드리스, 가짜 `LobbyClient`, 타이머는 `tick()` 직접 호출)
  - `꺼져_있으면_타이머가_없고_두_화면_줄이_비어_있다`
  - `켜면_tick마다_로비와_대전_화면에_같은_4줄이_들어간다`(가짜 `linkStats()` 값으로 `NetDebugText.lines`와 정확 일치)
  - `연결이_없으면_DEV_연결_없음_한_줄`
  - `끄면_refresh에서_타이머가_멈추고_줄이_지워진다`
  - `대전_화면_공급자가_null이면_로비만_갱신한다`
  - `DevRttPanel`: `setDebugLines` 후 라벨 보임, 빈 목록이면 숨김
- [ ] **Step 2:** 실패 확인. **Step 3:** 구현. **Step 4:** PASS.
- [ ] **Step 5:** WIP 커밋 정리 없이 새 커밋 `Feat: [dev] 로비·온라인 대전 화면에 연결 상태 줄 표시`. `.\gradlew.bat check --no-daemon` PASS(LINE ≥ 70%). `git push -u origin feature/dev-net-overlay`, PR 생성(제목 `Feat: [dev] 개발자 모드 설정 통합·대전 화면 연결 상태 표시 (#188 후속)`, 검증 수치는 XML 기준).

## 최종 보고

`docs/superpowers/plans/2026-11-16-dev-net-overlay-v2.report.md`(커밋하지 않음): 작업별 커밋, Ruling, 테스트 명령과 XML 기준 결과, 최종 check·LINE %, PR URL, 수동 확인 방법(설정 → 개발자 모드 켜기 → P2P 접속 → 로비 패널·대전 화면 확인).
