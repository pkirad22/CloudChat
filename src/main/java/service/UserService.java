package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import database.MongoDBConnection;
import model.User;

import org.bson.Document;

import org.mindrot.jbcrypt.BCrypt;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.result.UpdateResult;
import java.util.regex.Pattern;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.or;

import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
                                                        user.getEmail())
                                        .append("mobileNumber",
                                                        user.getMobileNumber())
                                        .append("emailVerified", false)
                                        .append("authProvider", "PASSWORD")
                                        .append("createdAt", new Date());

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

        public User findOrCreateGoogleUser(
                        GoogleAuthService.GoogleUser googleUser) {

                if (googleUser == null) {
                        throw new IllegalArgumentException(
                                        "Google user is required.");
                }

                String googleSub = googleUser.getSubject();

                String email = googleUser.getEmail()
                                .trim()
                                .toLowerCase(java.util.Locale.ROOT);

                if (googleSub == null
                                || googleSub.trim().isEmpty()
                                || email.isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Valid Google account details are required.");
                }

                // 1. Find an account already linked to this Google identity.
                Document linkedUser = users
                                .find(eq("googleSub", googleSub))
                                .first();

                if (linkedUser != null) {

                        // Block Google login for deactivated accounts.
                        String accountStatus = linkedUser.getString("accountStatus");

                        if ("DEACTIVATED".equalsIgnoreCase(accountStatus)) {
                                throw new AccountDeactivatedException();
                        }

                        return new User(
                                        linkedUser.getString("username"),
                                        null,
                                        linkedUser.getString("email"),
                                        linkedUser.getString("mobileNumber"));
                }

                // 2. Never automatically link Google to an existing email account.
                Pattern emailPattern = Pattern.compile(
                                "^" + Pattern.quote(email) + "$",
                                Pattern.CASE_INSENSITIVE);

                Document existingEmailUser = users
                                .find(new Document("email", emailPattern))
                                .first();

                if (existingEmailUser != null) {

                        // Also reject a deactivated account found by email.
                        String accountStatus = existingEmailUser.getString("accountStatus");

                        if ("DEACTIVATED".equalsIgnoreCase(accountStatus)) {
                                throw new AccountDeactivatedException();
                        }

                        throw new GoogleAccountConflictException(
                                        "An account with this email already exists. "
                                                        + "Please log in using your existing CloudChat account.");
                }

                // 3. Generate an available CloudChat username.
                String baseUsername = email.split("@", 2)[0]
                                .replaceAll("[^A-Za-z0-9_]", "_");

                if (baseUsername.length() < 3) {
                        baseUsername = "user_" + baseUsername;
                }

                if (baseUsername.length() > 24) {
                        baseUsername = baseUsername.substring(0, 24);
                }

                String username = baseUsername;
                int suffix = 1;

                while (users.find(
                                new Document(
                                                "username",
                                                Pattern.compile(
                                                                "^" + Pattern.quote(username) + "$",
                                                                Pattern.CASE_INSENSITIVE)))
                                .first() != null) {

                        String ending = "_" + suffix++;
                        int maxBaseLength = 30 - ending.length();

                        String shortenedBase = baseUsername.length() > maxBaseLength
                                        ? baseUsername.substring(0, maxBaseLength)
                                        : baseUsername;

                        username = shortenedBase + ending;
                }

                // 4. Create a new Google-linked account.
                Document newUser = new Document(
                                "username", username)
                                .append("email", email)
                                .append("mobileNumber", null)
                                .append("emailVerified", true)
                                .append("googleSub", googleSub)
                                .append("authProvider", "GOOGLE")
                                .append("accountStatus", "ACTIVE")
                                .append("createdAt", new Date());

                users.insertOne(newUser);

                System.out.println(
                                "Google account created: " + username);

                return new User(
                                username,
                                null,
                                email,
                                null);
        }

        public static class GoogleAccountConflictException
                        extends RuntimeException {

                public GoogleAccountConflictException(String message) {
                        super(message);
                }
        }

        // =========================================================
        // CHECK WHETHER AN ACCOUNT IS ACTIVE
        // Legacy accounts without accountStatus are treated as ACTIVE.
        // =========================================================

        public void ensureAccountActive(String username) {

                if (username == null || username.trim().isEmpty()) {
                        throw new IllegalArgumentException("Username is required.");
                }

                Document user = users.find(
                                eq("username", username.trim())).first();

                if (user == null) {
                        throw new IllegalArgumentException("CloudChat account was not found.");
                }

                String accountStatus = user.getString("accountStatus");

                if ("DEACTIVATED".equalsIgnoreCase(accountStatus)) {
                        throw new AccountDeactivatedException();
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
        // CHECK EMAIL EXISTS
        // =========================

        public boolean emailExists(String email) {
                if (email == null || email.trim().isEmpty()) {
                        return false;
                }

                Document existingUser = users
                                .find(new Document("email", email.trim().toLowerCase()))
                                .first();

                return existingUser != null;
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

        public static class EmailNotVerifiedException extends RuntimeException {
                public EmailNotVerifiedException() {
                        super("Please verify your email before logging in.");
                }
        }

        public static class AccountDeactivatedException extends RuntimeException {
                public AccountDeactivatedException() {
                        super("Your CloudChat account has been deactivated. Please contact the administrator.");
                }
        }

        public User loginUser(
                        String username,
                        String password) {

                try {

                        Document document = users.find(
                                        eq("username", username.trim()))
                                        .first();

                        if (document == null) {
                                return null;
                        }

                        String hashedPassword = document.getString("password");

                        if (hashedPassword == null || hashedPassword.isEmpty()) {
                                return null;
                        }

                        // Verify password first.
                        boolean validPassword = BCrypt.checkpw(
                                        password,
                                        hashedPassword);

                        if (!validPassword) {
                                return null;
                        }

                        // Reject deactivated accounts.
                        // Legacy accounts without accountStatus are treated as ACTIVE.
                        String accountStatus = document.getString("accountStatus");

                        if ("DEACTIVATED".equalsIgnoreCase(accountStatus)) {
                                throw new AccountDeactivatedException();
                        }

                        // Only check verification after the password is correct.
                        if (!Boolean.TRUE.equals(
                                        document.getBoolean("emailVerified"))) {
                                throw new EmailNotVerifiedException();
                        }

                        String email = document.getString("email");

                        return new User(
                                        username.trim(),
                                        hashedPassword,
                                        email);

                } catch (EmailNotVerifiedException e) {
                        // Let AuthApiHandler return the appropriate response.
                        throw e;

                } catch (AccountDeactivatedException e) {
                        throw e;
                }

                catch (Exception e) {

                        System.out.println(
                                        "Login failed: " + e.getMessage());

                        return null;
                }
        }

        // =========================================================
        // UPDATE USERNAME
        // =========================================================

        public boolean updateUsername(String currentUsername, String currentPassword, String newUsername) {
                if (currentUsername == null || newUsername == null) {
                        throw new IllegalArgumentException("Both usernames are required.");
                }

                currentUsername = currentUsername.trim();
                newUsername = newUsername.trim();

                if (currentUsername.isEmpty() || newUsername.isEmpty()) {
                        throw new IllegalArgumentException("Both usernames are required.");
                }

                if (!newUsername.matches("^[A-Za-z0-9_]{3,30}$")) {
                        throw new IllegalArgumentException(
                                        "Username must be 3-30 characters and contain only letters, numbers or underscores.");
                }

                // Find the account using its current username.
                Document currentUser = users.find(eq("username", currentUsername)).first();

                if (currentUser == null) {
                        throw new IllegalArgumentException("Current user was not found.");
                }

                if (currentUsername.equals(newUsername)) {
                        throw new IllegalArgumentException(
                                        "The new username must be different from the current username.");
                }

                String storedHash = currentUser.getString("password");

                if (storedHash == null || storedHash.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "This account does not have a password set. Username changes require additional account verification.");
                }

                if (currentPassword == null || currentPassword.isEmpty()
                                || !BCrypt.checkpw(currentPassword, storedHash)) {
                        throw new IllegalArgumentException("Current password is incorrect.");
                }

                // Match usernames case-insensitively, consistent with Google username
                // generation.
                Pattern duplicatePattern = Pattern.compile(
                                "^" + Pattern.quote(newUsername) + "$",
                                Pattern.CASE_INSENSITIVE);

                Document duplicate = users.find(
                                new Document("username", duplicatePattern)).first();

                if (duplicate != null) {
                        throw new IllegalArgumentException("Username already exists.");
                }

                UpdateResult result = users.updateOne(
                                eq("username", currentUsername),
                                new Document("$set", new Document("username", newUsername)));

                return result.getModifiedCount() == 1;
        }

        // =========================================================
        // CHANGE PASSWORD
        // =========================================================

        public boolean changePassword(
                        String username,
                        String currentPassword,
                        String newPassword) {

                if (username == null || username.trim().isEmpty()
                                || currentPassword == null || currentPassword.isEmpty()
                                || newPassword == null || newPassword.isEmpty()) {
                        throw new IllegalArgumentException("All password fields are required.");
                }

                if (newPassword.length() < 8 || newPassword.length() > 128
                                || newPassword.contains(" ")
                                || !newPassword.matches(".*[A-Z].*")
                                || !newPassword.matches(".*[a-z].*")
                                || !newPassword.matches(".*[0-9].*")
                                || !newPassword.matches(".*[^A-Za-z0-9].*")) {
                        throw new IllegalArgumentException(
                                        "Password must be 8-128 characters and contain uppercase, lowercase, number and special character with no spaces.");
                }

                Document user = users.find(eq("username", username.trim())).first();

                if (user == null) {
                        throw new IllegalArgumentException("User was not found.");
                }

                String storedHash = user.getString("password");

                // Google-only accounts may not have a password yet.
                if (storedHash == null || storedHash.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "This account does not have a password set. Password setup must be handled separately.");
                }

                if (!BCrypt.checkpw(currentPassword, storedHash)) {
                        throw new IllegalArgumentException("Current password is incorrect.");
                }

                if (BCrypt.checkpw(newPassword, storedHash)) {
                        throw new IllegalArgumentException(
                                        "Your new password must be different from your current password.");
                }

                String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());

                UpdateResult result = users.updateOne(
                                eq("username", username.trim()),
                                new Document("$set", new Document("password", newHash)));

                return result.getModifiedCount() == 1;
        }

        // =========================================================
        // GET SAFE PROFILE
        // =========================================================

        public Document getProfile(String username) {
                if (username == null || username.trim().isEmpty()) {
                        return null;
                }

                Document user = users.find(eq("username", username.trim())).first();

                if (user == null) {
                        return null;
                }

                String passwordHash = user.getString("password");
                String provider = user.getString("authProvider");

                if (provider == null || provider.trim().isEmpty()) {
                        provider = user.getString("googleSub") != null
                                        ? "GOOGLE"
                                        : "PASSWORD";
                }

                return new Document("username", user.getString("username"))
                                .append("email", user.getString("email"))
                                .append("authProvider", provider)
                                .append("hasPassword",
                                                passwordHash != null && !passwordHash.isEmpty());
        }

        // =========================================================
        // PASSWORD VALIDATION
        // =========================================================

        private void validateProfilePassword(String password) {
                if (password == null
                                || password.length() < 8
                                || password.length() > 128
                                || password.contains(" ")
                                || !password.matches(".*[A-Z].*")
                                || !password.matches(".*[a-z].*")
                                || !password.matches(".*[0-9].*")
                                || !password.matches(".*[^A-Za-z0-9].*")) {
                        throw new IllegalArgumentException(
                                        "Password must be 8-128 characters and contain uppercase, lowercase, number and special character with no spaces.");
                }
        }

        // =========================================================
        // SET FIRST PASSWORD FOR GOOGLE ACCOUNT
        // =========================================================

        public boolean setGoogleAccountPassword(
                        String verifiedGoogleSub,
                        String newPassword) {

                if (verifiedGoogleSub == null || verifiedGoogleSub.trim().isEmpty()) {
                        throw new IllegalArgumentException("Verified Google identity is required.");
                }

                validateProfilePassword(newPassword);

                Document account = users.find(eq("googleSub", verifiedGoogleSub)).first();

                if (account == null) {
                        throw new IllegalArgumentException(
                                        "No CloudChat account is linked to this Google identity.");
                }

                String existingHash = account.getString("password");

                if (existingHash != null && !existingHash.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "A password is already set. Use Change Password instead.");
                }

                String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());

                // Atomic check: only set a password when the field is absent or null.
                UpdateResult result = users.updateOne(
                                and(
                                                eq("googleSub", verifiedGoogleSub),
                                                or(
                                                                exists("password", false),
                                                                eq("password", null))),
                                new Document("$set", new Document("password", newHash)));

                if (result.getModifiedCount() == 1) {
                        return true;
                }

                throw new IllegalArgumentException(
                                "A password may already have been set. Refresh your profile and try again.");
        }

        // =========================================================
        // UPDATE GOOGLE ACCOUNT USERNAME
        // =========================================================

        public boolean updateGoogleUsernameBySub(
                        String verifiedGoogleSub,
                        String newUsername) {

                if (verifiedGoogleSub == null || verifiedGoogleSub.trim().isEmpty()
                                || newUsername == null) {
                        throw new IllegalArgumentException(
                                        "Verified Google identity and new username are required.");
                }

                newUsername = newUsername.trim();

                if (!newUsername.matches("^[A-Za-z0-9_]{3,30}$")) {
                        throw new IllegalArgumentException(
                                        "Username must be 3-30 characters and contain only letters, numbers or underscores.");
                }

                Document account = users.find(eq("googleSub", verifiedGoogleSub)).first();

                if (account == null) {
                        throw new IllegalArgumentException(
                                        "No CloudChat account is linked to this Google identity.");
                }

                String currentUsername = account.getString("username");

                if (currentUsername.equals(newUsername)) {
                        throw new IllegalArgumentException(
                                        "The new username must be different from the current username.");
                }

                Pattern duplicatePattern = Pattern.compile(
                                "^" + Pattern.quote(newUsername) + "$",
                                Pattern.CASE_INSENSITIVE);

                Document duplicate = users.find(
                                new Document("username", duplicatePattern)).first();

                if (duplicate != null) {
                        throw new IllegalArgumentException("Username already exists.");
                }

                UpdateResult result = users.updateOne(
                                and(
                                                eq("googleSub", verifiedGoogleSub),
                                                eq("username", currentUsername)),
                                new Document("$set", new Document("username", newUsername)));

                return result.getModifiedCount() == 1;
        }

        // =========================================================
        // ADMIN: LIST REGISTERED USERS SAFELY
        // Excludes password hashes and authentication secrets.
        // =========================================================

        public Document getAdminUsers(
                        String searchText,
                        int page,
                        int pageSize) {

                int safePage = Math.max(1, page);
                int safePageSize = Math.max(1, Math.min(pageSize, 100));

                String search = searchText == null ? "" : searchText.trim();

                org.bson.conversions.Bson filter = new Document();

                if (!search.isEmpty()) {
                        Pattern searchPattern = Pattern.compile(
                                        Pattern.quote(search),
                                        Pattern.CASE_INSENSITIVE);

                        filter = or(
                                        new Document("username", searchPattern),
                                        new Document("email", searchPattern),
                                        new Document("mobileNumber", searchPattern));
                }

                long total = users.countDocuments(filter);

                List<Document> safeUsers = new ArrayList<>();

                for (Document user : users.find(filter)
                                .projection(Projections.include(
                                                "username",
                                                "email",
                                                "mobileNumber",
                                                "emailVerified",
                                                "authProvider",
                                                "createdAt",
                                                "accountStatus"))
                                .sort(new Document("username", 1))
                                .skip((safePage - 1) * safePageSize)
                                .limit(safePageSize)) {

                        String provider = user.getString("authProvider");

                        if (provider == null || provider.isBlank()) {
                                provider = "PASSWORD";
                        }

                        Document safeUser = new Document()
                                        .append("username", user.getString("username"))
                                        .append("email", user.getString("email"))
                                        .append("mobileNumber", user.getString("mobileNumber"))
                                        .append("emailVerified",
                                                        Boolean.TRUE.equals(user.getBoolean("emailVerified")))
                                        .append("authProvider", provider)
                                        .append("accountStatus",
                                                        "DEACTIVATED".equalsIgnoreCase(
                                                                        user.getString("accountStatus"))
                                                                                        ? "DEACTIVATED"
                                                                                        : "ACTIVE");
                        ;

                        Object createdAt = user.get("createdAt");

                        if (createdAt instanceof Date) {
                                safeUser.append("createdAt", createdAt);
                        } else {
                                safeUser.append("createdAt", null);
                        }

                        safeUsers.add(safeUser);
                }

                int totalPages = (int) Math.ceil(
                                (double) total / safePageSize);

                return new Document("users", safeUsers)
                                .append("total", total)
                                .append("page", safePage)
                                .append("pageSize", safePageSize)
                                .append("totalPages", totalPages);
        }

        // =========================================================
        // ADMIN: DEACTIVATE / REACTIVATE USER
        // =========================================================

        public boolean updateAccountStatus(String username, String status) {
                if (username == null || username.trim().isEmpty()) {
                        throw new IllegalArgumentException("Username is required.");
                }

                if (status == null) {
                        throw new IllegalArgumentException(
                                        "Status must be ACTIVE or DEACTIVATED.");
                }

                String normalizedStatus = status.trim().toUpperCase(
                                java.util.Locale.ROOT);

                if (!normalizedStatus.equals("ACTIVE")
                                && !normalizedStatus.equals("DEACTIVATED")) {
                        throw new IllegalArgumentException(
                                        "Status must be ACTIVE or DEACTIVATED.");
                }

                UpdateResult result = users.updateOne(
                                eq("username", username.trim()),
                                new Document("$set",
                                                new Document("accountStatus", normalizedStatus)
                                                                .append("statusUpdatedAt", new Date())));

                return result.getMatchedCount() == 1;
        }

        // =========================================================
        // ADMIN: REMOVE USER ACCOUNT
        // Note: This deletes only the user document.
        // Associated chats and files are not deleted.
        // =========================================================

        public boolean removeUserAccount(String username) {

                if (username == null || username.trim().isEmpty()) {
                        throw new IllegalArgumentException("Username is required.");
                }

                com.mongodb.client.result.DeleteResult result = users.deleteOne(eq("username", username.trim()));

                return result.getDeletedCount() == 1;
        }

}