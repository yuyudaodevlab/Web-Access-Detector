package com.accessdetector;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        int port = 8080;
        String redirectUrl = null;
        String serveFile = null;
        String logFile = null;
        boolean isIpv4 = false;
        boolean isIpv6 = false;
        boolean isCdn = false;
        boolean isCf = false;
        boolean isNgrok = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-p")) {
                if (i + 1 < args.length) {
                    try {
                        port = Integer.parseInt(args[++i]);
                        if (port < 1 || port > 65535) {
                            System.err.println("[ERROR] Port number must be between 1 and 65535.");
                            System.exit(1);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("[ERROR] -p requires a port number argument.");
                        System.exit(1);
                    }
                } else {
                    System.err.println("[ERROR] -p requires a port number argument.");
                    System.exit(1);
                }
            } else if (arg.equals("-r")) {
                if (i + 1 < args.length) {
                    redirectUrl = args[++i];
                } else {
                    System.err.println("[ERROR] -r requires a redirect URL argument.");
                    System.exit(1);
                }
            } else if (arg.equals("-file")) {
                if (i + 1 < args.length) {
                    serveFile = args[++i];
                } else {
                    System.err.println("[ERROR] -file requires a filename argument.");
                    System.exit(1);
                }
            } else if (arg.equals("-l")) {
                if (i + 1 < args.length) {
                    logFile = args[++i];
                } else {
                    System.err.println("[ERROR] -l requires a log filename argument.");
                    System.exit(1);
                }
            } else if (arg.equals("-ipv4")) {
                isIpv4 = true;
            } else if (arg.equals("-ipv6")) {
                isIpv6 = true;
            } else if (arg.equals("--cdn")) {
                isCdn = true;
            } else if (arg.equals("-cf")) {
                isCf = true;
            } else if (arg.equals("-ngrok")) {
                isNgrok = true;
                isCdn = true; // -ngrok automatically enables X-Forwarded-For IP extraction
            }
        }

        if (redirectUrl != null && serveFile != null) {
            System.err.println("[ERROR] -r and -file cannot be used together.");
            System.exit(1);
        }

        if (isIpv4 && isIpv6) {
            System.err.println("[ERROR] -ipv4 and -ipv6 cannot be used together.");
            System.exit(1);
        }

        String bindAddress = "0.0.0.0";
        if (isIpv6) {
            bindAddress = "::";
        }

        ConsoleDisplay display = new ConsoleDisplay(port, bindAddress, logFile, redirectUrl, isNgrok);
        display.init();

        NgrokManager ngrokManager = null;
        if (isNgrok) {
            ngrokManager = new NgrokManager();
            try {
                String url = ngrokManager.startTunnel(port);
                display.setNgrokUrl(url);
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to start ngrok tunnel: " + e.getMessage());
            }
        }

        display.printStartupBanner();

        LogWriter logWriter = null;
        if (logFile != null) {
            logWriter = new LogWriter(logFile);
            logWriter.start();
        }

        try {
            HttpDetectorServer server = new HttpDetectorServer(
                    bindAddress, port, isCf, isCdn, isNgrok, redirectUrl, serveFile, display, logWriter
            );
            server.start();

            NgrokManager finalNgrokManager = ngrokManager;
            LogWriter finalLogWriter = logWriter;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                display.cleanup();
                if (finalNgrokManager != null) {
                    finalNgrokManager.stopTunnel();
                }
                if (finalLogWriter != null) {
                    finalLogWriter.close();
                }
            }));

        } catch (java.net.BindException e) {
            System.err.println("[ERROR] Port " + port + " is already in use. Please choose a different port.");
            display.cleanup();
            if (ngrokManager != null) {
                ngrokManager.stopTunnel();
            }
            if (logWriter != null) {
                logWriter.close();
            }
            System.exit(1);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to start server: " + e.getMessage());
            display.cleanup();
            if (ngrokManager != null) {
                ngrokManager.stopTunnel();
            }
            if (logWriter != null) {
                logWriter.close();
            }
            System.exit(1);
        }
    }
}
