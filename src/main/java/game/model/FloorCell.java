package game.model;

/** Ordinary walkable floor. */
public final class FloorCell extends Cell {
    public FloorCell(Position position) { super(position); }
    @Override public boolean canEnter(Actor actor) { return true; }
    @Override protected char groundSymbol() { return ' '; }
}
