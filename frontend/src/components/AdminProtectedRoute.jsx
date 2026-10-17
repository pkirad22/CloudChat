import { useEffect, useState } from "react";
import { Navigate, Outlet } from "react-router-dom";

const API_BASE_URL = "http://localhost:9000";

export default function AdminProtectedRoute() {
  const [status, setStatus] = useState("checking");

  useEffect(() => {
    let active = true;

    const validateSession = async () => {
      const token = sessionStorage.getItem("cloudchat_admin_token");
      const expiresAt = Number(
        sessionStorage.getItem("cloudchat_admin_expires_at"),
      );

      if (!token || !expiresAt || Date.now() >= expiresAt) {
        sessionStorage.removeItem("cloudchat_admin_token");
        sessionStorage.removeItem("cloudchat_admin_id");
        sessionStorage.removeItem("cloudchat_admin_expires_at");

        if (active) setStatus("unauthorized");
        return;
      }

      try {
        const response = await fetch(`${API_BASE_URL}/api/admin/session`, {
          method: "GET",
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });

        if (!response.ok) {
          sessionStorage.removeItem("cloudchat_admin_token");
          sessionStorage.removeItem("cloudchat_admin_id");
          sessionStorage.removeItem("cloudchat_admin_expires_at");

          if (active) setStatus("unauthorized");
          return;
        }

        const data = await response.json();

        if (active) {
          setStatus(data.success ? "authorized" : "unauthorized");
        }
      } catch {
        // Fail closed if the API cannot validate the session.
        if (active) setStatus("error");
      }
    };

    validateSession();

    return () => {
      active = false;
    };
  }, []);

  if (status === "checking") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-300">
        Checking administrator session...
      </div>
    );
  }

  if (status === "unauthorized") {
    return <Navigate to="/admin/login" replace />;
  }

  if (status === "error") {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-950 p-6 text-center text-white">
        <h2 className="text-xl font-semibold">Unable to verify session</h2>
        <p className="text-sm text-slate-400">
          The CloudChat API is unavailable. Start the backend and try again.
        </p>
        <button
          onClick={() => window.location.reload()}
          className="rounded-lg bg-cyan-500 px-4 py-2 font-medium text-slate-950"
        >
          Retry
        </button>
      </div>
    );
  }

  return <Outlet />;
}
