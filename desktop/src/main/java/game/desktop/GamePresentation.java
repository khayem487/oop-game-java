package game.desktop;

import game.model.Direction;
import game.model.GameSnapshot;
import game.model.InventoryEntry;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import java.util.Objects;

/** PAC presentation: JavaFX nodes and bindings only, commands go to the controller. */
public final class GamePresentation extends BorderPane {
    public interface Actions {
        void menu();
        void replay();
        void help();
        void credits();
    }
    private final GameController control;
    private final Actions actions;
    private final BoardCanvas board = new BoardCanvas();
    private final VBox overlay = new VBox(12);
    private final Label overlayTitle = new Label();
    private final Label overlayDescription = new Label();
    private final HBox overlayActions = new HBox(10);
    private final VBox inventoryBox = new VBox(7);
    private final Label extras = new Label();

    public GamePresentation(GameController control, Actions actions) {
        this.control = Objects.requireNonNull(control);
        this.actions = Objects.requireNonNull(actions);
        getStyleClass().add("game-root");
        setPadding(new Insets(26));
        setTop(header());
        setCenter(content());
        setBottom(footer());
        control.state().snapshotProperty().addListener((o, old, value) -> refresh(value));
        control.state().pausedProperty().addListener((o, old, value) -> refreshOverlay());
        refresh(control.state().snapshot());
    }

    private VBox header() {
        Label eyebrow = text("EXPLORATEUR · ÉDITION JAVA", "eyebrow");
        Label title = text("L’aventure continue.", "game-title");
        VBox titles = new VBox(4, eyebrow, title);
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        Button pause = button("Pause", "pause-button", control::togglePause);
        pause.textProperty().bind(Bindings.when(control.state().pausedProperty()).then("Reprendre").otherwise("Pause"));
        pause.disableProperty().bind(control.state().statusProperty().isNotEqualTo("playing"));
        HBox controls = new HBox(8, pause, button("Aide", "help-button", actions::help), button("Menu", "menu-button", actions::menu));
        controls.setAlignment(Pos.CENTER_RIGHT);
        HBox top = new HBox(20, titles, space, controls); top.setAlignment(Pos.CENTER_LEFT);
        Label level = text("", "level-line"); level.textProperty().bind(control.state().levelProperty());
        VBox header = new VBox(17, top, level); header.setPadding(new Insets(0, 0, 20, 0));
        return header;
    }

    private HBox content() {
        StackPane field = new StackPane(); field.getStyleClass().add("board-frame");
        field.setMinSize(280, 270); field.setPrefSize(720, 600);
        board.setManaged(false);
        board.widthProperty().bind(field.widthProperty()); board.heightProperty().bind(field.heightProperty());
        overlay.getStyleClass().add("board-overlay"); overlay.setAlignment(Pos.CENTER); overlay.setMaxWidth(360);
        overlayTitle.getStyleClass().add("overlay-title"); overlayTitle.setWrapText(true); overlayTitle.setAlignment(Pos.CENTER);
        overlayDescription.getStyleClass().add("muted");
        overlayDescription.setWrapText(true); overlayDescription.setAlignment(Pos.CENTER);
        overlayActions.setAlignment(Pos.CENTER);
        overlay.getChildren().addAll(overlayTitle, overlayDescription, overlayActions);
        field.getChildren().addAll(board, overlay); HBox.setHgrow(field, Priority.ALWAYS);
        VBox side = sidebar();
        ScrollPane sidebarScroll = new ScrollPane(side); sidebarScroll.getStyleClass().add("sidebar-scroll");
        sidebarScroll.setFitToWidth(true); sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setMinWidth(282); sidebarScroll.setPrefWidth(282); sidebarScroll.setMaxWidth(282);
        HBox content = new HBox(22, field, sidebarScroll); content.setMinHeight(0);
        return content;
    }

