package com.accessdetector;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConsoleDisplay {

    private final int port;
    private final String bindAddress;
    private final String logFile;
    private final String redirectUrl;
    private final boolean useNgrok;
    private volatile String ngrokUrl = "resolving...";

    private static final Set<String> SUSPICIOUS_PATHS = Set.of(
            "/admin", "/login", "/wp-admin", "/phpmyadmin", "/.env", "/config"
    );

    public ConsoleDisplay(int port, String bindAddress, String logFile, String redirectUrl, boolean useNgrok) {
        this.port = port;
        this.bindAddress = bindAddress;
        this.logFile = logFile;
        this.redirectUrl = redirectUrl;
        this.useNgrok = useNgrok;
    }

    public void init() {
        AnsiConsole.systemInstall();
    }

    public void cleanup() {
        AnsiConsole.systemUninstall();
    }

    public void setNgrokUrl(String url) {
        this.ngrokUrl = url;
    }

    public void printStartupBanner() {
        String logStatus = (logFile != null && !logFile.isEmpty()) ? logFile : "disabled";
        String redirectStatus = (redirectUrl != null && !redirectUrl.isEmpty()) ? redirectUrl : "disabled";
        String bindStr = bindAddress;
        if (bindAddress.equals("0.0.0.0") || bindAddress.equals("::")) {
            bindStr += " (all interfaces)";
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           Access Detector v1.0.0  Starting...                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Port      : %-47s ║%n", port);
        System.out.printf("║  Bind      : %-47s ║%n", bindStr);
        System.out.printf("║  Logging   : %-47s ║%n", logStatus);
        System.out.printf("║  Redirect  : %-47s ║%n", redirectStatus);

        if (useNgrok) {
            System.out.printf("║  ngrok URL : %-47s ║%n", ngrokUrl);
        }

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("HTTP server started. Press Ctrl+C to stop.");
    }

    public synchronized void refresh(CopyOnWriteArrayList<AccessRecord> accessLog) {
        int totalHits = accessLog.size();

        Ansi ansi = Ansi.ansi().eraseScreen().cursor(1, 1);

        ansi.fgCyan().a("╔════════════════════════════════════════════════════════════════════════════════════════════════════╗\n").reset();
        ansi.fgCyan().a("║").reset().a(String.format("  Access Detector  |  Port: %-6d |  Total Hits: %-52d", port, totalHits)).fgCyan().a("║\n").reset();
        ansi.fgCyan().a("╚════════════════════════════════════════════════════════════════════════════════════════════════════╝\n").reset();
        ansi.a("\n");

        ansi.fgYellow().a(" No   Time       IP Address        Location              ISP              Path                 User-Agent\n").reset();
        ansi.a("──────────────────────────────────────────────────────────────────────────────────────────────────────────────\n");

        for (int i = 0; i < accessLog.size(); i++) {
            AccessRecord record = accessLog.get(i);

            // Format ID
            String idStr = String.format("%3d", record.id);

            // Format Time
            String timeStr = String.format("%-10s", record.timestamp);

            // Format IP
            String ipStr = String.format("%-17s", record.ip);

            // Format Location
            String location = record.country;
            if (record.country.equals("Private IP") || record.country.equals("Local Connection")) {
                location = record.country;
            } else if (!record.region.isEmpty()) {
                location += " " + record.region;
            }
            if (location.isEmpty() && record.geoStatus.equals("Resolving")) {
                location = "Resolving";
            } else if (location.isEmpty() && record.geoStatus.equals("Failed")) {
                location = "Failed";
            }
            if (location.length() > 15) {
                location = location.substring(0, 12) + "...";
            }
            String locStr = String.format("%-21s", location);

            // Format ISP
            String isp = record.isp;
            if (isp == null || isp.isEmpty()) isp = "—";
            if (isp.length() > 15) {
                isp = isp.substring(0, 12) + "...";
            }
            String ispStr = String.format("%-16s", isp);

            // Format Path
            String path = record.path;
            if (path.length() > 20) {
                path = path.substring(0, 17) + "...";
            }
            String pathStr = String.format("%-20s", path);

            // Format User-Agent
            String ua = record.userAgent;
            if (ua == null || ua.isEmpty()) ua = "—";
            if (ua.length() > 20) {
                ua = ua.substring(0, 17) + "...";
            }
            String uaStr = ua;

            // Print row
            ansi.fgBrightBlack().a(" " + idStr + "  ").reset();
            ansi.a(timeStr);
            ansi.fgGreen().a(ipStr).reset();

            if (location.equals("Resolving")) {
                ansi.fgYellow().a(locStr).reset();
            } else if (location.equals("Failed")) {
                ansi.fgRed().a(locStr).reset();
            } else {
                ansi.fgDefault().a(locStr).reset();
            }

            ansi.fgDefault().a(" ").a(ispStr).reset();

            if (SUSPICIOUS_PATHS.contains(record.path)) {
                ansi.fgRed().a(" ").a(pathStr).reset();
            } else {
                ansi.fgDefault().a(" ").a(pathStr).reset();
            }

            ansi.fgBrightBlack().a(" ").a(uaStr).reset();
            ansi.a("\n");
        }

        System.out.print(ansi.toString());
        System.out.flush();
    }
}
