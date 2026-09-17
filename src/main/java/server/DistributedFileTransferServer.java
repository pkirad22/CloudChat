package server;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DistributedFileTransferServer implements Runnable {

    private final File file;
    private final String sender;
    private final String recipient;

    private volatile int transferPort = -1;

    private final CountDownLatch portReady = new CountDownLatch(1);

    public DistributedFileTransferServer(
            File file,
            String sender,
            String recipient) {

        this.file = file;
        this.sender = sender;
        this.recipient = recipient;
    }

    // =========================================================
    // GET TRANSFER PORT
    // =========================================================

    public int getTransferPort() {
        return transferPort;
    }

    // =========================================================
    // WAIT UNTIL PORT IS READY
    // =========================================================

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

    // =========================================================
    // RUN
    // =========================================================

    @Override
    public void run() {

        try (ServerSocket serverSocket = new ServerSocket(0)) {

            // -----------------------------------------------------
            // OS automatically selects a free port
            // -----------------------------------------------------

            transferPort = serverSocket.getLocalPort();

            System.out.println();
            System.out.println(
                    "[FILE-DIST] Transfer server started.");

            System.out.println(
                    "[FILE-DIST] Sender: "
                            + sender);

            System.out.println(
                    "[FILE-DIST] Recipient: "
                            + recipient);

            System.out.println(
                    "[FILE-DIST] File: "
                            + file.getName());

            System.out.println(
                    "[FILE-DIST] Size: "
                            + file.length()
                            + " bytes");

            System.out.println(
                    "[FILE-DIST] Listening on port: "
                            + transferPort);

            // -----------------------------------------------------
            // Tell waiting thread that port is ready
            // -----------------------------------------------------

            portReady.countDown();

            // -----------------------------------------------------
            // Wait for Server 2
            // -----------------------------------------------------

            Socket socket = serverSocket.accept();

            System.out.println(
                    "[FILE-DIST] Server-to-server "
                            + "connection established.");

            try (
                    DataOutputStream output = new DataOutputStream(
                            socket.getOutputStream());

                    FileInputStream fileInput = new FileInputStream(file)) {

                // -------------------------------------------------
                // Send file metadata
                // -------------------------------------------------

                output.writeUTF(
                        file.getName());

                output.writeLong(
                        file.length());

                // -------------------------------------------------
                // Send binary file data
                // -------------------------------------------------

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
                        "[FILE-DIST] File transferred "
                                + "successfully.");

                System.out.println(
                        "[FILE-DIST] Total bytes sent: "
                                + totalSent);
            }

            socket.close();

        } catch (IOException e) {

            // Make sure awaitPort() does not wait forever
            portReady.countDown();

            System.out.println(
                    "[FILE-DIST] Transfer error: "
                            + e.getMessage());
        }
    }
}