import {
  createWebSocket,
  sendWebSocketMessage,
  sendWebSocketBinary,
  closeWebSocket,
} from "./websocket";

import { saveReceivedFile, getStoredFilesForUser } from "./receivedFileStore";

const WS_URL = "ws://localhost:9001";

let socket = null;

let currentChatUsername = "";

let messageHandlers = [];
let connectionHandlers = [];
let closeHandlers = [];

// =========================================================
// PENDING REAL-TIME MESSAGE QUEUE
// =========================================================

let pendingMessages = [];

const MAX_PENDING_MESSAGES = 100;

// =========================================================
// PENDING RECEIVED FILE QUEUE
// =========================================================

let pendingFiles = [];

const MAX_PENDING_FILES = 100;

// =========================================================
// FILE UPLOAD STATE
// =========================================================

// Only one browser file upload at a time.
let fileUploadInProgress = false;

let activeFileUpload = null;

// =========================================================
// INCOMING FILE STATE
// =========================================================

// Incoming browser file transfer.
//
// The backend sends:
//
// 1. FILE_INCOMING: sender wants to send you filename
// 2. FILE_INFO: 12345 bytes
// 3. file_delivery_started JSON
// 4. Binary WebSocket chunks
// 5. file_delivery_complete JSON
//
// The important metadata for binary reception comes from
// file_delivery_started.

let incomingFile = null;

// Maximum incoming file size.
// Keep this consistent with backend validation.
const MAX_FILE_SIZE = 100 * 1024 * 1024;

// =========================================================
// CONNECT CHAT
// =========================================================

