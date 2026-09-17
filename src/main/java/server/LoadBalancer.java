package server;

public class LoadBalancer {

    private final ServerSynchronizer synchronizer;

    public LoadBalancer(ServerSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    // =========================================================
    // GET LOCAL LOAD
    // =========================================================

    public double getLocalLoad() {

        ServerLoadMonitor monitor = new ServerLoadMonitor();

        return monitor.getLoadScore();
    }

    // =========================================================
    // GET REMOTE LOAD
    // =========================================================

    public double getRemoteLoad() {

        if (synchronizer == null) {
            return -1;
        }

        return synchronizer.getRemoteLoadScore();
    }

    // =========================================================
    // SELECT SERVER
    // =========================================================

    public String selectServer() {

        double localLoad = getLocalLoad();
        double remoteLoad = getRemoteLoad();

        // -----------------------------------------------------
        // Remote load not available
        // -----------------------------------------------------

        if (remoteLoad < 0) {

            return getLocalServerName();
        }

        // -----------------------------------------------------
        // Local server has lower load
        // -----------------------------------------------------

        if (localLoad <= remoteLoad) {

            return getLocalServerName();
        }

        // -----------------------------------------------------
        // Remote server has lower load
        // -----------------------------------------------------

        return getRemoteServerName();
    }

    // =========================================================
    // LOCAL SERVER NAME
    // =========================================================

    private String getLocalServerName() {

        if (synchronizer == null) {
            return "UNKNOWN";
        }

        return synchronizer.isPrimaryServer()
                ? "SERVER1"
                : "SERVER2";
    }

    // =========================================================
    // REMOTE SERVER NAME
    // =========================================================

    private String getRemoteServerName() {

        if (synchronizer == null) {
            return "UNKNOWN";
        }

        return synchronizer.isPrimaryServer()
                ? "SERVER2"
                : "SERVER1";
    }

    // =========================================================
    // PRINT LOAD BALANCING DECISION
    // =========================================================

    public void printDecision() {

        double localLoad = getLocalLoad();
        double remoteLoad = getRemoteLoad();

        String selectedServer = selectServer();

        System.out.println();
        System.out.println(
                "==========================================");

        System.out.println(
                "[LOAD BALANCER] Server Selection");

        System.out.println(
                "==========================================");

        System.out.printf(
                "Local Server  : %s%n",
                getLocalServerName());

        System.out.printf(
                "Local Load    : %.2f%n",
                localLoad);

        if (remoteLoad < 0) {

            System.out.println(
                    "Remote Load   : NOT AVAILABLE");

        } else {

            System.out.printf(
                    "Remote Server : %s%n",
                    getRemoteServerName());

            System.out.printf(
                    "Remote Load   : %.2f%n",
                    remoteLoad);
        }

        System.out.println(
                "------------------------------------------");

        System.out.println(
                "Selected Server : "
                        + selectedServer);

        if (remoteLoad < 0) {

            System.out.println(
                    "Reason          : "
                            + "Remote load unavailable");

        } else if (localLoad <= remoteLoad) {

            System.out.println(
                    "Reason          : "
                            + "Local server has lower load");

        } else {

            System.out.println(
                    "Reason          : "
                            + "Remote server has lower load");
        }

        System.out.println(
                "==========================================");
    }
}