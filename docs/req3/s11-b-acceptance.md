# S11 B 트랙: 메뉴·키·저장·화면 전체 인수(S11-B1)와 시연 가독성·렌더 지연 실측(S11-B2)

- 소유: B 화면·입력. 코드: `src/main/java/tetris/ui/versus`, 테스트: `src/test/java/tetris/versus/ui`, 스크립트: `scripts/gui-acceptance-summary.ps1`
- 규칙: R3-REGRESSION, R3-NOSCORE, R3-MENU, R3-LATENCY, R3-DEMO
- 관련: [s11-ci-evidence.md](s11-ci-evidence.md)(C: 테스트·커버리지 증거), [s11-lan-measurement.md](s11-lan-measurement.md)(C: 두 PC 200ms 절차)

## 1. 실제 창 경로와 headless 구분

| 구분 | 태스크 | 무엇을 보나 | 클래스 |
|---|---|---|---|
| 실제 창 (`headless=false`) | `guiAcceptance` | 메인 메뉴→대전→결과→메뉴, 세 화면 크기·색맹, 두 사람 동시 키, 포커스 잃음, 싱글 설정·기록 보존, P2P 로비 배치 | `VersusAcceptanceGuiTest` (S11-B1 신규) |
| 실제 창 | `guiAcceptance` | loopback 두 창 3모드 입력→두 화면 지연 | `LoopbackLatencyAcceptanceTest` (S11-B2 신규) |
| 실제 창 | `guiAcceptance` | 실제 소켓 두 창 로비→경기→결과→같은 로비, 최근 IP 기록 | `BattleMainFrameTest` (임시 파일로 최근 IP 격리) |
| headless | `test` | 화면 상태·문구·입력 라우팅·로비 흐름 | `BattlePerspectiveTest`, `P2pConnectionFlowTest`, `PaintLogTest` 등 |

- `scripts/gui-acceptance-summary.ps1`: `guiAcceptance` 결과 XML에서 대전 GUI 인수 클래스 5개가 **실제로 선택·실행**(실행 수 > 0, 실패·오류 0)됐는지 검사하고 `build/verification/gui-acceptance-summary.json`을 남긴다. 빠지거나 전부 생략되면 CI가 실패한다.
- 기존 메뉴·저장 지연(체크리스트 6-5)은 `resourceAcceptance`의 `ChecklistInputLatencyTest`가 따로 재고, P2P 200 ms는 아래 2절(loopback)과 S11-C1(두 PC LAN)이 잰다. 둘을 섞어 기록하지 않는다.

## 2. S11-B1에서 확인한 것

| 완료 조건 | 결과 |
|---|---|
| 싱글 기록/설정 보존 | 세 크기 모두 대전 후 `settings.json`·`scores.json` 바이트 동일, 색맹·난이도 유지 |
| 대전 저장 없음 | 결과 화면 뒤 이름 입력·스코어보드로 가지 않음, 기록 1건 그대로 |
| 두 키 동시 입력 | P1 ←와 P2 D를 겹쳐 눌러도 각자 press/release 한 번씩 |
| 포커스 | 누른 채로 창 포커스를 잃으면 남은 키를 모두 놓고 로컬 대전이 일시정지 |
| 작은 화면 | 대전 화면은 SMALL/MEDIUM/LARGE 모두 창 안에 맞음. **결함 발견·수정**: SMALL(350×420)에서 P2P 로비 안내 문구가 413 px로 잘림 → 줄바꿈 폭 축소 |
| 수정키 release | Shift·Ctrl·Alt를 누른 채 떼도 release가 전달됨 (`BattleInputRouter`가 수정키 조합별 release를 모두 등록, 기존 `BattleInputTest`) |
| 최근 IP 격리 | 기존 GUI 인수가 개발자 `%APPDATA%\SETetrisTeam6\recent-peers.json`에 `127.0.0.1`을 쓰던 문제 수정. `MainFrame.socketLobby(RecentPeers)`로 임시 파일 사용, 클라이언트만 기록됨을 확인 |

## 3. S11-B2 렌더 지연 실측

- `PaintLog`: 경기 화면이 새 스냅샷을 받은 시각과 그리기를 마친 시각을 최근 256번 남긴다 (같은 스냅샷 다시 그리기는 제외).
- 측정 범위: Swing 키 이벤트 디스패치 → 호스트 엔진 반영 → STATE 송신(30 Hz) → 두 창이 움직인 블록을 그린 시각. OS 키보드 입력 지연과 LAN 전송 지연은 포함하지 않는다.
- 환경: Windows 11, JDK 25.0.2, 같은 PC loopback TCP, 2026-11-14. 모드·방향마다 8회 (←/→ 번갈아).

| 모드 | 입력 | 입력한 화면 중앙값 / 최대 | 상대 화면 중앙값 / 최대 | 200 ms 초과 |
|---|---|---|---|---|
| 일반 | 호스트 | 8.1 / 23.8 ms | 25.1 / 55.6 ms | 0 |
| 일반 | 클라이언트 | 24.9 / 41.6 ms | 13.3 / 27.9 ms | 0 |
| 아이템 | 호스트 | 4.7 / 14.2 ms | 22.6 / 36.4 ms | 0 |
| 아이템 | 클라이언트 | 23.5 / 37.1 ms | 17.7 / 20.3 ms | 0 |
| 시간제한 | 호스트 | 4.5 / 16.9 ms | 22.5 / 39.2 ms | 0 |
| 시간제한 | 클라이언트 | 22.9 / 36.4 ms | 15.3 / 32.7 ms | 0 |

- 상태 수신→그리기(두 화면 전체): 중앙값 2.4~3.1 ms, 최대 18.8 ms.
- 클라이언트 입력은 자기 화면도 호스트 STATE를 받아야 움직인다(호스트 권위). 그래서 클라이언트 자기 화면 값이 호스트 자기 화면보다 크다.
- 원자료: `build/verification/s11-loopback-latency.json` (CI windows-acceptance 산출물). 기준은 각 조합 p95 ≤ 200 ms.

## 4. 시연 가독성

- 준비 단계 오버레이에 "파란 테두리가 내 보드 (P2 (나)) · 조작 키는 화면 아래"(로컬은 "왼쪽 P1 · 오른쪽 P2")를 띄운다.
- 공격 예고(공격 대기 n/10), 키 안내, 승패(승리!/패배)는 S10-B1에서 정리한 표시를 그대로 쓴다.

## 5. 남은 한계

- 두 PC LAN·물리 키보드 실측은 사람이 두 PC에서 S11-C1 절차로 해야 한다. 여기 값은 같은 PC loopback이라 LAN 전송 지연이 없다.
- 낯선 사용자(타 팀) 시연 피드백은 S12-B2에서 실제 사람이 해야 한다.
