package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.regex.Pattern;

public class PasswordChangeController {

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private HBox errorBox;
    @FXML private Rectangle strengthMeter;
    @FXML private Label lengthIcon;
    @FXML private Label upperIcon;
    @FXML private Label lowerIcon;
    @FXML private Label numberIcon;
    @FXML private Label specialIcon;

    // TextFields for visible password toggle
    @FXML private TextField currentPasswordVisibleField;
    @FXML private TextField newPasswordVisibleField;
    @FXML private TextField confirmPasswordVisibleField;

    private String username;
    private UUID userId;
    private Runnable onPasswordChangedAndClose;
    private boolean isPasswordVisible = false;

    public void initialize(String username, UUID userId, Runnable onPasswordChangedAndClose) {
        this.username = username;
        this.userId = userId;
        this.onPasswordChangedAndClose = onPasswordChangedAndClose;

        // Initially hide visible fields
        if (currentPasswordVisibleField != null) currentPasswordVisibleField.setVisible(false);
        if (newPasswordVisibleField != null) newPasswordVisibleField.setVisible(false);
        if (confirmPasswordVisibleField != null) confirmPasswordVisibleField.setVisible(false);

        // Set up password strength listener
        newPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            validatePasswordStrength(newValue);
        });

        newPasswordVisibleField.textProperty().addListener((observable, oldValue, newValue) -> {
            validatePasswordStrength(newValue);
        });
    }

    @FXML
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            // Switch to visible text fields
            currentPasswordVisibleField.setText(currentPasswordField.getText());
            currentPasswordField.setVisible(false);
            currentPasswordVisibleField.setVisible(true);
            currentPasswordVisibleField.setManaged(true);

            newPasswordVisibleField.setText(newPasswordField.getText());
            newPasswordField.setVisible(false);
            newPasswordVisibleField.setVisible(true);
            newPasswordVisibleField.setManaged(true);

            confirmPasswordVisibleField.setText(confirmPasswordField.getText());
            confirmPasswordField.setVisible(false);
            confirmPasswordVisibleField.setVisible(true);
            confirmPasswordVisibleField.setManaged(true);
        } else {
            // Switch back to password fields
            currentPasswordField.setText(currentPasswordVisibleField.getText());
            currentPasswordVisibleField.setVisible(false);
            currentPasswordVisibleField.setManaged(false);
            currentPasswordField.setVisible(true);

            newPasswordField.setText(newPasswordVisibleField.getText());
            newPasswordVisibleField.setVisible(false);
            newPasswordVisibleField.setManaged(false);
            newPasswordField.setVisible(true);

            confirmPasswordField.setText(confirmPasswordVisibleField.getText());
            confirmPasswordVisibleField.setVisible(false);
            confirmPasswordVisibleField.setManaged(false);
            confirmPasswordField.setVisible(true);
        }
    }

    @FXML
    private void handleChangePassword() {
        String currentPassword = isPasswordVisible ?
                currentPasswordVisibleField.getText() : currentPasswordField.getText();
        String newPassword = isPasswordVisible ?
                newPasswordVisibleField.getText() : newPasswordField.getText();
        String confirmPassword = isPasswordVisible ?
                confirmPasswordVisibleField.getText() : confirmPasswordField.getText();

        if (!validateInputs(currentPassword, newPassword, confirmPassword)) {
            return;
        }

        if (changePassword(currentPassword, newPassword)) {
            showAlert(Alert.AlertType.INFORMATION, "Success", "Password changed successfully!\nYou will be redirected to the login page.");

            // Close the dialog and trigger the callback to navigate to login page
            closeDialog();
            if (onPasswordChangedAndClose != null) {
                onPasswordChangedAndClose.run();
            }
        }
    }

    private void closeDialog() {
        // Get the current stage (dialog) and close it
        Stage stage = (Stage) currentPasswordField.getScene().getWindow();
        stage.close();
    }

    private boolean validateInputs(String currentPassword, String newPassword, String confirmPassword) {
        hideError(); // Call this to hide any previous errors

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showError("All fields are required");
            return false;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("New passwords don't match");
            return false;
        }

        if (newPassword.equals(currentPassword)) {
            showError("New password cannot be the same as current password");
            return false;
        }

        if (!isPasswordStrong(newPassword)) {
            showError("Password must contain:\n- 8-64 characters\n- 1 uppercase letter\n- 1 lowercase letter\n- 1 number\n- 1 special character");
            return false;
        }

        return true;
    }

    private void validatePasswordStrength(String password) {
        // Check each requirement
        boolean hasMinLength = password.length() >= 8;
        boolean hasMaxLength = password.length() <= 64;
        boolean hasUpperCase = !password.equals(password.toLowerCase());
        boolean hasLowerCase = !password.equals(password.toUpperCase());
        boolean hasNumber = Pattern.compile(".*\\d.*").matcher(password).matches();
        boolean hasSpecial = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*").matcher(password).matches();

        // Update requirement indicators
        updateIcon(lengthIcon, hasMinLength && hasMaxLength);
        updateIcon(upperIcon, hasUpperCase);
        updateIcon(lowerIcon, hasLowerCase);
        updateIcon(numberIcon, hasNumber);
        updateIcon(specialIcon, hasSpecial);

        // Update strength meter if it exists
        if (strengthMeter != null) {
            // Calculate strength
            int strength = 0;
            if (hasMinLength) strength++;
            if (hasMaxLength) strength++;
            if (hasUpperCase) strength++;
            if (hasLowerCase) strength++;
            if (hasNumber) strength++;
            if (hasSpecial) strength++;

            // Update strength meter
            if (strength <= 2) {
                strengthMeter.setWidth(33);
                strengthMeter.setFill(Color.web("#e74c3c"));
            } else if (strength <= 4) {
                strengthMeter.setWidth(66);
                strengthMeter.setFill(Color.web("#f39c12"));
            } else {
                strengthMeter.setWidth(100);
                strengthMeter.setFill(Color.web("#27ae60"));
            }
        }
    }

    private void updateIcon(Label icon, boolean met) {
        if (icon != null) {
            if (met) {
                icon.setText("✓");
                icon.setTextFill(Color.web("#27ae60"));
            } else {
                icon.setText("○");
                icon.setTextFill(Color.web("#95a5a6"));
            }
        }
    }

    private boolean changePassword(String currentPassword, String newPassword) {
        // Verify current password first
        if (!verifyCurrentPassword(currentPassword)) {
            showError("Current password is incorrect");
            return false;
        }

        String sql = """
            UPDATE users 
            SET password_hash = ?, first_login = FALSE, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));

            stmt.setString(1, newPasswordHash);
            stmt.setObject(2, userId);

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                return true;
            } else {
                showError("Failed to update password");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to change password: " + e.getMessage());
            return false;
        }
    }

    private boolean verifyCurrentPassword(String password) {
        String sql = "SELECT password_hash FROM users WHERE user_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                return BCrypt.checkpw(password, storedHash);
            }
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isPasswordStrong(String password) {
        if (password.length() < 8 || password.length() > 64) return false;
        boolean hasUpper = !password.equals(password.toLowerCase());
        boolean hasLower = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorBox.setVisible(true);
    }

    private void hideError() {
        errorBox.setVisible(false);
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}