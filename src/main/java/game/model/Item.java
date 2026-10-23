package game.model;

/** Collectible item. Weapons are consumed automatically on an enemy collision. */
public final class Item implements InventoryElement {
    public enum Type { WEAPON, HOURGLASS, TROPHY }
    private final String id;
    private final Type type;
    Item(String id, Type type) { this.id = id; this.type = type; }
    public Type type() { return type; }
    @Override public String id() { return id; }
    @Override public String kind() { return "item"; }
    @Override public String name() {
        return switch (type) { case WEAPON -> "Arme"; case HOURGLASS -> "Sablier"; case TROPHY -> "Trophée"; };
    }
    @Override public String description() {
        return switch (type) {
            case WEAPON -> "Automatique au contact : blesse un ennemi, protège le joueur, puis disparaît.";
            case HOURGLASS -> "Utiliser avant un déplacement : immobilise les ennemis pendant 10 tours.";
            case TROPHY -> "Objet de quête consommé au ramassage : termine la carte, sans occuper l'inventaire.";
        };
    }
}
