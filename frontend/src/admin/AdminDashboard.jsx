import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  Ban,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  Eye,
  LogOut,
  RefreshCw,
  Search,
  ShieldCheck,
  UserRound,
  Users,
  X,
  AlertCircle,
  Mail,
  Phone,
  CalendarDays,
  ShieldAlert,
  RotateCcw,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import "./AdminDashboard.css";
import AdminServerMonitoring from "../components/AdminServerMonitoring";

const API_BASE_URL = "http://localhost:9000";

const PAGE_SIZE = 10;

const getAccountStatus = (user) => {
  const status = String(user?.accountStatus || "ACTIVE").toUpperCase();

  return status === "DEACTIVATED" ? "DEACTIVATED" : "ACTIVE";
};

const formatDate = (value) => {
  if (!value) return "Not available";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return date.toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const getInitials = (username) => {
  if (!username) return "U";

  return username
    .trim()
    .split(/[\s._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
};

function AdminDashboard() {
  const navigate = useNavigate();

  // =========================================================
  // STATE
  // =========================================================

  const [users, setUsers] = useState([]);
  const [pagination, setPagination] = useState({
    total: 0,
    page: 1,
    pageSize: PAGE_SIZE,
    totalPages: 0,
  });

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [updatingUser, setUpdatingUser] = useState(null);

  const [selectedUser, setSelectedUser] = useState(null);
  const [confirmationUser, setConfirmationUser] = useState(null);

  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  // =========================================================
  // ADMIN SESSION
  // =========================================================

  const getAdminToken = useCallback(() => {
    return sessionStorage.getItem("cloudchat_admin_token");
  }, []);

  const redirectToAdminLogin = useCallback(() => {
    sessionStorage.removeItem("cloudchat_admin_token");
    sessionStorage.removeItem("cloudchat_admin_id");
    sessionStorage.removeItem("cloudchat_admin_expires_at");

    navigate("/admin/login", { replace: true });
  }, [navigate]);

  // =========================================================
  // LOAD USERS
  //
  // This function updates state only after its asynchronous
  // request resolves. Effects below invoke it without
  // synchronously updating state inside the effect body.
  // =========================================================

  const loadUsers = useCallback(
    async ({ page = 1, searchTerm = "", showRefresh = false } = {}) => {
      const token = getAdminToken();

      if (!token) {
        redirectToAdminLogin();
        return;
      }

      let requestSucceeded = false;

      try {
        const params = new URLSearchParams({
          search: searchTerm.trim(),
          page: String(page),
          pageSize: String(PAGE_SIZE),
        });

        const response = await fetch(
          `${API_BASE_URL}/api/admin/users?${params.toString()}`,
          {
            method: "GET",
            headers: {
              Authorization: `Bearer ${token}`,
              Accept: "application/json",
            },
          },
        );

        if (response.status === 401 || response.status === 403) {
          redirectToAdminLogin();
          return;
        }

        const data = await response.json().catch(() => ({}));

        if (!response.ok || data.success === false) {
          throw new Error(data.message || "Unable to load users.");
        }

        const returnedUsers = Array.isArray(data.users) ? data.users : [];
        const returnedPagination = data.pagination || {};

        setUsers(returnedUsers);
        setPagination({
          total: Number(returnedPagination.total ?? returnedUsers.length),
          page: Number(returnedPagination.page ?? page),
          pageSize: Number(returnedPagination.pageSize ?? PAGE_SIZE),
          totalPages: Number(
            returnedPagination.totalPages ??
              Math.ceil(
                Number(returnedPagination.total ?? returnedUsers.length) /
                  PAGE_SIZE,
              ),
          ),
        });

        setError("");
        requestSucceeded = true;
      } catch (requestError) {
        setError(
          requestError.message ||
            "Unable to connect to the CloudChat admin server.",
        );
      } finally {
        setLoading(false);

        if (showRefresh) {
          setRefreshing(false);
        }
      }

      return requestSucceeded;
    },
    [getAdminToken, redirectToAdminLogin],
  );

  // =========================================================
  // INITIAL LOAD AND PAGE CHANGES
  //
  // Search is handled separately below so typing does not
  // immediately fire a request for every character.
  // =========================================================

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      // The request itself is asynchronous. Do not set loading
      // synchronously inside this effect.
      const token = sessionStorage.getItem("cloudchat_admin_token");

      if (!token) {
        if (!cancelled) {
          redirectToAdminLogin();
        }
        return;
      }

      try {
        const params = new URLSearchParams({
          search: search.trim(),
          page: String(pagination.page),
          pageSize: String(PAGE_SIZE),
        });

        const response = await fetch(
          `${API_BASE_URL}/api/admin/users?${params.toString()}`,
          {
            method: "GET",
            headers: {
              Authorization: `Bearer ${token}`,
              Accept: "application/json",
            },
          },
        );

        if (cancelled) return;

        if (response.status === 401 || response.status === 403) {
          redirectToAdminLogin();
          return;
        }

        const data = await response.json().catch(() => ({}));

        if (cancelled) return;

        if (!response.ok || data.success === false) {
          throw new Error(data.message || "Unable to load users.");
        }

        const returnedUsers = Array.isArray(data.users) ? data.users : [];
        const returnedPagination = data.pagination || {};

        setUsers(returnedUsers);
        setPagination((current) => ({
          ...current,
          total: Number(returnedPagination.total ?? returnedUsers.length),
          page: Number(returnedPagination.page ?? current.page),
          pageSize: Number(returnedPagination.pageSize ?? PAGE_SIZE),
          totalPages: Number(
            returnedPagination.totalPages ??
              Math.ceil(
                Number(returnedPagination.total ?? returnedUsers.length) /
                  PAGE_SIZE,
              ),
          ),
        }));

        setError("");
      } catch (requestError) {
        if (!cancelled) {
          setError(
            requestError.message ||
              "Unable to connect to the CloudChat admin server.",
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    run();

    return () => {
      cancelled = true;
    };
  }, [pagination.page, search, redirectToAdminLogin]);

  // =========================================================
  // SEARCH
  // =========================================================

  const handleSearchChange = (value) => {
    setSearch(value);
    setStatusFilter("ALL");

    // A new search should start from the first page.
    setPagination((current) => ({
      ...current,
      page: 1,
    }));
  };

  // =========================================================
  // REFRESH
  // =========================================================

  const handleRefresh = async () => {
    setRefreshing(true);
    setSuccessMessage("");
    setError("");

    try {
      await loadUsers({
        page: pagination.page,
        searchTerm: search,
        showRefresh: true,
      });
    } finally {
      setRefreshing(false);
    }
  };

  // =========================================================
  // CUSTOM STATUS CONFIRMATION
  // =========================================================

  const handleUpdateUserStatus = (user) => {
    setError("");
    setSuccessMessage("");
    setConfirmationUser(user);
  };

  const confirmUpdateUserStatus = async () => {
    if (!confirmationUser) return;

    const user = confirmationUser;
    const currentStatus = getAccountStatus(user);
    const nextStatus = currentStatus === "ACTIVE" ? "DEACTIVATED" : "ACTIVE";

    const token = getAdminToken();

    if (!token) {
      redirectToAdminLogin();
      return;
    }

    setUpdatingUser(user.username);
    setError("");
    setSuccessMessage("");

    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/users/status`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          username: user.username,
          accountStatus: nextStatus,
        }),
      });

      if (response.status === 401 || response.status === 403) {
        redirectToAdminLogin();
        return;
      }

      const data = await response.json().catch(() => ({}));

      if (!response.ok || data.success === false) {
        throw new Error(
          data.message || "Unable to update this user's account status.",
        );
      }

      // Update the visible row immediately.
      setUsers((currentUsers) =>
        currentUsers.map((currentUser) =>
          currentUser.username === user.username
            ? { ...currentUser, accountStatus: nextStatus }
            : currentUser,
        ),
      );

      setSelectedUser((currentUser) =>
        currentUser?.username === user.username
          ? { ...currentUser, accountStatus: nextStatus }
          : currentUser,
      );

      setConfirmationUser(null);

      setSuccessMessage(
        `${user.username}'s account has been ${
          nextStatus === "ACTIVE" ? "reactivated" : "deactivated"
        }.`,
      );

      // Refresh server data to keep the dashboard synchronized.
      await loadUsers({
        page: pagination.page,
        searchTerm: search,
      });
    } catch (requestError) {
      setError(requestError.message || "Failed to update the account status.");
    } finally {
      setUpdatingUser(null);
    }
  };

  // =========================================================
  // LOGOUT
  // =========================================================

  const handleLogout = async () => {
    const token = getAdminToken();

    try {
      if (token) {
        await fetch(`${API_BASE_URL}/api/admin/logout`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/json",
          },
        });
      }
    } catch {
      // Clear the local session even if the server is unavailable.
    } finally {
      redirectToAdminLogin();
    }
  };

  // =========================================================
  // CLIENT-SIDE STATUS FILTER
  // =========================================================

  const displayedUsers = useMemo(() => {
    if (statusFilter === "ALL") {
      return users;
    }

    return users.filter((user) => getAccountStatus(user) === statusFilter);
  }, [users, statusFilter]);

  const activeUsersOnPage = users.filter(
    (user) => getAccountStatus(user) === "ACTIVE",
  ).length;

  const deactivatedUsersOnPage = users.filter(
    (user) => getAccountStatus(user) === "DEACTIVATED",
  ).length;

  const totalPages = Math.max(1, pagination.totalPages || 1);
  const canGoPrevious = pagination.page > 1;
  const canGoNext = pagination.page < totalPages;

  const goToPage = (nextPage) => {
    if (nextPage < 1 || nextPage > totalPages) return;

    setPagination((current) => ({
      ...current,
      page: nextPage,
    }));
  };

  // =========================================================
  // RENDER
  // =========================================================

  return (
    <div className="admin-dashboard">
      {/* TOP NAVIGATION */}
      <header className="admin-topbar">
        <div className="admin-brand">
          <div className="admin-brand-icon">
            <ShieldCheck size={23} />
          </div>

          <div>
            <h1>CloudChat</h1>
            <p>Administration Console</p>
          </div>
        </div>

        <div className="admin-topbar-right">
          <div className="admin-role">
            <ShieldCheck size={16} />
            <span>System Administrator</span>
          </div>

          <button
            type="button"
            className="admin-logout-button"
            onClick={handleLogout}
          >
            <LogOut size={17} />
            <span>Logout</span>
          </button>
        </div>
      </header>

      <main className="admin-main">
        {/* PAGE HEADING */}
        <section className="admin-page-heading">
          <div>
            <div className="admin-eyebrow">
              <Activity size={15} />
              SYSTEM MANAGEMENT
            </div>

            <h2>Admin Dashboard</h2>

            <p>
              Monitor registered users and manage their CloudChat account
              access.
            </p>
          </div>

          <button
            type="button"
            className="admin-refresh-button"
            onClick={handleRefresh}
            disabled={refreshing}
          >
            <RefreshCw size={17} className={refreshing ? "admin-spin" : ""} />
            {refreshing ? "Refreshing..." : "Refresh"}
          </button>
        </section>

        {/* ALERTS */}
        {error && (
          <div className="admin-alert admin-alert-error" role="alert">
            <AlertCircle size={19} />
            <span>{error}</span>

            <button
              type="button"
              aria-label="Dismiss error"
              onClick={() => setError("")}
            >
              <X size={17} />
            </button>
          </div>
        )}

        {successMessage && (
          <div className="admin-alert admin-alert-success" role="status">
            <CheckCircle size={19} />
            <span>{successMessage}</span>

            <button
              type="button"
              aria-label="Dismiss success message"
              onClick={() => setSuccessMessage("")}
            >
              <X size={17} />
            </button>
          </div>
        )}

        {/* STATISTICS */}
        <section className="admin-stats-grid">
          <article className="admin-stat-card">
            <div className="admin-stat-icon stat-icon-purple">
              <Users size={23} />
            </div>

            <div className="admin-stat-content">
              <span>Total Registered Users</span>
              <strong>{pagination.total}</strong>
              <small>Across all matching records</small>
            </div>
          </article>

          <article className="admin-stat-card">
            <div className="admin-stat-icon stat-icon-green">
              <CheckCircle size={23} />
            </div>

            <div className="admin-stat-content">
              <span>Active Users</span>
              <strong>{activeUsersOnPage}</strong>
              <small>On the current page</small>
            </div>
          </article>

          <article className="admin-stat-card">
            <div className="admin-stat-icon stat-icon-orange">
              <Ban size={23} />
            </div>

            <div className="admin-stat-content">
              <span>Deactivated Users</span>
              <strong>{deactivatedUsersOnPage}</strong>
              <small>On the current page</small>
            </div>
          </article>

          <article className="admin-stat-card">
            <div className="admin-stat-icon stat-icon-blue">
              <Activity size={23} />
            </div>

            <div className="admin-stat-content">
              <span>Current Page</span>
              <strong>
                {pagination.total === 0 ? 0 : pagination.page}
                <span className="admin-stat-total"> / {totalPages}</span>
              </strong>
              <small>{PAGE_SIZE} users per page maximum</small>
            </div>
          </article>
        </section>

        {/* Server Monitoring */}
        <AdminServerMonitoring />

        {/* USER MANAGEMENT */}
        <section className="admin-users-panel">
          <div className="admin-panel-heading">
            <div>
              <h3>
                <Users size={20} />
                User Management
              </h3>

              <p>Search, view, and manage registered CloudChat users.</p>
            </div>

            <span className="admin-total-badge">{pagination.total} users</span>
          </div>

          {/* SEARCH AND FILTER */}
          <div className="admin-toolbar">
            <div className="admin-search-box">
              <Search size={19} />

              <input
                type="search"
                value={search}
                onChange={(event) => handleSearchChange(event.target.value)}
                placeholder="Search by username, email, or mobile..."
                aria-label="Search users"
              />

              {search && (
                <button
                  type="button"
                  className="admin-clear-search"
                  onClick={() => handleSearchChange("")}
                  aria-label="Clear search"
                >
                  <X size={16} />
                </button>
              )}
            </div>

            <div className="admin-filter-control">
              <label htmlFor="account-status-filter">Account status</label>

              <select
                id="account-status-filter"
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value)}
              >
                <option value="ALL">All statuses</option>
                <option value="ACTIVE">Active</option>
                <option value="DEACTIVATED">Deactivated</option>
              </select>
            </div>
          </div>

          {/* USERS TABLE */}
          <div className="admin-table-wrapper">
            <table className="admin-users-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Contact</th>
                  <th>Account Status</th>
                  <th>Email Verification</th>
                  <th>Registered</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={6}>
                      <div className="admin-table-message">
                        <RefreshCw className="admin-spin" size={23} />
                        <span>Loading users...</span>
                      </div>
                    </td>
                  </tr>
                ) : displayedUsers.length === 0 ? (
                  <tr>
                    <td colSpan={6}>
                      <div className="admin-empty-state">
                        <Users size={35} />
                        <strong>No users found</strong>
                        <p>Try changing your search or status filter.</p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  displayedUsers.map((user) => {
                    const accountStatus = getAccountStatus(user);
                    const isActive = accountStatus === "ACTIVE";

                    return (
                      <tr key={user.username}>
                        <td>
                          <div className="admin-user-cell">
                            <div className="admin-avatar">
                              {getInitials(user.username)}
                            </div>

                            <div className="admin-user-name">
                              <strong>{user.username || "Unknown user"}</strong>
                              <span>{user.authProvider || "LOCAL"}</span>
                            </div>
                          </div>
                        </td>

                        <td>
                          <div className="admin-contact-cell">
                            <span>
                              <Mail size={14} />
                              {user.email || "No email"}
                            </span>

                            <span>
                              <Phone size={14} />
                              {user.mobileNumber || "No mobile"}
                            </span>
                          </div>
                        </td>

                        <td>
                          <span
                            className={`admin-status-badge ${
                              isActive ? "status-active" : "status-deactivated"
                            }`}
                          >
                            <span className="admin-status-dot" />
                            {accountStatus}
                          </span>
                        </td>

                        <td>
                          <span
                            className={`admin-verification-badge ${
                              user.emailVerified
                                ? "email-verified"
                                : "email-unverified"
                            }`}
                          >
                            {user.emailVerified ? (
                              <CheckCircle size={14} />
                            ) : (
                              <AlertCircle size={14} />
                            )}

                            {user.emailVerified ? "Verified" : "Unverified"}
                          </span>
                        </td>

                        <td className="admin-date-cell">
                          {formatDate(user.createdAt)}
                        </td>

                        <td>
                          <div className="admin-row-actions">
                            <button
                              type="button"
                              className="admin-action-button action-view"
                              onClick={() => setSelectedUser(user)}
                              title="View user details"
                              aria-label={`View ${user.username} details`}
                            >
                              <Eye size={16} />
                            </button>

                            <button
                              type="button"
                              className={`admin-action-button ${
                                isActive
                                  ? "action-deactivate"
                                  : "action-reactivate"
                              }`}
                              onClick={() => handleUpdateUserStatus(user)}
                              disabled={updatingUser === user.username}
                              title={
                                isActive
                                  ? "Deactivate account"
                                  : "Reactivate account"
                              }
                              aria-label={
                                isActive
                                  ? `Deactivate ${user.username}`
                                  : `Reactivate ${user.username}`
                              }
                            >
                              {isActive ? (
                                <Ban size={16} />
                              ) : (
                                <RotateCcw size={16} />
                              )}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* PAGINATION */}
          <div className="admin-pagination">
            <p>
              Showing{" "}
              <strong>
                {pagination.total === 0
                  ? 0
                  : (pagination.page - 1) * pagination.pageSize + 1}
                {"–"}
                {Math.min(
                  pagination.page * pagination.pageSize,
                  pagination.total,
                )}
              </strong>{" "}
              of <strong>{pagination.total}</strong> users
            </p>

            <div className="admin-pagination-controls">
              <button
                type="button"
                onClick={() => goToPage(pagination.page - 1)}
                disabled={!canGoPrevious || loading}
                aria-label="Previous page"
              >
                <ChevronLeft size={18} />
                Previous
              </button>

              <span>
                Page{" "}
                <strong>{pagination.total === 0 ? 0 : pagination.page}</strong>{" "}
                of <strong>{totalPages}</strong>
              </span>

              <button
                type="button"
                onClick={() => goToPage(pagination.page + 1)}
                disabled={!canGoNext || loading}
                aria-label="Next page"
              >
                Next
                <ChevronRight size={18} />
              </button>
            </div>
          </div>
        </section>

        <footer className="admin-footer">
          <span>
            <ShieldCheck size={15} />
            CloudChat Administration Console
          </span>
          <span>Manage accounts securely</span>
        </footer>
      </main>

      {/* USER DETAILS MODAL */}
      {selectedUser && (
        <div
          className="admin-modal-overlay"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setSelectedUser(null);
            }
          }}
        >
          <section
            className="admin-modal admin-user-details-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="user-details-title"
          >
            <div className="admin-modal-header">
              <div>
                <span className="admin-modal-eyebrow">USER PROFILE</span>
                <h3 id="user-details-title">User Details</h3>
              </div>

              <button
                type="button"
                className="admin-modal-close"
                onClick={() => setSelectedUser(null)}
                aria-label="Close user details"
              >
                <X size={20} />
              </button>
            </div>

            <div className="admin-profile-summary">
              <div className="admin-profile-avatar">
                {getInitials(selectedUser.username)}
              </div>

              <div>
                <h4>{selectedUser.username}</h4>
                <p>{selectedUser.email || "No email address"}</p>

                <span
                  className={`admin-status-badge ${
                    getAccountStatus(selectedUser) === "ACTIVE"
                      ? "status-active"
                      : "status-deactivated"
                  }`}
                >
                  <span className="admin-status-dot" />
                  {getAccountStatus(selectedUser)}
                </span>
              </div>
            </div>

            <div className="admin-details-list">
              <div className="admin-detail-row">
                <span>
                  <UserRound size={16} />
                  Username
                </span>
                <strong>{selectedUser.username || "Not available"}</strong>
              </div>

              <div className="admin-detail-row">
                <span>
                  <Mail size={16} />
                  Email address
                </span>
                <strong>{selectedUser.email || "Not available"}</strong>
              </div>

              <div className="admin-detail-row">
                <span>
                  <Phone size={16} />
                  Mobile number
                </span>
                <strong>{selectedUser.mobileNumber || "Not available"}</strong>
              </div>

              <div className="admin-detail-row">
                <span>
                  <ShieldCheck size={16} />
                  Authentication
                </span>
                <strong>{selectedUser.authProvider || "LOCAL"}</strong>
              </div>

              <div className="admin-detail-row">
                <span>
                  <Mail size={16} />
                  Email verification
                </span>
                <strong>
                  {selectedUser.emailVerified ? "Verified" : "Unverified"}
                </strong>
              </div>

              <div className="admin-detail-row">
                <span>
                  <CalendarDays size={16} />
                  Registered
                </span>
                <strong>{formatDate(selectedUser.createdAt)}</strong>
              </div>
            </div>

            <div className="admin-modal-footer">
              <button
                type="button"
                className="admin-secondary-button"
                onClick={() => setSelectedUser(null)}
              >
                Close
              </button>

              <button
                type="button"
                className={`admin-primary-button ${
                  getAccountStatus(selectedUser) === "ACTIVE"
                    ? "button-danger"
                    : "button-success"
                }`}
                onClick={() => handleUpdateUserStatus(selectedUser)}
              >
                {getAccountStatus(selectedUser) === "ACTIVE" ? (
                  <Ban size={16} />
                ) : (
                  <RotateCcw size={16} />
                )}

                {getAccountStatus(selectedUser) === "ACTIVE"
                  ? "Deactivate Account"
                  : "Reactivate Account"}
              </button>
            </div>
          </section>
        </div>
      )}

      {/* CUSTOM STATUS CONFIRMATION MODAL */}
      {confirmationUser && (
        <div
          className="admin-modal-overlay admin-confirm-overlay"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !updatingUser) {
              setConfirmationUser(null);
            }
          }}
        >
          <section
            className="admin-modal admin-confirm-modal"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-status-title"
            aria-describedby="confirm-status-description"
          >
            <div
              className={`admin-confirm-icon ${
                getAccountStatus(confirmationUser) === "ACTIVE"
                  ? "confirm-icon-danger"
                  : "confirm-icon-success"
              }`}
            >
              {getAccountStatus(confirmationUser) === "ACTIVE" ? (
                <ShieldAlert size={27} />
              ) : (
                <CheckCircle size={27} />
              )}
            </div>

            <button
              type="button"
              className="admin-modal-close admin-confirm-close"
              onClick={() => setConfirmationUser(null)}
              disabled={Boolean(updatingUser)}
              aria-label="Close confirmation"
            >
              <X size={19} />
            </button>

            <div className="admin-confirm-content">
              <h3 id="confirm-status-title">
                {getAccountStatus(confirmationUser) === "ACTIVE"
                  ? "Deactivate account?"
                  : "Reactivate account?"}
              </h3>

              <p id="confirm-status-description">
                {getAccountStatus(confirmationUser) === "ACTIVE" ? (
                  <>
                    Are you sure you want to deactivate{" "}
                    <strong>{confirmationUser.username}</strong>? This prevents
                    the account from logging in while it remains deactivated.
                  </>
                ) : (
                  <>
                    Do you want to reactivate{" "}
                    <strong>{confirmationUser.username}</strong>? The account
                    will be marked as active again.
                  </>
                )}
              </p>

              <div className="admin-confirm-note">
                <ShieldCheck size={17} />
                <span>
                  This change will be saved to the CloudChat database.
                </span>
              </div>
            </div>

            <div className="admin-confirm-actions">
              <button
                type="button"
                className="admin-secondary-button"
                onClick={() => setConfirmationUser(null)}
                disabled={Boolean(updatingUser)}
              >
                Cancel
              </button>

              <button
                type="button"
                className={`admin-primary-button ${
                  getAccountStatus(confirmationUser) === "ACTIVE"
                    ? "button-danger"
                    : "button-success"
                }`}
                onClick={confirmUpdateUserStatus}
                disabled={Boolean(updatingUser)}
              >
                {updatingUser ? (
                  <>
                    <RefreshCw size={16} className="admin-spin" />
                    Updating...
                  </>
                ) : getAccountStatus(confirmationUser) === "ACTIVE" ? (
                  <>
                    <Ban size={16} />
                    Deactivate
                  </>
                ) : (
                  <>
                    <CheckCircle size={16} />
                    Reactivate
                  </>
                )}
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

export default AdminDashboard;
