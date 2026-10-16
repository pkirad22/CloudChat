import { useEffect, useRef, useState } from "react";
import { Eye, EyeOff, LockKeyhole, Mail, UserRound, Phone } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { registerUser, checkUsername, checkEmail } from "../services/api";

function Register() {
  const navigate = useNavigate();

  // UI state
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  // Form state
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [countryCode, setCountryCode] = useState("+91");
  const [mobileNumber, setMobileNumber] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [popup, setPopup] = useState({
    open: false,
    title: "",
    message: "",
    type: "info",
  });

  const showPopup = (message, type = "info", title = "") => {
    setPopup({
      open: true,
      title:
        title ||
        (type === "success"
          ? "Registration successful"
          : type === "error"
            ? "Unable to continue"
            : "CloudChat"),
      message,
      type,
    });
  };

  const closePopup = () => {
    setPopup((current) => ({ ...current, open: false }));
  };

  // Username availability state
  const [usernameStatus, setUsernameStatus] = useState("");
  const [checkingUsername, setCheckingUsername] = useState(false);
  const usernameCheckTimer = useRef(null);
  const usernameRequestId = useRef(0);

  // Email availability state
  const [emailStatus, setEmailStatus] = useState("");
  const [checkingEmail, setCheckingEmail] = useState(false);

  const emailCheckTimer = useRef(null);
  const emailRequestId = useRef(0);

  useEffect(() => {
    return () => {
      if (usernameCheckTimer.current) {
        clearTimeout(usernameCheckTimer.current);
      }
      usernameRequestId.current += 1;
    };
  }, []);

  useEffect(() => {
    return () => {
      if (usernameCheckTimer.current) {
        clearTimeout(usernameCheckTimer.current);
      }

      if (emailCheckTimer.current) {
        clearTimeout(emailCheckTimer.current);
      }

      usernameRequestId.current += 1;
      emailRequestId.current += 1;
    };
  }, []);

  // Validation
  const isValidEmail = (email) => {
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    return emailRegex.test(email.trim());
  };

  const isValidUsername = (value) => /^[A-Za-z0-9_]{3,30}$/.test(value);

  const isValidMobile = (value) => /^[0-9]{10}$/.test(value);

  // Username availability
  const handleUsernameChange = (event) => {
    const value = event.target.value;
    const cleanValue = value.trim();

    setUsername(value);
    setUsernameStatus("");

    if (usernameCheckTimer.current) {
      clearTimeout(usernameCheckTimer.current);
    }

    const requestId = ++usernameRequestId.current;

    if (!isValidUsername(cleanValue)) {
      setCheckingUsername(false);
      return;
    }

    setCheckingUsername(true);

    usernameCheckTimer.current = setTimeout(async () => {
      try {
        const result = await checkUsername(cleanValue);

        if (requestId !== usernameRequestId.current) return;

        setUsernameStatus(result.available ? "available" : "taken");
      } catch (error) {
        if (requestId !== usernameRequestId.current) return;

        console.error("Username check failed:", error);
        setUsernameStatus("error");
      } finally {
        if (requestId === usernameRequestId.current) {
          setCheckingUsername(false);
        }
      }
    }, 500);
  };

  const handleEmailChange = (event) => {
    const value = event.target.value;

    setEmail(value);
    setEmailStatus("");

    if (emailCheckTimer.current) {
      clearTimeout(emailCheckTimer.current);
    }

    const requestId = ++emailRequestId.current;
    const cleanEmail = value.trim().toLowerCase();

    if (!isValidEmail(cleanEmail)) {
      setCheckingEmail(false);
      return;
    }

    setCheckingEmail(true);

    emailCheckTimer.current = setTimeout(async () => {
      try {
        const result = await checkEmail(cleanEmail);

        if (requestId !== emailRequestId.current) return;

        setEmailStatus(result.available ? "available" : "taken");
      } catch (error) {
        if (requestId !== emailRequestId.current) return;

        console.error("Email availability check failed:", error);
        setEmailStatus("error");
      } finally {
        if (requestId === emailRequestId.current) {
          setCheckingEmail(false);
        }
      }
    }, 500);
  };

  // Password strength
  const passwordChecks = {
    length: password.length >= 8,
    uppercase: /[A-Z]/.test(password),
    lowercase: /[a-z]/.test(password),
    number: /[0-9]/.test(password),
    special: /[^A-Za-z0-9]/.test(password),
    noSpaces: !/\s/.test(password),
  };

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

  // Registration
  const handleRegister = async (event) => {
    event.preventDefault();

    const cleanUsername = username.trim();
    const cleanEmail = email.trim().toLowerCase();
    const cleanMobile = mobileNumber.trim();

    if (!isValidUsername(cleanUsername)) {
      showPopup(
        "Username must contain only letters, numbers and underscores, and must be 3–30 characters long.",
        "error",
        "Invalid username",
      );
      return;
    }

    if (checkingUsername) {
      showPopup("Please wait while we check username availability.", "info");
      return;
    }

    if (usernameStatus === "taken") {
      showPopup(
        "This username already exists. Please choose another.",
        "error",
        "Username unavailable",
      );
      return;
    }

    if (usernameStatus !== "available") {
      showPopup(
        "Please wait for a successful username availability check.",
        "info",
      );
      return;
    }

    if (!cleanEmail) {
      showPopup("Please enter your email address.", "error", "Email required");
      return;
    }

    if (!isValidEmail(cleanEmail)) {
      showPopup(
        "Please enter a valid email address, such as name@example.com.",
        "error",
        "Invalid email address",
      );
      return;
    }
    if (checkingEmail) {
      showPopup("Please wait while we check email availability.", "info");
      return;
    }

    if (emailStatus === "taken") {
      showPopup(
        "This email is already registered. Please use another email or sign in.",
        "error",
        "Email already registered",
      );
      return;
    }

    if (emailStatus !== "available") {
      showPopup(
        "Please wait for a successful email availability check.",
        "info",
      );
      return;
    }

    if (!isValidMobile(cleanMobile)) {
      showPopup("Please enter a valid 10-digit mobile number.", "error");
      return;
    }

    if (!passwordChecks.length) {
      showPopup("Password must contain at least 8 characters.", "error");
      return;
    }

    if (!passwordChecks.uppercase) {
      showPopup(
        "Password must contain at least one uppercase letter.",
        "error",
      );
      return;
    }

    if (!passwordChecks.lowercase) {
      showPopup(
        "Password must contain at least one lowercase letter.",
        "error",
      );
      return;
    }

    if (!passwordChecks.number) {
      showPopup("Password must contain at least one number.", "error");
      return;
    }

    if (!passwordChecks.special) {
      showPopup(
        "Password must contain at least one special character.",
        "error",
      );
      return;
    }

    if (!passwordChecks.noSpaces) {
      showPopup("Password cannot contain spaces.", "error");
      return;
    }

    if (password !== confirmPassword) {
      showPopup("Passwords do not match.", "error");
      return;
    }

    try {
      setLoading(true);

      const result = await registerUser(
        cleanUsername,
        cleanEmail,
        password,
        `${countryCode}${cleanMobile}`,
      );

      console.log("Registration response:", result);

      if (result.success) {
        sessionStorage.setItem(
          "cloudchat_registration_username",
          cleanUsername,
        );

        sessionStorage.setItem("cloudchat_registration_email", cleanEmail);
        sessionStorage.setItem("cloudchat_continue_to_otp", "true");

        showPopup(
          result.message ||
            "Your account has been created. Please verify your email.",
          "success",
          "Registration successful",
        );
      } else {
        showPopup(
          result.message || "Registration failed.",
          "error",
          "Registration failed",
        );
      }
    } catch (error) {
      console.error("Registration error:", error);

      showPopup(
        error.message || "Unable to connect to CloudChat server.",
        "error",
        "Registration failed",
      );
    } finally {
      setLoading(false);
    }
  };

  const usernameMessage = checkingUsername
    ? "Checking username..."
    : usernameStatus === "available"
      ? "✓ Username is available"
      : usernameStatus === "taken"
        ? "✕ Username already exists"
        : usernameStatus === "error"
          ? "Unable to check username. Try again."
          : username.length > 0 && !isValidUsername(username.trim())
            ? "Use 3–30 letters, numbers or underscores."
            : "";

  const usernameMessageColor =
    usernameStatus === "available"
      ? "#4ade80"
      : usernameStatus === "taken" || usernameStatus === "error"
        ? "#f87171"
        : "#94a3b8";

  return (
    <div className="cloudchat-app">
      <div className="bg-glow bg-glow-left" />
      <div className="bg-glow bg-glow-right" />

      <main className="auth-page">
        <section className="auth-card register-card">
          <div className="auth-header">
            <div>
              <span className="register-eyebrow">CLOUDCHAT ACCOUNT</span>
              <h2>Create your account</h2>
              <p>Join your distributed CloudChat workspace</p>
            </div>

            <LockKeyhole className="auth-lock" size={24} />
          </div>

          <div className="auth-tabs">
            <button
              type="button"
              className="auth-tab"
              onClick={() => navigate("/login")}
            >
              Sign in
            </button>

            <button
              type="button"
              className="auth-tab active"
              aria-current="page"
            >
              Create account
            </button>
          </div>

          <form onSubmit={handleRegister} className="register-form">
            {/* Username */}
            <div className="form-group">
              <label htmlFor="register-username">Username</label>

              <div className="input-icon-wrapper">
                <UserRound size={18} aria-hidden="true" />
                <input
                  id="register-username"
                  type="text"
                  placeholder="Choose a username"
                  value={username}
                  onChange={handleUsernameChange}
                  autoComplete="username"
                  maxLength={30}
                  minLength={3}
                  required
                />
              </div>

              {usernameMessage && (
                <small role="status" style={{ color: usernameMessageColor }}>
                  {usernameMessage}
                </small>
              )}
            </div>

            {/* Email */}
            <div className="form-group">
              <label htmlFor="register-email">Email address</label>

              <div className="input-icon-wrapper">
                <Mail size={18} aria-hidden="true" />
                <input
                  id="register-email"
                  type="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={handleEmailChange}
                  autoComplete="email"
                  required
                />
                {email.length > 0 && (
                  <small
                    role="status"
                    style={{
                      color:
                        emailStatus === "available"
                          ? "#4ade80"
                          : emailStatus === "taken" || emailStatus === "error"
                            ? "#f87171"
                            : "#94a3b8",
                    }}
                  >
                    {checkingEmail
                      ? "Checking email..."
                      : emailStatus === "available"
                        ? "✓ Email is available"
                        : emailStatus === "taken"
                          ? "✕ This email is already registered"
                          : emailStatus === "error"
                            ? "Unable to check email. Try again."
                            : !isValidEmail(email)
                              ? "Please enter a valid email address."
                              : ""}
                  </small>
                )}
              </div>

              {email.length > 0 && !isValidEmail(email) && (
                <small className="register-error">
                  Please enter a valid email address.
                </small>
              )}
            </div>

            {/* Mobile number */}
            <div className="form-group">
              <label htmlFor="register-mobile">Mobile number</label>

              <div className="input-icon-wrapper mobile-input-wrapper">
                <Phone size={18} aria-hidden="true" className="input-icon" />

                <div className="mobile-inputs-group">
                  <select
                    aria-label="Country calling code"
                    className="country-code-select"
                    value={countryCode}
                    onChange={(event) => setCountryCode(event.target.value)}
                  >
                    <option value="+91">+91</option>
                    <option value="+1">+1</option>
                    <option value="+44">+44</option>
                    <option value="+61">+61</option>
                    <option value="+971">+971</option>
                  </select>

                  <input
                    id="register-mobile"
                    type="tel"
                    inputMode="numeric"
                    placeholder="10-digit mobile number"
                    value={mobileNumber}
                    onChange={(event) =>
                      setMobileNumber(
                        event.target.value.replace(/\D/g, "").slice(0, 10),
                      )
                    }
                    autoComplete="tel-national"
                    maxLength={10}
                    pattern="[0-9]{10}"
                    aria-describedby="mobile-help"
                    required
                  />
                </div>
              </div>

              {mobileNumber.length > 0 && !isValidMobile(mobileNumber) && (
                <small className="register-error">
                  Enter exactly 10 digits.
                </small>
              )}
            </div>

            {/* Password */}
            <div className="form-group">
              <label htmlFor="register-password">Password</label>

              <div className="password-wrapper">
                <input
                  id="register-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Create a strong password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="new-password"
                  required
                />

                <button
                  type="button"
                  className="password-toggle"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  onClick={() => setShowPassword((current) => !current)}
                >
                  {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                </button>
              </div>

              {password.length > 0 && (
                <div className="password-strength">
                  <div className="password-strength-header">
                    <span>Password strength</span>
                    <strong>{passwordStrength}</strong>
                  </div>

                  <div
                    className="password-strength-bar"
                    role="progressbar"
                    aria-label="Password strength"
                    aria-valuemin={0}
                    aria-valuemax={5}
                    aria-valuenow={strengthScore}
                  >
                    <div
                      className={`password-strength-fill ${passwordStrength.toLowerCase()}`}
                      style={{
                        width: `${(strengthScore / 5) * 100}%`,
                      }}
                    />
                  </div>

                  <div className="password-rules">
                    {[
                      ["length", "At least 8 characters"],
                      ["uppercase", "Uppercase letter"],
                      ["lowercase", "Lowercase letter"],
                      ["number", "Number"],
                      ["special", "Special character"],
                      ["noSpaces", "No spaces"],
                    ].map(([key, label]) => (
                      <div
                        key={key}
                        className={passwordChecks[key] ? "rule valid" : "rule"}
                      >
                        {passwordChecks[key] ? "✓" : "○"} {label}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Confirm password */}
            <div className="form-group">
              <label htmlFor="register-confirm-password">
                Confirm password
              </label>

              <div className="password-wrapper">
                <input
                  id="register-confirm-password"
                  type={showConfirmPassword ? "text" : "password"}
                  placeholder="Re-enter your password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  autoComplete="new-password"
                  required
                />

                <button
                  type="button"
                  className="password-toggle"
                  aria-label={
                    showConfirmPassword
                      ? "Hide confirm password"
                      : "Show confirm password"
                  }
                  onClick={() => setShowConfirmPassword((current) => !current)}
                >
                  {showConfirmPassword ? (
                    <EyeOff size={19} />
                  ) : (
                    <Eye size={19} />
                  )}
                </button>
              </div>

              {confirmPassword.length > 0 && (
                <small
                  className={
                    password === confirmPassword
                      ? "register-success"
                      : "register-error"
                  }
                >
                  {password === confirmPassword
                    ? "✓ Passwords match"
                    : "Passwords do not match"}
                </small>
              )}
            </div>

            <button
              type="submit"
              className="login-button register-submit"
              disabled={
                loading ||
                checkingUsername ||
                checkingEmail ||
                usernameStatus !== "available"
              }
            >
              {loading
                ? "Creating account..."
                : checkingUsername
                  ? "Checking username..."
                  : "Continue to email verification"}
            </button>
          </form>
          <br />

          <button
            type="button"
            className="back-button"
            onClick={() => navigate("/")}
          >
            ← Back to CloudChat
          </button>
        </section>
      </main>

      {/* Custom CloudChat popup */}
      {popup.open && (
        <div className="cc-popup-overlay" onClick={closePopup}>
          <section
            className={`cc-popup cc-popup-${popup.type}`}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="cc-popup-title"
            aria-describedby="cc-popup-message"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="cc-popup-icon">
              {popup.type === "success"
                ? "✓"
                : popup.type === "error"
                  ? "!"
                  : "i"}
            </div>

            <h3 id="cc-popup-title">{popup.title}</h3>

            <p id="cc-popup-message">{popup.message}</p>

            <button
              type="button"
              className="cc-popup-button"
              onClick={() => {
                const shouldVerifyEmail = popup.type === "success";

                closePopup();

                if (shouldVerifyEmail) {
                  navigate("/verify-otp");
                }
              }}
            >
              {popup.type === "success" ? "Continue" : "OK"}
            </button>
          </section>
        </div>
      )}
    </div>
  );
}

export default Register;
