package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;

    private BufferedReader input;
    private PrintWriter output;

    private String username;

    public ClientHandler(Socket clientSocket) {

        this.clientSocket = clientSocket;
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

            // Ask for username
            sendMessage(
                    "USERNAME_REQUEST"
            );

            username = input.readLine();

            if (username == null ||
                    username.trim().isEmpty()) {

                sendMessage(
                        "ERROR: Username cannot be empty."
                );

                closeConnection();

                return;
            }

            username = username.trim();

            // Check username
            if (!ChatServer.addOnlineUser(
                    username,
                    this)) {

                sendMessage(
                        "ERROR: Username already online."
                );

                closeConnection();

                return;
            }

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

        } 
        else 
        {

            sendMessage(
                    "SYSTEM: You cannot send messages to '"
                            + groupName
                            + "'. "
                            + "You are not a member."
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