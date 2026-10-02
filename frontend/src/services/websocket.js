// =========================================================
// GENERIC WEBSOCKET SERVICE
// =========================================================

let socket = null;

// =========================================================
// CREATE CONNECTION
// =========================================================

export function createWebSocket(url) {
  if (!url) {
    throw new Error("WebSocket URL is required.");
  }

  // Reuse existing connection
  if (socket && socket.readyState === WebSocket.OPEN) {
    return socket;
  }

  socket = new WebSocket(url);

  // We receive binary file data as ArrayBuffer.
  socket.binaryType = "arraybuffer";

  return socket;
}

// =========================================================
// GET CURRENT SOCKET
// =========================================================

export function getWebSocket() {
  return socket;
}

// =========================================================
// CONNECTION STATUS
// =========================================================

export function isWebSocketConnected() {
  return socket !== null && socket.readyState === WebSocket.OPEN;
}

// =========================================================
// SEND TEXT
// =========================================================

export function sendWebSocketMessage(message) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    console.error("[WEBSOCKET] Socket is not connected.");
    return false;
  }

  try {
    socket.send(message);
    return true;
  } catch (error) {
    console.error("[WEBSOCKET] Failed to send message:", error);
    return false;
  }
}

// =========================================================
// SEND BINARY DATA
// =========================================================

export function sendWebSocketBinary(data) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    console.error("[WEBSOCKET] Socket is not connected.");
    return false;
  }

  try {
    socket.send(data);
    return true;
  } catch (error) {
    console.error("[WEBSOCKET] Failed to send binary data:", error);
    return false;
  }
}

// =========================================================
// CLOSE CONNECTION
// =========================================================

export function closeWebSocket() {
  if (!socket) {
    return;
  }

  try {
    socket.close();
  } catch (error) {
    console.error("[WEBSOCKET] Error closing socket:", error);
  }

  socket = null;
}

// =========================================================
// SOCKET STATE
// =========================================================

export function getWebSocketState() {
  if (!socket) {
    return WebSocket.CLOSED;
  }

  return socket.readyState;
}
