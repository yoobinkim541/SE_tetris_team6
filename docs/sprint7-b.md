# Sprint 7 B: 화면 모형과 입력

원본: [Sprint 7](https://app.notion.com/p/40da13e7df0081b5b454e29d60bdcf0e), S7-B1 / S7-B2.
기준 커밋: `553da83` (`origin/develop`). 기존 작업 폴더의 미커밋 변경은 포함하지 않았다.

## 실행

프로젝트 루트에서 JDK 21 이상으로 실행한다.

```powershell
.\gradlew.bat classes
java -cp build/classes/java/main tetris.ui.versus.BattleDemo
java -cp build/classes/java/main tetris.ui.versus.BattleDemo --color-blind
```

화면 수명 검증(네이티브 Windows 창, 자동 종료):

```powershell
.\gradlew.bat test --tests 'tetris.versus.ui.BattleLifecycleTest' -I docs/sprint7-b-gui.init.gradle
```

이 검증은 실제 등록된 포커스 콜백을 호출하고 창을 표시·숨겨 release 및 리스너 해제를 확인한다. OS의 실제 두 창 사이 키보드 포커스 이동을 자동화한 검증은 아니다.

두 20×10 보드, Next, 점수, 남은 시간, 0~10줄 공격 대기열을 표시한다.
대기열은 위에서 아래 순서이며, 공격칸은 `X` 무늬로 회색 `W`와 구분한다.
480×360 및 960×720에서 같은 비율로 축소·확대한다. 더 작은 창도 그릴 수 있지만 최소 권장 크기는 480×360이다.
데모의 명령 표시는 키 라우팅 확인용이다. 블록 이동·공격·승패 계산을 시뮬레이션하지 않는다.

## 기본키와 변경 정책

| 기능 | P1 | P2 |
|---|---|---|
| 왼쪽 / 오른쪽 | ← / → | A / D |
| Soft Drop | ↓ | S |
| 회전 | ↑ | W |
| Hard Drop | Space | F |
| 공통 Pause | P | P (공유 명령 하나) |
| 공통 Quit | Q | Q (공유 명령 하나) |

`BattleKeyMap.assign`으로 메모리 내 키를 바꿀 수 있다. A~Z, 0~9, 방향키, Space만 허용한다.
두 플레이어와 공통키 전체에서 중복을 거절하며 자동 교환하지 않는다. 실패 시 기존 매핑을 유지한다.
Pause/Quit은 어느 플레이어를 지정해도 공통 매핑 하나를 변경한다.
싱글 `KeyMap` 및 저장 파일은 읽거나 변경하지 않는다. 설정 UI·대전 키 영속화는 이번 S7 범위에 포함하지 않는다.

좌우·Soft Drop은 OS 반복 press마다 명령을 보낸다. 회전·Hard Drop·Pause·Quit은 release 전까지 한 번이다.
명령은 `(playerId, action, pressed, sequence)`이며 sequence는 라우터 수명 동안 증가한다.
공통 명령의 playerId는 ONE이다. 엔진은 PAUSE/QUIT을 매치 전체 명령으로 처리해야 한다.
수정 키를 누른 press는 받지 않지만, 모든 수정 키 조합의 release는 받는다.

## 수명과 통합 경계

- `BattleInputRouter`는 `JRootPane` 하나에 한 개만 설치한다. 자식 보드에 입력을 설치하지 않는다.
- `accepts`는 press 수락 여부만 결정한다. CLEARING/PAUSED/결과에서도 release는 전달한다.
- 포커스 이탈, 창 숨김·분리 시 held를 비우고 각각 release 명령을 보낸다.
- 메뉴·단절·결과 전환 시 `clearPressedKeys`, 새 matchId 수신 시 `resetForMatch`를 호출한다.
- 키 변경 시 `rebind`, 화면 폐기 시 `close`한다. 기존 루트 바인딩을 복원하고 리스너를 해제한다.
- 표시 DTO는 보드·Next·대기열을 생성 시와 접근 시 깊은 복사한다. 공격칸 값 9는 BlockType에 추가하지 않는다.
- `BoardRenderer`와 `BattlePanel`은 로컬/P2P에서 재사용한다. `GamePanel`의 싱글 그리기 경로는 유지한다.
- **A의 S7-A1 API가 기준 커밋에 없으므로** `ui/versus`에 임시 표시/입력 DTO와 포트를 둔다. A 소유 `feature/versus/api`를 대신 확정하지 않았다. 확정 후 어댑터로 변환해야 한다.
- Fake는 고정 MatchSnapshot과 명령 기록만 제공한다. 실제 GameSession, MatchEngine, Socket 의존은 없다.
- Swing 컴포넌트·라우터·Fake는 EDT에서 사용한다. 실제 네트워크 콜백은 어댑터가 EDT로 전달해야 한다.

## 검증 기록

2026-11-07, Windows / JDK 21.0.12.1:

- 기준 커밋의 `check` 통과.
- 최종 `.\gradlew.bat check --offline --no-daemon` 통과. 테스트 906개 중 실패/오류 0, GUI opt-in 등 10개 생략. 라인 커버리지 2176 / 2433 = 89.4% (70% 게이트 통과).
- 파트 B 기본 테스트 8개 통과: 불변 DTO/Fake 3개, 렌더링 2개, 입력 3개.
- 별도 네이티브 `BattleLifecycleTest` 1개 통과: 포커스 콜백·실제 창 숨김 release, close 리스너 해제.
- 색맹 960×720 렌더링 이미지를 직접 확인. Next 글자색 누수를 발견해 Graphics2D 격리 및 회귀 테스트로 수정.
- 별도 읽기 전용 에이전트 리뷰 후 포커스 이벤트 테스트 보강. 재리뷰에서 남은 중요 지적 없음. 팀원 리뷰와 구분한다.

UI 렌더링 이미지는 `build/reports/sprint7-b-preview.png`에 생성한다.

실제 엔진 연결, 두 PC 통합, 다른 팀원 리뷰는 별도 인수 항목이다. 자동 테스트나 Fake 시연을 해당 항목의 완료 근거로 사용하지 않는다.
사용자 요청에 따라 기존 커밋 형식으로 나눠 직접 develop에 푸시하며, 별도 PR/팀원 승인을 수행했다고 기록하지 않는다.
