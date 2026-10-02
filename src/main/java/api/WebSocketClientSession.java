package api;

import org.java_websocket.WebSocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketClientSession {

        private final WebSocket webSocket;

        private BackendSocketConnection backendConnection;

        private String username;
        private String password;

        private String currentServerId;

        private final AtomicBoolean authenticated = new AtomicBoolean(false);

        private final AtomicBoolean switchingServer = new AtomicBoolean(false);

        private final AtomicBoolean failoverInProgress = new AtomicBoolean(false);

        // =========================================================
        // FILE UPLOAD STATE
        // =========================================================

        private File uploadFile;

        private FileOutputStream uploadOutputStream;

        private String uploadRecipient;

        private String uploadFileName;

        private long uploadExpectedSize;

        private long uploadReceivedSize;

        private boolean fileUploadInProgress = false;

        private static final long MAX_FILE_SIZE = 100L * 1024L * 1024L;

        // =========================================================
        // FILE DOWNLOAD CHUNK SIZE
        // =========================================================

        private static final int FILE_CHUNK_SIZE = 64 * 1024;

        public WebSocketClientSession(
                        WebSocket webSocket) {

                this.webSocket = webSocket;
        }

        // =========================================================
        // START SESSION
        // =========================================================

        public void start() {

                sendJson(
                                "{\"type\":\"connected\","
                                                + "\"message\":\"CloudChat WebSocket connected\"}");
        }

        // =========================================================
        // HANDLE MESSAGE FROM REACT
        // =========================================================

        public void handleClientMessage(
                        String message) {

                if (message == null) {
                        return;
                }

                message = message.trim();

                if (message.isEmpty()) {
                        return;
                }

                System.out.println(
                                "[BRIDGE] React -> "
                                                + message);

                // -----------------------------------------------------
                // LOGIN REQUEST
                // -----------------------------------------------------

                if (message.startsWith("{")
                                && message.contains("\"type\":\"login\"")) {

                        handleLoginRequest(message);

                        return;
                }

                // -----------------------------------------------------
                // FILE START REQUEST
                // -----------------------------------------------------

                if (message.startsWith("{")
                                && message.contains("\"type\":\"file_start\"")) {

                        handleFileStart(message);

                        return;
                }

                // -----------------------------------------------------
                // NORMAL CHAT COMMAND
                // -----------------------------------------------------

                if (!authenticated.get()) {

                        sendError(
                                        "Not authenticated");

                        return;
                }

                sendToBackend(message);
        }

        // =========================================================
        // HANDLE FILE START
        // =========================================================

        private synchronized void handleFileStart(
                        String json) {

                if (!authenticated.get()) {

                        sendError(
                                        "Not authenticated");

                        return;
                }

                if (fileUploadInProgress) {

                        sendFileError(
                                        "Another file upload is already in progress.");

                        return;
                }

                String recipient = extractJsonValue(
                                json,
                                "recipient");

                String fileName = extractJsonValue(
                                json,
                                "fileName");

                String fileSizeText = extractJsonNumber(
                                json,
                                "fileSize");

                if (recipient == null
                                || recipient.trim().isEmpty()) {

                        sendFileError(
                                        "Recipient is required.");

                        return;
                }

                if (fileName == null
                                || fileName.trim().isEmpty()) {

                        sendFileError(
                                        "File name is required.");

                        return;
                }

                if (fileSizeText == null
                                || fileSizeText.trim().isEmpty()) {

                        sendFileError(
                                        "File size is required.");

                        return;
                }

                long fileSize;

                try {

                        fileSize = Long.parseLong(
                                        fileSizeText.trim());

                } catch (NumberFormatException e) {

                        sendFileError(
                                        "Invalid file size.");

                        return;
                }

                if (fileSize <= 0) {

                        sendFileError(
                                        "File cannot be empty.");

                        return;
                }

                if (fileSize > MAX_FILE_SIZE) {

                        sendFileError(
                                        "File is too large. Maximum size is 100 MB.");

                        return;
                }

                // -----------------------------------------------------
                // Prevent path traversal
                // -----------------------------------------------------

                String safeFileName;

                try {

                        safeFileName = Path.of(fileName)
                                        .getFileName()
                                        .toString();

                } catch (Exception e) {

                        sendFileError(
                                        "Invalid file name.");

                        return;
                }

                if (safeFileName.isEmpty()) {

                        sendFileError(
                                        "Invalid file name.");

                        return;
                }

                // -----------------------------------------------------
                // Create temporary upload file
                // -----------------------------------------------------

                try {

                        Path uploadDirectory = Path.of(
                                        System.getProperty(
                                                        "java.io.tmpdir"),
                                        "cloudchat-uploads");

                        Files.createDirectories(
                                        uploadDirectory);

                        Path temporaryPath = Files.createTempFile(
                                        uploadDirectory,
                                        "cloudchat-",
                                        "-" + safeFileName);

                        uploadFile = temporaryPath.toFile();

                        uploadOutputStream = new FileOutputStream(
                                        uploadFile);

                } catch (IOException e) {

                        System.out.println(
                                        "[BRIDGE-FILE] Unable to create temporary file: "
                                                        + e.getMessage());

                        sendFileError(
                                        "Unable to prepare file upload.");

                        cleanupFileUpload();

                        return;
                }

                uploadRecipient = recipient.trim();

                uploadFileName = safeFileName;

                uploadExpectedSize = fileSize;

                uploadReceivedSize = 0;

                fileUploadInProgress = true;

                System.out.println();
                System.out.println(
                                "[BRIDGE-FILE] Upload started");

                System.out.println(
                                "[BRIDGE-FILE] Sender: "
                                                + username);

                System.out.println(
                                "[BRIDGE-FILE] Recipient: "
                                                + uploadRecipient);

                System.out.println(
                                "[BRIDGE-FILE] File: "
                                                + uploadFileName);

                System.out.println(
                                "[BRIDGE-FILE] Expected size: "
                                                + uploadExpectedSize);

                sendJson(
                                "{\"type\":\"file_upload_started\","
                                                + "\"recipient\":\""
                                                + escapeJson(
                                                                uploadRecipient)
                                                + "\","
                                                + "\"fileName\":\""
                                                + escapeJson(
                                                                uploadFileName)
                                                + "\","
                                                + "\"fileSize\":"
                                                + uploadExpectedSize
                                                + "}");
        }

        // =========================================================
        // HANDLE BINARY FILE DATA FROM SENDER
        // =========================================================

        public synchronized void handleBinaryMessage(
                        ByteBuffer bytes) {

                if (!fileUploadInProgress
                                || uploadOutputStream == null) {

                        System.out.println(
                                        "[BRIDGE-FILE] Unexpected binary data.");

                        sendFileError(
                                        "No file upload is currently active.");

                        return;
                }

                if (bytes == null) {

                        sendFileError(
                                        "Empty binary data.");

                        return;
                }

                int remaining = bytes.remaining();

                if (remaining <= 0) {
                        return;
                }

                if (uploadReceivedSize
                                + remaining > uploadExpectedSize) {

                        sendFileError(
                                        "Received more data than expected.");

                        cleanupFileUpload();

                        return;
                }

                try {

                        byte[] buffer = new byte[remaining];

                        bytes.get(buffer);

                        uploadOutputStream.write(
                                        buffer);

                        uploadReceivedSize += remaining;

                        if (uploadReceivedSize == uploadExpectedSize) {

                                finishFileUpload();
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "[BRIDGE-FILE] File write failed: "
                                                        + e.getMessage());

                        sendFileError(
                                        "Unable to save uploaded file.");

                        cleanupFileUpload();
                }
        }

        // =========================================================
        // FINISH SENDER UPLOAD
        // =========================================================

        private synchronized void finishFileUpload() {

                try {

                        if (uploadOutputStream != null) {

                                uploadOutputStream.flush();

                                uploadOutputStream.close();

                                uploadOutputStream = null;
                        }

                        if (uploadFile == null
                                        || !uploadFile.exists()) {

                                sendFileError(
                                                "Uploaded file could not be found.");

                                cleanupFileUpload();

                                return;
                        }

                        if (uploadFile.length() != uploadExpectedSize) {

                                sendFileError(
                                                "Uploaded file size does not match.");

                                cleanupFileUpload();

                                return;
                        }

                        System.out.println();
                        System.out.println(
                                        "[BRIDGE-FILE] Upload completed");

                        System.out.println(
                                        "[BRIDGE-FILE] File: "
                                                        + uploadFile.getAbsolutePath());

                        String backendCommand = "/sendfile "
                                        + uploadRecipient
                                        + " "
                                        + uploadFile.getAbsolutePath();

                        System.out.println(
                                        "[BRIDGE-FILE] Sending backend command: "
                                                        + backendCommand);

                        sendToBackend(
                                        backendCommand);

                        sendJson(
                                        "{\"type\":\"file_upload_complete\","
                                                        + "\"recipient\":\""
                                                        + escapeJson(
                                                                        uploadRecipient)
                                                        + "\","
                                                        + "\"fileName\":\""
                                                        + escapeJson(
                                                                        uploadFileName)
                                                        + "\","
                                                        + "\"fileSize\":"
                                                        + uploadExpectedSize
                                                        + "}");

                } catch (Exception e) {

                        System.out.println(
                                        "[BRIDGE-FILE] Upload completion failed: "
                                                        + e.getMessage());

                        sendFileError(
                                        "Unable to process uploaded file.");

                        cleanupFileUpload();

                        return;
                }

                // -----------------------------------------------------
                // IMPORTANT
                //
                // Do not delete the file here.
                //
                // ClientHandler still needs it to send it to the
                // recipient's WebSocket.
                // -----------------------------------------------------

                resetFileUploadState();
        }

        // =========================================================
        // SEND FILE TO RECIPIENT BROWSER
        // =========================================================
        //
        // This completely replaces the old port 5001 approach for
        // local browser-to-browser transfers.
        //
        // =========================================================

        public synchronized void sendFileToBrowser(
                        String sender,
                        File file)
                        throws IOException {

                if (webSocket == null
                                || !webSocket.isOpen()) {

                        throw new IOException(
                                        "Recipient WebSocket is not open.");
                }

                if (file == null
                                || !file.exists()
                                || !file.isFile()) {

                        throw new IOException(
                                        "File does not exist.");
                }

                long fileSize = file.length();

                String fileName = file.getName();

                // -----------------------------------------------------
                // Tell recipient that binary transfer is starting
                // -----------------------------------------------------

                sendJson(
                                "{\"type\":\"file_delivery_started\","
                                                + "\"sender\":\""
                                                + escapeJson(sender)
                                                + "\","
                                                + "\"fileName\":\""
                                                + escapeJson(fileName)
                                                + "\","
                                                + "\"fileSize\":"
                                                + fileSize
                                                + "}");

                System.out.println(
                                "[WS-FILE] Sending binary file to "
                                                + username
                                                + ": "
                                                + fileName);

                long totalSent = 0;

                try (
                                FileInputStream input = new FileInputStream(file)) {

                        byte[] buffer = new byte[FILE_CHUNK_SIZE];

                        int bytesRead;

                        while ((bytesRead = input.read(buffer)) != -1) {

                                if (webSocket == null
                                                || !webSocket.isOpen()) {

                                        throw new IOException(
                                                        "Recipient WebSocket disconnected.");
                                }

                                byte[] chunk = new byte[bytesRead];

                                System.arraycopy(
                                                buffer,
                                                0,
                                                chunk,
                                                0,
                                                bytesRead);

                                webSocket.send(
                                                ByteBuffer.wrap(chunk));

                                totalSent += bytesRead;
                        }
                }

                // -----------------------------------------------------
                // Tell recipient transfer is complete
                // -----------------------------------------------------

                sendJson(
                                "{\"type\":\"file_delivery_complete\","
                                                + "\"sender\":\""
                                                + escapeJson(sender)
                                                + "\","
                                                + "\"fileName\":\""
                                                + escapeJson(fileName)
                                                + "\","
                                                + "\"fileSize\":"
                                                + fileSize
                                                + "}");

                System.out.println(
                                "[WS-FILE] File delivered successfully");

                System.out.println(
                                "[WS-FILE] Recipient: "
                                                + username);

                System.out.println(
                                "[WS-FILE] Total bytes: "
                                                + totalSent);
        }

        // =========================================================
        // FILE ERROR
        // =========================================================

        private synchronized void sendFileError(
                        String message) {

                sendJson(
                                "{\"type\":\"file_upload_error\","
                                                + "\"message\":\""
                                                + escapeJson(message)
                                                + "\"}");
        }

        // =========================================================
        // CLEANUP FILE UPLOAD
        // =========================================================

        private synchronized void cleanupFileUpload() {

                try {

                        if (uploadOutputStream != null) {

                                uploadOutputStream.close();
                        }

                } catch (Exception ignored) {
                }

                uploadOutputStream = null;

                deleteTemporaryUpload();

                resetFileUploadState();
        }

        // =========================================================
        // DELETE TEMPORARY UPLOAD
        // =========================================================

        private void deleteTemporaryUpload() {

                if (uploadFile == null) {
                        return;
                }

                try {

                        if (uploadFile.exists()) {

                                boolean deleted = uploadFile.delete();

                                System.out.println(
                                                "[BRIDGE-FILE] Temporary file deleted: "
                                                                + deleted);
                        }

                } catch (Exception e) {

                        System.out.println(
                                        "[BRIDGE-FILE] Unable to delete temporary file: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // RESET FILE STATE
        // =========================================================

        private void resetFileUploadState() {

                uploadFile = null;
                uploadRecipient = null;
                uploadFileName = null;
                uploadExpectedSize = 0;
                uploadReceivedSize = 0;
                fileUploadInProgress = false;
        }

        // =========================================================
        // LOGIN REQUEST
        // =========================================================

        private void handleLoginRequest(
                        String json) {

                String requestedUsername = extractJsonValue(
                                json,
                                "username");

                String requestedPassword = extractJsonValue(
                                json,
                                "password");

                if (requestedUsername == null
                                || requestedPassword == null
                                || requestedUsername.trim().isEmpty()
                                || requestedPassword.isEmpty()) {

                        sendError(
                                        "Username and password are required");

                        return;
                }

                username = requestedUsername.trim();

                password = requestedPassword;

                sendJson(
                                "{\"type\":\"login_start\","
                                                + "\"message\":\"Connecting to chat server...\"}");

                connectToBestServer();
        }

        // =========================================================
        // CONNECT TO BEST AVAILABLE SERVER
        // =========================================================

        private void connectToBestServer() {

                Thread thread = new Thread(
                                () -> {

                                        if (connectToServer(
                                                        "SERVER_1",
                                                        "localhost",
                                                        5000)) {

                                                return;
                                        }

                                        if (connectToServer(
                                                        "SERVER_2",
                                                        "localhost",
                                                        5002)) {

                                                return;
                                        }

                                        sendError(
                                                        "Both CloudChat servers are unavailable");

                                });

                thread.setName(
                                "Bridge-Server-Selector");

                thread.start();
        }

        // =========================================================
        // CONNECT TO SPECIFIC SERVER
        // =========================================================

        private boolean connectToServer(
                        String serverId,
                        String host,
                        int port) {

                try {

                        System.out.println(
                                        "[BRIDGE] Trying "
                                                        + serverId
                                                        + " "
                                                        + host
                                                        + ":"
                                                        + port);

                        BackendSocketConnection connection = new BackendSocketConnection(
                                        serverId,
                                        host,
                                        port);

                        if (!connection.connect()) {
                                return false;
                        }

                        backendConnection = connection;

                        currentServerId = serverId;

                        authenticated.set(false);

                        startBackendReader(
                                        connection);

                        sendJson(
                                        "{\"type\":\"server_connecting\","
                                                        + "\"server\":\""
                                                        + serverId
                                                        + "\"}");

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[BRIDGE] Server connection error: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // BACKEND READER
        // =========================================================

        private void startBackendReader(
                        BackendSocketConnection connection) {

                Thread readerThread = new Thread(
                                () -> {

                                        try {

                                                String message;

                                                while ((message = connection.readMessage()) != null) {

                                                        handleBackendMessage(
                                                                        connection,
                                                                        message);
                                                }

                                                handleBackendClosed(
                                                                connection);

                                        } catch (Exception e) {

                                                System.out.println(
                                                                "[BRIDGE] Backend reader error: "
                                                                                + e.getMessage());

                                                handleBackendClosed(
                                                                connection);
                                        }

                                });

                readerThread.setName(
                                "Bridge-BackendReader-"
                                                + connection.getServerId());

                readerThread.start();
        }

        // =========================================================
        // HANDLE BACKEND MESSAGE
        // =========================================================

        private void handleBackendMessage(
                        BackendSocketConnection connection,
                        String message) {

                System.out.println(
                                "[BRIDGE] "
                                                + connection.getServerId()
                                                + " -> React: "
                                                + message);

                // -----------------------------------------------------
                // SERVER LOAD
                // -----------------------------------------------------

                if (message.startsWith(
                                "SERVER_LOAD:")) {

                        sendJson(
                                        "{\"type\":\"server_load\","
                                                        + "\"server\":\""
                                                        + escapeJson(
                                                                        connection.getServerId())
                                                        + "\","
                                                        + "\"message\":\""
                                                        + escapeJson(message)
                                                        + "\"}");

                        return;
                }

                // -----------------------------------------------------
                // AUTH REQUEST
                // -----------------------------------------------------

                if (message.equals(
                                "AUTH_REQUEST")) {

                        connection.sendMessage(
                                        "login");

                        return;
                }

                // -----------------------------------------------------
                // USERNAME REQUEST
                // -----------------------------------------------------

                if (message.equals(
                                "USERNAME_REQUEST")) {

                        connection.sendMessage(
                                        username);

                        return;
                }

                // -----------------------------------------------------
                // PASSWORD REQUEST
                // -----------------------------------------------------

                if (message.equals(
                                "PASSWORD_REQUEST")) {

                        connection.sendMessage(
                                        password);

                        return;
                }

                // -----------------------------------------------------
                // LOGIN SUCCESS
                // -----------------------------------------------------

                if (message.startsWith(
                                "LOGIN_SUCCESS:")) {

                        boolean reconnecting = failoverInProgress.get();

                        authenticated.set(true);

                        // ---------------------------------------------------------
                        // Register browser WebSocket again
                        // ---------------------------------------------------------

                        WebSocketBridgeServer.registerUserSession(
                                        username,
                                        this);

                        System.out.println(
                                        "[OFFLINE-FILE] Browser session registered for "
                                                        + username);

                        // ---------------------------------------------------------
                        // Browser is ready to receive offline files.
                        // ---------------------------------------------------------

                        sendToBackend(
                                        "/deliverofflinefiles");

                        // ---------------------------------------------------------
                        // Reconnection success
                        // ---------------------------------------------------------

                        if (reconnecting) {

                                failoverInProgress.set(false);

                                sendJson(
                                                "{\"type\":\"server_reconnected\","
                                                                + "\"username\":\""
                                                                + escapeJson(username)
                                                                + "\","
                                                                + "\"server\":\""
                                                                + escapeJson(currentServerId)
                                                                + "\","
                                                                + "\"message\":\"Automatically reconnected to backup server\"}");

                                System.out.println();
                                System.out.println(
                                                "[FAILOVER] ==========================================");
                                System.out.println(
                                                "[FAILOVER] FAILOVER SUCCESSFUL");
                                System.out.println(
                                                "[FAILOVER] User: "
                                                                + username);
                                System.out.println(
                                                "[FAILOVER] Connected server: "
                                                                + currentServerId);
                                System.out.println(
                                                "[FAILOVER] ==========================================");

                        } else {

                                // -------------------------------------------------
                                // Normal initial login
                                // -------------------------------------------------

                                sendJson(
                                                "{\"type\":\"login_success\","
                                                                + "\"username\":\""
                                                                + escapeJson(username)
                                                                + "\","
                                                                + "\"server\":\""
                                                                + escapeJson(currentServerId)
                                                                + "\","
                                                                + "\"message\":\""
                                                                + escapeJson(message)
                                                                + "\"}");
                        }

                        return;
                }

                // -----------------------------------------------------
                // LOGIN FAILED
                // -----------------------------------------------------

                if (message.startsWith(
                                "LOGIN_FAILED:")
                                || message.startsWith(
                                                "ERROR: Username already online.")) {

                        authenticated.set(false);

                        String errorMessage;

                        if (message.startsWith(
                                        "LOGIN_FAILED:")) {

                                errorMessage = message.substring(
                                                "LOGIN_FAILED:"
                                                                .length())
                                                .trim();

                        } else {

                                errorMessage = "User is already logged in.";
                        }

                        sendJson(
                                        "{\"type\":\"login_failed\","
                                                        + "\"message\":\""
                                                        + escapeJson(errorMessage)
                                                        + "\"}");

                        return;
                }

                // -----------------------------------------------------
                // SERVER REDIRECT
                // -----------------------------------------------------

                if (message.startsWith(
                                "CONNECT_SERVER:")) {

                        handleServerRedirect(
                                        message);

                        return;
                }

                // -----------------------------------------------------
                // NORMAL BACKEND MESSAGE
                // -----------------------------------------------------

                sendJson(
                                "{\"type\":\"message\","
                                                + "\"server\":\""
                                                + escapeJson(
                                                                connection.getServerId())
                                                + "\","
                                                + "\"message\":\""
                                                + escapeJson(message)
                                                + "\"}");
        }

        // =========================================================
        // SERVER REDIRECT
        // =========================================================

        private void handleServerRedirect(
                        String message) {

                try {

                        String data = message.substring(
                                        "CONNECT_SERVER:"
                                                        .length());

                        String[] parts = data.split(":");

                        if (parts.length < 2) {

                                sendError(
                                                "Invalid server redirect");

                                return;
                        }

                        String host = parts[0];

                        int port = Integer.parseInt(
                                        parts[1]);

                        String serverId = port == 5000
                                        ? "SERVER_1"
                                        : "SERVER_2";

                        System.out.println(
                                        "[BRIDGE] Backend requested server switch -> "
                                                        + serverId);

                        sendJson(
                                        "{\"type\":\"server_switching\","
                                                        + "\"server\":\""
                                                        + serverId
                                                        + "\"}");

                        switchingServer.set(true);

                        if (backendConnection != null) {

                                backendConnection.close();
                        }

                        Thread switchThread = new Thread(
                                        () -> {

                                                boolean connected = connectToServer(
                                                                serverId,
                                                                host,
                                                                port);

                                                if (!connected) {

                                                        sendError(
                                                                        "Unable to connect to "
                                                                                        + serverId);
                                                }

                                        });

                        switchThread.setName(
                                        "Bridge-Server-Switch");

                        switchThread.start();

                } catch (Exception e) {

                        sendError(
                                        "Server switch failed: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // BACKEND CLOSED
        // =========================================================

        private void handleBackendClosed(
                        BackendSocketConnection connection) {

                // ---------------------------------------------------------
                // Ignore old connection if another backend connection
                // has already replaced it.
                // ---------------------------------------------------------

                if (connection != backendConnection) {

                        System.out.println(
                                        "[FAILOVER] Ignoring closed old connection: "
                                                        + connection.getServerId());

                        return;
                }

                // ---------------------------------------------------------
                // If this close happened because we intentionally switched
                // servers, do not start another failover.
                // ---------------------------------------------------------

                if (switchingServer.get()) {

                        System.out.println(
                                        "[FAILOVER] Backend closed during "
                                                        + "intentional server switch.");

                        return;
                }

                String failedServer = connection.getServerId();

                System.out.println();
                System.out.println(
                                "==================================================");
                System.out.println(
                                "[FAILOVER] Backend server connection lost");
                System.out.println(
                                "[FAILOVER] Failed server: "
                                                + failedServer);
                System.out.println(
                                "[FAILOVER] User: "
                                                + username);
                System.out.println(
                                "[FAILOVER] Starting automatic failover...");
                System.out.println(
                                "==================================================");

                authenticated.set(false);

                failoverInProgress.set(true);

                // ---------------------------------------------------------
                // Remove old user mapping temporarily.
                // It will be registered again after successful login
                // on the new backend server.
                // ---------------------------------------------------------

                WebSocketBridgeServer.unregisterUserSession(
                                username,
                                this);

                // ---------------------------------------------------------
                // Notify React that failover has started.
                // IMPORTANT:
                // Do NOT close the browser WebSocket.
                // ---------------------------------------------------------

                sendJson(
                                "{\"type\":\"server_failover_start\","
                                                + "\"failedServer\":\""
                                                + escapeJson(
                                                                failedServer)
                                                + "\","
                                                + "\"message\":\"Switching to backup server...\"}");

                // ---------------------------------------------------------
                // Start failover in another thread.
                // ---------------------------------------------------------

                Thread failoverThread = new Thread(
                                () -> {

                                        boolean switched = connectToBackupServer(
                                                        failedServer);

                                        if (!switched) {

                                                sendError(
                                                                "Both CloudChat servers are unavailable");

                                                sendJson(
                                                                "{\"type\":\"server_disconnected\","
                                                                                + "\"server\":\""
                                                                                + escapeJson(
                                                                                                failedServer)
                                                                                + "\","
                                                                                + "\"message\":\"Unable to reconnect to a CloudChat server\"}");
                                        }
                                });

                failoverThread.setName(
                                "Bridge-Auto-Failover-"
                                                + username);

                failoverThread.start();
        }

        // =========================================================
        // CONNECT TO BACKUP SERVER
        // =========================================================

        private boolean connectToBackupServer(
                        String failedServer) {

                String backupServerId;

                String backupHost = "localhost";

                int backupPort;

                // ---------------------------------------------------------
                // Determine backup server
                // ---------------------------------------------------------

                if ("SERVER_1".equals(
                                failedServer)) {

                        backupServerId = "SERVER_2";
                        backupPort = 5002;

                } else {

                        backupServerId = "SERVER_1";
                        backupPort = 5000;
                }

                System.out.println();
                System.out.println(
                                "[FAILOVER] Trying backup server: "
                                                + backupServerId
                                                + " "
                                                + backupHost
                                                + ":"
                                                + backupPort);

                switchingServer.set(true);

                try {

                        BackendSocketConnection connection = new BackendSocketConnection(
                                        backupServerId,
                                        backupHost,
                                        backupPort);

                        if (!connection.connect()) {

                                System.out.println(
                                                "[FAILOVER] Unable to connect to "
                                                                + backupServerId);

                                return false;
                        }

                        // -------------------------------------------------
                        // Replace backend connection
                        // -------------------------------------------------

                        backendConnection = connection;

                        currentServerId = backupServerId;

                        authenticated.set(false);

                        // -------------------------------------------------
                        // Start backend reader
                        // -------------------------------------------------

                        startBackendReader(
                                        connection);

                        System.out.println(
                                        "[FAILOVER] Backup connection established: "
                                                        + backupServerId);

                        sendJson(
                                        "{\"type\":\"server_switching\","
                                                        + "\"server\":\""
                                                        + escapeJson(
                                                                        backupServerId)
                                                        + "\","
                                                        + "\"message\":\"Connected to backup server. Re-authenticating...\"}");

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[FAILOVER] Backup connection failed: "
                                                        + e.getMessage());

                        return false;

                } finally {

                        switchingServer.set(false);
                }
        }

        // =========================================================
        // SEND TO BACKEND
        // =========================================================

        private void sendToBackend(
                        String message) {

                if (backendConnection == null
                                || !backendConnection.isConnected()) {

                        sendError(
                                        "No chat server connection");

                        return;
                }

                backendConnection.sendMessage(
                                message);
        }

        // =========================================================
        // CLOSE SESSION
        // =========================================================

        public void close() {

                System.out.println(
                                "[BRIDGE] Closing session for user: "
                                                + username);

                // -----------------------------------------------------
                // Remove browser user mapping
                // -----------------------------------------------------

                WebSocketBridgeServer.unregisterUserSession(
                                username,
                                this);

                // -----------------------------------------------------
                // Clean unfinished upload
                // -----------------------------------------------------

                cleanupFileUpload();

                // -----------------------------------------------------
                // Close backend
                // -----------------------------------------------------

                try {

                        if (backendConnection != null) {

                                System.out.println(
                                                "[BRIDGE] Closing backend connection for: "
                                                                + username);

                                backendConnection.close();

                                backendConnection = null;
                        }

                } catch (Exception e) {

                        System.out.println(
                                        "[BRIDGE] Error closing backend connection: "
                                                        + e.getMessage());
                }

                // -----------------------------------------------------
                // Close React WebSocket
                // -----------------------------------------------------

                try {

                        if (webSocket != null
                                        && webSocket.isOpen()) {

                                webSocket.close();
                        }

                } catch (Exception e) {

                        System.out.println(
                                        "[BRIDGE] Error closing React WebSocket: "
                                                        + e.getMessage());
                }

                authenticated.set(false);
        }

        // =========================================================
        // JSON VALUE EXTRACTION
        // =========================================================

        private String extractJsonValue(
                        String json,
                        String key) {

                String search = "\"" + key + "\"";

                int keyIndex = json.indexOf(search);

                if (keyIndex == -1) {
                        return null;
                }

                int colonIndex = json.indexOf(
                                ":",
                                keyIndex);

                if (colonIndex == -1) {
                        return null;
                }

                int firstQuote = json.indexOf(
                                "\"",
                                colonIndex);

                if (firstQuote == -1) {
                        return null;
                }

                int secondQuote = json.indexOf(
                                "\"",
                                firstQuote + 1);

                if (secondQuote == -1) {
                        return null;
                }

                return json.substring(
                                firstQuote + 1,
                                secondQuote);
        }

        // =========================================================
        // JSON NUMBER EXTRACTION
        // =========================================================

        private String extractJsonNumber(
                        String json,
                        String key) {

                String search = "\"" + key + "\"";

                int keyIndex = json.indexOf(search);

                if (keyIndex == -1) {
                        return null;
                }

                int colonIndex = json.indexOf(
                                ":",
                                keyIndex);

                if (colonIndex == -1) {
                        return null;
                }

                int start = colonIndex + 1;

                while (start < json.length()
                                && Character.isWhitespace(
                                                json.charAt(start))) {

                        start++;
                }

                int end = start;

                while (end < json.length()
                                && Character.isDigit(
                                                json.charAt(end))) {

                        end++;
                }

                if (start == end) {
                        return null;
                }

                return json.substring(
                                start,
                                end);
        }

        // =========================================================
        // SEND JSON
        // =========================================================

        private synchronized void sendJson(
                        String json) {

                if (webSocket == null) {
                        return;
                }

                if (!webSocket.isOpen()) {
                        return;
                }

                webSocket.send(json);
        }

        // =========================================================
        // SEND ERROR
        // =========================================================

        private void sendError(
                        String message) {

                sendJson(
                                "{\"type\":\"error\","
                                                + "\"message\":\""
                                                + escapeJson(message)
                                                + "\"}");
        }

        // =========================================================
        // JSON ESCAPE
        // =========================================================

        private String escapeJson(
                        String value) {

                if (value == null) {
                        return "";
                }

                return value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\r", "\\r")
                                .replace("\n", "\\n");
        }
}