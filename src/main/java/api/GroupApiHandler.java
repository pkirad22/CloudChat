package api;

import com.sun.net.httpserver.HttpExchange;

import model.User;
import service.GroupService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GroupApiHandler {

    private final GroupService groupService;

    public GroupApiHandler() {
        groupService = new GroupService();
    }

    // =========================================================
    // GET /api/groups
    // =========================================================

    public void handleGroups(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendJson(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        List<String> groups = groupService.getAllGroups();

        String response = "{"
                + "\"success\":true,"
                + "\"groups\":"
                + stringListToJson(groups)
                + "}";

        sendJson(exchange, 200, response);
    }

    // =========================================================
    // GET /api/groups/{groupName}
    // =========================================================

    public void handleGroupDetails(
            HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendJson(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String groupName = extractGroupName(exchange);

        if (groupName == null
                || groupName.isEmpty()) {

            sendJson(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        if (!groupService.groupExists(groupName)) {

            sendJson(
                    exchange,
                    404,
                    "{\"success\":false,\"message\":\"Group not found\"}");

            return;
        }

        List<String> members = groupService.getMembers(groupName);

        List<String> owners = groupService.getOwners(groupName);

        String joinCode = groupService.getJoinCode(groupName);

        String accessMode = groupService.getAccessMode(groupName);

        String response = "{"
                + "\"success\":true,"
                + "\"group\":{"
                + "\"groupName\":\""
                + escapeJson(groupName)
                + "\","
                + "\"members\":"
                + stringListToJson(members)
                + ","
                + "\"owners\":"
                + stringListToJson(owners)
                + ","
                + "\"joinCode\":\""
                + escapeJson(joinCode)
                + "\","
                + "\"accessMode\":\""
                + escapeJson(accessMode)
                + "\""
                + "}"
                + "}";

        sendJson(exchange, 200, response);
    }

    // =========================================================
    // GET /api/groups/{groupName}/members
    // =========================================================

    public void handleMembers(
            HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendJson(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String groupName = extractGroupName(exchange);

        if (!groupService.groupExists(groupName)) {

            sendJson(
                    exchange,
                    404,
                    "{\"success\":false,\"message\":\"Group not found\"}");

            return;
        }

        List<String> members = groupService.getMembers(groupName);

        String response = "{"
                + "\"success\":true,"
                + "\"groupName\":\""
                + escapeJson(groupName)
                + "\","
                + "\"members\":"
                + stringListToJson(members)
                + "}";

        sendJson(exchange, 200, response);
    }

    // =========================================================
    // GET /api/groups/{groupName}/owners
    // =========================================================

    public void handleOwners(
            HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendJson(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String groupName = extractGroupName(exchange);

        if (!groupService.groupExists(groupName)) {

            sendJson(
                    exchange,
                    404,
                    "{\"success\":false,\"message\":\"Group not found\"}");

            return;
        }

        List<String> owners = groupService.getOwners(groupName);

        String response = "{"
                + "\"success\":true,"
                + "\"groupName\":\""
                + escapeJson(groupName)
                + "\","
                + "\"owners\":"
                + stringListToJson(owners)
                + "}";

        sendJson(exchange, 200, response);
    }

    // =========================================================
    // POST /api/groups/create
    // =========================================================

    public void handleCreateGroup(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String groupName = extractJsonValue(body, "groupName");

        String creator = extractJsonValue(body, "creator");

        if (groupName == null || groupName.isBlank()
                || creator == null || creator.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"groupName and creator are required\"}");

            return;
        }

        groupName = groupName.trim();
        creator = creator.trim();

        try {

            if (groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        409,
                        "{\"success\":false,\"message\":\"Group already exists\"}");

                return;
            }

            boolean created = groupService.createGroup(
                    groupName,
                    creator);

            if (!created) {

                sendResponse(
                        exchange,
                        500,
                        "{\"success\":false,\"message\":\"Failed to create group\"}");

                return;
            }

            String joinCode = groupService.getJoinCode(groupName);

            String accessMode = groupService.getAccessMode(groupName);

            String response = "{"
                    + "\"success\":true,"
                    + "\"message\":\"Group created successfully\","
                    + "\"groupName\":\"" + groupName + "\","
                    + "\"creator\":\"" + creator + "\","
                    + "\"joinCode\":\"" + joinCode + "\","
                    + "\"accessMode\":\"" + accessMode + "\""
                    + "}";

            sendResponse(
                    exchange,
                    200,
                    response);

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to create group\"}");
        }
    }

    // =========================================================
    // DELETE /api/groups/{groupName}
    // =========================================================

    public void handleDeleteGroup(
            HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("DELETE")) {

            sendJson(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String groupName = extractGroupName(exchange);

        if (groupName == null
                || groupName.isEmpty()) {

            sendJson(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        if (!groupService.groupExists(groupName)) {

            sendJson(
                    exchange,
                    404,
                    "{\"success\":false,\"message\":\"Group not found\"}");

            return;
        }

        boolean deleted = groupService.deleteGroup(groupName);

        if (!deleted) {

            sendJson(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Failed to delete group\"}");

            return;
        }

        String response = "{"
                + "\"success\":true,"
                + "\"message\":\"Group deleted successfully\","
                + "\"groupName\":\""
                + escapeJson(groupName)
                + "\""
                + "}";

        sendJson(exchange, 200, response);
    }

    // =========================================================
    // JOIN GROUP
    // =========================================================

    public void handleJoinGroup(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String path = exchange.getRequestURI().getPath();

        // Expected:
        // /api/groups/{groupName}/join

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)
                || !path.endsWith("/join")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid group path\"}");

            return;
        }

        String groupName = path.substring(
                prefix.length(),
                path.length() - "/join".length());

        groupName = groupName.trim();

        if (groupName.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String username = extractJsonValue(body, "username");

        String joinCode = extractJsonValue(body, "joinCode");

        if (username == null || username.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Username is required\"}");

            return;
        }

        username = username.trim();

        try {

            // Check whether group exists
            if (!groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"Group not found\"}");

                return;
            }

            // Already a member?
            List<String> members = groupService.getMembers(groupName);

            if (members.contains(username)) {

                sendResponse(
                        exchange,
                        409,
                        "{\"success\":false,\"message\":\"User is already a member of this group\"}");

                return;
            }

            String accessMode = groupService.getAccessMode(groupName);

            /*
             * CODE mode requires a valid join code.
             */
            if ("CODE".equalsIgnoreCase(accessMode)) {

                if (joinCode == null || joinCode.isBlank()) {

                    sendResponse(
                            exchange,
                            400,
                            "{\"success\":false,\"message\":\"Join code is required\"}");

                    return;
                }

                String actualJoinCode = groupService.getJoinCode(groupName);

                if (!actualJoinCode.equals(joinCode.trim())) {

                    sendResponse(
                            exchange,
                            403,
                            "{\"success\":false,\"message\":\"Invalid join code\"}");

                    return;
                }

                boolean joined = groupService.joinGroupWithCode(
                        groupName,
                        username,
                        joinCode.trim());

                if (!joined) {

                    sendResponse(
                            exchange,
                            400,
                            "{\"success\":false,\"message\":\"Unable to join group\"}");

                    return;
                }

                sendResponse(
                        exchange,
                        200,
                        "{"
                                + "\"success\":true,"
                                + "\"message\":\"Joined group successfully\","
                                + "\"groupName\":\"" + groupName + "\","
                                + "\"username\":\"" + username + "\""
                                + "}");

                return;
            }

            /*
             * OPEN mode allows direct joining.
             */
            if ("OPEN".equalsIgnoreCase(accessMode)) {

                boolean joined = groupService.addMember(
                        groupName,
                        username);

                if (!joined) {

                    sendResponse(
                            exchange,
                            400,
                            "{\"success\":false,\"message\":\"Unable to join group\"}");

                    return;
                }

                sendResponse(
                        exchange,
                        200,
                        "{"
                                + "\"success\":true,"
                                + "\"message\":\"Joined group successfully\","
                                + "\"groupName\":\"" + groupName + "\","
                                + "\"username\":\"" + username + "\""
                                + "}");

                return;
            }

            /*
             * REQUEST mode:
             * Direct joining is not allowed.
             * User must send a join request.
             */
            if ("REQUEST".equalsIgnoreCase(accessMode)) {

                sendResponse(
                        exchange,
                        403,
                        "{"
                                + "\"success\":false,"
                                + "\"message\":\"This group requires an approval request\","
                                + "\"accessMode\":\"REQUEST\""
                                + "}");

                return;
            }

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Unknown group access mode\"}");

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to join group\"}");
        }
    }

    public void handleLeaveGroup(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String path = exchange.getRequestURI().getPath();

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)
                || !path.endsWith("/leave")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid group path\"}");

            return;
        }

        String groupName = path.substring(
                prefix.length(),
                path.length() - "/leave".length());

        groupName = groupName.trim();

        if (groupName.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String username = extractJsonValue(body, "username");

        if (username == null || username.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Username is required\"}");

            return;
        }

        username = username.trim();

        try {

            // Check group exists
            if (!groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"Group not found\"}");

                return;
            }

            // Check user is actually a member
            List<String> members = groupService.getMembers(groupName);

            if (!members.contains(username)) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"User is not a member of this group\"}");

                return;
            }

            // Remove member using existing GroupService logic
            boolean removed = groupService.removeMember(
                    groupName,
                    username);

            if (!removed) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Unable to leave group\"}");

                return;
            }

            String response = "{"
                    + "\"success\":true,"
                    + "\"message\":\"Left group successfully\","
                    + "\"groupName\":\"" + groupName + "\","
                    + "\"username\":\"" + username + "\""
                    + "}";

            sendResponse(
                    exchange,
                    200,
                    response);

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to leave group\"}");
        }
    }

    public void handleRequestJoin(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String path = exchange.getRequestURI().getPath();

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)
                || !path.endsWith("/request-join")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid group path\"}");

            return;
        }

        String groupName = path.substring(
                prefix.length(),
                path.length() - "/request-join".length());

        groupName = groupName.trim();

        if (groupName.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String username = extractJsonValue(body, "username");

        if (username == null || username.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Username is required\"}");

            return;
        }

        username = username.trim();

        try {

            // Check group exists
            if (!groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"Group not found\"}");

                return;
            }

            // Check whether user is already a member
            List<String> members = groupService.getMembers(groupName);

            if (members.contains(username)) {

                sendResponse(
                        exchange,
                        409,
                        "{\"success\":false,\"message\":\"User is already a member of this group\"}");

                return;
            }

            // Check whether request already exists
            List<String> pendingRequests = groupService.getPendingRequests(groupName);

            if (pendingRequests.contains(username)) {

                sendResponse(
                        exchange,
                        409,
                        "{\"success\":false,\"message\":\"Join request already exists\"}");

                return;
            }

            String accessMode = groupService.getAccessMode(groupName);

            // Request mode is intended for approval requests
            if (!"REQUEST".equalsIgnoreCase(accessMode)) {

                sendResponse(
                        exchange,
                        400,
                        "{"
                                + "\"success\":false,"
                                + "\"message\":\"This group does not use request-based joining\","
                                + "\"accessMode\":\"" + accessMode + "\""
                                + "}");

                return;
            }

            boolean requested = groupService.addJoinRequest(
                    groupName,
                    username);

            if (!requested) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Unable to submit join request\"}");

                return;
            }

            String response = "{"
                    + "\"success\":true,"
                    + "\"message\":\"Join request submitted successfully\","
                    + "\"groupName\":\"" + groupName + "\","
                    + "\"username\":\"" + username + "\""
                    + "}";

            sendResponse(
                    exchange,
                    200,
                    response);

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to submit join request\"}");
        }
    }

    public void handleSetAccessMode(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String path = exchange.getRequestURI().getPath();

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)
                || !path.endsWith("/access")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid group path\"}");

            return;
        }

        String groupName = path.substring(
                prefix.length(),
                path.length() - "/access".length());

        groupName = groupName.trim();

        if (groupName.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String accessMode = extractJsonValue(body, "accessMode");

        if (accessMode == null || accessMode.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"accessMode is required\"}");

            return;
        }

        accessMode = accessMode.trim().toUpperCase();

        // Only the three modes supported by CloudChat
        if (!accessMode.equals("OPEN")
                && !accessMode.equals("CODE")
                && !accessMode.equals("REQUEST")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid access mode. Use OPEN, CODE or REQUEST\"}");

            return;
        }

        try {

            if (!groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"Group not found\"}");

                return;
            }

            boolean updated = groupService.setAccessMode(
                    groupName,
                    accessMode);

            if (!updated) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Unable to update access mode\"}");

                return;
            }

            String joinCode = groupService.getJoinCode(groupName);

            String response = "{"
                    + "\"success\":true,"
                    + "\"message\":\"Access mode updated successfully\","
                    + "\"groupName\":\"" + groupName + "\","
                    + "\"accessMode\":\"" + accessMode + "\","
                    + "\"joinCode\":\"" + joinCode + "\""
                    + "}";

            sendResponse(
                    exchange,
                    200,
                    response);

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to update access mode\"}");
        }
    }

    public void handlePendingRequests(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String path = exchange.getRequestURI().getPath();

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)
                || !path.endsWith("/requests")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid group path\"}");

            return;
        }

        String groupName = path.substring(
                prefix.length(),
                path.length() - "/requests".length());

        groupName = groupName.trim();

        if (groupName.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        try {

            if (!groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"Group not found\"}");

                return;
            }

            List<String> requests = groupService.getPendingRequests(
                    groupName);

            StringBuilder json = new StringBuilder();

            json.append("{");
            json.append("\"success\":true,");
            json.append("\"groupName\":\"")
                    .append(groupName)
                    .append("\",");
            json.append("\"requests\":[");

            for (int i = 0; i < requests.size(); i++) {

                if (i > 0) {
                    json.append(",");
                }

                json.append("\"")
                        .append(requests.get(i))
                        .append("\"");
            }

            json.append("]");
            json.append("}");

            sendResponse(
                    exchange,
                    200,
                    json.toString());

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to get pending requests\"}");
        }
    }

    public void handleApproveRequest(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String path = exchange.getRequestURI().getPath();

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)
                || !path.endsWith("/approve")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid group path\"}");

            return;
        }

        String groupName = path.substring(
                prefix.length(),
                path.length() - "/approve".length());

        groupName = groupName.trim();

        if (groupName.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        try {

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String username = extractJsonValue(
                    body,
                    "username");

            String requestedBy = extractJsonValue(
                    body,
                    "requestedBy");

            // -------------------------------------------------
            // Validate request fields
            // -------------------------------------------------

            if (username == null
                    || username.isBlank()) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Username is required\"}");

                return;
            }

            if (requestedBy == null
                    || requestedBy.isBlank()) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"requestedBy is required\"}");

                return;
            }

            username = username.trim();
            requestedBy = requestedBy.trim();

            // -------------------------------------------------
            // Check group
            // -------------------------------------------------

            if (!groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"Group not found\"}");

                return;
            }

            // -------------------------------------------------
            // Check owner authorization
            // -------------------------------------------------

            List<String> owners = groupService.getOwners(groupName);

            if (!owners.contains(requestedBy)) {

                sendResponse(
                        exchange,
                        403,
                        "{"
                                + "\"success\":false,"
                                + "\"message\":\"Only group owners can approve join requests\""
                                + "}");

                return;
            }

            // -------------------------------------------------
            // Check pending request
            // -------------------------------------------------

            List<String> pendingRequests = groupService.getPendingRequests(
                    groupName);

            if (!pendingRequests.contains(username)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"No pending request found for this user\"}");

                return;
            }

            // -------------------------------------------------
            // Approve request
            // -------------------------------------------------

            boolean approved = groupService.approveJoinRequest(
                    groupName,
                    username);

            if (!approved) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Failed to approve join request\"}");

                return;
            }

            sendResponse(
                    exchange,
                    200,
                    "{"
                            + "\"success\":true,"
                            + "\"message\":\"Join request approved successfully\","
                            + "\"groupName\":\""
                            + groupName
                            + "\","
                            + "\"username\":\""
                            + username
                            + "\","
                            + "\"approvedBy\":\""
                            + requestedBy
                            + "\""
                            + "}");

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to approve join request\"}");
        }
    }

    public void handleRejectRequest(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    "{\"success\":false,\"message\":\"Method not allowed\"}");

            return;
        }

        String path = exchange.getRequestURI().getPath();

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)
                || !path.endsWith("/reject")) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Invalid group path\"}");

            return;
        }

        String groupName = path.substring(
                prefix.length(),
                path.length() - "/reject".length());

        groupName = groupName.trim();

        if (groupName.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"success\":false,\"message\":\"Group name is required\"}");

            return;
        }

        try {

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String username = extractJsonValue(
                    body,
                    "username");

            String requestedBy = extractJsonValue(
                    body,
                    "requestedBy");

            // -------------------------------------------------
            // Validate fields
            // -------------------------------------------------

            if (username == null
                    || username.isBlank()) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Username is required\"}");

                return;
            }

            if (requestedBy == null
                    || requestedBy.isBlank()) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"requestedBy is required\"}");

                return;
            }

            username = username.trim();
            requestedBy = requestedBy.trim();

            // -------------------------------------------------
            // Check group
            // -------------------------------------------------

            if (!groupService.groupExists(groupName)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"Group not found\"}");

                return;
            }

            // -------------------------------------------------
            // Check owner authorization
            // -------------------------------------------------

            List<String> owners = groupService.getOwners(groupName);

            if (!owners.contains(requestedBy)) {

                sendResponse(
                        exchange,
                        403,
                        "{"
                                + "\"success\":false,"
                                + "\"message\":\"Only group owners can reject join requests\""
                                + "}");

                return;
            }

            // -------------------------------------------------
            // Check pending request
            // -------------------------------------------------

            List<String> pendingRequests = groupService.getPendingRequests(
                    groupName);

            if (!pendingRequests.contains(username)) {

                sendResponse(
                        exchange,
                        404,
                        "{\"success\":false,\"message\":\"No pending request found for this user\"}");

                return;
            }

            // -------------------------------------------------
            // Reject request
            // -------------------------------------------------

            boolean rejected = groupService.rejectJoinRequest(
                    groupName,
                    username);

            if (!rejected) {

                sendResponse(
                        exchange,
                        400,
                        "{\"success\":false,\"message\":\"Failed to reject join request\"}");

                return;
            }

            sendResponse(
                    exchange,
                    200,
                    "{"
                            + "\"success\":true,"
                            + "\"message\":\"Join request rejected successfully\","
                            + "\"groupName\":\""
                            + groupName
                            + "\","
                            + "\"username\":\""
                            + username
                            + "\","
                            + "\"rejectedBy\":\""
                            + requestedBy
                            + "\""
                            + "}");

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"success\":false,\"message\":\"Failed to reject join request\"}");
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private String extractGroupName(
            HttpExchange exchange) {

        String path = exchange.getRequestURI()
                .getPath();

        String prefix = "/api/groups/";

        if (!path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());

        if (remaining.isEmpty()) {
            return null;
        }

        int slash = remaining.indexOf('/');

        if (slash >= 0) {
            remaining = remaining.substring(0, slash);
        }

        return URLDecoder.decode(
                remaining,
                StandardCharsets.UTF_8);
    }

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

    private String stringListToJson(
            List<String> values) {

        StringBuilder json = new StringBuilder("[");

        boolean first = true;

        for (String value : values) {

            if (!first) {
                json.append(",");
            }

            json.append("\"")
                    .append(escapeJson(value))
                    .append("\"");

            first = false;
        }

        json.append("]");

        return json.toString();
    }

    private String escapeJson(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

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

    private void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String response)
            throws IOException {

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json");

        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {

            outputStream.write(responseBytes);
        }
    }
}