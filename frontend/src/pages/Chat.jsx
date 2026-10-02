import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
  ArrowLeft,
  File,
  LogOut,
  MessageCircle,
  MoreVertical,
  Paperclip,
  Search,
  Send,
  Server,
  UserRound,
  Users,
  X,
} from "lucide-react";

import {
  getOnlineUsers,
  getPrivateMessageHistory,
  searchUsers,
} from "../services/api";

import {
  disconnectChat,
  isChatConnected,
  onChatClosed,
  onChatMessage,
  sendChatCommand,
  sendPrivateFile,
} from "../services/chatSocket";

import { saveReceivedFile } from "../services/receivedFileStore";

function Chat() {
  const navigate = useNavigate();

  const messageEndRef = useRef(null);
  const inputRef = useRef(null);
  const fileInputRef = useRef(null);

  // =========================================================
  // CURRENT USER
  // =========================================================

  const username = sessionStorage.getItem("cloudchat_username") || "";

  // =========================================================
  // STATE
  // =========================================================

  const [onlineUsers, setOnlineUsers] = useState([]);

  const [searchResults, setSearchResults] = useState([]);

  const [selectedUsername, setSelectedUsername] = useState(null);

  const [messages, setMessages] = useState({});

  const [messageInput, setMessageInput] = useState("");

  const [search, setSearch] = useState("");

  const [loadingUsers, setLoadingUsers] = useState(true);

  const [searchingUsers, setSearchingUsers] = useState(false);

  const [connected, setConnected] = useState(false);

  const [error, setError] = useState("");

  const [unreadCounts, setUnreadCounts] = useState({});

  const [loadingHistory, setLoadingHistory] = useState(false);

  // =========================================================
  // FILE UPLOAD STATE
  // =========================================================

  const [selectedFile, setSelectedFile] = useState(null);

  const [uploadingFile, setUploadingFile] = useState(false);

  const [uploadProgress, setUploadProgress] = useState(0);

  // =========================================================
  // SELECTED USER REF
  // =========================================================

  const selectedUsernameRef = useRef(null);

  useEffect(() => {
    selectedUsernameRef.current = selectedUsername;
  }, [selectedUsername]);

  // =========================================================
  // AUTHENTICATION CHECK
  // =========================================================

  useEffect(() => {
    if (!username) {
      navigate("/login", {
        replace: true,
      });
    }
  }, [username, navigate]);

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
          setOnlineUsers(result.users || []);
        }
      } catch (err) {
        if (cancelled) {
          return;
        }

        console.error("[CHAT] Unable to load online users:", err);

        setError(err.message || "Unable to load online users.");
      } finally {
        if (!cancelled) {
          setLoadingUsers(false);
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
      if (cancelled) {
        return;
      }

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
        if (cancelled) {
          return;
        }

        console.error("[CHAT SEARCH] Failed:", err);

        setSearchResults([]);
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

  // =========================================================
  // ONLINE USERNAME SET
  // =========================================================

  const onlineUsernameSet = useMemo(() => {
    return new Set(onlineUsers.map((user) => user.username));
  }, [onlineUsers]);

  // =========================================================
  // SELECTED USER
  // =========================================================

  const selectedUser = useMemo(() => {
    if (!selectedUsername) {
      return null;
    }

    const searchUser = searchResults.find(
      (user) => user.username === selectedUsername,
    );

    if (searchUser) {
      return {
        ...searchUser,
        online: onlineUsernameSet.has(searchUser.username),
      };
    }

    const onlineUser = onlineUsers.find(
      (user) => user.username === selectedUsername,
    );

    if (onlineUser) {
      return {
        ...onlineUser,
        online: true,
      };
    }

    return {
      username: selectedUsername,
      server: "CloudChat",
      online: false,
    };
  }, [selectedUsername, searchResults, onlineUsers, onlineUsernameSet]);

  // =========================================================
  // ADD INCOMING MESSAGE
  // =========================================================

  const addIncomingMessage = useCallback((sender, text, messageTime = null) => {
    if (!sender || !text) {
      return;
    }

    const isCurrentConversation = selectedUsernameRef.current === sender;

    setMessages((previous) => {
      const conversation = previous[sender] || [];

      const duplicate = conversation.some(
        (message) =>
          message.sender === sender &&
          message.text === text &&
          !message.history &&
          Date.now() - new Date(message.time).getTime() < 3000,
      );

      if (duplicate) {
        return previous;
      }

      const parsedTime = messageTime ? new Date(messageTime) : new Date();

      const newMessage = {
        id: `${Date.now()}-` + `${Math.random()}`,

        sender,

        text,

        incoming: true,

        own: false,

        time: Number.isNaN(parsedTime.getTime()) ? new Date() : parsedTime,

        history: false,
      };

      return {
        ...previous,

        [sender]: [...conversation, newMessage],
      };
    });

    if (!isCurrentConversation) {
      setUnreadCounts((previous) => ({
        ...previous,

        [sender]: (previous[sender] || 0) + 1,
      }));
    }
  }, []);

  // =========================================================
  // LOAD PRIVATE MESSAGE HISTORY
  // =========================================================

  const loadMessageHistory = useCallback(
    async (otherUsername) => {
      if (!username || !otherUsername) {
        return;
      }

      setLoadingHistory(true);
      setError("");

      try {
        console.log("[CHAT HISTORY] Loading:", username, "<->", otherUsername);

        const result = await getPrivateMessageHistory(username, otherUsername);

        if (!result.success) {
          throw new Error(result.message || "Unable to load message history.");
        }

        const history = (result.messages || []).map((message, index) => ({
          id: `history-${otherUsername}-` + `${index}-${message.timestamp}`,

          sender: message.sender,

          text: message.message,

          own: message.sender === username,

          incoming: message.sender !== username,

          time: new Date(message.timestamp),

          history: true,
        }));

        setMessages((previous) => {
          const existing = previous[otherUsername] || [];

          const liveMessages = existing.filter((message) => !message.history);

          const usedLiveIndexes = new Set();

          const unmatchedHistory = history.filter((historyMessage) => {
            const liveIndex = liveMessages.findIndex((liveMessage, index) => {
              if (usedLiveIndexes.has(index)) {
                return false;
              }

              return (
                liveMessage.sender === historyMessage.sender &&
                liveMessage.text === historyMessage.text
              );
            });

            if (liveIndex === -1) {
              return true;
            }

            usedLiveIndexes.add(liveIndex);

            return false;
          });

          const unmatchedLive = liveMessages.filter(
            (_, index) => !usedLiveIndexes.has(index),
          );

          const merged = [...unmatchedHistory, ...unmatchedLive];

          merged.sort(
            (a, b) => new Date(a.time).getTime() - new Date(b.time).getTime(),
          );

          return {
            ...previous,

            [otherUsername]: merged,
          };
        });

        console.log("[CHAT HISTORY] Loaded:", history.length, "messages");
      } catch (err) {
        console.error("[CHAT HISTORY] Failed:", err);

        setError(err.message || "Unable to load message history.");
      } finally {
        setLoadingHistory(false);
      }
    },
    [username],
  );

  // =========================================================
  // BACKEND MESSAGE PARSER
  // =========================================================

  const handleBackendMessage = useCallback(
    (rawMessage) => {
      if (!rawMessage) {
        return;
      }

      console.log("[CHAT PAGE] Backend message:", rawMessage);

      const message = String(rawMessage).trim();

      // ===================================================
      // SYSTEM / STATUS
      // ===================================================

      if (
        message.startsWith("SYSTEM:") ||
        message.startsWith("SERVER_LOAD:") ||
        message.startsWith("ONLINE_USERS:") ||
        message.startsWith("GROUPS:") ||
        message.startsWith("OFFLINE_MESSAGES_START") ||
        message.startsWith("OFFLINE_MESSAGES_END") ||
        message.startsWith("OFFLINE_GROUP_MESSAGES_START") ||
        message.startsWith("OFFLINE_GROUP_MESSAGES_END") ||
        message.startsWith("OFFLINE_FILES_START") ||
        message.startsWith("OFFLINE_FILES_END") ||
        message.startsWith("OFFLINE_GROUP_FILES_START") ||
        message.startsWith("OFFLINE_GROUP_FILES_END") ||
        message.startsWith("FILE_INCOMING:") || // Added to silence unhandled warning
        message.startsWith("FILE_INFO:") // Added to silence unhandled warning
      ) {
        return;
      }

      // ===================================================
      // OFFLINE PRIVATE MESSAGE
      // ===================================================

      if (message.startsWith("OFFLINE_MESSAGE:")) {
        const content = message.substring("OFFLINE_MESSAGE:".length);

        const firstColon = content.indexOf(":");

        const lastColon = content.lastIndexOf(":");

        if (firstColon === -1 || lastColon === -1 || firstColon === lastColon) {
          console.warn("[CHAT PAGE] Invalid offline message:", message);

          return;
        }

        const sender = content.substring(0, firstColon).trim();

        const timestamp = content.substring(firstColon + 1, lastColon).trim();

        const text = content.substring(lastColon + 1).trim();

        if (!sender || !text) {
          return;
        }

        console.log("[CHAT PAGE] Offline private message:", {
          sender,
          timestamp,
          text,
        });

        addIncomingMessage(sender, text, timestamp);

        return;
      }

      // ===================================================
      // PRIVATE MESSAGE
      // ===================================================

      if (message.startsWith("PRIVATE from ")) {
        const content = message.substring("PRIVATE from ".length);

        const separatorIndex = content.indexOf(":");

        if (separatorIndex === -1) {
          return;
        }

        const sender = content.substring(0, separatorIndex).trim();

        const text = content.substring(separatorIndex + 1).trim();

        if (!sender || !text) {
          return;
        }

        addIncomingMessage(sender, text);

        return;
      }

      // ===================================================
      // SENDER CONFIRMATION
      // ===================================================

      if (message.startsWith("PRIVATE to ")) {
        console.log("[CHAT PAGE] Private message sent:", message);

        return;
      }

      // ===================================================
      // LEGACY PRIVATE MESSAGE
      // ===================================================

      if (message.startsWith("PRIVATE_MESSAGE:")) {
        const content = message.substring("PRIVATE_MESSAGE:".length);

        const separatorIndex = content.indexOf(":");

        if (separatorIndex === -1) {
          return;
        }

        const sender = content.substring(0, separatorIndex).trim();

        const text = content.substring(separatorIndex + 1).trim();

        if (sender && text) {
          addIncomingMessage(sender, text);
        }

        return;
      }

      // ===================================================
      // OFFLINE FILE READY
      // ===================================================

      if (message.startsWith("OFFLINE_FILE_READY:")) {
        const content = message.substring("OFFLINE_FILE_READY:".length);
        const parts = content.split(":");

        // Ensure we have at least: sender, recipient, filename, size, id
        if (parts.length >= 5) {
          const sender = parts[0];
          // parts[1] is the recipient
          const fileSize = parseInt(parts[parts.length - 2], 10) || 0;

          // Rejoin the middle parts in case the actual filename contained colons
          const rawFileName = parts.slice(2, parts.length - 2).join(":");

          // Clean the filename by removing the "cloudchat-id_" prefix
          const cleanFileName = rawFileName
            .replace(/^temp_\d+_/, "")
            .replace(/^cloudchat-[\d-]+[_-]/, "");

          setMessages((previous) => {
            const conversation = previous[sender] || [];

            // Prevent duplicate file renders
            const duplicate = conversation.some(
              (msg) =>
                msg.file &&
                (msg.fileName === cleanFileName ||
                  msg.fileName === rawFileName) &&
                msg.fileSize === fileSize,
            );

            if (duplicate) {
              return previous;
            }

            const newMessage = {
              id: `offline-file-${Date.now()}-${Math.random()}`,
              sender,
              text: `📎 ${cleanFileName}`,
              incoming: true,
              own: false,
              time: new Date(),
              history: false,
              file: true,
              fileName: cleanFileName,
              fileSize: fileSize,
            };

            return {
              ...previous,
              [sender]: [...conversation, newMessage],
            };
          });

          // Handle Unread Notification
          if (selectedUsernameRef.current !== sender) {
            setUnreadCounts((prev) => ({
              ...prev,
              [sender]: (prev[sender] || 0) + 1,
            }));
          }
        }
        return;
      }

      // ===================================================
      // GENERIC MESSAGE
      // ===================================================

      if (message.startsWith("MESSAGE:")) {
        const content = message.substring("MESSAGE:".length);

        const separatorIndex = content.indexOf(":");

        if (separatorIndex === -1) {
          return;
        }

        const sender = content.substring(0, separatorIndex).trim();

        const text = content.substring(separatorIndex + 1).trim();

        if (sender && text) {
          addIncomingMessage(sender, text);
        }

        return;
      }

      console.log("[CHAT PAGE] Unhandled backend message:", message);
    },
    [addIncomingMessage],
  );

  // =========================================================
  // WEBSOCKET MESSAGE HANDLING
  // =========================================================

  // =========================================================
  // WEBSOCKET MESSAGE HANDLING
  // =========================================================

  useEffect(() => {
    const updateConnectionState = () => {
      setConnected(isChatConnected());
    };

    updateConnectionState();

    const unsubscribeMessage = onChatMessage((data) => {
      console.log("[CHAT PAGE] Received:", data);

      if (!data) {
        return;
      }

      // LOGIN SUCCESS
      if (data.type === "login_success") {
        setConnected(true);
        setError("");
        return;
      }

      // SERVER CONNECTED
      if (data.type === "server_connected") {
        setConnected(true);
        setError("");
        return;
      }

      // SERVER DISCONNECTED
      if (data.type === "server_disconnected") {
        setConnected(false);
        return;
      }

      // ERROR
      if (data.type === "error" || data.type === "login_failed") {
        setError(data.message || "Chat connection error.");
        return;
      }

      // =================================================
      // FILE UPLOAD EVENTS (SENDER)
      // =================================================
      if (
        data.type === "file_upload_started" ||
        data.type === "file_upload_complete" ||
        data.type === "file_upload_error"
      ) {
        return;
      }

      // =================================================
      // FILE FULLY RECEIVED
      // =================================================

      if (data.type === "file_received") {
        const isOwnFile = data.own === true;

        // For restored files:
        // sent file     -> conversationUser = recipient
        // received file -> conversationUser = sender
        //
        // For live received files, conversationUser may not exist,
        // so we fall back to sender.
        const conversationUser = data.conversationUser || data.sender;

        const sender = data.sender;
        const recipient = data.recipient;

        const fileId = data.fileId;
        const rawFileName = data.fileName;
        const fileSize = Number(data.fileSize) || 0;
        const downloadUrl = data.downloadUrl;

        if (!conversationUser || !rawFileName || !downloadUrl) {
          console.warn("[CHAT PAGE] Invalid received file event:", data);
          return;
        }

        const cleanFileName = rawFileName
          .replace(/^temp_\d+_/, "")
          .replace(/^cloudchat-\d+(?:-\d+)*[-_]/, "");

        setMessages((previous) => {
          const conversation = previous[conversationUser] || [];

          const existingIndex = conversation.findIndex((msg) => {
            if (fileId && msg.fileId) {
              return msg.fileId === fileId;
            }

            return (
              msg.file &&
              (msg.fileName === cleanFileName ||
                msg.fileName === rawFileName) &&
              Number(msg.fileSize) === fileSize &&
              msg.own === isOwnFile
            );
          });

          if (existingIndex !== -1) {
            const updatedConversation = [...conversation];

            updatedConversation[existingIndex] = {
              ...updatedConversation[existingIndex],

              file: true,
              fileId,

              fileName: cleanFileName,
              fileSize,

              downloadUrl,

              fileReady: true,

              sender,
              recipient,

              conversationUser,

              own: isOwnFile,
              incoming: !isOwnFile,
            };

            return {
              ...previous,
              [conversationUser]: updatedConversation,
            };
          }

          const newMessage = {
            id: fileId || `file-${Date.now()}-${Math.random()}`,

            sender: sender || (isOwnFile ? username : conversationUser),

            recipient,

            conversationUser,

            text: `📎 ${cleanFileName}`,

            incoming: !isOwnFile,
            own: isOwnFile,

            time: data.receivedAt ? new Date(data.receivedAt) : new Date(),

            history: false,

            file: true,
            fileId,

            fileName: cleanFileName,
            fileSize,

            downloadUrl,

            fileReady: true,

            persisted: data.persisted === true,
          };

          return {
            ...previous,

            [conversationUser]: [...conversation, newMessage],
          };
        });

        // Only received files should increase unread count.
        // A file sent by the current user must not create an unread message.
        if (!isOwnFile && selectedUsernameRef.current !== conversationUser) {
          setUnreadCounts((previous) => ({
            ...previous,
            [conversationUser]: (previous[conversationUser] || 0) + 1,
          }));
        }

        return;
      }
      // =================================================
      // INCOMING FILE DELIVERY EVENTS (RECIPIENT)
      // =================================================

      if (data.type === "file_delivery_started") {
        return; // Optional: Could be used to show a loading state in the future
      }

      // =================================================
      // FILE FULLY RECEIVED
      // =================================================

      if (data.type === "file_received") {
        const sender = data.sender;
        const rawFileName = data.fileName;
        const fileSize = Number(data.fileSize) || 0;
        const downloadUrl = data.downloadUrl;

        if (!sender || !rawFileName || !downloadUrl) {
          console.warn("[CHAT PAGE] Invalid received file event:", data);

          return;
        }

        const cleanFileName = rawFileName
          .replace(/^temp_\d+_/, "")
          .replace(/^cloudchat-\d+(?:-\d+)*[-_]/, "");

        console.log("[CHAT PAGE] File ready:", {
          sender,
          fileName: cleanFileName,
          fileSize,
        });

        setMessages((previous) => {
          const conversation = previous[sender] || [];

          // ---------------------------------------------------
          // Look for an existing placeholder created by
          // file_delivery_complete.
          // ---------------------------------------------------

          const existingIndex = conversation.findIndex(
            (msg) =>
              msg.file &&
              (msg.fileName === cleanFileName ||
                msg.fileName === rawFileName) &&
              Number(msg.fileSize) === fileSize,
          );

          if (existingIndex !== -1) {
            const updatedConversation = [...conversation];

            updatedConversation[existingIndex] = {
              ...updatedConversation[existingIndex],

              file: true,

              fileName: cleanFileName,

              fileSize,

              downloadUrl,

              fileReady: true,
            };

            return {
              ...previous,
              [sender]: updatedConversation,
            };
          }

          // ---------------------------------------------------
          // No placeholder exists.
          // Create a new file message.
          // ---------------------------------------------------

          const newMessage = {
            id: `${Date.now()}-${Math.random()}`,

            sender,

            text: `📎 ${cleanFileName}`,

            incoming: true,

            own: false,

            time: new Date(),

            history: false,

            file: true,

            fileName: cleanFileName,

            fileSize: fileSize,

            downloadUrl: null,

            fileReady: false,
          };

          return {
            ...previous,

            [sender]: [...conversation, newMessage],
          };
        });

        // -----------------------------------------------------
        // Unread notification
        // -----------------------------------------------------

        if (selectedUsernameRef.current !== sender) {
          setUnreadCounts((previous) => ({
            ...previous,

            [sender]: (previous[sender] || 0) + 1,
          }));
        }

        return;
      }

      if (data.type === "file_delivery_complete") {
        console.log("[CHAT PAGE] File delivery completed:", data);

        return;
      }

      // STRUCTURED PRIVATE MESSAGE
      if (data.type === "private_message") {
        const sender = data.sender || data.from;
        const text = data.message || "";

        if (sender && text) {
          addIncomingMessage(sender, text, data.timestamp || null);
        }
        return;
      }

      // RAW BACKEND MESSAGE
      if (data.type === "message") {
        handleBackendMessage(data.message);
        return;
      }

      console.log("[CHAT PAGE] Unhandled WebSocket data:", data);
    });

    const unsubscribeClose = onChatClosed(() => {
      setConnected(false);
    });

    return () => {
      unsubscribeMessage();
      unsubscribeClose();
    };
  }, [addIncomingMessage, handleBackendMessage]);

  // =========================================================
  // CURRENT CONVERSATION
  // =========================================================

  const currentMessages = selectedUsername
    ? messages[selectedUsername] || []
    : [];

  // =========================================================
  // AUTO SCROLL
  // =========================================================

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({
      behavior: "smooth",
    });
  }, [currentMessages.length, selectedUser, loadingHistory]);

  // =========================================================
  // SEND PRIVATE MESSAGE
  // =========================================================

  const handleSendMessage = () => {
    console.log("[CHAT TEST] React connected:", connected);

    console.log("[CHAT TEST] Socket connected:", isChatConnected());

    const text = messageInput.trim();

    if (!text) {
      return;
    }

    if (!selectedUser) {
      setError("Please select a user.");

      return;
    }

    if (!isChatConnected()) {
      setError("Chat connection is not active.");

      return;
    }

    const recipient = selectedUser.username;

    const command = `/msg ${recipient} ${text}`;

    console.log("[CHAT TEST] Sending:", command);

    const sent = sendChatCommand(command);

    console.log("[CHAT TEST] Sent:", sent);

    if (!sent) {
      setError("Unable to send message.");

      return;
    }

    setMessages((previous) => {
      const conversation = previous[recipient] || [];

      return {
        ...previous,

        [recipient]: [
          ...conversation,

          {
            id: `${Date.now()}-` + `${Math.random()}`,

            sender: username,

            text,

            own: true,

            incoming: false,

            time: new Date(),

            history: false,
          },
        ],
      };
    });

    setMessageInput("");

    setError("");

    setTimeout(() => {
      inputRef.current?.focus();
    }, 0);
  };

  // =========================================================
  // SELECT FILE
  // =========================================================

  const handleFileSelect = async (event) => {
    const file = event.target.files?.[0];

    // Reset input so the same file can
    // be selected again later.
    event.target.value = "";

    if (!file) {
      return;
    }

    if (!selectedUser) {
      setError("Please select a user before attaching a file.");

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
    // Maximum size
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

    const recipient = selectedUser.username;

    // -----------------------------------------------------
    // Show selected file
    // -----------------------------------------------------

    setSelectedFile(file);
    setUploadingFile(true);
    setUploadProgress(0);
    setError("");

    console.log("[CHAT FILE] Sending file:", {
      recipient,
      fileName: file.name,
      fileSize: file.size,
      online: selectedUser.online,
    });

    try {
      // IMPORTANT:
      // We intentionally DO NOT check selectedUser.online.
      //
      // The Java backend decides:
      //
      // online  -> immediate delivery
      // offline -> OfflineFileService queue
      //

      await sendPrivateFile(
        recipient,
        file,
        (progress, sentBytes, totalBytes) => {
          console.log(
            "[CHAT FILE] Progress:",
            progress,
            "%",
            sentBytes,
            "/",
            totalBytes,
          );

          setUploadProgress(progress);
        },
      );

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
        "[CHAT FILE] Sent file permanently stored:",
        storedFile.fileName,
        storedFile.id,
      );

      console.log("[CHAT FILE] File sent successfully:", file.name);

      // ---------------------------------------------------
      // Add file to current conversation
      // ---------------------------------------------------

      setMessages((previous) => {
        const conversation = previous[recipient] || [];

        return {
          ...previous,

          [recipient]: [
            ...conversation,

            {
              id: `${Date.now()}-` + `${Math.random()}`,

              sender: username,

              text: `📎 ${file.name}`,

              own: true,

              incoming: false,

              time: new Date(),

              history: false,

              file: true,

              fileName: file.name,

              fileSize: file.size,
            },
          ],
        };
      });

      setError("");
    } catch (err) {
      console.error("[CHAT FILE] Upload failed:", err);

      setError(err.message || "Unable to send file.");
    } finally {
      setUploadingFile(false);

      setUploadProgress(0);

      setSelectedFile(null);
    }
  };

  // =========================================================
  // OPEN FILE PICKER
  // =========================================================

  const handleAttachFile = () => {
    if (!selectedUser) {
      setError("Please select a user first.");

      return;
    }

    if (!isChatConnected()) {
      setError("Chat connection is not active.");

      return;
    }

    if (uploadingFile) {
      return;
    }

    fileInputRef.current?.click();
  };

  // =========================================================
  // REMOVE SELECTED FILE
  //
  // Normally upload begins immediately after selection,
  // but this function is kept for future UI use.
  // =========================================================

  const handleClearFile = () => {
    if (uploadingFile) {
      return;
    }

    setSelectedFile(null);
    setUploadProgress(0);

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  // =========================================================
  // ENTER TO SEND
  // =========================================================

  const handleInputKeyDown = (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();

      handleSendMessage();
    }
  };

  // =========================================================
  // SELECT USER
  // =========================================================

  const handleSelectUser = useCallback(
    (otherUsername) => {
      if (!otherUsername) {
        return;
      }

      console.log("[CHAT] Selecting:", otherUsername);

      // Don't allow changing recipient
      // during a file upload.
      if (uploadingFile) {
        setError("Please wait for the file upload to finish.");

        return;
      }

      setSelectedUsername(otherUsername);

      setUnreadCounts((previous) => {
        if (!previous[otherUsername]) {
          return previous;
        }

        const updated = {
          ...previous,
        };

        delete updated[otherUsername];

        return updated;
      });

      loadMessageHistory(otherUsername);
    },
    [loadMessageHistory, uploadingFile],
  );

  // =========================================================
  // DISPLAY USERS
  // =========================================================

  const displayedUsers = useMemo(() => {
    const query = search.trim();

    if (!query) {
      return onlineUsers.filter((user) => user.username !== username);
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
  }, [search, searchResults, onlineUsers, onlineUsernameSet, username]);

  // =========================================================
  // FORMAT MESSAGE TIME
  // =========================================================

  const formatTime = (date) => {
    if (!date) {
      return "";
    }

    const parsedDate = date instanceof Date ? date : new Date(date);

    if (Number.isNaN(parsedDate.getTime())) {
      return "";
    }

    return new Intl.DateTimeFormat("en-IN", {
      hour: "2-digit",
      minute: "2-digit",
    }).format(parsedDate);
  };

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
  // DOWNLOAD RECEIVED FILE
  // =========================================================

  const handleDownloadFile = useCallback((message) => {
    if (!message?.downloadUrl) {
      setError("This file is not ready for download yet.");

      return;
    }

    try {
      const link = document.createElement("a");

      link.href = message.downloadUrl;

      link.download = message.fileName || "download";

      document.body.appendChild(link);

      link.click();

      document.body.removeChild(link);
    } catch (error) {
      console.error("[CHAT FILE] Download failed:", error);

      setError("Unable to download the file.");
    }
  }, []);
  // =========================================================
  // LOGOUT
  // =========================================================

  const handleLogout = () => {
    disconnectChat();

    sessionStorage.removeItem("cloudchat_username");

    navigate("/login", {
      replace: true,
    });
  };

  // =========================================================
  // BACK TO DASHBOARD
  // =========================================================

  const handleBack = () => {
    navigate("/dashboard");
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
          maxWidth: "1450px",
          height: "calc(100vh - 40px)",
          minHeight: "0",
          margin: "0 auto",
          display: "flex",
          flexDirection: "column",
          position: "relative",
          zIndex: 1,
        }}
      >
        {/* ===================================================
            TOP BAR
        ==================================================== */}

        <header
          className="auth-card"
          style={{
            minHeight: "64px",
            padding: "12px 18px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "15px",
            marginBottom: "12px",
            flexShrink: 0,
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
              onClick={handleBack}
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
                <MessageCircle size={20} />

                <strong
                  style={{
                    fontSize: "18px",
                  }}
                >
                  CloudChat
                </strong>
              </div>

              <div
                style={{
                  fontSize: "11px",
                  opacity: 0.55,
                  marginTop: "2px",
                }}
              >
                Distributed messaging workspace
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
            {/* CONNECTION */}

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

            {/* CURRENT USER */}

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

            {/* LOGOUT */}

            <button
              type="button"
              onClick={handleLogout}
              className="admin-link"
              style={{
                display: "flex",
                alignItems: "center",
                gap: "6px",
                background: "transparent",
                border: "none",
                color: "inherit",
                cursor: "pointer",
              }}
            >
              <LogOut size={15} />
              Logout
            </button>
          </div>
        </header>

        {/* ===================================================
            ERROR
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

        {/* ===================================================
            CHAT WORKSPACE
        ==================================================== */}

        <section
          className="auth-card"
          style={{
            flex: 1,
            minHeight: 0,
            padding: 0,
            overflow: "hidden",
            display: "grid",
            gridTemplateColumns: "280px minmax(0, 1fr) 230px",
            gridTemplateRows: "minmax(0, 1fr)",
          }}
        >
          {/* =================================================
              LEFT SIDEBAR
          ================================================== */}

          <aside
            style={{
              borderRight: "1px solid rgba(255,255,255,0.08)",
              display: "flex",
              flexDirection: "column",
              minWidth: 0,
            }}
          >
            {/* SEARCH */}

            <div
              style={{
                padding: "16px",
                borderBottom: "1px solid rgba(255,255,255,0.08)",
              }}
            >
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

            {/* DIRECT MESSAGES */}

            <div
              style={{
                padding: "16px 12px 8px",
                fontSize: "11px",
                fontWeight: 700,
                letterSpacing: "1.2px",
                opacity: 0.45,
              }}
            >
              DIRECT MESSAGES
            </div>

            {/* USERS */}

            <div
              style={{
                overflowY: "auto",
                flex: 1,
                padding: "0 8px 12px",
              }}
            >
              {/* SEARCHING */}

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

              {/* INITIAL LOADING */}

              {!search.trim() && loadingUsers && (
                <div
                  style={{
                    padding: "20px 12px",
                    fontSize: "13px",
                    opacity: 0.55,
                  }}
                >
                  Loading users...
                </div>
              )}

              {/* NO SEARCH RESULTS */}

              {search.trim() &&
                !searchingUsers &&
                searchResults.length === 0 && (
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

              {/* NO ONLINE USERS */}

              {!search.trim() &&
                !loadingUsers &&
                displayedUsers.length === 0 && (
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

              {/* USERS */}

              {displayedUsers.map((user) => {
                const active = selectedUsername === user.username;

                const unreadCount = unreadCounts[user.username] || 0;

                const isOnline = search.trim()
                  ? onlineUsernameSet.has(user.username)
                  : true;

                return (
                  <button
                    key={user.username}
                    type="button"
                    onClick={() => handleSelectUser(user.username)}
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
                      cursor: "pointer",
                    }}
                  >
                    {/* AVATAR */}

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

                    {/* USERNAME */}

                    <div
                      style={{
                        minWidth: 0,
                        flex: 1,
                      }}
                    >
                      <div
                        style={{
                          fontSize: "14px",
                          fontWeight: unreadCount > 0 ? 700 : 600,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {user.username}
                      </div>

                      {search.trim() && (
                        <div
                          style={{
                            fontSize: "10px",
                            opacity: 0.45,
                            marginTop: "2px",
                          }}
                        >
                          {isOnline ? "Online" : "Offline"}
                        </div>
                      )}
                    </div>

                    {/* UNREAD COUNT */}

                    {unreadCount > 0 && (
                      <span
                        style={{
                          minWidth: "20px",
                          height: "20px",
                          padding: "0 6px",
                          borderRadius: "999px",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          background: "#ef4444",
                          color: "#fff",
                          fontSize: "11px",
                          fontWeight: 700,
                          flexShrink: 0,
                        }}
                      >
                        {unreadCount}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>

            {/* GROUPS */}

            <div
              style={{
                borderTop: "1px solid rgba(255,255,255,0.08)",
                padding: "14px 12px",
              }}
            >
              <button
                type="button"
                onClick={() => navigate("/groups")}
                style={{
                  width: "100%",
                  display: "flex",
                  alignItems: "center",
                  gap: "10px",
                  padding: "10px",
                  border: "none",
                  borderRadius: "8px",
                  background: "rgba(255,255,255,0.035)",
                  color: "inherit",
                  cursor: "pointer",
                  textAlign: "left",
                }}
              >
                <Users size={17} />

                <span
                  style={{
                    fontSize: "13px",
                    fontWeight: 600,
                  }}
                >
                  Groups
                </span>
              </button>
            </div>
          </aside>

          {/* =================================================
              MAIN CHAT
          ================================================== */}

          <section
            style={{
              minWidth: 0,
              minHeight: 0,
              height: "100%",
              display: "flex",
              flexDirection: "column",
              overflow: "hidden",
              background: "rgba(255,255,255,0.008)",
            }}
          >
            {!selectedUser ? (
              <div
                style={{
                  flex: 1,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  textAlign: "center",
                  padding: "30px",
                }}
              >
                <div
                  style={{
                    width: "70px",
                    height: "70px",
                    borderRadius: "20px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    background: "rgba(255,255,255,0.06)",
                    marginBottom: "18px",
                  }}
                >
                  <MessageCircle size={30} />
                </div>

                <h2
                  style={{
                    margin: "0 0 8px",
                    fontSize: "22px",
                  }}
                >
                  Welcome to CloudChat
                </h2>

                <p
                  style={{
                    margin: 0,
                    maxWidth: "400px",
                    opacity: 0.55,
                    fontSize: "14px",
                    lineHeight: 1.6,
                  }}
                >
                  Search for any registered user to start a private
                  conversation.
                </p>
              </div>
            ) : (
              <>
                {/* =================================================
                    CONVERSATION HEADER
                ================================================== */}

                <div
                  style={{
                    minHeight: "68px",
                    padding: "12px 18px",
                    borderBottom: "1px solid rgba(255,255,255,0.08)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "11px",
                    }}
                  >
                    <div
                      style={{
                        width: "40px",
                        height: "40px",
                        borderRadius: "50%",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        background: "rgba(255,255,255,0.08)",
                        position: "relative",
                      }}
                    >
                      <UserRound size={19} />

                      <span
                        style={{
                          position: "absolute",
                          right: "-1px",
                          bottom: "-1px",
                          width: "10px",
                          height: "10px",
                          borderRadius: "50%",
                          background:
                            selectedUser.online === false
                              ? "#71717a"
                              : "#4ade80",
                          border: "2px solid #111",
                        }}
                      />
                    </div>

                    <div>
                      <div
                        style={{
                          fontWeight: 700,
                          fontSize: "15px",
                        }}
                      >
                        {selectedUser.username}
                      </div>

                      <div
                        style={{
                          fontSize: "11px",
                          opacity: 0.5,
                          marginTop: "2px",
                        }}
                      >
                        {selectedUser.online === false
                          ? "Offline"
                          : `Online • ${selectedUser.server || "CloudChat"}`}
                      </div>
                    </div>
                  </div>

                  <button
                    type="button"
                    title="More options"
                    style={{
                      width: "38px",
                      height: "38px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      border: "none",
                      borderRadius: "8px",
                      background: "transparent",
                      color: "inherit",
                      cursor: "pointer",
                    }}
                  >
                    <MoreVertical size={19} />
                  </button>
                </div>

                {/* =================================================
                    MESSAGES
                ================================================== */}

                <div
                  style={{
                    flex: 1,
                    minHeight: 0,
                    height: 0,
                    overflowY: "auto",
                    overflowX: "hidden",
                    padding: "24px",
                    display: "flex",
                    flexDirection: "column",
                    gap: "12px",
                    boxSizing: "border-box",
                    position: "relative",
                  }}
                >
                  {loadingHistory && (
                    <div
                      style={{
                        position: "absolute",
                        top: "10px",
                        left: "50%",
                        transform: "translateX(-50%)",
                        zIndex: 2,
                        padding: "5px 10px",
                        borderRadius: "999px",
                        background: "rgba(20,20,20,0.85)",
                        border: "1px solid rgba(255,255,255,0.08)",
                        fontSize: "10px",
                        opacity: 0.65,
                        pointerEvents: "none",
                      }}
                    >
                      Loading history...
                    </div>
                  )}

                  {currentMessages.length === 0 && !loadingHistory && (
                    <div
                      style={{
                        flex: 1,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        textAlign: "center",
                        opacity: 0.45,
                      }}
                    >
                      <div>
                        <MessageCircle
                          size={26}
                          style={{
                            marginBottom: "8px",
                          }}
                        />

                        <div
                          style={{
                            fontSize: "13px",
                          }}
                        >
                          No messages yet.
                        </div>

                        <div
                          style={{
                            fontSize: "12px",
                            marginTop: "4px",
                          }}
                        >
                          Start a conversation with {selectedUser.username}.
                        </div>
                      </div>
                    </div>
                  )}

                  {currentMessages.map((message) => (
                    <div
                      key={message.id}
                      style={{
                        display: "flex",
                        justifyContent: message.own ? "flex-end" : "flex-start",
                      }}
                    >
                      <div
                        style={{
                          maxWidth: "70%",
                          padding: "10px 13px",
                          borderRadius: message.own
                            ? "14px 14px 3px 14px"
                            : "14px 14px 14px 3px",
                          background: message.own
                            ? "rgba(255,255,255,0.12)"
                            : "rgba(255,255,255,0.055)",
                          border: "1px solid rgba(255,255,255,0.07)",
                        }}
                      >
                        <div
                          style={{
                            fontSize: "14px",
                            lineHeight: 1.5,
                            wordBreak: "break-word",
                          }}
                        >
                          {message.file ? (
                            <button
                              type="button"
                              onClick={() => {
                                if (message.downloadUrl) {
                                  handleDownloadFile(message);
                                }
                              }}
                              disabled={!message.downloadUrl}
                              title={
                                message.downloadUrl
                                  ? "Click to download file"
                                  : "File is being received..."
                              }
                              style={{
                                display: "flex",
                                alignItems: "center",
                                gap: "9px",
                                width: "100%",
                                padding: "0",
                                border: "none",
                                background: "transparent",
                                color: "inherit",
                                textAlign: "left",
                                cursor: message.downloadUrl
                                  ? "pointer"
                                  : "default",
                                opacity: message.downloadUrl ? 1 : 0.55,
                              }}
                            >
                              <File size={18} />

                              <div
                                style={{
                                  minWidth: 0,
                                  flex: 1,
                                }}
                              >
                                <div
                                  style={{
                                    fontWeight: 600,
                                    wordBreak: "break-word",
                                    textDecoration: message.downloadUrl
                                      ? "underline"
                                      : "none",
                                    textUnderlineOffset: "3px",
                                  }}
                                >
                                  {message.fileName}
                                </div>

                                <div
                                  style={{
                                    fontSize: "10px",
                                    opacity: 0.5,
                                    marginTop: "2px",
                                  }}
                                >
                                  {formatFileSize(message.fileSize)}
                                </div>

                                <div
                                  style={{
                                    fontSize: "10px",
                                    opacity: 0.45,
                                    marginTop: "3px",
                                  }}
                                >
                                  {message.downloadUrl
                                    ? "Click to download"
                                    : "Receiving file..."}
                                </div>
                              </div>
                            </button>
                          ) : (
                            message.text
                          )}
                        </div>

                        <div
                          style={{
                            fontSize: "10px",
                            opacity: 0.45,
                            marginTop: "5px",
                            textAlign: message.own ? "right" : "left",
                          }}
                        >
                          {formatTime(message.time)}
                        </div>
                      </div>
                    </div>
                  ))}

                  <div ref={messageEndRef} />
                </div>

                {/* =================================================
                    MESSAGE COMPOSER
                ================================================== */}

                <div
                  style={{
                    padding: "12px 16px 16px",
                    borderTop: "1px solid rgba(255,255,255,0.08)",
                  }}
                >
                  {/* HIDDEN FILE INPUT */}

                  <input
                    ref={fileInputRef}
                    type="file"
                    onChange={handleFileSelect}
                    style={{
                      display: "none",
                    }}
                  />

                  {/* FILE UPLOAD STATUS */}

                  {uploadingFile && selectedFile && (
                    <div
                      style={{
                        marginBottom: "8px",
                        padding: "10px 12px",
                        borderRadius: "9px",
                        border: "1px solid rgba(255,255,255,0.08)",
                        background: "rgba(255,255,255,0.035)",
                      }}
                    >
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "9px",
                        }}
                      >
                        <File size={17} />

                        <div
                          style={{
                            minWidth: 0,
                            flex: 1,
                          }}
                        >
                          <div
                            style={{
                              fontSize: "12px",
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
                            Uploading...
                          </div>
                        </div>

                        <span
                          style={{
                            fontSize: "12px",
                            fontWeight: 600,
                          }}
                        >
                          {uploadProgress}%
                        </span>
                      </div>

                      {/* PROGRESS BAR */}

                      <div
                        style={{
                          height: "4px",
                          marginTop: "9px",
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
                    </div>
                  )}

                  {/* SELECTED FILE */}

                  {selectedFile && !uploadingFile && (
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "8px",
                        marginBottom: "8px",
                        padding: "8px 10px",
                        borderRadius: "8px",
                        background: "rgba(255,255,255,0.04)",
                        border: "1px solid rgba(255,255,255,0.08)",
                      }}
                    >
                      <File size={16} />

                      <span
                        style={{
                          flex: 1,
                          minWidth: 0,
                          fontSize: "12px",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {selectedFile.name}
                      </span>

                      <button
                        type="button"
                        onClick={handleClearFile}
                        title="Remove file"
                        style={{
                          width: "28px",
                          height: "28px",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          border: "none",
                          background: "transparent",
                          color: "inherit",
                          opacity: 0.55,
                          cursor: "pointer",
                        }}
                      >
                        <X size={15} />
                      </button>
                    </div>
                  )}

                  <div
                    style={{
                      display: "flex",
                      alignItems: "flex-end",
                      gap: "8px",
                      padding: "8px",
                      borderRadius: "12px",
                      border: "1px solid rgba(255,255,255,0.1)",
                      background: "rgba(255,255,255,0.025)",
                    }}
                  >
                    {/* ATTACHMENT */}

                    <button
                      type="button"
                      title={
                        uploadingFile ? "Uploading file..." : "Attach file"
                      }
                      onClick={handleAttachFile}
                      disabled={uploadingFile}
                      style={{
                        width: "40px",
                        height: "40px",
                        flexShrink: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        border: "none",
                        borderRadius: "9px",
                        background: "transparent",
                        color: "inherit",
                        opacity: uploadingFile ? 0.25 : 0.55,
                        cursor: uploadingFile ? "not-allowed" : "pointer",
                      }}
                    >
                      <Paperclip size={18} />
                    </button>

                    {/* INPUT */}

                    <textarea
                      ref={inputRef}
                      value={messageInput}
                      onChange={(event) => setMessageInput(event.target.value)}
                      onKeyDown={handleInputKeyDown}
                      placeholder={`Message ${selectedUser.username}...`}
                      rows={1}
                      disabled={uploadingFile}
                      style={{
                        flex: 1,
                        minWidth: 0,
                        resize: "none",
                        border: "none",
                        outline: "none",
                        background: "transparent",
                        color: "inherit",
                        padding: "10px 4px",
                        fontFamily: "inherit",
                        fontSize: "14px",
                        lineHeight: 1.4,
                        opacity: uploadingFile ? 0.5 : 1,
                      }}
                    />

                    {/* SEND */}

                    <button
                      type="button"
                      onClick={handleSendMessage}
                      disabled={!messageInput.trim() || uploadingFile}
                      title="Send message"
                      style={{
                        width: "42px",
                        height: "42px",
                        flexShrink: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        border: "none",
                        borderRadius: "10px",
                        background: messageInput.trim()
                          ? "rgba(255,255,255,0.14)"
                          : "rgba(255,255,255,0.04)",
                        color: "inherit",
                        cursor:
                          messageInput.trim() && connected && !uploadingFile
                            ? "pointer"
                            : "not-allowed",
                        opacity:
                          messageInput.trim() && connected && !uploadingFile
                            ? 1
                            : 0.4,
                      }}
                    >
                      <Send size={18} />
                    </button>
                  </div>

                  <div
                    style={{
                      fontSize: "10px",
                      opacity: 0.4,
                      marginTop: "7px",
                      textAlign: "center",
                    }}
                  >
                    Press Enter to send • Shift + Enter for a new line • 📎
                    Attach files up to 100 MB
                  </div>
                </div>
              </>
            )}
          </section>

          {/* =================================================
              RIGHT SIDEBAR
          ================================================== */}

          <aside
            style={{
              borderLeft: "1px solid rgba(255,255,255,0.08)",
              padding: "22px 18px",
              minWidth: 0,
            }}
          >
            {selectedUser ? (
              <>
                {/* PROFILE */}

                <div
                  style={{
                    textAlign: "center",
                    padding: "15px 0 24px",
                    borderBottom: "1px solid rgba(255,255,255,0.08)",
                  }}
                >
                  <div
                    style={{
                      width: "70px",
                      height: "70px",
                      margin: "0 auto 12px",
                      borderRadius: "50%",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      background: "rgba(255,255,255,0.08)",
                    }}
                  >
                    <UserRound size={30} />
                  </div>

                  <h3
                    style={{
                      margin: "0 0 5px",
                      fontSize: "17px",
                    }}
                  >
                    {selectedUser.username}
                  </h3>

                  <div
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "6px",
                      fontSize: "11px",
                      opacity: 0.65,
                    }}
                  >
                    <span
                      style={{
                        width: "7px",
                        height: "7px",
                        borderRadius: "50%",
                        background:
                          selectedUser.online === false ? "#71717a" : "#4ade80",
                      }}
                    />

                    {selectedUser.online === false ? "Offline" : "Online"}
                  </div>
                </div>

                {/* CONNECTION */}

                <div
                  style={{
                    padding: "20px 0",
                  }}
                >
                  <div
                    style={{
                      fontSize: "11px",
                      fontWeight: 700,
                      letterSpacing: "1px",
                      opacity: 0.4,
                      marginBottom: "12px",
                    }}
                  >
                    CONNECTION
                  </div>

                  {/* SERVER */}

                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "9px",
                      fontSize: "13px",
                      marginBottom: "12px",
                    }}
                  >
                    <Server size={16} opacity={0.6} />

                    <span
                      style={{
                        opacity: 0.7,
                      }}
                    >
                      Server
                    </span>

                    <span
                      style={{
                        marginLeft: "auto",
                        fontWeight: 600,
                      }}
                    >
                      {selectedUser.online === false
                        ? "-"
                        : selectedUser.server || "-"}
                    </span>
                  </div>

                  {/* MESSAGES */}

                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "9px",
                      fontSize: "13px",
                    }}
                  >
                    <MessageCircle size={16} opacity={0.6} />

                    <span
                      style={{
                        opacity: 0.7,
                      }}
                    >
                      Messages
                    </span>

                    <span
                      style={{
                        marginLeft: "auto",
                        fontWeight: 600,
                      }}
                    >
                      {currentMessages.length}
                    </span>
                  </div>
                </div>
              </>
            ) : (
              <div
                style={{
                  textAlign: "center",
                  opacity: 0.5,
                  paddingTop: "50px",
                }}
              >
                <UserRound size={30} />

                <p
                  style={{
                    fontSize: "13px",
                    lineHeight: 1.5,
                  }}
                >
                  Select a user to view their profile.
                </p>
              </div>
            )}
          </aside>
        </section>
      </main>
    </div>
  );
}

export default Chat;
