package api;

import com.sun.net.httpserver.HttpExchange;

import model.User;
import service.UserService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import service.EmailOtpService;
import service.GoogleAuthService;
import service.GoogleAuthTicketService;

public class AuthApiHandler {

        private final UserService userService;
        private final EmailOtpService emailOtpService;
        private final GoogleAuthService googleAuthService;
        private final GoogleAuthTicketService googleAuthTicketService;

        public AuthApiHandler() {

                userService = new UserService();
                emailOtpService = new EmailOtpService();
                googleAuthService = new GoogleAuthService();
                googleAuthTicketService = new GoogleAuthTicketService();
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

                } catch (UserService.EmailNotVerifiedException e) {

                        sendJson(
                                        exchange,
                                        403,
                                        "{\"success\":false,\"message\":\"Please verify your email before logging in\"}");

                } catch (Exception e) {

                        e.printStackTrace();

                        sendJson(
                                        exchange,
                                        500,
                                        "{\"success\":false,\"message\":\"Login failed\"}");
                }
        }

        // =========================================================
        // GOOGLE LOGIN API
        // =========================================================

        public void handleGoogleLogin(HttpExchange exchange)
                        throws IOException {

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                try {
                        String requestBody = readRequestBody(exchange);
                        String credential = extractJsonValue(requestBody, "credential");

                        if (credential == null || credential.trim().isEmpty()) {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Google credential is required\"}");
                                return;
                        }

                        // Verify the Google ID token on the backend.
                        GoogleAuthService.GoogleUser googleUser = googleAuthService.verifyIdToken(credential);

                        // Find the linked account or create a new verified account.
                        User loggedInUser = userService.findOrCreateGoogleUser(googleUser);

                        GoogleAuthTicketService.Ticket ticket = googleAuthTicketService.createTicket(
                                        loggedInUser.getUsername());

                        String response = "{"
                                        + "\"success\":true,"
                                        + "\"message\":\"Google identity verified successfully\","
                                        + "\"username\":\""
                                        + escapeJson(loggedInUser.getUsername())
                                        + "\","
                                        + "\"email\":\""
                                        + escapeJson(loggedInUser.getEmail())
                                        + "\","
                                        + "\"ticket\":\""
                                        + escapeJson(ticket.getToken())
                                        + "\","
                                        + "\"sessionId\":\""
                                        + escapeJson(ticket.getSessionId())
                                        + "\""
                                        + "}";

                        sendJson(exchange, 200, response);

                } catch (UserService.GoogleAccountConflictException e) {

                        sendJson(exchange, 409,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage()) + "\"}");

                } catch (SecurityException e) {

                        sendJson(exchange, 401,
                                        "{\"success\":false,\"message\":\"Google verification failed. Please try again.\"}");

                } catch (IllegalArgumentException e) {

                        sendJson(exchange, 400,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage()) + "\"}");

                } catch (Exception e) {

                        e.printStackTrace();

                        sendJson(exchange, 500,
                                        "{\"success\":false,\"message\":\"Google login failed\"}");
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

                        String mobileNumber = extractJsonValue(
                                        requestBody,
                                        "mobileNumber");

                        System.out.println("Registration request received.");
                        System.out.println("Mobile number received: " + mobileNumber);

                        // =================================================
                        // BASIC VALIDATION
                        // =================================================

                        if (username == null
                                        || password == null
                                        || email == null
                                        || mobileNumber == null
                                        || username.trim().isEmpty()
                                        || password.isEmpty()
                                        || email.trim().isEmpty()
                                        || mobileNumber.trim().isEmpty()) {

                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Username, password, email and mobile number are required\"}");

                                return;
                        }

                        username = username.trim();
                        email = email.trim().toLowerCase();
                        mobileNumber = mobileNumber.trim();

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

                        if (!mobileNumber.matches("^\\+[1-9][0-9]{7,14}$")) {
                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Please enter a valid mobile number with country code\"}");
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
                        // CREATE USER AND SEND EMAIL OTP
                        // =================================================

                        User user = new User(
                                        username,
                                        password,
                                        email,
                                        mobileNumber);

                        boolean registered = userService.registerUser(user);

                        if (!registered) {
                                sendJson(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Registration failed\"}");
                                return;
                        }

                        // Account created — attempt to send verification OTP.
                        boolean verificationEmailSent = false;

                        try {
                                emailOtpService.sendOtp(email);
                                verificationEmailSent = true;

                        } catch (Exception emailException) {
                                System.err.println(
                                                "Registration succeeded, but verification email could not be sent.");
                                emailException.printStackTrace();
                        }

                        String message = verificationEmailSent
                                        ? "Registration successful. A verification code has been sent to your email."
                                        : "Account created, but the verification email could not be sent. Please request a new code.";

                        String response = "{"
                                        + "\"success\":true,"
                                        + "\"message\":\"" + escapeJson(message) + "\","
                                        + "\"username\":\"" + escapeJson(username) + "\","
                                        + "\"email\":\"" + escapeJson(email) + "\","
                                        + "\"verificationEmailSent\":" + verificationEmailSent
                                        + "}";

                        sendJson(exchange, 201, response);

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

        // =========================================================
        // CHECK EMAIL AVAILABILITY
        // GET /api/auth/check-email?email=...
        // =========================================================
        public void handleCheckEmail(HttpExchange exchange) throws IOException {

                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                        exchange.sendResponseHeaders(204, -1);
                        exchange.close();
                        return;
                }

                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                String email = getQueryParam(
                                exchange.getRequestURI().getRawQuery(),
                                "email");

                if (email == null || email.trim().isEmpty()) {
                        sendJson(exchange, 400,
                                        "{\"success\":false,\"message\":\"Email is required\"}");
                        return;
                }

                email = email.trim().toLowerCase(java.util.Locale.ROOT);

                try {
                        boolean exists = userService.emailExists(email);

                        String response = exists
                                        ? "{\"success\":true,\"available\":false,"
                                                        + "\"message\":\"This email is already registered.\"}"
                                        : "{\"success\":true,\"available\":true,"
                                                        + "\"message\":\"Email is available.\"}";

                        sendJson(exchange, 200, response);

                } catch (Exception e) {
                        e.printStackTrace();

                        sendJson(exchange, 500,
                                        "{\"success\":false,"
                                                        + "\"message\":\"Unable to check email availability.\"}");
                }
        }

        // =========================================================
        // SEND EMAIL OTP API
        // =========================================================

        public void handleSendOtp(HttpExchange exchange) throws IOException {

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                try {
                        String requestBody = readRequestBody(exchange);
                        String email = extractJsonValue(requestBody, "email");

                        if (email == null || email.trim().isEmpty()) {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Email is required\"}");
                                return;
                        }

                        emailOtpService.sendOtp(email.trim().toLowerCase());

                        sendJson(exchange, 200,
                                        "{\"success\":true,\"message\":\"Verification code sent to your email\"}");

                } catch (IllegalArgumentException | IllegalStateException e) {
                        sendJson(exchange, 400,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage()) + "\"}");

                } catch (Exception e) {
                        e.printStackTrace();
                        sendJson(exchange, 500,
                                        "{\"success\":false,\"message\":\"Unable to send verification email. Please try again.\"}");
                }
        }

        // =========================================================
        // VERIFY EMAIL OTP API
        // =========================================================

        public void handleVerifyOtp(HttpExchange exchange) throws IOException {

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                try {
                        String requestBody = readRequestBody(exchange);

                        String email = extractJsonValue(requestBody, "email");
                        String otp = extractJsonValue(requestBody, "otp");

                        if (email == null || email.trim().isEmpty()
                                        || otp == null || !otp.matches("\\d{6}")) {

                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"A valid email and six-digit OTP are required\"}");
                                return;
                        }

                        boolean verified = emailOtpService.verifyOtp(
                                        email.trim().toLowerCase(),
                                        otp);

                        if (verified) {
                                sendJson(exchange, 200,
                                                "{\"success\":true,\"message\":\"Email verified successfully\"}");
                        } else {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Invalid or expired verification code\"}");
                        }

                } catch (Exception e) {
                        e.printStackTrace();
                        sendJson(exchange, 500,
                                        "{\"success\":false,\"message\":\"Email verification failed\"}");
                }
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

        // =========================================================
        // UPDATE USERNAME API
        // PUT /api/users/update-username
        // =========================================================

        public void handleUpdateUsername(HttpExchange exchange) throws IOException {
                if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                try {
                        String body = readRequestBody(exchange);

                        String currentUsername = extractJsonValue(body, "currentUsername");
                        String currentPassword = extractJsonValue(body, "currentPassword");
                        String newUsername = extractJsonValue(body, "newUsername");

                        if (currentUsername == null || currentPassword == null
                                        || newUsername == null) {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Current username, current password and new username are required\"}");
                                return;
                        }

                        boolean updated = userService.updateUsername(
                                        currentUsername,
                                        currentPassword,
                                        newUsername);

                        if (!updated) {
                                sendJson(exchange, 409,
                                                "{\"success\":false,\"message\":\"Username was not changed. Please try again.\"}");
                                return;
                        }

                        sendJson(exchange, 200,
                                        "{\"success\":true,\"message\":\"Username updated successfully\","
                                                        + "\"username\":\"" + escapeJson(newUsername.trim()) + "\"}");

                } catch (IllegalArgumentException e) {
                        sendJson(exchange, 400,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage()) + "\"}");

                } catch (Exception e) {
                        e.printStackTrace();
                        sendJson(exchange, 500,
                                        "{\"success\":false,\"message\":\"Unable to update username\"}");
                }
        }

        // =========================================================
        // CHANGE PASSWORD API
        // PUT /api/users/change-password
        // =========================================================

        public void handleChangePassword(HttpExchange exchange) throws IOException {
                if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                try {
                        String body = readRequestBody(exchange);

                        String username = extractJsonValue(body, "username");
                        String currentPassword = extractJsonValue(body, "currentPassword");
                        String newPassword = extractJsonValue(body, "newPassword");

                        if (username == null || currentPassword == null || newPassword == null) {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Username, current password and new password are required\"}");
                                return;
                        }

                        boolean updated = userService.changePassword(
                                        username,
                                        currentPassword,
                                        newPassword);

                        if (!updated) {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Password was not changed. Please try again.\"}");
                                return;
                        }

                        sendJson(exchange, 200,
                                        "{\"success\":true,\"message\":\"Password changed successfully\"}");

                } catch (IllegalArgumentException e) {
                        sendJson(exchange, 400,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage()) + "\"}");

                } catch (Exception e) {
                        e.printStackTrace();
                        sendJson(exchange, 500,
                                        "{\"success\":false,\"message\":\"Unable to change password\"}");
                }
        }

        // =========================================================
        // SET FIRST PASSWORD FOR GOOGLE ACCOUNT
        // POST /api/users/google/set-password
        // =========================================================

        public void handleGoogleSetPassword(HttpExchange exchange)
                        throws IOException {

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                try {
                        String body = readRequestBody(exchange);
                        String credential = extractJsonValue(body, "credential");
                        String newPassword = extractJsonValue(body, "newPassword");

                        if (credential == null || credential.trim().isEmpty()
                                        || newPassword == null) {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Google verification and new password are required\"}");
                                return;
                        }

                        GoogleAuthService.GoogleUser verified = googleAuthService.verifyIdToken(credential);

                        userService.setGoogleAccountPassword(
                                        verified.getSubject(), newPassword);

                        sendJson(exchange, 200,
                                        "{\"success\":true,\"message\":\"CloudChat password set successfully\"}");

                } catch (SecurityException e) {
                        sendJson(exchange, 401,
                                        "{\"success\":false,\"message\":\"Google verification failed. Please try again.\"}");

                } catch (IllegalArgumentException e) {
                        sendJson(exchange, 400,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage()) + "\"}");

                } catch (Exception e) {
                        e.printStackTrace();
                        sendJson(exchange, 500,
                                        "{\"success\":false,\"message\":\"Unable to set password\"}");
                }
        }

        // =========================================================
        // UPDATE GOOGLE ACCOUNT USERNAME
        // PUT /api/users/google/update-username
        // =========================================================

        public void handleGoogleUpdateUsername(HttpExchange exchange)
                        throws IOException {

                if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405,
                                        "{\"success\":false,\"message\":\"Method not allowed\"}");
                        return;
                }

                try {
                        String body = readRequestBody(exchange);
                        String credential = extractJsonValue(body, "credential");
                        String newUsername = extractJsonValue(body, "newUsername");

                        if (credential == null || credential.trim().isEmpty()
                                        || newUsername == null) {
                                sendJson(exchange, 400,
                                                "{\"success\":false,\"message\":\"Google verification and new username are required\"}");
                                return;
                        }

                        GoogleAuthService.GoogleUser verified = googleAuthService.verifyIdToken(credential);

                        boolean updated = userService.updateGoogleUsernameBySub(
                                        verified.getSubject(), newUsername);

                        if (!updated) {
                                sendJson(exchange, 409,
                                                "{\"success\":false,\"message\":\"Username was not changed. Please try again.\"}");
                                return;
                        }

                        sendJson(exchange, 200,
                                        "{\"success\":true,"
                                                        + "\"message\":\"Username updated successfully\","
                                                        + "\"username\":\""
                                                        + escapeJson(newUsername.trim()) + "\"}");

                } catch (SecurityException e) {
                        sendJson(exchange, 401,
                                        "{\"success\":false,\"message\":\"Google verification failed. Please try again.\"}");

                } catch (IllegalArgumentException e) {
                        sendJson(exchange, 400,
                                        "{\"success\":false,\"message\":\""
                                                        + escapeJson(e.getMessage()) + "\"}");

                } catch (Exception e) {
                        e.printStackTrace();
                        sendJson(exchange, 500,
                                        "{\"success\":false,\"message\":\"Unable to update username\"}");
                }
        }

}