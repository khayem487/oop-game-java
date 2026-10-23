package game.desktop;

import game.model.EnemySnapshot;
import game.model.GameSnapshot;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** Canvas presentation of an immutable snapshot; all collisions live in game.model. */
public final class BoardCanvas extends Canvas {
    private GameSnapshot snapshot;
    public BoardCanvas() {
        setWidth(640); setHeight(540);
        widthProperty().addListener((o, a, b) -> draw());
        heightProperty().addListener((o, a, b) -> draw());
        setAccessibleText("Plateau de jeu : flèches ou Z Q S D pour choisir une direction.");
    }
    public void setSnapshot(GameSnapshot snapshot) { this.snapshot = snapshot; draw(); }
    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        g.setFill(Color.web("#101c32")); g.fillRect(0, 0, getWidth(), getHeight());
        if (snapshot == null || snapshot.terrain().isEmpty()) return;
        int rows = snapshot.terrain().size(), cols = snapshot.terrain().getFirst().length();
        double size = Math.min((getWidth() - 36) / cols, (getHeight() - 36) / rows);
        double x0 = (getWidth() - cols * size) / 2, y0 = (getHeight() - rows * size) / 2;
        for (int row = 0; row < rows; row++) for (int col = 0; col < cols; col++) {
            double x = x0 + col * size, y = y0 + row * size;
            char tile = snapshot.terrain().get(row).charAt(col);
            g.setFill(Color.web((row + col) % 2 == 0 ? "#16253e" : "#192a45"));
            g.fillRoundRect(x + 1, y + 1, size - 2, size - 2, 8, 8);
            switch (tile) {
                case '#' -> {
                    g.setFill(Color.web("#354e70")); g.fillRoundRect(x + 2, y + 2, size - 4, size - 4, 8, 8);
                    g.setFill(Color.web("#4a668b")); g.fillRoundRect(x + 5, y + 5, size - 10, Math.max(2, size * .12), 4, 4);
                }
                case '.' -> {
                    g.setFill(Color.web("#f8c75c")); g.fillOval(x + size * .35, y + size * .35, size * .3, size * .3);
                    g.setFill(Color.web("#ffe5a5")); g.fillOval(x + size * .4, y + size * .38, size * .08, size * .08);
                }
                case '*' -> {
                    g.setFill(Color.web("#f58088"));
                    g.fillPolygon(new double[]{x + size * .17, x + size * .5, x + size * .83}, new double[]{y + size * .78, y + size * .2, y + size * .78}, 3);
                    glyph(g, "!", x, y, size, "#17243b");
                }
                case 'D' -> {
                    g.setFill(Color.web("#9d7fe6")); g.fillRoundRect(x + size * .18, y + size * .12, size * .64, size * .76, 8, 8);
                    glyph(g, "D", x, y, size, "#ffffff");
                }
                case 'W' -> glyph(g, "W", x, y, size, "#79e0bd");
                case 'H' -> glyph(g, "H", x, y, size, "#77c9fa");
                case 'T' -> glyph(g, "T", x, y, size, "#f8c75c");
                default -> { }
            }
        }
        for (EnemySnapshot enemy : snapshot.enemies()) {
            double x = x0 + enemy.col() * size, y = y0 + enemy.row() * size;
            String color = switch (enemy.type()) { case "ghost" -> "#c7a0f4"; case "hunter" -> "#fb8898"; default -> "#f3a36a"; };
            g.setFill(Color.web(color));
            g.fillRoundRect(x + size * .17, y + size * .16, size * .66, size * .69, size * .32, size * .32);
            glyph(g, switch (enemy.type()) { case "ghost" -> "G"; case "hunter" -> "C"; default -> "R"; }, x, y, size, "#16233b");
            if (enemy.lives() > 1) {
                g.setFill(Color.web("#ffffff"));
                for (int life = 0; life < enemy.lives(); life++) g.fillOval(x + size * .3 + life * size * .13, y + size * .79, size * .08, size * .08);
            }
        }
        double px = x0 + snapshot.playerCol() * size, py = y0 + snapshot.playerRow() * size;
        g.setFill(Color.web("#64dbcd")); g.fillOval(px + size * .1, py + size * .1, size * .8, size * .8);
        g.setFill(Color.web("#103b43"));
        g.fillOval(px + size * .3, py + size * .33, size * .1, size * .13);
        g.fillOval(px + size * .6, py + size * .33, size * .1, size * .13);
        g.setStroke(Color.web("#103b43")); g.setLineWidth(Math.max(1.5, size * .045));
        g.strokeArc(px + size * .32, py + size * .4, size * .36, size * .28, 195, 150, javafx.scene.shape.ArcType.OPEN);
    }
    private void glyph(GraphicsContext g, String text, double x, double y, double size, String color) {
        g.setFill(Color.web(color)); g.setFont(Font.font("Segoe UI", FontWeight.BOLD, size * .45));
        g.setTextAlign(TextAlignment.CENTER); g.fillText(text, x + size / 2, y + size * .67);
    }
}
