package game.model;

/** Immutable public view of one living enemy. */
public record EnemySnapshot(String id, String type, String name, int row, int col, int lives) {}
