package game.web;

import game.model.EnemySnapshot;
import game.model.GameSnapshot;
import game.model.InventoryEntry;
import java.util.List;

/** Small output-only JSON adapter. No deserialization of game objects is exposed. */
final class Json {
    private Json() {}

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029')
                        out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    static String strings(List<String> strings) {
        return "[" + String.join(",", strings.stream().map(Json::quote).toList()) + "]";
    }

    static String state(GameSnapshot state, long seed, String history) {
        StringBuilder out = new StringBuilder("{\"version\":2");
        field(out, "name", quote(state.playerName()));
        field(out, "playerName", quote(state.playerName()));
        // A string preserves the exact seed when the browser saves and replays a game.
        field(out, "seed", quote(Long.toString(seed)));
        field(out, "history", quote(history));
        field(out, "score", Integer.toString(state.score()));
        field(out, "lives", Integer.toString(state.lives()));
        field(out, "defeatedEnemies", Integer.toString(state.defeatedEnemies()));
        field(out, "levelIndex", Integer.toString(state.levelIndex()));
        field(out, "levelCount", Integer.toString(state.levelCount()));
        field(out, "world", Integer.toString(state.levelIndex() + 1));
        field(out, "worldCount", Integer.toString(state.levelCount()));
        field(out, "turns", Integer.toString(state.turns()));
        field(out, "steps", Integer.toString(state.turns()));
        field(out, "levelName", quote(state.levelName()));
        field(out, "objectiveText", quote(state.objectiveText()));
        field(out, "status", quote(state.status()));
        field(out, "toroidal", Boolean.toString(state.toroidal()));
        field(out, "playerRow", Integer.toString(state.playerRow()));
        field(out, "playerCol", Integer.toString(state.playerCol()));
        field(out, "terrain", strings(state.terrain()));
        field(out, "coinsRemaining", Integer.toString(state.coinsRemaining()));
        field(out, "enemiesRemaining", Integer.toString(state.enemiesRemaining()));
        field(out, "freezeTurns", Integer.toString(state.freezeTurns()));
        field(out, "messages", strings(state.messages()));
        String enemies = String.join(",", state.enemies().stream().map(Json::enemy).toList());
        field(out, "enemies", "[" + enemies + "]");
        String inventory = String.join(",", state.inventory().stream().map(Json::entry).toList());
        field(out, "inventory", "[" + inventory + "]");
        return out.append('}').toString();
    }

    private static String enemy(EnemySnapshot enemy) {
        return "{\"id\":" + quote(enemy.id()) + ",\"type\":" + quote(enemy.type())
            + ",\"name\":" + quote(enemy.name()) + ",\"row\":" + enemy.row()
            + ",\"col\":" + enemy.col() + ",\"lives\":" + enemy.lives() + "}";
    }

    private static String entry(InventoryEntry entry) {
        return "{\"id\":" + quote(entry.id()) + ",\"name\":" + quote(entry.name())
            + ",\"kind\":" + quote(entry.kind()) + ",\"description\":" + quote(entry.description()) + "}";
    }

    private static void field(StringBuilder out, String key, String encodedValue) {
        out.append(',').append(quote(key)).append(':').append(encodedValue);
    }
}
