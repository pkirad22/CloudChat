package api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cdimascio.dotenv.Dotenv;
import org.bson.Document;

import service.UserService;

public class AdminApiHandler {

    private static final long SESSION_TTL_MS = Duration.ofMinutes(30).toMillis();

    private final String configuredAdminId;
    private final String configuredAdminPassword;

    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<String, AdminSession> sessions = new ConcurrentHashMap<>();

    public AdminApiHandler() {

        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        configuredAdminId = dotenv.get("CLOUDCHAT_ADMIN_ID");
        configuredAdminPassword = dotenv.get("CLOUDCHAT_ADMIN_PASSWORD");
    }

    // =========================================================
    // ADMIN LOGIN
    // POST /api/admin/login
    // =========================================================

    public void handleLogin(HttpExchange exchange) throws IOException {

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 204, "");
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        // Fail closed when credentials have not been configured.
        if (isBlank(configuredAdminId)
                || isBlank(configuredAdminPassword)) {
            sendResponse(exchange, 503,
                    "{\"success\":false,\"message\":\"Admin authentication is not configured on the server\"}");
            return;
        }

        try {
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String adminId = extractJsonString(body, "adminId");
            String password = extractJsonString(body, "password");

            if (isBlank(adminId) || isBlank(password)) {
                sendResponse(exchange, 400,
                        "{\"success\":false,\"message\":\"Admin ID and password are required\"}");
                return;
            }

            boolean validId = constantTimeEquals(
                    configuredAdminId, adminId);

            boolean validPassword = constantTimeEquals(
                    configuredAdminPassword, password);

            if (!validId || !validPassword) {
                // Avoid revealing which credential was incorrect.
                sendResponse(exchange, 401,
                        "{\"success\":false,\"message\":\"Invalid admin credentials\"}");
                return;
            }

            String token = createSecureToken();
            long expiresAt = System.currentTimeMillis() + SESSION_TTL_MS;

            sessions.put(token, new AdminSession(adminId, expiresAt));

            String response = "{"
                    + "\"success\":true,"
                    + "\"message\":\"Admin login successful\","
                    + "\"adminId\":\"" + escapeJson(adminId) + "\","
                    + "\"token\":\"" + token + "\","
                    + "\"expiresAt\":" + expiresAt
                    + "}";

            sendResponse(exchange, 200, response);

        } catch (Exception e) {
            sendResponse(exchange, 400,
                    "{\"success\":false,\"message\":\"Invalid login request\"}");
        }
    }

    // =========================================================
    // CHECK ADMIN SESSION
    // GET /api/admin/session
    // =========================================================

    public void handleSession(HttpExchange exchange) throws IOException {

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 204, "");
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        AdminSession session = getValidSession(exchange);

        if (session == null) {
            sendResponse(exchange, 401,
                    "{\"success\":false,\"authenticated\":false,\"message\":\"Admin session is missing or expired\"}");
            return;
        }

        String response = "{"
                + "\"success\":true,"
                + "\"authenticated\":true,"
                + "\"adminId\":\"" + escapeJson(session.adminId) + "\","
                + "\"expiresAt\":" + session.expiresAt
                + "}";

        sendResponse(exchange, 200, response);
    }

    // =========================================================
    // ADMIN LOGOUT
    // POST /api/admin/logout
    // =========================================================

    public void handleLogout(HttpExchange exchange) throws IOException {

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 204, "");
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        String token = getBearerToken(exchange);

        if (token != null) {
            sessions.remove(token);
        }

        sendResponse(exchange, 200,
                "{\"success\":true,\"message\":\"Logged out\"}");
    }

    // =========================================================
    // VALIDATE ADMIN SESSION
    // Reuse this method when protecting future admin endpoints.
    // =========================================================

    public AdminSession getValidSession(HttpExchange exchange) {

        String token = getBearerToken(exchange);

        if (token == null) {
            return null;
        }

        AdminSession session = sessions.get(token);

        if (session == null) {
            return null;
        }

        if (System.currentTimeMillis() >= session.expiresAt) {
            sessions.remove(token, session);
            return null;
        }

        return session;
    }

    private String getBearerToken(HttpExchange exchange) {

        String authorization = exchange.getRequestHeaders()
                .getFirst("Authorization");

        if (authorization == null
                || !authorization.regionMatches(
                        true, 0, "Bearer ", 0, 7)) {
            return null;
        }

        String token = authorization.substring(7).trim();

        return token.isEmpty() ? null : token;
    }

    private String createSecureToken() {

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return HexFormat.of().formatHex(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {

        if (expected == null || actual == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String extractJsonString(String json, String key) {

        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String response) throws IOException {

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        if (statusCode != 204) {
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json; charset=UTF-8");
        }

        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    public static class AdminSession {

        private final String adminId;
        private final long expiresAt;

        public AdminSession(String adminId, long expiresAt) {
            this.adminId = adminId;
            this.expiresAt = expiresAt;
        }

        public String getAdminId() {
            return adminId;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }

    // =========================================================
    // ADMIN — LIST REGISTERED USERS
    // GET /api/admin/users?search=&page=1&pageSize=10
    // =========================================================

    public void handleUsers(HttpExchange exchange) {
        try {

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            // Only GET is allowed.
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(
                        exchange,
                        405,
                        "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                return;
            }

            // Validate the admin session on the backend.
            AdminSession session = getValidSession(exchange);

            if (session == null) {
                sendResponse(
                        exchange,
                        401,
                        "{\"success\":false,\"message\":\"Unauthorized. Please log in again.\"}");
                return;
            }

            // Read and validate query parameters.
            Map<String, String> params = parseAdminQuery(exchange.getRequestURI().getRawQuery());

            String search = params.getOrDefault("search", "").trim();

            int page = parsePositiveInt(params.get("page"), 1);
            int pageSize = parsePositiveInt(params.get("pageSize"), 10);

            // Prevent excessive page sizes.
            page = Math.max(1, page);
            pageSize = Math.max(1, Math.min(pageSize, 100));

            // Retrieve safe user records from MongoDB.
            UserService userService = new UserService();

            Document result = userService.getAdminUsers(
                    search,
                    page,
                    pageSize);

            @SuppressWarnings("unchecked")
            List<Document> users = (List<Document>) result.get("users");

            long total = result.get("total", 0L).longValue();
            int currentPage = result.getInteger("page", page);
            int currentPageSize = result.getInteger("pageSize", pageSize);
            int totalPages = result.getInteger("totalPages", 0);

            // Build JSON explicitly so dates are normal ISO strings,
            // not MongoDB Extended JSON values.
            StringBuilder json = new StringBuilder();

            json.append("{")
                    .append("\"success\":true,")
                    .append("\"users\":[");

            for (int i = 0; i < users.size(); i++) {
                Document user = users.get(i);

                if (i > 0) {
                    json.append(",");
                }

                String username = user.getString("username");
                String email = user.getString("email");
                String mobileNumber = user.getString("mobileNumber");
                String authProvider = user.getString("authProvider");
                String accountStatus = user.getString("accountStatus");

                if (accountStatus == null || accountStatus.isBlank()) {
                    accountStatus = "ACTIVE";
                }

                boolean emailVerified = Boolean.TRUE.equals(user.getBoolean("emailVerified"));

                Object createdAtValue = user.get("createdAt");
                String createdAt = null;

                if (createdAtValue instanceof Date) {
                    createdAt = ((Date) createdAtValue)
                            .toInstant()
                            .toString();
                }

                json.append("{")
                        .append("\"username\":")
                        .append(jsonValue(username))
                        .append(",\"email\":")
                        .append(jsonValue(email))
                        .append(",\"mobileNumber\":")
                        .append(jsonValue(mobileNumber))
                        .append(",\"emailVerified\":")
                        .append(emailVerified)
                        .append(",\"authProvider\":")
                        .append(jsonValue(authProvider))
                        .append(",\"accountStatus\":")
                        .append(jsonValue(accountStatus))
                        .append(",\"createdAt\":")
                        .append(jsonValue(createdAt))
                        .append("}");
            }

            json.append("],")
                    .append("\"pagination\":{")
                    .append("\"total\":").append(total).append(",")
                    .append("\"page\":").append(currentPage).append(",")
                    .append("\"pageSize\":").append(currentPageSize).append(",")
                    .append("\"totalPages\":").append(totalPages)
                    .append("}")
                    .append("}");

            sendResponse(exchange, 200, json.toString());

        } catch (Exception e) {
            System.err.println(
                    "[ADMIN] Failed to retrieve user list: "
                            + e.getMessage());

            e.printStackTrace();

            try {
                sendResponse(
                        exchange,
                        500,
                        "{\"success\":false,\"message\":\"Unable to retrieve users.\"}");
            } catch (Exception ignored) {
            }
        } finally {
            try {
                exchange.close();
            } catch (Exception ignored) {
            }
        }
    }

    // =========================================================
    // ADMIN — DEACTIVATE / REACTIVATE USER
    // PUT /api/admin/users/status
    // =========================================================

    public void handleUpdateUserStatus(HttpExchange exchange) {
        try {

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(
                        exchange,
                        405,
                        "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                return;
            }

            // Require a valid administrator session.
            AdminSession session = getValidSession(exchange);

            if (session == null) {
                sendResponse(
                        exchange,
                        401,
                        "{\"success\":false,\"message\":\"Unauthorized. Please log in again.\"}");
                return;
            }

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String username = extractJsonString(body, "username");
            String status = extractJsonString(body, "accountStatus");

            if (isBlank(username) || isBlank(status)) {
                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Username and account status are required.\"}");
                return;
            }

            username = username.trim();
            status = status.trim().toUpperCase(java.util.Locale.ROOT);

            if (!"ACTIVE".equals(status)
                    && !"DEACTIVATED".equals(status)) {
                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Account status must be ACTIVE or DEACTIVATED.\"}");
                return;
            }

            UserService userService = new UserService();

            boolean updated = userService.updateAccountStatus(username, status);

            if (!updated) {
                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"User not found or account status unchanged.\"}");
                return;
            }

            System.out.println(
                    "[ADMIN] " + session.adminId
                            + " changed account status for "
                            + username + " to " + status);

            sendResponse(
                    exchange,
                    200,
                    "{\"success\":true,\"message\":\"Account status updated successfully.\","
                            + "\"username\":" + jsonValue(username) + ","
                            + "\"accountStatus\":" + jsonValue(status) + "}");

        } catch (IllegalArgumentException e) {
            try {
                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":"
                                + jsonValue(e.getMessage()) + "}");
            } catch (IOException ignored) {
            }
        } catch (Exception e) {
            System.err.println(
                    "[ADMIN] Failed to update account status: "
                            + e.getMessage());

            try {
                sendResponse(
                        exchange,
                        500,
                        "{\"success\":false,\"message\":\"Unable to update account status.\"}");
            } catch (IOException ignored) {
            }
        } finally {
            try {
                exchange.close();
            } catch (Exception ignored) {
            }
        }
    }
    // =========================================================
    // ADMIN — QUERY PARAMETER PARSER
    // =========================================================

    private Map<String, String> parseAdminQuery(String query) {
        Map<String, String> params = new HashMap<>();

        if (query == null || query.isBlank()) {
            return params;
        }

        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');

            if (separator < 0) {
                continue;
            }

            String key = URLDecoder.decode(
                    pair.substring(0, separator),
                    StandardCharsets.UTF_8);

            String value = URLDecoder.decode(
                    pair.substring(separator + 1),
                    StandardCharsets.UTF_8);

            params.put(key, value);
        }

        return params;
    }

    // =========================================================
    // ADMIN — SAFE INTEGER PARSER
    // =========================================================

    private int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // =========================================================
    // ADMIN — JSON VALUE SERIALIZER
    // Uses the existing escapeJson(String) helper.
    // =========================================================

    private String jsonValue(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escapeJson(value) + "\"";
    }
}