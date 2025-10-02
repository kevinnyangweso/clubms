package com.cms.clubmanagementsystem.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000;

    private static final ThreadLocal<UserSession> currentSession = new ThreadLocal<>();
    private static final Map<String, TransientDataWrapper> transientData = new HashMap<>();

    private static class UserSession {
        private final UUID userId;
        private final UUID schoolId;
        private final String username;
        private final String schoolName;
        private final String role;
        private Connection sessionConnection;

        public UserSession(UUID userId, UUID schoolId, String username, String schoolName, String role) {
            this.userId = userId;
            this.schoolId = schoolId;
            this.username = username;
            this.schoolName = schoolName;
            this.role = role;
        }
    }

    private static class TransientDataWrapper {
        private final Object data;
        private final long createdAt;

        public TransientDataWrapper(Object data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
        }

        public Object getData() { return data; }
        public long getCreatedAt() { return createdAt; }
        public boolean isExpired(long timeoutMs) {
            return (System.currentTimeMillis() - createdAt) > timeoutMs;
        }
    }

    private SessionManager() {}

    public static synchronized void createSession(String username, UUID userId, UUID schoolId, String schoolName, String role) {
        closeSession();
        UserSession session = new UserSession(userId, schoolId, username, schoolName, role);
        currentSession.set(session);
        TenantContext.setCurrentUser(schoolId.toString(), userId.toString());
        logger.info("Session created for username={} userId={} schoolId={} schoolName={} role={}",
                username, userId, schoolId, schoolName, role);
    }

    public static synchronized String getCurrentUserRole() {
        UserSession session = currentSession.get();
        return session != null ? session.role : null;
    }

    public static synchronized UUID getCurrentUserId() {
        UserSession session = currentSession.get();
        return session != null ? session.userId : null;
    }

    public static synchronized UUID getCurrentSchoolId() {
        UserSession session = currentSession.get();
        return session != null ? session.schoolId : null;
    }

    public static synchronized String getCurrentUsername() {
        UserSession session = currentSession.get();
        return session != null ? session.username : null;
    }

    public static synchronized String getCurrentSchoolName() {
        UserSession session = currentSession.get();
        return session != null ? session.schoolName : null;
    }

    public static synchronized Connection getSessionConnection() {
        UserSession session = currentSession.get();
        return session != null ? session.sessionConnection : null;
    }

    public static synchronized void logout() {
        closeSession();
        logger.info("User logged out");
    }

    public static synchronized boolean isUserLoggedIn() {
        return currentSession.get() != null;
    }

    public static synchronized void closeSession() {
        UserSession session = currentSession.get();
        if (session != null) {
            if (session.sessionConnection != null) {
                try {
                    session.sessionConnection.createStatement().execute(
                            "SELECT set_config('app.current_school_id', '00000000-0000-0000-0000-000000000000', false)");
                    session.sessionConnection.close();
                } catch (SQLException e) {
                    logger.warn("Error closing session connection: {}", e.getMessage());
                }
            }
            clearTransientData();
            currentSession.remove();
            TenantContext.clear();
            logger.info("Session closed");
        }
    }

    // Transient data methods remain the same, just synchronized
    public static synchronized void setTransientData(String key, Object value) {
        setTransientData(key, value, DEFAULT_TIMEOUT_MS);
    }

    public static synchronized void setTransientData(String key, Object value, long timeoutMs) {
        transientData.put(key, new TransientDataWrapper(value));
        logger.debug("Transient data set for key: {} with {}ms timeout", key, timeoutMs);
    }

    public static synchronized Object getTransientData(String key) {
        return getTransientData(key, DEFAULT_TIMEOUT_MS);
    }

    public static synchronized Object getTransientData(String key, long timeoutMs) {
        TransientDataWrapper wrapper = transientData.get(key);
        if (wrapper == null || wrapper.isExpired(timeoutMs)) {
            transientData.remove(key);
            logger.debug("Transient data expired for key: {}", key);
            return null;
        }
        return wrapper.getData();
    }

    public static synchronized boolean hasValidTransientData(String key) {
        return hasValidTransientData(key, DEFAULT_TIMEOUT_MS);
    }

    public static synchronized boolean hasValidTransientData(String key, long timeoutMs) {
        TransientDataWrapper wrapper = transientData.get(key);
        if (wrapper == null || wrapper.isExpired(timeoutMs)) {
            transientData.remove(key);
            return false;
        }
        return true;
    }

    public static synchronized void removeTransientData(String key) {
        transientData.remove(key);
        logger.debug("Transient data removed for key: {}", key);
    }

    public static synchronized void clearTransientData() {
        transientData.clear();
        logger.debug("All transient data cleared.");
    }

    public static synchronized void cleanupExpiredData() {
        cleanupExpiredData(DEFAULT_TIMEOUT_MS);
    }

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

    public static synchronized boolean isCoordinator() {
        UserSession session = currentSession.get();
        if (session == null) return false;

        String sql = "SELECT role FROM users WHERE user_id = ? AND school_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, session.userId);
            stmt.setObject(2, session.schoolId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && "club_coordinator".equals(rs.getString("role"));
            }
        } catch (SQLException e) {
            logger.error("Failed to check coordinator role: {}", e.getMessage(), e);
            return false;
        }
    }

    public static synchronized boolean isActiveCoordinator() {
        UserSession session = currentSession.get();
        if (session == null) return false;

        String sql = "SELECT is_active, is_active_coordinator FROM users WHERE user_id = ? AND school_id = ? AND role = 'club_coordinator'";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, session.userId);
            stmt.setObject(2, session.schoolId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean("is_active") && rs.getBoolean("is_active_coordinator");
            }
        } catch (SQLException e) {
            logger.error("Failed to check coordinator permissions: {}", e.getMessage(), e);
            return false;
        }
    }

    public static synchronized boolean isTeacher() {
        UserSession session = currentSession.get();
        if (session == null) return false;

        String sql = "SELECT role FROM users WHERE user_id = ? AND school_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, session.userId);
            stmt.setObject(2, session.schoolId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && "teacher".equals(rs.getString("role"));
            }
        } catch (SQLException e) {
            logger.error("Failed to check teacher role: {}", e.getMessage(), e);
            return false;
        }
    }

    public static synchronized boolean canImportExcel() {
        return isActiveCoordinator();
    }

    public static synchronized void logImportAttempt(boolean authorized) {
        UserSession session = currentSession.get();
        if (session == null) return;

        logger.info("Excel import attempted by user: {}, school: {}, authorized: {}",
                session.userId, session.schoolId, authorized);

        String action = authorized ? "EXCEL_IMPORT_ATTEMPT_AUTHORIZED" : "EXCEL_IMPORT_ATTEMPT_UNAUTHORIZED";
        String sql = "INSERT INTO audit_logs (action_type, table_name, record_id, performed_by, school_id, ip_address, user_agent) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, action);
            stmt.setString(2, "excel_import");
            stmt.setString(3, "N/A");
            stmt.setObject(4, session.userId);
            stmt.setObject(5, session.schoolId);
            stmt.setString(6, getClientIP());
            stmt.setString(7, getClientUserAgent());
            stmt.executeUpdate();
            logger.debug("Audit log entry created for action: {}", action);
        } catch (SQLException e) {
            logger.error("Failed to log audit entry", e);
        }
    }

    private static String getClientIP() {
        return "127.0.0.1"; // Implement as needed
    }

    private static String getClientUserAgent() {
        return "JavaFX Application"; // Implement as needed
    }

}