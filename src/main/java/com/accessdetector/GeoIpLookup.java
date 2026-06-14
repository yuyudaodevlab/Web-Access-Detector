package com.accessdetector;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GeoIpLookup {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final ConcurrentHashMap<String, GeoResult> cache = new ConcurrentHashMap<>();

    public static class GeoResult {
        public final String status;
        public final String country;
        public final String regionName;
        public final String city;
        public final String isp;

        public GeoResult(String status, String country, String regionName, String city, String isp) {
            this.status = status;
            this.country = country;
            this.regionName = regionName;
            this.city = city;
            this.isp = isp;
        }
    }

    public static void lookup(AccessRecord record, ConsoleDisplay display, CopyOnWriteArrayList<AccessRecord> accessLog, LogWriter logWriter) {
        String ip = record.ip;

        // Check local and private
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            updateRecord(record, "Local Connection", "", "", "", "Done");
            display.refresh(accessLog);
            if (logWriter != null) logWriter.log(record);
            return;
        }

        if (isPrivateIp(ip)) {
            updateRecord(record, "Private IP", "", "", "", "Done");
            display.refresh(accessLog);
            if (logWriter != null) logWriter.log(record);
            return;
        }

        // Check cache
        if (cache.containsKey(ip)) {
            GeoResult res = cache.get(ip);
            if ("success".equals(res.status)) {
                updateRecord(record, res.country, res.regionName, res.city, res.isp, "Done");
            } else {
                updateRecord(record, "", "", "", "", "Failed");
            }
            display.refresh(accessLog);
            if (logWriter != null) logWriter.log(record);
            return;
        }

        // API Request
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,country,regionName,city,isp,query"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String status = json.optString("status");
                if ("success".equals(status)) {
                    GeoResult res = new GeoResult(
                            status,
                            json.optString("country"),
                            json.optString("regionName"),
                            json.optString("city"),
                            json.optString("isp")
                    );
                    cache.put(ip, res);
                    updateRecord(record, res.country, res.regionName, res.city, res.isp, "Done");
                } else {
                    System.err.println("[WARN]  GeoIP lookup failed for IP: " + ip);
                    GeoResult res = new GeoResult(status, "", "", "", "");
                    cache.put(ip, res);
                    updateRecord(record, "", "", "", "", "Failed");
                }
            } else {
                System.err.println("[WARN]  GeoIP lookup failed for IP: " + ip);
                updateRecord(record, "", "", "", "", "Failed");
            }
        } catch (Exception e) {
            System.err.println("[WARN]  GeoIP lookup failed for IP: " + ip);
            updateRecord(record, "", "", "", "", "Failed");
        }

        display.refresh(accessLog);
        if (logWriter != null) logWriter.log(record);
    }

    private static void updateRecord(AccessRecord record, String country, String region, String city, String isp, String geoStatus) {
        record.country = country;
        record.region = region;
        record.city = city;
        record.isp = isp;
        record.geoStatus = geoStatus;
    }

    private static boolean isPrivateIp(String ip) {
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) return true;
        if (ip.toLowerCase().startsWith("fd")) return true; // IPv6 Unique Local Address
        return false;
    }
}
