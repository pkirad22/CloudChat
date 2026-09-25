import { useNavigate } from "react-router-dom";

function AdminDashboard() {
  const navigate = useNavigate();

  return (
    <div className="cloudchat-app">
      <main className="dashboard-placeholder">
        <h1>CloudChat Admin Console</h1>

        <p>
          Server monitoring and load balancing dashboard
          will be built here.
        </p>

        <button
          type="button"
          className="login-button"
          onClick={() => navigate("/")}
        >
          Back to Home
        </button>
      </main>
    </div>
  );
}

export default AdminDashboard;