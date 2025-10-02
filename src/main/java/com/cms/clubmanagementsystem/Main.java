package com.cms.clubmanagementsystem;

import com.cms.clubmanagementsystem.controller.LoginController;
import com.cms.clubmanagementsystem.controller.WebhookController;
import com.cms.clubmanagementsystem.model.DataModel;
import com.cms.clubmanagementsystem.model.Learner;
import com.cms.clubmanagementsystem.service.ExcelSchoolServer;
import com.cms.clubmanagementsystem.service.StudentService;
import com.cms.clubmanagementsystem.service.WebhookService;
import com.cms.clubmanagementsystem.service.LearnerService;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import com.cms.clubmanagementsystem.utils.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int CLEANUP_INITIAL_DELAY_MINUTES = 1;
    private static final int CLEANUP_PERIOD_MINUTES = 5;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private ScheduledExecutorService cleanupScheduler;
    private ExecutorService webhookExecutor;
    private WebhookService webhookService;
    private ExcelSchoolServer excelSchoolServer;
    private int webhookPort;
    private LearnerService learnerService;
    private AtomicReference<UUID> currentSchoolId = new AtomicReference<>();

    @Override
    public void start(Stage stage) {
        try {
            initializeApplication();
            launchLoginScreen(stage);
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            showErrorAlert("Startup Error", "Could not initialize the application: " + e.getMessage());
            Platform.exit();
        }
    }

    private void initializeApplication() {
        // Load environment variables
        loadEnvironmentVariables();

        // Set up exception handling
        setupExceptionHandling();
    }

    // Method to start services after login
    public void onSchoolLogin(UUID schoolId) {
        this.currentSchoolId.set(schoolId);
        this.learnerService = new LearnerService(schoolId);

        // Test the integration first
        testExcelIntegration(schoolId);

        // Start services that require school ID
        startCleanupScheduler();
        startWebhookService();
        startExcelSchoolServer();

        logger.info("Services started for school ID: {}", schoolId);
    }

    private void testExcelIntegration(UUID schoolId) {
        logger.info("Testing Excel integration...");
        try {
            // Test database connection
            var learners = learnerService.getAllLearnersForSchool();
            logger.info("Found {} learners in database", learners.size());

            // Test file access
            String excelPath = EnvLoader.get("EXCEL_FILE_PATH");
            boolean fileExists = Files.exists(Paths.get(excelPath));
            logger.info("Excel file exists: {}", fileExists);

            if (fileExists) {
                logger.info("Excel file size: {} bytes", Files.size(Paths.get(excelPath)));
            }

        } catch (Exception e) {
            logger.error("Integration test failed: {}", e.getMessage(), e);
        }
    }

    private void loadEnvironmentVariables() {
        try {
            EnvLoader.loadEnv();
            logger.info("Environment variables loaded successfully");
            logger.debug("SMTP Host: {}", EnvLoader.get("SMTP_HOST"));
        } catch (RuntimeException ex) {
            logger.error("Could not load .env file", ex);
            throw new RuntimeException("Configuration error: " + ex.getMessage(), ex);
        }
    }

    private void launchLoginScreen(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent root = fxmlLoader.load();

        // Get the controller and set the main application instance
        LoginController loginController = fxmlLoader.getController();
        loginController.setMainApp(this); // ✅ This is the key line!

        // Set the main app as user data for the stage (optional backup)
        stage.setUserData(this);

        Scene scene = new Scene(root, 320, 240);
        stage.setTitle("Club Management System");
        stage.setScene(scene);
        stage.setMinWidth(320);
        stage.setMinHeight(240);
        stage.show();

        logger.info("Application UI launched successfully");
    }

    public static void main(String[] args) {
        setupGlobalExceptionHandling();
        launch(args);
    }

    private static void setupGlobalExceptionHandling() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in thread: {}", thread.getName(), throwable);
            handleGlobalException(throwable);
        });
    }

    private void setupExceptionHandling() {
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in JavaFX thread", throwable);
            handleGlobalException(throwable);
        });
    }

    /**
     * Starts the scheduled executor service for periodic cleanup of expired transient data
     */
    private void startCleanupScheduler() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SessionManager-Cleanup-Thread");
            thread.setDaemon(true);
            return thread;
        });

        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                SessionManager.cleanupExpiredData();
                logger.debug("Periodic cleanup of expired transient data completed");
            } catch (Exception e) {
                logger.error("Error during periodic cleanup", e);
            }
        }, CLEANUP_INITIAL_DELAY_MINUTES, CLEANUP_PERIOD_MINUTES, TimeUnit.MINUTES);

        logger.info("Started periodic cleanup scheduler for expired transient data");
    }

    /**
     * Starts the webhook listener service in a background daemon thread
     */
    private void startWebhookService() {
        try {
            // Get port from environment variable or use default
            String portStr = EnvLoader.get("WEBHOOK_LISTENER_PORT", "8080");
            webhookPort = Integer.parseInt(portStr);

            // Get school webhook registration URL
            String schoolWebhookUrl = EnvLoader.get("SCHOOL_WEBHOOK_URL");
            String callbackUrl = EnvLoader.get("WEBHOOK_CALLBACK_URL",
                    "http://localhost:" + webhookPort + "/webhook");

            webhookService = new WebhookService(webhookPort);
            webhookExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Webhook-Service-Thread");
                thread.setDaemon(true);
                return thread;
            });

            webhookExecutor.submit(() -> {
                try {
                    webhookService.start(this::handleIncomingWebhook);
                    registerWebhookWithSchool(schoolWebhookUrl, callbackUrl);
                } catch (Exception e) {
                    logger.error("Webhook service failed to start", e);
                }
            });

            logger.info("Webhook listener service initializing on port {}", webhookPort);
        } catch (NumberFormatException e) {
            logger.error("Invalid webhook port configuration", e);
        } catch (Exception e) {
            logger.error("Failed to start webhook service", e);
        }
    }

    /**
     * Starts the Excel School Server to monitor Excel file changes
     */
    private void startExcelSchoolServer() {
        UUID schoolId = currentSchoolId.get();
        if (schoolId == null) {
            logger.warn("No school ID available, skipping Excel School Server startup");
            return;
        }

        // Check if learnerService is properly initialized
        if (learnerService == null) {
            logger.error("LearnerService is not initialized, cannot start Excel School Server");
            return;
        }

        String excelFilePath = EnvLoader.get("EXCEL_FILE_PATH");
        String webhookUrl = EnvLoader.get("WEBHOOK_CALLBACK_URL", "http://localhost:" + webhookPort + "/webhook");
        String apiKey = EnvLoader.get("WEBHOOK_API_KEY");

        if (excelFilePath != null && !excelFilePath.trim().isEmpty()) {
            try {
                // Pass learnerService to ExcelSchoolServer
                excelSchoolServer = new ExcelSchoolServer(
                        excelFilePath,
                        webhookUrl,
                        apiKey,
                        learnerService  // Pass learner service to handle updates
                );
                excelSchoolServer.start();
                logger.info("Excel School Server started for file: {}", excelFilePath);
            } catch (Exception e) {
                logger.error("Failed to start Excel School Server", e);
            }
        } else {
            logger.info("No Excel file path configured, skipping Excel School Server startup");
        }
    }

    private void registerWebhookWithSchool(String schoolWebhookUrl, String callbackUrl) {
        if (schoolWebhookUrl != null && !schoolWebhookUrl.trim().isEmpty()) {
            try {
                boolean registered = webhookService.registerWithSchoolServer(schoolWebhookUrl, callbackUrl);
                if (registered) {
                    logger.info("Webhook registered successfully with school server");
                } else {
                    logger.error("Failed to register webhook with school server");
                }
            } catch (Exception e) {
                logger.error("Error during webhook registration with school server", e);
            }
        } else {
            logger.warn("No school webhook URL configured, skipping registration");
        }
    }

    /**
     * Handles an incoming webhook notification
     */
    private void handleIncomingWebhook(String eventType, String admissionNumber) {
        logger.info("Webhook received - Event: {}, Admission: {}", eventType, admissionNumber);

        showWebhookNotification(eventType, admissionNumber);
        processWebhookEvent(eventType, admissionNumber);
    }

    private void showWebhookNotification(String eventType, String admissionNumber) {
        Platform.runLater(() -> {
            String title = "Webhook Received";
            String message = getWebhookMessage(eventType, admissionNumber);
            NotificationUtil.showNotification(title, message);
        });
    }

    private String getWebhookMessage(String eventType, String admissionNumber) {
        switch (eventType) {
            case "new_student":
                return "📥 New student added: " + admissionNumber;
            case "student_updated":
                return "🔄 Student updated: " + admissionNumber;
            case "student_removed":
                return "🗑️ Student removed: " + admissionNumber;
            default:
                return "📨 Webhook received: " + eventType;
        }
    }

    private void processWebhookEvent(String eventType, String admissionNumber) {
        if ("student_removed".equals(eventType)) {
            removeStudentFromData(admissionNumber);
        } else if ("student_updated".equals(eventType) || "new_student".equals(eventType)) {
            refreshStudentData();
        }
    }

    private void refreshStudentData() {
        logger.info("Refreshing student data due to webhook notification");

        try {
            UUID schoolId = SessionManager.getCurrentSchoolId();
            if (schoolId == null) {
                logger.warn("No school ID in session, cannot fetch students");
                return;
            }

            StudentService studentService = new StudentService();
            List<Learner> updatedLearners = studentService.fetchAllStudentsFromDatabase(schoolId);
            DataModel.getInstance().updateLearners(updatedLearners);

            logger.info("Refreshed student data with {} learners", updatedLearners.size());
        } catch (Exception e) {
            logger.error("Error refreshing student data", e);
        }
    }

    private void removeStudentFromData(String studentId) {
        logger.info("Student removal requested for ID: {}", studentId);
        // Since we can't reliably map the ID, refresh all data
        refreshStudentData();
    }

    /**
     * Gracefully shuts down the cleanup scheduler
     */
    private void shutdownCleanupScheduler() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            try {
                logger.info("Shutting down cleanup scheduler...");
                cleanupScheduler.shutdown();

                if (!cleanupScheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.warn("Forcing shutdown of cleanup scheduler...");
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Cleanup scheduler shutdown interrupted", e);
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Gracefully shuts down the webhook service
     */
    private void shutdownWebhookService() {
        // Unregister webhook from school server
        String schoolWebhookUrl = EnvLoader.get("SCHOOL_WEBHOOK_URL");
        String callbackUrl = EnvLoader.get("WEBHOOK_CALLBACK_URL",
                "http://localhost:" + webhookPort + "/webhook");

        if (schoolWebhookUrl != null && webhookService != null) {
            try {
                webhookService.unregisterFromSchoolServer(schoolWebhookUrl, callbackUrl);
            } catch (Exception e) {
                logger.warn("Failed to unregister webhook from school server", e);
            }
        }

        // Shutdown webhook service
        if (webhookService != null) {
            try {
                webhookService.stop();
            } catch (Exception e) {
                logger.warn("Error stopping webhook service", e);
            }
        }

        // Shutdown executor
        if (webhookExecutor != null && !webhookExecutor.isShutdown()) {
            try {
                webhookExecutor.shutdownNow();
            } catch (Exception e) {
                logger.warn("Error shutting down webhook executor", e);
            }
        }
    }

    /**
     * Gracefully shuts down the Excel School Server
     */
    private void shutdownExcelSchoolServer() {
        if (excelSchoolServer != null) {
            try {
                excelSchoolServer.stop();
                logger.info("Excel School Server stopped");
            } catch (Exception e) {
                logger.warn("Error stopping Excel School Server", e);
            }
        }
    }

    @Override
    public void stop() {
        logger.info("Application shutdown initiated");

        try {
            shutdownWebhookService();
            shutdownCleanupScheduler();
            shutdownExcelSchoolServer();

            // Clean up session data
            SessionManager.closeSession();
            SessionManager.clearTransientData();

            // Clean up resources
            PasswordService.shutdown();
            DatabaseConnector.shutdown();

            logger.info("Application stopped successfully");
        } catch (Exception e) {
            logger.error("Error during application shutdown", e);
        } finally {
            Platform.exit();
        }
    }

    // Add this method to your Main class
    private void setupWebhookController() {
        // This would be called when navigating to webhook settings
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/webhook-settings.fxml"));
            Parent root = loader.load();
            WebhookController controller = loader.getController();

            // Set up webhook service to notify the controller
            webhookService = new WebhookService(webhookPort);
            webhookService.start(controller::onWebhookReceived);

            // Create a new stage for webhook settings
            Stage webhookStage = new Stage();
            webhookStage.setTitle("Webhook Settings");
            webhookStage.setScene(new Scene(root, 600, 500));
            webhookStage.show();

        } catch (IOException e) {
            logger.error("Failed to load webhook settings UI", e);
            showErrorAlert("UI Error", "Could not load webhook settings: " + e.getMessage());
        }
    }

    // Add a method to open webhook settings from your main UI
    @FXML
    private void handleOpenWebhookSettings() {
        setupWebhookController();
    }

    private static void handleGlobalException(Throwable throwable) {
        Throwable rootCause = getRootCause(throwable);
        logger.error("Global exception handler caught error", rootCause);

        if (rootCause instanceof IllegalArgumentException) {
            showErrorAlert("Validation Error", rootCause.getMessage());
        } else {
            showErrorAlert("Unexpected Error",
                    "An unexpected error occurred. Please try again.\n\n" +
                            "Details: " + (rootCause.getMessage() != null ?
                            rootCause.getMessage() : "No details available"));
        }
    }

    private static Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    public static void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static void loadScene(String fxmlPath, String title, ActionEvent event) throws IOException {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to load scene: {}", fxmlPath, e);
            throw e;
        }
    }


    // Add this method to Main.java to expose the manual trigger
    public void triggerExcelReload() {
        if (excelSchoolServer != null) {
            excelSchoolServer.forceReload();
        }
    }

    // Add this to your Main.java for debugging
    @FXML
    private void handleDebugExcelSync() {
        try {
            logger.info("Manual Excel sync triggered");
            if (excelSchoolServer != null) {
                excelSchoolServer.forceReload();
                showInfoAlert("Sync Started", "Excel synchronization has been triggered manually");
            } else {
                showErrorAlert("Sync Error", "Excel School Server is not initialized");
            }
        } catch (Exception e) {
            logger.error("Manual sync failed", e);
            showErrorAlert("Sync Failed", "Manual synchronization failed: " + e.getMessage());
        }
    }

    // Info alert
    public static void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Warning alert
    public static void showWarningAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Success alert
    public static void showSuccessAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText("Success");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Confirmation alert with callback
    public static void showConfirmationAlert(String title, String message, Runnable onConfirm) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            alert.showAndWait().ifPresent(response -> {
                if (response == javafx.scene.control.ButtonType.OK) {
                    onConfirm.run();
                }
            });
        });
    }
}