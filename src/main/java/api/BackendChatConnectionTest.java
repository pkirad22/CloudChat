package api;

public class BackendChatConnectionTest {

    public static void main(String[] args) {

        BackendChatConnection connection = new BackendChatConnection(
                "localhost",
                5000);

        System.out.println(
                "[TEST] Connecting to CloudChat Server 1...");

        if (!connection.connect()) {

            System.out.println(
                    "[TEST] Backend connection failed.");

            return;
        }

        boolean authenticated = connection.authenticate(
                "Pranav",
                "pranav@123");

        if (authenticated) {

            System.out.println(
                    "[TEST] BACKEND AUTHENTICATION SUCCESS");

        } else {

            System.out.println(
                    "[TEST] BACKEND AUTHENTICATION FAILED");
        }

        connection.close();
    }
}