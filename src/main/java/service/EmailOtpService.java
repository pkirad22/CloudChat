package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import database.MongoDBConnection;
import org.bson.Document;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Updates.*;

public class EmailOtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;

    private final MongoCollection<Document> users;
    private final MongoCollection<Document> otps;

    private final SecureRandom secureRandom = new SecureRandom();
    private final EmailService emailService = new EmailService();

    public EmailOtpService() {
        MongoDatabase database = MongoDBConnection.getDatabase();

        users = database.getCollection("users");
        otps = database.getCollection("email_otps");
    }

    public void sendOtp(String email) throws Exception {
        String normalizedEmail = email.trim().toLowerCase();

        Document user = users.find(eq("email", normalizedEmail)).first();

        if (user == null) {
            throw new IllegalArgumentException("Registration not found.");
        }

        if (Boolean.TRUE.equals(user.getBoolean("emailVerified"))) {
            throw new IllegalStateException("Email is already verified.");
        }

        Instant now = Instant.now();

        Document previousOtp = otps.find(eq("email", normalizedEmail)).first();

        if (previousOtp != null) {
            Date lastSent = previousOtp.getDate("lastSentAt");

            if (lastSent != null &&
                    now.isBefore(lastSent.toInstant()
                            .plusSeconds(RESEND_COOLDOWN_SECONDS))) {
                throw new IllegalStateException(
                        "Please wait before requesting another OTP.");
            }
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String otpHash = hashOtp(normalizedEmail, otp);

        Date expiresAt = Date.from(now.plusSeconds(OTP_EXPIRY_MINUTES * 60L));

        Document otpDocument = new Document("email", normalizedEmail)
                .append("otpHash", otpHash)
                .append("expiresAt", expiresAt)
                .append("lastSentAt", Date.from(now))
                .append("attempts", 0);

        otps.replaceOne(
                eq("email", normalizedEmail),
                otpDocument,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));

        try {
            emailService.sendVerificationOtp(normalizedEmail, otp);
        } catch (Exception e) {
            // Do not leave a usable code when sending the email fails.
            otps.deleteOne(eq("email", normalizedEmail));
            throw new Exception("Unable to send verification email. Please try again.", e);
        }
    }

    public boolean verifyOtp(String email, String suppliedOtp) throws Exception {
        String normalizedEmail = email.trim().toLowerCase();

        if (suppliedOtp == null || !suppliedOtp.matches("\\d{6}")) {
            return false;
        }

        Document otpDocument = otps.find(eq("email", normalizedEmail)).first();

        if (otpDocument == null) {
            return false;
        }

        Date expiresAt = otpDocument.getDate("expiresAt");

        if (expiresAt == null || !expiresAt.toInstant().isAfter(Instant.now())) {
            otps.deleteOne(eq("email", normalizedEmail));
            return false;
        }

        Integer attempts = otpDocument.getInteger("attempts", 0);

        if (attempts >= MAX_ATTEMPTS) {
            otps.deleteOne(eq("email", normalizedEmail));
            return false;
        }

        String expectedHash = otpDocument.getString("otpHash");
        String suppliedHash = hashOtp(normalizedEmail, suppliedOtp);

        if (!MessageDigest.isEqual(
                expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                suppliedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {

            otps.updateOne(
                    and(eq("email", normalizedEmail), eq("otpHash", expectedHash)),
                    inc("attempts", 1));

            return false;
        }

        users.updateOne(
                eq("email", normalizedEmail),
                set("emailVerified", true));

        otps.deleteOne(eq("email", normalizedEmail));

        return true;
    }

    private String hashOtp(String email, String otp) throws Exception {
        String value = email + ":" + otp;

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(digest);
    }
}