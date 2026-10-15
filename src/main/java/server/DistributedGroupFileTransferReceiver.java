package server;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import service.OfflineGroupFileService;

public class DistributedGroupFileTransferReceiver
                implements Runnable {

        private final String host;
        private final int port;

        private final String sender;
        private final String groupName;
        private final String expectedFileName;
        private final long expectedFileSize;

        private final List<String> recipients;

        private final String transferId;

        private final String sourceServer;
        private final String destinationServer;

        // =========================================================
        // CONSTRUCTOR
        // =========================================================

        public DistributedGroupFileTransferReceiver(
                        String host,
                        int port,
                        String sender,
                        String groupName,
                        String expectedFileName,
                        long expectedFileSize,
                        List<String> recipients,
                        String sourceServer,
                        String destinationServer,
                        String transferId) {

                this.host = host;
                this.port = port;

                this.sender = sender;
                this.groupName = groupName;

                this.expectedFileName = expectedFileName;
                this.expectedFileSize = expectedFileSize;

                this.recipients = recipients;

                this.transferId = transferId;

                this.sourceServer = sourceServer;
                this.destinationServer = destinationServer;
        }

        // =========================================================
        // BACKWARD-COMPATIBLE CONSTRUCTOR
        // =========================================================

        public DistributedGroupFileTransferReceiver(
                        String host,
                        int port,
                        String sender,
                        String groupName,
                        String expectedFileName,
                        long expectedFileSize) {

                this(
                                host,
                                port,
                                sender,
                                groupName,
                                expectedFileName,
                                expectedFileSize,
                                new ArrayList<>(),
                                "SERVER_1",
                                "SERVER_2",
                                java.util.UUID.randomUUID().toString());
        }

        // =========================================================
        // GET LOCAL ONLINE GROUP MEMBERS
        // =========================================================

        private List<String> getLocalOnlineGroupMembers(
                        String groupName) {

                List<String> localOnlineMembers = new ArrayList<>();

                for (String username : ChatServer.getLocalOnlineUsers()) {

                        if (username == null
                                        || username.trim().isEmpty()) {

                                continue;
                        }

                        username = username.trim();

                        // -------------------------------------------------
                        // Sender must never receive his own file
                        // -------------------------------------------------

                        if (username.equals(sender)) {
                                continue;
                        }

                        ClientHandler client = ChatServer.getOnlineUser(username);

                        if (client == null) {
                                continue;
                        }

                        // -------------------------------------------------
                        // Verify actual group membership
                        // -------------------------------------------------

                        if (ChatServer.isAnyGroupMember(
                                        groupName,
                                        client)) {

                                localOnlineMembers.add(
                                                username);
                        }
                }

                return localOnlineMembers;
        }

        // =========================================================
        // GET LOCAL GROUP MEMBERS
        // =========================================================
        //
        // Includes:
        // - Online local members
        // - Offline local members
        //
        // ChatServer provides the persistent membership list.
        //
        // =========================================================

        private Set<String> getLocalGroupMembers(
                        String groupName) {

                Set<String> localMembers = ConcurrentHashMap.newKeySet();

                Set<String> persistentMembers = ChatServer.getPersistentGroupMembers(
                                groupName);

                Set<String> remoteMembers = ChatServer.getRemoteGroupMembers(
                                groupName);

                if (persistentMembers == null
                                || persistentMembers.isEmpty()) {

                        return localMembers;
                }

                if (remoteMembers == null) {
                        remoteMembers = Set.of();
                }

                for (String username : persistentMembers) {

                        if (username == null
                                        || username.trim().isEmpty()) {

                                continue;
                        }

                        username = username.trim();

                        // -------------------------------------------------
                        // Never deliver back to sender
                        // -------------------------------------------------

                        if (username.equals(sender)) {
                                continue;
                        }

                        // -------------------------------------------------
                        // User belongs to the other server
                        // -------------------------------------------------

                        if (remoteMembers.contains(username)) {
                                continue;
                        }

                        localMembers.add(username);
                }

                return localMembers;
        }

        // =========================================================
        // RUN
        // =========================================================

        @Override
        public void run() {

                System.out.println();

                System.out.println(
                                "[GROUP-FILE-DIST] Connecting to source server...");

                System.out.println(
                                "[GROUP-FILE-DIST] Host: "
                                                + host);

                System.out.println(
                                "[GROUP-FILE-DIST] Port: "
                                                + port);

                System.out.println(
                                "[GROUP-FILE-DIST] Group: "
                                                + groupName);

                System.out.println(
                                "[GROUP-FILE-DIST] Sender: "
                                                + sender);

                System.out.println(
                                "[GROUP-FILE-DIST] Source server: "
                                                + sourceServer);

                System.out.println(
                                "[GROUP-FILE-DIST] Destination server: "
                                                + destinationServer);

                try (
                                Socket socket = new Socket(
                                                host,
                                                port);

                                DataInputStream input = new DataInputStream(
                                                socket.getInputStream())) {

                        System.out.println(
                                        "[GROUP-FILE-DIST] "
                                                        + "Connected to source server.");

                        // =====================================================
                        // RECEIVE FILE METADATA
                        // =====================================================

                        String receivedTransferId = input.readUTF();
                        String fileName = input.readUTF();
                        long fileSize = input.readLong();

                        if (!transferId.equals(receivedTransferId)) {
                                throw new IOException(
                                                "Group file transfer ID mismatch. Expected: "
                                                                + transferId
                                                                + ", received: "
                                                                + receivedTransferId);
                        }

                        System.out.println(
                                        "[GROUP-FILE-DIST] Receiving file: "
                                                        + fileName);

                        System.out.println(
                                        "[GROUP-FILE-DIST] Expected size: "
                                                        + fileSize
                                                        + " bytes");

                        // =====================================================
                        // VALIDATE FILE NAME
                        // =====================================================

                        if (!fileName.equals(
                                        expectedFileName)) {

                                System.out.println(
                                                "[GROUP-FILE-DIST] WARNING: "
                                                                + "File name mismatch.");

                                System.out.println(
                                                "[GROUP-FILE-DIST] Expected: "
                                                                + expectedFileName);

                                System.out.println(
                                                "[GROUP-FILE-DIST] Received: "
                                                                + fileName);
                        }

                        // =====================================================
                        // VALIDATE FILE SIZE
                        // =====================================================

                        if (fileSize != expectedFileSize) {

                                System.out.println(
                                                "[GROUP-FILE-DIST] WARNING: "
                                                                + "File size mismatch.");

                                System.out.println(
                                                "[GROUP-FILE-DIST] Expected: "
                                                                + expectedFileSize);

                                System.out.println(
                                                "[GROUP-FILE-DIST] Received: "
                                                                + fileSize);
                        }

                        // =====================================================
                        // CREATE DIRECTORY
                        // =====================================================

                        File directory = new File("distributed_files");

                        if (!directory.exists()) {

                                if (!directory.mkdirs()) {

                                        System.out.println(
                                                        "[GROUP-FILE-DIST] "
                                                                        + "Unable to create directory: "
                                                                        + directory.getAbsolutePath());

                                        return;
                                }
                        }

                        // =====================================================
                        // CREATE OUTPUT FILE
                        // =====================================================

                        File outputFile = new File(
                                        directory,
                                        fileName);

                        // =====================================================
                        // RECEIVE BINARY DATA
                        // =====================================================

                        try (
                                        FileOutputStream output = new FileOutputStream(
                                                        outputFile)) {

                                byte[] buffer = new byte[8192];

                                long totalReceived = 0;

                                while (totalReceived < fileSize) {

                                        int bytesToRead = (int) Math.min(
                                                        buffer.length,
                                                        fileSize
                                                                        - totalReceived);

                                        int bytesRead = input.read(
                                                        buffer,
                                                        0,
                                                        bytesToRead);

                                        if (bytesRead == -1) {

                                                throw new IOException(
                                                                "Connection closed before "
                                                                                + "complete group file was received.");
                                        }

                                        output.write(
                                                        buffer,
                                                        0,
                                                        bytesRead);

                                        totalReceived += bytesRead;
                                }

                                output.flush();

                                // =================================================
                                // TRANSFER SUCCESS
                                // =================================================

                                System.out.println(
                                                "[GROUP-FILE-DIST] "
                                                                + "File received successfully.");

                                System.out.println(
                                                "[GROUP-FILE-DIST] Group: "
                                                                + groupName);

                                System.out.println(
                                                "[GROUP-FILE-DIST] Sender: "
                                                                + sender);

                                System.out.println(
                                                "[GROUP-FILE-DIST] Saved to: "
                                                                + outputFile.getAbsolutePath());

                                System.out.println(
                                                "[GROUP-FILE-DIST] Total bytes received: "
                                                                + totalReceived);

                                // =================================================
                                // VERIFY COMPLETE FILE
                                // =================================================

                                if (totalReceived != fileSize) {

                                        System.out.println(
                                                        "[GROUP-FILE-DIST] "
                                                                        + "File transfer incomplete.");

                                        return;
                                }

                                // =================================================
                                // FIND LOCAL ONLINE RECIPIENTS
                                // =================================================

                                List<String> localOnlineRecipients = getLocalOnlineGroupMembers(
                                                groupName);

                                System.out.println(
                                                "[GROUP-FILE-DIST] "
                                                                + "Local online recipients: "
                                                                + localOnlineRecipients);

                                // =================================================
                                // DELIVER TO LOCAL ONLINE MEMBERS
                                // =================================================

                                if (!localOnlineRecipients.isEmpty()) {

                                        System.out.println(
                                                        "[GROUP-FILE-DIST] "
                                                                        + "Starting local group delivery...");

                                        GroupFileTransferTracker tracker = new GroupFileTransferTracker(
                                                        sender,
                                                        groupName,
                                                        outputFile,
                                                        recipients,
                                                        localOnlineRecipients,
                                                        "DISTRIBUTED_GROUP",
                                                        sourceServer,
                                                        destinationServer);

                                        ChatServer.deliverGroupFileToLocalMembers(
                                                        groupName,
                                                        sender,
                                                        outputFile,
                                                        tracker,
                                                        transferId);

                                } else {

                                        System.out.println(
                                                        "[GROUP-FILE-DIST] "
                                                                        + "No local online recipients.");
                                }

                                // =================================================
                                // GET ALL LOCAL GROUP MEMBERS
                                // =================================================

                                List<String> localGroupMembers = new ArrayList<>(
                                                getLocalGroupMembers(
                                                                groupName));

                                System.out.println(
                                                "[GROUP-FILE-DIST] "
                                                                + "Local group members: "
                                                                + localGroupMembers);

                                // =================================================
                                // GET OFFLINE GROUP FILE SERVICE
                                // =================================================

                                OfflineGroupFileService offlineGroupFileService = ChatServer
                                                .getOfflineGroupFileService();

                                if (offlineGroupFileService == null) {

                                        System.out.println(
                                                        "[OFFLINE-GROUP-FILE] "
                                                                        + "Service is not initialized.");

                                        return;
                                }

                                // =================================================
                                // QUEUE OFFLINE LOCAL MEMBERS
                                // =================================================

                                for (String recipient : localGroupMembers) {

                                        if (recipient == null
                                                        || recipient.trim().isEmpty()) {

                                                continue;
                                        }

                                        recipient = recipient.trim();

                                        // -------------------------------------------------
                                        // NEVER SEND TO SENDER
                                        // -------------------------------------------------

                                        if (recipient.equals(sender)) {

                                                System.out.println(
                                                                "[OFFLINE-GROUP-FILE] "
                                                                                + "Skipping sender: "
                                                                                + recipient);

                                                continue;
                                        }

                                        // -------------------------------------------------
                                        // ALREADY ONLINE LOCALLY
                                        // -------------------------------------------------

                                        if (localOnlineRecipients.contains(
                                                        recipient)) {

                                                System.out.println(
                                                                "[OFFLINE-GROUP-FILE] "
                                                                                + "Already online locally: "
                                                                                + recipient);

                                                continue;
                                        }

                                        // -------------------------------------------------
                                        // USER IS OFFLINE LOCALLY
                                        // -------------------------------------------------

                                        System.out.println(
                                                        "[OFFLINE-GROUP-FILE] "
                                                                        + "Recipient offline locally: "
                                                                        + recipient);

                                        boolean saved = offlineGroupFileService
                                                        .savePendingGroupFile(
                                                                        sender,
                                                                        recipient,
                                                                        groupName,
                                                                        outputFile);

                                        if (saved) {

                                                System.out.println(
                                                                "[OFFLINE-GROUP-FILE] "
                                                                                + "Group file queued: "
                                                                                + sender
                                                                                + " -> "
                                                                                + recipient
                                                                                + " ["
                                                                                + groupName
                                                                                + "] "
                                                                                + outputFile.getName());

                                        } else {

                                                System.out.println(
                                                                "[OFFLINE-GROUP-FILE] "
                                                                                + "Unable to queue group file for "
                                                                                + recipient);
                                        }
                                }

                                // =================================================
                                // FINAL STATUS
                                // =================================================

                                System.out.println(
                                                "[GROUP-FILE-DIST] "
                                                                + "Destination processing completed.");

                                System.out.println(
                                                "[GROUP-FILE-DIST] "
                                                                + "Online local recipients: "
                                                                + localOnlineRecipients);

                                System.out.println(
                                                "[GROUP-FILE-DIST] "
                                                                + "Offline local recipients queued through "
                                                                + "GridFS/PENDING.");
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "[GROUP-FILE-DIST] Receive error: "
                                                        + e.getMessage());

                        e.printStackTrace();
                }
        }
}