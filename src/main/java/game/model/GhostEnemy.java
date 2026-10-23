package game.model;

import java.util.SplittableRandom;

/** Keeps its heading through obstacles, turning toward an aligned player. */
public final class GhostEnemy extends Enemy {
    private Direction direction;
    public GhostEnemy(String id, String name, Position spawn, Direction direction) {
        super(id, name, 1, spawn);
        this.direction = direction;
    }
    @Override public String type() { return "ghost"; }
    @Override protected Position chooseDestination(Level level, Player player, SplittableRandom random) {
        Position from = getPosition(), target = player.getPosition();
        if (from.row() == target.row() && from.col() != target.col()) {
            direction = level.horizontalDirectionToward(from.col(), target.col());
        } else if (from.col() == target.col() && from.row() != target.row()) {
            direction = level.verticalDirectionToward(from.row(), target.row());
        }
        // A ghost deliberately bypasses Cell.canEnter, including walls, doors and traps.
        return level.neighbour(from, direction).orElse(from);
    }
}
