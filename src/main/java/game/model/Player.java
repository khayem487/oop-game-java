package game.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Player with immutable name, safe nonnegative score and a five-slot inventory. */
public final class Player extends Actor {
    public static final int INVENTORY_CAPACITY = 5;
    private static final AtomicInteger TOTAL_PLAYERS = new AtomicInteger();
    private int score;
    private int defeatedEnemies;
    private final List<InventoryElement> inventory = new ArrayList<>();

    /** Creates a player named JoueurN, counting all previously created players. */
    public Player() { this(null, TOTAL_PLAYERS.incrementAndGet()); }
    /** Creates a named player, initially with zero points and five lives. */
    public Player(String name) { this(validateName(name), TOTAL_PLAYERS.incrementAndGet()); }
    private Player(String name, int number) { super(name == null ? "Joueur" + number : name, 5); }
    private static String validateName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Le nom ne peut pas être vide.");
        if (name.codePointCount(0, name.length()) > 40) throw new IllegalArgumentException("Le nom dépasse 40 caractères.");
        if (name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Le nom contient un caractère de contrôle.");
        return name.strip();
    }
    public static int getTotalPlayers() { return TOTAL_PLAYERS.get(); }
    public int getScore() { return score; }
    public int getDefeatedEnemies() { return defeatedEnemies; }
    public void addPoints(int points) { if (points > 0) score = (int) Math.min(Integer.MAX_VALUE, (long) score + points); }
    public void removePoints(int points) { if (points > 0) score = Math.max(0, score - points); }
    void defeatedEnemy() { defeatedEnemies++; }
    boolean collect(InventoryElement element) {
        if (inventory.size() >= INVENTORY_CAPACITY) return false;
        inventory.add(element);
        return true;
    }
    boolean hasSkill(Skill.Type type) {
        return inventory.stream().anyMatch(element -> element instanceof Skill skill && skill.type() == type);
    }
    boolean hasTrophy() {
        return inventory.stream().anyMatch(element -> element instanceof Item item && item.type() == Item.Type.TROPHY);
    }
    Item takeWeapon() {
        for (int index = 0; index < inventory.size(); index++) {
            if (inventory.get(index) instanceof Item item && item.type() == Item.Type.WEAPON) {
                inventory.remove(index);
                return item;
            }
        }
        return null;
    }
    List<InventoryElement> inventory() { return List.copyOf(inventory); }
    InventoryElement entry(int index) {
        if (index < 0 || index >= inventory.size()) throw new IllegalArgumentException("Ce numéro d'inventaire n'existe pas.");
        return inventory.get(index);
    }
    void removeEntry(int index) { inventory.remove(index); }
    void sortInventory() { inventory.sort(InventoryElement.ORDER); }
    @Override public String toString() { return getName() + " : " + score + (score > 1 ? "pts" : "pt"); }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof Player player && getName().equalsIgnoreCase(player.getName());
    }
    @Override public int hashCode() {
        // String.equalsIgnoreCase compares Unicode simple upper/lower case folds.
        // Locale-based String.toLowerCase can expand İ and break that contract.
        return getName().codePoints().map(point -> Character.toLowerCase(Character.toUpperCase(point)))
                .reduce(0, (hash, point) -> 31 * hash + point);
    }
}
