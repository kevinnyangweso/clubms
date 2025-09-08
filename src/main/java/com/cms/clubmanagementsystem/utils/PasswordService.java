package com.cms.clubmanagementsystem.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PasswordService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordService.class);

    // Thread-safe storage for temporary passwords with expiration
    private static final Map<UUID, TempPasswordInfo> tempPasswords = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    // Password expiration time (30 minutes)
    private static final long PASSWORD_EXPIRATION_MINUTES = 30;

    static {
        // Schedule cleanup task to remove expired passwords
        cleanupScheduler.scheduleAtFixedRate(PasswordService::cleanupExpiredPasswords,
                1, 1, TimeUnit.MINUTES);
    }

    /**
     * Stores a temporary password in database (for transaction support)
     */
    public static boolean storeTempPassword(Connection conn, UUID userId, String tempPassword) throws SQLException {
        String sql = "INSERT INTO temp_passwords (user_id, temp_password, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setString(2, tempPassword);
            boolean success = stmt.executeUpdate() > 0;
            if (success) {
                logger.debug("Stored temp password in DB for user: {}", userId);
            }
            return success;
        }
    }


    /**
     * Retrieves a temporary password for a user (if not expired)
     */
    public static String getTempPassword(UUID userId) {
        TempPasswordInfo info = tempPasswords.get(userId);
        if (info != null && !info.isExpired()) {
            return info.getPassword();
        }
        return null;
    }

    /**
     * Removes a temporary password (after it's been displayed)
     */
    public static void clearTempPassword(UUID userId) {
        tempPasswords.remove(userId);
        logger.debug("Cleared temp password for user: {}", userId);
    }

    /**
     * Clean up expired passwords
     */
    private static void cleanupExpiredPasswords() {
        long currentTime = System.currentTimeMillis();
        tempPasswords.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(currentTime);
            if (expired) {
                logger.debug("Auto-cleared expired temp password for user: {}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * Shutdown the cleanup scheduler
     */
    public static void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Inner class to store password with timestamp
    private static class TempPasswordInfo {
        private final String password;
        private final long creationTime;

        public TempPasswordInfo(String password, long creationTime) {
            this.password = password;
            this.creationTime = creationTime;
        }

        public String getPassword() {
            return password;
        }

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        public boolean isExpired(long currentTime) {
            long elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(currentTime - creationTime);
            return elapsedMinutes >= PASSWORD_EXPIRATION_MINUTES;
        }
    }
}