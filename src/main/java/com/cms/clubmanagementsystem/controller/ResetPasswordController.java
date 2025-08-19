package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.PasswordResetService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.TenantContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class ResetPasswordController {

    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    // Individual digit fields
    @FXML private TextField tokenDigit1;
    @FXML private TextField tokenDigit2;
    @FXML private TextField tokenDigit3;
    @FXML private TextField tokenDigit4;
    @FXML private TextField tokenDigit5;
    @FXML private TextField tokenDigit6;

    private TextField[] tokenDigits;
    private String resetToken;
    private String userEmail;
    private UUID userSchoolId;

    @FXML
    public void initialize() {
        // Initialize the array for easy access
        tokenDigits = new TextField[]{tokenDigit1, tokenDigit2, tokenDigit3,
                tokenDigit4, tokenDigit5, tokenDigit6};

        // Set up input filtering and auto-advance for each digit field
        for (TextField digitField : tokenDigits) {
            setupDigitFieldBehavior(digitField);
        }

        // Set up paste handling for all digit fields
        setupPasteHandling();
    }

    private void setupDigitFieldBehavior(TextField digitField) {
        // Input filtering - only allow digits
        digitField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) return;

            // Only allow single digits
            if (!newValue.matches("\\d")) {
                digitField.setText("");
                return;
            }

            // If a digit is entered, move to next field
            if (newValue.length() == 1) {
                moveToNextDigitField(digitField);
            }
        });

        // Handle backspace and other key events
        digitField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.BACK_SPACE && digitField.getText().isEmpty()) {
                moveToPreviousDigitField(digitField);
            }

            // Handle paste with Ctrl+V
            if (event.isShortcutDown() && event.getCode() == KeyCode.V) {
                Platform.runLater(this::handlePaste);
                event.consume(); // Prevent default paste behavior
            }
        });
    }

    private void setupPasteHandling() {
        // Add paste handler to all digit fields
        for (TextField digitField : tokenDigits) {
            digitField.setOnKeyPressed(event -> {
                if (event.isShortcutDown() && event.getCode() == KeyCode.V) {
                    Platform.runLater(this::handlePaste);
                    event.consume();
                }
            });
        }
    }

    // Add this missing method that's referenced in FXML
    @FXML
    private void handleTokenDigitInput(KeyEvent event) {
        TextField source = (TextField) event.getSource();

        // Handle backspace to move to previous field when current field is empty
        if (event.getCode() == KeyCode.BACK_SPACE && source.getText().isEmpty()) {
            moveToPreviousDigitField(source);
        }

        // Handle left arrow to move to previous field
        if (event.getCode() == KeyCode.LEFT) {
            moveToPreviousDigitField(source);
            event.consume();
        }

        // Handle right arrow to move to next field
        if (event.getCode() == KeyCode.RIGHT) {
            moveToNextDigitField(source);
            event.consume();
        }

        // Handle digit input - auto-advance
        if (event.getCode().isDigitKey() && !source.getText().isEmpty()) {
            moveToNextDigitField(source);
        }
    }

    private void handlePaste() {
        String clipboardContent = Clipboard.getSystemClipboard().getString();
        if (clipboardContent != null) {
            String digitsOnly = clipboardContent.replaceAll("[^\\d]", "");
            if (digitsOnly.length() == 6) {
                setTokenFromString(digitsOnly);
                newPasswordField.requestFocus();
            } else {
                // Show warning for invalid paste
                showAlert(Alert.AlertType.WARNING,
                        "Pasted content must be exactly 6 digits. Found: " + digitsOnly.length());
            }
        }
    }

    private void setTokenFromString(String token) {
        if (token.length() != 6) return;

        for (int i = 0; i < 6; i++) {
            tokenDigits[i].setText(String.valueOf(token.charAt(i)));
        }
    }

    private void moveToNextDigitField(TextField currentField) {
        for (int i = 0; i < tokenDigits.length - 1; i++) {
            if (tokenDigits[i] == currentField) {
                tokenDigits[i + 1].requestFocus();
                break;
            }
        }

        // If last field is filled, move to password field
        if (currentField == tokenDigits[tokenDigits.length - 1] && !currentField.getText().isEmpty()) {
            newPasswordField.requestFocus();
        }
    }

    private void moveToPreviousDigitField(TextField currentField) {
        for (int i = 1; i < tokenDigits.length; i++) {
            if (tokenDigits[i] == currentField) {
                tokenDigits[i - 1].requestFocus();
                break;
            }
        }
    }

    /**
     * Gets the complete token from individual digit fields
     */
    private String getTokenFromDigits() {
        StringBuilder token = new StringBuilder();
        for (TextField digitField : tokenDigits) {
            token.append(digitField.getText());
        }
        return token.toString();
    }

    public void setUserData(String email, UUID schoolId) {
        this.userEmail = email;
        this.userSchoolId = schoolId;

        // Retrieve the token from SessionManager with timeout check
        this.resetToken = (String) SessionManager.getTransientData("resetToken_for_" + email);

        if (this.resetToken == null) {
            showAlert(Alert.AlertType.WARNING,
                    "Reset token expired or invalid. Please request a new password reset.");
        }
    }

    @FXML
    private void handleResetPassword() {
        String enteredToken = getTokenFromDigits();
        String newPassword = newPasswordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        // Validate token format (should be 6 digits)
        if (!isValidTokenFormat(enteredToken)) {
            showAlert(Alert.AlertType.ERROR, "Please enter a complete 6-digit code.");
            return;
        }

        // Check if we have a valid token from session
        if (resetToken == null || resetToken.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Reset session expired or invalid. Please request a new token.");
            return;
        }

        // Additional check to ensure token hasn't expired since retrieval
        if (!SessionManager.hasValidTransientData("resetToken_for_" + userEmail)) {
            showAlert(Alert.AlertType.ERROR, "Reset token has expired. Please request a new token.");
            return;
        }

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

        // Validate password strength
        if (!isPasswordStrong(newPassword)) {
            showAlert(Alert.AlertType.ERROR,
                    "Password must be at least 8 characters with uppercase, lowercase, number, and special character.");
            return;
        }

        // Validate token against database (check expiry) and update password
        try (Connection conn = DatabaseConnector.getConnection()) {
            TenantContext.setTenant(conn, userSchoolId.toString());

            PasswordResetService service = new PasswordResetService();
            if (!service.validateResetToken(conn, userEmail, enteredToken)) {
                showAlert(Alert.AlertType.ERROR, "Token has expired or is invalid. Please request a new one.");
                return;
            }

            // Use the new method with password history validation
            boolean success = service.resetPasswordWithHistory(conn, userEmail, userSchoolId, newPassword, resetToken);

            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Password reset successfully!");

                // Clear the transient data after successful reset
                SessionManager.removeTransientData("resetToken_for_" + userEmail);
                SessionManager.removeTransientData("resetSchool_for_" + userEmail);

                clearFields();
                handleBackToLogin();
            } else {
                showAlert(Alert.AlertType.ERROR, "Failed to reset password. Invalid token or user not found.");
            }

        } catch (SecurityException e) {
            // Password history violation
            showAlert(Alert.AlertType.ERROR, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database error: " + e.getMessage());
        }
    }

    /**
     * Validates password strength
     */
    private boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        // Check for at least one uppercase, one lowercase, one digit, one special char
        boolean hasUpper = !password.equals(password.toLowerCase());
        boolean hasLower = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    /**
     * Validates that the token is a 6-digit number
     */
    private boolean isValidTokenFormat(String token) {
        return token != null && token.matches("\\d{6}");
    }

    @FXML
    private void handleBackToLogin() {
        // Clean up any transient reset data
        if (userEmail != null) {
            SessionManager.removeTransientData("resetToken_for_" + userEmail);
            SessionManager.removeTransientData("resetSchool_for_" + userEmail);
        }

        try {
            Stage stage = (Stage) tokenDigit1.getScene().getWindow();
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
        // Clear all digit fields
        for (TextField digitField : tokenDigits) {
            digitField.clear();
        }
        newPasswordField.clear();
        confirmPasswordField.clear();

        // Focus back to first digit field
        tokenDigit1.requestFocus();
    }

    @FXML
    private void handleBackToForgotPassword() {
        // Clean up transient data
        if (userEmail != null) {
            SessionManager.removeTransientData("resetToken_for_" + userEmail);
            SessionManager.removeTransientData("resetSchool_for_" + userEmail);
        }

        try {
            Stage stage = (Stage) tokenDigit1.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/forgot-password.fxml"));
            stage.setScene(new Scene(root));
            stage.setTitle("Forgot Password");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Failed to load forgot password screen: " + e.getMessage());
        }
    }

}