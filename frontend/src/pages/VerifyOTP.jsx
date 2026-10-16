import { useEffect, useState } from "react";
import { ArrowLeft, Mail, LoaderCircle } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { sendEmailOtp, verifyEmailOtp } from "../services/api";

function VerifyOTP() {
  const navigate = useNavigate();

  const [otp, setOtp] = useState("");
  const [countdown, setCountdown] = useState(60);
  const [verifying, setVerifying] = useState(false);
  const [resending, setResending] = useState(false);
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState("");

  const email = sessionStorage.getItem("cloudchat_registration_email");

  useEffect(() => {
    if (countdown <= 0) return;

    const timer = setInterval(() => {
      setCountdown((current) => Math.max(0, current - 1));
    }, 1000);

    return () => clearInterval(timer);
  }, [countdown]);

  const handleVerify = async (event) => {
    event.preventDefault();

    if (!email) {
      setMessage("Registration email not found. Please register again.");
      setMessageType("error");
      return;
    }

    if (!/^\d{6}$/.test(otp)) {
      setMessage("Please enter the complete six-digit code.");
      setMessageType("error");
      return;
    }

    try {
      setVerifying(true);
      setMessage("");

      const result = await verifyEmailOtp(email, otp);

      setMessageType("success");
      setMessage(result.message || "Email verified successfully.");

      sessionStorage.removeItem("cloudchat_registration_email");
      sessionStorage.removeItem("cloudchat_registration_username");

      setTimeout(() => {
        navigate("/login", { replace: true });
      }, 1000);
    } catch (error) {
      setMessage(error.message || "Unable to verify the code.");
      setMessageType("error");
    } finally {
      setVerifying(false);
    }
  };

  const resendOtp = async () => {
    if (countdown > 0 || resending) return;

    if (!email) {
      setMessage("Registration email not found. Please register again.");
      setMessageType("error");
      return;
    }

    try {
      setResending(true);
      setMessage("");

      const result = await sendEmailOtp(email);

      setOtp("");
      setCountdown(60);
      setMessage(result.message || "A new verification code has been sent.");
      setMessageType("success");
    } catch (error) {
      setMessage(error.message || "Unable to resend the verification code.");
      setMessageType("error");

      // The backend enforces a 60-second cooldown.
      // Keep the resend button disabled if a request was too early.
      if (error.message?.toLowerCase().includes("wait")) {
        setCountdown(60);
      }
    } finally {
      setResending(false);
    }
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
            onClick={() => navigate("/register")}
          >
            <ArrowLeft size={17} />
            Back
          </button>

          <div className="otp-icon">
            <Mail size={27} />
          </div>

          <div className="otp-heading">
            <h2>Verify your email</h2>

            <p>We've sent a six-digit verification code to:</p>

            <strong>{email || "No registration email found"}</strong>
          </div>

          <form onSubmit={handleVerify}>
            <div className="form-group">
              <label htmlFor="verification-code">Verification code</label>

              <input
                id="verification-code"
                className="otp-input"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                pattern="[0-9]{6}"
                placeholder="000000"
                value={otp}
                onChange={(event) => {
                  setOtp(event.target.value.replace(/\D/g, "").slice(0, 6));
                  setMessage("");
                }}
                required
              />
            </div>

            {message && (
              <p
                role="status"
                aria-live="polite"
                style={{
                  color: messageType === "success" ? "#4ade80" : "#f87171",
                  marginBottom: "12px",
                }}
              >
                {message}
              </p>
            )}

            <button
              type="submit"
              className="login-button"
              disabled={otp.length !== 6 || verifying || !email}
            >
              {verifying ? (
                <>
                  <LoaderCircle
                    size={17}
                    className="inline-block animate-spin"
                  />{" "}
                  Verifying...
                </>
              ) : (
                "Verify email"
              )}
            </button>
          </form>

          <div className="otp-resend">
            <span>Didn't receive the code?</span>

            <button
              type="button"
              className="forgot"
              onClick={resendOtp}
              disabled={countdown > 0 || resending || !email}
            >
              {resending
                ? "Sending..."
                : countdown > 0
                  ? `Resend in ${countdown}s`
                  : "Resend code"}
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}

export default VerifyOTP;
