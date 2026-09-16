package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import database.MongoDBConnection;
import service.GroupService;

public class ChatServer {

        // =========================================================
        // SERVER CONFIGURATION
        // =========================================================

        private static final int PORT = 5000;
        private static final int SYNC_PORT = 6000;

        // =========================================================
        // SERVER STATE
        // =========================================================

        private static final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

        private static final Map<String, Set<ClientHandler>> groups = new ConcurrentHashMap<>();

        // =========================================================
        // REMOTE GROUP MEMBERS
        // =========================================================

        /*
         * Stores group membership of users connected to the
         * other CloudChat server.
         *
         * Example:
         *
         * MCA -> [Pranav]
         * DCC -> [Rahul, Amit]
         */
        private static final Map<String, Set<String>> remoteGroupMembers = new ConcurrentHashMap<>();

        private static final Map<String, Set<String>> persistentGroupMembers = new ConcurrentHashMap<>();
        /*
         * IMPORTANT:
         *
         * This synchronizer is shared by all ChatServer methods
         * inside the current JVM.
         *
         * Server 1 configures it as primary.
         * Server 2 configures it as secondary.
         */
        private static ServerSynchronizer serverSynchronizer;

        private static GroupService groupService;

        // =========================================================
        // INITIALIZE GROUP PERSISTENCE SERVICE
        // =========================================================

        public static void initializeGroupService() {

                if (groupService == null) {

                        groupService = new GroupService();

                        System.out.println(
                                        "[DB] GroupService initialized.");
                }
        }

        // =========================================================
        // INITIALIZE SERVER SYNCHRONIZER
        // =========================================================

        public static synchronized void initializeSynchronizer(
                        boolean primaryServer) {

                /*
                 * Prevent creating multiple synchronizers
                 * in the same JVM.
                 */
                if (serverSynchronizer != null) {
                        return;
                }

                serverSynchronizer = new ServerSynchronizer(primaryServer);

                serverSynchronizer.start();
        }

        // =========================================================
        // MAIN - SERVER 1
        // =========================================================

