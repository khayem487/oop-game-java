package game.desktop;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Own JavaFX component integration probe: real dialogs and their event handlers, no OS input. */
public final class DesktopDialogProbe {
    private DesktopDialogProbe() { }

    public static void main(String[] args) throws Exception {
        Path destination = Path.of(args.length == 0 ? "target/desktop-dialog-qa" : args[0]);
        Files.createDirectories(destination);
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.startup(() -> {
            DesktopApplication app = new DesktopApplication();
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> result.completeExceptionally(error));
            try {
                Platform.setImplicitExit(false);
                verify(app, destination);
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                app.stop();
                new ArrayList<>(Window.getWindows()).forEach(Window::hide);
            }
        });
        try { result.get(40, TimeUnit.SECONDS); }
        finally { Platform.exit(); }
    }

    private static void verify(DesktopApplication app, Path destination) throws Exception {
        Stage stage = new Stage(); app.start(stage);
        Region menu = (Region) stage.getScene().getRoot();
        menu.resize(920, 700); menu.applyCss(); menu.layout();
        save(menu.snapshot(null, null), destination.resolve("jeu-javafx-menu-minimal.png"));
        for (String id : new String[]{"start-button", "menu-help-button", "menu-credits-button", "quit-button"}) {
            var bounds = stage.getScene().getRoot().lookup("#" + id).localToScene(stage.getScene().getRoot().lookup("#" + id).getBoundsInLocal());
            check(bounds.getMinY() >= 0 && bounds.getMaxY() <= 700 && bounds.getMinX() >= 0 && bounds.getMaxX() <= 920,
                    id + " reachable at the minimum content size");
        }

        modal(stage, () -> button(stage, "start-button").fire(), pane -> {
            TextField name = (TextField) pane.lookup("#player-name-input");
            TextField seed = (TextField) pane.lookup("#seed-input");
            Button start = action(pane, ButtonBar.ButtonData.OK_DONE);
            name.setText(" "); start.fire();
            check(pane.getScene().getWindow().isShowing(), "Blank name leaves the dialog open");
            check(((Label) pane.lookup(".error-label")).getText().contains("nom"), "Blank name explains the validation error");
            name.setText("Mina QA"); seed.setText("0"); start.fire();
            check(pane.getScene().getWindow().isShowing(), "Non-positive seed leaves the dialog open");
            check(((Label) pane.lookup(".error-label")).getText().contains("graine"), "Invalid seed explains the validation error");
            savePane(pane, destination.resolve("jeu-javafx-nouvelle-partie-validation.png"));
            seed.setText("2027"); start.fire();
        });
        GameController control = field(app, "controller", GameController.class);
        check(control != null && control.state().snapshot().playerName().equals("Mina QA"), "Valid name creates the actual campaign");
        check(control.state().snapshot().levelCount() >= 6, "Desktop loads the complete campaign");
        Scene gameScene = stage.getScene();
        control.setPaused(false);
        key(gameScene, KeyCode.RIGHT); control.pulse(0); control.pulse(GameController.STEP_NANOS);
        check(control.state().snapshot().score() == 10, "App keyboard and controller collect a real campaign coin");

        int turns = control.state().snapshot().turns();
        modal(stage, () -> button(stage, "help-button").fire(), pane -> {
            check(control.state().isPaused(), "Opening help pauses the live game");
            key(gameScene, KeyCode.DOWN); control.pulse(100_000_000_000L);
            check(control.state().snapshot().turns() == turns, "Modal help prevents movement in the game behind it");
            check(pane.getContent() instanceof ScrollPane, "Long help is scrollable");
            savePane(pane, destination.resolve("jeu-javafx-dialogue-aide.png"));
            action(pane, ButtonBar.ButtonData.OK_DONE).fire();
        });

        control.setPaused(true);
        modal(stage, () -> button(stage, "credits-button").fire(), pane -> {
            ScrollPane content = (ScrollPane) pane.getContent();
            String credits = ((Label) content.getContent()).getText();
            check(credits.contains("Khayem Ben Ghorbel") && credits.contains("assistance"), "Credits show attribution and assistance accurately");
            savePane(pane, destination.resolve("jeu-javafx-dialogue-credits.png"));
            action(pane, ButtonBar.ButtonData.OK_DONE).fire();
        });
        check(control.state().isPaused(), "Opening credits from pause preserves the existing pause");

        int score = control.state().snapshot().score();
        modal(stage, () -> key(gameScene, KeyCode.R), pane -> {
            check(control.state().isPaused(), "Replay confirmation pauses the game");
            action(pane, ButtonBar.ButtonData.CANCEL_CLOSE).fire();
        });
        check(control.state().snapshot().score() == score, "Cancelling replay preserves progress");
        modal(stage, () -> key(gameScene, KeyCode.R), pane -> action(pane, ButtonBar.ButtonData.OK_DONE).fire());
        check(control.state().snapshot().score() == 0 && control.state().snapshot().turns() == 0, "Confirming replay resets score and turns");
        control.pulse(200_000_000_000L);
        check(control.state().snapshot().turns() == 0, "Replayed game waits for a fresh direction");

        modal(stage, () -> button(stage, "menu-button").fire(), pane -> {
            savePane(pane, destination.resolve("jeu-javafx-confirmation-menu.png"));
            action(pane, ButtonBar.ButtonData.CANCEL_CLOSE).fire();
        });
        check(stage.getScene() == gameScene, "Cancelling menu leaves the game scene intact");
        modal(stage, () -> button(stage, "menu-button").fire(), pane -> action(pane, ButtonBar.ButtonData.OK_DONE).fire());
        check(stage.getScene().getRoot() instanceof MenuPresentation, "Confirming menu returns to the start screen");
        check(field(app, "controller", GameController.class) == null, "Hidden game is disconnected from the animation timer");
        int hiddenTurns = control.state().snapshot().turns();
        field(app, "timer", AnimationTimer.class).handle(300_000_000_000L);
        check(control.state().snapshot().turns() == hiddenTurns, "Animation timer cannot move a hidden game");
        modal(stage, () -> button(stage, "quit-button").fire(), pane -> action(pane, ButtonBar.ButtonData.CANCEL_CLOSE).fire());
        check(stage.isShowing(), "Cancelling quit keeps the app open");
        System.out.println("PASS JavaFX dialogs: minimum menu reachability, name/seed validation, campaign keyboard, paused-modal input, scrollable help, credits, cancelled and confirmed replay/menu, disconnected hidden game, cancelled quit; 5 actual PNGs.");
    }

    private static void modal(Stage owner, Runnable open, Consumer<DialogPane> interact) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            DialogPane pane = null;
            try {
                pane = Window.getWindows().stream().filter(w -> w != owner && w.isShowing())
                        .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                        .map(DialogPane.class::cast).findFirst().orElseThrow(() -> new AssertionError("Expected an actual JavaFX dialog"));
                interact.accept(pane);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                if (pane != null && pane.getScene() != null && pane.getScene().getWindow().isShowing()) {
                    action(pane, ButtonBar.ButtonData.CANCEL_CLOSE, ButtonBar.ButtonData.OK_DONE).fire();
                }
            }
        });
        open.run();
        if (failure.get() != null) throw new AssertionError("Dialog verification failed", failure.get());
    }
    private static Button action(DialogPane pane, ButtonBar.ButtonData... wanted) {
        for (ButtonBar.ButtonData kind : wanted) for (ButtonType type : pane.getButtonTypes()) {
            if (type.getButtonData() == kind) return (Button) pane.lookupButton(type);
        }
        throw new AssertionError("Expected dialog action");
    }
    private static Button button(Stage stage, String id) { return (Button) stage.getScene().getRoot().lookup("#" + id); }
    private static void key(Scene scene, KeyCode code) { Event.fireEvent(scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)); }
    private static <T> T field(Object instance, String name, Class<T> type) throws Exception {
        Field value = instance.getClass().getDeclaredField(name); value.setAccessible(true); return type.cast(value.get(instance));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void savePane(DialogPane pane, Path destination) {
        try { pane.applyCss(); pane.layout(); save(pane.snapshot(null, null), destination); }
        catch (Exception error) { throw new AssertionError("Could not snapshot the actual dialog", error); }
    }
    private static void save(WritableImage image, Path destination) throws Exception {
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        ImageIO.write(output, "png", destination.toFile());
    }
}
