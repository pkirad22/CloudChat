package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import database.MongoDBConnection;
import model.User;

import org.bson.Document;

import org.mindrot.jbcrypt.BCrypt;

import static com.mongodb.client.model.Filters.eq;

public class UserService {

        private final MongoCollection<Document> users;

        public UserService() {

                MongoDatabase database = MongoDBConnection.getDatabase();

                users = database.getCollection("users");
        }

        // =========================
        // REGISTER USER
        // =========================

        public boolean registerUser(User user) {

                try {

                        // Check username
                        Document existingUser = users.find(
                                        eq("username",
                                                        user.getUsername()))
                                        .first();

                        if (existingUser != null) {

                                System.out.println(
                                                "Username already exists.");

                                return false;
                        }

                        // Hash password
                        String hashedPassword = BCrypt.hashpw(
                                        user.getPassword(),
                                        BCrypt.gensalt());

                        Document document = new Document(
                                        "username",
                                        user.getUsername())
                                        .append(
                                                        "password",
                                                        hashedPassword)
                                        .append(
                                                        "email",
                                                        user.getEmail());

                        users.insertOne(document);

                        System.out.println(
                                        "User registered: "
                                                        + user.getUsername());

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Registration failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================
        // CHECK USER EXISTS
        // =========================

        public boolean userExists(String username) {

                try {

                        if (username == null
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        Document existingUser = users.find(
                                        eq("username",
                                                        username.trim()))
                                        .first();

                        return existingUser != null;

                } catch (Exception e) {

                        System.out.println(
                                        "User existence check failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================
        // SEARCH USERS
        // =========================

        public java.util.List<String> searchUsers(String searchText) {

                java.util.List<String> usernames = new java.util.ArrayList<>();

                try {

                        if (searchText == null
                                        || searchText.trim().isEmpty()) {

                                return usernames;
                        }

                        String search = searchText.trim();

                        /*
                         * Case-insensitive username search.
                         *
                         * Example:
                         * "rah" -> Rahul
                         * "AM" -> Amul
                         */

                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                                        "^"
                                                        + java.util.regex.Pattern
                                                                        .quote(search),
                                        java.util.regex.Pattern.CASE_INSENSITIVE);

                        for (Document document : users.find(
                                        new Document("username",
                                                        pattern))) {

                                String username = document.getString("username");

                                if (username != null) {

                                        usernames.add(username);
                                }
                        }

                        return usernames;

                } catch (Exception e) {

                        System.out.println(
                                        "User search failed: "
                                                        + e.getMessage());

                        return usernames;
                }
        }

        // =========================
        // LOGIN USER
        // =========================

        public User loginUser(
                        String username,
                        String password) {

                try {

                        Document document = users.find(
                                        eq("username",
                                                        username))
                                        .first();

                        if (document == null) {

                                return null;
                        }

                        String hashedPassword = document.getString("password");

                        // Verify password
                        boolean validPassword = BCrypt.checkpw(
                                        password,
                                        hashedPassword);

                        if (!validPassword) {

                                return null;
                        }

                        String email = document.getString("email");

                        return new User(
                                        username,
                                        hashedPassword,
                                        email);

                } catch (Exception e) {

                        System.out.println(
                                        "Login failed: "
                                                        + e.getMessage());

                        return null;
                }
        }
}