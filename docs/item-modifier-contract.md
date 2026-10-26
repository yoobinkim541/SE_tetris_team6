# A → C 아이템 효과 계약

규칙: ARC-2, ITD-1~6, ITS-1~6, MOD-4. 관련 이슈: #67~#72.

## 책임

A가 `GameModifiers`의 공통 수명주기 기본 구현과 `GameSession` 연결을 제공합니다.
C는 `DoubleScoreEffect`, `SlowEffect`, `BombEffect`와 `ItemType.effect()` 연결 및 효과별 테스트를 구현합니다.
이 계약 PR은 C의 #68·#69를 완료 처리하지 않습니다. B·D·S는 C가 연결하기 전까지 기존 no-op입니다.

`ItemEffect.onLock(BoardView board, int itemRow, int itemCol, GameModifiers modifiers)`는 그대로 유지합니다.
행·열은 블록 격자가 아닌 보드 논리 좌표입니다. 효과 객체에는 세션 상태를 저장하지 않습니다.

## C가 호출할 API

| API | 호출 위치 | 동작 |
|---|---|---|
| `void activateDoubleScore()` | D의 `onLock` | 현재 Lock부터 행 보너스 2배. 이후 10 Lock. 재획득은 10으로 리셋 |
| `void activateSlow()` | S의 `onLock` | 다음 성공한 Spawn 10개에 간격 1.5배. 재획득은 10으로 리셋 |

두 효과 모두 등록 후 `ItemResult.none()`을 반환합니다. 행·칸 삭제 결과는 없습니다.
`ItemType.DOUBLE_SCORE`, `ItemType.SLOW`의 no-op을 각각 새 효과 객체로 바꿉니다.
C는 아래 A 호출을 효과 클래스에서 중복 호출하지 않습니다.

## A가 호출할 API

| API | 호출 시점 | 동작 |
|---|---|---|
| `void beginLock()` | L1 직전 | 이번 Lock의 D 갱신 여부 초기화 |
| `int scoreMultiplier()` | L5 | 행 보너스에만 1 또는 2 적용 |
| `void finishLock()` | L8, 삭제 유무와 무관하게 한 번 | 이번 Lock에서 갱신하지 않은 D 잔여 수 차감 |
| `void onSpawn()` | S2 성공 뒤 S3 | 현재 블록의 S 적용 여부 확정, S 잔여 Spawn 수 차감 |
| `double fallMultiplier()` | S4 | 현재 블록의 1.0 또는 1.5. 다음 성공한 Spawn까지 고정 |
| `List<ActiveEffect> effects()` | snapshot | 변경 불가능한 D/S 정보 사본 |

순서: `beginLock → L1 배치 → L2 효과 → [150 ms CLEARING] → L4 삭제 → L5 점수 → L6/L7 카운터·예약 → finishLock → S1/S2 → onSpawn → S4 타이머`.
Spawn 실패에는 `onSpawn`을 호출하지 않습니다. Pause/Resume은 잔여 수를 소비하지 않고 현재 블록의 저장된 간격을 사용합니다.
Restart는 새 `GameModifiers`로 초기화합니다. D와 S는 독립적으로 갱신·만료합니다.

## 경계 예시

- D 획득 Lock: 점수 2배, L8 이후 잔여 10. 다음 10 Lock도 점수 2배이며 마지막 L8 뒤 만료합니다.
- D 재획득 Lock: 점수 2배 유지, L8 이후 잔여 10. 4배가 되지 않습니다.
- S 획득 Lock 뒤 첫 Spawn: 현재 블록 1.5배, 미래 Spawn 잔여 9.
- S의 10번째 Spawn: 현재 블록 1.5배, 미래 잔여 0. 스냅샷에는 `ActiveEffect(SLOW, 0)`을 유지합니다. 다음 Spawn에 제거합니다.
- S 잔여 1에서 Pause/Resume: 현재 블록 배율과 잔여 수 모두 유지합니다.
- 행 점수만 D 대상입니다. 하강 점수와 B의 칸당 10점은 배율을 적용하지 않습니다.

## C 검증 및 인계 완료 조건

- D `onLock` 즉시 배율 2, 반환 `none`, 보드 불변.
- S `onLock` 직후 현재 간격 불변, 다음 Spawn부터 1.5, 반환 `none`, 보드 불변.
- 실제 D/S 아이템 블록으로 A의 Lock 파이프라인을 통과하는 통합 테스트 추가.
- B는 보드 밖을 제외한 3×3 칸 반환. 완성 행과 중복 제거·실제 삭제·칸 점수 계산은 A가 담당.
- 기존 `GameModifiersLifecycleTest`, `ModifierSessionTest`를 유지하고 전체 `gradlew test check` 통과.
- 최신 develop에서 브랜치를 만들고 계약 시그니처 변경이 필요하면 A와 먼저 조율.