export function connectChat(username, password) {
  currentChatUsername = username || "";
  return new Promise((resolve, reject) => {
    // -------------------------------------------------------
    // Reuse existing connection
    // -------------------------------------------------------

    if (socket && socket.readyState === WebSocket.OPEN) {
      resolve(socket);
      return;
    }

    // -------------------------------------------------------
    // Create WebSocket
    // -------------------------------------------------------
    socket = createWebSocket(WS_URL);

    // -------------------------------------------------------
    // OPEN
    // -------------------------------------------------------

    socket.onopen = () => {
      console.log("[CHAT WS] Connected to bridge");

      socket.send(
        JSON.stringify({
          type: "login",
          username,
          password,
        }),
      );
    };

    // -------------------------------------------------------
    // RECEIVE MESSAGE
    // -------------------------------------------------------

    socket.onmessage = (event) => {
      // =====================================================
      // BINARY MESSAGE FROM SERVER
      // =====================================================

      if (event.data instanceof ArrayBuffer) {
        handleIncomingBinaryFile(event.data);

        return;
      }

      // Some browsers/WebSocket implementations may provide
      // binary data as a Blob even when binaryType is set.
      if (event.data instanceof Blob) {
        handleIncomingBlobFile(event.data);

        return;
      }

      // =====================================================
      // TEXT MESSAGE
      // =====================================================

      const rawData = String(event.data ?? "");

      console.log("[CHAT WS] Backend:", rawData);

      // -----------------------------------------------------
      // FILE INCOMING METADATA
      // -----------------------------------------------------

      if (rawData.startsWith("FILE_INCOMING:")) {
        handleIncomingFileNotification(rawData);

        // Also send this to React so the Chat page can
        // display the normal incoming-file message.
        dispatchMessage({
          type: "file_incoming",
          message: rawData,
          rawMessage: rawData,
        });

        return;
      }

      // -----------------------------------------------------
      // FILE INFORMATION
      // -----------------------------------------------------

      if (rawData.startsWith("FILE_INFO:")) {
        handleIncomingFileInfo(rawData);

        // Also send to React.
        dispatchMessage({
          type: "file_info",
          message: rawData,
          rawMessage: rawData,
        });

        return;
      }

      // -----------------------------------------------------
      // PARSE JSON
      // -----------------------------------------------------

      let data;

      try {
        data = JSON.parse(rawData);
      } catch {
        data = {
          type: "message",
          message: rawData,
        };
      }

      if (!data || typeof data !== "object") {
        return;
      }

      // =====================================================
      // FILE DELIVERY STARTED
      //
      // THIS IS THE IMPORTANT FIX.
      //
      // The WebSocket bridge sends this immediately before
      // sending the binary file chunks.
      //
      // Example:
      //
      // {
      //   "type": "file_delivery_started",
      //   "sender": "Pranav",
      //   "fileName": "example.pdf",
      //   "fileSize": 992252
      // }
      // =====================================================

      if (data.type === "file_delivery_started") {
        handleIncomingFileDeliveryStarted(data);

        return;
      }

      // =====================================================
      // FILE DELIVERY COMPLETE
      // =====================================================

      if (data.type === "file_delivery_complete") {
        handleIncomingFileDeliveryComplete(data);

        return;
      }

      // =====================================================
      // FILE DELIVERY ERROR
      // =====================================================

      if (data.type === "file_delivery_error") {
        console.error("[CHAT FILE] File delivery error:", data.message);

        dispatchMessage({
          type: "file_receive_error",
          sender: data.sender || "Unknown",
          fileName: data.fileName || "received-file",
          message: data.message || "Incoming file delivery failed.",
        });

        resetIncomingFile();

        return;
      }

      // =====================================================
      // FILE UPLOAD EVENTS
      //
      // These are handled BEFORE normal React message
      // handlers so they cannot accidentally become chat
      // messages.
      // =====================================================

      if (data.type === "file_upload_started") {
        console.log("[CHAT WS] File upload started:", data);

        if (!activeFileUpload) {
          console.warn(
            "[CHAT WS] Received file_upload_started without active upload.",
          );

          return;
        }

        if (activeFileUpload.started) {
          console.warn("[CHAT WS] Duplicate file_upload_started received.");

          return;
        }

        activeFileUpload.started = true;

        uploadFileChunks(
          activeFileUpload.file,
          activeFileUpload.onProgress,
        ).catch((error) => {
          console.error("[CHAT WS] Binary upload failed:", error);

          if (!activeFileUpload) {
            return;
          }

          const upload = activeFileUpload;

          activeFileUpload = null;

          fileUploadInProgress = false;

          upload.reject(error);
        });

        return;
      }

      // =====================================================
      // FILE UPLOAD COMPLETE
      // =====================================================

      if (data.type === "file_upload_complete") {
        console.log("[CHAT WS] File upload completed:", data);

        if (!activeFileUpload) {
          console.warn(
            "[CHAT WS] Received file_upload_complete without active upload.",
          );
          return;
        }

        const upload = activeFileUpload;

        activeFileUpload = null;
        fileUploadInProgress = false;

        if (typeof upload.onProgress === "function") {
          upload.onProgress(100, upload.file.size, upload.file.size);
        }

        // Save the sent file permanently in IndexedDB.
        saveReceivedFile({
          sender: currentChatUsername,
          recipient: upload.recipient,

          owner: currentChatUsername,
          direction: "sent",

          fileName: upload.file.name,
          fileSize: upload.file.size,
          fileType: upload.file.type || "application/octet-stream",

          blob: upload.file,

          receivedAt: new Date().toISOString(),
        })
          .then((storedFile) => {
            console.log(
              "[FILE STORE] Sent file permanently stored:",
              storedFile.fileName,
              storedFile.id,
            );

            const completedData = {
              ...data,

              fileId: storedFile.id,
              fileName: storedFile.fileName,
              fileSize: storedFile.fileSize,
              fileType: storedFile.fileType,

              sender: currentChatUsername,
              recipient: upload.recipient,

              conversationUser: upload.recipient,

              own: true,
              persisted: true,
            };

            upload.resolve(completedData);
          })
          .catch((error) => {
            console.error(
              "[FILE STORE] Failed to permanently store sent file:",
              error,
            );

            /*
             * The server upload itself succeeded.
             * Resolve the upload even if IndexedDB persistence fails.
             */
            upload.resolve({
              ...data,

              sender: currentChatUsername,
              recipient: upload.recipient,

              conversationUser: upload.recipient,

              own: true,
              persisted: false,

              persistenceError:
                "File was sent successfully but could not be stored locally.",
            });
          });

        return;
      }

      // =====================================================
      // FILE UPLOAD ERROR
      // =====================================================

      if (data.type === "file_upload_error") {
        console.error("[CHAT WS] File upload error:", data.message);

        if (!activeFileUpload) {
          return;
        }

        const upload = activeFileUpload;

        activeFileUpload = null;

        fileUploadInProgress = false;

        upload.reject(new Error(data.message || "File upload failed."));

        return;
      }

      // =====================================================
      // LOGIN SUCCESS
      // =====================================================

      if (data.type === "login_success") {
        console.log("[CHAT WS] Login successful.");

        connectionHandlers.forEach((handler) => {
          try {
            handler(data);
          } catch (error) {
            console.error("[CHAT WS] Connection handler error:", error);
          }
        });

        // Notify normal React message subscribers too.
        dispatchMessage(data);

        resolve(socket);

        return;
      }

      // =====================================================
      // LOGIN ERROR
      // =====================================================

      if (data.type === "error" || data.type === "login_failed") {
        console.error("[CHAT WS] Login error:", data.message);

        dispatchMessage(data);

        reject(new Error(data.message || "Chat login failed."));

        return;
      }

      // =====================================================
      // SERVER CONNECTED
      // =====================================================

      if (data.type === "server_connected") {
        connectionHandlers.forEach((handler) => {
          try {
            handler(data);
          } catch (error) {
            console.error("[CHAT WS] Connection handler error:", error);
          }
        });

        dispatchMessage(data);

        return;
      }

      // =====================================================
      // SERVER DISCONNECTED
      // =====================================================

      if (data.type === "server_disconnected") {
        dispatchMessage(data);

        return;
      }

      // =====================================================
      // ALL OTHER NORMAL CHAT MESSAGES
      // =====================================================

      dispatchMessage(data);
    };

    // -------------------------------------------------------
    // ERROR
    // -------------------------------------------------------

    socket.onerror = (event) => {
      console.error("[CHAT WS] WebSocket error:", event);

      reject(new Error("Unable to connect to CloudChat server."));
    };

    // -------------------------------------------------------
    // CLOSE
    // -------------------------------------------------------

    socket.onclose = () => {
      console.log("[CHAT WS] Connection closed.");

      socket = null;

      // Reject active upload.
      if (activeFileUpload) {
        const upload = activeFileUpload;

        activeFileUpload = null;

        fileUploadInProgress = false;

        upload.reject(new Error("Chat connection closed during file upload."));
      }

      // Reset incomplete incoming file.
      resetIncomingFile();

      closeHandlers.forEach((handler) => {
        try {
          handler();
        } catch (error) {
          console.error("[CHAT WS] Close handler error:", error);
        }
      });
    };
  });
}

