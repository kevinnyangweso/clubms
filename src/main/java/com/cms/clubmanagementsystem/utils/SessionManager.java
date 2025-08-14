package com.cms.clubmanagementsystem.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.UUID;

public final class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private static UUID currentUserId;
    private static UUID currentSchoolId;
    private static String currentUsername;
    private static Connection sessionConnection; // dedicated connection for the logged-in user

    private SessionManager() {}

    public static synchronized void createSession(UUID userId, String username, UUID schoolId, Connection conn) {
        closeSession(); // close any previous session
        currentUserId = userId;
        currentUsername = username;
        currentSchoolId = schoolId;
        sessionConnection = conn;
        logger.info("Session created for username={} userId={} schoolId={}", username, userId, schoolId);
    }

    public static synchronized Connection getSessionConnection() {
        return sessionConnection;
    }

    public static synchronized UUID getCurrentUserId() { return currentUserId; }
    public static synchronized UUID getCurrentSchoolId() { return currentSchoolId; }
    public static synchronized String getCurrentUsername() { return currentUsername; }

    public static synchronized void closeSession() {
        if (sessionConnection != null) {
            try {
                // Clear session-level tenant variable to avoid leakage if connection returns to pool
                try {
                    sessionConnection.createStatement().execute("SELECT set_config('app.current_school_id', '00000000-0000-0000-0000-000000000000', false)");
                } catch (Exception ex) {
                    logger.warn("Failed to clear app.current_school_id on session close: {}", ex.getMessage());
                }
                sessionConnection.close();
            } catch (Exception e) {
                logger.warn("Error closing session connection: {}", e.getMessage());
            } finally {
                sessionConnection = null;
                currentUserId = null;
                currentSchoolId = null;
                currentUsername = null;
                logger.info("Session closed");
            }
        }
    }
}
