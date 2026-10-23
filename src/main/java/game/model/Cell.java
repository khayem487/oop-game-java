package game.model;

/** A positioned cell, independent from actors and optionally carrying one item. */
public abstract class Cell {
    private final Position position;
    private Item item;
    private boolean coin;
    protected Cell(Position position) { this.position = position; }
    public final Position getPosition() { return position; }
    public abstract boolean canEnter(Actor actor);
    protected abstract char groundSymbol();
    public boolean isTrap() { return false; }
    public final char symbol() {
        if (coin) return '.';
        if (item != null) return switch (item.type()) { case WEAPON -> 'W'; case HOURGLASS -> 'H'; case TROPHY -> 'T'; };
        return groundSymbol();
    }
    final boolean hasCoin() { return coin; }
    final void placeCoin() { coin = true; }
    final void removeCoin() { coin = false; }
    final Item item() { return item; }
    final void placeItem(Item item) { this.item = item; }
    final void removeItem() { item = null; }
    final boolean isEmptyFloor() { return groundSymbol() == ' ' && !coin && item == null; }
}
