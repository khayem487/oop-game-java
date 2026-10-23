"""Real Java HTTP gameplay, input validation and process-restart recovery.

No origin: start/stop an owned local server. With origin: test deployed API.
All campaign actions are ordinary public commands, without state injection.
"""
import concurrent.futures
import contextlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
SOLUTIONS = json.loads((ROOT / "scripts/campaign-solution.json").read_text(encoding="utf-8"))["solutions"]
ACTIONS = dict(U="UP", L="LEFT", D="DOWN", R="RIGHT", N="NEXT", S="SORT",
               **{str(slot): f"USE_{slot}" for slot in range(1, 6)})


def http(origin, command="STATE", history="", *, name="Explorateur", seed="1337",
         expected=200, raw=None, content_type="application/x-www-form-urlencoded",
         method="POST", path="/api/game", headers=None):
    body = raw if raw is not None else urlencode(dict(name=name, seed=seed, history=history, command=command)).encode()
    request = Request(origin + path, data=body if method == "POST" else None, method=method,
                      headers={"Content-Type": content_type, **(headers or {})})
    try:
        with urlopen(request, timeout=45) as response:
            status, data = response.status, response.read()
    except HTTPError as error:
        status, data = error.code, error.read()
    assert status == expected, (status, expected, path, data[:400])
    return json.loads(data)


def integration(origin):
    health = http(origin, method="GET", path="/api/health")
    assert health == dict(status="ok", engine="shared-java", version=2, maps=6), health
    first = http(origin)
    assert first["version"] == 2 and first["score"] == 0 and first["lives"] == 5
    assert first["levelCount"] == 6 and first["coinsRemaining"] == 6
    coin = http(origin, "RIGHT", "R")
    assert coin["score"] == 10 and coin["history"] == "RR" and coin["lives"] == 5
    assert http(origin, "RESTART", "RR") == first
    assert http(origin, "SORT")["turns"] == 0
    identity = http(origin, history="RR", name="Éclaireur", seed="9223372036854775807")
    assert identity["playerName"] == "Éclaireur" and identity["seed"] == "9223372036854775807"
    assert http(origin, headers={"Origin": origin, "Sec-Fetch-Site": "same-origin"}) == first
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        checks = list(pool.map(lambda n: http(origin, history="RR" if n % 2 else ""), range(12)))
    assert all(item["score"] == (10 if n % 2 else 0) for n, item in enumerate(checks))
    assert http(origin) == first
    wins, actions_checked = [], 0
    for solution in SOLUTIONS:
        identity_args = {"name": solution["name"], "seed": solution["seed"]}
        state = http(origin, **identity_args)
        checkpoints = {point["history"]: point for point in solution["checkpoints"]}
        for action in solution["history"]:
            state = http(origin, ACTIONS[action], state["history"], **identity_args)
            actions_checked += 1
            checkpoint = checkpoints.get(state["history"])
            if checkpoint:
                for key in ("levelIndex", "status", "score", "lives", "defeatedEnemies"):
                    assert state[key] == checkpoint[key], (solution["seed"], key, state)
        assert state["status"] == "won" and state["history"] == solution["history"]
        for key in ("score", "lives", "defeatedEnemies"):
            assert state[key] == solution[key]
        assert http(origin, history=solution["history"], **identity_args) == state
        http(origin, "RIGHT", state["history"], expected=400, **identity_args)
        wins.append({"seed": solution["seed"], "maps": 6, "actions": len(state["history"]),
                     "score": state["score"], "lives": state["lives"], "defeatedEnemies": state["defeatedEnemies"]})
    invalid = [
        dict(command="FLY"), dict(command="NEXT"), dict(command="USE_1"),
        dict(history="N"), dict(history="1"), dict(history="X"), dict(history="R" * 2049),
        dict(raw=b"command=STATE&score=9999"), dict(raw=b"command=STATE&command=RESTART"),
        dict(raw=b"history=%XX"), dict(name="<script>"), dict(name=" "),
        dict(seed="0"), dict(seed="-1"), dict(seed="9223372036854775808")]
    for case in invalid:
        http(origin, expected=400, **case)
    http(origin, expected=413, raw=b"x" * 4097)
    http(origin, expected=415, content_type="application/json", raw=b"{}")
    http(origin, expected=405, method="GET")
    http(origin, expected=404, method="GET", path="/missing")
    http(origin, expected=405, path="/")
    http(origin, expected=403, headers={"Origin": "https://example.invalid"})
    http(origin, expected=403, headers={"Sec-Fetch-Site": "cross-site"})
    for path, marker in (("/", b"Les jardins"), ("/game.js", b"/api/game"), ("/style.css", b".board")):
        with urlopen(origin + path, timeout=45) as response:
            assert response.status == 200 and marker in response.read()
            assert "frame-ancestors 'none'" in response.headers["Content-Security-Policy"]
            assert response.headers["X-Content-Type-Options"] == "nosniff"
    print(json.dumps(dict(result="PASS", origin=origin, campaigns=wins, real_http_actions=actions_checked,
                         concurrent_visitor_requests=12, rejected_invalid_requests=len(invalid) + 7), ensure_ascii=False))
    return state


@contextlib.contextmanager
def local_server():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    log_path = ROOT / ".build/http-test.log"
    with log_path.open("a", encoding="utf-8") as log:
        process = subprocess.Popen(["java", "--add-modules", "jdk.httpserver", "-jar",
                                    str(ROOT / ".build/game.jar"), str(port)], cwd=ROOT,
                                   env={**os.environ, "HOST": "127.0.0.1", "PORT": str(port)},
                                   stdout=log, stderr=log)
        origin = f"http://127.0.0.1:{port}"
        try:
            for _ in range(100):
                try:
                    with urlopen(origin + "/api/health", timeout=1):
                        break
                except OSError:
                    if process.poll() is not None:
                        raise AssertionError(f"Local Java server exited; see {log_path}")
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


if __name__ == "__main__":
    if len(sys.argv) > 1:
        integration(sys.argv[1].rstrip("/"))
    else:
        with local_server() as origin:
            final_state = integration(origin)
        with local_server() as fresh_origin:
            restored = http(fresh_origin, history=SOLUTIONS[-1]["history"],
                            name=SOLUTIONS[-1]["name"], seed=SOLUTIONS[-1]["seed"])
            assert restored == final_state, "Confirmed history must survive a server process restart"
            print(json.dumps(dict(result="PASS", process_restart_restore=True)))
