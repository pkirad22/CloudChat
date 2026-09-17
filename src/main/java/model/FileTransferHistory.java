package model;

import java.time.LocalDateTime;
import java.util.List;

public class FileTransferHistory {

    private String sender;
    private String recipient;
    private String groupName;

    // Used for group file transfers
    private List<String> recipients;

    private String fileName;
    private long fileSize;
    private String fileType;

    /*
     * LOCAL_PRIVATE
     * DISTRIBUTED_PRIVATE
     * LOCAL_GROUP
     * DISTRIBUTED_GROUP
     */
    private String transferType;

    private String sourceServer;
    private String destinationServer;

    // SUCCESS / FAILED
    private String status;

    private LocalDateTime timestamp;

    // =========================================
    // DEFAULT CONSTRUCTOR
    // =========================================

    public FileTransferHistory() {
        // Required for MongoDB
    }

    // =========================================
    // PARAMETERIZED CONSTRUCTOR
    // =========================================

    public FileTransferHistory(
            String sender,
            String recipient,
            String groupName,
            List<String> recipients,
            String fileName,
            long fileSize,
            String fileType,
            String transferType,
            String sourceServer,
            String destinationServer,
            String status,
            LocalDateTime timestamp) {

        this.sender = sender;
        this.recipient = recipient;
        this.groupName = groupName;
        this.recipients = recipients;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.transferType = transferType;
        this.sourceServer = sourceServer;
        this.destinationServer = destinationServer;
        this.status = status;
        this.timestamp = timestamp;
    }

    // =========================================
    // GETTERS AND SETTERS
    // =========================================

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getTransferType() {
        return transferType;
    }

    public void setTransferType(String transferType) {
        this.transferType = transferType;
    }

    public String getSourceServer() {
        return sourceServer;
    }

    public void setSourceServer(String sourceServer) {
        this.sourceServer = sourceServer;
    }

    public String getDestinationServer() {
        return destinationServer;
    }

    public void setDestinationServer(String destinationServer) {
        this.destinationServer = destinationServer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}