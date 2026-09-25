import { useState } from "react";
import {
  ArrowLeft,
  Eye,
  EyeOff,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { useNavigate } from "react-router-dom";

function AdminLogin() {
  const navigate = useNavigate();

  const [showPassword, setShowPassword] =
    useState(false);

  const handleLogin = (event) => {
    event.preventDefault();

    navigate("/admin/dashboard");
  };

  return (
    <div className="cloudchat-app">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />

      <main className="auth-page">
        <section className="auth-card">
          <button
            type="button"
            className="back-button"
            onClick={() => navigate("/")}
          >
            <ArrowLeft size={17} />
            Back to CloudChat
          </button>

          <div className="admin-heading">
            <div className="admin-icon">
              <ShieldCheck size={27} />
            </div>

            <div>
              <h2>Administrator</h2>

              <p>
                Infrastructure monitoring console
              </p>
            </div>
          </div>

          <div className="admin-warning">
            <ShieldCheck size={17} />

            <span>
              Administrator authentication required.
            </span>
          </div>

          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label>Admin ID</label>

              <div className="input-icon-wrapper">
                <UserRound size={18} />

                <input
                  type="text"
                  placeholder="Administrator ID"
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label>Password</label>

              <div className="password-wrapper">
                <input
                  type={
                    showPassword
                      ? "text"
                      : "password"
                  }
                  placeholder="••••••••"
                  required
                />

                <button
                  type="button"
                  className="password-toggle"
                  onClick={() =>
                    setShowPassword(
                      (current) => !current
                    )
                  }
                >
                  {showPassword ? (
                    <EyeOff size={19} />
                  ) : (
                    <Eye size={19} />
                  )}
                </button>
              </div>
            </div>

            <button
              type="submit"
              className="login-button admin-login-button"
            >
              Enter Admin Console
            </button>
          </form>

          <p className="admin-note">
            Access to server monitoring, load balancing,
            network status and system events.
          </p>
        </section>
      </main>
    </div>
  );
}

export default AdminLogin;