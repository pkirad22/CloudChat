import { useCallback, useEffect, useRef, useState } from "react";
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  Clock3,
  Cpu,
  Database,
  RefreshCw,
  Server,
  ShieldAlert,
  Users,
  Wifi,
  WifiOff,
  Zap,
} from "lucide-react";
import "./AdminServerMonitoring.css";

const MONITORING_API = "http://localhost:8081";
const REFRESH_INTERVAL = 5000;
const MAX_HISTORY = 20;

const EMPTY_SERVER = {
  status: "UNKNOWN",
  load: null,
  loadLevel: "UNKNOWN",
  cpuUsage: null,
  memoryUsage: null,
  onlineUsers: null,
  activeThreads: null,
  groups: null,
  connectedToPeer: false,
  peerServer: "",
  heartbeatStatus: "UNKNOWN",
  heartbeatAgeMillis: -1,
};

function normaliseServer(server) {
  if (!server || typeof server !== "object") {
    return { ...EMPTY_SERVER, status: "OFFLINE" };
  }

  return {
    ...EMPTY_SERVER,
    ...server,
    status: server.status || "UNKNOWN",
  };
}

function formatNumber(value, suffix = "") {
  if (
    value === null ||
    value === undefined ||
    !Number.isFinite(Number(value))
  ) {
    return "—";
  }

  return `${Number(value).toFixed(suffix === "%" ? 1 : 2)}${suffix}`;
}

function formatCount(value) {
  if (
    value === null ||
    value === undefined ||
    !Number.isFinite(Number(value))
  ) {
    return "—";
  }

  return Number(value).toLocaleString();
}

function formatAge(milliseconds) {
  if (!Number.isFinite(Number(milliseconds)) || milliseconds < 0) {
    return "No heartbeat data";
  }

  if (milliseconds < 1000) return `${milliseconds} ms ago`;

  return `${(milliseconds / 1000).toFixed(1)} sec ago`;
}

function getHealth(server) {
  if (server.status === "OFFLINE" || server.status === "ERROR") {
    return "offline";
  }

  if (server.status !== "ONLINE") {
    return "unknown";
  }

  if (
    server.heartbeatStatus === "DISCONNECTED" ||
    server.loadLevel === "CRITICAL"
  ) {
    return "warning";
  }

  return "online";
}

function MetricCard({ icon: Icon, label, value, detail, tone = "blue" }) {
  return (
    <div className="asm-metric-card">
      <div className={`asm-metric-icon asm-${tone}`}>
        <Icon size={21} />
      </div>

      <div className="asm-metric-content">
        <span className="asm-metric-label">{label}</span>
        <strong className="asm-metric-value">{value}</strong>
        <span className="asm-metric-detail">{detail}</span>
      </div>
    </div>
  );
}

function ServerCard({ server, expectedId }) {
  const health = getHealth(server);
  const isOnline = health === "online";
  const isOffline = health === "offline";
  const StatusIcon = isOnline
    ? CheckCircle2
    : isOffline
      ? WifiOff
      : AlertCircle;

  return (
    <article className="asm-server-card">
      <div className="asm-server-card-top">
        <div className="asm-server-title">
          <div className="asm-server-icon">
            <Server size={23} />
          </div>

          <div>
            <h3>{expectedId.replace("_", " ")}</h3>
            <p>Chat port {expectedId === "SERVER_1" ? "5000" : "5002"}</p>
          </div>
        </div>

        <span className={`asm-status asm-status-${health}`}>
          <StatusIcon size={14} />
          {health === "online"
            ? "Online"
            : health === "offline"
              ? "Offline"
              : health === "warning"
                ? "Degraded"
                : "Unknown"}
        </span>
      </div>

      <div className="asm-server-load">
        <div>
          <span>Server load</span>
          <strong>
            {server.load === null ? "—" : formatNumber(server.load)}
          </strong>
        </div>

        <span
          className={`asm-load-level asm-load-${String(
            server.loadLevel || "unknown",
          ).toLowerCase()}`}
        >
          {server.loadLevel || "Unknown"}
        </span>
      </div>

      <div className="asm-server-progress-list">
        <ProgressMetric label="CPU usage" value={server.cpuUsage} icon={Cpu} />
        <ProgressMetric
          label="Memory usage"
          value={server.memoryUsage}
          icon={Database}
        />
      </div>

      <div className="asm-server-stats">
        <div>
          <Users size={16} />
          <span>Online users</span>
          <strong>{formatCount(server.onlineUsers)}</strong>
        </div>

        <div>
          <Activity size={16} />
          <span>Active threads</span>
          <strong>{formatCount(server.activeThreads)}</strong>
        </div>

        <div>
          <Database size={16} />
          <span>Groups</span>
          <strong>{formatCount(server.groups)}</strong>
        </div>
      </div>

      <div className="asm-heartbeat">
        <div className="asm-heartbeat-heading">
          <Wifi size={16} />
          <span>Peer connection</span>
          <strong
            className={
              server.connectedToPeer ? "asm-text-success" : "asm-text-muted"
            }
          >
            {server.connectedToPeer ? "Connected" : "Disconnected"}
          </strong>
        </div>

        <div className="asm-heartbeat-heading">
          <Clock3 size={16} />
          <span>Heartbeat</span>
          <strong>{server.heartbeatStatus || "Unknown"}</strong>
        </div>

        <p>Last response: {formatAge(server.heartbeatAgeMillis)}</p>
      </div>
    </article>
  );
}

