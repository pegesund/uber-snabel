package com.snabel.resource;

import com.snabel.config.AppConfig;
import com.snabel.model.ImportSession;
import com.snabel.service.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@jakarta.ws.rs.Path("/api/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    AppConfig appConfig;

    @Inject
    FileService fileService;

    @Inject
    GitService gitService;

    @Inject
    ClaudeCodeService claudeCodeService;

    @Inject
    ValidationService validationService;

    @Inject
    MfeDiscoveryService mfeDiscoveryService;

    /**
     * Get available MFEs
     */
    @GET
    @jakarta.ws.rs.Path("/mfes")
    public Response getMfes() {
        List<MfeDiscoveryService.MfeInfo> mfes = mfeDiscoveryService.discoverMfes();
        return Response.ok(mfes).build();
    }

    /**
     * Create a new import session
     */
    @POST
    @jakarta.ws.rs.Path("/session")
    @Transactional
    public Response createSession(CreateSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();

        ImportSession session = new ImportSession();
        session.sessionId = sessionId;
        session.description = request.description;
        session.originalInstructions = request.instructions;
        session.targetMfe = request.targetMfe;
        session.status = ImportSession.SessionStatus.CREATED;
        session.persist();

        return Response.ok(Map.of(
            "sessionId", sessionId,
            "status", session.status,
            "targetMfe", session.targetMfe != null ? session.targetMfe : "",
            "createdAt", session.createdAt
        )).build();
    }

    /**
     * Upload zip file for a session
     */
    @POST
    @jakarta.ws.rs.Path("/session/{sessionId}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadZip(
        @PathParam("sessionId") String sessionId,
        @RestForm FileUpload file
    ) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Session not found"))
                .build();
        }

        try {
            // Save uploaded file
            Path zipPath = fileService.saveUploadedFile(
                sessionId,
                Files.newInputStream(file.filePath()),
                file.fileName()
            );

            session.zipFileName = file.fileName();
            session.status = ImportSession.SessionStatus.UNPACKING;
            session.persist();

            // Unpack zip file
            Path unpackedPath = fileService.unpackZipFile(sessionId, zipPath);
            session.unpackedPath = unpackedPath.toString();

            // Analyze files
            FileService.FileAnalysis analysis = fileService.analyzeFiles(sessionId, unpackedPath);

            session.status = ImportSession.SessionStatus.ANALYZING;
            session.persist();

            return Response.ok(Map.of(
                "sessionId", sessionId,
                "status", session.status,
                "unpackedPath", unpackedPath.toString(),
                "analysis", Map.of(
                    "totalFiles", analysis.totalFiles,
                    "typescriptFiles", analysis.typescriptFiles,
                    "javascriptFiles", analysis.javascriptFiles,
                    "totalSizeMB", analysis.totalSize / 1024.0 / 1024.0
                )
            )).build();

        } catch (IOException e) {
            session.status = ImportSession.SessionStatus.FAILED;
            session.errorMessage = "Upload failed: " + e.getMessage();
            session.persist();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Start Claude Code transformation
     */
    @POST
    @jakarta.ws.rs.Path("/session/{sessionId}/start")
    @Transactional
    public Response startTransformation(
        @PathParam("sessionId") String sessionId,
        StartRequest request
    ) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Session not found"))
                .build();
        }

        try {
            // Create git branch
            String branchName = gitService.createBranch(sessionId, session.description);
            session.branchName = branchName;
            session.persist();

            // Build instructions for Claude
            String fullInstructions;

            // Check if this is an upload-based or direct session
            if (session.unpackedPath != null && !session.unpackedPath.isEmpty()) {
                // Upload-based session: analyze files and build comprehensive instructions
                Path unpackedPath = Path.of(session.unpackedPath);
                FileService.FileAnalysis analysis = fileService.analyzeFiles(sessionId, unpackedPath);

                String backendApiPath = Path.of(appConfig.getBackendPath(), "docs", "API.md").toString();
                String baseInstructions = fileService.buildClaudeInstructions(
                    session.description,
                    analysis,
                    backendApiPath,
                    session.targetMfe
                );

                fullInstructions = baseInstructions + "\n\n" +
                    "USER ADDITIONAL INSTRUCTIONS:\n" +
                    (request.additionalInstructions != null ? request.additionalInstructions : session.originalInstructions);
            } else {
                // Direct session: use user instructions with context
                fullInstructions = fileService.buildDirectInstructions(
                    session.description,
                    session.originalInstructions,
                    request.additionalInstructions,
                    Path.of(appConfig.getBackendPath(), "docs", "API.md").toString(),
                    session.targetMfe
                );
            }

            // Determine working directory based on target MFE
            String workingDirectory = session.targetMfe != null && !session.targetMfe.isEmpty()
                ? mfeDiscoveryService.getMfeWorkingDirectory(session.targetMfe)
                : appConfig.getFrontendPath();

            // Start Claude Code process in the appropriate directory
            claudeCodeService.startClaudeProcess(
                sessionId,
                fullInstructions,
                workingDirectory
            );

            return Response.ok(Map.of(
                "sessionId", sessionId,
                "status", "RUNNING",
                "branchName", branchName
            )).build();

        } catch (Exception e) {
            session.status = ImportSession.SessionStatus.FAILED;
            session.errorMessage = "Failed to start: " + e.getMessage();
            session.persist();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Stop Claude Code process
     */
    @POST
    @jakarta.ws.rs.Path("/session/{sessionId}/stop")
    @Transactional
    public Response stopTransformation(@PathParam("sessionId") String sessionId) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Session not found"))
                .build();
        }

        claudeCodeService.stopProcess(sessionId);

        return Response.ok(Map.of(
            "sessionId", sessionId,
            "status", "STOPPED"
        )).build();
    }

    /**
     * Merge branch to main
     */
    @POST
    @jakarta.ws.rs.Path("/session/{sessionId}/merge")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response mergeBranch(@PathParam("sessionId") String sessionId, Map<String, String> body) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Session not found"))
                .build();
        }

        if (session.branchName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "No branch to merge"))
                .build();
        }

        String commitMessage = body != null ? body.get("commitMessage") : null;
        if (commitMessage == null || commitMessage.trim().isEmpty()) {
            commitMessage = "Merge session: " + session.description;
        }

        try {
            gitService.mergeBranch(sessionId, session.branchName, commitMessage);

            session.merged = true;
            session.mergedAt = LocalDateTime.now();
            session.status = ImportSession.SessionStatus.MERGED;
            session.persist();

            return Response.ok(Map.of(
                "sessionId", sessionId,
                "status", "MERGED",
                "mergedAt", session.mergedAt
            )).build();

        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Merge failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Validate session code
     */
    @POST
    @jakarta.ws.rs.Path("/session/{sessionId}/validate")
    @Transactional
    public Response validateSession(@PathParam("sessionId") String sessionId) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Session not found"))
                .build();
        }

        ValidationService.ValidationResult result = validationService.validate(sessionId);

        session.validated = result.overallPassed;
        session.buildPassed = result.buildCheck;
        session.testsPassed = result.testsCheck;
        session.persist();

        return Response.ok(Map.of(
            "passed", result.overallPassed,
            "typescript", result.typescriptCheck,
            "apiCompatibility", result.apiCompatibilityCheck,
            "tests", result.testsCheck,
            "build", result.buildCheck,
            "error", result.errorMessage
        )).build();
    }

    /**
     * Get session status
     */
    @GET
    @jakarta.ws.rs.Path("/session/{sessionId}")
    public Response getSession(@PathParam("sessionId") String sessionId) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Session not found"))
                .build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.sessionId);
        response.put("description", session.description);
        response.put("status", session.status);
        response.put("branchName", session.branchName);
        response.put("createdAt", session.createdAt);
        response.put("completedAt", session.completedAt);
        response.put("merged", session.merged);
        response.put("errorMessage", session.errorMessage);
        response.put("filesCreated", session.filesCreated);
        response.put("filesModified", session.filesModified);
        response.put("filesDeleted", session.filesDeleted);
        response.put("isRunning", claudeCodeService.isRunning(sessionId));

        return Response.ok(response).build();
    }

    /**
     * List all sessions
     */
    @GET
    @jakarta.ws.rs.Path("/sessions")
    public Response listSessions(
        @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        List<ImportSession> sessions = ImportSession.listAll();

        List<Map<String, Object>> result = sessions.stream()
            .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
            .limit(limit)
            .map(s -> {
                Map<String, Object> map = new HashMap<>();
                map.put("sessionId", s.sessionId);
                map.put("description", s.description);
                map.put("status", s.status);
                map.put("createdAt", s.createdAt);
                map.put("merged", s.merged != null && s.merged);
                return map;
            })
            .toList();

        return Response.ok(result).build();
    }

    /**
     * Get git diff for session
     */
    @GET
    @jakarta.ws.rs.Path("/session/{sessionId}/diff")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDiff(@PathParam("sessionId") String sessionId) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (session.branchName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("No branch available")
                .build();
        }

        try {
            String diff = gitService.getDiff(sessionId, session.branchName);
            return Response.ok(diff).build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to get diff: " + e.getMessage())
                .build();
        }
    }

    /**
     * Get changed files for session
     */
    @GET
    @jakarta.ws.rs.Path("/session/{sessionId}/changes")
    public Response getChanges(@PathParam("sessionId") String sessionId) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (session.branchName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "No branch available"))
                .build();
        }

        try {
            List<String> changes = gitService.getChangedFiles(sessionId, session.branchName);
            return Response.ok(Map.of("changes", changes)).build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to get changes: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Send a command/query to the running Claude Code process
     */
    @POST
    @jakarta.ws.rs.Path("/session/{sessionId}/command")
    @Transactional
    public Response sendCommand(@PathParam("sessionId") String sessionId, CommandRequest request) {
        ImportSession session = ImportSession.findBySessionId(sessionId);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Session not found"))
                .build();
        }

        if (!claudeCodeService.isProcessRunning(sessionId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "No Claude Code process is running for this session"))
                .build();
        }

        try {
            claudeCodeService.sendCommandToProcess(sessionId, request.command);

            return Response.ok(Map.of(
                "sessionId", sessionId,
                "command", request.command,
                "sent", true
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to send command: " + e.getMessage()))
                .build();
        }
    }

    // DTOs
    public static class CreateSessionRequest {
        public String description;
        public String instructions;
        public String targetMfe;
    }

    public static class StartRequest {
        public String additionalInstructions;
    }

    public static class CommandRequest {
        public String command;
    }
}
