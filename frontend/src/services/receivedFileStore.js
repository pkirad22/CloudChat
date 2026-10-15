const DB_NAME = "CloudChatFiles";
const DB_VERSION = 1;
const STORE_NAME = "receivedFiles";
const MAX_STORED_FILES = 100;

// =========================================================
// OPEN DATABASE
// =========================================================

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const database = event.target.result;

      if (!database.objectStoreNames.contains(STORE_NAME)) {
        const store = database.createObjectStore(STORE_NAME, {
          keyPath: "id",
        });

        store.createIndex("recipient", "recipient", {
          unique: false,
        });

        store.createIndex("receivedAt", "receivedAt", {
          unique: false,
        });
      }
    };

    request.onsuccess = () => resolve(request.result);

    request.onerror = () => {
      reject(
        request.error || new Error("Unable to open CloudChat file storage."),
      );
    };
  });
}

// =========================================================
// HELPERS
// =========================================================

function createFileId() {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }

  return `file-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function normalizeFileName(fileName) {
  if (!fileName) return "";

  return String(fileName)
    .replace(/^temp_\d+_/, "")
    .replace(/^cloudchat-\d+(?:-\d+)*[-_]/, "");
}

function sortByReceivedAt(files) {
  return files.sort(
    (a, b) =>
      new Date(a.receivedAt).getTime() - new Date(b.receivedAt).getTime(),
  );
}

function getAllRecords(database) {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, "readonly");
    const request = transaction.objectStore(STORE_NAME).getAll();

    request.onsuccess = () => resolve(request.result || []);

    request.onerror = () => {
      reject(request.error || new Error("Unable to read stored files."));
    };
  });
}

// =========================================================
// SAVE RECEIVED FILE
//
// Supports:
// - Private files
// - Group files
// - Stable transfer-ID deduplication
// - Existing private-file callers
// =========================================================

export async function saveReceivedFile({
  id = null,
  transferId = null,
  sender,
  recipient,
  owner = null,
  direction = "received",
  groupName = null,
  fileName,
  fileSize,
  fileType,
  blob,
  receivedAt = null,
}) {
  if (!sender || !recipient || !fileName || !blob) {
    throw new Error("Invalid received file data.");
  }

  const database = await openDatabase();
  const actualOwner = owner || recipient;
  const cleanFileName = normalizeFileName(fileName);

  try {
    const existingFiles = await getAllRecords(database);

    // Prefer the backend-provided stable transfer ID.
    // Do not deduplicate unrelated files by filename and size
    // when a stable transfer ID is available.
    if (transferId) {
      const existingTransfer = existingFiles.find(
        (file) => file.owner === actualOwner && file.transferId === transferId,
      );

      if (existingTransfer) {
        return existingTransfer;
      }
    } else {
      // Compatibility fallback for older messages without
      // a transfer ID. This may match two distinct files that
      // happen to have the same metadata.
      const existingFile = existingFiles.find((file) => {
        return (
          file.owner === actualOwner &&
          file.sender === sender &&
          file.recipient === recipient &&
          file.direction === direction &&
          (file.groupName || null) === (groupName || null) &&
          normalizeFileName(file.fileName) === cleanFileName &&
          Number(file.fileSize) === Number(fileSize ?? blob.size)
        );
      });

      if (existingFile) {
        return existingFile;
      }
    }

    const record = {
      id: id || transferId || createFileId(),
      transferId: transferId || null,

      sender,
      recipient,
      owner: actualOwner,
      direction,

      groupName: groupName || null,

      fileName: cleanFileName,
      fileSize: Number(fileSize) || blob.size || 0,
      fileType: fileType || blob.type || "application/octet-stream",

      blob,

      receivedAt: receivedAt || new Date().toISOString(),
    };

    await new Promise((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, "readwrite");
      const store = transaction.objectStore(STORE_NAME);

      store.put(record);

      transaction.oncomplete = resolve;

      transaction.onerror = () => {
        reject(transaction.error || new Error("Unable to save received file."));
      };

      transaction.onabort = () => {
        reject(
          transaction.error || new Error("File storage transaction aborted."),
        );
      };
    });

    await trimStoredFiles(actualOwner);

    console.log(
      "[FILE STORE] Saved:",
      cleanFileName,
      groupName ? `group: ${groupName}` : `direction: ${direction}`,
    );

    return record;
  } finally {
    database.close();
  }
}

// =========================================================
// GET FILES BY RECIPIENT
// Preserves the existing API.
// =========================================================

export async function getReceivedFiles(recipient) {
  if (!recipient) return [];

  const database = await openDatabase();

  try {
    const transaction = database.transaction(STORE_NAME, "readonly");
    const request = transaction
      .objectStore(STORE_NAME)
      .index("recipient")
      .getAll(recipient);

    const records = await new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result || []);

      request.onerror = () => {
        reject(request.error || new Error("Unable to read received files."));
      };
    });

    return sortByReceivedAt(records);
  } finally {
    database.close();
  }
}

// =========================================================
// GET ALL FILES OWNED BY A USER
// Preserves compatibility with existing records.
// =========================================================

export async function getStoredFilesForUser(username) {
  if (!username) return [];

  const database = await openDatabase();

  try {
    const allFiles = await getAllRecords(database);

    const userFiles = allFiles.filter((file) => {
      if (file.owner) {
        return file.owner === username;
      }

      return file.recipient === username || file.sender === username;
    });

    return sortByReceivedAt(userFiles);
  } finally {
    database.close();
  }
}

// =========================================================
// GET GROUP FILES FOR A USER
// =========================================================

export async function getStoredGroupFilesForUser(username, groupName) {
  if (!username || !groupName) return [];

  const files = await getStoredFilesForUser(username);

  return files.filter((file) => file.groupName === groupName);
}

// =========================================================
// DELETE ONE FILE
// =========================================================

export async function deleteReceivedFile(fileId) {
  if (!fileId) return;

  const database = await openDatabase();

  try {
    await new Promise((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, "readwrite");
      transaction.objectStore(STORE_NAME).delete(fileId);

      transaction.oncomplete = resolve;

      transaction.onerror = () => {
        reject(
          transaction.error || new Error("Unable to delete received file."),
        );
      };
    });

    console.log("[FILE STORE] Deleted:", fileId);
  } finally {
    database.close();
  }
}

// =========================================================
// CLEAR FILES FOR A USER
// NOT called automatically during logout.
// =========================================================

export async function clearReceivedFiles(recipient) {
  if (!recipient) return;

  const files = await getReceivedFiles(recipient);

  for (const file of files) {
    await deleteReceivedFile(file.id);
  }

  console.log("[FILE STORE] Cleared files for:", recipient);
}

// =========================================================
// LIMIT LOCAL STORAGE
// =========================================================

async function trimStoredFiles(owner) {
  const files = await getStoredFilesForUser(owner);

  if (files.length <= MAX_STORED_FILES) return;

  const filesToDelete = files.slice(0, files.length - MAX_STORED_FILES);

  for (const file of filesToDelete) {
    await deleteReceivedFile(file.id);
  }

  console.log("[FILE STORE] Trimmed old files for:", owner);
}
