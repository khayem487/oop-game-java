package game.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable map template. Actors and inventory are instantiated afresh for each game. */
public final class LevelDefinition {
    public enum Goal { COLLECT_COINS, DEFEAT_ENEMIES, FIND_TROPHY }
    private static final String SYMBOLS = "# .*DWRGCHT1";
    private final String name;
    private final String objective;
    private final List<String> rows;
    private final boolean toroidal;
    private final Goal goal;

    private LevelDefinition(String name, String objective, List<String> rows, boolean toroidal, Goal goal) {
        this.name = validText(name, "Le nom de carte", 100);
        this.goal = Objects.requireNonNull(goal, "goal");
        this.objective = validText(objective, "L'objectif", 240);
        this.rows = List.copyOf(rows);
        this.toroidal = toroidal;
        validateMap();
    }

    public static LevelDefinition of(String name, String objective, List<String> rows, boolean toroidal, Goal goal) {
        return new LevelDefinition(name, objective, rows, toroidal, goal);
    }
    public String name() { return name; }
    public String objective() { return objective; }
    public List<String> rows() { return rows; }
    public boolean toroidal() { return toroidal; }
    public Goal goal() { return goal; }

    /**
     * Reads UTF-8 rectangular maps. Optional leading headers are
     * {@code ;name=...}, {@code ;objective=...}, {@code ;toroidal=true|false},
     * and {@code ;goal=coins|enemies|trophy}. Plain files default to coins and a torus.
     * Supported symbols: # wall, space floor, 1 player, . coin, * trap, D door,
     * R random enemy, G ghost, C hunter, W weapon, H hourglass, T quest trophy.
     */
    public static LevelDefinition fromFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.size(path) > 131_072) throw new IOException("Le fichier de carte dépasse 128 Kio.");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> rows = new ArrayList<>();
        String name = path.getFileName().toString();
        String objective = null;
        boolean toroidal = true;
        Goal goal = Goal.COLLECT_COINS;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index == 0 && line.startsWith("\uFEFF")) line = line.substring(1);
            if (rows.isEmpty() && line.isBlank()) continue;
            if (rows.isEmpty() && line.startsWith(";")) {
                int separator = line.indexOf('=');
                if (separator < 2) throw new IllegalArgumentException("En-tête de carte invalide : " + line);
                String key = line.substring(1, separator).strip().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).strip();
                switch (key) {
                    case "name" -> name = value;
                    case "objective" -> objective = value;
                    case "toroidal" -> {
                        if (!value.equals("true") && !value.equals("false"))
                            throw new IllegalArgumentException("toroidal doit être true ou false.");
                        toroidal = Boolean.parseBoolean(value);
                    }
                    case "goal" -> goal = switch (value.toLowerCase(Locale.ROOT)) {
                        case "coins", "collect_coins" -> Goal.COLLECT_COINS;
                        case "enemies", "defeat_enemies" -> Goal.DEFEAT_ENEMIES;
                        case "trophy", "find_trophy" -> Goal.FIND_TROPHY;
                        default -> throw new IllegalArgumentException("Objectif inconnu : " + value);
                    };
                    default -> throw new IllegalArgumentException("En-tête inconnu : " + key);
                }
            } else rows.add(line);
        }
        return of(name, objective == null ? defaultObjective(goal) : objective, rows, toroidal, goal);
    }

    private static String validText(String value, String field, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(field + " doit être renseigné sans caractères de contrôle.");
        return value.strip();
    }
    private void validateMap() {
        if (rows.isEmpty() || rows.getFirst().isEmpty()) throw new IllegalArgumentException("La carte est vide.");
        int width = rows.getFirst().length();
        if (rows.size() > 40 || width > 60 || rows.size() * width > 1200)
            throw new IllegalArgumentException("La carte dépasse 40 lignes, 60 colonnes ou 1200 cellules.");
        int players = 0, coins = 0, enemies = 0, trophies = 0;
        for (String row : rows) {
            if (row.length() != width) throw new IllegalArgumentException("La carte doit être rectangulaire.");
            for (char symbol : row.toCharArray()) {
                if (SYMBOLS.indexOf(symbol) < 0) throw new IllegalArgumentException("Symbole de carte inconnu : " + symbol);
                if (symbol == '1') players++;
                if (symbol == '.') coins++;
                if ("RGC".indexOf(symbol) >= 0) enemies++;
                if (symbol == 'T') trophies++;
            }
        }
        if (players != 1) throw new IllegalArgumentException("La carte doit contenir exactement un joueur 1.");
        if (goal == Goal.COLLECT_COINS && coins == 0) throw new IllegalArgumentException("L'objectif pièces requiert au moins une pièce.");
        if (goal == Goal.DEFEAT_ENEMIES && enemies == 0) throw new IllegalArgumentException("L'objectif ennemis requiert au moins un ennemi.");
        if (goal == Goal.FIND_TROPHY && trophies != 1) throw new IllegalArgumentException("L'objectif trophée requiert exactement un T.");
    }
    private static String defaultObjective(Goal goal) {
        return switch (goal) {
            case COLLECT_COINS -> "Ramasser toutes les pièces de la carte.";
            case DEFEAT_ENEMIES -> "Vaincre tous les ennemis de la carte.";
            case FIND_TROPHY -> "Ramasser le trophée T de la carte.";
        };
    }
}
