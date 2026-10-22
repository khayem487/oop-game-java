import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A small bounded HTTP adapter. Every request replays the original Java game. */
public final class GameServer {
    private static final int MAX_BODY_BYTES = 2300;
    private static final Map<String, String> ASSETS = Map.of(
        "/", "text/html; charset=utf-8",
        "/style.css", "text/css; charset=utf-8",
        "/game.js", "text/javascript; charset=utf-8"
    );

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", args.length > 0 ? args[0] : "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 64);
        server.setExecutor(new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), new ThreadPoolExecutor.CallerRunsPolicy()));
        server.createContext("/", GameServer::handle);
        server.start();
        System.out.println("OOP Game Java ready at http://127.0.0.1:" + port);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/api/health")) {
                if (!method.equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
                send(exchange, 200, "application/json; charset=utf-8", "{\"status\":\"ok\",\"engine\":\"original-java\",\"worlds\":3}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.equals("/api/game")) {
                if (!method.equals("POST")) { methodNotAllowed(exchange, "POST"); return; }
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                if (type == null || !type.split(";", 2)[0].equalsIgnoreCase("application/x-www-form-urlencoded")) {
                    error(exchange, 415, "Format de commande non pris en charge."); return;
                }
                byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) { error(exchange, 413, "Commande trop volumineuse."); return; }
                Map<String, String> form = parseForm(new String(bytes, StandardCharsets.UTF_8));
                if (!form.keySet().stream().allMatch(key -> key.equals("history") || key.equals("command"))) {
                    error(exchange, 400, "Champ inconnu."); return;
                }
                String history = form.getOrDefault("history", "");
                String command = form.getOrDefault("command", "STATE");
                if (!history.matches("[ULDRN]{0,2048}")) { error(exchange, 400, "Historique invalide."); return; }
                if (command.equals("RESTART")) history = "";
                GameSession game = GameSession.replay(history);
                if (!command.equals("STATE") && !command.equals("RESTART")) {
                    char move = switch (command) {
                        case "UP" -> 'U';
                        case "LEFT" -> 'L';
                        case "DOWN" -> 'D';
                        case "RIGHT" -> 'R';
                        case "NEXT" -> 'N';
                        default -> throw new IllegalArgumentException("Commande inconnue.");
                    };
                    if (history.length() == GameSession.MAX_COMMANDS) throw new IllegalArgumentException("Partie trop longue. Recommencez une aventure.");
                    game.apply(move);
                    history += move;
                }
                send(exchange, 200, "application/json; charset=utf-8", game.json(history).getBytes(StandardCharsets.UTF_8));
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
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, exception.getMessage());
        } catch (Exception exception) {
            System.err.println("Request failed: " + exception.getClass().getSimpleName());
            error(exchange, 500, "Le jeu n’a pas pu répondre. Réessayez.");
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] pieces = pair.split("=", 2);
            String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pieces.length == 2 ? pieces[1] : "", StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Champ dupliqué.");
        }
        return values;
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        error(exchange, 405, "Méthode non autorisée.");
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", ("{\"error\":" + Json.quote(message) + "}").getBytes(StandardCharsets.UTF_8));
    }

    private static void headers(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        headers(exchange);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (var stream = exchange.getResponseBody()) { stream.write(body); }
    }
}
