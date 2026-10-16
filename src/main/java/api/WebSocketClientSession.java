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
import java.util.UUID;

public class WebSocketClientSession {

        private final WebSocket webSocket;

        private BackendSocketConnection backendConnection;

        private String username;
        private String password;

        // =========================================================
        // GOOGLE AUTHENTICATION STATE
        // =========================================================

        private String googleTicket;
        private String googleSessionId;

        private boolean googleAuthentication = false;

        private String currentServerId;

        private final AtomicBoolean authenticated = new AtomicBoolean(false);

        private final AtomicBoolean switchingServer = new AtomicBoolean(false);

        private final AtomicBoolean failoverInProgress = new AtomicBoolean(false);

        // =========================================================
        // FILE UPLOAD STATE
        // =========================================================

        private File uploadFile;

        // =========================================================
        // GROUP FILE UPLOAD STATE
        // =========================================================

        private File groupUploadFile;

        private FileOutputStream groupUploadOutputStream;

        private String groupUploadGroupName;

        private String groupUploadFileName;

        private String groupUploadTransferId;

        private long groupUploadExpectedSize;

        private long groupUploadReceivedSize;

        private boolean groupFileUploadInProgress = false;

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
                // GOOGLE LOGIN REQUEST
                // -----------------------------------------------------

                if (message.startsWith("{")
                                && message.contains("\"type\":\"google_login\"")) {

                        handleGoogleLoginRequest(message);

                        return;
                }

                // -----------------------------------------------------
                // LOGIN REQUEST
                // -----------------------------------------------------

                if (message.startsWith("{")
                                && message.contains("\"type\":\"login\"")) {

                        handleLoginRequest(message);

                        return;
                }

                // -----------------------------------------------------
                // GROUP FILE START REQUEST
                // -----------------------------------------------------

                if (message.startsWith("{")
                                && message.contains("\"type\":\"group_file_start\"")) {

                        handleGroupFileStart(message);

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
        // HANDLE GROUP FILE START
        // =========================================================

        private synchronized void handleGroupFileStart(
                        String json) {

                if (!authenticated.get()) {

                        sendError(
                                        "Not authenticated");

                        return;
                }

                if (groupFileUploadInProgress) {

                        sendGroupFileError(
                                        "Another group file upload is already in progress.");

                        return;
                }

                String groupName = extractJsonValue(
                                json,
                                "groupName");

                String fileName = extractJsonValue(
                                json,
                                "fileName");

                String fileSizeText = extractJsonNumber(
                                json,
                                "fileSize");

                // -----------------------------------------------------
                // Validate group name
                // -----------------------------------------------------

                if (groupName == null
                                || groupName.trim().isEmpty()) {

                        sendGroupFileError(
                                        "Group name is required.");

                        return;
                }

                // -----------------------------------------------------
                // Validate file name
                // -----------------------------------------------------

                if (fileName == null
                                || fileName.trim().isEmpty()) {

                        sendGroupFileError(
                                        "File name is required.");

                        return;
                }

                // -----------------------------------------------------
                // Validate file size
                // -----------------------------------------------------

                if (fileSizeText == null
                                || fileSizeText.trim().isEmpty()) {

                        sendGroupFileError(
                                        "File size is required.");

                        return;
                }

                long fileSize;

                try {

                        fileSize = Long.parseLong(
                                        fileSizeText.trim());

                } catch (NumberFormatException e) {

                        sendGroupFileError(
                                        "Invalid file size.");

                        return;
                }

                if (fileSize <= 0) {

                        sendGroupFileError(
                                        "File cannot be empty.");

                        return;
                }

                if (fileSize > MAX_FILE_SIZE) {

                        sendGroupFileError(
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

                        sendGroupFileError(
                                        "Invalid file name.");

                        return;
                }

                if (safeFileName.isEmpty()) {

                        sendGroupFileError(
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
                                        "cloudchat-group-uploads");

                        Files.createDirectories(
                                        uploadDirectory);

                        Path temporaryPath = Files.createTempFile(
                                        uploadDirectory,
                                        "cloudchat-group-",
                                        "-" + safeFileName);

                        groupUploadFile = temporaryPath.toFile();

                        groupUploadOutputStream = new FileOutputStream(
                                        groupUploadFile);

                } catch (IOException e) {

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] "
                                                        + "Unable to create temporary file: "
                                                        + e.getMessage());

                        sendGroupFileError(
                                        "Unable to prepare group file upload.");

                        cleanupGroupFileUpload();

                        return;
                }

                // -----------------------------------------------------
                // Store upload state
                // -----------------------------------------------------

                groupUploadGroupName = groupName.trim();

                groupUploadFileName = safeFileName;

                groupUploadExpectedSize = fileSize;

                groupUploadReceivedSize = 0;

                groupFileUploadInProgress = true;

                groupUploadTransferId = UUID.randomUUID().toString();

                System.out.println();
                System.out.println(
                                "[BRIDGE-GROUP-FILE] Upload started");

                System.out.println(
                                "[BRIDGE-GROUP-FILE] Sender: "
                                                + username);

                System.out.println(
                                "[BRIDGE-GROUP-FILE] Group: "
                                                + groupUploadGroupName);

                System.out.println(
                                "[BRIDGE-GROUP-FILE] File: "
                                                + groupUploadFileName);

                System.out.println(
                                "[BRIDGE-GROUP-FILE] Expected size: "
                                                + groupUploadExpectedSize);

                // -----------------------------------------------------
                // Notify React that upload has STARTED
                // -----------------------------------------------------

                sendJson(
                                "{\"type\":\"group_file_upload_started\","
                                                + "\"groupName\":\""
                                                + escapeJson(groupUploadGroupName)
                                                + "\","
                                                + "\"fileName\":\""
                                                + escapeJson(groupUploadFileName)
                                                + "\","
                                                + "\"fileSize\":"
                                                + groupUploadExpectedSize
                                                + "}");
        }

