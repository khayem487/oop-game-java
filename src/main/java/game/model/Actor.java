package game.model;

import java.util.Objects;

/** Shared identity, position and life cycle of players and enemies. */
public abstract class Actor {
    private final String name;
    private Position position;
    private Position spawn;
    private int lives;

    protected Actor(String name, int lives) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Le nom ne peut pas être vide.");
        this.name = name;
        this.lives = lives;
    }

    public final String getName() { return name; }
    public final int getLives() { return lives; }
    public final Position getPosition() { return position; }
    public final Position getSpawn() { return spawn; }
    final void enterLevel(Position start) { spawn = Objects.requireNonNull(start); position = start; }
    final void moveTo(Position destination) { position = Objects.requireNonNull(destination); }
    final void resetPosition() { position = spawn; }
    final void takeDamage(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Dégâts négatifs.");
        lives = Math.max(0, lives - amount);
    }
}
