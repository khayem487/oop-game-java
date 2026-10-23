package game.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

/** Runtime composition of a player, positioned cell objects and living enemies. */
public final class Level {
    private final LevelDefinition definition;
    private final Player player;
    private final Cell[][] cells;
    private final List<Enemy> enemies = new ArrayList<>();
    private boolean trophyCollected;

    Level(LevelDefinition definition, Player player, int index, SplittableRandom random) {
        this.definition = definition;
        this.player = player;
        cells = new Cell[definition.rows().size()][definition.rows().getFirst().length()];
        int enemyNumber = 0;
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                Position position = new Position(row, col);
                char symbol = definition.rows().get(row).charAt(col);
                Cell cell = switch (symbol) {
                    case '#' -> new WallCell(position);
                    case 'D' -> new LockedDoorCell(position);
                    case '*' -> new TrapCell(position);
                    default -> new FloorCell(position);
                };
                cells[row][col] = cell;
                switch (symbol) {
                    case '1' -> player.enterLevel(position);
                    case '.' -> cell.placeCoin();
                    case 'W', 'H', 'T' -> cell.placeItem(new Item("map-" + index + "-" + row + "-" + col,
                            switch (symbol) { case 'W' -> Item.Type.WEAPON; case 'H' -> Item.Type.HOURGLASS; default -> Item.Type.TROPHY; }));
                    case 'R', 'G', 'C' -> {
                        enemyNumber++;
                        String id = "enemy-" + index + "-" + enemyNumber;
                        String suffix = " " + (index + 1) + "-" + enemyNumber;
                        Enemy enemy = switch (symbol) {
                            case 'R' -> new RandomEnemy(id, "Sentinelle" + suffix, position);
                            case 'G' -> new GhostEnemy(id, "Fantôme" + suffix, position,
                                    Direction.values()[random.nextInt(Direction.values().length)]);
                            default -> new HunterEnemy(id, "Chasseur" + suffix, position);
                        };
                        enemies.add(enemy);
                    }
                    default -> { /* Terrain alone carries no actor or item. */ }
                }
            }
        }
    }
    public Player getPlayer() { return player; }
    public Cell getCell(Position position) {
        if (position == null || position.row() < 0 || position.col() < 0
                || position.row() >= cells.length || position.col() >= cells[0].length)
            throw new IllegalArgumentException("Cellule hors de la carte.");
        return cells[position.row()][position.col()];
    }
    public List<Enemy> getEnemies() { return List.copyOf(enemies); }
    public Set<Position> getOccupiedEnemyCells() {
        Set<Position> occupied = new HashSet<>();
        for (Enemy enemy : enemies) occupied.add(enemy.getPosition());
        return Set.copyOf(occupied);
    }
    /** A toroidal level always has four directional neighbours, even on its edge. */
    public Optional<Position> neighbour(Position position, Direction direction) {
        int row = position.row() + direction.rowDelta(), col = position.col() + direction.colDelta();
        if (definition.toroidal())
            return Optional.of(new Position(Math.floorMod(row, cells.length), Math.floorMod(col, cells[0].length)));
        if (row < 0 || row >= cells.length || col < 0 || col >= cells[0].length) return Optional.empty();
        return Optional.of(new Position(row, col));
    }
    boolean canEnemyEnter(Enemy enemy, Position position) { return getCell(position).canEnter(enemy); }
    void removeEnemy(Enemy enemy) { enemies.remove(enemy); }
    void consumeTrap(Position position) { cells[position.row()][position.col()] = new FloorCell(position); }
    void resetEnemies() { enemies.forEach(Enemy::resetPosition); }
    void collectedTrophy() { trophyCollected = true; }
    boolean goalReached() {
        return switch (definition.goal()) {
            case COLLECT_COINS -> coinsRemaining() == 0;
            case DEFEAT_ENEMIES -> enemies.isEmpty();
            case FIND_TROPHY -> trophyCollected;
        };
    }
    int coinsRemaining() {
        int coins = 0;
        for (Cell[] row : cells) for (Cell cell : row) if (cell.hasCoin()) coins++;
        return coins;
    }
    List<String> terrain() {
        List<String> rows = new ArrayList<>(cells.length);
        for (Cell[] row : cells) {
            StringBuilder text = new StringBuilder(row.length);
            for (Cell cell : row) text.append(cell.symbol());
            rows.add(text.toString());
        }
        return List.copyOf(rows);
    }
    List<Position> emptyTeleportDestinations() {
        Set<Position> occupied = getOccupiedEnemyCells();
        List<Position> candidates = new ArrayList<>();
        for (Cell[] row : cells) for (Cell cell : row)
            if (cell.isEmptyFloor() && !occupied.contains(cell.getPosition()) && !player.getPosition().equals(cell.getPosition()))
                candidates.add(cell.getPosition());
        return candidates;
    }
    Direction horizontalDirectionToward(int from, int target) {
        if (!definition.toroidal()) return target < from ? Direction.LEFT : Direction.RIGHT;
        int right = Math.floorMod(target - from, cells[0].length);
        int left = Math.floorMod(from - target, cells[0].length);
        return left <= right ? Direction.LEFT : Direction.RIGHT;
    }
    Direction verticalDirectionToward(int from, int target) {
        if (!definition.toroidal()) return target < from ? Direction.UP : Direction.DOWN;
        int down = Math.floorMod(target - from, cells.length), up = Math.floorMod(from - target, cells.length);
        return up <= down ? Direction.UP : Direction.DOWN;
    }
}
