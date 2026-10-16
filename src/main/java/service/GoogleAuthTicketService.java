
package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import database.MongoDBConnection;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class GoogleAuthTicketService {

    private static final long INITIAL_TICKET_LIFETIME_MS = 5 * 60 * 1000L; // 5 minutes

    private static final long SESSION_LIFETIME_MS = 12 * 60 * 60 * 1000L; // 12 hours after first redemption

    private final MongoCollection<Document> tickets;
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleAuthTicketService() {
        MongoDatabase database = MongoDBConnection.getDatabase();
        tickets = database.getCollection("google_auth_tickets");
    }

    /**
     * Creates a short-lived ticket for a verified Google user.
     * Only the ticket hash is stored in MongoDB.
     */
    public Ticket createTicket(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }

        cleanupExpiredTickets();

        String token = generateSecureToken();
        String sessionId = generateSecureToken();

        Instant now = Instant.now();
        Date expiresAt = Date.from(
                now.plusMillis(INITIAL_TICKET_LIFETIME_MS));

        Document document = new Document(
                "ticketHash", sha256(token))
                .append("username", username.trim())
                .append("sessionId", sessionId)
                .append("createdAt", Date.from(now))
                .append("expiresAt", expiresAt)
                .append("redeemed", false);

        tickets.insertOne(document);

        return new Ticket(token, sessionId, username.trim(), expiresAt);
    }

    /**
     * Validates a ticket presented by a chat-server authentication flow.
     *
     * The first redemption must happen within five minutes.
     * After that, only the same session ID can reuse it, for up to
     * twelve hours from the first redemption. This permits server
     * failover without allowing a different session to reuse the ticket.
     */
    public boolean validateTicket(String token, String sessionId) {
        if (token == null || token.trim().isEmpty()
                || sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }

        Date now = new Date();

        Document ticket = tickets.find(
                Filters.and(
                        Filters.eq("ticketHash", sha256(token)),
                        Filters.eq("sessionId", sessionId)))
                .first();

        if (ticket == null) {
            return false;
        }

        Boolean redeemed = ticket.getBoolean("redeemed", false);

        if (Boolean.TRUE.equals(redeemed)) {
            Date sessionExpiresAt = ticket.getDate("sessionExpiresAt");

            return sessionExpiresAt != null
                    && sessionExpiresAt.after(now);
        }

        Date expiresAt = ticket.getDate("expiresAt");

        if (expiresAt == null || !expiresAt.after(now)) {
            return false;
        }

        Date sessionExpiresAt = new Date(
                now.getTime() + SESSION_LIFETIME_MS);

        // Atomically allow only one first redemption.
        var result = tickets.updateOne(
                Filters.and(
                        Filters.eq("_id", ticket.getObjectId("_id")),
                        Filters.eq("redeemed", false),
                        Filters.gt("expiresAt", now)),
                Updates.combine(
                        Updates.set("redeemed", true),
                        Updates.set("redeemedAt", now),
                        Updates.set("sessionExpiresAt", sessionExpiresAt)));

        return result.getModifiedCount() == 1;
    }

    /**
     * Returns the username associated with a valid ticket/session pair.
     * The chat server should call this after successful validation.
     */
    public String getUsername(String token, String sessionId) {
        if (token == null || sessionId == null) {
            return null;
        }

        Date now = new Date();

        Document ticket = tickets.find(
                Filters.and(
                        Filters.eq("ticketHash", sha256(token)),
                        Filters.eq("sessionId", sessionId),
                        Filters.eq("redeemed", true),
                        Filters.gt("sessionExpiresAt", now)))
                .first();

        return ticket == null ? null : ticket.getString("username");
    }

    private void cleanupExpiredTickets() {
        Date now = new Date();

        tickets.deleteMany(
                Filters.or(
                        Filters.and(
                                Filters.eq("redeemed", false),
                                Filters.lte("expiresAt", now)),
                        Filters.and(
                                Filters.eq("redeemed", true),
                                Filters.lte("sessionExpiresAt", now))));
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to generate ticket hash.", e);
        }
    }

    public static class Ticket {
        private final String token;
        private final String sessionId;
        private final String username;
        private final Date expiresAt;

        public Ticket(
                String token,
                String sessionId,
                String username,
                Date expiresAt) {
            this.token = token;
            this.sessionId = sessionId;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUsername() {
            return username;
        }

        public Date getExpiresAt() {
            return new Date(expiresAt.getTime());
        }
    }
}
