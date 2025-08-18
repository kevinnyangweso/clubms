package com.cms.clubmanagementsystem.utils;

import java.sql.*;
import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<String> currentSchoolId = new ThreadLocal<>();

    // Add this setter method
    public static void setCurrentSchoolId(String schoolId) {
        currentSchoolId.set(schoolId);
    }

    public static void setTenant(Connection conn, String schoolId) throws SQLException {
        try {
            // Validate UUID format
            UUID.fromString(schoolId);

            // Set the session variable directly (no parameter binding)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET app.current_school_id = '" + schoolId + "'");
            }

            // Verify it was set correctly
            try (Statement verifyStmt = conn.createStatement();
                 ResultSet rs = verifyStmt.executeQuery("SHOW app.current_school_id")) {
                if (!rs.next() || !schoolId.equals(rs.getString(1))) {
                    throw new SQLException("Failed to verify tenant context");
                }
            }

            currentSchoolId.set(schoolId);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid school ID format", e);
        }
    }

    public static String getCurrentSchoolId() {
        return currentSchoolId.get();
    }

    public static void clear() {
        currentSchoolId.remove();
    }

    public static void ensureTenantSet(Connection conn) throws SQLException {
        String expectedTenant = getCurrentSchoolId();
        if (expectedTenant == null) {
            throw new SQLException("No tenant context is currently set");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_setting('app.current_school_id')")) {

            if (!rs.next()) {
                throw new SQLException("Failed to verify tenant context");
            }

            String actualTenant = rs.getString(1);
            if (!expectedTenant.equals(actualTenant)) {
                throw new SQLException(String.format(
                        "Tenant context mismatch. Expected: %s, Actual: %s",
                        expectedTenant, actualTenant));
            }
        }
    }
}