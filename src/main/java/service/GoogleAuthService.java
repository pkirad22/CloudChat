
package service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService() {
        String clientId = System.getenv("GOOGLE_CLIENT_ID");

        // Support dotenv-java if the application loads .env
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = io.github.cdimascio.dotenv.Dotenv
                    .configure()
                    .ignoreIfMissing()
                    .load()
                    .get("GOOGLE_CLIENT_ID");
        }

        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException(
                    "GOOGLE_CLIENT_ID is missing from the backend environment.");
        }

        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId.trim()))
                .build();
    }

    public GoogleUser verifyIdToken(String credential)
            throws GeneralSecurityException, IOException {

        if (credential == null || credential.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Google credential is required.");
        }

        GoogleIdToken idToken = verifier.verify(credential.trim());

        if (idToken == null) {
            throw new SecurityException(
                    "Invalid or expired Google credential.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        String subject = payload.getSubject();
        String email = payload.getEmail();

        Object emailVerifiedClaim = payload.get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(emailVerifiedClaim);

        if (subject == null || subject.trim().isEmpty()
                || email == null || email.trim().isEmpty()
                || !emailVerified) {
            throw new SecurityException(
                    "Google account email could not be verified.");
        }

        String name = (String) payload.get("name");

        return new GoogleUser(
                subject,
                email.trim().toLowerCase(java.util.Locale.ROOT),
                name);
    }

    public static class GoogleUser {
        private final String subject;
        private final String email;
        private final String name;

        public GoogleUser(String subject, String email, String name) {
            this.subject = subject;
            this.email = email;
            this.name = name;
        }

        public String getSubject() {
            return subject;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }
    }
}
