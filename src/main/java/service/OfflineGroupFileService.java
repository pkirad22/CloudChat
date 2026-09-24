package service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;

import database.MongoDBConnection;

public class OfflineGroupFileService {

    private final MongoCollection<Document> offlineGroupFiles;

    private final GridFSBucket gridFSBucket;

    public OfflineGroupFileService() {

        offlineGroupFiles = MongoDBConnection.getDatabase()
                .getCollection("offline_group_files");

        gridFSBucket = GridFSBuckets.create(
                MongoDBConnection.getDatabase(),
                "offline_group_file_storage");
    }

    // =========================================================
    // SAVE PENDING GROUP FILE
    // =========================================================

    public boolean savePendingGroupFile(
            String sender,
            String receiver,
            String groupName,
            File sourceFile) {

        try {

            if (sender == null
                    || receiver == null
                    || groupName == null
                    || sourceFile == null) {

                return false;
            }

            if (sender.trim().isEmpty()
                    || receiver.trim().isEmpty()
                    || groupName.trim().isEmpty()
                    || !sourceFile.exists()
                    || !sourceFile.isFile()) {

                return false;
            }

            // -------------------------------------------------
            // Upload actual file to MongoDB GridFS
            // -------------------------------------------------

            ObjectId gridFsFileId;

            try (FileInputStream inputStream = new FileInputStream(sourceFile)) {

                GridFSUploadOptions options = new GridFSUploadOptions()
                        .metadata(
                                new Document()
                                        .append(
                                                "sender",
                                                sender.trim())
                                        .append(
                                                "receiver",
                                                receiver.trim())
                                        .append(
                                                "groupName",
                                                groupName.trim())
                                        .append(
                                                "originalFileName",
                                                sourceFile.getName())
                                        .append(
                                                "fileType",
                                                getFileExtension(
                                                        sourceFile.getName())));

                gridFsFileId = gridFSBucket.uploadFromStream(
                        sourceFile.getName(),
                        inputStream,
                        options);
            }

            // -------------------------------------------------
            // Save queue metadata
            // -------------------------------------------------

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
                    "fileName",
                    sourceFile.getName());

            document.append(
                    "fileSize",
                    sourceFile.length());

            document.append(
                    "fileType",
                    getFileExtension(
                            sourceFile.getName()));

            document.append(
                    "gridFsFileId",
                    gridFsFileId);

            document.append(
                    "status",
                    "PENDING");

            document.append(
                    "timestamp",
                    LocalDateTime.now().toString());

            offlineGroupFiles.insertOne(document);

            System.out.println(
                    "[OFFLINE-GROUP-FILE] Pending group file saved: "
                            + sender
                            + " -> "
                            + receiver
                            + " ["
                            + groupName
                            + "] | "
                            + sourceFile.getName());

            return true;

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-FILE] Failed to save file: "
                            + e.getMessage());

            return false;
        }
    }

    // =========================================================
    // GET PENDING GROUP FILES
    // =========================================================

    public List<Document> getPendingGroupFiles(
            String receiver) {

        List<Document> files = new ArrayList<>();

        try {

            if (receiver == null
                    || receiver.trim().isEmpty()) {

                return files;
            }

            for (Document document : offlineGroupFiles.find(
                    Filters.and(
                            Filters.eq(
                                    "receiver",
                                    receiver.trim()),

                            Filters.eq(
                                    "status",
                                    "PENDING")))) {

                files.add(document);
            }

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-FILE] "
                            + "Unable to retrieve pending group files: "
                            + e.getMessage());
        }

        return files;
    }

    // =========================================================
    // DOWNLOAD FILE FROM GRIDFS
    // =========================================================

    public boolean downloadFile(
            ObjectId gridFsFileId,
            File destinationFile) {

        try {

            if (gridFsFileId == null
                    || destinationFile == null) {

                return false;
            }

            try (FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

                gridFSBucket.downloadToStream(
                        gridFsFileId,
                        outputStream);
            }

            return true;

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-FILE] "
                            + "Download failed: "
                            + e.getMessage());

            return false;
        }
    }

    // =========================================================
    // DOWNLOAD GRIDFS FILE TO OUTPUT STREAM
    // =========================================================

    public void downloadFileToStream(
            ObjectId gridFsFileId,
            java.io.OutputStream outputStream) {

        if (gridFsFileId == null
                || outputStream == null) {

            return;
        }

        try {

            gridFSBucket.downloadToStream(
                    gridFsFileId,
                    outputStream);

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-FILE] "
                            + "Unable to stream file: "
                            + e.getMessage());

            throw e;
        }
    }

    // =========================================================
    // DELETE PENDING GROUP FILE
    // =========================================================

    public boolean deletePendingGroupFile(
            ObjectId queueId) {

        try {

            if (queueId == null) {
                return false;
            }

            Document document = offlineGroupFiles.find(
                    Filters.eq(
                            "_id",
                            queueId))
                    .first();

            if (document == null) {
                return false;
            }

            ObjectId gridFsFileId = document.getObjectId(
                    "gridFsFileId");

            // -------------------------------------------------
            // Delete queue metadata
            // -------------------------------------------------

            long deleted = offlineGroupFiles.deleteOne(
                    Filters.eq(
                            "_id",
                            queueId))
                    .getDeletedCount();

            // -------------------------------------------------
            // Delete actual GridFS file
            // -------------------------------------------------

            if (deleted > 0
                    && gridFsFileId != null) {

                gridFSBucket.delete(
                        gridFsFileId);

                System.out.println(
                        "[OFFLINE-GROUP-FILE] "
                                + "File removed after delivery.");

                return true;
            }

        } catch (Exception e) {

            System.out.println(
                    "[OFFLINE-GROUP-FILE] "
                            + "Unable to remove pending group file: "
                            + e.getMessage());
        }

        return false;
    }

    // =========================================================
    // COUNT PENDING GROUP FILES
    // =========================================================

    public long getPendingGroupFileCount(
            String receiver) {

        try {

            if (receiver == null
                    || receiver.trim().isEmpty()) {

                return 0;
            }

            return offlineGroupFiles.countDocuments(
                    Filters.and(
                            Filters.eq(
                                    "receiver",
                                    receiver.trim()),

                            Filters.eq(
                                    "status",
                                    "PENDING")));

        } catch (Exception e) {

            return 0;
        }
    }

    // =========================================================
    // FILE EXTENSION
    // =========================================================

    private String getFileExtension(
            String fileName) {

        if (fileName == null) {
            return "";
        }

        int index = fileName.lastIndexOf('.');

        if (index == -1) {
            return "";
        }

        return fileName
                .substring(index + 1)
                .toLowerCase();
    }
}