    private VBox sidebar() {
        Label player = text("", "player-name"); player.textProperty().bind(control.state().playerNameProperty());
        Label score = text("", "stat-number"); score.setId("score-value");
        score.textProperty().bind(control.state().scoreProperty().asString());
        Label lives = text("", "stat-number"); lives.setId("lives-value");
        lives.textProperty().bind(control.state().livesProperty().asString());
        HBox stats = new HBox(36, new VBox(3, text("SCORE", "stat-label"), score), new VBox(3, text("VIES", "stat-label"), lives));
        Label goal = text("", "objective"); goal.setId("objective-value"); goal.setWrapText(true);
        goal.textProperty().bind(control.state().objectiveProperty());
        extras.getStyleClass().add("muted"); extras.setWrapText(true);
        VBox playerPanel = new VBox(8, player, stats, new Separator(), text("TA MISSION", "eyebrow"), goal, extras);
        playerPanel.getStyleClass().add("side-panel");
        Label inventoryTitle = text("INVENTAIRE  ·  5 PLACES", "eyebrow");
        Button sort = button("Trier les éléments  ·  T", "sort-button", control::sortInventory);
        sort.setMaxWidth(Double.MAX_VALUE);
        sort.disableProperty().bind(control.state().pausedProperty().or(control.state().statusProperty().isNotEqualTo("playing")));
        VBox inventory = new VBox(8, inventoryTitle, inventoryBox, sort);
        inventory.getStyleClass().add("side-panel");
        Button credits = button("À propos du jeu", "credits-button", actions::credits);
        credits.getStyleClass().add("text-button");
        VBox side = new VBox(15, playerPanel, inventory, credits); side.setPadding(new Insets(0, 5, 6, 0));
        return side;
    }

    private VBox footer() {
        Label message = text("", "event-message"); message.setId("event-message"); message.setWrapText(true);
        message.textProperty().bind(control.state().messageProperty());
        Label keys = text("FLÈCHES / ZQSD  direction continue     ESPACE  pause     1–5  inventaire     T  trier", "keyboard-help");
        keys.setWrapText(true);
        VBox footer = new VBox(9, message, keys); footer.setPadding(new Insets(17, 0, 0, 0));
        footer.setMinHeight(88); return footer;
    }

    private void refresh(GameSnapshot snapshot) {
        board.setSnapshot(snapshot);
        extras.setText(snapshot.coinsRemaining() + " pièce(s) · " + snapshot.enemiesRemaining() + " ennemi(s)\n"
                + snapshot.turns() + " déplacement(s)" + (snapshot.toroidal() ? " · bords reliés" : "")
                + (snapshot.freezeTurns() > 0 ? "\nSablier : " + snapshot.freezeTurns() + " tours" : ""));
        inventoryBox.getChildren().clear();
        for (int index = 0; index < 5; index++) {
            final int slot = index;
            InventoryEntry entry = index < snapshot.inventory().size() ? snapshot.inventory().get(index) : null;
            String title = entry == null ? "Emplacement libre" : entry.name();
            String kind = entry == null ? "—" : "skill".equals(entry.kind()) ? "Compétence" : "Objet";
            Label key = text(Integer.toString(index + 1), "slot-key");
            Label name = text(title, "slot-name"); name.setWrapText(true);
            name.setMinWidth(0); name.setMaxWidth(178);
            VBox labels = new VBox(2, name, text(kind, "slot-kind"));
            labels.setMinWidth(0); labels.setPrefWidth(178); labels.setMaxWidth(178);
            HBox box = new HBox(10, key, labels); box.setAlignment(Pos.CENTER_LEFT);
            box.setPrefWidth(202); box.setMinWidth(202); box.setMaxWidth(202);
            Button button = new Button(); button.setGraphic(box); button.setMaxWidth(Double.MAX_VALUE);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.setMinHeight(48); button.setPrefHeight(48); button.setMaxHeight(48);
            button.setId("inventory-slot-" + (index + 1)); button.getStyleClass().add("inventory-slot");
            if (entry == null) { button.getStyleClass().add("empty-slot"); button.setDisable(true); }
            else {
                button.setTooltip(new Tooltip(entry.description()));
                button.setAccessibleText("Emplacement " + (index + 1) + " : " + title + ". " + entry.description());
                button.disableProperty().bind(control.state().pausedProperty().or(control.state().statusProperty().isNotEqualTo("playing")));
                button.setOnAction(event -> control.useInventory(slot));
            }
            inventoryBox.getChildren().add(button);
        }
        refreshOverlay();
    }

