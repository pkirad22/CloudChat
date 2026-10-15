import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import {
  ArrowLeft,
  LogOut,
  Plus,
  RefreshCw,
  Search,
  Users,
  UserPlus,
  MessageCircle,
  X,
  Settings,
  Crown,
  UserMinus,
  Shield,
  Copy,
  Check,
  UserRound,
} from "lucide-react";

import {
  sendChatCommand,
  onChatMessage,
  isChatConnected,
} from "../services/chatSocket";

const Groups = () => {
  const navigate = useNavigate();

  // =========================================================
  // USER
  // =========================================================

  const username = sessionStorage.getItem("cloudchat_username") || "";

  // =========================================================
  // MAIN GROUP STATE
  // =========================================================

  const [groups, setGroups] = useState([]);
  const [groupSearchResults, setGroupSearchResults] = useState([]);
  const [searchingGroups, setSearchingGroups] = useState(false);

  const [search, setSearch] = useState("");

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  // =========================================================
  // GROUP INFORMATION
  // =========================================================

  const [groupInfo, setGroupInfo] = useState({});

  // =========================================================
  // CREATE GROUP
  // =========================================================

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newGroupName, setNewGroupName] = useState("");
  const [createAccessMode, setCreateAccessMode] = useState("REQUEST");

  // =========================================================
  // JOIN GROUP
  // =========================================================

  const [showJoinModal, setShowJoinModal] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [joinCode, setJoinCode] = useState("");

  // =========================================================
  // GROUP MANAGEMENT
  // =========================================================

  const [showManagementModal, setShowManagementModal] = useState(false);

  const [managementGroup, setManagementGroup] = useState("");

  const [managementMembers, setManagementMembers] = useState([]);

  const [pendingRequests, setPendingRequests] = useState([]);
  const [pendingRequestsLoading, setPendingRequestsLoading] = useState(false);

  const [managementLoading, setManagementLoading] = useState(false);

  const [managementActionLoading, setManagementActionLoading] = useState(false);

  const [copiedCode, setCopiedCode] = useState(false);

  const [managementCode, setManagementCode] = useState("");

  const [selectedAccessMode, setSelectedAccessMode] = useState("REQUEST");

  // =========================================================
  // CONFIRMATION MODAL
  // =========================================================

  const [confirmation, setConfirmation] = useState({
    open: false,
    type: "",
    title: "",
    message: "",
    memberUsername: "",
    groupName: "",
  });

  // =========================================================
  // LOAD USER GROUPS
  // =========================================================

  const loadGroups = useCallback(() => {
    if (!isChatConnected()) {
      setError("Chat connection is not available.");
      setLoading(false);
      return;
    }

    setError("");
    sendChatCommand("/groups");
  }, []);
  // =========================================================
  // SEARCH CHANGE HANDLER
  // =========================================================

  const handleSearchChange = (event) => {
    const value = event.target.value;

    setSearch(value);
    setGroupSearchResults([]);

    if (value.trim()) {
      setSearchingGroups(true);
    } else {
      setSearchingGroups(false);
    }
  };

  // =========================================================
  // INITIAL GROUP REQUEST
  // =========================================================

  useEffect(() => {
    sendChatCommand("/groups");
  }, []);

  // =========================================================
  // SEARCH GROUPS
  // =========================================================

  useEffect(() => {
    const query = search.trim();

    if (!query) {
      return;
    }

    const timer = window.setTimeout(() => {
      if (isChatConnected()) {
        sendChatCommand(`/searchgroups ${query}`);
      }
    }, 350);

    return () => {
      window.clearTimeout(timer);
    };
  }, [search]);

  // =========================================================
  // PARSE MEMBERS
  // =========================================================
  //
  // Supports:
  //
  // user1,user2,user3
  //
  // user1:OWNER,user2:MEMBER
  //
  // user1:OWNER,user2:MEMBER,user3:MEMBER
  //
  // =========================================================

  const parseMembers = useCallback(
    (memberData) => {
      if (!memberData) {
        return [];
      }

      const cleanedData = memberData.trim();

      if (!cleanedData) {
        return [];
      }

      // Backend /members currently returns:
      //
      // MEMBERS [CloudTest2]: Rahul Pranav
      //
      // Therefore split whitespace for this format.
      if (!cleanedData.includes(",") && !cleanedData.includes(":")) {
        return cleanedData
          .split(/\s+/)
          .map((name) => name.trim())
          .filter(Boolean)
          .map((name) => ({
            username: name,
            isOwner: name === username,
          }));
      }

      const members = [];

      cleanedData.split(",").forEach((entry) => {
        const memberParts = entry.split(":");

        const memberName = memberParts[0]?.trim();

        if (!memberName) {
          return;
        }

        const role = memberParts[1]?.trim()?.toUpperCase() || "MEMBER";

        members.push({
          username: memberName,
          isOwner: role === "OWNER" || role === "OWNER1" || role === "TRUE",
        });
      });

      return members;
    },
    [username],
  );

  // =========================================================
  // LOAD GROUP MANAGEMENT DATA
  // =========================================================

  const loadManagementData = useCallback((groupName) => {
    if (!groupName) return;

    if (!isChatConnected()) {
      setError("Chat connection is not available.");
      return;
    }

    setManagementMembers([]);
    setManagementCode("");
    setSelectedAccessMode("REQUEST");
    setPendingRequests([]);
    setPendingRequestsLoading(true);

    sendChatCommand(`/members ${groupName}`);
    sendChatCommand(`/groupcode ${groupName}`);
    sendChatCommand(`/groupinfo ${groupName}`);
    sendChatCommand(`/requests ${groupName}`);

    window.setTimeout(() => setManagementLoading(false), 1200);
  }, []);

  // =========================================================
  // SOCKET MESSAGE HANDLER
  // =========================================================

  useEffect(() => {
    const unsubscribe = onChatMessage((data) => {
      if (!data) {
        return;
      }

      // -----------------------------------------------------
      // Extract message text
      // -----------------------------------------------------

      const messageText =
        typeof data === "string"
          ? data
          : typeof data.message === "string"
            ? data.message
            : "";

      if (!messageText) {
        return;
      }

      // =====================================================
      // GROUP SEARCH RESPONSE
      // =====================================================

      if (messageText.startsWith("GROUP_SEARCH:")) {
        const searchData = messageText.substring("GROUP_SEARCH:".length).trim();

        if (!searchData) {
          setGroupSearchResults([]);
          setSearchingGroups(false);
          return;
        }

        const results = [
          ...new Set(
            searchData
              .split(",")
              .map((groupName) => groupName.trim())
              .filter(Boolean),
          ),
        ];

        setGroupSearchResults(results);
        setSearchingGroups(false);

        // -------------------------------------------------------
        // Get access mode and membership information for every
        // search result.
        // -------------------------------------------------------

        results.forEach((groupName) => {
          sendChatCommand(`/groupinfo ${groupName}`);
        });

        return;
      }

      // =====================================================
      // GROUP LIST RESPONSE
      // =====================================================

      if (messageText.startsWith("GROUPS:")) {
        const groupData = messageText.substring("GROUPS:".length).trim();

        if (!groupData) {
          setGroups([]);
          setLoading(false);
          setRefreshing(false);
          return;
        }

        const userGroups = [
          ...new Set(
            groupData
              .split(",")
              .map((groupName) => groupName.trim())
              .filter(Boolean),
          ),
        ];

        setGroups(userGroups);

        setLoading(false);
        setRefreshing(false);

        // Ask information for every group.
        userGroups.forEach((groupName) => {
          sendChatCommand(`/groupinfo ${groupName}`);
        });

        return;
      }

      // =====================================================
      // GROUP INFO RESPONSE
      // =====================================================
      //
      // Owner:
      //
      // GROUP_INFO:CloudTest2:CODE:8B03-8ACE:OWNER:1:MEMBERS:1
      //
      // Member:
      //
      // GROUP_INFO:CloudTest2:CODE::OWNER:0:MEMBER:1:MEMBERS:2
      //
      // =====================================================

      if (messageText.startsWith("GROUP_INFO:")) {
        const infoParts = messageText.split(":");

        if (infoParts.length >= 4) {
          const groupName = infoParts[1]?.trim();

          const accessMode = infoParts[2]?.trim()?.toUpperCase() || "REQUEST";

          const code = infoParts[3]?.trim() || "";

          let isOwner = false;
          let isMember = false;
          let memberCount = 0;

          for (let index = 4; index < infoParts.length; index++) {
            const currentPart = infoParts[index]?.trim()?.toUpperCase();

            if (currentPart === "OWNER") {
              const ownerValue = infoParts[index + 1]?.trim();

              isOwner =
                ownerValue === "1" || ownerValue?.toUpperCase() === "TRUE";
            }

            if (currentPart === "MEMBER") {
              const memberValue = infoParts[index + 1]?.trim();

              isMember =
                memberValue === "1" || memberValue?.toUpperCase() === "TRUE";
            }

            if (currentPart === "MEMBERS") {
              const countValue = infoParts[index + 1]?.trim();

              const parsedCount = Number.parseInt(countValue, 10);

              if (!Number.isNaN(parsedCount)) {
                memberCount = parsedCount;
              }
            }
          }

          if (groupName) {
            setGroupInfo((previous) => ({
              ...previous,
              [groupName]: {
                accessMode,
                joinCode: code,
                isOwner,
                isMember,
                memberCount,
              },
            }));

            if (showManagementModal && managementGroup === groupName) {
              setSelectedAccessMode(accessMode);
            }
          }
        }

        return;
      }

      // =====================================================
      // GROUP CODE RESPONSE
      // =====================================================

      if (messageText.startsWith("GROUP_CODE:")) {
        const codeParts = messageText.split(":");

        if (codeParts.length >= 3) {
          const groupName = codeParts[1]?.trim();
          const code = codeParts[2]?.trim();

          if (groupName && code) {
            setManagementCode(code);

            setGroupInfo((previous) => ({
              ...previous,
              [groupName]: {
                ...(previous[groupName] || {}),
                joinCode: code,
              },
            }));
          }
        }

        return;
      }

      // =====================================================
      // GROUP MEMBERS RESPONSE
      // =====================================================
      //
      // Backend currently sends:
      //
      // MEMBERS [CloudTest2]: Rahul Pranav
      //
      // Also supports:
      //
      // GROUP_MEMBERS:CloudTest2:Rahul,Pranav
      //
      // MEMBERS:CloudTest2:Rahul,Pranav
      //
      // =====================================================

      if (messageText.startsWith("PENDING REQUESTS [")) {
        const closingBracket = messageText.indexOf("]:");

        if (closingBracket !== -1) {
          const groupName = messageText
            .substring("PENDING REQUESTS [".length, closingBracket)
            .trim();

          const requestData = messageText.substring(closingBracket + 2).trim();

          const requests = requestData
            ? requestData
                .split(/\s+/)
                .map((name) => name.trim())
                .filter(Boolean)
            : [];

          if (showManagementModal && managementGroup === groupName) {
            setPendingRequests(requests);
            setPendingRequestsLoading(false);
          }
        }

        return;
      }

      if (messageText.startsWith("MEMBERS [")) {
        const closingBracket = messageText.indexOf("]:");

        if (closingBracket !== -1) {
          const groupName = messageText
            .substring("MEMBERS [".length, closingBracket)
            .trim();

          const memberList = messageText.substring(closingBracket + 2).trim();

          const parsedMembers = parseMembers(memberList);

          if (showManagementModal && managementGroup === groupName) {
            setManagementMembers(parsedMembers);
            setManagementLoading(false);
          }
        }

        return;
      }

      if (
        messageText.startsWith("GROUP_MEMBERS:") ||
        messageText.startsWith("MEMBERS:")
      ) {
        const prefix = messageText.startsWith("GROUP_MEMBERS:")
          ? "GROUP_MEMBERS:"
          : "MEMBERS:";

        const memberData = messageText.substring(prefix.length);

        const firstColon = memberData.indexOf(":");

        if (firstColon !== -1) {
          const groupName = memberData.substring(0, firstColon).trim();

          const memberList = memberData.substring(firstColon + 1).trim();

          const parsedMembers = parseMembers(memberList);

          if (showManagementModal && managementGroup === groupName) {
            setManagementMembers(parsedMembers);
            setManagementLoading(false);
          }
        }

        return;
      }

      // =====================================================
      // SYSTEM MESSAGE
      // =====================================================

      if (messageText.startsWith("SYSTEM:")) {
        if (messageText.includes("No pending join requests for group")) {
          setPendingRequests([]);
          setPendingRequestsLoading(false);
        }
        const systemMessage = messageText.substring("SYSTEM:".length).trim();

        if (systemMessage) {
          setMessage(systemMessage);
        }

        // Refresh groups after operations such as:
        // create, join, leave, remove, etc.
        if (
          systemMessage.toLowerCase().includes("successfully") ||
          systemMessage.toLowerCase().includes("joined") ||
          systemMessage.toLowerCase().includes("left") ||
          systemMessage.toLowerCase().includes("removed") ||
          systemMessage.toLowerCase().includes("created")
        ) {
          window.setTimeout(() => {
            loadGroups();
          }, 300);
        }

        return;
      }

      // =====================================================
      // ERROR MESSAGE
      // =====================================================

      if (messageText.startsWith("ERROR:")) {
        setError(messageText.substring("ERROR:".length).trim());

        setLoading(false);
        setRefreshing(false);
        setManagementLoading(false);

        return;
      }

      // =====================================================
      // GENERIC SYSTEM MESSAGE
      // =====================================================

      if (
        messageText.toLowerCase().includes("not available") ||
        messageText.toLowerCase().includes("does not exist") ||
        messageText.toLowerCase().includes("cannot") ||
        messageText.toLowerCase().includes("invalid")
      ) {
        setError(messageText);
        return;
      }
    });

    return () => {
      if (typeof unsubscribe === "function") {
        unsubscribe();
      }
    };
  }, [
    loadGroups,
    parseMembers,
    loadManagementData,
    showManagementModal,
    managementGroup,
  ]);

  // =========================================================
  // SEARCH GROUPS
  // =========================================================

  // =========================================================
  // REFRESH
  // =========================================================

  const handleRefresh = () => {
    if (!isChatConnected()) {
      setError("Chat connection is not available.");
      return;
    }

    setRefreshing(true);
    setError("");
    setMessage("");

    loadGroups();
  };

  // =========================================================
  // CREATE GROUP
  // =========================================================

  const handleCreateGroup = () => {
    const groupName = newGroupName.trim();

    if (!groupName) {
      setError("Please enter a group name.");
      return;
    }

    if (!isChatConnected()) {
      setError("Chat connection is not available.");
      return;
    }

    setError("");
    setMessage("");

    sendChatCommand(`/create ${groupName}`);

    // Access mode is configured separately after creation.
    window.setTimeout(() => {
      sendChatCommand(`/setaccess ${groupName} ${createAccessMode}`);
    }, 300);

    setNewGroupName("");
    setCreateAccessMode("REQUEST");
    setShowCreateModal(false);

    window.setTimeout(() => {
      loadGroups();
    }, 700);
  };

  // =========================================================
  // OPEN JOIN MODAL
  // =========================================================

  const openJoinModal = (groupName) => {
    const info = groupInfo[groupName] || {};

    setSelectedGroup({
      name: groupName,
      accessMode: info.accessMode || "REQUEST",
    });

    setJoinCode("");
    setError("");
    setMessage("");

    setShowJoinModal(true);
  };

  // =========================================================
  // JOIN GROUP
  // =========================================================

  const handleJoinGroup = () => {
    if (!selectedGroup?.name) {
      return;
    }

    if (!isChatConnected()) {
      setError("Chat connection is not available.");
      return;
    }

    const groupName = selectedGroup.name;
    const accessMode = selectedGroup.accessMode?.toUpperCase() || "REQUEST";

    setError("");
    setMessage("");

    // OPEN
    if (accessMode === "OPEN") {
      sendChatCommand(`/join ${groupName}`);
    }

    // CODE
    else if (accessMode === "CODE") {
      const code = joinCode.trim();

      if (!code) {
        setError("Please enter the group join code.");
        return;
      }

      sendChatCommand(`/join ${groupName} ${code}`);
    }

    // REQUEST
    else {
      sendChatCommand(`/requestjoin ${groupName}`);
    }

    setShowJoinModal(false);

    window.setTimeout(() => {
      loadGroups();
    }, 700);
  };

  // =========================================================
  // OPEN GROUP MANAGEMENT
  // =========================================================

  const openGroupManagement = (groupName) => {
    if (!groupName) {
      return;
    }

    const info = groupInfo[groupName];

    if (!info?.isOwner) {
      setError("Only the group owner can access group management.");
      return;
    }

    setManagementGroup(groupName);
    setManagementMembers([]);
    setManagementCode("");
    setCopiedCode(false);

    setError("");
    setMessage("");

    setShowManagementModal(true);

    loadManagementData(groupName);
  };

  // =========================================================
  // CLOSE MANAGEMENT MODAL
  // =========================================================

  const closeManagementModal = () => {
    setShowManagementModal(false);
    setManagementGroup("");
    setManagementMembers([]);
    setManagementCode("");
    setCopiedCode(false);
  };

  // =========================================================
  // COPY GROUP CODE
  // =========================================================

  const handleCopyCode = async () => {
    if (!managementCode) {
      return;
    }

    try {
      await navigator.clipboard.writeText(managementCode);

      setCopiedCode(true);

      window.setTimeout(() => {
        setCopiedCode(false);
      }, 1800);
    } catch (copyError) {
      console.error("Failed to copy group code:", copyError);

      setError("Unable to copy the group code.");
    }
  };

  const handleSaveAccessMode = () => {
    if (!managementGroup) return;

    if (!isChatConnected()) {
      setError("Chat connection is not available.");
      return;
    }

    setError("");
    setMessage("");

    sendChatCommand(`/setaccess ${managementGroup} ${selectedAccessMode}`);
  };

  // =========================================================
  // REMOVE MEMBER
  // =========================================================
  // =========================================================
  // OPEN CONFIRMATION MODAL
  // =========================================================

  const openConfirmation = ({
    type,
    title,
    message,
    memberUsername = "",
    groupName = "",
  }) => {
    setConfirmation({
      open: true,
      type,
      title,
      message,
      memberUsername,
      groupName,
    });
  };

  // =========================================================
  // CLOSE CONFIRMATION MODAL
  // =========================================================

  const closeConfirmation = () => {
    setConfirmation({
      open: false,
      type: "",
      title: "",
      message: "",
      memberUsername: "",
      groupName: "",
    });
  };

  // =========================================================
  // CONFIRM ACTION
  // =========================================================

  const handleConfirmAction = () => {
    const { type, memberUsername, groupName } = confirmation;

    if (!isChatConnected()) {
      setError("Chat connection is not available.");
      closeConfirmation();
      return;
    }

    // -------------------------------------------------------
    // REMOVE MEMBER
    // -------------------------------------------------------

    if (type === "REMOVE_MEMBER") {
      setManagementActionLoading(true);

      sendChatCommand(`/removemember ${groupName} ${memberUsername}`);

      closeConfirmation();

      window.setTimeout(() => {
        loadManagementData(groupName);
        setManagementActionLoading(false);
      }, 500);

      return;
    }

    // -------------------------------------------------------
    // MAKE OWNER
    // -------------------------------------------------------

    if (type === "MAKE_OWNER") {
      setManagementActionLoading(true);

      sendChatCommand(`/promote ${groupName} ${memberUsername}`);

      closeConfirmation();

      window.setTimeout(() => {
        loadManagementData(groupName);
        setManagementActionLoading(false);
      }, 500);

      return;
    }

    // -------------------------------------------------------
    // DEMOTE OWNER
    // -------------------------------------------------------

    if (type === "DEMOTE_OWNER") {
      setManagementActionLoading(true);

      sendChatCommand(`/demote ${groupName} ${memberUsername}`);

      closeConfirmation();

      window.setTimeout(() => {
        loadManagementData(groupName);
        setManagementActionLoading(false);
      }, 500);

      return;
    }

    // -------------------------------------------------------
    // LEAVE GROUP
    // -------------------------------------------------------

    if (type === "LEAVE_GROUP") {
      setError("");
      setMessage("");

      sendChatCommand(`/leave ${groupName}`);

      closeConfirmation();

      window.setTimeout(() => {
        loadGroups();
      }, 500);
    }
  };

  // =========================================================
  // REMOVE MEMBER
  // =========================================================

  const handleRemoveMember = (memberUsername) => {
    if (!managementGroup || !memberUsername) {
      return;
    }

    if (memberUsername === username) {
      return;
    }

    openConfirmation({
      type: "REMOVE_MEMBER",
      title: "Remove Member",
      message: `Remove ${memberUsername} from ${managementGroup}?`,
      memberUsername,
      groupName: managementGroup,
    });
  };

  // =========================================================
  // MAKE OWNER
  // =========================================================

  // =========================================================
  // MAKE OWNER
  // =========================================================

  const handleMakeOwner = (memberUsername) => {
    if (!managementGroup || !memberUsername) {
      return;
    }

    if (memberUsername === username) {
      return;
    }

    openConfirmation({
      type: "MAKE_OWNER",
      title: "Make Owner",
      message: `Make ${memberUsername} an owner of ${managementGroup}?`,
      memberUsername,
      groupName: managementGroup,
    });
  };

  // =========================================================
  // DEMOTE OWNER
  // =========================================================

  const handleDemoteOwner = (memberUsername) => {
    if (!managementGroup || !memberUsername) {
      return;
    }

    if (memberUsername === username) {
      return;
    }

    openConfirmation({
      type: "DEMOTE_OWNER",
      title: "Demote Owner",
      message: `Remove owner privileges from ${memberUsername}?`,
      memberUsername,
      groupName: managementGroup,
    });
  };

  // =========================================================
  // LEAVE GROUP
  // =========================================================

  const handleLeaveGroup = (groupName) => {
    if (!groupName) {
      return;
    }

    openConfirmation({
      type: "LEAVE_GROUP",
      title: "Leave Group",
      message: `Are you sure you want to leave ${groupName}?`,
      groupName,
    });
  };
  // =========================================================
  // OPEN GROUP CHAT
  // =========================================================

  const openGroupChat = (groupName) => {
    if (!groupName) {
      return;
    }

    sessionStorage.setItem("cloudchat_selected_group", groupName);

    navigate("/group-chat");
  };

  // =========================================================
  // LOGOUT
  // =========================================================

  const handleLogout = () => {
    try {
      if (isChatConnected()) {
        sendChatCommand("/exit");
      }
    } catch (logoutError) {
      console.error("Logout error:", logoutError);
    }

    sessionStorage.removeItem("cloudchat_username");
    sessionStorage.removeItem("cloudchat_token");
    sessionStorage.removeItem("cloudchat_selected_group");

    navigate("/login");
  };

  // =========================================================
  // DISPLAYED GROUPS
  // =========================================================

  const displayedGroups = search.trim() ? groupSearchResults : groups;

  // =========================================================
  // RENDER
  // =========================================================

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      {/* =====================================================
          HEADER
      ===================================================== */}

      <header className="sticky top-0 z-30 border-b border-slate-800 bg-slate-950/95 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate("/dashboard")}
              className="rounded-xl border border-slate-700 bg-slate-900 p-2.5 text-slate-300 transition hover:border-slate-600 hover:bg-slate-800 hover:text-white"
              title="Back to Dashboard"
            >
              <ArrowLeft size={20} />
            </button>

            <div>
              <h1 className="text-xl font-semibold">Groups</h1>

              <p className="text-sm text-slate-400">
                Discover and manage your group conversations
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleRefresh}
              disabled={refreshing}
              className="rounded-xl border border-slate-700 bg-slate-900 p-2.5 text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
              title="Refresh Groups"
            >
              <RefreshCw
                size={19}
                className={refreshing ? "animate-spin" : ""}
              />
            </button>

            <button
              onClick={() => setShowCreateModal(true)}
              className="flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-medium transition hover:bg-indigo-500"
            >
              <Plus size={18} />
              Create Group
            </button>

            <button
              onClick={handleLogout}
              className="rounded-xl border border-slate-700 bg-slate-900 p-2.5 text-slate-300 transition hover:border-red-500/40 hover:bg-red-500/10 hover:text-red-400"
              title="Logout"
            >
              <LogOut size={19} />
            </button>
          </div>
        </div>
      </header>

      {/* =====================================================
          MAIN CONTENT
      ===================================================== */}

      <main className="mx-auto max-w-7xl px-6 py-8">
        {/* SEARCH */}

        <div className="mb-6">
          <div className="relative">
            <Search
              size={19}
              className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500"
            />

            <input
              type="text"
              value={search}
              onChange={handleSearchChange}
              placeholder="Search groups..."
              className="w-full rounded-2xl border border-slate-800 bg-slate-900 py-3.5 pl-11 pr-11 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-indigo-500"
            />

            {search && (
              <button
                onClick={() => setSearch("")}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 transition hover:text-white"
              >
                <X size={18} />
              </button>
            )}
          </div>

          {search.trim() && (
            <p className="mt-2 text-xs text-slate-500">
              Search results from available groups
            </p>
          )}
        </div>

        {/* ===================================================
            ALERTS
        =================================================== */}

        {error && (
          <div className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">
            {error}
          </div>
        )}

        {message && !error && (
          <div className="mb-5 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-300">
            {message}
          </div>
        )}

        {/* ===================================================
            TITLE
        =================================================== */}

        <div className="mb-5 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold">
              {search.trim() ? "Search Results" : "My Groups"}
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              {search.trim()
                ? "Groups matching your search"
                : "Groups you are currently a member of"}
            </p>
          </div>

          {!search.trim() && (
            <div className="flex items-center gap-2 rounded-full border border-slate-800 bg-slate-900 px-3 py-1.5 text-xs text-slate-400">
              <Users size={14} />
              {groups.length} {groups.length === 1 ? "Group" : "Groups"}
            </div>
          )}
        </div>

        {/* ===================================================
            LOADING
        =================================================== */}

        {(loading || searchingGroups) && (
          <div className="flex min-h-[220px] items-center justify-center">
            <div className="flex items-center gap-3 text-sm text-slate-400">
              <RefreshCw size={18} className="animate-spin" />
              {searchingGroups ? "Searching groups..." : "Loading groups..."}
            </div>
          </div>
        )}

        {/* ===================================================
            EMPTY STATE
        =================================================== */}

        {!loading && !searchingGroups && displayedGroups.length === 0 && (
          <div className="rounded-2xl border border-slate-800 bg-slate-900/60 px-6 py-16 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-800 text-slate-500">
              {search.trim() ? <Search size={26} /> : <Users size={26} />}
            </div>

            <h3 className="text-base font-semibold">
              {search.trim() ? "No groups found" : "No groups yet"}
            </h3>

            <p className="mx-auto mt-2 max-w-md text-sm text-slate-500">
              {search.trim()
                ? "Try a different group name."
                : "Create a group or search for an existing group to get started."}
            </p>

            {!search.trim() && (
              <button
                onClick={() => setShowCreateModal(true)}
                className="mt-5 inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-medium transition hover:bg-indigo-500"
              >
                <Plus size={17} />
                Create Group
              </button>
            )}
          </div>
        )}

        {/* ===================================================
            GROUP GRID
        =================================================== */}

        {!loading && !searchingGroups && displayedGroups.length > 0 && (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {displayedGroups.map((groupName) => {
              const info = groupInfo[groupName] || {};

              const isMember = info.isMember || groups.includes(groupName);

              const isOwner = info.isOwner;

              const accessMode = info.accessMode || "REQUEST";

              return (
                <div
                  key={groupName}
                  className="group rounded-2xl border border-slate-800 bg-slate-900/70 p-5 transition hover:border-slate-700 hover:bg-slate-900"
                >
                  {/* GROUP TOP */}

                  <div className="flex items-start justify-between gap-4">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-indigo-500/10 text-indigo-400">
                        <Users size={23} />
                      </div>

                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-white">
                          {groupName}
                        </h3>

                        <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
                          <Users size={13} />

                          {info.memberCount !== undefined
                            ? `${info.memberCount} ${
                                info.memberCount === 1 ? "member" : "members"
                              }`
                            : "Group"}
                        </div>
                      </div>
                    </div>

                    {/* OWNER BADGE */}

                    {isOwner && (
                      <span className="flex shrink-0 items-center gap-1 rounded-full border border-amber-500/20 bg-amber-500/10 px-2.5 py-1 text-[11px] font-medium text-amber-400">
                        <Crown size={12} />
                        Owner
                      </span>
                    )}
                  </div>

                  {/* ACCESS MODE */}

                  <div className="mt-4 flex items-center justify-between">
                    <span className="text-xs text-slate-500">Access</span>

                    <span className="rounded-full border border-slate-700 bg-slate-800 px-2.5 py-1 text-[11px] font-medium text-slate-300">
                      {accessMode}
                    </span>
                  </div>

                  {/* ACTIONS */}

                  <div className="mt-5 flex flex-wrap gap-2">
                    {/* MEMBER */}

                    {isMember && (
                      <button
                        onClick={() => openGroupChat(groupName)}
                        className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-indigo-600 px-3 py-2.5 text-sm font-medium transition hover:bg-indigo-500"
                      >
                        <MessageCircle size={16} />
                        Chat
                      </button>
                    )}

                    {/* OWNER MANAGEMENT */}

                    {isOwner && (
                      <button
                        onClick={() => openGroupManagement(groupName)}
                        className="flex items-center justify-center gap-2 rounded-xl border border-slate-700 bg-slate-800 px-3 py-2.5 text-sm font-medium text-slate-200 transition hover:bg-slate-700"
                      >
                        <Settings size={16} />
                        Manage
                      </button>
                    )}

                    {/* NON-MEMBER JOIN */}

                    {!isMember && (
                      <button
                        onClick={() => openJoinModal(groupName)}
                        className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-emerald-600 px-3 py-2.5 text-sm font-medium transition hover:bg-emerald-500"
                      >
                        <UserPlus size={16} />

                        {accessMode === "OPEN"
                          ? "Join"
                          : accessMode === "CODE"
                            ? "Join with Code"
                            : "Request to Join"}
                      </button>
                    )}

                    {/* MEMBER LEAVE */}

                    {isMember && !isOwner && (
                      <button
                        onClick={() => handleLeaveGroup(groupName)}
                        className="flex items-center justify-center gap-2 rounded-xl border border-red-500/20 bg-red-500/5 px-3 py-2.5 text-sm font-medium text-red-400 transition hover:bg-red-500/10"
                      >
                        <UserMinus size={16} />
                        Leave
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </main>

      {/* =====================================================
          CREATE GROUP MODAL
      ===================================================== */}

      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
            <div className="mb-6 flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold">Create Group</h2>

                <p className="mt-1 text-sm text-slate-500">
                  Create a new group conversation
                </p>
              </div>

              <button
                onClick={() => setShowCreateModal(false)}
                className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-800 hover:text-white"
              >
                <X size={19} />
              </button>
            </div>

            {/* GROUP NAME */}

            <label className="mb-2 block text-sm font-medium text-slate-300">
              Group Name
            </label>

            <input
              type="text"
              value={newGroupName}
              onChange={(event) => setNewGroupName(event.target.value)}
              placeholder="Enter group name"
              className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-600 focus:border-indigo-500"
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  handleCreateGroup();
                }
              }}
            />

            {/* ACCESS MODE */}

            <label className="mb-2 mt-5 block text-sm font-medium text-slate-300">
              Access Mode
            </label>

            <select
              value={createAccessMode}
              onChange={(event) => setCreateAccessMode(event.target.value)}
              className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none focus:border-indigo-500"
            >
              <option value="REQUEST">Request to Join</option>

              <option value="CODE">Join with Code</option>

              <option value="OPEN">Open Join</option>
            </select>

            <div className="mt-3 rounded-xl border border-slate-800 bg-slate-950/70 p-3 text-xs leading-5 text-slate-500">
              {createAccessMode === "REQUEST" &&
                "Users must send a join request before they can join the group."}

              {createAccessMode === "CODE" &&
                "Users need the group join code to join directly."}

              {createAccessMode === "OPEN" &&
                "Users can join the group directly without approval or a code."}
            </div>

            {/* BUTTONS */}

            <div className="mt-6 flex gap-3">
              <button
                onClick={() => setShowCreateModal(false)}
                className="flex-1 rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-sm font-medium text-slate-300 transition hover:bg-slate-700"
              >
                Cancel
              </button>

              <button
                onClick={handleCreateGroup}
                className="flex-1 rounded-xl bg-indigo-600 px-4 py-3 text-sm font-medium transition hover:bg-indigo-500"
              >
                Create Group
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================
          JOIN GROUP MODAL
      ===================================================== */}

      {showJoinModal && selectedGroup && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
            <div className="mb-6 flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold">Join Group</h2>

                <p className="mt-1 text-sm text-slate-500">
                  {selectedGroup.name}
                </p>
              </div>

              <button
                onClick={() => setShowJoinModal(false)}
                className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-800 hover:text-white"
              >
                <X size={19} />
              </button>
            </div>

            {/* ACCESS MODE */}

            <div className="mb-5 flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950 p-4">
              <span className="text-sm text-slate-400">Access Mode</span>

              <span className="rounded-full border border-slate-700 bg-slate-800 px-3 py-1 text-xs font-medium text-slate-300">
                {selectedGroup.accessMode}
              </span>
            </div>

            {/* CODE */}

            {selectedGroup.accessMode === "CODE" && (
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-300">
                  Join Code
                </label>

                <input
                  type="text"
                  value={joinCode}
                  onChange={(event) => setJoinCode(event.target.value)}
                  placeholder="XXXX-XXXX"
                  className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-center font-mono text-sm uppercase tracking-widest text-white outline-none placeholder:text-slate-600 focus:border-indigo-500"
                />

                <p className="mt-2 text-xs text-slate-500">
                  Enter the join code provided by the group owner.
                </p>
              </div>
            )}

            {/* REQUEST */}

            {selectedGroup.accessMode === "REQUEST" && (
              <div className="rounded-xl border border-slate-800 bg-slate-950 p-4">
                <div className="flex items-start gap-3">
                  <UserPlus size={20} className="mt-0.5 text-indigo-400" />

                  <div>
                    <p className="text-sm font-medium text-slate-200">
                      Request to Join
                    </p>

                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      Your request will be sent to the group owner for approval.
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* OPEN */}

            {selectedGroup.accessMode === "OPEN" && (
              <div className="rounded-xl border border-slate-800 bg-slate-950 p-4">
                <div className="flex items-start gap-3">
                  <Check size={20} className="mt-0.5 text-emerald-400" />

                  <div>
                    <p className="text-sm font-medium text-slate-200">
                      Open Group
                    </p>

                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      You can join this group directly.
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* BUTTONS */}

            <div className="mt-6 flex gap-3">
              <button
                onClick={() => setShowJoinModal(false)}
                className="flex-1 rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-sm font-medium text-slate-300 transition hover:bg-slate-700"
              >
                Cancel
              </button>

              <button
                onClick={handleJoinGroup}
                className="flex-1 rounded-xl bg-emerald-600 px-4 py-3 text-sm font-medium transition hover:bg-emerald-500"
              >
                {selectedGroup.accessMode === "REQUEST"
                  ? "Send Request"
                  : "Join Group"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================
          GROUP MANAGEMENT MODAL
      ===================================================== */}

      {showManagementModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-6 backdrop-blur-sm">
          <div className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-slate-800 bg-slate-900 shadow-2xl">
            {/* HEADER */}

            <div className="flex items-center justify-between border-b border-slate-800 px-6 py-5">
              <div>
                <div className="flex items-center gap-2">
                  <Shield size={19} className="text-indigo-400" />

                  <h2 className="text-lg font-semibold">Group Management</h2>
                </div>

                <p className="mt-1 text-sm text-slate-500">{managementGroup}</p>
              </div>

              <button
                onClick={closeManagementModal}
                className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-800 hover:text-white"
              >
                <X size={19} />
              </button>
            </div>

            {/* BODY */}

            <div className="overflow-y-auto p-6">
              {/* LOADING */}

              {managementLoading && (
                <div className="mb-5 flex items-center gap-2 rounded-xl border border-slate-800 bg-slate-950 p-4 text-sm text-slate-400">
                  <RefreshCw size={17} className="animate-spin" />
                  Loading group information...
                </div>
              )}

              {/* GROUP ACCESS */}

              <div className="mb-5 rounded-2xl border border-slate-800 bg-slate-950 p-5">
                <div className="mb-4">
                  <h3 className="font-medium">Group Access</h3>

                  <p className="mt-1 text-xs text-slate-500">
                    Choose how users can join this group.
                  </p>
                </div>

                <div className="space-y-3">
                  {/* OPEN */}

                  <button
                    type="button"
                    onClick={() => setSelectedAccessMode("OPEN")}
                    className={`w-full rounded-xl border p-4 text-left transition ${
                      selectedAccessMode === "OPEN"
                        ? "border-emerald-500/50 bg-emerald-500/10"
                        : "border-slate-800 bg-slate-900 hover:border-slate-700"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <div
                        className={`mt-0.5 h-4 w-4 rounded-full border-2 ${
                          selectedAccessMode === "OPEN"
                            ? "border-emerald-400 bg-emerald-400"
                            : "border-slate-600"
                        }`}
                      />

                      <div>
                        <p className="font-medium text-white">Open</p>

                        <p className="mt-1 text-sm text-slate-400">
                          Anyone can join this group directly.
                        </p>
                      </div>
                    </div>
                  </button>

                  {/* REQUEST ACCESS */}

                  <button
                    type="button"
                    onClick={() => setSelectedAccessMode("REQUEST")}
                    className={`w-full rounded-xl border p-4 text-left transition ${
                      selectedAccessMode === "REQUEST"
                        ? "border-amber-500/50 bg-amber-500/10"
                        : "border-slate-800 bg-slate-900 hover:border-slate-700"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <div
                        className={`mt-0.5 h-4 w-4 rounded-full border-2 ${
                          selectedAccessMode === "REQUEST"
                            ? "border-amber-400 bg-amber-400"
                            : "border-slate-600"
                        }`}
                      />

                      <div>
                        <p className="font-medium text-white">Request Access</p>

                        <p className="mt-1 text-sm text-slate-400">
                          Users must request access before joining.
                        </p>
                      </div>
                    </div>
                  </button>

                  {/* PENDING JOIN REQUESTS */}

                  <div className="mb-5 rounded-2xl border border-slate-800 bg-slate-950 p-5">
                    <div className="mb-4">
                      <h3 className="font-medium">Pending Join Requests</h3>

                      <p className="mt-1 text-xs text-slate-500">
                        Users waiting for approval to join this group.
                      </p>
                    </div>

                    {pendingRequestsLoading ? (
                      <div className="flex items-center gap-2 rounded-xl border border-slate-800 bg-slate-900 p-4 text-sm text-slate-400">
                        <RefreshCw size={16} className="animate-spin" />
                        Loading requests...
                      </div>
                    ) : pendingRequests.length === 0 ? (
                      <div className="rounded-xl border border-slate-800 bg-slate-900 p-4 text-sm text-slate-500">
                        No pending join requests.
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {pendingRequests.map((requestUsername) => (
                          <div
                            key={requestUsername}
                            className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-900 p-4"
                          >
                            <div className="flex items-center gap-3">
                              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-500/10 text-indigo-400">
                                <UserRound size={17} />
                              </div>

                              <div>
                                <p className="text-sm font-medium text-white">
                                  {requestUsername}
                                </p>

                                <p className="text-xs text-slate-500">
                                  Requested to join
                                </p>
                              </div>
                            </div>

                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                onClick={() => {
                                  sendChatCommand(
                                    `/approve ${managementGroup} ${requestUsername}`,
                                  );

                                  setPendingRequests((previous) =>
                                    previous.filter(
                                      (name) => name !== requestUsername,
                                    ),
                                  );
                                }}
                                className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white transition hover:bg-emerald-500"
                              >
                                Approve
                              </button>

                              <button
                                type="button"
                                onClick={() => {
                                  sendChatCommand(
                                    `/reject ${managementGroup} ${requestUsername}`,
                                  );

                                  setPendingRequests((previous) =>
                                    previous.filter(
                                      (name) => name !== requestUsername,
                                    ),
                                  );
                                }}
                                className="rounded-lg bg-red-600/90 px-3 py-2 text-xs font-medium text-white transition hover:bg-red-500"
                              >
                                Reject
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* JOIN CODE */}

                  <button
                    type="button"
                    onClick={() => setSelectedAccessMode("CODE")}
                    className={`w-full rounded-xl border p-4 text-left transition ${
                      selectedAccessMode === "CODE"
                        ? "border-indigo-500/50 bg-indigo-500/10"
                        : "border-slate-800 bg-slate-900 hover:border-slate-700"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <div
                        className={`mt-0.5 h-4 w-4 rounded-full border-2 ${
                          selectedAccessMode === "CODE"
                            ? "border-indigo-400 bg-indigo-400"
                            : "border-slate-600"
                        }`}
                      />

                      <div>
                        <p className="font-medium text-white">Join Code</p>

                        <p className="mt-1 text-sm text-slate-400">
                          Users need the group join code to enter.
                        </p>
                      </div>
                    </div>
                  </button>
                </div>
                <div className="mt-5 flex justify-end">
                  <button
                    type="button"
                    onClick={handleSaveAccessMode}
                    disabled={managementLoading}
                    className="rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    Save Changes
                  </button>
                </div>
              </div>

              {/* GROUP CODE */}

              <div className="rounded-2xl border border-slate-800 bg-slate-950 p-5">
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <h3 className="font-medium">Group Join Code</h3>

                    <p className="mt-1 text-xs text-slate-500">
                      Share this code with users when the group uses CODE
                      access.
                    </p>
                  </div>

                  <button
                    onClick={handleCopyCode}
                    disabled={!managementCode}
                    className="flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs font-medium text-slate-300 transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {copiedCode ? (
                      <>
                        <Check size={15} />
                        Copied
                      </>
                    ) : (
                      <>
                        <Copy size={15} />
                        Copy
                      </>
                    )}
                  </button>
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 px-4 py-4 text-center font-mono text-lg tracking-[0.25em] text-indigo-300">
                  {managementCode || "Loading..."}
                </div>
              </div>

              {/* MEMBERS */}

              <div className="mt-5 rounded-2xl border border-slate-800 bg-slate-950 p-5">
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <h3 className="font-medium">Members</h3>

                    <p className="mt-1 text-xs text-slate-500">
                      Manage members and owner permissions.
                    </p>
                  </div>

                  <span className="rounded-full border border-slate-700 bg-slate-900 px-2.5 py-1 text-xs text-slate-400">
                    {managementMembers.length}
                  </span>
                </div>

                {managementMembers.length === 0 ? (
                  <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-center text-sm text-slate-500">
                    No members found.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {managementMembers.map((member) => {
                      const isCurrentUser = member.username === username;

                      return (
                        <div
                          key={member.username}
                          className="flex items-center justify-between gap-3 rounded-xl border border-slate-800 bg-slate-900 px-4 py-3"
                        >
                          {/* MEMBER INFO */}

                          <div className="flex min-w-0 items-center gap-3">
                            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-800 text-slate-400">
                              <UserRound size={17} />
                            </div>

                            <div className="min-w-0">
                              <p className="truncate text-sm font-medium text-slate-200">
                                {member.username}
                              </p>

                              <div className="mt-0.5 flex items-center gap-1.5">
                                {member.isOwner ? (
                                  <span className="flex items-center gap-1 text-[11px] text-amber-400">
                                    <Crown size={11} />
                                    Owner
                                  </span>
                                ) : (
                                  <span className="text-[11px] text-slate-500">
                                    Member
                                  </span>
                                )}

                                {isCurrentUser && (
                                  <span className="text-[11px] text-indigo-400">
                                    You
                                  </span>
                                )}
                              </div>
                            </div>
                          </div>

                          {/* MEMBER ACTIONS */}

                          {!isCurrentUser && (
                            <div className="flex shrink-0 items-center gap-2">
                              {/* OWNER / MEMBER ACTION */}

                              {member.isOwner ? (
                                <button
                                  onClick={() =>
                                    handleDemoteOwner(member.username)
                                  }
                                  disabled={managementActionLoading}
                                  className="rounded-lg border border-amber-500/20 bg-amber-500/5 px-2.5 py-2 text-xs font-medium text-amber-400 transition hover:bg-amber-500/10 disabled:cursor-not-allowed disabled:opacity-40"
                                  title="Demote Owner"
                                >
                                  Demote
                                </button>
                              ) : (
                                <button
                                  onClick={() =>
                                    handleMakeOwner(member.username)
                                  }
                                  disabled={managementActionLoading}
                                  className="rounded-lg border border-indigo-500/20 bg-indigo-500/5 px-2.5 py-2 text-xs font-medium text-indigo-400 transition hover:bg-indigo-500/10 disabled:cursor-not-allowed disabled:opacity-40"
                                  title="Make Owner"
                                >
                                  Make Owner
                                </button>
                              )}

                              {/* REMOVE MEMBER */}

                              <button
                                onClick={() =>
                                  handleRemoveMember(member.username)
                                }
                                disabled={managementActionLoading}
                                className="rounded-lg border border-red-500/20 bg-red-500/5 p-2 text-red-400 transition hover:bg-red-500/10 disabled:cursor-not-allowed disabled:opacity-40"
                                title="Remove Member"
                              >
                                <UserMinus size={15} />
                              </button>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {/* FOOTER */}

            <div className="border-t border-slate-800 px-6 py-4">
              <button
                onClick={closeManagementModal}
                className="w-full rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-sm font-medium text-slate-300 transition hover:bg-slate-700"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================
          CONFIRMATION MODAL
      ===================================================== */}

      {confirmation.open && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 px-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-white">
                  {confirmation.title}
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Please confirm this action.
                </p>
              </div>

              <button
                onClick={closeConfirmation}
                className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-800 hover:text-white"
                title="Cancel"
              >
                <X size={19} />
              </button>
            </div>

            <div className="mt-6 rounded-xl border border-slate-800 bg-slate-950 p-4">
              <p className="text-sm leading-6 text-slate-300">
                {confirmation.message}
              </p>
            </div>

            <div className="mt-6 flex gap-3">
              <button
                onClick={closeConfirmation}
                className="flex-1 rounded-xl border border-slate-700 bg-slate-800 px-4 py-3 text-sm font-medium text-slate-300 transition hover:bg-slate-700"
              >
                Cancel
              </button>

              <button
                onClick={handleConfirmAction}
                className="flex-1 rounded-xl bg-indigo-600 px-4 py-3 text-sm font-medium text-white transition hover:bg-indigo-500"
              >
                {confirmation.type === "REMOVE_MEMBER"
                  ? "Remove Member"
                  : confirmation.type === "MAKE_OWNER"
                    ? "Make Owner"
                    : confirmation.type === "DEMOTE_OWNER"
                      ? "Demote Owner"
                      : "Leave Group"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Groups;
