package com.cms.clubmanagementsystem.service;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PasswordResetService {
    private static final SecureRandom random = new SecureRandom();
    private static final int TOKEN_LENGTH = 6; // 6-digit number
    private static final int TOKEN_EXPIRY_MINUTES = 30; // Token expires in 30 minutes

    public String generateResetToken(Connection conn, String email, UUID schoolId) throws SQLException {
        // Generate 6-digit numeric token
        String token = generateNumericToken();

        // Calculate expiry time
        java.sql.Timestamp expiryTime = new java.sql.Timestamp(
                System.currentTimeMillis() + (TOKEN_EXPIRY_MINUTES * 60 * 1000)
        );

        // Update the user record with the token and expiry time
        String sql = "UPDATE users SET reset_token = ?, reset_token_expiry = ? WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            stmt.setTimestamp(2, expiryTime);
            stmt.setString(3, email);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0 ? token : null;
        }
    }

    /**
     * Generates a 6-digit numeric token (000000 to 999999)
     */
    private String generateNumericToken() {
        int number = random.nextInt(1_000_000); // 0 to 999,999
        return String.format("%06d", number); // Pad with leading zeros to make 6 digits
    }

    /**
     * Validates if a reset token is valid and not expired
     */
    public boolean validateResetToken(Connection conn, String email, String token, UUID userSchoolId) throws SQLException {
        String sql = "SELECT reset_token_expiry FROM users WHERE email = ? AND reset_token = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, token);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp expiryTime = rs.getTimestamp("reset_token_expiry");
                    // Check if token is not expired
                    return expiryTime != null && expiryTime.after(new java.sql.Timestamp(System.currentTimeMillis()));
                }
            }
        }
        return false;
    }

    /**
     * Validates password against history and updates if valid
     */
    public boolean resetPasswordWithHistory(Connection conn, String email, UUID schoolId,
                                            String newPassword, String resetToken) throws SQLException {
        // First get user ID
        UUID userId = getUserIdByEmail(conn, email);
        if (userId == null) {
            return false;
        }

        // Hash the new password
        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());

        // Check password history
        PasswordHistoryService historyService = new PasswordHistoryService();
        if (historyService.isPasswordInHistory(conn, userId, schoolId, newPassword)) {
            throw new SecurityException("Password has been used recently. Please choose a different password.");
        }

        // Update password
        String sql = "UPDATE users SET password_hash = ?, is_active = TRUE, " +
                "reset_token = NULL, reset_token_expiry = NULL " +
                "WHERE email = ? AND reset_token = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hashedPassword);
            stmt.setString(2, email);
            stmt.setString(3, resetToken);

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                // Add to password history
                historyService.addToPasswordHistory(conn, userId, schoolId, hashedPassword);
                // Clean up old history
                historyService.cleanupOldPasswordHistory(conn, userId, schoolId);
                return true;
            }
        }
        return false;
    }


    private UUID getUserIdByEmail(Connection conn, String email) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("user_id");
                }
            }
        }
        return null;
    }
}