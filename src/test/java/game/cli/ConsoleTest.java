package game.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleTest {
    @TempDir Path directory;

    @Test void noArgumentPrintsUsageAndDoesNotStartAnInvisibleCampaign() {
        Result result = execute(new String[]{}, "");
        assertEquals(0, result.code());
        assertTrue(result.output().contains("Usage"));
        assertFalse(result.output().contains("CARTE"));
    }

    @Test void missingFileProducesHelpfulFailure() {
        Result result = execute(new String[]{directory.resolve("absent.txt").toString()}, "");
        assertEquals(2, result.code());
        assertTrue(result.error().contains("Impossible de lancer"));
    }

    @Test void realFilesChainWhileKeepingPlayerScoreAndName() throws Exception {
        Path first = directory.resolve("premier.txt"), second = directory.resolve("second.txt");
        Files.writeString(first, "#####\n#1. #\n#####\n");
        Files.writeString(second, "#####\n#1. #\n#####\n");
        Result result = execute(new String[]{"--name=Alice", first.toString(), second.toString()}, "d\nd\n");
        assertEquals(0, result.code(), result.error());
        assertTrue(result.output().contains("NIVEAU TERMINÉ"));
        assertTrue(result.output().contains("Alice · 20 points · 5 vies"), result.output());
        assertTrue(result.output().contains("AVENTURE TERMINÉE"));
    }

    @Test void emptyAndUnknownCommandsCanBeFollowedByAProperQuit() {
        Result result = execute(new String[]{"--campaign", "--name=Bob"}, "\n???\naide\nx\n");
        assertEquals(0, result.code(), result.error());
        assertTrue(result.output().contains("Commande inconnue"));
        assertTrue(result.output().contains("Merci d'avoir joué"));
    }

    private static Result execute(String[] args, String input) {
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        int code = Main.run(args, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(output, true, StandardCharsets.UTF_8), new PrintStream(error, true, StandardCharsets.UTF_8));
        return new Result(code, output.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
    }
    private record Result(int code, String output, String error) {}
}
