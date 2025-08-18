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

        public String getSchoolName() {
            return schoolName;
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

            if (schools.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No schools found in the database.");
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
            // Set tenant context
            TenantContext.setTenant(conn, selectedSchool.getId().toString());

            PasswordResetService service = new PasswordResetService();
            String generatedToken = service.generateResetToken(conn, email);

            if (generatedToken == null) {
                showAlert(Alert.AlertType.ERROR,
                        "No account found with that email in the selected school");
                return;
            }

            // Send email and open reset screen
            new EmailService().sendEmail(email, "Password Reset",
                    "Your reset token: " + generatedToken);

            openResetPasswordScreen(generatedToken, email, selectedSchool.getId());

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void debugVerifyTenantContext(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_setting('app.current_school_id')")) {
            if (rs.next()) {
                System.out.println("Current tenant context: " + rs.getString(1));
            }
        }
    }

    private void openResetPasswordScreen(String token, String email, UUID schoolId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/reset-password.fxml"));
            Parent root = loader.load();

            ResetPasswordController controller = loader.getController();
            controller.setResetToken(token, email, schoolId);

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Reset Password");

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Failed to load reset password screen.");
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