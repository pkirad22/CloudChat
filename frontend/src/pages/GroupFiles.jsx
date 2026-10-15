import { useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  FolderOpen,
  MessageCircle,
  UserRound,
  RefreshCw,
} from "lucide-react";
import { useState } from "react";

import GroupFileSharing from "../components/GroupFileSharing";

export default function GroupFiles() {
  const navigate = useNavigate();

  const username = sessionStorage.getItem("cloudchat_username") || "";
  const selectedGroup =
    sessionStorage.getItem("cloudchat_selected_group") || "";

  const handleBack = () => {
    navigate("/group-chat");
  };

  if (!username || !selectedGroup) {
    return (
      <div className="cloudchat-page">
        <div className="auth-card">
          <div style={{ padding: "32px", textAlign: "center" }}>
            <FolderOpen size={42} />
            <h2>Group Files</h2>
            <p>No active group was found.</p>

            <button
              onClick={() => navigate("/groups")}
              style={{
                padding: "11px 18px",
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

  return (
    <div
      style={{
        minHeight: "100vh",
        background:
          "radial-gradient(circle at top left, rgba(59,130,246,0.14), transparent 30%), #07111f",
        color: "#fff",
      }}
    >
      <header
        style={{
          minHeight: "70px",
          padding: "14px 24px",
          display: "flex",
          alignItems: "center",
          gap: "14px",
          borderBottom: "1px solid rgba(255,255,255,0.08)",
          background: "rgba(7,17,31,0.88)",
          position: "sticky",
          top: 0,
          zIndex: 20,
          backdropFilter: "blur(14px)",
        }}
      >
        <button
          onClick={handleBack}
          title="Back to Group Chat"
          style={{
            width: "42px",
            height: "42px",
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

        <MessageCircle size={25} />

        <div style={{ flex: 1 }}>
          <div style={{ fontSize: "18px", fontWeight: 700 }}>CloudChat</div>

          <div style={{ fontSize: "12px", opacity: 0.6 }}>
            Distributed Messaging
          </div>
        </div>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "12px",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              fontSize: "13px",
              opacity: 0.8,
            }}
          >
            <UserRound size={16} />
            {username}
          </div>

          <button
            type="button"
            onClick={() =>
              window.dispatchEvent(
                new CustomEvent("cloudchat:refresh-group-files"),
              )
            }
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
        </div>
      </header>

      <main
        style={{
          width: "100%",
          maxWidth: "1100px",
          margin: "0 auto",
          padding: "28px 20px",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "14px",
            marginBottom: "24px",
          }}
        >
          <div
            style={{
              width: "54px",
              height: "54px",
              borderRadius: "15px",
              background: "rgba(59,130,246,0.15)",
              border: "1px solid rgba(59,130,246,0.25)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <FolderOpen size={27} />
          </div>

          <div>
            <h1
              style={{
                margin: 0,
                fontSize: "24px",
                fontWeight: 750,
              }}
            >
              Group Files
            </h1>

            <p
              style={{
                margin: "6px 0 0",
                fontSize: "13px",
                opacity: 0.6,
              }}
            >
              {selectedGroup} · Files shared by all group members
            </p>
          </div>
        </div>

        <section
          style={{
            padding: "20px",
            borderRadius: "16px",
            border: "1px solid rgba(255,255,255,0.09)",
            background: "rgba(15,25,41,0.92)",
            boxShadow: "0 15px 45px rgba(0,0,0,0.16)",
          }}
        >
          <GroupFileSharing groupName={selectedGroup} username={username} />
        </section>

        <button
          onClick={handleBack}
          style={{
            marginTop: "20px",
            padding: "11px 16px",
            borderRadius: "10px",
            border: "1px solid rgba(255,255,255,0.1)",
            background: "rgba(255,255,255,0.05)",
            color: "#fff",
            display: "inline-flex",
            alignItems: "center",
            gap: "8px",
            cursor: "pointer",
          }}
        >
          <ArrowLeft size={16} />
          Back to Group Chat
        </button>
      </main>
    </div>
  );
}
