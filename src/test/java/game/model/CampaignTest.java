package game.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Winning replays use only the same public commands accepted by the three UIs. */
class CampaignTest {
    private static final String NAME = "Explorateur";
    private static final List<String> OPENING = List.of(
            "RRRRRRDDLLLLL",
            "RRRRRRDDLLLLLRRRRRURRURR",
            "RRRRRRRR5RRDDLLLLLLLLLL");

    private record Solution(long seed, List<String> levels) {
        @Override public String toString() { return "seed=" + seed; }
    }

    static Stream<Solution> winningSolutions() {
        return Stream.of(
                new Solution(20261022L, List.of(OPENING.get(0), OPENING.get(1), OPENING.get(2),
                        "RDDD2R2L", "22R", "2RDD2LULL2L2LR")),
                new Solution(1337L, List.of(OPENING.get(0), OPENING.get(1), OPENING.get(2),
                        "RR2RD2R2D", "22R", "2RU2LL2U2L2R2D")));
    }

    @ParameterizedTest(name = "six complete maps without losing a life: {0}")
    @MethodSource("winningSolutions")
    void entireCampaignCanBeWonThroughNormalCommands(Solution solution) {
        GameEngine game = GameEngine.campaign(NAME, solution.seed());
        int[] checkpointScores = {60, 130, 150, 180, 180, 180};
        boolean crossedDoor = false;
        boolean usedHourglass = false;
        boolean usedTeleport = false;
        boolean weaponProtectedPlayer = false;
        int commands = 0;
        int frozenMovements = 0;
        for (int levelIndex = 0; levelIndex < solution.levels().size(); levelIndex++) {
            if (levelIndex > 0) {
                assertEquals("level-complete", game.snapshot().status());
                game.nextLevel();
                commands++;
            }
            assertEquals(levelIndex, game.snapshot().levelIndex());
            assertEquals("playing", game.snapshot().status());
            for (char command : solution.levels().get(levelIndex).toCharArray()) {
                GameSnapshot before = game.snapshot();
                apply(game, command);
                GameSnapshot after = game.snapshot();
                if (isMovement(command) && before.freezeTurns() > 0) {
                    frozenMovements++;
                    assertEquals(before.freezeTurns() - 1, after.freezeTurns());
                    for (EnemySnapshot survivor : after.enemies()) {
                        EnemySnapshot old = before.enemies().stream()
                                .filter(enemy -> enemy.id().equals(survivor.id())).findFirst().orElseThrow();
                        assertEquals(new Position(old.row(), old.col()), new Position(survivor.row(), survivor.col()),
                                "Hourglass freezes surviving enemies, including on a wall attempt");
                    }
                }
                if (isMovement(command) && (after.playerRow() != before.playerRow()
                        || after.playerCol() != before.playerCol())) {
                    crossedDoor |= before.terrain().get(after.playerRow()).charAt(after.playerCol()) == 'D';
                }
                if (command >= '1' && command <= '5') {
                    InventoryEntry used = before.inventory().get(command - '1');
                    usedHourglass |= used.name().equals("Sablier") && after.freezeTurns() == 10;
                    usedTeleport |= used.id().equals("skill-teleport")
                            && after.inventory().stream().anyMatch(entry -> entry.id().equals(used.id()));
                }
                weaponProtectedPlayer |= after.messages().stream().anyMatch(text -> text.contains("aucun dégât reçu"));
                commands++;
            }
            GameSnapshot checkpoint = game.snapshot();
            assertEquals(levelIndex == 5 ? "won" : "level-complete", checkpoint.status(), checkpoint.messages().toString());
            assertEquals(checkpointScores[levelIndex], checkpoint.score());
            assertEquals(5, checkpoint.lives(), "The route needs no death, reset or replenishment");
            if (levelIndex == 0) assertTrue(checkpoint.inventory().isEmpty());
            if (levelIndex == 1) {
                assertEquals(0, checkpoint.defeatedEnemies());
                assertEquals(List.of("skill-lockpick"), checkpoint.inventory().stream().map(InventoryEntry::id).toList());
            }
            if (levelIndex == 2) {
                assertEquals(3, checkpoint.defeatedEnemies());
                assertEquals(List.of("skill-lockpick", "skill-teleport"),
                        checkpoint.inventory().stream().map(InventoryEntry::id).toList());
            }
            assertEquals(NAME, checkpoint.playerName());
            assertTrue(checkpoint.inventory().size() <= Player.INVENTORY_CAPACITY);
            switch (Campaign.levels().get(levelIndex).goal()) {
                case COLLECT_COINS -> assertEquals(0, checkpoint.coinsRemaining());
                case DEFEAT_ENEMIES -> assertEquals(0, checkpoint.enemiesRemaining());
                case FIND_TROPHY -> assertTrue(checkpoint.terrain().stream().noneMatch(row -> row.contains("T")));
            }
        }
        GameSnapshot won = game.snapshot();
        assertEquals(6, won.levelCount());
        assertEquals(5, won.levelIndex());
        assertEquals(6, won.defeatedEnemies());
        assertEquals(0, won.enemiesRemaining());
        assertTrue(won.inventory().stream().anyMatch(entry -> entry.id().equals("skill-lockpick")));
        assertTrue(won.inventory().stream().anyMatch(entry -> entry.id().equals("skill-teleport")));
        assertTrue(crossedDoor, "The replay must actually pass the locked door");
        assertTrue(usedHourglass, "The replay must consume a real ten-turn hourglass");
        assertEquals(10, frozenMovements, "The hourglass protects exactly ten movement attempts");
        assertTrue(usedTeleport, "The acquired teleport skill is reusable");
        assertTrue(weaponProtectedPlayer, "Combat must use the collected weapons");
        assertTrue(commands < 2048, "Winning replay fits the HTTP history limit");
        assertThrows(IllegalArgumentException.class, () -> game.move(Direction.RIGHT));
        assertThrows(IllegalArgumentException.class, game::nextLevel);
    }

