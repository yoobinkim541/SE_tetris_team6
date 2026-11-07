# 3차 요구사항 결정 기록 (S7-A1)

Notion "팀 결정 제안: S7-A1에서 확정하고 근거 기록"을 계약 v1에 반영한 결과다. 원문 필수 / 팀 결정 / 교수님 확인 필요로 나눈다.
원문이 모호하거나 팀끼리만 정하면 문제가 될 수 있는 항목은 e-class에 문의한다 (Req3 p14).

## A. 원문 필수

| ID | 내용 | 근거 |
|---|---|---|
| F-01 | 2줄 이상 삭제해야 공격 | p4 |
| F-02 | 넘어가는 줄은 마지막 블록 칸을 뺀 형태 그대로 | p4–5 |
| F-03 | 공격 줄은 현재 블록 처리 뒤, 다음 Spawn 전에 아래쪽에 추가 | p4 |
| F-04 | 대기열 최대 10줄. 10줄이면 무시, 합쳐서 넘치면 가장 아래쪽 버림 | p6 |
| F-05 | 대전 결과는 스코어보드에 기록하지 않음 | p5 |
| F-06 | P2P 모드는 호스트만 선택. 양쪽이 '게임 시작'을 눌러야 시작 | p8 |
| F-07 | P2P 경기 후 연결 직후 대기 상태로 복귀 | p9 |
| F-08 | 동일 LAN 입력→표시 200 ms 이하 | p9 |

## B. 팀 결정 (Notion 제안 기본값 → 계약 v1)

| ID | 결정 | 계약 반영 | 확인할 것 |
|---|---|---|---|
| D-01 | 대전 모드는 `BattleMode {CLASSIC, ITEM, TIMED}`. 싱글 `GameMode`와 섞지 않음 | `BattleMode` | — |
| D-02 | **동시 패배**: 같은 판정 프레임(pulse)의 양측 패배는 무승부. 콜백 도착 순서·클라이언트 수신 시각으로 승패를 정하지 않음 | `MatchResult.draw(TOPPED_OUT)` | — |
| D-03 | **Pause·Quit**: 누구나 전체 Pause/Resume 요청, 호스트가 순서를 정해 자동 적용·통지(승인 버튼 없음). 경기 중 Quit 확정은 기권 결과 후 로비. 로비 떠나기는 연결 종료 | `EndReason.FORFEIT` | — |
| D-04 | **공격 칸**: 값 9, 회색, 아이템 없음 | `VersusRules.ATTACK_CELL` | palette·직렬화·스트레스 값 범위 동시 갱신 (B·C) |
| D-05 | **시간제한**: 180초, 일반 블록, NORMAL. 동점은 무승부. 마감과 같거나 늦은 입력은 무시하고, 마감 전에 정산된 점수만 비교(CLEARING 중 미확정 점수 제외) | `DEFAULT_TIME_LIMIT_MILLIS`, `MatchResult.draw(TIME_UP)` | 원문에 시간·동점·아이템 조합 미정 → Q-01 |
| D-06 | **마감·패배 우선순위**: 같은 pulse에서 쌓기 불가가 확정되면 TOPPED_OUT이 TIME_UP보다 우선 | `MatchEngine` 문서 | — |
| D-07 | **시계**: 대전은 `DueTimeClock`이 GameClock의 start/stop을 마감 시각으로만 기록. 호스트 EDT pulse(16 ms)가 due인 낙하·삭제만 실행. 싱글은 SwingGameClock 유지. C는 tick을 보내지 않음 | `DueTimeClock`, `MATCH_PULSE_MILLIS`, `MatchEngine.advance` | — |
| D-08 | **pulse 순서**: due 작업·입력 → 삭제 확정·공격 수집 → 대기열 반영 → Spawn 대기 세션에 공격 삽입 → 양측 Spawn → 승패 확정. 새 블록이 이미 나온 뒤 도착한 공격은 그 블록의 다음 Lock 경계에 적용 | `SpawnBoundary`, `MatchEngine` | — |
| D-09 | **공격 대상**: 최종 삭제 행 합집합 중 보이는 행. 자연 완성 + L 삭제 행 포함, 중복 제거 후 2줄 이상이면 전송. L이 지운 불완전 행은 기존 빈칸을 유지해 보냄. B/W의 칸 삭제는 행 수에 넣지 않음 | `LockResult.clearedRows` | Q-02 |
| D-10 | **공격 순서**: 행 위→아래, 열 왼→오. 기존 대기열 뒤에 추가. 상쇄 없음 | `AttackRows` | — |
| D-11 | **삽입 시점**: 내 줄 삭제·점수·효과 정산 뒤, 새 블록 생성 전. CLEARING 150 ms 도중 즉시 삽입 금지. 공격은 삭제 확정 때 1회 발행 | `LockResult`, `SpawnBoundary` | — |
| D-12 | **상단 초과**: 공격 삽입으로 기존 고정 칸이 보이는 보드 위로 밀리면 패배 | S8-A1 | 숨은 생성 행 경계 확인 |
| D-13 | **입력**: 반복 가능한 이동(MOVE_LEFT/RIGHT/SOFT_DROP)은 OS 반복 press마다 1회, ROTATE/HARD_DROP/PAUSE/QUIT은 press→release 사이 1회. release는 CLEARING·PAUSED·결과에서도 held 해제. Spawn 대기 중(currentBlock null) 세션에는 이동·회전·Hard Drop을 전달하지 않고 다음 pulse로 넘김 | `PlayerCommand` | — |
| D-14 | **조작 키**: P1 방향키/Space, P2 A/D/S/W/F, 공통 P Pause·Q Quit. 싱글 키를 덮어쓰지 않음 (`BattleKeyMap` 및 `docs/sprint7-b.md` 기준) | B 소유 | 동시 입력 한계 실측 |

### C 소유 결정 (참고, C가 확정)

| 항목 | 제안 기본값 |
|---|---|
| 지연 | heartbeat 500 ms, RTT > 200 ms 3회 또는 정상 메시지 1.5초 미수신 |
| 끊김 | 연결 시도 5초, 정상 메시지 5초 미수신 |
| 회복 | RTT ≤ 150 ms 3회·정상 수신 회복 시 해제 |
| 최근 IP | 성공한 최근 5개, 중복 제거, `recent-peers.json` |
| 로비 상태 | INITIAL → HOSTING/CONNECTING → LOBBY → STARTING → PLAYING → RESULT → LOBBY. 접속 오류·취소·경기 중 단절은 안내 후 INITIAL |

로비 계약(`LobbyClient`, `LobbySnapshot`)은 C 소유라 A의 api 패키지에 두지 않는다. 처음 초안(커밋 ad53fab)의 host/connect/cancel/configure/ready/battle, configRevision이 바뀌면 ready를 지우는 규칙은 C가 참고용으로 볼 수 있다.

## C. 교수님 확인 필요 (e-class 문의 후보)

| ID | 질문 | 임시 처리 |
|---|---|---|
| Q-01 | 시간제한 모드의 시간(180초)·동점 무승부·아이템 미사용을 팀이 정해도 되는가 | D-05 |
| Q-02 | 아이템 대전에서 L 아이템이 지운 불완전 행도 공격으로 보내고, 무게추 등 칸 삭제는 공격에서 빼도 되는가 | D-09 |
| Q-03 | 대전 모드에도 난이도(easy/hard 확률·속도)를 적용해야 하는가 | 기본 NORMAL, 설정에 포함 |
