"""Core parity and full HTTP gameplay. Uses only Python/JDK standard libraries.

Without an origin, starts and stops an owned local Java process. With an origin,
checks the deployed API without changing any shared/user state.
"""
from collections import deque
from pathlib import Path
import concurrent.futures
import contextlib
import json
import re
import socket
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
BASE_COMMIT = "8e46b6950f3b7c7218e405187ff257d21513c2fa"


def parity():
    target = ROOT / ".build" / "parity"
    target.mkdir(parents=True, exist_ok=True)
    originals = []
    for name in ("Player", "Level"):
        source = subprocess.check_output(["git", "show", f"{BASE_COMMIT}:src/{name}.java"], cwd=ROOT).decode("utf-8")
        path = target / f"Reference{name}.java"
        path.write_text(re.sub(rf"\b{name}\b", "Reference" + name, source), encoding="utf-8")
        originals.append(path)
    subprocess.run(["javac", "--release", "21", "-encoding", "UTF-8", "-cp", str(ROOT / ".build" / "game.jar"), "-d", str(target), *map(str, originals), str(ROOT / "web" / "tests" / "ParityTest.java")], check=True)
    import os
    subprocess.run(["java", "-cp", os.pathsep.join((str(ROOT / ".build" / "game.jar"), str(target))), "ParityTest"], check=True)


def http(origin, command="STATE", history="", *, expected=200, raw=None, content_type="application/x-www-form-urlencoded", method="POST", path="/api/game"):
    body = raw if raw is not None else urlencode({"history": history, "command": command}).encode()
    request = Request(origin + path, data=body if method == "POST" else None, method=method, headers={"Content-Type": content_type})
    try:
        with urlopen(request, timeout=30) as response:
            status, data = response.status, response.read()
    except HTTPError as error:
        status, data = error.code, error.read()
    assert status == expected, (status, expected, path, data[:400])
    return json.loads(data)


def route(grid, target_kind):
    start = next((r, c) for r, row in enumerate(grid) for c, tile in enumerate(row) if tile == "1")
    queue = deque([(start, [])])
    visited = {start}
    while queue:
        (row, col), moves = queue.popleft()
        for dr, dc, move in ((-1, 0, "UP"), (0, -1, "LEFT"), (1, 0, "DOWN"), (0, 1, "RIGHT")):
            r, c = row + dr, col + dc
            if not (0 <= r < len(grid) and 0 <= c < len(grid[r])) or (r, c) in visited:
                continue
            tile = grid[r][c]
            if tile == "#" or tile == "*" and target_kind != "*":
                continue
            if tile == target_kind:
                return moves + [move]
            if tile != "*":
                visited.add((r, c))
                queue.append(((r, c), moves + [move]))
    raise AssertionError(f"No reachable target {target_kind}")


def follow(origin, state, moves):
    for move in moves:
        state = http(origin, move, state["history"])
    return state


def complete_world(origin, state):
    while state["status"] == "playing":
        state = follow(origin, state, route(state["grid"], "."))
    return state


def integration(origin):
    health = http(origin, method="GET", path="/api/health")
    assert health == {"status": "ok", "engine": "original-java", "worlds": 3}, health
    first = http(origin)
    assert first["score"] == 0 and first["lives"] == 5 and first["coinsRemaining"] > 0
    assert http(origin, "UP")["event"] == "wall"
    coin = http(origin, "RIGHT", "R")
    assert coin["score"] == 10 and coin["history"] == "RR" and coin["lives"] == 5
    # Concurrent requests use their own full histories; no process-global game can leak.
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        checks = list(pool.map(lambda n: http(origin, history="RR" if n % 2 else ""), range(32)))
    assert all(item["score"] == (10 if n % 2 else 0) for n, item in enumerate(checks))
    assert http(origin) == first
    assert http(origin, "RESTART", "RR") == first
    total_coins = 0
    state = first
    for world in range(1, 4):
        assert state["world"] == world
        total_coins += state["coinsRemaining"]
        state = complete_world(origin, state)
        assert state["coinsRemaining"] == 0 and state["lives"] == 5
        assert state["status"] == ("won" if world == 3 else "level-complete")
        if world < 3:
            state = http(origin, "NEXT", state["history"])
    assert state["score"] == total_coins * 10
    http(origin, "RIGHT", state["history"], expected=400)
    # Actual traps over three worlds exhaust five lives, keep earned points, then stop.
    trap_state = http(origin)
    for world in range(1, 4):
        trap_state = follow(origin, trap_state, route(trap_state["grid"], "*"))
        assert trap_state["event"] == "trap"
        assert trap_state["lives"] == max(0, 5 - world * 2)
        if world < 3:
            trap_state = complete_world(origin, trap_state)
            trap_state = http(origin, "NEXT", trap_state["history"])
    assert trap_state["status"] == "game-over"
    http(origin, "LEFT", trap_state["history"], expected=400)
    assert http(origin, "RESTART", trap_state["history"]) == first
    for kwargs in (
        {"command": "FLY"}, {"command": "NEXT"}, {"history": "N"}, {"history": "X"},
        {"history": "R" * 2049}, {"raw": b"command=STATE&score=9999"},
        {"raw": b"command=STATE&command=RESTART"}, {"raw": b"history=%XX"}
    ):
        http(origin, expected=400, **kwargs)
    http(origin, expected=413, raw=b"x" * 2301)
    http(origin, expected=415, content_type="application/json", raw=b"{}")
    http(origin, expected=405, method="GET")
    http(origin, expected=404, method="GET", path="/missing")
    http(origin, expected=405, path="/")
    for path, marker in (("/", b"Les jardins"), ("/game.js", b"/api/game"), ("/style.css", b".board")):
        with urlopen(origin + path, timeout=30) as response:
            assert response.status == 200 and marker in response.read()
            assert "frame-ancestors 'none'" in response.headers["Content-Security-Policy"]
    print(json.dumps({"result": "PASS", "origin": origin, "worlds_completed": 3, "coins": total_coins, "winning_score": state["score"], "winning_moves": len(state["history"]), "traps_game_over": True, "concurrent_visitor_requests": 32, "error_cases": 14}))


@contextlib.contextmanager
def local_server():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    log = (ROOT / ".build" / "http-test.log").open("w", encoding="utf-8")
    process = subprocess.Popen(["java", "--add-modules", "jdk.httpserver", "-jar", str(ROOT / ".build" / "game.jar"), str(port)], cwd=ROOT, stdout=log, stderr=log)
    origin = f"http://127.0.0.1:{port}"
    try:
        for _ in range(50):
            try:
                with urlopen(origin + "/api/health", timeout=1):
                    break
            except OSError:
                if process.poll() is not None:
                    raise AssertionError("Local Java server exited; see .build/http-test.log")
                time.sleep(0.1)
        else:
            raise AssertionError("Local Java server did not start")
        yield origin
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
        log.close()


if __name__ == "__main__":
    if len(sys.argv) > 1:
        integration(sys.argv[1].rstrip("/"))
    else:
        parity()
        with local_server() as origin:
            integration(origin)
