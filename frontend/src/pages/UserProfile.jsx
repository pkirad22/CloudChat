import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  ShieldCheck,
  UserRound,
} from "lucide-react";

import {
  getCurrentUser,
  updateUsername,
  changePassword,
  setGoogleAccountPassword,
  updateGoogleUsername,
} from "../services/api";

function UserProfile() {
  const navigate = useNavigate();

  const [username, setUsername] = useState(
    () => sessionStorage.getItem("cloudchat_username") || "",
  );

  const [usernameForm, setUsernameForm] = useState({
    currentPassword: "",
    newUsername: "",
  });

  const [passwordForm, setPasswordForm] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });

  const [showPasswords, setShowPasswords] = useState({});
  const [usernameLoading, setUsernameLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);

  const [usernameMessage, setUsernameMessage] = useState(null);
  const [passwordMessage, setPasswordMessage] = useState(null);

  const [profile, setProfile] = useState(null);
  const [googleCredential, setGoogleCredential] = useState("");
  const [profileLoading, setProfileLoading] = useState(true);
  const googleButtonRef = useRef(null);

  const isGoogleAccount = profile?.authProvider === "GOOGLE";
  const hasPassword = profile?.hasPassword === true;
  const needsPasswordSetup = isGoogleAccount && !hasPassword;

  useEffect(() => {
    const storedUsername = sessionStorage.getItem("cloudchat_username");

    if (!storedUsername) {
      navigate("/login", { replace: true });
      return;
    }

    let cancelled = false;

    getCurrentUser(storedUsername)
      .then((result) => {
        if (!cancelled) {
          setProfile(result);
          setUsername(result.username);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setProfile(null);
          setUsernameMessage({
            type: "error",
            text: error.message || "Unable to load your account profile.",
          });
        }
      })
      .finally(() => {
        if (!cancelled) setProfileLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  useEffect(() => {
    if (profileLoading || !isGoogleAccount || !googleButtonRef.current) {
      return;
    }

    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

    if (!clientId) {
      console.error(
        "Google verification is not configured. Check VITE_GOOGLE_CLIENT_ID.",
      );
      return;
    }

    let cancelled = false;

    const initializeGoogle = () => {
      if (
        cancelled ||
        !window.google?.accounts?.id ||
        !googleButtonRef.current
      ) {
        return;
      }

      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (response) => {
          if (!response?.credential) {
            setGoogleCredential("");
            setUsernameMessage({
              type: "error",
              text: "Google verification failed. Please try again.",
            });
            return;
          }

          setGoogleCredential(response.credential);
          setUsernameMessage({
            type: "success",
            text: "Google identity verified. You can now update your username.",
          });
          setPasswordMessage({
            type: "success",
            text: "Google identity verified. You can now set your password.",
          });
        },
      });

      window.google.accounts.id.renderButton(googleButtonRef.current, {
        theme: "filled_blue",
        size: "large",
        text: "continue_with",
        shape: "rectangular",
      });
    };

    if (window.google?.accounts?.id) {
      initializeGoogle();
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

      script.addEventListener("load", initializeGoogle);
    }

    return () => {
      cancelled = true;
    };
  }, [isGoogleAccount, profileLoading]);

  const togglePassword = (field) => {
    setShowPasswords((previous) => ({
      ...previous,
      [field]: !previous[field],
    }));
  };

  const handleUsernameInput = (event) => {
    const { name, value } = event.target;

    setUsernameForm((previous) => ({
      ...previous,
      [name]: value,
    }));

    setUsernameMessage(null);
  };

  const handlePasswordInput = (event) => {
    const { name, value } = event.target;

    setPasswordForm((previous) => ({
      ...previous,
      [name]: value,
    }));

    setPasswordMessage(null);
  };

  const handleUsernameSubmit = async (event) => {
    event.preventDefault();
    setUsernameMessage(null);

    const newUsername = usernameForm.newUsername.trim();

    if (!/^[A-Za-z0-9_]{3,30}$/.test(newUsername)) {
      setUsernameMessage({
        type: "error",
        text: "Username must contain 3–30 letters, numbers, or underscores.",
      });
      return;
    }

    if (newUsername.toLowerCase() === username.toLowerCase()) {
      setUsernameMessage({
        type: "error",
        text: "Your new username must be different from your current username.",
      });
      return;
    }

    if (isGoogleAccount && !googleCredential) {
      setUsernameMessage({
        type: "error",
        text: "Verify your Google account before changing your username.",
      });
      return;
    }

    if (!isGoogleAccount && !usernameForm.currentPassword) {
      setUsernameMessage({
        type: "error",
        text: "Enter your current password to continue.",
      });
      return;
    }

    setUsernameLoading(true);

    try {
      const result = isGoogleAccount
        ? await updateGoogleUsername(googleCredential, newUsername)
        : await updateUsername(
            username,
            usernameForm.currentPassword,
            newUsername,
          );

      const updatedUsername = result.username || newUsername;

      sessionStorage.setItem("cloudchat_username", updatedUsername);
      setUsername(updatedUsername);
      setProfile((previous) =>
        previous ? { ...previous, username: updatedUsername } : previous,
      );
      setGoogleCredential("");

      setUsernameForm({
        currentPassword: "",
        newUsername: "",
      });

      setUsernameMessage({
        type: "success",
        text: "Username updated. Reconnect to chat if your current session still uses the previous username.",
      });
    } catch (error) {
      setUsernameMessage({
        type: "error",
        text: error.message || "Unable to update your username.",
      });
    } finally {
      setUsernameLoading(false);
    }
  };

  const handlePasswordSubmit = async (event) => {
    event.preventDefault();
    setPasswordMessage(null);

    const { currentPassword, newPassword, confirmPassword } = passwordForm;

    // Validate the new password first.
    if (newPassword.length < 8 || newPassword.length > 128) {
      setPasswordMessage({
        type: "error",
        text: "Your new password must contain 8–128 characters.",
      });
      return;
    }

    if (/\s/.test(newPassword)) {
      setPasswordMessage({
        type: "error",
        text: "Your new password cannot contain spaces.",
      });
      return;
    }

    if (
      !/[A-Z]/.test(newPassword) ||
      !/[a-z]/.test(newPassword) ||
      !/[0-9]/.test(newPassword) ||
      !/[^A-Za-z0-9]/.test(newPassword)
    ) {
      setPasswordMessage({
        type: "error",
        text: "Use uppercase and lowercase letters, a number, and a special character.",
      });
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordMessage({
        type: "error",
        text: "The new password and confirmation do not match.",
      });
      return;
    }

    if (needsPasswordSetup) {
      if (!googleCredential) {
        setPasswordMessage({
          type: "error",
          text: "Verify your Google account before setting a password.",
        });
        return;
      }
    } else {
      if (!currentPassword) {
        setPasswordMessage({
          type: "error",
          text: "Enter your current password.",
        });
        return;
      }

      if (currentPassword === newPassword) {
        setPasswordMessage({
          type: "error",
          text: "Your new password must be different from your current password.",
        });
        return;
      }
    }

    setPasswordLoading(true);

    try {
      const result = needsPasswordSetup
        ? await setGoogleAccountPassword(googleCredential, newPassword)
        : await changePassword(username, currentPassword, newPassword);

      setPasswordForm({
        currentPassword: "",
        newPassword: "",
        confirmPassword: "",
      });

      setShowPasswords({});
      setGoogleCredential("");

      if (needsPasswordSetup) {
        setProfile((previous) =>
          previous ? { ...previous, hasPassword: true } : previous,
        );
      }

      setPasswordMessage({
        type: "success",
        text:
          result.message ||
          (needsPasswordSetup
            ? "CloudChat password set successfully."
            : "Your password has been changed successfully."),
      });
    } catch (error) {
      setPasswordMessage({
        type: "error",
        text: error.message || "Unable to update your password.",
      });
    } finally {
      setPasswordLoading(false);
    }
  };

  const renderPasswordInput = ({
    id,
    label,
    name,
    value,
    onChange,
    placeholder,
    autoComplete,
  }) => (
    <div className="up-field">
      <label htmlFor={id}>{label}</label>

      <div className="up-password-wrap">
        <input
          id={id}
          name={name}
          type={showPasswords[id] ? "text" : "password"}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          autoComplete={autoComplete}
          required
        />

        <button
          type="button"
          className="up-eye-button"
          onClick={() => togglePassword(id)}
          aria-label={showPasswords[id] ? "Hide password" : "Show password"}
        >
          {showPasswords[id] ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
    </div>
  );

  return (
    <div className="up-page">
      <style>{`
        .up-page {
          min-height: 100vh;
          padding: 36px 20px 56px;
          color: #eaf0ff;
          background:
            radial-gradient(ellipse at 10% 0%, rgba(66, 118, 220, .15), transparent 38%),
            radial-gradient(ellipse at 100% 100%, rgba(111, 75, 220, .10), transparent 35%),
            #080d18;
          font-family: Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          box-sizing: border-box;
        }

        .up-page *,
        .up-page *::before,
        .up-page *::after {
          box-sizing: border-box;
        }

        .up-container {
          width: 100%;
          max-width: 960px;
          margin: 0 auto;
        }

        .up-back {
          display: inline-flex;
          align-items: center;
          gap: 9px;
          margin-bottom: 30px;
          padding: 10px 13px;
          border: 1px solid #26334b;
          border-radius: 10px;
          background: rgba(17, 26, 43, .75);
          color: #c4d0e6;
          font-size: 13px;
          cursor: pointer;
          transition: .2s ease;
        }

        .up-back:hover {
          background: #17243b;
          border-color: #4569a7;
          color: #fff;
        }

        .up-heading {
          display: flex;
          align-items: center;
          gap: 17px;
          margin-bottom: 28px;
        }

        .up-heading-icon {
          width: 58px;
          height: 58px;
          display: grid;
          place-items: center;
          flex-shrink: 0;
          border: 1px solid rgba(104, 157, 255, .25);
          border-radius: 16px;
          color: #8db7ff;
          background: rgba(71, 126, 230, .13);
        }

        .up-heading h1 {
          margin: 0 0 7px;
          font-size: clamp(25px, 4vw, 32px);
          letter-spacing: -.8px;
          font-weight: 750;
        }

        .up-heading p {
          margin: 0;
          color: #8f9db5;
          font-size: 14px;
          line-height: 1.6;
        }

        .up-account-card {
          display: flex;
          align-items: center;
          gap: 16px;
          padding: 23px;
          margin-bottom: 24px;
          border: 1px solid #25334a;
          border-radius: 16px;
          background: rgba(15, 24, 40, .86);
        }

        .up-avatar {
          width: 56px;
          height: 56px;
          display: grid;
          place-items: center;
          flex-shrink: 0;
          border-radius: 15px;
          background: linear-gradient(140deg, #477ee8, #7555d9);
          color: white;
          font-size: 24px;
          font-weight: 750;
          text-transform: uppercase;
        }

        .up-account-details {
          min-width: 0;
          flex: 1;
        }

        .up-eyebrow {
          margin-bottom: 5px;
          color: #8798b3;
          font-size: 11px;
          font-weight: 750;
          letter-spacing: 1.2px;
          text-transform: uppercase;
        }

        .up-account-name {
          overflow-wrap: anywhere;
          font-size: 19px;
          font-weight: 700;
        }

        .up-account-caption {
          margin-top: 5px;
          color: #8e9cb3;
          font-size: 12px;
        }

        .up-account-status {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 7px 10px;
          border: 1px solid rgba(70, 190, 139, .22);
          border-radius: 999px;
          background: rgba(43, 160, 110, .09);
          color: #70d9ab;
          font-size: 11px;
          white-space: nowrap;
        }

        .up-grid {
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 22px;
          align-items: start;
        }

        .up-card {
          min-width: 0;
          padding: 24px;
          border: 1px solid #25334a;
          border-radius: 16px;
          background: rgba(15, 24, 40, .91);
          box-shadow: 0 15px 45px rgba(0, 0, 0, .12);
        }

        .up-card-heading {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 24px;
        }

        .up-card-icon {
          width: 43px;
          height: 43px;
          display: grid;
          place-items: center;
          flex-shrink: 0;
          border-radius: 12px;
          background: rgba(77, 132, 238, .12);
          color: #91baff;
        }

        .up-card-heading h2 {
          margin: 0 0 5px;
          font-size: 17px;
          font-weight: 700;
        }

        .up-card-heading p {
          margin: 0;
          color: #8d9bb2;
          font-size: 12px;
          line-height: 1.5;
        }

        .up-field {
          margin-bottom: 18px;
        }

        .up-field label {
          display: block;
          margin-bottom: 9px;
          color: #cbd5e6;
          font-size: 12px;
          font-weight: 650;
        }

        .up-field input {
          display: block;
          width: 100%;
          min-width: 0;
          padding: 12px 13px;
          border: 1px solid #2b3a53;
          border-radius: 10px;
          outline: none;
          background: #0b1322;
          color: #eef4ff;
          font: inherit;
          font-size: 13px;
          transition: border-color .2s, box-shadow .2s;
        }

        .up-field input::placeholder {
          color: #60708a;
        }

        .up-field input:focus {
          border-color: #659cff;
          box-shadow: 0 0 0 3px rgba(87, 145, 245, .12);
        }

        .up-password-wrap {
          position: relative;
        }

        .up-password-wrap input {
          padding-right: 45px;
        }

        .up-eye-button {
          position: absolute;
          top: 50%;
          right: 8px;
          display: grid;
          place-items: center;
          width: 31px;
          height: 31px;
          transform: translateY(-50%);
          border: 0;
          border-radius: 7px;
          background: transparent;
          color: #8999b3;
          cursor: pointer;
        }

        .up-eye-button:hover {
          background: #19263c;
          color: #dce8ff;
        }

        .up-help {
          margin: -7px 0 18px;
          color: #8292ab;
          font-size: 11px;
          line-height: 1.6;
        }

        .up-submit {
          display: flex;
          justify-content: center;
          align-items: center;
          gap: 9px;
          width: 100%;
          min-height: 44px;
          padding: 12px 15px;
          border: 1px solid #568af0;
          border-radius: 10px;
          background: linear-gradient(120deg, #3d75df, #5a68d9);
          color: white;
          font-size: 13px;
          font-weight: 700;
          cursor: pointer;
          transition: filter .2s, transform .2s;
        }

        .up-submit:hover:not(:disabled) {
          filter: brightness(1.12);
          transform: translateY(-1px);
        }

        .up-submit:disabled {
          cursor: not-allowed;
          opacity: .65;
        }

        .up-message {
          display: flex;
          align-items: flex-start;
          gap: 9px;
          margin: 0 0 18px;
          padding: 12px;
          border: 1px solid transparent;
          border-radius: 10px;
          font-size: 12px;
          line-height: 1.6;
          overflow-wrap: anywhere;
        }

        .up-message.success {
          border-color: rgba(69, 190, 139, .25);
          background: rgba(36, 154, 103, .10);
          color: #83e2b6;
        }

        .up-message.error {
          border-color: rgba(244, 103, 119, .25);
          background: rgba(205, 66, 83, .09);
          color: #ff9ca8;
        }

        .up-message svg {
          flex-shrink: 0;
          margin-top: 1px;
        }

        .up-footer-note {
          display: flex;
          align-items: flex-start;
          gap: 10px;
          margin-top: 23px;
          padding: 15px 17px;
          border: 1px solid #243149;
          border-radius: 12px;
          color: #8e9db5;
          background: rgba(13, 21, 36, .75);
          font-size: 12px;
          line-height: 1.7;
        }

        .up-footer-note svg {
          flex-shrink: 0;
          margin-top: 2px;
          color: #83adf5;
        }

        @media (max-width: 720px) {
          .up-page {
            padding: 23px 14px 38px;
          }

          .up-grid {
            grid-template-columns: minmax(0, 1fr);
          }

          .up-card {
            padding: 20px;
          }

          .up-account-card {
            padding: 17px;
          }

          .up-account-status {
            display: none;
          }
        }
      `}</style>

      <main className="up-container">
        {profileLoading ? (
          <div className="up-card">
            <LoaderCircle size={20} />
            <p>Loading your account profile...</p>
          </div>
        ) : (
          <>
            <button
              type="button"
              className="up-back"
              onClick={() => navigate("/dashboard")}
            >
              <ArrowLeft size={16} />
              Back to Dashboard
            </button>

            <header className="up-heading">
              <div className="up-heading-icon">
                <UserRound size={28} />
              </div>

              <div>
                <h1>Account Settings</h1>
                <p>Manage your CloudChat username and password.</p>
              </div>
            </header>

            <section className="up-account-card">
              <div className="up-avatar">{username.charAt(0) || "U"}</div>

              <div className="up-account-details">
                <div className="up-eyebrow">Your account</div>
                <div className="up-account-name">{username}</div>
                <div className="up-account-caption">CloudChat user account</div>
              </div>

              <div className="up-account-status">
                <ShieldCheck size={14} />
                Account settings
              </div>
            </section>

            <div className="up-grid">
              <section className="up-card">
                <div className="up-card-heading">
                  <div className="up-card-icon">
                    <UserRound size={21} />
                  </div>

                  <div>
                    <h2>Change Username</h2>
                    <p>Update the name shown on your account.</p>
                  </div>
                </div>

                <form onSubmit={handleUsernameSubmit}>
                  <div className="up-field">
                    <label htmlFor="current-username">Current username</label>

                    <input
                      id="current-username"
                      type="text"
                      value={username}
                      readOnly
                    />
                  </div>

                  {/* Google accounts verify with Google; other accounts use their password */}
                  {isGoogleAccount ? (
                    <div className="up-field">
                      <label>Google account verification</label>
                      <div ref={googleButtonRef} />
                      <p className="up-help">
                        Verify with the Google account linked to this CloudChat
                        account.
                      </p>
                    </div>
                  ) : (
                    renderPasswordInput({
                      id: "username-current-password",
                      label: "Current password",
                      name: "currentPassword",
                      value: usernameForm.currentPassword,
                      onChange: handleUsernameInput,
                      placeholder: "Enter your current password",
                      autoComplete: "current-password",
                    })
                  )}

                  <div className="up-field">
                    <label htmlFor="new-username">New username</label>

                    <input
                      id="new-username"
                      name="newUsername"
                      type="text"
                      value={usernameForm.newUsername}
                      onChange={handleUsernameInput}
                      placeholder="Enter a new username"
                      minLength={3}
                      maxLength={30}
                      autoComplete="username"
                      required
                    />
                  </div>

                  <p className="up-help">
                    Use 3–30 characters. Letters, numbers, and underscores are
                    allowed.
                  </p>

                  {usernameMessage && (
                    <div
                      className={`up-message ${usernameMessage.type}`}
                      role="status"
                    >
                      {usernameMessage.type === "success" ? (
                        <CheckCircle2 size={17} />
                      ) : (
                        <ShieldCheck size={17} />
                      )}
                      <span>{usernameMessage.text}</span>
                    </div>
                  )}

                  <button
                    className="up-submit"
                    type="submit"
                    disabled={usernameLoading}
                  >
                    {usernameLoading ? (
                      <>
                        <LoaderCircle size={17} />
                        Updating username...
                      </>
                    ) : (
                      <>
                        <UserRound size={17} />
                        Update Username
                      </>
                    )}
                  </button>
                </form>
              </section>

              <section className="up-card">
                <div className="up-card-heading">
                  <div className="up-card-icon">
                    <KeyRound size={21} />
                  </div>

                  <div>
                    <h2>Change Password</h2>
                    <p>Keep your account protected.</p>
                  </div>
                </div>

                <form onSubmit={handlePasswordSubmit}>
                  {!needsPasswordSetup &&
                    renderPasswordInput({
                      id: "password-current",
                      label: "Current password",
                      name: "currentPassword",
                      value: passwordForm.currentPassword,
                      onChange: handlePasswordInput,
                      placeholder: "Enter your current password",
                      autoComplete: "current-password",
                    })}

                  {renderPasswordInput({
                    id: "password-new",
                    label: "New password",
                    name: "newPassword",
                    value: passwordForm.newPassword,
                    onChange: handlePasswordInput,
                    placeholder: "Create a new password",
                    autoComplete: "new-password",
                  })}

                  {renderPasswordInput({
                    id: "password-confirm",
                    label: "Confirm new password",
                    name: "confirmPassword",
                    value: passwordForm.confirmPassword,
                    onChange: handlePasswordInput,
                    placeholder: "Re-enter your new password",
                    autoComplete: "new-password",
                  })}

                  <p className="up-help">
                    Use 8–128 characters with uppercase and lowercase letters, a
                    number, and a special character. Spaces are not allowed.
                  </p>

                  {passwordMessage && (
                    <div
                      className={`up-message ${passwordMessage.type}`}
                      role="status"
                    >
                      {passwordMessage.type === "success" ? (
                        <CheckCircle2 size={17} />
                      ) : (
                        <ShieldCheck size={17} />
                      )}
                      <span>{passwordMessage.text}</span>
                    </div>
                  )}

                  <button
                    className="up-submit"
                    type="submit"
                    disabled={passwordLoading}
                  >
                    {passwordLoading ? (
                      <>
                        <LoaderCircle size={17} />
                        Updating password...
                      </>
                    ) : (
                      <>
                        <KeyRound size={17} />
                        {needsPasswordSetup
                          ? "Set CloudChat Password"
                          : "Change Password"}
                      </>
                    )}
                  </button>
                </form>
              </section>
            </div>

            <div className="up-footer-note">
              <ShieldCheck size={17} />
              <span>
                Password accounts must enter their current password to make
                changes. Google accounts must verify their linked Google
                identity. Passwords are securely hashed by the backend.
              </span>
            </div>
          </>
        )}
      </main>
    </div>
  );
}

export default UserProfile;
