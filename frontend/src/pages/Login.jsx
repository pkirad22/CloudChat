import { useEffect, useState } from "react";
import {
  Eye,
  EyeOff,
  LockKeyhole,
  UserRound,
  MessageCircle,
  ShieldCheck,
  Network,
  ArrowRight,
  CheckCircle2,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { loginUser, googleLogin } from "../services/api";
import { connectChat, connectChatWithGoogle } from "../services/chatSocket";

function Login() {
  const navigate = useNavigate();

  const [showPassword, setShowPassword] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    let cancelled = false;

    const initializeGoogleSignIn = () => {
      if (cancelled) return;

      if (!clientId) {
        setError(
          "Google Sign-In is not configured. Check your frontend .env file.",
        );
        return;
      }

      if (!window.google?.accounts?.id) {
        setError(
          "Google Sign-In could not be loaded. Please refresh the page.",
        );
        return;
      }

      const buttonContainer = document.getElementById("google-signin-button");

      if (!buttonContainer) return;

      window.google.accounts.id.initialize({
        client_id: clientId,

        callback: async (response) => {
          if (!response?.credential) {
            setError("Google did not return a valid sign-in credential.");
            return;
          }

          setError("");
          setLoading(true);

          try {
            // Step 1: Verify Google's credential with the Java backend.
            const data = await googleLogin(response.credential);

            if (!data.username || !data.ticket || !data.sessionId) {
              throw new Error(
                "The server did not return complete Google login information.",
              );
            }

            // Step 2: Store the CloudChat username.
            sessionStorage.setItem("cloudchat_username", data.username);

            // Step 3: Authenticate with the Java chat server using the ticket.
            await connectChatWithGoogle(
              data.username,
              data.ticket,
              data.sessionId,
            );

            navigate("/dashboard");
          } catch (err) {
            setError(err.message || "Google Sign-In failed. Please try again.");
          } finally {
            setLoading(false);
          }
        },
      });

      buttonContainer.replaceChildren();

      window.google.accounts.id.renderButton(buttonContainer, {
        type: "standard",
        theme: "outline",
        size: "large",
        text: "continue_with",
        shape: "rectangular",
        width: 320,
        logo_alignment: "left",
      });
    };

    if (window.google?.accounts?.id) {
      initializeGoogleSignIn();
    } else {
      let script = document.querySelector(
        'script[src="https://accounts.google.com/gsi/client"]',
      );

      if (!script) {
        script = document.createElement("script");
        script.src = "https://accounts.google.com/gsi/client";
        script.async = true;
        script.defer = true;
        document.head.appendChild(script);
      }

      script.addEventListener("load", initializeGoogleSignIn);

      script.addEventListener(
        "error",
        () => {
          if (!cancelled) {
            setError("Unable to load Google Sign-In. Check your connection.");
          }
        },
        { once: true },
      );
    }

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const handleLogin = async (event) => {
    event.preventDefault();
    setError("");

    if (!username.trim() || !password) {
      setError("Please enter your username and password.");
      return;
    }

    setLoading(true);

    try {
      const data = await loginUser(username.trim(), password);

      sessionStorage.setItem("cloudchat_username", data.username);

      await connectChat(username.trim(), password);

      navigate("/dashboard");
    } catch (err) {
      setError(err.message || "Login failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="cloudchat-app login-page">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />

      <main className="login-layout">
        {/* Left showcase panel */}
        <section className="login-showcase">
          <button
            type="button"
            className="login-brand"
            onClick={() => navigate("/")}
          >
            <span className="login-brand-icon">
              <MessageCircle size={25} />
            </span>

            <span className="login-brand-text">
              <strong>CloudChat</strong>
              <small>DISTRIBUTED MESSAGING</small>
            </span>
          </button>

          <div className="login-showcase-content">
            <div className="login-eyebrow">
              <span className="login-eyebrow-dot" />
              YOUR TEAM. ALWAYS CONNECTED.
            </div>

            <h1>
              Conversations
              <br />
              without <span>boundaries.</span>
            </h1>

            <p className="login-showcase-description">
              Connect with your team through a secure, distributed messaging
              platform built for reliable communication.
            </p>

            <div className="login-benefits">
              <div className="login-benefit">
                <span className="login-benefit-icon">
                  <ShieldCheck size={21} />
                </span>

                <div>
                  <strong>Secure communication</strong>
                  <p>Authentication-backed access to your workspace.</p>
                </div>
              </div>

              <div className="login-benefit">
                <span className="login-benefit-icon">
                  <Network size={21} />
                </span>

                <div>
                  <strong>Distributed architecture</strong>
                  <p>Designed around multiple chat servers.</p>
                </div>
              </div>

              <div className="login-benefit">
                <span className="login-benefit-icon">
                  <MessageCircle size={21} />
                </span>

                <div>
                  <strong>One connected workspace</strong>
                  <p>Private chats, group conversations, and file sharing.</p>
                </div>
              </div>
            </div>
          </div>

          <div className="login-showcase-footer">
            <span>
              <CheckCircle2 size={15} />
              Built for connected teams
            </span>

            <span>CloudChat © 2026</span>
          </div>
        </section>

        {/* Right login panel */}
        <section className="login-form-panel">
          <div className="login-card">
            <div className="login-mobile-brand">
              <span className="login-brand-icon">
                <MessageCircle size={23} />
              </span>
              <strong>CloudChat</strong>
            </div>

            <div className="login-card-heading">
              <div className="login-heading-icon">
                <LockKeyhole size={23} />
              </div>

              <div>
                <h2>Welcome back</h2>
                <p>Sign in to continue to your workspace.</p>
              </div>
            </div>

            <div className="auth-tabs login-tabs">
              <button
                type="button"
                className="auth-tab active"
                aria-current="page"
              >
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

            <form onSubmit={handleLogin}>
              <div className="form-group login-form-group">
                <label htmlFor="login-username">Email or username</label>

                <div className="input-icon-wrapper login-input">
                  <input
                    id="login-username"
                    type="text"
                    name="username"
                    placeholder="Enter your email or username"
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

              <div className="form-group login-form-group">
                <label htmlFor="login-password">Password</label>

                <div className="password-wrapper login-input">
                  <LockKeyhole size={18} className="login-password-icon" />

                  <input
                    id="login-password"
                    type={showPassword ? "text" : "password"}
                    name="password"
                    placeholder="Enter your password"
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
                    aria-label={
                      showPassword ? "Hide password" : "Show password"
                    }
                  >
                    {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                  </button>
                </div>
              </div>

              {error && (
                <div className="auth-error login-error" role="alert">
                  {error}
                </div>
              )}

              <div className="form-options login-form-options">
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

              <button
                type="submit"
                className="login-button login-submit"
                disabled={loading}
              >
                <span>
                  {loading ? "Signing in..." : "Sign in to CloudChat"}
                </span>

                {!loading && <ArrowRight size={18} />}
              </button>

              <div className="divider login-divider">
                <span>OR CONTINUE WITH</span>
              </div>

              <div
                id="google-signin-button"
                className="google-button login-google"
                aria-label="Continue with Google"
                aria-busy={loading}
                style={{
                  display: "flex",
                  justifyContent: "center",
                  opacity: loading ? 0.6 : 1,
                  pointerEvents: loading ? "none" : "auto",
                }}
              />

              <button
                type="button"
                className="admin-link login-admin-link"
                onClick={() => navigate("/admin/login")}
                disabled={loading}
              >
                <ShieldCheck size={17} />
                Sign in as administrator
              </button>
            </form>

            <div className="login-security-note">
              <LockKeyhole size={14} />
              <span>Your workspace is protected by authentication.</span>
            </div>

            <button
              type="button"
              className="back-button login-back"
              onClick={() => navigate("/")}
              disabled={loading}
            >
              ← Back to CloudChat
            </button>
          </div>

          <p className="login-panel-footer">
            Secure access to your distributed workspace
          </p>
        </section>
      </main>
    </div>
  );
}

export default Login;
