package api;

import com.sun.net.httpserver.HttpExchange;

import service.UserService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserApiHandler {

        private final HttpClient httpClient;

        private static final String SERVER_1_USERS = "http://localhost:8081/api/server/users";

        private static final String SERVER_2_USERS = "http://localhost:8082/api/server/users";

        private final UserService userService;

        public UserApiHandler() {

                httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(2))
                                .build();

                userService = new UserService();
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
                                exchange.getRequestURI().getRawQuery(),
                                "username");

                if (username == null || username.trim().isEmpty()) {

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

                sendJson(exchange, 200, response);
        }

        // =========================================================
        // GET ONLINE USERS FROM BOTH SERVERS
        // =========================================================

        public void handleOnlineUsers(HttpExchange exchange)
                        throws IOException {

                if (!exchange.getRequestMethod()
                                .equalsIgnoreCase("GET")) {

                        sendJson(
                                        exchange,
                                        405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");

                        return;
                }

                System.out.println(
                                "[API] Fetching online users from Server 1 and Server 2...");

                String users1 = getServerUsers(SERVER_1_USERS);

                String users2 = getServerUsers(SERVER_2_USERS);

                String combinedUsers;

                if (users1.isEmpty() && users2.isEmpty()) {

                        combinedUsers = "";

                } else if (users1.isEmpty()) {

                        combinedUsers = users2;

                } else if (users2.isEmpty()) {

                        combinedUsers = users1;

                } else {

                        combinedUsers = users1 + "," + users2;
                }

                String response = "{"
                                + "\"success\":true,"
                                + "\"users\":["
                                + combinedUsers
                                + "]"
                                + "}";

                System.out.println(
                                "[API] Online users response: "
                                                + response);

                sendJson(
                                exchange,
                                200,
                                response);
        }

        // =========================================================
        // SEARCH REGISTERED USERS
        // =========================================================

        public void handleSearchUsers(HttpExchange exchange)
                        throws IOException {

                if (!exchange.getRequestMethod()
                                .equalsIgnoreCase("GET")) {

                        sendJson(
                                        exchange,
                                        405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");

                        return;
                }

                String searchText = getQueryParameter(
                                exchange.getRequestURI().getRawQuery(),
                                "username");

                if (searchText == null || searchText.trim().isEmpty()) {

                        sendJson(
                                        exchange,
                                        400,
                                        "{\"success\":false,\"message\":\"Search text is required\"}");

                        return;
                }

                searchText = searchText.trim();

                try {

                        List<String> usernames = userService.searchUsers(searchText);

                        /*
                         * Get currently online users from both servers.
                         * This allows the frontend to display:
                         *
                         * Online
                         * Offline
                         */

                        String users1 = getServerUsers(SERVER_1_USERS);
                        String users2 = getServerUsers(SERVER_2_USERS);

                        Set<String> onlineUsers = new HashSet<>();

                        extractOnlineUsernames(users1, onlineUsers);
                        extractOnlineUsernames(users2, onlineUsers);

                        StringBuilder usersJson = new StringBuilder();

                        usersJson.append("[");

                        boolean first = true;

                        for (String username : usernames) {

                                if (!first) {
                                        usersJson.append(",");
                                }

                                boolean online = onlineUsers.contains(username);

                                usersJson.append("{");

                                usersJson.append("\"username\":\"")
                                                .append(escapeJson(username))
                                                .append("\",");

                                usersJson.append("\"online\":")
                                                .append(online);

                                usersJson.append("}");

                                first = false;
                        }

                        usersJson.append("]");

                        String response = "{"
                                        + "\"success\":true,"
                                        + "\"users\":"
                                        + usersJson
                                        + "}";

                        System.out.println(
                                        "[API] User search: "
                                                        + searchText
                                                        + " -> "
                                                        + response);

                        sendJson(
                                        exchange,
                                        200,
                                        response);

                } catch (Exception e) {

                        System.out.println(
                                        "[API] User search failed: "
                                                        + e.getMessage());

                        sendJson(
                                        exchange,
                                        500,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage())
                                                        + "\"}");
                }
        }

        // =========================================================
        // GET USERS FROM ONE MONITORING SERVER
        // =========================================================

        private String getServerUsers(String url) {

                try {

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .timeout(Duration.ofSeconds(3))
                                        .GET()
                                        .build();

                        HttpResponse<String> response = httpClient.send(
                                        request,
                                        HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() != 200) {

                                System.out.println(
                                                "[API] Server unavailable: "
                                                                + url
                                                                + " | HTTP "
                                                                + response.statusCode());

                                return "";
                        }

                        String body = response.body();

                        System.out.println(
                                        "[API] Response from "
                                                        + url
                                                        + ": "
                                                        + body);

                        return extractUsersArray(body);

                } catch (Exception e) {

                        /*
                         * One server being offline should NOT make
                         * the entire online-users API fail.
                         */

                        System.out.println(
                                        "[API] Could not reach "
                                                        + url
                                                        + " - treating server as offline.");

                        System.out.println(
                                        "[API] Reason: "
                                                        + e.getClass().getSimpleName()
                                                        + " - "
                                                        + e.getMessage());

                        return "";
                }
        }

        // =========================================================
        // EXTRACT USERS ARRAY
        // =========================================================

        private String extractUsersArray(String json) {

                if (json == null || json.isEmpty()) {
                        return "";
                }

                int start = json.indexOf("\"users\":[");

                if (start == -1) {
                        return "";
                }

                start = start + "\"users\":[".length();

                int end = json.indexOf("]", start);

                if (end == -1) {
                        return "";
                }

                return json
                                .substring(start, end)
                                .trim();
        }

        // =========================================================
        // QUERY PARAMETER
        // =========================================================

        private String getQueryParameter(
                        String query,
                        String parameter) {

                if (query == null || query.isEmpty()) {
                        return null;
                }

                String[] parameters = query.split("&");

                for (String param : parameters) {

                        String[] pair = param.split("=", 2);

                        if (pair.length == 2
                                        && pair[0].equals(parameter)) {

                                return pair[1];
                        }
                }

                return null;
        }

        // =========================================================
        // JSON ESCAPE
        // =========================================================

        private String escapeJson(String value) {

                if (value == null) {
                        return "";
                }

                return value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"");
        }

        // =========================================================
        // EXTRACT ONLINE USERNAMES
        // =========================================================

        private void extractOnlineUsernames(
                        String usersJson,
                        Set<String> onlineUsers) {

                if (usersJson == null
                                || usersJson.trim().isEmpty()) {

                        return;
                }

                /*
                 * Example usersJson:
                 *
                 * {"username":"Pranav","server":"SERVER_1"},
                 * {"username":"Rahul","server":"SERVER_1"}
                 */

                String[] users = usersJson.split("\\},\\{");

                for (String user : users) {

                        String marker = "\"username\":\"";

                        int start = user.indexOf(marker);

                        if (start == -1) {
                                continue;
                        }

                        start += marker.length();

                        int end = user.indexOf("\"", start);

                        if (end == -1) {
                                continue;
                        }

                        String username = user.substring(start, end);

                        if (!username.isEmpty()) {
                                onlineUsers.add(username);
                        }
                }
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