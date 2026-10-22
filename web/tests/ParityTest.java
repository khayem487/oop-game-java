import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Compare the adapter to classes compiled directly from the original Git commit. */
public class ParityTest {
    private static final PrintStream OUTPUT = System.out;
    private static ReferenceLevel referenceLevel;
    private static ReferencePlayer referencePlayer;
    private static int checks;

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
        checks++;
    }

    private static ReferenceLevel originalWorld(int index) {
        var rows = GameSession.WORLDS.get(index).rows();
        char[][] grid = new char[rows.size()][];
        int startRow = -1, startCol = -1;
        for (int row = 0; row < rows.size(); row++) {
            grid[row] = rows.get(row).toCharArray();
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col] == '1') { startRow = row; startCol = col; grid[row][col] = ' '; }
            }
        }
        ReferenceLevel level = new ReferenceLevel(grid);
        level.placePlayer(startRow, startCol);
        level.saveStartPosition();
        return level;
    }

    private static String[] originalSnapshot() throws Exception {
        Field field = ReferenceLevel.class.getDeclaredField("grid");
        field.setAccessible(true);
        char[][] grid = (char[][]) field.get(referenceLevel);
        return Arrays.stream(grid).map(String::new).toArray(String[]::new);
    }

    private static void compare(GameSession game) throws Exception {
        check(game.player().getLives() == referencePlayer.getLives(), "lives parity");
        check(game.player().getScore() == referencePlayer.getScore(), "score parity");
        check(game.level().getCoinCounter() == referenceLevel.getCoinCounter(), "coin parity");
        check(Arrays.equals(game.level().snapshot(), originalSnapshot()), "grid/collision/reset parity");
    }

    private static void applyBoth(GameSession game, char command) throws Exception {
        if (command == 'N') {
            game.apply(command);
            referenceLevel = originalWorld(game.worldIndex());
        } else {
            ReferenceLevel.Direction direction = switch (command) {
                case 'U' -> ReferenceLevel.Direction.UP;
                case 'L' -> ReferenceLevel.Direction.LEFT;
                case 'D' -> ReferenceLevel.Direction.DOWN;
                case 'R' -> ReferenceLevel.Direction.RIGHT;
                default -> throw new AssertionError();
            };
            ReferenceLevel.MoveResult result = referenceLevel.movePlayer(direction);
            // The original Main's reward/damage branch, against unmodified original classes.
            if (result.getCoinsCollected() == 1) referencePlayer.addPoints(10);
            else if (result.getDammageDone() != 0) {
                referencePlayer.reduceLives(result.getDammageDone());
                referenceLevel.resetPlayerPosition();
            }
            game.apply(command);
        }
        compare(game);
    }

    private static void verifyReachableWorlds() {
        for (var world : GameSession.WORLDS) {
            var rows = world.rows();
            int width = rows.get(0).length();
            int start = -1;
            Set<Integer> coins = new HashSet<>();
            for (int row = 0; row < rows.size(); row++) {
                check(rows.get(row).length() == width, "rectangular world");
                for (int col = 0; col < width; col++) {
                    char tile = rows.get(row).charAt(col);
                    if (tile == '1') start = row * width + col;
                    if (tile == '.') coins.add(row * width + col);
                    if (row == 0 || row == rows.size() - 1 || col == 0 || col == width - 1) check(tile == '#', "closed world boundary");
                }
            }
            check(start >= 0 && !coins.isEmpty(), "playable start and coins");
            Set<Integer> visited = new HashSet<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.remove();
                for (int next : new int[]{cell-width, cell+width, cell-1, cell+1}) {
                    int row = next / width, col = next % width;
                    if (row < 0 || row >= rows.size() || col < 0 || col >= width) continue;
                    char tile = rows.get(row).charAt(col);
                    if (tile != '#' && tile != '*' && visited.add(next)) queue.add(next);
                }
            }
            check(visited.containsAll(coins), "all coins reachable without taking damage: " + world.name());
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        verifyReachableWorlds();
        referencePlayer = new ReferencePlayer("Explorateur");
        referenceLevel = originalWorld(0);
        GameSession game = new GameSession();
        compare(game);
        // Explicit coin, wall, damage and reset; a consumed trap does not damage twice.
        for (char command : "RRLDLDDDRDDDR".toCharArray()) applyBoth(game, command);
        check(game.player().getScore() == 10, "collected coin remains after reset");
        check(game.player().getLives() == 3, "single-use trap costs exactly two lives");
        var random = new Random(742L);
        char[] commands = {'U', 'L', 'D', 'R'};
        for (int step = 0; step < 4000 && !game.status().equals("game-over") && !game.status().equals("won"); step++) {
            applyBoth(game, game.status().equals("level-complete") ? 'N' : commands[random.nextInt(4)]);
        }
        String[] copy = game.level().snapshot();
        copy[0] = "tampered";
        check(!game.level().snapshot()[0].equals("tampered"), "snapshot is defensive");
        GameSession a = GameSession.replay("RR");
        GameSession b = GameSession.replay("");
        check(a.player().getScore() == 10 && b.player().getScore() == 0, "visitors do not share state");
        check(GameSession.replay("RR").json("RR").equals(a.json("RR")), "replay is deterministic");
        try { GameSession.replay("N"); throw new AssertionError("early next accepted"); } catch (IllegalArgumentException expected) { checks++; }
        try { GameSession.replay("X"); throw new AssertionError("invalid move accepted"); } catch (IllegalArgumentException expected) { checks++; }
        try { GameSession.replay("L".repeat(2049)); throw new AssertionError("oversized history accepted"); } catch (IllegalArgumentException expected) { checks++; }
        OUTPUT.println("PASS original-commit parity, reachable worlds, deterministic replay, bounds and visitor isolation (" + checks + " assertions)");
    }
}