// =========================================================
// INCOMING FILE NOTIFICATION
// =========================================================

function handleIncomingFileNotification(message) {
  // Expected format:
  //
  // FILE_INCOMING: Pranav wants to send you filename.pdf

  const prefix = "FILE_INCOMING:";

  const content = message.substring(prefix.length).trim();

  if (!content) {
    console.warn("[CHAT FILE] Empty FILE_INCOMING notification.");

    return;
  }

  console.log("[CHAT FILE] Incoming file notification:", content);

  // -------------------------------------------------------
  // Extract sender and filename
  // -------------------------------------------------------

  const wantsToSend = " wants to send you ";

  const separatorIndex = content.indexOf(wantsToSend);

  let sender = "Unknown";

  let fileName = "received-file";

  if (separatorIndex !== -1) {
    sender = content.substring(0, separatorIndex).trim();

    fileName = content.substring(separatorIndex + wantsToSend.length).trim();
  }

  // -------------------------------------------------------
  // Start a new incoming file
  // -------------------------------------------------------

  if (incomingFile) {
    console.warn(
      "[CHAT FILE] Previous incoming file was still active. Resetting it.",
    );

    resetIncomingFile();
  }

  incomingFile = {
    sender,

    fileName,

    expectedSize: null,

    receivedSize: 0,

    chunks: [],

    startedAt: Date.now(),
  };

  console.log("[CHAT FILE] Incoming file prepared:", incomingFile);
}

// =========================================================
// INCOMING FILE INFORMATION
// =========================================================

function handleIncomingFileInfo(message) {
  // Expected:
  //
  // FILE_INFO: 992252 bytes

  const prefix = "FILE_INFO:";

  const content = message.substring(prefix.length).trim();

  if (!content) {
    console.warn("[CHAT FILE] Empty FILE_INFO message.");

    return;
  }

  // Extract first number from the message.
  const match = content.match(/\d+/);

  if (!match) {
    console.warn(
      "[CHAT FILE] Unable to determine incoming file size:",
      content,
    );

    return;
  }

  const fileSize = Number(match[0]);

  if (!Number.isFinite(fileSize) || fileSize <= 0) {
    console.warn("[CHAT FILE] Invalid incoming file size:", fileSize);

    return;
  }

  if (fileSize > MAX_FILE_SIZE) {
    console.error("[CHAT FILE] Incoming file exceeds maximum size:", fileSize);

    resetIncomingFile();

    dispatchMessage({
      type: "file_receive_error",

      message: "Incoming file is larger than the 100 MB limit.",
    });

    return;
  }

  // -------------------------------------------------------
  // If FILE_INFO arrived before FILE_INCOMING
  // -------------------------------------------------------

  if (!incomingFile) {
    incomingFile = {
      sender: "Unknown",

      fileName: "received-file",

      expectedSize: fileSize,

      receivedSize: 0,

      chunks: [],

      startedAt: Date.now(),
    };
  } else {
    incomingFile.expectedSize = fileSize;
  }

  console.log("[CHAT FILE] Incoming file size:", fileSize, "bytes");
}

