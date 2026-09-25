package api;

public class BackendPrivateMessageTest {

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

        if (!authenticated) {

            System.out.println(
                    "[TEST] BACKEND AUTHENTICATION FAILED");

            connection.close();

            return;
        }

        System.out.println(
                "[TEST] BACKEND AUTHENTICATION SUCCESS");

        // =====================================================
        // SEND REAL CLOUDCHAT PRIVATE MESSAGE
        // =====================================================

        boolean sent = connection.sendPrivateMessage(
                "Rahul",
                "Hello Rahul, this message came through the bridge backend!");

        if (sent) {

            System.out.println(
                    "[TEST] PRIVATE MESSAGE SENT TO BACKEND");

        } else {

            System.out.println(
                    "[TEST] FAILED TO SEND PRIVATE MESSAGE");
        }

        // =====================================================
        // READ BACKEND RESPONSE
        // =====================================================

        try {

            String response = connection.readResponse();

            if (response != null) {

                System.out.println(
                        "[TEST] BACKEND RESPONSE: "
                                + response);
            }

        } catch (Exception e) {

            System.out.println(
                    "[TEST] Error reading backend response: "
                            + e.getMessage());
        }

        connection.close();

        System.out.println(
                "[TEST] Backend connection closed.");
    }
}