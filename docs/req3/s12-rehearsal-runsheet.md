# S12 리허설 진행표: 사람이 직접 해야 하는 남은 확인을 한 번에 끝내기

- 대상 이슈: [#159](https://github.com/yoobinkim541/SE_tetris_team6/issues/159) 두 PC 200ms, [#161](https://github.com/yoobinkim541/SE_tetris_team6/issues/161) 평가장 접속·복구 연습, [#172](https://github.com/yoobinkim541/SE_tetris_team6/issues/172) 타인 플레이·회고, [#181](https://github.com/yoobinkim541/SE_tetris_team6/issues/181) 대표 선수 연습, [#162](https://github.com/yoobinkim541/SE_tetris_team6/issues/162) 평가 빌드 고정
- 규칙: R3-DEMO, R3-LATENCY, R3-DISCONNECT, R3-REGRESSION
- 이 문서는 **순서와 기록 위치**만 정한다. 각 절차의 세부 규칙은 링크한 원문을 따른다. 결과 칸은 실제로 한 것만 적고, 하지 않은 칸은 비워 둔다.

## 0. 준비물 (리허설 전날까지)

| 준비물 | 확인 |
|---|---|
| 같은 공유기에 붙은 Windows PC 2대 (가능하면 유선) | [ ] |
| 두 PC에 같은 평가 번들 (`build/eval-bundle/<날짜>-<커밋>/SE-Tetris-windows.zip`) 압축 해제, `SHA256SUMS.txt` 대조 | [ ] |
| 120fps 이상 촬영 가능한 휴대폰과 거치대 (두 화면과 두 키보드가 한 프레임에 들어가게) | [ ] |
| 팀원 아닌 플레이어 1명 이상 (30분) | [ ] |
| 관리자 권한 PowerShell (방화벽 예외 `scripts/firewall-rule.ps1 -Apply`) | [ ] |
| 팀원 3명 모두 참석 가능한 시간 (회고 포함 약 2시간 30분) | [ ] |

## 1. 진행 순서

| 순서 | 소요 | 할 일 | 절차 원문 | 기록할 곳 | 관련 이슈 |
|---|---|---|---|---|---|
| 1 | 15분 | 두 PC 정보 기록, 방화벽 예외, `NetTool info`·`host`·`client` 기준선 | [evaluation-network-guide.md](evaluation-network-guide.md) 1장 1~5단계, [s11-lan-measurement.md](s11-lan-measurement.md) 1·2장 | `s11-lan-measurement.md` 1장 표, 기준선 CSV는 `measurements/` | #161, #159 |
| 2 | 45분 | 입력→두 화면 영상 측정: 3모드 × 2방향 × 100입력 | `s11-lan-measurement.md` 3장 | 모드별 CSV, 4장 판정표 | #159 |
| 3 | 20분 | 복구 연습: 강제 종료, 케이블 끊기, `lag` 300ms, `--stall-after-ms 5000`, 재접속 | `evaluation-network-guide.md` 4장, `s11-lan-measurement.md` 5장 | 아래 3장 표 (정상 LAN 판정표와 섞지 않음) | #161, #159 |
| 4 | 30분 | 팀원 아닌 플레이어가 시연 동선대로 플레이 (설명은 30초 대본만) | [s12-b-demo.md](s12-b-demo.md) 2장, [s12-a-rules-brief.md](s12-a-rules-brief.md) 2장 | `s12-b-demo.md` 3장 표 | #172 |
| 5 | 20분 | 대표 선수 선발과 연습: 규칙 설명, 질문 답하기, 낯선 조작키 적응 | `s12-a-rules-brief.md` 2~4장 | `s12-a-rules-brief.md` 4장 표 | #181 |
| 6 | 20분 | 팀 회고: KPT 초안을 읽고 확정, 4번에서 나온 문제를 이슈로 등록 | `s12-b-demo.md` 5장 | `s12-b-demo.md` 5장 (초안 문구를 확정 문구로 교체) | #172 |

- 2번에서 측정 중 하나라도 `최대 + 오차 > 200ms`면 멈추고 원인(유선/무선, 공유기, 백그라운드 프로그램)을 적은 뒤 다시 잰다. 다시 잰 결과와 처음 결과를 둘 다 남긴다.
- 4번 플레이어에게는 키를 미리 알려 주지 않는다. 막힌 곳을 보는 것이 목적이다.

## 2. 플레이어 관찰 메모 양식 (4번에서 사용)

플레이하는 동안 옆에서 받아 적고, 끝난 뒤 `s12-b-demo.md` 3장 표에 옮긴다.

| 시점 | 화면 | 플레이어가 한 말·행동 | 우리가 도와준 것 (있으면) |
|---|---|---|---|
| | 메인 메뉴 → 대전 | | |
| | 로컬 경기 (키 찾기·공격) | | |
| | 일시정지·나가기·결과 | | |
| | P2P 로비 (IP 입력·준비) | | |
| | P2P 경기·재대전 | | |
| | 끊김 안내 | | |

마지막 질문 두 개: "가장 헷갈린 것은?", "다시 한다면 무엇이 있었으면 좋겠나?"

## 3. 복구 연습 기록 (3번에서 사용)

| 시나리오 | 실제 관찰한 동작 | 안내 문구 | 걸린 시간 | 기대와 다르면 이슈 번호 |
|---|---|---|---|---|
| 상대 강제 종료 | | | | |
| 케이블/Wi-Fi 끊기 | | | | |
| 느린 네트워크 (`lag --delay-ms 300`) | | | | |
| 전송 정지 (`--stall-after-ms 5000`) | | | | |
| 회복 (재접속) | | | | |

## 4. 끝난 뒤 정리

1. 기록한 문서와 CSV를 한 PR로 `develop`에 올린다. 본문에 실제로 끝난 이슈만 `Closes`로 적고, 남은 것은 `Refs`로 적는다.
2. 이슈별 완료 조건:
   - #159: 판정표 6행이 모두 채워지고 실제 두 PC 영상 측정이다. 불합격이면 결과를 그대로 적고 이슈를 열어 둔다.
   - #161: 3장 표 5행에 실제 관찰이 있다.
   - #172: `s12-b-demo.md` 3장에 플레이어 기록이 1행 이상, 5장 KPT가 확정 문구다.
   - #181: `s12-a-rules-brief.md` 4장에 실제 선발·연습 기록이 있다.
3. 리허설에서 결함을 고쳤다면 평가 번들을 새 커밋으로 다시 만든다 ([release-checklist.md](../release-checklist.md) "평가 빌드 고정"). 평가일이 정해지면 D-7·D-1 날짜를 그 표에 채우고, D-1에 만든 번들을 USB와 팀 드라이브 두 곳에 복사한다 (#162).
4. 노션 Sprint Tasks에서 해당 작업의 Status와 Work Log를 갱신한다.
