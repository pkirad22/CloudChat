package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import model.ServerStatus;
import service.ServerMonitoringService;

public class MonitoringApiServer {

        private final int port;
        private final int peerApiPort;
        private final ServerMonitoringService monitoringService;

        private final HttpClient httpClient;

        public MonitoringApiServer(
                        int port,
                        String serverId,
                        String peerServer,
                        int peerApiPort) {

                this.port = port;
                this.peerApiPort = peerApiPort;

                this.monitoringService = new ServerMonitoringService(
                                serverId,
                                peerServer);

                this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(
                                                Duration.ofSeconds(2))
                                .build();
        }

        // =========================================================
        // START API SERVER
        // =========================================================

        public void start() {

                try {

                        HttpServer server = HttpServer.create(
                                        new InetSocketAddress(port),
                                        0);

                        // Individual server status
                        server.createContext(
                                        "/api/server/status",
                                        this::handleStatus);

                        // Complete cluster status
                        server.createContext(
                                        "/api/cluster/status",
                                        this::handleClusterStatus);

                        server.createContext(
                                        "/api/server/users",
                                        this::handleOnlineUsers);

                        server.setExecutor(null);

                        server.start();

                        System.out.println(
                                        "[MONITOR API] Server started on port "
                                                        + port);

                        System.out.println(
                                        "[MONITOR API] Server endpoint: "
                                                        + "http://localhost:"
                                                        + port
                                                        + "/api/server/status");

                        System.out.println(
                                        "[MONITOR API] Cluster endpoint: "
                                                        + "http://localhost:"
                                                        + port
                                                        + "/api/cluster/status");

                } catch (IOException e) {

                        System.out.println(
                                        "[MONITOR API] Failed to start: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // SERVER STATUS
        // =========================================================

        private void handleStatus(
                        HttpExchange exchange) {

                try {

                        if (!exchange.getRequestMethod()
                                        .equalsIgnoreCase("GET")) {

                                sendResponse(
                                                exchange,
                                                405,
                                                "{\"error\":\"Method Not Allowed\"}");

                                return;
                        }

                        ServerStatus status = monitoringService
                                        .getCurrentStatus();

                        String json = convertToJson(status);

                        exchange.getResponseHeaders()
                                        .set(
                                                        "Content-Type",
                                                        "application/json");

                        sendResponse(
                                        exchange,
                                        200,
                                        json);

                } catch (Exception e) {

                        try {

                                sendResponse(
                                                exchange,
                                                500,
                                                "{\"error\":\""
                                                                + escapeJson(
                                                                                e.getMessage())
                                                                + "\"}");

                        } catch (IOException ignored) {

                                System.out.println(
                                                "[MONITOR API] "
                                                                + "Failed to send error response.");
                        }
                }
        }

        // =========================================================
        // CLUSTER STATUS
        // =========================================================

        private void handleClusterStatus(
                        HttpExchange exchange) {

                try {

                        if (!exchange.getRequestMethod()
                                        .equalsIgnoreCase("GET")) {

                                sendResponse(
                                                exchange,
                                                405,
                                                "{\"error\":\"Method Not Allowed\"}");

                                return;
                        }

                        // ---------------------------------------------
                        // Get current server status
                        // ---------------------------------------------

                        ServerStatus currentStatus = monitoringService
                                        .getCurrentStatus();

                        String currentJson = convertToJson(currentStatus);

                        // ---------------------------------------------
                        // Get peer server status
                        // ---------------------------------------------

                        String peerJson = getPeerServerStatus();

                        // ---------------------------------------------
                        // Build cluster response
                        // ---------------------------------------------

                        String json = "{"
                                        + "\""
                                        + currentStatus
                                                        .getServerId()
                                        + "\":"
                                        + currentJson
                                        + ","
                                        + "\""
                                        + currentStatus
                                                        .getPeerServer()
                                        + "\":"
                                        + peerJson
                                        + "}";

                        exchange.getResponseHeaders()
                                        .set(
                                                        "Content-Type",
                                                        "application/json");

                        sendResponse(
                                        exchange,
                                        200,
                                        json);

                } catch (Exception e) {

                        try {

                                sendResponse(
                                                exchange,
                                                500,
                                                "{\"error\":\""
                                                                + escapeJson(
                                                                                e.getMessage())
                                                                + "\"}");

                        } catch (IOException ignored) {

                                System.out.println(
                                                "[MONITOR API] "
                                                                + "Failed to send cluster error response.");
                        }
                }
        }

        // =========================================================
        // GET PEER SERVER STATUS
        // =========================================================

        private String getPeerServerStatus() {

                try {

                        String url = "http://localhost:"
                                        + peerApiPort
                                        + "/api/server/status";

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .timeout(
                                                        Duration.ofSeconds(2))
                                        .GET()
                                        .build();

                        HttpResponse<String> response = httpClient.send(
                                        request,
                                        HttpResponse.BodyHandlers
                                                        .ofString());

                        if (response.statusCode() == 200) {

                                return response.body();
                        }

                        return createOfflinePeerResponse(
                                        "HTTP_" + response.statusCode());

                } catch (Exception e) {

                        System.out.println(
                                        "[MONITOR API] Peer server unavailable: "
                                                        + e.getMessage());

                        return createOfflinePeerResponse(
                                        "UNREACHABLE");
                }
        }

        // =========================================================
        // OFFLINE PEER RESPONSE
        // =========================================================

        private String createOfflinePeerResponse(
                        String reason) {

                return "{"
                                + "\"serverId\":\""
                                + escapeJson(
                                                monitoringService
                                                                .getCurrentStatus()
                                                                .getPeerServer())
                                + "\","
                                + "\"status\":\"OFFLINE\","
                                + "\"load\":0,"
                                + "\"loadLevel\":\"OFFLINE\","
                                + "\"cpuUsage\":0,"
                                + "\"memoryUsage\":0,"
                                + "\"onlineUsers\":0,"
                                + "\"activeThreads\":0,"
                                + "\"groups\":0,"
                                + "\"connectedToPeer\":false,"
                                + "\"peerServer\":\"\","
                                + "\"reason\":\""
                                + escapeJson(reason)
                                + "\""
                                + "}";
        }

        // =========================================================
        // CONVERT STATUS TO JSON
        // =========================================================

        private String convertToJson(
                        ServerStatus status) {

                return "{"
                                + "\"serverId\":\""
                                + escapeJson(
                                                status.getServerId())
                                + "\","

                                + "\"status\":\""
                                + escapeJson(
                                                status.getStatus())
                                + "\","

                                + "\"load\":"
                                + status.getLoad()
                                + ","

                                + "\"loadLevel\":\""
                                + escapeJson(
                                                status.getLoadLevel())
                                + "\","

                                + "\"cpuUsage\":"
                                + status.getCpuUsage()
                                + ","

                                + "\"memoryUsage\":"
                                + status.getMemoryUsage()
                                + ","

                                + "\"onlineUsers\":"
                                + status.getOnlineUsers()
                                + ","

                                + "\"activeThreads\":"
                                + status.getActiveThreads()
                                + ","

                                + "\"groups\":"
                                + status.getGroups()
                                + ","

                                + "\"connectedToPeer\":"
                                + status.isConnectedToPeer()
                                + ","

                                + "\"peerServer\":\""
                                + escapeJson(
                                                status.getPeerServer())
                                + "\""

                                + "}";
        }

        // =========================================================
        // SEND RESPONSE
        // =========================================================

        private void sendResponse(
                        HttpExchange exchange,
                        int statusCode,
                        String response)
                        throws IOException {

                byte[] responseBytes = response.getBytes("UTF-8");

                exchange.sendResponseHeaders(
                                statusCode,
                                responseBytes.length);

                try (OutputStream outputStream = exchange.getResponseBody()) {

                        outputStream.write(responseBytes);
                }
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
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"");
        }

        // =========================================================
        // ONLINE USERS
        // =========================================================

        private void handleOnlineUsers(
                        HttpExchange exchange)
                        throws IOException {

                try {

                        if (!exchange.getRequestMethod()
                                        .equalsIgnoreCase("GET")) {

                                sendResponse(
                                                exchange,
                                                405,
                                                "{\"error\":\"Method Not Allowed\"}");

                                return;
                        }

                        ServerStatus currentStatus = monitoringService.getCurrentStatus();

                        String serverId = currentStatus.getServerId();

                        StringBuilder json = new StringBuilder();

                        json.append("{");

                        json.append("\"success\":true,");

                        json.append("\"serverId\":\"")
                                        .append(escapeJson(serverId))
                                        .append("\",");

                        json.append("\"users\":[");

                        boolean first = true;

                        for (String username : ChatServer.getOnlineUsers().keySet()) {

                                if (!first) {
                                        json.append(",");
                                }

                                json.append("{");

                                json.append("\"username\":\"")
                                                .append(escapeJson(username))
                                                .append("\",");

                                json.append("\"server\":\"")
                                                .append(escapeJson(serverId))
                                                .append("\"");

                                json.append("}");

                                first = false;
                        }

                        json.append("]");

                        json.append("}");

                        exchange.getResponseHeaders()
                                        .set(
                                                        "Content-Type",
                                                        "application/json");

                        sendResponse(
                                        exchange,
                                        200,
                                        json.toString());

                } catch (Exception e) {

                        sendResponse(
                                        exchange,
                                        500,
                                        "{\"error\":\""
                                                        + escapeJson(
                                                                        e.getMessage())
                                                        + "\"}");
                }
        }
}