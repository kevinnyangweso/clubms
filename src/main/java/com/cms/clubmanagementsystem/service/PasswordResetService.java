package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.TenantContext;
import com.cms.clubmanagementsystem.utils.TokenGenerator;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class PasswordResetService {
    // Configuration constants
    private static final int TOKEN_EXPIRY_HOURS = 1;
    private static final int MAX_ATTEMPTS = 5;
    private static final int TOKEN_LENGTH = 32;

    public String generateResetToken(Connection conn, String email) throws SQLException {
        // 1. Verify tenant context
        TenantContext.ensureTenantSet(conn);

        // 2. Check request rate limit
        if (isRateLimited(conn, email)) {
            throw new SecurityException("Too many reset attempts. Please try again later.");
        }

        // 3. Verify user exists in current tenant
        UUID userId = getUserId(conn, email);
        if (userId == null) {
            return null; // Silent fail for security
        }

        // 4. Generate secure token (using your TokenGenerator)
        String token = TokenGenerator.generateToken(TOKEN_LENGTH);
        Instant expiry = Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS);

        // 5. Store token and log attempt
        storeToken(conn, userId, token, expiry);
        logAttempt(conn, userId);

        return token;
    }

    public boolean validateToken(Connection conn, String email, String token) throws SQLException {
        String sql = "SELECT user_id, reset_token_expiry FROM users " +
                "WHERE email = ? AND school_id = ? AND reset_token = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.toLowerCase());
            stmt.setObject(2, UUID.fromString(TenantContext.getCurrentSchoolId()));
            stmt.setString(3, token);

            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return false;

            // Check expiration
            Timestamp expiry = rs.getTimestamp("reset_token_expiry");
            return expiry.toInstant().isAfter(Instant.now());
        }
    }

    public void invalidateToken(Connection conn, String email) throws SQLException {
        String sql = "UPDATE users SET reset_token = NULL, reset_token_expiry = NULL " +
                "WHERE email = ? AND school_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.toLowerCase());
            stmt.setObject(2, UUID.fromString(TenantContext.getCurrentSchoolId()));
            stmt.executeUpdate();
        }
    }

    private UUID getUserId(Connection conn, String email) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE email = ? AND school_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.toLowerCase());
            stmt.setObject(2, UUID.fromString(TenantContext.getCurrentSchoolId()));
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? (UUID) rs.getObject("user_id") : null;
        }
    }

    private void storeToken(Connection conn, UUID userId, String token, Instant expiry) throws SQLException {
        String sql = "UPDATE users SET reset_token = ?, reset_token_expiry = ? WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            stmt.setTimestamp(2, Timestamp.from(expiry));
            stmt.setObject(3, userId);
            stmt.executeUpdate();
        }
    }

    private void logAttempt(Connection conn, UUID userId) throws SQLException {
        String sql = "INSERT INTO password_reset_attempts (user_id, attempt_time) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        }
    }

    private boolean isRateLimited(Connection conn, String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM password_reset_attempts a " +
                "JOIN users u ON a.user_id = u.user_id " +
                "WHERE u.email = ? AND a.attempt_time > ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.toLowerCase());
            stmt.setTimestamp(2, Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) >= MAX_ATTEMPTS;
        }
    }
}