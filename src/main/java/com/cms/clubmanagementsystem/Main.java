package com.cms.clubmanagementsystem;

import com.cms.clubmanagementsystem.controller.LearnersController;
import com.cms.clubmanagementsystem.model.LearnerImportDTO;
import com.cms.clubmanagementsystem.utils.EnvLoader; // <-- Added import
import com.cms.clubmanagementsystem.utils.PasswordService;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends Application {

    private ScheduledExecutorService cleanupScheduler;

    @Override
    public void start(Stage stage) throws IOException {
        // Load environment variables
        try {
            EnvLoader.loadEnv();
            System.out.println("SMTP Host loaded: " + EnvLoader.get("SMTP_HOST")); // test print
        } catch (RuntimeException ex) {
            showErrorAlert("Configuration Error", "Could not load .env file:\n" + ex.getMessage());
            return; // Stop launching if .env is missing
        }

        // Start the periodic cleanup scheduler for expired transient data
        startCleanupScheduler();

        // Set up global exception handling for JavaFX thread
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            handleGlobalException(throwable);
        });

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root, 320, 240);
        stage.setTitle("Club Management System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // Set default uncaught exception handler for non-JavaFX threads
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            handleGlobalException(throwable);
        });

        launch();
    }

    /**
     * Starts the scheduled executor service for periodic cleanup of expired transient data
     */
    private void startCleanupScheduler() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("SessionManager-Cleanup-Thread");
            thread.setDaemon(true); // Make it a daemon thread so it doesn't prevent JVM shutdown
            return thread;
        });

        // Schedule cleanup to run every 5 minutes
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                SessionManager.cleanupExpiredData();
                System.out.println("Periodic cleanup of expired transient data completed.");
            } catch (Exception e) {
                System.err.println("Error during periodic cleanup: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 5, TimeUnit.MINUTES); // Initial delay 1 minute, then every 5 minutes

        System.out.println("Started periodic cleanup scheduler for expired transient data.");
    }

    /**
     * Gracefully shuts down the cleanup scheduler
     */
    private void shutdownCleanupScheduler() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            try {
                System.out.println("Shutting down cleanup scheduler...");
                cleanupScheduler.shutdown();

                // Wait a bit for ongoing tasks to complete
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("Forcing shutdown of cleanup scheduler...");
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("Cleanup scheduler shutdown interrupted: " + e.getMessage());
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void stop() throws Exception {
        // Shutdown cleanup scheduler when application stops
        shutdownCleanupScheduler();

        // Clean up any remaining session data
        SessionManager.closeSession();
        SessionManager.clearTransientData();

        // Clean up resources
        PasswordService.shutdown();
        DatabaseConnector.shutdown();

        System.out.println("Application stopped. Cleaned up session data.");
        super.stop();
    }

    private static void handleGlobalException(Throwable throwable) {
        // Unwrap the exception if it's wrapped
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        if (rootCause instanceof IllegalArgumentException) {
            showErrorAlert("Validation Error", rootCause.getMessage());
        } else {
            // Log the full error for debugging
            System.err.println("Unexpected error:");
            throwable.printStackTrace();

            // Show generic error to user
            showErrorAlert("An unexpected error occurred. Please try again.",
                    rootCause.getMessage() != null ? rootCause.getMessage() : "No details available");
        }
    }

    public static void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void loadScene(String fxmlPath, String title, ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlPath));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.show();
    }

}
