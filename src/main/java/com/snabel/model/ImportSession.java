package com.snabel.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_sessions")
public class ImportSession extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String sessionId;

    @Column(nullable = false)
    public String description;

    @Column(nullable = true)
    public String branchName;

    @Column(length = 2000)
    public String originalInstructions;

    @Column(nullable = true)
    public String targetMfe;

    @Column(nullable = true)
    public String workingDirectory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SessionStatus status;

    public String claudeSessionId;

    public String zipFileName;

    public String unpackedPath;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    public LocalDateTime startedAt;

    public LocalDateTime completedAt;

    @Column(length = 5000)
    public String errorMessage;

    @Column(columnDefinition = "TEXT")
    public String outputLog;

    public Integer filesCreated = 0;
    public Integer filesModified = 0;
    public Integer filesDeleted = 0;

    public Boolean merged = false;
    public LocalDateTime mergedAt;

    public Boolean validated = false;
    public Boolean buildPassed = false;
    public Boolean testsPassed = false;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = SessionStatus.CREATED;
        }
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static ImportSession findBySessionId(String sessionId) {
        return find("sessionId", sessionId).firstResult();
    }

    public static ImportSession findByClaudeSessionId(String claudeSessionId) {
        return find("claudeSessionId", claudeSessionId).firstResult();
    }

    public enum SessionStatus {
        CREATED,        // Session created, ready to start
        UNPACKING,      // Unpacking zip file
        ANALYZING,      // Claude is analyzing the code
        TRANSFORMING,   // Claude is transforming code
        RUNNING,        // Claude Code is actively running
        PAUSED,         // Paused by user
        VALIDATING,     // Running validation checks
        COMPLETED,      // Successfully completed
        FAILED,         // Failed with errors
        MERGED          // Merged into main branch
    }
}