// =========================================================
// FILE DELIVERY STARTED
// =========================================================
//
// This is the actual metadata event that arrives immediately
// before the binary WebSocket chunks.
//
// Example:
//
// {
//   "type": "file_delivery_started",
//   "sender": "Pranav",
//   "fileName": "example.pdf",
//   "fileSize": 992252
// }
//
// This function initializes incomingFile so that the next
// ArrayBuffer chunks can be accepted.
// =========================================================

function handleIncomingFileDeliveryStarted(data) {
  console.log("[CHAT FILE] File delivery started:", data);

  const sender =
    typeof data.sender === "string" && data.sender.trim()
      ? data.sender.trim()
      : "Unknown";

  const fileName =
    typeof data.fileName === "string" && data.fileName.trim()
      ? data.fileName.trim()
      : "received-file";

  const fileSize = Number(data.fileSize);

  // -------------------------------------------------------
  // Validate file size
  // -------------------------------------------------------

  if (!Number.isFinite(fileSize) || fileSize <= 0) {
    console.error(
      "[CHAT FILE] Invalid file_delivery_started size:",
      data.fileSize,
    );

    dispatchMessage({
      type: "file_receive_error",

      sender,

      fileName,

      message: "Invalid incoming file size.",
    });

    resetIncomingFile();

    return;
  }

  if (fileSize > MAX_FILE_SIZE) {
    console.error("[CHAT FILE] Incoming file exceeds 100 MB:", fileSize);

    dispatchMessage({
      type: "file_receive_error",

      sender,

      fileName,

      message: "Incoming file is larger than the 100 MB limit.",
    });

    resetIncomingFile();

    return;
  }

  // -------------------------------------------------------
  // Reset previous incomplete transfer
  // -------------------------------------------------------

  if (incomingFile) {
    console.warn("[CHAT FILE] Replacing previous incoming file state.");

    resetIncomingFile();
  }

  // -------------------------------------------------------
  // Create incoming file state
  // -------------------------------------------------------

  incomingFile = {
    sender,

    fileName,

    expectedSize: fileSize,

    receivedSize: 0,

    chunks: [],

    startedAt: Date.now(),
  };

  console.log("[CHAT FILE] Incoming binary transfer initialized:", {
    sender: incomingFile.sender,

    fileName: incomingFile.fileName,

    expectedSize: incomingFile.expectedSize,
  });

  // -------------------------------------------------------
  // Notify React
  // -------------------------------------------------------

  dispatchMessage({
    type: "file_receive_started",

    sender,

    fileName,

    fileSize,

    receivedSize: 0,

    progress: 0,
  });
}

// =========================================================
// FILE DELIVERY COMPLETE
// =========================================================

function handleIncomingFileDeliveryComplete(data) {
  console.log("[CHAT FILE] Backend reported file delivery complete:", data);

  // -------------------------------------------------------
  // If the binary data has already completed and the
  // incoming state has already been reset, simply notify
  // React.
  // -------------------------------------------------------

  if (!incomingFile) {
    dispatchMessage({
      type: "file_delivery_complete",

      sender: data.sender || "Unknown",

      fileName: data.fileName || "received-file",

      fileSize: Number(data.fileSize) || 0,
    });

    return;
  }

  // -------------------------------------------------------
  // Check whether the expected binary data was received.
  // -------------------------------------------------------

  if (
    incomingFile.expectedSize !== null &&
    incomingFile.receivedSize !== incomingFile.expectedSize
  ) {
    console.error(
      "[CHAT FILE] Backend reported completion, but received size does not match.",
      {
        expected: incomingFile.expectedSize,

        received: incomingFile.receivedSize,
      },
    );

    dispatchMessage({
      type: "file_receive_error",

      sender: incomingFile.sender,

      fileName: incomingFile.fileName,

      message:
        "File delivery completed on the server, but the browser received incomplete data.",
    });

    resetIncomingFile();

    return;
  }

  // -------------------------------------------------------
  // Normally completeIncomingFile() has already created
  // the Blob and reset incomingFile.
  //
  // If we still have incomingFile here, complete it now.
  // -------------------------------------------------------

  completeIncomingFile();
}

// =========================================================
// HANDLE INCOMING ARRAYBUFFER
// =========================================================

