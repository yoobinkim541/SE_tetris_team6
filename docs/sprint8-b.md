# Sprint 8 B: 메뉴·입력·공격 표시·결과

## 후속 실제 엔진 통합

S8-A1/A2와 기본 실행의 실제 엔진 연결은 [실제 로컬 통합 검증](sprint8-local.md)에서 이어서 완료했다. 팀원 리뷰·main 병합도 확인됐으며 현재 남은 인수는 [Sprint 8 마감 기록](sprint8-closeout.md)을 따른다. 아래는 PR #137 당시의 구현 및 검증 이력이다.

## 구현

메인 메뉴 마지막의 `대전` → 모드 선택 → 로컬 일반 대전 미리보기로 들어간다. 기존 싱글 메뉴 순서는 유지한다. 아이템·시간제한 대전은 준비 중으로 표시한다.

`MainFrame`은 싱글 세션을 종료하고 싱글 키 바인딩을 정지한 뒤 대전을 연다. `BattleScreenController`가 한 경기의 공통 `BattleClient` 구독과 루트 입력 라우터를 소유한다. 실제 S8-A2 엔진이 들어오면 `Function<MatchConfig, BattleClient>` 생성자 인수로 연결한다. 기본 `PreviewBattleClient`는 Fake 화면과 Pause만 제공하며 블록 이동·공격·승패를 시뮬레이션하지 않는다.

P1/P2 이동키는 독립적으로 전달한다. 공통 Pause/Quit는 한 번만 처리하고, 동기 스냅샷 갱신 중에도 공통 키의 눌림 잠금을 유지한다. Pause·확인창·포커스 이탈·종료에서 게임 키를 해제한다. Quit 기본 선택은 아니요이며, 진행 중 확인창은 양쪽 Pause 후 취소하면 복귀한다. 결과 전환 전에 게임 입력을 제거하고 화면을 떠나면 구독·클라이언트를 닫는다. 이전 경기의 지연된 결과는 새 경기로 넘어오지 않는다.

결과는 승자/무승부·종료 사유·두 점수를 표시하고 Enter로 모드 선택, Esc로 메인 메뉴로 돌아간다. 싱글 GameOver·이름 입력·스코어보드 저장을 호출하지 않는다. 공격 대기열과 결과 표시 상세는 [B2 계약](sprint8-b2.md)을 따른다.

## 검증과 재현

Java 21 / Gradle 8.10.2:

2026-11-08 로컬 최종 실행: 전체 1,069개 중 1,055 통과 / 0 실패 / 14 생략, LINE 3,425/3,821 (89.64%). `check guiAcceptance releaseJar` 성공. GUI는 13개 중 12 통과 / 0 실패 / OS 키 주입 1개 명시적 생략. [실제 창 printAll 결과](req3/screenshots/sprint8-b-window.png).

```powershell
.\gradlew.bat check guiAcceptance releaseJar --no-daemon
```

- 컨트롤러: P1/P2 독립 입력, Pause 전 키 해제, 공통 키 반복 억제, Quit 확인/취소, 결과 후 입력 제거, 멱등 close.
- B2: 양쪽 대기열 0~10줄의 전체 행/구멍 순서, 공격칸 9와 회색 W 무늬, 두 팔레트, 작은 셀, 결과·콜백·변경된 키 가이드.
- 실제 창: 메인 메뉴→모드 선택→대전→무승부 결과→모드 선택, 점수 메모리와 파일 미생성, 실제 Swing KeyEvent 겹친 입력과 press/release 전달. 마지막 창 렌더링은 `build/verification/sprint8-b-window.png`에 저장한다. 이는 화면 캡처가 아닌 창의 printAll 결과다.
- 구현 전 컨트롤러 테스트는 클래스 부재로 컴파일 RED를 확인했다. GUI 테스트 추가 중 잘못된 Scoreboard 생성자를 바로잡았다.

## 남은 인수 경계

실제 엔진 S8-A2가 없으므로 실제 로컬 한 판·공격 삽입·승패 통합 완료를 주장하지 않는다. 실제 엔진 연결과 인수는 별도로 남는다.

이 환경에서 Robot OS 키 주입 테스트는 창과 BattlePanel의 포커스가 확인돼도 KeyEventDispatcher에 이벤트가 하나도 도달하지 않았다. 자동 Swing KeyEvent 검증과 물리 키보드 동시 입력/롤오버는 다르다. 대화형 Windows 데스크톱에서는 다음으로 OS 입력을 재검증한다:

```powershell
.\gradlew.bat guiAcceptance -PnativeInput=true --tests '*BattleMainFrameTest.nativeOverlapping*' --no-daemon
```

AI 코드 리뷰는 사용자 요청으로 수행했다. 다른 팀원의 실제 승인이나 실키보드 인수를 대신했다고 기록하지 않는다. 따라서 B 구현·자동 검증과 명세의 전체 Done(엔진 통합·실키보드·팀원 리뷰)을 구분한다.
