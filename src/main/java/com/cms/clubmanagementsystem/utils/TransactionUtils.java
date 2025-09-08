package com.cms.clubmanagementsystem.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransactionUtils {
    private static final Logger LOGGER = Logger.getLogger(TransactionUtils.class.getName());

    public static <T> T executeInTransaction(Connection conn, Function<Connection, T> operation)
            throws SQLException {

        boolean originalAutoCommit = conn.getAutoCommit();

        try {
            conn.setAutoCommit(false);
            T result = operation.apply(conn);
            conn.commit();
            return result;

        } catch (SQLException e) {
            try {
                conn.rollback();
                LOGGER.log(Level.WARNING, "Transaction rolled back due to error: {0}", e.getMessage());
            } catch (SQLException rollbackEx) {
                LOGGER.log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
                // Add the rollback exception as a suppressed exception
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to restore auto-commit state", e);
            }
        }
    }

    // Overload for operations that don't return a value
    public static void executeInTransaction(Connection conn, TransactionOperation operation)
            throws SQLException {
        executeInTransaction(conn, connection -> {
            try {
                operation.execute(connection);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @FunctionalInterface
    public interface TransactionOperation {
        void execute(Connection connection) throws SQLException;
    }
}