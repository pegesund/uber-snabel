package com.snabel.config;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

@ApplicationScoped
@Startup
public class AppConfig {

    @ConfigProperty(name = "uber-snabel.frontend.path", defaultValue = "/home/petter/dev/snabel/snabel_frontend")
    String defaultFrontendPath;

    @ConfigProperty(name = "uber-snabel.backend.path", defaultValue = "/home/petter/dev/snabel/snabel_backend")
    String defaultBackendPath;

    @ConfigProperty(name = "uber-snabel.temp.directory", defaultValue = "/tmp/uber-snabel")
    String defaultTempDirectory;

    @ConfigProperty(name = "uber-snabel.claude.executable", defaultValue = "claude")
    String defaultClaudeExecutable;

    @ConfigProperty(name = "uber-snabel.claude.unsafe-mode", defaultValue = "true")
    Boolean defaultClaudeUnsafeMode;

    private Properties configProperties;
    private Path configFile;

    public AppConfig() {
        // Constructor - CDI will inject fields after this
    }

    @PostConstruct
    public void init() {
        loadConfig();
    }

    private void loadConfig() {
        configFile = Paths.get(System.getProperty("user.home"), ".uber-snabel", "config.ini");
        configProperties = new Properties();

        // Create config directory if it doesn't exist
        try {
            Files.createDirectories(configFile.getParent());

            if (Files.exists(configFile)) {
                try (InputStream input = Files.newInputStream(configFile)) {
                    configProperties.load(input);
                }
            } else {
                // Create default config file
                createDefaultConfig();
            }
        } catch (IOException e) {
            System.err.println("Error loading config file: " + e.getMessage());
        }
    }

    private void createDefaultConfig() throws IOException {
        configProperties.setProperty("frontend.path", defaultFrontendPath);
        configProperties.setProperty("backend.path", defaultBackendPath);
        configProperties.setProperty("temp.directory", defaultTempDirectory);
        configProperties.setProperty("claude.executable", defaultClaudeExecutable);
        configProperties.setProperty("claude.unsafe.mode", defaultClaudeUnsafeMode.toString());
        configProperties.setProperty("branch.prefix", "figma-import");
        configProperties.setProperty("upload.max.size.mb", "100");
        configProperties.setProperty("session.timeout.hours", "24");

        saveConfig();
    }

    public void saveConfig() throws IOException {
        try (OutputStream output = Files.newOutputStream(configFile)) {
            configProperties.store(output, "Uber Snabel Configuration");
        }
    }

    // Getters
    public String getFrontendPath() {
        return configProperties.getProperty("frontend.path", defaultFrontendPath);
    }

    public String getBackendPath() {
        return configProperties.getProperty("backend.path", defaultBackendPath);
    }

    public String getTempDirectory() {
        return configProperties.getProperty("temp.directory", defaultTempDirectory);
    }

    public String getClaudeExecutable() {
        return configProperties.getProperty("claude.executable", defaultClaudeExecutable);
    }

    public boolean getClaudeUnsafeMode() {
        return Boolean.parseBoolean(
            configProperties.getProperty("claude.unsafe.mode", defaultClaudeUnsafeMode.toString())
        );
    }

    public String getBranchPrefix() {
        return configProperties.getProperty("branch.prefix", "figma-import");
    }

    public int getUploadMaxSizeMb() {
        return Integer.parseInt(configProperties.getProperty("upload.max.size.mb", "100"));
    }

    public int getSessionTimeoutHours() {
        return Integer.parseInt(configProperties.getProperty("session.timeout.hours", "24"));
    }

    // Setters
    public void setFrontendPath(String path) {
        configProperties.setProperty("frontend.path", path);
    }

    public void setBackendPath(String path) {
        configProperties.setProperty("backend.path", path);
    }

    public void setTempDirectory(String path) {
        configProperties.setProperty("temp.directory", path);
    }

    public void setClaudeExecutable(String executable) {
        configProperties.setProperty("claude.executable", executable);
    }

    public void setClaudeUnsafeMode(boolean unsafe) {
        configProperties.setProperty("claude.unsafe.mode", String.valueOf(unsafe));
    }

    public void setBranchPrefix(String prefix) {
        configProperties.setProperty("branch.prefix", prefix);
    }

    public Properties getAllProperties() {
        return new Properties(configProperties);
    }

    public Path getConfigFile() {
        return configFile;
    }
}
