package com.snabel.service;

import com.snabel.config.AppConfig;
import com.snabel.websocket.LogWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class GitService {

    @Inject
    AppConfig appConfig;

    @Inject
    LogWebSocket logWebSocket;

    /**
     * Create a new branch for the import session
     */
    public String createBranch(String sessionId, String description) throws IOException {
        String branchName = generateBranchName(description);
        String frontendPath = appConfig.getFrontendPath();

        logInfo(sessionId, "Creating branch: " + branchName);

        // Ensure we're in frontend directory and on main
        try {
            executeGitCommand(sessionId, frontendPath, "git", "checkout", "main");
        } catch (IOException e) {
            logInfo(sessionId, "Could not checkout main, trying master...");
            try {
                executeGitCommand(sessionId, frontendPath, "git", "checkout", "master");
            } catch (IOException e2) {
                logInfo(sessionId, "Warning: Could not checkout main/master, staying on current branch");
            }
        }

        // Try to pull if remote exists
        try {
            executeGitCommand(sessionId, frontendPath, "git", "pull", "origin", "HEAD");
        } catch (IOException e) {
            logInfo(sessionId, "Warning: Could not pull from origin (no remote configured)");
        }

        // Create new branch
        executeGitCommand(sessionId, frontendPath, "git", "checkout", "-b", branchName);

        logInfo(sessionId, "Branch created successfully: " + branchName);
        return branchName;
    }

    /**
     * Merge branch into main with conflict resolution
     */
    public void mergeBranch(String sessionId, String branchName, String commitMessage) throws IOException {
        String frontendPath = appConfig.getFrontendPath();

        logInfo(sessionId, "Starting merge of branch: " + branchName);

        // Commit any pending changes in the branch
        try {
            executeGitCommand(sessionId, frontendPath, "git", "add", ".");
            executeGitCommand(sessionId, frontendPath, "git", "commit", "-m",
                "Final changes before merge - Session: " + sessionId);
        } catch (IOException e) {
            logInfo(sessionId, "No changes to commit before merge");
        }

        // Switch to main and pull latest
        executeGitCommand(sessionId, frontendPath, "git", "checkout", "main");
        executeGitCommand(sessionId, frontendPath, "git", "pull", "origin", "main");

        // Attempt merge
        try {
            executeGitCommand(sessionId, frontendPath, "git", "merge", branchName, "--no-ff", "-m", commitMessage);
            logInfo(sessionId, "Branch merged successfully without conflicts");
        } catch (IOException e) {
            // Merge conflict - resolve automatically
            logInfo(sessionId, "Merge conflicts detected, resolving automatically...");
            resolveConflicts(sessionId, frontendPath);

            // Complete merge
            executeGitCommand(sessionId, frontendPath, "git", "add", ".");
            executeGitCommand(sessionId, frontendPath, "git", "commit", "-m",
                commitMessage + " (with auto-resolved conflicts)");

            logInfo(sessionId, "Conflicts resolved and merge completed");
        }

        // Delete the feature branch
        executeGitCommand(sessionId, frontendPath, "git", "branch", "-d", branchName);
        logInfo(sessionId, "Feature branch deleted: " + branchName);
    }

    /**
     * Resolve merge conflicts by preferring the latest version (theirs)
     */
    private void resolveConflicts(String sessionId, String workingDir) throws IOException {
        logInfo(sessionId, "Resolving conflicts by accepting latest version");

        // Get list of conflicted files
        String conflicts = executeGitCommand(sessionId, workingDir, "git", "diff", "--name-only", "--diff-filter=U");

        if (conflicts.isEmpty()) {
            logInfo(sessionId, "No conflicts found");
            return;
        }

        String[] conflictedFiles = conflicts.split("\n");
        for (String file : conflictedFiles) {
            if (file.trim().isEmpty()) continue;

            logInfo(sessionId, "Resolving conflict in: " + file);

            // Accept "theirs" (the incoming changes from the branch)
            executeGitCommand(sessionId, workingDir, "git", "checkout", "--theirs", file.trim());
            executeGitCommand(sessionId, workingDir, "git", "add", file.trim());
        }
    }

    /**
     * Get current git status
     */
    public String getStatus(String sessionId) throws IOException {
        String frontendPath = appConfig.getFrontendPath();
        return executeGitCommand(sessionId, frontendPath, "git", "status", "--short");
    }

    /**
     * Get diff for the current branch
     */
    public String getDiff(String sessionId, String branchName) throws IOException {
        String frontendPath = appConfig.getFrontendPath();
        return executeGitCommand(sessionId, frontendPath, "git", "diff", "main..." + branchName);
    }

    /**
     * Get list of changed files
     */
    public List<String> getChangedFiles(String sessionId, String branchName) throws IOException {
        String frontendPath = appConfig.getFrontendPath();
        String output = executeGitCommand(sessionId, frontendPath, "git", "diff", "--name-status", "main..." + branchName);

        return Arrays.stream(output.split("\n"))
            .filter(line -> !line.trim().isEmpty())
            .map(line -> {
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                    return parts[0] + " " + parts[1]; // Status + filename
                }
                return line;
            })
            .collect(Collectors.toList());
    }

    /**
     * Rollback to a specific commit
     */
    public void rollback(String sessionId, String commitHash) throws IOException {
        String frontendPath = appConfig.getFrontendPath();

        logInfo(sessionId, "Rolling back to commit: " + commitHash);
        executeGitCommand(sessionId, frontendPath, "git", "reset", "--hard", commitHash);
        logInfo(sessionId, "Rollback completed");
    }

    /**
     * Delete a branch
     */
    public void deleteBranch(String sessionId, String branchName) throws IOException {
        String frontendPath = appConfig.getFrontendPath();

        logInfo(sessionId, "Deleting branch: " + branchName);

        // Switch to main first
        executeGitCommand(sessionId, frontendPath, "git", "checkout", "main");

        // Force delete the branch
        executeGitCommand(sessionId, frontendPath, "git", "branch", "-D", branchName);

        logInfo(sessionId, "Branch deleted: " + branchName);
    }

    /**
     * Get current branch name
     */
    public String getCurrentBranch(String sessionId) throws IOException {
        String frontendPath = appConfig.getFrontendPath();
        return executeGitCommand(sessionId, frontendPath, "git", "rev-parse", "--abbrev-ref", "HEAD").trim();
    }

    /**
     * Execute a git command and return output
     */
    private String executeGitCommand(String sessionId, String workingDir, String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        logInfo(sessionId, "Executing: " + String.join(" ", command));

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                logInfo(sessionId, "  " + line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git command failed with exit code " + exitCode + ": " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }

        return output.toString();
    }

    /**
     * Generate a branch name from description
     */
    private String generateBranchName(String description) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String sanitized = description
            .toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");

        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return appConfig.getBranchPrefix() + "/" + sanitized + "-" + timestamp;
    }

    private void logInfo(String sessionId, String message) {
        logWebSocket.sendLog(sessionId, "GIT", message);
    }
}
