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
     * Reset frontend: if on figma-import branch, delete it and switch to main; otherwise reset to HEAD
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

            Path frontendDir = Path.of(frontendPath).toFile().getCanonicalFile().toPath();

            // Check current branch
            ProcessBuilder pbBranch = new ProcessBuilder("git", "branch", "--show-current");
            pbBranch.directory(frontendDir.toFile());
            pbBranch.redirectErrorStream(true);

            Process branchProcess = pbBranch.start();
            StringBuilder branchOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(branchProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    branchOutput.append(line);
                }
            }
            branchProcess.waitFor();

            String currentBranch = branchOutput.toString().trim();
            StringBuilder output = new StringBuilder();

            // If on a figma-import branch, delete it and switch to main
            if (currentBranch.startsWith("figma-import/")) {
                // Switch to main
                ProcessBuilder pbCheckout = new ProcessBuilder("git", "checkout", "main");
                pbCheckout.directory(frontendDir.toFile());
                pbCheckout.redirectErrorStream(true);

                Process checkoutProcess = pbCheckout.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(checkoutProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                int checkoutExit = checkoutProcess.waitFor();

                if (checkoutExit != 0) {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of("error", "Failed to switch to main", "output", output.toString()))
                        .build();
                }

                // Delete the figma-import branch
                ProcessBuilder pbDelete = new ProcessBuilder("git", "branch", "-D", currentBranch);
                pbDelete.directory(frontendDir.toFile());
                pbDelete.redirectErrorStream(true);

                Process deleteProcess = pbDelete.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(deleteProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                int deleteExit = deleteProcess.waitFor();

                if (deleteExit == 0) {
                    return Response.ok(Map.of(
                        "message", "Deleted branch " + currentBranch + " and switched to main",
                        "output", output.toString()
                    )).build();
                } else {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of("error", "Failed to delete branch", "output", output.toString()))
                        .build();
                }
            } else {
                // On main or other branch: just reset to HEAD
                ProcessBuilder pb = new ProcessBuilder("git", "reset", "--hard", "HEAD");
                pb.directory(frontendDir.toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
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
                        .entity(Map.of("error", "Git reset failed", "output", output.toString()))
                        .build();
                }
            }

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to reset frontend: " + e.getMessage()))
                .build();
        }
    }
}
