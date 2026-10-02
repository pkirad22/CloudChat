package api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class BackendSocketConnection {

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private String serverId;
    private String host;
    private int port;

    public BackendSocketConnection(
            String serverId,
            String host,
            int port) {

        this.serverId = serverId;
        this.host = host;
        this.port = port;
    }

    // =========================================================
    // CONNECT
    // =========================================================

    public boolean connect() {

        try {

            System.out.println(
                    "[BRIDGE] Connecting to "
                            + serverId
                            + " at "
                            + host
                            + ":"
                            + port);

            socket = new Socket(host, port);

            input = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream()));

            output = new PrintWriter(
                    socket.getOutputStream(),
                    true);

            System.out.println(
                    "[BRIDGE] Connected to "
                            + serverId);

            return true;

        } catch (IOException e) {

            System.out.println(
                    "[BRIDGE] Unable to connect to "
                            + serverId
                            + ": "
                            + e.getMessage());

            close();

            return false;
        }
    }

    // =========================================================
    // READ MESSAGE
    // =========================================================

    public String readMessage()
            throws IOException {

        if (input == null) {
            return null;
        }

        return input.readLine();
    }

    // =========================================================
    // SEND MESSAGE
    // =========================================================

    public synchronized void sendMessage(
            String message) {

        if (output == null) {
            return;
        }

        output.println(message);
        output.flush();
    }

    // =========================================================
    // CHECK CONNECTION
    // =========================================================

    public boolean isConnected() {

        return socket != null
                && socket.isConnected()
                && !socket.isClosed();
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public String getServerId() {
        return serverId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Socket getSocket() {
        return socket;
    }

    // =========================================================
    // CLOSE
    // =========================================================

    public synchronized void close() {

        if (socket == null) {
            return;
        }

        System.out.println(
                "[BACKEND] Closing socket for "
                        + serverId
                        + " at "
                        + host
                        + ":"
                        + port);

        try {

            /*
             * Explicitly signal the server that no more data
             * will be sent on this connection.
             */
            if (!socket.isOutputShutdown()) {

                socket.shutdownOutput();

                System.out.println(
                        "[BACKEND] Output shutdown.");
            }

        } catch (IOException e) {

            System.out.println(
                    "[BACKEND] Output shutdown error: "
                            + e.getMessage());
        }

        try {

            /*
             * Close the input side as well.
             */
            if (!socket.isInputShutdown()) {

                socket.shutdownInput();

                System.out.println(
                        "[BACKEND] Input shutdown.");
            }

        } catch (IOException e) {

            System.out.println(
                    "[BACKEND] Input shutdown error: "
                            + e.getMessage());
        }

        try {

            if (!socket.isClosed()) {

                socket.close();

                System.out.println(
                        "[BACKEND] Socket closed.");
            }

        } catch (IOException e) {

            System.out.println(
                    "[BACKEND] Error closing socket: "
                            + e.getMessage());

        } finally {

            input = null;
            output = null;
            socket = null;
        }
    }
}