        public static void main(String[] args) {

                System.out.println("=================================");
                System.out.println("       CloudChat Server 1");
                System.out.println("=================================");

                MongoDBConnection.connect();

                initializeGroupService();

                loadGroupsFromDatabase();

                // Server 1 = PRIMARY
                initializeSynchronizer(true);

                System.out.println(
                                "Server synchronization service started.");

                System.out.println(
                                "Server 1 synchronization port: "
                                                + SYNC_PORT);

                System.out.println();

                try (ServerSocket serverSocket = new ServerSocket(PORT)) {

                        System.out.println(
                                        "Server 1 started successfully.");

                        System.out.println(
                                        "Client listening port: "
                                                        + PORT);

                        System.out.println(
                                        "Waiting for clients...");

                        System.out.println();

                        while (true) {

                                Socket clientSocket = serverSocket.accept();

                                System.out.println(
                                                "New client connected to Server 1: "
                                                                + clientSocket.getInetAddress());

                                ClientHandler clientHandler = new ClientHandler(clientSocket);

                                Thread clientThread = new Thread(clientHandler);

                                clientThread.start();
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "Server 1 error: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // ONLINE USER MANAGEMENT
        // =========================================================

        public static boolean addOnlineUser(
                        String username,
                        ClientHandler clientHandler) {

                ClientHandler existing = onlineUsers.putIfAbsent(
                                username,
                                clientHandler);

                if (existing != null) {
                        return false;
                }

                System.out.println(
                                username + " is now online.");

                System.out.println(
                                "Server local online users: "
                                                + onlineUsers.keySet());

                // -----------------------------------------------------
                // Restore persisted group memberships for this user
                // -----------------------------------------------------

                for (Map.Entry<String, Set<String>> entry : persistentGroupMembers.entrySet()) {

                        String groupName = entry.getKey();
                        Set<String> members = entry.getValue();

                        if (members.contains(username)) {

                                Set<ClientHandler> groupMembers = groups.computeIfAbsent(
                                                groupName,
                                                key -> ConcurrentHashMap.newKeySet());

                                groupMembers.add(clientHandler);

                                System.out.println(
                                                "[DB] Restored group membership: "
                                                                + username
                                                                + " -> "
                                                                + groupName);
                        }
                }

                // -----------------------------------------------------
                // Notify remote server
                // -----------------------------------------------------

                if (serverSynchronizer != null) {

                        serverSynchronizer.send(
                                        "USER_ONLINE:" + username);

                        /*
                         * Send complete state as well.
                         *
                         * This helps if an online event was missed
                         * during a synchronization reconnect.
                         */
                        serverSynchronizer.sendCurrentState();
                }

                return true;
        }

        // =========================================================
        // REMOVE ONLINE USER
        // =========================================================

        public static void removeOnlineUser(
                        String username) {

                if (username == null) {
                        return;
                }

                ClientHandler removed = onlineUsers.remove(username);

                if (removed != null) {

                        System.out.println(
                                        username + " is now offline.");

                        if (serverSynchronizer != null) {

                                serverSynchronizer.send(
                                                "USER_OFFLINE:" + username);

                                serverSynchronizer.sendCurrentState();
                        }
                }
        }

        // =========================================================
        // SEND ONLINE USERS
        // =========================================================

        public static void sendOnlineUsers(
                        ClientHandler requester) {

                StringBuilder userList = new StringBuilder(
                                "ONLINE_USERS:");

                // -----------------------------------------------------
                // Local users
                // -----------------------------------------------------

                for (String username : onlineUsers.keySet()) {

                        userList
                                        .append(username)
                                        .append(",");
                }

                // -----------------------------------------------------
                // Remote users
                // -----------------------------------------------------

                if (serverSynchronizer != null) {

                        for (String username : serverSynchronizer
                                        .getRemoteOnlineUsers()) {

                                if (!onlineUsers.containsKey(username)) {

                                        userList
                                                        .append(username)
                                                        .append(",");
                                }
                        }
                }

                requester.sendMessage(
                                userList.toString());
        }

        // =========================================================
        // GET LOCAL USER
        // =========================================================

        public static ClientHandler getOnlineUser(
                        String username) {

                return onlineUsers.get(username);
        }

        // =========================================================
        // GET LOCAL USERS
        // =========================================================

        public static Set<String> getLocalOnlineUsers() {

                return Set.copyOf(
                                onlineUsers.keySet());
        }
        // =========================================================
        // BROADCAST MESSAGE
        // =========================================================

        public static void broadcastMessage(
                        String message,
                        ClientHandler sender) {

                // -----------------------------------------------------
                // Deliver ONLY to LOCAL users
                // -----------------------------------------------------

                for (ClientHandler client : onlineUsers.values()) {

                        if (client != sender) {

                                client.sendMessage(message);
                        }
                }

                // -----------------------------------------------------
                // NO REMOTE SERVER ROUTING
                // -----------------------------------------------------

                System.out.println(
                                "[BROADCAST] Message from "
                                                + sender.getUsername()
                                                + " delivered to local users only.");
        }
        // =========================================================
        // RECEIVE REMOTE BROADCAST
        // =========================================================

        // =========================================================
        // PRIVATE MESSAGE
        // =========================================================

        public static boolean sendPrivateMessage(
                        String sender,
                        String recipient,
                        String message) {

                // -----------------------------------------------------
                // CASE 1: LOCAL RECIPIENT
                // -----------------------------------------------------

                ClientHandler recipientHandler = onlineUsers.get(recipient);

                if (recipientHandler != null) {

                        recipientHandler.sendMessage(
                                        "PRIVATE from "
                                                        + sender
                                                        + ": "
                                                        + message);

                        return true;
                }

                // -----------------------------------------------------
                // CASE 2: REMOTE RECIPIENT
                // -----------------------------------------------------

                if (serverSynchronizer != null
                                && serverSynchronizer.isConnected()
                                && serverSynchronizer
                                                .getRemoteOnlineUsers()
                                                .contains(recipient)) {

                        serverSynchronizer.send(
                                        "PRIVATE_ROUTE:"
                                                        + sender
                                                        + ":"
                                                        + recipient
                                                        + ":"
                                                        + message);

                        System.out.println(
                                        "[ROUTE] Private message "
                                                        + sender
                                                        + " -> "
                                                        + recipient
                                                        + " sent to remote server.");

                        return true;
                }

                // -----------------------------------------------------
                // CASE 3: RECIPIENT NOT FOUND
                // -----------------------------------------------------

                return false;
        }

        // =========================================================
        // RECEIVE REMOTE PRIVATE MESSAGE
        // =========================================================

        public static boolean deliverRemotePrivateMessage(
                        String sender,
                        String recipient,
                        String message) {

                ClientHandler recipientHandler = onlineUsers.get(recipient);

                if (recipientHandler == null) {

                        System.out.println(
                                        "[ROUTE] Remote private message failed. "
                                                        + recipient
                                                        + " is not connected locally.");

                        return false;
                }

                recipientHandler.sendMessage(
                                "PRIVATE from "
                                                + sender
                                                + ": "
                                                + message);

                System.out.println(
                                "[ROUTE] Remote private message delivered: "
                                                + sender
                                                + " -> "
                                                + recipient);

                return true;
        }

        // =========================================================
        // GROUP CREATION
        // =========================================================

        public static boolean createGroup(
                        String groupName,
                        ClientHandler creator) {

                if (groupName == null || groupName.trim().isEmpty()
                                || creator == null
                                || creator.getUsername() == null) {
                        return false;
                }

                groupName = groupName.trim();
                String username = creator.getUsername().trim();

                // -----------------------------------------------------
                // Check whether group already exists
                // -----------------------------------------------------

                if (groups.containsKey(groupName)
                                || remoteGroupMembers.containsKey(groupName)) {

                        return false;
                }

                // -----------------------------------------------------
                // Save group to MongoDB
                // -----------------------------------------------------

                if (groupService == null) {
                        System.out.println(
                                        "[DB] GroupService is not initialized.");
                        return false;
                }

                boolean saved = groupService.createGroup(
                                groupName,
                                username);

                if (!saved) {
                        System.out.println(
                                        "[DB] Group was not saved: "
                                                        + groupName);
                        return false;
                }

                // -----------------------------------------------------
                // Create local in-memory group
                // -----------------------------------------------------

                Set<ClientHandler> members = ConcurrentHashMap.newKeySet();

                members.add(creator);

                groups.put(
                                groupName,
                                members);

                System.out.println(
                                "Group created: "
                                                + groupName);

                System.out.println(
                                "[DB] Group persisted: "
                                                + groupName);

                // -----------------------------------------------------
                // Notify remote server
                // -----------------------------------------------------

                if (serverSynchronizer != null
                                && serverSynchronizer.isConnected()) {

                        serverSynchronizer.send(
                                        "GROUP_CREATED:"
                                                        + groupName
                                                        + ":"
                                                        + username);

                        System.out.println(
                                        "[SYNC] Group creation sent: "
                                                        + groupName);
                }

                return true;
        }

        // =========================================================
        // JOIN GROUP
        // =========================================================

        public static boolean joinGroup(
                        String groupName,
                        ClientHandler client) {

                if (groupName == null
                                || groupName.trim().isEmpty()
                                || client == null
                                || client.getUsername() == null) {

                        return false;
                }

                groupName = groupName.trim();
                String username = client.getUsername().trim();

                // =====================================================
                // CASE 1: LOCAL GROUP
                // =====================================================

                Set<ClientHandler> members = groups.get(groupName);

                if (members != null) {

                        // -------------------------------------------------
                        // Check if already a member
                        // -------------------------------------------------

                        if (members.contains(client)) {
                                return false;
                        }

                        // -------------------------------------------------
                        // Save membership to MongoDB
                        // -------------------------------------------------

                        if (groupService == null) {
                                System.out.println(
                                                "[DB] GroupService is not initialized.");
                                return false;
                        }

                        boolean saved = groupService.addMember(
                                        groupName,
                                        username);

                        if (!saved) {
                                System.out.println(
                                                "[DB] Failed to save group membership: "
                                                                + username
                                                                + " -> "
                                                                + groupName);
                                return false;
                        }

                        // -------------------------------------------------
                        // Add member to local in-memory group
                        // -------------------------------------------------

                        members.add(client);

                        persistentGroupMembers
                                        .computeIfAbsent(
                                                        groupName,
                                                        key -> ConcurrentHashMap.newKeySet())
                                        .add(username);

                        System.out.println(
                                        username
                                                        + " joined group "
                                                        + groupName);

                        System.out.println(
                                        "[DB] Group membership persisted: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        // -------------------------------------------------
                        // Synchronize membership with remote server
                        // -------------------------------------------------

                        if (serverSynchronizer != null
                                        && serverSynchronizer.isConnected()) {

                                serverSynchronizer.send(
                                                "GROUP_JOIN:"
                                                                + groupName
                                                                + ":"
                                                                + username);

                                System.out.println(
                                                "[SYNC] Group join sent: "
                                                                + groupName
                                                                + " -> "
                                                                + username);
                        }

                        return true;
                }

                // =====================================================
                // CASE 2: RESTORED GROUP FROM MONGODB
                // =====================================================

                if (persistentGroupMembers.containsKey(groupName)) {

                        Set<String> persistentMembers = persistentGroupMembers.get(groupName);

                        // Already a member
                        if (persistentMembers.contains(username)) {
                                return false;
                        }

                        // -------------------------------------------------
                        // Save new member to MongoDB
                        // -------------------------------------------------

                        if (groupService == null) {
                                System.out.println(
                                                "[DB] GroupService is not initialized.");
                                return false;
                        }

                        boolean saved = groupService.addMember(
                                        groupName,
                                        username);

                        if (!saved) {
                                System.out.println(
                                                "[DB] Failed to save group membership: "
                                                                + username
                                                                + " -> "
                                                                + groupName);
                                return false;
                        }

                        // -------------------------------------------------
                        // Update restored membership
                        // -------------------------------------------------

                        persistentMembers.add(username);

                        // -------------------------------------------------
                        // Create local group state
                        // -------------------------------------------------

                        Set<ClientHandler> restoredMembers = ConcurrentHashMap.newKeySet();

                        // Add currently online members that belong
                        // to this persisted group.
                        for (ClientHandler onlineClient : onlineUsers.values()) {

                                if (onlineClient.getUsername() != null
                                                && persistentMembers.contains(
                                                                onlineClient.getUsername())) {

                                        restoredMembers.add(onlineClient);
                                }
                        }

                        // Add the joining client
                        restoredMembers.add(client);

                        groups.put(
                                        groupName,
                                        restoredMembers);

                        System.out.println(
                                        username
                                                        + " joined restored group "
                                                        + groupName);

                        System.out.println(
                                        "[DB] Restored group membership persisted: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        // -------------------------------------------------
                        // Synchronize with remote server
                        // -------------------------------------------------

                        if (serverSynchronizer != null
                                        && serverSynchronizer.isConnected()) {

                                serverSynchronizer.send(
                                                "GROUP_JOIN:"
                                                                + groupName
                                                                + ":"
                                                                + username);

                                System.out.println(
                                                "[SYNC] Restored group join sent: "
                                                                + groupName
                                                                + " -> "
                                                                + username);
                        }

                        return true;
                }

                // =====================================================
                // CASE 3: REMOTE GROUP
                // =====================================================

                if (remoteGroupMembers.containsKey(groupName)) {

                        if (serverSynchronizer != null
                                        && serverSynchronizer.isConnected()) {

                                /*
                                 * Remember that this LOCAL client has joined
                                 * a group hosted on the remote server.
                                 */

                                client.addRemoteGroup(groupName);

                                serverSynchronizer.send(
                                                "GROUP_JOIN_REQUEST:"
                                                                + groupName
                                                                + ":"
                                                                + username);

                                System.out.println(
                                                "[ROUTE] Remote group join request sent: "
                                                                + username
                                                                + " -> "
                                                                + groupName);

                                return true;
                        }
                }

                // =====================================================
                // CASE 4: GROUP DOES NOT EXIST
                // =====================================================

                return false;
        }

        public static boolean isRemoteGroup(String groupName) {
                return remoteGroupMembers.containsKey(groupName);
        }

        public static boolean isLocalRemoteGroupMember(
                        String groupName,
                        String username) {

                ClientHandler client = onlineUsers.get(username);

                if (client == null) {
                        return false;
                }

                return client.isRemoteGroupMember(groupName);
        }

        public static boolean isRemoteUserOnline(String username) {

                if (serverSynchronizer == null) {
                        return false;
                }

                return serverSynchronizer.isRemoteUserOnline(username);
        }

        public static boolean sendFileRouteRequest(
                        String sender,
                        String recipient,
                        String fileName,
                        long fileSize) {

                if (serverSynchronizer == null) {

                        System.out.println(
                                        "[FILE] ServerSynchronizer is not initialized.");

                        return false;
                }

                return serverSynchronizer.sendFileRouteRequest(
                                sender,
                                recipient,
                                fileName,
                                fileSize);
        }

        // =========================================================
        // LEAVE GROUP
        // =========================================================

        public static boolean leaveGroup(
                        String groupName,
                        ClientHandler client) {

                if (groupName == null
                                || client == null
                                || client.getUsername() == null) {

                        return false;
                }

                groupName = groupName.trim();
                String username = client.getUsername().trim();

                // =====================================================
                // CASE 1: LOCAL GROUP
                // =====================================================

                Set<ClientHandler> members = groups.get(groupName);

                if (members != null) {

                        if (!members.contains(client)) {
                                return false;
                        }

                        // -------------------------------------------------
                        // Remove member from MongoDB
                        // -------------------------------------------------

                        if (groupService == null) {
                                System.out.println(
                                                "[DB] GroupService is not initialized.");
                                return false;
                        }

                        boolean removed = groupService.removeMember(
                                        groupName,
                                        username);

                        if (!removed) {
                                System.out.println(
                                                "[DB] Failed to remove member from group: "
                                                                + username
                                                                + " -> "
                                                                + groupName);
                                return false;
                        }

                        // -------------------------------------------------
                        // Remove from persistent in-memory membership
                        // -------------------------------------------------

                        Set<String> persistentMembers = persistentGroupMembers.get(groupName);

                        if (persistentMembers != null) {
                                persistentMembers.remove(username);
                        }

                        // -------------------------------------------------
                        // Remove from local in-memory group
                        // -------------------------------------------------

                        members.remove(client);

                        System.out.println(
                                        username
                                                        + " left group "
                                                        + groupName);

                        System.out.println(
                                        "[DB] Group membership removed: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        // -------------------------------------------------
                        // Synchronize membership with remote server
                        // -------------------------------------------------

                        if (serverSynchronizer != null
                                        && serverSynchronizer.isConnected()) {

                                serverSynchronizer.send(
                                                "GROUP_LEAVE:"
                                                                + groupName
                                                                + ":"
                                                                + username);
                        }

                        // -------------------------------------------------
                        // Check whether group is completely empty
                        // -------------------------------------------------

                        Set<String> remoteMembers = remoteGroupMembers.get(groupName);

                        boolean hasRemoteMembers = remoteMembers != null
                                        && !remoteMembers.isEmpty();

                        boolean hasPersistentMembers = persistentMembers != null
                                        && !persistentMembers.isEmpty();

                        if (members.isEmpty()
                                        && !hasRemoteMembers
                                        && !hasPersistentMembers) {

                                // ---------------------------------------------
                                // Delete group from MongoDB
                                // ---------------------------------------------

                                groupService.deleteGroup(
                                                groupName);

                                persistentGroupMembers.remove(
                                                groupName);

                                groups.remove(
                                                groupName);

                                System.out.println(
                                                "Local group "
                                                                + groupName
                                                                + " deleted because it is empty.");

                                // ---------------------------------------------
                                // Notify remote server
                                // ---------------------------------------------

                                if (serverSynchronizer != null
                                                && serverSynchronizer.isConnected()) {

                                        serverSynchronizer.send(
                                                        "GROUP_DELETED:"
                                                                        + groupName);
                                }
                        }

                        return true;
                }

                // =====================================================
                // CASE 2: REMOTE GROUP
                // =====================================================

                if (client.isRemoteGroupMember(groupName)) {

                        client.removeRemoteGroup(groupName);

                        System.out.println(
                                        username
                                                        + " left remote group "
                                                        + groupName);

                        if (serverSynchronizer != null
                                        && serverSynchronizer.isConnected()) {

                                serverSynchronizer.send(
                                                "GROUP_LEAVE:"
                                                                + groupName
                                                                + ":"
                                                                + username);
                        }

                        return true;
                }

                return false;
        }

        // =========================================================
        // GROUP MESSAGE
        // =========================================================

        public static boolean sendGroupMessage(
                        String groupName,
                        ClientHandler sender,
                        String message) {

                if (groupName == null || sender == null || message == null) {
                        return false;
                }

                groupName = groupName.trim();

                // =====================================================
                // CASE 1: GROUP IS LOCAL TO THIS SERVER
                // =====================================================

                Set<ClientHandler> localMembers = groups.get(groupName);

                if (localMembers != null) {

                        // Sender must be a member of the local group
                        if (!localMembers.contains(sender)) {
                                return false;
                        }

                        // Send to local members
                        for (ClientHandler member : localMembers) {

                                if (member != sender) {

                                        member.sendMessage(
                                                        "GROUP [" + groupName + "] "
                                                                        + sender.getUsername()
                                                                        + ": "
                                                                        + message);
                                }
                        }

                        // -------------------------------------------------
                        // Route to the other server
                        // -------------------------------------------------

                        Set<String> remoteMembers = remoteGroupMembers.get(groupName);

                        if (remoteMembers != null
                                        && !remoteMembers.isEmpty()
                                        && serverSynchronizer != null
                                        && serverSynchronizer.isConnected()) {

                                serverSynchronizer.send(
                                                "GROUP_MESSAGE_ROUTE:"
                                                                + groupName
                                                                + ":"
                                                                + sender.getUsername()
                                                                + ":"
                                                                + message);

                                System.out.println(
                                                "[ROUTE] Local group message routed: "
                                                                + sender.getUsername()
                                                                + " -> "
                                                                + groupName);
                        }

                        return true;
                }

                // =====================================================
                // CASE 2: GROUP IS REMOTE
                // =====================================================

                if (sender.isRemoteGroupMember(groupName)) {

                        if (serverSynchronizer == null
                                        || !serverSynchronizer.isConnected()) {

                                return false;
                        }

                        serverSynchronizer.send(
                                        "GROUP_MESSAGE_ROUTE:"
                                                        + groupName
                                                        + ":"
                                                        + sender.getUsername()
                                                        + ":"
                                                        + message);

                        System.out.println(
                                        "[ROUTE] Remote group message routed: "
                                                        + sender.getUsername()
                                                        + " -> "
                                                        + groupName);

                        return true;
                }

                return false;
        }

        // =========================================================
        // GROUP LIST
        // =========================================================

        public static void sendGroupList(
                        ClientHandler requester) {

                StringBuilder groupList = new StringBuilder("GROUPS:");

                Set<String> allGroups = ConcurrentHashMap.newKeySet();

                // -----------------------------------------------------
                // Local groups currently in memory
                // -----------------------------------------------------

                allGroups.addAll(
                                groups.keySet());

                // -----------------------------------------------------
                // Remote groups
                // -----------------------------------------------------

                allGroups.addAll(
                                remoteGroupMembers.keySet());

                // -----------------------------------------------------
                // Groups restored from MongoDB
                // -----------------------------------------------------

                allGroups.addAll(
                                persistentGroupMembers.keySet());

                // -----------------------------------------------------
                // Build response
                // -----------------------------------------------------

                for (String groupName : allGroups) {

                        groupList
                                        .append(groupName)
                                        .append(",");
                }

                requester.sendMessage(
                                groupList.toString());
        }

        // =========================================================
        // GROUP MEMBERS
        // =========================================================

        public static void sendGroupMembers(
                        String groupName,
                        ClientHandler requester) {

                if (groupName == null
                                || groupName.trim().isEmpty()) {

                        requester.sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                groupName = groupName.trim();

                Set<ClientHandler> localMembers = groups.get(groupName);

                Set<String> remoteMembers = remoteGroupMembers.get(groupName);

                Set<String> persistentMembers = persistentGroupMembers.get(groupName);

                // -----------------------------------------------------
                // GROUP DOES NOT EXIST
                // -----------------------------------------------------

                if (localMembers == null
                                && remoteMembers == null
                                && persistentMembers == null
                                && !requester.isRemoteGroupMember(groupName)) {

                        requester.sendMessage(
                                        "SYSTEM: Group does not exist.");

                        return;
                }

                Set<String> allMembers = new LinkedHashSet<>();

                // -----------------------------------------------------
                // PERSISTED MEMBERS FROM MONGODB
                // -----------------------------------------------------

                if (persistentMembers != null) {

                        allMembers.addAll(
                                        persistentMembers);
                }

                // -----------------------------------------------------
                // LOCAL MEMBERS
                // -----------------------------------------------------

                if (localMembers != null) {

                        for (ClientHandler member : localMembers) {

                                if (member.getUsername() != null) {

                                        allMembers.add(
                                                        member.getUsername());
                                }
                        }
                }

                // -----------------------------------------------------
                // REMOTE MEMBERS
                // -----------------------------------------------------

                if (remoteMembers != null) {

                        allMembers.addAll(
                                        remoteMembers);
                }

                // -----------------------------------------------------
                // LOCAL USERS WHO JOINED A REMOTE GROUP
                // -----------------------------------------------------

                for (ClientHandler client : onlineUsers.values()) {

                        if (client.isRemoteGroupMember(
                                        groupName)) {

                                if (client.getUsername() != null) {

                                        allMembers.add(
                                                        client.getUsername());
                                }
                        }
                }

                // -----------------------------------------------------
                // SEND RESULT
                // -----------------------------------------------------

                StringBuilder membersList = new StringBuilder(
                                "MEMBERS ["
                                                + groupName
                                                + "]: ");

                for (String username : allMembers) {

                        membersList
                                        .append(username)
                                        .append(" ");
                }

                requester.sendMessage(
                                membersList.toString().trim());
        }

        // =========================================================
        // GROUP MEMBER CHECK
        // =========================================================

        public static boolean isGroupMember(
                        String groupName,
                        ClientHandler client) {

                if (groupName == null
                                || client == null) {

                        return false;
                }

                Set<ClientHandler> members = groups.get(groupName);

                if (members != null
                                && members.contains(client)) {

                        return true;
                }

                return client.isRemoteGroupMember(
                                groupName);
        }

        // =========================================================
        // CHECK ANY GROUP MEMBERSHIP
        // =========================================================

        public static boolean isAnyGroupMember(
                        String groupName,
                        ClientHandler client) {

                if (groupName == null
                                || client == null) {

                        return false;
                }

                // Local group
                if (isGroupMember(
                                groupName,
                                client)) {

                        return true;
                }

                // Remote group
                return client.isRemoteGroupMember(
                                groupName);
        }

        // =========================================================
        // GROUP EXISTS
        // =========================================================

        public static boolean groupExists(
                        String groupName) {

                return groups.containsKey(groupName)
                                || remoteGroupMembers.containsKey(groupName);
        }

        // =========================================================
        // REMOTE GROUP CREATED
        // =========================================================

        public static void createRemoteGroup(
                        String groupName,
                        String creator) {

                if (groupName == null
                                || groupName.trim().isEmpty()) {

                        return;
                }

                groupName = groupName.trim();

                Set<String> members = remoteGroupMembers.computeIfAbsent(
                                groupName,
                                key -> ConcurrentHashMap.newKeySet());

                if (creator != null
                                && !creator.trim().isEmpty()) {

                        members.add(
                                        creator.trim());
                }

                System.out.println(
                                "[SYNC] Remote group created: "
                                                + groupName
                                                + " by "
                                                + creator);
        }

        // =========================================================
        // REMOTE GROUP JOIN
        // =========================================================

        public static void addRemoteGroupMember(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null
                                || groupName.trim().isEmpty()
                                || username.trim().isEmpty()) {

                        return;
                }

                Set<String> members = remoteGroupMembers.computeIfAbsent(
                                groupName.trim(),
                                key -> ConcurrentHashMap.newKeySet());

                members.add(
                                username.trim());

                System.out.println(
                                "[SYNC] Remote group member added: "
                                                + username
                                                + " -> "
                                                + groupName);
        }

        // =========================================================
        // PERSIST REMOTE GROUP MEMBER
        // =========================================================

        public static boolean addPersistentGroupMember(
                        String groupName,
                        String username) {

                if (groupService == null) {
                        System.out.println(
                                        "[DB] GroupService is not initialized.");
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

                // -----------------------------------------------------
                // Save member to MongoDB
                // -----------------------------------------------------

                boolean success = groupService.addMember(
                                groupName,
                                username);

                if (!success) {

                        System.out.println(
                                        "[DB] Failed to save member: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return false;
                }

                // -----------------------------------------------------
                // Update persistent in-memory state
                // -----------------------------------------------------

                Set<String> members = persistentGroupMembers.computeIfAbsent(
                                groupName,
                                key -> ConcurrentHashMap.newKeySet());

                members.add(username);

                System.out.println(
                                "[DB] Persistent group member added: "
                                                + username
                                                + " -> "
                                                + groupName);

                return true;
        }

        // =========================================================
        // REMOVE PERSISTENT GROUP MEMBER
        // =========================================================

        public static boolean removePersistentGroupMember(
                        String groupName,
                        String username) {

                if (groupService == null) {
                        System.out.println(
                                        "[DB] GroupService is not initialized.");
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

                // -----------------------------------------------------
                // Remove member from MongoDB
                // -----------------------------------------------------

                boolean success = groupService.removeMember(
                                groupName,
                                username);

                if (!success) {

                        System.out.println(
                                        "[DB] Failed to remove member: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return false;
                }

                // -----------------------------------------------------
                // Update persistent in-memory state
                // -----------------------------------------------------

                Set<String> members = persistentGroupMembers.get(groupName);

                if (members != null) {
                        members.remove(username);
                }

                System.out.println(
                                "[DB] Persistent group member removed: "
                                                + username
                                                + " -> "
                                                + groupName);

                return true;
        }

        // =========================================================
        // CONFIRM REMOTE GROUP JOIN
        // =========================================================

        public static void confirmRemoteGroupJoin(
                        String groupName,
                        String username) {

                if (groupName == null || username == null) {
                        return;
                }

                ClientHandler client = onlineUsers.get(username.trim());

                if (client == null) {

                        System.out.println(
                                        "[SYNC] Cannot confirm remote group join. "
                                                        + username
                                                        + " is not locally online.");

                        return;
                }

                client.addRemoteGroup(groupName.trim());

                System.out.println(
                                "[SYNC] Remote group membership confirmed: "
                                                + username
                                                + " -> "
                                                + groupName);
        }

        // =========================================================
        // REMOTE GROUP LEAVE
        // =========================================================

        public static void removeRemoteGroupMember(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null) {

                        return;
                }

                Set<String> members = remoteGroupMembers.get(
                                groupName.trim());

                if (members == null) {
                        return;
                }

                members.remove(
                                username.trim());

                System.out.println(
                                "[SYNC] Remote group member removed: "
                                                + username
                                                + " -> "
                                                + groupName);

                if (members.isEmpty()) {

                        remoteGroupMembers.remove(
                                        groupName.trim());

                        System.out.println(
                                        "[SYNC] Remote group removed: "
                                                        + groupName);
                }
        }

        // =========================================================
        // REMOTE GROUP DELETED
        // =========================================================

        public static void deleteRemoteGroup(
                        String groupName) {

                if (groupName == null) {
                        return;
                }

                groupName = groupName.trim();

                remoteGroupMembers.remove(groupName);

                // Remove this remote group from all local clients.
                for (ClientHandler client : onlineUsers.values()) {
                        client.removeRemoteGroup(groupName);
                }

                System.out.println(
                                "[SYNC] Remote group deleted: "
                                                + groupName);
        }

        public static void loadGroupsFromDatabase() {

                if (groupService == null) {
                        System.out.println(
                                        "[DB] GroupService is not initialized.");
                        return;
                }

                persistentGroupMembers.clear();

                for (String groupName : groupService.getAllGroups()) {

                        Set<String> members = ConcurrentHashMap.newKeySet();

                        members.addAll(
                                        groupService.getMembers(groupName));

                        persistentGroupMembers.put(
                                        groupName,
                                        members);

                        System.out.println(
                                        "[DB] Loaded group: "
                                                        + groupName
                                                        + " | Members: "
                                                        + members);
                }

                System.out.println(
                                "[DB] Persistent groups loaded: "
                                                + persistentGroupMembers.size());
        }

        // =========================================================
        // CHECK LOCAL GROUP
        // =========================================================

        public static boolean isLocalGroup(
                        String groupName) {

                if (groupName == null) {
                        return false;
                }

                return groups.containsKey(
                                groupName.trim());
        }

        // =========================================================
        // ADD REMOTE MEMBER TO LOCAL GROUP
        // =========================================================

        public static boolean addLocalGroupMember(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null
                                || groupName.trim().isEmpty()
                                || username.trim().isEmpty()) {

                        return false;
                }

                groupName = groupName.trim();
                username = username.trim();

                Set<ClientHandler> members = groups.get(groupName);

                if (members == null) {
                        return false;
                }

                /*
                 * The user is connected to the OTHER server,
                 * therefore we cannot add a ClientHandler here.
                 *
                 * The actual remote membership is represented
                 * by the synchronization layer.
                 *
                 * We only confirm that the group exists locally.
                 */

                System.out.println(
                                "[SYNC] Remote member registered: "
                                                + username
                                                + " -> "
                                                + groupName);

                return true;
        }

        // =========================================================
        // GET REMOTE GROUP MEMBERS
        // =========================================================

        public static Set<String> getRemoteGroupMembers(
                        String groupName) {

                Set<String> members = remoteGroupMembers.get(
                                groupName);

                if (members == null) {

                        return Set.of();
                }

                return Set.copyOf(members);
        }

        // =========================================================
        // CHECK REMOTE GROUP MEMBER
        // =========================================================

        public static boolean isRemoteGroupMember(
                        String groupName,
                        String username) {

                Set<String> members = remoteGroupMembers.get(
                                groupName);

                if (members == null) {
                        return false;
                }

                return members.contains(
                                username);
        }

        // =========================================================
        // RECEIVE REMOTE GROUP MESSAGE
        // =========================================================

        public static void deliverRemoteGroupMessage(
                        String groupName,
                        String sender,
                        String message) {

                groupName = groupName.trim();

                String formattedMessage = "GROUP [" + groupName + "] "
                                + sender + ": " + message;

                System.out.println(
                                "[ROUTE] Delivering remote group message: "
                                                + sender + " -> " + groupName);

                // =====================================================
                // CASE 1: GROUP IS LOCAL HERE
                // =====================================================

                Set<ClientHandler> localMembers = groups.get(groupName);

                if (localMembers != null) {

                        System.out.println(
                                        "[ROUTE] Group " + groupName
                                                        + " exists locally. Delivering to local members.");

                        for (ClientHandler client : localMembers) {

                                client.sendMessage(formattedMessage);
                        }

                        return;
                }

                // =====================================================
                // CASE 2: GROUP IS REMOTE HERE
                // =====================================================

                boolean delivered = false;

                for (ClientHandler client : onlineUsers.values()) {

                        if (client.isRemoteGroupMember(groupName)) {

                                client.sendMessage(formattedMessage);

                                delivered = true;
                        }
                }

                if (delivered) {

                        System.out.println(
                                        "[ROUTE] Message delivered to remote-group members.");

                } else {

                        System.out.println(
                                        "[ROUTE] No local members found for remote group "
                                                        + groupName);
                }
        }

        // =========================================================
        // STATISTICS
        // =========================================================

        public static int getOnlineUserCount() {

                return onlineUsers.size();
        }

        public static int getGroupCount() {

                return groups.size();
        }

        // =========================================================
        // GET SYNCHRONIZER
        // =========================================================

        public static ServerSynchronizer getServerSynchronizer() {

                return serverSynchronizer;
        }
}