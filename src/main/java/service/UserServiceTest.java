package service;

import database.MongoDBConnection;
import model.User;

public class UserServiceTest {

    public static void main(String[] args) {

        System.out.println(
                "Starting User Service Test..."
        );

        // Connect to MongoDB
        MongoDBConnection.connect();

        // Create service
        UserService userService =
                new UserService();

        // Create test user
        User user =
                new User(
                        "rahul",
                        "rahul123",
                        "rahul@gmail.com"
                );

        // Register
        boolean registered =
                userService.registerUser(user);

        System.out.println(
                "Registration result: "
                        + registered
        );

        // Login
        User loggedIn =
                userService.loginUser(
                        "rahul",
                        "rahul123"
                );

        if (loggedIn != null) {

            System.out.println(
                    "Login successful!"
            );

            System.out.println(
                    "Welcome "
                            + loggedIn.getUsername()
            );

        } else {

            System.out.println(
                    "Login failed!"
            );
        }

        MongoDBConnection.close();
    }
}