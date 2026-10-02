// =========================================================
// CLOUDCHAT RECEIVED FILE STORE
// =========================================================
//
// Stores received files in IndexedDB so they survive:
//
// - Chat page navigation
// - browser refresh
// - logout
// - login again
// - closing/reopening the Chat page
//
// The actual Blob is stored, not a temporary blob URL.
// =========================================================

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

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onerror = () => {
      reject(
        request.error || new Error("Unable to open CloudChat file storage."),
      );
    };
  });
}

// =========================================================
// GENERATE FILE ID
// =========================================================

function createFileId() {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }

  return `file-${Date.now()}-` + `${Math.random().toString(36).slice(2)}`;
}

// =========================================================
// NORMALIZE STORED FILE NAME
// =========================================================

function normalizeFileName(fileName) {
  if (!fileName) {
    return "";
  }

  return fileName
    .replace(/^temp_\d+_/, "")
    .replace(/^cloudchat-\d+(?:-\d+)*[-_]/, "");
}

// =========================================================
// SAVE RECEIVED FILE
// =========================================================

// =========================================================
// SAVE FILE
// =========================================================

export async function saveReceivedFile({
  sender,
  recipient,
  owner = null,
  direction = "received",
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

  const cleanFileName = normalizeFileName(fileName);

  const actualOwner = owner || recipient;

  // =======================================================
  // CHECK FOR EXISTING FILE
  // =======================================================

  const existingFile = await new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, "readonly");

    const store = transaction.objectStore(STORE_NAME);

    const request = store.getAll();

    request.onsuccess = () => {
      const files = request.result || [];

      const match = files.find((file) => {
        const storedCleanName = normalizeFileName(file.fileName);

        return (
          file.owner === actualOwner &&
          file.sender === sender &&
          file.recipient === recipient &&
          file.direction === direction &&
          storedCleanName === cleanFileName &&
          Number(file.fileSize) === Number(fileSize || blob.size)
        );
      });

      resolve(match || null);
    };

    request.onerror = () => {
      reject(request.error || new Error("Unable to check existing files."));
    };
  });

  // =======================================================
  // DUPLICATE FOUND
  // =======================================================

  if (existingFile) {
    database.close();

    console.log("[FILE STORE] Duplicate prevented:", cleanFileName);

    return existingFile;
  }

  // =======================================================
  // CREATE NEW RECORD
  // =======================================================

  const id = createFileId();

  const record = {
    id,

    sender,

    recipient,

    owner: actualOwner,

    direction,

    // Store clean filename
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

    transaction.oncomplete = () => {
      resolve();
    };

    transaction.onerror = () => {
      reject(transaction.error || new Error("Unable to save received file."));
    };

    transaction.onabort = () => {
      reject(
        transaction.error || new Error("File storage transaction aborted."),
      );
    };
  });

  database.close();

  await trimStoredFiles(actualOwner);

  console.log(
    "[FILE STORE] Saved:",
    cleanFileName,
    "from:",
    sender,
    "direction:",
    direction,
  );

  return record;
}

// =========================================================
// GET FILES FOR CURRENT USER
// =========================================================

export async function getReceivedFiles(recipient) {
  if (!recipient) {
    return [];
  }

  const database = await openDatabase();

  const records = await new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, "readonly");

    const store = transaction.objectStore(STORE_NAME);

    const index = store.index("recipient");

    const request = index.getAll(recipient);

    request.onsuccess = () => {
      resolve(request.result || []);
    };

    request.onerror = () => {
      reject(request.error || new Error("Unable to read received files."));
    };
  });

  database.close();

  records.sort(
    (a, b) =>
      new Date(a.receivedAt).getTime() - new Date(b.receivedAt).getTime(),
  );

  return records;
}

// =========================================================
// DELETE ONE FILE
// =========================================================

export async function deleteReceivedFile(fileId) {
  if (!fileId) {
    return;
  }

  const database = await openDatabase();

  await new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, "readwrite");

    const store = transaction.objectStore(STORE_NAME);

    store.delete(fileId);

    transaction.oncomplete = () => {
      resolve();
    };

    transaction.onerror = () => {
      reject(transaction.error || new Error("Unable to delete received file."));
    };
  });

  database.close();

  console.log("[FILE STORE] Deleted:", fileId);
}

// =========================================================
// CLEAR ALL FILES FOR A USER
// =========================================================
//
// NOT called during logout.
// Kept here for future "Clear received files" UI.
// =========================================================

export async function clearReceivedFiles(recipient) {
  if (!recipient) {
    return;
  }

  const files = await getReceivedFiles(recipient);

  for (const file of files) {
    await deleteReceivedFile(file.id);
  }

  console.log("[FILE STORE] Cleared files for:", recipient);
}

export async function getStoredFilesForUser(username) {
  if (!username) {
    return [];
  }

  const database = await openDatabase();

  const records = await new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, "readonly");

    const store = transaction.objectStore(STORE_NAME);
    const request = store.getAll();

    request.onsuccess = () => {
      const allFiles = request.result || [];

      const userFiles = allFiles.filter((file) => {
        // New records
        if (file.owner) {
          return file.owner === username;
        }

        // Compatibility with files saved before
        // the owner field was added
        return file.recipient === username || file.sender === username;
      });

      resolve(userFiles);
    };

    request.onerror = () => {
      reject(request.error || new Error("Unable to read stored files."));
    };
  });

  database.close();

  records.sort(
    (a, b) =>
      new Date(a.receivedAt).getTime() - new Date(b.receivedAt).getTime(),
  );

  return records;
}

// =========================================================
// LIMIT STORAGE
// =========================================================

async function trimStoredFiles(owner) {
  const files = await getStoredFilesForUser(owner);

  if (files.length <= MAX_STORED_FILES) {
    return;
  }

  const filesToDelete = files.slice(0, files.length - MAX_STORED_FILES);

  for (const file of filesToDelete) {
    await deleteReceivedFile(file.id);
  }

  console.log("[FILE STORE] Trimmed old files for:", owner);
}
