package game.model;

/** Collision code delegates to the cell instead of checking character literals. */
public final class WallCell extends Cell {
    public WallCell(Position position) { super(position); }
    @Override public boolean canEnter(Actor actor) { return false; }
    @Override protected char groundSymbol() { return '#'; }
}
