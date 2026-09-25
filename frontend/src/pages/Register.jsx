import { useRef, useState } from "react";
import { Eye, EyeOff, LockKeyhole, Mail, UserRound } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { registerUser, checkUsername } from "../services/api";

function Register() {
  const navigate = useNavigate();

  // =========================================================
  // UI STATE
  // =========================================================

  const [showPassword, setShowPassword] = useState(false);

  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // =========================================================
  // FORM STATE
  // =========================================================

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [loading, setLoading] = useState(false);

  // =========================================================
  // USERNAME AVAILABILITY STATE
  // =========================================================

  const [usernameStatus, setUsernameStatus] = useState("");

  const [checkingUsername, setCheckingUsername] = useState(false);

  // Used for debouncing username API requests
  const usernameCheckTimer = useRef(null);

  // =========================================================
  // EMAIL VALIDATION
  // =========================================================

  const isValidEmail = (value) => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  };

  // =========================================================
  // USERNAME VALIDATION
  // =========================================================

  const isValidUsername = (value) => {
    return /^[A-Za-z0-9_]{3,30}$/.test(value);
  };

  // =========================================================
  // USERNAME CHANGE + REAL-TIME AVAILABILITY CHECK
  // =========================================================

  const handleUsernameChange = (event) => {
    const value = event.target.value;

    setUsername(value);
    setUsernameStatus("");

    // Cancel previous API check
    if (usernameCheckTimer.current) {
      clearTimeout(usernameCheckTimer.current);
    }

    const cleanValue = value.trim();

    // Don't check invalid usernames
    if (!isValidUsername(cleanValue)) {
      setCheckingUsername(false);
      return;
    }

    setCheckingUsername(true);

    // Wait 500ms before checking the database
    usernameCheckTimer.current = setTimeout(async () => {
      try {
        const result = await checkUsername(cleanValue);

        if (result.available) {
          setUsernameStatus("available");
        } else {
          setUsernameStatus("taken");
        }
      } catch (error) {
        console.error("Username check failed:", error);

        setUsernameStatus("error");
      } finally {
        setCheckingUsername(false);
      }
    }, 500);
  };

  // =========================================================
  // PASSWORD VALIDATION
  // =========================================================

  const passwordChecks = {
    length: password.length >= 8,
    uppercase: /[A-Z]/.test(password),
    lowercase: /[a-z]/.test(password),
    number: /[0-9]/.test(password),
    special: /[^A-Za-z0-9]/.test(password),
    noSpaces: !/\s/.test(password),
  };

  // Only actual password-strength requirements
  // are counted in the strength score.
  const strengthScore = [
    passwordChecks.length,
    passwordChecks.uppercase,
    passwordChecks.lowercase,
    passwordChecks.number,
    passwordChecks.special,
  ].filter(Boolean).length;

  const passwordStrength =
    password.length === 0
      ? ""
      : !passwordChecks.noSpaces
        ? "Invalid"
        : strengthScore <= 2
          ? "Weak"
          : strengthScore <= 4
            ? "Medium"
            : "Strong";

  // =========================================================
  // REGISTER
  // =========================================================

  const handleRegister = async (event) => {
    event.preventDefault();

    const cleanUsername = username.trim();
    const cleanEmail = email.trim().toLowerCase();

    // -------------------------------------------------------
    // USERNAME VALIDATION
    // -------------------------------------------------------

    if (!isValidUsername(cleanUsername)) {
      alert(
        "Username must contain only letters, numbers and underscores, and must be 3–30 characters long.",
      );
      return;
    }

    // Don't allow registration while username is being checked
    if (checkingUsername) {
      alert("Please wait while we check username availability.");
      return;
    }

    // Don't allow an already-existing username
    if (usernameStatus === "taken") {
      alert("This username already exists. Please choose another username.");
      return;
    }

    // If the availability check failed
    if (usernameStatus === "error") {
      alert("Unable to verify username availability. Please try again.");
      return;
    }

    // -------------------------------------------------------
    // EMAIL VALIDATION
    // -------------------------------------------------------

    if (!isValidEmail(cleanEmail)) {
      alert("Please enter a valid email address.");
      return;
    }

    // -------------------------------------------------------
    // PASSWORD VALIDATION
    // -------------------------------------------------------

    if (!passwordChecks.length) {
      alert("Password must contain at least 8 characters.");
      return;
    }

    if (!passwordChecks.uppercase) {
      alert("Password must contain at least one uppercase letter.");
      return;
    }

    if (!passwordChecks.lowercase) {
      alert("Password must contain at least one lowercase letter.");
      return;
    }

    if (!passwordChecks.number) {
      alert("Password must contain at least one number.");
      return;
    }

    if (!passwordChecks.special) {
      alert("Password must contain at least one special character.");
      return;
    }

    if (!passwordChecks.noSpaces) {
      alert("Password cannot contain spaces.");
      return;
    }

    // -------------------------------------------------------
    // CONFIRM PASSWORD
    // -------------------------------------------------------

    if (password !== confirmPassword) {
      alert("Passwords do not match.");
      return;
    }

    // -------------------------------------------------------
    // REGISTER WITH BACKEND
    // -------------------------------------------------------

    try {
      setLoading(true);

      const result = await registerUser(cleanUsername, cleanEmail, password);

      console.log("Registration response:", result);

      if (result.success) {
        alert("Account created successfully. Please verify your email.");

        // Store registration information
        // for the OTP verification page.
        sessionStorage.setItem(
          "cloudchat_registration_username",
          cleanUsername,
        );

        sessionStorage.setItem("cloudchat_registration_email", cleanEmail);

        navigate("/verify-otp");
      } else {
        alert(result.message || "Registration failed.");
      }
    } catch (error) {
      console.error("Registration error:", error);

      alert(error.message || "Unable to connect to CloudChat server.");
    } finally {
      setLoading(false);
    }
  };

  // =========================================================
  // JSX
  // =========================================================

  return (
    <div className="cloudchat-app">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />

      <main className="auth-page">
        <section className="auth-card">
          {/* =================================================
              HEADER
          ================================================= */}

          <div className="auth-header">
            <div>
              <h2>Create your account</h2>

              <p>Join your distributed CloudChat workspace</p>
            </div>

            <LockKeyhole className="auth-lock" size={24} />
          </div>

          {/* =================================================
              AUTH TABS
          ================================================= */}

          <div className="auth-tabs">
            <button
              type="button"
              className="auth-tab"
              onClick={() => navigate("/login")}
            >
              Sign in
            </button>

            <button type="button" className="auth-tab active">
              Create account
            </button>
          </div>

          {/* =================================================
              REGISTER FORM
          ================================================= */}

          <form onSubmit={handleRegister}>
            {/* =================================================
                USERNAME
            ================================================= */}

            <div className="form-group">
              <label>Username</label>

              <div className="input-icon-wrapper">
                <UserRound size={18} />

                <input
                  type="text"
                  placeholder="Enter Username"
                  value={username}
                  onChange={handleUsernameChange}
                  maxLength={30}
                  required
                />
              </div>

              {/* USERNAME STATUS */}

              {username.length > 0 && (
                <small
                  style={{
                    color:
                      usernameStatus === "available"
                        ? "#4ade80"
                        : usernameStatus === "taken"
                          ? "#f87171"
                          : usernameStatus === "error"
                            ? "#facc15"
                            : "#94a3b8",
                  }}
                >
                  {checkingUsername
                    ? "Checking username..."
                    : usernameStatus === "available"
                      ? "✓ Username is available"
                      : usernameStatus === "taken"
                        ? "✕ Username already exists"
                        : usernameStatus === "error"
                          ? "Unable to check username"
                          : !isValidUsername(username.trim())
                            ? "Username must be 3–30 characters and use only letters, numbers and underscores."
                            : ""}
                </small>
              )}
            </div>

            {/* =================================================
                EMAIL
            ================================================= */}

            <div className="form-group">
              <label>Email address</label>

              <div className="input-icon-wrapper">
                <Mail size={18} />

                <input
                  type="email"
                  placeholder="you@team.io"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                />
              </div>

              {email.length > 0 && !isValidEmail(email) && (
                <small
                  style={{
                    color: "#f87171",
                  }}
                >
                  Please enter a valid email address.
                </small>
              )}
            </div>

            {/* =================================================
                PASSWORD
            ================================================= */}

            <div className="form-group">
              <label>Password</label>

              <div className="password-wrapper">
                <input
                  type={showPassword ? "text" : "password"}
                  placeholder="Create a strong password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />

                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((current) => !current)}
                >
                  {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                </button>
              </div>

              {/* =================================================
                  PASSWORD STRENGTH
              ================================================= */}

              {password.length > 0 && (
                <div className="password-strength">
                  <div className="password-strength-header">
                    <span>Password strength</span>

                    <strong>{passwordStrength}</strong>
                  </div>

                  <div className="password-strength-bar">
                    <div
                      className={`password-strength-fill ${passwordStrength.toLowerCase()}`}
                      style={{
                        width: `${(strengthScore / 5) * 100}%`,
                      }}
                    />
                  </div>

                  <div className="password-rules">
                    {/* LENGTH */}

                    <div
                      className={passwordChecks.length ? "rule valid" : "rule"}
                    >
                      {passwordChecks.length ? "✓" : "○"} At least 8 characters
                    </div>

                    {/* UPPERCASE */}

                    <div
                      className={
                        passwordChecks.uppercase ? "rule valid" : "rule"
                      }
                    >
                      {passwordChecks.uppercase ? "✓" : "○"} Uppercase letter
                    </div>

                    {/* LOWERCASE */}

                    <div
                      className={
                        passwordChecks.lowercase ? "rule valid" : "rule"
                      }
                    >
                      {passwordChecks.lowercase ? "✓" : "○"} Lowercase letter
                    </div>

                    {/* NUMBER */}

                    <div
                      className={passwordChecks.number ? "rule valid" : "rule"}
                    >
                      {passwordChecks.number ? "✓" : "○"} Number
                    </div>

                    {/* SPECIAL */}

                    <div
                      className={passwordChecks.special ? "rule valid" : "rule"}
                    >
                      {passwordChecks.special ? "✓" : "○"} Special character
                    </div>

                    {/* NO SPACES */}

                    <div
                      className={
                        passwordChecks.noSpaces ? "rule valid" : "rule"
                      }
                    >
                      {passwordChecks.noSpaces ? "✓" : "○"} No spaces
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* =================================================
                CONFIRM PASSWORD
            ================================================= */}

            <div className="form-group">
              <label>Confirm password</label>

              <div className="password-wrapper">
                <input
                  type={showConfirmPassword ? "text" : "password"}
                  placeholder="Repeat your password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  required
                />

                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowConfirmPassword((current) => !current)}
                >
                  {showConfirmPassword ? (
                    <EyeOff size={19} />
                  ) : (
                    <Eye size={19} />
                  )}
                </button>
              </div>

              {/* PASSWORD MATCH STATUS */}

              {confirmPassword.length > 0 && (
                <small
                  style={{
                    color: password === confirmPassword ? "#4ade80" : "#f87171",
                  }}
                >
                  {password === confirmPassword
                    ? "✓ Passwords match"
                    : "Passwords do not match"}
                </small>
              )}
            </div>

            {/* =================================================
                SUBMIT
            ================================================= */}

            <button
              type="submit"
              className="login-button"
              disabled={
                loading || checkingUsername || usernameStatus === "taken"
              }
            >
              {loading
                ? "Creating account..."
                : checkingUsername
                  ? "Checking username..."
                  : "Continue to email verification"}
            </button>

            {/* =================================================
                DIVIDER
            ================================================= */}

            <div className="divider">
              <span>OR</span>
            </div>

            {/* =================================================
                GOOGLE
            ================================================= */}

            <button
              type="button"
              className="google-button"
              onClick={() =>
                alert("Google registration will be connected later.")
              }
            >
              <span className="google-icon">G</span>
              Sign up with Google
            </button>
          </form>

          {/* =================================================
              BACK BUTTON
          ================================================= */}

          <button
            type="button"
            className="back-button"
            onClick={() => navigate("/")}
          >
            ← Back to CloudChat
          </button>
        </section>
      </main>
    </div>
  );
}

export default Register;
