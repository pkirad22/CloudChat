package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import database.MongoDBConnection;

public class OfflineMessageService {

    private final MongoCollection<Document> offlineMessages;

    public OfflineMessageService() {

        offlineMessages = MongoDBConnection.getDatabase()
                .getCollection("offline_messages");
    }

    // =========================================================
    // SAVE PENDING MESSAGE
    // =========================================================

    public boolean savePendingMessage(
            String sender,
            String receiver,
            String message) {

        try {

            if (sender == null
                    || receiver == null
                    || message == null
                    || sender.trim().isEmpty()
                    || receiver.trim().isEmpty()
                    || message.trim().isEmpty()) {

                return false;
            }

            Document document = new Document();

            document.append(
                    "sender",
                    sender.trim());

            document.append(
                    "receiver",
                    receiver.trim());

            document.append(
                    "message",
                    message);

            document.append(
                    "messageType",
                    "PRIVATE");

            document.append(
                    "status",
                    "PENDING");

            document.append(
                    "timestamp",
                    LocalDateTime.now().toString());

            offlineMessages.insertOne(document);

            System.out.println(
                    "[OFFLINE-MESSAGE] Pending message saved: "
                            + sender
                            + " -> "
                            + receiver);

            return true;

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-MESSAGE] Failed to save message: "
                            + e.getMessage());

            return false;
        }
    }

    // =========================================================
    // GET PENDING MESSAGES
    // =========================================================

    public List<Document> getPendingMessages(
            String receiver) {

        List<Document> messages = new ArrayList<>();

        try {

            if (receiver == null
                    || receiver.trim().isEmpty()) {

                return messages;
            }

            for (Document document : offlineMessages.find(
                    Filters.and(
                            Filters.eq(
                                    "receiver",
                                    receiver.trim()),
                            Filters.eq(
                                    "status",
                                    "PENDING")))) {

                messages.add(document);
            }

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-MESSAGE] "
                            + "Unable to retrieve pending messages: "
                            + e.getMessage());
        }

        return messages;
    }

    // =========================================================
    // DELETE DELIVERED MESSAGE
    // =========================================================

    public boolean deletePendingMessage(
            org.bson.types.ObjectId messageId) {

        try {

            if (messageId == null) {
                return false;
            }

            long deleted = offlineMessages.deleteOne(
                    Filters.eq(
                            "_id",
                            messageId))
                    .getDeletedCount();

            if (deleted > 0) {

                System.out.println(
                        "[OFFLINE-MESSAGE] "
                                + "Pending message removed after delivery.");

                return true;
            }

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-MESSAGE] "
                            + "Unable to remove pending message: "
                            + e.getMessage());
        }

        return false;
    }

    // =========================================================
    // COUNT PENDING MESSAGES
    // =========================================================

    public long getPendingMessageCount(
            String receiver) {

        try {

            return offlineMessages.countDocuments(
                    Filters.and(
                            Filters.eq(
                                    "receiver",
                                    receiver),
                            Filters.eq(
                                    "status",
                                    "PENDING")));

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-MESSAGE] "
                            + "Unable to count pending messages: "
                            + e.getMessage());

            return 0;
        }
    }
}