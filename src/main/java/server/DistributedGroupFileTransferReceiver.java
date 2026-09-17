package server;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class DistributedGroupFileTransferReceiver
                implements Runnable {

        private final String host;
        private final int port;

        private final String sender;
        private final String groupName;
        private final String expectedFileName;
        private final long expectedFileSize;

        private final List<String> recipients;

        private final String sourceServer;
        private final String destinationServer;

        public DistributedGroupFileTransferReceiver(
                        String host,
                        int port,
                        String sender,
                        String groupName,
                        String expectedFileName,
                        long expectedFileSize,
                        List<String> recipients,
                        String sourceServer,
                        String destinationServer) {

                this.host = host;
                this.port = port;

                this.sender = sender;
                this.groupName = groupName;

                this.expectedFileName = expectedFileName;

                this.expectedFileSize = expectedFileSize;

                this.recipients = recipients;

                this.sourceServer = sourceServer;

                this.destinationServer = destinationServer;
        }

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
                                new java.util.ArrayList<>(),
                                "SERVER_1",
                                "SERVER_2");
        }

        private java.util.List<String> getLocalRecipients(
                        String groupName) {

                java.util.List<String> localRecipients = new java.util.ArrayList<>();

                for (String username : ChatServer.getLocalOnlineUsers()) {

                        if (username == null
                                        || username.trim().isEmpty()) {

                                continue;
                        }

                        ClientHandler client = ChatServer.getOnlineUser(username);

                        if (client == null) {
                                continue;
                        }

                        if (ChatServer.isAnyGroupMember(
                                        groupName,
                                        client)) {

                                localRecipients.add(username);
                        }
                }

                return localRecipients;
        }

        @Override
        public void run() {

                System.out.println();
                System.out.println(
                                "[GROUP-FILE-DIST] Connecting to source server...");

                System.out.println(
                                "[GROUP-FILE-DIST] Host: " + host);

                System.out.println(
                                "[GROUP-FILE-DIST] Port: " + port);

                System.out.println(
                                "[GROUP-FILE-DIST] Group: " + groupName);

                System.out.println(
                                "[GROUP-FILE-DIST] Sender: " + sender);

                try (
                                Socket socket = new Socket(host, port);

                                DataInputStream input = new DataInputStream(
                                                socket.getInputStream())) {

                        System.out.println(
                                        "[GROUP-FILE-DIST] Connected to source server.");

                        // =====================================================
                        // RECEIVE FILE METADATA
                        // =====================================================

                        String fileName = input.readUTF();

                        long fileSize = input.readLong();

                        System.out.println(
                                        "[GROUP-FILE-DIST] Receiving file: "
                                                        + fileName);

                        System.out.println(
                                        "[GROUP-FILE-DIST] Expected size: "
                                                        + fileSize
                                                        + " bytes");

                        // =====================================================
                        // VALIDATE METADATA
                        // =====================================================

                        if (!fileName.equals(expectedFileName)) {

                                System.out.println(
                                                "[GROUP-FILE-DIST] WARNING: "
                                                                + "File name mismatch.");
                        }

                        if (fileSize != expectedFileSize) {

                                System.out.println(
                                                "[GROUP-FILE-DIST] WARNING: "
                                                                + "File size mismatch.");
                        }

                        // =====================================================
                        // CREATE DIRECTORY
                        // =====================================================

                        File directory = new File(
                                        "distributed_files");

                        if (!directory.exists()) {

                                directory.mkdirs();
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
                                        FileOutputStream output = new FileOutputStream(outputFile)) {

                                byte[] buffer = new byte[8192];

                                long totalReceived = 0;

                                while (totalReceived < fileSize) {

                                        int bytesToRead = (int) Math.min(
                                                        buffer.length,
                                                        fileSize - totalReceived);

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
                                                                + outputFile
                                                                                .getAbsolutePath());

                                System.out.println(
                                                "[GROUP-FILE-DIST] Total bytes received: "
                                                                + totalReceived);

                                // =================================================
                                // DELIVER TO LOCAL GROUP MEMBERS
                                // =================================================

                                if (totalReceived == fileSize) {

                                        System.out.println(
                                                        "[GROUP-FILE-DIST] "
                                                                        + "Starting local group delivery...");

                                        GroupFileTransferTracker tracker = new GroupFileTransferTracker(
                                                        sender,
                                                        groupName,
                                                        outputFile,
                                                        recipients,
                                                        getLocalRecipients(groupName),
                                                        "DISTRIBUTED_GROUP",
                                                        sourceServer,
                                                        destinationServer);

                                        ChatServer.deliverGroupFileToLocalMembers(
                                                        groupName,
                                                        sender,
                                                        outputFile,
                                                        tracker);
                                }
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "[GROUP-FILE-DIST] Receive error: "
                                                        + e.getMessage());
                }
        }
}