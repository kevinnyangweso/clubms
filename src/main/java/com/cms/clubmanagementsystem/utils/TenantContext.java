package com.cms.clubmanagementsystem.utils;

import java.sql.*;
import java.util.UUID;

public class TenantContext {
    public static void setTenant(Connection conn, String schoolId) throws SQLException {
        try {
            // First validate it's a proper UUID
            UUID.fromString(schoolId);

            // Set the context
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT set_config('app.current_school_id', ?, true)")) {
                stmt.setString(1, schoolId);
                stmt.execute();
            }

            // Verify it was set
            try (Statement verify = conn.createStatement();
                 ResultSet rs = verify.executeQuery("SHOW app.current_school_id")) {
                if (rs.next()) {
                    System.out.println("Current tenant verified as: " + rs.getString(1));
                }
            }
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid school ID format: " + schoolId, e);
        }
    }
}
