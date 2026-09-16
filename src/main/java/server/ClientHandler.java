package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;

import model.User;
import model.Message;

import service.UserService;
import service.ChatHistoryService;

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

        // =========================================================
        // CONSTRUCTOR
        // =========================================================

        public ClientHandler(Socket clientSocket) {

                this.clientSocket = clientSocket;

                this.userService = new UserService();

                this.chatHistoryService = new ChatHistoryService();
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

                                if (message.startsWith("/join ")) {

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

        private void handleSendFile(
                        String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage(
                                        "SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /sendfile username filepath");

                        return;
                }

                String recipient = parts[1];

                String filePath = parts[2];

                File file = new File(filePath);

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

                ClientHandler recipientHandler = ChatServer.getOnlineUser(
                                recipient);

                if (recipientHandler == null) {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + recipient
                                                        + "' is not online.");

                        return;
                }

                if (!isSupportedFile(file)) {

                        sendMessage(
                                        "SYSTEM: Unsupported file type.");

                        sendMessage(
                                        "SYSTEM: Supported files: "
                                                        + "Images, PDF and Audio.");

                        return;
                }

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
                                                recipient,
                                                file));

                fileTransferThread.start();

                sendMessage(
                                "SYSTEM: File transfer started.");
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
        // PRIVATE MESSAGE
        // =========================================================

        private void handlePrivateMessage(
                        String message) {

                String[] parts = message.split(" ", 3);

                if (parts.length < 3) {

                        sendMessage(
                                        "SYSTEM: Invalid format.");

                        sendMessage(
                                        "SYSTEM: Use /msg username message");

                        return;
                }

                String recipient = parts[1];

                String privateMessage = parts[2];

                boolean sent = ChatServer.sendPrivateMessage(
                                username,
                                recipient,
                                privateMessage);

                if (sent) {

                        /*
                         * Save the private message ONLY on the
                         * originating server.
                         *
                         * Both servers use the same MongoDB database,
                         * so the history is available regardless of
                         * which server the user later connects to.
                         */
                        Message chatMessage = new Message(
                                        username,
                                        recipient,
                                        null,
                                        privateMessage,
                                        "PRIVATE",
                                        LocalDateTime.now());

                        chatHistoryService.saveMessage(
                                        chatMessage);

                        /*
                         * Tell sender that the message was successfully
                         * delivered/routed.
                         */
                        sendMessage(
                                        "PRIVATE to "
                                                        + recipient
                                                        + ": "
                                                        + privateMessage);

                } else {

                        sendMessage(
                                        "SYSTEM: User '"
                                                        + recipient
                                                        + "' is not online.");
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