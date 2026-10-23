# Implementation contract: complete the six-world OOP assignment

Source requirements: `src/app.pdf` (50 pages), original commit `8e46b695...`.
Read extracted requirements in `../../tmp/pdfs/oop-game-consignes-extraction.txt` from repository root (absolute workspace path also available in parent chat).

## Ownership

- Core agent: `src/main/java/game/model/**`, `src/test/java/game/model/**` only.
- Desktop agent: `desktop/**` only; tell parent necessary Maven profile/dependencies. Use JavaFX 21 compatible with installed JDK21, pin verified dependency version. No application-global installs.
- Web agent: `web/src/**`, `web/public/**`, `web/tests/**` only. New source classes must use package `game.web`. Parent changes build scripts/Dockerfile.
- Parent: build, CLI (`src/main/java/game/cli/**`), archives, scripts, Maven, documentation, integration/verification/deployment.
- Do not commit, switch branches, push or deploy from agents. Shared branch `feat/complete-oop-game`.

## Shared Java API (implement exactly; message parent promptly for necessary changes)

Package `game.model`:

```java
public enum Direction { UP, LEFT, DOWN, RIGHT }
public record Position(int row, int col) {}
public record EnemySnapshot(String id, String type, String name, int row, int col, int lives) {}
public record InventoryEntry(String id, String name, String kind, String description) {}
public record GameSnapshot(
    String playerName, int score, int lives, int defeatedEnemies,
    int levelIndex, int levelCount, int turns,
    String levelName, String objectiveText, String status, boolean toroidal,
    int playerRow, int playerCol,
    java.util.List<String> terrain,
    java.util.List<EnemySnapshot> enemies,
    java.util.List<InventoryEntry> inventory,
    int coinsRemaining, int enemiesRemaining, int freezeTurns,
    java.util.List<String> messages) {}
public final class GameEngine {
    public static GameEngine campaign(String name, long seed);
    public static GameEngine ofLevels(String name, long seed, java.util.List<LevelDefinition> levels);
    public GameSnapshot snapshot();
    public void move(Direction direction);
    public boolean useInventory(int index); // zero based, no movement turn consumed
    public void sortInventory();
    public void nextLevel(); // only after level-complete; last goal gives won
    public void restart(); // same name/seed/campaign, clean progress
}
public final class LevelDefinition {
    public static LevelDefinition fromFile(java.nio.file.Path path) throws java.io.IOException;
    // Core agent defines additional constructors/factories for tests and campaign.
}
```

Snapshot lists and elements must be immutable defensive values. `levelIndex` is zero based. `terrain` holds ground symbols only (no player/enemies); actor positions are separate and multiple enemies may occupy a cell. `type` values: `random`, `ghost`, `hunter`. Inventory `kind`: `item` or `skill`. Status values: `playing`, `level-complete`, `won`, `game-over`. An action reports short human-readable French messages; reject invalid commands with IllegalArgumentException. Desktop/web must render text safely.

## Required game rules

Complete worlds1–6, not merely three browser demo maps. World1 Player immutable name, safe score, equals/hashCode, default JoueurN/static counter, Javadoc. World2 object composition, robust player/map validation, bounds, console ZQSD, file levels, executable JAR. World3 coins +10, five lives, consumed traps -2 and return to spawn, replay/chain levels, **cell objects**, polymorphic doors D, toroidal neighbors. World4 abstract actor hierarchy, random enemy R (one life), collisions -1, enemy reset on any player life loss, occupied-cell set, ghost G ignores obstacles and turns to chase when aligned, hunter C (three lives, collision -2) with genuine shortest-path chase, packages. World5 type-safe five-slot inventory, items consumed after use/skills reusable, no collection/gain if full, automatic weapon W deals one enemy damage and prevents player damage, ten-turn hourglass H, automatic lockpick at score strictly >100, teleport after three defeats, items then skills sorted by name, coin/kill-all/special-item victory goals. World6 JavaFX PAC, Canvas, keyboard, HUD/inventory, Properties, clickable inventory, menu/name/replay/credits dialogs, continuous movement via AnimationTimer.

Use seeded deterministic randomness for all game events so headless replay and desktop share the same engine. Enemy movement follows each player movement attempt, including hitting a wall. Meaningful tests must verify rule boundaries, toroidal/pathfinding, death/reset, inventory full and skill thresholds, independent sessions. Parent will review edge cases and run integration tests.

Campaign should include at least six well-designed small playable maps demonstrating all mechanics, meaningful progression and the different victory goals. Do not create a map that cannot be won. Clearly distinguish **cartes** in UI from course **mondes**. Include useful mechanics help; no fake controls. Core agent owns campaign definitions. Agree any required rule clarification in messages.

## Web transport

Keep a real Java server and replay model, no JS game-rule clone. POST `/api/game` accepts `name`, `seed`, `history`, `command`; defaults name Explorateur/seed positive fixed value. History chars U/L/D/R/N, slots1–5 and S(sort), max2048. Commands STATE, RESTART, UP/LEFT/DOWN/RIGHT, NEXT, USE_1…USE_5, SORT. Server validates all values; user cannot submit score/lives/grid. Return old useful fields plus complete snapshot inventory/enemies/objective/terrain and canonical name/seed/history, `version:2` for browser-save migration. Old v1 save must be discarded with a clear message. Body limit ~4KB, bounded workers and computation; no external URL/file/import inputs. HTML/CSS/JS served under strict CSP. Root deployment stays on existing project; no extra GitHub grant.

## Verification/reporting

Be candid about compile/test status. No silent stubbed features and no artificial giant test-count loops. Desktop must genuinely compile and its controller/HUD behavior be checked. Parent will perform browser UI review and publish the updated application only after passing checks. Leave the user's originals in archives managed by parent.