function handleIncomingBinaryFile(arrayBuffer) {
  if (!(arrayBuffer instanceof ArrayBuffer)) {
    return;
  }

  const byteLength = arrayBuffer.byteLength;

  console.log("[CHAT FILE] Received binary chunk:", byteLength, "bytes");

  // -------------------------------------------------------
  // No metadata yet
  // -------------------------------------------------------

  if (!incomingFile) {
    console.warn("[CHAT FILE] Binary data received without file metadata.");

    return;
  }

  // -------------------------------------------------------
  // Validate expected size
  // -------------------------------------------------------

  if (
    incomingFile.expectedSize !== null &&
    incomingFile.receivedSize + byteLength > incomingFile.expectedSize
  ) {
    console.error("[CHAT FILE] Incoming file exceeds expected size.");

    dispatchMessage({
      type: "file_receive_error",

      sender: incomingFile.sender,

      fileName: incomingFile.fileName,

      message: "Incoming file size exceeded expected size.",
    });

    resetIncomingFile();

    return;
  }

  // -------------------------------------------------------
  // Store chunk
  // -------------------------------------------------------

  incomingFile.chunks.push(arrayBuffer);

  incomingFile.receivedSize += byteLength;

  // -------------------------------------------------------
  // Progress
  // -------------------------------------------------------

  let progress = 0;

  if (incomingFile.expectedSize !== null && incomingFile.expectedSize > 0) {
    progress = Math.min(
      100,
      Math.round((incomingFile.receivedSize / incomingFile.expectedSize) * 100),
    );
  }

  console.log(
    "[CHAT FILE] Receive progress:",
    `${progress}%`,
    `${incomingFile.receivedSize}/${incomingFile.expectedSize}`,
  );

  dispatchMessage({
    type: "file_receive_progress",

    sender: incomingFile.sender,

    fileName: incomingFile.fileName,

    receivedSize: incomingFile.receivedSize,

    expectedSize: incomingFile.expectedSize,

    progress,
  });

  // -------------------------------------------------------
  // File complete
  // -------------------------------------------------------

  if (
    incomingFile.expectedSize !== null &&
    incomingFile.receivedSize === incomingFile.expectedSize
  ) {
    void completeIncomingFile();
  }
}

// =========================================================
// HANDLE INCOMING BLOB
// =========================================================

async function handleIncomingBlobFile(blob) {
  if (!(blob instanceof Blob)) {
    return;
  }

  try {
    const arrayBuffer = await blob.arrayBuffer();

    handleIncomingBinaryFile(arrayBuffer);
  } catch (error) {
    console.error("[CHAT FILE] Failed to read incoming Blob:", error);

    dispatchMessage({
      type: "file_receive_error",

      message: "Unable to read incoming file data.",
    });

    resetIncomingFile();
  }
}

// =========================================================
// COMPLETE INCOMING FILE
// =========================================================

async function completeIncomingFile() {
  if (!incomingFile) {
    return;
  }

  const fileData = incomingFile;

  incomingFile = null;

  console.log(
    "[CHAT FILE] Incoming file completed:",
    fileData.fileName,
    fileData.receivedSize,
    "bytes",
  );

  try {
    const blob = new Blob(fileData.chunks, {
      type: getMimeType(fileData.fileName),
    });

    // -------------------------------------------------------
    // VALIDATE SIZE
    // -------------------------------------------------------

    if (fileData.expectedSize && blob.size !== fileData.expectedSize) {
      console.error("[CHAT FILE] Final Blob size mismatch.", {
        expected: fileData.expectedSize,
        actual: blob.size,
      });

      dispatchMessage({
        type: "file_receive_error",

        sender: fileData.sender,

        fileName: fileData.fileName,

        message: "Received file is incomplete or corrupted.",
      });

      resetIncomingFile();

      return;
    }

    // -------------------------------------------------------
    // SAVE PERMANENTLY IN INDEXEDDB
    // -------------------------------------------------------

    const storedFile = await saveReceivedFile({
      sender: fileData.sender,

      recipient: currentChatUsername,

      fileName: sanitizeFileName(fileData.fileName),

      fileSize: blob.size,

      fileType: getMimeType(fileData.fileName),

      blob,

      receivedAt: new Date().toISOString(),
    });

    console.log(
      "[CHAT FILE] Permanently stored:",
      storedFile.fileName,
      storedFile.id,
    );

    // -------------------------------------------------------
    // CREATE TEMPORARY URL FOR CURRENT SESSION
    // -------------------------------------------------------

    const downloadUrl = URL.createObjectURL(blob);

    // -------------------------------------------------------
    // SEND TO CHAT UI
    // -------------------------------------------------------

    dispatchMessage({
      type: "file_received",

      fileId: storedFile.id,

      sender: storedFile.sender,

      recipient: storedFile.recipient,

      fileName: storedFile.fileName,

      fileSize: storedFile.fileSize,

      fileType: storedFile.fileType,

      downloadUrl,

      receivedAt: storedFile.receivedAt,

      message: `${storedFile.fileName} received successfully.`,
    });
  } catch (error) {
    console.error("[CHAT FILE] Failed to persist incoming file:", error);

    dispatchMessage({
      type: "file_receive_error",

      sender: fileData.sender,

      fileName: fileData.fileName,

      message: "Failed to permanently save received file.",
    });
  }
}

