# 요구사항 2 최종 로컬 검증 — 2026-10-26

검토 PR: https://github.com/yoobinkim541/SE_tetris_team6/pull/100

구현 커밋 `4c35e37`의 [원격 CI](https://github.com/yoobinkim541/SE_tetris_team6/actions/runs/40335775691)도 Linux test와 Windows 인수·패키징·EXE 실행을 모두 통과했다. `simsgood2`에게 팀 리뷰를 요청했으며 아직 승인·병합은 없다.

| 검증 | 결과 | 범위 |
|---|---|---|
| test/check | 861 실행, 854 통과, GUI 7 생략, 실패 0 | develop #98의 350개 강화 회귀와 저장·렌더링 인수 조건 포함 |
| guiAcceptance | 7 통과, 생략/실패 0 | 실제 JFrame 메뉴·모드·난이도·생성기·저장·포커스 콜백·화면 크기 |
| requirementsAudit | 71 통과, 생략/실패 0 | 최초 69개 중 63개 실패 → 기존 69개 전부 통과 + 실제 창 2개 강화 |
| LINE coverage | 89.78% | 전체 코드, 최소 70% 게이트 통과 |
| Java UI 배율 | 100% / 150% / 200% 통과 | 실제 창 크기 3종이 해당 데스크톱에 들어감 |
| 작은 화면 | 셀 12~20px 9개 크기 통과 | x2/SLOW가 보이며 조작 안내를 덮지 않음 |
| releaseJar/packageApp | 성공 | 최신 JAR, 런타임 포함 Windows ZIP |
| 패키지 EXE 실행 | 성공, 종료 코드 0 | 격리 APPDATA로 창 생성 후 정상 닫기 |

EXE 실행 증거: build/verification/package-smoke.json. 실제 창 제목은 SE Tetris이다. jpackage 런처와 실제 창을 소유하는 동일 EXE 자식 PID를 구분해 검증했다. 사용자의 기존 데이터는 변경하지 않았다.

최신 로컬 ZIP SHA-256:

`BC0B862F8575B0442AB5C08BA7ED409438D5CEAF9693A52B84691EA6B22D6F88`

## 검토 중 발견한 추가 문제

1. **작은 셀의 정보 잘림:** 기존 배치는 최소 셀에서 효과 잔여 수가 화면 밖으로 잘리거나 조작 안내와 겹쳤다. 최초 4개 크기 테스트가 모두 실패함을 확인한 뒤 셀 20px 이하에서 한 줄 정보 배치를 적용했고 12~20px 전체 검증이 통과했다.
2. **패키지 재생성 실패:** jpackage가 생성한 EXE의 DOS ReadOnly 속성으로 이전 출력 삭제가 실패했다. 정규화한 대상이 build 하위인지 검증한 뒤 생성 파일의 속성을 해제하고 재패키징했다.
3. **EXE 창 탐지 실패:** 최초 스모크 스크립트는 런처 PID에서만 창을 찾아 실패했다. 동일 EXE의 직접 자식 PID에 실제 창이 있음을 진단하고, 해당 창 정상 종료까지 확인했다.

독립 코드 검토에서 1번을 발견했으며 수정 후 재검토에 추가 구체적 결함이 없었다. 자동 코드 검토는 팀원 리뷰 승인을 대체하지 않는다.

## 미진행과 한계

- 사용자 2명 평가와 교수자 문의: 사용자가 미진행으로 확인했으며 [기록지](user-playtest.md)와 [문의 초안](professor-confirmation.md)을 준비했다.
- Windows IME 실제 입력·OS 설정 자체의 배율 변경·실제 Alt+Tab 플레이: 수동 확인이 필요하다. 등록된 포커스 콜백 검증을 실제 Alt+Tab 조작으로 표현하지 않는다.
- 원격 CI는 위 링크에서 성공을 확인했다. 팀원 승인·병합과 최종 main Release는 아직 미완료다.

## 재현

```powershell
.\gradlew.bat test check guiAcceptance releaseJar packageApp -I audit/requirements-audit.init.gradle requirementsAudit -PauditGui=true --no-daemon
.\gradlew.bat guiAcceptance '-PguiScale=1.5' --no-daemon
.\gradlew.bat guiAcceptance '-PguiScale=2.0' --no-daemon
.\scripts\verify-package.ps1
```
