# S12-C1 평가장 네트워크 준비 안내

- 소유: C 네트워크. 관련: `scripts/firewall-rule.ps1`, `src/main/java/tetris/feature/network/tools/NetTool.java`
- 근거: [#161 S12-C1](https://github.com/yoobinkim541/SE_tetris_team6/issues/161), 규칙 R3-DEMO·R3-DISCONNECT·R3-LATENCY
- 함께 보기: `docs/req3/s11-lan-measurement.md`(200ms 측정 절차), `docs/req3/s10-c-network.md`(heartbeat 지연·끊김 판정), `docs/req3/network-protocol.md`

> **현재 상태: 실제 장비 검증 미실시.** 이 문서는 평가 당일 절차와 장애 대응표까지만 확정한다.
> 최소 사양 장비(6-4·6-5)와 두 PC 실제 측정은 **미검증**이다.

## 0. 방화벽 원칙

| 하지 않는 것 | 하는 것 |
|---|---|
| 방화벽 프로필 전체 해제(`Set-NetFirewallProfile -Enabled False`, `netsh advfirewall set allprofiles state off`) | 인바운드 **TCP 47123** 한 포트만, 프로필 `Private`, 원격 주소 `LocalSubnet` 예외 |
| 평가 직전 임의로 규칙 대량 생성 | 스크립트가 출력한 명령을 확인한 뒤 **호스트 PC에만** `-Apply` |
| 규칙을 남긴 채 평가 종료 | 평가 후 `-Action Remove`로 같은 규칙 제거 |

스크립트는 **기본이 dry run**이다. `-Apply`가 없으면 실행할 명령만 출력하고 아무것도 바꾸지 않는다. 관리자 권한이 없으면 `-Apply`도 거부한다. 전체 방화벽을 끄는 명령은 스크립트에 없다.

```powershell
# 1) 무엇을 실행할지 먼저 확인 (변경 없음)
powershell -File scripts/firewall-rule.ps1
powershell -File scripts/firewall-rule.ps1 -Program "C:\Eval\SE Tetris\SE Tetris.exe"   # 앱 단위로 좁힐 때

# 2) 관리자 PowerShell에서 실제 적용
powershell -File scripts/firewall-rule.ps1 -Apply

# 3) 적용 확인 / 평가 후 제거
powershell -File scripts/firewall-rule.ps1 -Action Show
powershell -File scripts/firewall-rule.ps1 -Action Remove -Apply
```

규칙 이름은 `SE Tetris P2P (TCP 47123)` 하나로 고정된다. 같은 이름이 이미 있으면 새로 만들지 않는다.

## 1. 평가장 체크리스트 (순서대로)

| # | 단계 | 명령 / 확인 | 통과 기준 |
|---|---|---|---|
| 1 | 두 PC가 **같은 서브넷**인지 확인 | 두 PC에서 `ipconfig` → IPv4 주소와 서브넷 마스크 비교, `NetTool info`의 주소 후보 확인 | 앞 3옥텟이 같고 서로 ping이 통한다 |
| 2 | 패키지 압축 해제 | `SE-Tetris-windows.zip`을 각 PC의 짧은 경로(예: `C:\Eval\`)에 푼다 | `C:\Eval\SE Tetris\SE Tetris.exe`가 있다 |
| 3 | 도구 실행 경로 확인 | 아래 §2 참고 | `NetTool info`가 주소·포트를 출력하고 종료 코드 0 |
| 4 | 호스트 PC 방화벽 예외 | §0의 스크립트 절차 | `-Action Show`에 규칙 1개, `Enabled: True`, `LocalSubnet` |
| 5 | 연결 확인 | 호스트: `NetTool host 47123` / 클라이언트: `NetTool client <호스트IP> 47123 --count 20` | 표본 20개, 200ms 초과 0건, 클라이언트 종료 코드 0 |
| 6 | 게임 P2P 접속 | 두 PC에서 `SE Tetris.exe` 실행 → 호스트가 대전 대기 → 클라이언트가 `NetTool info`에서 확인한 호스트 IP 입력 | 두 화면에 대전 화면이 뜨고 입력이 상대에 반영됨 |
| 7 | 느린 네트워크·끊김 리허설 | §4 복구 연습 | 지연 안내 → 끊김 → 재접속 순서가 관찰됨 |

3단계에서 도구를 못 찾으면 5·6단계로 넘어가지 않는다. "게임은 되는데 도구가 안 된다"는 상태는 접속 문제를 진단할 수단이 없다는 뜻이다.

## 2. 패키지 안에서 `NetTool` 실행하기

`jpackage` 이미지에는 **`java.exe`가 들어 있지 않다**(`runtime\bin`에 DLL만 있다). 따라서 패키지 폴더의 JAR들을 **설치된 JDK 21의 `java`로** 실행한다.

패키지 레이아웃(실제 확인):

```text
SE Tetris\
  SE Tetris.exe
  app\
    tetris.jar                 ← NetTool 포함
    jackson-annotations-2.22.jar
    jackson-core-2.22.3.jar
    jackson-databind-2.22.3.jar
  runtime\                     ← 앱 실행용 JRE (java.exe 없음)
```

```powershell
# 패키지를 푼 폴더로 이동
cd "C:\Eval\SE Tetris"

# app\* 와일드카드로 tetris.jar + Jackson을 모두 클래스패스에 넣는다
java -cp "app\*" tetris.feature.network.tools.NetTool info
java -cp "app\*" tetris.feature.network.tools.NetTool host 47123
java -cp "app\*" tetris.feature.network.tools.NetTool client 192.168.0.21 47123 --count 200 --interval-ms 50 --csv baseline.csv

# 지연 주입이 필요하면 (클라이언트 PC에서 실행)
java -cp "app\*" tetris.feature.network.tools.NetTool lag 47124 192.168.0.21 47123 --delay-ms 300
```

개발 PC에서 저장소를 그대로 쓸 때는 실행 JAR 하나로 충분하다(Jackson이 안에 들어 있다).

```powershell
.\gradlew.bat releaseJar --no-daemon
java -cp "build\libs\SE-Tetris.jar" tetris.feature.network.tools.NetTool info
```

JDK가 없는 평가 PC라면 JAR 도구 대신 §5의 앱 자체 동작으로 접속을 확인한다. 이 경우 `NetTool` 진단은 **사용할 수 없다**고 기록한다.

## 3. 장애별 증상·원인·조치

| 증상 | 확인 | 원인 | 조치 |
|---|---|---|---|
| `연결 실패: REFUSED: Connection refused` (종료 2) | 호스트에서 `NetTool host`가 떠 있는지, 포트 번호가 같은지 | 호스트가 대기 중이 아님, 포트 불일치, 다른 프로세스가 포트 점유 | 호스트에서 `host <포트>` 재실행. `NetTool info`의 `bind 불가`면 포트 변경 |
| `연결 실패: TIMEOUT` 또는 5초 초과 | 두 PC `ipconfig`, 같은 서브넷 여부, `-Action Show` | 다른 서브넷/공유기 격리, 방화벽 예외 없음, 무선 AP 클라이언트 격리 | 같은 서브넷으로 이동, §0 예외 적용, AP의 "클라이언트 격리" 해제 |
| `연결 실패: INVALID_ADDRESS` (종료 2) | 입력한 IP 문자열 | 오타, `0.0.0.0`·브로드캐스트·IPv6 입력, 포트 범위 밖 | `NetTool info`가 출력한 IPv4를 그대로 사용 |
| `PORT_IN_USE` / `bind 불가` | `NetTool info` | 이전 실행이 남아 있거나 다른 앱이 47123 사용 | 이전 프로세스 종료, 또는 다른 포트(예: 47124) 사용 |
| 게임에서 "지연" 안내가 계속 뜸 | 상대 PC에서 `lag` 중계기가 켜져 있는지 | 지연 주입이 남아 있음, 또는 실제 무선 구간 지연 | 중계기 종료, 유선 전환. 정상 LAN 측정과 지연 주입 결과를 섞지 않는다 |
| 끊김 뒤 첫 화면이 갱신되지 않음 | 상대 앱이 살아 있는지, `LOST` 상태인지 | `LOST`는 되돌릴 수 없음(5초 무수신) | 앱을 다시 실행하고 재접속. 회복은 재접속으로만 된다 |
| 두 PC 중 한쪽만 창이 뜸 | 실행 로그, JDK/JRE 오류 | 압축 해제 경로의 한글·공백, 백신 차단 | 짧은 영문 경로(`C:\Eval\`)로 다시 풀기 |
| 호스트 IP가 계속 바뀜 | 공유기 DHCP 임대 | 재부팅·재접속마다 주소 변경 | 평가 중에는 공유기에서 IP 고정 또는 유선 유지 |

종료 코드: `0` 성공, `1` 사용법·인자 오류, `2` 연결 실패. 오류는 표준 오류에 한국어 한 줄로만 나오고 스택 트레이스는 나오지 않는다.

## 4. 복구 연습 시나리오

평가 전에 **최소 한 번** 아래를 실제로 해 본다. 결과는 "연습함/관찰 내용"으로 기록하고, 정상 LAN 200ms 측정 결과와 분리한다.

| 시나리오 | 만드는 방법 | 기대 동작 | 기록 |
|---|---|---|---|
| 상대 강제 종료 | 클라이언트 PC에서 앱을 작업 관리자로 종료 | 호스트에 끊김 안내, 로비 복귀. 예외 창 없음 | 안내 문구·걸린 시간 |
| 케이블/Wi-Fi 끊기 | 유선 뽑기 또는 무선 어댑터 사용 안 함 | 1.5초 뒤 지연 안내, 5초 뒤 끊김 판정 → 앱이 멈추지 않음 | 전환 시각 |
| 느린 네트워크 | 클라이언트 PC에서 `NetTool lag 47124 <호스트IP> 47123 --delay-ms 300` 실행 후 게임을 중계기에 접속 | 입력 반영이 늦어지고 지연 안내가 뜸 | 왕복 지연, 안내 문구 |
| 전송 정지 | `NetTool lag ... --stall-after-ms 5000` | 5초 뒤부터 화면 갱신이 멈추고 끊김 판정으로 이어짐 | 판정까지 걸린 시간 |
| 회복 | 중계기 종료 후 재접속 | 새 연결로 정상 진행 | 재접속 성공 여부 |

`lag`는 **정상 성능 측정용이 아니다.** 지연 주입 수치를 200ms 판정 표에 넣지 않는다.

## 5. 도구 없이 확인하는 최소 점검

JDK가 없는 평가 PC라면 앱만으로 아래를 확인한다.

1. 두 PC를 같은 서브넷에 두고 `ipconfig`로 주소를 적는다.
2. 호스트 PC에서 앱 실행 → 대전 대기 화면의 안내 주소가 `ipconfig`의 IPv4와 같은지 확인.
3. 클라이언트 PC에서 그 주소로 접속. 5초 안에 대전 화면이 뜨는지 본다.
4. 실패하면 §0의 방화벽 예외를 적용하고 다시 시도한다.

## 6. 미검증 항목

| 항목 | 상태 | 필요한 것 |
|---|---|---|
| 실제 두 PC에서 패키지 실행·P2P 접속 | **미검증** | 평가장과 같은 서브넷의 두 PC |
| 방화벽 예외 적용 후 접속 | **미검증** | 관리자 권한으로 `-Apply` 실행(이 저장소의 자동 검증은 dry run까지만) |
| 최소 사양 장비(6-4·6-5) | **미검증** | 해당 사양 장비 확보 |
| 무선 구간 지연·끊김 | **미검증** | 실제 무선 환경 |
| 입력→두 화면 표시 200ms | **미검증** | `docs/req3/s11-lan-measurement.md`의 영상 측정 |

자동 검증이 확인한 것: 스크립트가 dry run에서 명령만 출력하고 규칙을 만들지 않음(`Get-NetFirewallRule -DisplayName 'SE Tetris*'` 결과 0건), `-Port 0`과 비관리자 `-Apply`가 종료 코드 1, 패키지 `app\tetris.jar`에 `NetTool` 클래스 포함, `java -cp "app\*" ... NetTool info` 실행 성공.
