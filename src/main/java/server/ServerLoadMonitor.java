package server;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

public class ServerLoadMonitor {

    // =========================================================
    // SERVER LOAD MONITOR
    // =========================================================

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;

    public ServerLoadMonitor() {

        osBean = ManagementFactory
                .getOperatingSystemMXBean();

        memoryBean = ManagementFactory
                .getMemoryMXBean();
    }

    // =========================================================
    // CONNECTED CLIENTS
    // =========================================================

    public int getConnectedClients() {

        try {

            return ChatServer
                    .getOnlineUsers()
                    .size();

        } catch (Exception e) {

            return 0;
        }
    }

    // =========================================================
    // CPU USAGE
    // =========================================================

    public double getCpuUsage() {

        try {

            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {

                com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;

                double cpuLoad = sunOsBean
                        .getCpuLoad();

                if (cpuLoad >= 0) {

                    return cpuLoad * 100.0;
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "[LOAD] Unable to read CPU usage: "
                            + e.getMessage());
        }

        return -1;
    }

    // =========================================================
    // JVM MEMORY USAGE
    // =========================================================

    public double getMemoryUsage() {

        try {

            long used = memoryBean
                    .getHeapMemoryUsage()
                    .getUsed();

            long max = memoryBean
                    .getHeapMemoryUsage()
                    .getMax();

            if (max <= 0) {

                return -1;
            }

            return ((double) used / max) * 100.0;

        } catch (Exception e) {

            System.out.println(
                    "[LOAD] Unable to read memory usage: "
                            + e.getMessage());

            return -1;
        }
    }

    // =========================================================
    // ACTIVE THREADS
    // =========================================================

    public int getActiveThreads() {

        return Thread.activeCount();
    }

    // =========================================================
    // LOAD SCORE
    // =========================================================
    //
    // Current weighting:
    //
    // CPU = 40%
    // Memory = 30%
    // Clients = 20%
    // Threads = 10%
    //
    // Client/thread values are normalized.
    // =========================================================

    public double getLoadScore() {

        double cpu = getCpuUsage();

        double memory = getMemoryUsage();

        int clients = getConnectedClients();

        int threads = getActiveThreads();

        // ---------------------------------------------
        // Normalize clients
        // ---------------------------------------------

        double clientScore = Math.min(
                clients * 10.0,
                100.0);

        // ---------------------------------------------
        // Normalize threads
        // ---------------------------------------------

        double threadScore = Math.min(
                threads * 5.0,
                100.0);

        // ---------------------------------------------
        // Handle unavailable values
        // ---------------------------------------------

        if (cpu < 0) {
            cpu = 0;
        }

        if (memory < 0) {
            memory = 0;
        }

        // ---------------------------------------------
        // Weighted load score
        // ---------------------------------------------

        return (cpu * 0.40)
                +
                (memory * 0.30)
                +
                (clientScore * 0.20)
                +
                (threadScore * 0.10);
    }

    // =========================================================
    // LOAD LEVEL
    // =========================================================

    public String getLoadLevel() {

        double score = getLoadScore();

        if (score < 40) {

            return "LOW";

        } else if (score < 70) {

            return "MEDIUM";

        } else if (score < 85) {

            return "HIGH";

        } else {

            return "CRITICAL";
        }
    }

    // =========================================================
    // PRINT LOAD INFORMATION
    // =========================================================

    public void printLoadInformation() {

        System.out.println();
        System.out.println(
                "==========================================");

        System.out.println(
                "[LOAD MONITOR] Server Load Information");

        System.out.println(
                "==========================================");

        System.out.println(
                "Connected Clients : "
                        + getConnectedClients());

        System.out.printf(
                "CPU Usage         : %.2f%%%n",
                getCpuUsage());

        System.out.printf(
                "Memory Usage      : %.2f%%%n",
                getMemoryUsage());

        System.out.println(
                "Active Threads    : "
                        + getActiveThreads());

        System.out.printf(
                "Load Score        : %.2f%n",
                getLoadScore());

        System.out.println(
                "Load Level        : "
                        + getLoadLevel());

        System.out.println(
                "==========================================");

    }
}