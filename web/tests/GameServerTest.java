package game.web;

import com.sun.net.httpserver.HttpServer;
import game.model.Direction;
import game.model.GameEngine;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP tests: isolated replay, schema boundaries and browser asset policy. */
class GameServerTest {
    private static HttpServer server;
    private static URI base;
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @BeforeAll static void startServer() throws Exception {
        server = GameServer.start(0);
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterAll static void stopServer() {
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }

    @Test void returnsTheActualSharedModelAndCanonicalReplay() throws Exception {
        GameEngine expected = GameEngine.campaign("Élodie", 73);
        expected.move(Direction.RIGHT);
        expected.move(Direction.RIGHT);
        HttpResponse<String> response = post(form(Map.of("name", "Élodie", "seed", "00073", "history", "R", "command", "RIGHT")));
        assertEquals(200, response.statusCode());
        assertEquals(10, expected.snapshot().score(), "This route must collect an actual campaign coin");
        assertEquals(Json.state(expected.snapshot(), 73, "RR"), response.body());
        assertTrue(response.body().contains("\"version\":2"));
        assertTrue(response.body().contains("\"seed\":\"73\""));
        assertTrue(response.body().contains("\"inventory\":[]"));
        HttpResponse<String> reconstructed = post(form(Map.of("name", "Élodie", "seed", "73", "history", "RR", "command", "STATE")));
        assertEquals(response.body(), reconstructed.body(), "A new request reconstructs the complete game without a server session");
    }

    @Test void restartResetsActionsButPreservesChosenIdentityAndSeed() throws Exception {
        HttpResponse<String> response = post(form(Map.of("name", "Ada", "seed", "9223372036854775807", "history", "RR", "command", "RESTART")));
        assertEquals(200, response.statusCode());
        assertEquals(Json.state(GameEngine.campaign("Ada", Long.MAX_VALUE).snapshot(), Long.MAX_VALUE, ""), response.body());
    }

    @Test void sortIsReplayedAndEmptyInventorySlotsAreRejected() throws Exception {
        HttpResponse<String> response = post(form(Map.of("history", "S", "command", "SORT")));
        assertEquals(200, response.statusCode());
        GameEngine expected = GameEngine.campaign("Explorateur", 1337);
        expected.sortInventory(); expected.sortInventory();
        assertEquals(Json.state(expected.snapshot(), 1337, "SS"), response.body());
        assertTrue(response.body().contains("\"turns\":0"));
        assertEquals(400, post(form(Map.of("command", "USE_1"))).statusCode());
        assertEquals(400, post(form(Map.of("history", "1"))).statusCode(), "An impossible inventory action cannot be restored as valid progress");
    }

    @Test void concurrentVisitorsDoNotShareProgressOrNames() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> requests = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            String body = form(Map.of("name", "Visiteur " + index, "seed", String.valueOf(index + 1), "history", index % 2 == 0 ? "RR" : "", "command", "STATE"));
            requests.add(CLIENT.sendAsync(request(body), HttpResponse.BodyHandlers.ofString()));
        }
        for (int index = 0; index < requests.size(); index++) {
            HttpResponse<String> response = requests.get(index).join();
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"playerName\":\"Visiteur " + index + "\""));
            assertTrue(response.body().contains("\"score\":" + (index % 2 == 0 ? 10 : 0) + ","));
        }
    }

    @Test void rejectsMalformedOrUnauthorizedStateInsteadOfTrustingIt() throws Exception {
        List<String> invalid = List.of("score=9000", "lives=99", "grid=anything", "history=XX", "command=WIN", "name=%3Cscript%3E", "name=", "seed=0", "seed=-1", "seed=1.2", "seed=9223372036854775808", "seed=1&seed=2", "history=%ZZ", "history=" + "R".repeat(2049), "history=" + "R".repeat(2048) + "&command=RIGHT");
        for (String body : invalid) {
            HttpResponse<String> response = post(body);
            assertEquals(400, response.statusCode(), body.substring(0, Math.min(50, body.length())));
            assertTrue(response.body().startsWith("{\"error\":"));
        }
        assertEquals(413, post("history=" + "R".repeat(4097)).statusCode());
        HttpRequest json = HttpRequest.newBuilder(base.resolve("/api/game")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(415, CLIENT.send(json, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void rejectsCrossSiteBrowserCommandsButAcceptsSameOrigin() throws Exception {
        HttpRequest foreign = HttpRequest.newBuilder(base.resolve("/api/game")).header("Content-Type", "application/x-www-form-urlencoded").header("Origin", "https://example.invalid").POST(HttpRequest.BodyPublishers.ofString("")).build();
        assertEquals(403, CLIENT.send(foreign, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest same = HttpRequest.newBuilder(base.resolve("/api/game")).header("Content-Type", "application/x-www-form-urlencoded").header("Origin", base.toString()).POST(HttpRequest.BodyPublishers.ofString("")).build();
        assertEquals(200, CLIENT.send(same, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest fetchSite = HttpRequest.newBuilder(base.resolve("/api/game")).header("Content-Type", "application/x-www-form-urlencoded").header("Sec-Fetch-Site", "cross-site").POST(HttpRequest.BodyPublishers.ofString("")).build();
        assertEquals(403, CLIENT.send(fetchSite, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void servesOnlyPackagedAssetsWithStrictPoliciesAndMethods() throws Exception {
        for (String path : List.of("/", "/style.css", "/game.js")) {
            HttpResponse<String> response = get(path);
            assertEquals(200, response.statusCode(), path);
            assertTrue(response.headers().firstValue("Content-Security-Policy").orElseThrow().contains("script-src 'self'"));
            assertFalse(response.headers().firstValue("Content-Security-Policy").orElseThrow().contains("unsafe-inline"));
            assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        }
        assertEquals(200, get("/api/health").statusCode());
        assertTrue(get("/api/health").body().contains("\"maps\":6"));
        assertEquals(405, get("/api/game").statusCode());
        assertEquals(400, get("/api/game?score=1").statusCode());
        assertEquals(404, get("/src/app.pdf").statusCode());
        assertEquals(404, get("/.env").statusCode());
        assertEquals(404, get("/unknown").statusCode());
        HttpResponse<String> head = CLIENT.send(HttpRequest.newBuilder(base.resolve("/style.css")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, head.statusCode()); assertEquals("", head.body());
    }

    @Test void outputEncoderEscapesControlCharactersAndQuotes() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0000\"", Json.quote("a\"b\\c\n\t\u0000"));
    }

    private static String form(Map<String, String> values) {
        return String.join("&", values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).toList());
    }
    private static HttpRequest request(String body) {
        return HttpRequest.newBuilder(base.resolve("/api/game")).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private static HttpResponse<String> post(String body) throws Exception { return CLIENT.send(request(body), HttpResponse.BodyHandlers.ofString()); }
    private static HttpResponse<String> get(String path) throws Exception { return CLIENT.send(HttpRequest.newBuilder(base.resolve(path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
}
