package database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.github.cdimascio.dotenv.Dotenv;

import org.bson.Document;

public class MongoDBConnection {

    private static MongoClient mongoClient;

    private static MongoDatabase database;

    public static void connect() {

        try {

            Dotenv dotenv =
                    Dotenv.configure()
                            .ignoreIfMissing()
                            .load();

            String connectionString =
                    dotenv.get("MONGODB_URI");

            if (connectionString == null ||
                    connectionString.isBlank()) {

                throw new IllegalStateException(
                        "MONGODB_URI is not configured."
                );
            }

            mongoClient =
                    MongoClients.create(
                            connectionString
                    );

            database =
                    mongoClient.getDatabase(
                            "CloudChat"
                    );

            // Test connection
            database.runCommand(
                    new Document(
                            "ping",
                            1
                    )
            );

            System.out.println(
                    "MongoDB connected successfully."
            );

            System.out.println(
                    "Database: CloudChat"
            );

        } catch (Exception e) {

            System.out.println(
                    "MongoDB connection failed."
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );
        }
    }

    public static MongoDatabase getDatabase() {

        return database;
    }

    public static void close() {

        if (mongoClient != null) {

            mongoClient.close();

            System.out.println(
                    "MongoDB connection closed."
            );
        }
    }
}