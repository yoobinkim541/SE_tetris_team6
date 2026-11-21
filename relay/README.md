# 인터넷 대전 중계 서버 (예비)

같은 LAN이 아닌 두 PC가 방 코드로 만나 대전하게 해 주는 중계 서버는 두 곳이다. 게임 프로토콜(호스트 권위, HELLO, heartbeat)은 그대로이고, 서버는 짝이 지어진 두 연결 사이에서 바이트를 순서대로 넘기기만 한다. 이 폴더는 **예비 서버**(`tetris_relay.py`)다.

```mermaid
flowchart LR
    HG["호스트 게임<br/>(아무 포트로 대기)"] <-->|루프백 TCP| HB["InternetRelay"]
    HB <-->|"메인: TCP / 예비: WebSocket"| R["중계 서버"]
    R <--> GB["InternetRelay"]
    GB <-->|루프백 TCP| GG["참가자 게임<br/>127.0.0.1:임시 포트"]
```

## 서버와 고르는 규칙

| | 메인 | 예비 |
|---|---|---|
| 주소 | `158.179.174.70:47123` (오라클 클라우드 VM, TCP) | `wss://tetris.ybkim.pe.kr/relay` (hermes, Cloudflare 터널) |
| 코드 | 6글자 `K7Q-M2X` (헷갈리는 0 O 1 I L 제외) | 숫자 4자리 `4821` |
| 서버 코드 | `tetris.relay.RelayServer` (같은 JAR, `scripts/deploy-relay.ps1`로 배포) | `relay/tetris_relay.py` (Docker) |
| 2026-11-20 실측 | 게임 heartbeat 왕복 평균 46ms (최소 21ms) | 왕복 약 570ms~1.5초 (한국에서 Cloudflare 무료 경로) |

- 호스트는 **메인부터** 방을 만든다. 메인에 3초 안에 닿지 않거나 방 코드를 받기 전에 실패하면 예비 서버에서 방을 만든다.
- 참가자는 **코드 모양으로 서버를 고른다**(6글자 = 메인, 숫자 4자리 = 예비). 그래서 두 사람이 늘 같은 서버에서 만난다. 대소문자·하이픈·공백은 아무렇게나 입력해도 된다.
- 다른 서버로 시험할 때: `-Dtetris.relay=127.0.0.1:47123`(메인), `-Dtetris.relay.fallback=ws://127.0.0.1:8765/relay`(예비)
- 예비 서버는 들어오는 포트를 열지 않고 서버 IP도 드러나지 않는다. 메인 서버는 공인 IP로 바로 받으므로 더 빠르다.

## 규약 (텍스트 JSON → 이후 바이너리)

| 방향 | 메시지 |
|---|---|
| 호스트 → 서버 | `{"op":"host","relay":1,"proto":<WireFormat.VERSION>}` |
| 서버 → 호스트 | `{"op":"room","code":"4821","expiresIn":600}` |
| 참가자 → 서버 | `{"op":"join","relay":1,"proto":<WireFormat.VERSION>,"code":"4821"}` |
| 서버 → 양쪽 | `{"op":"paired"}` (호스트에게 먼저), 이후 바이너리만 |
| 서버 → 실패 | `{"op":"error","reason":"NO_ROOM / PROTO_MISMATCH / SERVER_FULL / TOO_MANY_ATTEMPTS / EXPIRED / BAD_REQUEST"}` 후 닫음 |

제한: 방 500개, 메시지 64 KiB, 방향별 1 MiB/s(넘으면 늦춤), 방 코드 10분, 연결 3시간, IP별 틀린 코드 1분 10회.

이름이 있는 방(v2)은 목록에 올라간다. 이름·공개 여부·모드를 빼면 예전과 같이 목록에 없는 방이다.

