package service;

import model.ServerStatus;
import server.ChatServer;
import server.ServerLoadMonitor;
import server.ServerSynchronizer;

public class ServerMonitoringService {

    private final String serverId;
    private final String peerServer;
    private final ServerLoadMonitor loadMonitor;

    public ServerMonitoringService(String serverId, String peerServer) {
        this.serverId = serverId;
        this.peerServer = peerServer;
        this.loadMonitor = new ServerLoadMonitor();
    }

    public ServerStatus getCurrentStatus() {

        double cpuUsage = loadMonitor.getCpuUsage();
        double memoryUsage = loadMonitor.getMemoryUsage();
        double load = loadMonitor.getLoadScore();
        String loadLevel = loadMonitor.getLoadLevel();

        int onlineUsers = loadMonitor.getConnectedClients();
        int activeThreads = loadMonitor.getActiveThreads();
        int groups = ChatServer.getGroupCount();

        ServerSynchronizer synchronizer = ChatServer.getServerSynchronizer();

        boolean connectedToPeer = synchronizer != null && synchronizer.isConnected();

        ServerStatus status = new ServerStatus(
                serverId,
                "ONLINE",
                load,
                loadLevel,
                cpuUsage,
                memoryUsage,
                onlineUsers,
                activeThreads,
                groups,
                connectedToPeer,
                peerServer);

        // Heartbeat monitoring
        if (synchronizer == null) {

            status.setHeartbeatStatus("DISCONNECTED");
            status.setLastHeartbeatResponseAt(0L);
            status.setHeartbeatAgeMillis(-1L);

        } else {

            status.setHeartbeatStatus(
                    synchronizer.getHeartbeatStatus());

            status.setLastHeartbeatResponseAt(
                    synchronizer.getLastHeartbeatResponseAt());

            status.setHeartbeatAgeMillis(
                    synchronizer.getHeartbeatAgeMillis());
        }

        return status;
    }
}