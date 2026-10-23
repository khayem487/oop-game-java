import java.util.List;

/** Main's score/lives/level loop, reusable without Scanner or console rendering. */
public final class GameSession {
    public record World(String name, String subtitle, List<String> rows) {}
    public static final int MAX_COMMANDS = 2048;
    public static final List<World> WORLDS = List.of(
        new World("Le jardin oublié", "Prendre ses repères", List.of(
            "###########",
            "#1 .#    .#",
            "# # # ### #",
            "# # .   # #",
            "# * ### . #",
            "#   .     #",
            "###########")),
        new World("Les galeries", "Chaque détour compte", List.of(
            "#############",
            "#1  # .     #",
            "# # # # ### #",
            "#.#   # . # #",
            "# ### ### # #",
            "#   *     #.#",
            "### ### # # #",
            "#.      #   #",
            "#############")),
        new World("Le dernier sanctuaire", "Garder son sang-froid", List.of(
            "###############",
            "#1  . #     . #",
            "# ### # ### # #",
            "#   #   # * # #",
            "### # ### # # #",
            "# . # .   #   #",
            "# # ##### ### #",
            "# *     .     #",
            "###############"))
    );

    private Player player;
    private Level level;
    private int worldIndex;
    private int steps;
    private String status;
    private String event;

    public GameSession() {
        player = new Player("Explorateur");
        loadWorld(0);
        event = "start";
    }

    private void loadWorld(int index) {
        World world = WORLDS.get(index);
        int width = world.rows().get(0).length();
        char[][] grid = new char[world.rows().size()][width];
        int startRow = -1, startCol = -1;
        for (int row = 0; row < grid.length; row++) {
            if (world.rows().get(row).length() != width) throw new IllegalStateException("Invalid world width");
            grid[row] = world.rows().get(row).toCharArray();
            for (int col = 0; col < width; col++) {
                if (grid[row][col] == '1') {
                    startRow = row;
                    startCol = col;
                    grid[row][col] = ' ';
                }
            }
        }
        if (startRow < 0) throw new IllegalStateException("Missing world start");
        level = new Level(grid);
        level.setConsoleOutput(false);
        level.placePlayer(startRow, startCol);
        level.saveStartPosition();
        worldIndex = index;
        status = "playing";
    }

    public static GameSession replay(String history) {
        if (history.length() > MAX_COMMANDS) throw new IllegalArgumentException("Partie trop longue. Recommencez une aventure.");
        GameSession session = new GameSession();
        for (int i = 0; i < history.length(); i++) session.apply(history.charAt(i));
        return session;
    }

    public void apply(char command) {
        if (command == 'N') {
            if (!status.equals("level-complete")) throw new IllegalArgumentException("Le niveau actuel doit être terminé.");
            loadWorld(worldIndex + 1);
            event = "next";
            return;
        }
        Level.Direction direction = switch (command) {
            case 'U' -> Level.Direction.UP;
            case 'L' -> Level.Direction.LEFT;
            case 'D' -> Level.Direction.DOWN;
            case 'R' -> Level.Direction.RIGHT;
            default -> throw new IllegalArgumentException("Commande inconnue.");
        };
        if (!status.equals("playing")) throw new IllegalArgumentException("Cette étape de la partie est terminée.");
        String before = String.join("\n", level.snapshot());
        Level.MoveResult result = level.movePlayer(direction);
        String after = String.join("\n", level.snapshot());
        boolean moved = !before.equals(after);
        if (moved) steps++;
        event = moved ? "move" : "wall";
        // These are the rules from the original console Main, using original models.
        if (result.getCoinsCollected() == 1) {
            player.addPoints(10);
            event = "coin";
        } else if (result.getDammageDone() != 0) {
            player.reduceLives(result.getDammageDone());
            level.resetPlayerPosition();
            event = "trap";
        }
        if (player.getLives() <= 0) status = "game-over";
        else if (level.getCoinCounter() == 0) {
            status = worldIndex == WORLDS.size() - 1 ? "won" : "level-complete";
        }
    }

    public String json(String history) {
        World world = WORLDS.get(worldIndex);
        return "{" +
            "\"history\":" + Json.quote(history) + "," +
            "\"world\":" + (worldIndex + 1) + "," +
            "\"worldCount\":" + WORLDS.size() + "," +
            "\"name\":" + Json.quote(world.name()) + "," +
            "\"subtitle\":" + Json.quote(world.subtitle()) + "," +
            "\"score\":" + player.getScore() + "," +
            "\"lives\":" + Math.max(0, player.getLives()) + "," +
            "\"coinsRemaining\":" + level.getCoinCounter() + "," +
            "\"steps\":" + steps + "," +
            "\"status\":" + Json.quote(status) + "," +
            "\"event\":" + Json.quote(event) + "," +
            "\"grid\":" + Json.array(level.snapshot()) + "}";
    }

    // Package-private access for parity/integration tests; browser receives snapshots only.
    Player player() { return player; }
    Level level() { return level; }
    String status() { return status; }
    int worldIndex() { return worldIndex; }
}