function ProgressMetric({ label, value, icon: Icon }) {
  const numericValue =
    value !== null && value !== undefined && Number.isFinite(Number(value))
      ? Number(value)
      : null;

  const progress =
    numericValue === null ? 0 : Math.max(0, Math.min(100, numericValue));

  return (
    <div className="asm-progress-metric">
      <div className="asm-progress-heading">
        <span>
          <Icon size={15} />
          {label}
        </span>
        <strong>
          {numericValue === null ? "—" : formatNumber(numericValue, "%")}
        </strong>
      </div>

      <div
        className="asm-progress-track"
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={numericValue === null ? undefined : progress}
      >
        <div
          className={`asm-progress-fill ${
            progress >= 85 ? "asm-progress-high" : ""
          }`}
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  );
}

function HistoryChart({ title, history, field, suffix = "%" }) {
  const available = history.filter((item) => Number.isFinite(item[field]));
  const max = Math.max(100, ...available.map((item) => item[field]));

  return (
    <section className="asm-chart-card">
      <div className="asm-chart-header">
        <div>
          <h3>{title}</h3>
          <p>Last {history.length} monitoring samples</p>
        </div>
        <Activity size={19} />
      </div>

      {available.length < 2 ? (
        <div className="asm-chart-empty">
          Waiting for enough real monitoring samples…
        </div>
      ) : (
        <div
          className="asm-chart"
          role="img"
          aria-label={`${title} over recent monitoring samples`}
        >
          {history.map((item, index) => {
            const value = item[field];
            const height = Number.isFinite(value)
              ? Math.max(3, (value / max) * 100)
              : 0;

            return (
              <div className="asm-chart-column" key={`${item.time}-${index}`}>
                <div className="asm-chart-bar-area">
                  {Number.isFinite(value) && (
                    <div
                      className="asm-chart-bar"
                      style={{ height: `${height}%` }}
                      title={`${formatNumber(value, suffix)} at ${item.label}`}
                    />
                  )}
                </div>
                <span>{index === history.length - 1 ? "Now" : ""}</span>
              </div>
            );
          })}
        </div>
      )}

      <div className="asm-chart-footer">
        <span>Most recent sample</span>
        <strong>
          {available.length
            ? formatNumber(available[available.length - 1][field], suffix)
            : "—"}
        </strong>
      </div>
    </section>
  );
}

