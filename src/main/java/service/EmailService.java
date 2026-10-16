
package service;

import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import io.github.cdimascio.dotenv.Dotenv;

public class EmailService {

        private static final Dotenv DOTENV = Dotenv.configure()
                        .ignoreIfMissing()
                        .load();

        private static String getConfig(String key) {
                String value = System.getenv(key);

                if (value == null || value.isBlank()) {
                        value = DOTENV.get(key);
                }

                return value;
        }

        public void sendVerificationOtp(String recipientEmail, String otp)
                        throws MessagingException {

                String senderEmail = getConfig("CLOUDCHAT_SMTP_EMAIL");
                String appPassword = getConfig("CLOUDCHAT_SMTP_APP_PASSWORD");

                if (senderEmail == null || senderEmail.isBlank()
                                || appPassword == null || appPassword.isBlank()) {
                        throw new IllegalStateException(
                                        "Gmail SMTP configuration is missing. Check your .env file.");
                }

                Properties properties = new Properties();

                properties.put("mail.smtp.host", "smtp.gmail.com");
                properties.put("mail.smtp.port", "587");
                properties.put("mail.smtp.auth", "true");
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.starttls.required", "true");
                properties.put("mail.smtp.connectiontimeout", "10000");
                properties.put("mail.smtp.timeout", "10000");
                properties.put("mail.smtp.writetimeout", "10000");

                Session session = Session.getInstance(
                                properties,
                                new Authenticator() {
                                        @Override
                                        protected PasswordAuthentication getPasswordAuthentication() {
                                                return new PasswordAuthentication(
                                                                senderEmail,
                                                                appPassword);
                                        }
                                });

                Message message = new MimeMessage(session);

                message.setFrom(new InternetAddress(senderEmail));
                message.setRecipients(
                                Message.RecipientType.TO,
                                InternetAddress.parse(recipientEmail, true));

                message.setSubject("CloudChat - Email Verification");

                message.setText(
                                "Hello,\n\n"
                                                + "Your CloudChat email verification code is: "
                                                + otp
                                                + "\n\n"
                                                + "This code expires in 5 minutes.\n"
                                                + "If you did not register for CloudChat, you can ignore this email.\n\n"
                                                + "CloudChat Team");

                Transport.send(message);
        }
}
