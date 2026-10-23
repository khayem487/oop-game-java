package game.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import game.model.Direction;
import game.model.GameEngine;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded, stateless HTTP transport around the shared Java campaign engine. */
public final class GameServer {
    static final int MAX_COMMANDS = 2048;
    private static final int MAX_BODY_BYTES = 4096;
    private static final long REPLAY_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
    private static final Set<String> FIELDS = Set.of("name", "seed", "history", "command");
    private static final Map<String, String> ASSETS = Map.of(
        "/", "text/html; charset=utf-8",
        "/style.css", "text/css; charset=utf-8",
        "/game.js", "text/javascript; charset=utf-8");

    private GameServer() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", args.length > 0 ? args[0] : "8080"));
        HttpServer server = start(port);
        System.out.println("OOP Game Java v2 ready at http://127.0.0.1:" + server.getAddress().getPort());
    }

    static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv().getOrDefault("HOST", "127.0.0.1"), port), 32);
        server.setExecutor(new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.CallerRunsPolicy()));
        server.createContext("/", GameServer::handle);
        server.start();
        return server;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (exchange.getRequestURI().getRawQuery() != null) {
                error(exchange, 400, "Les paramètres d’URL ne sont pas acceptés."); return;
            }
            if (path.equals("/api/health")) {
                if (!method.equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
                send(exchange, 200, "application/json; charset=utf-8",
                    "{\"status\":\"ok\",\"engine\":\"shared-java\",\"version\":2,\"maps\":6}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.equals("/api/game")) {
                if (!method.equals("POST")) { methodNotAllowed(exchange, "POST"); return; }
                if (!sameOrigin(exchange)) { error(exchange, 403, "Origine non autorisée."); return; }
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                if (type == null || !type.split(";", 2)[0].equalsIgnoreCase("application/x-www-form-urlencoded")) {
                    error(exchange, 415, "Format de commande non pris en charge."); return;
                }
                String size = exchange.getRequestHeaders().getFirst("Content-Length");
                if (size != null && (Long.parseLong(size) < 0 || Long.parseLong(size) > MAX_BODY_BYTES)) {
                    error(exchange, 413, "Commande trop volumineuse."); return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
                if (body.length > MAX_BODY_BYTES) { error(exchange, 413, "Commande trop volumineuse."); return; }
                Map<String, String> form = parseForm(new String(body, StandardCharsets.UTF_8));
                if (!FIELDS.containsAll(form.keySet())) throw new IllegalArgumentException("Champ inconnu.");
                String name = form.getOrDefault("name", "Explorateur").strip();
                if (!name.matches("[\\p{L}\\p{M}\\p{N} '\u2019-]{1,32}") || name.isBlank())
                    throw new IllegalArgumentException("Choisissez un nom de 1 à 32 caractères, sans symbole spécial.");
                String rawSeed = form.getOrDefault("seed", "1337");
                if (!rawSeed.matches("[0-9]{1,19}")) throw new IllegalArgumentException("Graine invalide.");
                long seed = Long.parseLong(rawSeed);
                if (seed < 1) throw new IllegalArgumentException("La graine doit être positive.");
                String history = form.getOrDefault("history", "");
                if (!history.matches("[ULDRN1-5S]{0," + MAX_COMMANDS + "}"))
                    throw new IllegalArgumentException("Historique invalide.");
                String command = form.getOrDefault("command", "STATE");
                Character action = commandAction(command);
                if (command.equals("RESTART")) history = "";
                if (action != null && history.length() == MAX_COMMANDS)
                    throw new IllegalArgumentException("Limite de 2 048 actions atteinte. Recommencez l’aventure.");
                long started = System.nanoTime();
                GameEngine game = GameEngine.campaign(name, seed);
                for (int index = 0; index < history.length(); index++) {
                    if (System.nanoTime() - started > REPLAY_BUDGET_NANOS)
                        throw new IllegalArgumentException("Cette sauvegarde demande trop de calcul. Recommencez l’aventure.");
                    apply(game, history.charAt(index));
                }
                if (action != null) { apply(game, action); history += action; }
                send(exchange, 200, "application/json; charset=utf-8",
                    Json.state(game.snapshot(), seed, history).getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (ASSETS.containsKey(path)) {
                if (!method.equals("GET") && !method.equals("HEAD")) { methodNotAllowed(exchange, "GET, HEAD"); return; }
                String resource = path.equals("/") ? "/public/index.html" : "/public" + path;
                try (InputStream input = GameServer.class.getResourceAsStream(resource)) {
                    if (input == null) { error(exchange, 404, "Page introuvable."); return; }
                    byte[] body = input.readAllBytes();
                    if (method.equals("HEAD")) {
                        headers(exchange);
                        exchange.getResponseHeaders().set("Content-Type", ASSETS.get(path));
                        exchange.sendResponseHeaders(200, -1);
                        exchange.close();
                    } else send(exchange, 200, ASSETS.get(path), body);
                }
                return;
            }
            error(exchange, 404, "Page introuvable.");
        } catch (NumberFormatException exception) {
            error(exchange, 400, "Nombre hors limites.");
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, exception.getMessage());
        } catch (Exception exception) {
            System.err.println("Request failed: " + exception.getClass().getSimpleName());
            error(exchange, 500, "Le jeu n’a pas pu répondre. Réessayez.");
        } finally { exchange.close(); }
    }

    private static Character commandAction(String command) {
        return switch (command) {
            case "STATE", "RESTART" -> null;
            case "UP" -> 'U'; case "LEFT" -> 'L'; case "DOWN" -> 'D'; case "RIGHT" -> 'R';
            case "NEXT" -> 'N'; case "SORT" -> 'S';
            case "USE_1" -> '1'; case "USE_2" -> '2'; case "USE_3" -> '3';
            case "USE_4" -> '4'; case "USE_5" -> '5';
            default -> throw new IllegalArgumentException("Commande inconnue.");
        };
    }

    private static void apply(GameEngine game, char action) {
        switch (action) {
            case 'U' -> game.move(Direction.UP); case 'L' -> game.move(Direction.LEFT);
            case 'D' -> game.move(Direction.DOWN); case 'R' -> game.move(Direction.RIGHT);
            case 'N' -> game.nextLevel(); case 'S' -> game.sortInventory();
            case '1', '2', '3', '4', '5' -> game.useInventory(action - '1');
            default -> throw new IllegalArgumentException("Action invalide.");
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&", -1)) {
            if (pair.isEmpty()) continue;
            String[] pieces = pair.split("=", 2);
            String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pieces.length == 2 ? pieces[1] : "", StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Champ dupliqué.");
        }
        return values;
    }

    private static boolean sameOrigin(HttpExchange exchange) {
        if ("cross-site".equals(exchange.getRequestHeaders().getFirst("Sec-Fetch-Site"))) return false;
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true; // Non-browser clients can run the deterministic API tests.
        try {
            URI uri = URI.create(origin);
            String host = exchange.getRequestHeaders().getFirst("Host");
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                && uri.getRawAuthority() != null && uri.getRawAuthority().equalsIgnoreCase(host)
                && uri.getRawUserInfo() == null && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        error(exchange, 405, "Méthode non autorisée.");
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, "application/json; charset=utf-8",
            ("{\"error\":" + Json.quote(message == null ? "Commande invalide." : message) + "}").getBytes(StandardCharsets.UTF_8));
    }

    private static void headers(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
        exchange.getResponseHeaders().set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        headers(exchange);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (var stream = exchange.getResponseBody()) { stream.write(body); }
    }
}
