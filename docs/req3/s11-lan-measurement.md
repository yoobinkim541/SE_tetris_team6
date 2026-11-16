# S11-C1 두 PC LAN 200ms·느린 네트워크 검증 절차

- 소유: C 네트워크. 코드: `src/main/java/tetris/feature/network/tools`(`NetTool`, `LagProxy`, `LatencyStats`, `LatencySample`, `LatencyCsv`), 테스트: `src/test/java/tetris/versus/network/{LatencyStatsTest,NetToolTest,LagProxyTest}.java`
- 근거: [#159 S11-C1](https://github.com/yoobinkim541/SE_tetris_team6/issues/159), 규칙 R3-LATENCY·R3-DEMO, `docs/req3/decisions.md` F-08
- 선행: S10-C1(#152)·S10-C2(#153) heartbeat·지연·끊김 판정

> **현재 상태: 실제 두 PC 미측정.** 이 문서는 도구·절차·기록 양식까지만 확정한다.
> 실제 두 PC 측정과 판정(합격/불합격)은 아직 없다.

## 0. 판정 규칙 (그대로 지킨다)

| 항목 | 규칙 |
|---|---|
| 측정 대상 | **입력 발생 → 호스트 반영 → 두 화면 표시**까지의 시간. 네트워크 RTT만으로 판정하지 않는다 |
| 합격 조건 | 3모드(CLASSIC·ITEM·TIMED) × 2방향(호스트→클라이언트, 클라이언트→호스트) × 최소 100입력 = **600입력**, 모두 **최대값 + 측정 오차 ≤ 200ms** |
| 불합격 기준 | 위 600입력 중 하나라도 `최대 + 오차 > 200ms` |
| 측정 오차 | 촬영 프레임 간격의 절반. 120fps면 **±8.3ms**, 60fps면 ±16.7ms(합격선 200ms 이하면 60fps로도 가능하나 오차가 커진다) |
| 금지 | 단일 PC loopback 결과로 합격 선언, ping/RTT 통과만으로 합격 선언, 지연 주입 결과를 정상 LAN 결과로 사용 |
| 미측정 표기 | 두 PC·영상 계측이 없으면 문서·PR에 **"미검증"**으로 적는다 |

`NetTool client`의 출력은 **네트워크 왕복(RTT) 기준선**일 뿐이며, 200ms 판정 근거가 아니다. 도구도 마지막 줄에 그 사실을 고정 출력한다.

## 1. 준비: 기록 표

측정 전에 아래 표를 채운다. 한쪽 PC만 적으면 재현할 수 없다.

| 항목 | PC-A (호스트) | PC-B (클라이언트) |
|---|---|---|
| 모델·CPU | | |
| RAM | | |
| OS / 빌드 | | |
| JDK (`java -version` 첫 줄) | | |
| 빌드 커밋 (`git rev-parse HEAD`) | | |
| 네트워크 (유선/Wi-Fi, 공유기 모델, 대역) | | |
| 같은 서브넷 여부 (`NetTool info` 출력) | | |
| 실행한 JAR/앱 경로 | | |
| 촬영 기기·해상도·fps | | |

측정 절차 전에 두 PC 모두 **같은 커밋**에서 빌드한 산출물을 쓴다. 커밋이 다르면 결과를 합치지 않는다.

## 2. 네트워크 기준선 (`NetTool`)

```text
# 1) 두 PC 모두: 주소·포트 확인, TCP 47123 bind 가능 여부
java -cp "SE-Tetris.jar" tetris.feature.network.tools.NetTool info

# 2) 호스트 PC: 접속 1회를 받고 PING에 PONG 응답 (Ctrl+C로 종료)
java -cp "SE-Tetris.jar" tetris.feature.network.tools.NetTool host 47123

# 3) 클라이언트 PC: RTT 200회 측정, 표본 CSV 저장
java -cp "SE-Tetris.jar" tetris.feature.network.tools.NetTool client <호스트IP> 47123 --count 200 --interval-ms 50 --csv net-baseline.csv
```

출력 예(loopback 기준 확인용, 실측 아님):

```text
네트워크 왕복(RTT) 표본 20개, 요청 20개
p50=1.50ms p95=2.22ms 최대=3.62ms, 200ms 초과 0건
표본 CSV: net-baseline.csv
네트워크 왕복만 측정했습니다. 입력→두 화면 표시 200ms 판정은 docs/req3/s11-lan-measurement.md 절차로 따로 합니다.
```

기록 항목: `p50` / `p95` / `최대` / 200ms 초과 건수. 이 값은 **기준선**이며 200ms 판정 근거가 아니다. 기준선 최대 RTT가 이미 200ms에 가까우면 영상 측정 전에 네트워크(유선 전환·공유기 확인)를 먼저 고친다.

종료 코드: `0` 정상, `1` 사용법 오류, `2` 연결 실패(닫힌 포트·잘못된 IP·시간 초과). 실패 시 표준 오류에 한국어 한 줄만 나오고 스택 트레이스는 나오지 않는다.

## 3. 입력 → 두 화면 표시 측정

1. **촬영 배치**: 한 카메라에 두 PC 화면과 **입력 장치(키보드/패드)가 같은 프레임에** 들어오게 둔다. 입력이 화면 밖이면 입력 시각을 특정할 수 없어 측정이 성립하지 않는다.
2. **프레임 레이트**: 120fps 이상으로 촬영한다(오차 ±8.3ms). 120fps 촬영이 불가능하면 60fps로 찍고 오차를 ±16.7ms로 적는다.
3. **입력 단위**: 한 번의 키 입력(회전 등)마다 (a) 입력이 보이는 프레임, (b) 호스트 화면 반영 프레임, (c) 상대 화면 반영 프레임의 타임코드를 읽는다.
4. **표본 수**: 모드 3 × 방향 2 × **100입력** = 600입력. 방향은 "호스트에서 입력 → 클라이언트 화면"과 "클라이언트에서 입력 → 호스트 화면"이다.
5. **모드**: `CLASSIC`, `ITEM`, `TIMED` 세 모드 모두 같은 방법으로 잰다. 모드별로 CSV를 따로 만든다.
6. **기록 단위**: 프레임 → ms 변환은 `프레임 차이 × (1000 / fps)`. 입력 프레임과 표시 프레임의 차이를 ms로 적고, 오차를 별도 열에 적는다.

### CSV 열 정의 (Task 1 `LatencyCsv` 형식)

헤더는 아래 한 줄로 고정한다. 다른 헤더의 파일은 읽지 않는다(`IOException`).

```text
mode,direction,sequence,inputNanos,localShownNanos,remoteShownNanos
```

| 열 | 의미 | 영상 측정 시 값 |
|---|---|---|
| `mode` | `CLASSIC` / `ITEM` / `TIMED` (`NetTool` RTT 측정은 `NET`) | 모드 이름 |
| `direction` | `host->client` / `client->host` (`NetTool`은 `client->host`) | 입력이 발생한 쪽 → 표시된 쪽 |
| `sequence` | 입력 순번 (1부터) | 1~100 |
| `inputNanos` | 입력 발생 시각 | 측정 시작 기준 오프셋을 ns로 환산한 값 |
| `localShownNanos` | 입력한 쪽 화면 표시 시각 | `inputNanos` 이상이어야 한다 |
| `remoteShownNanos` | 상대 화면 표시 시각. 미기록이면 `-1` | 대개 기록한다 |

- 영상에서 읽은 값은 절대 시각이 아니므로 **측정 시작 프레임을 0으로 둔 오프셋**을 ns로 적는다. 두 PC의 시계를 맞출 필요가 없다는 점이 이 형식의 목적이다.
- `localShownNanos < inputNanos` 또는 `remoteShownNanos < inputNanos`인 행은 생성 단계에서 거부된다(측정 실수 방지).
- `NetTool client --csv`가 쓰는 표본은 `mode=NET`, `direction=client->host`, `localShownNanos = remoteShownNanos = 수신 시각`(PONG 수신 시각)이다. **영상 측정 CSV와 섞지 않는다.**

## 4. 판정표 양식

모드별·방향별로 한 행씩 채운다. 12행(3모드 × 2방향 × ... 실제로는 3모드 × 2방향 = 6행, 각 100입력)이 기본이고, 모드별로 따로 낼 때는 6행을 유지한다.

| 모드 | 방향 | 표본 | p50 (ms) | p95 (ms) | 최대 (ms) | 200ms 초과 건수 | 측정 오차 (ms) | 최대+오차 | 판정 |
|---|---|---|---|---|---|---|---|---|---|
| CLASSIC | host->client | 100 | | | | | ±8.3 | | |
| CLASSIC | client->host | 100 | | | | | ±8.3 | | |
| ITEM | host->client | 100 | | | | | ±8.3 | | |
| ITEM | client->host | 100 | | | | | ±8.3 | | |
| TIMED | host->client | 100 | | | | | ±8.3 | | |
| TIMED | client->host | 100 | | | | | ±8.3 | | |

- `p50` / `p95` / `최대`는 nearest-rank(`ceil(p/100 × n)`번째) 기준이다. `LatencyStats.of(millis, 200)`가 같은 방식으로 계산한다.
- 200ms 초과 건수는 `> 200`인 표본 수다. 정확히 200.0ms는 초과가 아니다.
- **판정은 "최대 + 측정 오차 ≤ 200ms"로만 한다.** p95나 평균으로 합격을 선언하지 않는다.

## 5. 지연·끊김·회복 재현 (`lag`)

정상 LAN 결과와 **분리해서** 기록한다. 지연 주입 결과는 "정상 네트워크 성능"이 아니라 "안내·복구 동작 확인"용이다.

```text
# 중계기: 로컬 47124로 들어온 접속을 127.0.0.1:47123으로 넘기며 방향별 300ms 지연
java -cp "SE-Tetris.jar" tetris.feature.network.tools.NetTool lag 47124 <호스트IP> 47123 --delay-ms 300

# 중계기를 거쳐 RTT 측정 (클라이언트는 중계기 주소·포트로 접속)
java -cp "SE-Tetris.jar" tetris.feature.network.tools.NetTool client 127.0.0.1 47124 --count 20 --interval-ms 500
```

- `--delay-ms 300`이면 왕복 RTT는 약 600ms가 된다(받은 시각 + 지연 → 반대 방향도 같은 지연).
- `--stall-after-ms 5000`을 주면 중계 시작 5초 뒤부터 데이터 전달이 멈춘다. 소켓은 유지되므로 앱에서는 **지연 안내 → 끊김 판정** 순서를 재현할 수 있다.
- 재현 시나리오:
  1. **지연 안내**: heartbeat RTT가 200ms를 3회 연속 넘으면 `LAGGING` 안내가 뜨는지 확인한다(`docs/req3/s10-c-network.md` §2).
  2. **끊김**: `--stall-after-ms`로 1,500ms 이상 무수신을 만들면 `LAGGING`, 5,000ms를 넘기면 `LOST`로 바뀌는지 확인한다.
  3. **회복**: 중계기를 새로 띄워 지연을 0으로 두고 다시 접속해 게임이 정상 진행되는지 확인한다. `LOST`는 되돌릴 수 없으므로 재접속이 필요하다.
- 중계기 종료는 `Ctrl+C`. 중계 중 한쪽이 끊기면 반대쪽 연결도 닫혀 스레드가 남지 않는다(테스트 `LagProxyTest`가 검증).

## 6. 현재 상태

- 실제 두 PC 측정: **미측정**. 위 1~5절의 표는 양식만 제공한다.
- 최소 사양 장비(6-4·6-5) 검증: **미검증**. 해당 장비 확보 후 별도 기록한다.
- loopback에서 확인한 사실: `NetTool info/host/client/lag` 동작, CSV 형식, 종료 코드 0/1/2, 오류 한 줄 출력. 이는 도구 자체 검증이며 200ms 판정과 무관하다.

## 7. 단일 PC 2프로세스 실측 (2026-11-14, 보조 근거)

> **두 PC 판정이 아니다.** 0절 규칙대로 이 결과로 #159 합격을 선언하지 않는다. 두 PC·영상 측정 전까지 200ms 판정은 **미검증**이다.

### 7.1 방법

- 도구: `src/test/java/tetris/versus/ui/TwoProcessLatencyHarness.java` (CI에서 돌지 않는 측정 도구)
- 같은 PC에서 **JVM 프로세스 두 개**가 각자 실제 게임 창(`MainFrame`)을 띄운다. 클라이언트는 호스트의 **LAN 주소**(172.30.1.64)로 접속한다. Windows는 같은 PC 주소로 가는 TCP를 내부 경로로 처리하므로 랜선·공유기 지연은 들어가지 않는다.
- 입력: 입력한 쪽 프로세스의 경기 화면에 ←/→ 키 이벤트를 보낸다(OS 키보드 지연 제외). 측정값은 **키 이벤트 → 각 화면이 그 블록 이동을 그린 시각**(`PaintLog`)이다.
- 시계: 두 프로세스의 `System.nanoTime` 기준점 차이를 드라이버가 50번 왕복으로 재서 보정한다. 보정 불확도 ±0.27 ms(가장 짧은 왕복의 절반 합). 영상 측정 오차(±8.3 ms)보다 작다.
- 표본: 3모드 × 2방향 × 100입력 = **600입력**, 입력 간격 30 ms. 모드·방향마다 새 판(재대전)으로 시작한다. 2초 안에 두 화면 중 하나라도 그리지 못하면 유실로 센다.
- 환경: Windows 11, JDK 25.0.2 실행(컴파일 `--release 21`), `build/libs/SE-Tetris.jar`(releaseJar), 커밋 `cc484f3` 기준 + 이 측정 도구. 지연 주입 실행(7.3)은 7.4의 LagProxy 수정을 넣은 빌드로 다시 쟀다.
- 실행:
  ```text
  gradlew releaseJar testClasses
  java -cp "build/libs/SE-Tetris.jar;build/classes/java/test" tetris.versus.ui.TwoProcessLatencyHarness 100 build/verification/s11-two-process
  java -cp "build/libs/SE-Tetris.jar;build/classes/java/test" tetris.versus.ui.TwoProcessLatencyHarness 40 build/verification/s11-two-process-lag60 60
  ```
- 원자료: `docs/req3/measurements/s11-two-process/*.csv`(`LatencyCsv` 형식), `s11-two-process-summary.txt`

### 7.2 결과 (지연 주입 없음, 600입력, 유실 0)

| 모드 | 방향 | 화면 | 표본 | p50 (ms) | p95 (ms) | 최대 (ms) | 200ms 초과 |
|---|---|---|---|---|---|---|---|
| CLASSIC | host->client | 입력한 화면 | 100 | 14.70 | 25.10 | 37.53 | 0 |
| CLASSIC | host->client | 상대 화면 | 100 | 19.98 | 39.73 | 66.30 | 0 |
| CLASSIC | client->host | 입력한 화면 | 100 | 20.31 | 36.22 | 52.49 | 0 |
| CLASSIC | client->host | 상대 화면 | 100 | 18.55 | 32.57 | 34.74 | 0 |
| ITEM | host->client | 입력한 화면 | 100 | 14.23 | 18.43 | 29.70 | 0 |
| ITEM | host->client | 상대 화면 | 100 | 15.93 | 34.55 | 45.48 | 0 |
| ITEM | client->host | 입력한 화면 | 100 | 19.58 | 35.41 | 51.58 | 0 |
| ITEM | client->host | 상대 화면 | 100 | 18.54 | 21.04 | 34.74 | 0 |
| TIMED | host->client | 입력한 화면 | 100 | 14.22 | 19.24 | 29.68 | 0 |
| TIMED | host->client | 상대 화면 | 100 | 15.77 | 31.88 | 35.30 | 0 |
| TIMED | client->host | 입력한 화면 | 100 | 19.63 | 35.20 | 50.23 | 0 |
| TIMED | client->host | 상대 화면 | 100 | 18.50 | 33.43 | 37.34 | 0 |
| **전체** | 양방향 | 입력한 화면 | 600 | 18.58 | 35.07 | **52.49** | 0 |
| **전체** | 양방향 | 상대 화면 | 600 | 18.50 | 34.49 | **66.30** | 0 |

최대 + 보정 불확도 = 66.57 ms. 같은 PC 조건에서는 600입력 모두 200 ms 안이다. 실제 두 PC에서는 여기에 LAN 전송 지연(방향마다)과 OS 키 입력 지연이 더해진다.

### 7.3 지연 주입 (LagProxy 방향당 60 ms, 240입력, 유실 0) — 정상 LAN 결과 아님

| 모드 | 방향 | 입력한 화면 p50 / 최대 | 상대 화면 p50 / 최대 | 200ms 초과 |
|---|---|---|---|---|
| CLASSIC | host->client | 18.00 / 27.09 | 91.35 / 126.91 | 0 |
| CLASSIC | client->host | 158.15 / 188.20 | 81.57 / 103.76 | 0 |
| ITEM | host->client | 13.95 / 34.27 | 83.05 / 112.53 | 0 |
| ITEM | client->host | 158.61 / 188.58 | 81.44 / 105.59 | 0 |
| TIMED | host->client | 14.01 / 34.10 | 81.64 / 98.08 | 0 |
| TIMED | client->host | 158.92 / 174.27 | 80.62 / 95.47 | 0 |

기대와 맞는다. 호스트 입력은 호스트 화면에 바로, 클라이언트 화면에는 한 방향 지연(60 ms)만큼 늦게 그려진다. 클라이언트 입력은 호스트까지 60 ms, 상태가 돌아오는 데 60 ms가 걸려 입력한 화면이 약 120 ms + 처리 시간 뒤에 그려진다. 원자료: `docs/req3/measurements/s11-two-process-lag60/`.

### 7.4 측정 중 찾아 고친 결함: LagProxy 지연 누적

- 증상: 처음 지연 주입 실행에서 방향당 60 ms인데 상대 화면 지연이 약 140 ms(두 배)로 나왔다.
- 원인: `LagProxy`가 청크를 읽고 → 지연만큼 기다리고 → 쓴 뒤에야 다음 청크를 읽었다. 경기 상태처럼 16 ms마다 계속 오는 데이터는 기다리는 동안 쌓였다가 한 번 더 지연돼 실효 지연이 최대 두 배로 부풀고, 전달도 지연 주기마다 한 번으로 묶였다. 문서의 "받은 시각 + 지연" 약속과 달랐다.
- 수정: 읽기 스레드가 도착 즉시 시각을 찍어 대기열에 넣고, 전달 스레드가 그 시각 + 지연에 보낸다. 정지(`stallAfterMillis`)·양쪽 종료 동작은 그대로다.
- 회귀 테스트: `LagProxyTest.연속_송신에서도_지연이_누적되지_않는다` (10 ms마다 60개, 방향당 60 ms → 최대 왕복이 120~170 ms 안). 수정 전 187 ms로 실패, 수정 후 통과. 5절의 `NetTool lag` 지연 재현도 같은 중계기를 쓰므로 함께 바로잡혔다.

### 7.5 남은 일

- 실제 두 PC × 영상(120 fps) 600입력 측정과 판정표(4절) 작성 → 이것이 #159 합격 근거다.
- 두 PC에서 5절 지연 안내·끊김·회복 화면 재현. 끊김·회복 판정 자체는 `ConnectionHealthTest`, `LinkHealthLobbyTest`, `LagProxyTest`가 실제 소켓으로 자동 검증한다.
