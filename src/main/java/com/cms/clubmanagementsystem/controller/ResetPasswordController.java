package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.TenantContext;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class ResetPasswordController {

    @FXML private TextField tokenField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    private String resetToken;
    private String userEmail;
    private UUID userSchoolId;

    public void setResetToken(String token, String email, UUID schoolId) {
        this.resetToken = token;
        this.userEmail = email;
        this.userSchoolId = schoolId;
        tokenField.setText(token);
    }

    @FXML
    private void handleResetPassword() {
        String enteredToken = tokenField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Basic validations
        if (!enteredToken.equals(resetToken)) {
            showAlert(Alert.AlertType.ERROR, "Invalid reset token.");
            return;
        }

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Password fields cannot be empty.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showAlert(Alert.AlertType.ERROR, "Passwords do not match.");
            return;
        }

        // Hash the new password
        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());

        // Update password in database respecting RLS
        try (Connection conn = DatabaseConnector.getConnection()) {
            // Set tenant context to enforce RLS for this school
            TenantContext.setTenant(conn, userSchoolId.toString());

            String sql = "UPDATE users SET password_hash = ?, is_active = TRUE WHERE email = ? AND reset_token = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hashedPassword);
                stmt.setString(2, userEmail);
                stmt.setString(3, resetToken);

                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated > 0) {
                    showAlert(Alert.AlertType.INFORMATION, "Password reset successfully!");
                    clearFields();
                    // Optionally return to login screen after successful reset
                    handleBackToLogin();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Failed to reset password. Invalid token or user not found in your school.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Stage stage = (Stage) tokenField.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            stage.setScene(new Scene(root));
            stage.setTitle("Login");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Failed to load login screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void clearFields() {
        tokenField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
    }
}