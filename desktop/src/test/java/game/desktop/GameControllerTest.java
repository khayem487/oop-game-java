package game.desktop;

import game.model.Direction;
import game.model.GameEngine;
import game.model.LevelDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GameControllerTest {
    private LevelDefinition level(String name, String row) {
        return LevelDefinition.of(name, "Ramasser toutes les pièces", List.of("#".repeat(row.length()), row, "#".repeat(row.length())), false, LevelDefinition.Goal.COLLECT_COINS);
    }
    private GameController controller(String row) {
        return new GameController(GameEngine.ofLevels("Mina", 2027, List.of(level("Essai", row))));
    }
    @Test void timerUsesLatestDirectionAndDoesNotRepeatWithinTheMovementInterval() {
        GameController control = controller("#1    .#");
        control.requestDirection(Direction.RIGHT); control.pulse(0);
        assertEquals(2, control.state().snapshot().playerCol());
        control.pulse(GameController.STEP_NANOS - 1);
        assertEquals(2, control.state().snapshot().playerCol());
        control.pulse(GameController.STEP_NANOS);
        assertEquals(3, control.state().snapshot().playerCol());
        control.requestDirection(Direction.LEFT); control.pulse(GameController.STEP_NANOS * 2);
        assertEquals(2, control.state().snapshot().playerCol());
        assertEquals(3, control.state().snapshot().turns());
    }
    @Test void pausePreventsMovementAndResumeDoesNotCatchUpAccumulatedFrames() {
        GameController control = controller("#1    .#");
        control.requestDirection(Direction.RIGHT); control.pulse(0); control.setPaused(true);
        control.requestDirection(Direction.DOWN); control.pulse(10_000_000_000L);
        assertEquals(1, control.state().snapshot().turns());
        control.setPaused(false); control.pulse(10_000_000_001L);
        assertEquals(2, control.state().snapshot().turns());
        assertEquals(3, control.state().snapshot().playerCol());
    }
    @Test void hudPropertiesTrackTheEngineRatherThanASeparateScore() {
        GameController control = controller("#1. .#");
        AtomicInteger notifications = new AtomicInteger();
        control.state().scoreProperty().addListener((o, old, value) -> notifications.incrementAndGet());
        control.requestDirection(Direction.RIGHT); control.pulse(0);
        assertEquals("Mina", control.state().playerNameProperty().get());
        assertEquals(10, control.state().scoreProperty().get());
        assertEquals(5, control.state().livesProperty().get());
        assertEquals(1, notifications.get());
        assertEquals(control.state().snapshot().objectiveText(), control.state().objectiveProperty().get());
    }
    @Test void clickingConsumableDoesNotSpendAMovementTurnAndUpdatesObservableInventory() {
        GameController control = controller("#1H .#");
        control.requestDirection(Direction.RIGHT); control.pulse(0);
        assertEquals(1, control.state().inventory().size());
        assertThrows(UnsupportedOperationException.class, () -> control.state().inventory().clear());
        int before = control.state().snapshot().turns();
        control.useInventory(0);
        assertTrue(control.state().inventory().isEmpty());
        assertEquals(10, control.state().snapshot().freezeTurns());
        assertEquals(before, control.state().snapshot().turns());
    }
    @Test void aCompletedCardWaitsForExplicitContinueAndPreservesThePlayer() {
        GameController control = new GameController(GameEngine.ofLevels("Mina", 42, List.of(level("Une", "#1.#"), level("Deux", "#1.#"))));
        control.requestDirection(Direction.RIGHT); control.pulse(0);
        assertEquals("level-complete", control.state().statusProperty().get());
        assertTrue(control.state().isPaused());
        control.pulse(100_000_000_000L);
        assertEquals(1, control.state().snapshot().turns());
        control.nextLevel();
        assertEquals(1, control.state().snapshot().levelIndex());
        assertEquals(10, control.state().scoreProperty().get());
        assertFalse(control.state().isPaused());
        int turns = control.state().snapshot().turns();
        control.pulse(100_000_000_001L);
        assertEquals(turns, control.state().snapshot().turns(), "New cards wait for a direction");
        control.requestDirection(Direction.RIGHT); control.pulse(100_000_000_002L);
        assertEquals("won", control.state().statusProperty().get());
        assertEquals(20, control.state().scoreProperty().get());
    }
    @Test void replayResetsProgressAndRequiresAFreshDirection() {
        GameController control = controller("#1. .#");
        control.requestDirection(Direction.RIGHT); control.pulse(0);
        control.restart(); control.pulse(5_000_000_000L);
        assertEquals(0, control.state().scoreProperty().get());
        assertEquals(0, control.state().snapshot().turns());
        assertEquals(1, control.state().snapshot().playerCol());
        assertEquals("Mina", control.state().playerNameProperty().get());
        assertFalse(control.state().isPaused());
    }
    @Test void terminalStatesIgnoreDirectionPauseInventoryAndFurtherPulses() {
        GameController victory = controller("#1.#");
        victory.requestDirection(Direction.RIGHT); victory.pulse(0);
        var won = victory.state().snapshot();
        assertEquals("won", won.status());
        victory.requestDirection(Direction.LEFT); victory.togglePause();
        victory.useInventory(0); victory.sortInventory(); victory.nextLevel();
        victory.pulse(100_000_000_000L);
        assertEquals(won, victory.state().snapshot());
        assertTrue(victory.state().isPaused());

        GameController death = controller("#1*** .#");
        death.requestDirection(Direction.RIGHT);
        for (int index = 0; index < 6; index++) death.pulse(index * GameController.STEP_NANOS);
        var lost = death.state().snapshot();
        assertEquals("game-over", lost.status());
        death.requestDirection(Direction.LEFT); death.setPaused(false);
        death.useInventory(0); death.nextLevel(); death.pulse(100_000_000_000L);
        assertEquals(lost, death.state().snapshot());
        assertTrue(death.state().isPaused());
    }
}
