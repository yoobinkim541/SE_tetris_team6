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
