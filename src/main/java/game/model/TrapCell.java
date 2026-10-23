package game.model;

/** A one-use trap: walkable for the player, avoided by ordinary enemies. */
public final class TrapCell extends Cell {
    public TrapCell(Position position) { super(position); }
    @Override public boolean canEnter(Actor actor) { return actor instanceof Player; }
    @Override protected char groundSymbol() { return '*'; }
    @Override public boolean isTrap() { return true; }
}
