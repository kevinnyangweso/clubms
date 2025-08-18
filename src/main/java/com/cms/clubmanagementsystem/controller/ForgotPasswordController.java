package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.EmailService;
import com.cms.clubmanagementsystem.service.PasswordResetService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.TenantContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.UUID;

public class ForgotPasswordController {

    @FXML private ComboBox<SchoolItem> schoolComboBox;
    @FXML private TextField emailField;

    private final ObservableList<SchoolItem> schools = FXCollections.observableArrayList();

    public static class SchoolItem {
        private final String schoolName;
        private final UUID schoolId;

        public SchoolItem(String schoolName, UUID schoolId) {
            this.schoolName = schoolName;
            this.schoolId = schoolId;
        }

        @Override
        public String toString() {
            return schoolName;
        }

        public UUID getId() {
            return schoolId;
        }
    }

    @FXML
    public void initialize() {
        schoolComboBox.setItems(schools);
        loadSchools();
    }

    private void loadSchools() {
        schools.clear();
        String query = "SELECT school_id, school_name FROM schools ORDER BY school_name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String idStr = rs.getString("school_id");
                String schoolName = rs.getString("school_name");

                if (idStr == null || idStr.isEmpty() || schoolName == null || schoolName.isEmpty()) {
                    continue;
                }

                try {
                    UUID schoolId = UUID.fromString(idStr);
                    schools.add(new SchoolItem(schoolName, schoolId));
                } catch (IllegalArgumentException e) {
                    System.out.println("Skipping invalid UUID: " + idStr);
                }
            }

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error loading schools: " + e.getMessage());
        }
    }

    @FXML
    private void handleSendToken() {
        String email = emailField.getText().trim().toLowerCase();
        SchoolItem selectedSchool = schoolComboBox.getSelectionModel().getSelectedItem();

        if (email.isEmpty() || selectedSchool == null) {
            showAlert(Alert.AlertType.WARNING, "Please enter email and select school");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            TenantContext.setTenant(conn, selectedSchool.getId().toString());

            PasswordResetService service = new PasswordResetService();
            String generatedToken = service.generateResetToken(conn, email);

            if (generatedToken == null) {
                showAlert(Alert.AlertType.ERROR,
                        "No account found with that email in the selected school");
                return;
            }

            // Send email with token
            new EmailService().sendEmail(email, "Password Reset Token",
                    "Your password reset token is: " + generatedToken + "\n\n" +
                            "Please enter this token in the reset password form.");

            showAlert(Alert.AlertType.INFORMATION,
                    "A reset token has been sent to your email.\n" +
                            "Please check your email and copy-paste the token in the " +
                            "Enter Reset Token field.");

            // Open reset password screen (empty token)
            openResetPasswordScreen();

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void openResetPasswordScreen() {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/reset-password.fxml"));
            stage.setScene(new Scene(root));
            stage.setTitle("Reset Password");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Failed to load reset password screen.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            stage.setScene(new Scene(root));
            stage.setTitle("Login");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Failed to load login screen.");
        }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}