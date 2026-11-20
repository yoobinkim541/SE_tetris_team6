# 인터넷 대전 중계 서버

같은 LAN이 아닌 두 PC가 **방 코드 4자리**로 만나 대전하게 해 주는 작은 서버다. 게임 프로토콜(호스트 권위, HELLO, heartbeat)은 그대로이고, 서버는 짝이 지어진 두 연결 사이에서 바이트를 순서대로 넘기기만 한다.

```mermaid
flowchart LR
    HG["호스트 게임<br/>(아무 포트로 대기)"] <-->|루프백 TCP| HB["InternetRelay"]
    HB <-->|WebSocket| R["중계 서버<br/>tetris_relay.py"]
    R <-->|WebSocket| GB["InternetRelay"]
    GB <-->|루프백 TCP| GG["참가자 게임<br/>127.0.0.1:임시 포트"]
```

## 접속 주소 (게임이 앞에서부터 시도)

| 순서 | 주소 | 경로 | 제한 시간 |
|---|---|---|---|
| 1 | `ws://tetris-direct.mywiki.pe.kr:47131/relay` | 서버 공인 IP로 바로 | 2초 |
| 2 | `wss://tetris.mywiki.pe.kr/relay` | Cloudflare 터널 (443만 열려 있는 네트워크에서도 됨) | 8초 |

- 다른 서버로 시험할 때: `java -Dtetris.relay=ws://127.0.0.1:8765/relay -jar SE-Tetris.jar`
- Cloudflare 경로는 한국 무료 요금제 특성상 왕복이 수백 ms까지 늘 수 있다. 1번 포트가 열려 있으면 그쪽이 훨씬 빠르다.

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
.venv/Scripts/python.exe tetris_relay.py --port 8765 --direct-port 8766
```

## 배포 (hermes 서버)

```bash
scp relay/{Dockerfile,.dockerignore,requirements.txt,tetris_relay.py} hermes:apps/tetris-relay/
ssh hermes 'cd ~/apps/tetris-relay && docker build -t tetris-relay:<n> . \
  && docker rm -f tetris-relay \
  && docker run -d --name tetris-relay --restart unless-stopped \
       -p 127.0.0.1:47130:8765 -p 47131:8766 tetris-relay:<n>'
```

- Cloudflare 터널: `~/.cloudflared/ybkim-personal.yml`의 `tetris.mywiki.pe.kr → http://127.0.0.1:47130` (서비스 `cloudflared-ybkim`)
- 바로 붙는 포트: DNS `tetris-direct.mywiki.pe.kr` (A, 프록시 끔) → 서버 공인 IP. 오라클 클라우드 보안 목록에 **TCP 47131 수신 허용**이 있어야 열린다
- 상태 확인: `curl https://tetris.mywiki.pe.kr/health` → `ok`
