package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import database.MongoDBConnection;

public class ChatServer2 {

        // =========================================================
        // SERVER 2 CONFIGURATION
        // =========================================================

        private static final int PORT = 5002;
        private static final int SYNC_PORT = 6000;

        // =========================================================
        // MAIN
        // =========================================================

        public static void main(String[] args) {

                System.out.println("=================================");
                System.out.println("       CloudChat Server 2");
                System.out.println("=================================");

                MongoDBConnection.connect();

                // Initialize shared group persistence service
                ChatServer.initializeGroupService();

                // Load persisted groups and members from MongoDB
                ChatServer.loadGroupsFromDatabase();

                /*
                 * IMPORTANT:
                 *
                 * ClientHandler uses ChatServer methods.
                 *
                 * Therefore Server 2 must configure the
                 * ChatServer synchronizer as SECONDARY.
                 *
                 * This prevents Server 2 from accidentally using
                 * ChatServer's primary synchronizer.
                 */
                ChatServer.initializeSynchronizer(false);

                System.out.println(
                                "Server synchronization service started.");

                System.out.println(
                                "Server 2 will connect to Server 1 "
                                                + "on synchronization port "
                                                + SYNC_PORT);

                System.out.println();

                try (ServerSocket serverSocket = new ServerSocket(PORT)) {

                        System.out.println(
                                        "Server 2 started successfully.");

                        System.out.println(
                                        "Client listening port: "
                                                        + PORT);

                        System.out.println(
                                        "Waiting for clients...");

                        System.out.println();

                        while (true) {

                                Socket clientSocket = serverSocket.accept();

                                System.out.println(
                                                "New client connected to Server 2: "
                                                                + clientSocket.getInetAddress());

                                ClientHandler clientHandler = new ClientHandler(clientSocket);

                                Thread clientThread = new Thread(clientHandler);

                                clientThread.start();
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "Server 2 error: "
                                                        + e.getMessage());
                }
        }
}