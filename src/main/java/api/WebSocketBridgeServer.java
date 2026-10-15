package api;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketBridgeServer extends WebSocketServer {

        // =========================================================
        // ACTIVE WEBSOCKET SESSIONS
        // =========================================================

        private final Map<WebSocket, WebSocketClientSession> sessions = new ConcurrentHashMap<>();

        // =========================================================
        // USER -> WEBSOCKET SESSION
        // =========================================================

        /*
         * This map belongs to the WebSocket Bridge JVM.
         *
         * Example:
         *
         * "Pranav" -> WebSocketClientSession
         *
         * Server 1 / Server 2 cannot directly access this map
         * when they are running in separate JVM processes.
         *
         * Therefore sendFileToUser() falls back to the REST
         * API bridge when the session is not available locally.
         */

        private static final Map<String, WebSocketClientSession> userSessions = new ConcurrentHashMap<>();

        // =========================================================
        // INTERNAL REST BRIDGE
        // =========================================================

        private static final String INTERNAL_FILE_API = "http://localhost:9000/internal/files/deliver";

        private static final String INTERNAL_GROUP_FILE_API = "http://localhost:9000/internal/group-files/deliver";

        private static final int INTERNAL_CONNECT_TIMEOUT = 5000;

        private static final int INTERNAL_READ_TIMEOUT = 300000;

        // =========================================================
        // CONSTRUCTOR
        // =========================================================

        public WebSocketBridgeServer(int port) {

                super(new InetSocketAddress(port));
        }

        // =========================================================
        // CLIENT CONNECTED
        // =========================================================

        @Override
        public void onOpen(
                        WebSocket connection,
                        ClientHandshake handshake) {

                System.out.println(
                                "[WS-BRIDGE] React client connected: "
                                                + connection.getRemoteSocketAddress());

                WebSocketClientSession session = new WebSocketClientSession(connection);

                sessions.put(
                                connection,
                                session);

                session.start();
        }

        // =========================================================
        // TEXT MESSAGE FROM REACT
        // =========================================================

        @Override
        public void onMessage(
                        WebSocket connection,
                        String message) {

                WebSocketClientSession session = sessions.get(connection);

                if (session == null) {

                        System.out.println(
                                        "[WS-BRIDGE] Session not found");

                        return;
                }

                session.handleClientMessage(message);
        }

        // =========================================================
        // BINARY MESSAGE FROM REACT
        // =========================================================

        /*
         * Used when React uploads a file to the bridge.
         */

        @Override
        public void onMessage(
                        WebSocket connection,
                        java.nio.ByteBuffer bytes) {

                WebSocketClientSession session = sessions.get(connection);

                if (session == null) {

                        System.out.println(
                                        "[WS-BRIDGE] Binary message received "
                                                        + "but session was not found.");

                        return;
                }

                session.handleBinaryMessage(bytes);
        }

        // =========================================================
        // REGISTER USER SESSION
        // =========================================================

        public static void registerUserSession(
                        String username,
                        WebSocketClientSession session) {

                if (username == null
                                || username.trim().isEmpty()
                                || session == null) {

                        return;
                }

                String cleanUsername = username.trim();

                userSessions.put(
                                cleanUsername,
                                session);

                System.out.println(
                                "[WS-BRIDGE] User session registered: "
                                                + cleanUsername);

                System.out.println(
                                "[WS-BRIDGE] Active browser users: "
                                                + userSessions.keySet());
        }

        // =========================================================
        // REMOVE USER SESSION
        // =========================================================

        public static void unregisterUserSession(
                        String username,
                        WebSocketClientSession session) {

                if (username == null
                                || username.trim().isEmpty()) {

                        return;
                }

                String cleanUsername = username.trim();

                if (session == null) {

                        userSessions.remove(
                                        cleanUsername);

                } else {

                        userSessions.remove(
                                        cleanUsername,
                                        session);
                }

                System.out.println(
                                "[WS-BRIDGE] User session removed: "
                                                + cleanUsername);
        }

        // =========================================================
        // SEND FILE TO BROWSER USER
        // =========================================================

        /*
         * This method is still called by ClientHandler:
         *
         * WebSocketBridgeServer.sendFileToUser(
         * recipient,
         * sender,
         * file
         * );
         *
         * If ClientHandler is running in the SAME JVM as the
         * WebSocket Bridge, the local userSessions map is used.
         *
         * If ClientHandler is running in another JVM, the local
         * map will not contain the browser session. In that case
         * we call the REST API on port 9000.
         */

        public static boolean sendFileToUser(
                        String recipient,
                        String sender,
                        File file,
                        String originalFileName) {

                if (recipient == null
                                || recipient.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-FILE] Recipient is empty.");

                        return false;
                }

                if (sender == null
                                || sender.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-FILE] Sender is empty.");

                        return false;
                }

                if (file == null
                                || !file.exists()
                                || !file.isFile()) {

                        System.out.println(
                                        "[WS-FILE] File does not exist.");

                        return false;
                }

                String cleanRecipient = recipient.trim();

                String cleanFileName = (originalFileName == null
                                || originalFileName.trim().isEmpty())
                                                ? file.getName()
                                                : originalFileName.trim();

                // =====================================================
                // FIRST: TRY LOCAL WEBSOCKET SESSION
                // =====================================================

                WebSocketClientSession session = userSessions.get(cleanRecipient);

                if (session != null) {

                        System.out.println(
                                        "[WS-FILE] Local WebSocket session found for: "
                                                        + cleanRecipient);

                        return sendFileUsingSession(
                                        session,
                                        cleanRecipient,
                                        sender,
                                        file,
                                        cleanFileName);
                }

                // =====================================================
                // SECOND: TRY REST BRIDGE
                // =====================================================

                System.out.println(
                                "[WS-FILE] No local WebSocket session found for: "
                                                + cleanRecipient);

                System.out.println(
                                "[WS-FILE] Trying REST bridge: "
                                                + INTERNAL_FILE_API);

                return requestFileDeliveryThroughApi(
                                cleanRecipient,
                                sender,
                                file,
                                cleanFileName);
        }

        // =========================================================
        // SEND GROUP FILE TO BROWSER USER
        // =========================================================
        //
        // This is separate from private-file delivery.
        //
        // Group file delivery includes:
        // - sender
        // - group name
        // - file
        // - original file name
        //
        // The actual browser transfer still uses the SAME
        // WebSocketClientSession / existing WebSocket connection.
        //

        public static boolean sendGroupFileToUser(
                        String recipient,
                        String sender,
                        String groupName,
                        File file,
                        String originalFileName,
                        String transferId) {

                if (recipient == null
                                || recipient.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Recipient is empty.");

                        return false;
                }

                if (sender == null
                                || sender.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Sender is empty.");

                        return false;
                }

                if (groupName == null
                                || groupName.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Group name is empty.");

                        return false;
                }

                if (file == null
                                || !file.exists()
                                || !file.isFile()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] File does not exist.");

                        return false;
                }

                String cleanRecipient = recipient.trim();
                String cleanGroupName = groupName.trim();

                String cleanFileName = (originalFileName == null
                                || originalFileName.trim().isEmpty())
                                                ? file.getName()
                                                : originalFileName.trim();

                // =====================================================
                // FIRST: TRY LOCAL WEBSOCKET SESSION
                // =====================================================

                WebSocketClientSession session = userSessions.get(cleanRecipient);

                if (session != null) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Local WebSocket session found for: "
                                                        + cleanRecipient);

                        return sendGroupFileUsingSession(
                                        session,
                                        cleanRecipient,
                                        sender,
                                        cleanGroupName,
                                        file,
                                        cleanFileName,
                                        transferId);
                }

                // =====================================================
                // SECOND: USE INTERNAL REST BRIDGE
                // =====================================================

                System.out.println(
                                "[WS-GROUP-FILE] No local WebSocket session found for: "
                                                + cleanRecipient);

                System.out.println(
                                "[WS-GROUP-FILE] Group file requires REST bridge.");

                return requestGroupFileDeliveryThroughApi(
                                cleanRecipient,
                                sender,
                                cleanGroupName,
                                file,
                                cleanFileName,
                                transferId);
        }

        // =========================================================
        // ACTUAL GROUP FILE DELIVERY USING WEBSOCKET SESSION
        // =========================================================

        private static boolean sendGroupFileUsingSession(
                        WebSocketClientSession session,
                        String recipient,
                        String sender,
                        String groupName,
                        File file,
                        String originalFileName,
                        String transferId) {

                System.out.println();
                System.out.println(
                                "[WS-GROUP-FILE] =====================================");

                System.out.println(
                                "[WS-GROUP-FILE] Starting group file transfer");

                System.out.println(
                                "[WS-GROUP-FILE] Sender: "
                                                + sender);

                System.out.println(
                                "[WS-GROUP-FILE] Group: "
                                                + groupName);

                System.out.println(
                                "[WS-GROUP-FILE] Recipient: "
                                                + recipient);

                System.out.println(
                                "[WS-GROUP-FILE] File: "
                                                + file.getName());

                System.out.println(
                                "[WS-GROUP-FILE] Size: "
                                                + file.length()
                                                + " bytes");

                System.out.println(
                                "[WS-GROUP-FILE] =====================================");

                try {

                        session.sendGroupFileToBrowser(
                                        sender,
                                        groupName,
                                        file,
                                        originalFileName,
                                        transferId);

                        System.out.println(
                                        "[WS-GROUP-FILE] File handed to recipient WebSocket.");

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Browser group file transfer failed: "
                                                        + e.getMessage());

                        e.printStackTrace();

                        return false;
                }
        }

        // =========================================================
        // REQUEST GROUP FILE DELIVERY THROUGH REST API
        // =========================================================

        // =========================================================
        // REQUEST GROUP FILE DELIVERY THROUGH REST API
        // =========================================================

        private static boolean requestGroupFileDeliveryThroughApi(
                        String recipient,
                        String sender,
                        String groupName,
                        File file,
                        String originalFileName,
                        String transferId) {

                HttpURLConnection connection = null;

                try {

                        // -----------------------------------------------------
                        // CREATE HTTP CONNECTION
                        // -----------------------------------------------------

                        URL url = new URL(INTERNAL_GROUP_FILE_API);

                        connection = (HttpURLConnection) url.openConnection();

                        connection.setRequestMethod("POST");

                        connection.setDoOutput(true);

                        connection.setConnectTimeout(
                                        INTERNAL_CONNECT_TIMEOUT);

                        connection.setReadTimeout(
                                        INTERNAL_READ_TIMEOUT);

                        connection.setRequestProperty(
                                        "Content-Type",
                                        "application/x-www-form-urlencoded; charset=UTF-8");

                        // -----------------------------------------------------
                        // BUILD FORM DATA
                        // -----------------------------------------------------

                        String requestBody = "recipient="
                                        + URLEncoder.encode(
                                                        recipient,
                                                        StandardCharsets.UTF_8)

                                        + "&sender="
                                        + URLEncoder.encode(
                                                        sender,
                                                        StandardCharsets.UTF_8)

                                        + "&groupName="
                                        + URLEncoder.encode(
                                                        groupName,
                                                        StandardCharsets.UTF_8)

                                        + "&filePath="
                                        + URLEncoder.encode(
                                                        file.getAbsolutePath(),
                                                        StandardCharsets.UTF_8)

                                        + "&fileName="
                                        + URLEncoder.encode(
                                                        originalFileName,
                                                        StandardCharsets.UTF_8)

                                        + "&transferId="
                                        + URLEncoder.encode(transferId, StandardCharsets.UTF_8);

                        byte[] requestBytes = requestBody.getBytes(
                                        StandardCharsets.UTF_8);

                        // -----------------------------------------------------
                        // SEND REQUEST
                        // -----------------------------------------------------

                        System.out.println();
                        System.out.println(
                                        "[WS-GROUP-FILE] Sending internal REST delivery request.");

                        System.out.println(
                                        "[WS-GROUP-FILE] Recipient: "
                                                        + recipient);

                        System.out.println(
                                        "[WS-GROUP-FILE] Sender: "
                                                        + sender);

                        System.out.println(
                                        "[WS-GROUP-FILE] Group: "
                                                        + groupName);

                        System.out.println(
                                        "[WS-GROUP-FILE] File: "
                                                        + originalFileName);

                        try (OutputStream outputStream = connection.getOutputStream()) {

                                outputStream.write(requestBytes);
                                outputStream.flush();
                        }

                        // -----------------------------------------------------
                        // READ RESPONSE CODE
                        // -----------------------------------------------------

                        int responseCode = connection.getResponseCode();

                        System.out.println(
                                        "[WS-GROUP-FILE] REST bridge response code: "
                                                        + responseCode);

                        // -----------------------------------------------------
                        // READ RESPONSE BODY
                        // -----------------------------------------------------

                        InputStream responseStream;

                        if (responseCode >= 200 && responseCode < 300) {

                                responseStream = connection.getInputStream();

                        } else {

                                responseStream = connection.getErrorStream();
                        }

                        String responseBody = "";

                        if (responseStream != null) {

                                try (InputStream input = responseStream) {

                                        responseBody = new String(
                                                        input.readAllBytes(),
                                                        StandardCharsets.UTF_8);
                                }
                        }

                        System.out.println(
                                        "[WS-GROUP-FILE] REST bridge response body: "
                                                        + responseBody);

                        // -----------------------------------------------------
                        // CHECK HTTP STATUS
                        // -----------------------------------------------------

                        if (responseCode >= 200 && responseCode < 300) {

                                System.out.println(
                                                "[WS-GROUP-FILE] Internal API accepted the request.");

                                System.out.println(
                                                "[WS-GROUP-FILE] Note: HTTP success alone does not "
                                                                + "confirm browser receipt.");

                                return true;
                        }

                        // -----------------------------------------------------
                        // DELIVERY REQUEST FAILED
                        // -----------------------------------------------------

                        System.out.println(
                                        "[WS-GROUP-FILE] Internal API rejected the delivery request.");

                        return false;

                } catch (Exception e) {

                        System.out.println(
                                        "[WS-GROUP-FILE] REST bridge connection failed: "
                                                        + e.getMessage());

                        e.printStackTrace();

                        return false;

                } finally {

                        if (connection != null) {

                                connection.disconnect();
                        }
                }
        }

        // =========================================================
        // ACTUAL FILE DELIVERY USING WEBSOCKET SESSION
        // =========================================================

        private static boolean sendFileUsingSession(
                        WebSocketClientSession session,
                        String recipient,
                        String sender,
                        File file,
                        String originalFileName) {

                System.out.println();
                System.out.println(
                                "[WS-FILE] =====================================");

                System.out.println(
                                "[WS-FILE] Starting browser file transfer");

                System.out.println(
                                "[WS-FILE] Sender: "
                                                + sender);

                System.out.println(
                                "[WS-FILE] Recipient: "
                                                + recipient);

                System.out.println(
                                "[WS-FILE] File: "
                                                + file.getName());

                System.out.println(
                                "[WS-FILE] Size: "
                                                + file.length()
                                                + " bytes");

                System.out.println(
                                "[WS-FILE] =====================================");

                try {

                        session.sendFileToBrowser(
                                        sender,
                                        file);

                        System.out.println(
                                        "[WS-FILE] File handed to recipient WebSocket.");

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[WS-FILE] Browser file transfer failed: "
                                                        + e.getMessage());

                        e.printStackTrace();

                        return false;
                }
        }

        // =========================================================
        // REQUEST FILE DELIVERY THROUGH REST API
        // =========================================================

        private static boolean requestFileDeliveryThroughApi(
                        String recipient,
                        String sender,
                        File file,
                        String originalFileName) {

                HttpURLConnection connection = null;

                try {

                        URL url = new URL(INTERNAL_FILE_API);

                        connection = (HttpURLConnection) url.openConnection();

                        connection.setRequestMethod(
                                        "POST");

                        connection.setDoOutput(true);

                        connection.setConnectTimeout(
                                        INTERNAL_CONNECT_TIMEOUT);

                        connection.setReadTimeout(
                                        INTERNAL_READ_TIMEOUT);

                        connection.setRequestProperty(
                                        "Content-Type",
                                        "application/x-www-form-urlencoded; charset=UTF-8");

                        // =====================================================
                        // BUILD FORM DATA
                        // =====================================================

                        String requestBody = "recipient="
                                        + URLEncoder.encode(
                                                        recipient,
                                                        StandardCharsets.UTF_8)
                                        + "&sender="
                                        + URLEncoder.encode(
                                                        sender,
                                                        StandardCharsets.UTF_8)
                                        + "&filePath="
                                        + URLEncoder.encode(
                                                        file.getAbsolutePath(),
                                                        StandardCharsets.UTF_8);

                        byte[] requestBytes = requestBody.getBytes(
                                        StandardCharsets.UTF_8);

                        // =====================================================
                        // SEND REQUEST
                        // =====================================================

                        try (OutputStream outputStream = connection.getOutputStream()) {

                                outputStream.write(
                                                requestBytes);

                                outputStream.flush();
                        }

                        // =====================================================
                        // READ RESPONSE
                        // =====================================================

                        int responseCode = connection.getResponseCode();

                        System.out.println(
                                        "[WS-FILE] REST bridge response: "
                                                        + responseCode);

                        if (responseCode >= 200
                                        && responseCode < 300) {

                                System.out.println(
                                                "[WS-FILE] REST bridge delivered file successfully.");

                                return true;
                        }

                        // =====================================================
                        // READ ERROR RESPONSE
                        // =====================================================

                        try {

                                if (connection.getErrorStream() != null) {

                                        String errorResponse = new String(
                                                        connection
                                                                        .getErrorStream()
                                                                        .readAllBytes(),
                                                        StandardCharsets.UTF_8);

                                        System.out.println(
                                                        "[WS-FILE] REST bridge error: "
                                                                        + errorResponse);
                                }

                        } catch (Exception ignored) {
                        }

                        System.out.println(
                                        "[WS-FILE] REST bridge could not deliver file.");

                        return false;

                } catch (Exception e) {

                        System.out.println(
                                        "[WS-FILE] REST bridge connection failed: "
                                                        + e.getMessage());

                        return false;

                } finally {

                        if (connection != null) {

                                connection.disconnect();
                        }
                }
        }

        // =========================================================
        // INTERNAL FILE DELIVERY
        // =========================================================

        /*
         * IMPORTANT:
         *
         * This method is called by ApiServer.
         *
         * ApiServer and WebSocketBridgeServer run in the same JVM
         * when started by ApiServerMain.
         *
         * Therefore userSessions is accessible here.
         */

        public static boolean deliverFileFromInternalRequest(
                        String recipient,
                        String sender,
                        String filePath) {

                if (recipient == null
                                || recipient.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-FILE] Internal delivery recipient is empty.");

                        return false;
                }

                if (sender == null
                                || sender.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-FILE] Internal delivery sender is empty.");

                        return false;
                }

                if (filePath == null
                                || filePath.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-FILE] Internal delivery file path is empty.");

                        return false;
                }

                File file = new File(filePath);

                if (!file.exists()
                                || !file.isFile()) {

                        System.out.println(
                                        "[WS-FILE] Internal delivery file does not exist: "
                                                        + filePath);

                        return false;
                }

                String cleanRecipient = recipient.trim();

                System.out.println();
                System.out.println(
                                "[WS-FILE] Internal request reached WebSocket Bridge.");

                System.out.println(
                                "[WS-FILE] Looking for browser session: "
                                                + cleanRecipient);

                // =====================================================
                // FIND RECIPIENT SESSION
                // =====================================================

                WebSocketClientSession session = userSessions.get(cleanRecipient);

                if (session == null) {

                        System.out.println(
                                        "[WS-FILE] No browser WebSocket session found for: "
                                                        + cleanRecipient);

                        System.out.println(
                                        "[WS-FILE] Active browser users: "
                                                        + userSessions.keySet());

                        return false;
                }

                // =====================================================
                // DELIVER FILE
                // =====================================================

                return sendFileUsingSession(
                                session,
                                cleanRecipient,
                                sender,
                                file,
                                file.getName());
        }

        // =========================================================
        // INTERNAL GROUP FILE DELIVERY
        // =========================================================
        //
        // This method is called by ApiServer when another server
        // sends a group-file delivery request through the internal
        // REST bridge.
        //
        // =========================================================

        public static boolean deliverGroupFileFromInternalRequest(
                        String recipient,
                        String sender,
                        String groupName,
                        String filePath,
                        String originalFileName,
                        String transferId) {

                if (recipient == null
                                || recipient.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Internal delivery recipient is empty.");

                        return false;
                }

                if (sender == null
                                || sender.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Internal delivery sender is empty.");

                        return false;
                }

                if (groupName == null
                                || groupName.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Internal delivery group name is empty.");

                        return false;
                }

                if (filePath == null
                                || filePath.trim().isEmpty()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Internal delivery file path is empty.");

                        return false;
                }

                File file = new File(filePath);

                // =====================================================
                // VALIDATE FILE
                // =====================================================

                if (!file.exists()
                                || !file.isFile()
                                || !file.canRead()) {

                        System.out.println(
                                        "[WS-GROUP-FILE] Internal delivery file does not exist "
                                                        + "or cannot be read: "
                                                        + filePath);

                        return false;
                }

                String cleanRecipient = recipient.trim();

                String cleanGroupName = groupName.trim();

                String cleanFileName = (originalFileName == null
                                || originalFileName.trim().isEmpty())
                                                ? file.getName()
                                                : originalFileName.trim();

                System.out.println();
                System.out.println(
                                "[WS-GROUP-FILE] =====================================");

                System.out.println(
                                "[WS-GROUP-FILE] Internal group file request reached WebSocket Bridge.");

                System.out.println(
                                "[WS-GROUP-FILE] Sender: "
                                                + sender);

                System.out.println(
                                "[WS-GROUP-FILE] Group: "
                                                + cleanGroupName);

                System.out.println(
                                "[WS-GROUP-FILE] Recipient: "
                                                + cleanRecipient);

                System.out.println(
                                "[WS-GROUP-FILE] File: "
                                                + cleanFileName);

                System.out.println(
                                "[WS-GROUP-FILE] Path: "
                                                + filePath);

                System.out.println(
                                "[WS-GROUP-FILE] =====================================");

                // =====================================================
                // FIND RECIPIENT WEBSOCKET SESSION
                // =====================================================

                WebSocketClientSession session = userSessions.get(cleanRecipient);

                if (session == null) {

                        System.out.println(
                                        "[WS-GROUP-FILE] No browser WebSocket session found for: "
                                                        + cleanRecipient);

                        System.out.println(
                                        "[WS-GROUP-FILE] Active browser users: "
                                                        + userSessions.keySet());

                        return false;
                }

                // =====================================================
                // DELIVER THROUGH EXISTING WEBSOCKET CONNECTION
                // =====================================================

                return sendGroupFileUsingSession(
                                session,
                                cleanRecipient,
                                sender,
                                cleanGroupName,
                                file,
                                cleanFileName,
                                transferId);

        }

        // =========================================================
        // CLIENT DISCONNECTED
        // =========================================================

        @Override
        public void onClose(
                        WebSocket connection,
                        int code,
                        String reason,
                        boolean remote) {

                System.out.println(
                                "[WS-BRIDGE] React client disconnected: "
                                                + connection.getRemoteSocketAddress()
                                                + " | code="
                                                + code
                                                + " | reason="
                                                + reason);

                WebSocketClientSession session = sessions.remove(connection);

                if (session != null) {

                        session.close();
                }
        }

        // =========================================================
        // ERROR
        // =========================================================

        @Override
        public void onError(
                        WebSocket connection,
                        Exception exception) {

                System.out.println(
                                "[WS-BRIDGE] WebSocket error: "
                                                + exception.getMessage());

                if (connection != null) {

                        WebSocketClientSession session = sessions.get(connection);

                        if (session != null) {

                                session.close();
                        }
                }

                exception.printStackTrace();
        }

        // =========================================================
        // SERVER STARTED
        // =========================================================

        @Override
        public void onStart() {

                System.out.println();
                System.out.println(
                                "==============================================");

                System.out.println(
                                "       CloudChat WebSocket Bridge");

                System.out.println(
                                "==============================================");

                System.out.println(
                                "[WS-BRIDGE] WebSocket server started");

                System.out.println(
                                "[WS-BRIDGE] Port: 9001");

                System.out.println(
                                "[WS-BRIDGE] URL: ws://localhost:9001");

                System.out.println(
                                "[WS-BRIDGE] Internal REST file bridge: "
                                                + INTERNAL_FILE_API);

                System.out.println(
                                "[WS-BRIDGE] Internal REST group file bridge: "
                                                + INTERNAL_GROUP_FILE_API);

                System.out.println(
                                "==============================================");

                System.out.println();
        }

        // =========================================================
        // GET ACTIVE SESSION COUNT
        // =========================================================

        public int getActiveSessionCount() {

                return sessions.size();
        }

        // =========================================================
        // STOP SERVER
        // =========================================================

        public void shutdown() {

                System.out.println(
                                "[WS-BRIDGE] Shutting down...");

                for (WebSocketClientSession session : sessions.values()) {

                        try {

                                session.close();

                        } catch (Exception ignored) {
                        }
                }

                sessions.clear();

                userSessions.clear();

                try {

                        stop();

                } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();

                        System.out.println(
                                        "[WS-BRIDGE] Shutdown interrupted");
                }
        }
}