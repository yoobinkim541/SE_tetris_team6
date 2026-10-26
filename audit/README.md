# 요구사항 인수 조건 진단

이 디렉터리는 현재 미충족 요구사항을 **실패로 재현**하는 별도 진단 도구입니다.
기존 CI의 `test check` 성공을 요구사항 1·2 전체 충족으로 해석하지 않습니다.
진단 테스트의 실패는 구현 공백이며, 예외를 삼키거나 기대 실패로 바꾸지 않았습니다.

```powershell
# 화면 없는 저장·픽셀 진단. 실제 JFrame 검사는 명시적으로 건너뛴다.
.\gradlew.bat -I audit/requirements-audit.init.gradle requirementsAudit --no-daemon

# Windows 데스크톱: 실제 MainFrame 메뉴·시작·기록 저장까지 모두 검증한다.
.\gradlew.bat -I audit/requirements-audit.init.gradle requirementsAudit -PauditGui=true --no-daemon
```

GUI 실행은 화면에 창을 표시하지 않으며, 테스트에서 만든 창과 타이머를 종료합니다.
저장 테스트는 JUnit 임시 디렉터리만 사용하므로 사용자 설정·기록은 변경하지 않습니다.

기준 코드 `b244b1f`: GUI 포함 69건 중 63건 실패, 6건 통과, skip 0.
실행 결과는 `build/reports/tests/requirementsAudit/index.html`과 `build/test-results/requirementsAudit/`에 있습니다.

| 클래스 | 검증 범위 | 결과 |
|---|---|---|
| RequirementsDataAuditTest | 난이도 저장·무효값, 기록 분류 저장·무효값, 분류별 Top 10, 기존 파일 | 23건 / 실패 21 |
| RequirementsVisualAuditTest | CLEARING 흰색, 현재·NEXT 아이템, 무게추, 상태 정보, 설정 키 조작 | 27건 / 실패 25 |
| OrdinaryGlyphAuditTest | 일반 블록 7종 × 기본/색맹 팔레트의 O 문자 | 14건 / 실패 12 |
| ProductionWiringAuditTest | 실제 5개 메뉴·아이템 진입·EASY/HARD 시작·기록 분류 저장 | 5건 / 실패 5 |

각 실패는 [상세 감사 보고서](../docs/requirements-audit-2026-10-26.md)의 항목과 연결합니다.
UI·저장 구현 PR이 올라오면 그 브랜치에서 이 명령을 실행해 실패가 해소됐는지 확인하고,
완성된 인수 테스트는 해당 PR의 일반 CI 회귀 테스트로 편입해야 합니다.
이 진단 작업은 현재 기본 `check`에 연결되어 있지 않습니다. 기본 CI만 초록인 것으로 이 실패들을 완료 처리하면 안 됩니다.
