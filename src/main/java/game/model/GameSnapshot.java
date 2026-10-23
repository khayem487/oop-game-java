package game.model;

import java.util.List;

/** A defensive, immutable snapshot. Terrain excludes actors, which may overlap. */
public record GameSnapshot(
        String playerName, int score, int lives, int defeatedEnemies,
        int levelIndex, int levelCount, int turns,
        String levelName, String objectiveText, String status, boolean toroidal,
        int playerRow, int playerCol, List<String> terrain,
        List<EnemySnapshot> enemies, List<InventoryEntry> inventory,
        int coinsRemaining, int enemiesRemaining, int freezeTurns, List<String> messages) {
    public GameSnapshot {
        terrain = List.copyOf(terrain);
        enemies = List.copyOf(enemies);
        inventory = List.copyOf(inventory);
        messages = List.copyOf(messages);
    }
}
