package database;

public class MongoDBTest {

    public static void main(String[] args) {

        System.out.println(
                "Starting MongoDB connection test..."
        );

        MongoDBConnection.connect();

        MongoDBConnection.close();

        System.out.println(
                "MongoDB test completed."
        );
    }
}