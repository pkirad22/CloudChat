package server;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;

import model.FileTransferHistory;
import service.FileTransferHistoryService;

public class DistributedFileTransferReceiver
                implements Runnable {

        private final String host;
        private final int port;

        private final String sender;
        private final String recipient;

        private final String sourceServer;
        private final String destinationServer;

        public DistributedFileTransferReceiver(
                        String host,
                        int port,
                        String sender,
                        String recipient,
                        String sourceServer,
                        String destinationServer) {

                this.host = host;
                this.port = port;
                this.sender = sender;
                this.recipient = recipient;
                this.sourceServer = sourceServer;
                this.destinationServer = destinationServer;
        }

        @Override
        public void run() {

                System.out.println();
                System.out.println(
                                "[FILE-DIST] Connecting to source server...");

                System.out.println(
                                "[FILE-DIST] Host: " + host);

                System.out.println(
                                "[FILE-DIST] Port: " + port);

                try (
                                Socket socket = new Socket(host, port);

                                DataInputStream input = new DataInputStream(
                                                socket.getInputStream())) {

                        System.out.println(
                                        "[FILE-DIST] Connected to source server.");

                        // -----------------------------------------------------
                        // RECEIVE METADATA
                        // -----------------------------------------------------

                        String fileName = input.readUTF();

                        long fileSize = input.readLong();

                        System.out.println(
                                        "[FILE-DIST] Receiving file: "
                                                        + fileName);

                        System.out.println(
                                        "[FILE-DIST] Expected size: "
                                                        + fileSize
                                                        + " bytes");

                        // -----------------------------------------------------
                        // CREATE DIRECTORY
                        // -----------------------------------------------------

                        File directory = new File("distributed_files");

                        if (!directory.exists()) {
                                directory.mkdirs();
                        }

                        // -----------------------------------------------------
                        // CREATE OUTPUT FILE
                        // -----------------------------------------------------

                        File outputFile = new File(
                                        directory,
                                        fileName);

                        // -----------------------------------------------------
                        // RECEIVE BINARY DATA
                        // -----------------------------------------------------

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
                                                                                + "complete file was received.");
                                        }

                                        output.write(
                                                        buffer,
                                                        0,
                                                        bytesRead);

                                        totalReceived += bytesRead;
                                }

                                output.flush();

                                System.out.println(
                                                "[FILE-DIST] File received successfully.");

                                System.out.println(
                                                "[FILE-DIST] Sender: "
                                                                + sender);

                                System.out.println(
                                                "[FILE-DIST] Recipient: "
                                                                + recipient);

                                System.out.println(
                                                "[FILE-DIST] Saved to: "
                                                                + outputFile
                                                                                .getAbsolutePath());

                                System.out.println(
                                                "[FILE-DIST] Total bytes received: "
                                                                + totalReceived);

                                // =====================================================
                                // VERIFY COMPLETE TRANSFER
                                // =====================================================

                                if (totalReceived == fileSize) {

                                        // -------------------------------------------------
                                        // CHECK RECIPIENT
                                        // -------------------------------------------------

                                        ClientHandler recipientHandler = ChatServer.getOnlineUser(
                                                        recipient);

                                        if (recipientHandler == null) {

                                                System.out.println(
                                                                "[FILE-DELIVERY] "
                                                                                + "Recipient went offline: "
                                                                                + recipient);

                                                return;
                                        }

                                        // =================================================
                                        // SAVE DISTRIBUTED FILE HISTORY
                                        // =================================================

                                        saveFileTransferHistory(
                                                        fileName,
                                                        totalReceived);

                                        // =================================================
                                        // START DELIVERY TO RECIPIENT CLIENT
                                        // =================================================

                                        File recipientFile = outputFile;

                                        System.out.println(
                                                        "[FILE-DELIVERY] "
                                                                        + "Starting delivery to "
                                                                        + recipient);

                                        DistributedFileDeliveryServer deliveryServer = new DistributedFileDeliveryServer(
                                                        recipientFile,
                                                        sender,
                                                        recipient,
                                                        recipientHandler);

                                        Thread deliveryThread = new Thread(
                                                        deliveryServer,
                                                        "DistributedFileDelivery-"
                                                                        + fileName);

                                        deliveryThread.start();
                                }
                        }

                } catch (IOException e) {

                        System.out.println(
                                        "[FILE-DIST] Receive error: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // SAVE DISTRIBUTED PRIVATE FILE HISTORY
        // =========================================================

        private void saveFileTransferHistory(
                        String fileName,
                        long fileSize) {

                try {

                        FileTransferHistory history = new FileTransferHistory(

                                        sender,
                                        recipient,
                                        null,
                                        null,

                                        fileName,
                                        fileSize,
                                        getFileType(fileName),

                                        "DISTRIBUTED_PRIVATE",

                                        sourceServer,
                                        destinationServer,

                                        "SUCCESS",

                                        LocalDateTime.now());

                        FileTransferHistoryService historyService = new FileTransferHistoryService();

                        historyService.saveFileTransfer(
                                        history);

                        System.out.println(
                                        "[FILE-HISTORY] "
                                                        + "Distributed private file transfer "
                                                        + "saved to MongoDB.");

                } catch (Exception e) {

                        System.out.println(
                                        "[FILE-HISTORY] "
                                                        + "Failed to save distributed "
                                                        + "file history: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // GET FILE TYPE
        // =========================================================

        private String getFileType(
                        String fileName) {

                int lastDot = fileName.lastIndexOf('.');

                if (lastDot == -1
                                || lastDot == fileName.length() - 1) {

                        return "unknown";
                }

                return fileName
                                .substring(lastDot + 1)
                                .toLowerCase();
        }
}