package game.model;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/** Three-life hunter using breadth-first search on the actual walkable cell graph. */
public final class HunterEnemy extends Enemy {
    public HunterEnemy(String id, String name, Position spawn) { super(id, name, 3, spawn); }
    @Override public String type() { return "hunter"; }
    @Override public int collisionDamage() { return 2; }
    @Override protected Position chooseDestination(Level level, Player player, SplittableRandom random) {
        Position start = getPosition(), target = player.getPosition();
        if (start.equals(target)) return start;
        ArrayDeque<Position> queue = new ArrayDeque<>();
        Map<Position, Position> firstStep = new HashMap<>();
        queue.add(start);
        firstStep.put(start, start);
        while (!queue.isEmpty()) {
            Position current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                Position next = level.neighbour(current, direction).orElse(null);
                if (next == null || firstStep.containsKey(next) || !level.canEnemyEnter(this, next)) continue;
                Position step = current.equals(start) ? next : firstStep.get(current);
                firstStep.put(next, step);
                if (next.equals(target)) return step;
                queue.addLast(next);
            }
        }
        return start;
    }
}
