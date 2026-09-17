package server;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;

import model.FileTransferHistory;
import service.FileTransferHistoryService;

public class FileTransferServer implements Runnable {

        private static final int FILE_PORT = 5001;

        private final String sender;
        private final String recipient;
        private final File file;

        public FileTransferServer(
                        String sender,
                        String recipient,
                        File file) {

                this.sender = sender;
                this.recipient = recipient;
                this.file = file;
        }

        @Override
        public void run() {

                try (ServerSocket serverSocket = new ServerSocket(FILE_PORT)) {

                        System.out.println(
                                        "File transfer server waiting on port "
                                                        + FILE_PORT);

                        Socket socket = serverSocket.accept();

                        System.out.println(
                                        "File transfer connection established.");

                        try (
                                        DataOutputStream output = new DataOutputStream(
                                                        socket.getOutputStream());

                                        FileInputStream fileInput = new FileInputStream(file)) {

                                // ---------------------------------------------
                                // Send file name
                                // ---------------------------------------------

                                output.writeUTF(
                                                file.getName());

                                // ---------------------------------------------
                                // Send file size
                                // ---------------------------------------------

                                output.writeLong(
                                                file.length());

                                // ---------------------------------------------
                                // Send file data
                                // ---------------------------------------------

                                byte[] buffer = new byte[8192];

                                int bytesRead;
                                long totalSent = 0;

                                while ((bytesRead = fileInput.read(buffer)) != -1) {

                                        output.write(
                                                        buffer,
                                                        0,
                                                        bytesRead);

                                        totalSent += bytesRead;
                                }

                                output.flush();

                                // ---------------------------------------------
                                // Transfer completed successfully
                                // ---------------------------------------------

                                System.out.println(
                                                "File sent successfully to "
                                                                + recipient);

                                System.out.println(
                                                "Total bytes sent: "
                                                                + totalSent);

                                // ---------------------------------------------
                                // SAVE FILE TRANSFER HISTORY
                                // ---------------------------------------------

                                saveFileTransferHistory(
                                                totalSent);
                        }

                        socket.close();

                } catch (IOException e) {

                        System.out.println(
                                        "File transfer error: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // SAVE LOCAL PRIVATE FILE HISTORY
        // =========================================================

        private void saveFileTransferHistory(
                        long totalSent) {

                try {

                        FileTransferHistory history = new FileTransferHistory(

                                        sender, // sender
                                        recipient, // recipient
                                        null, // groupName
                                        null, // recipients

                                        file.getName(), // fileName
                                        totalSent, // fileSize
                                        getFileType(file), // fileType

                                        "LOCAL_PRIVATE", // transferType

                                        "LOCAL", // sourceServer
                                        "LOCAL", // destinationServer

                                        "SUCCESS", // status

                                        LocalDateTime.now()); // timestamp

                        FileTransferHistoryService historyService = new FileTransferHistoryService();

                        historyService.saveFileTransfer(
                                        history);

                        System.out.println(
                                        "[FILE-HISTORY] "
                                                        + "Local private file transfer "
                                                        + "saved to MongoDB.");

                } catch (Exception e) {

                        System.out.println(
                                        "[FILE-HISTORY] "
                                                        + "Failed to save file history: "
                                                        + e.getMessage());
                }
        }

        // =========================================================
        // GET FILE TYPE
        // =========================================================

        private String getFileType(File file) {

                String fileName = file.getName();

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