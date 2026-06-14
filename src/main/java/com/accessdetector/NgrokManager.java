package com.accessdetector;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NgrokManager {

    private NgrokClient ngrokClient;

    public String startTunnel(int port) throws Exception {
        // Display warning if no authtoken is found in ngrok config
        Path ngrokConfigPath = Paths.get(System.getProperty("user.home"), ".ngrok2", "ngrok.yml");
        Path ngrokConfigPath2 = Paths.get(System.getProperty("user.home"), "AppData", "Local", "ngrok", "ngrok.yml");
        Path ngrokConfigPath3 = Paths.get(System.getProperty("user.home"), ".config", "ngrok", "ngrok.yml");

        boolean hasToken = false;
        try {
            if (Files.exists(ngrokConfigPath) && new String(Files.readAllBytes(ngrokConfigPath)).contains("authtoken")) hasToken = true;
            if (Files.exists(ngrokConfigPath2) && new String(Files.readAllBytes(ngrokConfigPath2)).contains("authtoken")) hasToken = true;
            if (Files.exists(ngrokConfigPath3) && new String(Files.readAllBytes(ngrokConfigPath3)).contains("authtoken")) hasToken = true;
        } catch(Exception e) {}

        if (!hasToken) {
            System.err.println("[WARN] No ngrok authtoken found. Free-tier limits apply.");
            System.err.println("       See: https://dashboard.ngrok.com");
        }

        JavaNgrokConfig config = new JavaNgrokConfig.Builder()
            .build();

        ngrokClient = new NgrokClient.Builder()
            .withJavaNgrokConfig(config)
            .build();

        CreateTunnel createTunnel = new CreateTunnel.Builder()
            .withAddr(port)
            .build();

        Tunnel tunnel = ngrokClient.connect(createTunnel);
        return tunnel.getPublicUrl();  // e.g., "https://xxxx.ngrok-free.app"
    }

    public void stopTunnel() {
        if (ngrokClient != null) {
            ngrokClient.kill();
        }
    }
}
