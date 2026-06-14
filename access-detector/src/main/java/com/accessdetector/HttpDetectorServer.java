package com.accessdetector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpDetectorServer {

    private final HttpServer server;
    private final ExecutorService geoIpExecutor;
    private final CopyOnWriteArrayList<AccessRecord> accessLog;
    private final ConsoleDisplay display;
    private final LogWriter logWriter;
    private final AtomicInteger idCounter = new AtomicInteger(1);

    private final boolean isCf;
    private final boolean isCdn;
    private final boolean isNgrok;
    private final String redirectUrl;
    private final String serveFile;
    private byte[] fileBytes = null;
    private String fileContentType = "application/octet-stream";

    // 1x1 transparent GIF pixel
    private static final byte[] TRANSPARENT_PIXEL = {
            71, 73, 70, 56, 57, 97, 1, 0, 1, 0, (byte) 128, 0, 0, 0, 0, 0,
            (byte) 255, (byte) 255, (byte) 255, 33, (byte) 249, 4, 1, 0, 0, 0, 0, 44,
            0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 1, 68, 0, 59
    };

    public HttpDetectorServer(String bindAddress, int port, boolean isCf, boolean isCdn, boolean isNgrok,
                              String redirectUrl, String serveFile, ConsoleDisplay display, LogWriter logWriter) throws IOException {
        this.isCf = isCf;
        this.isCdn = isCdn;
        this.isNgrok = isNgrok;
        this.redirectUrl = redirectUrl;
        this.serveFile = serveFile;
        this.display = display;
        this.logWriter = logWriter;
        this.accessLog = new CopyOnWriteArrayList<>();

        // Load file if needed
        if (serveFile != null) {
            java.nio.file.Path f = java.nio.file.Paths.get(serveFile);
            if (!Files.exists(f)) {
                System.err.println("[ERROR] File '" + serveFile + "' not found.");
                System.exit(1);
            }
            fileBytes = Files.readAllBytes(f);
            fileContentType = getContentType(serveFile);
        }

        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", new DetectorHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));

        geoIpExecutor = Executors.newFixedThreadPool(4);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        geoIpExecutor.shutdownNow();
    }

    private class DetectorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Extract IP
            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();

            if (isCf) {
                String cfIp = exchange.getRequestHeaders().getFirst("CF-Connecting-IP");
                if (cfIp != null && !cfIp.isEmpty()) {
                    ip = cfIp.split(",")[0].trim();
                }
            } else if (isCdn || isNgrok) {
                String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    ip = xff.split(",")[0].trim();
                }
            }

            // Clean IP
            if (ip.contains("%")) {
                ip = ip.substring(0, ip.indexOf("%"));
            }
            if (ip.startsWith("::ffff:")) {
                ip = ip.substring(7);
            }

            // Other fields
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            if (userAgent == null) userAgent = "";
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            // Create record
            AccessRecord record = new AccessRecord(idCounter.getAndIncrement(), timestamp, ip, method, path, userAgent);
            accessLog.add(record);

            // Trigger Display refresh
            display.refresh(accessLog);

            // Submitting GeoIP lookup to background thread
            geoIpExecutor.submit(() -> GeoIpLookup.lookup(record, display, accessLog, logWriter));

            // Response
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                exchange.getResponseHeaders().add("Location", redirectUrl);
                exchange.sendResponseHeaders(302, -1); // 302 Found, no body
            } else if (serveFile != null && fileBytes != null) {
                exchange.getResponseHeaders().add("Content-Type", fileContentType);
                exchange.sendResponseHeaders(200, fileBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(fileBytes);
                os.close();
            } else {
                exchange.getResponseHeaders().add("Content-Type", "image/gif");
                exchange.sendResponseHeaders(200, TRANSPARENT_PIXEL.length);
                OutputStream os = exchange.getResponseBody();
                os.write(TRANSPARENT_PIXEL);
                os.close();
            }
        }
    }

    private String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
