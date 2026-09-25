package api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class UserApiHandler {

        private final HttpClient httpClient;

        private static final String SERVER_1_USERS = "http://localhost:8081/api/server/users";

        private static final String SERVER_2_USERS = "http://localhost:8082/api/server/users";

        public UserApiHandler() {

                httpClient = HttpClient.newBuilder()
                                .connectTimeout(
                                                Duration.ofSeconds(2))
                                .build();
        }

        // =========================================================
        // GET CURRENT USER
        // =========================================================

        public void handleMe(HttpExchange exchange)
                        throws IOException {

                if (!exchange.getRequestMethod()
                                .equalsIgnoreCase("GET")) {

                        sendJson(
                                        exchange,
                                        405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");

                        return;
                }

                String username = getQueryParameter(
                                exchange.getRequestURI()
                                                .getRawQuery(),
                                "username");

                if (username == null
                                || username.trim().isEmpty()) {

                        sendJson(
                                        exchange,
                                        400,
                                        "{\"success\":false,\"message\":\"Username is required\"}");

                        return;
                }

                username = username.trim();

                String response = "{"
                                + "\"success\":true,"
                                + "\"username\":\""
                                + escapeJson(username)
                                + "\""
                                + "}";

                sendJson(
                                exchange,
                                200,
                                response);
        }

        // =========================================================
        // GET ONLINE USERS FROM BOTH SERVERS
        // =========================================================

        public void handleOnlineUsers(
                        HttpExchange exchange)
                        throws IOException {

                if (!exchange.getRequestMethod()
                                .equalsIgnoreCase("GET")) {

                        sendJson(
                                        exchange,
                                        405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");

                        return;
                }

                try {

                        String server1Response = getServerUsers(
                                        SERVER_1_USERS);

                        String server2Response = getServerUsers(
                                        SERVER_2_USERS);

                        String users1 = extractUsersArray(
                                        server1Response);

                        String users2 = extractUsersArray(
                                        server2Response);

                        String combinedUsers;

                        if (users1.isEmpty()
                                        && users2.isEmpty()) {

                                combinedUsers = "";

                        } else if (users1.isEmpty()) {

                                combinedUsers = users2;

                        } else if (users2.isEmpty()) {

                                combinedUsers = users1;

                        } else {

                                combinedUsers = users1
                                                + ","
                                                + users2;
                        }

                        String response = "{"
                                        + "\"success\":true,"
                                        + "\"users\":["
                                        + combinedUsers
                                        + "]"
                                        + "}";

                        sendJson(
                                        exchange,
                                        200,
                                        response);

                } catch (Exception e) {

                        sendJson(
                                        exchange,
                                        500,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(
                                                                        e.getMessage())
                                                        + "\"}");
                }
        }

        // =========================================================
        // CALL MONITORING API
        // =========================================================

        private String getServerUsers(
                        String url)
                        throws IOException,
                        InterruptedException {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(
                                                URI.create(url))
                                .timeout(
                                                Duration.ofSeconds(3))
                                .GET()
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers
                                                .ofString());

                if (response.statusCode() != 200) {

                        return "{\"users\":[]}";
                }

                return response.body();
        }

        // =========================================================
        // EXTRACT USERS ARRAY
        // =========================================================

        private String extractUsersArray(
                        String json) {

                if (json == null
                                || json.isEmpty()) {

                        return "";
                }

                int start = json.indexOf(
                                "\"users\":[");

                if (start == -1) {

                        return "";
                }

                start = start
                                + "\"users\":[".length();

                int end = json.indexOf(
                                "]",
                                start);

                if (end == -1) {

                        return "";
                }

                return json.substring(
                                start,
                                end)
                                .trim();
        }

        // =========================================================
        // QUERY PARAMETER
        // =========================================================

        private String getQueryParameter(
                        String query,
                        String parameter) {

                if (query == null
                                || query.isEmpty()) {

                        return null;
                }

                String[] parameters = query.split("&");

                for (String param : parameters) {

                        String[] pair = param.split(
                                        "=",
                                        2);

                        if (pair.length == 2
                                        && pair[0]
                                                        .equals(parameter)) {

                                return pair[1];
                        }
                }

                return null;
        }

        // =========================================================
        // JSON ESCAPE
        // =========================================================

        private String escapeJson(
                        String value) {

                if (value == null) {

                        return "";
                }

                return value
                                .replace(
                                                "\\",
                                                "\\\\")
                                .replace(
                                                "\"",
                                                "\\\"");
        }

        // =========================================================
        // SEND JSON
        // =========================================================

        private void sendJson(
                        HttpExchange exchange,
                        int statusCode,
                        String response)
                        throws IOException {

                byte[] responseBytes = response.getBytes(
                                StandardCharsets.UTF_8);

                exchange.getResponseHeaders()
                                .set(
                                                "Content-Type",
                                                "application/json");

                exchange.getResponseHeaders()
                                .set(
                                                "Access-Control-Allow-Origin",
                                                "*");

                exchange.sendResponseHeaders(
                                statusCode,
                                responseBytes.length);

                exchange.getResponseBody()
                                .write(responseBytes);

                exchange.getResponseBody()
                                .close();
        }
}