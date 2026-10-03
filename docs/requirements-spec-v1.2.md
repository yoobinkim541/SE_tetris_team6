# SE Tetris 1차 요구사항·설계 명세 — 구체화판 v1.2

| 항목 | 내용 |
|---|---|
| 기반 문서 | `SE_Tetris_Req_Spec_Final_v1.0.md` (팀 최종 합의안) |
| 대조한 코드 | 최초: `Block.java`, `IBlock.java`, `JBlock.java` 및 저장소 파일 구조 (2026-09-29 기준). 최신 대조: `develop` + `develop-yoobin` 머지 결과 (2026-10-04 기준) |
| 목적 | v1.0의 결정을 **그대로 유지**하면서, 구현·테스트 시 해석이 갈릴 수 있는 부분을 규칙·수치·예시로 확정한다 |

## 개정 이력

| 날짜 | 반영한 변경 | 바뀐 절 |
|---|---|---|
| 2026-09-29 | 최초 작성 | 전체 |
| 2026-10-04 | 구현과 대조해 갱신하고, **구현된 규칙을 모두 [확정]으로 전환**(팀 결정 T1~T7 포함). 열린 항목: Q1~Q4, §15.2, §16 CI 게이트·배포. Spawn을 값 있는 칸 기준 맨 윗줄 정렬 + 숨은 줄 2행으로 구현하고 팀 확정(T2). Swing UI 구현(PR #22, 한글 UI 통일 T4, 자동 축소 T3, 포커스 이탈 Pause T5, Reset Settings 확인창 T6). 저장 계층을 interface(`SettingsRepository`, `ScoreboardRepository`) + JSON 구현(`JsonSettingsRepository`, `JsonScoreboardRepository`)으로 분리. 이름 규칙 담당을 `ScoreEntry`로 이동. Board 공개 API 표 갱신 | §0, §2, §3, §9, §11, §12, §13, §14, §17, §18 |
| 2026-10-04 | v1.2로 판 올림 (파일명 `requirements-spec-v1.1.md` → `requirements-spec-v1.2.md`) | 제목 |

## 표기 규칙

| 태그 | 의미 |
|---|---|
| **[확정]** | v1.0에서 이미 합의된 결정. 이 문서에서 바꾸지 않았다 |
| **[제안]** | v1.0이 침묵하거나 모호해서 이 문서가 정한 값. **팀이 승인하면 확정**된다 |
| **[확인]** | 팀 또는 교수자 확인이 필요하다. 권장안을 함께 적었다 |

충돌 시 우선순위: v1.0 **[확정]** > 이 문서의 **[제안]**.
각 규칙에는 ID(예: `SPN-1`)를 붙였다. 테스트 케이스와 PR에서 이 ID로 추적한다.

---

## 0. 모호점·충돌 요약

| # | 모호했던 점 | 처리 | 태그 | 본문 |
|--:|---|---|---|---|
| 1 | "즉시 고정"이 **착지하는 순간**인지 **하강 시도가 실패하는 순간**인지 | 하강 시도가 실패하는 순간에 고정. 착지 후 다음 Tick(또는 Soft Drop)까지 좌우 이동·회전 가능 | 확정 | §3 LCK |
| 2 | Spawn "첫 번째 행": 실제 블록은 패딩 포함 n×n 행렬이고, I는 행렬 0행이 비어 있다 | 값 있는 가장 높은 칸을 row 0에 맞춘다(I는 앵커 row −1). 보드 위에 숨은 줄 2행을 둔다 | 확정 (T2, 2026-10-04) | §2 SPN |
| 3 | 회전 기준점과, 보드 위쪽 밖으로 나가는 경우 | n×n 행렬 회전, 앵커 고정. 값 있는 칸이 좌우·아래 보드 밖이거나 숨은 줄(2행)보다 위면 실패 | 확정 (T2, 2026-10-04) | §3 ROT |
| 4 | 레벨 상승이 두 곳(줄 삭제, Spawn)에서 일어나는 순서 | Lock→Spawn 파이프라인을 번호로 고정. 한 사이클 최대 Level +2 | 확정 | §4 |
| 5 | "Spawn"의 정의 (Next 큐에 만들어지는 것과 구분) | Next 큐에서 꺼내 보드에 놓는 순간. 게임 시작 첫 블록도 1번으로 센다 | 확정 | §1, §4 |
| 6 | 타이머 재시작 시점, 스레드 모델 | Spawn·Resume 직후 전체 간격으로 재시작. Swing Timer 단일 스레드 | 확정 | §8 |
| 7 | Quit/Restart 확인창이 떠 있는 동안 게임이 도는지 | 확인창 동안 게임 정지 | 확정 | §8 |
| 8 | 키를 누르고 있을 때 Hard Drop이 매 블록 연속 발동하는 문제 | Left/Right/Down만 반복 허용. Rotate·Hard Drop·Pause·Quit은 키를 뗀 뒤 다시 눌러야 함 | 확정 | §7 |
| 9 | 조작키로 지정 가능한 키의 범위와 예약키 | 허용 목록·예약키(Enter, Esc) 명시 | 확정 | §7 |
| 10 | 한글 IME가 켜져 있을 때 키 입력 | 게임 화면에서 IME 비활성화. 구현 시 실제 검증 | 확정 | §7 |
| 11 | 이름 규칙: "10자" 기준, 허용 문자, 공백, Esc | 문자 집합·trim·Esc 무효로 확정 | 확정 | §11 |
| 12 | 스코어보드 진입/동점 삽입 규칙과 기록 필드 | 진입 조건식, "동점이면 뒤에 삽입", 필드 정의 | 확정 | §11 |
| 13 | Game Over 이후 화면 순서 (GameOverPanel이 어디에 들어가는지) | GameOver → (Name) → Scoreboard → Main Menu/Exit | 확정 | §9 |
| 14 | GameState 전이 누락 (Pause에서 Quit/Restart) | 전이표 완성 | 확정 | §8 |
| 15 | Large 셀 40px × 20행 = 800px가 노트북 화면에서 잘림 | 사용 가능 화면에 맞춰 셀 크기를 자동 축소(설정값은 유지) | 확정 | §13 |
| 16 | UI 언어 혼용 (메뉴는 한글, Pause/Settings는 영문) | `Messages`에 모으고 한글로 통일. 게임 용어만 영문 | 확정 | §9 |
| 17 | Reset Settings에 확인창이 있는지 | Reset Scoreboard와 동일하게 확인창 제공 | 확정 | §10 |
| 18 | JSON 값 검증, 부분 손상, 원자적 저장, 손상 파일 처리 | 검증 규칙·오류 처리표 확정 | 확정 | §12 |
| 19 | 커버리지 70% 측정 대상 vs "UI를 억지로 테스트하지 않는다" | 측정 범위(제외 패키지)를 명시하고 교수자에게 확인 | 확인 | §15 |
| 20 | jpackage 산출물 형태 (app-image vs 설치형 exe/msi) | app-image(zip)를 기본 산출물로. 설치형은 선택 | 확인 | §16 |
| 21 | 보드 크기 3종 요구를 "셀 표시 크기 3종"으로 해석해도 되는지 | 교수자 확인 필요 | 확인 | §17 |
| 22 | 창 닫기(X)·메인 메뉴 '게임 종료'·창 포커스 이탈 | 동작 정의 | 확정 | §8 |
| 23 | 색상 RGB를 구현 단계로 미룸 → 팀원마다 다른 색을 고를 위험 | 기본/색맹 팔레트 초기값 제시 | 확정 | §13 |
| 24 | 저장소의 기존 `docs/requirements-spec.md`가 v1.0과 정면 충돌(콘솔 UI, 3종 보드 크기, 22×12 벽 배열 등) | 이 문서로 대체하고 옛 문서는 폐기/보관 | 정리 필요 | §18 |

---

## 1. 용어·좌표·자료값

- **G-1** 논리 보드는 **20행 × 10열 고정**이다. `row 0..19`(위→아래), `col 0..9`(왼→오). **[확정]**
  v1.0의 열 번호(1-기반, 예: I의 Spawn 열 4)는 이 문서의 `col + 1`과 같다.
  내부 배열에 벽 테두리(22×12)를 둘지는 구현 자유이며, **Board의 외부 API는 항상 0-기반 논리 좌표만 사용**한다. **[확정]**
- **G-2** 보드 배열의 값: `0`=EMPTY, `1..7`=I,J,L,O,S,T,Z의 고정 칸 (이 순서). 벽 배열을 쓰는 경우에만 `-1`=WALL. **[확정]**
- **G-3** 화면 크기가 고정 논리 보드를 쓰므로 `Board.WIDTH=10`, `HEIGHT=20`은 상수로 둔다. 기존 문서가 지적한 "WIDTH/HEIGHT를 기본값으로만 취급" 문제는 사라진다. **[확정]**
- **G-4** 블록 = `n×n` 정사각 `shape` 행렬 + 앵커 `(row, col)` + `rotation(0..3)`. **[확정]**
  - `n`: I=4, O=2, 그 외 5종=3. 현재 `IBlock`, `JBlock` 등이 이미 패딩 포함 정사각 행렬이다.
  - 앵커는 행렬 좌상단 칸이 놓인 보드 좌표다. **값이 있는 칸만 놓일 수 있는 곳에 있으면 되므로** 앵커 자체는 보드 밖(예: `col = -1`, Spawn 직후 I의 `row = -1`)일 수 있다.
  - 보드 위에는 화면에 그리지 않는 **숨은 줄 2행**(`row −2, −1`, `Board.MARGIN = 2`)이 있다. 값 있는 칸은 숨은 줄에 들어갈 수 있다. **[확정]** (T2)
  - v1.0의 `blockWidth`는 이 `n`이다 (I=4, JLSTZ=3, O=2와 일치).
- **G-5** 용어 **[확정]**

| 용어 | 정의 |
|---|---|
| Tick | 자동 낙하 타이머 1회 발화 |
| 하강 시도 | Tick 또는 Soft Drop이 현재 블록을 한 칸 내리려는 동작 |
| Lock | 현재 블록을 보드에 기록해 고정하는 것 |
| Spawn | Next 큐의 맨 앞 블록을 꺼내 **보드에 현재 블록으로 놓는 순간**. 큐에 새 블록이 생성되는 것은 Spawn이 아니다 |
| 블록 사이클 | Spawn → (이동·회전·하강) → Lock → 줄 삭제 → 다음 Spawn |

---

## 2. 블록 생성·Next·Spawn

- **BLK-1** 7종(I, J, L, O, S, T, Z)을 구현하고, 0° 모양은 Reference Code(=현재 `*Block.java`의 `shape`)를 따른다. **[확정]**
- **BLK-2** 각 블록은 독립적으로 1/7 확률로 선택한다. 7-bag은 쓰지 않는다. **[확정]**
- **BLK-3** 선택 정책은 `BlockGenerator` 인터페이스 뒤에 두고, 테스트에서는 시드를 고정한 `Random` 또는 고정 순열을 주입한다. 통계 검정 대신 **주입으로 결정적 테스트**를 한다. **[확정]**
- **NXT-1** Next는 큐 구조다. Spawn 때마다 큐 앞에서 1개를 꺼내고 즉시 새 블록을 뒤에 채워, 큐 길이가 항상 `previewCount` 이상이 되게 한다. **1차는 `previewCount = 1`**이고 UI는 맨 앞 1개만 그린다. **[확정]**
- **SPN-1** Spawn 앵커: `col = floor((10 − n) / 2)`, `row = −(값 있는 첫 행렬 행)`, `rotation = 0`. 즉 **값 있는 가장 높은 칸이 보이는 맨 윗줄(row 0)**에 온다. 담당: `SpawnPolicy.getSpawnRow/getSpawnColumn`. **[확정]** (T2)

| 블록 | n | 앵커 (row, col) (0-기반) | v1.0 열 표기(1-기반) |
|---|--:|--:|--:|
| I | 4 | (−1, 3) | 4 |
| J, L, S, T, Z | 3 | (0, 3) | 4 |
| O | 2 | (0, 4) | 5 |

- **SPN-2 [확정]** (T2, 2026-10-04 팀 확정) 7종 모두 **첫 번째 줄부터 보인다**. 행렬 0행이 비어 있는 I는 앵커가 숨은 줄(row −1)에 놓인다.
  - v1.0의 "첫 번째 행부터 보이는 상태"를 문자 그대로 따른다.
  - 최초 v1.1은 "행렬 원점을 row 0"(I는 두 번째 줄)을 권장했다. 그 이유였던 "Spawn 직후 I 회전이 항상 실패" 문제는 보드 위 숨은 줄 2행(G-4)으로 해결해, 값 있는 칸 기준 정렬을 택했다.
- **SPN-3** Spawn 충돌 검사는 이동 충돌 검사와 **같은 함수**를 쓴다. **[확정]**
- **SPN-4** Spawn 충돌 시: 블록을 보드에 그리지 않고, `blockCounter`·Level·타이머를 바꾸지 않고, 즉시 `GAME_OVER`로 전환한다. Game Over 화면의 보드는 마지막 정상 상태를 유지한다. **[확정]**
- **SPN-5** 게임 시작 시 첫 블록의 Spawn도 블록 생성 카운터에 1로 포함한다. **[확정]**

---

## 3. 이동·회전·Lock·줄 삭제

### 3.1 입력별 동작표 **[확정]** (v1.0 §7, §8, §13 종합)

| 입력 | 조건 | 성공 시 | 실패 시 | 점수 |
|---|---|---|---|---|
| Left / Right | 이동한 위치가 유효 | 앵커 col ∓1 | 입력 무시 | 0 |
| Soft Drop (Down) | 한 칸 아래가 유효 | 앵커 row +1 | **Lock** (§4) | 성공 시 `+Level` |
| Tick (자동 낙하) | 위와 동일 | 위와 동일 | **Lock** | 성공 시 `+Level` |
| Rotate | 회전 결과가 유효 | 행렬 회전, `rotation=(rotation+1)%4` | 입력 무시 | 0 |
| Hard Drop | 항상 가능 (이동 거리 d ≥ 0) | 최저 위치로 이동 후 즉시 Lock | — | `+d × Level` |

- 유효 = "값이 있는 모든 칸이 보드 안 또는 보드 위 숨은 줄 2행 안에 있고 고정 칸과 겹치지 않음". 좌우·아래로 나가거나 숨은 줄보다 위로 나가면 무효. 숨은 줄의 칸은 화면에 그리지 않는다. **[확정]** (T2)
- 이동 실패(좌우·회전)는 **보드·점수·타이머를 전혀 바꾸지 않는다**.
- Hard Drop은 d = 0이어도 유효하며 Lock만 수행한다(0점).

### 3.2 회전

- **ROT-1** 시계방향 90°만 지원한다. **[확정]**
- **ROT-2** 회전은 `n×n` 행렬 회전 `rotated[j][n−1−i] = shape[i][j]`이며 현재 `Block.rotateRight()`와 같다. **앵커는 이동하지 않는다.** **[확정]**
- **ROT-3** 회전 결과가 유효하지 않으면 회전 전 상태를 그대로 유지한다. Wall Kick은 없다. **[확정]**
- **ROT-4** O는 회전해도 모양·위치가 그대로다. `rotation` 값은 0으로 유지하든 순환하든 외부에서 구분할 수 없다. **[확정]**
- **ROT-5** 회전 정책은 `RotationPolicy`로 분리한다. 1차 구현은 `BasicRotationPolicy`. **[확정]**
- **ROT-6** 행렬 회전과 고정 앵커 때문에 생기는 동작을 **버그가 아닌 사양**으로 명시한다. **[확정]**
  - I는 0°에서 행렬 1행, 180°에서 행렬 2행에 놓이므로 180° 회전 시 **한 칸 아래로 이동한 것처럼** 보인다.
  - 세로 I(앵커 col 7, 즉 보드 col 9)를 회전하면 가로 4칸이 col 7..10이 되어 **실패**한다. 벽 근처 회전 실패는 Wall Kick이 없는 정상 결과다.
  - Spawn 직후의 I(앵커 (−1,3))를 회전하면 col 5, row −1..2의 세로 I가 된다. 맨 위 1칸은 숨은 줄에 있어 화면에는 3칸만 보인다.

### 3.3 Lock

- **LCK-1** Lock Delay는 없다. **[확정]**
- **LCK-2 [확정]** Lock 시점: **하강 시도(Tick 또는 Soft Drop)가 실패하는 순간** Lock한다.
  - 블록이 바닥·고정 블록 위에 착지한 뒤에도, 다음 하강 시도 전까지는 좌우 이동·회전이 가능하다. 그동안 아래가 비게 되면 다시 하강할 수 있다.
  - 착지 후 Lock까지의 시간은 "다음 Tick(최대 현재 낙하 간격)" 또는 "Soft Drop 입력 즉시"다. 이것은 Lock Delay 기능이 아니라 하강 시도 방식의 결과다.
  - 대안 B: 하강 이동이 성공한 뒤 더 내려갈 수 없으면 그 즉시 Lock. 구현은 단순하지만 착지 후 미세 조정이 불가능해진다.
  - **A로 확정**(T1). v1.0 §8.1 문구("이동을 시도한다. 더 이상 아래로 이동할 수 없으면 즉시 고정")와 일치한다. 구현: `ImmediateLockPolicy.shouldLock(descentFailed)`. 나중에 B로 바꾸려면 §3.1의 "Lock" 조건과 `LockPolicy`만 바뀐다.
- **LCK-3** Hard Drop은 §4의 파이프라인을 동기적으로 끝까지 수행하므로, 그 블록에 대한 추가 입력은 자연히 불가능하다. **[확정]**
- **LCK-4** Lock된 칸에는 블록 종류 값(1..7)을 기록한다. **[확정]**
- **LCK-5** `LockPolicy`(1차: 하강 실패 시 즉시 Lock)를 분리해 향후 Lock Delay 추가에 대비한다. **[확정]**

### 3.4 줄 삭제

- **CLR-1** 한 행의 10칸이 모두 `≠ 0`이면 완성 행이다. 한 번의 Lock으로 완성된 행은 모두 동시에 삭제한다. **[확정]**
- **CLR-2** 삭제 후 위쪽 행을 삭제된 행 수만큼 아래로 내리고, 맨 위에 빈 행을 채운다. **[확정]**
- **CLR-3** 한 번에 삭제 가능한 최대 행 수는 4다(블록 행렬이 4×4 이하). **[확정]**
- **CLR-4** 벽·바닥은 완성 검사 대상이 아니다. **[확정]**

---

## 4. 블록 사이클 파이프라인 (핵심)

Lock과 Spawn은 아래 순서로만 수행한다. `GameSession.lockCurrentBlock()`(L1~L5)과 이어서 호출되는 `spawnNextBlock()`(S1~S4)이 맡는다. **[확정]**

```text
[하강 시도]  성공 → score += Level      실패 → LOCK
[Hard Drop]  d칸 이동 → score += d × Level → LOCK

LOCK
  L1  현재 블록 칸을 보드에 기록 (값 1..7)
  L2  완성 행 수 n 계산 (0..4)
  L3  n > 0 이면 행 삭제·압축
  L4  n > 0 이면 score += bonus(n)          // 1:100  2:300  3:500  4:800
  L5  lineCounter += n
      lineCounter ≥ 5 이면 Level += 1, lineCounter −= 5
  → SPAWN

SPAWN
  S1  current = queue.pop();  queue.append(generate())
  S2  Spawn 앵커(§2 SPN-1, SpawnPolicy), rotation 0 에서 충돌?
        YES → GAME_OVER  (blockCounter, Level, 타이머 변경 없음. 여기서 종료)
  S3  blockCounter += 1
      blockCounter ≥ 10 이면 Level += 1, blockCounter −= 10
  S4  낙하 타이머를 interval(Level)로 재설정하고 재시작
```

- **PIPE-1** Level은 **L5와 S3에서만** 바뀌며 둘 다 블록 사이(진행 중인 블록이 없을 때)다. 따라서 진행 중인 블록의 점수와 속도는 Level 변경의 영향을 받지 않는다. v1.0 §12의 "해당 블록부터 / 다음 Spawn 블록부터" 문구는 이 순서로 자동 만족된다.
- **PIPE-2** L5에서 `lineCounter ≤ 4`, `n ≤ 4`이므로 줄 카운터에 의한 상승은 한 사이클에 **최대 1회**다. S3도 최대 1회이므로 한 사이클의 Level 상승은 **최대 +2**다(v1.0 §11.3과 일치).
- **PIPE-3** 줄 삭제 보너스(L4)는 Lock된 시점의 점수에 더하며, 그 사이클의 Level 상승과 무관한 고정값이다. **[확정]**
- **PIPE-4** 게임 시작(LOADING): 보드 비우기 → `Level=1, score=0, 카운터 0` → 큐 초기화 → S1~S4 수행. 첫 블록도 S3에서 `blockCounter=1`이 된다. **[확정]**

### 4.1 검증용 시나리오 (그대로 테스트 케이스가 된다)

| 상황 | 결과 |
|---|---|
| Level 1, `blockCounter=9`, `lineCounter=3`인 상태에서 2줄 삭제 Lock 후 다음 블록 Spawn | L5: `lineCounter=5→0`, Level 2 · S3: `blockCounter=10→0`, Level 3. **새 블록은 Level 3(600 ms, 3점/칸)** |
| 12번째 블록 Spawn까지 줄 삭제 없음 | Level 2, `blockCounter=2` (10번째 Spawn 시점에 Level 2) |
| 7줄 누적 삭제 (한 번에 1줄씩) | 5번째 줄에서 Level +1, `lineCounter=2` |
| Spawn 충돌로 Game Over | 그 블록은 `blockCounter`에 포함되지 않음 |

---

## 5. 레벨·속도

- **LVL-1** Level 1에서 시작하고 상한은 없다. **[확정]**
- **LVL-2** 낙하 간격 `interval(level) = max(250, 1200 − 200 × level)` ms. **[확정]** (v1.0 §10 표를 한 식으로 표현)

| Level | 1 | 2 | 3 | 4 | 5 이상 |
|---|--:|--:|--:|--:|--:|
| interval | 1000 | 800 | 600 | 400 | 250 |

- **LVL-3** 두 카운터(블록 10개, 삭제 5줄)는 서로 독립이며 각자 기준값만큼 차감하고 나머지를 유지한다. **[확정]**
- **LVL-4** Level, 카운터, 점수는 Restart와 새 게임에서만 초기화한다. **[확정]**
- **LVL-5** 속도·Level 정책은 `LevelPolicy` 한 곳에서만 계산하고 UI에 중복 구현하지 않는다. **[확정]**

---

## 6. 점수

- **SCR-1** 하강 1칸 성공(자동·Soft Drop 동일)마다 `+Level`. **[확정]**
- **SCR-2** Hard Drop은 `실제 이동 칸 수 × Level`, 별도 보너스 없음. **[확정]**
- **SCR-3** 줄 삭제 보너스 `1줄 +100 / 2줄 +300 / 3줄 +500 / 4줄 +800`. Level 영향 없음. 요구사항에 직접 제시되지 않은 **팀 추가 점수 규칙**이다. **[확정]**
- **SCR-4** 아래는 점수가 0이다: 좌우 이동, 회전, 실패한 하강 시도, Level 상승, Game Over. **[확정]**
- **SCR-5** 점수는 `long`으로 관리한다(스코어보드 JSON에도 정수로 저장). **[확정]**
- **SCR-6** 점수 계산은 `ScoringPolicy` 한 곳에서만 한다. **[확정]**

**검증 예시** — Level 3에서 자동·Soft Drop으로 5칸(15점), Hard Drop으로 8칸(24점), Lock으로 2줄 삭제(+300점) → 이 블록 사이클의 점수 합 **339점**.

> 원 요구사항의 "1칸당 1점 + 속도가 빨라지면 추가 점수"는 `Level × 칸`으로 충족된다. Level 1은 1점(기본), Level 3은 1점 + 추가 2점이다.

---

## 7. 입력(키)

### 7.1 기본 키 **[확정]**

| 기능 | 기본 키 | 기능 | 기본 키 |
|---|---|---|---|
| 왼쪽 이동 | ← | Hard Drop | Space |
| 오른쪽 이동 | → | Pause / Resume | P |
| Soft Drop | ↓ | 현재 게임 종료 | Q |
| 회전 | ↑ | | |

메뉴 키 `↑ ↓ Enter Esc`는 설정 대상이 아니다. **[확정]**

### 7.2 키 지정 규칙 **[확정]**

- **KEY-1** 지정 가능한 키: `A–Z`, `0–9`, 방향키 4개, `Space`.
- **KEY-2** 지정 불가(예약·무시): `Enter`, `Esc`, `Shift/Ctrl/Alt/Win/CapsLock` 등 수정·잠금 키, `F1–F12`, 그 외 위에 없는 키. 시도하면 "사용할 수 없는 키입니다" 안내 후 변경하지 않는다.
- **KEY-3** 7개 기능은 항상 서로 다른 키에 할당되어 있어야 한다. 이미 다른 기능이 쓰는 키를 지정하면 **변경을 적용하지 않고** "이미 [기능]에 사용 중입니다"를 안내한다(자동 교환 없음). **[확정]**
- **KEY-4** 같은 기능에 현재 키와 같은 키를 다시 지정하면 오류 없이 변경 없음으로 처리한다.
- **KEY-5** 방향키를 게임 기능에 지정해도 메뉴·확인창에서는 항상 메뉴 규칙(↑↓ 이동)이 우선한다. 화면 맥락이 다르므로 충돌하지 않는다.
- **KEY-6** 저장 값은 `KeyEvent` VK 코드(정수)다. 표시 문자열은 실행 시 `KeyEvent.getKeyText`로 만든다. Key Guide 문구는 현재 설정에서 생성하며 하드코딩하지 않는다. 담당 메서드: `KeyMap.displayName(action)`(키 이름), `Messages.keyGuide(screen, keyMap)`·`Messages.pauseKeyGuide(keyMap)`(화면별 문구). **[확정]**

### 7.3 반복 입력 **[확정]**

| 입력 | 키를 누르고 있을 때 |
|---|---|
| Left / Right / Soft Drop | OS 반복 이벤트마다 1회 처리 (DAS/ARR 없음) |
| Rotate / Hard Drop / Pause / Quit | **최초 눌림만 처리**하고, 키를 뗀 뒤 다시 눌러야 다시 처리 |

- 이유: Space를 누르고 있으면 다음 블록들이 계속 Hard Drop되어 실수로 게임이 끝난다.
- **KEY-7** 수정 키(Shift/Ctrl/Alt)가 눌린 상태의 입력은 1차에서 무시한다.
- **KEY-8** 창이 포커스를 잃으면 눌림 상태를 모두 초기화한다.
- **KEY-9** Swing은 `InputMap/ActionMap` Key Binding(`WHEN_IN_FOCUSED_WINDOW`)을 쓴다. "눌림/뗌"을 모두 바인딩해 위 규칙을 구현한다. 화면에 포커스를 받는 `JButton`을 두지 않는다(Space가 버튼을 누르는 문제 방지). **[확정]**
- **KEY-10 [확정]** 한글 IME가 켜져 있으면 Java에서 문자 키의 `keyCode`가 기대와 다르게 올 수 있다. 게임 패널은 `enableInputMethods(false)`로 시작하고, **한글 입력 상태에서 P/Q/방향키가 동작하는지 수동 검증**한다. 2026-10-04 실제 플레이로 검증 완료(이름 입력칸의 한글 조합 포함).

---

## 8. 시간·Pause·Restart·Quit·종료

### 8.1 타이머 **[확정]**

- **TMR-1** `GameSession`은 Swing EDT **단일 스레드**에서만 동작한다(`javax.swing.Timer`, coalesce 켬). 별도 스레드 경합을 만들지 않는다.
- **TMR-2** 게임 로직은 Swing과 무관한 `tick()` 메서드로 노출한다. 테스트는 타이머 없이 `tick()`을 직접 호출한다.
- **TMR-3** 타이머는 **Spawn 직후**, **Resume 직후**, **확인창 취소 직후**에 전체 간격으로 재시작한다. 그래서 블록은 Spawn 후 최소 1개 간격 뒤에 처음 떨어지고, Pause 시간이 밀린 Tick으로 몰려오지 않는다. **[확정]**
- **TMR-4** Soft Drop과 좌우 이동은 낙하 타이머를 리셋하지 않는다.

### 8.2 GameState 전이

| From | To | 트리거 |
|---|---|---|
| LOADING | PLAYING | 초기화 완료 (UI 없이 즉시) |
| PLAYING | PAUSED | Pause 키, 창 포커스 이탈, Quit 확인창 표시 |
| PAUSED | PLAYING | Resume, 확인창에서 No |
| PLAYING | GAME_OVER | Spawn 충돌 (§2 SPN-4) |
| GAME_OVER | ENDED | Game Over 화면에서 Enter |
| PLAYING / PAUSED | ENDED | Quit 확인 Yes |
| PAUSED | LOADING | Restart 확인 Yes (새 세션으로 재초기화) |

v1.0의 전이 4줄에서 **PAUSED→ENDED, PAUSED→LOADING**을 추가했다. PLAYING 중 Quit 키를 누르면 `requestQuit`이 먼저 PAUSED로 바꾸므로, 실제로 ENDED에는 항상 PAUSED(또는 GAME_OVER)에서 들어간다. 앱 종료 시 `end()`는 어느 상태에서 불러도 안전하다(ENDED면 무시). GAME_OVER 상태에서는 게임 조작 입력을 모두 무시한다. **[확정]**

### 8.3 Pause / Restart / Quit

- **PAU-1** Pause 상태에서 자동 낙하·이동·회전·Soft/Hard Drop·점수·Level·카운터가 모두 멈춘다. **[확정]**
- **PAU-2** Pause 메뉴: `Resume / Restart / Quit to Main Menu`. `↑↓ Enter`로 선택하고 Pause 키 또는 `Esc`로 Resume한다. Quit 키는 Pause 상태에서도 동작한다. **[확정]**
- **PAU-3** 창이 포커스를 잃으면(`windowDeactivated`) `PLAYING`이면 자동으로 Pause한다. **[확정]**
- **RST-1** Restart는 Pause 메뉴에서만 가능하다. 확인창에서 Yes면 새 세션(Level 1, Score 0, 카운터·보드·큐 초기화)을 시작하고, No면 Pause 화면을 유지한다. 폐기된 게임 점수는 저장하지 않고 설정은 유지한다. **[확정]**
- **QIT-1** Quit 키는 프로그램 종료가 아닌 **현재 게임 종료**다. 확인창 동안 게임은 정지한다. No면 Quit 키를 누르기 전 상태로 돌아가고(PLAYING이었다면 §8.1 TMR-3에 따라 타이머 재시작), Yes면 세션을 폐기하고 Main Menu로 간다. 점수는 저장하지 않는다. **[확정]**
- **CNF-1** 확인창: `Yes / No`, 기본 선택은 **No**, `↑↓`(또는 `←→`)로 이동, `Enter` 확정, `Esc`는 No. **[확정]**
- **EXT-1** 창의 X 버튼/Alt+F4는 프로그램을 즉시 종료하고 진행 중인 게임을 폐기한다(확인 없음). 설정은 이미 자동 저장되어 있다. 이 경우 Name 입력 중이던 기록은 저장되지 않는다. **[확정]**
- **EXT-2** Main Menu의 `게임 종료`는 확인 없이 즉시 종료한다. 저장할 미완료 데이터가 없기 때문이다. **[확정]**
- **EXT-3** 게임 플레이 중 `Esc`는 아무 동작도 하지 않는다(실수 방지). **[확정]**

---

## 9. 화면 흐름과 화면별 규칙

```text
Main Menu ─ 게임 시작 ─▶ Game ⇄ Pause(오버레이)
   │                       │  └ Q → 확인 → Main Menu
   │                       └ Spawn 실패
   │                            ▼
   │                       GameOver
   │                            ▼
   │                  Top10? ─Yes▶ NameInput
   │                     │No        │
   │                     ▼          ▼
   ├─ 설정 ────▶ Settings       Scoreboard(신규 기록 강조)
   ├─ 스코어보드 ▶ Scoreboard        ▼
   └─ 게임 종료 ▶ 프로그램 종료   Main Menu / Exit
```

| 화면 | 규칙 | 태그 |
|---|---|---|
| Main Menu | 상단 `SE Tetris`. 항목 4개(게임 시작 / 설정 / 스코어보드 / 게임 종료). `↑↓`는 **순환(맨 위에서 ↑ → 맨 아래)**, `Enter` 선택. Esc는 무동작. 마우스는 지원하지 않는다(키보드 전용). 메뉴 목록과 렌더링을 분리해 항목을 추가할 수 있게 한다 | 확정 |
| Game | 표시: Board, Next, Score, Level, 현재 GameState, Key Guide. **Lines(누적 삭제 줄)**도 표시한다. State는 `GameState` 이름(`PLAYING` 등)을 그대로 보여준다. Ghost(착지 예상) 블록은 표시하지 않는다 | 확정 |
| Pause | 정지된 보드 위 오버레이. `CardLayout`만으로는 겹쳐 그릴 수 없으므로 **`PausePanel`·`ConfirmDialog`를 `GamePanel`의 자식 컴포넌트로 겹쳐 그린다**. 확인창이 떠 있는 동안 Pause 메뉴는 숨긴다. 새로 Pause할 때마다 선택은 첫 항목(계속하기). `Esc`도 Resume. **게임 세션 객체는 하나를 계속 쓰고 새 게임마다 `start()`로 초기화**한다 | 확정 |
| GameOver | `GAME OVER`, 최종 Score, Level 표시. `Enter`로 다음 단계 | 확정 |
| NameInput | Top 10 진입 시에만 표시(§11). `Enter` 저장(유효할 때만), `Esc`는 무동작(이름 입력을 건너뛰지 않는다) | 확정 |
| Scoreboard (게임 종료 후) | 신규 기록 행 강조. 하단 선택지 `Main Menu`(기본) / `Exit` | 확정 |
| Scoreboard (메뉴에서) | 강조 없음. `Enter` 또는 `Esc`로 Main Menu 복귀. 기록이 없으면 "기록 없음" 표시 | 확정 |
| Settings | Main Menu에서만 진입(Pause·게임 중 불가). 항목별 동작은 §10 | 확정 |

### 9.1 Key Guide **[확정]**

`Messages`(`keyGuide`, `pauseKeyGuide`)가 만드는 실제 문구다. 게임 화면은 정보 패널에 한 줄씩 그린다.

```text
게임      : ←/→ 이동 · ↓ 소프트 드롭 · ↑ 회전 · Space 하드 드롭 · P 일시정지 · Q 게임 끝내기
Pause     : ↑/↓ 선택   Enter 확인   P/Esc 계속
확인창    : ←/→ 선택   Enter 확인   Esc 아니오
Main Menu : ↑/↓ 이동   Enter 선택
Settings  : ↑/↓ 이동   Enter 선택   Esc 뒤로   (조작키 하위 화면: ↑/↓ 이동   Enter 키 변경   Esc 뒤로)
Scoreboard: Enter/Esc 메인 메뉴   (게임 종료 후: ↑/↓ 이동   Enter 선택)
GameOver  : Enter 계속
Name      : Enter 저장
```

게임·Pause 안내의 키 글자는 현재 `KeyMap`에서 만들며 하드코딩하지 않는다. 방향키는 화살표 기호로 표시한다.

### 9.2 UI 문자열과 언어 **[확정]** (T4)

- UI 문자열은 `tetris.ui.Messages` 한 곳에 모으고 **한글로 통일**했다(PR #22).
- 게임 용어(`SCORE`, `LEVEL`, `LINES`, `NEXT`, `STATE`, `PAUSED`, `GAME OVER`, `SCOREBOARD`)와 게임명 `SE Tetris`, 화면 크기 이름(`Small/Medium/Large`)은 영문을 유지한다.
- 바꿀 때는 `Messages.java`만 고친다. 없는 키를 요청하면 `IllegalArgumentException`.

---

## 10. 설정

- **SET-1** 설정 항목: Screen Size, Control Keys, Color Blind Mode, Reset Scoreboard, Reset Settings. **[확정]**
- **SET-2** 항목별 동작 **[확정]** (Control Keys 흐름의 담당 메서드: `SettingPanel.openControlKeys` → `beginKeyCapture(action)` → `captureKey(keyCode)` / `cancelKeyCapture` → `closeControlKeys`)

| 항목 | 조작 |
|---|---|
| Screen Size | `Enter`(또는 `←→`)로 Small → Medium → Large 순환. 즉시 적용하고 창을 다시 배치한다 |
| Color Blind Mode | `Enter`로 ON/OFF 토글. 즉시 적용 |
| Control Keys | `Enter` → 7개 기능과 현재 키 목록 → 기능 선택 후 `Enter` → "새 키를 누르세요 (Esc 취소)" → §7.2 검증 통과 시 즉시 적용 |
| Reset Scoreboard | 확인창(Yes/No) → Yes면 Top 10 전체 삭제. 설정은 바꾸지 않는다 |
| Reset Settings | 확인창(Yes/No) → Yes면 §10 기본값으로 복원. Scoreboard는 바꾸지 않는다 |

- **SET-3** 유효한 변경이 완료되는 즉시 적용하고 자동 저장한다. Save 버튼은 없다. **[확정]**
- **SET-4** 저장 실패 시 현재 실행에는 값을 적용하고, 실패를 알리며, 다음 실행은 마지막으로 정상 저장된 설정을 쓴다. **[확정]**
- **SET-5 [확정]** Reset Settings에도 확인창을 둔다(v1.0은 Reset Scoreboard만 명시). 조작키 전체를 잃는 동작이라 실수 방지가 필요하다. 확인창은 공용 `ConfirmDialog`(기본 선택 No, `Esc` = No).
- **SET-7 [확정]** 조작키 변경 시 예약키·중복 키는 안내 문구("사용할 수 없는 키입니다", "이미 [기능]에 사용 중입니다")만 표시하고 키는 바꾸지 않는다. 새 키 입력 대기 중에는 모든 키 눌림을 가로채므로 메뉴 키가 동작하지 않는다(`Esc`만 취소).
- **SET-6** 기본값 **[확정]**

| Screen Size | Color Blind | Left | Right | Soft Drop | Rotate | Hard Drop | Pause | Quit |
|---|---|---|---|---|---|---|---|---|
| Medium | OFF | ← | → | ↓ | ↑ | Space | P | Q |

---

## 11. 스코어보드

- **SBD-1** Top 10만 유지한다. **[확정]**
- **SBD-2** 진입 조건: `기록 수 < 10` 이거나 `현재 점수 > 10위 점수`. 그러면 이름 입력 화면을 표시한다. 동점이면 먼저 기록된 쪽이 앞이므로 10위와 같은 점수는 진입하지 못한다. **[확정]**
- **SBD-3** 삽입 규칙 **[확정]**: 새 기록은 `점수 ≥ 새 점수`인 마지막 기록 **바로 뒤**에 삽입한다. 그 뒤 10개를 넘으면 마지막을 버린다. 이렇게 하면 별도 시간 비교 없이 "먼저 기록된 쪽이 앞"이 보장된다.
- **SBD-4** 기록 필드 **[확정]**: `name`, `score`(필수, v1.0) + `level`, `lines`, `recordedAt`(ISO-8601, UTC; 표시·디버깅용). 순서는 파일 내 배열 순서가 기준이다.
- **SBD-5** 점수 0도 특별 취급하지 않는다(진입 조건을 그대로 적용). **[확정]**
- **SBD-6** 저장 시점: 이름 확정(`Enter`) 즉시 저장한다. 저장 실패 시 이번 실행에서는 메모리 상의 순위를 보여주고 실패를 알린다. **[확정]**
- **SBD-7** 프로그램 종료 후에도 유지하고, Reset Scoreboard 전까지 삭제하지 않는다. **[확정]**

### 11.1 이름 규칙

- **NAM-1** 허용 문자 **[확정]**: 한글 음절 `가–힣`, 한글 자모 `ㄱ–ㅎ ㅏ–ㅣ`, 영문 `A–Z a–z`, 숫자 `0–9`, 그리고 **문자 사이의 공백**. 그 외 문자(특수문자·이모지 등)는 입력을 받지 않는다.
- **NAM-2** 길이: 앞뒤 공백을 제거(trim)한 뒤 **1자 이상 10자 이하**. 허용 문자가 모두 BMP 문자라 `String.length()`로 센다. **[확정]**
- **NAM-3** 빈 문자열, 공백만 있는 이름은 금지하며 `Enter`를 눌러도 저장하지 않고 안내한다. 같은 이름의 중복 등록은 허용한다. **[확정]**
- **NAM-4** 한글 IME 조합 중에도 10자 제한이 지켜지도록 `DocumentFilter`로 제한한다(입력 후 검사만 하지 않는다). 담당: `NameInputPanel.createNameFilter()`. **[확정]**
  - IME 조합 문자열은 자르지 않고 통째로 받거나 거부한다. 거부 직후 Swing이 이미 입력된 글자를 지우려는 삭제 한 번은 필터가 무시한다(10자에서 한 글자를 더 칠 때 기존 글자가 지워지는 문제 방지).
- **NAM-5 [확정]** 이름 규칙(NAM-1~3)의 검사는 `ScoreEntry.isValidName` 한 곳이 맡는다. `ScoreEntry` 생성자는 이름 규칙, `score ≥ 0`, `level ≥ 1`, `lines ≥ 0`, `recordedAt ≠ null`을 검증하고 이름을 trim해 보관한다. 따라서 규칙을 어기는 기록 객체는 만들 수 없다.

| 입력 | 결과 |
|---|---|
| `홍길동` | 허용 |
| `A B` | 허용(내부 공백) |
| `ABCDEFGHIJK` (11자) | 11번째 입력 거부 |
| `   ` | 저장 불가 |
| `A!` | `!` 입력 거부 |

---

## 12. 저장

- **PER-1** 저장 파일은 `settings.json`, `scoreboard.json` 두 개뿐이다. 진행 중 게임 Save/Resume은 없다(`BlockSave` 제거). **[확정]**
- **PER-2** 위치는 `%APPDATA%\SETetrisTeam6\`. 환경변수 `APPDATA`가 없거나 비어 있으면 `{user.home}\AppData\Roaming`으로 대체한다. **[확정]**
  - 디렉터리는 **처음 저장할 때** 만든다(`DataDirectory`는 경로만 계산). 만들 수 없으면 그 저장이 실패(`save` → `false`)하고, 화면은 SET-4·SBD-6에 따라 이번 실행에만 적용하고 실패를 알린다. **[확정]**
- **PER-3** Jackson 호출은 저장 계층에서만 한다. 게임 로직과 UI는 Jackson을 직접 호출하지 않는다. **[확정]** 사용 버전: `jackson-databind 2.22.3`.
- **PER-4** 쓰기는 임시 파일(`*.json.tmp`)에 쓴 뒤 교체(원자적 이동, 지원하지 않으면 일반 교체)해, 쓰다가 프로그램이 종료돼도 파일이 반쯤 쓰인 상태로 남지 않게 한다. **[확정]**
- **PER-5** 모든 파일에 `schemaVersion`(정수, 현재 1)을 둔다. 읽을 때 **모르는 필드는 무시**하고 없는 필드는 기본값을 쓴다. 값 뒤에 다른 내용이 붙은 파일은 구문 손상으로 본다. **[확정]**
- **PER-6 [확정]** 저장소는 interface로 두고 JSON 구현을 따로 둔다. UI·`Main`은 interface 타입으로만 쓴다.

| interface | 구현 | 메서드 |
|---|---|---|
| `SettingsRepository` | `JsonSettingsRepository` | `LoadResult<Settings> load()`, `boolean save(Settings)` |
| `ScoreboardRepository` | `JsonScoreboardRepository` | `LoadResult<Scoreboard> load()`, `boolean save(Scoreboard)` |

  - `load()`는 예외를 던지지 않고 항상 값(문제가 있으면 기본값)과 경고 목록(`LoadResult.warnings`)을 돌려준다. `save()`는 실패하면 `false`.
  - 파일 입출력 공통 처리(읽기 상태 판정, 원자적 쓰기, `*.corrupt` 보관)는 `JsonFile`(패키지 내부)이 맡는다.
  - 테스트는 경로를 받는 생성자(`new JsonSettingsRepository(Path)`)로 임시 디렉터리를 쓴다.

### 12.1 파일 형식 **[확정]**

`settings.json` — `keys`의 이름은 `GameAction` enum 이름 그대로다. 값은 `java.awt.event.KeyEvent`의 키 코드.
```json
{
  "schemaVersion": 1,
  "screenSize": "MEDIUM",
  "colorBlindMode": false,
  "keys": {
    "MOVE_LEFT": 37, "MOVE_RIGHT": 39, "SOFT_DROP": 40, "ROTATE": 38,
    "HARD_DROP": 32, "PAUSE": 80, "QUIT": 81
  }
}
```

> 주의: `GameAction` 이름을 바꾸면 기존 파일의 `keys`가 무효가 되어 기본 조작키로 돌아간다(경고 표시). 이름을 바꿀 때는 저장 형식도 함께 검토한다.

`scoreboard.json`
```json
{
  "schemaVersion": 1,
  "entries": [
    { "name": "홍길동", "score": 12345, "level": 7, "lines": 31,
      "recordedAt": "2026-09-29T12:34:56Z" }
  ]
}
```

### 12.2 오류 처리표 **[확정]** (v1.0 §36)

| 상황 | 동작 | 사용자 알림 |
|---|---|---|
| `settings.json` 없음 | 기본 설정으로 실행. 파일은 처음 설정을 바꿀 때 생성 | 없음 |
| `settings.json` 읽기 실패 (권한 등) | 기본 설정으로 실행 | 경고 (Main Menu에서 1회) |
| `settings.json` 구문 손상 / 최상위가 JSON 객체가 아님 | 기본 설정으로 실행 | 경고 |
| `settings.json` 값 일부 무효 (알 수 없는 `screenSize`, boolean이 아닌 `colorBlindMode` 등) | **해당 필드만** 기본값으로 대체. 필드가 없으면 경고 없이 기본값 | 경고 |
| `settings.json`의 `keys`가 중복·누락·예약키 포함 | **`keys` 전체를** 기본값으로 대체(중복 금지 불변식 유지, `KeyMap.tryReplaceAll`) | 경고 |
| `scoreboard.json` 없음 | 빈 스코어보드 | 없음 |
| `scoreboard.json` 읽기 실패 (권한 등) | 빈 스코어보드 | 경고 |
| `scoreboard.json` 구문 손상 / `entries`가 배열이 아님 | 손상 파일을 `scoreboard.json.corrupt`로 옮기고 빈 스코어보드 | 경고 |
| `scoreboard.json` 일부 항목 무효 (이름 규칙 위반, 음수 점수, `score` 누락 등) | **해당 항목만** 버림. `level`·`lines`가 없으면 1·0, `recordedAt`이 없거나 잘못되면 1970-01-01T00:00:00Z로 살린다 | 경고 ("올바르지 않은 기록 N개를 제외") |
| `scoreboard.json`이 정렬 안 됨 / 10개 초과 | SBD-3 순서 규칙으로 정렬·자르기 | 없음 |

- 저장 파일 문제로 프로그램이 실행 불가 상태가 되면 안 된다. **[확정]** `Main`은 `load()`에서 예상 못 한 예외가 나도 기본값과 경고로 대체한다.
- 손상 파일은 이후 정상 저장 때 덮어쓴다. 스코어보드는 기록을 잃을 수 있으므로 **손상 파일을 `*.corrupt` 이름으로 옮겨 둔다**(이미 있으면 덮어씀, 옮기기 실패는 무시). 설정 파일은 옮기지 않는다. **[확정]**
- 프로그램 2개를 동시에 실행하는 경우는 방지하지 않는다(마지막 저장이 우선). 1차 범위 밖. **[확정]**

---

## 13. 렌더링 (Swing)

### 13.1 화면 크기

- **RND-1** 논리 보드는 항상 20×10이고 셀 표시 크기만 바뀐다. 기본은 Medium. **[확정]**

| 설정 | 셀 | 보드 픽셀 (폭 × 높이) |
|---|--:|---|
| Small | 20 px | 200 × 400 |
| Medium | 30 px | 300 × 600 |
| Large | 40 px | 400 × 800 |

- **RND-2** 창은 크기 조절을 막고(`setResizable(false)`), 화면 크기 변경 시 `pack()` 후 화면 중앙에 다시 배치한다. 보드 오른쪽에 정보 패널(Next, Score, Level, State, Key Guide)을 두며 패널 폭·글자 크기는 셀 크기에 비례한다. **[확정]**
- **RND-3 [확정]** (T3) Large는 보드만 800 px이고 제목 표시줄과 여백을 더하면 약 860 px다. Windows 11 노트북(1080p, 배율 125~150%)에서는 사용 가능 높이가 약 720~820 px라 그대로면 **화면 밖으로 잘린다**.
  - 창(테두리 포함)이 작업 표시줄을 뺀 사용 가능 화면에 맞지 않으면 **실제 셀 크기를 1 px씩 줄여** 맞는 최대값으로 표시한다. 최소 12 px. 설정값은 `LARGE`로 유지한다. 담당: `MainFrame.applyScreenSize`.
  - 창 배치: `[여백][보드 10c][여백][정보 6c][여백] × [여백][보드 20c][여백]`, 여백 = c/2. 모든 화면이 이 크기를 공유한다.

### 13.2 색과 글자

- **RND-4** 7종은 서로 다른 색과 `I/J/L/O/S/T/Z` 문자를 **항상 함께** 그린다(기본·색맹 모드 모두). 현재 블록, 고정 블록, Next는 동일한 렌더링 정책을 쓴다. **[확정]**
- **RND-5** 문자는 굵은 고정폭 글꼴, 크기는 셀의 약 60%. 글자색은 배경색 밝기에 따라 흑/백 자동 선택. **[확정]**
- **RND-6** 초기 팔레트 **[확정]** (v1.0은 RGB를 구현 단계로 미뤘으나, 팀원마다 다른 색을 고르지 않도록 시작값을 준다. 화면에서 보고 조정해도 되며, 기준은 아래 대비 조건이다.)

| 블록 | 기본 모드 | 색맹 모드 (Okabe–Ito 계열) |
|---|---|---|
| I | `#00BCD4` (청록) | `#56B4E9` (하늘) |
| J | `#1E4FD8` (파랑) | `#0072B2` (진파랑) |
| L | `#F57C00` (주황) | `#E69F00` (호박) |
| O | `#FBC02D` (노랑) | `#F0E442` (밝은 노랑) |
| S | `#2E9E44` (초록) | `#009E73` (청록 녹색) |
| T | `#8E24AA` (보라) | `#CC79A7` (분홍 보라) |
| Z | `#D32F2F` (빨강) | `#D55E00` (주홍) |

  - **대비 조건**: J와 L, S와 Z 쌍이 특히 잘 구분되어야 한다.
  - 색맹 모드는 적록·청황 색각이상에서도 밝기와 색상이 함께 구분되도록 위 팔레트를 쓴다. 문자 표시는 두 모드 모두 유지한다.
  - 원 요구사항의 색맹 항목은 "색상·패턴 조정"이므로, 색맹 모드에서 굵은 글자 등 추가 패턴을 넣는 것은 자유다.

---

## 14. 설계 구조

### 14.1 계층과 책임 **[확정]** (v1.0 §31)

```text
Swing UI / Key Binding  ──읽기──▶  GameSnapshot (보드 복사본, 현재·Next, Score, Level, State)
        │ 명령 호출
        ▼
   GameSession  (상태기계, tick(), §4 파이프라인 진입점)
        │
        ▼
 정책: RotationPolicy · LockPolicy · LevelPolicy · ScoringPolicy · BlockGenerator
        │
        ▼
 Board (유일한 보드 변경 지점) ◀── CrashDetector(판정만, 상태 변경 없음)
        │
        ▼
 Block(shape/rotate)

저장 계층: SettingsRepository · ScoreboardRepository (interface)
          └ JsonSettingsRepository · JsonScoreboardRepository · JsonFile · DataDirectory (Jackson은 여기서만)

앱 계층: Main (저장 파일 로드, EDT에서 시작) → MainFrame (화면 흐름·저장 호출, CardLayout)
```

- UI는 보드 배열을 직접 수정하지 않는다. `GameSession`이 공개하는 명령(`moveLeft`, `moveRight`, `moveDown`(=Soft Drop), `rotateRight`, `hardDrop`, `togglePause`(=`pause`/`resume`), `requestQuit`, `confirmQuit`, `cancelQuit`, `onFocusLost`, `restart`, `tick` 등)만 호출한다. Pause 키는 `togglePause` 하나에 연결하고, 현재 `GameState`에 따른 분기는 `GameSession`이 맡는다. **[확정]**
- 화면 갱신은 `GameSession`이 UI에 "상태가 바뀌었다"고 알리는 단순한 관찰자(listener) 방식이 좋다. UI가 주기적으로 게임 상태를 폴링하지 않는다. **[확정]**
- `GameState`(LOADING/PLAYING/PAUSED/GAME_OVER/ENDED)는 게임 진행 상태만 표현하고, MENU/SETTINGS/SCOREBOARD 같은 화면 흐름은 앱 계층이 관리한다. **[확정]**

### 14.2 확장 지점 (v1.0 §45 대응) **[확정]**

| 향후 기능 | 교체·추가 지점 |
|---|---|
| 7-bag | `BlockGenerator` 구현 교체 |
| Wall Kick / SRS | `RotationPolicy` 구현 추가 |
| Lock Delay | `LockPolicy` 구현 추가 |
| Next 여러 개 | `previewCount` 증가, UI는 큐에서 여러 개 그리기 |
| 메뉴 추가 | 메뉴 항목 목록과 렌더링 분리 |

### 14.3 파일 구조 (v1.2 기준) **[확정]**

파일이 아니라 메서드로 충분한 기능은 파일을 없애고 담당 클래스의 메서드 시그니처로 옮겼다.

```text
src/main/java/tetris/
├─ Main.java
├─ component/block/
│  ├─ Block.java                 (+ getType())
│  ├─ BlockType.java             (신규: I..Z, 셀 값 1..7)
│  └─ IBlock … ZBlock.java
├─ feature/
│  ├─ data/Board.java            (유지)
│  ├─ crash/CrashDetector.java   (판정 전용. IsBlockOnTop·IsRowFull 제거)
│  ├─ game/
│  │  ├─ GameSession.java        (이동·회전·드롭·Pause·Restart·Quit·Lock 파이프라인 메서드)
│  │  ├─ GameState.java
│  │  ├─ GameSnapshot.java · GameListener.java · GameClock.java   (신규)
│  ├─ rule/                      (신규: 교체 가능한 정책)
│  │  ├─ BlockGenerator · RandomBlockGenerator · NextBlockQueue · SpawnPolicy
│  │  ├─ RotationPolicy · BasicRotationPolicy
│  │  ├─ LockPolicy · ImmediateLockPolicy
│  │  └─ LevelPolicy · ScoringPolicy
│  ├─ score/    Scoreboard · ScoreEntry
│  ├─ setting/  Settings · ScreenSize · GameAction · KeyMap
│  ├─ save/     SettingsRepository · ScoreboardRepository (interface)
│  │            JsonSettingsRepository · JsonScoreboardRepository · JsonFile · DataDirectory · LoadResult
└─ ui/
   ├─ MainFrame · MenuItem
   ├─ MainMenuPanel · GamePanel · PausePanel · ConfirmDialog
   ├─ SettingPanel · ScoreBoardPanel · NameInputPanel · GameOverPanel
   └─ KeyBindingManager · BlockPalette · Messages · Theme · SwingGameClock

src/test/java/tetris/
├─ feature/
│    BoardTest · CrashTest · MoveTest · HardDropTest · SpawnPolicyTest · SpawnAndRotationTest
│    LockPipelineTest · LevelPolicyTest · ScoringPolicyTest · BlockTest · BlockGenerationTest
│    GameSessionFlowTest · KeyMapTest · ScreenSizeTest · ScoreEntryTest · ScoreboardTest
│    PersistenceTest · RandomInputStressTest · GameTestSupport
├─ ui/
│    KeyBindingManagerTest · MenuPanelsTest · MessagesAndPaletteTest · NameFilterTest
└─ debug/AsciiBoard
```

- **[확정]** 화면 전환 콜백을 받도록 UI 생성자 시그니처가 바뀌었다(PR #22): `GamePanel(Settings, PausePanel, Consumer<GameSnapshot> onGameOver)`, `KeyBindingManager(Runnable onQuit)`, `GameOverPanel(Runnable onContinue)`, `ScoreBoardPanel(Runnable onMainMenu, Runnable onExit)`, `SettingPanel(Settings, BooleanSupplier onChanged, BooleanSupplier onResetScoreboard, Runnable onBack)`, `MainFrame(Settings, Scoreboard, SettingsRepository, ScoreboardRepository)`.

### 14.4 Board 공개 API (0-기반 논리 좌표) **[확정]**

모두 구현되어 있다. 블록 위치 이동은 Board가 아니라 `Block.setPosition`으로 하고, `GameSession`이 `canPlace`로 먼저 확인한다.

| 메서드 | 기능 |
|---|---|
| `reset()` | 숨은 줄을 포함한 모든 칸 비우기 (벽은 유지) |
| `canPlace(block, row, col)` | 앵커를 (row, col)에 뒀을 때 유효한지 (상태 변경 없음, §3.1) |
| `canPlace(shape, row, col)` | 회전 전 확인용: 주어진 모양으로 유효한지 |
| `tryPlaceBlock(block)` | Lock: 블록 종류 값(1..7)으로 기록 (LCK-4). 놓을 수 없으면 아무것도 바꾸지 않고 `false` |
| `isRowFull(row)` | 행 완성 여부 (row 0..19, 범위 밖은 `false`) |
| `findFullRows()` | 완성 행 목록 (0-기반, 위→아래) |
| `clearRows(rows)` | 행 삭제·압축·상단 빈 행 (CLR-1, CLR-2). 숨은 줄도 함께 내린다 |
| `getDropDistance(block)` | Hard Drop 거리 (0 이상, 상태 변경 없음) |
| `copyCells()` | 보이는 영역 20×10 복사본 (UI/스냅샷용) |

Spawn 위치 계산은 Board에서 빠져 `SpawnPolicy.getSpawnRow/getSpawnColumn`(§2)이 맡는다. Spawn 충돌 판정은 `canPlace`를 그대로 쓴다(SPN-3).

내부 배열은 상하좌우로 `Board.MARGIN`(2)칸씩 늘린 24x14다(위: 빈 숨은 줄, 아래·좌우: 벽 `-1`). 논리 좌표 → 배열 인덱스 변환은 Board 안에서만 한다. 이전 이름(`CanPlace`, `PlaceBlock`, `GetFullRowCount`, `ClearLines`)은 위 이름으로 바꿨다.

**삭제 대상(메서드로 흡수)**

| 삭제 파일 | 흡수한 곳 |
|---|---|
| `move/MoveBlockLeft·Right·Down` | `GameSession.moveLeft/moveRight/moveDown` |
| `drop/BlockDrop`, `drop/ShiftRowDown`, `clear/LineClear·MultiLineClear` | `GameSession.hardDrop`, `Board.ClearLines` 등 |
| `playsetting/Pause·Restart·ReturnToMain` | `GameSession.pause/resume/restart/end`, `MainFrame.showScreen` |
| `timer/FallingTime·DecreaseTime` | `LevelPolicy.fallIntervalMillis` |
| `timer/RandomSelect` | `RandomBlockGenerator` |
| `score/ScoreCount·ScoreBoardSort·ScoreReset` | `ScoringPolicy`, `Scoreboard.add/clear` |
| `setting/BoardSizeChange·ColorChange·ControlKeyChange·ControlKeySet` | `Settings`, `ScreenSize`, `KeyMap.assign` |
| `save/BlockSave·ControlKeySave·ScoreBoardSave·ScoreSave·SettingSave` | `SettingsRepository`, `ScoreboardRepository` |
| `component/button/*` (9개) | `ui/MenuItem` (라벨 + 동작) |
| `ui/*Screen` (6개) | `ui/*Panel` |
| 테스트 `DropTest·ScoreTest·SaveTest·SettingTest·TimerTest` | 위 신규 테스트 클래스 |

---

## 15. 테스트

### 15.1 요구사항 → 테스트 매핑 **[확정]**

| 영역 | 검증 항목 (ID) |
|---|---|
| Board | 20×10 크기, 0-기반 좌표, 초기 빈 칸, 셀 값 범위 (G-1, G-2) |
| Spawn | I 앵커 `(−1,3)`, J/L/S/T/Z `(0,3)`, O `(0,4)`, 충돌 시 Game Over (SPN-1, SPN-4) |
| 이동/충돌 | 벽·바닥·고정 칸 충돌 시 입력 무시, 상태 불변 (§3.1) |
| 회전 | 시계 방향 4회 = 원상 복귀, O 불변, 벽 근처 실패, 세로 I `col 7 → 실패` (ROT-2~4, ROT-6) |
| Lock | 하강 실패 시 Lock, 착지 후 좌우 이동 가능, Hard Drop d=0 (LCK-2, §3.1) |
| 줄 삭제 | 1~4줄 동시 삭제, 압축, 상단 빈 행 (CLR-1~3) |
| 파이프라인 | §4.1 시나리오 4개 |
| Level/Speed | `interval(1..4)=1000/800/600/400`, `interval(5)=interval(50)=250`, 카운터 독립 누적 (LVL-2, LVL-3) |
| 점수 | 하강 `+Level`, Hard Drop `d×Level`, 보너스 100/300/500/800, §6의 339점 예시 |
| Next | 큐 길이 유지, 맨 앞 1개만 노출 (NXT-1) |
| 블록 생성 | 고정 시드로 7종 모두 등장, `BlockGenerator` 주입 (BLK-3) |
| Pause/Restart/Quit | 정지 중 tick 무시, Resume 후 타이머 재시작, Restart 초기화 항목, Quit 시 저장 안 함 (§8) |
| 키 설정 | 중복·예약키 거부하고 상태 불변, 같은 키 재지정 (KEY-1~4) |
| 스코어보드 | 진입 조건, 동점 삽입, 10개 제한, §11.1 이름 표 (SBD-2~5, NAM-1~4) |
| 저장 | §12.2 표의 모든 행을 임시 디렉터리로 재현, 원자적 교체 (PER-*) |
| UI 로직 | 키 반복 규칙·Pause 중 메뉴 키 우선·rebind (KEY-5, KEY-8, §7.3), 이름 필터 (NAM-1, NAM-4), 메뉴 순환·설정 화면, `Messages`·팔레트. 그리기 코드는 테스트하지 않는다 |

**스코어보드 검증 예시**: 10개 중 10위가 500점일 때 새 점수 500은 진입 불가, 501은 진입하고 기존 10위는 밀려난다. 기존 700점이 있을 때 새 700점은 그 뒤에 들어간다.

### 15.2 커버리지 **[확인]**

- 목표: 직접 작성한 코드의 **Line Coverage 70% 이상**(JaCoCo). **[확정]**
- v1.0에는 "단순 Swing 출력 코드를 억지로 테스트하지 않는다"고 되어 있는데, 분모에서 UI를 뺄지 넣을지 정하지 않았다. 권장 측정 범위:
  - **포함**: `feature.*`, `component.block.*`, 저장 계층, 정책 클래스
  - **제외**: `tetris.ui.*`의 그리기·레이아웃 코드, `Main`
  - 제외 범위는 교수자에게 확인한다(§17). 제외 규칙은 JaCoCo 설정에 명시하고 README에 남긴다.

### 15.3 비기능 테스트 **[확정]**

시드를 고정한 헤드리스 `GameSession`에 무작위 입력(이동·회전·Soft/Hard Drop·tick)을 **10,000회 이상** 적용한다. Game Over가 되면 새 세션을 시작하고, 매 연산 후 다음 불변식을 검사한다.

- 예외가 없다.
- 보드의 모든 값이 `0..7` 범위다.
- 현재 블록이 고정 칸과 겹치지 않고, 값 있는 칸이 모두 보드 안에 있다.
- Score는 감소하지 않고, Level은 1 이상이며 감소하지 않는다.

소요 시간은 로그로 남기되, **시간 임계값 단언은 구현 후 실제 측정한 뒤에** 넣는다(v1.0 방침 유지).

---

## 16. CI/CD·배포

- **CI-1** 트리거: Pull Request와 `main` 변경. 단계: Java 21 → Gradle 빌드 → JUnit → 비기능 테스트 → JaCoCo → Line Coverage ≥ 70%. 빌드·테스트·비기능 테스트·커버리지 중 하나라도 실패하면 CI를 실패로 한다. **[확정]** 커버리지 게이트는 `jacocoTestCoverageVerification`(LINE, 최소 0.70)으로 만들고 리포트를 아티팩트로 올린다. **[제안]**
  - **현재 상태 (2026-10-04)**: `.github/workflows/ci.yml`은 `main`·`develop` push와 두 브랜치 대상 PR에서 Java 21 + Gradle 8.10.2로 `gradle test`를 돌리고, 테스트 수·JaCoCo 커버리지 요약을 Actions Summary와 PR 댓글에 남기며, 테스트·커버리지 리포트를 아티팩트로 올린다. **커버리지 70% 게이트(`jacocoTestCoverageVerification`)와 UI 제외 규칙은 아직 없다.** §15.2 확인 후 추가한다.
- **CD-1** `main` 또는 릴리스 태그에서 `windows-latest` 러너 → Gradle 릴리스 빌드 → `jpackage` → 아티팩트/Release 업로드. **[확정]**
- **DEP-1 [확인]** 산출물 형태:
  - **권장**: `jpackage --type app-image`로 만든 폴더(`SE Tetris.exe` 포함)를 **zip으로 배포**. 설치 도구(WiX)가 필요 없어 CI가 단순하다.
  - 대안: `--type exe`/`msi` 설치형은 WiX 설치가 필요하다.
  - 어느 쪽이든 "별도 Java 설치·명령어 입력 없이 더블클릭 실행"과 "아이콘 포함" 조건을 만족해야 한다.
- **DEP-2** 콘솔 창 없이 실행한다(`--win-console` 사용 안 함). 번들 런타임에 `java.desktop`이 포함되는지, Jackson이 비모듈 JAR이라 `jlink` 대상 모듈에 영향이 없는지 **첫 패키징에서 확인**한다. **[제안]**
- **DEP-3** README에 다음을 기록한다: 요구 환경, 빌드·테스트·패키징 명령, 사용자 데이터 경로(`%APPDATA%\SETetrisTeam6\`), 설정·스코어보드 파일 형식(§12.1). **[확정 + 제안]**

---

## 17. 확인 필요 목록 (교수자 / 팀)

### 교수자 질문 (e-class)

| # | 질문 | 권장 입장 |
|--:|---|---|
| Q1 | "텍스트 기반 테트리스"를 Swing GUI 창에서 색상+문자로 표현해도 되는가? 콘솔 환경 구현 제한이 있는가? (v1.0 §47) | 허용되면 Swing 유지 |
| Q2 | "보드 크기 3종 이상" 요구를 **논리 20×10은 고정하고 셀 표시 크기 3종**으로 충족해도 되는가? 아니면 실제 행·열 수가 달라져야 하는가? | 셀 크기 3종. 원 요구사항의 20×10 규정과도 일치 |
| Q3 | Line Coverage 70% 측정에서 Swing UI 클래스를 분모에서 제외해도 되는가? | 제외 (§15.2) |
| Q4 | 배포물은 jpackage app-image(zip)도 인정되는가, 설치형이어야 하는가? | app-image zip 허용 (§16 DEP-1) |

Q2가 특히 중요하다. 답이 "실제 행·열 수 변경"이라면 v1.0 §2가 바뀌고 Board, Spawn, 테스트 범위가 모두 영향을 받는다.

### 팀 결정

| # | 결정 사항 | 권장 | 확정 (2026-10-04, 구현 기준) |
|--:|---|---|---|
| T1 | LCK-2: Lock 시점 (착지 후 다음 하강 실패 vs 착지 즉시) | 하강 실패 시 (A) | **A** (`ImmediateLockPolicy`) |
| T2 | SPN-2: I의 Spawn 줄 (행렬 원점 row 0 vs 값 있는 칸 top-align) | 행렬 원점 row 0 | **top-align + 숨은 줄 2행** (권장과 다름, §2) |
| T3 | RND-3: Large의 화면 초과 처리 | 자동 축소 | **자동 축소** (최소 12 px) |
| T4 | §9.2: UI 언어 통일 | 팀이 선택 | **한글 통일**, 게임 용어만 영문 |
| T5 | PAU-3: 창 포커스 이탈 시 자동 Pause | 사용 | **사용** (눌린 키 상태도 초기화) |
| T6 | SET-5: Reset Settings 확인창 | 사용 | **사용** |
| T7 | §7.2: 지정 가능한 키 집합 | 제시한 목록 | **제시한 목록** (`KeyMap.isAssignable`: A–Z, 0–9, 방향키, Space) |

T1~T7은 모두 2026-10-04 구현 기준으로 확정했다. T2만 최초 권장과 다르다. 남은 열린 항목은 교수자 질문 Q1~Q4와 §15.2 커버리지 범위, §16 배포(DEP-1, DEP-2)다.

---

## 18. 추적성과 저장소 정리

### 18.1 원 요구사항(TeamProject_Req1) ↔ 명세 ID

| 원 요구사항 | 명세 |
|---|---|
| 시작 메뉴 (게임 이름, 4개 항목, 키보드 탐색, 확장 가능) | §9 Main Menu |
| 20×10 보드, 7종 블록, 균등 랜덤, 서로 다른 색 | G-1, BLK-1~3, RND-4~6 |
| 이동·회전·Hard Drop, 반복 입력, Pause, 게임 중 종료 키 | §3, §7, §8 |
| 1초 낙하, 블록 수/줄 수에 따른 가속 | LVL-1~4, §4 |
| 점수(칸당 1점, 속도 증가 시 추가, 팀 추가 규칙, 실시간 표시) | SCR-1~3, §9 Game |
| 색맹 모드 | SET-2, RND-6 |
| 설정(크기 3종, 키, 스코어 초기화, 색맹, 전체 복원, 재실행 유지) | §10, §12 |
| Game Over → 스코어보드 → 이름 입력 → 강조 → 메뉴/종료 | SPN-4, §9, §11 |
| Top 10 이상 스코어보드와 영속성 | §11, §12 |
| Git 추적, 테스트 커버리지, 비기능 테스트, 더블클릭 배포 | §15, §16 |

### 18.2 저장소 기존 문서·골격과의 차이 **[정리 필요]**

기존 `docs/requirements-spec.md`는 v1.0과 아래 항목이 충돌하므로 **이 문서로 대체하고 옛 문서는 폐기(또는 `docs/archive/`로 이동)**한다.

| 항목 | 기존 문서 | v1.0 / v1.2 |
|---|---|---|
| UI | 텍스트 기반 **콘솔** UI | **Swing** 텍스트형 UI |
| 보드 크기 | Small 15×8 / Medium 20×10 / Large 25×12 | 20×10 고정, 셀 크기 3종 |
| 내부 배열 | 22×12 벽 배열이 정책 | 구현 자유, 외부 API는 논리 좌표 |
| 점수 | 칸당 1점 + 속도 추가 1점 | `Level × 칸` |
| 저장 | `BlockSave`로 게임 재개 | 진행 중 게임 저장 없음 |
| 저장 포맷 | 텍스트 파일 | JSON(Jackson) 2개 |
| 커버리지 | 50~70% 이상 | 70% 이상 |
| 스코어보드 | "최소 상위 10개" | Top 10만 유지 |

### 18.3 기존 클래스 골격 처리 방향 **[확정]** (최종 구조는 §14.3 참고)

| 기존 | 방향 |
|---|---|
| `ui/*Screen` (Landing, Menu, Board, Setting, ScoreBoard, End) | Swing 패널(`MainMenuPanel`, `GamePanel`, `SettingPanel`, `ScoreBoardPanel`, `GameOverPanel`, `NameInputPanel`, `PausePanel`)로 재정의. Landing과 Menu는 통합 |
| `component/button/*` | Swing에서는 메뉴 항목(라벨+동작) 목록으로 대체 가능. 필요성 재검토 |
| `feature/move/*`, `drop/*`, `clear/*` | 판정·계산은 순수 로직으로 남기고, 보드 변경은 `Board`로 통일. 클래스를 합쳐도 무방 |
| `feature/crash/CrashDetector` | 유지 (판정 전용). 상단 도달로 Game Over를 판정하는 `IsBlockOnTop`은 SPN-4와 충돌해 제거. **[확정]** `CanPlace`·`IsRowFull`은 배열 인덱스 기준 판정으로 남아 있고, `Board.canPlace`·`Board.isRowFull`이 논리 좌표를 변환해 호출한다 |
| `feature/timer/FallingTime`, `DecreaseTime` | `LevelPolicy`(간격 계산)로 흡수 |
| `feature/timer/RandomSelect` | `BlockGenerator` 구현체 |
| `feature/setting/BoardSizeChange` | 삭제 후 `ScreenSize`(셀 크기) 설정으로 대체. 논리 보드는 변하지 않는다 |
| `feature/setting/ControlKey*`, `ColorChange` | 유지 (§7, §13 규칙 반영) |
| `feature/save/BlockSave` | 삭제 |
| `feature/save/ControlKeySave`, `SettingSave`, `ScoreSave`, `ScoreBoardSave` | `SettingsRepository`(키 포함), `ScoreboardRepository` 두 개로 통합 |
| `feature/score/ScoreCount`, `ScoreBoardSort`, `ScoreReset` | `ScoringPolicy`, 스코어보드 삽입 로직(SBD-3), 스코어보드 `clear`로 정리 |
| `feature/playsetting/Pause`, `Restart`, `ReturnToMain` | `GameSession`의 메서드로 흡수 |
| `turn/BlockTurnRight` (문서에는 있으나 저장소에 없음) | `RotationPolicy`로 대체. 행렬 회전 자체는 `Block.rotateRight()`가 이미 제공 |

---

## 19. 구현 우선순위 (v1.0 §43 유지)

1. 데이터 모델: Board 20×10, 7종 Block, Spawn, 회전 상태, 기본 충돌, Board 단위 테스트
2. 핵심 규칙: 이동, 회전, Soft/Hard Drop, Lock, 줄 삭제, Game Over, 점수 — **§4 파이프라인 먼저**
3. 게임 진행: GameSession, GameState, 낙하 타이머, Level/Speed, Next, Pause/Restart/Quit
4. Swing UI: MainFrame, 각 Panel, 보드 렌더링, Key Binding
5. 부가 기능: Screen Size, Color Blind, 키 설정, JSON 저장, 스코어보드, Reset
6. 테스트·배포: 커버리지 70%, 비기능 테스트, CI, jpackage, CD, README
