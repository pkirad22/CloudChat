package server;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class GroupFileDeliveryServer implements Runnable {

        private final File file;
        private final String sender;
        private final String groupName;
        private final ClientHandler recipientHandler;

        private final GroupFileTransferTracker tracker;

        private volatile int deliveryPort = -1;

        public GroupFileDeliveryServer(
                        File file,
                        String sender,
                        String groupName,
                        ClientHandler recipientHandler,
                        GroupFileTransferTracker tracker) {

                this.file = file;
                this.sender = sender;
                this.groupName = groupName;
                this.recipientHandler = recipientHandler;
                this.tracker = tracker;
        }

        public GroupFileDeliveryServer(
                        File file,
                        String sender,
                        String groupName,
                        ClientHandler recipientHandler) {

                this(
                                file,
                                sender,
                                groupName,
                                recipientHandler,
                                null);
        }

        @Override
        public void run() {

                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        deliveryPort = serverSocket.getLocalPort();

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] Delivery server started.");

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] Group: "
                                                        + groupName);

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] Sender: "
                                                        + sender);

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] Recipient: "
                                                        + recipientHandler.getUsername());

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] File: "
                                                        + file.getName());

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] Port: "
                                                        + deliveryPort);

                        // -----------------------------------------------------
                        // Tell recipient client where to download the file
                        // -----------------------------------------------------

                        recipientHandler.sendMessage(
                                        "GROUP_FILE_DELIVERY_READY:"
                                                        + groupName
                                                        + ":"
                                                        + sender
                                                        + ":"
                                                        + file.getName()
                                                        + ":"
                                                        + file.length()
                                                        + ":"
                                                        + deliveryPort);

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] "
                                                        + "Delivery information sent to "
                                                        + recipientHandler.getUsername());

                        // -----------------------------------------------------
                        // Wait for recipient client
                        // -----------------------------------------------------

                        Socket socket = serverSocket.accept();

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] Recipient client connected.");

                        // -----------------------------------------------------
                        // Send file
                        // -----------------------------------------------------

                        try (
                                        DataOutputStream output = new DataOutputStream(
                                                        socket.getOutputStream());

                                        FileInputStream fileInput = new FileInputStream(file)) {

                                output.writeUTF(file.getName());
                                output.writeLong(file.length());

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
                                                "[GROUP-FILE-DELIVERY] "
                                                                + "File delivered successfully.");

                                System.out.println(
                                                "[GROUP-FILE-DELIVERY] Recipient: "
                                                                + recipientHandler.getUsername());

                                System.out.println(
                                                "[GROUP-FILE-DELIVERY] Total bytes sent: "
                                                                + totalSent);

                                // =========================================================
                                // GROUP FILE HISTORY TRACKING
                                // =========================================================

                                if (tracker != null
                                                && totalSent == file.length()) {

                                        System.out.println(
                                                        "[GROUP-FILE-HISTORY] Reporting successful delivery: "
                                                                        + recipientHandler.getUsername());

                                        tracker.deliveryCompleted(
                                                        recipientHandler.getUsername());
                                }
                        }

                        socket.close();

                } catch (IOException e) {

                        System.out.println(
                                        "[GROUP-FILE-DELIVERY] Delivery error: "
                                                        + e.getMessage());
                }
        }
}