package game.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LevelValidationTest {
    @TempDir Path directory;

    @Test void readsUtf8BomHeadersAndPreservesMeaningfulSpaces() throws IOException {
        Path file = directory.resolve("niveau.txt");
        Files.writeString(file, "\uFEFF;name=Épreuve finale\n;objective=Trouver le trophée\n;toroidal=false\n;goal=trophy\n1 T \n####\n");
        LevelDefinition definition = LevelDefinition.fromFile(file);
        assertEquals("Épreuve finale", definition.name());
        assertEquals("Trouver le trophée", definition.objective());
        assertFalse(definition.toroidal());
        assertEquals(LevelDefinition.Goal.FIND_TROPHY, definition.goal());
        assertEquals(List.of("1 T ", "####"), definition.rows());
        assertThrows(UnsupportedOperationException.class, () -> definition.rows().clear());
    }

    @Test void plainMapDefaultsToToroidalCoinObjective() throws IOException {
        Path file = directory.resolve("arbitrary-name.map");
        Files.writeString(file, "1.\n  \n");
        LevelDefinition definition = LevelDefinition.fromFile(file);
        assertTrue(definition.toroidal());
        assertEquals("arbitrary-name.map", definition.name());
        assertEquals(LevelDefinition.Goal.COLLECT_COINS, definition.goal());
    }

    @Test void inputRowsCannotBeChangedAfterConstruction() {
        List<String> rows = new ArrayList<>(List.of("1."));
        LevelDefinition definition = LevelDefinition.of("Carte", "Pièces", rows, false, LevelDefinition.Goal.COLLECT_COINS);
        rows.set(0, "##");
        assertEquals(List.of("1."), definition.rows());
    }

    static Stream<Arguments> invalidMaps() {
        return Stream.of(
                Arguments.of(List.of(), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of(""), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of("1.", "#"), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of(" ."), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of("11."), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of("1?."), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of("1\t."), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of("1  "), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(List.of("1."), LevelDefinition.Goal.DEFEAT_ENEMIES),
                Arguments.of(List.of("1."), LevelDefinition.Goal.FIND_TROPHY),
                Arguments.of(List.of("1TT"), LevelDefinition.Goal.FIND_TROPHY),
                Arguments.of(List.of("1" + ".".repeat(60)), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(Stream.concat(Stream.of("1."), Stream.generate(() -> "  ").limit(40)).toList(), LevelDefinition.Goal.COLLECT_COINS),
                Arguments.of(Stream.concat(Stream.of("1" + ".".repeat(59)), Stream.generate(() -> " ".repeat(60)).limit(20)).toList(), LevelDefinition.Goal.COLLECT_COINS));
    }

    @ParameterizedTest @MethodSource("invalidMaps")
    void rejectsMalformedOrUnusableMaps(List<String> rows, LevelDefinition.Goal goal) {
        assertThrows(IllegalArgumentException.class,
                () -> LevelDefinition.of("Carte", "Objectif", rows, false, goal));
    }

    @ParameterizedTest @ValueSource(strings = {";garbage\n1.", ";unknown=yes\n1.",
            ";toroidal=maybe\n1.", ";goal=unknown\n1.", ";name=\n1.", ";objective=\n1."})
    void rejectsMalformedFileHeaders(String content) throws IOException {
        Path file = directory.resolve("invalid.map");
        Files.writeString(file, content);
        assertThrows(IllegalArgumentException.class, () -> LevelDefinition.fromFile(file));
    }

    @Test void missingAndOversizedFilesHaveCheckedIoFailures() throws IOException {
        assertThrows(IOException.class, () -> LevelDefinition.fromFile(directory.resolve("absent.map")));
        Path file = directory.resolve("large.map");
        Files.writeString(file, "x".repeat(131_073));
        assertThrows(IOException.class, () -> LevelDefinition.fromFile(file));
    }
}
