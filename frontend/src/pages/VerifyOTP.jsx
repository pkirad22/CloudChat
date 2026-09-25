import { useEffect, useState } from "react";
import { ArrowLeft, Mail } from "lucide-react";
import { useNavigate } from "react-router-dom";

function VerifyOTP() {
  const navigate = useNavigate();

  const [otp, setOtp] = useState("");
  const [countdown, setCountdown] = useState(60);

  useEffect(() => {
    if (countdown <= 0) {
      return;
    }

    const timer = setInterval(() => {
      setCountdown((current) => current - 1);
    }, 1000);

    return () => clearInterval(timer);
  }, [countdown]);

  const handleVerify = (event) => {
    event.preventDefault();

    if (otp.length !== 6) {
      return;
    }

    alert("Email verified successfully!");

    navigate("/login");
  };

  const resendOtp = () => {
    if (countdown > 0) {
      return;
    }

    setOtp("");
    setCountdown(60);

    alert("A new OTP has been sent.");
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

            <p>
              We've sent a 6-digit verification code to your
              email address.
            </p>
          </div>

          <form onSubmit={handleVerify}>
            <div className="form-group">
              <label>Verification code</label>

              <input
                className="otp-input"
                type="text"
                inputMode="numeric"
                maxLength={6}
                placeholder="000000"
                value={otp}
                onChange={(event) =>
                  setOtp(
                    event.target.value
                      .replace(/\D/g, "")
                      .slice(0, 6)
                  )
                }
                required
              />
            </div>

            <button
              type="submit"
              className="login-button"
              disabled={otp.length !== 6}
            >
              Verify email
            </button>
          </form>

          <div className="otp-resend">
            <span>Didn't receive the code?</span>

            <button
              type="button"
              className="forgot"
              onClick={resendOtp}
              disabled={countdown > 0}
            >
              {countdown > 0
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