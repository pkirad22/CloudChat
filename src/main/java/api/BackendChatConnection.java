package api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class BackendChatConnection {

    private final String serverAddress;
    private final int serverPort;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    public BackendChatConnection(
            String serverAddress,
            int serverPort) {

        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public boolean connect() {

        try {

            socket = new Socket(
                    serverAddress,
                    serverPort);

            input = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream()));

            output = new PrintWriter(
                    socket.getOutputStream(),
                    true);

            System.out.println(
                    "[BRIDGE] Connected to backend "
                            + serverAddress
                            + ":"
                            + serverPort);

            return true;

        } catch (IOException e) {

            System.out.println(
                    "[BRIDGE] Backend connection failed: "
                            + e.getMessage());

            return false;
        }
    }

    public String readLine()
            throws IOException {

        if (input == null) {
            return null;
        }

        return input.readLine();
    }

    public void send(String message) {

        if (output != null) {

            output.println(message);

            System.out.println(
                    "[BRIDGE → BACKEND] "
                            + message);
        }
    }

    public String readResponse() throws IOException {

        if (input == null) {
            return null;
        }

        String response = input.readLine();

        if (response != null) {

            System.out.println(
                    "[BACKEND → BRIDGE] "
                            + response);
        }

        return response;
    }

    public boolean sendPrivateMessage(
            String recipient,
            String message) {

        if (!isConnected()) {

            System.out.println(
                    "[BRIDGE] Backend connection is not active.");

            return false;
        }

        if (recipient == null
                || recipient.trim().isEmpty()) {

            return false;
        }

        if (message == null
                || message.trim().isEmpty()) {

            return false;
        }

        send(
                "/msg "
                        + recipient.trim()
                        + " "
                        + message.trim());

        return true;
    }

    public boolean isConnected() {

        return socket != null
                && socket.isConnected()
                && !socket.isClosed();
    }

    public boolean authenticate(
            String username,
            String password) {

        try {

            // =====================================================
            // WAIT FOR AUTH_REQUEST
            // SERVER_LOAD MAY ARRIVE FIRST
            // =====================================================

            String response;

            while (true) {

                response = readLine();

                if (response == null) {

                    System.out.println(
                            "[BRIDGE] Backend closed connection.");

                    return false;
                }

                System.out.println(
                        "[BACKEND → BRIDGE] "
                                + response);

                if (response.startsWith("SERVER_LOAD:")) {

                    System.out.println(
                            "[BRIDGE] Server load received: "
                                    + response);

                    continue;
                }

                if (response.equals("AUTH_REQUEST")) {
                    break;
                }

                System.out.println(
                        "[BRIDGE] Expected AUTH_REQUEST but received: "
                                + response);

                return false;
            }

            // =====================================================
            // SELECT LOGIN
            // =====================================================

            send("login");

            response = readLine();

            System.out.println(
                    "[BACKEND → BRIDGE] "
                            + response);

            if (!"USERNAME_REQUEST".equals(response)) {

                System.out.println(
                        "[BRIDGE] Expected USERNAME_REQUEST but received: "
                                + response);

                return false;
            }

            // =====================================================
            // USERNAME
            // =====================================================

            send(username);

            response = readLine();

            System.out.println(
                    "[BACKEND → BRIDGE] "
                            + response);

            if (!"PASSWORD_REQUEST".equals(response)) {

                System.out.println(
                        "[BRIDGE] Expected PASSWORD_REQUEST but received: "
                                + response);

                return false;
            }

            // =====================================================
            // PASSWORD
            // =====================================================

            send(password);

            response = readLine();

            System.out.println(
                    "[BACKEND → BRIDGE] "
                            + response);

            // =====================================================
            // LOGIN RESULT
            // =====================================================

            if (response != null
                    && response.startsWith("LOGIN_SUCCESS")) {

                System.out.println(
                        "[BRIDGE] Backend authentication successful for "
                                + username);

                return true;
            }

            System.out.println(
                    "[BRIDGE] Backend authentication failed: "
                            + response);

            return false;

        } catch (IOException e) {

            System.out.println(
                    "[BRIDGE] Authentication error: "
                            + e.getMessage());

            return false;
        }
    }

    public void close() {

        try {

            if (socket != null) {
                socket.close();
            }

        } catch (IOException e) {

            System.out.println(
                    "[BRIDGE] Error closing backend connection: "
                            + e.getMessage());
        }
    }
}