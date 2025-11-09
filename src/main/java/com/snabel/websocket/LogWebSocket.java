package com.snabel.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

@ServerEndpoint("/ws/logs/{sessionId}")
@ApplicationScoped
public class LogWebSocket {

    private static final Map<String, Set<Session>> sessions = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId) {
        System.out.println("WebSocket opened for session: " + sessionId);
        sessions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @OnClose
    public void onClose(Session session, @PathParam("sessionId") String sessionId) {
        System.out.println("WebSocket closed for session: " + sessionId);
        Set<Session> sessionSet = sessions.get(sessionId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                sessions.remove(sessionId);
            }
        }
    }

    @OnError
    public void onError(Session session, @PathParam("sessionId") String sessionId, Throwable throwable) {
        System.err.println("WebSocket error for session " + sessionId + ": " + throwable.getMessage());
        throwable.printStackTrace();
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("sessionId") String sessionId) {
        System.out.println("Received message from session " + sessionId + ": " + message);
        // Echo back for now (could be used for client commands later)
        sendToSession(session, createLogMessage("INFO", "Received: " + message));
    }

    /**
     * Send a log message to all clients connected to a specific session
     */
    public void sendLog(String sessionId, String level, String message) {
        Set<Session> sessionSet = sessions.get(sessionId);
        if (sessionSet != null) {
            String logMessage = createLogMessage(level, message);
            sessionSet.forEach(session -> sendToSession(session, logMessage));
        }
    }

    /**
     * Broadcast a message to all connected clients
     */
    public void broadcast(String message) {
        sessions.values().forEach(sessionSet ->
            sessionSet.forEach(session -> sendToSession(session, message))
        );
    }

    private void sendToSession(Session session, String message) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                System.err.println("Failed to send message to session: " + e.getMessage());
            }
        }
    }

    private String createLogMessage(String level, String message) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            logEntry.put("level", level);
            logEntry.put("message", message);
            return objectMapper.writeValueAsString(logEntry);
        } catch (Exception e) {
            // Fallback to simple message
            return String.format("[%s] %s: %s",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                level,
                message
            );
        }
    }

    /**
     * Get number of active connections for a session
     */
    public int getConnectionCount(String sessionId) {
        Set<Session> sessionSet = sessions.get(sessionId);
        return sessionSet != null ? sessionSet.size() : 0;
    }

    /**
     * Check if any clients are connected to a session
     */
    public boolean hasConnections(String sessionId) {
        return getConnectionCount(sessionId) > 0;
    }
}
