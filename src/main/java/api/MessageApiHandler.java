package api;

import com.sun.net.httpserver.HttpExchange;
import model.Message;
import service.ChatHistoryService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MessageApiHandler {

    private final ChatHistoryService chatHistoryService;

    public MessageApiHandler() {
        chatHistoryService = new ChatHistoryService();
    }

    // =========================================================
    // GET PRIVATE CHAT HISTORY
    //
    // /api/messages/history?username=Pranav&with=Rahul
    // =========================================================

    public void getPrivateChatHistory(
            HttpExchange exchange) throws IOException {

        if (!"GET".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            sendJson(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String query = exchange.getRequestURI().getRawQuery();

        String username = getQueryParameter(query, "username");

        String otherUser = getQueryParameter(query, "with");

        if (username == null ||
                username.trim().isEmpty() ||
                otherUser == null ||
                otherUser.trim().isEmpty()) {

            sendJson(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"username and with are required\"}");

            return;
        }

        username = username.trim();
        otherUser = otherUser.trim();

        try {

            List<Message> messages = chatHistoryService.getPrivateChatHistory(
                    username,
                    otherUser);

            StringBuilder json = new StringBuilder();

            json.append("{");
            json.append("\"success\":true,");
            json.append("\"messages\":[");

            for (int i = 0; i < messages.size(); i++) {

                Message message = messages.get(i);

                if (i > 0) {
                    json.append(",");
                }

                json.append("{");

                json.append("\"sender\":\"")
                        .append(escapeJson(
                                message.getSender()))
                        .append("\",");

                json.append("\"receiver\":\"")
                        .append(escapeJson(
                                message.getReceiver()))
                        .append("\",");

                json.append("\"message\":\"")
                        .append(escapeJson(
                                message.getMessage()))
                        .append("\",");

                json.append("\"messageType\":\"")
                        .append(escapeJson(
                                message.getMessageType()))
                        .append("\",");

                json.append("\"timestamp\":\"")
                        .append(escapeJson(
                                message.getTimestamp()
                                        .toString()))
                        .append("\"");

                json.append("}");
            }

            json.append("]");

            json.append("}");

            sendJson(
                    exchange,
                    200,
                    json.toString());

        } catch (Exception e) {

            e.printStackTrace();

            sendJson(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\""
                            + escapeJson(
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Unable to load message history")
                            + "\"}");
        }
    }

    // =========================================================
    // QUERY PARAMETER
    // =========================================================

    private String getQueryParameter(
            String query,
            String parameter) {

        if (query == null ||
                query.isEmpty()) {

            return null;
        }

        String[] parameters = query.split("&");

        for (String param : parameters) {

            String[] pair = param.split("=", 2);

            if (pair.length == 2 &&
                    pair[0].equals(parameter)) {

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
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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