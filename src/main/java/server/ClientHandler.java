package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import database.MongoDBConnection;
import model.User;
import service.UserService;


public class ClientHandler implements Runnable {

    private final Socket clientSocket;

    private BufferedReader input;
    private PrintWriter output;

    private String username;

    private final UserService userService;

    public ClientHandler(Socket clientSocket) {

        this.clientSocket = clientSocket;
        this.userService = new UserService();
    }

    @Override
    public void run() {

        try {

            input = new BufferedReader(
                    new InputStreamReader(
                            clientSocket.getInputStream()
                    )
            );

            output = new PrintWriter(
                    clientSocket.getOutputStream(),
                    true
            );

// ================================
// AUTHENTICATION
// ================================

                boolean authenticated = false;

                while (!authenticated) {

                sendMessage(
                        "AUTH_REQUEST"
                );

                String authChoice = input.readLine();

                if (authChoice == null) {

                        closeConnection();

                        return;
                }

                authChoice = authChoice.trim()
                        .toLowerCase();

                // ================================
                // REGISTER
                // ================================

                if (authChoice.equals("register")) {

                        sendMessage(
                                "USERNAME_REQUEST"
                        );

                        String registerUsername =
                                input.readLine();

                        if (registerUsername == null ||
                                registerUsername.trim().isEmpty()) {

                        sendMessage(
                                "ERROR: Username cannot be empty."
                        );

                        continue;
                        }

                        registerUsername =
                                registerUsername.trim();

                        sendMessage(
                                "EMAIL_REQUEST"
                        );

                        String email =
                                input.readLine();

                        if (email == null ||
                                email.trim().isEmpty()) {

                        sendMessage(
                                "ERROR: Email cannot be empty."
                        );

                        continue;
                        }

                        email = email.trim();

                        sendMessage(
                                "PASSWORD_REQUEST"
                        );

                        String password =
                                input.readLine();

                        if (password == null ||
                                password.isEmpty()) {

                        sendMessage(
                                "ERROR: Password cannot be empty."
                        );

                        continue;
                        }

                        User newUser =
                                new User(
                                        registerUsername,
                                        password,
                                        email
                                );

                        boolean registered =
                                userService.registerUser(
                                        newUser
                                );

                        if (registered) {

                        sendMessage(
                                "REGISTER_SUCCESS: "
                                        + "Registration successful. "
                                        + "Please login."
                        );

                        } else {

                        sendMessage(
                                "REGISTER_FAILED: "
                                        + "Username already exists."
                        );
                        }
                }

                // ================================
                // LOGIN
                // ================================

                else if (authChoice.equals("login")) {

                        sendMessage(
                                "USERNAME_REQUEST"
                        );

                        String loginUsername =
                                input.readLine();

                        if (loginUsername == null ||
                                loginUsername.trim().isEmpty()) {

                        sendMessage(
                                "ERROR: Username cannot be empty."
                        );

                        continue;
                        }

                        loginUsername =
                                loginUsername.trim();

                        sendMessage(
                                "PASSWORD_REQUEST"
                        );

                        String password =
                                input.readLine();

                        if (password == null ||
                                password.isEmpty()) {

                        sendMessage(
                                "ERROR: Password cannot be empty."
                        );

                        continue;
                        }

                        // Check MongoDB
                        User loggedInUser =
                                userService.loginUser(
                                        loginUsername,
                                        password
                                );

                        if (loggedInUser == null) {

                        sendMessage(
                                "LOGIN_FAILED: "
                                        + "Invalid username or password."
                        );

                        continue;
                        }

                        // Check whether user is already online
                        if (!ChatServer.addOnlineUser(
                                loginUsername,
                                this)) {

                        sendMessage(
                                "ERROR: Username already online."
                        );

                        continue;
                        }

                        // Authentication successful
                        username = loginUsername;

                        authenticated = true;

                        sendMessage(
                                "LOGIN_SUCCESS: Welcome "
                                        + username
                                        + "!"
                        );

                        ChatServer.broadcastMessage(
                                "SYSTEM: "
                                        + username
                                        + " joined the chat.",
                                this
                        );

                        ChatServer.sendOnlineUsers(
                                this
                        );
                }

                else {

                        sendMessage(
                                "ERROR: Please choose "
                                        + "LOGIN or REGISTER."
                        );
                }
                }

                // ================================
                // START CHAT
                // ================================

                String message;

            // Message loop
            while ((message = input.readLine()) != null) {

                message = message.trim();

                if (message.isEmpty()) {
                    continue;
                }

                // ==============================
                // ONLINE USERS
                // ==============================

                if (message.equalsIgnoreCase(
                        "/users")) {

                    ChatServer.sendOnlineUsers(
                            this
                    );

                    continue;
                }

                // ==============================
                // GROUP LIST
                // ==============================

                if (message.equalsIgnoreCase(
                        "/groups")) {

                    ChatServer.sendGroupList(
                            this
                    );

                    continue;
                }

                // ==============================
                // CREATE GROUP
                // ==============================

                if (message.startsWith(
                        "/create ")) {

                    handleCreateGroup(
                            message
                    );

                    continue;
                }

                // ==============================
                // JOIN GROUP
                // ==============================

                if (message.startsWith(
                        "/join ")) {

                    handleJoinGroup(
                            message
                    );

                    continue;
                }

                // ==============================
                // LEAVE GROUP
                // ==============================

                if (message.startsWith(
                        "/leave ")) {

                    handleLeaveGroup(
                            message
                    );

                    continue;
                }

                // ==============================
                // GROUP MEMBERS
                // ==============================

                if (message.startsWith(
                        "/members ")) {

                    handleGroupMembers(
                            message
                    );

                    continue;
                }

                // ==============================
                // GROUP MESSAGE
                // ==============================

                if (message.startsWith(
                        "/groupmsg ")) {

                    handleGroupMessage(
                            message
                    );

                    continue;
                }

                // ==============================
                // PRIVATE MESSAGE
                // ==============================

                if (message.startsWith(
                        "/msg ")) {

                    handlePrivateMessage(
                            message
                    );

                    continue;
                }

                // ==============================
                // EXIT
                // ==============================

                if (message.equalsIgnoreCase(
                        "/exit")) {

                    break;
                }

                // ==============================
                // NORMAL BROADCAST
                // ==============================

                String formattedMessage =
                        username
                                + ": "
                                + message;

                System.out.println(
                        formattedMessage
                );

                ChatServer.broadcastMessage(
                        formattedMessage,
                        this
                );
            }

        } catch (IOException e) {

            System.out.println(
                    "Connection error for "
                            + username
                            + ": "
                            + e.getMessage()
            );

        } finally {

            disconnect();
        }
    }

