package api;

import com.sun.net.httpserver.HttpExchange;

import model.User;
import service.UserService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class AuthApiHandler {

        private final UserService userService;

        public AuthApiHandler() {

                userService = new UserService();
        }

        // =========================================================
        // LOGIN API
        // =========================================================

        public void handleLogin(HttpExchange exchange)
                        throws IOException {

                if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

                        sendJson(
                                        exchange,
                                        405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");

                        return;
                }

                try {

                        String requestBody = readRequestBody(exchange);

                        String username = extractJsonValue(requestBody, "username");

                        String password = extractJsonValue(requestBody, "password");

                        if (username == null
                                        || password == null
                                        || username.trim().isEmpty()
                                        || password.isEmpty()) {

                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Username and password are required\"}");

                                return;
                        }

                        User loggedInUser = userService.loginUser(
                                        username.trim(),
                                        password);

                        if (loggedInUser != null) {

                                String response = "{"
                                                + "\"success\":true,"
                                                + "\"message\":\"Login successful\","
                                                + "\"username\":\""
                                                + escapeJson(username.trim())
                                                + "\""
                                                + "}";

                                sendJson(
                                                exchange,
                                                200,
                                                response);

                        } else {

                                sendJson(
                                                exchange,
                                                401,
                                                "{\"success\":false,\"message\":\"Invalid username or password\"}");
                        }

                } catch (Exception e) {

                        e.printStackTrace();

                        sendJson(
                                        exchange,
                                        500,
                                        "{\"success\":false,\"message\":\"Login failed\"}");
                }
        }

        // =========================================================
        // REGISTER API
        // =========================================================

        public void handleRegister(HttpExchange exchange)
                        throws IOException {

                if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

                        sendJson(
                                        exchange,
                                        405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");

                        return;
                }

                try {

                        String requestBody = readRequestBody(exchange);

                        String username = extractJsonValue(
                                        requestBody,
                                        "username");

                        String password = extractJsonValue(
                                        requestBody,
                                        "password");

                        String email = extractJsonValue(
                                        requestBody,
                                        "email");

                        // =================================================
                        // BASIC VALIDATION
                        // =================================================

                        if (username == null
                                        || password == null
                                        || email == null
                                        || username.trim().isEmpty()
                                        || password.isEmpty()
                                        || email.trim().isEmpty()) {

                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Username, password and email are required\"}");

                                return;
                        }

                        username = username.trim();
                        email = email.trim().toLowerCase();

                        // =================================================
                        // USERNAME VALIDATION
                        // =================================================

                        if (!isValidUsername(username)) {

                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Username must be 3-30 characters and contain only letters, numbers or underscores\"}");

                                return;
                        }

                        // =================================================
                        // EMAIL VALIDATION
                        // =================================================

                        if (!isValidEmail(email)) {

                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Please enter a valid email address\"}");

                                return;
                        }

                        // =================================================
                        // PASSWORD VALIDATION
                        // =================================================

                        if (!isStrongPassword(password)) {

                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Password must be at least 8 characters and contain uppercase, lowercase, number and special character with no spaces\"}");

                                return;
                        }

                        // =================================================
                        // CHECK USERNAME
                        // =================================================

                        if (userService.userExists(username)) {

                                sendJson(
                                                exchange,
                                                409,
                                                "{\"success\":false,\"message\":\"Username already exists\"}");

                                return;
                        }

                        // =================================================
                        // CHECK USERNAME
                        // =================================================

                        if (userService.userExists(username)) {

                                sendJson(
                                                exchange,
                                                409,
                                                "{\"success\":false,\"message\":\"Username already exists\"}");

                                return;
                        }

                        // =================================================
                        // CREATE USER
                        // =================================================

                        User user = new User(
                                        username,
                                        password,
                                        email);

                        boolean registered = userService.registerUser(user);

                        if (registered) {

                                String response = "{"
                                                + "\"success\":true,"
                                                + "\"message\":\"Registration successful\","
                                                + "\"username\":\""
                                                + escapeJson(username)
                                                + "\","
                                                + "\"email\":\""
                                                + escapeJson(email)
                                                + "\""
                                                + "}";

                                sendJson(
                                                exchange,
                                                201,
                                                response);

                        } else {

                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Registration failed\"}");
                        }

                } catch (Exception e) {

                        e.printStackTrace();

                        sendJson(
                                        exchange,
                                        500,
                                        "{\"success\":false,\"message\":\"Registration failed\"}");
                }
        }

        // =========================================================
        // READ REQUEST BODY
        // =========================================================

        private String readRequestBody(
                        HttpExchange exchange)
                        throws IOException {

                InputStream inputStream = exchange.getRequestBody();

                return new String(
                                inputStream.readAllBytes(),
                                StandardCharsets.UTF_8);
        }

        // =========================================================
        // SIMPLE JSON VALUE EXTRACTOR
        // =========================================================

        private String extractJsonValue(
                        String json,
                        String key) {

                String searchKey = "\"" + key + "\"";

                int keyIndex = json.indexOf(searchKey);

                if (keyIndex == -1) {

                        return null;
                }

                int colonIndex = json.indexOf(
                                ":",
                                keyIndex);

                if (colonIndex == -1) {

                        return null;
                }

                int firstQuote = json.indexOf(
                                "\"",
                                colonIndex + 1);

                if (firstQuote == -1) {

                        return null;
                }

                int secondQuote = json.indexOf(
                                "\"",
                                firstQuote + 1);

                if (secondQuote == -1) {

                        return null;
                }

                return json.substring(
                                firstQuote + 1,
                                secondQuote);
        }

        // =========================================================
        // JSON ESCAPE
        // =========================================================

        private String escapeJson(String value) {

                return value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"");
        }

        // =========================================================
        // SEND JSON RESPONSE
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
        // =========================================================
        // VALIDATION PATTERNS
        // =========================================================

        private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,30}$");

        private static final Pattern EMAIL_PATTERN = Pattern.compile(
                        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

        // =========================================================
        // USERNAME VALIDATION
        // =========================================================

        private boolean isValidUsername(String username) {

                return username != null
                                && USERNAME_PATTERN.matcher(username).matches();
        }

        // =========================================================
        // EMAIL VALIDATION
        // =========================================================

        private boolean isValidEmail(String email) {

                return email != null
                                && EMAIL_PATTERN.matcher(email).matches();
        }

        // =========================================================
        // PASSWORD VALIDATION
        // =========================================================

        private boolean isStrongPassword(String password) {

                if (password == null || password.length() < 8) {
                        return false;
                }

                if (password.length() > 128) {
                        return false;
                }

                if (password.contains(" ")) {
                        return false;
                }

                boolean hasUppercase = password.matches(".*[A-Z].*");

                boolean hasLowercase = password.matches(".*[a-z].*");

                boolean hasNumber = password.matches(".*[0-9].*");

                boolean hasSpecial = password.matches(".*[^A-Za-z0-9].*");

                return hasUppercase
                                && hasLowercase
                                && hasNumber
                                && hasSpecial;
        }

        public void checkUsername(HttpExchange exchange)
                        throws IOException {

                if (!"GET".equalsIgnoreCase(
                                exchange.getRequestMethod())) {

                        sendJson(
                                        exchange,
                                        405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");

                        return;
                }

                String query = exchange.getRequestURI().getQuery();

                String username = getQueryParam(query, "username");

                if (username == null ||
                                !isValidUsername(username)) {

                        sendJson(
                                        exchange,
                                        400,
                                        "{\"success\":false,\"message\":\"Invalid username\"}");

                        return;
                }

                boolean exists = userService.userExists(username);

                String response = "{\"success\":true,\"available\":"
                                + (!exists)
                                + ",\"message\":\""
                                + (exists
                                                ? "Username already exists"
                                                : "Username is available")
                                + "\"}";

                sendJson(
                                exchange,
                                200,
                                response);
        }

        private String getQueryParam(
                        String query,
                        String key) {

                if (query == null || query.isEmpty()) {
                        return null;
                }

                for (String parameter : query.split("&")) {

                        String[] pair = parameter.split("=", 2);

                        if (pair.length == 2 &&
                                        pair[0].equals(key)) {

                                return java.net.URLDecoder.decode(
                                                pair[1],
                                                StandardCharsets.UTF_8);
                        }
                }

                return null;
        }
}