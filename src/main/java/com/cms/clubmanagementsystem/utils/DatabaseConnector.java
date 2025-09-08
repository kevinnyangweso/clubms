package com.cms.clubmanagementsystem.utils;

import com.cms.clubmanagementsystem.utils.SessionManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.Objects;

public class DatabaseConnector {
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;
    private static HikariDataSource dataSource;
    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    static {
        try {
            EnvLoader.loadEnv();
            DB_URL = Objects.requireNonNull(EnvLoader.get("DB_URL"), "DB_URL is not set");
            DB_USER = Objects.requireNonNull(EnvLoader.get("DB_USER"), "DB_USER is not set");
            DB_PASSWORD = Objects.requireNonNull(EnvLoader.get("DB_PASSWORD"), "DB_PASSWORD is not set");
            System.out.println("Database configuration loaded, connection pool will be initialized on first use");
        } catch (Exception e) {
            System.err.println("❌ Failed to load database configuration");
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void initializeConnectionPool() {
        if (initialized) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }

            try {
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
                config.setLeakDetectionThreshold(60000); // 60 seconds

                // Additional recommended settings
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("cachePrepStmts", "true");

                // Connection validation
                config.setConnectionTestQuery("SELECT 1");
                config.setValidationTimeout(5000);

                dataSource = new HikariDataSource(config);
                initialized = true;
                System.out.println("✅ Database connection pool initialized successfully");

            } catch (Exception e) {
                System.err.println("❌ Failed to initialize database connection pool:");
                e.printStackTrace();
                dataSource = null;
                initialized = false;
                throw new RuntimeException("Failed to initialize database connection pool", e);
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            initializeConnectionPool();
        }

        if (dataSource == null) {
            throw new SQLException("Database connection pool not initialized");
        }

        Connection conn = dataSource.getConnection();
        applyTenantContext(conn);
        return conn;
    }

    private static void applyTenantContext(Connection conn) throws SQLException {
        UUID currentSchoolId = SessionManager.getCurrentSchoolId();
        UUID currentUserId = SessionManager.getCurrentUserId();

        if (currentSchoolId != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT set_config('app.current_school_id', ?, false)")) {
                stmt.setString(1, currentSchoolId.toString());
                stmt.execute();
            }
        }

        if (currentUserId != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT set_config('app.current_user_id', ?, false)")) {
                stmt.setString(1, currentUserId.toString());
                stmt.execute();
            }
        }
    }

    // Health check method
    public static boolean isHealthy() {
        if (!initialized || dataSource == null) {
            return false;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.execute("SELECT 1");
        } catch (SQLException e) {
            return false;
        }
    }

    // Get pool statistics for monitoring
    public static String getPoolStats() {
        if (dataSource == null) {
            return "Pool not initialized";
        }

        return String.format(
                "Active: %d, Idle: %d, Total: %d, Waiting: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            initialized = false;
            System.out.println("Database connection pool shutdown successfully");
        }
    }

    // Add a static block for graceful shutdown
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (initialized) {
                shutdown();
            }
        }));
    }
}