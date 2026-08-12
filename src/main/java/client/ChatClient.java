package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {

    private static final String SERVER_ADDRESS =
            "localhost";

    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {

        System.out.println(
                "================================="
        );

        System.out.println(
                "        CloudChat Client"
        );

        System.out.println(
                "================================="
        );

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

            Scanner scanner =
                    new Scanner(System.in);

            // =================================
            // AUTHENTICATION
            // =================================

            boolean authenticated =
                    authenticate(
                            serverInput,
                            serverOutput,
                            scanner
                    );

            if (!authenticated) {

                socket.close();

                System.out.println(
                        "Disconnected from server."
                );

                return;
            }

            // =================================
            // RECEIVE MESSAGES FROM SERVER
            // =================================

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

                            System.out.println();

                            System.out.println(
                                    "Connection to server lost."
                            );
                        }
                    });

            receiveThread.start();

            // =================================
            // COMMAND MENU
            // =================================

            printCommandMenu();

            // =================================
            // SEND MESSAGES
            // =================================

            while (true) {

                System.out.print("You: ");

                if (!scanner.hasNextLine()) {
                        break;
                }

                String message =
                        scanner.nextLine();

                if (message == null) {
                        break;
                }

                serverOutput.println(message);

                if (message.equalsIgnoreCase("/exit")) {
                        break;
                }
        }
            socket.close();

            System.out.println(
                    "Disconnected from server."
            );

        } catch (IOException e) {

            System.out.println();

            System.out.println(
                    "Unable to connect to server."
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );
        }
    }

    // =========================================
    // AUTHENTICATION METHOD
    // =========================================

    private static boolean authenticate(
            BufferedReader serverInput,
            PrintWriter serverOutput,
            Scanner scanner) {

        while (true) {

            try {

                /*
                 * Server first sends:
                 *
                 * AUTH_REQUEST
                 */

                String authRequest =
                        serverInput.readLine();

                if (authRequest == null) {

                    System.out.println(
                            "Server disconnected."
                    );

                    return false;
                }

                if (!authRequest.equals(
                        "AUTH_REQUEST")) {

                    System.out.println(
                            "Unexpected server response: "
                                    + authRequest
                    );

                    return false;
                }

                // =================================
                // LOGIN MENU
                // =================================

                System.out.println();

                System.out.println(
                        "================================="
                );

                System.out.println(
                        "        CLOUDCHAT LOGIN"
                );

                System.out.println(
                        "================================="
                );

                System.out.println(
                        "1. Login"
                );

                System.out.println(
                        "2. Register"
                );

                System.out.println(
                        "3. Exit"
                );

                System.out.println(
                        "================================="
                );

                System.out.print(
                        "Enter choice: "
                );

                String choice =
                        scanner.nextLine()
                                .trim();

                // =================================
                // EXIT
                // =================================

                if (choice.equals("3")) {

                    serverOutput.println(
                            "exit"
                    );

                    return false;
                }

                // =================================
                // LOGIN
                // =================================

               if (choice.equals("1")) {

    boolean loginSuccessful =
            performLogin(
                    serverInput,
                    serverOutput,
                    scanner
            );

                if (loginSuccessful) {

                        return true;
                }

                // Login failed.
                // Go back to authentication menu.
                continue;
                }

                // =================================
                // REGISTER
                // =================================

                if (choice.equals("2")) {

                    performRegistration(
                            serverInput,
                            serverOutput,
                            scanner
                    );

                    /*
                     * After registration we DON'T
                     * return true.
                     *
                     * The user must login.
                     */

                    continue;
                }

                System.out.println();

                System.out.println(
                        "Invalid choice."
                );

            } catch (IOException e) {

                System.out.println();

                System.out.println(
                        "Authentication error: "
                                + e.getMessage()
                );

                return false;
            }
        }
    }

    // =========================================
    // LOGIN
    // =========================================

    private static boolean performLogin(
            BufferedReader serverInput,
            PrintWriter serverOutput,
            Scanner scanner)
            throws IOException {

        // Tell server that the user selected LOGIN        
        serverOutput.println("login");

        /*
         * Server expects:
         *
         * USERNAME_REQUEST
         */

        String request =
                serverInput.readLine();

        if (!"USERNAME_REQUEST".equals(
                request)) {

            System.out.println(
                    "Unexpected server response: "
                            + request
            );

            return false;
        }

        System.out.print(
                "Enter username: "
        );

        String username =
                scanner.nextLine().trim();

        serverOutput.println(
                username
        );

        /*
         * Server now asks for password.
         */

        request =
                serverInput.readLine();

        if (!"PASSWORD_REQUEST".equals(
                request)) {

            System.out.println(
                    "Unexpected server response: "
                            + request
            );

            return false;
        }

        System.out.print(
                "Enter password: "
        );

        String password =
                scanner.nextLine();

        serverOutput.println(
                password
        );

        /*
         * Server checks MongoDB + BCrypt.
         */

        String response =
                serverInput.readLine();

        if (response == null) {

            System.out.println(
                    "Server disconnected."
            );

            return false;
        }

        System.out.println();

        System.out.println(
                response
        );

        // =================================
        // LOGIN SUCCESS
        // =================================

        if (response.startsWith(
                "LOGIN_SUCCESS")) {

            System.out.println();

            System.out.println(
                    "Authentication successful!"
            );

            return true;
        }

        // =================================
        // LOGIN FAILED
        // =================================

        System.out.println();

        System.out.println(
                "Login failed."
        );

        /*
         * IMPORTANT:
         *
         * We return false here because the
         * current server authentication loop
         * will send AUTH_REQUEST again.
         *
         * However, because the server keeps the
         * connection open, we need to continue
         * authentication rather than closing.
         */

        return false;
    }

    // =========================================
    // REGISTRATION
    // =========================================

    private static void performRegistration(
            BufferedReader serverInput,
            PrintWriter serverOutput,
            Scanner scanner)
            throws IOException {

        /*
         * Tell server that we want registration.
         */

        serverOutput.println(
                "register"
        );

        // =================================
        // USERNAME
        // =================================

        String request =
                serverInput.readLine();

        if (!"USERNAME_REQUEST".equals(
                request)) {

            System.out.println(
                    "Unexpected server response: "
                            + request
            );

            return;
        }

        System.out.print(
                "Enter username: "
        );

        String username =
                scanner.nextLine().trim();

        serverOutput.println(
                username
        );

        // =================================
        // EMAIL
        // =================================

        request =
                serverInput.readLine();

        if (!"EMAIL_REQUEST".equals(
                request)) {

            System.out.println(
                    "Unexpected server response: "
                            + request
            );

            return;
        }

        System.out.print(
                "Enter email: "
        );

        String email =
                scanner.nextLine().trim();

        serverOutput.println(
                email
        );

        // =================================
        // PASSWORD
        // =================================

        request =
                serverInput.readLine();

        if (!"PASSWORD_REQUEST".equals(
                request)) {

            System.out.println(
                    "Unexpected server response: "
                            + request
            );

            return;
        }

        System.out.print(
                "Enter password: "
        );

        String password =
                scanner.nextLine();

        serverOutput.println(
                password
        );

        // =================================
        // REGISTRATION RESULT
        // =================================

        String response =
                serverInput.readLine();

        if (response == null) {

            System.out.println(
                    "Server disconnected."
            );

            return;
        }

        System.out.println();

        System.out.println(
                response
        );

        if (response.startsWith(
                "REGISTER_SUCCESS")) {

            System.out.println();

            System.out.println(
                    "Registration completed."
            );

            System.out.println(
                    "Please login with your credentials."
            );

        } else {

            System.out.println();

            System.out.println(
                    "Registration failed."
            );
        }
    }
        // =========================================
    // COMMAND MENU
    // =========================================

    private static void printCommandMenu() {

        System.out.println();

        System.out.println(
                "================================="
        );

        System.out.println(
                "          CHAT COMMANDS"
        );

        System.out.println(
                "================================="
        );

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
    }

    // =========================================
    // FORMAT SERVER MESSAGES
    // =========================================

    private static String formatServerMessage(
            String message) {

        if (message == null) {

            return "";
        }

        // =================================
        // ONLINE USERS
        // =================================

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

            return "\n"
                    + "========== ONLINE USERS ==========\n"
                    + users
                    + "\n"
                    + "==================================";
        }

        // =================================
        // GROUPS
        // =================================

        if (message.startsWith(
                "GROUPS:")) {

            String groups =
                    message.substring(
                            "GROUPS:"
                                    .length()
                    );

            groups = groups.replace(
                    ",",
                    "\n"
            );

            return "\n"
                    + "============ GROUPS =============\n"
                    + groups
                    + "\n"
                    + "==================================";
        }

        return message;
    }
}