package model;

public class ServerStatus {

    private String serverId;
    private String status;
    private double load;
    private String loadLevel;
    private double cpuUsage;
    private double memoryUsage;
    private int onlineUsers;
    private int activeThreads;
    private int groups;
    private boolean connectedToPeer;
    private String peerServer;

    public ServerStatus() {
    }

    public ServerStatus(
            String serverId,
            String status,
            double load,
            String loadLevel,
            double cpuUsage,
            double memoryUsage,
            int onlineUsers,
            int activeThreads,
            int groups,
            boolean connectedToPeer,
            String peerServer) {

        this.serverId = serverId;
        this.status = status;
        this.load = load;
        this.loadLevel = loadLevel;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.onlineUsers = onlineUsers;
        this.activeThreads = activeThreads;
        this.groups = groups;
        this.connectedToPeer = connectedToPeer;
        this.peerServer = peerServer;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    public String getLoadLevel() {
        return loadLevel;
    }

    public void setLoadLevel(String loadLevel) {
        this.loadLevel = loadLevel;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public int getOnlineUsers() {
        return onlineUsers;
    }

    public void setOnlineUsers(int onlineUsers) {
        this.onlineUsers = onlineUsers;
    }

    public int getActiveThreads() {
        return activeThreads;
    }

    public void setActiveThreads(int activeThreads) {
        this.activeThreads = activeThreads;
    }

    public int getGroups() {
        return groups;
    }

    public void setGroups(int groups) {
        this.groups = groups;
    }

    public boolean isConnectedToPeer() {
        return connectedToPeer;
    }

    public void setConnectedToPeer(boolean connectedToPeer) {
        this.connectedToPeer = connectedToPeer;
    }

    public String getPeerServer() {
        return peerServer;
    }

    public void setPeerServer(String peerServer) {
        this.peerServer = peerServer;
    }
}