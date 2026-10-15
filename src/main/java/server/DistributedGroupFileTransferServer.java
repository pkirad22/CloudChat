package server;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DistributedGroupFileTransferServer
                implements Runnable {

        private final File file;
        private final String sender;
        private final String groupName;
        private final String transferId;

        private volatile int transferPort = -1;

        private final CountDownLatch portReady = new CountDownLatch(1);

        public DistributedGroupFileTransferServer(
                        File file,
                        String sender,
                        String groupName,
                        String transferId) {

                this.file = file;
                this.sender = sender;
                this.groupName = groupName;
                this.transferId = transferId;
        }

        public int getTransferPort() {
                return transferPort;
        }

        public boolean awaitPort(long timeoutMillis) {
                try {
                        return portReady.await(
                                        timeoutMillis,
                                        TimeUnit.MILLISECONDS);

                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                }
        }

        @Override
        public void run() {
                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        transferPort = serverSocket.getLocalPort();

                        System.out.println();
                        System.out.println(
                                        "[GROUP-FILE-DIST] Transfer server started.");

                        System.out.println(
                                        "[GROUP-FILE-DIST] Sender: " + sender);

                        System.out.println(
                                        "[GROUP-FILE-DIST] Group: " + groupName);

                        System.out.println(
                                        "[GROUP-FILE-DIST] File: " + file.getName());

                        System.out.println(
                                        "[GROUP-FILE-DIST] Transfer ID: " + transferId);

                        System.out.println(
                                        "[GROUP-FILE-DIST] Size: "
                                                        + file.length() + " bytes");

                        System.out.println(
                                        "[GROUP-FILE-DIST] Listening on port: "
                                                        + transferPort);

                        portReady.countDown();

                        try (Socket socket = serverSocket.accept();
                                        DataOutputStream output = new DataOutputStream(
                                                        socket.getOutputStream());
                                        FileInputStream fileInput = new FileInputStream(file)) {

                                System.out.println(
                                                "[GROUP-FILE-DIST] Server-to-server "
                                                                + "connection established.");

                                // Metadata must be read in this exact order
                                // by the receiving server.
                                output.writeUTF(transferId);
                                output.writeUTF(file.getName());
                                output.writeLong(file.length());

                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                long totalSent = 0;

                                while ((bytesRead = fileInput.read(buffer)) != -1) {
                                        output.write(buffer, 0, bytesRead);
                                        totalSent += bytesRead;
                                }

                                output.flush();

                                System.out.println(
                                                "[GROUP-FILE-DIST] File transferred successfully.");

                                System.out.println(
                                                "[GROUP-FILE-DIST] Total bytes sent: "
                                                                + totalSent);
                        }

                } catch (IOException e) {
                        System.out.println(
                                        "[GROUP-FILE-DIST] Transfer error: "
                                                        + e.getMessage());

                } finally {
                        portReady.countDown();
                }
        }
}