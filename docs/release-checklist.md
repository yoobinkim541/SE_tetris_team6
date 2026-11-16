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

- [x] [구현 커밋 4c35e37의 Linux·Windows CI 성공](https://github.com/yoobinkim541/SE_tetris_team6/actions/runs/40335775691)
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

## 평가 빌드 고정 (S12-C2)

평가일이 확정되기 전에는 **날짜를 적지 않는다**. 아래 `D-7`, `D-1` 칸은 실제 평가일이 정해진 뒤 그 날짜로 채운다. 제출일이나 임의의 12/13을 평가일로 가정해 쓰지 않는다.

| 시점 | 할 일 | 날짜 | 담당 |
|---|---|---|---|
| D-7 | 기능 동결: `develop`에 새 기능 병합 중단, 버그 수정만 | (평가일 확정 후) | |
| D-1 | 평가 빌드 고정: 아래 명령으로 번들 생성, 이후 커밋 동결 | (평가일 확정 후) | |
| D-1 | 백업 사본 2곳(USB, 팀 드라이브)에 번들 zip 복사 | (평가일 확정 후) | |
| 평가 당일 | USB 사본으로 설치, `SHA256SUMS.txt` 대조 | - | |

### 번들 생성

```powershell
# 커밋되지 않은 변경이 있으면 거부된다. 의도한 것이면 -AllowDirty를 붙인다
git status --porcelain

# 빌드 + 검증 + 번들 (check, guiAcceptance, releaseJar, packageApp, coverage-summary 포함)
powershell -File scripts/build-eval-bundle.ps1

# 이미 빌드해 둔 산출물로 번들만 다시 만들 때
powershell -File scripts/build-eval-bundle.ps1 -SkipBuild
```

출력: `build/eval-bundle/<yyyyMMdd>-<short commit>/` 에 `SE-Tetris-windows.zip`, `SE-Tetris.jar`, `SHA256SUMS.txt`, `build-info.json`, `test-summary.json`, `README.txt` 6개 파일과 같은 이름의 `.zip`.

- `challenge/` 브랜치에서는 실행을 거부한다(평가 코드 유입 방지).
- 체크섬 재확인: `Get-FileHash -LiteralPath <번들 폴더>\SE-Tetris-windows.zip -Algorithm SHA256` 값이 `SHA256SUMS.txt`와 같은지 본다.
- `build-info.json`의 `commit`·`dirty`로 어느 커밋·상태에서 만든 번들인지 식별한다. `dirty=true`인 번들은 평가용으로 쓰지 않는다.

### 평가 전 확인

- [ ] 세 모드(CLASSIC·ITEM·TIMED) 로컬 인수표: (링크 자리 — 검증 후 채움)
- [ ] 세 모드 P2P 인수표: (링크 자리 — 검증 후 채움)
- [ ] `test-summary.json`의 `gatePassed=true`, `line.ratio ≥ 0.70`
- [ ] `build-info.json`의 `commit`이 고정한 커밋과 같고 `dirty=false`
- [ ] 번들에 `challenge` 관련 코드·브랜치 산출물이 없음(`git log --oneline` 및 zip 내용 확인)
- [ ] 두 PC 네트워크 절차: `docs/req3/evaluation-network-guide.md`
- [ ] 200ms 측정 절차: `docs/req3/s11-lan-measurement.md` (실제 두 PC 측정은 별도, **미검증**)

릴리스 게시와 배포 PR은 팀 리뷰 PR로만 한다. 이 절차로 만든 번들은 로컬 증거이며, 태그·Release 발행을 대신하지 않는다.

