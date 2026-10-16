import { useEffect, useState } from "react";

import {
  ArrowRight,
  MessageCircle,
  Network,
  ShieldCheck,
  Users,
  FileText,
  Activity,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { googleLogin } from "../services/api";
import { connectChatWithGoogle } from "../services/chatSocket";
import Navbar from "../components/Navbar";
import FeatureCard from "../components/FeatureCard";

function Landing() {
  const navigate = useNavigate();

  const [googleLoading, setGoogleLoading] = useState(false);
  const [googleError, setGoogleError] = useState("");

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    let cancelled = false;

    const initializeGoogleSignIn = () => {
      if (cancelled) return;

      if (!clientId) {
        setGoogleError(
          "Google Sign-In is not configured. Check your frontend .env file.",
        );
        return;
      }

      if (!window.google?.accounts?.id) {
        setGoogleError(
          "Google Sign-In could not be loaded. Please refresh the page.",
        );
        return;
      }

      const buttonContainer = document.getElementById(
        "landing-google-signin-button",
      );

      if (!buttonContainer) return;

      window.google.accounts.id.initialize({
        client_id: clientId,

        callback: async (response) => {
          if (!response?.credential) {
            setGoogleError("Google did not return a valid sign-in credential.");
            return;
          }

          setGoogleError("");
          setGoogleLoading(true);

          try {
            // Verify the Google credential with the Java backend.
            const data = await googleLogin(response.credential);

            if (!data.username || !data.ticket || !data.sessionId) {
              throw new Error(
                "The server did not return complete Google login information.",
              );
            }

            // Save the authenticated CloudChat username.
            sessionStorage.setItem("cloudchat_username", data.username);

            // Authenticate with the Java chat server.
            await connectChatWithGoogle(
              data.username,
              data.ticket,
              data.sessionId,
            );

            // Open the dashboard after successful authentication.
            navigate("/dashboard");
          } catch (err) {
            setGoogleError(
              err.message || "Google Sign-In failed. Please try again.",
            );
          } finally {
            setGoogleLoading(false);
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
            setGoogleError(
              "Unable to load Google Sign-In. Check your connection.",
            );
          }
        },
        { once: true },
      );
    }

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  return (
    <div className="cloudchat-app landing-page">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />
      <Navbar />
      <main className="hero-section landing-hero">
        {/* LEFT: HERO CONTENT */}
        <section className="hero-content landing-content">
          <div className="status-pill">
            <span />
            Real-time · Multi-server
          </div>

          <p className="landing-eyebrow">DISTRIBUTED MESSAGING PLATFORM</p>

          <h1 className="landing-title">
            Message
            <br />
            across <span>every</span>
            <br />
            <span>node</span>, in real
            <br />
            time.
          </h1>

          <p className="hero-description landing-description">
            Private and group conversations that stay in sync across distributed
            servers — securely, instantly, everywhere.
          </p>

          <div className="landing-actions">
            <button
              type="button"
              className="login-button landing-primary"
              onClick={() => navigate("/login")}
            >
              Get started
              <ArrowRight size={18} />
            </button>

            <button
              type="button"
              className="landing-secondary"
              onClick={() =>
                document
                  .getElementById("landing-features")
                  ?.scrollIntoView({ behavior: "smooth" })
              }
            >
              Explore features
            </button>
          </div>

          <div className="landing-trust-row">
            <div className="landing-trust-item">
              <ShieldCheck size={17} />
              <span>Secure access</span>
            </div>

            <div className="landing-trust-item">
              <Network size={17} />
              <span>Distributed servers</span>
            </div>

            <div className="landing-trust-item">
              <Activity size={17} />
              <span>Real-time messaging</span>
            </div>
          </div>

          {/* FEATURES */}
          <section className="landing-features" id="landing-features">
            <div className="landing-section-heading">
              <span>BUILT FOR CONNECTIVITY</span>

              <h2>Everything connected.</h2>

              <p>
                Communication tools designed to work together across your
                distributed system.
              </p>
            </div>

            <div className="feature-grid landing-feature-grid">
              <FeatureCard
                icon={<MessageCircle size={22} />}
                title="Private & group"
                description="Live conversations for individuals and teams."
              />

              <FeatureCard
                icon={<ShieldCheck size={22} />}
                title="Secure authentication"
                description="Protected access to your workspace."
              />

              <FeatureCard
                icon={<Network size={22} />}
                title="Multi-server"
                description="Communication across connected servers."
              />
            </div>
          </section>
        </section>

        {/* RIGHT: AUTHENTICATION CARD */}
        <aside className="landing-side">
          <section className="auth-card landing-auth-card">
            <div className="landing-card-topline">
              <span className="landing-card-indicator" />
              CLOUDCHAT WORKSPACE
            </div>

            <div className="auth-header">
              <div>
                <h2>Welcome to CloudChat</h2>
                <p>Secure access to your distributed workspace</p>
              </div>

              <ShieldCheck className="auth-lock" size={24} />
            </div>

            <div className="landing-preview">
              <div className="landing-preview-icon">
                <MessageCircle size={22} />
              </div>

              <div className="landing-preview-copy">
                <strong>One connected workspace</strong>
                <span>People, groups and files in one place</span>
              </div>
            </div>

            <div className="landing-capabilities">
              <div>
                <Users size={17} />
                <span>Group conversations</span>
              </div>

              <div>
                <FileText size={17} />
                <span>File sharing</span>
              </div>

              <div>
                <Network size={17} />
                <span>Distributed communication</span>
              </div>
            </div>

            <button
              type="button"
              className="login-button landing-signin"
              onClick={() => navigate("/login")}
            >
              Sign in to CloudChat
              <ArrowRight size={17} />
            </button>

            <div className="divider">
              <span>OR</span>
            </div>

            <div
              id="landing-google-signin-button"
              className="google-button"
              aria-label="Continue with Google"
              aria-busy={googleLoading}
              style={{
                display: "flex",
                justifyContent: "center",
                opacity: googleLoading ? 0.6 : 1,
                pointerEvents: googleLoading ? "none" : "auto",
              }}
            />

            {googleError && (
              <p
                role="alert"
                style={{
                  color: "#dc2626",
                  fontSize: "13px",
                  marginTop: "8px",
                  textAlign: "center",
                }}
              >
                {googleError}
              </p>
            )}

            {googleLoading && (
              <p style={{ textAlign: "center", fontSize: "13px" }}>
                Signing in with Google...
              </p>
            )}

            <p className="landing-register-prompt">
              New to CloudChat?{" "}
              <button type="button" onClick={() => navigate("/register")}>
                Create an account
              </button>
            </p>

            <div className="landing-card-divider" />

            <button
              type="button"
              className="admin-link"
              onClick={() => navigate("/admin/login")}
            >
              <ShieldCheck size={15} />
              Administrator login
              <ArrowRight size={14} />
            </button>
          </section>

          <div className="landing-side-note">
            <Network size={16} />
            <span>Built for distributed communication</span>
          </div>
        </aside>
      </main>

      {/* SINGLE FOOTER — NO DUPLICATE NAVIGATION OR LOGOS */}
      <footer className="footer landing-footer">
        <span>© 2026 CloudChat Systems</span>

        <span className="landing-footer-center">
          Distributed messaging. One connected experience.
        </span>

        <button
          type="button"
          className="footer-admin"
          onClick={() => navigate("/admin/login")}
        >
          Administrator Login
        </button>
      </footer>
    </div>
  );
}

export default Landing;
