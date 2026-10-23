package game.model;

import java.util.Comparator;

/** Type-safe inventory contract: a slot can contain an item or a skill. */
public interface InventoryElement extends Comparable<InventoryElement> {
    Comparator<InventoryElement> ORDER = Comparator
            .comparingInt((InventoryElement element) -> element.kind().equals("item") ? 0 : 1)
            .thenComparing(InventoryElement::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(InventoryElement::id);
    String id();
    String name();
    String kind();
    String description();
    @Override default int compareTo(InventoryElement other) { return ORDER.compare(this, other); }
    default InventoryEntry snapshot() { return new InventoryEntry(id(), name(), kind(), description()); }
}
