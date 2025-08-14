package com.cms.clubmanagementsystem.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Handles PostgreSQL database connections for the application.
 * Uses environment variables loaded from .env file via EnvLoader.
 */
public class DatabaseConnector {
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        // Load environment variables
        EnvLoader.loadEnv();

        // Get database connection parameters
        DB_URL = Objects.requireNonNull(
                EnvLoader.get("DB_URL"),
                "DB_URL is not set in environment variables"
        );
        DB_USER = Objects.requireNonNull(
                EnvLoader.get("DB_USER"),
                "DB_USER is not set in environment variables"
        );
        DB_PASSWORD = Objects.requireNonNull(
                EnvLoader.get("DB_PASSWORD"),
                "DB_PASSWORD is not set in environment variables"
        );
    }

    /**
     * Returns a new connection to the database.
     * @return Connection object
     * @throws SQLException if database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}