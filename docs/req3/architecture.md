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

엔진 쪽 구현(`tetris.feature.versus`): `DueTimeClock`, `AttackRowExtractor`, `PendingAttackQueue`. `GameSession`은 선택적 `SpawnBoundary`로 삭제 확정 후 LockResult를 발행한다. 실제 공격 삽입·MatchEngine·로컬 BattleClient 구현은 S8 이후다.

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

- `GameSession`의 여섯 번째 생성자 인수로 `SpawnBoundary`를 주입한다. 기존 생성자는 `IMMEDIATE` 경로를 그대로 사용한다.
- 공격 사본은 삭제 전 최종 행 합집합에서 만든다. 정산 후 `onLockResolved`를 한 번 호출하며 `deferSpawn()`이면 타이머를 멈추고 `isSpawnPending()` 상태로 기다린다.
- 엔진은 공격 삽입을 마친 뒤 `resumeSpawn()`을 한 번 호출한다. Pause 중에는 이 호출이 무시되므로 Resume 뒤 엔진이 다시 호출한다. 대기 중 이동·회전·Drop·tick은 무시한다.

## B·C 소비자 계약 인수 (S7 마감)

| 지금 | v1 | 할 일 |
|---|---|---|
| B 임시 PlayerCommand/PlayerId | `api.PlayerCommand`, `api.PlayerId` | 이전 완료, 중복 타입 제거 |
| B 임시 BattleClient | `api.BattleClient` | Fake·입력 라우터·화면이 직접 소비 |
| B 임시 MatchSnapshot | `api.MatchSnapshot.PlayerView` | 이전 완료, phase/result에서 화면 문구 생성 |
| B 공격칸 상수 | `VersusRules.ATTACK_CELL` | 이전 완료 |
| C CONFIG / INPUT / STATE / RESULT | `MatchConfig` / `PlayerCommand` / `MatchSnapshot` / `MatchResult` | 공유 DTO를 계약 원본으로 고정. CommonWireContractTest에서 CONFIG/INPUT/STATE 실제 프레임 소비 검증; 운영용 변환기는 후속 통합 |
| C 로비 (`network.lobby.LobbyClient`, `LobbySnapshot`) | C 소유 | 계약·불변 상태·설정 revision/READY 검증 구현. 실제 접속·화면은 S8 |

인수 근거: `BattleCommonApiTest`, `CommonWireContractTest`, `LobbyContractTest`. Fake와 계약 테스트가 실제 엔진·소켓 로비 구현 완료를 뜻하지 않는다.
