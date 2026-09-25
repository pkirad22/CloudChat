import { MessageCircle } from "lucide-react";
import { useNavigate } from "react-router-dom";

function Navbar() {
  const navigate = useNavigate();

  return (
    <header className="navbar">
      <button
        type="button"
        className="brand brand-button"
        onClick={() => navigate("/")}
      >
        <div className="brand-logo">
          <MessageCircle size={22} />
        </div>

        <div>
          <div className="brand-name">CloudChat</div>
          <div className="brand-subtitle">DISTRIBUTED MESSAGING</div>
        </div>
      </button>

      <nav className="nav-links">
        <a href="#platform">Platform</a>
        <a href="#security">Security</a>
        <a href="#network">Network</a>

        <button
          type="button"
          className="nav-signin"
          onClick={() => navigate("/login")}
        >
          Sign in
        </button>
      </nav>
    </header>
  );
}

export default Navbar;