// =========================================================
// RESET INCOMING FILE
// =========================================================

function resetIncomingFile() {
  incomingFile = null;
}

// =========================================================
// SANITIZE FILE NAME
// =========================================================

function sanitizeFileName(fileName) {
  if (!fileName || typeof fileName !== "string") {
    return "received-file";
  }

  let cleaned = fileName.replace(/[\\/]/g, "_").replace(/\.\./g, "_").trim();

  // Remove temporary backend filename prefix.
  //
  // Example:
  // temp_1790959760222_cloudchat-123456-7_file.pdf
  // becomes:
  // cloudchat-123456-7_file.pdf
  cleaned = cleaned.replace(/^temp_\d+_/, "");

  // Remove CloudChat transport prefix.
  //
  // Example:
  // cloudchat-123456789-7_file.pdf
  // becomes:
  // file.pdf
  cleaned = cleaned.replace(/^cloudchat-[\d-]+_/, "");

  return cleaned || "received-file";
}

// =========================================================
// MIME TYPE
// =========================================================

function getMimeType(fileName) {
  if (!fileName || typeof fileName !== "string") {
    return "application/octet-stream";
  }

  const extension = fileName.split(".").pop().toLowerCase();

  const mimeTypes = {
    pdf: "application/pdf",

    txt: "text/plain",

    csv: "text/csv",

    json: "application/json",

    xml: "application/xml",

    html: "text/html",
    htm: "text/html",

    jpg: "image/jpeg",
    jpeg: "image/jpeg",

    png: "image/png",

    gif: "image/gif",

    webp: "image/webp",

    mp3: "audio/mpeg",

    wav: "audio/wav",

    ogg: "audio/ogg",

    mp4: "video/mp4",

    webm: "video/webm",

    zip: "application/zip",

    rar: "application/vnd.rar",

    "7z": "application/x-7z-compressed",

    doc: "application/msword",

    docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",

    xls: "application/vnd.ms-excel",

    xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",

    ppt: "application/vnd.ms-powerpoint",

    pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",

    java: "text/x-java-source",

    js: "text/javascript",

    jsx: "text/javascript",

    ts: "text/typescript",

    tsx: "text/typescript",

    css: "text/css",

    py: "text/x-python",

    c: "text/x-c",

    cpp: "text/x-c++",

    h: "text/x-c",

    hpp: "text/x-c++",

    sql: "application/sql",
  };

  return mimeTypes[extension] || "application/octet-stream";
}

// =========================================================
// DISPATCH NORMAL MESSAGE
// =========================================================

function dispatchMessage(data) {
  if (!data) {
    return;
  }

  if (messageHandlers.length > 0) {
    messageHandlers.forEach((handler) => {
      try {
        handler(data);
      } catch (error) {
        console.error("[CHAT WS] Message handler error:", error);
      }
    });

    return;
  }

  // ---------------------------------------------------------
  // Queue normal messages when Chat.jsx is not listening
  // ---------------------------------------------------------

  if (data.type === "message" || data.type === "private_message") {
    queuePendingMessage(data);

    return;
  }

  // ---------------------------------------------------------
  // Queue received files when Chat.jsx is not listening
  // ---------------------------------------------------------

  if (data.type === "file_received") {
    if (pendingFiles.length >= MAX_PENDING_FILES) {
      const oldestFile = pendingFiles.shift();

      if (oldestFile?.downloadUrl) {
        URL.revokeObjectURL(oldestFile.downloadUrl);
      }
    }

    pendingFiles.push(data);

    console.log("[CHAT WS] File queued for Chat.jsx:", data.fileName);
  }
}
// =========================================================
// QUEUE PENDING MESSAGE
// =========================================================

function queuePendingMessage(data) {
  if (!data) {
    return;
  }

  console.log("[CHAT WS] Queuing incoming message:", data);

  pendingMessages.push(data);

  if (pendingMessages.length > MAX_PENDING_MESSAGES) {
    pendingMessages.shift();
  }
}

// =========================================================
// SEND MESSAGE
// =========================================================

