package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.TenantContext;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class PasswordResetService {

    public String generateResetToken(Connection conn, String email) throws SQLException {
        // First verify the tenant context is set
        TenantContext.ensureTenantSet(conn);

        // Debug output
        System.out.println("Generating token for email: " + email +
                " in school: " + TenantContext.getCurrentSchoolId());

        // Check if user exists in current tenant
        String checkSql = "SELECT user_id FROM users WHERE email = ? AND school_id = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, email.toLowerCase());
            checkStmt.setObject(2, UUID.fromString(TenantContext.getCurrentSchoolId()));
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                System.out.println("DEBUG: No user found with email " + email +
                        " in school " + TenantContext.getCurrentSchoolId());
                return null;
            }
        }

        // Generate token
        String token = UUID.randomUUID().toString();
        Timestamp expiry = Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS));

        String updateSql = "UPDATE users SET reset_token = ?, reset_token_expiry = ? " +
                "WHERE email = ? AND school_id = ? RETURNING reset_token";

        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, token);
            stmt.setTimestamp(2, expiry);
            stmt.setString(3, email.toLowerCase());
            stmt.setObject(4, UUID.fromString(TenantContext.getCurrentSchoolId()));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("reset_token");
            }
            System.out.println("DEBUG: Update succeeded but no rows returned");
            return null;
        }
    }

}
