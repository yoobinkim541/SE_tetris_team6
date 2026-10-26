# 요구사항 2 릴리스 확인표

## 구현 및 자동 검증

- [x] 난이도 UI·영속화·가중치 생성기 실제 게임 연결
- [x] ITEM/NORMAL 진입·5종 아이템·표시·150ms 삭제 상태
- [x] 네 분류 Top 10·레거시 호환·좌우 탐색·강조 복원
- [x] develop의 #98 복합 키 해제·강화 수명주기 수정 통합
- [x] 기본 check에 저장·픽셀 인수 테스트 포함, LINE 70% 게이트
- [x] Windows 패키징에 실제 창 연결 검증 포함
- [x] v2 명세·검증 화면·이슈/PR 템플릿

## 최종 제출 조건

- [ ] 최신 커밋의 원격 CI 초록
- [ ] 팀원 1명 이상 리뷰 승인 후 develop 병합
- [ ] [실제 사용자 A/B 평가](user-playtest.md) 완료, P0/P1=0 확인
- [ ] 실제 한글 IME·운영체제 배율 환경에서 수동 플레이 결과 기록
- [ ] [교수자 Q1/Q2/Q4 답변](professor-confirmation.md) 반영
- [x] [Notion 명세](https://app.notion.com/p/3f4a13e7df008128a956ff96d84d694e) 게시 및 규칙·검증 결과 읽기 확인
- [ ] main 반영 PR 승인·병합
- [ ] 해당 main 커밋에 최종 버전 태그와 Release 발행, ZIP/JAR 첨부

자동 테스트와 로컬 ZIP 생성은 실제 평가·리뷰·최종 Release와 구분한다. 위 조건이 남아 있으면 최종 완료로 표시하지 않는다. PR #100을 먼저 검토하며 승인 없이 main에 직접 푸시하거나 리뷰를 대체하지 않는다.

## 배포 재현

```powershell
.\gradlew.bat test check guiAcceptance releaseJar packageApp --no-daemon
.\gradlew.bat guiAcceptance '-PguiScale=1.5' --no-daemon
.\gradlew.bat guiAcceptance '-PguiScale=2.0' --no-daemon
.\scripts\verify-package.ps1
Get-FileHash build/distributions/SE-Tetris-windows.zip -Algorithm SHA256
```

배율 검증은 별도 JVM의 Java UI 배율을 사용한다. Windows 설정 자체의 배율을 바꾼 수동 평가와 동일하지 않다.
