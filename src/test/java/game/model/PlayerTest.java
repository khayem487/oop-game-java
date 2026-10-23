package game.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerTest {
    @Test void scoreNeverUnderflowsOrOverflows() {
        Player player = new Player("Alice");
        assertEquals(0, player.getScore());
        assertEquals(5, player.getLives());
        player.addPoints(-5);
        player.removePoints(-10);
        assertEquals(0, player.getScore());
        player.addPoints(20);
        player.removePoints(50);
        assertEquals(0, player.getScore());
        player.addPoints(Integer.MAX_VALUE);
        player.addPoints(1);
        assertEquals(Integer.MAX_VALUE, player.getScore());
        player.removePoints(Integer.MAX_VALUE);
        assertEquals(0, player.getScore());
    }

    @Test void equalityUsesOnlyCaseInsensitiveNameIncludingUnicode() {
        Player alice = new Player("Alice");
        Player other = new Player("ALICE");
        other.addPoints(90);
        assertEquals(alice, other);
        assertNotSame(alice, other);
        assertNotEquals(alice, "Alice");
        assertNotEquals(alice, null);
        assertNotEquals(alice, new Player("Bob"));
        // Turkish dotted/dotless I, Greek final sigma, and supplementary Deseret
        // all exercise Java's equalsIgnoreCase folding, without a locale expansion.
        for (String[] pair : List.of(new String[]{"İ", "i"}, new String[]{"ı", "I"},
                new String[]{"ς", "σ"}, new String[]{"\uD801\uDC00", "\uD801\uDC28"})) {
            Player first = new Player(pair[0]), second = new Player(pair[1]);
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertEquals(1, new HashSet<>(List.of(first, second)).size());
        }
    }

    @Test void generatedNamesCountNamedAndUnnamedPlayers() {
        int before = Player.getTotalPlayers();
        Player named = new Player(" Alice ");
        Player unnamed = new Player();
        assertEquals("Alice", named.getName());
        assertEquals("Joueur" + (before + 2), unnamed.getName());
        assertEquals(before + 2, Player.getTotalPlayers());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  ", "bad\nname", "bad\u0000name"})
    void rejectsInvalidNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Player(name));
    }

    @Test void nameLengthCountsUnicodeCodePoints() {
        assertEquals("😀".repeat(40), new Player("😀".repeat(40)).getName());
        assertThrows(IllegalArgumentException.class, () -> new Player("😀".repeat(41)));
    }

    @Test void scoreTextAgreesAtZeroOneAndSeveralPoints() {
        Player player = new Player("Alice");
        assertEquals("Alice : 0pt", player.toString());
        player.addPoints(1);
        assertEquals("Alice : 1pt", player.toString());
        player.addPoints(1);
        assertEquals("Alice : 2pts", player.toString());
    }

    @Test void inventoryIsTypedBoundedDefensiveAndSortedByTypeThenName() {
        Player player = new Player("Alice");
        Skill teleport = new Skill(Skill.Type.TELEPORT);
        Skill lockpick = new Skill(Skill.Type.LOCKPICK);
        Item hourglass = new Item("h", Item.Type.HOURGLASS);
        Item weapon = new Item("w", Item.Type.WEAPON);
        assertTrue(player.collect(teleport));
        assertTrue(player.collect(hourglass));
        assertTrue(player.collect(lockpick));
        assertTrue(player.collect(weapon));
        assertTrue(player.collect(new Item("w2", Item.Type.WEAPON)));
        assertFalse(player.collect(new Item("extra", Item.Type.HOURGLASS)));
        List<InventoryElement> beforeSort = player.inventory();
        assertThrows(UnsupportedOperationException.class, beforeSort::clear);
        player.sortInventory();
        assertEquals(List.of("Arme", "Arme", "Sablier", "Crochetage", "Téléportation"),
                player.inventory().stream().map(InventoryElement::name).toList());
        assertSame(teleport, beforeSort.getFirst());
        assertThrows(IllegalArgumentException.class, () -> player.entry(-1));
        assertThrows(IllegalArgumentException.class, () -> player.entry(5));
    }
}
