import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
  ArrowLeft,
  LogOut,
  MessageCircle,
  Send,
  Users,
  Server,
  UserRound,
  RefreshCw,
  FolderOpen,
} from "lucide-react";

import GroupFileSharing from "../components/GroupFileSharing";

import {
  disconnectChat,
  isChatConnected,
  onChatClosed,
  onChatMessage,
  sendChatCommand,
} from "../services/chatSocket";

export default function GroupChat() {
  const navigate = useNavigate();

  // =========================================================
  // CURRENT USER / SELECTED GROUP
  // =========================================================

  const username = sessionStorage.getItem("cloudchat_username") || "";

  const selectedGroup =
    sessionStorage.getItem("cloudchat_selected_group") || "";

  // =========================================================
  // STATE
  // =========================================================

  const [messages, setMessages] = useState([]);

  const [messageInput, setMessageInput] = useState("");

  const [connected, setConnected] = useState(isChatConnected());

  const [loadingHistory, setLoadingHistory] = useState(false);

  const [error, setError] = useState("");

  const [systemMessage, setSystemMessage] = useState("");

  const [memberCount, setMemberCount] = useState(null);

  const [groupMembers, setGroupMembers] = useState([]);
  const [showMembers, setShowMembers] = useState(false);

  // =========================================================
  // REFS
  // =========================================================

  const messagesEndRef = useRef(null);

  const groupNameRef = useRef(selectedGroup);

  const usernameRef = useRef(username);

  const historyRequestedRef = useRef(false);

  // =========================================================
  // KEEP REFS UPDATED
  // =========================================================

  useEffect(() => {
    groupNameRef.current = selectedGroup;
  }, [selectedGroup]);

  useEffect(() => {
    usernameRef.current = username;
  }, [username]);

  // =========================================================
  // AUTO SCROLL
  // =========================================================

  const scrollToBottom = useCallback(() => {
    setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({
        behavior: "smooth",
      });
    }, 50);
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  // =========================================================
  // ADD MESSAGE
  // =========================================================

  const addMessage = useCallback(
    ({
      sender,
      text,
      timestamp = null,
      own = false,
      offline = false,
      system = false,
    }) => {
      if (!text) {
        return;
      }

      const currentGroup = groupNameRef.current;

      if (!currentGroup) {
        return;
      }

      setMessages((previous) => {
        const normalizedSender = sender || "Unknown";

        const normalizedText = String(text).trim();

        if (!normalizedText) {
          return previous;
        }

        const alreadyExists = previous.some(
          (message) =>
            message.sender === normalizedSender &&
            message.text === normalizedText &&
            message.timestamp === timestamp,
        );

        if (alreadyExists) {
          return previous;
        }

        return [
          ...previous,
          {
            id: Date.now() + Math.random().toString(36).slice(2),
            sender: normalizedSender,
            text: normalizedText,
            timestamp: timestamp || new Date().toISOString(),
            own: own || normalizedSender === usernameRef.current,
            offline,
            system,
          },
        ];
      });
    },
    [],
  );

  // =========================================================
  // PARSE GROUP MESSAGE
  // =========================================================

  const parseGroupMessage = useCallback(
    (message) => {
      if (!message) {
        return false;
      }

      const prefix = "GROUP [";

      if (!message.startsWith(prefix)) {
        return false;
      }

      const closingBracket = message.indexOf("]");

      if (closingBracket === -1) {
        return false;
      }

      const receivedGroup = message
        .substring(prefix.length, closingBracket)
        .trim();

      if (receivedGroup.toLowerCase() !== groupNameRef.current.toLowerCase()) {
        return false;
      }

      const content = message.substring(closingBracket + 1).trim();

      if (!content) {
        return true;
      }

      const colonIndex = content.indexOf(":");

      if (colonIndex === -1) {
        return true;
      }

      let sender = content.substring(0, colonIndex).trim();

      const text = content.substring(colonIndex + 1).trim();

      if (sender === "You") {
        sender = usernameRef.current;
      }

      addMessage({
        sender,
        text,
        own: sender === usernameRef.current,
      });

      return true;
    },
    [addMessage],
  );

  // =========================================================
  // PARSE OFFLINE GROUP MESSAGE
  // =========================================================

  const parseOfflineGroupMessage = useCallback(
    (message) => {
      const prefix = "OFFLINE_GROUP_MESSAGE:";

      if (!message.startsWith(prefix)) {
        return false;
      }

      const payload = message.substring(prefix.length);

      const firstColon = payload.indexOf(":");

      if (firstColon === -1) {
        return true;
      }

      const groupName = payload.substring(0, firstColon).trim();

      const remainingAfterGroup = payload.substring(firstColon + 1);

      const secondColon = remainingAfterGroup.indexOf(":");

      if (secondColon === -1) {
        return true;
      }

      const sender = remainingAfterGroup.substring(0, secondColon).trim();

      const remainingAfterSender = remainingAfterGroup.substring(
        secondColon + 1,
      );

      const thirdColon = remainingAfterSender.indexOf(":");

      if (thirdColon === -1) {
        return true;
      }

      const timestamp = remainingAfterSender.substring(0, thirdColon).trim();

      const text = remainingAfterSender.substring(thirdColon + 1).trim();

      if (groupName.toLowerCase() !== groupNameRef.current.toLowerCase()) {
        return true;
      }

      if (!sender || !text) {
        return true;
      }

      addMessage({
        sender,
        text,
        timestamp,
        offline: true,
        own: sender === usernameRef.current,
      });

      return true;
    },
    [addMessage],
  );

  // =========================================================
  // PARSE GROUP HISTORY
  // =========================================================

  const parseHistoryMessage = useCallback(
    (message) => {
      if (!message) {
        return false;
      }

      const match = message.match(/^\[([^\]]+)\]\s+([^:]+):\s*(.*)$/);

      if (!match) {
        return false;
      }

      const [, time, sender, text] = match;

      if (!sender || !text) {
        return false;
      }

      addMessage({
        sender: sender.trim(),
        text: text.trim(),
        timestamp: time.trim(),
        own: sender.trim() === usernameRef.current,
      });

      return true;
    },
    [addMessage],
  );

  // =========================================================
  // HANDLE BACKEND MESSAGE
  // =========================================================

  const handleBackendMessage = useCallback(
    (rawMessage) => {
      if (!rawMessage) {
        return;
      }

      const message = String(rawMessage).trim();

      if (!message) {
        return;
      }

      console.log("[GROUP CHAT] Backend:", message);

      // =====================================================
      // OFFLINE GROUP MESSAGE
      // =====================================================

      if (message.startsWith("OFFLINE_GROUP_MESSAGE:")) {
        parseOfflineGroupMessage(message);
        return;
      }

      // =====================================================
      // OFFLINE GROUP MESSAGE START
      // =====================================================

      if (message === "OFFLINE_GROUP_MESSAGES_START") {
        setSystemMessage("Loading your offline group messages...");
        return;
      }

      // =====================================================
      // OFFLINE GROUP MESSAGE END
      // =====================================================

      if (message === "OFFLINE_GROUP_MESSAGES_END") {
        setSystemMessage("Offline group messages delivered.");

        setTimeout(() => {
          setSystemMessage("");
        }, 3000);

        return;
      }

      // =====================================================
      // GROUP MESSAGE
      // =====================================================

      if (message.startsWith("GROUP [")) {
        parseGroupMessage(message);
        return;
      }

      // =====================================================
      // GROUP HISTORY HEADER
      // =====================================================

      if (message.startsWith("========== GROUP HISTORY:")) {
        return;
      }

      // =====================================================
      // GROUP HISTORY MESSAGE
      // =====================================================

      if (/^\[[^\]]+\]\s+[^:]+:/.test(message)) {
        if (loadingHistory || historyRequestedRef.current) {
          parseHistoryMessage(message);
          return;
        }
      }

      // =====================================================
      // GROUP HISTORY FOOTER
      // =====================================================

      if (message === "==========================================") {
        setLoadingHistory(false);
        return;
      }

      // =====================================================
      // SYSTEM
      // =====================================================

      if (message.startsWith("SYSTEM:")) {
        const systemText = message.substring("SYSTEM:".length).trim();

        setSystemMessage(systemText);

        setTimeout(() => {
          setSystemMessage((current) =>
            current === systemText ? "" : current,
          );
        }, 4000);

        return;
      }

      // =====================================================
      // ACCESS DENIED
      // =====================================================

      if (message.startsWith("ACCESS_DENIED:")) {
        setError(message.substring("ACCESS_DENIED:".length).trim());

        setTimeout(() => {
          setError("");
        }, 5000);

        return;
      }

      // =====================================================
      // ERROR
      // =====================================================

      if (message.startsWith("ERROR:")) {
        setError(message.substring("ERROR:".length).trim());

        return;
      }

      // =====================================================
      // LOGIN / SERVER EVENTS
      // =====================================================

      if (message.startsWith("LOGIN_SUCCESS:")) {
        setConnected(true);
        return;
      }

      if (message.startsWith("SERVER_CONNECTED")) {
        setConnected(true);
        return;
      }

      if (message.startsWith("SERVER_DISCONNECTED")) {
        setConnected(false);
        return;
      }

      // =====================================================
      // GROUP MEMBER RESPONSE
      // =====================================================

      if (message.startsWith("MEMBERS [")) {
        const colonIndex = message.indexOf(":");

        if (colonIndex !== -1) {
          const memberText = message.substring(colonIndex + 1).trim();

          if (memberText) {
            const members = memberText
              .split(",")
              .map((member) => member.trim())
              .filter(Boolean)
              .map((member) => {
                const [memberUsername, role = "MEMBER"] = member.split(":");

                return {
                  username: memberUsername.trim(),
                  role: role.trim().toUpperCase(),
                };
              });

            setGroupMembers(members);
            setMemberCount(members.length);
          } else {
            setGroupMembers([]);
            setMemberCount(0);
          }
        }

        return;
      }
    },
    [
      loadingHistory,
      parseGroupMessage,
      parseHistoryMessage,
      parseOfflineGroupMessage,
    ],
  );

  // =========================================================
  // LOAD GROUP HISTORY
  // =========================================================

  const loadGroupHistory = useCallback(() => {
    if (!selectedGroup) {
      return;
    }

    if (!isChatConnected()) {
      return;
    }

    if (historyRequestedRef.current) {
      return;
    }

    historyRequestedRef.current = true;

    setLoadingHistory(true);

    console.log("[GROUP CHAT] Loading history:", selectedGroup);

    sendChatCommand(`/grouphistory ${selectedGroup}`);

    setTimeout(() => {
      setLoadingHistory(false);
    }, 5000);
  }, [selectedGroup]);

  // =========================================================
  // CONNECTED HANDLER
  // =========================================================

  useEffect(() => {
    const unsubscribeConnected = onChatMessage((data) => {
      if (!data) {
        return;
      }

      if (typeof data === "object") {
        if (data.type === "message") {
          handleBackendMessage(data.message);
          return;
        }

        if (data.type === "private_message") {
          return;
        }

        if (data.type === "server_connected") {
          setConnected(true);
          return;
        }

        if (data.type === "server_disconnected") {
          setConnected(false);
          return;
        }

        if (data.type === "error" || data.type === "login_failed") {
          setError(data.message || "Connection error.");
          return;
        }

        return;
      }

      handleBackendMessage(data);
    });

    return () => {
      if (unsubscribeConnected) {
        unsubscribeConnected();
      }
    };
  }, [handleBackendMessage]);

  // =========================================================
  // CHAT CLOSED
  // =========================================================

  useEffect(() => {
    const unsubscribeClosed = onChatClosed(() => {
      setConnected(false);
    });

    return () => {
      if (unsubscribeClosed) {
        unsubscribeClosed();
      }
    };
  }, []);

  // =========================================================
  // LOAD HISTORY AFTER SOCKET IS READY
  // =========================================================

  useEffect(() => {
    if (!selectedGroup) {
      return;
    }

    if (!isChatConnected()) {
      return;
    }

    loadGroupHistory();
  }, [selectedGroup, connected, loadGroupHistory]);

  // =========================================================
  // REQUEST GROUP MEMBERS
  // =========================================================

  useEffect(() => {
    if (!selectedGroup) {
      return;
    }

    if (!isChatConnected()) {
      return;
    }

    sendChatCommand(`/members ${selectedGroup}`);
  }, [selectedGroup, connected]);

  // =========================================================
  // SEND MESSAGE
  // =========================================================

  const handleSendMessage = useCallback(() => {
    const text = messageInput.trim();

    if (!text) {
      return;
    }

    if (!selectedGroup) {
      setError("No group selected.");
      return;
    }

    if (!isChatConnected()) {
      setError("Not connected to the chat server.");
      return;
    }

    sendChatCommand(`/groupmsg ${selectedGroup} ${text}`);

    setMessageInput("");
  }, [messageInput, selectedGroup]);

  // =========================================================
  // ENTER KEY
  // =========================================================

  const handleKeyDown = useCallback(
    (event) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        handleSendMessage();
      }
    },
    [handleSendMessage],
  );

  // =========================================================
  // REFRESH HISTORY
  // =========================================================

  const refreshHistory = useCallback(() => {
    if (!selectedGroup) {
      return;
    }

    setMessages([]);

    historyRequestedRef.current = false;

    setLoadingHistory(true);

    sendChatCommand(`/grouphistory ${selectedGroup}`);
  }, [selectedGroup]);

  // =========================================================
  // BACK TO GROUP MANAGEMENT
  // =========================================================

  const handleBack = () => {
    navigate("/groups");
  };

  // =========================================================
  // LOGOUT
  // =========================================================

  const handleLogout = () => {
    try {
      if (isChatConnected()) {
        sendChatCommand("/exit");
      }
    } catch (error) {
      console.warn("[GROUP CHAT] Logout error:", error);
    }

    disconnectChat();

    sessionStorage.removeItem("cloudchat_username");

    sessionStorage.removeItem("cloudchat_selected_group");

    navigate("/login");
  };

  // =========================================================
  // GROUP INITIAL
  // =========================================================

  const groupInitial = selectedGroup
    ? selectedGroup.charAt(0).toUpperCase()
    : "G";

  // =========================================================
  // MESSAGE COUNT
  // =========================================================

  const messageCount = messages.length;

  // =========================================================
  // EMPTY GROUP
  // =========================================================

  if (!selectedGroup) {
    return (
      <div className="cloudchat-page">
        <div className="bg-glow" />

        <div className="auth-card">
          <div
            style={{
              textAlign: "center",
              padding: "40px",
            }}
          >
            <Users
              size={48}
              style={{
                marginBottom: "16px",
              }}
            />

            <h2>No Group Selected</h2>

            <p>Please select a group from Group Management.</p>

            <button
              onClick={() => navigate("/group")}
              style={{
                marginTop: "20px",
                padding: "12px 20px",
                borderRadius: "10px",
                border: "none",
                cursor: "pointer",
              }}
            >
              Back to Groups
            </button>
          </div>
        </div>
      </div>
    );
  }

  // =========================================================
  // MAIN UI
  // =========================================================

  return (
    <div
      style={{
        minHeight: "100vh",
        background:
          "radial-gradient(circle at top left, rgba(59,130,246,0.14), transparent 30%), #07111f",
        color: "#fff",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <div className="bg-glow" />

      {/* =====================================================
          TOP HEADER
      ===================================================== */}

      <header
        style={{
          height: "70px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "0 24px",
          borderBottom: "1px solid rgba(255,255,255,0.08)",
          background: "rgba(7,17,31,0.88)",
          backdropFilter: "blur(14px)",
          position: "sticky",
          top: 0,
          zIndex: 20,
        }}
      >
        {/* LEFT */}

        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "14px",
          }}
        >
          <button
            onClick={handleBack}
            title="Back to Groups"
            style={{
              width: "40px",
              height: "40px",
              borderRadius: "10px",
              border: "1px solid rgba(255,255,255,0.1)",
              background: "rgba(255,255,255,0.05)",
              color: "#fff",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <ArrowLeft size={20} />
          </button>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "10px",
            }}
          >
            <MessageCircle size={25} />

            <div>
              <div
                style={{
                  fontSize: "18px",
                  fontWeight: 700,
                }}
              >
                CloudChat
              </div>

              <div
                style={{
                  fontSize: "11px",
                  opacity: 0.55,
                }}
              >
                Distributed Messaging
              </div>
            </div>
          </div>
        </div>

        {/* RIGHT */}

        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "18px",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "7px",
              fontSize: "13px",
            }}
          >
            <span
              style={{
                width: "8px",
                height: "8px",
                borderRadius: "50%",
                background: connected ? "#22c55e" : "#ef4444",
                boxShadow: connected
                  ? "0 0 10px rgba(34,197,94,0.7)"
                  : "0 0 10px rgba(239,68,68,0.7)",
              }}
            />

            {connected ? "Connected" : "Disconnected"}
          </div>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              fontSize: "14px",
              opacity: 0.85,
            }}
          >
            <UserRound size={17} />

            {username}
          </div>

          <button
            type="button"
            onClick={() => {
              window.dispatchEvent(
                new CustomEvent("cloudchat:refresh-group-files"),
              );
            }}
            title="Refresh group files"
            aria-label="Refresh group files"
            style={{
              width: "38px",
              height: "38px",
              borderRadius: "9px",
              border: "1px solid rgba(255,255,255,0.1)",
              background: "rgba(255,255,255,0.05)",
              color: "#fff",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <RefreshCw size={17} />
          </button>

          <button
            onClick={handleLogout}
            title="Logout"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "7px",
              padding: "9px 13px",
              borderRadius: "9px",
              border: "1px solid rgba(255,255,255,0.1)",
              background: "rgba(255,255,255,0.05)",
              color: "#fff",
              cursor: "pointer",
            }}
          >
            <LogOut size={16} />
            Logout
          </button>
        </div>
      </header>

      {/* =====================================================
          WORKSPACE
      ===================================================== */}

      <main
        style={{
          flex: 1,
          display: "grid",
          gridTemplateColumns: "260px minmax(0, 1fr) 260px",
          minHeight: "calc(100vh - 70px)",
        }}
      >
        {/* ===================================================
            LEFT SIDEBAR
        =================================================== */}

        <aside
          style={{
            borderRight: "1px solid rgba(255,255,255,0.08)",
            background: "rgba(8,18,32,0.65)",
            padding: "22px 16px",
          }}
        >
          <div
            style={{
              fontSize: "12px",
              textTransform: "uppercase",
              letterSpacing: "1px",
              opacity: 0.5,
              marginBottom: "14px",
            }}
          >
            Group
          </div>

          <div
            style={{
              padding: "16px",
              borderRadius: "14px",
              background: "rgba(255,255,255,0.05)",
              border: "1px solid rgba(255,255,255,0.08)",
            }}
          >
            <div
              style={{
                width: "48px",
                height: "48px",
                borderRadius: "14px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                background: "rgba(59,130,246,0.15)",
                border: "1px solid rgba(59,130,246,0.25)",
                fontSize: "20px",
                fontWeight: 700,
                marginBottom: "12px",
              }}
            >
              {groupInitial}
            </div>

            <div
              style={{
                fontWeight: 700,
                fontSize: "15px",
                wordBreak: "break-word",
              }}
            >
              {selectedGroup}
            </div>

            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "7px",
                marginTop: "8px",
                fontSize: "12px",
                opacity: 0.6,
              }}
            >
              <Users size={14} />

              {memberCount !== null ? `${memberCount} members` : "Group"}
            </div>
          </div>

          <button
            onClick={() => {
              if (!isChatConnected()) {
                setError("Chat connection is not available.");
                return;
              }

              setShowMembers(true);
              sendChatCommand(`/members ${selectedGroup}`);
            }}
            style={{
              width: "100%",
              marginTop: "18px",
              padding: "11px 12px",
              borderRadius: "10px",
              border: "1px solid rgba(255,255,255,0.08)",
              background: "rgba(255,255,255,0.04)",
              color: "#fff",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "8px",
            }}
          >
            <Users size={16} />
            View Members
          </button>

          <button
            onClick={() => navigate("/group-files")}
            style={{
              width: "100%",
              marginTop: "12px",
              padding: "11px 12px",
              borderRadius: "10px",
              border: "1px solid rgba(255,255,255,0.08)",
              background: "rgba(255,255,255,0.04)",
              color: "#fff",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "8px",
            }}
          >
            <FolderOpen size={16} />
            Group Files
          </button>
        </aside>

        {/* ===================================================
            CENTER CHAT
        =================================================== */}

        <section
          style={{
            display: "flex",
            flexDirection: "column",
            minWidth: 0,
            background: "rgba(5,14,27,0.45)",
          }}
        >
          {/* CHAT HEADER */}

          <div
            style={{
              height: "72px",
              padding: "0 22px",
              borderBottom: "1px solid rgba(255,255,255,0.08)",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              background: "rgba(255,255,255,0.025)",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "12px",
              }}
            >
              <div
                style={{
                  width: "42px",
                  height: "42px",
                  borderRadius: "12px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  background: "rgba(59,130,246,0.15)",
                  border: "1px solid rgba(59,130,246,0.25)",
                  fontWeight: 700,
                }}
              >
                {groupInitial}
              </div>

              <div>
                <div
                  style={{
                    fontWeight: 700,
                    fontSize: "16px",
                  }}
                >
                  {selectedGroup}
                </div>

                <div
                  style={{
                    fontSize: "12px",
                    opacity: 0.55,
                    display: "flex",
                    alignItems: "center",
                    gap: "5px",
                  }}
                >
                  <Users size={12} />
                  Group Chat
                </div>
              </div>
            </div>

            <button
              onClick={refreshHistory}
              title="Refresh history"
              style={{
                width: "38px",
                height: "38px",
                borderRadius: "9px",
                border: "1px solid rgba(255,255,255,0.08)",
                background: "rgba(255,255,255,0.04)",
                color: "#fff",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <RefreshCw size={17} />
            </button>
          </div>

          {/* SYSTEM MESSAGE */}

          {systemMessage && (
            <div
              style={{
                padding: "9px 16px",
                textAlign: "center",
                fontSize: "12px",
                color: "rgba(255,255,255,0.65)",
                background: "rgba(59,130,246,0.06)",
                borderBottom: "1px solid rgba(255,255,255,0.05)",
              }}
            >
              {systemMessage}
            </div>
          )}

          {/* ERROR */}

          {error && (
            <div
              style={{
                padding: "9px 16px",
                textAlign: "center",
                fontSize: "12px",
                color: "#fca5a5",
                background: "rgba(239,68,68,0.08)",
                borderBottom: "1px solid rgba(239,68,68,0.15)",
              }}
            >
              {error}
            </div>
          )}

          {/* MESSAGES */}

          <div
            style={{
              flex: 1,
              overflowY: "auto",
              padding: "22px",
              display: "flex",
              flexDirection: "column",
              gap: "12px",
            }}
          >
            {loadingHistory && messages.length === 0 && (
              <div
                style={{
                  textAlign: "center",
                  opacity: 0.5,
                  fontSize: "13px",
                  padding: "30px",
                }}
              >
                Loading group history...
              </div>
            )}

            {!loadingHistory && messages.length === 0 && (
              <div
                style={{
                  flex: 1,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  textAlign: "center",
                  opacity: 0.45,
                  padding: "40px",
                }}
              >
                <div>
                  <MessageCircle
                    size={42}
                    style={{
                      marginBottom: "12px",
                    }}
                  />

                  <div
                    style={{
                      fontWeight: 600,
                      marginBottom: "5px",
                    }}
                  >
                    No messages yet
                  </div>

                  <div
                    style={{
                      fontSize: "12px",
                    }}
                  >
                    Start the conversation with your group.
                  </div>
                </div>
              </div>
            )}

            {messages.map((message) => {
              const isOwn = message.own || message.sender === username;

              return (
                <div
                  key={message.id}
                  style={{
                    display: "flex",
                    justifyContent: isOwn ? "flex-end" : "flex-start",
                  }}
                >
                  <div
                    style={{
                      maxWidth: "75%",
                      minWidth: "100px",
                    }}
                  >
                    {!isOwn && (
                      <div
                        style={{
                          fontSize: "11px",
                          fontWeight: 700,
                          opacity: 0.65,
                          marginBottom: "5px",
                          paddingLeft: "5px",
                        }}
                      >
                        {message.sender}
                      </div>
                    )}

                    <div
                      style={{
                        padding: "10px 13px",
                        borderRadius: isOwn
                          ? "14px 14px 4px 14px"
                          : "14px 14px 14px 4px",
                        background: isOwn
                          ? "rgba(59,130,246,0.18)"
                          : "rgba(255,255,255,0.06)",
                        border: isOwn
                          ? "1px solid rgba(59,130,246,0.25)"
                          : "1px solid rgba(255,255,255,0.07)",
                      }}
                    >
                      <div
                        style={{
                          fontSize: "14px",
                          lineHeight: 1.5,
                          whiteSpace: "pre-wrap",
                          overflowWrap: "anywhere",
                        }}
                      >
                        {message.text}
                      </div>

                      <div
                        style={{
                          display: "flex",
                          justifyContent: "flex-end",
                          gap: "6px",
                          marginTop: "5px",
                          fontSize: "10px",
                          opacity: 0.45,
                        }}
                      >
                        {message.offline && <span>Offline</span>}

                        <span>{formatMessageTime(message.timestamp)}</span>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}

            <div ref={messagesEndRef} />
          </div>

          {/* =================================================
              COMPOSER
              ================================================= */}

          <div
            style={{
              padding: "14px 18px",
              borderTop: "1px solid rgba(255,255,255,0.08)",
              background: "rgba(255,255,255,0.025)",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "flex-end",
                gap: "10px",
                padding: "7px",
                borderRadius: "13px",
                background: "rgba(255,255,255,0.045)",
                border: "1px solid rgba(255,255,255,0.08)",
              }}
            >
              <textarea
                value={messageInput}
                onChange={(event) => setMessageInput(event.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={
                  connected
                    ? `Message ${selectedGroup}...`
                    : "Waiting for connection..."
                }
                disabled={!connected}
                rows={1}
                style={{
                  flex: 1,
                  resize: "none",
                  border: "none",
                  outline: "none",
                  background: "transparent",
                  color: "#fff",
                  padding: "9px 8px",
                  fontSize: "14px",
                  fontFamily: "inherit",
                  minHeight: "22px",
                  maxHeight: "100px",
                }}
              />

              <button
                onClick={handleSendMessage}
                disabled={!connected || !messageInput.trim()}
                title="Send"
                style={{
                  width: "40px",
                  height: "40px",
                  borderRadius: "10px",
                  border: "none",
                  background:
                    connected && messageInput.trim()
                      ? "#2563eb"
                      : "rgba(255,255,255,0.08)",
                  color: "#fff",
                  cursor:
                    connected && messageInput.trim()
                      ? "pointer"
                      : "not-allowed",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <Send size={17} />
              </button>
            </div>

            <div
              style={{
                marginTop: "6px",
                paddingLeft: "5px",
                fontSize: "10px",
                opacity: 0.35,
              }}
            >
              Press Enter to send
            </div>
          </div>
        </section>

        {/* ===================================================
            RIGHT SIDEBAR
        =================================================== */}

        <aside
          style={{
            borderLeft: "1px solid rgba(255,255,255,0.08)",
            background: "rgba(8,18,32,0.65)",
            padding: "22px 18px",
          }}
        >
          <div
            style={{
              fontSize: "12px",
              textTransform: "uppercase",
              letterSpacing: "1px",
              opacity: 0.5,
              marginBottom: "18px",
            }}
          >
            Group Details
          </div>

          <div
            style={{
              textAlign: "center",
              padding: "15px 0 22px",
              borderBottom: "1px solid rgba(255,255,255,0.07)",
            }}
          >
            <div
              style={{
                width: "68px",
                height: "68px",
                borderRadius: "20px",
                margin: "0 auto 12px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "28px",
                fontWeight: 700,
                background: "rgba(59,130,246,0.15)",
                border: "1px solid rgba(59,130,246,0.25)",
              }}
            >
              {groupInitial}
            </div>

            <div
              style={{
                fontSize: "16px",
                fontWeight: 700,
                wordBreak: "break-word",
              }}
            >
              {selectedGroup}
            </div>

            <div
              style={{
                marginTop: "6px",
                fontSize: "12px",
                opacity: 0.5,
              }}
            >
              Group conversation
            </div>
          </div>

          <div
            style={{
              paddingTop: "20px",
              display: "flex",
              flexDirection: "column",
              gap: "12px",
            }}
          >
            <InfoRow
              icon={<Users size={17} />}
              label="Members"
              value={memberCount !== null ? memberCount : "—"}
            />

            <InfoRow
              icon={<MessageCircle size={17} />}
              label="Messages"
              value={messageCount}
            />

            <InfoRow
              icon={<Server size={17} />}
              label="Connection"
              value={connected ? "Online" : "Offline"}
            />

            <InfoRow
              icon={<UserRound size={17} />}
              label="You"
              value={username}
            />
          </div>

          <div
            style={{
              marginTop: "25px",
              padding: "13px",
              borderRadius: "11px",
              background: "rgba(59,130,246,0.06)",
              border: "1px solid rgba(59,130,246,0.1)",
              fontSize: "11px",
              lineHeight: 1.6,
              opacity: 0.65,
            }}
          >
            Group messages are synchronized across CloudChat servers. Messages
            sent while members are offline are delivered when they reconnect.
          </div>
        </aside>
      </main>

      {/* =====================================================
          VIEW MEMBERS MODAL
      ===================================================== */}

      {showMembers && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 100,
            background: "rgba(0,0,0,0.65)",
            backdropFilter: "blur(6px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "20px",
          }}
          onClick={() => setShowMembers(false)}
        >
          <div
            style={{
              width: "100%",
              maxWidth: "420px",
              maxHeight: "80vh",
              overflow: "hidden",
              borderRadius: "18px",
              border: "1px solid rgba(255,255,255,0.1)",
              background: "#0b1728",
              boxShadow: "0 25px 70px rgba(0,0,0,0.45)",
            }}
            onClick={(event) => event.stopPropagation()}
          >
            {/* MODAL HEADER */}

            <div
              style={{
                padding: "20px",
                borderBottom: "1px solid rgba(255,255,255,0.08)",
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
              }}
            >
              <div>
                <div
                  style={{
                    fontSize: "16px",
                    fontWeight: 700,
                  }}
                >
                  Group Members
                </div>

                <div
                  style={{
                    marginTop: "4px",
                    fontSize: "12px",
                    opacity: 0.5,
                  }}
                >
                  {selectedGroup}
                </div>
              </div>

              <button
                onClick={() => setShowMembers(false)}
                style={{
                  width: "34px",
                  height: "34px",
                  borderRadius: "9px",
                  border: "1px solid rgba(255,255,255,0.08)",
                  background: "rgba(255,255,255,0.05)",
                  color: "#fff",
                  cursor: "pointer",
                  fontSize: "18px",
                }}
              >
                ×
              </button>
            </div>

            {/* MEMBER COUNT */}

            <div
              style={{
                padding: "12px 20px",
                borderBottom: "1px solid rgba(255,255,255,0.06)",
                fontSize: "12px",
                opacity: 0.55,
              }}
            >
              {groupMembers.length}{" "}
              {groupMembers.length === 1 ? "member" : "members"}
            </div>

            {/* MEMBER LIST */}

            <div
              style={{
                padding: "12px",
                maxHeight: "55vh",
                overflowY: "auto",
              }}
            >
              {groupMembers.length === 0 ? (
                <div
                  style={{
                    padding: "35px 20px",
                    textAlign: "center",
                    fontSize: "13px",
                    opacity: 0.5,
                  }}
                >
                  No members found.
                </div>
              ) : (
                groupMembers.map((member) => {
                  const isOwner = member.role === "OWNER";
                  const isCurrentUser = member.username === username;

                  return (
                    <div
                      key={member.username}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "12px",
                        padding: "12px",
                        borderRadius: "12px",
                        background: "rgba(255,255,255,0.035)",
                        border: "1px solid rgba(255,255,255,0.06)",
                        marginBottom: "8px",
                      }}
                    >
                      {/* AVATAR */}

                      <div
                        style={{
                          width: "38px",
                          height: "38px",
                          borderRadius: "11px",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          background: isOwner
                            ? "rgba(234,179,8,0.14)"
                            : "rgba(59,130,246,0.14)",
                          border: isOwner
                            ? "1px solid rgba(234,179,8,0.25)"
                            : "1px solid rgba(59,130,246,0.2)",
                          fontSize: "14px",
                          fontWeight: 700,
                        }}
                      >
                        {member.username.charAt(0).toUpperCase()}
                      </div>

                      {/* USER INFO */}

                      <div
                        style={{
                          flex: 1,
                          minWidth: 0,
                        }}
                      >
                        <div
                          style={{
                            fontSize: "13px",
                            fontWeight: 600,
                            wordBreak: "break-word",
                          }}
                        >
                          {member.username}

                          {isCurrentUser && (
                            <span
                              style={{
                                marginLeft: "7px",
                                fontSize: "10px",
                                opacity: 0.45,
                              }}
                            >
                              You
                            </span>
                          )}
                        </div>

                        <div
                          style={{
                            marginTop: "3px",
                            fontSize: "10px",
                            opacity: 0.45,
                          }}
                        >
                          {isOwner ? "Group Owner" : "Member"}
                        </div>
                      </div>

                      {/* OWNER BADGE */}

                      {isOwner && (
                        <div
                          style={{
                            padding: "5px 8px",
                            borderRadius: "7px",
                            background: "rgba(234,179,8,0.1)",
                            border: "1px solid rgba(234,179,8,0.2)",
                            color: "#facc15",
                            fontSize: "10px",
                            fontWeight: 600,
                          }}
                        >
                          Owner
                        </div>
                      )}
                    </div>
                  );
                })
              )}
            </div>

            {/* FOOTER */}

            <div
              style={{
                padding: "14px 20px",
                borderTop: "1px solid rgba(255,255,255,0.08)",
                display: "flex",
                justifyContent: "flex-end",
              }}
            >
              <button
                onClick={() => setShowMembers(false)}
                style={{
                  padding: "9px 16px",
                  borderRadius: "9px",
                  border: "1px solid rgba(255,255,255,0.08)",
                  background: "rgba(255,255,255,0.05)",
                  color: "#fff",
                  cursor: "pointer",
                  fontSize: "12px",
                }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// =========================================================
// INFO ROW
// =========================================================

function InfoRow({ icon, label, value }) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "10px",
        padding: "10px 0",
      }}
    >
      <div
        style={{
          width: "32px",
          height: "32px",
          borderRadius: "9px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "rgba(255,255,255,0.05)",
          opacity: 0.75,
        }}
      >
        {icon}
      </div>

      <div
        style={{
          flex: 1,
        }}
      >
        <div
          style={{
            fontSize: "10px",
            opacity: 0.45,
            textTransform: "uppercase",
          }}
        >
          {label}
        </div>

        <div
          style={{
            fontSize: "13px",
            marginTop: "2px",
            wordBreak: "break-word",
          }}
        >
          {value}
        </div>
      </div>
    </div>
  );
}

// =========================================================
// FORMAT MESSAGE TIME
// =========================================================

function formatMessageTime(timestamp) {
  if (!timestamp) {
    return "";
  }

  try {
    if (/^\d{2}:\d{2}/.test(timestamp)) {
      return timestamp.substring(0, 5);
    }

    const date = new Date(timestamp);

    if (!Number.isNaN(date.getTime())) {
      return date.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
    }

    return timestamp;
  } catch {
    return timestamp;
  }
}
