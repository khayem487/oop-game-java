package game.model;

import java.util.SplittableRandom;

/** One-life enemy attempting a seeded random cardinal direction each turn. */
public final class RandomEnemy extends Enemy {
    public RandomEnemy(String id, String name, Position spawn) { super(id, name, 1, spawn); }
    @Override public String type() { return "random"; }
    @Override protected Position chooseDestination(Level level, Player player, SplittableRandom random) {
        Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
        return level.neighbour(getPosition(), direction)
                .filter(position -> level.canEnemyEnter(this, position)).orElse(getPosition());
    }
}
