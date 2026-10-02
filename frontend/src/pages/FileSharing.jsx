import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
  ArrowLeft,
  Download,
  File,
  FileText,
  Image,
  Loader2,
  Search,
  Send,
  UserRound,
  X,
} from "lucide-react";

import { getOnlineUsers, searchUsers } from "../services/api";

import {
  isChatConnected,
  onChatClosed,
  onChatMessage,
  sendPrivateFile,
} from "../services/chatSocket";

import {
  getStoredFilesForUser,
  saveReceivedFile,
} from "../services/receivedFileStore";

function FileSharing() {
  const navigate = useNavigate();

  const username = sessionStorage.getItem("cloudchat_username") || "";

  const fileInputRef = useRef(null);

  // =========================================================
  // STATE
  // =========================================================

  const [onlineUsers, setOnlineUsers] = useState([]);

  const [searchResults, setSearchResults] = useState([]);

  const [search, setSearch] = useState("");

  const [selectedRecipient, setSelectedRecipient] = useState(null);

  const [selectedFile, setSelectedFile] = useState(null);

  const [uploadingFile, setUploadingFile] = useState(false);

  const [uploadProgress, setUploadProgress] = useState(0);

  const [files, setFiles] = useState([]);

  const [loadingFiles, setLoadingFiles] = useState(true);

  const [searchingUsers, setSearchingUsers] = useState(false);

  const [connected, setConnected] = useState(false);

  const [error, setError] = useState("");

  const [success, setSuccess] = useState("");

  // =========================================================
  // AUTHENTICATION
  // =========================================================

  useEffect(() => {
    if (!username) {
      navigate("/login", {
        replace: true,
      });
    }
  }, [username, navigate]);

  // =========================================================
  // CONNECTION STATE
  // =========================================================

  useEffect(() => {
    const unsubscribeClose = onChatClosed(() => {
      setConnected(false);
    });

    const checkConnection = () => {
      setConnected(isChatConnected());
    };

    const interval = setInterval(checkConnection, 1000);

    checkConnection();

    return () => {
      clearInterval(interval);
      unsubscribeClose();
    };
  }, []);

  // =========================================================
  // LOAD ONLINE USERS
  // =========================================================

  useEffect(() => {
    if (!username) {
      return undefined;
    }

    let cancelled = false;

    const fetchUsers = async () => {
      try {
        const result = await getOnlineUsers();

        if (cancelled) {
          return;
        }

        if (result.success) {
          setOnlineUsers(
            (result.users || []).filter((user) => user.username !== username),
          );
        }
      } catch (err) {
        if (!cancelled) {
          console.error("[FILE SHARING] Unable to load online users:", err);
        }
      }
    };

    fetchUsers();

    const interval = setInterval(fetchUsers, 10000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [username]);

  // =========================================================
  // SEARCH REGISTERED USERS
  // =========================================================

  useEffect(() => {
    const query = search.trim();

    if (!query) {
      return undefined;
    }

    let cancelled = false;

    const timer = setTimeout(async () => {
      try {
        setSearchingUsers(true);

        const result = await searchUsers(query);

        if (cancelled) {
          return;
        }

        if (result.success) {
          const users = (result.users || []).filter(
            (user) => user.username && user.username !== username,
          );

          setSearchResults(users);
        } else {
          setSearchResults([]);
        }
      } catch (err) {
        if (!cancelled) {
          console.error("[FILE SHARING SEARCH] Failed:", err);

          setSearchResults([]);
        }
      } finally {
        if (!cancelled) {
          setSearchingUsers(false);
        }
      }
    }, 400);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [search, username]);

  useEffect(() => {
    let cancelled = false;

    const loadHistory = async () => {
      if (!username) {
        return;
      }

      try {
        const storedFiles = await getStoredFilesForUser(username);

        if (!cancelled) {
          setFiles(storedFiles || []);
        }
      } catch (err) {
        if (!cancelled) {
          console.error("[FILE SHARING] Failed to load file history:", err);
        }
      } finally {
        if (!cancelled) {
          setLoadingFiles(false);
        }
      }
    };

    loadHistory();

    return () => {
      cancelled = true;
    };
  }, [username]);

  // =========================================================
  // DISPLAY USERS
  // =========================================================

  const onlineUsernameSet = useMemo(() => {
    return new Set(onlineUsers.map((user) => user.username));
  }, [onlineUsers]);

  const displayedUsers = useMemo(() => {
    const query = search.trim();

    if (!query) {
      return onlineUsers;
    }

    return searchResults.map((user) => ({
      username: user.username,

      online: onlineUsernameSet.has(user.username),

      server:
        user.server ||
        (
          onlineUsers.find(
            (onlineUser) => onlineUser.username === user.username,
          ) || {}
        ).server ||
        "CloudChat",
    }));
  }, [search, searchResults, onlineUsers, onlineUsernameSet]);

  // =========================================================
  // LOAD FILE HISTORY
  // =========================================================

  const loadFileHistory = useCallback(async () => {
    if (!username) {
      return;
    }

    try {
      const storedFiles = await getStoredFilesForUser(username);

      setFiles(storedFiles || []);
    } catch (err) {
      console.error("[FILE SHARING] Failed to load file history:", err);
    }
  }, [username]);

  // =========================================================
  // REAL-TIME FILE EVENTS
  // =========================================================

  useEffect(() => {
    const unsubscribeMessage = onChatMessage(async (data) => {
      if (!data) {
        return;
      }

      // -----------------------------------------------
      // CONNECTION
      // -----------------------------------------------

      if (
        data.type === "login_success" ||
        data.type === "server_connected" ||
        data.type === "server_reconnected"
      ) {
        setConnected(true);

        return;
      }

      if (data.type === "server_disconnected") {
        setConnected(false);

        return;
      }

      // -----------------------------------------------
      // FILE UPLOAD STATUS
      // -----------------------------------------------

      if (
        data.type === "file_upload_started" ||
        data.type === "file_upload_complete" ||
        data.type === "file_upload_error"
      ) {
        return;
      }

      // -----------------------------------------------
      // FILE RECEIVED
      // -----------------------------------------------

      if (data.type === "file_received") {
        console.log("[FILE SHARING] File received:", data);

        await loadFileHistory();

        setSuccess(`File received: ${data.fileName || "file"}`);

        setTimeout(() => {
          setSuccess("");
        }, 4000);

        return;
      }

      // -----------------------------------------------
      // FILE DELIVERY
      // -----------------------------------------------

      if (data.type === "file_delivery_started") {
        return;
      }

      if (data.type === "file_delivery_complete") {
        console.log("[FILE SHARING] File delivery completed:", data);

        await loadFileHistory();

        return;
      }

      // -----------------------------------------------
      // FILE ERROR
      // -----------------------------------------------

      if (data.type === "file_delivery_error") {
        setError(data.message || "Unable to deliver the file.");

        return;
      }
    });

    return () => {
      unsubscribeMessage();
    };
  }, [loadFileHistory]);

  // =========================================================
  // SELECT RECIPIENT
  // =========================================================

  const handleSelectRecipient = (user) => {
    if (!user?.username) {
      return;
    }

    if (uploadingFile) {
      return;
    }

    setSelectedRecipient(user);

    setError("");

    setSuccess("");
  };

  // =========================================================
  // SELECT FILE
  // =========================================================

  const handleFileSelect = (event) => {
    const file = event.target.files?.[0];

    event.target.value = "";

    if (!file) {
      return;
    }

    if (!selectedRecipient) {
      setError("Please select a recipient first.");

      return;
    }

    if (!isChatConnected()) {
      setError("Chat connection is not active.");

      return;
    }

    if (uploadingFile) {
      setError("Another file is already being uploaded.");

      return;
    }

    // -----------------------------------------------------
    // MAXIMUM FILE SIZE
    // -----------------------------------------------------

    const MAX_FILE_SIZE = 100 * 1024 * 1024;

    if (file.size > MAX_FILE_SIZE) {
      setError("File is too large. Maximum size is 100 MB.");

      return;
    }

    if (file.size <= 0) {
      setError("The selected file is empty.");

      return;
    }

    setSelectedFile(file);

    setError("");

    setSuccess("");
  };

  // =========================================================
  // OPEN FILE PICKER
  // =========================================================

  const handleChooseFile = () => {
    if (!selectedRecipient) {
      setError("Please select a recipient first.");

      return;
    }

    if (!connected) {
      setError("Chat connection is not active.");

      return;
    }

    if (uploadingFile) {
      return;
    }

    fileInputRef.current?.click();
  };

  // =========================================================
  // CLEAR FILE
  // =========================================================

  const handleClearFile = () => {
    if (uploadingFile) {
      return;
    }

    setSelectedFile(null);

    setUploadProgress(0);

    setError("");

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  // =========================================================
  // SEND FILE
  // =========================================================

  const handleSendFile = async () => {
    if (!selectedRecipient) {
      setError("Please select a recipient.");

      return;
    }

    if (!selectedFile) {
      setError("Please select a file.");

      return;
    }

    if (!isChatConnected()) {
      setError("Chat connection is not active.");

      return;
    }

    if (uploadingFile) {
      return;
    }

    const recipient = selectedRecipient.username;

    const file = selectedFile;

    setUploadingFile(true);

    setUploadProgress(0);

    setError("");

    setSuccess("");

    console.log("[FILE SHARING] Sending file:", {
      recipient,
      fileName: file.name,
      fileSize: file.size,
      online: selectedRecipient.online,
    });

    try {
      // IMPORTANT:
      //
      // We intentionally do NOT check whether
      // the recipient is online.
      //
      // Java backend decides:
      //
      // online  -> immediate delivery
      // offline -> OfflineFileService queue

      await sendPrivateFile(
        recipient,
        file,
        (progress, sentBytes, totalBytes) => {
          console.log(
            "[FILE SHARING] Progress:",
            progress,
            "%",
            sentBytes,
            "/",
            totalBytes,
          );

          setUploadProgress(progress);
        },
      );

      // ---------------------------------------------------
      // Persist sent file locally
      // ---------------------------------------------------

      const storedFile = await saveReceivedFile({
        sender: username,
        recipient,
        fileName: file.name,
        fileSize: file.size,
        fileType: file.type || "application/octet-stream",
        blob: file,
        receivedAt: new Date().toISOString(),
      });

      console.log(
        "[FILE SHARING] Sent file stored:",
        storedFile.fileName,
        storedFile.id,
      );

      // ---------------------------------------------------
      // Refresh history
      // ---------------------------------------------------

      await loadFileHistory();

      setSuccess(`File sent successfully to ${recipient}.`);

      setSelectedFile(null);

      setUploadProgress(0);

      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } catch (err) {
      console.error("[FILE SHARING] Upload failed:", err);

      setError(err.message || "Unable to send file.");
    } finally {
      setUploadingFile(false);
    }
  };

  // =========================================================
  // DOWNLOAD FILE
  // =========================================================

  const handleDownloadFile = useCallback((file) => {
    if (!file) {
      return;
    }

    try {
      // -----------------------------------------------
      // IndexedDB stored Blob
      // -----------------------------------------------

      if (file.blob) {
        const url = URL.createObjectURL(file.blob);

        const link = document.createElement("a");

        link.href = url;

        link.download = file.fileName || "download";

        document.body.appendChild(link);

        link.click();

        document.body.removeChild(link);

        setTimeout(() => {
          URL.revokeObjectURL(url);
        }, 1000);

        return;
      }

      // -----------------------------------------------
      // Backend download URL
      // -----------------------------------------------

      if (file.downloadUrl) {
        const link = document.createElement("a");

        link.href = file.downloadUrl;

        link.download = file.fileName || "download";

        document.body.appendChild(link);

        link.click();

        document.body.removeChild(link);

        return;
      }

      setError("This file is not available for download.");
    } catch (err) {
      console.error("[FILE SHARING] Download failed:", err);

      setError("Unable to download the file.");
    }
  }, []);

  // =========================================================
  // FORMAT FILE SIZE
  // =========================================================

  const formatFileSize = (bytes) => {
    if (!bytes || bytes <= 0) {
      return "0 B";
    }

    if (bytes < 1024) {
      return `${bytes} B`;
    }

    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }

    if (bytes < 1024 * 1024 * 1024) {
      return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
  };

  // =========================================================
  // FORMAT DATE
  // =========================================================

  const formatDate = (date) => {
    if (!date) {
      return "";
    }

    const parsed = date instanceof Date ? date : new Date(date);

    if (Number.isNaN(parsed.getTime())) {
      return "";
    }

    return new Intl.DateTimeFormat("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(parsed);
  };

  // =========================================================
  // FILE ICON
  // =========================================================

  const getFileIcon = (fileName) => {
    const extension = fileName?.split(".").pop()?.toLowerCase();

    if (["png", "jpg", "jpeg", "gif", "webp", "svg"].includes(extension)) {
      return <Image size={21} />;
    }

    if (
      ["txt", "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx"].includes(
        extension,
      )
    ) {
      return <FileText size={21} />;
    }

    return <File size={21} />;
  };

  // =========================================================
  // LOGOUT
  // =========================================================

  const handleLogout = () => {
    sessionStorage.removeItem("cloudchat_username");

    navigate("/login", {
      replace: true,
    });
  };

  // =========================================================
  // UI
  // =========================================================

  return (
    <div
      className="cloudchat-app"
      style={{
        minHeight: "100vh",
        padding: "20px",
        boxSizing: "border-box",
      }}
    >
      <div className="bg-glow bg-glow-left" />

      <div className="bg-glow bg-glow-right" />

      <main
        style={{
          maxWidth: "1400px",
          minHeight: "calc(100vh - 40px)",
          margin: "0 auto",
          position: "relative",
          zIndex: 1,
          display: "flex",
          flexDirection: "column",
        }}
      >
        {/* ===================================================
            HEADER
        ==================================================== */}

        <header
          className="auth-card"
          style={{
            padding: "14px 18px",
            minHeight: "64px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "15px",
            marginBottom: "14px",
          }}
        >
          {/* LEFT */}

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "12px",
            }}
          >
            <button
              type="button"
              onClick={() => navigate("/dashboard")}
              className="icon-button"
              title="Back to dashboard"
              style={{
                width: "40px",
                height: "40px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                border: "1px solid rgba(255,255,255,0.1)",
                borderRadius: "10px",
                background: "rgba(255,255,255,0.04)",
                color: "inherit",
                cursor: "pointer",
              }}
            >
              <ArrowLeft size={19} />
            </button>

            <div>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                }}
              >
                <File size={20} />

                <strong
                  style={{
                    fontSize: "18px",
                  }}
                >
                  File Sharing
                </strong>
              </div>

              <div
                style={{
                  fontSize: "11px",
                  opacity: 0.55,
                  marginTop: "2px",
                }}
              >
                Distributed file transfer workspace
              </div>
            </div>
          </div>

          {/* RIGHT */}

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "16px",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "7px",
                fontSize: "12px",
                opacity: 0.8,
              }}
            >
              <span
                style={{
                  width: "8px",
                  height: "8px",
                  borderRadius: "50%",
                  background: connected ? "#4ade80" : "#f87171",
                  boxShadow: connected
                    ? "0 0 8px rgba(74,222,128,0.6)"
                    : "none",
                }}
              />

              {connected ? "Connected" : "Disconnected"}
            </div>

            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "9px",
              }}
            >
              <div
                style={{
                  width: "34px",
                  height: "34px",
                  borderRadius: "50%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  background: "rgba(255,255,255,0.08)",
                }}
              >
                <UserRound size={17} />
              </div>

              <span
                style={{
                  fontSize: "14px",
                  fontWeight: 600,
                }}
              >
                {username}
              </span>
            </div>

            <button
              type="button"
              onClick={handleLogout}
              className="admin-link"
              style={{
                background: "transparent",
                border: "none",
                color: "inherit",
                cursor: "pointer",
              }}
            >
              Logout
            </button>
          </div>
        </header>

        {/* ===================================================
            ALERTS
        ==================================================== */}

        {error && (
          <div
            style={{
              padding: "10px 14px",
              marginBottom: "12px",
              borderRadius: "9px",
              background: "rgba(220,38,38,0.12)",
              border: "1px solid rgba(220,38,38,0.25)",
              color: "#f87171",
              fontSize: "13px",
            }}
          >
            {error}
          </div>
        )}

        {success && (
          <div
            style={{
              padding: "10px 14px",
              marginBottom: "12px",
              borderRadius: "9px",
              background: "rgba(34,197,94,0.10)",
              border: "1px solid rgba(34,197,94,0.22)",
              color: "#4ade80",
              fontSize: "13px",
            }}
          >
            {success}
          </div>
        )}

        {/* ===================================================
            MAIN WORKSPACE
        ==================================================== */}

        <section
          className="auth-card"
          style={{
            flex: 1,
            padding: 0,
            overflow: "hidden",
            display: "grid",
            gridTemplateColumns: "280px minmax(0, 1fr)",
            minHeight: "600px",
          }}
        >
          {/* =================================================
              LEFT — RECIPIENTS
          ================================================== */}

          <aside
            style={{
              borderRight: "1px solid rgba(255,255,255,0.08)",
              display: "flex",
              flexDirection: "column",
              minWidth: 0,
            }}
          >
            <div
              style={{
                padding: "16px",
                borderBottom: "1px solid rgba(255,255,255,0.08)",
              }}
            >
              <div
                style={{
                  fontSize: "13px",
                  fontWeight: 700,
                  marginBottom: "12px",
                }}
              >
                Send file to
              </div>

              <div
                style={{
                  position: "relative",
                }}
              >
                <Search
                  size={16}
                  style={{
                    position: "absolute",
                    left: "12px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    opacity: 0.45,
                  }}
                />

                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Search users..."
                  style={{
                    width: "100%",
                    boxSizing: "border-box",
                    padding: "10px 12px 10px 36px",
                    borderRadius: "9px",
                    border: "1px solid rgba(255,255,255,0.1)",
                    background: "rgba(255,255,255,0.035)",
                    color: "inherit",
                    outline: "none",
                  }}
                />
              </div>
            </div>

            <div
              style={{
                padding: "14px 12px 8px",
                fontSize: "11px",
                fontWeight: 700,
                letterSpacing: "1.2px",
                opacity: 0.45,
              }}
            >
              RECIPIENTS
            </div>

            <div
              style={{
                flex: 1,
                overflowY: "auto",
                padding: "0 8px 12px",
              }}
            >
              {search.trim() && searchingUsers && (
                <div
                  style={{
                    padding: "20px 12px",
                    fontSize: "13px",
                    opacity: 0.55,
                  }}
                >
                  Searching users...
                </div>
              )}

              {!search.trim() && displayedUsers.length === 0 && (
                <div
                  style={{
                    padding: "20px 12px",
                    fontSize: "13px",
                    opacity: 0.55,
                  }}
                >
                  No other users online.
                </div>
              )}

              {search.trim() &&
                !searchingUsers &&
                displayedUsers.length === 0 && (
                  <div
                    style={{
                      padding: "20px 12px",
                      fontSize: "13px",
                      opacity: 0.55,
                    }}
                  >
                    No registered user found.
                  </div>
                )}

              {displayedUsers.map((user) => {
                const active = selectedRecipient?.username === user.username;

                const isOnline = onlineUsernameSet.has(user.username);

                return (
                  <button
                    key={user.username}
                    type="button"
                    onClick={() => handleSelectRecipient(user)}
                    disabled={uploadingFile}
                    style={{
                      width: "100%",
                      display: "flex",
                      alignItems: "center",
                      gap: "10px",
                      padding: "11px 10px",
                      marginBottom: "3px",
                      borderRadius: "9px",
                      border: "none",
                      background: active
                        ? "rgba(255,255,255,0.09)"
                        : "transparent",
                      color: "inherit",
                      textAlign: "left",
                      cursor: uploadingFile ? "not-allowed" : "pointer",
                      opacity: uploadingFile ? 0.6 : 1,
                    }}
                  >
                    <div
                      style={{
                        width: "38px",
                        height: "38px",
                        borderRadius: "50%",
                        flexShrink: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        background: "rgba(255,255,255,0.08)",
                        position: "relative",
                      }}
                    >
                      <UserRound size={18} />

                      <span
                        style={{
                          position: "absolute",
                          right: "-1px",
                          bottom: "-1px",
                          width: "9px",
                          height: "9px",
                          borderRadius: "50%",
                          background: isOnline ? "#4ade80" : "#71717a",
                          border: "2px solid #111",
                        }}
                      />
                    </div>

                    <div
                      style={{
                        minWidth: 0,
                        flex: 1,
                      }}
                    >
                      <div
                        style={{
                          fontSize: "14px",
                          fontWeight: 600,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {user.username}
                      </div>

                      <div
                        style={{
                          fontSize: "10px",
                          opacity: 0.45,
                          marginTop: "2px",
                        }}
                      >
                        {isOnline ? "Online" : "Offline"}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          </aside>

          {/* =================================================
              RIGHT — FILE WORKSPACE
          ================================================== */}

          <section
            style={{
              minWidth: 0,
              minHeight: 0,
              padding: "28px",
              overflowY: "auto",
            }}
          >
            {/* =================================================
                SEND FILE
            ================================================== */}

            <div
              style={{
                marginBottom: "30px",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "10px",
                  marginBottom: "6px",
                }}
              >
                <Send size={21} />

                <h2
                  style={{
                    margin: 0,
                    fontSize: "22px",
                  }}
                >
                  Send File
                </h2>
              </div>

              <p
                style={{
                  margin: "0 0 20px",
                  opacity: 0.55,
                  fontSize: "13px",
                }}
              >
                Send a file to an online or offline CloudChat user.
              </p>

              {/* SELECTED RECIPIENT */}

              <div
                style={{
                  padding: "14px",
                  borderRadius: "10px",
                  border: "1px solid rgba(255,255,255,0.08)",
                  background: "rgba(255,255,255,0.025)",
                  marginBottom: "12px",
                }}
              >
                <div
                  style={{
                    fontSize: "10px",
                    opacity: 0.45,
                    marginBottom: "7px",
                    textTransform: "uppercase",
                    letterSpacing: "1px",
                  }}
                >
                  Recipient
                </div>

                {selectedRecipient ? (
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "10px",
                    }}
                  >
                    <UserRound size={18} />

                    <div
                      style={{
                        flex: 1,
                      }}
                    >
                      <div
                        style={{
                          fontWeight: 600,
                          fontSize: "14px",
                        }}
                      >
                        {selectedRecipient.username}
                      </div>

                      <div
                        style={{
                          fontSize: "11px",
                          opacity: 0.5,
                        }}
                      >
                        {selectedRecipient.online === false
                          ? "Offline — file will be queued"
                          : "Online — file will be delivered immediately"}
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => setSelectedRecipient(null)}
                      disabled={uploadingFile}
                      style={{
                        width: "30px",
                        height: "30px",
                        border: "none",
                        borderRadius: "7px",
                        background: "rgba(255,255,255,0.05)",
                        color: "inherit",
                        cursor: uploadingFile ? "not-allowed" : "pointer",
                      }}
                    >
                      <X size={15} />
                    </button>
                  </div>
                ) : (
                  <div
                    style={{
                      fontSize: "13px",
                      opacity: 0.45,
                    }}
                  >
                    Select a recipient from the left.
                  </div>
                )}
              </div>

              {/* FILE PICKER */}

              <input
                ref={fileInputRef}
                type="file"
                onChange={handleFileSelect}
                style={{
                  display: "none",
                }}
              />

              <button
                type="button"
                onClick={handleChooseFile}
                disabled={uploadingFile || !selectedRecipient}
                style={{
                  width: "100%",
                  minHeight: "110px",
                  padding: "20px",
                  borderRadius: "12px",
                  border: "1px dashed rgba(255,255,255,0.16)",
                  background: "rgba(255,255,255,0.025)",
                  color: "inherit",
                  cursor:
                    uploadingFile || !selectedRecipient
                      ? "not-allowed"
                      : "pointer",
                  opacity: !selectedRecipient ? 0.45 : 1,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "8px",
                }}
              >
                <File size={28} />

                <span
                  style={{
                    fontSize: "14px",
                    fontWeight: 600,
                  }}
                >
                  Choose File
                </span>

                <span
                  style={{
                    fontSize: "11px",
                    opacity: 0.45,
                  }}
                >
                  Maximum size: 100 MB
                </span>
              </button>

              {/* SELECTED FILE */}

              {selectedFile && (
                <div
                  style={{
                    marginTop: "12px",
                    padding: "12px 14px",
                    borderRadius: "10px",
                    border: "1px solid rgba(255,255,255,0.08)",
                    background: "rgba(255,255,255,0.035)",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "10px",
                    }}
                  >
                    {getFileIcon(selectedFile.name)}

                    <div
                      style={{
                        minWidth: 0,
                        flex: 1,
                      }}
                    >
                      <div
                        style={{
                          fontSize: "13px",
                          fontWeight: 600,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {selectedFile.name}
                      </div>

                      <div
                        style={{
                          fontSize: "10px",
                          opacity: 0.5,
                          marginTop: "2px",
                        }}
                      >
                        {formatFileSize(selectedFile.size)}
                      </div>
                    </div>

                    {!uploadingFile && (
                      <button
                        type="button"
                        onClick={handleClearFile}
                        title="Remove file"
                        style={{
                          width: "30px",
                          height: "30px",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          border: "none",
                          borderRadius: "7px",
                          background: "rgba(255,255,255,0.05)",
                          color: "inherit",
                          cursor: "pointer",
                        }}
                      >
                        <X size={15} />
                      </button>
                    )}
                  </div>

                  {/* UPLOAD PROGRESS */}

                  {uploadingFile && (
                    <>
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          marginTop: "12px",
                          fontSize: "11px",
                          opacity: 0.65,
                        }}
                      >
                        <span>Uploading...</span>

                        <span>{uploadProgress}%</span>
                      </div>

                      <div
                        style={{
                          height: "5px",
                          marginTop: "7px",
                          borderRadius: "999px",
                          background: "rgba(255,255,255,0.08)",
                          overflow: "hidden",
                        }}
                      >
                        <div
                          style={{
                            width: `${uploadProgress}%`,
                            height: "100%",
                            borderRadius: "999px",
                            background: "#4ade80",
                            transition: "width 0.15s ease",
                          }}
                        />
                      </div>
                    </>
                  )}
                </div>
              )}

              {/* SEND BUTTON */}

              <button
                type="button"
                onClick={handleSendFile}
                disabled={
                  !selectedRecipient ||
                  !selectedFile ||
                  uploadingFile ||
                  !connected
                }
                style={{
                  width: "100%",
                  marginTop: "12px",
                  padding: "12px 16px",
                  border: "none",
                  borderRadius: "9px",
                  background:
                    selectedRecipient &&
                    selectedFile &&
                    connected &&
                    !uploadingFile
                      ? "rgba(255,255,255,0.13)"
                      : "rgba(255,255,255,0.04)",
                  color: "inherit",
                  cursor:
                    selectedRecipient &&
                    selectedFile &&
                    connected &&
                    !uploadingFile
                      ? "pointer"
                      : "not-allowed",
                  opacity:
                    selectedRecipient &&
                    selectedFile &&
                    connected &&
                    !uploadingFile
                      ? 1
                      : 0.45,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "8px",
                  fontWeight: 600,
                }}
              >
                {uploadingFile ? (
                  <>
                    <Loader2
                      size={17}
                      style={{
                        animation: "spin 1s linear infinite",
                      }}
                    />
                    Sending...
                  </>
                ) : (
                  <>
                    <Send size={17} />
                    Send File
                  </>
                )}
              </button>
            </div>

            {/* =================================================
                FILE HISTORY
            ================================================== */}

            <div
              style={{
                borderTop: "1px solid rgba(255,255,255,0.08)",
                paddingTop: "24px",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  marginBottom: "14px",
                }}
              >
                <div>
                  <h2
                    style={{
                      margin: "0 0 4px",
                      fontSize: "20px",
                    }}
                  >
                    File History
                  </h2>

                  <p
                    style={{
                      margin: 0,
                      fontSize: "12px",
                      opacity: 0.5,
                    }}
                  >
                    Your sent and received files
                  </p>
                </div>

                <span
                  style={{
                    fontSize: "12px",
                    opacity: 0.5,
                  }}
                >
                  {files.length} file
                  {files.length === 1 ? "" : "s"}
                </span>
              </div>

              {loadingFiles ? (
                <div
                  style={{
                    padding: "30px",
                    textAlign: "center",
                    opacity: 0.5,
                    fontSize: "13px",
                  }}
                >
                  Loading file history...
                </div>
              ) : files.length === 0 ? (
                <div
                  style={{
                    padding: "40px 20px",
                    textAlign: "center",
                    border: "1px dashed rgba(255,255,255,0.1)",
                    borderRadius: "10px",
                    opacity: 0.5,
                  }}
                >
                  <File size={28} />

                  <div
                    style={{
                      marginTop: "9px",
                      fontSize: "13px",
                    }}
                  >
                    No files yet
                  </div>

                  <div
                    style={{
                      marginTop: "4px",
                      fontSize: "11px",
                    }}
                  >
                    Sent and received files will appear here.
                  </div>
                </div>
              ) : (
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    gap: "8px",
                  }}
                >
                  {files.map((file) => {
                    const isSent =
                      file.direction === "sent" ||
                      (file.owner === username && file.sender === username);

                    const otherUser = isSent ? file.recipient : file.sender;

                    return (
                      <div
                        key={file.id}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "12px",
                          padding: "12px 14px",
                          borderRadius: "10px",
                          border: "1px solid rgba(255,255,255,0.07)",
                          background: "rgba(255,255,255,0.025)",
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
                            background: "rgba(255,255,255,0.06)",
                            flexShrink: 0,
                          }}
                        >
                          {getFileIcon(file.fileName)}
                        </div>

                        <div
                          style={{
                            minWidth: 0,
                            flex: 1,
                          }}
                        >
                          <div
                            style={{
                              fontSize: "13px",
                              fontWeight: 600,
                              overflow: "hidden",
                              textOverflow: "ellipsis",
                              whiteSpace: "nowrap",
                            }}
                          >
                            {file.fileName}
                          </div>

                          <div
                            style={{
                              fontSize: "10px",
                              opacity: 0.45,
                              marginTop: "3px",
                            }}
                          >
                            {isSent
                              ? `Sent to ${otherUser || "unknown user"}`
                              : `Received from ${otherUser || "unknown user"}`}
                            {" • "}
                            {formatFileSize(file.fileSize)}
                            {" • "}
                            {formatDate(file.receivedAt)}
                          </div>
                        </div>

                        <div
                          style={{
                            fontSize: "10px",
                            padding: "4px 7px",
                            borderRadius: "999px",
                            background: isSent
                              ? "rgba(255,255,255,0.06)"
                              : "rgba(74,222,128,0.10)",
                            opacity: 0.8,
                            flexShrink: 0,
                          }}
                        >
                          {isSent ? "Sent" : "Received"}
                        </div>

                        <button
                          type="button"
                          onClick={() => handleDownloadFile(file)}
                          title="Download file"
                          style={{
                            width: "34px",
                            height: "34px",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            border: "none",
                            borderRadius: "8px",
                            background: "rgba(255,255,255,0.06)",
                            color: "inherit",
                            cursor: "pointer",
                            flexShrink: 0,
                          }}
                        >
                          <Download size={16} />
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </section>
        </section>
      </main>
    </div>
  );
}

export default FileSharing;
