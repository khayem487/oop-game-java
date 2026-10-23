package game.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class GameRulesTest {
    private static LevelDefinition map(boolean toroidal, LevelDefinition.Goal goal, String... rows) {
        return LevelDefinition.of("Carte de test", "Atteindre l'objectif indiqué", List.of(rows), toroidal, goal);
    }
    private static GameEngine game(String... rows) {
        return GameEngine.ofLevels("Alice", 47, List.of(map(false, LevelDefinition.Goal.COLLECT_COINS, rows)));
    }
    private static Level level(boolean toroidal, String... rows) {
        return new Level(map(toroidal, LevelDefinition.Goal.COLLECT_COINS, rows), new Player("Alice"), 0, new SplittableRandom(7));
    }
    private static void right(GameEngine game, int count) {
        for (int index = 0; index < count; index++) game.move(Direction.RIGHT);
    }
    private static Position position(GameSnapshot snapshot) { return new Position(snapshot.playerRow(), snapshot.playerCol()); }

    @Test void cellsProvidePolymorphicCollisionRulesAndDoorsStayClosedToEnemies() {
        Position position = new Position(0, 0);
        Player player = new Player("Alice");
        Enemy enemy = new RandomEnemy("r", "Sentinelle", position);
        Cell floor = new FloorCell(position), wall = new WallCell(position), trap = new TrapCell(position), door = new LockedDoorCell(position);
        assertTrue(floor.canEnter(player));
        assertTrue(floor.canEnter(enemy));
        assertFalse(wall.canEnter(player));
        assertFalse(wall.canEnter(enemy));
        assertTrue(trap.canEnter(player));
        assertFalse(trap.canEnter(enemy));
        assertFalse(door.canEnter(player));
        player.collect(new Skill(Skill.Type.LOCKPICK));
        assertTrue(door.canEnter(player));
        assertFalse(door.canEnter(enemy));
    }

    @Test void toroidalNeighboursWrapInEveryDirectionAndBoundedOnesStop() {
        Level torus = level(true, "1. ", "   ");
        Position corner = new Position(0, 0);
        assertEquals(new Position(1, 0), torus.neighbour(corner, Direction.UP).orElseThrow());
        assertEquals(new Position(0, 2), torus.neighbour(corner, Direction.LEFT).orElseThrow());
        assertEquals(new Position(1, 0), torus.neighbour(corner, Direction.DOWN).orElseThrow());
        assertEquals(new Position(0, 1), torus.neighbour(corner, Direction.RIGHT).orElseThrow());
        Level bounded = level(false, "1. ", "   ");
        assertTrue(bounded.neighbour(corner, Direction.UP).isEmpty());
        assertTrue(bounded.neighbour(corner, Direction.LEFT).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> bounded.getCell(new Position(-1, 0)));
        assertThrows(IllegalArgumentException.class, () -> bounded.getCell(new Position(0, 3)));
    }

    @Test void coinsDisappearIncreaseScoreAndCompleteEachLevelWithoutResettingPlayerData() {
        LevelDefinition definition = map(false, LevelDefinition.Goal.COLLECT_COINS, "1.");
        GameEngine game = GameEngine.ofLevels("Alice", 9, List.of(definition, definition));
        game.move(Direction.RIGHT);
        assertEquals(10, game.snapshot().score());
        assertEquals(0, game.snapshot().coinsRemaining());
        assertEquals("  ", game.snapshot().terrain().getFirst());
        assertEquals("level-complete", game.snapshot().status());
        assertThrows(IllegalArgumentException.class, () -> game.move(Direction.RIGHT));
        game.nextLevel();
        assertEquals(10, game.snapshot().score());
        assertEquals(5, game.snapshot().lives());
        assertEquals(new Position(0, 0), position(game.snapshot()));
        game.move(Direction.RIGHT);
        assertEquals("won", game.snapshot().status());
        assertEquals(20, game.snapshot().score());
        assertThrows(IllegalArgumentException.class, game::nextLevel);
    }

    @Test void trapsAreConsumedAndThreeTrapHitsCauseGameOverThenCleanRestart() {
        GameEngine game = game("1***.");
        GameSnapshot initial = game.snapshot();
        game.move(Direction.RIGHT);
        assertEquals(3, game.snapshot().lives());
        assertEquals(new Position(0, 0), position(game.snapshot()));
        assertEquals(' ', game.snapshot().terrain().getFirst().charAt(1));
        right(game, 2);
        assertEquals(1, game.snapshot().lives());
        right(game, 3);
        assertEquals(0, game.snapshot().lives());
        assertEquals("game-over", game.snapshot().status());
        assertThrows(IllegalArgumentException.class, () -> game.useInventory(0));
        game.restart();
        assertEquals(initial, game.snapshot());
    }

    @Test void wallAttemptsStillAdvanceTheTurnAndMoveEnemies() {
        GameEngine game = game("########", "#1  C .#", "########");
        game.move(Direction.UP);
        GameSnapshot snapshot = game.snapshot();
        assertEquals(1, snapshot.turns());
        assertEquals(new Position(1, 1), position(snapshot));
        assertEquals(3, snapshot.enemies().getFirst().col());
    }

    @Test void ordinaryEnemyCollisionLosesOneLifeAndResetsBothActors() {
        GameEngine game = game("#####", "#1R.#", "#####");
        game.move(Direction.RIGHT);
        GameSnapshot snapshot = game.snapshot();
        assertEquals(4, snapshot.lives());
        assertEquals(new Position(1, 1), position(snapshot));
        assertEquals(2, snapshot.enemies().getFirst().col());
        assertTrue(snapshot.messages().stream().anyMatch(message -> message.contains("Sentinelle")));
    }

    @Test void simultaneousGhostContactsApplyEachDamageAndResetEverySurvivor() {
        GameEngine game = game("#####", "# G #", "#G1.#", "#####");
        game.move(Direction.DOWN); // A wall attempt; both aligned ghosts enter the player's cell.
        GameSnapshot snapshot = game.snapshot();
        assertEquals(3, snapshot.lives());
        assertEquals(new Position(2, 2), position(snapshot));
        assertEquals(List.of(new Position(1, 2), new Position(2, 1)), snapshot.enemies().stream()
                .map(enemy -> new Position(enemy.row(), enemy.col())).toList());
    }

    @Test void losingLivesOnATrapAlsoResetsAllEnemiesWithoutMovingThemAgain() {
        GameEngine game = game("######", "# G  #", "#G1*.#", "######");
        game.move(Direction.RIGHT);
        GameSnapshot snapshot = game.snapshot();
        assertEquals(3, snapshot.lives());
        assertEquals(List.of(new Position(1, 2), new Position(2, 1)), snapshot.enemies().stream()
                .map(enemy -> new Position(enemy.row(), enemy.col())).toList());
        assertEquals(' ', snapshot.terrain().get(2).charAt(3));
    }

    @Test void occupiedCellSetDeduplicatesOverlappingEnemiesAndIsDefensive() {
        Level level = level(false, "1.RG.");
        Position shared = new Position(0, 2);
        level.getEnemies().forEach(enemy -> enemy.moveTo(shared));
        Set<Position> occupied = level.getOccupiedEnemyCells();
        assertEquals(Set.of(shared), occupied);
        assertThrows(UnsupportedOperationException.class, occupied::clear);
        assertThrows(UnsupportedOperationException.class, () -> level.getEnemies().clear());
        level.getEnemies().getFirst().moveTo(new Position(0, 1));
        assertEquals(Set.of(shared), occupied);
        assertEquals(2, level.getOccupiedEnemyCells().size());
    }

    @Test void randomEnemyCannotCrossWallsDoorsOrTrapsAndDoesNotCollectCoins() {
        Level prison = level(false, "#####", "#1. #", "#*RD#", "#####");
        Enemy enemy = prison.getEnemies().getFirst();
        // The only walkable exit is the coin immediately above it.
        SplittableRandom random = new SplittableRandom(70);
        boolean crossedCoin = false;
        for (int turn = 0; turn < 12; turn++) {
            enemy.takeTurn(prison, prison.getPlayer(), random);
            assertTrue(prison.getCell(enemy.getPosition()) instanceof FloorCell);
            crossedCoin |= prison.getCell(enemy.getPosition()).hasCoin();
        }
        assertTrue(crossedCoin, "The enemy should actually walk across the coin in this seeded scenario");
        assertEquals(1, prison.coinsRemaining());
        // All four directions are blocked by a wall, trap or locked door.
        Level surrounded = level(false, "1.#", "#*#", "#R#", "#D#", "###");
        Enemy trapped = surrounded.getEnemies().getFirst();
        Position start = trapped.getPosition();
        for (int turn = 0; turn < 8; turn++) trapped.takeTurn(surrounded, surrounded.getPlayer(), random);
        assertEquals(start, trapped.getPosition());
        assertEquals(1, trapped.getLives());
    }

    @Test void ghostCrossesObstaclesKeepsHeadingAndTurnsOnlyWhenAligned() {
        Level level = level(false, "#######", "#1#D* #", "#     #", "#  .  #", "#######");
        level.getPlayer().moveTo(new Position(2, 1));
        GhostEnemy ghost = new GhostEnemy("g", "Fantôme", new Position(1, 3), Direction.RIGHT);
        SplittableRandom random = new SplittableRandom(3);
        ghost.takeTurn(level, level.getPlayer(), random);
        assertEquals(new Position(1, 4), ghost.getPosition()); // Trap ignored.
        ghost.takeTurn(level, level.getPlayer(), random);
        assertEquals(new Position(1, 5), ghost.getPosition()); // Same heading without alignment.
        ghost.takeTurn(level, level.getPlayer(), random);
        assertEquals(new Position(1, 6), ghost.getPosition()); // Wall ignored.
        level.getPlayer().moveTo(new Position(1, 1));
        ghost.takeTurn(level, level.getPlayer(), random);
        assertEquals(new Position(1, 5), ghost.getPosition()); // Now aligned, turns left.
        level.getPlayer().moveTo(new Position(3, 5));
        ghost.takeTurn(level, level.getPlayer(), random);
        assertEquals(new Position(2, 5), ghost.getPosition()); // Aligned column, turns down.
        level.getPlayer().moveTo(new Position(2, 1));
        GhostEnemy throughDoor = new GhostEnemy("g2", "Autre fantôme", new Position(1, 4), Direction.LEFT);
        throughDoor.takeTurn(level, level.getPlayer(), random);
        assertEquals(new Position(1, 3), throughDoor.getPosition()); // Locked door ignored as well.
    }

    @Test void hunterFindsActualShortestDetourAndUsesToroidalEdges() {
        Level maze = level(false, "########", "#C#  1.#", "# # ####", "#      #", "########");
        Enemy hunter = maze.getEnemies().getFirst();
        hunter.takeTurn(maze, maze.getPlayer(), new SplittableRandom(4));
        assertEquals(new Position(2, 1), hunter.getPosition()); // Must initially move away from player's row.
        assertEquals(3, hunter.getLives());
        assertEquals(2, hunter.collisionDamage());
        Level torus = level(true, "1...C");
        Enemy torusHunter = torus.getEnemies().getFirst();
        torusHunter.takeTurn(torus, torus.getPlayer(), new SplittableRandom(4));
        assertEquals(new Position(0, 0), torusHunter.getPosition()); // One step right, rather than four left.
        Level blocked = level(false, "1.#C#");
        Enemy blockedHunter = blocked.getEnemies().getFirst();
        blockedHunter.takeTurn(blocked, blocked.getPlayer(), new SplittableRandom(4));
        assertEquals(new Position(0, 3), blockedHunter.getPosition());
    }

    @Test void hunterContactCostsTwoLivesButAutomaticWeaponsPreventDamageAndConsumeOnePerHit() {
        GameEngine noWeapon = game("1C.");
        noWeapon.move(Direction.RIGHT);
        assertEquals(3, noWeapon.snapshot().lives());
        GameEngine armed = game("#########", "#1WWWC..#", "#########");
        right(armed, 2);
        assertEquals(5, armed.snapshot().lives());
        assertEquals(2, armed.snapshot().enemies().getFirst().lives());
        assertEquals(5, armed.snapshot().enemies().getFirst().col());
        assertEquals(1, armed.snapshot().inventory().size());
        armed.move(Direction.RIGHT);
        assertEquals(1, armed.snapshot().enemies().getFirst().lives());
        armed.move(Direction.RIGHT);
        assertEquals(0, armed.snapshot().enemiesRemaining());
        assertEquals(1, armed.snapshot().defeatedEnemies());
        assertEquals(5, armed.snapshot().lives());
        assertTrue(armed.snapshot().inventory().isEmpty());
    }

    @Test void hourglassFreezesExactlyTenMovementAttemptsButActivationIsNotATurn() {
        GameEngine game = game("###############", "#1H       .  G#", "###############");
        game.move(Direction.RIGHT);
        EnemySnapshot frozen = game.snapshot().enemies().getFirst();
        int before = game.snapshot().turns();
        assertTrue(game.useInventory(0));
        assertEquals(before, game.snapshot().turns());
        assertEquals(10, game.snapshot().freezeTurns());
        assertTrue(game.snapshot().inventory().isEmpty());
        for (int turn = 1; turn <= 10; turn++) {
            game.move(Direction.UP); // Even hitting a wall consumes one frozen turn.
            assertEquals(frozen, game.snapshot().enemies().getFirst());
            assertEquals(10 - turn, game.snapshot().freezeTurns());
        }
        game.move(Direction.UP);
        assertEquals(frozen.col() - 1, game.snapshot().enemies().getFirst().col());
    }

    @Test void lockpickRequiresStrictlyMoreThanOneHundredPointsAndRemainsReusable() {
        GameEngine game = game("1...........D.");
        right(game, 10);
        assertEquals(100, game.snapshot().score());
        assertTrue(game.snapshot().inventory().isEmpty());
        game.move(Direction.RIGHT);
        assertEquals(110, game.snapshot().score());
        assertEquals(List.of("skill-lockpick"), game.snapshot().inventory().stream().map(InventoryEntry::id).toList());
        assertFalse(game.useInventory(0)); // Automatic ability, no manual action needed.
        assertEquals(11, game.snapshot().turns());
        game.move(Direction.RIGHT);
        assertEquals(12, game.snapshot().playerCol());
        assertEquals('D', game.snapshot().terrain().getFirst().charAt(12));
        game.move(Direction.LEFT);
        game.move(Direction.RIGHT);
        assertEquals(1, game.snapshot().inventory().size());
        game.move(Direction.RIGHT);
        assertEquals("won", game.snapshot().status());
    }

    @Test void fullInventoryLeavesItemsOnGroundAndDefersSkillsUntilASlotIsFreed() {
        GameEngine game = game("1HHHHHW...........D.");
        right(game, 5);
        assertEquals(5, game.snapshot().inventory().size());
        game.move(Direction.RIGHT);
        assertEquals('W', game.snapshot().terrain().getFirst().charAt(6));
        assertEquals(5, game.snapshot().inventory().size());
        right(game, 11);
        assertEquals(110, game.snapshot().score());
        assertTrue(game.snapshot().inventory().stream().noneMatch(entry -> entry.kind().equals("skill")));
        game.move(Direction.RIGHT); // D remains closed because skill could not be gained.
        assertEquals(17, game.snapshot().playerCol());
        assertTrue(game.useInventory(0));
        assertEquals(5, game.snapshot().inventory().size());
        assertEquals("skill-lockpick", game.snapshot().inventory().getLast().id());
        game.move(Direction.RIGHT);
        assertEquals(18, game.snapshot().playerCol());
    }

    @Test void questTrophyIsConsumedImmediatelyEvenWithFiveFullInventorySlots() {
        GameEngine game = GameEngine.ofLevels("Alice", 12, List.of(map(false,
                LevelDefinition.Goal.FIND_TROPHY, "1HHHHHT")));
        right(game, 5);
        assertEquals(5, game.snapshot().inventory().size());
        game.move(Direction.RIGHT);
        assertEquals("won", game.snapshot().status());
        assertEquals(' ', game.snapshot().terrain().getFirst().charAt(6));
        assertEquals(5, game.snapshot().inventory().size());
        assertTrue(game.snapshot().inventory().stream().noneMatch(entry -> entry.name().equals("Trophée")));
    }

    @Test void threeEnemyDefeatsUnlockReusableTeleportOntoAnUnoccupiedEmptyCell() {
        LevelDefinition fight = map(false, LevelDefinition.Goal.DEFEAT_ENEMIES, "1WR");
        GameEngine game = GameEngine.ofLevels("Alice", 19, List.of(fight, fight, fight,
                map(false, LevelDefinition.Goal.COLLECT_COINS, "1    .")));
        for (int number = 1; number <= 3; number++) {
            game.move(Direction.RIGHT);
            if (game.snapshot().status().equals("playing")) game.move(Direction.RIGHT);
            assertEquals(number, game.snapshot().defeatedEnemies());
            assertEquals("level-complete", game.snapshot().status());
            assertEquals(number == 3 ? 1 : 0, game.snapshot().inventory().size());
            game.nextLevel();
        }
        InventoryEntry teleport = game.snapshot().inventory().getFirst();
        assertEquals("skill-teleport", teleport.id());
        Position before = position(game.snapshot());
        int turns = game.snapshot().turns();
        assertTrue(game.useInventory(0));
        assertNotEquals(before, position(game.snapshot()));
        assertEquals(' ', game.snapshot().terrain().getFirst().charAt(game.snapshot().playerCol()));
        assertEquals(List.of(teleport), game.snapshot().inventory());
        assertEquals(turns, game.snapshot().turns());
        before = position(game.snapshot());
        assertTrue(game.useInventory(0));
        assertNotEquals(before, position(game.snapshot()));
        assertEquals(List.of(teleport), game.snapshot().inventory());
    }

    @Test void snapshotsAreImmutableDefensiveAndIndependentBetweenGames() {
        GameEngine first = game("1H.."), second = game("1H..");
        GameSnapshot original = first.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> original.terrain().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.enemies().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.inventory().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.messages().clear());
        first.move(Direction.RIGHT);
        first.useInventory(0);
        assertEquals(original, second.snapshot());
        assertEquals(" H..", original.terrain().getFirst());
        assertEquals(0, original.turns());
    }

    @Test void seededReplayAndRestartReconstructIdenticalAiOutcomes() {
        LevelDefinition definition = map(true, LevelDefinition.Goal.COLLECT_COINS,
                "#########", "#1 H....#", "# R # G #", "#   C   #", "#########");
        GameEngine first = GameEngine.ofLevels("Alice", 2026, List.of(definition));
        GameEngine second = GameEngine.ofLevels("Alice", 2026, List.of(definition));
        List<Direction> replay = List.of(Direction.RIGHT, Direction.RIGHT, Direction.UP, Direction.LEFT,
                Direction.DOWN, Direction.RIGHT, Direction.RIGHT, Direction.DOWN);
        for (Direction direction : replay) {
            if (!first.snapshot().status().equals("playing")) break;
            first.move(direction);
            second.move(direction);
            assertEquals(first.snapshot(), second.snapshot());
        }
        first.restart();
        second.restart();
        assertEquals(first.snapshot(), second.snapshot());
        first.move(Direction.RIGHT);
        assertNotEquals(first.snapshot(), second.snapshot());
    }

    private static GameEngine acquireTeleport(LevelDefinition finalLevel) {
        LevelDefinition fight = map(false, LevelDefinition.Goal.DEFEAT_ENEMIES, "1WR");
        GameEngine game = GameEngine.ofLevels("Alice", 19, List.of(fight, fight, fight, finalLevel));
        for (int index = 0; index < 3; index++) {
            game.move(Direction.RIGHT);
            if (game.snapshot().status().equals("playing")) game.move(Direction.RIGHT);
            game.nextLevel();
        }
        return game;
    }

    @Test void teleportSelectionIsSeededAndReplaysIdentically() {
        LevelDefinition destination = map(false, LevelDefinition.Goal.COLLECT_COINS, "1    .");
        GameEngine first = acquireTeleport(destination), second = acquireTeleport(destination);
        first.useInventory(0);
        second.useInventory(0);
        assertEquals(first.snapshot(), second.snapshot());
        first.useInventory(0);
        second.useInventory(0);
        assertEquals(first.snapshot(), second.snapshot());
    }

    @Test void teleportWithoutAnEmptyDestinationDoesNotMoveOrConsumeTheSkill() {
        GameEngine game = acquireTeleport(map(false, LevelDefinition.Goal.COLLECT_COINS, "1."));
        GameSnapshot before = game.snapshot();
        assertFalse(game.useInventory(0));
        assertEquals(position(before), position(game.snapshot()));
        assertEquals(before.inventory(), game.snapshot().inventory());
        assertEquals(before.turns(), game.snapshot().turns());
    }

    @Test void invalidCommandsCannotMutateState() {
        GameEngine game = game("1.");
        GameSnapshot before = game.snapshot();
        assertThrows(IllegalArgumentException.class, () -> game.move(null));
        assertThrows(IllegalArgumentException.class, () -> game.useInventory(0));
        assertThrows(IllegalArgumentException.class, () -> game.useInventory(-1));
        assertThrows(IllegalArgumentException.class, game::nextLevel);
        assertEquals(before, game.snapshot());
    }
}
