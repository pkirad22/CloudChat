import { MessageCircle, Network, ShieldCheck } from "lucide-react";
import { useNavigate } from "react-router-dom";

import Navbar from "../components/Navbar";
import FeatureCard from "../components/FeatureCard";

function Landing() {
  const navigate = useNavigate();

  return (
    <div className="cloudchat-app">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />

      <Navbar />

      <main className="hero-section">
        <section className="hero-content">
          <div className="status-pill">
            <span />
            Real-time · Multi-server
          </div>

          <h1>
            Message
            <br />
            across <span>every</span>
            <br />
            <span>node</span>, in real
            <br />
            time.
          </h1>

          <p className="hero-description">
            Private and group conversations that stay in sync across distributed
            servers — securely, instantly, everywhere.
          </p>

          <div className="feature-grid">
            <FeatureCard
              icon={<MessageCircle size={22} />}
              title="Private & group"
              description="Live threads for every team."
            />

            <FeatureCard
              icon={<ShieldCheck size={22} />}
              title="Secure auth"
              description="Protected sessions by default."
            />

            <FeatureCard
              icon={<Network size={22} />}
              title="Multi-server"
              description="Automatic resilient routing."
            />
          </div>
        </section>

        <section className="auth-card">
          <div className="auth-header">
            <div>
              <h2>Welcome to CloudChat</h2>

              <p>Secure access to your distributed workspace</p>
            </div>

            <ShieldCheck className="auth-lock" size={24} />
          </div>

          <button
            type="button"
            className="login-button"
            onClick={() => navigate("/login")}
          >
            Sign in to CloudChat
          </button>

          <div className="divider">
            <span>OR</span>
          </div>

          <button
            type="button"
            className="google-button"
            onClick={() => alert("Google Sign-In will be connected later.")}
          >
            <span className="google-icon">G</span>
            Continue with Google
          </button>

          <button
            type="button"
            className="admin-link"
            onClick={() => navigate("/admin/login")}
          >
            Administrator login
          </button>
        </section>
      </main>

      <footer className="footer">
        <span>© 2026 CloudChat Systems</span>

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