        // =========================================================
        // HANDLE BINARY FILE DATA FROM SENDER
        // =========================================================

        public synchronized void handleBinaryMessage(
                        ByteBuffer bytes) {

                // -----------------------------------------------------
                // GROUP FILE UPLOAD
                // -----------------------------------------------------

                if (groupFileUploadInProgress) {

                        handleGroupFileBinaryData(bytes);

                        return;
                }

                // -----------------------------------------------------
                // PRIVATE FILE UPLOAD
                // -----------------------------------------------------

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
        // HANDLE GROUP FILE BINARY DATA
        // =========================================================

        private synchronized void handleGroupFileBinaryData(
                        ByteBuffer bytes) {

                if (!groupFileUploadInProgress
                                || groupUploadOutputStream == null) {

                        sendGroupFileError(
                                        "No group file upload is currently active.");

                        return;
                }

                if (bytes == null) {

                        sendGroupFileError(
                                        "Empty binary data.");

                        return;
                }

                int remaining = bytes.remaining();

                if (remaining <= 0) {
                        return;
                }

                if (groupUploadReceivedSize
                                + remaining > groupUploadExpectedSize) {

                        sendGroupFileError(
                                        "Received more data than expected.");

                        cleanupGroupFileUpload();

                        return;
                }

                try {

                        byte[] buffer = new byte[remaining];

                        bytes.get(buffer);

                        groupUploadOutputStream.write(
                                        buffer);

                        groupUploadReceivedSize += remaining;

                        if (groupUploadReceivedSize == groupUploadExpectedSize) {

                                finishGroupFileUpload();
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] File write failed: "
                                                        + e.getMessage());

                        sendGroupFileError(
                                        "Unable to save uploaded group file.");

                        cleanupGroupFileUpload();
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
        // FINISH GROUP FILE UPLOAD
        // =========================================================

        private synchronized void finishGroupFileUpload() {

                try {

                        if (groupUploadOutputStream != null) {

                                groupUploadOutputStream.flush();

                                groupUploadOutputStream.close();

                                groupUploadOutputStream = null;
                        }

                        // -----------------------------------------------------
                        // Validate uploaded file
                        // -----------------------------------------------------

                        if (groupUploadFile == null
                                        || !groupUploadFile.exists()) {

                                sendGroupFileError(
                                                "Uploaded group file could not be found.");

                                cleanupGroupFileUpload();

                                return;
                        }

                        if (groupUploadFile.length() != groupUploadExpectedSize) {

                                sendGroupFileError(
                                                "Uploaded group file size does not match.");

                                cleanupGroupFileUpload();

                                return;
                        }

                        System.out.println();
                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] Upload completed");

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] Sender: "
                                                        + username);

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] Group: "
                                                        + groupUploadGroupName);

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] File: "
                                                        + groupUploadFile.getAbsolutePath());

                        // -----------------------------------------------------
                        // Send to backend
                        // -----------------------------------------------------

                        String backendCommand = "/groupfile "
                                        + groupUploadGroupName
                                        + " "
                                        + groupUploadFile.getAbsolutePath();

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] Sending backend command: "
                                                        + backendCommand);

                        sendToBackend(backendCommand);

                        // -----------------------------------------------------
                        // Notify React that upload is complete
                        // -----------------------------------------------------

                        sendJson(
                                        "{\"type\":\"group_file_upload_complete\","
                                                        + "\"groupName\":\""
                                                        + escapeJson(
                                                                        groupUploadGroupName)
                                                        + "\","
                                                        + "\"fileName\":\""
                                                        + escapeJson(
                                                                        groupUploadFileName)
                                                        + "\","
                                                        + "\"fileSize\":"
                                                        + groupUploadExpectedSize
                                                        + "}");

                } catch (Exception e) {

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] Upload completion failed: "
                                                        + e.getMessage());

                        sendGroupFileError(
                                        "Unable to process uploaded group file.");

                        cleanupGroupFileUpload();

                        return;
                }

                // -----------------------------------------------------
                // Do NOT delete the temporary file here.
                // ChatServer still needs it for group delivery.
                // -----------------------------------------------------

                resetGroupFileUploadState();
        }

        // =========================================================
        // SEND GROUP FILE TO RECIPIENT BROWSER
        // =========================================================

        public synchronized void sendGroupFileToBrowser(
                        String sender,
                        String groupName,
                        File file,
                        String originalFileName,
                        String transferId) throws IOException {

                if (webSocket == null || !webSocket.isOpen()) {
                        throw new IOException("Recipient WebSocket is not open.");
                }

                if (file == null || !file.exists() || !file.isFile()) {
                        throw new IOException("Group file does not exist.");
                }

                if (groupName == null || groupName.trim().isEmpty()) {
                        throw new IOException("Group name is empty.");
                }

                if (transferId == null || transferId.trim().isEmpty()) {
                        throw new IOException("Group file transfer ID is missing.");
                }

                long fileSize = file.length();

                String fileName = (originalFileName == null || originalFileName.trim().isEmpty())
                                ? file.getName()
                                : originalFileName.trim();

                sendJson(
                                "{\"type\":\"group_file_delivery_started\","
                                                + "\"transferId\":\"" + escapeJson(transferId) + "\","
                                                + "\"sender\":\"" + escapeJson(sender) + "\","
                                                + "\"groupName\":\"" + escapeJson(groupName) + "\","
                                                + "\"fileName\":\"" + escapeJson(fileName) + "\","
                                                + "\"fileSize\":" + fileSize
                                                + "}");

                long totalSent = 0;

                try (FileInputStream input = new FileInputStream(file)) {
                        byte[] buffer = new byte[FILE_CHUNK_SIZE];
                        int bytesRead;

                        while ((bytesRead = input.read(buffer)) != -1) {
                                if (webSocket == null || !webSocket.isOpen()) {
                                        throw new IOException("Recipient WebSocket disconnected.");
                                }

                                byte[] chunk = new byte[bytesRead];
                                System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                                webSocket.send(ByteBuffer.wrap(chunk));
                                totalSent += bytesRead;
                        }
                }

                sendJson(
                                "{\"type\":\"group_file_delivery_complete\","
                                                + "\"transferId\":\"" + escapeJson(transferId) + "\","
                                                + "\"sender\":\"" + escapeJson(sender) + "\","
                                                + "\"groupName\":\"" + escapeJson(groupName) + "\","
                                                + "\"fileName\":\"" + escapeJson(fileName) + "\","
                                                + "\"fileSize\":" + fileSize
                                                + "}");

                System.out.println(
                                "[WS-GROUP-FILE] Transfer " + transferId
                                                + " delivered to " + username
                                                + " (" + totalSent + " bytes).");
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
        // HANDLE GOOGLE LOGIN
        // =========================================================

        private void handleGoogleLoginRequest(String json) {

                String requestedUsername = extractJsonValue(json, "username");
                String requestedTicket = extractJsonValue(json, "ticket");
                String requestedSessionId = extractJsonValue(json, "sessionId");

                if (requestedUsername == null
                                || requestedUsername.trim().isEmpty()
                                || requestedTicket == null
                                || requestedTicket.trim().isEmpty()
                                || requestedSessionId == null
                                || requestedSessionId.trim().isEmpty()) {

                        sendError("Google login information is incomplete.");
                        return;
                }

                username = requestedUsername.trim();
                googleTicket = requestedTicket.trim();
                googleSessionId = requestedSessionId.trim();

                password = null;
                googleAuthentication = true;

                sendJson(
                                "{\"type\":\"login_start\","
                                                + "\"message\":\"Connecting with Google...\"}");

                connectToBestServer();
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

                // Normal password login — not Google login
                googleAuthentication = false;

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

                if (message.equals("AUTH_REQUEST")) {
                        connection.sendMessage(
                                        googleAuthentication ? "google_ticket" : "login");
                        return;
                }

                if (message.equals("GOOGLE_TICKET_REQUEST")) {
                        if (googleTicket == null || googleTicket.isBlank()) {
                                connection.sendMessage("");
                        } else {
                                connection.sendMessage(googleTicket);
                        }
                        return;
                }

                if (message.equals("GOOGLE_SESSION_REQUEST")) {
                        if (googleSessionId == null || googleSessionId.isBlank()) {
                                connection.sendMessage("");
                        } else {
                                connection.sendMessage(googleSessionId);
                        }
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
                // Clean unfinished uploads
                // -----------------------------------------------------

                cleanupFileUpload();

                cleanupGroupFileUpload();

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
        // GROUP FILE ERROR
        // =========================================================

        private synchronized void sendGroupFileError(
                        String message) {

                sendJson(
                                "{\"type\":\"group_file_upload_error\","
                                                + "\"message\":\""
                                                + escapeJson(message)
                                                + "\"}");
        }

        // =========================================================
        // CLEANUP GROUP FILE UPLOAD
        // =========================================================

        private synchronized void cleanupGroupFileUpload() {

                try {

                        if (groupUploadOutputStream != null) {

                                groupUploadOutputStream.close();
                        }

                } catch (Exception ignored) {
                }

                groupUploadOutputStream = null;

                deleteTemporaryGroupUpload();

                resetGroupFileUploadState();
        }

        // =========================================================
        // DELETE TEMPORARY GROUP UPLOAD
        // =========================================================

        private void deleteTemporaryGroupUpload() {

                if (groupUploadFile == null) {
                        return;
                }

                try {

                        if (groupUploadFile.exists()) {

                                boolean deleted = groupUploadFile.delete();

                                System.out.println(
                                                "[BRIDGE-GROUP-FILE] "
                                                                + "Temporary file deleted: "
                                                                + deleted);
                        }

                } catch (Exception e) {

                        System.out.println(
                                        "[BRIDGE-GROUP-FILE] "
                                                        + "Unable to delete temporary file: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // RESET GROUP FILE UPLOAD STATE
        // =========================================================

        private void resetGroupFileUploadState() {

                groupUploadFile = null;

                groupUploadGroupName = null;

                groupUploadFileName = null;

                groupUploadExpectedSize = 0;

                groupUploadReceivedSize = 0;

                groupFileUploadInProgress = false;
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