export function sendChatMessage(message) {
  // -------------------------------------------------------
  // Validate message
  // -------------------------------------------------------

  if (message === null || message === undefined) {
    console.error("[CHAT WS] Cannot send empty message.");

    return false;
  }

  const text = String(message);

  if (!text.trim()) {
    console.error("[CHAT WS] Cannot send blank message.");

    return false;
  }

  // -------------------------------------------------------
  // Check connection
  // -------------------------------------------------------

  if (!socket || socket.readyState !== WebSocket.OPEN) {
    console.error("[CHAT WS] Socket is not connected.");

    return false;
  }

  // -------------------------------------------------------
  // Normal chat commands are ALWAYS sent as TEXT.
  // -------------------------------------------------------

  try {
    const sent = sendWebSocketMessage(text);

    if (sent) {
      console.log("[CHAT WS] Sent text command:", text);
    }

    return sent;
  } catch (error) {
    console.error("[CHAT WS] Failed to send text command:", error);

    return false;
  }
}

// =========================================================
// SEND CHAT COMMAND
// =========================================================

export function sendChatCommand(command) {
  return sendChatMessage(command);
}

// =========================================================
// SEND PRIVATE FILE
// =========================================================

export function sendPrivateFile(recipient, file, onProgress = null) {
  return new Promise((resolve, reject) => {
    // -----------------------------------------------------
    // Check WebSocket
    // -----------------------------------------------------

    if (!socket || socket.readyState !== WebSocket.OPEN) {
      reject(new Error("Chat is not connected."));

      return;
    }

    // -----------------------------------------------------
    // Check recipient
    // -----------------------------------------------------

    if (!recipient || !recipient.trim()) {
      reject(new Error("Recipient is required."));

      return;
    }

    // -----------------------------------------------------
    // Check file
    // -----------------------------------------------------

    if (!file) {
      reject(new Error("No file selected."));

      return;
    }

    // -----------------------------------------------------
    // Check file size
    // -----------------------------------------------------

    if (typeof file.size !== "number") {
      reject(new Error("Unable to determine file size."));

      return;
    }

    if (file.size <= 0) {
      reject(new Error("The selected file is empty."));

      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      reject(new Error("File is too large. Maximum size is 100 MB."));

      return;
    }

    // -----------------------------------------------------
    // Only one upload at a time
    // -----------------------------------------------------

    if (fileUploadInProgress) {
      reject(new Error("Another file upload is already in progress."));

      return;
    }

    // -----------------------------------------------------
    // Prepare upload state
    // -----------------------------------------------------

    const cleanRecipient = recipient.trim();

    fileUploadInProgress = true;

    activeFileUpload = {
      recipient: cleanRecipient,

      file,

      started: false,

      resolve,

      reject,

      onProgress,
    };

    // -----------------------------------------------------
    // Initial progress
    // -----------------------------------------------------

    if (typeof onProgress === "function") {
      onProgress(0, 0, file.size);
    }

    // -----------------------------------------------------
    // Send ONLY file-start JSON
    // -----------------------------------------------------

    try {
      const fileStartMessage = JSON.stringify({
        type: "file_start",

        recipient: cleanRecipient,

        fileName: file.name,

        fileSize: file.size,
      });

      console.log("[CHAT WS] Sending file_start:", fileStartMessage);

      socket.send(fileStartMessage);
    } catch (error) {
      activeFileUpload = null;

      fileUploadInProgress = false;

      reject(error);
    }
  });
}

// =========================================================
// SEND FILE IN BINARY CHUNKS
// =========================================================

async function uploadFileChunks(file, onProgress) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    throw new Error("Chat connection was lost.");
  }

  // 64 KB chunks.
  const CHUNK_SIZE = 64 * 1024;

  let offset = 0;

  console.log("[CHAT WS] Starting binary upload:", file.name);

  while (offset < file.size) {
    // -----------------------------------------------------
    // Check connection
    // -----------------------------------------------------

    if (!socket || socket.readyState !== WebSocket.OPEN) {
      throw new Error("Chat connection was lost during file upload.");
    }

    // -----------------------------------------------------
    // Read next chunk
    // -----------------------------------------------------

    const end = Math.min(offset + CHUNK_SIZE, file.size);

    const chunk = file.slice(offset, end);

    const buffer = await chunk.arrayBuffer();

    // -----------------------------------------------------
    // Send binary chunk
    // -----------------------------------------------------

    const sent = sendWebSocketBinary(buffer);

    if (!sent) {
      throw new Error("Failed to send file chunk.");
    }

    offset += buffer.byteLength;

    // -----------------------------------------------------
    // Progress
    // -----------------------------------------------------

    const progress = Math.min(100, Math.round((offset / file.size) * 100));

    console.log("[CHAT WS] File upload progress:", `${progress}%`);

    if (typeof onProgress === "function") {
      onProgress(progress, offset, file.size);
    }

    // -----------------------------------------------------
    // Yield to browser
    // -----------------------------------------------------

    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  }

  console.log("[CHAT WS] All file chunks sent:", file.name);
}

