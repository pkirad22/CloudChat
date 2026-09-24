package service;

import java.io.File;
import java.util.Set;

public class FileValidationService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(

            // Images
            "jpg", "jpeg", "png", "gif", "webp",
            "bmp", "svg", "ico", "tiff",

            // Documents
            "pdf", "doc", "docx", "txt", "rtf", "odt",

            // Office / Spreadsheet / Presentation
            "xls", "xlsx", "csv",
            "ppt", "pptx", "odp",

            // Audio
            "mp3", "wav", "m4a", "aac",
            "flac", "ogg",

            // Video
            "mp4", "mkv", "avi", "mov",
            "webm", "3gp",

            // Archives
            "zip", "rar", "7z", "tar", "gz",

            // Source code / Data
            "java", "py", "js", "jsx",
            "html", "css", "json", "xml",
            "sql", "c", "cpp", "h");

    private FileValidationService() {
        // Utility class
    }

    public static boolean isAllowed(File file) {

        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        String extension = getFileExtension(file);

        return ALLOWED_EXTENSIONS.contains(extension);
    }

    public static String getFileExtension(File file) {

        if (file == null || file.getName() == null) {
            return "";
        }

        String fileName = file.getName();

        int lastDot = fileName.lastIndexOf('.');

        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }

        return fileName
                .substring(lastDot + 1)
                .toLowerCase();
    }

    public static String getFileType(File file) {

        String extension = getFileExtension(file);

        if (extension.isEmpty()) {
            return "UNKNOWN";
        }

        return switch (extension) {

            case "jpg", "jpeg", "png", "gif", "webp",
                    "bmp", "svg", "ico", "tiff" ->
                "IMAGE";

            case "pdf", "doc", "docx", "txt", "rtf", "odt" ->
                "DOCUMENT";

            case "xls", "xlsx", "csv",
                    "ppt", "pptx", "odp" ->
                "OFFICE";

            case "mp3", "wav", "m4a", "aac",
                    "flac", "ogg" ->
                "AUDIO";

            case "mp4", "mkv", "avi", "mov",
                    "webm", "3gp" ->
                "VIDEO";

            case "zip", "rar", "7z", "tar", "gz" ->
                "ARCHIVE";

            case "java", "py", "js", "jsx",
                    "html", "css", "json", "xml",
                    "sql", "c", "cpp", "h" ->
                "CODE";

            default ->
                "UNKNOWN";
        };
    }

    public static String getSupportedExtensions() {
        return String.join(", ", ALLOWED_EXTENSIONS);
    }
}