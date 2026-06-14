package com.accessdetector;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LogWriter {
    private final String filename;
    private BufferedWriter writer;

    public LogWriter(String filename) {
        this.filename = filename;
    }

    public synchronized void start() {
        if (filename == null || filename.isEmpty()) {
            return;
        }
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename, true), StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to open log file: " + e.getMessage());
        }
    }

    public synchronized void log(AccessRecord record) {
        if (writer == null) {
            return;
        }
        try {
            // [2025-01-15 14:22:01] IP=203.0.113.1 PATH=/login METHOD=GET LOCATION=Japan,Tokyo ISP=AS2527 NTT UA=Mozilla/5.0 (Windows NT 10.0)
            String datePart = java.time.LocalDate.now().toString();
            String location = record.country;
            if (!record.city.isEmpty()) {
                location += "," + record.city;
            }
            if (location.isEmpty() && !record.region.isEmpty()) {
                location += "," + record.region;
            }
            if (location.startsWith(",")) location = location.substring(1);
            if (location.isEmpty()) location = "—";

            String isp = record.isp != null && !record.isp.isEmpty() ? record.isp : "—";

            String logLine = String.format("[%s %s] IP=%s PATH=%s METHOD=%s LOCATION=%s ISP=%s UA=%s",
                    datePart, record.timestamp, record.ip, record.path, record.method, location, isp, record.userAgent);

            writer.write(logLine);
            writer.write(System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to write to log file: " + e.getMessage());
        }
    }

    public synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
