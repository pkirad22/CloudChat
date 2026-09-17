package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import database.MongoDBConnection;
import model.FileTransferHistory;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public class FileTransferHistoryService {

    private final MongoCollection<Document> fileCollection;

    // =========================================
    // CONSTRUCTOR
    // =========================================

    public FileTransferHistoryService() {

        MongoDatabase database = MongoDBConnection.getDatabase();

        fileCollection = database.getCollection(
                "file_transfer_history");
    }

    // =========================================
    // SAVE FILE TRANSFER
    // =========================================

    public void saveFileTransfer(
            FileTransferHistory transfer) {

        Document document = new Document(
                "sender",
                transfer.getSender())

                .append(
                        "recipient",
                        transfer.getRecipient())

                .append(
                        "groupName",
                        transfer.getGroupName())

                .append(
                        "recipients",
                        transfer.getRecipients())

                .append(
                        "fileName",
                        transfer.getFileName())

                .append(
                        "fileSize",
                        transfer.getFileSize())

                .append(
                        "fileType",
                        transfer.getFileType())

                .append(
                        "transferType",
                        transfer.getTransferType())

                .append(
                        "sourceServer",
                        transfer.getSourceServer())

                .append(
                        "destinationServer",
                        transfer.getDestinationServer())

                .append(
                        "status",
                        transfer.getStatus())

                .append(
                        "timestamp",
                        Date.from(
                                transfer.getTimestamp()
                                        .atZone(
                                                ZoneId.systemDefault())
                                        .toInstant()));

        fileCollection.insertOne(document);
    }

    // =========================================
    // GET PRIVATE FILE HISTORY
    // =========================================

    public List<FileTransferHistory> getPrivateFileHistory(
            String user1,
            String user2) {

        List<FileTransferHistory> history = new ArrayList<>();

        for (Document document : fileCollection.find(
                and(
                        or(
                                and(
                                        eq(
                                                "sender",
                                                user1),
                                        eq(
                                                "recipient",
                                                user2)),

                                and(
                                        eq(
                                                "sender",
                                                user2),
                                        eq(
                                                "recipient",
                                                user1))),

                        or(
                                eq(
                                        "transferType",
                                        "LOCAL_PRIVATE"),

                                eq(
                                        "transferType",
                                        "DISTRIBUTED_PRIVATE"))))
                .sort(
                        new Document(
                                "timestamp",
                                1))) {

            history.add(
                    documentToFileTransfer(
                            document));
        }

        return history;
    }

    // =========================================
    // GET GROUP FILE HISTORY
    // =========================================

    public List<FileTransferHistory> getGroupFileHistory(
            String groupName) {

        List<FileTransferHistory> history = new ArrayList<>();

        for (Document document : fileCollection.find(
                and(
                        eq(
                                "groupName",
                                groupName),

                        or(
                                eq(
                                        "transferType",
                                        "LOCAL_GROUP"),

                                eq(
                                        "transferType",
                                        "DISTRIBUTED_GROUP"))))
                .sort(
                        new Document(
                                "timestamp",
                                1))) {

            history.add(
                    documentToFileTransfer(
                            document));
        }

        return history;
    }

    // =========================================
    // GET USER FILE HISTORY
    // =========================================

    public List<FileTransferHistory> getUserFileHistory(
            String username) {

        List<FileTransferHistory> history = new ArrayList<>();

        for (Document document : fileCollection.find(
                or(
                        eq(
                                "sender",
                                username),

                        eq(
                                "recipient",
                                username),

                        eq(
                                "recipients",
                                username)))
                .sort(
                        new Document(
                                "timestamp",
                                1))) {

            history.add(
                    documentToFileTransfer(
                            document));
        }

        return history;
    }

    // =========================================
    // CONVERT DOCUMENT TO MODEL
    // =========================================

    private FileTransferHistory documentToFileTransfer(
            Document document) {

        Date date = document.getDate(
                "timestamp");

        LocalDateTime timestamp = date.toInstant()
                .atZone(
                        ZoneId.systemDefault())
                .toLocalDateTime();

        return new FileTransferHistory(

                document.getString(
                        "sender"),

                document.getString(
                        "recipient"),

                document.getString(
                        "groupName"),

                document.getList(
                        "recipients",
                        String.class),

                document.getString(
                        "fileName"),

                document.getLong(
                        "fileSize"),

                document.getString(
                        "fileType"),

                document.getString(
                        "transferType"),

                document.getString(
                        "sourceServer"),

                document.getString(
                        "destinationServer"),

                document.getString(
                        "status"),

                timestamp);
    }
}