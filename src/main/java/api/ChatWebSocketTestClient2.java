package api;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ChatWebSocketTestClient2 extends WebSocketClient {

    public ChatWebSocketTestClient2() {
        super(URI.create("ws://localhost:9001"));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {

        System.out.println("[TEST-2] WebSocket connected.");

        send("""
                {
                    "type": "AUTH",
                    "username": "Rahul"
                }
                """);
    }

    @Override
    public void onMessage(String message) {

        System.out.println("[TEST-2] RECEIVED:");
        System.out.println(message);
    }

    @Override
    public void onClose(
            int code,
            String reason,
            boolean remote) {

        System.out.println(
                "[TEST-2] Connection closed: "
                        + reason);
    }

    @Override
    public void onError(Exception ex) {

        System.out.println(
                "[TEST-2] WebSocket error:");

        ex.printStackTrace();
    }

    public static void main(String[] args)
            throws Exception {

        ChatWebSocketTestClient2 client = new ChatWebSocketTestClient2();

        client.connect();

        Thread.sleep(30000);

        client.close();
    }
}