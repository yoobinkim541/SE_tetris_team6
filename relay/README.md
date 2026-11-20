# 인터넷 대전 중계 서버

같은 LAN이 아닌 두 PC가 **방 코드 4자리**로 만나 대전하게 해 주는 작은 서버다. 게임 프로토콜(호스트 권위, HELLO, heartbeat)은 그대로이고, 서버는 짝이 지어진 두 연결 사이에서 바이트를 순서대로 넘기기만 한다.

```mermaid
flowchart LR
    HG["호스트 게임<br/>(아무 포트로 대기)"] <-->|루프백 TCP| HB["InternetRelay"]
    HB <-->|WebSocket| R["중계 서버<br/>tetris_relay.py"]
    R <-->|WebSocket| GB["InternetRelay"]
    GB <-->|루프백 TCP| GG["참가자 게임<br/>127.0.0.1:임시 포트"]
```

## 접속 주소

`wss://tetris.ybkim.pe.kr/relay` (Cloudflare 터널). 서버로 들어오는 포트는 열지 않고, 서버 IP도 드러나지 않는다. 443(HTTPS)만 열린 학교·회사 네트워크에서도 접속된다.

- 다른 서버로 시험할 때: `java -Dtetris.relay=ws://127.0.0.1:8765/relay -jar SE-Tetris.jar`
- 한국에서는 Cloudflare 무료 요금제 경로 특성상 왕복이 수백 ms까지 늘 수 있다(2026-11-20 실측 약 570ms). 호스트는 자기 PC에서 판정하므로 영향이 없고, 참가자 입력이 그만큼 늦게 보인다.

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
