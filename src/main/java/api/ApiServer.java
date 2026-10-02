package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ApiServer {

        private static final int PORT = 9000;

        private HttpServer server;

        private AuthApiHandler authApiHandler;
        private UserApiHandler userApiHandler;
        private GroupApiHandler groupApiHandler;
        private MessageApiHandler messageApiHandler;

        public void start() throws IOException {

                server = HttpServer.create(
                                new InetSocketAddress(PORT),
                                0);

                // =====================================================
                // API HANDLERS
                // =====================================================

                authApiHandler = new AuthApiHandler();
                userApiHandler = new UserApiHandler();
                groupApiHandler = new GroupApiHandler();
                messageApiHandler = new MessageApiHandler();

                // =====================================================
                // HEALTH CHECK
                // =====================================================

                server.createContext(
                                "/api/health",
                                withCors(this::handleHealth));

                // =====================================================
                // AUTHENTICATION
                // =====================================================

                server.createContext(
                                "/api/auth/login",
                                withCors(authApiHandler::handleLogin));

                // =====================================================
                // REGISTRATION
                // =====================================================

                server.createContext(
                                "/api/auth/register",
                                withCors(authApiHandler::handleRegister));

                // =====================================================
                // USERS
                // =====================================================

                server.createContext(
                                "/api/users/me",
                                withCors(userApiHandler::handleMe));

                server.createContext(
                                "/api/users/online",
                                withCors(userApiHandler::handleOnlineUsers));

                server.createContext(
                                "/api/users/search",
                                withCors(userApiHandler::handleSearchUsers));

                // =====================================================
                // PRIVATE MESSAGE HISTORY
                // =====================================================

                server.createContext(
                                "/api/messages/history",
                                withCors(messageApiHandler::getPrivateChatHistory));

                // =====================================================
                // GROUPS
                // =====================================================

                server.createContext(
                                "/api/groups",
                                withCors(groupApiHandler::handleGroups));

                server.createContext(
                                "/api/groups/",
                                withCors(exchange -> {

                                        String path = exchange
                                                        .getRequestURI()
                                                        .getPath();

                                        String method = exchange
                                                        .getRequestMethod();

                                        if (path.endsWith("/members")) {

                                                groupApiHandler.handleMembers(
                                                                exchange);

                                        } else if (path.endsWith("/owners")) {

                                                groupApiHandler.handleOwners(
                                                                exchange);

                                        } else if (path.endsWith("/leave")
                                                        && method.equalsIgnoreCase("POST")) {

                                                groupApiHandler.handleLeaveGroup(
                                                                exchange);

                                        } else if (path.endsWith("/join")
                                                        && method.equalsIgnoreCase("POST")) {

                                                groupApiHandler.handleJoinGroup(
                                                                exchange);

                                        } else if (path.endsWith("/request-join")
                                                        && method.equalsIgnoreCase("POST")) {

                                                groupApiHandler.handleRequestJoin(
                                                                exchange);

                                        } else if (path.endsWith("/access")
                                                        && method.equalsIgnoreCase("POST")) {

                                                groupApiHandler.handleSetAccessMode(
                                                                exchange);

                                        } else if (path.endsWith("/requests")
                                                        && method.equalsIgnoreCase("GET")) {

                                                groupApiHandler.handlePendingRequests(
                                                                exchange);

                                        } else if (path.endsWith("/approve")
                                                        && method.equalsIgnoreCase("POST")) {

                                                groupApiHandler.handleApproveRequest(
                                                                exchange);

                                        } else if (path.endsWith("/reject")
                                                        && method.equalsIgnoreCase("POST")) {

                                                groupApiHandler.handleRejectRequest(
                                                                exchange);

                                        } else if (method.equalsIgnoreCase("DELETE")) {

                                                groupApiHandler.handleDeleteGroup(
                                                                exchange);

                                        } else {

                                                groupApiHandler.handleGroupDetails(
                                                                exchange);
                                        }

                                }));

                // =====================================================
                // CHECK USERNAME
                // =====================================================

                server.createContext(
                                "/api/auth/check-username",
                                exchange -> {

                                        addCorsHeaders(exchange);

                                        authApiHandler.checkUsername(exchange);
                                });

                // =====================================================
                // ONLINE USERS
                // =====================================================

                server.createContext(
                                "/api/users/online",
                                exchange -> {

                                        addCorsHeaders(exchange);

                                        userApiHandler.handleOnlineUsers(exchange);
                                });

                // =====================================================
                // CREATE GROUP
                // =====================================================

                server.createContext(
                                "/api/groups/create",
                                withCors(groupApiHandler::handleCreateGroup));

                // =====================================================
                // INTERNAL FILE DELIVERY
                // =====================================================
                //
                // This endpoint is NOT for React.
                //
                // Server 1 / Server 2 can call this endpoint when they
                // need the WebSocket Bridge JVM to deliver a file to
                // a browser user.
                //
                // =====================================================

                server.createContext(
                                "/internal/files/deliver",
                                this::handleInternalFileDelivery);

                // =====================================================
                // START SERVER
                // =====================================================

                server.start();

                System.out.println();
                System.out.println("==========================================");
                System.out.println("        CLOUDCHAT API BRIDGE");
                System.out.println("==========================================");

                System.out.println(
                                "[API] Bridge started on port " + PORT);

                System.out.println(
                                "[API] Health endpoint: http://localhost:"
                                                + PORT
                                                + "/api/health");

                System.out.println(
                                "[API] Login endpoint: http://localhost:"
                                                + PORT
                                                + "/api/auth/login");

                System.out.println(
                                "[API] Internal file delivery endpoint: http://localhost:"
                                                + PORT
                                                + "/internal/files/deliver");

                System.out.println(
                                "[API] CORS enabled for React: http://localhost:5173");

                System.out.println("==========================================");
                System.out.println();
        }

        // =========================================================
        // INTERNAL FILE DELIVERY
        // =========================================================

        private void handleInternalFileDelivery(
                        HttpExchange exchange) {

                try {

                        // -------------------------------------------------
                        // Only POST is allowed
                        // -------------------------------------------------

                        if (!exchange.getRequestMethod()
                                        .equalsIgnoreCase("POST")) {

                                sendResponse(
                                                exchange,
                                                405,
                                                "{\"success\":false,\"message\":\"Method Not Allowed\"}");

                                return;
                        }

                        // -------------------------------------------------
                        // Security check
                        // -------------------------------------------------
                        //
                        // This endpoint is intended only for local
                        // Server 1 / Server 2 communication.
                        //
                        // -------------------------------------------------

                        InetSocketAddress remoteAddress = exchange.getRemoteAddress();

                        if (remoteAddress == null
                                        || remoteAddress.getAddress() == null
                                        || !remoteAddress.getAddress().isLoopbackAddress()) {

                                System.out.println(
                                                "[API-FILE] Rejected non-local request.");

                                sendResponse(
                                                exchange,
                                                403,
                                                "{\"success\":false,\"message\":\"Forbidden\"}");

                                return;
                        }

                        // -------------------------------------------------
                        // Read request body
                        // -------------------------------------------------

                        String requestBody;

                        try (InputStream inputStream = exchange.getRequestBody()) {

                                requestBody = new String(
                                                inputStream.readAllBytes(),
                                                StandardCharsets.UTF_8);
                        }

                        Map<String, String> params = parseFormData(requestBody);

                        String recipient = params.get("recipient");

                        String sender = params.get("sender");

                        String filePath = params.get("filePath");

                        // -------------------------------------------------
                        // Validate parameters
                        // -------------------------------------------------

                        if (recipient == null
                                        || recipient.trim().isEmpty()) {

                                sendResponse(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Recipient is required\"}");

                                return;
                        }

                        if (sender == null
                                        || sender.trim().isEmpty()) {

                                sendResponse(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"Sender is required\"}");

                                return;
                        }

                        if (filePath == null
                                        || filePath.trim().isEmpty()) {

                                sendResponse(
                                                exchange,
                                                400,
                                                "{\"success\":false,\"message\":\"File path is required\"}");

                                return;
                        }

                        System.out.println();
                        System.out.println(
                                        "[API-FILE] =====================================");

                        System.out.println(
                                        "[API-FILE] Internal file delivery request");

                        System.out.println(
                                        "[API-FILE] Sender: "
                                                        + sender);

                        System.out.println(
                                        "[API-FILE] Recipient: "
                                                        + recipient);

                        System.out.println(
                                        "[API-FILE] File: "
                                                        + filePath);

                        System.out.println(
                                        "[API-FILE] =====================================");

                        // -------------------------------------------------
                        // Let the WebSocket Bridge deliver the file.
                        //
                        // IMPORTANT:
                        // This code executes inside the API/WebSocket
                        // Bridge JVM, so userSessions is available here.
                        // -------------------------------------------------

                        boolean delivered = WebSocketBridgeServer
                                        .deliverFileFromInternalRequest(
                                                        recipient,
                                                        sender,
                                                        filePath);

                        if (delivered) {

                                sendResponse(
                                                exchange,
                                                200,
                                                "{\"success\":true,\"message\":\"File delivered\"}");

                        } else {

                                sendResponse(
                                                exchange,
                                                404,
                                                "{\"success\":false,\"message\":\"Recipient WebSocket session not found or delivery failed\"}");
                        }

                } catch (Exception e) {

                        System.out.println(
                                        "[API-FILE] Internal delivery error: "
                                                        + e.getMessage());

                        e.printStackTrace();

                        try {

                                sendResponse(
                                                exchange,
                                                500,
                                                "{\"success\":false,\"message\":\"Internal file delivery error\"}");

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
        // PARSE FORM DATA
        // =========================================================

        private Map<String, String> parseFormData(
                        String body) {

                Map<String, String> result = new HashMap<>();

                if (body == null
                                || body.trim().isEmpty()) {

                        return result;
                }

                String[] pairs = body.split("&");

                for (String pair : pairs) {

                        int separator = pair.indexOf('=');

                        if (separator <= 0) {
                                continue;
                        }

                        String key = URLDecoder.decode(
                                        pair.substring(
                                                        0,
                                                        separator),
                                        StandardCharsets.UTF_8);

                        String value = URLDecoder.decode(
                                        pair.substring(
                                                        separator + 1),
                                        StandardCharsets.UTF_8);

                        result.put(
                                        key,
                                        value);
                }

                return result;
        }

        // =========================================================
        // CORS WRAPPER
        // =========================================================

        private HttpHandler withCors(
                        HttpHandler handler) {

                return exchange -> {

                        addCorsHeaders(exchange);

                        // -----------------------------------------
                        // Browser preflight request
                        // -----------------------------------------

                        if (exchange.getRequestMethod()
                                        .equalsIgnoreCase("OPTIONS")) {

                                exchange.sendResponseHeaders(
                                                204,
                                                -1);

                                exchange.close();

                                return;
                        }

                        // -----------------------------------------
                        // Continue to actual API handler
                        // -----------------------------------------

                        handler.handle(exchange);
                };
        }

        // =========================================================
        // ADD CORS HEADERS
        // =========================================================

        private void addCorsHeaders(
                        HttpExchange exchange) {

                exchange.getResponseHeaders().set(
                                "Access-Control-Allow-Origin",
                                "http://localhost:5173");

                exchange.getResponseHeaders().set(
                                "Access-Control-Allow-Methods",
                                "GET, POST, PUT, DELETE, OPTIONS");

                exchange.getResponseHeaders().set(
                                "Access-Control-Allow-Headers",
                                "Content-Type, Authorization");

                exchange.getResponseHeaders().set(
                                "Access-Control-Allow-Credentials",
                                "true");

                exchange.getResponseHeaders().set(
                                "Access-Control-Max-Age",
                                "3600");
        }

        // =========================================================
        // HEALTH CHECK
        // =========================================================

        private void handleHealth(
                        HttpExchange exchange)
                        throws IOException {

                if (!exchange.getRequestMethod()
                                .equalsIgnoreCase("GET")) {

                        sendResponse(
                                        exchange,
                                        405,
                                        "{\"error\":\"Method Not Allowed\"}");

                        return;
                }

                String response = "{"
                                + "\"status\":\"ONLINE\","
                                + "\"service\":\"CloudChat API Bridge\","
                                + "\"port\":"
                                + PORT
                                + "}";

                sendResponse(
                                exchange,
                                200,
                                response);
        }

        // =========================================================
        // SEND RESPONSE
        // =========================================================

        private void sendResponse(
                        HttpExchange exchange,
                        int statusCode,
                        String response)
                        throws IOException {

                byte[] responseBytes = response.getBytes(
                                StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                                "Content-Type",
                                "application/json; charset=UTF-8");

                exchange.sendResponseHeaders(
                                statusCode,
                                responseBytes.length);

                try (OutputStream outputStream = exchange.getResponseBody()) {

                        outputStream.write(
                                        responseBytes);
                }
        }
}