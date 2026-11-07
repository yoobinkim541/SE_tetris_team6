# 3차 대전 모드 구조 (계약 v1, S7-A1)

원본 설계: Notion "3인 분담 · 파일 소유권·인터페이스 계약". 이 문서는 A가 구현한 계약 v1과 B·C 임시 코드의 대응을 적는다.

## 파일 소유권 (Notion 요약)

| 파트 | 기존 수정권 | 새 파일 | 테스트 |
|---|---|---|---|
| A | feature/game, feature/data/Board, feature/rule, feature/crash, feature/item, component/block | `feature/versus/api`, `feature/versus` | `tetris/versus/core` |
| B | ui 전체, Main.java, feature/setting | `ui/versus` | `tetris/versus/ui` |
| C | feature/save, build.gradle, .github/workflows, scripts | `feature/network`, `feature/network/protocol` | `tetris/versus/network` |

리뷰는 A→B→C→A 순서로 돈다. 소유 영역 밖 수정은 담당자에게 이슈로 요청한다.

## 흐름 (호스트 권위)

```
B 로컬/P2P 화면 ──PlayerCommand──▶ BattleClient ──▶ A MatchEngine (호스트 EDT, 두 GameSession)
                ◀──MatchSnapshot──┘                     ▲
클라이언트 명령 → C TCP 읽기 → 검증·큐 → EDT 전달 ─────┘
호스트 MatchSnapshot → C DTO·직렬화 → TCP → 클라이언트 EDT → B 화면
```

## 계약 v1 (`tetris.feature.versus.api`)

| 타입 | Notion 계약 후보 | 내용 |
|---|---|---|
| `PlayerId` | PlayerId {ONE, TWO} | `opponent()` |
| `BattleMode`, `MatchConfig` | BattleMode {CLASSIC, ITEM, TIMED} / MatchConfig | TIMED만 제한 시간 > 0, 기본 180초 |
| `PlayerCommand` | PlayerCommand(playerId, action, pressed, sequence) | B 임시 DTO와 필드 동일 |
| `AttackRows` | AttackRows(width=10, rows) | 불변, 값 0/9, 위→아래. 10줄 제한은 받는 쪽 대기열 |
| `MatchSnapshot` (+`PlayerView`, `MatchPhase`, `MatchResult`) | MatchSnapshot(matchId, revision, players, pendingRows, remainingMillis, result) | 플레이어별 보드·Next·점수·대기열(0–10). 결과는 FINISHED일 때만 |
| `BattleClient` | send / subscribe / close | 로컬·P2P·Fake가 같은 포트 |
| `MatchEngine` | start / submit / advance(now) / snapshot / close | 두 세션과 승패의 단일 소유자 |
| `LockResult`, `SpawnBoundary` | LockResult / deferred Spawn 경계 | 삭제 확정 후 1회 발행, 대전은 Spawn 보류, 싱글은 `IMMEDIATE` |
| `MatchClock` | — | 단조 시계(ms) |
| `VersusRules` | — | 2줄 / 10줄 / 공격 칸 9 / 180초 / pulse 16 ms |

엔진 쪽 구현(`tetris.feature.versus`): `DueTimeClock` — GameClock 어댑터. 다음은 S7-A2 이후에 만든다: AttackRows 추출, 대기열, MatchEngine 구현, 로컬 BattleClient.

## 시계와 Spawn 경계

- `GameSession.tick()`은 화면 프레임이 아니라 **실제 한 칸 하강 또는 CLEARING 종료**다. 16 ms마다 두 세션 tick을 부르면 낙하 속도와 150 ms 규칙이 깨진다.
- 그래서 대전 세션에는 `DueTimeClock`을 주입한다. `start(interval)`은 마감 시각만 기록하고, MatchEngine이 pulse마다 `isDue(now)`인 세션만 tick한 뒤 `onTicked(fired)`로 다음 마감을 잡는다. tick 안에서 다시 start/stop되면 그 값을 따른다.
- 제한 시간은 `MatchClock` 기준 마감 시각으로 판정하고, Pause 동안 멈춘 만큼 뒤로 민다.
- Lock 흐름:

```
Lock → (CLEARING 150 ms) → 점수·카운터 정산 → SpawnBoundary.onLockResolved(LockResult) ─┐
     ┌──────────────────────────────────────────────────────────────────────────────────┘
     └→ deferSpawn()? ── 아니오(싱글) → Spawn
                        └ 예(대전) → 엔진이 대기열 공격을 아래에 삽입 → Spawn 재개
```

- `GameSession`에 `SpawnBoundary`를 연결하는 일은 S7-A2/S8-A1에서 한다. 싱글은 `IMMEDIATE`라 동작이 바뀌지 않는다.

## B·C 임시 코드 → v1

| 지금 | v1 | 할 일 |
|---|---|---|
| B `ui.versus.PlayerCommand` (+ 중첩 PlayerId) | `api.PlayerCommand`, `api.PlayerId` | import 변경 |
| B `ui.versus.BattleClient` | `api.BattleClient` | import 변경 (메서드 같음) |
| B `ui.versus.MatchSnapshot.Player` | `api.MatchSnapshot.PlayerView` | 이름 변경 |
| B `MatchSnapshot.status: String` | `phase: MatchPhase`, `result: MatchResult` | 화면 문구는 B가 enum에서 만든다 |
| B `MatchSnapshot.ATTACK_CELL` | `VersusRules.ATTACK_CELL` | 위치만 변경 (값 9) |
| C `MessageType.CONFIG / INPUT / STATE / RESULT` payload | `MatchConfig` / `PlayerCommand` / `MatchSnapshot` / `MatchResult` | C가 DTO 변환기 추가 |
| C 로비 (`LobbyClient`, `LobbySnapshot`) | C 소유 | C가 정의, B가 표시 |
