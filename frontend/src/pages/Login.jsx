import { useState } from "react";
import { Eye, EyeOff, LockKeyhole, UserRound } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { loginUser } from "../services/api";

function Login() {
  const navigate = useNavigate();

  const [showPassword, setShowPassword] = useState(false);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLogin = async (event) => {
    event.preventDefault();

    setError("");

    if (!username.trim() || !password) {
      setError("Please enter username and password.");
      return;
    }

    try {
      setLoading(true);

      const result = await loginUser(username.trim(), password);

      console.log("Login successful:", result);

      // Store logged-in username for the frontend session
      sessionStorage.setItem("cloudchat_username", result.username);

      // Redirect after successful login
      navigate("/dashboard");
    } catch (error) {
      console.error("Login failed:", error);

      setError(error.message || "Invalid username or password.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="cloudchat-app">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />

      <main className="auth-page">
        <section className="auth-card">
          {/* Header */}
          <div className="auth-header">
            <div>
              <h2>Welcome back</h2>

              <p>Secure access to your distributed workspace</p>
            </div>

            <LockKeyhole className="auth-lock" size={24} />
          </div>

          {/* Authentication Tabs */}
          <div className="auth-tabs">
            <button type="button" className="auth-tab active">
              Sign in
            </button>

            <button
              type="button"
              className="auth-tab"
              onClick={() => navigate("/register")}
            >
              Create account
            </button>
          </div>

          {/* Login Form */}
          <form onSubmit={handleLogin}>
            {/* Username */}
            <div className="form-group">
              <label htmlFor="login-username">Email or Username</label>

              <div className="input-icon-wrapper">
                <UserRound size={18} />

                <input
                  id="login-username"
                  type="text"
                  name="username"
                  placeholder="you@team.io or username"
                  value={username}
                  onChange={(event) => {
                    setUsername(event.target.value);
                    setError("");
                  }}
                  autoComplete="username"
                  disabled={loading}
                  required
                />
              </div>
            </div>

            {/* Password */}
            <div className="form-group">
              <label htmlFor="login-password">Password</label>

              <div className="password-wrapper">
                <input
                  id="login-password"
                  type={showPassword ? "text" : "password"}
                  name="password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    setError("");
                  }}
                  autoComplete="current-password"
                  disabled={loading}
                  required
                />

                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((current) => !current)}
                  disabled={loading}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                </button>
              </div>
            </div>

            {/* Login Error */}
            {error && <div className="auth-error">{error}</div>}

            {/* Options */}
            <div className="form-options">
              <label className="remember">
                <input type="checkbox" disabled={loading} />

                <span>Keep me signed in</span>
              </label>

              <button
                type="button"
                className="forgot"
                onClick={() =>
                  alert("Forgot password will be connected later.")
                }
                disabled={loading}
              >
                Forgot password?
              </button>
            </div>

            {/* Login Button */}
            <button type="submit" className="login-button" disabled={loading}>
              {loading ? "Signing in..." : "Sign in to CloudChat"}
            </button>

            {/* Divider */}
            <div className="divider">
              <span>OR</span>
            </div>

            {/* Google Login */}
            <button
              type="button"
              className="google-button"
              onClick={() => alert("Google Sign-In will be connected later.")}
              disabled={loading}
            >
              <span className="google-icon">G</span>
              Continue with Google
            </button>

            {/* Admin Login */}
            <button
              type="button"
              className="admin-link"
              onClick={() => navigate("/admin/login")}
              disabled={loading}
            >
              Administrator login
            </button>
          </form>

          {/* Back Button */}
          <button
            type="button"
            className="back-button"
            onClick={() => navigate("/")}
            disabled={loading}
          >
            ← Back to CloudChat
          </button>
        </section>
      </main>
    </div>
  );
}

export default Login;
