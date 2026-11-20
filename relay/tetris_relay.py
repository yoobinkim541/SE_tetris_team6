"""SE Tetris 인터넷 대전 중계 서버.

같은 LAN이 아닌 두 PC가 방 코드로 만나게 한다. 게임 프로토콜은 건드리지 않고, 짝이 지어진 두 WebSocket 사이에서
바이너리 메시지를 순서대로 그대로 넘긴다(호스트 권위·HELLO·heartbeat는 게임이 그대로 처리한다).

제어 메시지(텍스트 JSON)
  클라이언트 → 서버 (연결 직후 한 번)
    {"op": "host", "relay": 1, "proto": <게임 프레임 버전>}
    {"op": "join", "relay": 1, "proto": <게임 프레임 버전>, "code": "4821"}
  서버 → 클라이언트
    {"op": "room", "code": "4821", "expiresIn": 600}   호스트에게 방 코드
    {"op": "paired"}                                    양쪽에게. 이후 바이너리만 오간다
    {"op": "error", "reason": "..."}                    직후 연결을 닫는다
      BAD_REQUEST / NO_ROOM / PROTO_MISMATCH / SERVER_FULL / TOO_MANY_ATTEMPTS / EXPIRED
"""

from __future__ import annotations

import argparse
import asyncio
import functools
import json
import logging
import secrets
import time
from collections import deque
from dataclasses import dataclass, field
from http import HTTPStatus

from websockets.asyncio.server import ServerConnection, serve
from websockets.exceptions import ConnectionClosed

RELAY_VERSION = 1
PATH = "/relay"
FIRST_MESSAGE_TIMEOUT = 10.0
UNPAIRED_TTL = 600.0          # 방 코드 유효 시간(초)
SESSION_MAX = 3 * 60 * 60.0   # 짝이 지어진 뒤 최대 유지 시간(초)
MAX_ROOMS = 500
MAX_MESSAGE_BYTES = 64 * 1024
BYTES_PER_SECOND = 1024 * 1024  # 방향별 전송 상한. 넘으면 버리지 않고 늦춘다
FAILED_JOIN_LIMIT = 10          # IP별 1분 동안 틀린 코드 허용 횟수
FAILED_JOIN_WINDOW = 60.0

log = logging.getLogger("tetris-relay")


@dataclass
class Room:
    code: str
    host: ServerConnection
    proto: int
    paired: asyncio.Event = field(default_factory=asyncio.Event)
    joiner: ServerConnection | None = None


