package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {

        System.out.println("=================================");
        System.out.println("        CloudChat Client");
        System.out.println("=================================");

        try {

            Socket socket =
                    new Socket(
                            SERVER_ADDRESS,
                            SERVER_PORT
                    );

            System.out.println(
                    "Connected to CloudChat Server."
            );

            BufferedReader serverInput =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream()
                            )
                    );

            PrintWriter serverOutput =
                    new PrintWriter(
                            socket.getOutputStream(),
                            true
                    );

            BufferedReader keyboardInput =
                    new BufferedReader(
                            new InputStreamReader(
                                    System.in
                            )
                    );

            // Username login
            String request =
                    serverInput.readLine();

            if ("USERNAME_REQUEST".equals(request)) {

                System.out.print(
                        "Enter your username: "
                );

                String username =
                        keyboardInput.readLine();

                serverOutput.println(username);
            }

            String loginResponse =
                    serverInput.readLine();

            if (loginResponse == null ||
                    loginResponse.startsWith("ERROR")) {

                System.out.println(
                        loginResponse
                );

                socket.close();

                return;
            }

            System.out.println();
            System.out.println(
                    loginResponse
            );

            // Receive messages from server
            Thread receiveThread =
                    new Thread(() -> {

                        try {

                            String serverMessage;

                            while ((serverMessage =
                                    serverInput.readLine())
                                    != null) {

                                System.out.println();
                                System.out.println(
                                        formatServerMessage(
                                                serverMessage
                                        )
                                );

                                System.out.print(
                                        "You: "
                                );
                            }

                        } catch (IOException e) {

                            System.out.println(
                                    "Connection to server lost."
                            );
                        }
                    });

            receiveThread.start();

            // Command menu
            System.out.println();
            System.out.println(
                    "================================="
            );
            System.out.println(
                    "          CHAT COMMANDS"
            );
            System.out.println(
                    "=================================");

            System.out.println(
                    "/users"
            );

            System.out.println(
                    "/msg username message"
            );

            System.out.println(
                    "/create group"
            );

            System.out.println(
                    "/join group"
            );

            System.out.println(
                    "/leave group"
            );

            System.out.println(
                    "/groups"
            );

            System.out.println(
                    "/members group"
            );

            System.out.println(
                    "/groupmsg group message"
            );

            System.out.println(
                    "/exit"
            );

            System.out.println(
                    "================================="
            );

            System.out.println();

            // Send messages
            while (true) {

                System.out.print("You: ");

                String message =
                        keyboardInput.readLine();

                if (message == null) {
                    break;
                }

                serverOutput.println(message);

                if (message.equalsIgnoreCase(
                        "/exit")) {

                    break;
                }
            }

            socket.close();

            System.out.println(
                    "Disconnected from server."
            );

        } catch (IOException e) {

            System.out.println(
                    "Unable to connect to server."
            );

            System.out.println(
                    "Error: " + e.getMessage()
            );
        }
    }

    private static String formatServerMessage(
            String message) {

        if (message.startsWith(
                "ONLINE_USERS:")) {

            String users =
                    message.substring(
                            "ONLINE_USERS:"
                                    .length()
                    );

            users = users.replace(
                    ",",
                    "\n"
            );

            return "\n========== ONLINE USERS ==========\n"
                    + users
                    + "==================================";
        }

        if (message.startsWith("GROUPS:")) {

            String groups =
                    message.substring(
                            "GROUPS:".length()
                    );

            groups = groups.replace(
                    ",",
                    "\n"
            );

            return "\n============ GROUPS =============\n"
                    + groups
                    + "==================================";
        }

        return message;
    }
}