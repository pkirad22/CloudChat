package server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

import service.FileValidationService;

import service.OfflineMessageService;
import service.OfflineGroupMessageService;

import model.User;
import model.Message;

import service.UserService;
import service.ChatHistoryService;
import service.GroupService;
import service.OfflineFileService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

        // =========================================================
        // CLIENT CONNECTION
        // =========================================================

        private final Socket clientSocket;

        private BufferedReader input;

        private PrintWriter output;

        private String username;

        // =========================================================
        // REMOTE GROUP MEMBERSHIP
        // =========================================================

        /*
         * Groups that this client joined on another server.
         *
         * Example:
         *
         * Pranav connected to Server 2
         * and joined MCA hosted on Server 1.
         *
         * remoteJoinedGroups = [MCA]
         */
        private final Set<String> remoteJoinedGroups = ConcurrentHashMap.newKeySet();

        // =========================================================
        // SERVICES
        // =========================================================

        private final UserService userService;

        private final ChatHistoryService chatHistoryService;

        private final GroupService groupService;

        private final OfflineMessageService offlineMessageService;

        private final OfflineFileService offlineFileService;

        private final OfflineGroupMessageService offlineGroupMessageService;

        // =========================================================
        // CONSTRUCTOR
        // =========================================================

        public ClientHandler(Socket clientSocket) {

                this.clientSocket = clientSocket;

                this.userService = new UserService();

                this.groupService = new GroupService();

                this.chatHistoryService = new ChatHistoryService();

                this.offlineMessageService = new OfflineMessageService();

                this.offlineFileService = new OfflineFileService();

                this.offlineGroupMessageService = new OfflineGroupMessageService();
        }

        // =========================================================
        // RUN
        // =========================================================

        @Override
        public void run() {

                try {

                        input = new BufferedReader(
                                        new InputStreamReader(
                                                        clientSocket.getInputStream()));

                        output = new PrintWriter(
                                        clientSocket.getOutputStream(),
                                        true);

                        // =================================================
                        // AUTHENTICATION
                        // =================================================

                        boolean authenticated = false;

                        // =================================================
                        // SERVER LOAD INFORMATION
                        // =================================================

                        ServerLoadMonitor loadMonitor = new ServerLoadMonitor();

                        double loadScore = loadMonitor.getLoadScore();

                        String loadLevel = loadMonitor.getLoadLevel();

                        sendMessage(
                                        "SERVER_LOAD:"
                                                        + String.format("%.2f", loadScore)
                                                        + ":"
                                                        + loadLevel);

                        while (!authenticated) {

                                sendMessage("AUTH_REQUEST");

                                String authChoice = input.readLine();

                                if (authChoice == null) {

                                        closeConnection();

                                        return;
                                }

                                authChoice = authChoice
                                                .trim()
                                                .toLowerCase();

                                // =================================================
                                // REGISTER
                                // =================================================

                                if (authChoice.equals("register")) {

                                        sendMessage(
                                                        "USERNAME_REQUEST");

                                        String registerUsername = input.readLine();

                                        if (registerUsername == null
                                                        || registerUsername
                                                                        .trim()
                                                                        .isEmpty()) {

                                                sendMessage(
                                                                "ERROR: Username cannot be empty.");

                                                continue;
                                        }

                                        registerUsername = registerUsername.trim();

                                        sendMessage(
                                                        "EMAIL_REQUEST");

                                        String email = input.readLine();

                                        if (email == null
                                                        || email
                                                                        .trim()
                                                                        .isEmpty()) {

                                                sendMessage(
                                                                "ERROR: Email cannot be empty.");

                                                continue;
                                        }

                                        email = email.trim();

                                        sendMessage(
                                                        "PASSWORD_REQUEST");

                                        String password = input.readLine();

                                        if (password == null
                                                        || password.isEmpty()) {

                                                sendMessage(
                                                                "ERROR: Password cannot be empty.");

                                                continue;
                                        }

                                        User newUser = new User(
                                                        registerUsername,
                                                        password,
                                                        email);

                                        boolean registered = userService.registerUser(
                                                        newUser);

                                        if (registered) {

                                                sendMessage(
                                                                "REGISTER_SUCCESS: "
                                                                                + "Registration successful. "
                                                                                + "Please login.");

                                        } else {

                                                sendMessage(
                                                                "REGISTER_FAILED: "
                                                                                + "Username already exists.");
                                        }
                                }

                                // =================================================
                                // LOGIN
                                // =================================================

                                else if (authChoice.equals("login")) {

                                        sendMessage(
                                                        "USERNAME_REQUEST");

                                        String loginUsername = input.readLine();

                                        if (loginUsername == null
                                                        || loginUsername
                                                                        .trim()
                                                                        .isEmpty()) {

                                                sendMessage(
                                                                "ERROR: Username cannot be empty.");

                                                continue;
                                        }

                                        loginUsername = loginUsername.trim();

                                        sendMessage(
                                                        "PASSWORD_REQUEST");

                                        String password = input.readLine();

                                        if (password == null
                                                        || password.isEmpty()) {

                                                sendMessage(
                                                                "ERROR: Password cannot be empty.");

                                                continue;
                                        }

                                        User loggedInUser = userService.loginUser(
                                                        loginUsername,
                                                        password);

                                        if (loggedInUser == null) {

                                                sendMessage(
                                                                "LOGIN_FAILED: "
                                                                                + "Invalid username or password.");

                                                continue;
                                        }

                                        // =============================================
                                        // ADD USER TO CURRENT SERVER
                                        // =============================================

                                        if (!ChatServer.addOnlineUser(
                                                        loginUsername,
                                                        this)) {

                                                sendMessage(
                                                                "ERROR: Username already online.");

                                                continue;
                                        }

                                        username = loginUsername;

                                        authenticated = true;

                                        // =============================================
                                        // LOGIN SUCCESS
                                        // =============================================

                                        sendMessage(
                                                        "LOGIN_SUCCESS: Welcome "
                                                                        + username
                                                                        + "!");

                                        // =================================================
                                        // DELIVER OFFLINE MESSAGES
                                        // =================================================

                                        deliverOfflineMessages();

                                        deliverOfflineFiles();

                                        deliverOfflineGroupMessages();

                                        deliverOfflineGroupFiles();

                                        // =============================================
                                        // BROADCAST USER JOIN
                                        // =============================================

                                        ChatServer.broadcastMessage(
                                                        "SYSTEM: "
                                                                        + username
                                                                        + " joined the chat.",
                                                        this);

                                        // =============================================
                                        // SEND ONLINE USERS
                                        // =============================================

                                        ChatServer.sendOnlineUsers(
                                                        this);
                                }

                                // =================================================
                                // INVALID AUTH OPTION
                                // =================================================

                                else {

                                        sendMessage(
                                                        "ERROR: Please choose "
                                                                        + "LOGIN or REGISTER.");
                                }
                        }

                        // =====================================================
                        // START CHAT
                        // =====================================================

                        String message;

                        while ((message = input.readLine()) != null) {

                                message = message.trim();

                                if (message.isEmpty()) {

                                        continue;
                                }

                                // =================================================
                                // ONLINE USERS
                                // =================================================

                                if (message.equalsIgnoreCase("/users")) {

                                        ChatServer.sendOnlineUsers(
                                                        this);

                                        continue;
                                }

                                // =================================================
                                // GROUP LIST
                                // =================================================

                                if (message.equalsIgnoreCase("/groups")) {

                                        ChatServer.sendGroupList(
                                                        this);

                                        continue;
                                }

                                if (message.equalsIgnoreCase("/load")) {

                                        ServerLoadMonitor monitor = new ServerLoadMonitor();

                                        monitor.printLoadInformation();

                                        sendMessage(
                                                        "SYSTEM: Load information printed on server console.");

                                        continue;
                                }

                                // =================================================
                                // CONNECTION LOAD CHECK
                                // =================================================

                                if (message.equalsIgnoreCase("/connectionload")) {

                                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                                        if (synchronizer == null) {

                                                sendMessage(
                                                                "SYSTEM: Server synchronizer is not available.");

                                                continue;
                                        }

                                        ServerLoadMonitor monitor = new ServerLoadMonitor();

                                        double localLoad = monitor.getLoadScore();

                                        double remoteLoad = synchronizer.getRemoteLoadScore();

                                        String localServer = synchronizer.isPrimaryServer()
                                                        ? "SERVER1"
                                                        : "SERVER2";

                                        String remoteServer = synchronizer.isPrimaryServer()
                                                        ? "SERVER2"
                                                        : "SERVER1";

                                        sendMessage(
                                                        "CONNECTION_LOAD:"
                                                                        + localServer
                                                                        + ":"
                                                                        + localLoad
                                                                        + ":"
                                                                        + remoteServer
                                                                        + ":"
                                                                        + remoteLoad);

                                        continue;
                                }

                                if (message.equalsIgnoreCase("/balance")) {

                                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                                        if (synchronizer == null) {

                                                sendMessage(
                                                                "SYSTEM: Server synchronizer is not available.");

                                                continue;
                                        }

                                        LoadBalancer loadBalancer = new LoadBalancer(synchronizer);

                                        loadBalancer.printDecision();

                                        sendMessage(
                                                        "SYSTEM: Load balancing decision printed on server console.");

                                        continue;
                                }

                                // =================================================
                                // AUTOMATIC LOAD-BALANCED CONNECTION
                                // =================================================

                                if (message.equalsIgnoreCase("/balanceconnect")) {

                                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                                        if (synchronizer == null) {

                                                sendMessage(
                                                                "SYSTEM: Server synchronizer is not available.");

                                                continue;
                                        }

                                        LoadBalancer loadBalancer = new LoadBalancer(synchronizer);

                                        String selectedServer = loadBalancer.selectServer();

                                        System.out.println();
                                        System.out.println(
                                                        "[AUTO-BALANCE] Selected server: "
                                                                        + selectedServer);

                                        // =============================================
                                        // LOCAL SERVER SELECTED
                                        // =============================================

                                        if (selectedServer.equals(
                                                        synchronizer.isPrimaryServer()
                                                                        ? "SERVER1"
                                                                        : "SERVER2")) {

                                                sendMessage(
                                                                "SYSTEM: Current server has lower load.");

                                                sendMessage(
                                                                "SYSTEM: You are already connected to the selected server.");

                                                continue;
                                        }

                                        // =============================================
                                        // REMOTE SERVER SELECTED
                                        // =============================================

                                        String targetHost = "localhost";

                                        int targetPort;

                                        if (selectedServer.equals("SERVER1")) {

                                                targetPort = 5000;

                                        } else if (selectedServer.equals("SERVER2")) {

                                                targetPort = 5002;

                                        } else {

                                                sendMessage(
                                                                "SYSTEM: Unable to determine target server.");

                                                continue;
                                        }

                                        System.out.println(
                                                        "[AUTO-BALANCE] Redirecting client to "
                                                                        + targetHost
                                                                        + ":"
                                                                        + targetPort);

                                        sendMessage(
                                                        "CONNECT_SERVER:"
                                                                        + targetHost
                                                                        + ":"
                                                                        + targetPort);

                                        continue;
                                }

                                // =================================================
                                // CREATE GROUP
                                // =================================================

                                if (message.startsWith("/create ")) {

                                        handleCreateGroup(message);

                                        continue;
                                }

                                // =================================================
                                // JOIN GROUP
                                // =================================================

                                if (message.startsWith("/joingroup ")) {

                                        handleJoinGroup(message);

                                        continue;
                                }

                                // =================================================
                                // LEAVE GROUP
                                // =================================================

                                if (message.startsWith("/leave ")) {

                                        handleLeaveGroup(message);

                                        continue;
                                }

                                // =================================================
                                // GROUP MEMBERS
                                // =================================================

                                if (message.startsWith("/members ")) {

                                        handleGroupMembers(message);

                                        continue;
                                }

                                // =================================================
                                // GROUP OWNER MANAGEMENT
                                // =================================================

                                if (message.startsWith("/addmember ")) {

                                        handleAddMember(message);

                                        continue;
                                }

                                if (message.startsWith("/removemember ")) {

                                        handleRemoveMember(message);

                                        continue;
                                }

                                if (message.startsWith("/promote ")) {

                                        handlePromoteMember(message);

                                        continue;
                                }

                                if (message.startsWith("/demote ")) {

                                        handleDemoteMember(message);

                                        continue;
                                }

                                if (message.startsWith("/requestjoin ")) {
                                        handleRequestJoin(message);
                                        continue;
                                }

                                if (message.startsWith("/requests ")) {
                                        handleRequests(message);
                                        continue;
                                }

                                if (message.startsWith("/approve ")) {
                                        handleApprove(message);
                                        continue;
                                }

                                if (message.startsWith("/reject ")) {
                                        handleReject(message);
                                        continue;
                                }

                                if (message.startsWith("/join ")) {
                                        handleJoin(message);
                                        continue;
                                }

                                if (message.startsWith("/setaccess ")) {
                                        handleSetAccess(message);
                                        continue;
                                }

                                // =================================================
                                // GROUP MESSAGE
                                // =================================================

                                if (message.startsWith("/groupmsg ")) {

                                        handleGroupMessage(message);

                                        continue;
                                }

                                // =================================================
                                // FILE TRANSFER
                                // =================================================

                                if (message.startsWith("/sendfile ")) {

                                        handleSendFile(message);

                                        continue;
                                }

                                if (message.startsWith("/groupfile ")) {
                                        handleGroupFile(message);
                                        continue;
                                }

                                // =================================================
                                // PRIVATE MESSAGE
                                // =================================================

                                if (message.startsWith("/msg ")) {

                                        handlePrivateMessage(message);

                                        continue;
                                }

                                // =================================================
                                // PRIVATE CHAT HISTORY
                                // =================================================

                                if (message.startsWith("/history ")) {

                                        handlePrivateChatHistory(message);

                                        continue;
                                }

                                // =================================================
                                // GROUP CHAT HISTORY
                                // =================================================

                                if (message.startsWith("/grouphistory ")) {

                                        handleGroupChatHistory(message);

                                        continue;
                                }

                                // =================================================
                                // EXIT
                                // =================================================

                                if (message.equalsIgnoreCase("/exit")) {

                                        break;
                                }

                                // =================================================
                                // BROADCAST MESSAGE
                                // =================================================

                                String formattedMessage = username
                                                + ": "
                                                + message;

                                System.out.println(
                                                formattedMessage);

                                Message chatMessage = new Message(
                                                username,
                                                null,
                                                null,
                                                message,
                                                "BROADCAST",
                                                LocalDateTime.now());

                                chatHistoryService.saveMessage(
                                                chatMessage);

                                ChatServer.broadcastMessage(
                                                formattedMessage,
                                                this);
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "Connection error for "
                                                        + username
                                                        + ": "
                                                        + e.getMessage());

                } finally {

                        disconnect();
                }
        }

        // =========================================================
        // CREATE GROUP
        // =========================================================

        private void handleCreateGroup(
                        String message) {

                String groupName = message.substring(
                                "/create ".length())
                                .trim();

                if (groupName.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                boolean created = ChatServer.createGroup(
                                groupName,
                                this);

                if (created) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' created successfully.");

                } else {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' already exists.");
                }
        }

        // =========================================================
        // JOIN GROUP
        // =========================================================

        private void handleJoinGroup(
                        String message) {

                String groupName = message.substring(
                                "/join ".length())
                                .trim();

                if (groupName.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                boolean joined = ChatServer.joinGroup(
                                groupName,
                                this);

                if (joined) {

                        sendMessage(
                                        "SYSTEM: You joined group '"
                                                        + groupName
                                                        + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' does not exist.");
                }
        }

        // =========================================================
        // LEAVE GROUP
        // =========================================================

        private void handleLeaveGroup(
                        String message) {

                String groupName = message.substring(
                                "/leave ".length())
                                .trim();

                boolean left = ChatServer.leaveGroup(
                                groupName,
                                this);

                if (left) {

                        sendMessage(
                                        "SYSTEM: You left group '"
                                                        + groupName
                                                        + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: You are not a member of '"
                                                        + groupName
                                                        + "'.");
                }
        }

        // =========================================================
        // SEND FILE
        // =========================================================

        private void handleSendFile(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {
                        sendMessage("SYSTEM: Invalid format.");
                        sendMessage("SYSTEM: Use /sendfile username filepath");
                        return;
                }

                String recipient = parts[1];
                String filePath = parts[2];

                File file = new File(filePath);

                // -----------------------------------------------------
                // Validate file
                // -----------------------------------------------------

                if (!file.exists()) {
                        sendMessage("SYSTEM: File does not exist.");
                        return;
                }

                if (!file.isFile()) {
                        sendMessage("SYSTEM: Selected path is not a file.");
                        return;
                }

                if (!FileValidationService.isAllowed(file)) {
                        sendMessage("SYSTEM: Unsupported file type.");

                        sendMessage(
                                        "SYSTEM: Supported file categories: "
                                                        + "Images, Documents, Office, Audio, Video, Archives and Code.");

                        sendMessage(
                                        "SYSTEM: File type: "
                                                        + FileValidationService.getFileType(file));

                        return;
                }

                // -----------------------------------------------------
                // Check local recipient
                // -----------------------------------------------------

                ClientHandler recipientHandler = ChatServer.getOnlineUser(recipient);

                if (recipientHandler != null) {

                        System.out.println(
                                        "[FILE] Local transfer: "
                                                        + username
                                                        + " -> "
                                                        + recipient);

                        recipientHandler.sendMessage(
                                        "FILE_INCOMING: "
                                                        + username
                                                        + " wants to send you "
                                                        + file.getName());

                        recipientHandler.sendMessage(
                                        "FILE_INFO: "
                                                        + file.length()
                                                        + " bytes");

                        Thread fileTransferThread = new Thread(
                                        new FileTransferServer(
                                                        username,
                                                        recipient,
                                                        file));

                        fileTransferThread.start();

                        sendMessage(
                                        "SYSTEM: File transfer started.");

                        return;
                }

                // -----------------------------------------------------
                // Check remote recipient
                // -----------------------------------------------------

                if (ChatServer.isRemoteUserOnline(recipient)) {

                        System.out.println(
                                        "[FILE] Remote recipient detected: "
                                                        + username
                                                        + " -> "
                                                        + recipient);

                        // -------------------------------------------------
                        // Start distributed file transfer server
                        // -------------------------------------------------

                        DistributedFileTransferServer transferServer = new DistributedFileTransferServer(
                                        file,
                                        username,
                                        recipient);

                        Thread transferThread = new Thread(
                                        transferServer,
                                        "DistributedFileTransferServer-"
                                                        + file.getName());

                        transferThread.start();

                        // -------------------------------------------------
                        // Wait until transfer port is ready
                        // -------------------------------------------------

                        boolean portReady = transferServer.awaitPort(5000);

                        if (!portReady) {

                                sendMessage(
                                                "SYSTEM: Unable to start distributed file transfer.");

                                return;
                        }

                        int transferPort = transferServer.getTransferPort();

                        System.out.println(
                                        "[FILE-DIST] Transfer port ready: "
                                                        + transferPort);

                        // -------------------------------------------------
                        // Send routing request to remote server
                        // -------------------------------------------------

                        boolean routed = ChatServer.sendFileRouteRequest(
                                        username,
                                        recipient,
                                        file.getName(),
                                        file.length(),
                                        transferPort);

                        if (routed) {

                                sendMessage(
                                                "SYSTEM: Remote file transfer request sent.");

                        } else {

                                sendMessage(
                                                "SYSTEM: Unable to contact remote server.");
                        }

                        return;
                }

                // -----------------------------------------------------
                // Recipient is offline
                // -----------------------------------------------------

                System.out.println(
                                "[OFFLINE-FILE] Recipient offline: "
                                                + recipient);

                boolean saved = offlineFileService.savePendingFile(
                                username,
                                recipient,
                                file);

                if (saved) {

                        sendMessage(
                                        "SYSTEM: "
                                                        + recipient
                                                        + " is offline.");

                        sendMessage(
                                        "SYSTEM: File saved and will be delivered when "
                                                        + recipient
                                                        + " comes online.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to save file for offline delivery.");
                }
        }

        private void handleGroupFile(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage("SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /groupfile groupname filepath");

                        return;
                }

                String groupName = parts[1].trim();

                String filePath = parts[2].trim();

                File file = new File(filePath);

                // =========================================================
                // FILE VALIDATION
                // =========================================================

                if (!file.exists()) {

                        sendMessage(
                                        "SYSTEM: File does not exist.");

                        return;
                }

                if (!file.isFile()) {

                        sendMessage(
                                        "SYSTEM: Selected path is not a file.");

                        return;
                }

                if (!FileValidationService.isAllowed(file)) {

                        sendMessage(
                                        "SYSTEM: Unsupported file type.");

                        sendMessage(
                                        "SYSTEM: Supported file categories: "
                                                        + "Images, Documents, Office, Audio, Video, Archives and Code.");

                        sendMessage(
                                        "SYSTEM: Detected file type: "
                                                        + FileValidationService.getFileType(file));

                        return;
                }

                // =========================================================
                // GROUP VALIDATION
                // =========================================================

                if (!ChatServer.groupExists(groupName)) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' does not exist.");

                        return;
                }

                if (!ChatServer.isAnyGroupMember(
                                groupName,
                                this)) {

                        sendMessage(
                                        "SYSTEM: You are not a member of group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                // =========================================================
                // START GROUP FILE TRANSFER
                // =========================================================

                System.out.println();

                System.out.println(
                                "[GROUP-FILE] "
                                                + username
                                                + " sending file to group "
                                                + groupName);

                System.out.println(
                                "[GROUP-FILE] File: "
                                                + file.getName());

                System.out.println(
                                "[GROUP-FILE] Size: "
                                                + file.length()
                                                + " bytes");

                boolean started = ChatServer.sendGroupFile(
                                username,
                                groupName,
                                file);

                if (started) {

                        sendMessage(
                                        "SYSTEM: Group file transfer started.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to start group file transfer.");
                }
        }

        // =========================================================
        // GROUP MESSAGE
        // =========================================================

        private void handleGroupMessage(
                        String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage(
                                        "SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /groupmsg group message");

                        return;
                }

                String groupName = parts[1];

                String groupMessage = parts[2];

                boolean sent = ChatServer.sendGroupMessage(
                                groupName,
                                this,
                                groupMessage);

                if (sent) {

                        Message chatMessage = new Message(
                                        username,
                                        null,
                                        groupName,
                                        groupMessage,
                                        "GROUP",
                                        LocalDateTime.now());

                        chatHistoryService.saveMessage(
                                        chatMessage);

                        sendMessage(
                                        "GROUP ["
                                                        + groupName
                                                        + "] You: "
                                                        + groupMessage);

                } else {

                        sendMessage(
                                        "SYSTEM: You cannot send messages to group '"
                                                        + groupName
                                                        + "'.");
                }
        }

        // =========================================================
        // GROUP MEMBERS
        // =========================================================

        private void handleGroupMembers(
                        String message) {

                String groupName = message.substring(
                                "/members ".length())
                                .trim();

                ChatServer.sendGroupMembers(
                                groupName,
                                this);
        }

        // =========================================================
        // ADD MEMBER
        // =========================================================

        private void handleAddMember(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage("SYSTEM: Invalid format.");
                        sendMessage(
                                        "SYSTEM: Use /addmember groupname username");

                        return;
                }

                String groupName = parts[1].trim();
                String targetUser = parts[2].trim();

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                if (targetUser.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username cannot be empty.");

                        return;
                }

                if (!userService.userExists(targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '" + targetUser
                                                        + "' does not exist.");

                        return;
                }

                if (groupService.isMember(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '" + targetUser
                                                        + "' is already a member.");

                        return;
                }

                boolean added = groupService.addMember(
                                groupName,
                                targetUser);

                if (added) {

                        // ---------------------------------------------------------
                        // UPDATE LOCAL PERSISTENT MEMORY
                        // ---------------------------------------------------------

                        ChatServer.addSyncedPersistentGroupMember(
                                        groupName,
                                        targetUser);

                        // ---------------------------------------------------------
                        // SYNCHRONIZE MEMBER WITH OTHER SERVER
                        // ---------------------------------------------------------

                        ChatServer.getServerSynchronizer().sendGroupMemberAdd(
                                        groupName,
                                        targetUser);

                        sendMessage(
                                        "SYSTEM: User '" + targetUser
                                                        + "' added to group '"
                                                        + groupName + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to add user.");
                }
        }

        // =========================================================
        // REMOVE MEMBER
        // =========================================================

        private void handleRemoveMember(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage("SYSTEM: Invalid format.");
                        sendMessage(
                                        "SYSTEM: Use /removemember groupname username");

                        return;
                }

                String groupName = parts[1].trim();
                String targetUser = parts[2].trim();

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                if (targetUser.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username cannot be empty.");

                        return;
                }

                if (!groupService.isMember(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' is not a member of group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                if (groupService.isOwner(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: Cannot remove owner. "
                                                        + "Demote the owner first.");

                        return;
                }

                boolean removed = groupService.removeMember(
                                groupName,
                                targetUser);

                if (removed) {

                        ChatServer.removePersistentGroupMember(
                                        groupName,
                                        targetUser);

                        ChatServer.getServerSynchronizer()
                                        .sendGroupMemberRemove(
                                                        groupName,
                                                        targetUser);

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' removed from group '"
                                                        + groupName
                                                        + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to remove user.");
                }
        }

        // =========================================================
        // PROMOTE MEMBER TO OWNER
        // =========================================================

        private void handlePromoteMember(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage("SYSTEM: Invalid format.");
                        sendMessage(
                                        "SYSTEM: Use /promote groupname username");

                        return;
                }

                String groupName = parts[1].trim();
                String targetUser = parts[2].trim();

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                if (targetUser.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username cannot be empty.");

                        return;
                }

                if (!groupService.isMember(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' is not a member of group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                if (groupService.isOwner(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '" + targetUser
                                                        + "' is already an owner.");

                        return;
                }

                boolean promoted = groupService.promoteToOwner(
                                groupName,
                                targetUser);

                if (promoted) {

                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                        if (synchronizer != null) {

                                synchronizer.sendGroupOwnerAdd(
                                                groupName,
                                                targetUser);
                        }

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' is now an owner of group '"
                                                        + groupName
                                                        + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to promote user.");
                }
        }

        // =========================================================
        // DEMOTE OWNER
        // =========================================================

        private void handleDemoteMember(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage("SYSTEM: Invalid format.");
                        sendMessage(
                                        "SYSTEM: Use /demote groupname username");

                        return;
                }

                String groupName = parts[1].trim();
                String targetUser = parts[2].trim();

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                if (targetUser.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username cannot be empty.");

                        return;
                }

                if (!groupService.isOwner(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' is not a member of group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                if (!groupService.isOwner(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' is not an owner.");

                        return;
                }

                boolean demoted = groupService.demoteOwner(
                                groupName,
                                targetUser);

                if (demoted) {

                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                        if (synchronizer != null) {

                                synchronizer.sendGroupOwnerRemove(
                                                groupName,
                                                targetUser);
                        }

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' is no longer an owner of group '"
                                                        + groupName
                                                        + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to demote owner.");

                        sendMessage(
                                        "SYSTEM: At least one owner must remain.");
                }
        }

        // =========================================================
        // REQUEST TO JOIN GROUP
        // =========================================================

        private void handleRequestJoin(String message) {

                String[] parts = message.split(" ", 2);

                if (parts.length < 2) {

                        sendMessage("SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /requestjoin groupname");

                        return;
                }

                String groupName = parts[1].trim();

                if (groupName.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                // -------------------------------------------------
                // CHECK GROUP EXISTS
                // -------------------------------------------------

                if (!groupService.groupExists(groupName)) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' does not exist.");

                        return;
                }

                // -------------------------------------------------
                // CHECK ACCESS MODE
                // -------------------------------------------------

                String accessMode = groupService.getAccessMode(groupName);

                if (accessMode == null) {

                        accessMode = "REQUEST";
                }

                accessMode = accessMode.trim().toUpperCase();

                // -------------------------------------------------
                // REQUEST MODE
                // -------------------------------------------------

                if (accessMode.equals("REQUEST")) {

                        // ---------------------------------------------
                        // CHECK ALREADY MEMBER
                        // ---------------------------------------------

                        if (groupService.isMember(
                                        groupName,
                                        username)) {

                                sendMessage(
                                                "SYSTEM: You are already a member of group '"
                                                                + groupName
                                                                + "'.");

                                return;
                        }

                        // ---------------------------------------------
                        // CHECK EXISTING REQUEST
                        // ---------------------------------------------

                        if (groupService.hasJoinRequest(
                                        groupName,
                                        username)) {

                                sendMessage(
                                                "SYSTEM: You already have a pending join request for group '"
                                                                + groupName
                                                                + "'.");

                                return;
                        }

                        // ---------------------------------------------
                        // CREATE REQUEST
                        // ---------------------------------------------

                        boolean requested = groupService.addJoinRequest(
                                        groupName,
                                        username);

                        if (requested) {

                                sendMessage(
                                                "SYSTEM: Join request sent for group '"
                                                                + groupName
                                                                + "'.");

                        } else {

                                sendMessage(
                                                "SYSTEM: Unable to send join request.");
                        }

                        return;
                }

                // -------------------------------------------------
                // CODE MODE
                // -------------------------------------------------

                if (accessMode.equals("CODE")) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' requires a join code.");

                        sendMessage(
                                        "SYSTEM: Use /join "
                                                        + groupName
                                                        + " <joinCode>");

                        return;
                }

                // -------------------------------------------------
                // OPEN MODE
                // -------------------------------------------------

                if (accessMode.equals("OPEN")) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' is open for direct joining.");

                        sendMessage(
                                        "SYSTEM: No join request is required.");

                        return;
                }

                // -------------------------------------------------
                // UNKNOWN MODE
                // -------------------------------------------------

                sendMessage(
                                "SYSTEM: Invalid access mode configured for group '"
                                                + groupName
                                                + "'.");

        }

        // =========================================================
        // VIEW PENDING JOIN REQUESTS
        // =========================================================

        private void handleRequests(String message) {

                String[] parts = message.split(" ", 2);

                if (parts.length < 2) {

                        sendMessage("SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /requests groupname");

                        return;
                }

                String groupName = parts[1].trim();

                if (groupName.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                List<String> pendingRequests = groupService.getPendingRequests(
                                groupName);

                if (pendingRequests.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: No pending join requests for group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                StringBuilder response = new StringBuilder();

                response.append(
                                "PENDING REQUESTS [")
                                .append(groupName)
                                .append("]: ");

                for (String username : pendingRequests) {

                        response.append(username)
                                        .append(" ");
                }

                sendMessage(
                                response.toString().trim());
        }

        // =========================================================
        // APPROVE JOIN REQUEST
        // =========================================================

        private void handleApprove(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage("SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /approve groupname username");

                        return;
                }

                String groupName = parts[1].trim();

                String targetUser = parts[2].trim();

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                if (targetUser.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username cannot be empty.");

                        return;
                }

                if (!userService.userExists(targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' does not exist.");

                        return;
                }

                if (groupService.isMember(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' is already a member.");

                        return;
                }

                if (!groupService.hasJoinRequest(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: No pending join request found for user '"
                                                        + targetUser
                                                        + "'.");

                        return;
                }

                boolean approved = groupService.approveJoinRequest(
                                groupName,
                                targetUser);

                if (approved) {

                        /*
                         * Update local in-memory membership.
                         */
                        ChatServer.addSyncedPersistentGroupMember(
                                        groupName,
                                        targetUser);

                        /*
                         * Synchronize membership with
                         * the other server.
                         */
                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                        if (synchronizer != null) {

                                synchronizer.sendGroupMemberAdd(
                                                groupName,
                                                targetUser);
                        }

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + targetUser
                                                        + "' approved and added to group '"
                                                        + groupName
                                                        + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to approve join request.");
                }
        }

        // =========================================================
        // REJECT JOIN REQUEST
        // =========================================================

        private void handleReject(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage("SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /reject groupname username");

                        return;
                }

                String groupName = parts[1].trim();

                String targetUser = parts[2].trim();

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                if (targetUser.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username cannot be empty.");

                        return;
                }

                if (!groupService.hasJoinRequest(
                                groupName,
                                targetUser)) {

                        sendMessage(
                                        "SYSTEM: No pending join request found for user '"
                                                        + targetUser
                                                        + "'.");
                        return;
                }

                boolean rejected = groupService.rejectJoinRequest(
                                groupName,
                                targetUser);

                if (rejected) {

                        sendMessage(
                                        "SYSTEM: Join request from '"
                                                        + targetUser
                                                        + "' rejected for group '"
                                                        + groupName
                                                        + "'.");

                } else {

                        sendMessage(
                                        "SYSTEM: Unable to reject join request.");
                }
        }

        // =========================================================
        // JOIN GROUP USING JOIN CODE
        // =========================================================

        // =========================================================
        // JOIN GROUP
        // =========================================================

        private void handleJoin(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 2) {

                        sendMessage(
                                        "SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /join groupname [joinCode]");

                        return;
                }

                String groupName = parts[1].trim();

                String joinCode = null;

                if (parts.length == 3) {
                        joinCode = parts[2].trim();
                }

                if (groupName.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                // -------------------------------------------------
                // CHECK GROUP EXISTS
                // -------------------------------------------------

                if (!groupService.groupExists(groupName)) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' does not exist.");

                        return;
                }

                // -------------------------------------------------
                // CHECK ACCESS MODE
                // -------------------------------------------------

                String accessMode = groupService.getAccessMode(groupName);

                if (accessMode == null) {
                        accessMode = "REQUEST";
                }

                accessMode = accessMode.trim().toUpperCase();

                // -------------------------------------------------
                // ALREADY MEMBER
                // -------------------------------------------------

                if (groupService.isMember(
                                groupName,
                                username)) {

                        sendMessage(
                                        "SYSTEM: You are already a member of group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                // =================================================
                // OPEN MODE
                // =================================================

                if (accessMode.equals("OPEN")) {

                        // A join code must NOT be accepted in OPEN mode.
                        if (joinCode != null && !joinCode.isEmpty()) {

                                sendMessage(
                                                "SYSTEM: Group '"
                                                                + groupName
                                                                + "' is OPEN.");

                                sendMessage(
                                                "SYSTEM: Join code is not required.");

                                sendMessage(
                                                "SYSTEM: Use /join "
                                                                + groupName);

                                return;
                        }

                        // ---------------------------------------------
                        // DIRECT JOIN
                        // ---------------------------------------------

                        boolean joined = groupService.addMember(
                                        groupName,
                                        username);

                        if (!joined) {

                                sendMessage(
                                                "SYSTEM: Unable to join group '"
                                                                + groupName
                                                                + "'.");

                                return;
                        }

                        // ---------------------------------------------
                        // UPDATE LOCAL PERSISTENT MEMBERSHIP
                        // ---------------------------------------------

                        ChatServer.addSyncedPersistentGroupMember(
                                        groupName,
                                        username);

                        // ---------------------------------------------
                        // SYNCHRONIZE OTHER SERVER
                        // ---------------------------------------------

                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                        if (synchronizer != null) {

                                synchronizer.sendGroupMemberAdd(
                                                groupName,
                                                username);
                        }

                        sendMessage(
                                        "SYSTEM: Successfully joined group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                // =================================================
                // CODE MODE
                // =================================================

                if (accessMode.equals("CODE")) {

                        if (joinCode == null
                                        || joinCode.isEmpty()) {

                                sendMessage(
                                                "SYSTEM: Group '"
                                                                + groupName
                                                                + "' requires a join code.");

                                sendMessage(
                                                "SYSTEM: Use /join "
                                                                + groupName
                                                                + " <joinCode>");

                                return;
                        }

                        boolean joined = groupService.joinGroupWithCode(
                                        groupName,
                                        username,
                                        joinCode);

                        if (!joined) {

                                sendMessage(
                                                "SYSTEM: Invalid join code or unable to join group.");

                                return;
                        }

                        // ---------------------------------------------
                        // UPDATE LOCAL PERSISTENT MEMBERSHIP
                        // ---------------------------------------------

                        ChatServer.addSyncedPersistentGroupMember(
                                        groupName,
                                        username);

                        // ---------------------------------------------
                        // SYNCHRONIZE OTHER SERVER
                        // ---------------------------------------------

                        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

                        if (synchronizer != null) {

                                synchronizer.sendGroupMemberAdd(
                                                groupName,
                                                username);
                        }

                        sendMessage(
                                        "SYSTEM: Successfully joined group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                // =================================================
                // REQUEST MODE
                // =================================================

                if (accessMode.equals("REQUEST")) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' uses REQUEST access mode.");

                        sendMessage(
                                        "SYSTEM: Use /requestjoin "
                                                        + groupName);

                        return;
                }

                // =================================================
                // INVALID MODE
                // =================================================

                sendMessage(
                                "SYSTEM: Invalid access mode configured for group '"
                                                + groupName
                                                + "'.");
        }
        // =========================================================
        // SET GROUP ACCESS MODE
        // =========================================================

        private void handleSetAccess(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage(
                                        "SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /setaccess groupname mode");

                        sendMessage(
                                        "SYSTEM: Modes: REQUEST, CODE, OPEN");

                        return;
                }

                String groupName = parts[1].trim();

                String accessMode = parts[2].trim().toUpperCase();

                if (groupName.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                if (accessMode.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Access mode cannot be empty.");

                        return;
                }

                // -------------------------------------------------
                // OWNER CHECK
                // -------------------------------------------------

                if (!requireGroupOwner(groupName)) {
                        return;
                }

                // -------------------------------------------------
                // VALIDATE MODE
                // -------------------------------------------------

                if (!accessMode.equals("REQUEST")
                                && !accessMode.equals("CODE")
                                && !accessMode.equals("OPEN")) {

                        sendMessage(
                                        "SYSTEM: Invalid access mode.");

                        sendMessage(
                                        "SYSTEM: Available modes: REQUEST, CODE, OPEN");

                        return;
                }

                // -------------------------------------------------
                // UPDATE DATABASE
                // -------------------------------------------------

                boolean updated = groupService.setAccessMode(
                                groupName,
                                accessMode);

                if (!updated) {

                        sendMessage(
                                        "SYSTEM: Unable to change access mode.");

                        return;
                }

                sendMessage(
                                "SYSTEM: Access mode for group '"
                                                + groupName
                                                + "' changed to "
                                                + accessMode
                                                + ".");
        }

        // =========================================================
        // PRIVATE MESSAGE
        // =========================================================

        private void handlePrivateMessage(String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage(
                                        "SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /msg username message");

                        return;
                }

                String recipient = parts[1].trim();

                String privateMessage = parts[2].trim();

                if (recipient.isEmpty() || privateMessage.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username and message cannot be empty.");

                        return;
                }

                // =====================================================
                // CHECK WHETHER RECIPIENT IS ONLINE
                // =====================================================

                boolean sent = ChatServer.sendPrivateMessage(
                                username,
                                recipient,
                                privateMessage);

                // =====================================================
                // CREATE CHAT HISTORY RECORD
                // =====================================================

                Message chatMessage = new Message(
                                username,
                                recipient,
                                null,
                                privateMessage,
                                "PRIVATE",
                                LocalDateTime.now());

                // =====================================================
                // RECIPIENT ONLINE
                // =====================================================

                if (sent) {

                        chatHistoryService.saveMessage(
                                        chatMessage);

                        sendMessage(
                                        "PRIVATE to "
                                                        + recipient
                                                        + ": "
                                                        + privateMessage);

                        System.out.println(
                                        "[PRIVATE] Message delivered: "
                                                        + username
                                                        + " -> "
                                                        + recipient);

                        return;
                }

                // =====================================================
                // RECIPIENT OFFLINE
                // =====================================================

                System.out.println(
                                "[OFFLINE-MESSAGE] Recipient offline: "
                                                + recipient);

                // -----------------------------------------------------
                // SAVE PERMANENT CHAT HISTORY
                // -----------------------------------------------------

                chatHistoryService.saveMessage(
                                chatMessage);

                // -----------------------------------------------------
                // SAVE TO OFFLINE MESSAGE QUEUE
                // -----------------------------------------------------

                boolean saved = offlineMessageService.savePendingMessage(
                                username,
                                recipient,
                                privateMessage);

                if (saved) {

                        sendMessage(
                                        "SYSTEM: "
                                                        + recipient
                                                        + " is offline.");

                        sendMessage(
                                        "SYSTEM: Message saved and will be delivered "
                                                        + "when "
                                                        + recipient
                                                        + " comes online.");

                } else {

                        sendMessage(
                                        "SYSTEM: "
                                                        + recipient
                                                        + " is offline.");

                        sendMessage(
                                        "SYSTEM: Failed to save message for offline delivery.");
                }
        }

        // =========================================================
        // SEND MESSAGE TO CLIENT
        // =========================================================

        public void sendMessage(
                        String message) {

                if (output != null) {

                        output.println(message);
                }
        }

        // =========================================================
        // DELIVER OFFLINE MESSAGES
        // =========================================================

        private void deliverOfflineMessages() {

                if (username == null || username.trim().isEmpty()) {

                        return;
                }

                try {

                        List<org.bson.Document> pendingMessages = offlineMessageService.getPendingMessages(username);

                        if (pendingMessages.isEmpty()) {

                                return;
                        }

                        System.out.println(
                                        "[OFFLINE-MESSAGE] Delivering "
                                                        + pendingMessages.size()
                                                        + " pending message(s) to "
                                                        + username);

                        sendMessage(
                                        "OFFLINE_MESSAGES_START");

                        sendMessage(
                                        "SYSTEM: You have "
                                                        + pendingMessages.size()
                                                        + " pending message(s).");

                        for (org.bson.Document document : pendingMessages) {

                                String sender = document.getString("sender");

                                String message = document.getString("message");

                                String timestamp = document.getString("timestamp");

                                sendMessage(
                                                "OFFLINE_MESSAGE:"
                                                                + sender
                                                                + ":"
                                                                + timestamp
                                                                + ":"
                                                                + message);

                                org.bson.types.ObjectId messageId = document.getObjectId("_id");

                                offlineMessageService.deletePendingMessage(
                                                messageId);
                        }

                        sendMessage(
                                        "OFFLINE_MESSAGES_END");

                        System.out.println(
                                        "[OFFLINE-MESSAGE] All pending messages "
                                                        + "delivered to "
                                                        + username);

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-MESSAGE] Delivery error: "
                                                        + e.getMessage());
                }

        }

        // =========================================================
        // DELIVER OFFLINE GROUP MESSAGES
        // =========================================================

        private void deliverOfflineGroupMessages() {

                try {

                        List<Document> pendingMessages = offlineGroupMessageService
                                        .getPendingGroupMessages(username);

                        if (pendingMessages.isEmpty()) {
                                return;
                        }

                        System.out.println();

                        System.out.println(
                                        "[OFFLINE-GROUP-MESSAGE] "
                                                        + pendingMessages.size()
                                                        + " pending group message(s) found for "
                                                        + username);

                        sendMessage(
                                        "OFFLINE_GROUP_MESSAGES_START");

                        sendMessage(
                                        "SYSTEM: You have "
                                                        + pendingMessages.size()
                                                        + " pending group message(s).");

                        for (Document document : pendingMessages) {

                                String sender = document.getString("sender");

                                String groupName = document.getString("groupName");

                                String message = document.getString("message");

                                String timestamp = document.getString("timestamp");

                                // ---------------------------------------------
                                // SEND TO CLIENT
                                // ---------------------------------------------

                                sendMessage(
                                                "OFFLINE_GROUP_MESSAGE:"
                                                                + groupName
                                                                + ":"
                                                                + sender
                                                                + ":"
                                                                + timestamp
                                                                + ":"
                                                                + message);

                                System.out.println(
                                                "[OFFLINE-GROUP-MESSAGE] Delivered: "
                                                                + sender
                                                                + " -> "
                                                                + username
                                                                + " ["
                                                                + groupName
                                                                + "]");

                                // ---------------------------------------------
                                // REMOVE FROM QUEUE
                                // ---------------------------------------------

                                org.bson.types.ObjectId id = document.getObjectId("_id");

                                offlineGroupMessageService
                                                .deletePendingGroupMessage(id);
                        }

                        sendMessage(
                                        "OFFLINE_GROUP_MESSAGES_END");

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-GROUP-MESSAGE] "
                                                        + "Delivery failed: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // DELIVER OFFLINE FILES
        // =========================================================

        private void deliverOfflineFiles() {

                if (username == null || username.trim().isEmpty()) {
                        return;
                }

                try {

                        List<Document> pendingFiles = offlineFileService.getPendingFiles(username);

                        if (pendingFiles.isEmpty()) {
                                return;
                        }

                        System.out.println(
                                        "[OFFLINE-FILE] Delivering "
                                                        + pendingFiles.size()
                                                        + " pending file(s) to "
                                                        + username);

                        sendMessage("OFFLINE_FILES_START");

                        sendMessage(
                                        "SYSTEM: You have "
                                                        + pendingFiles.size()
                                                        + " pending file(s).");

                        for (Document document : pendingFiles) {

                                String sender = document.getString("sender");

                                String fileName = document.getString("fileName");

                                long fileSize = document.getLong("fileSize");

                                org.bson.types.ObjectId gridFsFileId = document.getObjectId("gridFsFileId");

                                // -------------------------------------------------
                                // Create temporary delivery server
                                // -------------------------------------------------

                                ServerSocket deliveryServer = new ServerSocket(0);

                                int deliveryPort = deliveryServer.getLocalPort();

                                System.out.println(
                                                "[OFFLINE-FILE] Delivery port ready: "
                                                                + deliveryPort);

                                // -------------------------------------------------
                                // Tell client where to download the file
                                // -------------------------------------------------

                                sendMessage(
                                                "OFFLINE_FILE_READY:"
                                                                + sender
                                                                + ":"
                                                                + username
                                                                + ":"
                                                                + fileName
                                                                + ":"
                                                                + fileSize
                                                                + ":"
                                                                + deliveryPort);

                                // -------------------------------------------------
                                // Wait for client connection
                                // -------------------------------------------------

                                try (
                                                Socket fileSocket = deliveryServer.accept();

                                                DataOutputStream output = new DataOutputStream(
                                                                fileSocket.getOutputStream())) {

                                        System.out.println(
                                                        "[OFFLINE-FILE] Client connected for: "
                                                                        + fileName);

                                        // -------------------------------------------------
                                        // Send file metadata
                                        // -------------------------------------------------

                                        output.writeUTF(fileName);
                                        output.writeLong(fileSize);

                                        output.flush();

                                        // -------------------------------------------------
                                        // Download from GridFS directly into socket
                                        // -------------------------------------------------

                                        offlineFileService.downloadFileToStream(
                                                        gridFsFileId,
                                                        output);

                                        output.flush();

                                        System.out.println(
                                                        "[OFFLINE-FILE] File delivered successfully: "
                                                                        + fileName);

                                        // -------------------------------------------------
                                        // Remove queue entry + GridFS file
                                        // -------------------------------------------------

                                        offlineFileService.deletePendingFile(
                                                        document.getObjectId("_id"));
                                }

                                deliveryServer.close();
                        }

                        sendMessage("OFFLINE_FILES_END");

                        System.out.println(
                                        "[OFFLINE-FILE] All pending files delivered to "
                                                        + username);

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-FILE] Delivery error: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // DELIVER OFFLINE GROUP FILES
        // =========================================================

        private void deliverOfflineGroupFiles() {

                try {

                        List<Document> pendingFiles = ChatServer.getOfflineGroupFileService()
                                        .getPendingGroupFiles(username);

                        if (pendingFiles.isEmpty()) {
                                return;
                        }

                        System.out.println();

                        System.out.println(
                                        "[OFFLINE-GROUP-FILE] "
                                                        + pendingFiles.size()
                                                        + " pending group file(s) found for "
                                                        + username);

                        sendMessage(
                                        "OFFLINE_GROUP_FILES_START");

                        sendMessage(
                                        "SYSTEM: You have "
                                                        + pendingFiles.size()
                                                        + " pending group file(s).");

                        for (Document document : pendingFiles) {

                                String sender = document.getString("sender");

                                String groupName = document.getString("groupName");

                                String fileName = document.getString("fileName");

                                long fileSize = document.getLong("fileSize");

                                ObjectId gridFsFileId = document.getObjectId(
                                                "gridFsFileId");

                                // -------------------------------------------------
                                // Create dynamic transfer port
                                // -------------------------------------------------

                                try (ServerSocket fileServerSocket = new ServerSocket(0)) {

                                        int deliveryPort = fileServerSocket.getLocalPort();

                                        sendMessage(
                                                        "OFFLINE_GROUP_FILE_READY:"
                                                                        + groupName
                                                                        + ":"
                                                                        + sender
                                                                        + ":"
                                                                        + fileName
                                                                        + ":"
                                                                        + fileSize
                                                                        + ":"
                                                                        + deliveryPort);

                                        System.out.println(
                                                        "[OFFLINE-GROUP-FILE] "
                                                                        + "Waiting for client connection on port "
                                                                        + deliveryPort);

                                        // -------------------------------------------------
                                        // Wait for client to connect
                                        // -------------------------------------------------

                                        try (Socket fileSocket = fileServerSocket.accept()) {

                                                System.out.println(
                                                                "[OFFLINE-GROUP-FILE] "
                                                                                + "Client connected for "
                                                                                + fileName);

                                                DataOutputStream outputStream = new DataOutputStream(
                                                                fileSocket.getOutputStream());

                                                // -------------------------------------------------
                                                // Send file metadata
                                                // -------------------------------------------------

                                                outputStream.writeUTF(fileName);

                                                outputStream.writeLong(fileSize);

                                                outputStream.flush();

                                                // -------------------------------------------------
                                                // Stream file from GridFS
                                                // -------------------------------------------------

                                                ChatServer.getOfflineGroupFileService()
                                                                .downloadFileToStream(
                                                                                gridFsFileId,
                                                                                outputStream);

                                                outputStream.flush();

                                                System.out.println(
                                                                "[OFFLINE-GROUP-FILE] "
                                                                                + "Delivered: "
                                                                                + sender
                                                                                + " -> "
                                                                                + username
                                                                                + " ["
                                                                                + groupName
                                                                                + "] "
                                                                                + fileName);
                                        }

                                        // -------------------------------------------------
                                        // Delete only after successful transfer
                                        // -------------------------------------------------

                                        ChatServer.getOfflineGroupFileService()
                                                        .deletePendingGroupFile(
                                                                        document.getObjectId("_id"));

                                } catch (Exception e) {

                                        System.out.println(
                                                        "[OFFLINE-GROUP-FILE] "
                                                                        + "Unable to deliver "
                                                                        + fileName
                                                                        + ": "
                                                                        + e.getMessage());
                                }
                        }

                        sendMessage(
                                        "OFFLINE_GROUP_FILES_END");

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-GROUP-FILE] Delivery failed: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // PRIVATE CHAT HISTORY
        // =========================================================

        private void handlePrivateChatHistory(
                        String message) {

                String otherUser = message.substring(
                                "/history ".length())
                                .trim();

                if (otherUser.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Username cannot be empty.");

                        return;
                }

                List<Message> history = chatHistoryService
                                .getPrivateChatHistory(
                                                username,
                                                otherUser);

                if (history.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: No private chat history found with "
                                                        + otherUser);

                        return;
                }

                sendMessage(
                                "========== CHAT HISTORY WITH "
                                                + otherUser
                                                + " ==========");

                for (Message msg : history) {

                        String sender = msg.getSender();

                        String text = msg.getMessage();

                        String time = msg.getTimestamp()
                                        .toLocalTime()
                                        .toString();

                        sendMessage(
                                        "["
                                                        + time
                                                        + "] "
                                                        + sender
                                                        + ": "
                                                        + text);
                }

                sendMessage(
                                "==========================================");
        }

        // =========================================================
        // SUPPORTED FILE TYPES
        // =========================================================

        private boolean isSupportedFile(
                        File file) {

                String fileName = file.getName()
                                .toLowerCase();

                return fileName.endsWith(".jpg")
                                || fileName.endsWith(".jpeg")
                                || fileName.endsWith(".png")
                                || fileName.endsWith(".gif")
                                || fileName.endsWith(".webp")
                                || fileName.endsWith(".pdf")
                                || fileName.endsWith(".mp3")
                                || fileName.endsWith(".wav")
                                || fileName.endsWith(".m4a")
                                || fileName.endsWith(".aac");
        }

        // =========================================================
        // GROUP CHAT HISTORY
        // =========================================================

        private void handleGroupChatHistory(
                        String message) {

                String groupName = message.substring(
                                "/grouphistory ".length())
                                .trim();

                if (groupName.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: Group name cannot be empty.");

                        return;
                }

                if (!ChatServer.groupExists(
                                groupName)) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' does not exist.");

                        return;
                }

                if (!ChatServer.isGroupMember(
                                groupName,
                                this)) {

                        sendMessage(
                                        "ACCESS_DENIED: You are not a member "
                                                        + "of group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                List<Message> history = chatHistoryService
                                .getGroupChatHistory(
                                                groupName);

                if (history.isEmpty()) {

                        sendMessage(
                                        "SYSTEM: No chat history found for group '"
                                                        + groupName
                                                        + "'.");

                        return;
                }

                sendMessage(
                                "========== GROUP HISTORY: "
                                                + groupName
                                                + " ==========");

                for (Message msg : history) {

                        String sender = msg.getSender();

                        String text = msg.getMessage();

                        String time = msg.getTimestamp()
                                        .toLocalTime()
                                        .toString();

                        sendMessage(
                                        "["
                                                        + time
                                                        + "] "
                                                        + sender
                                                        + ": "
                                                        + text);
                }

                sendMessage(
                                "==========================================");
        }

        // =========================================================
        // GROUP ROLE HELPERS
        // =========================================================

        private boolean isGroupMember(
                        String groupName) {

                return groupService.isMember(
                                groupName,
                                username);
        }

        // =========================================================
        // CHECK GROUP OWNER
        // =========================================================

        private boolean isGroupOwner(
                        String groupName) {

                return groupService.isOwner(
                                groupName,
                                username);
        }

        // =========================================================
        // OWNER AUTHORIZATION
        // =========================================================

        private boolean requireGroupOwner(
                        String groupName) {

                if (groupName == null
                                || groupName.trim().isEmpty()) {

                        sendMessage(
                                        "ACCESS_DENIED: Group name cannot be empty.");

                        return false;
                }

                groupName = groupName.trim();

                // ---------------------------------------------
                // Check group exists
                // ---------------------------------------------

                if (!groupService.groupExists(
                                groupName)) {

                        sendMessage(
                                        "SYSTEM: Group '"
                                                        + groupName
                                                        + "' does not exist.");

                        return false;
                }

                // ---------------------------------------------
                // Check membership
                // ---------------------------------------------

                if (!isGroupMember(
                                groupName)) {

                        sendMessage(
                                        "ACCESS_DENIED: You are not a member "
                                                        + "of group '"
                                                        + groupName
                                                        + "'.");

                        return false;
                }

                // ---------------------------------------------
                // Check owner role
                // ---------------------------------------------

                if (!isGroupOwner(
                                groupName)) {

                        sendMessage(
                                        "ACCESS_DENIED: Only group owners "
                                                        + "can perform this operation.");

                        return false;
                }

                return true;
        }

        // =========================================================
        // DISCONNECT
        // =========================================================

        private void disconnect() {

                if (username != null) {

                        ChatServer.removeOnlineUser(
                                        username);

                        ChatServer.broadcastMessage(
                                        "SYSTEM: "
                                                        + username
                                                        + " left the chat.",
                                        this);
                }

                closeConnection();
        }

        // =========================================================
        // CLOSE CONNECTION
        // =========================================================

        private void closeConnection() {

                try {

                        if (clientSocket != null
                                        && !clientSocket.isClosed()) {

                                clientSocket.close();
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "Error closing connection.");
                }
        }

        // =========================================================
        // GET USERNAME
        // =========================================================

        public String getUsername() {

                return username;
        }
        // =========================================================
        // REMOTE GROUP MEMBERSHIP HELPERS
        // =========================================================

        public void addRemoteGroup(
                        String groupName) {

                if (groupName != null
                                && !groupName.trim().isEmpty()) {

                        remoteJoinedGroups.add(
                                        groupName.trim());
                }
        }

        public void removeRemoteGroup(
                        String groupName) {

                if (groupName != null) {

                        remoteJoinedGroups.remove(
                                        groupName.trim());
                }
        }

        public boolean isRemoteGroupMember(
                        String groupName) {

                if (groupName == null) {
                        return false;
                }

                return remoteJoinedGroups.contains(
                                groupName.trim());
        }

        public Set<String> getRemoteJoinedGroups() {

                return Set.copyOf(
                                remoteJoinedGroups);
        }
}