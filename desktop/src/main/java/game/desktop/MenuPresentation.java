package game.desktop;

import game.model.GameSnapshot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/** Start screen, kept separate so it can be laid out and checked without opening a window. */
public final class MenuPresentation extends BorderPane {
    public MenuPresentation(GameSnapshot preview, Runnable start, Runnable help, Runnable credits, Runnable quit) {
        getStyleClass().addAll("game-root", "menu-root"); setPadding(new Insets(50));
        Label tag = GamePresentation.text("LE JEU OOP · VERSION BUREAU", "eyebrow");
        Label title = GamePresentation.text("Petites cases.\nGrande aventure.", "menu-title");
        Label introduction = GamePresentation.text("Récolte, esquive, explore. Un moteur Java, une campagne et un inventaire pour changer la donne.", "menu-description");
        introduction.setWrapText(true); introduction.setMaxWidth(470);
        Button play = GamePresentation.primaryButton("Commencer l’aventure  →", "start-button", start);
        play.getStyleClass().add("start-button");
        HBox minor = new HBox(10, GamePresentation.button("Comment jouer", "menu-help-button", help), GamePresentation.button("Crédits", "menu-credits-button", credits));
        Button exit = GamePresentation.button("Quitter le jeu", "quit-button", quit); exit.getStyleClass().add("text-button");
        Label details = GamePresentation.text(preview.levelCount() + " cartes · 5 vies · des ennemis, des objets et des compétences\nMécanismes des mondes pédagogiques 1 à 6.", "menu-details");
        details.setWrapText(true);
        VBox hero = new VBox(23, tag, title, introduction, play, minor, details, exit); hero.setAlignment(Pos.CENTER_LEFT); hero.setMaxWidth(510);
        BoardCanvas previewCanvas = new BoardCanvas(); previewCanvas.setSnapshot(preview); previewCanvas.setManaged(false);
        StackPane previewFrame = new StackPane(previewCanvas); previewFrame.getStyleClass().add("board-frame");
        previewCanvas.widthProperty().bind(previewFrame.widthProperty()); previewCanvas.heightProperty().bind(previewFrame.heightProperty());
        previewFrame.setMinSize(260, 300); previewFrame.setMaxHeight(540); HBox.setHgrow(previewFrame, Priority.ALWAYS);
        HBox columns = new HBox(40, hero, previewFrame); columns.setAlignment(Pos.CENTER); setCenter(columns);
        Label footer = GamePresentation.text("100 % Java · JavaFX Canvas · même moteur que les versions console et web", "keyboard-help");
        BorderPane.setMargin(footer, new Insets(24, 0, 0, 0)); setBottom(footer);
    }
}
