import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ShieldCheck,
  LockKeyhole,
  Eye,
  EyeOff,
  ArrowLeft,
  LoaderCircle,
  AlertCircle,
} from "lucide-react";

const API_BASE_URL = "http://localhost:9000";

export default function AdminLogin() {
  const navigate = useNavigate();

  const [adminId, setAdminId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLogin = async (event) => {
    event.preventDefault();
    setError("");

    if (!adminId.trim() || !password) {
      setError("Please enter your administrator ID and password.");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          adminId: adminId.trim(),
          password,
        }),
      });

      const data = await response.json();

      if (!response.ok || !data.success || !data.token) {
        throw new Error(data.message || "Invalid administrator credentials.");
      }

      // Store the token for authenticated admin API requests.
      sessionStorage.setItem("cloudchat_admin_token", data.token);
      sessionStorage.setItem("cloudchat_admin_id", data.adminId);
      sessionStorage.setItem(
        "cloudchat_admin_expires_at",
        String(data.expiresAt),
      );

      navigate("/admin/dashboard", { replace: true });
    } catch (err) {
      setError(
        err.message === "Failed to fetch"
          ? "Cannot connect to the CloudChat API. Make sure the backend is running."
          : err.message || "Login failed. Please try again.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-white flex items-center justify-center p-5 relative overflow-hidden">
      {/* Background effects */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute -top-32 -left-24 h-96 w-96 rounded-full bg-cyan-500/10 blur-3xl" />
        <div className="absolute -bottom-32 -right-24 h-96 w-96 rounded-full bg-blue-600/10 blur-3xl" />

        <div
          className="absolute inset-0 opacity-[0.06]"
          style={{
            backgroundImage:
              "linear-gradient(rgba(148,163,184,0.4) 1px, transparent 1px), linear-gradient(90deg, rgba(148,163,184,0.4) 1px, transparent 1px)",
            backgroundSize: "40px 40px",
          }}
        />
      </div>

      <div className="relative z-10 w-full max-w-md">
        {/* Back navigation */}
        <button
          type="button"
          onClick={() => navigate("/")}
          className="mb-8 inline-flex items-center gap-2 text-sm text-slate-400 transition hover:text-white"
        >
          <ArrowLeft size={17} />
          Back to CloudChat
        </button>

        {/* Brand */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl border border-cyan-400/20 bg-cyan-400/10 shadow-lg shadow-cyan-950/30">
            <ShieldCheck size={34} className="text-cyan-400" />
          </div>

          <h1 className="text-3xl font-bold tracking-tight">
            CloudChat <span className="text-cyan-400">Admin</span>
          </h1>

          <p className="mt-2 text-sm text-slate-400">
            Secure infrastructure management console
          </p>
        </div>

        {/* Login card */}
        <div className="rounded-2xl border border-slate-800 bg-slate-900/90 p-7 shadow-2xl shadow-black/30 backdrop-blur-xl sm:p-8">
          <div className="mb-7">
            <h2 className="text-xl font-semibold">Administrator Login</h2>
            <p className="mt-1 text-sm text-slate-400">
              Sign in to manage CloudChat users and servers.
            </p>
          </div>

          {error && (
            <div
              role="alert"
              className="mb-5 flex items-start gap-3 rounded-xl border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-300"
            >
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-5">
            <div>
              <label
                htmlFor="adminId"
                className="mb-2 block text-sm font-medium text-slate-300"
              >
                Administrator ID
              </label>

              <div className="flex items-center gap-3 rounded-xl border border-slate-700 bg-slate-950 px-4 transition focus-within:border-cyan-500">
                <ShieldCheck size={19} className="shrink-0 text-slate-500" />

                <input
                  id="adminId"
                  type="text"
                  autoComplete="username"
                  value={adminId}
                  onChange={(event) => setAdminId(event.target.value)}
                  placeholder="Enter administrator ID"
                  className="w-full bg-transparent py-3.5 text-sm text-white outline-none placeholder:text-slate-600"
                  disabled={loading}
                  required
                />
              </div>
            </div>

            <div>
              <label
                htmlFor="adminPassword"
                className="mb-2 block text-sm font-medium text-slate-300"
              >
                Password
              </label>

              <div className="flex items-center gap-3 rounded-xl border border-slate-700 bg-slate-950 px-4 transition focus-within:border-cyan-500">
                <LockKeyhole size={19} className="shrink-0 text-slate-500" />

                <input
                  id="adminPassword"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Enter your password"
                  className="w-full bg-transparent py-3.5 text-sm text-white outline-none placeholder:text-slate-600"
                  disabled={loading}
                  required
                />

                <button
                  type="button"
                  onClick={() => setShowPassword((previous) => !previous)}
                  className="text-slate-500 transition hover:text-white"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-cyan-500 px-4 py-3.5 font-semibold text-slate-950 transition hover:bg-cyan-400 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading ? (
                <>
                  <LoaderCircle size={19} className="animate-spin" />
                  Authenticating...
                </>
              ) : (
                <>
                  Secure Login
                  <ShieldCheck size={19} />
                </>
              )}
            </button>
          </form>

          <div className="mt-6 flex items-center justify-center gap-2 text-xs text-slate-500">
            <LockKeyhole size={13} />
            Authorized administrators only
          </div>
        </div>

        <p className="mt-6 text-center text-xs text-slate-600">
          CloudChat Distributed Messaging System
        </p>
      </div>
    </div>
  );
}
