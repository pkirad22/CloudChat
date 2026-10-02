package api;

import database.MongoDBConnection;

public class ApiServerMain {

    public static void main(String[] args) {

        try {

            // =================================================
            // MONGODB
            // =================================================

            MongoDBConnection.connect();

            System.out.println(
                    "[API] MongoDB connected.");

            // =================================================
            // REST API
            // =================================================

            ApiServer apiServer = new ApiServer();

            apiServer.start();

            System.out.println(
                    "[API] REST API started on port 9000.");

            // =================================================
            // WEBSOCKET BRIDGE
            // =================================================

            WebSocketBridgeServer webSocketBridgeServer = new WebSocketBridgeServer(9001);

            webSocketBridgeServer.start();

            System.out.println(
                    "[API] WebSocket Bridge starting on port 9001.");

        } catch (Exception e) {

            System.out.println(
                    "[API] Failed to start API Bridge.");

            e.printStackTrace();
        }
    }
}