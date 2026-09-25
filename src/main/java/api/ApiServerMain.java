package api;

import database.MongoDBConnection;

public class ApiServerMain {

    public static void main(String[] args) {

        try {

            // =================================================
            // CONNECT TO MONGODB
            // =================================================

            MongoDBConnection.connect();

            System.out.println(
                    "[API] MongoDB connected.");

            // =================================================
            // START API BRIDGE
            // =================================================

            ApiServer apiServer = new ApiServer();

            apiServer.start();

        } catch (Exception e) {

            System.out.println(
                    "[API] Failed to start API Bridge.");

            e.printStackTrace();
        }
    }
}