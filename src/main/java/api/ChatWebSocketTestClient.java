package api;

import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ChatWebSocketTestClient extends WebSocketClient {

    public ChatWebSocketTestClient() {

        super(URI.create("ws://localhost:9001"));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {

        System.out.println("[TEST] WebSocket connected.");

        send("""
                {
                    "type": "AUTH",
                    "username": "Pranav"
                    "password": "pranav@123"
                }
                """);
    }

    @Override
    public void onMessage(String message) {

        System.out.println("[TEST] RECEIVED:");
        System.out.println(message);

        if (message.contains("AUTH_SUCCESS")) {

            send("""
                    {
                       "type": "PRIVATE_MESSAGE",
                        "to": "Rahul",
                        "message": "Hello Rahul, this is a WebSocket test!"
                    }
                    """);
        }
    }

    @Override
    public void onClose(
            int code,
            String reason,
            boolean remote) {

        System.out.println(
                "[TEST] Connection closed: "
                        + reason);
    }

    @Override
    public void onError(Exception ex) {

        System.out.println(
                "[TEST] WebSocket error:");

        ex.printStackTrace();
    }

    public static void main(String[] args)
            throws Exception {

        ChatWebSocketTestClient client = new ChatWebSocketTestClient();

        client.connect();

        Thread.sleep(10000);

        client.close();
    }
}