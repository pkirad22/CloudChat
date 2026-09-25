import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Activity,
  LogOut,
  RefreshCw,
  Server,
  UserRound,
  Users,
} from "lucide-react";
import { getOnlineUsers } from "../services/api";

function Dashboard() {
  const navigate = useNavigate();

  // Get username directly from the current session.
  const username = sessionStorage.getItem("cloudchat_username") || "";

  const [onlineUsers, setOnlineUsers] = useState([]);
  const [serverId, setServerId] = useState("-");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  // =========================================================
  // CHECK AUTHENTICATION
  // =========================================================

  useEffect(() => {
    if (!username) {
      navigate("/login", { replace: true });
    }
  }, [navigate, username]);

  // =========================================================
  // LOAD ONLINE USERS
  // =========================================================

  const loadOnlineUsers = useCallback(async () => {
    try {
      setError("");

      const result = await getOnlineUsers();

      if (result.success) {
        setOnlineUsers(result.users || []);
        setServerId(result.serverId || "-");
      } else {
        setError(result.message || "Unable to load online users.");
      }
    } catch (error) {
      console.error("Failed to load online users:", error);

      setError(error.message || "Unable to connect to CloudChat API.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // =========================================================
  // INITIAL DATA LOAD
  // =========================================================

  useEffect(() => {
    if (!username) {
      return;
    }

    const timer = setTimeout(() => {
      loadOnlineUsers();
    }, 0);

    return () => clearTimeout(timer);
  }, [loadOnlineUsers, username]);

  // =========================================================
  // AUTOMATIC REFRESH
  // =========================================================

  useEffect(() => {
    if (!username) {
      return;
    }

    const interval = setInterval(() => {
      loadOnlineUsers();
    }, 10000);

    return () => clearInterval(interval);
  }, [loadOnlineUsers, username]);

  // =========================================================
  // MANUAL REFRESH
  // =========================================================

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadOnlineUsers();
  };

  // =========================================================
  // LOGOUT
  // =========================================================

  const handleLogout = () => {
    sessionStorage.removeItem("cloudchat_username");

    navigate("/login", { replace: true });
  };

  return (
    <div className="cloudchat-app">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />

      <main
        style={{
          minHeight: "100vh",
          padding: "32px",
          maxWidth: "1200px",
          margin: "0 auto",
          width: "100%",
          boxSizing: "border-box",
        }}
      >
        {/* =====================================================
            HEADER
        ====================================================== */}

        <header
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: "20px",
            marginBottom: "32px",
            flexWrap: "wrap",
          }}
        >
          <div>
            <p
              style={{
                margin: "0 0 6px",
                fontSize: "13px",
                opacity: 0.65,
                textTransform: "uppercase",
                letterSpacing: "1.5px",
              }}
            >
              CloudChat
            </p>

            <h1
              style={{
                margin: 0,
                fontSize: "32px",
                fontWeight: 700,
              }}
            >
              Welcome back, {username}
            </h1>

            <p
              style={{
                margin: "8px 0 0",
                opacity: 0.7,
              }}
            >
              Distributed messaging workspace
            </p>
          </div>

          <div
            style={{
              display: "flex",
              gap: "10px",
              alignItems: "center",
            }}
          >
            <button
              type="button"
              className="google-button"
              onClick={handleRefresh}
              disabled={refreshing}
              style={{
                width: "auto",
                padding: "10px 16px",
                display: "flex",
                alignItems: "center",
                gap: "8px",
              }}
            >
              <RefreshCw
                size={17}
                style={{
                  animation: refreshing ? "spin 1s linear infinite" : "none",
                }}
              />

              {refreshing ? "Refreshing..." : "Refresh"}
            </button>

            <button
              type="button"
              className="admin-link"
              onClick={handleLogout}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "7px",
              }}
            >
              <LogOut size={16} />
              Logout
            </button>
          </div>
        </header>

        {/* =====================================================
            ERROR
        ====================================================== */}

        {error && (
          <div
            style={{
              padding: "14px 16px",
              marginBottom: "24px",
              borderRadius: "10px",
              background: "rgba(220, 38, 38, 0.12)",
              border: "1px solid rgba(220, 38, 38, 0.3)",
              color: "#f87171",
            }}
          >
            {error}
          </div>
        )}

        {/* =====================================================
            STAT CARDS
        ====================================================== */}

        <section
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
            gap: "18px",
            marginBottom: "28px",
          }}
        >
          {/* Online Users */}
          <div
            className="auth-card"
            style={{
              padding: "22px",
              minHeight: "auto",
            }}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <div>
                <p
                  style={{
                    margin: 0,
                    opacity: 0.65,
                    fontSize: "14px",
                  }}
                >
                  Online Users
                </p>

                <h2
                  style={{
                    margin: "8px 0 0",
                    fontSize: "30px",
                  }}
                >
                  {loading ? "..." : onlineUsers.length}
                </h2>
              </div>

              <Users size={28} />
            </div>
          </div>

          {/* Current Server */}
          <div
            className="auth-card"
            style={{
              padding: "22px",
              minHeight: "auto",
            }}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <div>
                <p
                  style={{
                    margin: 0,
                    opacity: 0.65,
                    fontSize: "14px",
                  }}
                >
                  Current Server
                </p>

                <h2
                  style={{
                    margin: "8px 0 0",
                    fontSize: "24px",
                  }}
                >
                  {loading ? "..." : serverId}
                </h2>
              </div>

              <Server size={28} />
            </div>
          </div>

          {/* Connection */}
          <div
            className="auth-card"
            style={{
              padding: "22px",
              minHeight: "auto",
            }}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <div>
                <p
                  style={{
                    margin: 0,
                    opacity: 0.65,
                    fontSize: "14px",
                  }}
                >
                  Connection
                </p>

                <h2
                  style={{
                    margin: "8px 0 0",
                    fontSize: "24px",
                  }}
                >
                  {error ? "Offline" : "Connected"}
                </h2>
              </div>

              <Activity size={28} />
            </div>
          </div>
        </section>

        {/* =====================================================
            ONLINE USERS
        ====================================================== */}

        <section
          className="auth-card"
          style={{
            padding: "24px",
            minHeight: "auto",
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: "20px",
              gap: "15px",
            }}
          >
            <div>
              <h2
                style={{
                  margin: 0,
                  fontSize: "22px",
                }}
              >
                Online Users
              </h2>

              <p
                style={{
                  margin: "6px 0 0",
                  opacity: 0.6,
                  fontSize: "14px",
                }}
              >
                Users currently connected to the CloudChat cluster
              </p>
            </div>

            <Users size={24} />
          </div>

          {/* Loading */}
          {loading && (
            <div
              style={{
                padding: "30px",
                textAlign: "center",
                opacity: 0.65,
              }}
            >
              Loading online users...
            </div>
          )}

          {/* Empty */}
          {!loading && !error && onlineUsers.length === 0 && (
            <div
              style={{
                padding: "30px",
                textAlign: "center",
                opacity: 0.65,
              }}
            >
              No users are currently online.
            </div>
          )}

          {/* Users */}
          {!loading && onlineUsers.length > 0 && (
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
                gap: "12px",
              }}
            >
              {onlineUsers.map((user, index) => (
                <div
                  key={`${user.username}-${index}`}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "12px",
                    padding: "14px",
                    borderRadius: "10px",
                    border: "1px solid rgba(255,255,255,0.08)",
                    background: "rgba(255,255,255,0.025)",
                  }}
                >
                  <div
                    style={{
                      width: "42px",
                      height: "42px",
                      borderRadius: "50%",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      background: "rgba(255,255,255,0.08)",
                      flexShrink: 0,
                    }}
                  >
                    <UserRound size={20} />
                  </div>

                  <div
                    style={{
                      minWidth: 0,
                      flex: 1,
                    }}
                  >
                    <div
                      style={{
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
                        display: "flex",
                        alignItems: "center",
                        gap: "6px",
                        marginTop: "4px",
                        fontSize: "12px",
                        opacity: 0.6,
                      }}
                    >
                      <span
                        style={{
                          width: "7px",
                          height: "7px",
                          borderRadius: "50%",
                          background: "#4ade80",
                          display: "inline-block",
                        }}
                      />

                      {user.server || "Unknown server"}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* =====================================================
            UPCOMING MODULES
        ====================================================== */}

        <section
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
            gap: "18px",
            marginTop: "24px",
          }}
        >
          <div
            className="auth-card"
            style={{
              padding: "20px",
              minHeight: "auto",
            }}
          >
            <h3 style={{ marginTop: 0 }}>Private Chat</h3>

            <p style={{ opacity: 0.65 }}>
              Real-time private messaging will be connected through WebSocket.
            </p>
          </div>

          <div
            className="auth-card"
            style={{
              padding: "20px",
              minHeight: "auto",
            }}
          >
            <h3 style={{ marginTop: 0 }}>Groups</h3>

            <p style={{ opacity: 0.65 }}>
              Distributed groups and members will appear here.
            </p>
          </div>

          <div
            className="auth-card"
            style={{
              padding: "20px",
              minHeight: "auto",
            }}
          >
            <h3 style={{ marginTop: 0 }}>File Sharing</h3>

            <p style={{ opacity: 0.65 }}>
              Private and group file sharing will be connected next.
            </p>
          </div>
        </section>
      </main>

      <style>{`
        @keyframes spin {
          from {
            transform: rotate(0deg);
          }

          to {
            transform: rotate(360deg);
          }
        }
      `}</style>
    </div>
  );
}

export default Dashboard;
