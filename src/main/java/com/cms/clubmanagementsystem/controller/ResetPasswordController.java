package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.EmailService;
import com.cms.clubmanagementsystem.service.PasswordResetService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.TenantContext;
import com.cms.clubmanagementsystem.utils.TokenGenerator;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class ResetPasswordController {

    // Configuration constants
    private static final int PASSWORD_HISTORY_LIMIT = 5;
    private static final int PASSWORD_EXPIRY_DAYS = 90;

    @FXML private TextField emailField;
    @FXML private TextField tokenField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    private final PasswordResetService passwordResetService = new PasswordResetService();
    private final EmailService emailService = new EmailService();

    @FXML
    private void initialize() {
        // Initialize any necessary components
    }

    @FXML
    private void handleResetPassword() {
        String enteredEmail = emailField.getText().trim();
        String enteredToken = tokenField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Basic validations
        if (enteredEmail.isEmpty() || enteredToken.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Email and token fields cannot be empty.");
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

        if (!TokenGenerator.matchesPasswordRequirements(newPassword)) {
            showAlert(Alert.AlertType.ERROR,
                    "Password must contain:\n" +
                            "- 8-64 characters\n" +
                            "- At least 1 uppercase letter\n" +
                            "- At least 1 lowercase letter\n" +
                            "- At least 1 number\n" +
                            "- At least 1 special character (!@#$%^&* etc.)");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID schoolId = getSchoolIdForEmail(conn, enteredEmail);
            if (schoolId == null) {
                showAlert(Alert.AlertType.ERROR, "No account found with that email.");
                return;
            }

            TenantContext.setTenant(conn, schoolId.toString());

            // Get user ID first
            UUID userId = getUserId(conn, enteredEmail);
            if (userId == null) {
                showAlert(Alert.AlertType.ERROR, "User not found.");
                return;
            }

            // Check against current and historical passwords
            if (isPasswordInHistory(conn, userId, newPassword, PASSWORD_HISTORY_LIMIT)) {
                showAlert(Alert.AlertType.ERROR,
                        "Cannot reuse any of your last " + PASSWORD_HISTORY_LIMIT + " passwords.");
                return;
            }

            if (!passwordResetService.validateToken(conn, enteredEmail, enteredToken)) {
                showAlert(Alert.AlertType.ERROR, "Invalid or expired token. Please request a new one.");
                return;
            }

            String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            Instant passwordExpiry = Instant.now().plus(PASSWORD_EXPIRY_DAYS, ChronoUnit.DAYS);

            // Update password and set expiry
            String sql = "UPDATE users SET password_hash = ?, password_expiry = ?, " +
                    "reset_token = NULL, reset_token_expiry = NULL " +
                    "WHERE email = ? AND reset_token = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hashedPassword);
                stmt.setTimestamp(2, java.sql.Timestamp.from(passwordExpiry));
                stmt.setString(3, enteredEmail);
                stmt.setString(4, enteredToken);

                if (stmt.executeUpdate() > 0) {
                    // Store in password history
                    storePasswordHistory(conn, userId, hashedPassword, schoolId);

                    // Send notification email
                    emailService.sendEmail(enteredEmail, "Password Changed",
                            "Your password was successfully changed. If you didn't make this change, " +
                                    "please contact your administrator immediately.");

                    showAlert(Alert.AlertType.INFORMATION, "Password reset successfully!");
                    clearFields();
                    handleBackToLogin();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Failed to reset password.");
                }
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private UUID getUserId(Connection conn, String email) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? (UUID) rs.getObject("user_id") : null;
        }
    }

    private boolean isPasswordInHistory(Connection conn, UUID userId, String newPassword, int historyLimit)
            throws SQLException {
        // Check current password first
        if (isSameAsCurrentPassword(conn, userId, newPassword)) {
            return true;
        }

        // Check historical passwords
        String sql = "SELECT password_hash FROM password_history " +
                "WHERE user_id = ? " +
                "ORDER BY created_at DESC " +
                "LIMIT ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setInt(2, historyLimit - 1); // minus 1 because we checked current separately

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String oldHash = rs.getString("password_hash");
                if (BCrypt.checkpw(newPassword, oldHash)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSameAsCurrentPassword(Connection conn, UUID userId, String newPassword)
            throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String currentHash = rs.getString("password_hash");
                return BCrypt.checkpw(newPassword, currentHash);
            }
            return false;
        }
    }

    private void storePasswordHistory(Connection conn, UUID userId, String newHash, UUID schoolId)
            throws SQLException {
        String sql = "INSERT INTO password_history (user_id, password_hash, school_id) " +
                "VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setString(2, newHash);
            stmt.setObject(3, schoolId);
            stmt.executeUpdate();
        }

        // Clean up old history records
        cleanPasswordHistory(conn, userId);
    }

    private void cleanPasswordHistory(Connection conn, UUID userId) throws SQLException {
        // Keep only the most recent records (limit + 1 for safety)
        String sql = "DELETE FROM password_history " +
                "WHERE user_id = ? AND history_id NOT IN (" +
                "  SELECT history_id FROM password_history " +
                "  WHERE user_id = ? " +
                "  ORDER BY created_at DESC " +
                "  LIMIT " + (PASSWORD_HISTORY_LIMIT + 1) +
                ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            stmt.executeUpdate();
        }
    }

    private UUID getSchoolIdForEmail(Connection conn, String email) throws SQLException {
        String sql = "SELECT school_id FROM users WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            var rs = stmt.executeQuery();
            return rs.next() ? (UUID) rs.getObject("school_id") : null;
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
        }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void clearFields() {
        emailField.clear();
        tokenField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
    }
}