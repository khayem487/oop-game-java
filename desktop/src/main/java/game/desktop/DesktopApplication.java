package game.desktop;

import game.model.GameEngine;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Desktop entry point, navigation and native JavaFX dialogs. */
public final class DesktopApplication extends Application {
    private Stage stage;
    private GameController controller;
    private String previousName = "Explorateur";
    private long previousSeed = 2027L;
    private final AnimationTimer timer = new AnimationTimer() {
        @Override public void handle(long now) { if (controller != null) controller.pulse(now); }
    };

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Explorateur · Jeu Java");
        stage.setMinWidth(920); stage.setMinHeight(700);
        stage.setOnCloseRequest(event -> { event.consume(); requestQuit(); });
        stage.focusedProperty().addListener((o, wasFocused, focused) -> {
            if (!focused && controller != null && controller.isPlaying()) controller.setPaused(true);
        });
        showMenu(); stage.show();
    }

    private void showMenu() {
        timer.stop(); controller = null;
        setScene(new MenuPresentation(GameEngine.campaign("Explorateur", previousSeed).snapshot(),
                this::requestNewGame, this::showHelp, this::showCredits, this::requestQuit), null);
    }

    private void requestNewGame() {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.initOwner(stage); dialog.setTitle("Ton aventure");
        dialog.setHeaderText("Comment t’appelles-tu ?");
        ButtonType start = new ButtonType("C’est parti", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(start, new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE));
        TextField name = new TextField(previousName); name.setId("player-name-input"); name.setPromptText("Ton nom (32 caractères maximum)");
        TextField seed = new TextField(Long.toString(previousSeed)); seed.setId("seed-input"); seed.setPromptText("2027");
        Label hint = GamePresentation.text("La graine reproduit les mêmes choix aléatoires. Change-la pour varier les rencontres.", "muted"); hint.setWrapText(true);
        Label error = new Label(); error.getStyleClass().add("error-label"); error.setWrapText(true);
        VBox fields = new VBox(9, new Label("NOM DU JOUEUR"), name, new Label("GRAINE DE L’AVENTURE"), seed, hint, error);
        fields.setPadding(new Insets(10, 0, 0, 0)); fields.setPrefWidth(390);
        dialog.getDialogPane().setContent(fields); styleDialog(dialog.getDialogPane());
        dialog.getDialogPane().lookupButton(start).addEventFilter(ActionEvent.ACTION, event -> {
            if (name.getText().strip().isBlank() || name.getText().strip().length() > 32 || name.getText().strip().codePoints().anyMatch(Character::isISOControl)) {
                error.setText("Choisis un nom de 1 à 32 caractères, sans caractère de contrôle."); event.consume(); return;
            }
            try { if (Long.parseLong(seed.getText().strip()) <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException exception) { error.setText("La graine doit être un entier entre 1 et " + Long.MAX_VALUE + "."); event.consume(); }
        });
        dialog.setOnShown(event -> { name.requestFocus(); name.selectAll(); });
        if (dialog.showAndWait().filter(start::equals).isPresent()) {
            previousName = name.getText().strip(); previousSeed = Long.parseLong(seed.getText().strip());
            try { startGame(GameEngine.campaign(previousName, previousSeed)); }
            catch (IllegalArgumentException exception) { info("Impossible de commencer", exception.getMessage()); }
        }
    }

    private void startGame(GameEngine engine) {
        controller = new GameController(engine);
        GamePresentation view = new GamePresentation(controller, new GamePresentation.Actions() {
            @Override public void menu() { requestMenu(); }
            @Override public void replay() { requestReplay(); }
            @Override public void help() { showHelp(); }
            @Override public void credits() { showCredits(); }
        });
        setScene(view, view); timer.start();
    }

    private void setScene(javafx.scene.Parent root, GamePresentation game) {
        Scene scene = new Scene(root, 1200, 820); scene.getStylesheets().add(stylesheet());
        if (game != null) game.installKeyboard(scene);
        stage.setScene(scene);
    }

    private void requestMenu() {
        withPause(() -> {
            if (confirm("Revenir au menu ?", "La partie en cours sera abandonnée. Tu pourras recommencer avec le même nom et la même graine.", "Revenir au menu")) showMenu();
        });
    }
    private void requestReplay() {
        if (controller == null) return;
        withPause(() -> {
            if (confirm("Recommencer de zéro ?", "Vies, score, cartes et inventaire seront réinitialisés. La graine sera conservée.", "Recommencer")) controller.restart();
        });
    }
    private void requestQuit() {
        withPause(() -> {
            if (confirm("Quitter l’aventure ?", "Merci d’avoir joué. La partie de bureau n’est pas sauvegardée à la fermeture.", "Quitter")) { timer.stop(); Platform.exit(); }
        });
    }
    private void showHelp() {
        withPause(() -> info("Comment jouer", "Choisis une direction avec les flèches ou Z Q S D. Le personnage continue toutes les 240 ms ; espace, P ou Échap met le jeu en pause. La perte de focus met aussi le jeu en pause.\n\n"
                + "Chaque pièce rapporte 10 points. Un piège (*) coûte 2 vies puis disparaît. Une perte de vies renvoie le joueur et les ennemis à leur départ. Les cartes aux bords reliés permettent de passer d’un côté à l’autre.\n\n"
                + "R : ennemi aléatoire · G : fantôme qui traverse les obstacles · C : chasseur qui cherche le chemin le plus court.\n\n"
                + "W : arme, consommée automatiquement lors d’une collision pour infliger un dégât sans perdre de vie. H : sablier, à activer pour immobiliser les ennemis pendant 10 déplacements. T : trophée à récupérer sur certaines cartes.\n\n"
                + "Au-delà de 100 points, la compétence de crochetage permet de traverser D. Après 3 ennemis vaincus, la téléportation permet de rejoindre une case vide. Ces gains exigent une place libre dans l’inventaire (5 maximum).\n\n"
                + "Clique un élément ou utilise 1–5 pour l’activer. Les objets sont consommés, les compétences restent. Certaines fonctions comme l’arme et le crochetage sont automatiques. T trie les éléments. N passe à la carte suivante après une réussite ; R propose de tout recommencer. Lis l’objectif : pièces, ennemis ou trophée."));
    }
    private void showCredits() {
        withPause(() -> info("Crédits · Explorateur", "Projet de programmation orientée objet de Khayem Ben Ghorbel.\n\n"
                + "Jeu construit à partir du sujet pédagogique Introduction à la programmation objet (50 pages, mondes 1 à 6). Les cartes de campagne illustrent ces mécanismes ; ce sont des cartes, pas les mondes du cours.\n\n"
                + "Moteur Java partagé par les éditions console, JavaFX et web. Interface JavaFX : Canvas, propriétés observables et architecture Présentation–Abstraction–Contrôle.\n\n"
                + "Complément et adaptation réalisés avec une assistance de programmation. Visuels dessinés dans le code, sans téléchargement ni service externe.\n\nMerci d’avoir joué !"));
    }
    private void withPause(Runnable action) {
        GameController current = controller;
        boolean resume = current != null && current.isPlaying() && !current.state().isPaused();
        if (current != null) current.setPaused(true);
        action.run();
        if (resume && controller == current && current.isPlaying()) current.setPaused(false);
    }
    private boolean confirm(String title, String message, String choice) {
        ButtonType yes = new ButtonType(choice, ButtonBar.ButtonData.OK_DONE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message, yes, new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.initOwner(stage); dialog.setTitle(title); dialog.setHeaderText(title); styleDialog(dialog.getDialogPane());
        return dialog.showAndWait().filter(yes::equals).isPresent();
    }
    private void info(String title, String message) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION); dialog.initOwner(stage); dialog.setTitle(title); dialog.setHeaderText(title);
        Label body = new Label(message); body.setWrapText(true); body.setMaxWidth(560);
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true); scroll.setMaxHeight(520); scroll.setPrefWidth(580);
        dialog.getDialogPane().setContent(scroll); styleDialog(dialog.getDialogPane()); dialog.showAndWait();
    }
    private void styleDialog(DialogPane pane) { pane.getStylesheets().add(stylesheet()); pane.getStyleClass().add("game-dialog"); }
    private String stylesheet() { return DesktopApplication.class.getResource("game.css").toExternalForm(); }
    @Override public void stop() { timer.stop(); }
}
