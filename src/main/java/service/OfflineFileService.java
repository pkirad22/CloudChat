package service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
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

public class OfflineFileService {

        private final MongoCollection<Document> offlineFiles;

        private final GridFSBucket gridFSBucket;

        public OfflineFileService() {

                offlineFiles = MongoDBConnection.getDatabase()
                                .getCollection("offline_files");

                gridFSBucket = GridFSBuckets.create(
                                MongoDBConnection.getDatabase(),
                                "offline_file_storage");
        }

        // =========================================================
        // SAVE PENDING FILE
        // =========================================================

        public boolean savePendingFile(
                        String sender,
                        String receiver,
                        File sourceFile) {

                try {

                        if (sender == null
                                        || receiver == null
                                        || sourceFile == null) {

                                return false;
                        }

                        if (sender.trim().isEmpty()
                                        || receiver.trim().isEmpty()
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
                                                                                                "originalFileName",
                                                                                                sourceFile.getName())
                                                                                .append(
                                                                                                "fileType",
                                                                                                getFileExtension(
                                                                                                                sourceFile.getName()))
                                                                                .append(
                                                                                                "originalFileSize",
                                                                                                sourceFile.length()));

                                gridFsFileId = gridFSBucket.uploadFromStream(
                                                sourceFile.getName(),
                                                inputStream,
                                                options);
                        }

                        // -------------------------------------------------
                        // Save queue metadata
                        // -------------------------------------------------

                        Document document = new Document()
                                        .append(
                                                        "sender",
                                                        sender.trim())
                                        .append(
                                                        "receiver",
                                                        receiver.trim())
                                        .append(
                                                        "fileName",
                                                        sourceFile.getName())
                                        .append(
                                                        "fileSize",
                                                        sourceFile.length())
                                        .append(
                                                        "fileType",
                                                        getFileExtension(
                                                                        sourceFile.getName()))
                                        .append(
                                                        "gridFsFileId",
                                                        gridFsFileId)
                                        .append(
                                                        "status",
                                                        "PENDING")
                                        .append(
                                                        "timestamp",
                                                        LocalDateTime.now().toString());

                        offlineFiles.insertOne(document);

                        System.out.println(
                                        "[OFFLINE-FILE] Pending file saved: "
                                                        + sender
                                                        + " -> "
                                                        + receiver
                                                        + " | "
                                                        + sourceFile.getName()
                                                        + " | GridFS ID: "
                                                        + gridFsFileId);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-FILE] Failed to save file: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // GET PENDING FILES
        // =========================================================

        public List<Document> getPendingFiles(
                        String receiver) {

                List<Document> files = new ArrayList<>();

                try {

                        if (receiver == null
                                        || receiver.trim().isEmpty()) {

                                return files;
                        }

                        for (Document document : offlineFiles.find(
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
                                        "[OFFLINE-FILE] Unable to retrieve pending files: "
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

                if (gridFsFileId == null
                                || destinationFile == null) {

                        return false;
                }

                try (FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

                        gridFSBucket.downloadToStream(
                                        gridFsFileId,
                                        outputStream);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-FILE] Download failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // DOWNLOAD GRIDFS FILE TO OUTPUT STREAM
        // =========================================================

        public boolean downloadFileToStream(
                        ObjectId gridFsFileId,
                        OutputStream outputStream) {

                if (gridFsFileId == null
                                || outputStream == null) {

                        return false;
                }

                try {

                        gridFSBucket.downloadToStream(
                                        gridFsFileId,
                                        outputStream);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-FILE] Unable to stream file: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // DELETE PENDING FILE
        // =========================================================

        public boolean deletePendingFile(
                        ObjectId queueId) {

                try {

                        if (queueId == null) {
                                return false;
                        }

                        Document document = offlineFiles.find(
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

                        long deleted = offlineFiles.deleteOne(
                                        Filters.eq(
                                                        "_id",
                                                        queueId))
                                        .getDeletedCount();

                        // -------------------------------------------------
                        // Delete actual GridFS file
                        // -------------------------------------------------

                        if (deleted > 0
                                        && gridFsFileId != null) {

                                try {

                                        gridFSBucket.delete(
                                                        gridFsFileId);

                                        System.out.println(
                                                        "[OFFLINE-FILE] GridFS file deleted: "
                                                                        + gridFsFileId);

                                } catch (Exception gridFsException) {

                                        System.out.println(
                                                        "[OFFLINE-FILE] Queue deleted but "
                                                                        + "GridFS cleanup failed: "
                                                                        + gridFsException.getMessage());
                                }

                                System.out.println(
                                                "[OFFLINE-FILE] Pending file removed "
                                                                + "after successful delivery.");

                                return true;
                        }

                        return deleted > 0;

                } catch (Exception e) {

                        System.out.println(
                                        "[OFFLINE-FILE] Unable to remove pending file: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // COUNT
        // =========================================================

        public long getPendingFileCount(
                        String receiver) {

                try {

                        if (receiver == null
                                        || receiver.trim().isEmpty()) {

                                return 0;
                        }

                        return offlineFiles.countDocuments(
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

                if (fileName == null
                                || fileName.trim().isEmpty()) {

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