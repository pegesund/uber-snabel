package com.snabel.service;

import com.snabel.config.AppConfig;
import com.snabel.websocket.LogWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.*;

@ApplicationScoped
public class FileService {

    @Inject
    AppConfig appConfig;

    @Inject
    LogWebSocket logWebSocket;

    /**
     * Save uploaded zip file
     */
    public Path saveUploadedFile(String sessionId, InputStream inputStream, String fileName) throws IOException {
        Path uploadDir = Paths.get(appConfig.getTempDirectory(), "uploads", sessionId);
        Files.createDirectories(uploadDir);

        Path filePath = uploadDir.resolve(sanitizeFileName(fileName));

        logInfo(sessionId, "Saving uploaded file: " + fileName);

        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

        logInfo(sessionId, "File saved: " + filePath);

        return filePath;
    }

    /**
     * Unpack a zip file to a temporary directory
     */
    public Path unpackZipFile(String sessionId, Path zipFile) throws IOException {
        Path unpackDir = Paths.get(appConfig.getTempDirectory(), "unpacked", sessionId);

        // Clean up existing directory if it exists
        if (Files.exists(unpackDir)) {
            logInfo(sessionId, "Cleaning up existing unpacked directory");
            deleteDirectory(unpackDir);
        }

        Files.createDirectories(unpackDir);

        logInfo(sessionId, "Unpacking zip file: " + zipFile.getFileName());

        int fileCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = unpackDir.resolve(entry.getName());

                // Security check: prevent path traversal
                if (!entryPath.normalize().startsWith(unpackDir.normalize())) {
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // Create parent directories if needed
                    Files.createDirectories(entryPath.getParent());

                    // Extract file
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    fileCount++;

                    if (fileCount % 10 == 0) {
                        logInfo(sessionId, "Extracted " + fileCount + " files...");
                    }
                }

                zis.closeEntry();
            }
        }

        logInfo(sessionId, "Unpacking complete. Extracted " + fileCount + " files to: " + unpackDir);

        return unpackDir;
    }

    /**
     * Analyze unpacked files and generate summary
     */
    public FileAnalysis analyzeFiles(String sessionId, Path directory) throws IOException {
        logInfo(sessionId, "Analyzing unpacked files...");

        FileAnalysis analysis = new FileAnalysis();

        Files.walk(directory)
            .filter(Files::isRegularFile)
            .forEach(file -> {
                String fileName = file.getFileName().toString();
                String extension = getFileExtension(fileName);

                analysis.totalFiles++;

                if (extension.equals("ts") || extension.equals("tsx")) {
                    analysis.typescriptFiles++;
                } else if (extension.equals("js") || extension.equals("jsx")) {
                    analysis.javascriptFiles++;
                } else if (extension.equals("css") || extension.equals("scss")) {
                    analysis.styleFiles++;
                } else if (extension.equals("json")) {
                    analysis.jsonFiles++;
                } else if (extension.equals("html")) {
                    analysis.htmlFiles++;
                }

                try {
                    analysis.totalSize += Files.size(file);
                } catch (IOException e) {
                    // Ignore
                }
            });

        logInfo(sessionId, String.format(
            "Analysis: %d files, %d TS/TSX, %d JS/JSX, %d CSS, %d JSON, %d HTML, %.2f MB total",
            analysis.totalFiles,
            analysis.typescriptFiles,
            analysis.javascriptFiles,
            analysis.styleFiles,
            analysis.jsonFiles,
            analysis.htmlFiles,
            analysis.totalSize / 1024.0 / 1024.0
        ));

        return analysis;
    }

    /**
     * Build instructions for Claude Code based on file analysis
     */
    public String buildClaudeInstructions(String userDescription, FileAnalysis analysis, String backendApiPath, String targetMfe) {
        StringBuilder instructions = new StringBuilder();

        instructions.append("You are tasked with transforming uploaded TypeScript code into production-ready React code.\n\n");

        instructions.append("PROJECT CONTEXT:\n");
        instructions.append("- User description: ").append(userDescription).append("\n");
        instructions.append("- Source code statistics: ")
            .append(analysis.totalFiles).append(" files, ")
            .append(analysis.typescriptFiles).append(" TypeScript files\n");

        if (targetMfe != null && !targetMfe.isEmpty()) {
            instructions.append("- Target MFE: ").append(targetMfe).append("\n");
            instructions.append("- You are working in the '").append(targetMfe).append("' micro-frontend app\n");
        }
        instructions.append("\n");

        instructions.append("REQUIREMENTS:\n");
        instructions.append("1. Transform all components into proper React components\n");
        instructions.append("2. Use Tailwind CSS for all styling (remove inline styles)\n");
        instructions.append("3. ONLY use the backend API documented below - do NOT create fake/mock endpoints\n");
        instructions.append("4. Follow the Nx micro-frontend architecture in snabel_frontend\n");
        instructions.append("5. Use TypeScript with proper type safety\n");
        instructions.append("6. Implement proper error handling\n");
        instructions.append("7. Add loading states for async operations\n");
        instructions.append("8. Follow React best practices (hooks, functional components)\n");
        instructions.append("9. Reuse components from the shared design-system package when possible\n\n");

        // Load and include backend API documentation
        String apiDocs = loadBackendApiDocs(backendApiPath);
        if (apiDocs != null) {
            instructions.append("=== BACKEND API DOCUMENTATION ===\n\n");
            instructions.append(apiDocs);
            instructions.append("\n\n=== END OF API DOCUMENTATION ===\n\n");
        } else {
            // Fallback if API docs can't be loaded
            instructions.append("BACKEND API (Base URL: http://localhost:8080):\n");
            instructions.append("- Authentication: JWT tokens via POST /api/auth/login\n");
            instructions.append("- Available endpoints:\n");
            instructions.append("  * GET /api/accounts - List accounts\n");
            instructions.append("  * POST /api/accounts - Create account\n");
            instructions.append("  * GET /api/invoices - List invoices\n");
            instructions.append("  * POST /api/invoices - Create invoice\n");
            instructions.append("- All endpoints (except login) require Authorization: Bearer <token>\n");
            instructions.append("- Refer to ").append(backendApiPath).append(" for complete API details\n\n");
        }

        instructions.append("DELIVERABLES:\n");
        if (targetMfe != null && !targetMfe.isEmpty()) {
            instructions.append("- Place all components in the '").append(targetMfe).append("' MFE directory (apps/").append(targetMfe).append("/src/)\n");
            instructions.append("- Follow the routing conventions for the '").append(targetMfe).append("' MFE\n");
            instructions.append("- Use the shared design-system package from packages/design-system\n");
        } else {
            instructions.append("- Convert all components to the appropriate MFE (invoicing, expenses, reports, etc.)\n");
            instructions.append("- Each MFE is in apps/{mfe-name}/ directory\n");
        }
        instructions.append("- Implement proper routing using react-router-dom\n");
        instructions.append("- Add API integration using the exact endpoints documented above\n");
        instructions.append("- Ensure code passes TypeScript compilation\n");
        instructions.append("- Write clean, maintainable code\n");
        instructions.append("- Use Tailwind CSS for all styling\n\n");

        if (targetMfe != null && !targetMfe.isEmpty()) {
            instructions.append("IMPORTANT: You are working in apps/").append(targetMfe).append("/ - make all changes within this MFE directory.\n\n");
        }

        instructions.append("Get started by analyzing the source code and creating a plan for the transformation.");

        return instructions.toString();
    }

    /**
     * Build instructions for direct Claude Code session (without upload)
     */
    public String buildDirectInstructions(String description, String originalInstructions,
                                         String additionalInstructions, String backendApiPath,
                                         String targetMfe) {
        StringBuilder instructions = new StringBuilder();

        instructions.append("You are working on a development task for the Snabel accounting system.\n\n");

        instructions.append("PROJECT CONTEXT:\n");
        instructions.append("- Task: ").append(description).append("\n");

        if (targetMfe != null && !targetMfe.isEmpty()) {
            instructions.append("- Target MFE: ").append(targetMfe).append("\n");
            instructions.append("- Working directory: apps/").append(targetMfe).append("/\n");
        }
        instructions.append("\n");

        instructions.append("ARCHITECTURE:\n");
        instructions.append("- Frontend: Nx monorepo with micro-frontends (Module Federation)\n");
        instructions.append("- MFEs: shell, dashboard, invoicing, expenses, reports, clients\n");
        instructions.append("- Styling: Tailwind CSS\n");
        instructions.append("- Shared components: packages/design-system\n");
        instructions.append("- TypeScript with React functional components\n\n");

        // Load and include backend API documentation
        String apiDocs = loadBackendApiDocs(backendApiPath);
        if (apiDocs != null) {
            instructions.append("=== BACKEND API DOCUMENTATION ===\n\n");
            instructions.append(apiDocs);
            instructions.append("\n\n=== END OF API DOCUMENTATION ===\n\n");
        } else {
            instructions.append("BACKEND API:\n");
            instructions.append("- Base URL: http://localhost:8080\n");
            instructions.append("- Authentication: JWT tokens via POST /api/auth/login\n");
            instructions.append("- Main endpoints: /api/accounts, /api/invoices\n");
            instructions.append("- All endpoints require Authorization: Bearer <token>\n\n");
        }

        instructions.append("REQUIREMENTS:\n");
        instructions.append("1. Follow the Nx micro-frontend architecture\n");
        instructions.append("2. Use TypeScript with proper type safety\n");
        instructions.append("3. Use Tailwind CSS for styling\n");
        instructions.append("4. Implement proper error handling\n");
        instructions.append("5. Add loading states for async operations\n");
        instructions.append("6. Use React best practices (hooks, functional components)\n");
        instructions.append("7. ONLY use backend API endpoints documented above\n");
        instructions.append("8. Reuse components from packages/design-system when possible\n\n");

        if (targetMfe != null && !targetMfe.isEmpty()) {
            instructions.append("IMPORTANT: You are working in apps/").append(targetMfe)
                .append("/ - make all changes within this MFE directory.\n\n");
        }

        instructions.append("USER TASK:\n");
        if (originalInstructions != null && !originalInstructions.isEmpty()) {
            instructions.append(originalInstructions).append("\n\n");
        }
        if (additionalInstructions != null && !additionalInstructions.isEmpty()) {
            instructions.append("ADDITIONAL INSTRUCTIONS:\n");
            instructions.append(additionalInstructions).append("\n\n");
        }

        instructions.append("Please analyze the task and start implementing the solution.");

        return instructions.toString();
    }

    /**
     * Load backend API documentation
     */
    private String loadBackendApiDocs(String backendApiPath) {
        try {
            Path apiDocsPath = Paths.get(backendApiPath, "docs", "API.md");
            if (Files.exists(apiDocsPath)) {
                return Files.readString(apiDocsPath);
            }
        } catch (Exception e) {
            System.err.println("Failed to load backend API docs: " + e.getMessage());
        }
        return null;
    }

    /**
     * Delete a directory and all its contents
     */
    public void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
            .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    System.err.println("Failed to delete: " + path + " - " + e.getMessage());
                }
            });
    }

    /**
     * Clean up old session files (older than configured timeout)
     */
    public void cleanupOldSessions() {
        Path tempDir = Paths.get(appConfig.getTempDirectory());

        if (!Files.exists(tempDir)) {
            return;
        }

        long maxAgeHours = appConfig.getSessionTimeoutHours();
        long maxAgeMillis = maxAgeHours * 60 * 60 * 1000;
        long now = System.currentTimeMillis();

        try {
            Files.list(tempDir)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        long lastModified = Files.getLastModifiedTime(dir).toMillis();
                        if (now - lastModified > maxAgeMillis) {
                            System.out.println("Cleaning up old session directory: " + dir);
                            deleteDirectory(dir);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to cleanup directory: " + dir + " - " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to list temp directories: " + e.getMessage());
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }

    private void logInfo(String sessionId, String message) {
        logWebSocket.sendLog(sessionId, "FILE", message);
    }

    public static class FileAnalysis {
        public int totalFiles = 0;
        public int typescriptFiles = 0;
        public int javascriptFiles = 0;
        public int styleFiles = 0;
        public int jsonFiles = 0;
        public int htmlFiles = 0;
        public long totalSize = 0;
    }
}
