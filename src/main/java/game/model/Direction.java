package game.model;

/** The four cardinal moves, shared by every interface. */
public enum Direction {
    UP(-1, 0), LEFT(0, -1), DOWN(1, 0), RIGHT(0, 1);
    private final int rowDelta;
    private final int colDelta;
    Direction(int rowDelta, int colDelta) { this.rowDelta = rowDelta; this.colDelta = colDelta; }
    public int rowDelta() { return rowDelta; }
    public int colDelta() { return colDelta; }
}
