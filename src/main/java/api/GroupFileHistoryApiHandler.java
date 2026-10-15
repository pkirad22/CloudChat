package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.bson.Document;

import service.GroupFileHistoryService;
import service.GroupService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class GroupFileHistoryApiHandler implements HttpHandler {

    private final GroupService groupService;

    public GroupFileHistoryApiHandler() {
        this.groupService = new GroupService();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            if ("/api/group-files/history".equals(path)) {
                handleHistory(exchange);
            } else if ("/api/group-files/download".equals(path)) {
                handleDownload(exchange);
            } else {
                sendJson(exchange, 404,
                        "{\"success\":false,\"message\":\"Endpoint not found\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();

            if (exchange.getResponseCode() == -1) {
                sendJson(exchange, 500,
                        "{\"success\":false,\"message\":\"Group file request failed\"}");
            } else {
                exchange.close();
            }
        }
    }

    private void handleHistory(HttpExchange exchange) throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        Map<String, String> params = getQueryParams(exchange);

        String groupName = params.get("groupName");
        String username = params.get("username");

        if (isBlank(groupName) || isBlank(username)) {
            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"groupName and username are required\"}");
            return;
        }

        groupName = groupName.trim();
        username = username.trim();

        if (!groupService.groupExists(groupName)) {
            sendJson(exchange, 404,
                    "{\"success\":false,\"message\":\"Group not found\"}");
            return;
        }

        if (!isMember(groupName, username)) {
            sendJson(exchange, 403,
                    "{\"success\":false,\"message\":\"Only current group members can view file history\"}");
            return;
        }

        int limit = 100;

        try {
            String requestedLimit = params.get("limit");
            if (requestedLimit != null) {
                limit = Integer.parseInt(requestedLimit);
            }
        } catch (NumberFormatException ignored) {
            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"Invalid limit\"}");
            return;
        }

        try (GroupFileHistoryService historyService = new GroupFileHistoryService()) {

            List<Document> files = historyService.getGroupFileHistory(groupName, limit);

            StringBuilder json = new StringBuilder();
            json.append("{\"success\":true,\"files\":[");

            for (int i = 0; i < files.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }

                Document file = files.get(i);

                json.append("{")
                        .append("\"transferId\":\"")
                        .append(escapeJson(file.getString("transferId")))
                        .append("\",")
                        .append("\"fileName\":\"")
                        .append(escapeJson(file.getString("fileName")))
                        .append("\",")
                        .append("\"sender\":\"")
                        .append(escapeJson(file.getString("sender")))
                        .append("\",")
                        .append("\"groupName\":\"")
                        .append(escapeJson(file.getString("groupName")))
                        .append("\",")
                        .append("\"fileSize\":")
                        .append(file.get("fileSize", Number.class) == null
                                ? 0
                                : file.get("fileSize", Number.class).longValue())
                        .append(",")
                        .append("\"uploadedAt\":\"")
                        .append(file.getDate("uploadedAt") == null
                                ? ""
                                : file.getDate("uploadedAt").toInstant().toString())
                        .append("\"")
                        .append("}");
            }

            json.append("]}");

            sendJson(exchange, 200, json.toString());
        }
    }

    private void handleDownload(HttpExchange exchange) throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        Map<String, String> params = getQueryParams(exchange);

        String transferId = params.get("transferId");
        String groupName = params.get("groupName");
        String username = params.get("username");

        if (isBlank(transferId) || isBlank(groupName) || isBlank(username)) {
            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"transferId, groupName and username are required\"}");
            return;
        }

        transferId = transferId.trim();
        groupName = groupName.trim();
        username = username.trim();

        if (!groupService.groupExists(groupName)) {
            sendJson(exchange, 404,
                    "{\"success\":false,\"message\":\"Group not found\"}");
            return;
        }

        if (!isMember(groupName, username)) {
            sendJson(exchange, 403,
                    "{\"success\":false,\"message\":\"Only current group members can download group files\"}");
            return;
        }

        try (GroupFileHistoryService historyService = new GroupFileHistoryService()) {

            /*
             * The service downloads by transferId, so first fetch metadata
             * to ensure that the requested file belongs to this group.
             */
            List<Document> records = historyService.getGroupFileHistory(groupName, 200);

            Document selected = null;

            for (Document record : records) {
                if (transferId.equals(record.getString("transferId"))) {
                    selected = record;
                    break;
                }
            }

            if (selected == null) {
                sendJson(exchange, 404,
                        "{\"success\":false,\"message\":\"File not found in this group\"}");
                return;
            }

            String fileName = selected.getString("fileName");
            if (isBlank(fileName)) {
                fileName = "download";
            }

            String safeFileName = fileName
                    .replace("\\", "_")
                    .replace("/", "_")
                    .replace("\"", "_")
                    .replace("\r", "_")
                    .replace("\n", "_");

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set(
                    "Content-Disposition",
                    "attachment; filename=\"" + safeFileName + "\"");
            exchange.getResponseHeaders().set(
                    "Cache-Control", "no-store");

            /*
             * Use a temporary buffer first so we can return a proper 404
             * if the record disappears before streaming begins.
             */
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

            boolean found;

            try {
                found = historyService.downloadGroupFile(
                        transferId,
                        buffer);
            } catch (Exception e) {
                e.printStackTrace();

                sendJson(
                        exchange,
                        500,
                        "{\"success\":false,\"message\":\"Failed to download group file\"}");

                return;
            }

            if (!found) {
                sendJson(exchange, 404,
                        "{\"success\":false,\"message\":\"File not found\"}");
                return;
            }

            byte[] bytes = buffer.toByteArray();

            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private boolean isMember(String groupName, String username) {
        List<String> members = groupService.getMembers(groupName);
        return members != null && members.contains(username);
    }

    private Map<String, String> getQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();

        String query = exchange.getRequestURI().getRawQuery();

        if (query == null || query.isBlank()) {
            return params;
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);

            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";

            params.put(key, value);
        }

        return params;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
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

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void sendJson(
            HttpExchange exchange,
            int status,
            String body) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}