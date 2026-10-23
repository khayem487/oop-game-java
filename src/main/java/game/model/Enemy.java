package game.model;

import java.util.SplittableRandom;

/** Enemy behaviour is selected through polymorphism, not a switch in the game loop. */
public abstract class Enemy extends Actor {
    private final String id;
    protected Enemy(String id, String name, int lives, Position spawn) {
        super(name, lives);
        this.id = id;
        enterLevel(spawn);
    }
    public final String getId() { return id; }
    public abstract String type();
    public int collisionDamage() { return 1; }
    protected abstract Position chooseDestination(Level level, Player player, SplittableRandom random);
    final void takeTurn(Level level, Player player, SplittableRandom random) {
        moveTo(chooseDestination(level, player, random));
    }
    final EnemySnapshot snapshot() {
        return new EnemySnapshot(id, type(), getName(), getPosition().row(), getPosition().col(), getLives());
    }
}
