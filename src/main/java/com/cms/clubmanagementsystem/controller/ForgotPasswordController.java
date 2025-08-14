package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.EmailService;
import com.cms.clubmanagementsystem.utils.TokenGenerator;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

public class ForgotPasswordController {

    @FXML private TextField emailField;

    @FXML
    private void handleSendResetLink() {
        String email = emailField.getText().trim();
        if (email.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Please enter your email address.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {

            // STEP 1 — Tell PostgreSQL to bypass RLS for this session
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET app.bypass_rls = true"); // You must define this in your DB policy logic
            }

            // STEP 2 — generate token
            String token = TokenGenerator.generateToken();
            LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);

            // STEP 3 — store token in DB (no school_id filter here)
            String sql = "UPDATE users SET reset_token = ?, reset_token_expiry = ? WHERE email = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, token);
                stmt.setObject(2, expiry);
                stmt.setString(3, email);
                int rows = stmt.executeUpdate();

                if (rows == 0) {
                    showAlert(Alert.AlertType.ERROR, "No account found with that email.");
                    return;
                }
            }

            // STEP 4 — send email
            String resetLink = "http://localhost:8080/reset-password?token=" + token; // Replace with actual link
            EmailService emailService = new EmailService();
            emailService.sendPasswordResetEmail(email, resetLink);

            showAlert(Alert.AlertType.INFORMATION, "Password reset link sent to your email.");

            // STEP 5 — load reset password screen
            loadResetPasswordScreen();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error: " + e.getMessage());
        }
    }

    /**
     * Loads the Reset Password screen (FXML) after sending the link.
     */
    private void loadResetPasswordScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cms/clubmanagementsystem/view/ResetPassword.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Reset Password");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Failed to load Reset Password screen: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
