package service;

import model.ServerStatus;
import server.ChatServer;
import server.ServerLoadMonitor;

public class ServerMonitoringService {

    private final String serverId;
    private final String peerServer;

    private final ServerLoadMonitor loadMonitor;

    public ServerMonitoringService(
            String serverId,
            String peerServer) {

        this.serverId = serverId;
        this.peerServer = peerServer;

        this.loadMonitor = new ServerLoadMonitor();
    }

    // =========================================================
    // CURRENT SERVER STATUS
    // =========================================================

    public ServerStatus getCurrentStatus() {

        double cpuUsage = loadMonitor.getCpuUsage();

        double memoryUsage = loadMonitor.getMemoryUsage();

        double load = loadMonitor.getLoadScore();

        String loadLevel = loadMonitor.getLoadLevel();

        int onlineUsers = loadMonitor.getConnectedClients();

        int activeThreads = loadMonitor.getActiveThreads();

        int groups = ChatServer.getGroupCount();

        boolean connectedToPeer = ChatServer.getServerSynchronizer() != null;

        return new ServerStatus(
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
    }
}