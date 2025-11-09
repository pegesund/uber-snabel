package com.snabel.service;

import com.snabel.config.AppConfig;
import com.snabel.model.ImportSession;
import com.snabel.websocket.LogWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class ClaudeCodeService {

    @Inject
    AppConfig appConfig;

    @Inject
    LogWebSocket logWebSocket;

    private final Map<String, String> sessionClaudeIds = new ConcurrentHashMap<>();
    private final Map<String, Boolean> sessionFirstCommand = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> runningTasks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Start a Claude Code session - just initializes it
     */
    @Transactional
    public void startClaudeProcess(String sessionId, String instructions, String workingDirectory) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        session.status = ImportSession.SessionStatus.RUNNING;
        session.startedAt = LocalDateTime.now();
        session.workingDirectory = workingDirectory;

        // Generate a UUID for Claude session if not exists
        if (session.claudeSessionId == null) {
            session.claudeSessionId = UUID.randomUUID().toString();
        }

        sessionClaudeIds.put(sessionId, session.claudeSessionId);
        sessionFirstCommand.put(sessionId, true);
        session.persist();

        logInfo(sessionId, "Claude Code session initialized");
        logInfo(sessionId, "Working directory: " + workingDirectory);
        logInfo(sessionId, "Ready to receive commands");
    }

    /**
     * Send a command to Claude using --print mode
     */
    public void sendCommandToProcess(String sessionId, String command) throws IOException {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        String claudeSessionId = sessionClaudeIds.get(sessionId);
        if (claudeSessionId == null) {
            throw new IllegalStateException("No Claude session ID for session: " + sessionId);
        }

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                executeClaudeCommand(sessionId, command, claudeSessionId, session.workingDirectory);
            } catch (Exception e) {
                logError(sessionId, "Error executing command: " + e.getMessage());
            }
        });

        runningTasks.put(sessionId, task);
    }

    private void executeClaudeCommand(String sessionId, String command, String claudeSessionId, String workingDirectory) throws IOException, InterruptedException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("stdbuf");
        cmdList.add("-o0");
        cmdList.add(appConfig.getClaudeExecutable());
        cmdList.add("--print");
        cmdList.add("--verbose");
        cmdList.add("--output-format");
        cmdList.add("stream-json");

        if (appConfig.getClaudeUnsafeMode()) {
            cmdList.add("--dangerously-skip-permissions");
        }

        // Use --continue for subsequent commands to maintain conversation history
        Boolean isFirstCommand = sessionFirstCommand.get(sessionId);
        if (isFirstCommand != null && !isFirstCommand) {
            cmdList.add("--continue");
        } else if (isFirstCommand != null) {
            // Mark that we've sent the first command
            sessionFirstCommand.put(sessionId, false);
        }

        cmdList.add(command);

        logInfo(sessionId, "→ " + command);

        // Ensure working directory exists
        File workDir = new File(workingDirectory);
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.directory(workDir);
        pb.redirectErrorStream(true); // Merge stderr into stdout
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process process = pb.start();
        process.getOutputStream().close();

        // Read output line by line and stream to WebSocket
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            parseAndLogJsonLine(sessionId, line);
        }

        reader.close();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            logError(sessionId, "Command failed with exit code: " + exitCode);
        }
    }

    private void parseAndLogJsonLine(String sessionId, String jsonLine) {
        try {
            JsonNode root = objectMapper.readTree(jsonLine);
            String type = root.path("type").asText();

            if ("assistant".equals(type)) {
                // Extract Claude's response
                JsonNode content = root.path("message").path("content");
                if (content.isArray() && content.size() > 0) {
                    String text = content.get(0).path("text").asText();
                    if (!text.isEmpty()) {
                        logInfo(sessionId, "← " + text);
                    }
                }
            }
            // Don't log system init messages - they're just noise
        } catch (Exception e) {
            // Ignore malformed JSON
        }
    }

    public void stopProcess(String sessionId) {
        runningTasks.remove(sessionId);
        sessionClaudeIds.remove(sessionId);
        sessionFirstCommand.remove(sessionId);
        updateSessionStatus(sessionId, ImportSession.SessionStatus.PAUSED);
        logInfo(sessionId, "Session stopped");
    }

    public boolean isRunning(String sessionId) {
        CompletableFuture<Void> task = runningTasks.get(sessionId);
        return task != null && !task.isDone();
    }

    public boolean isProcessRunning(String sessionId) {
        // In --print mode, there's no long-running process
        // First check in-memory map
        if (sessionClaudeIds.containsKey(sessionId)) {
            return true;
        }

        // If not in memory, check database and restore to memory if session is active
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session != null && session.claudeSessionId != null &&
            (session.status == ImportSession.SessionStatus.RUNNING ||
             session.status == ImportSession.SessionStatus.TRANSFORMING)) {
            // Restore to in-memory map
            sessionClaudeIds.put(sessionId, session.claudeSessionId);
            sessionFirstCommand.putIfAbsent(sessionId, false); // Assume not first command if restoring
            return true;
        }

        return false;
    }

    // Logging helpers
    private void logInfo(String sessionId, String message) {
        logWebSocket.sendLog(sessionId, "INFO", message);
    }

    private void logError(String sessionId, String message) {
        logWebSocket.sendLog(sessionId, "ERROR", message);
    }

    // Database update helpers
    private void updateSessionCompleted(String sessionId) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session != null) {
            session.status = ImportSession.SessionStatus.COMPLETED;
            session.completedAt = LocalDateTime.now();
            session.persist();
        }
    }

    private void updateSessionError(String sessionId, String errorMessage) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session != null) {
            session.status = ImportSession.SessionStatus.FAILED;
            session.errorMessage = errorMessage;
            session.completedAt = LocalDateTime.now();
            session.persist();
        }
    }

    private void updateSessionStatus(String sessionId, ImportSession.SessionStatus status) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session != null) {
            session.status = status;
            session.persist();
        }
    }
}
