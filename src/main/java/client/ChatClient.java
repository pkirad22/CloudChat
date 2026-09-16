package client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {

        // =========================================================
        // SERVER CONFIGURATION
        // =========================================================

        private static final String SERVER_ADDRESS = "localhost";

        private static final int PRIMARY_SERVER_PORT = 5000;
        private static final int SECONDARY_SERVER_PORT = 5002;

        // =========================================================
        // SERVER SELECTION
        // =========================================================

        /*
         * The user selects the preferred server when the client
         * starts.
         *
         * Example:
         *
         * Server 1 selected:
         * Preferred Server = 1
         * Failover Server = 2
         *
         * Server 2 selected:
         * Preferred Server = 2
         * Failover Server = 1
         */

        private static volatile int preferredServerPort = -1;

        private static volatile int failoverServerPort = -1;

        // =========================================================
        // CURRENT CONNECTION
        // =========================================================

        private static volatile Socket socket;

        private static volatile BufferedReader serverInput;

        private static volatile PrintWriter serverOutput;

        private static volatile int currentServerPort = -1;

        // =========================================================
        // CLIENT STATE
        // =========================================================

        private static volatile boolean exiting = false;

        private static volatile boolean reconnecting = false;

        private static volatile boolean authenticated = false;

        // =========================================================
        // LOGIN INFORMATION
        // Stored only in memory for automatic re-authentication
        // after failover.
        // =========================================================

        private static volatile String currentUsername;

        private static volatile String currentPassword;

        // =========================================================
        // MAIN
        // =========================================================

        public static void main(String[] args) {

                System.out.println("=================================");
                System.out.println("        CloudChat Client");
                System.out.println("=================================");

                /*
                 * IMPORTANT:
                 *
                 * Only ONE Scanner is created for System.in.
                 *
                 * This Scanner is used by the main thread.
                 * Failover authentication does NOT create
                 * another Scanner.
                 */

                Scanner scanner = new Scanner(System.in);

                // =====================================================
                // SERVER SELECTION
                // =====================================================

                if (!selectServer(scanner)) {

                        System.out.println();
                        System.out.println(
                                        "Client startup cancelled.");

                        scanner.close();

                        return;
                }

                // =====================================================
                // INITIAL CONNECTION
                // =====================================================

                if (!connectToSelectedServer(scanner)) {

                        System.out.println();
                        System.out.println(
                                        "Unable to connect to the selected CloudChat server.");

                        scanner.close();

                        return;
                }

                // =====================================================
                // AUTHENTICATION
                // =====================================================

                try {

                        authenticated = authenticate(
                                        serverInput,
                                        serverOutput,
                                        scanner);

                } catch (Exception e) {

                        System.out.println();
                        System.out.println(
                                        "Authentication error: "
                                                        + e.getMessage());

                        closeConnection();

                        scanner.close();

                        return;
                }

                // =====================================================
                // AUTHENTICATION FAILED / EXIT
                // =====================================================

                if (!authenticated) {

                        closeConnection();

                        System.out.println();
                        System.out.println(
                                        "Disconnected from server.");

                        scanner.close();

                        return;
                }

                // =====================================================
                // CONNECTION STATUS
                // =====================================================

                System.out.println();
                System.out.println("=================================");
                System.out.println("       CONNECTION STATUS");
                System.out.println("=================================");

                System.out.println(
                                "Connected to "
                                                + currentServerName());

                System.out.println(
                                "Preferred server: "
                                                + serverNameFromPort(
                                                                preferredServerPort));

                System.out.println(
                                "Failover server: "
                                                + serverNameFromPort(
                                                                failoverServerPort));

                System.out.println(
                                "Authentication successful!");

                System.out.println(
                                "=================================");

                // =====================================================
                // START RECEIVE THREAD
                // =====================================================

                startReceiveThread();

                // =====================================================
                // COMMAND MENU
                // =====================================================

                printCommandMenu();

                // =====================================================
                // MAIN INPUT LOOP
                // =====================================================

                while (!exiting) {

                        /*
                         * During failover, don't send commands through
                         * the old/dead socket.
                         */

                        if (reconnecting) {

                                try {

                                        Thread.sleep(200);

                                } catch (InterruptedException e) {

                                        Thread.currentThread().interrupt();
                                        break;
                                }

                                continue;
                        }

                        System.out.print("You: ");

                        if (!scanner.hasNextLine()) {
                                break;
                        }

                        String message = scanner.nextLine();

                        if (message == null) {
                                break;
                        }

                        // =================================================
                        // EXIT
                        // =================================================

                        if (message.equalsIgnoreCase("/exit")) {

                                exiting = true;

                                if (serverOutput != null) {

                                        serverOutput.println("/exit");
                                }

                                break;
                        }

                        // =================================================
                        // SEND MESSAGE
                        // =================================================

                        if (serverOutput != null
                                        && !reconnecting
                                        && authenticated) {

                                serverOutput.println(message);
                        }
                }

                // =====================================================
                // CLOSE
                // =====================================================

                closeConnection();

                System.out.println();
                System.out.println(
                                "Disconnected from server.");

                scanner.close();
        }

        // =========================================================
        // SERVER SELECTION
        // =========================================================

        private static boolean selectServer(Scanner scanner) {

                while (true) {

                        System.out.println();
                        System.out.println(
                                        "=================================");

                        System.out.println(
                                        "        SELECT CLOUDCHAT SERVER");

                        System.out.println(
                                        "=================================");

                        System.out.println(
                                        "1. Server 1 ("
                                                        + SERVER_ADDRESS
                                                        + ":"
                                                        + PRIMARY_SERVER_PORT
                                                        + ")");

                        System.out.println(
                                        "2. Server 2 ("
                                                        + SERVER_ADDRESS
                                                        + ":"
                                                        + SECONDARY_SERVER_PORT
                                                        + ")");

                        System.out.println(
                                        "3. Exit");

                        System.out.println(
                                        "=================================");

                        System.out.print(
                                        "Enter choice: ");

                        if (!scanner.hasNextLine()) {

                                return false;
                        }

                        String choice = scanner.nextLine().trim();

                        // =================================================
                        // SERVER 1
                        // =================================================

                        if (choice.equals("1")) {

                                preferredServerPort = PRIMARY_SERVER_PORT;

                                failoverServerPort = SECONDARY_SERVER_PORT;

                                System.out.println();
                                System.out.println(
                                                "Preferred server selected: Server 1");

                                System.out.println(
                                                "Failover server: Server 2");

                                return true;
                        }

                        // =================================================
                        // SERVER 2
                        // =================================================

                        if (choice.equals("2")) {

                                preferredServerPort = SECONDARY_SERVER_PORT;

                                failoverServerPort = PRIMARY_SERVER_PORT;

                                System.out.println();
                                System.out.println(
                                                "Preferred server selected: Server 2");

                                System.out.println(
                                                "Failover server: Server 1");

                                return true;
                        }

                        // =================================================
                        // EXIT
                        // =================================================

                        if (choice.equals("3")) {

                                return false;
                        }

                        System.out.println();
                        System.out.println(
                                        "Invalid choice. Please select 1, 2, or 3.");
                }
        }

        // =========================================================
        // CONNECT TO SELECTED SERVER
        // =========================================================

        // =========================================================
        // CONNECT TO SELECTED SERVER
        // =========================================================

        private static boolean connectToSelectedServer(
                        Scanner scanner) {

                System.out.println();
                System.out.println(
                                "=================================");

                System.out.println(
                                "       INITIAL SERVER CONNECTION");

                System.out.println(
                                "=================================");

                System.out.println(
                                "Preferred server: "
                                                + serverNameFromPort(
                                                                preferredServerPort));

                System.out.println(
                                "Address: "
                                                + SERVER_ADDRESS
                                                + ":"
                                                + preferredServerPort);

                try {

                        System.out.println();
                        System.out.println(
                                        "Connecting to "
                                                        + serverNameFromPort(
                                                                        preferredServerPort)
                                                        + "...");

                        Socket newSocket = new Socket(
                                        SERVER_ADDRESS,
                                        preferredServerPort);

                        socket = newSocket;

                        setupStreams();

                        currentServerPort = preferredServerPort;

                        System.out.println();
                        System.out.println(
                                        serverNameFromPort(
                                                        currentServerPort)
                                                        + " connected successfully.");

                        System.out.println(
                                        "Connected to CloudChat "
                                                        + serverNameFromPort(
                                                                        currentServerPort)
                                                        + ".");

                        System.out.println(
                                        "=================================");

                        return true;

                } catch (IOException e) {

                        // =====================================================
                        // SELECTED SERVER UNAVAILABLE
                        // =====================================================

                        System.out.println();
                        System.out.println(
                                        serverNameFromPort(
                                                        preferredServerPort)
                                                        + " unavailable.");

                        System.out.println(
                                        "Reason: "
                                                        + e.getMessage());

                        System.out.println();
                        System.out.println(
                                        "=================================");

                        System.out.println(
                                        "       SERVER UNAVAILABLE");

                        System.out.println(
                                        "=================================");

                        System.out.println(
                                        "The selected server is currently unavailable.");

                        System.out.println();

                        // =====================================================
                        // ASK USER FOR FALLBACK
                        // =====================================================

                        System.out.println(
                                        "Would you like to connect to "
                                                        + serverNameFromPort(
                                                                        failoverServerPort)
                                                        + " instead?");

                        System.out.println(
                                        "1. Yes");

                        System.out.println(
                                        "2. No");

                        System.out.println(
                                        "=================================");

                        System.out.print(
                                        "Enter choice: ");

                        if (!scanner.hasNextLine()) {

                                return false;
                        }

                        String choice = scanner.nextLine().trim();

                        // =====================================================
                        // USER DOES NOT WANT FALLBACK
                        // =====================================================

                        if (!choice.equals("1")) {

                                System.out.println();
                                System.out.println(
                                                "Connection cancelled.");

                                return false;
                        }

                        // =====================================================
                        // TRY OTHER SERVER
                        // =====================================================

                        System.out.println();
                        System.out.println(
                                        "Connecting to "
                                                        + serverNameFromPort(
                                                                        failoverServerPort)
                                                        + "...");

                        System.out.println(
                                        "Address: "
                                                        + SERVER_ADDRESS
                                                        + ":"
                                                        + failoverServerPort);

                        try {

                                Socket newSocket = new Socket(
                                                SERVER_ADDRESS,
                                                failoverServerPort);

                                socket = newSocket;

                                setupStreams();

                                currentServerPort = failoverServerPort;

                                System.out.println();
                                System.out.println(
                                                serverNameFromPort(
                                                                currentServerPort)
                                                                + " connected successfully.");

                                System.out.println(
                                                "Connected to CloudChat "
                                                                + serverNameFromPort(
                                                                                currentServerPort)
                                                                + ".");

                                System.out.println(
                                                "=================================");

                                return true;

                        } catch (IOException secondException) {

                                // =================================================
                                // BOTH SERVERS UNAVAILABLE
                                // =================================================

                                System.out.println();
                                System.out.println(
                                                serverNameFromPort(
                                                                failoverServerPort)
                                                                + " is also unavailable.");

                                System.out.println(
                                                "Reason: "
                                                                + secondException.getMessage());

                                System.out.println();
                                System.out.println(
                                                "=================================");

                                System.out.println(
                                                "       NO SERVER AVAILABLE");

                                System.out.println(
                                                "=================================");

                                System.out.println(
                                                "Both CloudChat servers are currently unavailable.");

                                System.out.println(
                                                "Please try again later.");

                                System.out.println(
                                                "=================================");

                                return false;
                        }
                }
        }

        // =========================================================
        // SETUP STREAMS
        // =========================================================

        private static void setupStreams()
                        throws IOException {

                serverInput = new BufferedReader(
                                new InputStreamReader(
                                                socket.getInputStream()));

                serverOutput = new PrintWriter(
                                socket.getOutputStream(),
                                true);
        }

        // =========================================================
        // RECEIVE THREAD
        // =========================================================

        private static void startReceiveThread() {

                Thread receiveThread = new Thread(() -> {

                        try {

                                while (!exiting) {

                                        BufferedReader input = serverInput;

                                        if (input == null) {

                                                break;
                                        }

                                        String serverMessage;

                                        while (!exiting
                                                        && !reconnecting
                                                        && (serverMessage = input.readLine()) != null) {

                                                System.out.println();

                                                System.out.println(
                                                                formatServerMessage(
                                                                                serverMessage));

                                                // =================================================
                                                // FILE TRANSFER
                                                // =================================================

                                                if (serverMessage.startsWith(
                                                                "FILE_INCOMING:")) {

                                                        System.out.println(
                                                                        "Preparing to receive file...");

                                                        receiveFile();
                                                }

                                                System.out.print("You: ");
                                        }

                                        // =====================================================
                                        // SERVER CONNECTION LOST
                                        // =====================================================

                                        if (!exiting
                                                        && !reconnecting) {

                                                System.out.println();
                                                System.out.println(
                                                                "Connection to "
                                                                                + currentServerName()
                                                                                + " lost.");

                                                performFailover();
                                        }

                                        break;
                                }

                        } catch (IOException e) {

                                if (!exiting
                                                && !reconnecting) {

                                        System.out.println();
                                        System.out.println(
                                                        "Connection to "
                                                                        + currentServerName()
                                                                        + " lost.");

                                        performFailover();
                                }
                        }

                });

                receiveThread.setDaemon(true);

                receiveThread.start();
        }

        // =========================================================
        // AUTOMATIC FAILOVER
        // =========================================================

        private static synchronized void performFailover() {

                if (reconnecting || exiting) {

                        return;
                }

                reconnecting = true;

                authenticated = false;

                System.out.println();
                System.out.println(
                                "=================================");

                System.out.println(
                                "       AUTOMATIC FAILOVER");

                System.out.println(
                                "=================================");

                int failedServer = currentServerPort;

                String failedServerName = currentServerName();

                System.out.println(
                                "Failed server: "
                                                + failedServerName);

                // =====================================================
                // CLOSE FAILED CONNECTION
                // =====================================================

                closeSocketOnly();

                // =====================================================
                // DETERMINE FAILOVER SERVER
                // =====================================================

                int backupPort = failoverServerPort;

                /*
                 * Safety check:
                 *
                 * If the failover port somehow equals the failed
                 * server, determine the other server manually.
                 */

                if (backupPort == failedServer) {

                        if (failedServer == PRIMARY_SERVER_PORT) {

                                backupPort = SECONDARY_SERVER_PORT;

                        } else {

                                backupPort = PRIMARY_SERVER_PORT;
                        }
                }

                // =====================================================
                // TRY FAILOVER SERVER
                // =====================================================

                while (!exiting) {

                        try {

                                System.out.println();
                                System.out.println(
                                                "Trying failover server: "
                                                                + serverNameFromPort(
                                                                                backupPort)
                                                                + "...");

                                System.out.println(
                                                "Address: "
                                                                + SERVER_ADDRESS
                                                                + ":"
                                                                + backupPort);

                                Socket newSocket = new Socket(
                                                SERVER_ADDRESS,
                                                backupPort);

                                socket = newSocket;

                                setupStreams();

                                currentServerPort = backupPort;

                                System.out.println();
                                System.out.println(
                                                "Failover server connected successfully!");

                                System.out.println(
                                                "Connected to "
                                                                + currentServerName()
                                                                + ".");

                                // =================================================
                                // AUTOMATIC RE-AUTHENTICATION
                                // =================================================

                                System.out.println();
                                System.out.println(
                                                "Re-authenticating...");

                                boolean loginSuccessful = automaticReAuthentication();

                                if (loginSuccessful) {

                                        authenticated = true;

                                        /*
                                         * After successful failover,
                                         * the failed server remains the
                                         * preferred server for future
                                         * sessions, while this server is
                                         * simply the current connection.
                                         */

                                        System.out.println();
                                        System.out.println(
                                                        "=================================");

                                        System.out.println(
                                                        "       FAILOVER SUCCESSFUL");

                                        System.out.println(
                                                        "=================================");

                                        System.out.println(
                                                        "Connected to "
                                                                        + currentServerName());

                                        System.out.println(
                                                        "Authentication successful!");

                                        System.out.println(
                                                        "Your session has been restored.");

                                        System.out.println(
                                                        "=================================");

                                        System.out.println();

                                        reconnecting = false;

                                        // =================================================
                                        // START RECEIVING FROM NEW SERVER
                                        // =================================================

                                        startReceiveThread();

                                        // =================================================
                                        // SHOW COMMAND MENU
                                        // =================================================

                                        printCommandMenu();

                                        return;
                                }

                                // =================================================
                                // RE-AUTHENTICATION FAILED
                                // =================================================

                                System.out.println();
                                System.out.println(
                                                "Automatic re-authentication failed.");

                                closeSocketOnly();

                        } catch (IOException e) {

                                System.out.println();
                                System.out.println(
                                                "Failover server unavailable.");

                                System.out.println(
                                                "Retrying in 3 seconds...");

                                try {

                                        Thread.sleep(3000);

                                } catch (InterruptedException interruptedException) {

                                        Thread.currentThread().interrupt();

                                        break;
                                }
                        }
                }

                reconnecting = false;
        }

        // =========================================================
        // AUTOMATIC RE-AUTHENTICATION
        // =========================================================

        private static boolean automaticReAuthentication() {

                if (currentUsername == null
                                || currentPassword == null) {

                        System.out.println(
                                        "No stored login credentials available.");

                        return false;
                }

                try {

                        // =================================================
                        // SERVER AUTH REQUEST
                        // =================================================

                        String request = serverInput.readLine();

                        if (request == null
                                        || !request.equals(
                                                        "AUTH_REQUEST")) {

                                System.out.println(
                                                "Unexpected server response: "
                                                                + request);

                                return false;
                        }

                        // =================================================
                        // SEND LOGIN REQUEST
                        // =================================================

                        serverOutput.println("login");

                        request = serverInput.readLine();

                        if (request == null
                                        || !request.equals(
                                                        "USERNAME_REQUEST")) {

                                System.out.println(
                                                "Unexpected server response: "
                                                                + request);

                                return false;
                        }

                        // =================================================
                        // SEND USERNAME
                        // =================================================

                        serverOutput.println(
                                        currentUsername);

                        request = serverInput.readLine();

                        if (request == null
                                        || !request.equals(
                                                        "PASSWORD_REQUEST")) {

                                System.out.println(
                                                "Unexpected server response: "
                                                                + request);

                                return false;
                        }

                        // =================================================
                        // SEND PASSWORD
                        // =================================================

                        serverOutput.println(
                                        currentPassword);

                        String response = serverInput.readLine();

                        if (response == null) {

                                return false;
                        }

                        System.out.println();

                        System.out.println(
                                        response);

                        // =================================================
                        // LOGIN SUCCESS
                        // =================================================

                        if (response.startsWith(
                                        "LOGIN_SUCCESS")) {

                                return true;
                        }

                        return false;

                } catch (IOException e) {

                        System.out.println(
                                        "Re-authentication error: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // CURRENT SERVER NAME
        // =========================================================

        private static String currentServerName() {

                return serverNameFromPort(
                                currentServerPort);
        }

        // =========================================================
        // SERVER NAME FROM PORT
        // =========================================================

        private static String serverNameFromPort(
                        int port) {

                if (port == PRIMARY_SERVER_PORT) {

                        return "Server 1";
                }

                if (port == SECONDARY_SERVER_PORT) {

                        return "Server 2";
                }

                return "Unknown Server";
        }

        // =========================================================
        // CLOSE SOCKET ONLY
        // =========================================================

        private static void closeSocketOnly() {

                try {

                        if (socket != null
                                        && !socket.isClosed()) {

                                socket.close();
                        }

                } catch (IOException ignored) {
                }

                socket = null;

                serverInput = null;

                serverOutput = null;
        }

        // =========================================================
        // CLOSE CONNECTION
        // =========================================================

        private static void closeConnection() {

                exiting = true;

                try {

                        if (serverOutput != null) {

                                serverOutput.println("/exit");
                        }

                } catch (Exception ignored) {
                }

                closeSocketOnly();

                // Clear credentials from memory
                currentUsername = null;
                currentPassword = null;
        }

        // =========================================================
        // AUTHENTICATION
        // =========================================================

        private static boolean authenticate(
                        BufferedReader serverInput,
                        PrintWriter serverOutput,
                        Scanner scanner) {

                while (true) {

                        try {

                                // =================================================
                                // AUTH REQUEST
                                // =================================================

                                String authRequest = serverInput.readLine();

                                if (authRequest == null) {

                                        System.out.println(
                                                        "Server disconnected.");

                                        return false;
                                }

                                if (!authRequest.equals(
                                                "AUTH_REQUEST")) {

                                        System.out.println(
                                                        "Unexpected server response: "
                                                                        + authRequest);

                                        return false;
                                }

                                // =================================================
                                // LOGIN MENU
                                // =================================================

                                System.out.println();
                                System.out.println(
                                                "=================================");

                                System.out.println(
                                                "        CLOUDCHAT LOGIN");

                                System.out.println(
                                                "=================================");

                                System.out.println(
                                                "1. Login");

                                System.out.println(
                                                "2. Register");

                                System.out.println(
                                                "3. Exit");

                                System.out.println(
                                                "=================================");

                                System.out.print(
                                                "Enter choice: ");

                                String choice = scanner.nextLine().trim();

                                // =================================================
                                // EXIT
                                // =================================================

                                if (choice.equals("3")) {

                                        serverOutput.println(
                                                        "exit");

                                        exiting = true;

                                        return false;
                                }

                                // =================================================
                                // LOGIN
                                // =================================================

                                if (choice.equals("1")) {

                                        boolean loginSuccessful = performLogin(
                                                        serverInput,
                                                        serverOutput,
                                                        scanner);

                                        if (loginSuccessful) {

                                                return true;
                                        }

                                        continue;
                                }

                                // =================================================
                                // REGISTER
                                // =================================================

                                if (choice.equals("2")) {

                                        performRegistration(
                                                        serverInput,
                                                        serverOutput,
                                                        scanner);

                                        continue;
                                }

                                System.out.println();
                                System.out.println(
                                                "Invalid choice.");

                        } catch (IOException e) {

                                System.out.println();
                                System.out.println(
                                                "Authentication error: "
                                                                + e.getMessage());

                                return false;
                        }
                }
        }

        // =========================================================
        // LOGIN
        // =========================================================

        private static boolean performLogin(
                        BufferedReader serverInput,
                        PrintWriter serverOutput,
                        Scanner scanner)
                        throws IOException {

                serverOutput.println("login");

                String request = serverInput.readLine();

                if (!"USERNAME_REQUEST".equals(
                                request)) {

                        System.out.println(
                                        "Unexpected server response: "
                                                        + request);

                        return false;
                }

                System.out.print(
                                "Enter username: ");

                String username = scanner.nextLine().trim();

                serverOutput.println(
                                username);

                request = serverInput.readLine();

                if (!"PASSWORD_REQUEST".equals(
                                request)) {

                        System.out.println(
                                        "Unexpected server response: "
                                                        + request);

                        return false;
                }

                System.out.print(
                                "Enter password: ");

                String password = scanner.nextLine();

                serverOutput.println(
                                password);

                String response = serverInput.readLine();

                if (response == null) {

                        System.out.println(
                                        "Server disconnected.");

                        return false;
                }

                System.out.println();
                System.out.println(
                                response);

                // =====================================================
                // LOGIN SUCCESS
                // =====================================================

                if (response.startsWith(
                                "LOGIN_SUCCESS")) {

                        /*
                         * Store credentials in memory so the client can
                         * automatically authenticate after failover.
                         */

                        currentUsername = username;

                        currentPassword = password;

                        System.out.println();
                        System.out.println(
                                        "Authentication successful!");

                        return true;
                }

                // =====================================================
                // LOGIN FAILED
                // =====================================================

                System.out.println();
                System.out.println(
                                "Login failed.");

                return false;
        }

        // =========================================================
        // REGISTRATION
        // =========================================================

        private static void performRegistration(
                        BufferedReader serverInput,
                        PrintWriter serverOutput,
                        Scanner scanner)
                        throws IOException {

                serverOutput.println("register");

                // =====================================================
                // USERNAME
                // =====================================================

                String request = serverInput.readLine();

                if (!"USERNAME_REQUEST".equals(
                                request)) {

                        System.out.println(
                                        "Unexpected server response: "
                                                        + request);

                        return;
                }

                System.out.print(
                                "Enter username: ");

                String username = scanner.nextLine().trim();

                serverOutput.println(
                                username);

                // =====================================================
                // EMAIL
                // =====================================================

                request = serverInput.readLine();

                if (!"EMAIL_REQUEST".equals(
                                request)) {

                        System.out.println(
                                        "Unexpected server response: "
                                                        + request);

                        return;
                }

                System.out.print(
                                "Enter email: ");

                String email = scanner.nextLine().trim();

                serverOutput.println(
                                email);

                // =====================================================
                // PASSWORD
                // =====================================================

                request = serverInput.readLine();

                if (!"PASSWORD_REQUEST".equals(
                                request)) {

                        System.out.println(
                                        "Unexpected server response: "
                                                        + request);

                        return;
                }

                System.out.print(
                                "Enter password: ");

                String password = scanner.nextLine();

                serverOutput.println(
                                password);

                // =====================================================
                // RESULT
                // =====================================================

                String response = serverInput.readLine();

                if (response == null) {

                        System.out.println(
                                        "Server disconnected.");

                        return;
                }

                System.out.println();
                System.out.println(
                                response);

                if (response.startsWith(
                                "REGISTER_SUCCESS")) {

                        System.out.println();
                        System.out.println(
                                        "Registration completed.");

                        System.out.println(
                                        "Please login with your credentials.");

                } else {

                        System.out.println();
                        System.out.println(
                                        "Registration failed.");
                }
        }

        // =========================================================
        // RECEIVE FILE
        // =========================================================

        private static void receiveFile() {

                final int FILE_PORT = 5001;

                File downloadDirectory = new File("downloads");

                if (!downloadDirectory.exists()) {

                        downloadDirectory.mkdirs();
                }

                try (
                                Socket fileSocket = new Socket(
                                                SERVER_ADDRESS,
                                                FILE_PORT);

                                DataInputStream input = new DataInputStream(
                                                fileSocket.getInputStream())) {

                        // =================================================
                        // FILE NAME
                        // =================================================

                        String fileName = input.readUTF();

                        // =================================================
                        // FILE SIZE
                        // =================================================

                        long fileSize = input.readLong();

                        System.out.println();
                        System.out.println(
                                        "=================================");

                        System.out.println(
                                        "Receiving file: "
                                                        + fileName);

                        System.out.println(
                                        "File size: "
                                                        + fileSize
                                                        + " bytes");

                        File outputFile = new File(
                                        downloadDirectory,
                                        fileName);

                        // =================================================
                        // TOTAL RECEIVED
                        // =================================================

                        long totalReceived = 0;

                        // =================================================
                        // WRITE FILE
                        // =================================================

                        try (
                                        FileOutputStream output = new FileOutputStream(
                                                        outputFile)) {

                                byte[] buffer = new byte[8192];

                                while (totalReceived < fileSize) {

                                        int bytesRead = input.read(
                                                        buffer,
                                                        0,
                                                        (int) Math.min(
                                                                        buffer.length,
                                                                        fileSize
                                                                                        - totalReceived));

                                        if (bytesRead == -1) {

                                                break;
                                        }

                                        output.write(
                                                        buffer,
                                                        0,
                                                        bytesRead);

                                        totalReceived += bytesRead;
                                }
                        }

                        // =================================================
                        // SUCCESS / INCOMPLETE
                        // =================================================

                        if (totalReceived == fileSize) {

                                System.out.println(
                                                "File received successfully!");

                        } else {

                                System.out.println(
                                                "File transfer incomplete.");

                                System.out.println(
                                                "Expected: "
                                                                + fileSize
                                                                + " bytes");

                                System.out.println(
                                                "Received: "
                                                                + totalReceived
                                                                + " bytes");
                        }

                        // =================================================
                        // FILE LOCATION
                        // =================================================

                        System.out.println(
                                        "Saved at: "
                                                        + outputFile
                                                                        .getAbsolutePath());

                        System.out.println(
                                        "=================================");

                } catch (IOException e) {

                        System.out.println();
                        System.out.println(
                                        "Unable to receive file.");

                        System.out.println(
                                        "Error: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // COMMAND MENU
        // =========================================================

        private static void printCommandMenu() {

                System.out.println();
                System.out.println(
                                "=================================");

                System.out.println(
                                "          CHAT COMMANDS");

                System.out.println(
                                "=================================");

                System.out.println(
                                "/users");

                System.out.println(
                                "/msg username message");

                System.out.println(
                                "/create group");

                System.out.println(
                                "/join group");

                System.out.println(
                                "/leave group");

                System.out.println(
                                "/groups");

                System.out.println(
                                "/members group");

                System.out.println(
                                "/groupmsg group message");

                System.out.println(
                                "/sendfile username filepath");

                System.out.println(
                                "/history username");

                System.out.println(
                                "/grouphistory group");

                System.out.println(
                                "/exit");

                System.out.println(
                                "=================================");

                System.out.println();
        }

        // =========================================================
        // FORMAT SERVER MESSAGES
        // =========================================================

        private static String formatServerMessage(
                        String message) {

                if (message == null) {

                        return "";
                }

                // =====================================================
                // ONLINE USERS
                // =====================================================

                if (message.startsWith(
                                "ONLINE_USERS:")) {

                        String users = message.substring(
                                        "ONLINE_USERS:"
                                                        .length());

                        users = users.replace(
                                        ",",
                                        "\n");

                        return "\n"
                                        + "========== ONLINE USERS ==========\n"
                                        + users
                                        + "\n"
                                        + "==================================";
                }

                // =====================================================
                // GROUPS
                // =====================================================

                if (message.startsWith(
                                "GROUPS:")) {

                        String groups = message.substring(
                                        "GROUPS:"
                                                        .length());

                        groups = groups.replace(
                                        ",",
                                        "\n");

                        return "\n"
                                        + "============ GROUPS =============\n"
                                        + groups
                                        + "\n"
                                        + "==================================";
                }

                return message;
        }
}