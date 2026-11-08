# Sprint 8: 실제 로컬 일반 대전 통합

## 반영 범위

기준 `develop b505465`의 B 화면/입력과 C 네트워크 구현에 S8-A1/A2를 추가했다. 기본 `MainFrame`에서 대전 → 로컬 일반 대전을 선택하면 `LocalBattleClient`와 실제 두 `GameSession`이 실행된다. 아이템·시간제한 대전, P2P 경기 엔진 연결은 S9 범위로 남긴다.

- `Board.insertAttackRows`: 내부 숨은행을 포함한 고정 칸과 아이템 표시를 함께 밀고 공격 행 순서·구멍·벽을 보존한다. 공격칸 값은 9이며 아이템은 없다. 고정 칸이 보이는 보드 위로 밀리면 패배다.
- `GameSession.applyPendingAttack`: Lock/삭제 정산 뒤, 지연 Spawn 경계에서만 공격을 받는다. 싱글의 기본 즉시 Spawn 경로는 유지한다.
- `LocalMatchEngine`: 두 due 시계를 시각 순서로 진행하고, 공격 수집 → 상대 큐 반영 → 수신 삽입 → 양쪽 Spawn → 승패 확정 순서로 처리한다. 동일 pulse의 양쪽 패배는 무승부이며 종료 후 두 시계와 후속입력을 멈춘다.
- 수신 공격은 FIFO 최대 10줄이며 상쇄하지 않는다. 현재 블록/삭제 애니메이션 중에는 큐에 보관하고 다음 Spawn 직전에 한 번 소모한다.
- Spawn 대기 중 연속 입력은 다음 pulse로 넘긴다. 비반복 입력의 물리 press/release 래치는 입력 수신 시 확인하므로 중복 Hard Drop이 다음 블록으로 새지 않는다.
- `LocalBattleClient`: EDT 16ms 타이머를 소유하고 화면에 불변 스냅샷을 전달한다. Pause/Quit는 모달 창 전에 동기 반영한다. 결과 중 키 해제 재진입이 결과를 중복 발행하지 않는다.
- 기권은 상대 승리 결과를 표시한다. P를 누른 상태에서 재개 후 Quit 확인창을 열어도 양쪽을 정지한다. 대전 결과는 싱글 점수 저장 경로로 전달하지 않는다.

## 최종 검증

2026-11-08 / Linux / Temurin JDK 21 / Gradle 8.10.2. 기존 Xorg dummy 가상 화면에 xfwm4 창 관리자를 실행했다.

```bash
gradle check guiAcceptance releaseJar -PnativeInput=true --offline --no-daemon --max-workers=2
```

- `check`: 1,087개 중 **1,072 통과 / 0 실패 / 15 생략**. 생략은 기본 headless 실행에서 GUI 인수 테스트를 제외한 결과다.
- 네트워크 테스트 **97개 / 실패 0**. 실제 loopback 및 두 프로세스 TCP, 연결/취소/타임아웃, READY/CONFIG/START 회귀를 포함한다.
- `guiAcceptance`: **14 통과 / 0 실패 / 0 생략**. 기존 OS native 겹친 입력과 기본 생성자의 실제 로컬 엔진에서 P1/P2 좌우 이동, 결과 전환, 점수 메모리/파일 미저장을 확인했다. 좌우 이동 검증은 중력 낙하로 충족할 수 없다.
- JaCoCo LINE **90.08%**, 70% 게이트 통과.
- 실행 JAR: `build/libs/SE-Tetris.jar`.
- 실제 가상 화면의 Robot 캡처: `build/verification/sprint8-local-screen.png`.

초기 GUI 실행은 창 관리자가 없는 Xorg에서 기존 `allConfiguredSizesFitCurrentScaledDesktop`가 LARGE=MEDIUM으로 측정되어 실패했다. 같은 크기 API를 별도 재현한 뒤 xfwm4를 실행하자 단독 검사와 최종 전체 GUI가 통과했다. 테스트를 생략하거나 크기 조건을 완화하지 않았다.

## 테스트 선행 및 독립 AI 리뷰

공격 삽입, 엔진, 클라이언트는 클래스/메서드 부재의 실패를 먼저 확인했다. 실제 기권 통합에서 결과 중복 콜백을 재현해 수정했다. 별도 읽기 전용 AI 리뷰가 발견한 P 래치/모달 Pause와 직접 엔진 호출의 중복 Hard Drop을 각각 실패 테스트로 확인하고 수정한 후 전체 검사를 재실행했다. AI 리뷰는 다른 팀원 승인이 아니다.

## 남은 실기·협업 인수

- 실제 사용자 물리 키보드의 동시 입력/롤오버 확인: 가상 X11 OS 키 주입으로 인증할 수 없다.
- 다른 팀원 1명의 실제 리뷰 및 PR 병합.
- Windows CI 결과는 PR의 해당 실행 결과로 별도 확인한다. 이 문서의 로컬 Linux 통과를 Windows 실행 근거로 쓰지 않는다.
- 두 PC LAN 지연 실측은 S11 범위이며 로컬 loopback 결과로 대신하지 않는다.

따라서 구현·로컬 자동 검증 완료와 Sprint 전체 Done을 구분하고 Notion은 Review로 유지한다.
