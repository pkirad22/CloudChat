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
}