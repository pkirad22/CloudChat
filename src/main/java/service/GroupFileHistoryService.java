
package service;

import database.MongoDBConnection;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GroupFileHistoryService implements AutoCloseable {

    private static final String DATABASE_NAME = "CloudChat";
    private static final String HISTORY_COLLECTION = "group_file_history";
    private static final String HISTORY_BUCKET = "group_file_history_storage";

    private final MongoCollection<Document> historyCollection;
    private final GridFSBucket gridFSBucket;

    public GroupFileHistoryService() {
        MongoDatabase database = MongoDBConnection.getDatabase();

        if (database == null) {
            throw new IllegalStateException(
                    "MongoDB is not connected. Call MongoDBConnection.connect() first.");
        }

        historyCollection = database.getCollection(HISTORY_COLLECTION);
        gridFSBucket = GridFSBuckets.create(database, HISTORY_BUCKET);

        historyCollection.createIndex(
                Indexes.ascending("transferId"),
                new IndexOptions().unique(true));

        historyCollection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("groupName"),
                        Indexes.descending("uploadedAt")));
    }

    /**
     * Saves a permanent history copy of a group file.
     * Calling this method again with the same transferId will not
     * create another history record.
     */
    public synchronized Document saveGroupFile(
            String transferId,
            String sender,
            String groupName,
            String fileName,
            File file) throws Exception {

        if (transferId == null || transferId.trim().isEmpty()
                || sender == null || sender.trim().isEmpty()
                || groupName == null || groupName.trim().isEmpty()
                || fileName == null || fileName.trim().isEmpty()
                || file == null || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Invalid group-file history data.");
        }

        Document existing = historyCollection.find(
                new Document("transferId", transferId)).first();

        if (existing != null) {
            return existing;
        }

        ObjectId gridFsObjectId = new ObjectId();
        org.bson.BsonObjectId gridFsFileId = new org.bson.BsonObjectId(gridFsObjectId);

        try (FileInputStream input = new FileInputStream(file)) {
            GridFSUploadOptions options = new GridFSUploadOptions()
                    .chunkSizeBytes(255 * 1024)
                    .metadata(new Document("transferId", transferId)
                            .append("sender", sender)
                            .append("groupName", groupName)
                            .append("originalFileName", fileName));

            gridFSBucket.uploadFromStream(
                    gridFsFileId,
                    fileName,
                    input,
                    options);
        }

        Document record = new Document("transferId", transferId)
                .append("sender", sender)
                .append("groupName", groupName)
                .append("fileName", fileName)
                .append("fileSize", file.length())
                .append("gridFsFileId", gridFsObjectId)
                .append("uploadedAt", new Date());

        try {
            historyCollection.insertOne(record);
            return record;
        } catch (Exception e) {
            // Avoid leaving an orphaned GridFS file if metadata insertion fails.
            try {
                gridFSBucket.delete(gridFsFileId);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /**
     * Returns newest-first metadata for a group.
     * The caller must verify that the requesting user belongs to the group.
     */
    public List<Document> getGroupFileHistory(String groupName, int limit) {
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name is required.");
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<Document> results = new ArrayList<>();

        for (Document document : historyCollection.find(
                new Document("groupName", groupName))
                .sort(Sorts.descending("uploadedAt"))
                .limit(safeLimit)) {

            // Do not expose the internal GridFS ID as the public file identifier.
            Document item = new Document(document);
            item.remove("gridFsFileId");
            results.add(item);
        }

        return results;
    }

    /**
     * Streams a saved file to the supplied output stream.
     * The caller must verify group membership before invoking this method.
     */
    public boolean downloadGroupFile(String transferId, OutputStream output)
            throws Exception {

        if (transferId == null || transferId.trim().isEmpty() || output == null) {
            return false;
        }

        Document record = historyCollection.find(
                new Document("transferId", transferId)).first();

        if (record == null) {
            return false;
        }

        ObjectId fileId = record.getObjectId("gridFsFileId");
        gridFSBucket.downloadToStream(fileId, output);

        return true;
    }

    public void close() {

    }
}
