package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerSynchronizer {

        // =========================================================
        // CONFIGURATION
        // =========================================================

        private static final int SYNC_PORT = 6000;

        private static final String SERVER_ADDRESS = "localhost";

        private static final int HEARTBEAT_INTERVAL = 5000;

        private static final int RETRY_INTERVAL = 3000;

        // =========================================================
        // SERVER IDENTITY
        // =========================================================

        private final boolean primaryServer;

        // =========================================================
        // CONNECTION
        // =========================================================

        private volatile Socket syncSocket;

        private volatile BufferedReader input;

        private volatile PrintWriter output;

        private volatile boolean running = true;

        private volatile boolean connected = false;

        // =========================================================
        // REMOTE STATE
        // =========================================================

        private final Set<String> remoteOnlineUsers = ConcurrentHashMap.newKeySet();

        // =========================================================
        // WRITE LOCK
        // =========================================================

        private final Object writeLock = new Object();

        // =========================================================
        // DISTRIBUTED LOAD INFORMATION
        // =========================================================

        private static volatile double remoteLoadScore = -1;

        private static volatile int remoteConnectedClients = 0;

        private static volatile double remoteCpuUsage = -1;

        private static volatile double remoteMemoryUsage = -1;

        private static volatile int remoteActiveThreads = 0;

        // =========================================================
        // CONSTRUCTOR
        // =========================================================

        public ServerSynchronizer(
                        boolean primaryServer) {

                this.primaryServer = primaryServer;
        }

        // =========================================================
        // START
        // =========================================================

        public void start() {

                Thread synchronizerThread = new Thread(
                                this::runSynchronizer,
                                primaryServer
                                                ? "Server1-Synchronizer"
                                                : "Server2-Synchronizer");

                synchronizerThread.setDaemon(true);

                synchronizerThread.start();
        }

        // =========================================================
        // RUN SYNCHRONIZER
        // =========================================================

        private void runSynchronizer() {

                if (primaryServer) {

                        runPrimarySynchronizer();

                } else {

                        runSecondarySynchronizer();
                }
        }

        // =========================================================
        // SERVER 1
        // =========================================================

        private void runPrimarySynchronizer() {

                while (running) {

                        try (ServerSocket serverSocket = new ServerSocket(SYNC_PORT)) {

                                System.out.println();
                                System.out.println(
                                                "=================================");

                                System.out.println(
                                                " Server 1 Synchronization Service");

                                System.out.println(
                                                "=================================");

                                System.out.println(
                                                "Listening for Server 2 on port "
                                                                + SYNC_PORT);

                                System.out.println();

                                Socket socket = serverSocket.accept();

                                System.out.println(
                                                "[SYNC] Server 2 connected from: "
                                                                + socket.getInetAddress());

                                setupConnection(socket);

                                // Exchange complete state immediately.
                                sendCurrentState();

                                send("STATE_REQUEST");

                                handleConnection();

                        } catch (IOException e) {

                                if (running) {

                                        System.out.println(
                                                        "[SYNC] Server 1 synchronization error: "
                                                                        + e.getMessage());

                                        sleep(RETRY_INTERVAL);
                                }
                        }
                }
        }

        // =========================================================
        // SERVER 2
        // =========================================================

        private void runSecondarySynchronizer() {

                while (running) {

                        try {

                                System.out.println(
                                                "[SYNC] Trying to connect Server 2 "
                                                                + "to Server 1...");

                                Socket socket = new Socket(
                                                SERVER_ADDRESS,
                                                SYNC_PORT);

                                setupConnection(socket);

                                System.out.println(
                                                "[SYNC] Server 2 connected to "
                                                                + "Server 1 successfully.");

                                // Exchange complete state immediately.
                                sendCurrentState();

                                send("STATE_REQUEST");

                                handleConnection();

                        } catch (IOException e) {

                                connected = false;

                                remoteOnlineUsers.clear();

                                System.out.println(
                                                "[SYNC] Server 1 synchronization unavailable.");

                                System.out.println(
                                                "[SYNC] Retrying in "
                                                                + (RETRY_INTERVAL / 1000)
                                                                + " seconds...");

                                sleep(RETRY_INTERVAL);
                        }
                }
        }

        // =========================================================
        // SETUP CONNECTION
        // =========================================================

        private synchronized void setupConnection(
                        Socket socket)
                        throws IOException {

                closeConnection();

                syncSocket = socket;

                input = new BufferedReader(
                                new InputStreamReader(
                                                syncSocket.getInputStream()));

                output = new PrintWriter(
                                syncSocket.getOutputStream(),
                                true);

                connected = true;

                // -----------------------------------------------------
                // Identify server
                // -----------------------------------------------------

                if (primaryServer) {

                        send("SERVER_ID:SERVER1");

                } else {

                        send("SERVER_ID:SERVER2");
                }

                // -----------------------------------------------------
                // Start heartbeat
                // -----------------------------------------------------

                startHeartbeat(syncSocket);
                startLoadSynchronization(syncSocket);
        }

        // =========================================================
        // HANDLE CONNECTION
        // =========================================================

        private void handleConnection() {

                Socket currentSocket = syncSocket;

                try {

                        String message;

                        while (running
                                        && connected
                                        && currentSocket != null
                                        && !currentSocket.isClosed()
                                        && (message = input.readLine()) != null) {

                                processMessage(message);
                        }

                } catch (IOException e) {

                        if (running) {

                                System.out.println(
                                                "[SYNC] Synchronization connection lost.");
                        }

                } finally {

                        if (syncSocket == currentSocket) {

                                closeConnection();

                                remoteOnlineUsers.clear();

                                System.out.println(
                                                "[SYNC] Remote online-user list cleared.");
                        }
                }
        }

        // =========================================================
        // PROCESS SYNC MESSAGE
        // =========================================================

        private void processMessage(
                        String message) {

                if (message == null) {
                        return;
                }

                message = message.trim();

                if (message.isEmpty()) {
                        return;
                }

                // =====================================================
                // SERVER ID
                // =====================================================

                if (message.startsWith(
                                "SERVER_ID:")) {

                        System.out.println(
                                        "[SYNC] " + message);

                        return;
                }

                // =====================================================
                // HEARTBEAT
                // =====================================================

                if (message.equals("PING")) {

                        send("PONG");

                        return;
                }

                if (message.equals("PONG")) {

                        System.out.println(
                                        "[SYNC] Heartbeat received.");

                        return;
                }

                // =====================================================
                // STATE REQUEST
                // =====================================================

                if (message.equals(
                                "STATE_REQUEST")) {

                        sendCurrentState();

                        return;
                }

                // =====================================================
                // COMPLETE USER STATE
                // =====================================================

                if (message.startsWith(
                                "STATE_USERS:")) {

                        String users = message.substring(
                                        "STATE_USERS:".length());

                        updateRemoteUsers(users);

                        return;
                }

                // =====================================================
                // USER ONLINE
                // =====================================================

                if (message.startsWith(
                                "USER_ONLINE:")) {

                        String username = message.substring(
                                        "USER_ONLINE:".length())
                                        .trim();

                        if (!username.isEmpty()) {

                                remoteOnlineUsers.add(
                                                username);

                                System.out.println(
                                                "[SYNC] Remote user ONLINE: "
                                                                + username);
                        }

                        return;
                }

                // =====================================================
                // USER OFFLINE
                // =====================================================

                if (message.startsWith(
                                "USER_OFFLINE:")) {

                        String username = message.substring(
                                        "USER_OFFLINE:".length())
                                        .trim();

                        if (!username.isEmpty()) {

                                remoteOnlineUsers.remove(
                                                username);

                                System.out.println(
                                                "[SYNC] Remote user OFFLINE: "
                                                                + username);
                        }

                        return;
                }

                if (message.startsWith("LOAD_UPDATE:")) {

                        processLoadUpdate(message);

                        return;
                }

                // =====================================================
                // PRIVATE MESSAGE ROUTING
                // =====================================================

                if (message.startsWith(
                                "PRIVATE_ROUTE:")) {

                        processPrivateRoute(
                                        message);

                        return;
                }

                // =====================================================
                // BROADCAST ROUTING
                // =====================================================

                if (message.startsWith(
                                "BROADCAST_ROUTE:")) {

                        processBroadcastRoute(
                                        message);

                        return;
                }

                // =====================================================
                // GROUP JOIN REQUEST
                // =====================================================

                if (message.startsWith(
                                "GROUP_JOIN_REQUEST:")) {

                        processGroupJoinRequest(
                                        message);

                        return;
                }

                // =====================================================
                // GROUP JOIN ACKNOWLEDGEMENT
                // =====================================================

                if (message.startsWith(
                                "GROUP_JOIN_ACK:")) {

                        processGroupJoinAck(
                                        message);

                        return;
                }

                // =====================================================
                // GROUP MESSAGE ROUTING
                // =====================================================

                if (message.startsWith(
                                "GROUP_MESSAGE_ROUTE:")) {

                        processGroupMessageRoute(
                                        message);

                        return;
                }

                // =====================================================
                // GROUP CREATED
                // =====================================================

                if (message.startsWith(
                                "GROUP_CREATED:")) {

                        processGroupCreated(
                                        message);

                        return;
                }

                // =====================================================
                // GROUP JOIN
                // =====================================================

                if (message.startsWith(
                                "GROUP_JOIN:")) {

                        processGroupJoin(
                                        message);

                        return;
                }

                if (message.startsWith("GROUP_MEMBER_ADD:")) {

                        processGroupMemberAdd(message);

                        return;
                }

                if (message.startsWith("GROUP_MEMBER_REMOVE:")) {
                        processGroupMemberRemove(message);
                        return;
                }

                if (message.startsWith("GROUP_OWNER_ADD:")) {
                        processGroupOwnerAdd(message);
                        return;
                }

                if (message.startsWith("GROUP_OWNER_REMOVE:")) {
                        processGroupOwnerRemove(message);
                        return;
                }

                // =====================================================
                // GROUP LEAVE
                // =====================================================

                if (message.startsWith(
                                "GROUP_LEAVE:")) {

                        processGroupLeave(
                                        message);

                        return;
                }

                // =====================================================
                // GROUP DELETED
                // =====================================================

                if (message.startsWith(
                                "GROUP_DELETED:")) {

                        processGroupDeleted(
                                        message);

                        return;
                }

                if (message.startsWith("FILE_ROUTE_REQUEST:")) {
                        processFileRouteRequest(message);
                        return;
                }

                if (message.startsWith("FILE_ROUTE_ACK:")) {
                        processFileRouteAck(message);
                        return;
                }

                // =====================================================
                // GROUP FILE ROUTING
                // =====================================================

                if (message.startsWith("FILE_GROUP_ROUTE_REQUEST:")) {

                        processGroupFileRouteRequest(message);

                        return;
                }

                if (message.startsWith("FILE_GROUP_ROUTE_ACK:")) {

                        processGroupFileRouteAck(message);

                        return;
                }

                // =====================================================
                // STATUS
                // =====================================================

                if (message.equals(
                                "STATUS_REQUEST")) {

                        send(
                                        "STATUS_RESPONSE:"
                                                        + getServerName());

                        return;
                }

                if (message.startsWith(
                                "STATUS_RESPONSE:")) {

                        System.out.println(
                                        "[SYNC] Remote server status: "
                                                        + message);

                        return;
                }

                // =====================================================
                // UNKNOWN
                // =====================================================

                System.out.println(
                                "[SYNC] Unknown message: "
                                                + message);
        }

        // =========================================================
        // PRIVATE ROUTE PROCESSING
        // =========================================================

        private void processPrivateRoute(
                        String message) {

                /*
                 * Format:
                 *
                 * PRIVATE_ROUTE:
                 * sender:
                 * recipient:
                 * message
                 *
                 * split limit = 4
                 *
                 * This allows the actual message to contain ":".
                 */

                String[] parts = message.split(
                                ":",
                                4);

                if (parts.length < 4) {

                        System.out.println(
                                        "[ROUTE] Invalid PRIVATE_ROUTE message.");

                        return;
                }

                String sender = parts[1];

                String recipient = parts[2];

                String actualMessage = parts[3];

                System.out.println(
                                "[ROUTE] Private route received: "
                                                + sender
                                                + " -> "
                                                + recipient);

                /*
                 * Deliver only to the LOCAL server.
                 *
                 * We do NOT call sendPrivateMessage()
                 * here because that could route the message
                 * back to the other server.
                 */

                boolean delivered = ChatServer
                                .deliverRemotePrivateMessage(
                                                sender,
                                                recipient,
                                                actualMessage);

                if (!delivered) {

                        System.out.println(
                                        "[ROUTE] Recipient "
                                                        + recipient
                                                        + " not found locally.");
                }
        }

        // =========================================================
        // BROADCAST ROUTE PROCESSING
        // =========================================================

        private void processBroadcastRoute(
                        String message) {

                /*
                 * Format:
                 *
                 * BROADCAST_ROUTE:
                 * sender:
                 * message
                 *
                 * split limit = 3
                 *
                 * This allows the actual message to contain ":".
                 */

                String[] parts = message.split(
                                ":",
                                3);

                if (parts.length < 3) {

                        System.out.println(
                                        "[ROUTE] Invalid BROADCAST_ROUTE message.");

                        return;
                }

                String sender = parts[1];

                String actualMessage = parts[2];

                System.out.println(
                                "[ROUTE] Broadcast route received from "
                                                + sender);

                /*
                 * Deliver only to local clients.
                 *
                 * IMPORTANT:
                 * We don't call broadcastMessage()
                 * because that would send the message back
                 * to the remote server.
                 */

        }

        // =========================================================
        // GROUP CREATED PROCESSING
        // =========================================================

        private void processGroupCreated(
                        String message) {

                /*
                 * Format:
                 *
                 * GROUP_CREATED:groupName:creator
                 */

                String[] parts = message.split(
                                ":",
                                3);

                if (parts.length < 3) {

                        System.out.println(
                                        "[SYNC] Invalid GROUP_CREATED message.");

                        return;
                }

                String groupName = parts[1];

                String creator = parts[2];

                System.out.println(
                                "[SYNC] Remote group created: "
                                                + groupName
                                                + " by "
                                                + creator);

                ChatServer.createRemoteGroup(
                                groupName,
                                creator);
        }

        // =========================================================
        // GROUP JOIN PROCESSING
        // =========================================================

        private void processGroupJoin(
                        String message) {

                /*
                 * Format:
                 *
                 * GROUP_JOIN:groupName:username
                 */

                String[] parts = message.split(
                                ":",
                                3);

                if (parts.length < 3) {

                        System.out.println(
                                        "[SYNC] Invalid GROUP_JOIN message.");

                        return;
                }

                String groupName = parts[1];

                String username = parts[2];

                System.out.println(
                                "[SYNC] Remote group join: "
                                                + username
                                                + " -> "
                                                + groupName);

                ChatServer.addRemoteGroupMember(
                                groupName,
                                username);
        }

        private void processGroupMemberAdd(
                        String message) {

                try {

                        // Format:
                        // GROUP_MEMBER_ADD:groupName:username

                        String[] parts = message.split(":", 3);

                        if (parts.length < 3) {

                                System.out.println(
                                                "[SYNC] Invalid GROUP_MEMBER_ADD message.");

                                return;
                        }

                        String groupName = parts[1].trim();
                        String username = parts[2].trim();

                        if (groupName.isEmpty()
                                        || username.isEmpty()) {

                                return;
                        }

                        System.out.println(
                                        "[SYNC] Remote group member added: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        /*
                         * IMPORTANT:
                         *
                         * Do NOT call addPersistentGroupMember()
                         * here because that method writes to MongoDB.
                         *
                         * MongoDB has already been updated by
                         * the originating server.
                         *
                         * We only update this server's in-memory
                         * persistent membership state.
                         */

                        ChatServer.addSyncedPersistentGroupMember(
                                        groupName,
                                        username);

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Error processing "
                                                        + "GROUP_MEMBER_ADD: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // PROCESS GROUP MEMBER REMOVE
        // =========================================================

        private void processGroupMemberRemove(String message) {

                try {

                        String[] parts = message.split(":", 3);

                        if (parts.length < 3) {

                                System.out.println(
                                                "[SYNC] Invalid GROUP_MEMBER_REMOVE message.");

                                return;
                        }

                        String groupName = parts[1].trim();

                        String username = parts[2].trim();

                        if (groupName.isEmpty()
                                        || username.isEmpty()) {

                                return;
                        }

                        System.out.println(
                                        "[SYNC] Remote group member removed: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        ChatServer.removeSyncedPersistentGroupMember(
                                        groupName,
                                        username);

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Error processing GROUP_MEMBER_REMOVE: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // PROCESS GROUP OWNER ADD
        // =========================================================

        private void processGroupOwnerAdd(String message) {

                try {

                        String[] parts = message.split(":", 3);

                        if (parts.length < 3) {

                                System.out.println(
                                                "[SYNC] Invalid GROUP_OWNER_ADD message.");

                                return;
                        }

                        String groupName = parts[1].trim();

                        String username = parts[2].trim();

                        if (groupName.isEmpty()
                                        || username.isEmpty()) {

                                return;
                        }

                        System.out.println(
                                        "[SYNC] Remote group owner added: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        ChatServer.addSyncedGroupOwner(
                                        groupName,
                                        username);

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Error processing GROUP_OWNER_ADD: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // PROCESS GROUP OWNER REMOVE
        // =========================================================

        private void processGroupOwnerRemove(String message) {

                try {

                        String[] parts = message.split(":", 3);

                        if (parts.length < 3) {

                                System.out.println(
                                                "[SYNC] Invalid GROUP_OWNER_REMOVE message.");

                                return;
                        }

                        String groupName = parts[1].trim();

                        String username = parts[2].trim();

                        if (groupName.isEmpty()
                                        || username.isEmpty()) {

                                return;
                        }

                        System.out.println(
                                        "[SYNC] Remote group owner removed: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        ChatServer.removeSyncedGroupOwner(
                                        groupName,
                                        username);

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Error processing GROUP_OWNER_REMOVE: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // PROCESS GROUP JOIN REQUEST
        // =========================================================

        private void processGroupJoinRequest(
                        String message) {

                /*
                 * Format:
                 *
                 * GROUP_JOIN_REQUEST:groupName:username
                 */

                String[] parts = message.split(":", 3);

                if (parts.length < 3) {

                        System.out.println(
                                        "[SYNC] Invalid GROUP_JOIN_REQUEST message.");

                        return;
                }

                String groupName = parts[1];

                String username = parts[2];

                System.out.println(
                                "[ROUTE] Group join request received: "
                                                + username
                                                + " -> "
                                                + groupName);

                // ---------------------------------------------------------
                // Verify that this server owns the group
                // ---------------------------------------------------------

                if (!ChatServer.isLocalGroup(
                                groupName)) {

                        System.out.println(
                                        "[ROUTE] Group "
                                                        + groupName
                                                        + " does not exist locally.");

                        send(
                                        "GROUP_JOIN_ACK:"
                                                        + groupName
                                                        + ":"
                                                        + username
                                                        + ":FAILED");

                        return;
                }

                // ---------------------------------------------------------
                // Persist remote member in MongoDB
                // ---------------------------------------------------------

                boolean persisted = ChatServer.addPersistentGroupMember(
                                groupName,
                                username);

                if (!persisted) {

                        System.out.println(
                                        "[DB] Failed to persist group member: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        send(
                                        "GROUP_JOIN_ACK:"
                                                        + groupName
                                                        + ":"
                                                        + username
                                                        + ":FAILED");

                        return;
                }

                // ---------------------------------------------------------
                // Register remote member in memory
                // ---------------------------------------------------------

                ChatServer.addRemoteGroupMember(
                                groupName,
                                username);

                // ---------------------------------------------------------
                // Send acknowledgement
                // ---------------------------------------------------------

                send(
                                "GROUP_JOIN_ACK:"
                                                + groupName
                                                + ":"
                                                + username
                                                + ":SUCCESS");

                System.out.println(
                                "[ROUTE] Remote group join accepted: "
                                                + username
                                                + " -> "
                                                + groupName);
        }

        // =========================================================
        // PROCESS GROUP JOIN ACK
        // =========================================================

        private void processGroupJoinAck(
                        String message) {

                /*
                 * Format:
                 *
                 * GROUP_JOIN_ACK:groupName:username:SUCCESS
                 */

                String[] parts = message.split(":", 4);

                if (parts.length < 4) {

                        System.out.println(
                                        "[SYNC] Invalid GROUP_JOIN_ACK message.");

                        return;
                }

                String groupName = parts[1];

                String username = parts[2];

                String status = parts[3];

                if ("SUCCESS".equalsIgnoreCase(
                                status)) {

                        ChatServer.confirmRemoteGroupJoin(
                                        groupName,
                                        username);

                        System.out.println(
                                        "[ROUTE] Remote group join confirmed: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                } else {

                        System.out.println(
                                        "[ROUTE] Remote group join rejected: "
                                                        + username
                                                        + " -> "
                                                        + groupName);
                }
        }

        // =========================================================
        // PROCESS GROUP MESSAGE ROUTE
        // =========================================================

        private void processGroupMessageRoute(String data) {

                try {

                        String[] parts = data.split(":", 4);

                        if (parts.length < 4) {

                                System.out.println(
                                                "[ROUTE] Invalid GROUP_MESSAGE_ROUTE.");

                                return;
                        }

                        String groupName = parts[1].trim();
                        String sender = parts[2].trim();
                        String message = parts[3];

                        System.out.println(
                                        "[ROUTE] Group message received: "
                                                        + sender
                                                        + " -> "
                                                        + groupName);

                        ChatServer.deliverRemoteGroupMessage(
                                        groupName,
                                        sender,
                                        message);

                } catch (Exception e) {

                        System.out.println(
                                        "[ROUTE] Error processing group message: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // PROCESS GROUP FILE ROUTE REQUEST
        // =========================================================

        private void processGroupFileRouteRequest(
                        String message) {

                try {

                        /*
                         * Format:
                         *
                         * FILE_GROUP_ROUTE_REQUEST:
                         * sender:
                         * groupName:
                         * fileName:
                         * fileSize:
                         * transferPort
                         *
                         * split limit = 6
                         */

                        String[] parts = message.split(":", 7);

                        if (parts.length < 7) {

                                System.out.println(
                                                "[SYNC] Invalid FILE_GROUP_ROUTE_REQUEST.");

                                return;
                        }

                        String sender = parts[1];

                        String groupName = parts[2];

                        String fileName = parts[3];

                        long fileSize = Long.parseLong(parts[4]);

                        int transferPort = Integer.parseInt(parts[5]);

                        String recipientString = parts[6];

                        java.util.List<String> allRecipients = new java.util.ArrayList<>();

                        if (!recipientString.trim().isEmpty()) {

                                for (String recipient : recipientString.split(",")) {

                                        if (recipient != null
                                                        && !recipient.trim().isEmpty()) {

                                                allRecipients.add(
                                                                recipient.trim());
                                        }
                                }
                        }

                        System.out.println();

                        System.out.println(
                                        "[GROUP-FILE-DIST] GROUP FILE ROUTE REQUEST received.");

                        System.out.println(
                                        "[GROUP-FILE-DIST] Sender: "
                                                        + sender);

                        System.out.println(
                                        "[GROUP-FILE-DIST] Group: "
                                                        + groupName);

                        System.out.println(
                                        "[GROUP-FILE-DIST] File: "
                                                        + fileName);

                        System.out.println(
                                        "[GROUP-FILE-DIST] Size: "
                                                        + fileSize
                                                        + " bytes");

                        System.out.println(
                                        "[GROUP-FILE-DIST] Source transfer port: "
                                                        + transferPort);

                        System.out.println(
                                        "[GROUP-FILE-DIST] All recipients: "
                                                        + allRecipients);

                        // =====================================================
                        // VERIFY LOCAL GROUP MEMBERS
                        // =====================================================

                        Set<String> remoteGroupMembers = ChatServer.getRemoteGroupMembers(
                                        groupName);

                        /*
                         * On this server, remoteGroupMembers contains users
                         * belonging to the group on the OTHER server.
                         *
                         * We need to find users who are actually connected
                         * LOCALLY and joined this group.
                         */

                        java.util.List<String> localRecipients = new java.util.ArrayList<>();

                        for (String username : ChatServer.getLocalOnlineUsers()) {

                                if (username == null
                                                || username.trim().isEmpty()) {

                                        continue;
                                }

                                ClientHandler client = ChatServer.getOnlineUser(username);

                                if (client == null) {
                                        continue;
                                }

                                if (ChatServer.isAnyGroupMember(
                                                groupName,
                                                client)) {

                                        localRecipients.add(username);

                                        System.out.println(
                                                        "[GROUP-FILE-DIST] "
                                                                        + "Local recipient found: "
                                                                        + username);
                                }
                        }

                        boolean hasLocalRecipients = !localRecipients.isEmpty();

                        if (!hasLocalRecipients) {

                                System.out.println(
                                                "[GROUP-FILE-DIST] No local online recipients "
                                                                + "for group "
                                                                + groupName);

                                System.out.println(
                                                "[GROUP-FILE-DIST] File will be received "
                                                                + "and checked for offline delivery.");

                        } else {

                                System.out.println(
                                                "[GROUP-FILE-DIST] Local online recipients: "
                                                                + localRecipients);
                        }

                        String sourceServer = primaryServer
                                        ? "SERVER_1"
                                        : "SERVER_2";

                        String destinationServer = primaryServer
                                        ? "SERVER_2"
                                        : "SERVER_1";

                        // =====================================================
                        // START SERVER-TO-SERVER FILE RECEIVER
                        // =====================================================

                        Thread receiverThread = new Thread(
                                        new DistributedGroupFileTransferReceiver(
                                                        SERVER_ADDRESS,
                                                        transferPort,
                                                        sender,
                                                        groupName,
                                                        fileName,
                                                        fileSize,
                                                        allRecipients,
                                                        sourceServer,
                                                        destinationServer),
                                        "DistributedGroupFileReceiver-"
                                                        + fileName);

                        receiverThread.start();

                        // =====================================================
                        // ACKNOWLEDGE
                        // =====================================================

                        send(
                                        "FILE_GROUP_ROUTE_ACK:"
                                                        + sender
                                                        + ":"
                                                        + groupName
                                                        + ":READY");

                        System.out.println(
                                        "[GROUP-FILE-DIST] Receiver started for group: "
                                                        + groupName);

                } catch (Exception e) {

                        System.out.println(
                                        "[GROUP-FILE-DIST] Error processing "
                                                        + "group file route request: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // PROCESS GROUP FILE ROUTE ACK
        // =========================================================

        private void processGroupFileRouteAck(
                        String message) {

                try {

                        String[] parts = message.split(":", 4);

                        if (parts.length < 4) {

                                System.out.println(
                                                "[SYNC] Invalid FILE_GROUP_ROUTE_ACK.");

                                return;
                        }

                        String sender = parts[1];

                        String groupName = parts[2];

                        String status = parts[3];

                        System.out.println(
                                        "[SYNC] FILE_GROUP_ROUTE_ACK received: "
                                                        + sender
                                                        + " -> Group: "
                                                        + groupName
                                                        + " | Status: "
                                                        + status);

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Error processing "
                                                        + "FILE_GROUP_ROUTE_ACK: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // GROUP LEAVE PROCESSING
        // =========================================================

        private void processGroupLeave(
                        String message) {

                /*
                 * Format:
                 *
                 * GROUP_LEAVE:groupName:username
                 */

                String[] parts = message.split(
                                ":",
                                3);

                if (parts.length < 3) {

                        System.out.println(
                                        "[SYNC] Invalid GROUP_LEAVE message.");

                        return;
                }

                String groupName = parts[1];

                String username = parts[2];

                System.out.println(
                                "[SYNC] Remote group leave: "
                                                + username
                                                + " <- "
                                                + groupName);

                // ---------------------------------------------------------
                // Remove member from MongoDB and persistent state
                // ---------------------------------------------------------

                boolean removed = ChatServer.removePersistentGroupMember(
                                groupName,
                                username);

                if (!removed) {

                        System.out.println(
                                        "[DB] Failed to persist group leave: "
                                                        + username
                                                        + " <- "
                                                        + groupName);

                        return;
                }

                // ---------------------------------------------------------
                // Remove member from remote in-memory state
                // ---------------------------------------------------------

                ChatServer.removeRemoteGroupMember(
                                groupName,
                                username);
        }

        // =========================================================
        // GROUP DELETED PROCESSING
        // =========================================================

        private void processGroupDeleted(
                        String message) {

                /*
                 * Format:
                 *
                 * GROUP_DELETED:groupName
                 */

                String[] parts = message.split(
                                ":",
                                2);

                if (parts.length < 2) {

                        System.out.println(
                                        "[SYNC] Invalid GROUP_DELETED message.");

                        return;
                }

                String groupName = parts[1];

                System.out.println(
                                "[SYNC] Remote group deleted: "
                                                + groupName);

                ChatServer.deleteRemoteGroup(
                                groupName);
        }

        // =========================================================
        // SEND CURRENT STATE
        // =========================================================

        public void sendCurrentState() {

                if (!connected) {
                        return;
                }

                Set<String> localUsers = ChatServer.getLocalOnlineUsers();

                StringBuilder message = new StringBuilder(
                                "STATE_USERS:");

                boolean first = true;

                for (String username : localUsers) {

                        if (!first) {
                                message.append(",");
                        }

                        message.append(username);

                        first = false;
                }

                send(
                                message.toString());

                System.out.println(
                                "[SYNC] Current user state sent: "
                                                + localUsers);
        }

        // =========================================================
        // UPDATE REMOTE USERS
        // =========================================================

        private void updateRemoteUsers(
                        String users) {

                remoteOnlineUsers.clear();

                if (users == null
                                || users.trim().isEmpty()) {

                        System.out.println(
                                        "[SYNC] Remote server has no online users.");

                        return;
                }

                String[] usernames = users.split(",");

                for (String username : usernames) {

                        username = username.trim();

                        if (!username.isEmpty()) {

                                remoteOnlineUsers.add(
                                                username);
                        }
                }

                System.out.println(
                                "[SYNC] Remote online users updated: "
                                                + remoteOnlineUsers);
        }

        // =========================================================
        // PROCESS REMOTE LOAD UPDATE
        // =========================================================

        private void processLoadUpdate(String message) {

                try {

                        String[] parts = message.split(":", 6);

                        if (parts.length < 6) {

                                System.out.println(
                                                "[LOAD SYNC] Invalid LOAD_UPDATE message.");

                                return;
                        }

                        remoteConnectedClients = Integer.parseInt(parts[1]);

                        remoteCpuUsage = Double.parseDouble(parts[2]);

                        remoteMemoryUsage = Double.parseDouble(parts[3]);

                        remoteActiveThreads = Integer.parseInt(parts[4]);

                        remoteLoadScore = Double.parseDouble(parts[5]);

                        System.out.println();

                        System.out.println(
                                        "[LOAD SYNC] Remote server load updated.");

                        System.out.println(
                                        "[LOAD SYNC] Clients : "
                                                        + remoteConnectedClients);

                        System.out.printf(
                                        "[LOAD SYNC] CPU     : %.2f%%%n",
                                        remoteCpuUsage);

                        System.out.printf(
                                        "[LOAD SYNC] Memory  : %.2f%%%n",
                                        remoteMemoryUsage);

                        System.out.println(
                                        "[LOAD SYNC] Threads : "
                                                        + remoteActiveThreads);

                        System.out.printf(
                                        "[LOAD SYNC] Score   : %.2f%n",
                                        remoteLoadScore);

                } catch (Exception e) {

                        System.out.println(
                                        "[LOAD SYNC] Error processing load update: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // SEND LOAD UPDATE
        // =========================================================

        private void sendLoadUpdate() {

                try {

                        ServerLoadMonitor monitor = new ServerLoadMonitor();

                        int clients = monitor.getConnectedClients();

                        double cpu = monitor.getCpuUsage();

                        double memory = monitor.getMemoryUsage();

                        int threads = monitor.getActiveThreads();

                        double score = monitor.getLoadScore();

                        String message = "LOAD_UPDATE:"
                                        + clients + ":"
                                        + cpu + ":"
                                        + memory + ":"
                                        + threads + ":"
                                        + score;

                        send(message);

                        System.out.printf(
                                        "[LOAD SYNC] Local load sent - "
                                                        + "Clients: %d, "
                                                        + "CPU: %.2f%%, "
                                                        + "Memory: %.2f%%, "
                                                        + "Threads: %d, "
                                                        + "Score: %.2f%n",
                                        clients,
                                        cpu,
                                        memory,
                                        threads,
                                        score);

                } catch (Exception e) {

                        System.out.println(
                                        "[LOAD SYNC] Unable to send load update: "
                                                        + e.getMessage());
                }
        }

        private void processFileRouteRequest(String message) {

                try {

                        /*
                         * Format:
                         *
                         * FILE_ROUTE_REQUEST:
                         * sender:
                         * recipient:
                         * fileName:
                         * fileSize:
                         * transferPort
                         *
                         * split limit = 6
                         */

                        String[] parts = message.split(":", 6);

                        if (parts.length < 6) {

                                System.out.println(
                                                "[SYNC] Invalid FILE_ROUTE_REQUEST.");

                                return;
                        }

                        String sender = parts[1];

                        String recipient = parts[2];

                        String fileName = parts[3];

                        long fileSize = Long.parseLong(parts[4]);

                        int transferPort = Integer.parseInt(parts[5]);

                        // =====================================================
                        // DETERMINE SERVER DIRECTION
                        // =====================================================

                        String sourceServer = primaryServer
                                        ? "SERVER_2"
                                        : "SERVER_1";

                        String destinationServer = primaryServer
                                        ? "SERVER_1"
                                        : "SERVER_2";

                        System.out.println(
                                        "[SYNC] FILE_ROUTE_REQUEST received: "
                                                        + sender
                                                        + " -> "
                                                        + recipient
                                                        + " | File: "
                                                        + fileName
                                                        + " | Size: "
                                                        + fileSize
                                                        + " bytes"
                                                        + " | Port: "
                                                        + transferPort);

                        // ---------------------------------------------------------
                        // Verify recipient is local
                        // ---------------------------------------------------------

                        ClientHandler recipientHandler = ChatServer.getOnlineUser(
                                        recipient);

                        if (recipientHandler == null) {

                                System.out.println(
                                                "[SYNC] File recipient is offline: "
                                                                + recipient);

                                send(
                                                "FILE_ROUTE_ACK:"
                                                                + sender
                                                                + ":"
                                                                + recipient
                                                                + ":FAILED");

                                return;
                        }

                        System.out.println(
                                        "[SYNC] File recipient is online: "
                                                        + recipient);

                        // ---------------------------------------------------------
                        // Start binary receiver
                        // ---------------------------------------------------------

                        Thread receiverThread = new Thread(
                                        new DistributedFileTransferReceiver(
                                                        SERVER_ADDRESS,
                                                        transferPort,
                                                        sender,
                                                        recipient,
                                                        sourceServer,
                                                        destinationServer),
                                        "DistributedFileReceiver-"
                                                        + fileName);

                        receiverThread.start();

                        // ---------------------------------------------------------
                        // Tell source server that receiver started
                        // ---------------------------------------------------------

                        send(
                                        "FILE_ROUTE_ACK:"
                                                        + sender
                                                        + ":"
                                                        + recipient
                                                        + ":READY");

                        System.out.println(
                                        "[FILE-DIST] Receiver started for: "
                                                        + fileName);

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Error processing "
                                                        + "FILE_ROUTE_REQUEST: "
                                                        + e.getMessage());
                }
        }

        private void processFileRouteAck(String message) {

                try {

                        String[] parts = message.split(":", 4);

                        if (parts.length < 4) {
                                System.out.println("[SYNC] Invalid FILE_ROUTE_ACK.");
                                return;
                        }

                        String sender = parts[1];
                        String recipient = parts[2];
                        String status = parts[3];

                        System.out.println(
                                        "[SYNC] FILE_ROUTE_ACK received: "
                                                        + sender + " -> "
                                                        + recipient
                                                        + " | Status: " + status);

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Error processing FILE_ROUTE_ACK: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // SEND
        // =========================================================

        public void send(
                        String message) {

                if (!connected
                                || output == null) {

                        return;
                }

                synchronized (writeLock) {

                        if (output != null) {

                                output.println(
                                                message);
                        }
                }
        }

        // =========================================================
        // HEARTBEAT
        // =========================================================

        public void sendHeartbeat() {

                if (connected) {

                        send("PING");
                }
        }

        private void startHeartbeat(
                        Socket connectionSocket) {

                Thread heartbeatThread = new Thread(
                                () -> {

                                        while (running
                                                        && connected
                                                        && syncSocket == connectionSocket) {

                                                sleep(
                                                                HEARTBEAT_INTERVAL);

                                                if (running
                                                                && connected
                                                                && syncSocket == connectionSocket) {

                                                        sendHeartbeat();
                                                }
                                        }
                                },
                                primaryServer
                                                ? "Server1-Heartbeat"
                                                : "Server2-Heartbeat");

                heartbeatThread.setDaemon(true);

                heartbeatThread.start();
        }

        // =========================================================
        // LOAD SYNCHRONIZATION
        // =========================================================

        private void startLoadSynchronization(
                        Socket connectionSocket) {

                Thread loadThread = new Thread(
                                () -> {

                                        while (running
                                                        && connected
                                                        && syncSocket == connectionSocket) {

                                                sleep(5000);

                                                if (running
                                                                && connected
                                                                && syncSocket == connectionSocket) {

                                                        sendLoadUpdate();
                                                }
                                        }
                                },
                                primaryServer
                                                ? "Server1-Load-Synchronization"
                                                : "Server2-Load-Synchronization");

                loadThread.setDaemon(true);

                loadThread.start();

                System.out.println(
                                "[LOAD SYNC] Load synchronization thread started.");
        }

        // =========================================================
        // SERVER STATUS
        // =========================================================

        public void requestServerStatus() {

                if (connected) {

                        send(
                                        "STATUS_REQUEST");
                }
        }

        // =========================================================
        // CONNECTION STATUS
        // =========================================================

        public boolean isConnected() {

                return connected;
        }

        public boolean sendFileRouteRequest(
                        String sender,
                        String recipient,
                        String fileName,
                        long fileSize,
                        int transferPort) {

                try {

                        if (!connected) {

                                System.out.println(
                                                "[SYNC] Cannot send file route request. "
                                                                + "Server not connected.");

                                return false;
                        }

                        String message = "FILE_ROUTE_REQUEST:"
                                        + sender
                                        + ":"
                                        + recipient
                                        + ":"
                                        + fileName
                                        + ":"
                                        + fileSize
                                        + ":"
                                        + transferPort;

                        send(message);

                        System.out.println(
                                        "[SYNC] FILE_ROUTE_REQUEST sent: "
                                                        + sender
                                                        + " -> "
                                                        + recipient
                                                        + " | File: "
                                                        + fileName
                                                        + " | Size: "
                                                        + fileSize
                                                        + " bytes"
                                                        + " | Port: "
                                                        + transferPort);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Failed to send file route request: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // SEND GROUP FILE ROUTE REQUEST
        // =========================================================

        public boolean sendGroupFileRouteRequest(
                        String sender,
                        String groupName,
                        String fileName,
                        long fileSize,
                        int transferPort,
                        java.util.Set<String> recipients) {

                try {

                        if (!connected) {

                                System.out.println(
                                                "[SYNC] Cannot send group file route request. "
                                                                + "Server not connected.");

                                return false;
                        }

                        /*
                         * Format:
                         *
                         * FILE_GROUP_ROUTE_REQUEST:
                         * sender:
                         * groupName:
                         * fileName:
                         * fileSize:
                         * transferPort:
                         * recipients
                         */

                        String recipientList = String.join(
                                        ",",
                                        recipients);

                        String message = "FILE_GROUP_ROUTE_REQUEST:"
                                        + sender
                                        + ":"
                                        + groupName
                                        + ":"
                                        + fileName
                                        + ":"
                                        + fileSize
                                        + ":"
                                        + transferPort
                                        + ":"
                                        + recipientList;

                        send(message);

                        System.out.println(
                                        "[SYNC] FILE_GROUP_ROUTE_REQUEST sent: "
                                                        + sender
                                                        + " -> Group: "
                                                        + groupName
                                                        + " | File: "
                                                        + fileName
                                                        + " | Size: "
                                                        + fileSize
                                                        + " bytes"
                                                        + " | Port: "
                                                        + transferPort
                                                        + " | Recipients: "
                                                        + recipientList);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Failed to send group file route request: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // SEND GROUP MEMBER ADD
        // =========================================================

        // =========================================================
        // SEND GROUP MEMBER ADD
        // =========================================================

        public boolean sendGroupMemberAdd(
                        String groupName,
                        String username) {

                try {

                        if (!connected) {

                                System.out.println(
                                                "[SYNC] Cannot send GROUP_MEMBER_ADD. "
                                                                + "Server not connected.");

                                return false;
                        }

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        String message = "GROUP_MEMBER_ADD:"
                                        + groupName
                                        + ":"
                                        + username;

                        send(message);

                        System.out.println(
                                        "[SYNC] GROUP_MEMBER_ADD sent: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Failed to send GROUP_MEMBER_ADD: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // SEND GROUP MEMBER REMOVE
        // =========================================================

        public boolean sendGroupMemberRemove(
                        String groupName,
                        String username) {

                try {

                        if (!connected) {

                                System.out.println(
                                                "[SYNC] Cannot send GROUP_MEMBER_REMOVE. "
                                                                + "Server not connected.");

                                return false;
                        }

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        String message = "GROUP_MEMBER_REMOVE:"
                                        + groupName
                                        + ":"
                                        + username;

                        send(message);

                        System.out.println(
                                        "[SYNC] GROUP_MEMBER_REMOVE sent: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Failed to send GROUP_MEMBER_REMOVE: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // SEND GROUP OWNER ADD
        // =========================================================

        public boolean sendGroupOwnerAdd(
                        String groupName,
                        String username) {

                try {

                        if (!connected) {

                                System.out.println(
                                                "[SYNC] Cannot send GROUP_OWNER_ADD. "
                                                                + "Server not connected.");

                                return false;
                        }

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        String message = "GROUP_OWNER_ADD:"
                                        + groupName
                                        + ":"
                                        + username;

                        send(message);

                        System.out.println(
                                        "[SYNC] GROUP_OWNER_ADD sent: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Failed to send GROUP_OWNER_ADD: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // SEND GROUP OWNER REMOVE
        // =========================================================

        public boolean sendGroupOwnerRemove(
                        String groupName,
                        String username) {

                try {

                        if (!connected) {

                                System.out.println(
                                                "[SYNC] Cannot send GROUP_OWNER_REMOVE. "
                                                                + "Server not connected.");

                                return false;
                        }

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        String message = "GROUP_OWNER_REMOVE:"
                                        + groupName
                                        + ":"
                                        + username;

                        send(message);

                        System.out.println(
                                        "[SYNC] GROUP_OWNER_REMOVE sent: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[SYNC] Failed to send GROUP_OWNER_REMOVE: "
                                                        + e.getMessage());

                        return false;
                }
        }

        public boolean isRemoteUserOnline(String username) {

                if (username == null || username.trim().isEmpty()) {
                        return false;
                }

                return remoteOnlineUsers.contains(username.trim());
        }

        // =========================================================
        // REMOTE ONLINE USERS
        // =========================================================

        public Set<String> getRemoteOnlineUsers() {

                return Collections.unmodifiableSet(
                                remoteOnlineUsers);
        }

        // =========================================================
        // SERVER NAME
        // =========================================================

        private String getServerName() {

                return primaryServer
                                ? "SERVER1"
                                : "SERVER2";
        }

        // =========================================================
        // CLOSE CONNECTION
        // =========================================================

        private synchronized void closeConnection() {

                connected = false;

                try {

                        if (syncSocket != null
                                        && !syncSocket.isClosed()) {

                                syncSocket.close();
                        }

                } catch (IOException ignored) {
                }

                syncSocket = null;

                input = null;

                output = null;
        }

        // =========================================================
        // STOP
        // =========================================================

        public void stop() {

                running = false;

                closeConnection();

                remoteOnlineUsers.clear();
        }

        // =========================================================
        // SLEEP
        // =========================================================

        private void sleep(
                        long milliseconds) {

                try {

                        Thread.sleep(
                                        milliseconds);

                } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();
                }
        }

        // =========================================================
        // REMOTE LOAD GETTERS
        // =========================================================

        public double getRemoteLoadScore() {

                return remoteLoadScore;
        }

        public int getRemoteConnectedClients() {

                return remoteConnectedClients;
        }

        public double getRemoteCpuUsage() {

                return remoteCpuUsage;
        }

        public double getRemoteMemoryUsage() {

                return remoteMemoryUsage;
        }

        public int getRemoteActiveThreads() {

                return remoteActiveThreads;
        }

        public boolean isPrimaryServer() {
                return primaryServer;
        }
}