# OOP Game (Java)

Small object-oriented game project built step by step for a university OOP course.
The project progresses by "worlds" and "levels" (World 1, World 2, ...).

## Browser edition — original Java engine

The browser edition uses the existing `Player` and `Level` classes on a real Java HTTP server. JavaScript only sends controls and displays the state returned by Java. It does not calculate collisions, rewards, damage or level completion.

Three playable maps contain 15 coins. Collecting a coin gives 10 points; a trap costs two of the initial five lives and returns the player to the level start. Already collected coins stay collected. These are the rules from the original console `Main`. The terminal entrypoint and earlier course worlds are retained.

### Run locally

**Play the real Java browser edition: https://khayem-oop-game.vercel.app/**
Published and checked on 22 October 2026: all three worlds, 150-point victory,
defeat/restart, independent visitors, keyboard controls and browser-save resume.

Requires **JDK 21+** and Python 3 for the portable build/test helpers. No Maven, Node packages or downloaded Java dependencies are needed.

```text
python scripts/build-web.py
java --add-modules jdk.httpserver -jar .build/game.jar 8080
```

Open `http://localhost:8080`. Use arrow keys, ZQSD, WASD or the on-screen direction buttons. The restart button starts a new adventure. Completing a map unlocks the next one; the final map ends the adventure.

### How player state works

Each request carries only the visitor's history of valid commands. The Java adapter replays that history through the real model and applies the next command. Browser-supplied scores, lives, maps and positions are rejected. The server stores no shared game session, account or personal data, so visitors remain independent and progress does not depend on a warm server instance. The history is saved in that browser's local storage; another browser/device starts its own game.

This is a casual solo demo, without a global leaderboard or prizes. Command histories are not trusted as timing or competitive performance evidence. Input bodies are limited to 2,300 bytes, histories to 2,048 commands, and request workers/queues are bounded. Restarting clears the current history. New maps can require a fresh browser save; incompatible histories restart with a visible message.

### Tests

```text
python scripts/build-web.py
python scripts/test-web.py
```

The suite compiles the original `Player` and `Level` from commit `8e46b6950f3b7c7218e405187ff257d21513c2fa` as reference classes. It checks parity for score, lives, coins, collisions, consumed traps and reset positions, verifies all coins are reachable, then starts an owned Java server and plays all three maps through the actual HTTP API. It also covers defeat/restart, concurrent visitor isolation, malformed/oversized requests and rejected score injection. The helper stops its own test server afterward.

Once a real deployment exists, `python scripts/test-web.py https://YOUR-DEPLOYMENT` checks the same HTTP flows without shared state or external side effects. Browser rendering and keyboard/touch use still require visual checks.

### Container deployment

`Dockerfile.vercel` compiles the original Java classes plus `web/src`, packages the browser assets in the JAR and runs Java as an unprivileged user. It listens on `PORT` (default 3000 in the container). `/api/health` reports the Java engine and three worlds. No database, notification service, external account or secret is required.

Vercel container instances may stop when idle. Since every request reconstructs the game from the browser's validated history, a later request can resume after restart. Availability, startup delay and hosting quotas still depend on the hosting service. This repository does not claim that the deployment is permanently warm.

### HTTP interface

- `GET /api/health` — health and engine identity.
- `POST /api/game` — `application/x-www-form-urlencoded` fields `history` and `command`.
- Commands: `STATE`, `UP`, `LEFT`, `DOWN`, `RIGHT`, `NEXT`, `RESTART`.
- Server response: canonical history, grid, world, status, score, lives, remaining coins, move count and last event.
- `history` uses `U/L/D/R/N`; malformed or impossible histories and early level transitions return 400. Wrong methods return 405, unsupported media types 415 and oversized bodies 413.

The production alias is public and deployment previews remain protected.
The source is published in this repository. Publication currently uses the
authenticated Vercel CLI; automatic Git deployment has not been enabled for
this repository because the GitHub App grant covers five other named repositories.

## How to run (World 1)
From `src`:

```
javac -encoding UTF-8 Main.java Player.java Level.java
java Main ../world2_send/src/level1.txt
```

## Structure
- `src/Main.java` - simple test harness for the current world.
- `src/Player.java` - player model (World 1).
- `src/Level.java` - level model (World 2+).

## Javadoc
Generate HTML docs from `src`:

```
javadoc -d docs Player.java Main.java
```

## Notes
This repo tracks the source files only; generated files and IDE settings are ignored.
