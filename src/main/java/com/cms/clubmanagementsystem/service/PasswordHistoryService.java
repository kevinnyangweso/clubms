package com.cms.clubmanagementsystem.service;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PasswordHistoryService {

    private static final int PASSWORD_HISTORY_LIMIT = 5;

    /**
     * Checks if password has been used in last N resets
     */
    public boolean isPasswordInHistory(Connection conn, UUID userId, UUID schoolId, String newPasswordHash) throws SQLException {
        String sql = "SELECT password_hash FROM password_history " +
                "WHERE user_id = ? AND school_id = ? " +
                "ORDER BY created_at DESC " +
                "LIMIT ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, schoolId);
            stmt.setInt(3, PASSWORD_HISTORY_LIMIT);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String oldHash = rs.getString("password_hash");
                    if (BCrypt.checkpw(newPasswordHash, oldHash)) {
                        return true; // Password found in history
                    }
                }
            }
        }
        return false;
    }

    /**
     * Adds a password to history
     */
    public void addToPasswordHistory(Connection conn, UUID userId, UUID schoolId, String passwordHash) throws SQLException {
        String sql = "INSERT INTO password_history (user_id, school_id, password_hash) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, schoolId);
            stmt.setString(3, passwordHash);
            stmt.executeUpdate();
        }
    }

    /**
     * Gets the user's recent password history
     */
    public List<String> getPasswordHistory(Connection conn, UUID userId, UUID schoolId, int limit) throws SQLException {
        List<String> history = new ArrayList<>();
        String sql = "SELECT password_hash FROM password_history " +
                "WHERE user_id = ? AND school_id = ? " +
                "ORDER BY created_at DESC " +
                "LIMIT ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, schoolId);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(rs.getString("password_hash"));
                }
            }
        }
        return history;
    }

    /**
     * Cleans up old password history beyond the limit
     */
    public void cleanupOldPasswordHistory(Connection conn, UUID userId, UUID schoolId) throws SQLException {
        String sql = "DELETE FROM password_history " +
                "WHERE (user_id, school_id, created_at) IN (" +
                "    SELECT user_id, school_id, created_at " +
                "    FROM password_history " +
                "    WHERE user_id = ? AND school_id = ? " +
                "    ORDER BY created_at DESC " +
                "    OFFSET ?" +
                ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, schoolId);
            stmt.setInt(3, PASSWORD_HISTORY_LIMIT);
            stmt.executeUpdate();
        }
    }
}