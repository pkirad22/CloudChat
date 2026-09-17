package server;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import model.FileTransferHistory;
import service.FileTransferHistoryService;

public class GroupFileTransferTracker {

    private final String sender;
    private final String groupName;
    private final File file;

    // Complete recipient list for history
    private final List<String> recipients;

    // Recipients that this particular server must deliver to
    private final List<String> expectedRecipients;

    private final String transferType;
    private final String sourceServer;
    private final String destinationServer;

    private final Set<String> completedRecipients = new HashSet<>();

    // =========================================================
    // LOCAL GROUP CONSTRUCTOR
    // =========================================================

    public GroupFileTransferTracker(
            String sender,
            String groupName,
            File file,
            List<String> recipients) {

        this(
                sender,
                groupName,
                file,
                recipients,
                recipients,
                "LOCAL_GROUP",
                "LOCAL",
                "LOCAL");
    }

    // =========================================================
    // GENERAL CONSTRUCTOR
    // =========================================================

    public GroupFileTransferTracker(
            String sender,
            String groupName,
            File file,
            List<String> recipients,
            List<String> expectedRecipients,
            String transferType,
            String sourceServer,
            String destinationServer) {

        this.sender = sender;
        this.groupName = groupName;
        this.file = file;

        this.recipients = recipients;
        this.expectedRecipients = expectedRecipients;

        this.transferType = transferType;
        this.sourceServer = sourceServer;
        this.destinationServer = destinationServer;
    }

    // =========================================================
    // DELIVERY COMPLETED
    // =========================================================

    public synchronized void deliveryCompleted(
            String recipient) {

        if (recipient == null
                || recipient.trim().isEmpty()) {

            return;
        }

        if (!expectedRecipients.contains(recipient)) {
            return;
        }

        if (!completedRecipients.add(recipient)) {
            return;
        }

        System.out.println();

        System.out.println(
                "[GROUP-FILE-HISTORY] Delivery completed: "
                        + recipient);

        System.out.println(
                "[GROUP-FILE-HISTORY] Completed: "
                        + completedRecipients.size()
                        + "/"
                        + expectedRecipients.size());

        if (completedRecipients.size() == expectedRecipients.size()) {

            saveHistory();
        }
    }

    // =========================================================
    // SAVE HISTORY
    // =========================================================

    private void saveHistory() {

        try {

            FileTransferHistory history = new FileTransferHistory(

                    sender,

                    null,

                    groupName,

                    recipients,

                    file.getName(),

                    file.length(),

                    getFileType(
                            file.getName()),

                    transferType,

                    sourceServer,

                    destinationServer,

                    "SUCCESS",

                    LocalDateTime.now());

            FileTransferHistoryService historyService = new FileTransferHistoryService();

            historyService.saveFileTransfer(history);

            System.out.println();

            System.out.println(
                    "[GROUP-FILE-HISTORY] "
                            + "Group transfer history saved.");

            System.out.println(
                    "[GROUP-FILE-HISTORY] Type: "
                            + transferType);

            System.out.println(
                    "[GROUP-FILE-HISTORY] Sender: "
                            + sender);

            System.out.println(
                    "[GROUP-FILE-HISTORY] Group: "
                            + groupName);

            System.out.println(
                    "[GROUP-FILE-HISTORY] Recipients: "
                            + recipients);

            System.out.println(
                    "[GROUP-FILE-HISTORY] Source: "
                            + sourceServer);

            System.out.println(
                    "[GROUP-FILE-HISTORY] Destination: "
                            + destinationServer);

        } catch (Exception e) {

            System.out.println(
                    "[GROUP-FILE-HISTORY] "
                            + "Unable to save history: "
                            + e.getMessage());
        }
    }

    // =========================================================
    // FILE TYPE
    // =========================================================

    private String getFileType(String fileName) {

        if (fileName == null) {
            return "UNKNOWN";
        }

        String lower = fileName.toLowerCase();

        if (lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")) {
            return "IMAGE/JPEG";
        }

        if (lower.endsWith(".png")) {
            return "IMAGE/PNG";
        }

        if (lower.endsWith(".gif")) {
            return "IMAGE/GIF";
        }

        if (lower.endsWith(".webp")) {
            return "IMAGE/WEBP";
        }

        if (lower.endsWith(".pdf")) {
            return "APPLICATION/PDF";
        }

        if (lower.endsWith(".mp3")) {
            return "AUDIO/MP3";
        }

        if (lower.endsWith(".wav")) {
            return "AUDIO/WAV";
        }

        if (lower.endsWith(".m4a")) {
            return "AUDIO/M4A";
        }

        if (lower.endsWith(".aac")) {
            return "AUDIO/AAC";
        }

        return "UNKNOWN";
    }
}