| 방향 | 메시지 |
|---|---|
| 호스트 → 서버 | `{"op":"host","relay":1,"proto":N,"name":"대전","visibility":"public","mode":"classic"}` |
| 서버 → 호스트 | `{"op":"room","code":"4821","id":"K7QM2X","expiresIn":600}` |
| 목록 | `{"op":"list","relay":1}` → `{"op":"rooms","rooms":[{"id","name","mode","private","players"}]}` 후 닫음. 비밀 코드·IP는 없다 |
| 공개 방 참가 | `{"op":"join","relay":1,"proto":N,"id":"K7QM2X"}` |
| 비공개 방 | id로 참가하면 `{"op":"error","reason":"PRIVATE"}`. 코드로 참가하면 기존 join |

모드 토큰: `classic` `item` `timed` `raid-coop` `raid-race`. 이름은 앞뒤 공백·제어 문자를 뺀 1~20자. 목록은 최신 50개, IP당 목록 초당 2회, IP당 대기 방 3개. 짝이 지어지거나 10분이 지나면 목록에서 사라진다.

메인 서버(줄 프로토콜)도 같다. `HOST v1`은 그대로다. `HOST v2 public classic <name-base64url>` → `ROOM <코드> <id>`. `LIST v2` → `R <id> <mode> <0|1> <인원> <name-base64url>` … `END` 후 닫음. `JOINID v2 <id>`는 공개 방만. 비공개는 `ERR PRIVATE`. 옛 서버는 v2에 `ERR`을 주며, 클라이언트는 그때 예비 서버로 넘어간다.

## 메인 서버 갱신

오라클 VM에서 중계를 돌리는 사람용이다. 게임 JAR와 따로, 중계 클래스만 담은 jar를 만든다.

```bat
gradlew.bat relayServerJar
```

결과: `build/libs/tetris-relay-server.jar` (Main-Class `tetris.relay.RelayServer`).

VM의 서비스는 `/opt/tetris-relay/relay.jar`를 classpath로 `tetris.relay.RelayServer 47123`을 실행한다 (`scripts/tetris-relay.service`). 갈아끼운 뒤 재시작한다.

```bash
scp build/libs/tetris-relay-server.jar <사용자>@<VM>:/tmp/relay.jar
ssh <사용자>@<VM> 'sudo install -o tetris-relay -g tetris-relay -m 644 /tmp/relay.jar /opt/tetris-relay/relay.jar && sudo systemctl restart tetris-relay && sudo systemctl --no-pager status tetris-relay'
```

확인: `printf "HOST v1\n" | nc <VM> 47123` → `ROOM`으로 시작하는 한 줄. v2를 올린 뒤에는 `LIST v2`가 `END`로 끝나야 한다.

예비 서버 이미지(배포는 하지 않는다. 명령만):

```bash
docker build -t tetris-relay:4 relay
```

## 실행·테스트

```bash
uv venv relay/.venv
uv pip install --python relay/.venv/Scripts/python.exe -r relay/requirements.txt pytest
cd relay && .venv/Scripts/python.exe -m pytest -q
.venv/Scripts/python.exe tetris_relay.py --port 8765
```

## 배포 (hermes 서버)

```bash
scp relay/{Dockerfile,.dockerignore,requirements.txt,tetris_relay.py} hermes:apps/tetris-relay/
ssh hermes 'cd ~/apps/tetris-relay && docker build -t tetris-relay:<n> . \
  && docker rm -f tetris-relay \
  && docker run -d --name tetris-relay --restart unless-stopped \
       -p 127.0.0.1:47130:8765 tetris-relay:<n>'
```

- Cloudflare 터널: `~/.cloudflared/ybkim-personal.yml`의 `tetris.ybkim.pe.kr → http://127.0.0.1:47130` (서비스 `cloudflared-ybkim`)
- DNS: Cloudflare 대시보드 ybkim.pe.kr 영역에 `tetris` CNAME → `e2bde56f-9404-4a59-a4bd-0386d41ee639.cfargotunnel.com` (프록시 켬)
- 상태 확인: `curl https://tetris.ybkim.pe.kr/health` → `ok`