// =========================================================
// GET SOCKET
// =========================================================

export function getChatSocket() {
  return socket;
}

// =========================================================
// CONNECTION STATUS
// =========================================================

export function isChatConnected() {
  return socket !== null && socket.readyState === WebSocket.OPEN;
}

async function restorePersistedFiles(handler) {
  if (!currentChatUsername) {
    return;
  }

  try {
    const storedFiles = await getStoredFilesForUser(currentChatUsername);

    if (!storedFiles.length) {
      return;
    }

    console.log("[FILE STORE] Restoring persisted files:", storedFiles.length);

    storedFiles.forEach((file) => {
      if (!file.blob) {
        return;
      }

      const isSent =
        file.direction === "sent" ||
        (file.owner === currentChatUsername &&
          file.sender === currentChatUsername);

      const conversationUser = isSent ? file.recipient : file.sender;

      if (!conversationUser) {
        return;
      }

      const downloadUrl = URL.createObjectURL(file.blob);

      handler({
        type: "file_received",

        fileId: file.id,

        // Actual sender of the file
        sender: file.sender,

        // Actual recipient of the file
        recipient: file.recipient,

        // Used by Chat.jsx to decide which conversation
        // the file belongs to
        conversationUser,

        // Sent by current user OR received from another user
        own: isSent,

        fileName: file.fileName,
        fileSize: file.fileSize,
        fileType: file.fileType,

        downloadUrl,

        receivedAt: file.receivedAt,

        persisted: true,

        message: isSent
          ? `${file.fileName} sent successfully.`
          : `${file.fileName} received successfully.`,
      });
    });
  } catch (error) {
    console.error("[FILE STORE] Failed to restore files:", error);
  }
}

// =========================================================
// MESSAGE SUBSCRIBER
// =========================================================

export function onChatMessage(handler) {
  if (typeof handler !== "function") {
    return () => {};
  }

  messageHandlers.push(handler);

  console.log("[CHAT WS] Message handler registered.");

  // =========================================================
  // REPLAY PENDING TEXT MESSAGES
  // =========================================================

  if (pendingMessages.length > 0) {
    const messagesToReplay = [...pendingMessages];

    pendingMessages = [];

    messagesToReplay.forEach((data) => {
      try {
        handler(data);
      } catch (error) {
        console.error("[CHAT WS] Pending message replay error:", error);
      }
    });
  }

  // =========================================================
  // REPLAY PENDING FILES
  // =========================================================

  if (pendingFiles.length > 0) {
    const filesToReplay = [...pendingFiles];

    pendingFiles = [];

    filesToReplay.forEach((data) => {
      try {
        handler(data);
      } catch (error) {
        console.error("[CHAT WS] Pending file replay error:", error);
      }
    });
  }

  // =========================================================
  // RESTORE FILES FROM INDEXEDDB
  // =========================================================

  void restorePersistedFiles(handler);

  return () => {
    messageHandlers = messageHandlers.filter((item) => item !== handler);

    console.log("[CHAT WS] Message handler removed.");
  };
}

// =========================================================
// CONNECTION SUBSCRIBER
// =========================================================

export function onChatConnected(handler) {
  if (typeof handler !== "function") {
    return () => {};
  }

  connectionHandlers.push(handler);

  return () => {
    connectionHandlers = connectionHandlers.filter((item) => item !== handler);
  };
}

// =========================================================
// CLOSE SUBSCRIBER
// =========================================================

export function onChatClosed(handler) {
  if (typeof handler !== "function") {
    return () => {};
  }

  closeHandlers.push(handler);

  return () => {
    closeHandlers = closeHandlers.filter((item) => item !== handler);
  };
}

// =========================================================
// DISCONNECT
// =========================================================

export function disconnectChat() {
  console.log("[CHAT WS] Disconnecting chat...");

  // -------------------------------------------------------
  // Reject active upload BEFORE closing socket
  // -------------------------------------------------------

  if (activeFileUpload) {
    const upload = activeFileUpload;

    activeFileUpload = null;

    fileUploadInProgress = false;

    upload.reject(new Error("Chat disconnected."));
  }

  // -------------------------------------------------------
  // Close WebSocket
  // -------------------------------------------------------

  closeWebSocket();
  socket = null;

  // -------------------------------------------------------
  // Clear pending messages
  // -------------------------------------------------------

  pendingMessages = [];

  pendingFiles.forEach((file) => {
    if (file?.downloadUrl) {
      URL.revokeObjectURL(file.downloadUrl);
    }
  });

  pendingFiles = [];

  // -------------------------------------------------------
  // Clear incoming file
  // -------------------------------------------------------

  resetIncomingFile();
}
