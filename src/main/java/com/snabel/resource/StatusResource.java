package com.snabel.resource;

import com.snabel.config.AppConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@jakarta.ws.rs.Path("/api/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @Inject
    AppConfig appConfig;

    /**
     * Check if frontend is running
     */
    @GET
    @jakarta.ws.rs.Path("/frontend")
    public Response getFrontendStatus() {
        Map<String, Object> status = new HashMap<>();
        String frontendPath = appConfig.getFrontendPath();

        status.put("configured", frontendPath != null);
        status.put("path", frontendPath);
        status.put("exists", Files.exists(Path.of(frontendPath)));

        // Check if frontend is actually responding (try IPv6 first since Angular dev server uses IPv6)
        String url = "http://[::1]:4200";
        boolean portOpen = checkPortInUse(4200);
        boolean responding = checkHttpService(url);

        status.put("running", responding);
        status.put("portOpen", portOpen);
        status.put("responding", responding);
        status.put("url", "http://localhost:4200");

        return Response.ok(status).build();
    }

    /**
     * Check if backend is running
     */
    @GET
    @jakarta.ws.rs.Path("/backend")
    public Response getBackendStatus() {
        Map<String, Object> status = new HashMap<>();
        String backendPath = appConfig.getBackendPath();

        status.put("configured", backendPath != null);
        status.put("path", backendPath);
        status.put("exists", Files.exists(Path.of(backendPath)));

        // Check if backend is actually responding
        String url = "http://127.0.0.1:8080/";
        boolean portOpen = checkPortInUse(8080);
        boolean responding = checkHttpService(url);

        status.put("running", responding);
        status.put("portOpen", portOpen);
        status.put("responding", responding);
        status.put("url", "http://localhost:8080");

        return Response.ok(status).build();
    }

    /**
     * Get system status
     */
    @GET
    public Response getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("uber-snabel", Map.of(
            "version", "1.0.0",
            "status", "running"
        ));

        status.put("config", Map.of(
            "frontendPath", appConfig.getFrontendPath(),
            "backendPath", appConfig.getBackendPath(),
            "tempDirectory", appConfig.getTempDirectory(),
            "claudeExecutable", appConfig.getClaudeExecutable(),
            "configFile", appConfig.getConfigFile().toString()
        ));

        return Response.ok(status).build();
    }

    /**
     * Get configuration
     */
    @GET
    @jakarta.ws.rs.Path("/config")
    public Response getConfig() {
        Map<String, String> config = new HashMap<>();

        Properties props = appConfig.getAllProperties();
        props.forEach((key, value) -> config.put(key.toString(), value.toString()));

        return Response.ok(config).build();
    }

    /**
     * Update configuration
     */
    @PUT
    @jakarta.ws.rs.Path("/config")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateConfig(Map<String, String> updates) {
        try {
            if (updates.containsKey("frontend.path")) {
                appConfig.setFrontendPath(updates.get("frontend.path"));
            }
            if (updates.containsKey("backend.path")) {
                appConfig.setBackendPath(updates.get("backend.path"));
            }
            if (updates.containsKey("temp.directory")) {
                appConfig.setTempDirectory(updates.get("temp.directory"));
            }
            if (updates.containsKey("claude.executable")) {
                appConfig.setClaudeExecutable(updates.get("claude.executable"));
            }
            if (updates.containsKey("branch.prefix")) {
                appConfig.setBranchPrefix(updates.get("branch.prefix"));
            }

            appConfig.saveConfig();

            return Response.ok(Map.of("message", "Configuration updated successfully")).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to update config: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Check if a port is in use
     */
    private boolean checkPortInUse(int port) {
        try {
            Process process = new ProcessBuilder("lsof", "-i", ":" + port)
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line != null; // If there's output, port is in use
            }
        } catch (Exception e) {
            // lsof might not be available, try netstat as fallback
            try {
                Process process = new ProcessBuilder("netstat", "-an")
                    .redirectErrorStream(true)
                    .start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(":" + port) && (line.contains("LISTEN") || line.contains("ESTABLISHED"))) {
                            return true;
                        }
                    }
                }
            } catch (Exception ex) {
                // If both fail, return false
            }
        }
        return false;
    }

    /**
     * Check if an HTTP service is responding
     */
    private boolean checkHttpService(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(2000); // 2 second timeout
            connection.setReadTimeout(2000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // Any 2xx or 3xx response means the service is responding
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false;
        }
    }
}
