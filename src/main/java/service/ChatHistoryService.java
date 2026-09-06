package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import database.MongoDBConnection;
import model.Message;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public class ChatHistoryService {

    private final MongoCollection<Document> messageCollection;

    public ChatHistoryService() {

        MongoDatabase database = MongoDBConnection.getDatabase();

        messageCollection = database.getCollection("messages");
    }

    // =========================================
    // SAVE MESSAGE
    // =========================================

    public void saveMessage(Message message) {

        Document document = new Document("sender",
                message.getSender())

                .append("receiver",
                        message.getReceiver())

                .append("groupName",
                        message.getGroupName())

                .append("message",
                        message.getMessage())

                .append("messageType",
                        message.getMessageType())

                .append("timestamp",
                        Date.from(
                                message.getTimestamp()
                                        .atZone(
                                                ZoneId.systemDefault())
                                        .toInstant()));

        messageCollection.insertOne(document);
    }

    // =========================================
    // GET PRIVATE CHAT HISTORY
    // =========================================

    public List<Message> getPrivateChatHistory(
            String user1,
            String user2) {

        List<Message> messages = new ArrayList<>();

        for (Document document : messageCollection.find(
                or(
                        and(
                                eq("sender", user1),
                                eq("receiver", user2)),
                        and(
                                eq("sender", user2),
                                eq("receiver", user1))))
                .sort(
                        new Document("timestamp", 1))) {

            messages.add(
                    documentToMessage(document));
        }

        return messages;
    }

    // =========================================
    // GET GROUP CHAT HISTORY
    // =========================================

    public List<Message> getGroupChatHistory(
            String groupName) {

        List<Message> messages = new ArrayList<>();

        for (Document document : messageCollection.find(
                and(
                        eq("groupName", groupName),
                        eq("messageType", "GROUP")))
                .sort(
                        new Document("timestamp", 1))) {

            messages.add(
                    documentToMessage(document));
        }

        return messages;
    }

    // =========================================
    // CONVERT DOCUMENT TO MESSAGE
    // =========================================

    private Message documentToMessage(
            Document document) {

        Date date = document.getDate("timestamp");

        LocalDateTime timestamp = date.toInstant()
                .atZone(
                        ZoneId.systemDefault())
                .toLocalDateTime();

        return new Message(
                document.getString("sender"),
                document.getString("receiver"),
                document.getString("groupName"),
                document.getString("message"),
                document.getString("messageType"),
                timestamp);
    }
}