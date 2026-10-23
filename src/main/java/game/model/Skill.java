package game.model;

/** Reusable learned ability; unlike an item, using it never consumes the slot. */
public final class Skill implements InventoryElement {
    public enum Type { LOCKPICK, TELEPORT }
    private final Type type;
    Skill(Type type) { this.type = type; }
    public Type type() { return type; }
    @Override public String id() { return "skill-" + type.name().toLowerCase(java.util.Locale.ROOT); }
    @Override public String kind() { return "skill"; }
    @Override public String name() { return type == Type.LOCKPICK ? "Crochetage" : "Téléportation"; }
    @Override public String description() {
        return type == Type.LOCKPICK
                ? "Automatique : traverse les portes D, qui restent fermées aux ennemis. Gagné au-delà de 100 points."
                : "Utiliser avant un déplacement : rejoint une case vide au hasard. Réutilisable après 3 ennemis vaincus.";
    }
}
