package game.desktop;

import game.model.GameEngine;
import game.model.LevelDefinition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Explicit real JavaFX scene smoke test and PNG export, without native input automation. */
public final class DesktopSnapshotProbe {
    public static void main(String[] args) throws Exception {
        Path destination = Path.of(args.length == 0 ? "target/desktop-qa" : args[0]);
        Files.createDirectories(destination);
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.startup(() -> {
            try { verify(destination); result.complete(null); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        try { result.get(30, TimeUnit.SECONDS); }
        finally { Platform.exit(); }
    }
    private static void verify(Path destination) throws Exception {
        LevelDefinition sample = LevelDefinition.of("Le jardin d’essai", "Récupérer les pièces puis atteindre le trophée", List.of(
                "############", "#1H.. W T  #", "# ##  D ## #", "# R . * G  #", "#   C      #", "############"), false, LevelDefinition.Goal.FIND_TROPHY);
        GameController control = new GameController(GameEngine.ofLevels("Mina", 2027, List.of(sample)));
        int[] callbacks = {0};
        GamePresentation view = new GamePresentation(control, new GamePresentation.Actions() {
            public void menu() { callbacks[0]++; }
            public void replay() { control.restart(); }
            public void help() { callbacks[0]++; }
            public void credits() { callbacks[0]++; }
        });
        Scene scene = new Scene(view, 1200, 820);
        scene.getStylesheets().add(DesktopApplication.class.getResource("game.css").toExternalForm());
        view.installKeyboard(scene); layout(view, 1200, 820);
        key(scene, KeyCode.RIGHT); control.pulse(0);
        check(control.state().inventory().size() == 1, "Keyboard moves the actual engine and collects the hourglass");
        Button slot = (Button) view.lookup("#inventory-slot-1");
        check(!slot.isDisabled(), "Collected inventory slot enabled"); slot.fire();
        check(control.state().inventory().isEmpty() && control.state().snapshot().freezeTurns() == 10, "Inventory button activates the actual item");
        control.pulse(GameController.STEP_NANOS);
        check(((Label) view.lookup("#score-value")).getText().equals("10"), "Score label binding updates after collecting a coin");
        check(((Label) view.lookup("#lives-value")).getText().equals(Integer.toString(control.state().snapshot().lives())), "Life label binding matches engine");
        layout(view, 1200, 820); save(view.snapshot(null, null), destination.resolve("jeu-javafx-en-jeu.png"));
        key(scene, KeyCode.SPACE); check(control.state().isPaused(), "Keyboard pause works");
        check(view.lookup("#resume-button").isVisible(), "Pause overlay is rendered");
        layout(view, 1200, 820); save(view.snapshot(null, null), destination.resolve("jeu-javafx-pause.png"));
        ((Button) view.lookup("#resume-button")).fire(); check(!control.state().isPaused(), "Pause button resumes");
        ((Button) view.lookup("#help-button")).fire(); check(callbacks[0] == 1, "Help callback connected");
        layout(view, 920, 700); save(view.snapshot(null, null), destination.resolve("jeu-javafx-fenetre-minimale.png"));
        ScrollPane sidebar = (ScrollPane) view.lookup(".sidebar-scroll");
        sidebar.setVvalue(1); layout(view, 920, 700);
        var creditsBounds = view.lookup("#credits-button").localToScene(view.lookup("#credits-button").getBoundsInLocal());
        var viewport = sidebar.lookup(".viewport");
        var viewportBounds = viewport.localToScene(viewport.getBoundsInLocal());
        save(view.snapshot(null, null), destination.resolve("jeu-javafx-sidebar-bas.png"));
        check(creditsBounds.getMinY() >= viewportBounds.getMinY() && creditsBounds.getMaxY() <= viewportBounds.getMaxY(), "Sidebar bottom controls remain reachable by scrolling at minimum size: " + creditsBounds + " / " + viewportBounds);
        verifyTerminalStates(destination);
        int[] menuCalls = {0};
        MenuPresentation menu = new MenuPresentation(GameEngine.campaign("Explorateur", 2027).snapshot(), () -> menuCalls[0]++, () -> menuCalls[0]++, () -> menuCalls[0]++, () -> menuCalls[0]++);
        Scene menuScene = new Scene(menu, 1200, 820); menuScene.getStylesheets().add(DesktopApplication.class.getResource("game.css").toExternalForm());
        layout(menu, 1200, 820); save(menu.snapshot(null, null), destination.resolve("jeu-javafx-menu.png"));
        for (String id : List.of("start-button", "menu-help-button", "menu-credits-button", "quit-button")) ((Button) menu.lookup("#" + id)).fire();
        check(menuCalls[0] == 4, "All menu actions connected");
        System.out.println("PASS JavaFX: keyboard, actual item activation, HUD, pause, help, minimum-size sidebar scrolling, next card, victory, game-over/replay, all menu actions, 7 actual scene PNGs.");
    }
    private static void verifyTerminalStates(Path destination) throws Exception {
        LevelDefinition card = LevelDefinition.of("La dernière pièce", "Ramasser la pièce", List.of("####", "#1.#", "####"), false, LevelDefinition.Goal.COLLECT_COINS);
        GameController control = new GameController(GameEngine.ofLevels("Mina", 42, List.of(card, card)));
        int[] credits = {0};
        GamePresentation view = new GamePresentation(control, new GamePresentation.Actions() {
            public void menu() { }
            public void replay() { control.restart(); }
            public void help() { }
            public void credits() { credits[0]++; }
        });
        Scene scene = new Scene(view, 1200, 820); scene.getStylesheets().add(DesktopApplication.class.getResource("game.css").toExternalForm()); view.installKeyboard(scene);
        key(scene, KeyCode.RIGHT); control.pulse(0);
        check(view.lookup("#next-button") instanceof Button, "Completed card offers Continue");
        ((Button) view.lookup("#next-button")).fire();
        check(control.state().snapshot().levelIndex() == 1 && control.state().snapshot().score() == 10, "Continue calls actual engine and carries progress");
        key(scene, KeyCode.RIGHT); control.pulse(1);
        check("won".equals(control.state().snapshot().status()), "Last goal wins");
        var victory = control.state().snapshot();
        key(scene, KeyCode.LEFT); key(scene, KeyCode.SPACE); key(scene, KeyCode.N); key(scene, KeyCode.DIGIT1); control.pulse(100_000_000_000L);
        check(control.state().snapshot().equals(victory), "Victory ignores movement, unpause, next and item keys");
        ((Button) view.lookup("#won-credits-button")).fire(); check(credits[0] == 1, "Victory credits action connected");
        layout(view, 1200, 820); save(view.snapshot(null, null), destination.resolve("jeu-javafx-victoire.png"));
        ((Button) view.lookup("#replay-button")).fire(); check(control.state().snapshot().score() == 0, "Victory replay resets progress");

        LevelDefinition traps = LevelDefinition.of("Les trois pièges", "Ramasser la pièce", List.of("########", "#1*** .#", "########"), false, LevelDefinition.Goal.COLLECT_COINS);
        GameController death = new GameController(GameEngine.ofLevels("Mina", 42, List.of(traps)));
        GamePresentation deathView = new GamePresentation(death, new GamePresentation.Actions() {
            public void menu() { }
            public void replay() { death.restart(); }
            public void help() { }
            public void credits() { }
        });
        Scene deathScene = new Scene(deathView, 1200, 820); deathScene.getStylesheets().add(DesktopApplication.class.getResource("game.css").toExternalForm()); deathView.installKeyboard(deathScene);
        key(deathScene, KeyCode.RIGHT);
        for (int step = 0; step < 6; step++) death.pulse(step * GameController.STEP_NANOS);
        check("game-over".equals(death.state().snapshot().status()), "Trap deaths reach actual game over");
        var gameOver = death.state().snapshot();
        key(deathScene, KeyCode.LEFT); key(deathScene, KeyCode.SPACE); key(deathScene, KeyCode.N); death.pulse(100_000_000_000L);
        check(death.state().snapshot().equals(gameOver), "Game over ignores movement, unpause and next keys");
        layout(deathView, 1200, 820); save(deathView.snapshot(null, null), destination.resolve("jeu-javafx-fin-partie.png"));
        ((Button) deathView.lookup("#replay-button")).fire(); check(death.state().snapshot().lives() == 5 && "playing".equals(death.state().snapshot().status()), "Game-over replay resets lives");
    }
    private static void key(Scene scene, KeyCode code) {
        Event.fireEvent(scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
    private static void layout(Region root, double width, double height) { root.resize(width, height); root.applyCss(); root.layout(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void save(WritableImage image, Path path) throws Exception {
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        ImageIO.write(output, "png", path.toFile());
    }
}
