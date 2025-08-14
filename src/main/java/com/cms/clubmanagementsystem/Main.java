package com.cms.clubmanagementsystem;

import com.cms.clubmanagementsystem.utils.EnvLoader; // <-- Added import
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

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
