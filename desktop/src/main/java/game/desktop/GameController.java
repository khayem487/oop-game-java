package game.desktop;

import game.model.Direction;
import game.model.GameEngine;
import java.util.Objects;

/** PAC control: coordinates input, elapsed time, the abstraction and observable presentation. */
public final class GameController {
    public static final long STEP_NANOS = 240_000_000L;
    private final GameEngine engine;
    private final GameState state = new GameState();
    private Direction direction;
    private long previousStep = Long.MIN_VALUE;

    public GameController(GameEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        refresh();
    }

    public GameState state() { return state; }
    public void requestDirection(Direction desired) {
        if (isPlaying() && !state.isPaused()) direction = Objects.requireNonNull(desired, "direction");
    }

    /** Called by AnimationTimer; no thread, wall clock, or game-rule duplication. */
    public void pulse(long now) {
        if (!isPlaying() || state.isPaused() || direction == null) return;
        if (previousStep == Long.MIN_VALUE || now - previousStep >= STEP_NANOS) {
            previousStep = now;
            engine.move(direction);
            refresh();
        }
    }

    public void setPaused(boolean paused) {
        state.setPaused(paused || !isPlaying());
        previousStep = Long.MIN_VALUE;
    }
    public void togglePause() { setPaused(!state.isPaused()); }
    public boolean isPlaying() { return "playing".equals(state.statusProperty().get()); }
    public void useInventory(int index) {
        if (!isPlaying() || state.isPaused()) return;
        engine.useInventory(index);
        refresh();
    }
    public void sortInventory() {
        if (!isPlaying() || state.isPaused()) return;
        engine.sortInventory();
        refresh();
    }
    public void restart() {
        engine.restart();
        direction = null;
        previousStep = Long.MIN_VALUE;
        state.setPaused(false);
        refresh();
    }
    public void nextLevel() {
        if (!"level-complete".equals(state.statusProperty().get())) return;
        engine.nextLevel();
        direction = null;
        previousStep = Long.MIN_VALUE;
        state.setPaused(false);
        refresh();
    }
    private void refresh() {
        state.update(engine.snapshot());
        if (!isPlaying()) state.setPaused(true);
    }
}
