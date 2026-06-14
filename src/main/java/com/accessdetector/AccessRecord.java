package com.accessdetector;

public class AccessRecord {
    public final int    id;
    public final String timestamp;    // "HH:mm:ss" format
    public final String ip;
    public final String method;
    public final String path;
    public final String userAgent;

    // Filled asynchronously by GeoIpLookup
    public volatile String country  = "";
    public volatile String region   = "";
    public volatile String city     = "";
    public volatile String isp      = "";
    public volatile String geoStatus = "Resolving"; // "Resolving" | "Done" | "Failed"

    public AccessRecord(int id, String timestamp, String ip, String method, String path, String userAgent) {
        this.id = id;
        this.timestamp = timestamp;
        this.ip = ip;
        this.method = method;
        this.path = path;
        this.userAgent = userAgent;
    }
}
