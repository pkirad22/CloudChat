package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    private static final int PORT = 5000;

    /*
     * username -> ClientHandler
     */
    private static final Map<String, ClientHandler> onlineUsers =
            new ConcurrentHashMap<>();

    /*
     * groupName -> group members
     */
    private static final Map<String, Set<ClientHandler>> groups =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {

        System.out.println("=================================");
        System.out.println("       CloudChat Server");
        System.out.println("=================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Server started successfully.");
            System.out.println("Listening on port: " + PORT);
            System.out.println("Waiting for clients...");
            System.out.println();

            while (true) {

                Socket clientSocket =
                        serverSocket.accept();

                System.out.println(
                        "New client connected: "
                                + clientSocket.getInetAddress()
                );

                ClientHandler clientHandler =
                        new ClientHandler(clientSocket);

                Thread clientThread =
                        new Thread(clientHandler);

                clientThread.start();
            }

        } catch (IOException e) {

            System.out.println(
                    "Server error: " + e.getMessage()
            );
        }
    }

    // =====================================================
    // USER MANAGEMENT
    // =====================================================

    public static boolean addOnlineUser(
            String username,
            ClientHandler clientHandler) {

        if (onlineUsers.containsKey(username)) {
            return false;
        }

        onlineUsers.put(
                username,
                clientHandler
        );

        System.out.println(
                username + " is now online."
        );

        System.out.println(
                "Online users: "
                        + onlineUsers.keySet()
        );

        return true;
    }

    public static void removeOnlineUser(
            String username) {

        if (username != null) {

            onlineUsers.remove(username);

            System.out.println(
                    username + " is now offline."
            );
        }
    }

    public static void sendOnlineUsers(
            ClientHandler requester) {

        StringBuilder userList =
                new StringBuilder(
                        "ONLINE_USERS:"
                );

        for (String username :
                onlineUsers.keySet()) {

            userList.append(username)
                    .append(",");
        }

        requester.sendMessage(
                userList.toString()
        );
    }

    // =====================================================
    // BROADCAST
    // =====================================================

    public static void broadcastMessage(
            String message,
            ClientHandler sender) {

        for (ClientHandler client :
                onlineUsers.values()) {

            if (client != sender) {

                client.sendMessage(message);
            }
        }
    }

    // =====================================================
    // PRIVATE MESSAGE
    // =====================================================

    public static boolean sendPrivateMessage(
            String sender,
            String recipient,
            String message) {

        ClientHandler recipientHandler =
                onlineUsers.get(recipient);

        if (recipientHandler != null) {

            recipientHandler.sendMessage(
                    "PRIVATE from "
                            + sender
                            + ": "
                            + message
            );

            return true;
        }

        return false;
    }

    // =====================================================
    // GROUP MANAGEMENT
    // =====================================================

    /*
     * Create a new group.
     */
    public static boolean createGroup(
            String groupName,
            ClientHandler creator) {

        if (groups.containsKey(groupName)) {

            return false;
        }

        Set<ClientHandler> members =
                ConcurrentHashMap.newKeySet();

        members.add(creator);

        groups.put(
                groupName,
                members
        );

        System.out.println(
                "Group created: "
                        + groupName
        );

        return true;
    }

    /*
     * Add a user to a group.
     */
    public static boolean joinGroup(
            String groupName,
            ClientHandler client) {

        Set<ClientHandler> members =
                groups.get(groupName);

        if (members == null) {

            return false;
        }

        members.add(client);

        return true;
    }

    /*
     * Remove a user from a group.
     */
    public static boolean leaveGroup(
            String groupName,
            ClientHandler client) {

        Set<ClientHandler> members =
                groups.get(groupName);

        if (members == null) {

            return false;
        }

        members.remove(client);

        /*
         * Delete empty group.
         */
        if (members.isEmpty()) {

            groups.remove(groupName);
        }

        return true;
    }

    /*
     * Send message to group.
     */
public static boolean sendGroupMessage(
        String groupName,
        ClientHandler sender,
        String message) 
    {

        Set<ClientHandler> members =
                groups.get(groupName);

        // Group does not exist
        if (members == null) {
            return false;
        }

        // Sender is NOT a member of the group
        if (!members.contains(sender)) {
            return false;
        }

        // Sender is a valid group member
        for (ClientHandler member : members) {

            if (member != sender) {

                member.sendMessage(
                        "GROUP ["
                                + groupName
                                + "] "
                                + sender.getUsername()
                                + ": "
                                + message
                );
            }
        }

        return true;
    }

    /*
     * Send list of available groups.
     */
    public static void sendGroupList(
            ClientHandler requester) {

        StringBuilder groupList =
                new StringBuilder(
                        "GROUPS:"
                );

        for (String groupName :
                groups.keySet()) {

            groupList.append(groupName)
                    .append(",");
        }

        requester.sendMessage(
                groupList.toString()
        );
    }

    /*
     * Send group members.
     */
    public static void sendGroupMembers(
            String groupName,
            ClientHandler requester) {

        Set<ClientHandler> members =
                groups.get(groupName);

        if (members == null) {

            requester.sendMessage(
                    "SYSTEM: Group does not exist."
            );

            return;
        }

        StringBuilder membersList =
                new StringBuilder(
                        "MEMBERS ["
                                + groupName
                                + "]: "
                );

        for (ClientHandler member :
                members) {

            membersList.append(
                    member.getUsername()
            );

            membersList.append(" ");
        }

        requester.sendMessage(
                membersList.toString()
        );
    }

    // =====================================================
    // GETTERS
    // =====================================================

    public static int getOnlineUserCount() {

        return onlineUsers.size();
    }

    public static int getGroupCount() {

        return groups.size();
    }
}