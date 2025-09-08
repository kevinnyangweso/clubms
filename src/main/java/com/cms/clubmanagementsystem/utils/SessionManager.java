package com.cms.clubmanagementsystem.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public final class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    // Default timeout: 30 minutes (in milliseconds)
    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000;

    private static UUID currentUserId;
    private static UUID currentSchoolId;
    private static String currentUsername;
    private static String currentUserRole;
    private static String currentSchoolName;
    private static Connection sessionConnection; // dedicated connection for the logged-in user

    // Transient Data Storage with timeout support
    private static final Map<String, TransientDataWrapper> transientData = new HashMap<>();

    // Wrapper class to store data with creation timestamp
    private static class TransientDataWrapper {
        private final Object data;
        private final long createdAt;

        public TransientDataWrapper(Object data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
        }

        public Object getData() {
            return data;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public boolean isExpired(long timeoutMs) {
            return (System.currentTimeMillis() - createdAt) > timeoutMs;
        }
    }

    private SessionManager() {}

    /**
     * Get current user role
     */
    public static synchronized String getCurrentUserRole() {
        return currentUserRole;
    }

    public static synchronized void createSession(String username, UUID userId, UUID schoolId, String schoolName, String role) {
        closeSession();
        currentUserId = userId;
        currentUsername = username;
        currentSchoolId = schoolId;
        currentSchoolName = schoolName; // This line is crucial
        currentUserRole = role;
        logger.info("Session created for username={} userId={} schoolId={} schoolName={} role={}",
                username, userId, schoolId, schoolName, role);
    }

    public static synchronized String getCurrentSchoolName() {
        return currentSchoolName;
    }
    public static synchronized Connection getSessionConnection() {
        return sessionConnection;
    }
    public static synchronized UUID getCurrentUserId() {
        return currentUserId; }
    public static synchronized UUID getCurrentSchoolId() {
        return currentSchoolId; }
    public static synchronized String getCurrentUsername() {
        return currentUsername; }

    /**
     * Clear the current user session (logout) - simple version that just clears session variables
     */
    public static synchronized void logout() {
        currentUsername = null;
        currentUserId = null;
        currentSchoolId = null;
        currentSchoolName = null;
        currentUserRole = null;
        logger.info("User logged out");
    }

    /**
     * Check if a user is currently logged in
     */
    public static synchronized boolean isLoggedIn() {
        return currentUserId != null && currentSchoolId != null;
    }

    public static synchronized void closeSession() {
        // Clear all transient data when the user session ends (logs out)
        clearTransientData();

        if (sessionConnection != null) {
            try {
                // Clear session-level tenant variable to avoid leakage if connection returns to pool
                try {
                    sessionConnection.createStatement().execute("SELECT set_config('" +
                            "app.current_school_id', '00000000-0000-0000-0000-000000000000', false)");
                } catch (Exception ex) {
                    logger.warn("Failed to clear app.current_school_id on session close: {}",
                            ex.getMessage());
                }
                sessionConnection.close();
            } catch (Exception e) {
                logger.warn("Error closing session connection: {}", e.getMessage());
            } finally {
                sessionConnection = null;
                currentUserId = null;
                currentSchoolId = null;
                currentUsername = null;
                currentUserRole = null;
                logger.info("Session closed");
            }
        }
    }

    // --- Enhanced Transient Data Methods with Timeout ---

    /**
     * Stores transient data with default timeout (15 minutes)
     */
    public static synchronized void setTransientData(String key, Object value) {
        setTransientData(key, value, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Stores transient data with custom timeout
     * @param key The data key
     * @param value The data value
     * @param timeoutMs Timeout in milliseconds
     */
    public static synchronized void setTransientData(String key, Object value, long timeoutMs) {
        transientData.put(key, new TransientDataWrapper(value));
        logger.debug("Transient data set for key: {} with {}ms timeout", key, timeoutMs);
    }

    /**
     * Retrieves transient data if it exists and hasn't expired
     */
    public static synchronized Object getTransientData(String key) {
        return getTransientData(key, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Retrieves transient data with custom timeout check
     */
    public static synchronized Object getTransientData(String key, long timeoutMs) {
        TransientDataWrapper wrapper = transientData.get(key);
        if (wrapper == null) {
            return null;
        }

        if (wrapper.isExpired(timeoutMs)) {
            transientData.remove(key);
            logger.debug("Transient data expired for key: {}", key);
            return null;
        }

        return wrapper.getData();
    }

    /**
     * Checks if transient data exists and hasn't expired
     */
    public static synchronized boolean hasValidTransientData(String key) {
        return hasValidTransientData(key, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Checks if transient data exists and hasn't expired with custom timeout
     */
    public static synchronized boolean hasValidTransientData(String key, long timeoutMs) {
        TransientDataWrapper wrapper = transientData.get(key);
        if (wrapper == null) {
            return false;
        }

        if (wrapper.isExpired(timeoutMs)) {
            transientData.remove(key);
            return false;
        }

        return true;
    }

    /**
     * Removes specific transient data
     */
    public static synchronized void removeTransientData(String key) {
        transientData.remove(key);
        logger.debug("Transient data removed for key: {}", key);
    }

    /**
     * Clears all transient data
     */
    public static synchronized void clearTransientData() {
        transientData.clear();
        logger.debug("All transient data cleared.");
    }

    /**
     * Cleans up expired transient data entries
     */
    public static synchronized void cleanupExpiredData() {
        cleanupExpiredData(DEFAULT_TIMEOUT_MS);
    }

    /**
     * Cleans up expired transient data entries with custom timeout
     */
    public static synchronized void cleanupExpiredData(long timeoutMs) {
        int removedCount = 0;
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, TransientDataWrapper> entry : transientData.entrySet()) {
            if (currentTime - entry.getValue().getCreatedAt() > timeoutMs) {
                transientData.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.debug("Cleaned up {} expired transient data entries", removedCount);
        }
    }

    /**
     * Check if user is ANY coordinator (for viewing access)
     * Returns true for both active and inactive coordinators
     */
    public static synchronized boolean isCoordinator() {
        if (currentUserId == null || currentSchoolId == null) {
            return false;
        }

        String sql = "SELECT role FROM users WHERE user_id = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, currentUserId);
            stmt.setObject(2, currentSchoolId);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && "club_coordinator".equals(rs.getString("role"));

        } catch (SQLException e) {
            logger.error("Failed to check coordinator role: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if user is an ACTIVE coordinator (for editing access)
     * Returns true only for coordinators with both flags set to true
     */
    public static synchronized boolean isActiveCoordinator() {
        if (currentUserId == null || currentSchoolId == null) {
            return false;
        }

        String sql = "SELECT is_active, is_active_coordinator FROM users WHERE user_id = ? " +
                "AND school_id = ? AND role = 'club_coordinator'";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, currentUserId);
            stmt.setObject(2, currentSchoolId);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getBoolean("is_active") && rs.getBoolean("is_active_coordinator");

        } catch (SQLException e) {
            logger.error("Failed to check coordinator permissions: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if user is a teacher
     */
    public static synchronized boolean isTeacher() {
        if (currentUserId == null || currentSchoolId == null) {
            return false;
        }

        String sql = "SELECT role FROM users WHERE user_id = ? AND school_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, currentUserId);
            stmt.setObject(2, currentSchoolId);

            ResultSet rs = stmt.executeQuery();
            return rs.next() && "teacher".equals(rs.getString("role"));

        } catch (SQLException e) {
            logger.error("Failed to check teacher role: {}", e.getMessage(), e);
            return false;
        }
    }
}
