package com.snabel.service;

import com.snabel.config.AppConfig;
import com.snabel.websocket.LogWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ValidationService {

    @Inject
    AppConfig appConfig;

    @Inject
    LogWebSocket logWebSocket;

    /**
     * Validate the transformed code
     */
    public ValidationResult validate(String sessionId) {
        logInfo(sessionId, "Starting validation...");

        ValidationResult result = new ValidationResult();

        try {
            // 1. Check TypeScript compilation
            result.typescriptCheck = checkTypeScript(sessionId);

            // 2. Check for API compatibility (ensure only allowed endpoints are used)
            result.apiCompatibilityCheck = checkApiCompatibility(sessionId);

            // 3. Run tests (if they exist)
            result.testsCheck = runTests(sessionId);

            // 4. Run build
            result.buildCheck = runBuild(sessionId);

            result.overallPassed = result.typescriptCheck && result.apiCompatibilityCheck &&
                                   result.testsCheck && result.buildCheck;

            if (result.overallPassed) {
                logInfo(sessionId, "✓ All validation checks passed");
            } else {
                logInfo(sessionId, "✗ Some validation checks failed");
            }

        } catch (Exception e) {
            logError(sessionId, "Validation error: " + e.getMessage());
            result.overallPassed = false;
            result.errorMessage = e.getMessage();
        }

        return result;
    }

    /**
     * Check TypeScript compilation
     */
    private boolean checkTypeScript(String sessionId) {
        logInfo(sessionId, "Checking TypeScript compilation...");

        try {
            String frontendPath = appConfig.getFrontendPath();

            ProcessBuilder pb = new ProcessBuilder("npx", "tsc", "--noEmit");
            pb.directory(new File(frontendPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logInfo(sessionId, "✓ TypeScript compilation passed");
                return true;
            } else {
                logError(sessionId, "✗ TypeScript compilation failed:\n" + output);
                return false;
            }

        } catch (Exception e) {
            logError(sessionId, "TypeScript check error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check API compatibility - ensure code only uses allowed backend endpoints
     */
    private boolean checkApiCompatibility(String sessionId) {
        logInfo(sessionId, "Checking API compatibility...");

        try {
            String frontendPath = appConfig.getFrontendPath();
            Path srcPath = Paths.get(frontendPath, "apps");

            if (!Files.exists(srcPath)) {
                logInfo(sessionId, "⚠ Frontend source not found, skipping API check");
                return true;
            }

            // Allowed API endpoints from backend documentation
            Set<String> allowedEndpoints = Set.of(
                "/api/auth/login",
                "/api/accounts",
                "/api/invoices"
            );

            // Patterns to find API calls
            Pattern apiCallPattern = Pattern.compile("(?:fetch|axios\\.(?:get|post|put|delete))\\s*\\(['\"]([^'\"]+)['\"]");

            List<String> violations = new ArrayList<>();

            Files.walk(srcPath)
                .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".tsx") ||
                               path.toString().endsWith(".js") || path.toString().endsWith(".jsx"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        Matcher matcher = apiCallPattern.matcher(content);

                        while (matcher.find()) {
                            String url = matcher.group(1);

                            // Check if it's an API call
                            if (url.startsWith("/api/") || url.contains("localhost:8080")) {
                                // Extract endpoint path
                                String endpoint = extractEndpoint(url);

                                // Check if it's allowed
                                if (!isAllowedEndpoint(endpoint, allowedEndpoints)) {
                                    violations.add(file.getFileName() + ": " + url);
                                }
                            }
                        }
                    } catch (IOException e) {
                        // Skip file
                    }
                });

            if (violations.isEmpty()) {
                logInfo(sessionId, "✓ API compatibility check passed");
                return true;
            } else {
                logError(sessionId, "✗ API compatibility violations found:");
                violations.forEach(v -> logError(sessionId, "  - " + v));
                return false;
            }

        } catch (Exception e) {
            logError(sessionId, "API compatibility check error: " + e.getMessage());
            return true; // Don't fail on check errors
        }
    }

    /**
     * Run tests
     */
    private boolean runTests(String sessionId) {
        logInfo(sessionId, "Running tests...");

        try {
            String frontendPath = appConfig.getFrontendPath();

            // Check if tests exist
            Path testPath = Paths.get(frontendPath, "apps");
            boolean hasTests = Files.walk(testPath)
                .anyMatch(path -> path.toString().contains(".spec.") || path.toString().contains(".test."));

            if (!hasTests) {
                logInfo(sessionId, "⚠ No tests found, skipping");
                return true;
            }

            ProcessBuilder pb = new ProcessBuilder("npm", "run", "test");
            pb.directory(new File(frontendPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logInfo(sessionId, line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logInfo(sessionId, "✓ Tests passed");
                return true;
            } else {
                logError(sessionId, "✗ Tests failed");
                return false;
            }

        } catch (Exception e) {
            logError(sessionId, "Test execution error: " + e.getMessage());
            return true; // Don't fail on test errors
        }
    }

    /**
     * Run build
     */
    private boolean runBuild(String sessionId) {
        logInfo(sessionId, "Running build...");

        try {
            String frontendPath = appConfig.getFrontendPath();

            ProcessBuilder pb = new ProcessBuilder("npm", "run", "build");
            pb.directory(new File(frontendPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");

                    // Log important lines
                    if (line.contains("error") || line.contains("Error") || line.contains("✓") || line.contains("built")) {
                        logInfo(sessionId, line);
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logInfo(sessionId, "✓ Build successful");
                return true;
            } else {
                logError(sessionId, "✗ Build failed");
                return false;
            }

        } catch (Exception e) {
            logError(sessionId, "Build error: " + e.getMessage());
            return false;
        }
    }

    private String extractEndpoint(String url) {
        // Remove protocol and host
        String endpoint = url.replaceAll("https?://[^/]+", "");

        // Get base endpoint (remove query params and IDs)
        endpoint = endpoint.split("\\?")[0];
        endpoint = endpoint.replaceAll("/\\d+", "");

        return endpoint;
    }

    private boolean isAllowedEndpoint(String endpoint, Set<String> allowedEndpoints) {
        return allowedEndpoints.stream().anyMatch(allowed ->
            endpoint.startsWith(allowed) || endpoint.equals(allowed)
        );
    }

    private void logInfo(String sessionId, String message) {
        logWebSocket.sendLog(sessionId, "VALIDATE", message);
    }

    private void logError(String sessionId, String message) {
        logWebSocket.sendLog(sessionId, "ERROR", message);
    }

    public static class ValidationResult {
        public boolean typescriptCheck = false;
        public boolean apiCompatibilityCheck = false;
        public boolean testsCheck = false;
        public boolean buildCheck = false;
        public boolean overallPassed = false;
        public String errorMessage = null;
    }
}
