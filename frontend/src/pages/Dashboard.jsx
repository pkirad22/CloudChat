import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  CheckCircle2,
  Cloud,
  File,
  LogOut,
  MessageCircle,
  RefreshCw,
  Server,
  Users,
  Wifi,
  Zap,
} from "lucide-react";

import { getOnlineUsers } from "../services/api";
import {
  disconnectChat,
  getActiveServer,
  onActiveServerChange,
} from "../services/chatSocket";

function Dashboard() {
  const navigate = useNavigate();
  const username = sessionStorage.getItem("cloudchat_username") || "";

  const [onlineUsers, setOnlineUsers] = useState([]);
  const [serverId, setServerId] = useState(() => getActiveServer() || "-");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  // Authentication
  useEffect(() => {
    if (!username) {
      navigate("/login", { replace: true });
    }
  }, [navigate, username]);

  // Track the actual chat server.
  // This updates when login or failover successfully establishes a server.
  useEffect(() => {
    const unsubscribe = onActiveServerChange((activeServer) => {
      setServerId(activeServer || "-");
    });

    return unsubscribe;
  }, []);

  // Fetch online users and other API information.
  // Do not overwrite the active chat server with a potentially stale API value.
  const loadOnlineUsers = useCallback(async () => {
    try {
      setError("");

      const result = await getOnlineUsers();

      if (result.success) {
        setOnlineUsers(result.users || []);
      } else {
        setError(result.message || "Unable to load online users.");
      }
    } catch (err) {
      console.error("Failed to load online users:", err);
      setError(err.message || "Unable to connect to CloudChat API.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    if (!username) return;

    const timer = setTimeout(() => {
      loadOnlineUsers();
    }, 0);

    return () => clearTimeout(timer);
  }, [loadOnlineUsers, username]);

  // Automatic refresh every 10 seconds
  useEffect(() => {
    if (!username) return;

    const interval = setInterval(() => {
      loadOnlineUsers();
    }, 10000);

    return () => clearInterval(interval);
  }, [loadOnlineUsers, username]);

  const handleRefresh = async () => {
    if (refreshing) return;

    setRefreshing(true);
    await loadOnlineUsers();
  };

  const handleLogout = () => {
    disconnectChat();
    sessionStorage.removeItem("cloudchat_username");
    navigate("/login", { replace: true });
  };

  const modules = [
    {
      title: "Private Chat",
      description:
        "Connect directly with other users through real-time messaging.",
      icon: MessageCircle,
      label: "Open conversations",
      path: "/chat",
      accent: "blue",
      number: "01",
    },
    {
      title: "Groups",
      description:
        "Create communities, manage members and communicate together.",
      icon: Users,
      label: "Explore groups",
      path: "/groups",
      accent: "purple",
      number: "02",
    },
    {
      title: "File Sharing",
      description: "Send and receive files across your CloudChat network.",
      icon: File,
      label: "Manage files",
      path: "/file-sharing",
      accent: "cyan",
      number: "03",
    },
  ];

  return (
    <div className="cc-dashboard">
      <style>{`
        .cc-dashboard {
          --cc-bg: #080d18;
          --cc-panel: #101827;
          --cc-panel-light: #141e30;
          --cc-border: rgba(151, 171, 205, 0.15);
          --cc-text: #edf3ff;
          --cc-muted: #8b9ab4;
          --cc-blue: #62a5ff;
          --cc-cyan: #51e0dc;

          min-height: 100vh;
          color: var(--cc-text);
          background:
            radial-gradient(ellipse at 12% 0%, rgba(44, 103, 193, 0.15), transparent 35%),
            radial-gradient(ellipse at 95% 30%, rgba(35, 191, 195, 0.07), transparent 28%),
            var(--cc-bg);
          font-family: inherit;
          padding: 0;
          box-sizing: border-box;
          overflow-x: hidden;
        }

        .cc-dashboard *,
        .cc-dashboard *::before,
        .cc-dashboard *::after {
          box-sizing: border-box;
        }

        .cc-layout {
          display: grid;
          grid-template-columns: 238px minmax(0, 1fr);
          min-height: 100vh;
          max-width: 1800px;
          margin: 0 auto;
        }

        .cc-sidebar {
          display: flex;
          flex-direction: column;
          padding: 29px 18px 22px;
          border-right: 1px solid var(--cc-border);
          background: rgba(8, 13, 24, 0.68);
          min-width: 0;
        }

        .cc-brand {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 0 8px;
          margin-bottom: 42px;
        }

        .cc-brand-icon {
          width: 43px;
          height: 43px;
          display: grid;
          place-items: center;
          border-radius: 14px;
          color: #081522;
          background: linear-gradient(135deg, #79d8ff, #65e1d5);
          box-shadow: 0 8px 28px rgba(72, 188, 223, 0.2);
        }

        .cc-brand-name {
          font-size: 18px;
          font-weight: 750;
          letter-spacing: -0.6px;
          margin: 0;
        }

        .cc-brand-subtitle {
          margin: 4px 0 0;
          color: var(--cc-muted);
          font-size: 10px;
          letter-spacing: 1.5px;
          text-transform: uppercase;
        }

        .cc-nav-label {
          margin: 0 10px 12px;
          color: #697995;
          font-size: 10px;
          font-weight: 700;
          letter-spacing: 1.6px;
          text-transform: uppercase;
        }

        .cc-nav {
          display: flex;
          flex-direction: column;
          gap: 6px;
        }

        .cc-nav-item {
          display: flex;
          align-items: center;
          gap: 12px;
          width: 100%;
          min-height: 45px;
          padding: 0 12px;
          border: 1px solid transparent;
          border-radius: 11px;
          background: transparent;
          color: #9baac2;
          font: inherit;
          font-size: 13px;
          text-align: left;
          cursor: pointer;
          transition: background 0.2s, color 0.2s, border-color 0.2s;
        }

        .cc-nav-item:hover {
          background: rgba(255, 255, 255, 0.045);
          color: var(--cc-text);
        }

        .cc-nav-item.cc-nav-active {
          color: #8cc8ff;
          background: linear-gradient(90deg, rgba(63, 140, 255, 0.17), rgba(63, 140, 255, 0.045));
          border-color: rgba(98, 165, 255, 0.15);
        }

        .cc-nav-active .cc-nav-icon {
          color: #78b9ff;
        }

        .cc-nav-icon {
          display: flex;
          color: #8292ad;
        }

        .cc-sidebar-divider {
          height: 1px;
          margin: 26px 5px;
          background: var(--cc-border);
        }

        .cc-sidebar-card {
          margin-top: auto;
          padding: 16px;
          border: 1px solid rgba(98, 165, 255, 0.17);
          border-radius: 15px;
          background: linear-gradient(145deg, rgba(35, 67, 111, 0.25), rgba(17, 28, 45, 0.7));
        }

        .cc-sidebar-card-icon {
          display: grid;
          place-items: center;
          width: 35px;
          height: 35px;
          margin-bottom: 14px;
          border-radius: 10px;
          color: #8bc4ff;
          background: rgba(98, 165, 255, 0.13);
        }

        .cc-sidebar-card h3 {
          margin: 0;
          font-size: 13px;
        }

        .cc-sidebar-card p {
          margin: 8px 0 0;
          color: var(--cc-muted);
          font-size: 11px;
          line-height: 1.7;
        }

        .cc-sidebar-footer {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 22px 7px 0;
        }

        .cc-profile-trigger {
          width: 100%;
          border: 1px solid transparent;
          border-radius: 12px;
          background: transparent;
          color: inherit;
          font: inherit;
          text-align: left;
          cursor: pointer;
          transition: background 0.2s ease, border-color 0.2s ease;
        }

        .cc-profile-trigger:hover {
          background: rgba(98, 165, 255, 0.08);
          border-color: rgba(98, 165, 255, 0.18);
        }

        .cc-profile-trigger:focus-visible {
          outline: 2px solid #62a5ff;
          outline-offset: 3px;
        }

        .cc-profile-arrow {
          flex-shrink: 0;
          color: #8292ad;
          opacity: 0;
          transition: opacity 0.2s ease, transform 0.2s ease;
        }

        .cc-profile-trigger:hover .cc-profile-arrow {
          opacity: 1;
          transform: translateX(2px);
        }

        .cc-avatar {
          display: grid;
          place-items: center;
          width: 37px;
          height: 37px;
          flex-shrink: 0;
          border: 1px solid rgba(98, 165, 255, 0.22);
          border-radius: 12px;
          color: #9dd0ff;
          background: rgba(98, 165, 255, 0.12);
          font-weight: 750;
          font-size: 14px;
        }

        .cc-user-info {
          min-width: 0;
          flex: 1;
        }

        .cc-user-name {
          overflow: hidden;
          color: #e5edfc;
          font-size: 12px;
          font-weight: 650;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .cc-user-role {
          margin-top: 4px;
          color: var(--cc-muted);
          font-size: 10px;
        }

        .cc-main {
          min-width: 0;
          padding: 28px 34px 40px;
        }

        .cc-topbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 16px;
          padding-bottom: 25px;
          border-bottom: 1px solid var(--cc-border);
        }

        .cc-breadcrumb {
          color: var(--cc-muted);
          font-size: 12px;
        }

        .cc-breadcrumb span {
          color: #dfe9fb;
        }

        .cc-topbar-actions {
          display: flex;
          align-items: center;
          gap: 10px;
        }

        .cc-network-pill {
          display: flex;
          align-items: center;
          gap: 8px;
          min-height: 36px;
          padding: 0 12px;
          border: 1px solid var(--cc-border);
          border-radius: 10px;
          color: #aebed5;
          background: rgba(255, 255, 255, 0.025);
          font-size: 11px;
        }

        .cc-live-dot,
        .cc-online-dot {
          width: 7px;
          height: 7px;
          flex-shrink: 0;
          border-radius: 50%;
          background: #45dfa2;
          box-shadow: 0 0 12px rgba(69, 223, 162, 0.5);
        }

        .cc-refresh-btn,
        .cc-logout-btn {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          min-height: 38px;
          padding: 0 13px;
          border: 1px solid var(--cc-border);
          border-radius: 10px;
          color: #d8e4f8;
          background: #111a2a;
          font: inherit;
          font-size: 12px;
          cursor: pointer;
          transition: background 0.2s, border-color 0.2s;
        }

        .cc-refresh-btn:hover,
        .cc-logout-btn:hover {
          border-color: rgba(98, 165, 255, 0.4);
          background: #19263b;
        }

        .cc-refresh-btn:disabled {
          opacity: 0.65;
          cursor: wait;
        }

        .cc-logout-btn {
          color: #ffaaaa;
          border-color: rgba(255, 117, 117, 0.16);
        }

        .cc-spin {
          animation: cc-spin 1s linear infinite;
        }

        @keyframes cc-spin {
          to { transform: rotate(360deg); }
        }

        .cc-heading {
          display: flex;
          justify-content: space-between;
          align-items: flex-end;
          gap: 20px;
          margin: 32px 0 25px;
        }

        .cc-eyebrow {
          display: flex;
          align-items: center;
          gap: 8px;
          margin: 0 0 10px;
          color: #7fbaff;
          font-size: 10px;
          font-weight: 750;
          letter-spacing: 1.8px;
          text-transform: uppercase;
        }

        .cc-heading h1 {
          margin: 0;
          color: #f0f5ff;
          font-size: clamp(25px, 3vw, 34px);
          line-height: 1.2;
          letter-spacing: -1.2px;
          font-weight: 750;
          overflow-wrap: anywhere;
        }

        .cc-heading p {
          margin: 10px 0 0;
          color: var(--cc-muted);
          font-size: 13px;
          line-height: 1.6;
        }

        .cc-date-label {
          flex-shrink: 0;
          padding-bottom: 3px;
          color: #8494ad;
          font-size: 11px;
        }

        .cc-hero {
          position: relative;
          display: grid;
          grid-template-columns: minmax(0, 1.15fr) minmax(230px, 0.85fr);
          align-items: center;
          gap: 20px;
          min-height: 240px;
          padding: 29px 32px;
          overflow: hidden;
          border: 1px solid rgba(99, 160, 255, 0.23);
          border-radius: 20px;
          background:
            radial-gradient(ellipse at 88% 50%, rgba(50, 138, 204, 0.2), transparent 36%),
            linear-gradient(110deg, #14213a 0%, #111b2d 55%, #0d202e 100%);
          box-shadow: 0 18px 50px rgba(0, 0, 0, 0.13);
        }

        .cc-hero::after {
          position: absolute;
          right: -100px;
          top: -180px;
          width: 360px;
          height: 360px;
          border: 1px solid rgba(111, 196, 255, 0.09);
          border-radius: 50%;
          content: "";
          pointer-events: none;
        }

        .cc-hero-copy {
          position: relative;
          z-index: 1;
        }

        .cc-hero-tag {
          display: inline-flex;
          align-items: center;
          gap: 7px;
          padding: 7px 10px;
          border: 1px solid rgba(110, 185, 255, 0.18);
          border-radius: 8px;
          color: #a4d5ff;
          background: rgba(68, 140, 213, 0.1);
          font-size: 10px;
          font-weight: 700;
          letter-spacing: 0.8px;
          text-transform: uppercase;
        }

        .cc-hero h2 {
          max-width: 430px;
          margin: 17px 0 10px;
          font-size: clamp(22px, 2.5vw, 29px);
          line-height: 1.25;
          letter-spacing: -0.8px;
        }

        .cc-hero p {
          max-width: 420px;
          margin: 0;
          color: #9aabc5;
          font-size: 12px;
          line-height: 1.8;
        }

        .cc-hero-button {
          display: inline-flex;
          align-items: center;
          gap: 9px;
          margin-top: 20px;
          padding: 11px 15px;
          border: 0;
          border-radius: 9px;
          color: #091523;
          background: linear-gradient(100deg, #8bd4ff, #75e1d5);
          font: inherit;
          font-size: 12px;
          font-weight: 750;
          cursor: pointer;
          transition: transform 0.2s, box-shadow 0.2s;
        }

        .cc-hero-button:hover {
          transform: translateY(-2px);
          box-shadow: 0 8px 24px rgba(92, 207, 222, 0.18);
        }

        .cc-network-art {
          position: relative;
          z-index: 1;
          display: grid;
          place-items: center;
          min-height: 175px;
          width: 100%;
        }

        .cc-network-art svg {
          width: 100%;
          max-width: 340px;
          overflow: visible;
        }

        .cc-stat-grid {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 16px;
          margin-top: 20px;
        }

        .cc-stat-card {
          min-width: 0;
          padding: 19px 20px;
          border: 1px solid var(--cc-border);
          border-radius: 15px;
          background: linear-gradient(145deg, rgba(19, 29, 46, 0.94), rgba(13, 21, 34, 0.92));
          transition: border-color 0.2s, transform 0.2s;
        }

        .cc-stat-card:hover {
          transform: translateY(-2px);
          border-color: rgba(98, 165, 255, 0.29);
        }

        .cc-stat-top {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 10px;
        }

        .cc-stat-label {
          color: #9baac2;
          font-size: 12px;
        }

        .cc-stat-icon {
          display: grid;
          place-items: center;
          width: 37px;
          height: 37px;
          flex-shrink: 0;
          border-radius: 11px;
        }

        .cc-stat-icon.blue {
          color: #80bcff;
          background: rgba(70, 145, 255, 0.12);
        }

        .cc-stat-icon.cyan {
          color: #6be0d6;
          background: rgba(64, 209, 196, 0.11);
        }

        .cc-stat-icon.green {
          color: #61e4a7;
          background: rgba(67, 220, 151, 0.1);
        }

        .cc-stat-value {
          margin-top: 16px;
          color: #f0f5ff;
          font-size: 28px;
          font-weight: 750;
          letter-spacing: -0.8px;
          overflow-wrap: anywhere;
        }

        .cc-stat-value.cc-stat-server {
          font-size: 21px;
        }

        .cc-stat-foot {
          display: flex;
          align-items: center;
          gap: 6px;
          margin-top: 9px;
          color: #8494ad;
          font-size: 10px;
        }

        .cc-section {
          margin-top: 29px;
        }

        .cc-section-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 15px;
          margin-bottom: 16px;
        }

        .cc-section-title {
          margin: 0;
          font-size: 17px;
          font-weight: 700;
          letter-spacing: -0.35px;
        }

        .cc-section-subtitle {
          margin: 6px 0 0;
          color: var(--cc-muted);
          font-size: 11px;
          line-height: 1.6;
        }

        .cc-section-count {
          display: inline-flex;
          align-items: center;
          gap: 7px;
          padding: 7px 10px;
          border: 1px solid var(--cc-border);
          border-radius: 9px;
          color: #b7c5dc;
          background: rgba(255, 255, 255, 0.025);
          font-size: 10px;
          white-space: nowrap;
        }

        .cc-users-panel {
          overflow: hidden;
          border: 1px solid var(--cc-border);
          border-radius: 15px;
          background: rgba(15, 24, 39, 0.8);
        }

        .cc-users-table-head {
          display: grid;
          grid-template-columns: minmax(0, 1.5fr) minmax(110px, 0.8fr) minmax(100px, 0.7fr);
          gap: 15px;
          padding: 13px 19px;
          border-bottom: 1px solid var(--cc-border);
          color: #7889a4;
          background: rgba(255, 255, 255, 0.018);
          font-size: 9px;
          font-weight: 750;
          letter-spacing: 1px;
          text-transform: uppercase;
        }

        .cc-user-row {
          display: grid;
          grid-template-columns: minmax(0, 1.5fr) minmax(110px, 0.8fr) minmax(100px, 0.7fr);
          align-items: center;
          gap: 15px;
          padding: 13px 19px;
          border-bottom: 1px solid rgba(151, 171, 205, 0.08);
          transition: background 0.2s;
        }

        .cc-user-row:last-child {
          border-bottom: 0;
        }

        .cc-user-row:hover {
          background: rgba(255, 255, 255, 0.025);
        }

        .cc-user-cell {
          display: flex;
          align-items: center;
          gap: 11px;
          min-width: 0;
        }

        .cc-user-cell .cc-avatar {
          width: 36px;
          height: 36px;
          border-radius: 11px;
          font-size: 12px;
        }

        .cc-user-cell-name {
          overflow: hidden;
          color: #e0e9fa;
          font-size: 12px;
          font-weight: 650;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .cc-user-cell-sub {
          margin-top: 4px;
          color: #788aa5;
          font-size: 10px;
        }

        .cc-server-label {
          display: inline-flex;
          align-items: center;
          gap: 7px;
          color: #acbad0;
          font-size: 11px;
          overflow-wrap: anywhere;
        }

        .cc-status-badge {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          justify-self: start;
          padding: 6px 9px;
          border: 1px solid rgba(69, 223, 162, 0.13);
          border-radius: 7px;
          color: #71e5b1;
          background: rgba(69, 223, 162, 0.06);
          font-size: 10px;
        }

        .cc-empty-state {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          min-height: 170px;
          padding: 25px;
          text-align: center;
        }

        .cc-empty-icon {
          display: grid;
          place-items: center;
          width: 45px;
          height: 45px;
          margin-bottom: 13px;
          border: 1px solid var(--cc-border);
          border-radius: 14px;
          color: #8ba7ce;
          background: rgba(255, 255, 255, 0.025);
        }

        .cc-empty-state h3 {
          margin: 0;
          font-size: 13px;
        }

        .cc-empty-state p {
          margin: 8px 0 0;
          color: var(--cc-muted);
          font-size: 11px;
        }

        .cc-error {
          margin-top: 18px;
          padding: 13px 15px;
          border: 1px solid rgba(255, 113, 113, 0.25);
          border-radius: 10px;
          color: #ffaaaa;
          background: rgba(220, 38, 38, 0.1);
          font-size: 12px;
          line-height: 1.6;
        }

        .cc-modules-grid {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 16px;
        }

        .cc-module-card {
          position: relative;
          min-width: 0;
          padding: 20px;
          overflow: hidden;
          border: 1px solid var(--cc-border);
          border-radius: 15px;
          background: linear-gradient(150deg, rgba(19, 29, 46, 0.94), rgba(12, 20, 33, 0.96));
          text-align: left;
          cursor: pointer;
          transition: transform 0.2s, border-color 0.2s, background 0.2s;
        }

        .cc-module-card:hover {
          transform: translateY(-4px);
          border-color: rgba(98, 165, 255, 0.34);
          background: linear-gradient(150deg, rgba(24, 39, 62, 0.98), rgba(13, 23, 39, 0.98));
        }

        .cc-module-top {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 12px;
        }

        .cc-module-icon {
          display: grid;
          place-items: center;
          width: 43px;
          height: 43px;
          border-radius: 13px;
        }

        .cc-module-icon.blue {
          color: #81bcff;
          background: rgba(70, 145, 255, 0.12);
        }

        .cc-module-icon.purple {
          color: #c2a2ff;
          background: rgba(156, 111, 255, 0.12);
        }

        .cc-module-icon.cyan {
          color: #6be0d6;
          background: rgba(64, 209, 196, 0.11);
        }

        .cc-module-number {
          color: #53647e;
          font-size: 10px;
          font-weight: 750;
          letter-spacing: 1px;
        }

        .cc-module-card h3 {
          margin: 19px 0 8px;
          color: #e8f0ff;
          font-size: 14px;
          font-weight: 700;
        }

        .cc-module-card p {
          min-height: 52px;
          margin: 0;
          color: #8c9cb5;
          font-size: 11px;
          line-height: 1.8;
        }

        .cc-module-link {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 8px;
          margin-top: 16px;
          padding-top: 14px;
          border-top: 1px solid rgba(151, 171, 205, 0.1);
          color: #9fcaff;
          font-size: 11px;
          font-weight: 650;
        }

        .cc-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          flex-wrap: wrap;
          gap: 12px;
          margin-top: 31px;
          padding-top: 20px;
          border-top: 1px solid var(--cc-border);
          color: #687a96;
          font-size: 10px;
        }

        .cc-footer-brand {
          display: flex;
          align-items: center;
          gap: 7px;
        }

        .cc-footer-status {
          display: flex;
          align-items: center;
          gap: 7px;
        }

        @media (min-width: 1500px) {
          .cc-main {
            padding-right: 48px;
            padding-left: 48px;
          }
        }

        @media (max-width: 1100px) {
          .cc-layout {
            grid-template-columns: 205px minmax(0, 1fr);
          }

          .cc-main {
            padding-right: 23px;
            padding-left: 23px;
          }

          .cc-hero {
            padding: 25px;
          }

          .cc-stat-grid {
            gap: 11px;
          }

          .cc-stat-card {
            padding: 16px;
          }

          .cc-modules-grid {
            gap: 11px;
          }

          .cc-module-card {
            padding: 16px;
          }
        }

        @media (max-width: 820px) {
          .cc-layout {
            grid-template-columns: 1fr;
          }

          .cc-sidebar {
            display: block;
            padding: 15px 20px;
            border-right: 0;
            border-bottom: 1px solid var(--cc-border);
          }

          .cc-brand {
            margin: 0;
            padding: 0;
          }

          .cc-brand-icon {
            width: 37px;
            height: 37px;
          }

          .cc-brand-name {
            font-size: 16px;
          }

          .cc-nav-label,
          .cc-sidebar-card,
          .cc-sidebar-footer,
          .cc-sidebar-divider {
            display: none;
          }
          
          .cc-profile-trigger {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

.cc-profile-trigger:hover {
  background: rgba(98, 165, 255, 0.08);
  border-color: rgba(98, 165, 255, 0.18);
}

.cc-profile-trigger:focus-visible {
  outline: 2px solid #62a5ff;
  outline-offset: 3px;
}

.cc-profile-arrow {
  flex-shrink: 0;
  color: #8292ad;
  opacity: 0;
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.cc-profile-trigger:hover .cc-profile-arrow {
  opacity: 1;
  transform: translateX(2px);
}

          .cc-nav {
            flex-direction: row;
            gap: 6px;
            margin-top: 14px;
            overflow-x: auto;
          }

          .cc-nav-item {
            justify-content: center;
            width: auto;
            min-height: 38px;
            padding: 0 11px;
            white-space: nowrap;
          }

          .cc-main {
            padding: 22px 20px 32px;
          }

          .cc-hero {
            grid-template-columns: minmax(0, 1fr) minmax(180px, 0.8fr);
          }
        }

        @media (max-width: 600px) {
          .cc-main {
            padding: 17px 14px 25px;
          }

          .cc-topbar {
            align-items: flex-start;
            padding-bottom: 17px;
          }

          .cc-topbar-actions {
            flex-wrap: wrap;
            justify-content: flex-end;
          }

          .cc-network-pill {
            display: none;
          }

          .cc-refresh-btn,
          .cc-logout-btn {
            min-height: 35px;
            padding: 0 10px;
            font-size: 11px;
          }

          .cc-heading {
            display: block;
            margin: 25px 0 20px;
          }

          .cc-heading h1 {
            font-size: 27px;
          }

          .cc-date-label {
            margin-top: 12px;
          }

          .cc-hero {
            grid-template-columns: 1fr;
            padding: 23px 20px;
            gap: 4px;
          }

          .cc-hero h2 {
            font-size: 24px;
          }

          .cc-network-art {
            min-height: 125px;
            max-height: 150px;
          }

          .cc-network-art svg {
            max-height: 150px;
          }

          .cc-stat-grid {
            grid-template-columns: 1fr;
            gap: 10px;
            margin-top: 13px;
          }

          .cc-stat-card {
            padding: 14px 16px;
          }

          .cc-stat-top {
            align-items: center;
          }

          .cc-stat-value {
            margin-top: 10px;
            font-size: 24px;
          }

          .cc-stat-foot {
            margin-top: 6px;
          }

          .cc-section {
            margin-top: 25px;
          }

          .cc-section-title {
            font-size: 15px;
          }

          .cc-users-table-head {
            display: none;
          }

          .cc-user-row {
            grid-template-columns: minmax(0, 1fr) auto;
            gap: 10px;
            padding: 13px;
          }

          .cc-server-label {
            display: none;
          }

          .cc-status-badge {
            grid-column: 2;
            grid-row: 1;
          }

          .cc-modules-grid {
            grid-template-columns: 1fr;
            gap: 12px;
          }

          .cc-module-card {
            padding: 17px;
          }

          .cc-module-card h3 {
            margin-top: 14px;
          }

          .cc-module-card p {
            min-height: 0;
          }

          .cc-module-link {
            margin-top: 13px;
          }

          .cc-footer {
            align-items: flex-start;
            flex-direction: column;
          }
        }

        @media (prefers-reduced-motion: reduce) {
          .cc-dashboard *,
          .cc-dashboard *::before,
          .cc-dashboard *::after {
            animation-duration: 0.01ms !important;
            transition-duration: 0.01ms !important;
          }
        }
      `}</style>

      <div className="cc-layout">
        {/* Sidebar */}
        <aside className="cc-sidebar">
          <div className="cc-brand">
            <div className="cc-brand-icon">
              <Cloud size={24} />
            </div>

            <div>
              <h2 className="cc-brand-name">CloudChat</h2>
              <p className="cc-brand-subtitle">Distributed Network</p>
            </div>
          </div>

          <p className="cc-nav-label">Workspace</p>

          <nav className="cc-nav" aria-label="Main navigation">
            <button
              type="button"
              className="cc-nav-item cc-nav-active"
              onClick={() => navigate("/dashboard")}
            >
              <span className="cc-nav-icon">
                <Activity size={18} />
              </span>
              Overview
            </button>

            <button
              type="button"
              className="cc-nav-item"
              onClick={() => navigate("/chat")}
            >
              <span className="cc-nav-icon">
                <MessageCircle size={18} />
              </span>
              Private Chat
            </button>

            <button
              type="button"
              className="cc-nav-item"
              onClick={() => navigate("/groups")}
            >
              <span className="cc-nav-icon">
                <Users size={18} />
              </span>
              Groups
            </button>

            <button
              type="button"
              className="cc-nav-item"
              onClick={() => navigate("/file-sharing")}
            >
              <span className="cc-nav-icon">
                <File size={18} />
              </span>
              File Sharing
            </button>
          </nav>

          <div className="cc-sidebar-card">
            <div className="cc-sidebar-card-icon">
              <Zap size={19} />
            </div>

            <h3>Distributed Messaging</h3>

            <p>
              Communicate across connected servers with CloudChat&apos;s
              messaging infrastructure.
            </p>
          </div>

          <button
            type="button"
            className="cc-sidebar-footer cc-profile-trigger"
            onClick={() => navigate("/profile")}
            aria-label="Open user profile"
            title="Open User Profile"
          >
            <div className="cc-avatar">
              {username.charAt(0).toUpperCase() || "U"}
            </div>

            <div className="cc-user-info">
              <div className="cc-user-name">{username || "Guest"}</div>
              <div className="cc-user-role">CloudChat user</div>
            </div>

            <ArrowRight size={15} className="cc-profile-arrow" />
          </button>
        </aside>

        {/* Main dashboard */}
        <main className="cc-main">
          <header className="cc-topbar">
            <div className="cc-breadcrumb">
              Workspace <span>/ Overview</span>
            </div>

            <div className="cc-topbar-actions">
              <div className="cc-network-pill">
                <span className="cc-live-dot" />
                CloudChat Workspace
              </div>

              <button
                type="button"
                className="cc-refresh-btn"
                onClick={handleRefresh}
                disabled={refreshing}
              >
                <RefreshCw size={15} className={refreshing ? "cc-spin" : ""} />
                {refreshing ? "Refreshing" : "Refresh"}
              </button>

              <button
                type="button"
                className="cc-logout-btn"
                onClick={handleLogout}
              >
                <LogOut size={15} />
                Logout
              </button>
            </div>
          </header>

          <section className="cc-heading">
            <div>
              <p className="cc-eyebrow">
                <Activity size={14} />
                Workspace Overview
              </p>

              <h1>Welcome back, {username || "User"}</h1>

              <p>
                Monitor your messaging workspace and stay connected to your
                network.
              </p>
            </div>

            <div className="cc-date-label">Live workspace overview</div>
          </section>

          {/* Network hero */}
          <section className="cc-hero">
            <div className="cc-hero-copy">
              <div className="cc-hero-tag">
                <Wifi size={13} />
                Distributed messaging
              </div>

              <h2>Your conversations, connected everywhere.</h2>

              <p>
                Access private conversations, collaborate in groups and share
                files through your CloudChat workspace.
              </p>

              <button
                type="button"
                className="cc-hero-button"
                onClick={() => navigate("/chat")}
              >
                Open Private Chat
                <ArrowRight size={16} />
              </button>
            </div>

            <div
              className="cc-network-art"
              aria-label="Illustration of a connected network"
            >
              <svg viewBox="0 0 340 200" role="img">
                <defs>
                  <linearGradient
                    id="cc-line-gradient"
                    x1="0"
                    y1="0"
                    x2="1"
                    y2="1"
                  >
                    <stop offset="0%" stopColor="#69b5ff" stopOpacity="0.8" />
                    <stop
                      offset="100%"
                      stopColor="#54e0d1"
                      stopOpacity="0.55"
                    />
                  </linearGradient>

                  <linearGradient
                    id="cc-core-gradient"
                    x1="0"
                    y1="0"
                    x2="1"
                    y2="1"
                  >
                    <stop offset="0%" stopColor="#8ad4ff" />
                    <stop offset="100%" stopColor="#5ce0d0" />
                  </linearGradient>
                </defs>

                <circle
                  cx="170"
                  cy="100"
                  r="79"
                  fill="none"
                  stroke="#77baff"
                  strokeOpacity="0.1"
                  strokeDasharray="4 8"
                />

                <circle
                  cx="170"
                  cy="100"
                  r="57"
                  fill="none"
                  stroke="#77baff"
                  strokeOpacity="0.14"
                />

                <g
                  stroke="url(#cc-line-gradient)"
                  strokeWidth="1.5"
                  fill="none"
                >
                  <path d="M170 100 L75 43" />
                  <path d="M170 100 L267 44" />
                  <path d="M170 100 L286 126" />
                  <path d="M170 100 L225 170" />
                  <path d="M170 100 L78 157" />
                  <path d="M75 43 L267 44" strokeOpacity="0.23" />
                  <path d="M78 157 L225 170" strokeOpacity="0.23" />
                  <path d="M267 44 L286 126" strokeOpacity="0.23" />
                </g>

                <g>
                  <circle
                    cx="170"
                    cy="100"
                    r="35"
                    fill="#4a9dce"
                    fillOpacity="0.12"
                  />
                  <circle
                    cx="170"
                    cy="100"
                    r="27"
                    fill="#19374a"
                    stroke="#73d7e6"
                    strokeOpacity="0.7"
                  />
                  <path
                    d="M157 87 Q157 83 162 83 H179 Q184 83 184 88 V101 Q184 106 179 106 H170 L162 112 V106 H162 Q157 106 157 101 Z"
                    fill="url(#cc-core-gradient)"
                  />
                  <circle cx="165" cy="94" r="2" fill="#173148" />
                  <circle cx="171" cy="94" r="2" fill="#173148" />
                  <circle cx="177" cy="94" r="2" fill="#173148" />
                </g>

                <g>
                  <circle
                    cx="75"
                    cy="43"
                    r="19"
                    fill="#142b43"
                    stroke="#71baff"
                    strokeOpacity="0.7"
                  />
                  <circle cx="75" cy="39" r="5" fill="#8acaff" />
                  <path d="M65 51 Q65 43 75 43 Q85 43 85 51" fill="#8acaff" />

                  <circle
                    cx="267"
                    cy="44"
                    r="19"
                    fill="#142b43"
                    stroke="#70dcd2"
                    strokeOpacity="0.7"
                  />
                  <circle cx="267" cy="40" r="5" fill="#76e4d7" />
                  <path
                    d="M257 52 Q257 44 267 44 Q277 44 277 52"
                    fill="#76e4d7"
                  />

                  <circle
                    cx="286"
                    cy="126"
                    r="17"
                    fill="#142b43"
                    stroke="#71baff"
                    strokeOpacity="0.7"
                  />
                  <path d="M278 120 H294 V132 H278 Z" fill="#8acaff" />
                  <path
                    d="M281 124 H291 M281 128 H288"
                    stroke="#142b43"
                    strokeWidth="1.5"
                  />

                  <circle
                    cx="225"
                    cy="170"
                    r="17"
                    fill="#142b43"
                    stroke="#70dcd2"
                    strokeOpacity="0.7"
                  />
                  <path
                    d="M216 165 Q216 161 220 161 H230 Q234 161 234 165 V173 H216 Z"
                    fill="#76e4d7"
                  />
                  <path d="M220 177 H230" stroke="#76e4d7" strokeWidth="2" />

                  <circle
                    cx="78"
                    cy="157"
                    r="18"
                    fill="#142b43"
                    stroke="#71baff"
                    strokeOpacity="0.7"
                  />
                  <circle cx="78" cy="152" r="4" fill="#8acaff" />
                  <path
                    d="M69 166 Q69 158 78 158 Q87 158 87 166"
                    fill="#8acaff"
                  />
                </g>

                <g fill="#a9dfff">
                  <circle cx="122" cy="72" r="2.5" />
                  <circle cx="222" cy="71" r="2.5" />
                  <circle cx="231" cy="113" r="2.5" />
                  <circle cx="196" cy="136" r="2.5" />
                  <circle cx="120" cy="128" r="2.5" />
                </g>
              </svg>
            </div>
          </section>

          {/* Statistics */}
          <section className="cc-stat-grid" aria-label="Workspace statistics">
            <div className="cc-stat-card">
              <div className="cc-stat-top">
                <span className="cc-stat-label">Online Users</span>

                <div className="cc-stat-icon blue">
                  <Users size={19} />
                </div>
              </div>

              <div className="cc-stat-value">
                {loading ? "..." : onlineUsers.length}
              </div>

              <div className="cc-stat-foot">
                <span className="cc-online-dot" />
                Connected users reported by the API
              </div>
            </div>

            <div className="cc-stat-card">
              <div className="cc-stat-top">
                <span className="cc-stat-label">Current Server</span>

                <div className="cc-stat-icon cyan">
                  <Server size={19} />
                </div>
              </div>

              <div className="cc-stat-value cc-stat-server">{serverId}</div>

              <div className="cc-stat-foot">
                <Activity size={12} />
                Active chat server
              </div>
            </div>

            <div className="cc-stat-card">
              <div className="cc-stat-top">
                <span className="cc-stat-label">API Connection</span>

                <div className="cc-stat-icon green">
                  {error ? <Wifi size={19} /> : <CheckCircle2 size={19} />}
                </div>
              </div>

              <div className="cc-stat-value" style={{ fontSize: "24px" }}>
                {error ? "Check status" : loading ? "Checking..." : "Available"}
              </div>

              <div className="cc-stat-foot">
                <span
                  className="cc-online-dot"
                  style={{
                    background: error
                      ? "#fb7185"
                      : loading
                        ? "#fbbf24"
                        : "#45dfa2",
                    boxShadow: "none",
                  }}
                />
                {error ? "API request failed" : "Latest API request succeeded"}
              </div>
            </div>
          </section>

          {error && (
            <div className="cc-error" role="alert">
              <strong>Unable to refresh workspace data.</strong> {error}
            </div>
          )}

          {/* Online users */}
          <section className="cc-section">
            <div className="cc-section-header">
              <div>
                <h2 className="cc-section-title">Connected Users</h2>
                <p className="cc-section-subtitle">
                  Users currently returned by the CloudChat online-users API.
                </p>
              </div>

              <div className="cc-section-count">
                <span className="cc-online-dot" />
                {loading ? "Loading..." : `${onlineUsers.length} users`}
              </div>
            </div>

            <div className="cc-users-panel">
              <div className="cc-users-table-head">
                <span>User</span>
                <span>Server</span>
                <span>Status</span>
              </div>

              {loading ? (
                <div className="cc-empty-state">
                  <div className="cc-empty-icon">
                    <RefreshCw size={20} className="cc-spin" />
                  </div>
                  <h3>Loading connected users</h3>
                  <p>Retrieving the latest information from CloudChat.</p>
                </div>
              ) : onlineUsers.length === 0 ? (
                <div className="cc-empty-state">
                  <div className="cc-empty-icon">
                    <Users size={21} />
                  </div>
                  <h3>No users currently online</h3>
                  <p>When users connect, their information will appear here.</p>
                </div>
              ) : (
                onlineUsers.map((user, index) => (
                  <div
                    className="cc-user-row"
                    key={`${user.username}-${index}`}
                  >
                    <div className="cc-user-cell">
                      <div className="cc-avatar">
                        {(user.username || "?").charAt(0).toUpperCase()}
                      </div>

                      <div className="cc-user-info">
                        <div className="cc-user-cell-name">{user.username}</div>
                        <div className="cc-user-cell-sub">CloudChat member</div>
                      </div>
                    </div>

                    <div className="cc-server-label">
                      <Server size={13} />
                      {user.server || "Unknown server"}
                    </div>

                    <div className="cc-status-badge">
                      <span className="cc-online-dot" />
                      Online
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>

          {/* Workspace modules */}
          <section className="cc-section">
            <div className="cc-section-header">
              <div>
                <h2 className="cc-section-title">Your Workspace</h2>
                <p className="cc-section-subtitle">
                  Choose a service to continue working in CloudChat.
                </p>
              </div>
            </div>

            <div className="cc-modules-grid">
              {modules.map((module) => {
                const ModuleIcon = module.icon;

                return (
                  <button
                    key={module.path}
                    type="button"
                    className="cc-module-card"
                    onClick={() => navigate(module.path)}
                  >
                    <div className="cc-module-top">
                      <div className={`cc-module-icon ${module.accent}`}>
                        <ModuleIcon size={21} />
                      </div>

                      <span className="cc-module-number">{module.number}</span>
                    </div>

                    <h3>{module.title}</h3>
                    <p>{module.description}</p>

                    <div className="cc-module-link">
                      <span>{module.label}</span>
                      <ArrowUpRight size={16} />
                    </div>
                  </button>
                );
              })}
            </div>
          </section>

          {/* Footer */}
          <footer className="cc-footer">
            <div className="cc-footer-brand">
              <Cloud size={15} />
              CloudChat · Distributed Messaging System
            </div>

            <div className="cc-footer-status">
              <span className="cc-online-dot" />
              Dashboard refreshes every 10 seconds
            </div>
          </footer>
        </main>
      </div>
    </div>
  );
}

export default Dashboard;
