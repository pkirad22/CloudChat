package server;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class DistributedFileDeliveryServer implements Runnable {

        private final File file;
        private final String sender;
        private final String recipient;
        private final ClientHandler recipientHandler;

        private final GroupFileTransferTracker groupFileTransferTracker;

        private volatile int deliveryPort = -1;

        public DistributedFileDeliveryServer(
                        File file,
                        String sender,
                        String recipient,
                        ClientHandler recipientHandler) {

                this(
                                file,
                                sender,
                                recipient,
                                recipientHandler,
                                null);
        }

        public DistributedFileDeliveryServer(
                        File file,
                        String sender,
                        String recipient,
                        ClientHandler recipientHandler,
                        GroupFileTransferTracker groupFileTransferTracker) {

                this.file = file;
                this.sender = sender;
                this.recipient = recipient;
                this.recipientHandler = recipientHandler;
                this.groupFileTransferTracker = groupFileTransferTracker;
        }

        public int getDeliveryPort() {
                return deliveryPort;
        }

        @Override
        public void run() {

                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        // =====================================================
                        // GET FREE PORT
                        // =====================================================

                        deliveryPort = serverSocket.getLocalPort();

                        System.out.println();
                        System.out.println(
                                        "[FILE-DELIVERY] Delivery server started.");

                        System.out.println(
                                        "[FILE-DELIVERY] Sender: "
                                                        + sender);

                        System.out.println(
                                        "[FILE-DELIVERY] Recipient: "
                                                        + recipient);

                        System.out.println(
                                        "[FILE-DELIVERY] File: "
                                                        + file.getName());

                        System.out.println(
                                        "[FILE-DELIVERY] Delivery port: "
                                                        + deliveryPort);

                        // =====================================================
                        // INFORM RECIPIENT CLIENT
                        // =====================================================

                        recipientHandler.sendMessage(
                                        "FILE_DELIVERY_READY:"
                                                        + sender
                                                        + ":"
                                                        + recipient
                                                        + ":"
                                                        + file.getName()
                                                        + ":"
                                                        + file.length()
                                                        + ":"
                                                        + deliveryPort);

                        System.out.println(
                                        "[FILE-DELIVERY] Delivery information "
                                                        + "sent to recipient.");

                        // =====================================================
                        // WAIT FOR CLIENT
                        // =====================================================

                        Socket socket = serverSocket.accept();

                        System.out.println(
                                        "[FILE-DELIVERY] Recipient client "
                                                        + "connected.");

                        // =====================================================
                        // SEND FILE
                        // =====================================================

                        try (
                                        DataOutputStream output = new DataOutputStream(
                                                        socket.getOutputStream());

                                        FileInputStream fileInput = new FileInputStream(file)) {

                                output.writeUTF(
                                                file.getName());

                                output.writeLong(
                                                file.length());

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

                                System.out.println(
                                                "[FILE-DELIVERY] File delivered "
                                                                + "successfully to "
                                                                + recipient);

                                System.out.println(
                                                "[FILE-DELIVERY] Total bytes sent: "
                                                                + totalSent);

                                if (groupFileTransferTracker != null
                                                && totalSent == file.length()) {

                                        groupFileTransferTracker.deliveryCompleted(
                                                        recipientHandler.getUsername());
                                }

                                // =====================================================
                                // GROUP FILE HISTORY
                                // =====================================================

                                if (groupFileTransferTracker != null
                                                && totalSent == file.length()) {

                                        groupFileTransferTracker
                                                        .deliveryCompleted(recipient);
                                }
                        }

                        socket.close();

                } catch (IOException e) {

                        System.out.println(
                                        "[FILE-DELIVERY] Delivery error: "
                                                        + e.getMessage());
                }
        }
}