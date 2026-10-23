package game.model;

/** Only a player with lockpicking can cross; this never unlocks it for enemies. */
public final class LockedDoorCell extends Cell {
    public LockedDoorCell(Position position) { super(position); }
    @Override public boolean canEnter(Actor actor) {
        return actor instanceof Player player && player.hasSkill(Skill.Type.LOCKPICK);
    }
    @Override protected char groundSymbol() { return 'D'; }
}
