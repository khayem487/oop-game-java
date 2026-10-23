package game.desktop;

import game.model.GameSnapshot;
import game.model.InventoryEntry;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Observable presentation state. Rules and mutable game objects stay in the engine. */
public final class GameState {
    private final ReadOnlyObjectWrapper<GameSnapshot> snapshot = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper playerName = new ReadOnlyStringWrapper("");
    private final ReadOnlyIntegerWrapper score = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper lives = new ReadOnlyIntegerWrapper();
    private final ReadOnlyStringWrapper level = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper objective = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper paused = new ReadOnlyBooleanWrapper();
    private final ObservableList<InventoryEntry> inventory = FXCollections.observableArrayList();

    void update(GameSnapshot value) {
        playerName.set(value.playerName());
        score.set(value.score());
        lives.set(value.lives());
        level.set("CARTE " + (value.levelIndex() + 1) + " / " + value.levelCount() + " · " + value.levelName());
        objective.set(value.objectiveText());
        status.set(value.status());
        message.set(value.messages().isEmpty() ? "À toi de jouer : choisis une direction." : String.join("\n", value.messages()));
        inventory.setAll(value.inventory());
        snapshot.set(value);
    }

    void setPaused(boolean value) { paused.set(value); }
    public ReadOnlyObjectProperty<GameSnapshot> snapshotProperty() { return snapshot.getReadOnlyProperty(); }
    public GameSnapshot snapshot() { return snapshot.get(); }
    public ReadOnlyStringProperty playerNameProperty() { return playerName.getReadOnlyProperty(); }
    public ReadOnlyIntegerProperty scoreProperty() { return score.getReadOnlyProperty(); }
    public ReadOnlyIntegerProperty livesProperty() { return lives.getReadOnlyProperty(); }
    public ReadOnlyStringProperty levelProperty() { return level.getReadOnlyProperty(); }
    public ReadOnlyStringProperty objectiveProperty() { return objective.getReadOnlyProperty(); }
    public ReadOnlyStringProperty statusProperty() { return status.getReadOnlyProperty(); }
    public ReadOnlyStringProperty messageProperty() { return message.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty pausedProperty() { return paused.getReadOnlyProperty(); }
    public boolean isPaused() { return paused.get(); }
    public ObservableList<InventoryEntry> inventory() { return FXCollections.unmodifiableObservableList(inventory); }
}
