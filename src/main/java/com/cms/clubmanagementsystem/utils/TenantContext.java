package com.cms.clubmanagementsystem.utils;

import java.sql.*;
import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<String> currentSchoolId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    public static void setCurrentUser(String schoolId, String userId) {
        currentSchoolId.set(schoolId);
        currentUserId.set(userId);
    }

    public static String getCurrentUserId() {
        return currentUserId.get();
    }

    public static void setCurrentSchoolId(String schoolId) {
        currentSchoolId.set(schoolId);
    }

    // Updated method to accept both schoolId and userId
    public static void setTenant(Connection conn, String schoolId, String userId) throws SQLException {
        setCurrentUser(schoolId, userId);
        try {
            // Validate UUID formats
            UUID.fromString(schoolId);
            UUID.fromString(userId);

            // Set both session variables
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET app.current_school_id = '" + schoolId + "'");
                stmt.execute("SET app.current_user_id = '" + userId + "'");
            }

            // Verify both were set correctly
            try (Statement verifyStmt = conn.createStatement();
                 ResultSet rs = verifyStmt.executeQuery("SELECT current_setting('app.current_school_id'), current_setting('app.current_user_id')")) {
                if (!rs.next() || !schoolId.equals(rs.getString(1)) || !userId.equals(rs.getString(2))) {
                    throw new SQLException("Failed to verify tenant context");
                }
            }

        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid UUID format", e);
        }
    }

    public static String getCurrentSchoolId() {
        return currentSchoolId.get();
    }

    public static void clear() {
        currentSchoolId.remove();
        currentUserId.remove(); // Clear both
    }

    public static void ensureTenantSet(Connection conn) throws SQLException {
        String expectedSchoolId = getCurrentSchoolId();
        String expectedUserId = getCurrentUserId();

        if (expectedSchoolId == null || expectedUserId == null) {
            throw new SQLException("No tenant context is currently set");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_setting('app.current_school_id'), current_setting('app.current_user_id')")) {

            if (!rs.next()) {
                throw new SQLException("Failed to verify tenant context");
            }

            String actualSchoolId = rs.getString(1);
            String actualUserId = rs.getString(2);

            if (!expectedSchoolId.equals(actualSchoolId) || !expectedUserId.equals(actualUserId)) {
                throw new SQLException(String.format(
                        "Tenant context mismatch. Expected school: %s, user: %s. Actual school: %s, user: %s",
                        expectedSchoolId, expectedUserId, actualSchoolId, actualUserId));
            }
        }
    }
}