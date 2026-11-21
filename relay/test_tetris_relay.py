import asyncio
import json
import urllib.request

import pytest
from websockets.asyncio.client import connect
from websockets.exceptions import ConnectionClosed

import tetris_relay


def run(coro):
    return asyncio.run(asyncio.wait_for(coro, 20))


async def start_server():
    ready = asyncio.get_running_loop().create_future()
    task = asyncio.ensure_future(tetris_relay.run("127.0.0.1", 0, ready))
    server = await ready
    port = next(iter(server.sockets)).getsockname()[1]
    return task, f"ws://127.0.0.1:{port}"


async def host(base, proto=7):
    ws = await connect(base + "/relay")
    await ws.send(json.dumps({"op": "host", "relay": 1, "proto": proto}))
    room = json.loads(await ws.recv())
    return ws, room


async def join(base, code, proto=7):
    ws = await connect(base + "/relay")
    await ws.send(json.dumps({"op": "join", "relay": 1, "proto": proto, "code": code}))
    return ws, json.loads(await ws.recv())


async def stop(task):
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass


def test_host_gets_four_digit_code_and_bytes_flow_both_ways_in_order():
    async def scenario():
        task, base = await start_server()
        h, room = await host(base)
        assert room["op"] == "room" and len(room["code"]) == 4 and room["code"].isdigit()
        j, reply = await join(base, room["code"])
        assert reply == {"op": "paired"}
        assert json.loads(await h.recv()) == {"op": "paired"}
        for i in range(50):
            await j.send(bytes([i]))
        assert [ (await h.recv())[0] for _ in range(50)] == list(range(50))
        await h.send(b"\x01\x02\x03")
        assert await j.recv() == b"\x01\x02\x03"
        await h.close()
        await j.close()
        await stop(task)
    run(scenario())


def test_unknown_code_is_rejected():
    async def scenario():
        task, base = await start_server()
        j, reply = await join(base, "0000")
        assert reply == {"op": "error", "reason": "NO_ROOM"}
        with pytest.raises(ConnectionClosed):
            await j.recv()
        await stop(task)
    run(scenario())


def test_room_accepts_only_one_joiner():
    async def scenario():
        task, base = await start_server()
        h, room = await host(base)
        j1, _ = await join(base, room["code"])
        j2, reply = await join(base, room["code"])
        assert reply == {"op": "error", "reason": "NO_ROOM"}
        await h.close(); await j1.close(); await j2.close()
        await stop(task)
    run(scenario())


def test_different_game_versions_are_rejected():
    async def scenario():
        task, base = await start_server()
        h, room = await host(base, proto=7)
        j, reply = await join(base, room["code"], proto=8)
        assert reply == {"op": "error", "reason": "PROTO_MISMATCH"}
        await h.close(); await j.close()
        await stop(task)
    run(scenario())


def test_room_disappears_when_host_leaves_before_join():
    async def scenario():
        task, base = await start_server()
        h, room = await host(base)
        await h.close()
        await asyncio.sleep(0.2)
        j, reply = await join(base, room["code"])
        assert reply["reason"] == "NO_ROOM"
        await j.close()
        await stop(task)
    run(scenario())


def test_one_side_closing_closes_the_other():
    async def scenario():
        task, base = await start_server()
        h, room = await host(base)
        j, _ = await join(base, room["code"])
        await h.recv()  # paired
        await j.close()
        with pytest.raises(ConnectionClosed):
            await asyncio.wait_for(h.recv(), 5)
        await stop(task)
    run(scenario())


def test_bad_first_message_is_rejected():
    async def scenario():
        task, base = await start_server()
        ws = await connect(base + "/relay")
        await ws.send("hello")
        assert json.loads(await ws.recv()) == {"op": "error", "reason": "BAD_REQUEST"}
        await stop(task)
    run(scenario())


def test_repeated_wrong_codes_are_rate_limited():
    async def scenario():
        task, base = await start_server()
        for _ in range(tetris_relay.FAILED_JOIN_LIMIT):
            j, reply = await join(base, "9999")
            assert reply["reason"] == "NO_ROOM"
        j, reply = await join(base, "9999")
        assert reply["reason"] == "TOO_MANY_ATTEMPTS"
        await stop(task)
    run(scenario())


def test_health_endpoint_answers_ok():
    async def scenario():
        task, base = await start_server()
        url = base.replace("ws://", "http://") + "/health"
        body = await asyncio.to_thread(lambda: urllib.request.urlopen(url, timeout=5).read())
        assert body == b"ok\n"
        await stop(task)
    run(scenario())
