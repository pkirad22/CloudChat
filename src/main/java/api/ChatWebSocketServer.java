package api;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatWebSocketServer extends WebSocketServer {

        // =========================================================
        // CONFIGURATION
        // =========================================================

        private static final int PORT = 9001;

        // username -> WebSocket connection
        private static final Map<String, WebSocket> connectedUsers = new ConcurrentHashMap<>();

        // WebSocket connection -> username
        private static final Map<WebSocket, String> connections = new ConcurrentHashMap<>();

        // username -> real CloudChat backend connection
        private static final Map<String, BackendChatConnection> backendConnections = new ConcurrentHashMap<>();

        // One reader thread per backend connection
        private static final ExecutorService backendReaderPool = Executors.newCachedThreadPool();

        // =========================================================
        // CONSTRUCTOR
        // =========================================================

        public ChatWebSocketServer() {

                super(
                                new InetSocketAddress(
                                                "0.0.0.0",
                                                PORT));
        }

        // =========================================================
        // SERVER START
        // =========================================================

        @Override
        public void onStart() {

                System.out.println(
                                "[WS] Chat WebSocket Server started");

                System.out.println(
                                "[WS] Listening on port "
                                                + PORT);
        }

        // =========================================================
        // CLIENT CONNECTED
        // =========================================================

        @Override
        public void onOpen(
                        WebSocket connection,
                        ClientHandshake handshake) {

                System.out.println(
                                "[WS] Client connected: "
                                                + connection.getRemoteSocketAddress());

                sendJson(
                                connection,
                                "{"
                                                + "\"type\":\"CONNECTED\","
                                                + "\"message\":\"WebSocket connection established\""
                                                + "}");
        }

        // =========================================================
        // MESSAGE RECEIVED
        // =========================================================

        @Override
        public void onMessage(
                        WebSocket connection,
                        String message) {

                System.out.println(
                                "[WS] Received: "
                                                + message);

                handleMessage(
                                connection,
                                message);
        }

        // =========================================================
        // CLIENT DISCONNECTED
        // =========================================================

        @Override
        public void onClose(
                        WebSocket connection,
                        int code,
                        String reason,
                        boolean remote) {

                String username = connections.remove(connection);

                if (username != null) {

                        BackendChatConnection backendConnection = backendConnections.remove(username);

                        if (backendConnection != null) {
                                backendConnection.close();

                                System.out.println(
                                                "[WS] Backend connection closed for "
                                                                + username);
                        }
                }

                if (username != null) {

                        connectedUsers.remove(
                                        username,
                                        connection);

                        System.out.println(
                                        "[WS] User disconnected: "
                                                        + username);
                }

                System.out.println(
                                "[WS] Connection closed: "
                                                + reason);
        }

        // =========================================================
        // ERROR
        // =========================================================

        @Override
        public void onError(
                        WebSocket connection,
                        Exception exception) {

                System.out.println(
                                "[WS] WebSocket error");

                exception.printStackTrace();
        }

        // =========================================================
        // MESSAGE HANDLER
        // =========================================================

        private void handleMessage(
                        WebSocket connection,
                        String message) {

                if (message == null
                                || message.isBlank()) {

                        sendError(
                                        connection,
                                        "Message cannot be empty");

                        return;
                }

                String type = extractJsonValue(
                                message,
                                "type");

                if (type == null
                                || type.isBlank()) {

                        sendError(
                                        connection,
                                        "Message type is required");

                        return;
                }

                switch (type.toUpperCase()) {

                        case "AUTH":

                                handleAuthentication(
                                                connection,
                                                message);

                                break;

                        case "PING":

                                sendJson(
                                                connection,
                                                "{"
                                                                + "\"type\":\"PONG\""
                                                                + "}");

                                break;

                        case "PRIVATE_MESSAGE":

                                handlePrivateMessage(
                                                connection,
                                                message);

                                break;

                        default:

                                sendError(
                                                connection,
                                                "Unsupported message type: "
                                                                + type);
                }
        }

        // =========================================================
        // WEBSOCKET AUTHENTICATION
        // =========================================================

        private void handleAuthentication(
                        WebSocket connection,
                        String message) {

                String username = extractJsonValue(
                                message,
                                "username");

                String password = extractJsonValue(
                                message,
                                "password");

                if (username == null || username.isBlank()) {
                        sendError(connection, "Username is required");
                        return;
                }

                if (password == null || password.isBlank()) {
                        sendError(connection, "Password is required");
                        return;
                }

                username = username.trim();

                // ---------------------------------------------------------
                // Prevent duplicate WebSocket login
                // ---------------------------------------------------------

                WebSocket existingConnection = connectedUsers.get(username);

                if (existingConnection != null
                                && existingConnection != connection) {

                        sendJson(
                                        connection,
                                        "{"
                                                        + "\"type\":\"AUTH_SUCCESS\","
                                                        + "\"username\":\""
                                                        + escapeJson(username)
                                                        + "\""
                                                        + "}");

                        return;
                }

                // ---------------------------------------------------------
                // Connect to real CloudChat backend
                // ---------------------------------------------------------

                BackendChatConnection backendConnection = new BackendChatConnection(
                                "localhost",
                                5000);

                System.out.println(
                                "[WS] Connecting " + username
                                                + " to CloudChat backend...");

                if (!backendConnection.connect()) {

                        sendJson(
                                        connection,
                                        "{"
                                                        + "\"type\":\"AUTH_FAILED\","
                                                        + "\"message\":\"CloudChat backend unavailable\""
                                                        + "}");

                        return;
                }

                // ---------------------------------------------------------
                // Authenticate against REAL CloudChat server
                // ---------------------------------------------------------

                if (!backendConnection.authenticate(
                                username,
                                password)) {

                        backendConnection.close();

                        sendJson(
                                        connection,
                                        "{"
                                                        + "\"type\":\"AUTH_FAILED\","
                                                        + "\"message\":\"Invalid username or password\""
                                                        + "}");

                        return;
                }

                // ---------------------------------------------------------
                // Store WebSocket + backend connection
                // ---------------------------------------------------------

                connectedUsers.put(
                                username,
                                connection);

                connections.put(
                                connection,
                                username);

                backendConnections.put(
                                username,
                                backendConnection);

                System.out.println(
                                "[WS] User authenticated through CloudChat backend: "
                                                + username);

                // ---------------------------------------------------------
                // Start backend reader
                // ---------------------------------------------------------

                startBackendReader(
                                username,
                                connection,
                                backendConnection);

                // ---------------------------------------------------------
                // Send authentication success
                // ---------------------------------------------------------

                sendJson(
                                connection,
                                "{"
                                                + "\"type\":\"AUTH_SUCCESS\","
                                                + "\"username\":\""
                                                + escapeJson(username)
                                                + "\""
                                                + "}");

                sendOnlineUsers(connection);
        }

        // =========================================================
        // ONLINE USERS
        // =========================================================

        private void sendOnlineUsers(
                        WebSocket connection) {

                StringBuilder json = new StringBuilder();

                json.append("{");
                json.append("\"type\":\"ONLINE_USERS\",");
                json.append("\"users\":[");

                int index = 0;

                for (String username : connectedUsers.keySet()) {

                        if (index > 0) {
                                json.append(",");
                        }

                        json.append("\"")
                                        .append(username)
                                        .append("\"");

                        index++;
                }

                json.append("]");
                json.append("}");

                sendJson(
                                connection,
                                json.toString());
        }

        private void startBackendReader(
                        String username,
                        WebSocket webSocket,
                        BackendChatConnection backendConnection) {

                backendReaderPool.submit(() -> {

                        System.out.println(
                                        "[BRIDGE] Backend reader started for "
                                                        + username);

                        try {

                                while (backendConnection.isConnected()
                                                && webSocket.isOpen()) {

                                        String response = backendConnection.readResponse();

                                        if (response == null) {
                                                break;
                                        }

                                        handleBackendResponse(
                                                        username,
                                                        webSocket,
                                                        response);
                                }

                        } catch (Exception e) {

                                System.out.println(
                                                "[BRIDGE] Backend reader stopped for "
                                                                + username
                                                                + ": "
                                                                + e.getMessage());

                        } finally {

                                backendConnections.remove(
                                                username,
                                                backendConnection);

                                System.out.println(
                                                "[BRIDGE] Backend connection removed for "
                                                                + username);
                        }
                });
        }

        private void handleBackendResponse(
                        String username,
                        WebSocket webSocket,
                        String response) {

                System.out.println(
                                "[BACKEND EVENT] "
                                                + username
                                                + " <- "
                                                + response);

                // ---------------------------------------------------------
                // Private message received
                // ---------------------------------------------------------

                if (response.startsWith("PRIVATE from ")) {

                        int colonIndex = response.indexOf(": ");

                        if (colonIndex > 0) {

                                String sender = response.substring(
                                                "PRIVATE from ".length(),
                                                colonIndex);

                                String message = response.substring(
                                                colonIndex + 2);

                                sendJson(
                                                webSocket,
                                                "{"
                                                                + "\"type\":\"PRIVATE_MESSAGE\","
                                                                + "\"from\":\""
                                                                + escapeJson(sender)
                                                                + "\","
                                                                + "\"message\":\""
                                                                + escapeJson(message)
                                                                + "\""
                                                                + "}");
                        }

                        return;
                }

                // ---------------------------------------------------------
                // Private message confirmation
                // ---------------------------------------------------------

                if (response.startsWith("PRIVATE to ")) {

                        int colonIndex = response.indexOf(": ");

                        if (colonIndex > 0) {

                                String recipient = response.substring(
                                                "PRIVATE to ".length(),
                                                colonIndex);

                                String message = response.substring(
                                                colonIndex + 2);

                                sendJson(
                                                webSocket,
                                                "{"
                                                                + "\"type\":\"MESSAGE_SENT\","
                                                                + "\"to\":\""
                                                                + escapeJson(recipient)
                                                                + "\","
                                                                + "\"message\":\""
                                                                + escapeJson(message)
                                                                + "\""
                                                                + "}");
                        }

                        return;
                }

                // ---------------------------------------------------------
                // Offline message
                // ---------------------------------------------------------

                if (response.startsWith("SYSTEM:")) {

                        sendJson(
                                        webSocket,
                                        "{"
                                                        + "\"type\":\"SYSTEM\","
                                                        + "\"message\":\""
                                                        + escapeJson(response.substring(7).trim())
                                                        + "\""
                                                        + "}");

                        return;
                }

                // ---------------------------------------------------------
                // Online users
                // ---------------------------------------------------------

                if (response.startsWith("ONLINE_USERS:")) {

                        String usersText = response.substring(
                                        "ONLINE_USERS:".length());

                        String[] users = usersText.split(",");

                        StringBuilder json = new StringBuilder();

                        json.append("{");
                        json.append("\"type\":\"ONLINE_USERS\",");
                        json.append("\"users\":[");

                        int index = 0;

                        for (String user : users) {

                                if (user == null || user.isBlank()) {
                                        continue;
                                }

                                if (index > 0) {
                                        json.append(",");
                                }

                                json.append("\"")
                                                .append(escapeJson(user.trim()))
                                                .append("\"");

                                index++;
                        }

                        json.append("]");
                        json.append("}");

                        sendJson(
                                        webSocket,
                                        json.toString());

                        return;
                }

                // ---------------------------------------------------------
                // Server load
                // ---------------------------------------------------------

                if (response.startsWith("SERVER_LOAD:")) {

                        String[] parts = response.split(":");

                        if (parts.length >= 3) {

                                sendJson(
                                                webSocket,
                                                "{"
                                                                + "\"type\":\"SERVER_LOAD\","
                                                                + "\"load\":\""
                                                                + escapeJson(parts[1])
                                                                + "\","
                                                                + "\"level\":\""
                                                                + escapeJson(parts[2])
                                                                + "\""
                                                                + "}");
                        }

                        return;
                }

                // ---------------------------------------------------------
                // Other backend events
                // ---------------------------------------------------------

                sendJson(
                                webSocket,
                                "{"
                                                + "\"type\":\"BACKEND_EVENT\","
                                                + "\"message\":\""
                                                + escapeJson(response)
                                                + "\""
                                                + "}");
        }

        // =========================================================
        // ERROR RESPONSE
        // =========================================================

        private void sendError(
                        WebSocket connection,
                        String message) {

                sendJson(
                                connection,
                                "{"
                                                + "\"type\":\"ERROR\","
                                                + "\"message\":\""
                                                + message
                                                + "\""
                                                + "}");
        }

        // =========================================================
        // JSON RESPONSE
        // =========================================================

        private void sendJson(
                        WebSocket connection,
                        String json) {

                if (connection != null
                                && connection.isOpen()) {

                        connection.send(json);
                }

        }

        // =========================================================
        // SIMPLE JSON VALUE EXTRACTOR
        // =========================================================

        private String extractJsonValue(
                        String json,
                        String key) {

                String search = "\"" + key + "\"";

                int keyIndex = json.indexOf(search);

                if (keyIndex == -1) {
                        return null;
                }

                int colonIndex = json.indexOf(
                                ":",
                                keyIndex);

                if (colonIndex == -1) {
                        return null;
                }

                int start = json.indexOf(
                                "\"",
                                colonIndex);

                if (start == -1) {
                        return null;
                }

                int end = json.indexOf(
                                "\"",
                                start + 1);

                if (end == -1) {
                        return null;
                }

                return json.substring(
                                start + 1,
                                end);
        }

        private void handlePrivateMessage(
                        WebSocket senderSocket,
                        String json) {

                String recipient = extractJsonValue(
                                json,
                                "to");

                String message = extractJsonValue(
                                json,
                                "message");

                String sender = connections.get(senderSocket);

                if (sender == null) {

                        sendError(
                                        senderSocket,
                                        "Not authenticated");

                        return;
                }

                if (recipient == null
                                || recipient.trim().isEmpty()) {

                        sendError(
                                        senderSocket,
                                        "Recipient is required");

                        return;
                }

                if (message == null
                                || message.trim().isEmpty()) {

                        sendError(
                                        senderSocket,
                                        "Message cannot be empty");

                        return;
                }

                BackendChatConnection backendConnection = backendConnections.get(sender);

                if (backendConnection == null
                                || !backendConnection.isConnected()) {

                        sendError(
                                        senderSocket,
                                        "CloudChat backend connection is not available");

                        return;
                }

                // ---------------------------------------------------------
                // Send through REAL CloudChat backend
                // ---------------------------------------------------------

                boolean sent = backendConnection.sendPrivateMessage(
                                recipient.trim(),
                                message.trim());

                if (!sent) {

                        sendError(
                                        senderSocket,
                                        "Failed to send message to CloudChat backend");

                        return;
                }

                System.out.println(
                                "[WS → BACKEND] "
                                                + sender
                                                + " -> "
                                                + recipient
                                                + ": "
                                                + message);
        }

        private String escapeJson(String value) {

                if (value == null) {
                        return "";
                }

                return value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");
        }

        // =========================================================
        // MAIN
        // =========================================================

        public static void main(
                        String[] args) {

                try {

                        ChatWebSocketServer server = new ChatWebSocketServer();

                        server.start();

                        System.out.println(
                                        "[WS] Server startup initiated.");

                } catch (Exception e) {

                        System.out.println(
                                        "[WS] Failed to start WebSocket server.");

                        e.printStackTrace();
                }
        }
}