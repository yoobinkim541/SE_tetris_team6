# 3차 대전 모드 구조 (계약 v1, S7-A1)

## 패키지와 소유

| 패키지 | 소유 | 내용 |
|---|---|---|
| `tetris.feature.versus.api` | A | 공통 계약. 엔진·Swing·소켓 타입에 의존하지 않는다 (`Difficulty`, `GameAction` enum만 참조) |
| `tetris.feature.versus` | A (S7-A2~) | 두 `GameSession`을 묶는 경기 엔진, 공격 줄 추출, 10줄 대기열, 로컬 `BattleClient` 구현 |
| `tetris.ui.versus` | B | 경기·로비 화면, 키 라우터. 지금의 임시 DTO는 v1으로 옮긴다 (아래) |
| `tetris.feature.network` | C | 프레임 코덱, `PeerConnection`, P2P `LobbyClient`·`BattleClient` 구현 |

```
 ui.versus (B) ──PlayerCommand──▶ BattleClient ◀── 구현: LocalBattle (A) / P2P Host·Client (C)
          ◀──MatchSnapshot──┘                         │
 ui.versus lobby (B) ──▶ LobbyClient ◀── 구현: P2P (C), FakeLobbyClient (테스트)
                                                     ▼
                                     feature.versus (A) ── 2 × GameSession + AttackQueue
```

## 계약 v1 타입

| 타입 | 역할 |
|---|---|
| `PlayerId` | ONE/TWO, `opponent()` |
| `VersusMode` | NORMAL/ITEM/TIME_LIMIT. `itemsEnabled()`로 엔진의 싱글 `GameMode` 선택 |
| `MatchConfig` | 모드·난이도·제한 시간. TIME_LIMIT만 시간 > 0 |
| `PlayerCommand` | 키 누름/뗌 + sequence. B의 임시 DTO와 필드가 같다 |
| `MatchSnapshot` / `PlayerView` | 20×10 보드, Next(≤4×4), 점수, 대기 공격 줄(0–10). 방어적 복사. `phase`, `result`, `remainingMillis(-1=무제한)` |
| `MatchPhase`, `MatchResult` | 진행 단계, 승자·종료 이유. TIME_UP만 무승부 가능 |
| `BattleClient` | `send(PlayerCommand)`, `subscribe(MatchSnapshot)`. 구독 즉시 현재 값 1회, EDT 콜백 |
| `LobbyClient` / `LobbySnapshot` / `LobbyPhase` | P2P 로비 초안. host/connect/cancel/configure/ready/battle |
| `MatchClock` | 제한 시간용 단조 시계 |
| `PreSpawnHook` | Spawn 직전 경계. 싱글은 `NONE` |
| `VersusRules` | 2줄 / 10줄 / 공격 칸 9 / 보드 크기 / 기본 2분 |
| `FakeLobbyClient` | 소켓 없는 로비 가짜 (B 화면·테스트용) |

## 시간 경계 (GameClock vs MatchClock)

- `GameClock`(기존)은 **다음 낙하 tick까지의 간격**이다. Spawn, CLEARING(150 ms), Pause/Resume마다 `start(interval)`로 다시 센다. 그래서 경기 전체 시간을 재는 데 쓸 수 없다.
- 제한 시간은 `MatchClock`으로 **마감 시각(due time)** = 시작 시각 + `timeLimitMillis`를 정해 두고, 매 tick·스냅샷 발행 때 `due - now`로 `remainingMillis`를 계산한다. Pause 동안 멈춘 시간만큼 마감 시각을 뒤로 민다 (D-07).
- 마감에 도달하면 두 보드를 함께 멈추고 점수를 비교한다. 같은 tick에 Spawn 실패와 시간 종료가 겹치면 **Spawn 실패(TOPPED_OUT)가 우선**한다.

## Spawn 경계 (공격 줄 삽입)

```
Lock → (CLEARING 150 ms) → L4~L6 점수·카운터 → [PreSpawnHook.beforeSpawn()] → Spawn
                                         └ 여기서 공격 줄 확정·상대 대기열로 전송 (S7-A2)
```

- 공격 줄은 CLEARING이 **시작될 때가 아니라** 줄 삭제가 확정될 때 한 번 보낸다 (Pause로 CLEARING이 다시 시작돼도 중복 전송 없음).
- 받은 쪽은 자기 `beforeSpawn()`에서 대기열을 보드 아래에 넣는다. 싱글 플레이는 `PreSpawnHook.NONE`이라 `GameSession` 동작이 바뀌지 않는다.
- `GameSession`에 훅을 연결하는 작업은 S7-A2에서 한다 (`feature/game/GameSession`).

## B·C 임시 계약에서 v1으로 옮기는 방법

| B/C의 현재 | v1 | 바꿀 것 |
|---|---|---|
| `ui.versus.PlayerCommand` (+ 중첩 `PlayerId`) | `api.PlayerCommand`, `api.PlayerId` | import만 변경 |
| `ui.versus.BattleClient` | `api.BattleClient` | import만 변경 (메서드 동일) |
| `ui.versus.MatchSnapshot.Player` | `api.MatchSnapshot.PlayerView` | 이름 변경 |
| `ui.versus.MatchSnapshot(…, String status)` | `phase: MatchPhase`, `result: MatchResult` | 문자열 상태 → enum. 화면 문구는 B가 만든다 |
| `MatchSnapshot.ATTACK_CELL` | `VersusRules.ATTACK_CELL` | 상수 위치 변경 (값 9 동일) |
| C `MessageType.CONFIG` payload | `MatchConfig` | 변환기 추가 (C) |
| C `MessageType.INPUT` payload | `PlayerCommand` | 변환기 추가 (C) |
| C `MessageType.STATE` payload | `MatchSnapshot` 전송 DTO | 변환기 추가 (C), revision으로 오래된 값 버림 |
| C `MessageType.RESULT` payload | `MatchResult` | 변환기 추가 (C) |
| C `PeerRole` | `LobbySnapshot.host` | 로비 상태에서는 boolean으로 노출 |
