package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import database.MongoDBConnection;

public class OfflineGroupMessageService {

    private final MongoCollection<Document> offlineGroupMessages;

    public OfflineGroupMessageService() {

        offlineGroupMessages = MongoDBConnection.getDatabase()
                .getCollection("offline_group_messages");
    }

    // =========================================================
    // SAVE OFFLINE GROUP MESSAGE
    // =========================================================

    public boolean savePendingGroupMessage(
            String sender,
            String receiver,
            String groupName,
            String message) {

        try {

            if (sender == null
                    || receiver == null
                    || groupName == null
                    || message == null
                    || sender.trim().isEmpty()
                    || receiver.trim().isEmpty()
                    || groupName.trim().isEmpty()
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
                    "groupName",
                    groupName.trim());

            document.append(
                    "message",
                    message);

            document.append(
                    "messageType",
                    "GROUP");

            document.append(
                    "status",
                    "PENDING");

            document.append(
                    "timestamp",
                    LocalDateTime.now().toString());

            offlineGroupMessages.insertOne(document);

            System.out.println(
                    "[OFFLINE-GROUP-MESSAGE] Pending group message saved: "
                            + sender
                            + " -> "
                            + receiver
                            + " ["
                            + groupName
                            + "]");

            return true;

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-MESSAGE] Failed to save message: "
                            + e.getMessage());

            return false;
        }
    }

    // =========================================================
    // GET PENDING GROUP MESSAGES
    // =========================================================

    public List<Document> getPendingGroupMessages(
            String receiver) {

        List<Document> messages = new ArrayList<>();

        try {

            if (receiver == null
                    || receiver.trim().isEmpty()) {

                return messages;
            }

            for (Document document : offlineGroupMessages.find(
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
                    "[OFFLINE-GROUP-MESSAGE] Unable to retrieve messages: "
                            + e.getMessage());
        }

        return messages;
    }

    // =========================================================
    // DELETE AFTER DELIVERY
    // =========================================================

    public boolean deletePendingGroupMessage(
            org.bson.types.ObjectId messageId) {

        try {

            if (messageId == null) {
                return false;
            }

            long deleted = offlineGroupMessages
                    .deleteOne(
                            Filters.eq(
                                    "_id",
                                    messageId))
                    .getDeletedCount();

            if (deleted > 0) {

                System.out.println(
                        "[OFFLINE-GROUP-MESSAGE] "
                                + "Pending message removed after delivery.");

                return true;
            }

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-MESSAGE] "
                            + "Unable to remove message: "
                            + e.getMessage());
        }

        return false;
    }

    // =========================================================
    // COUNT PENDING MESSAGES
    // =========================================================

    public long getPendingGroupMessageCount(
            String receiver) {

        try {

            return offlineGroupMessages.countDocuments(
                    Filters.and(
                            Filters.eq(
                                    "receiver",
                                    receiver),

                            Filters.eq(
                                    "status",
                                    "PENDING")));

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-MESSAGE] "
                            + "Unable to count messages: "
                            + e.getMessage());
        }

        return 0;
    }
}