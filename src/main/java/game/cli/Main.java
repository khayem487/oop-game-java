package game.cli;

import game.model.Direction;
import game.model.EnemySnapshot;
import game.model.GameEngine;
import game.model.GameSnapshot;
import game.model.LevelDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Console presentation/controller for the same game model as JavaFX and the web. */
public final class Main {
    private Main() {}

    /** Starts either the bundled campaign or the level files supplied on the command line. */
    public static void main(String[] args) {
        int result = run(args, System.in, System.out, System.err);
        if (result != 0) System.exit(result);
    }

    static int run(String[] args, InputStream input, PrintStream out, PrintStream error) {
        if (args.length == 0 || List.of(args).contains("--help")) {
            usage(out);
            return 0;
        }
        try {
            String name = null;
            long seed = 20261022L;
            boolean campaign = false;
            List<LevelDefinition> levels = new ArrayList<>();
            for (String argument : args) {
                if (argument.equals("--campaign")) campaign = true;
                else if (argument.startsWith("--name=")) name = argument.substring(7);
                else if (argument.startsWith("--seed=")) seed = Long.parseLong(argument.substring(7));
                else if (argument.startsWith("--")) throw new IllegalArgumentException("Option inconnue : " + argument);
                else levels.add(LevelDefinition.fromFile(Path.of(argument)));
            }
            if (campaign && !levels.isEmpty()) throw new IllegalArgumentException("Choisissez --campaign ou des fichiers de niveaux.");
            if (!campaign && levels.isEmpty()) { usage(out); return 2; }
            Scanner scanner = new Scanner(input, StandardCharsets.UTF_8);
            if (name == null) {
                out.print("Nom du joueur : ");
                name = scanner.hasNextLine() ? scanner.nextLine().strip() : "Explorateur";
                if (name.isEmpty()) name = "Explorateur";
            }
            GameEngine game = campaign ? GameEngine.campaign(name, seed) : GameEngine.ofLevels(name, seed, levels);
            out.println("ZQSD : se déplacer | 1-5 : inventaire | tri : trier | aide | x : quitter");
            while (true) {
                GameSnapshot state = game.snapshot();
                print(state, out);
                if (state.status().equals("won")) {
                    out.println("AVENTURE TERMINÉE. Merci d'avoir joué !");
                    return 0;
                }
                if (state.status().equals("level-complete")) {
                    out.println("NIVEAU TERMINÉ. Passage au suivant.");
                    game.nextLevel();
                    continue;
                }
                if (state.status().equals("game-over")) {
                    out.print("GAME OVER. Recommencer de zéro ? [o/n] : ");
                    if (!scanner.hasNextLine()) return 0;
                    String answer = scanner.nextLine().strip().toLowerCase(java.util.Locale.ROOT);
                    while (!List.of("o", "oui", "y", "yes", "n", "non", "no").contains(answer)) {
                        out.print("Répondez o ou n : ");
                        if (!scanner.hasNextLine()) return 0;
                        answer = scanner.nextLine().strip().toLowerCase(java.util.Locale.ROOT);
                    }
                    if (answer.startsWith("n")) return 0;
                    game.restart();
                    continue;
                }
                out.print("> ");
                if (!scanner.hasNextLine()) return 0;
                String command = scanner.nextLine().strip().toLowerCase(java.util.Locale.ROOT);
                if (command.isEmpty()) continue;
                if (command.equals("x") || command.equals("quitter")) {
                    out.println("Merci d'avoir joué !");
                    return 0;
                }
                try {
                    switch (command) {
                        case "z", "haut" -> game.move(Direction.UP);
                        case "q", "gauche" -> game.move(Direction.LEFT);
                        case "s", "bas" -> game.move(Direction.DOWN);
                        case "d", "droite" -> game.move(Direction.RIGHT);
                        case "tri" -> game.sortInventory();
                        case "1", "2", "3", "4", "5" -> game.useInventory(Integer.parseInt(command) - 1);
                        case "aide" -> out.println("# mur · D porte · . pièce · * piège · R ennemi · G fantôme · C chasseur · W arme · H sablier · T trophée. Les armes et le crochetage agissent automatiquement ; activez sablier/téléportation avant un déplacement.");
                        default -> out.println("Commande inconnue. Tapez aide pour les commandes.");
                    }
                } catch (IllegalArgumentException rejected) { out.println(rejected.getMessage()); }
            }
        } catch (IOException | IllegalArgumentException failure) {
            error.println("Impossible de lancer le jeu : " + failure.getMessage());
            return 2;
        }
    }

    private static void usage(PrintStream out) {
        out.println("Usage : java -jar oop-game-2.0.0.jar [--name=Nom] [--seed=123] <niveau.txt> [autres-niveaux.txt...]");
        out.println("Ou    : java -jar oop-game-2.0.0.jar --campaign [--name=Nom] [--seed=123]");
    }

    private static void print(GameSnapshot state, PrintStream out) {
        out.printf("%nCARTE %d/%d - %s%s%n", state.levelIndex() + 1, state.levelCount(), state.levelName(), state.toroidal() ? " (tore)" : "");
        out.printf("%s · %d points · %d vies · %d ennemi(s) vaincu(s)%n", state.playerName(), state.score(), state.lives(), state.defeatedEnemies());
        out.println("Objectif : " + state.objectiveText());
        char[][] rendered = state.terrain().stream().map(String::toCharArray).toArray(char[][]::new);
        for (EnemySnapshot enemy : state.enemies()) {
            rendered[enemy.row()][enemy.col()] = switch (enemy.type()) { case "ghost" -> 'G'; case "hunter" -> 'C'; default -> 'R'; };
        }
        rendered[state.playerRow()][state.playerCol()] = '1';
        for (char[] row : rendered) out.println(new String(row));
        if (state.freezeTurns() > 0) out.println("Sablier : " + state.freezeTurns() + " déplacement(s) restant(s).");
        for (int index = 0; index < state.inventory().size(); index++) {
            var item = state.inventory().get(index);
            out.printf("%d. %s (%s)%n", index + 1, item.name(), item.kind().equals("skill") ? "compétence" : "objet");
        }
        for (String message : state.messages()) out.println(message);
    }
}
