package com.snabel.resource;

import com.snabel.config.AppConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@jakarta.ws.rs.Path("/api/git")
@Produces(MediaType.APPLICATION_JSON)
public class GitResource {

    @Inject
    AppConfig appConfig;

    /**
     * Reset frontend to HEAD (git reset --hard HEAD)
     */
    @POST
    @jakarta.ws.rs.Path("/reset-frontend")
    public Response resetFrontend() {
        try {
            String frontendPath = appConfig.getFrontendPath();

            if (frontendPath == null || !Files.exists(Path.of(frontendPath))) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Frontend path not configured or does not exist"))
                    .build();
            }

            // Execute git reset --hard HEAD
            ProcessBuilder pb = new ProcessBuilder("git", "reset", "--hard", "HEAD");
            pb.directory(Path.of(frontendPath).toFile());
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
                return Response.ok(Map.of(
                    "message", "Frontend reset to HEAD successfully",
                    "output", output.toString()
                )).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                        "error", "Git reset failed",
                        "output", output.toString()
                    ))
                    .build();
            }

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to reset frontend: " + e.getMessage()))
                .build();
        }
    }
}