    @Test
    void campaignCoversDifferentGoalsAndAllThreeEnemyClasses() {
        List<LevelDefinition> levels = Campaign.levels();
        assertEquals(6, levels.size());
        assertEquals(EnumSet.allOf(LevelDefinition.Goal.class),
                EnumSet.copyOf(levels.stream().map(LevelDefinition::goal).toList()));
        assertTrue(levels.stream().anyMatch(LevelDefinition::toroidal));
        String symbols = String.join("", levels.stream().flatMap(level -> level.rows().stream()).toList());
        for (char symbol : new char[] {'R', 'G', 'C', 'D', '*', 'W', 'H', 'T'})
            assertTrue(symbols.indexOf(symbol) >= 0, "Missing campaign mechanic: " + symbol);
        assertEquals(Set.of("random", "ghost", "hunter"), levels.stream()
                .flatMap(level -> GameEngine.ofLevels(NAME, 20261022L, List.of(level)).snapshot().enemies().stream())
                .map(EnemySnapshot::type).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void restartPreservesSeedAndDoesNotMutateAnotherVisitorsSession() {
        GameEngine played = GameEngine.campaign(NAME, 20261022L);
        GameEngine otherVisitor = GameEngine.campaign("Autre visiteur", 1337L);
        GameSnapshot initial = played.snapshot();
        GameSnapshot otherInitial = otherVisitor.snapshot();
        for (char command : OPENING.getFirst().toCharArray()) apply(played, command);
        played.nextLevel();
        for (char command : OPENING.get(1).toCharArray()) apply(played, command);
        assertEquals(130, played.snapshot().score());
        played.restart();
        assertEquals(initial, played.snapshot());
        assertEquals(otherInitial, otherVisitor.snapshot());
        for (char command : OPENING.getFirst().toCharArray()) apply(played, command);
        assertEquals("level-complete", played.snapshot().status());
        assertEquals(60, played.snapshot().score());
        assertEquals(otherInitial, otherVisitor.snapshot());
    }

    private static boolean isMovement(char command) { return "ULDR".indexOf(command) >= 0; }

    private static void apply(GameEngine game, char command) {
        switch (command) {
            case 'U' -> game.move(Direction.UP);
            case 'L' -> game.move(Direction.LEFT);
            case 'D' -> game.move(Direction.DOWN);
            case 'R' -> game.move(Direction.RIGHT);
            default -> {
                if (command < '1' || command > '5') throw new IllegalArgumentException("Bad replay command");
                assertTrue(game.useInventory(command - '1'), "Replayed inventory command must have an effect");
            }
        }
    }
}
