package com.snabel.service;

import com.snabel.config.AppConfig;
import com.snabel.websocket.LogWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MfeDiscoveryService {

    @Inject
    AppConfig appConfig;

    @Inject
    LogWebSocket logWebSocket;

    /**
     * Discover available MFEs in the frontend project
     */
    public List<MfeInfo> discoverMfes() {
        List<MfeInfo> mfes = new ArrayList<>();
        String frontendPath = appConfig.getFrontendPath();

        try {
            // Look for apps directory in Nx workspace
            Path appsDir = Paths.get(frontendPath, "apps");

            if (!Files.exists(appsDir)) {
                System.err.println("Apps directory not found: " + appsDir);
                return getDefaultMfes(); // Return expected MFEs from documentation
            }

            // Scan for directories in apps/
            Files.list(appsDir)
                .filter(Files::isDirectory)
                .forEach(mfeDir -> {
                    String mfeName = mfeDir.getFileName().toString();
                    MfeInfo mfe = new MfeInfo();
                    mfe.name = mfeName;
                    mfe.path = mfeDir.toString();
                    mfe.route = inferRoute(mfeName);
                    mfe.port = inferPort(mfeName);
                    mfe.description = inferDescription(mfeName);
                    mfes.add(mfe);
                });

            if (mfes.isEmpty()) {
                return getDefaultMfes();
            }

            // Sort by name
            mfes.sort(Comparator.comparing(m -> m.name));

        } catch (IOException e) {
            System.err.println("Failed to discover MFEs: " + e.getMessage());
            return getDefaultMfes();
        }

        return mfes;
    }

    /**
     * Get default MFEs based on documentation
     */
    private List<MfeInfo> getDefaultMfes() {
        return Arrays.asList(
            createMfe("shell", "/", 4200, "Host app - layout, routing, auth UI"),
            createMfe("dashboard", "/", 4201, "Overview dashboard with quick stats"),
            createMfe("invoicing", "/invoices/*", 4202, "Invoice management and templates"),
            createMfe("expenses", "/expenses/*", 4203, "Expense tracking and approval"),
            createMfe("reports", "/reports/*", 4204, "Financial reports (P&L, balance sheet)"),
            createMfe("clients", "/clients/*", 4205, "Client directory and billing history")
        );
    }

    private MfeInfo createMfe(String name, String route, int port, String description) {
        MfeInfo mfe = new MfeInfo();
        mfe.name = name;
        mfe.path = Paths.get(appConfig.getFrontendPath(), "apps", name).toString();
        mfe.route = route;
        mfe.port = port;
        mfe.description = description;
        return mfe;
    }

    /**
     * Infer route from MFE name
     */
    private String inferRoute(String mfeName) {
        if (mfeName.equals("shell")) {
            return "/";
        } else if (mfeName.equals("dashboard")) {
            return "/";
        } else {
            return "/" + mfeName + "/*";
        }
    }

    /**
     * Infer port from MFE name (based on standard Nx convention)
     */
    private int inferPort(String mfeName) {
        switch (mfeName) {
            case "shell": return 4200;
            case "dashboard": return 4201;
            case "invoicing": return 4202;
            case "expenses": return 4203;
            case "reports": return 4204;
            case "clients": return 4205;
            default: return 4200 + mfeName.hashCode() % 100;
        }
    }

    /**
     * Infer description from MFE name
     */
    private String inferDescription(String mfeName) {
        switch (mfeName) {
            case "shell": return "Host app - layout, routing, auth UI";
            case "dashboard": return "Overview dashboard with quick stats";
            case "invoicing": return "Invoice management and templates";
            case "expenses": return "Expense tracking and approval";
            case "reports": return "Financial reports (P&L, balance sheet)";
            case "clients": return "Client directory and billing history";
            default: return "Micro-frontend: " + mfeName;
        }
    }

    /**
     * Get MFE by name
     */
    public MfeInfo getMfeByName(String name) {
        return discoverMfes().stream()
            .filter(mfe -> mfe.name.equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get working directory for MFE
     */
    public String getMfeWorkingDirectory(String mfeName) {
        if (mfeName == null || mfeName.isEmpty()) {
            return appConfig.getFrontendPath(); // Default to root
        }

        MfeInfo mfe = getMfeByName(mfeName);
        if (mfe != null) {
            return mfe.path;
        }

        // Fallback to constructing path
        return Paths.get(appConfig.getFrontendPath(), "apps", mfeName).toString();
    }

    /**
     * MFE Information
     */
    public static class MfeInfo {
        public String name;
        public String path;
        public String route;
        public int port;
        public String description;
    }
}