    private void refreshOverlay() {
        String status = control.state().statusProperty().get();
        boolean visible = !"playing".equals(status) || control.state().isPaused();
        overlay.setVisible(visible); overlay.setManaged(visible);
        overlayActions.getChildren().clear();
        if (!visible) return;
        switch (status) {
            case "level-complete" -> {
                overlayTitle.setText("Carte terminée !"); overlayDescription.setText("Tu conserves tes vies, ton score et ton inventaire pour la suite.");
                overlayActions.getChildren().add(primaryButton("Continuer →", "next-button", control::nextLevel));
            }
            case "won" -> {
                overlayTitle.setText("Bravo, " + control.state().playerNameProperty().get() + " !");
                overlayDescription.setText("Campagne accomplie. Merci d’avoir joué ! Ton score : " + control.state().scoreProperty().get() + ".");
                overlayActions.getChildren().addAll(primaryButton("Rejouer", "replay-button", actions::replay), button("Crédits", "won-credits-button", actions::credits));
            }
            case "game-over" -> {
                overlayTitle.setText("Fin de l’aventure"); overlayDescription.setText("Plus de vies, mais encore des idées. Repars de zéro pour une nouvelle tentative.");
                overlayActions.getChildren().addAll(primaryButton("Recommencer", "replay-button", actions::replay), button("Menu", "end-menu-button", actions::menu));
            }
            default -> {
                overlayTitle.setText("On fait une pause."); overlayDescription.setText("L’aventure attendra. Espace ou le bouton ci-dessous pour reprendre.");
                overlayActions.getChildren().add(primaryButton("Reprendre", "resume-button", () -> control.setPaused(false)));
            }
        }
    }

    public void installKeyboard(Scene scene) { scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKey); }
    private void handleKey(KeyEvent event) {
        KeyCode code = event.getCode();
        switch (code) {
            case UP, Z, W -> control.requestDirection(Direction.UP);
            case LEFT, Q, A -> control.requestDirection(Direction.LEFT);
            case DOWN, S -> control.requestDirection(Direction.DOWN);
            case RIGHT, D -> control.requestDirection(Direction.RIGHT);
            case SPACE, P, ESCAPE -> control.togglePause();
            case DIGIT1, NUMPAD1 -> useSlot(0);
            case DIGIT2, NUMPAD2 -> useSlot(1);
            case DIGIT3, NUMPAD3 -> useSlot(2);
            case DIGIT4, NUMPAD4 -> useSlot(3);
            case DIGIT5, NUMPAD5 -> useSlot(4);
            case T -> control.sortInventory();
            case N -> control.nextLevel();
            case R -> actions.replay();
            case F1 -> actions.help();
            default -> { return; }
        }
        event.consume();
    }
    private void useSlot(int index) {
        if (index < control.state().inventory().size()) control.useInventory(index);
    }
    static Label text(String text, String css) { Label label = new Label(text); label.getStyleClass().add(css); return label; }
    static Button button(String label, String id, Runnable action) {
        Button button = new Button(label); button.setId(id); button.setOnAction(event -> action.run()); return button;
    }
    static Button primaryButton(String label, String id, Runnable action) {
        Button button = button(label, id, action); button.getStyleClass().add("primary-button"); return button;
    }
}
