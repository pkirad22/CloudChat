package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransferServer implements Runnable {

    private static final int FILE_PORT = 5001;

    private final String recipient;
    private final File file;

    public FileTransferServer(
            String recipient,
            File file) {

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

                // Send file name
                output.writeUTF(
                        file.getName());

                // Send file size
                output.writeLong(
                        file.length());

                // Send file data
                byte[] buffer = new byte[8192];

                int bytesRead;

                while ((bytesRead = fileInput.read(buffer)) != -1) {

                    output.write(
                            buffer,
                            0,
                            bytesRead);
                }

                output.flush();

                System.out.println(
                        "File sent successfully to "
                                + recipient);
            }

            socket.close();

        } catch (IOException e) {

            System.out.println(
                    "File transfer error: "
                            + e.getMessage());
        }
    }
}