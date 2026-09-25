package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ApiServer {

        private static final int PORT = 9000;

        private HttpServer server;

        private AuthApiHandler authApiHandler;
        private UserApiHandler userApiHandler;
        private GroupApiHandler groupApiHandler;

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

                server.createContext(
                                "/api/auth/check-username",
                                exchange -> {
                                        addCorsHeaders(exchange);
                                        authApiHandler.checkUsername(exchange);
                                });

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
                                "[API] CORS enabled for React: http://localhost:5173");

                System.out.println("==========================================");
                System.out.println();
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
                                + "\"port\":" + PORT
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

                        outputStream.write(responseBytes);
                }
        }
}