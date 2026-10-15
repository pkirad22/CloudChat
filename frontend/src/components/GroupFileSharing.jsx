import { useCallback, useEffect, useRef, useState } from "react";

import {
  CheckCircle2,
  Download,
  File,
  FileArchive,
  FileImage,
  FileText,
  Loader2,
  Paperclip,
  Send,
  X,
  RefreshCw,
} from "lucide-react";

import { onChatMessage, sendGroupFile } from "../services/chatSocket";

const MAX_FILE_SIZE = 100 * 1024 * 1024;

// Your existing Java API server.
const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || "http://localhost:9000"
).replace(/\/+$/, "");

function FileIconDisplay({ icon: Icon, size = 19 }) {
  return <Icon size={size} />;
}

function formatFileSize(bytes) {
  const size = Number(bytes) || 0;

  if (size <= 0) return "0 B";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function getDisplayFileName(fileName = "") {
  const cleanedName = String(fileName).trim();

  return cleanedName.replace(/^cloudchat-group-\d+-/i, "") || cleanedName;
}

function getFileIcon(fileName = "", fileType = "") {
  const lowerName = fileName.toLowerCase();
  const lowerType = String(fileType).toLowerCase();

  if (lowerType.startsWith("image/")) {
    return FileImage;
  }

  if (
    lowerType.includes("pdf") ||
    lowerType.startsWith("text/") ||
    lowerName.endsWith(".pdf") ||
    lowerName.endsWith(".doc") ||
    lowerName.endsWith(".docx") ||
    lowerName.endsWith(".txt")
  ) {
    return FileText;
  }

  if (
    lowerName.endsWith(".zip") ||
    lowerName.endsWith(".rar") ||
    lowerName.endsWith(".7z") ||
    lowerName.endsWith(".tar") ||
    lowerName.endsWith(".gz")
  ) {
    return FileArchive;
  }

  return File;
}

function formatTime(value) {
  if (!value) return "";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "";

  return date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function getUsername() {
  return sessionStorage.getItem("cloudchat_username")?.trim() || "";
}

/*
 * MongoDB history records use transferId.
 * Live events may use transferId or id.
 * Use one stable key to avoid showing the same file twice.
 */
function normalizeFile(file, fallbackGroupName) {
  if (!file || typeof file !== "object") {
    return null;
  }

  const transferId = file.transferId || file.id || "";

  const fileName = file.fileName || file.originalFileName || "Unknown file";

  const group = file.groupName || fallbackGroupName;

  if (!group || !fileName) {
    return null;
  }

  // Files in permanent history must have a backend transfer ID.
  // Live events without an ID get a temporary deduplication key.
  const id =
    transferId ||
    [
      group,
      file.sender || "Unknown",
      fileName,
      file.fileSize || 0,
      file.receivedAt || file.uploadedAt || "",
    ].join("::");

  return {
    ...file,
    id,
    transferId: transferId || null,
    groupName: group,
    fileName,
    sender: file.sender || "Unknown",
    fileSize: Number(file.fileSize) || 0,
    fileType: file.fileType || "application/octet-stream",
    receivedAt: file.receivedAt || file.uploadedAt || "",
    downloadUrl: file.downloadUrl || null,
    blob: file.blob || null,
  };
}

function mergeFiles(previous, incoming, groupName) {
  const merged = new Map();

  for (const rawFile of [...previous, ...incoming]) {
    const file = normalizeFile(rawFile, groupName);

    if (!file) continue;

    const existing = merged.get(file.id);

    if (existing) {
      // Preserve locally available data while allowing permanent
      // history metadata to update the existing entry.
      merged.set(file.id, {
        ...existing,
        ...file,
        blob: file.blob || existing.blob || null,
        downloadUrl: file.downloadUrl || existing.downloadUrl || null,
        transferId: file.transferId || existing.transferId || null,
      });
    } else {
      merged.set(file.id, file);
    }
  }

  return [...merged.values()].sort((a, b) => {
    const aTime = new Date(a.receivedAt || 0).getTime() || 0;
    const bTime = new Date(b.receivedAt || 0).getTime() || 0;

    return bTime - aTime;
  });
}

function triggerBlobDownload(blob, fileName) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");

  anchor.href = url;
  anchor.download = fileName || "download";

  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  // Give the browser time to begin the download.
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export default function GroupFileSharing({ groupName }) {
  const fileInputRef = useRef(null);
  const refreshTimeoutRef = useRef(null);

  const [selectedFile, setSelectedFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatus, setUploadStatus] = useState("");

  const [receivedFiles, setReceivedFiles] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [downloadingIds, setDownloadingIds] = useState([]);

  /*
   * Load permanent history from MongoDB through the Java API.
   * This works after reconnecting through either chat server,
   * provided the API and servers share the same database.
   */
  const loadGroupFileHistory = useCallback(
    async ({ silent = false } = {}) => {
      const username = getUsername();

      if (!groupName || !username) {
        setReceivedFiles([]);
        setHistoryError(
          !username ? "Please sign in again to view group files." : "",
        );
        return;
      }

      if (!silent) {
        setLoadingHistory(true);
      }

      setHistoryError("");

      try {
        const params = new URLSearchParams({
          groupName,
          username,
          limit: "200",
        });

        const response = await fetch(
          `${API_BASE_URL}/api/group-files/history?${params.toString()}`,
          {
            method: "GET",
            headers: {
              Accept: "application/json",
            },
            cache: "no-store",
          },
        );

        const result = await response.json().catch(() => ({}));

        if (!response.ok || !result.success) {
          throw new Error(
            result.message || `Failed to load history (${response.status}).`,
          );
        }

        const historyFiles = Array.isArray(result.files)
          ? result.files
              .map((file) => normalizeFile(file, groupName))
              .filter(Boolean)
          : [];

        setReceivedFiles((previous) =>
          mergeFiles(previous, historyFiles, groupName),
        );
      } catch (error) {
        console.error("[GROUP FILE] History request failed:", error);

        setHistoryError(
          error?.message || "Could not load permanent group-file history.",
        );
      } finally {
        if (!silent) {
          setLoadingHistory(false);
        }
      }
    },
    [groupName],
  );

  useEffect(() => {
    let cancelled = false;

    Promise.resolve().then(() => {
      if (!cancelled) {
        loadGroupFileHistory();
      }
    });

    return () => {
      cancelled = true;

      if (refreshTimeoutRef.current) {
        window.clearTimeout(refreshTimeoutRef.current);
      }
    };
  }, [groupName, loadGroupFileHistory]);

  /*
   * Listen for live group-file events.
   * Permanent history remains the source of truth for older files.
   */
  useEffect(() => {
    if (!groupName) return undefined;

    const unsubscribe = onChatMessage((data) => {
      if (!data || typeof data !== "object") return;

      if (data.groupName && data.groupName !== groupName) {
        return;
      }

      switch (data.type) {
        case "group_file_upload_started": {
          setUploading(true);
          setUploadProgress(0);
          setUploadStatus("Preparing file...");
          break;
        }

        case "group_file_upload_complete": {
          setUploadProgress(100);
          setUploadStatus("File sent successfully.");

          // Refresh permanent history after the backend has saved it.
          if (refreshTimeoutRef.current) {
            window.clearTimeout(refreshTimeoutRef.current);
          }

          refreshTimeoutRef.current = window.setTimeout(() => {
            loadGroupFileHistory({ silent: true });
          }, 500);

          break;
        }

        case "group_file_upload_error": {
          setUploading(false);
          setUploadProgress(0);
          setUploadStatus(data.message || "File upload failed.");
          break;
        }

        case "group_file_receive_started":
        case "group_file_receive_progress": {
          break;
        }

        case "group_file_received": {
          console.log("[GROUP FILE RECEIVED]", {
            transferId: receivedFile.transferId,
            id: receivedFile.id,
            fileName: receivedFile.fileName,
            sender: receivedFile.sender,
            groupName: receivedFile.groupName,
            fileSize: receivedFile.fileSize,
            receivedAt: receivedFile.receivedAt,
            fullEvent: data,
          });

          const receivedFile = normalizeFile(
            {
              ...data,
              receivedAt: data.receivedAt || new Date().toISOString(),
            },
            groupName,
          );

          if (receivedFile) {
            setReceivedFiles((previous) =>
              mergeFiles(previous, [receivedFile], groupName),
            );
          }

          /*
           * A receive event may arrive before the permanent history
           * metadata becomes visible. Refresh shortly afterward.
           */
          if (refreshTimeoutRef.current) {
            window.clearTimeout(refreshTimeoutRef.current);
          }

          refreshTimeoutRef.current = window.setTimeout(() => {
            loadGroupFileHistory({ silent: true });
          }, 700);

          break;
        }

        case "group_file_delivery_error": {
          setUploadStatus(
            data.message || "A group file could not be delivered.",
          );
          break;
        }

        default:
          break;
      }
    });

    return () => {
      if (typeof unsubscribe === "function") {
        unsubscribe();
      }
    };
  }, [groupName, loadGroupFileHistory]);

  useEffect(() => {
    const handleRefresh = () => {
      loadGroupFileHistory();
    };

    window.addEventListener("cloudchat:refresh-group-files", handleRefresh);

    return () => {
      window.removeEventListener(
        "cloudchat:refresh-group-files",
        handleRefresh,
      );
    };
  }, [loadGroupFileHistory]);

  const handleFileSelection = (event) => {
    const file = event.target.files?.[0];

    if (!file) return;

    if (file.size <= 0) {
      setUploadStatus("The selected file is empty.");
      setSelectedFile(null);
      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      setUploadStatus("File size cannot exceed 100 MB.");
      setSelectedFile(null);

      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }

      return;
    }

    setSelectedFile(file);
    setUploadStatus("");
  };

  const clearSelection = () => {
    setSelectedFile(null);
    setUploadProgress(0);

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleSendFile = async () => {
    if (!groupName) {
      setUploadStatus("No group selected.");
      return;
    }

    if (!selectedFile) {
      setUploadStatus("Please select a file first.");
      return;
    }

    if (uploading) return;

    try {
      setUploading(true);
      setUploadProgress(0);
      setUploadStatus("Starting upload...");

      await sendGroupFile(groupName, selectedFile, (progress) => {
        const percentage = Math.max(0, Math.min(100, Number(progress) || 0));

        setUploadProgress(percentage);
        setUploadStatus(`Uploading ${percentage}%`);
      });

      setUploadProgress(100);
      setUploadStatus("File sent successfully.");

      // Give the backend time to finish saving permanent history.
      window.setTimeout(() => {
        setUploading(false);
        setUploadStatus("");
        clearSelection();
        loadGroupFileHistory({ silent: true });
      }, 1000);
    } catch (error) {
      console.error("[GROUP FILE] Upload failed:", error);

      setUploading(false);
      setUploadProgress(0);
      setUploadStatus(error?.message || "Failed to send group file.");
    }
  };

  const handleRemoveSelectedFile = () => {
    if (uploading) return;

    setUploadStatus("");
    clearSelection();
  };

  /*
   * Prefer the permanent backend download endpoint.
   * A local blob/object URL is used only as a fallback for
   * older live events that do not have a transferId.
   */
  const handleDownload = async (file) => {
    if (!file) return;

    const username = getUsername();

    if (!username) {
      setHistoryError("Please sign in again to download this file.");
      return;
    }

    if (!file.transferId) {
      if (file.blob instanceof Blob) {
        triggerBlobDownload(file.blob, file.fileName);
        return;
      }

      if (file.downloadUrl) {
        const anchor = document.createElement("a");
        anchor.href = file.downloadUrl;
        anchor.download = file.fileName || "download";
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        return;
      }

      setHistoryError(
        "This file has no permanent transfer ID. Refresh the group history.",
      );
      return;
    }

    setDownloadingIds((previous) =>
      previous.includes(file.id) ? previous : [...previous, file.id],
    );

    try {
      const params = new URLSearchParams({
        transferId: file.transferId,
        groupName,
        username,
      });

      const response = await fetch(
        `${API_BASE_URL}/api/group-files/download?${params.toString()}`,
        {
          method: "GET",
          cache: "no-store",
        },
      );

      if (!response.ok) {
        let message = `Download failed (${response.status}).`;

        try {
          const result = await response.json();
          message = result.message || message;
        } catch {
          // The server may return a non-JSON error response.
        }

        throw new Error(message);
      }

      const blob = await response.blob();

      if (!blob.size) {
        throw new Error("The downloaded file is empty.");
      }

      triggerBlobDownload(blob, file.fileName);
      setHistoryError("");
    } catch (error) {
      console.error("[GROUP FILE] Download failed:", error);

      setHistoryError(error?.message || "Could not download the group file.");
    } finally {
      setDownloadingIds((previous) => previous.filter((id) => id !== file.id));
    }
  };

  const selectedFileIcon = selectedFile
    ? getFileIcon(selectedFile.name, selectedFile.type)
    : Paperclip;

  return (
    <div
      style={{
        width: "100%",
        marginTop: "12px",
        color: "#fff",
      }}
    >
      {/* Header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: "8px",
          marginBottom: "10px",
          padding: "0 3px",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "8px",
          }}
        >
          <Paperclip size={16} />

          <span style={{ fontSize: "13px", fontWeight: 700 }}>
            Group File Sharing
          </span>
        </div>

        <span style={{ fontSize: "10px", opacity: 0.55 }}>Max 100 MB</span>
      </div>

      {/* Send file card */}
      <div
        style={{
          width: "100%",
          boxSizing: "border-box",
          padding: "12px",
          borderRadius: "12px",
          background: "rgba(255,255,255,0.04)",
          border: "1px solid rgba(255,255,255,0.09)",
        }}
      >
        {!selectedFile ? (
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            style={{
              width: "100%",
              minHeight: "48px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "8px",
              borderRadius: "10px",
              border: "1px dashed rgba(255,255,255,0.2)",
              background: "rgba(255,255,255,0.035)",
              color: "rgba(255,255,255,0.75)",
              cursor: uploading ? "not-allowed" : "pointer",
              fontSize: "13px",
              fontWeight: 600,
            }}
          >
            <Paperclip size={18} />
            <span>Select a file to send to the group</span>
          </button>
        ) : (
          <div
            style={{
              borderRadius: "10px",
              background: "rgba(255,255,255,0.045)",
              border: "1px solid rgba(255,255,255,0.08)",
              padding: "10px",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "10px",
              }}
            >
              <div
                style={{
                  width: "38px",
                  height: "38px",
                  borderRadius: "9px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  background: "rgba(59,130,246,0.14)",
                  border: "1px solid rgba(59,130,246,0.2)",
                  flexShrink: 0,
                }}
              >
                <FileIconDisplay icon={selectedFileIcon} size={18} />
              </div>

              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{
                    fontSize: "12px",
                    fontWeight: 600,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {getDisplayFileName(selectedFile.name)}
                </div>

                <div
                  style={{
                    fontSize: "10px",
                    opacity: 0.5,
                    marginTop: "3px",
                  }}
                >
                  {formatFileSize(selectedFile.size)}
                </div>
              </div>

              {!uploading && (
                <button
                  type="button"
                  onClick={handleRemoveSelectedFile}
                  title="Remove file"
                  style={{
                    width: "30px",
                    height: "30px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    borderRadius: "8px",
                    border: "none",
                    background: "rgba(255,255,255,0.06)",
                    color: "rgba(255,255,255,0.55)",
                    cursor: "pointer",
                  }}
                >
                  <X size={15} />
                </button>
              )}
            </div>

            {uploading && (
              <div style={{ marginTop: "12px" }}>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    fontSize: "10px",
                    marginBottom: "5px",
                    opacity: 0.65,
                  }}
                >
                  <span>{uploadStatus || "Uploading..."}</span>
                  <span>{uploadProgress}%</span>
                </div>

                <div
                  style={{
                    height: "5px",
                    borderRadius: "999px",
                    background: "rgba(255,255,255,0.08)",
                    overflow: "hidden",
                  }}
                >
                  <div
                    style={{
                      height: "100%",
                      width: `${uploadProgress}%`,
                      background: "#3b82f6",
                      borderRadius: "999px",
                      transition: "width 0.2s ease",
                    }}
                  />
                </div>
              </div>
            )}

            {!uploading && (
              <button
                type="button"
                onClick={handleSendFile}
                style={{
                  width: "100%",
                  marginTop: "10px",
                  height: "38px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "7px",
                  borderRadius: "9px",
                  border: "none",
                  background: "#2563eb",
                  color: "#fff",
                  cursor: "pointer",
                  fontSize: "12px",
                  fontWeight: 600,
                }}
              >
                <Send size={15} />
                <span>Send to Group</span>
              </button>
            )}
          </div>
        )}

        {uploadStatus && !uploading && (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "6px",
              marginTop: "9px",
              fontSize: "10px",
              color: /failed|cannot|empty|exceed|select a file|no group/i.test(
                uploadStatus,
              )
                ? "#fca5a5"
                : "rgba(255,255,255,0.65)",
            }}
          >
            <CheckCircle2 size={13} />
            <span>{uploadStatus}</span>
          </div>
        )}

        <input
          ref={fileInputRef}
          type="file"
          style={{ display: "none" }}
          onChange={handleFileSelection}
        />
      </div>

      {/* Permanent group file history */}
      <div
        style={{
          marginTop: "10px",
          padding: "12px",
          borderRadius: "12px",
          background: "rgba(255,255,255,0.04)",
          border: "1px solid rgba(255,255,255,0.09)",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "8px",
            marginBottom: "9px",
          }}
        >
          <div>
            <div style={{ fontSize: "12px", fontWeight: 700 }}>
              Group File History
            </div>

            <div
              style={{
                fontSize: "10px",
                opacity: 0.5,
                marginTop: "3px",
              }}
            >
              Files saved in permanent server storage
            </div>
          </div>

          <button
            type="button"
            onClick={() => loadGroupFileHistory()}
            disabled={loadingHistory}
            title="Refresh history"
            style={{
              border: "1px solid rgba(255,255,255,0.12)",
              background: "rgba(255,255,255,0.05)",
              color: "#fff",
              padding: "6px 9px",
              borderRadius: "7px",
              fontSize: "10px",
              cursor: loadingHistory ? "wait" : "pointer",
            }}
          >
            {loadingHistory ? "Loading..." : "Refresh"}
          </button>
        </div>

        {historyError && (
          <div
            role="alert"
            style={{
              padding: "9px",
              marginBottom: "9px",
              borderRadius: "8px",
              background: "rgba(239,68,68,0.08)",
              border: "1px solid rgba(239,68,68,0.2)",
              color: "#fca5a5",
              fontSize: "11px",
              overflowWrap: "anywhere",
            }}
          >
            {historyError}
          </div>
        )}

        {loadingHistory && receivedFiles.length === 0 ? (
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              gap: "8px",
              padding: "20px 5px",
              fontSize: "11px",
              opacity: 0.65,
            }}
          >
            <Loader2
              size={15}
              style={{ animation: "spin 1s linear infinite" }}
            />
            Loading permanent group files...
          </div>
        ) : receivedFiles.length === 0 ? (
          <div
            style={{
              textAlign: "center",
              padding: "20px 5px",
              fontSize: "11px",
              opacity: 0.5,
            }}
          >
            No files have been saved for this group yet.
          </div>
        ) : (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "7px",
            }}
          >
            {receivedFiles.map((file) => {
              const ReceivedFileIcon = getFileIcon(
                file.fileName,
                file.fileType,
              );

              const isDownloading = downloadingIds.includes(file.id);

              return (
                <div
                  key={file.id}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "9px",
                    padding: "9px",
                    borderRadius: "9px",
                    background: "rgba(255,255,255,0.035)",
                    border: "1px solid rgba(255,255,255,0.07)",
                  }}
                >
                  <div
                    style={{
                      width: "34px",
                      height: "34px",
                      borderRadius: "8px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      background: "rgba(59,130,246,0.12)",
                      flexShrink: 0,
                    }}
                  >
                    <ReceivedFileIcon size={17} />
                  </div>

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        fontSize: "11px",
                        fontWeight: 600,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                      title={file.fileName}
                    >
                      {getDisplayFileName(file.fileName)}
                    </div>

                    <div
                      style={{
                        fontSize: "9px",
                        opacity: 0.55,
                        marginTop: "3px",
                        overflowWrap: "anywhere",
                      }}
                    >
                      {file.sender} • {formatFileSize(file.fileSize)}
                      {(file.uploadedAt || file.receivedAt) && (
                        <>
                          {" • "}
                          {formatTime(file.uploadedAt || file.receivedAt)}
                        </>
                      )}
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleDownload(file)}
                    disabled={isDownloading}
                    title="Download file"
                    aria-label={`Download ${file.fileName}`}
                    style={{
                      width: "32px",
                      height: "32px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      borderRadius: "8px",
                      border: "1px solid rgba(255,255,255,0.08)",
                      background: "rgba(255,255,255,0.06)",
                      color: "#fff",
                      cursor: isDownloading ? "wait" : "pointer",
                      opacity: isDownloading ? 0.55 : 1,
                      flexShrink: 0,
                    }}
                  >
                    {isDownloading ? (
                      <Loader2
                        size={15}
                        style={{ animation: "spin 1s linear infinite" }}
                      />
                    ) : (
                      <Download size={15} />
                    )}
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {uploading && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "6px",
            marginTop: "7px",
            fontSize: "10px",
            opacity: 0.5,
          }}
        >
          <Loader2 size={13} style={{ animation: "spin 1s linear infinite" }} />
          <span>Sending {selectedFile?.name || "file"}...</span>
        </div>
      )}
    </div>
  );
}