class Relay:
    def __init__(self) -> None:
        self.rooms: dict[str, Room] = {}
        self.failed_joins: dict[str, deque[float]] = {}

    # ── 접속 처리 ─────────────────────────────

    async def handle(self, ws: ServerConnection, trust_proxy: bool = False) -> None:
        """trust_proxy: Cloudflare 터널 뒤 포트면 CF-Connecting-IP를 실제 IP로 믿는다. 직접 포트는 헤더를 무시한다"""
        try:
            first = await asyncio.wait_for(ws.recv(), FIRST_MESSAGE_TIMEOUT)
        except (asyncio.TimeoutError, ConnectionClosed):
            await ws.close(1008, "no request")
            return
        request = self._parse(first)
        if request is None:
            await self._fail(ws, "BAD_REQUEST")
            return
        if request["op"] == "host":
            await self._host(ws, request["proto"])
        else:
            await self._join(ws, request["proto"], request["code"], self._client_ip(ws, trust_proxy))

    @staticmethod
    def _parse(raw: object) -> dict | None:
        if not isinstance(raw, str) or len(raw) > 512:
            return None
        try:
            msg = json.loads(raw)
        except ValueError:
            return None
        if not isinstance(msg, dict) or msg.get("relay") != RELAY_VERSION:
            return None
        proto = msg.get("proto")
        if not isinstance(proto, int) or isinstance(proto, bool):
            return None
        if msg.get("op") == "host":
            return {"op": "host", "proto": proto}
        code = msg.get("code")
        if msg.get("op") == "join" and isinstance(code, str) and len(code) == 4 and code.isdigit():
            return {"op": "join", "proto": proto, "code": code}
        return None

    async def _host(self, ws: ServerConnection, proto: int) -> None:
        if len(self.rooms) >= MAX_ROOMS:
            await self._fail(ws, "SERVER_FULL")
            return
        room = Room(self._new_code(), ws, proto)
        self.rooms[room.code] = room
        try:
            await ws.send(json.dumps({"op": "room", "code": room.code, "expiresIn": int(UNPAIRED_TTL)}))
            paired = asyncio.ensure_future(room.paired.wait())
            closed = asyncio.ensure_future(ws.wait_closed())
            done, _ = await asyncio.wait({paired, closed}, timeout=UNPAIRED_TTL,
                                         return_when=asyncio.FIRST_COMPLETED)
            paired.cancel()
            closed.cancel()
            if not room.paired.is_set():
                if closed not in done:
                    await self._fail(ws, "EXPIRED")
                return
            assert room.joiner is not None
            await self._session(ws, room.joiner)
        finally:
            if self.rooms.get(room.code) is room:
                del self.rooms[room.code]

    async def _join(self, ws: ServerConnection, proto: int, code: str, ip: str) -> None:
        if self._too_many_failures(ip):
            await self._fail(ws, "TOO_MANY_ATTEMPTS")
            return
        room = self.rooms.get(code)
        if room is None or room.joiner is not None:
            self._record_failure(ip)
            await self._fail(ws, "NO_ROOM")
            return
        if room.proto != proto:
            await self._fail(ws, "PROTO_MISMATCH")
            return
        del self.rooms[code]
        room.joiner = ws
        try:
            # 호스트가 먼저 알아야 게임 연결을 열고 첫 바이트를 받을 준비를 한다
            await room.host.send(json.dumps({"op": "paired"}))
            await ws.send(json.dumps({"op": "paired"}))
        except ConnectionClosed:
            await ws.close()
            await room.host.close()
            room.paired.set()
            return
        room.paired.set()
        await self._session(ws, room.host)

    # ── 짝이 지어진 뒤 ───────────────────────────

    async def _session(self, me: ServerConnection, peer: ServerConnection) -> None:
        """me → peer 방향을 넘긴다. 반대 방향은 상대 쪽 handle()이 넘긴다. 한쪽이 끝나면 둘 다 닫는다"""
        try:
            await asyncio.wait_for(self._pump(me, peer), SESSION_MAX)
        except (asyncio.TimeoutError, ConnectionClosed):
            pass
        finally:
            await me.close()
            await peer.close()

    @staticmethod
    async def _pump(src: ServerConnection, dst: ServerConnection) -> None:
        budget = float(BYTES_PER_SECOND)
        last = time.monotonic()
        async for message in src:
            if not isinstance(message, bytes):
                return  # 짝이 지어진 뒤 텍스트는 규약 위반
            now = time.monotonic()
            budget = min(float(BYTES_PER_SECOND), budget + (now - last) * BYTES_PER_SECOND)
            last = now
            budget -= len(message)
            if budget < 0:
                await asyncio.sleep(-budget / BYTES_PER_SECOND)
            await dst.send(message)

    # ── 보조 ─────────────────────────────────

    def _new_code(self) -> str:
        while True:
            code = f"{secrets.randbelow(10000):04d}"
            if code not in self.rooms:
                return code

    @staticmethod
    def _client_ip(ws: ServerConnection, trust_proxy: bool) -> str:
        forwarded = ws.request.headers.get("CF-Connecting-IP") if trust_proxy and ws.request else None
        if forwarded:
            return forwarded
        remote = ws.remote_address
        return str(remote[0]) if remote else "?"

    def _too_many_failures(self, ip: str) -> bool:
        times = self.failed_joins.get(ip)
        if not times:
            return False
        cutoff = time.monotonic() - FAILED_JOIN_WINDOW
        while times and times[0] < cutoff:
            times.popleft()
        return len(times) >= FAILED_JOIN_LIMIT

    def _record_failure(self, ip: str) -> None:
        self.failed_joins.setdefault(ip, deque()).append(time.monotonic())

    @staticmethod
    async def _fail(ws: ServerConnection, reason: str) -> None:
        try:
            await ws.send(json.dumps({"op": "error", "reason": reason}))
        except ConnectionClosed:
            pass
        await ws.close(1008, reason)


def process_request(connection, request):
    if request.path == "/health":
        return connection.respond(HTTPStatus.OK, "ok\n")
    if request.path != PATH:
        return connection.respond(HTTPStatus.NOT_FOUND, "not found\n")
    return None


async def run(host: str, port: int, ready: asyncio.Future | None = None, direct_port: int | None = None) -> None:
    """port: Cloudflare 터널이 붙는 포트. direct_port: 인터넷에서 바로 붙는 포트(지연이 더 짧다). 방 목록은 함께 쓴다"""
    relay = Relay()
    options = dict(process_request=process_request, max_size=MAX_MESSAGE_BYTES,
                   ping_interval=20, ping_timeout=20, compression=None)
    async with serve(functools.partial(relay.handle, trust_proxy=True), host, port, **options) as server:
        if direct_port is None:
            if ready is not None:
                ready.set_result(server)
            await server.serve_forever()
            return
        async with serve(relay.handle, host, direct_port, **options) as direct:
            if ready is not None:
                ready.set_result((server, direct))
            await asyncio.gather(server.serve_forever(), direct.serve_forever())


def main() -> None:
    parser = argparse.ArgumentParser(description="SE Tetris 인터넷 대전 중계 서버")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765, help="Cloudflare 터널 뒤 포트")
    parser.add_argument("--direct-port", type=int, default=None, help="인터넷에서 바로 붙는 포트")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    log.info("listening on %s:%d%s (direct %s)", args.host, args.port, PATH, args.direct_port)
    asyncio.run(run(args.host, args.port, direct_port=args.direct_port))


if __name__ == "__main__":
    main()
