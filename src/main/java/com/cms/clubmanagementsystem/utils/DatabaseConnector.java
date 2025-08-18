package com.cms.clubmanagementsystem.utils;

import java.sql.*;
import java.util.Objects;
import java.util.UUID;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConnector {
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;
    private static HikariDataSource dataSource;

    static {
        EnvLoader.loadEnv();
        DB_URL = Objects.requireNonNull(EnvLoader.get("DB_URL"), "DB_URL is not set");
        DB_USER = Objects.requireNonNull(EnvLoader.get("DB_USER"), "DB_USER is not set");
        DB_PASSWORD = Objects.requireNonNull(EnvLoader.get("DB_PASSWORD"), "DB_PASSWORD is not set");
        initializeConnectionPool();
    }

    private static void initializeConnectionPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

        config.setPoolName("CMS-Pool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = dataSource.getConnection();
        applyTenantContext(conn);
        return conn;
    }

    private static void applyTenantContext(Connection conn) throws SQLException {
        String currentTenant = TenantContext.getCurrentSchoolId();
        if (currentTenant != null) {
            verifySchoolExists(conn, currentTenant);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET app.current_school_id = '" + currentTenant + "'");
            }

            verifyTenantContext(conn, currentTenant);
        }
    }

    private static void verifySchoolExists(Connection conn, String schoolId) throws SQLException {
        String sql = "SELECT 1 FROM schools WHERE school_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(schoolId));
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("School not found: " + schoolId);
            }
        }
    }

    private static void verifyTenantContext(Connection conn, String expectedTenant) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW app.current_school_id")) {
            if (!rs.next() || !expectedTenant.equals(rs.getString(1))) {
                throw new SQLException("Tenant context verification failed");
            }
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}