    // =====================================================
    // CREATE GROUP
    // =====================================================

    private void handleCreateGroup(
            String message) {

        String groupName =
                message.substring(
                        "/create ".length()
                ).trim();

        if (groupName.isEmpty()) {

            sendMessage(
                    "SYSTEM: Group name cannot be empty."
            );

            return;
        }

        boolean created =
                ChatServer.createGroup(
                        groupName,
                        this
                );

        if (created) {

            sendMessage(
                    "SYSTEM: Group '"
                            + groupName
                            + "' created successfully."
            );

        } else {

            sendMessage(
                    "SYSTEM: Group '"
                            + groupName
                            + "' already exists."
            );
        }
    }

    // =====================================================
    // JOIN GROUP
    // =====================================================

    private void handleJoinGroup(
            String message) {

        String groupName =
                message.substring(
                        "/join ".length()
                ).trim();

        if (groupName.isEmpty()) {

            sendMessage(
                    "SYSTEM: Group name cannot be empty."
            );

            return;
        }

        boolean joined =
                ChatServer.joinGroup(
                        groupName,
                        this
                );

        if (joined) {

            sendMessage(
                    "SYSTEM: You joined group '"
                            + groupName
                            + "'."
            );

        } else {

            sendMessage(
                    "SYSTEM: Group '"
                            + groupName
                            + "' does not exist."
            );
        }
    }

    // =====================================================
    // LEAVE GROUP
    // =====================================================

    private void handleLeaveGroup(
            String message) {

        String groupName =
                message.substring(
                        "/leave ".length()
                ).trim();

        boolean left =
                ChatServer.leaveGroup(
                        groupName,
                        this
                );

        if (left) {

            sendMessage(
                    "SYSTEM: You left group '"
                            + groupName
                            + "'."
            );

        } else {

            sendMessage(
                    "SYSTEM: You are not a member of '"
                            + groupName
                            + "'."
            );
        }
    }

    // =====================================================
    // GROUP MESSAGE
    // =====================================================

    private void handleGroupMessage(
            String message) {

        /*
         * Expected:
         *
         * /groupmsg groupName message
         */

        String[] parts =
                message.split(
                        " ",
                        3
                );

        if (parts.length < 3) {

            sendMessage(
                    "SYSTEM: Invalid format."
            );

            sendMessage(
                    "SYSTEM: Use /groupmsg group message"
            );

            return;
        }

        String groupName = parts[1];

        String groupMessage = parts[2];

        boolean sent =
                ChatServer.sendGroupMessage(
                        groupName,
                        this,
                        groupMessage
                );

                if (sent) {

                sendMessage(
                        "GROUP ["
                                + groupName
                                + "] You: "
                                + groupMessage
                );

                } else {

                sendMessage(
                        "SYSTEM: You cannot send messages to group '"
                                + groupName
                                + "'."
                );
                }
    }

    // =====================================================
    // GROUP MEMBERS
    // =====================================================

    private void handleGroupMembers(
            String message) {

        String groupName =
                message.substring(
                        "/members ".length()
                ).trim();

        ChatServer.sendGroupMembers(
                groupName,
                this
        );
    }

    // =====================================================
    // PRIVATE MESSAGE
    // =====================================================

    private void handlePrivateMessage(
            String message) {

        String[] parts =
                message.split(
                        " ",
                        3
                );

        if (parts.length < 3) {

            sendMessage(
                    "SYSTEM: Invalid format."
            );

            sendMessage(
                    "SYSTEM: Use /msg username message"
            );

            return;
        }

        String recipient = parts[1];

        String privateMessage = parts[2];

        boolean sent =
                ChatServer.sendPrivateMessage(
                        username,
                        recipient,
                        privateMessage
                );

        if (sent) {

            sendMessage(
                    "PRIVATE to "
                            + recipient
                            + ": "
                            + privateMessage
            );

        } else {

            sendMessage(
                    "SYSTEM: User '"
                            + recipient
                            + "' is not online."
            );
        }
    }

    // =====================================================
    // SEND MESSAGE
    // =====================================================

    public void sendMessage(
            String message) {

        if (output != null) {

            output.println(message);
        }
    }

    // =====================================================
    // DISCONNECT
    // =====================================================

    private void disconnect() {

        if (username != null) {

            ChatServer.removeOnlineUser(
                    username
            );

            ChatServer.broadcastMessage(
                    "SYSTEM: "
                            + username
                            + " left the chat.",
                    this
            );
        }

        closeConnection();
    }

    private void closeConnection() {

        try {

            if (clientSocket != null &&
                    !clientSocket.isClosed()) {

                clientSocket.close();
            }

        } catch (IOException e) {

            System.out.println(
                    "Error closing connection."
            );
        }
    }

    public String getUsername() {

        return username;
    }
}