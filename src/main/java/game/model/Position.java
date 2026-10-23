package game.model;

/** Immutable cell coordinates, with value equality suitable for a set or map. */
public record Position(int row, int col) {}