export default function AdminServerMonitoring() {
  const [servers, setServers] = useState({});
  const [history, setHistory] = useState([]);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);

  const previousHealth = useRef({});
  const requestInProgress = useRef(false);

  const fetchMonitoringData = useCallback(async (manual = false) => {
    if (requestInProgress.current) return;

    requestInProgress.current = true;

    if (manual) setRefreshing(true);

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);

      let response;

      try {
        response = await fetch(`${MONITORING_API}/api/cluster/status`, {
          method: "GET",
          cache: "no-store",
          signal: controller.signal,
        });
      } finally {
        clearTimeout(timeoutId);
      }

      if (!response.ok) {
        throw new Error(`Monitoring API returned HTTP ${response.status}`);
      }

      const data = await response.json();

      const nextServers = {
        SERVER_1: normaliseServer(data.SERVER_1),
        SERVER_2: normaliseServer(data.SERVER_2),
      };

      if (!data.SERVER_1 && !data.SERVER_2) {
        throw new Error("The monitoring API returned no server status data.");
      }

      const now = Date.now();
      const label = new Date(now).toLocaleTimeString();

      setServers(nextServers);
      setError("");
      setLastUpdated(now);

      setHistory((current) => {
        const sample = {
          time: now,
          label,
          server1Cpu:
            nextServers.SERVER_1.cpuUsage == null
              ? null
              : Number(nextServers.SERVER_1.cpuUsage),

          server2Cpu:
            nextServers.SERVER_2.cpuUsage == null
              ? null
              : Number(nextServers.SERVER_2.cpuUsage),

          server1Memory:
            nextServers.SERVER_1.memoryUsage == null
              ? null
              : Number(nextServers.SERVER_1.memoryUsage),

          server2Memory:
            nextServers.SERVER_2.memoryUsage == null
              ? null
              : Number(nextServers.SERVER_2.memoryUsage),
        };

        return [...current, sample].slice(-MAX_HISTORY);
      });

      const newEvents = [];

      for (const [id, server] of Object.entries(nextServers)) {
        const currentHealth = getHealth(server);
        const previous = previousHealth.current[id];

        if (previous && previous !== currentHealth) {
          newEvents.push({
            id: `${id}-${now}-${currentHealth}`,
            time: now,
            server: id,
            type: currentHealth === "offline" ? "error" : "status",
            message: `${id.replace("_", " ")} changed from ${previous} to ${currentHealth}.`,
          });
        }

        previousHealth.current[id] = currentHealth;
      }

      if (newEvents.length) {
        setEvents((current) => [...newEvents, ...current].slice(0, 12));
      }
    } catch (fetchError) {
      const message =
        fetchError.name === "AbortError"
          ? "Monitoring request timed out."
          : fetchError.message || "Unable to reach the monitoring API.";

      setError(
        `${message} Check that Server 1's monitoring API is running on port 8081 and allows browser access.`,
      );

      setEvents((current) => {
        const latest = current[0];

        if (latest?.message === message) return current;

        return [
          {
            id: `monitoring-api-${Date.now()}`,
            time: Date.now(),
            server: "MONITORING API",
            type: "error",
            message,
          },
          ...current,
        ].slice(0, 12);
      });
    } finally {
      requestInProgress.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    let intervalId;

    const startMonitoring = async () => {
      // Defer the initial update outside the synchronous effect body.
      await Promise.resolve();

      if (cancelled) return;

      fetchMonitoringData();

      intervalId = setInterval(() => {
        if (!cancelled) {
          fetchMonitoringData();
        }
      }, REFRESH_INTERVAL);
    };

    startMonitoring();

    return () => {
      cancelled = true;

      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [fetchMonitoringData]);

  const server1 = servers.SERVER_1 || EMPTY_SERVER;
  const server2 = servers.SERVER_2 || EMPTY_SERVER;

  const totalUsers =
    Number.isFinite(Number(server1.onlineUsers)) &&
    Number.isFinite(Number(server2.onlineUsers))
      ? Number(server1.onlineUsers) + Number(server2.onlineUsers)
      : null;

  const onlineServers = [server1, server2].filter(
    (server) => getHealth(server) === "online",
  ).length;

  const currentHistory = history.map((sample) => ({
    ...sample,
    cpuUsage: sample.server1Cpu,
    memoryUsage: sample.server1Memory,
  }));

  const allServersOnline =
    getHealth(server1) === "online" && getHealth(server2) === "online";

  return (
    <section className="admin-server-monitoring">
      <div className="asm-page-header">
        <div>
          <div className="asm-eyebrow">
            <Activity size={15} />
            INFRASTRUCTURE
          </div>
          <h2>Server Monitoring</h2>
          <p>Live health, resource usage, and synchronization status.</p>
        </div>

        <button
          type="button"
          className="asm-refresh-button"
          onClick={() => fetchMonitoringData(true)}
          disabled={refreshing}
        >
          <RefreshCw size={16} className={refreshing ? "asm-spinning" : ""} />
          {refreshing ? "Refreshing…" : "Refresh"}
        </button>
      </div>

      {error && (
        <div className="asm-error-banner" role="alert">
          <AlertCircle size={19} />
          <div>
            <strong>Monitoring data unavailable</strong>
            <p>{error}</p>
          </div>
        </div>
      )}

      <div className="asm-overview-grid">
        <MetricCard
          icon={Server}
          label="Servers online"
          value={loading ? "—" : `${onlineServers}/2`}
          detail={
            allServersOnline ? "Both servers healthy" : "Check server status"
          }
          tone={allServersOnline ? "green" : "orange"}
        />
        <MetricCard
          icon={Users}
          label="Online users"
          value={formatCount(totalUsers)}
          detail="Combined reported server counts"
          tone="blue"
        />
        <MetricCard
          icon={Zap}
          label="Server 1 load"
          value={formatNumber(server1.load)}
          detail={server1.loadLevel || "Awaiting data"}
          tone="purple"
        />
        <MetricCard
          icon={Wifi}
          label="Peer synchronization"
          value={
            server1.connectedToPeer && server2.connectedToPeer
              ? "Connected"
              : "Check status"
          }
          detail="Heartbeat and peer connection"
          tone={
            server1.connectedToPeer && server2.connectedToPeer
              ? "green"
              : "orange"
          }
        />
      </div>

      <div className="asm-section-heading">
        <div>
          <h3>Server instances</h3>
          <p>Resource readings and connectivity from the backend.</p>
        </div>
        <span className="asm-auto-refresh">
          <span className="asm-live-dot" />
          Auto-refresh every 5s
        </span>
      </div>

      <div className="asm-server-grid">
        <ServerCard server={server1} expectedId="SERVER_1" />
        <ServerCard server={server2} expectedId="SERVER_2" />
      </div>

      <div className="asm-section-heading">
        <div>
          <h3>Resource history</h3>
          <p>Samples collected while this dashboard remains open.</p>
        </div>
      </div>

      <div className="asm-charts-grid">
        <HistoryChart
          title="Server 1 CPU usage"
          history={currentHistory}
          field="cpuUsage"
        />
        <HistoryChart
          title="Server 1 memory usage"
          history={currentHistory}
          field="memoryUsage"
        />
        <HistoryChart
          title="Server 2 CPU usage"
          history={history.map((item) => ({
            ...item,
            cpuUsage: item.server2Cpu,
          }))}
          field="cpuUsage"
        />
        <HistoryChart
          title="Server 2 memory usage"
          history={history.map((item) => ({
            ...item,
            memoryUsage: item.server2Memory,
          }))}
          field="memoryUsage"
        />
      </div>

      <section className="asm-events-card">
        <div className="asm-events-header">
          <div>
            <h3>Monitoring events</h3>
            <p>Observed connection failures and health changes.</p>
          </div>
          <ShieldAlert size={20} />
        </div>

        {events.length === 0 ? (
          <div className="asm-no-events">
            <CheckCircle2 size={20} />
            <span>
              No server state changes or monitoring errors observed yet.
            </span>
          </div>
        ) : (
          <div className="asm-event-list">
            {events.map((event) => (
              <div className="asm-event-row" key={event.id}>
                <span
                  className={`asm-event-icon ${
                    event.type === "error"
                      ? "asm-event-error"
                      : "asm-event-status"
                  }`}
                >
                  {event.type === "error" ? (
                    <AlertCircle size={16} />
                  ) : (
                    <Activity size={16} />
                  )}
                </span>
                <div className="asm-event-message">
                  <strong>{event.server.replace("_", " ")}</strong>
                  <p>{event.message}</p>
                </div>
                <time>{new Date(event.time).toLocaleTimeString()}</time>
              </div>
            ))}
          </div>
        )}
      </section>

      <div className="asm-footer">
        <span>
          Last successful update:{" "}
          {lastUpdated ? new Date(lastUpdated).toLocaleTimeString() : "Not yet"}
        </span>
        <span>
          {error
            ? "Connection issue"
            : loading
              ? "Connecting…"
              : "Monitoring active"}
        </span>
      </div>
    </section>
  );
}
