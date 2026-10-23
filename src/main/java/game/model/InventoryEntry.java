package game.model;

/** Immutable description of an inventory slot; its index is supplied by the list. */
public record InventoryEntry(String id, String name, String kind, String description) {}
