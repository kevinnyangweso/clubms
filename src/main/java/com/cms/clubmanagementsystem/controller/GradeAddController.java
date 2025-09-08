package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.ClubService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.UUID;

public class GradeAddController implements Initializable {

    @FXML private TextField gradeNameField;

    private UUID schoolId;
    private final ClubService clubService = new ClubService();
    private boolean isActiveCoordinator = false; // NEW: Track coordinator status

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        schoolId = SessionManager.getCurrentSchoolId();
        checkCoordinatorPermissions(); // NEW: Check permissions
        updateUIForPermissions(); // NEW: Update UI based on permissions
    }

    // NEW: Check coordinator permissions
    private void checkCoordinatorPermissions() {
        isActiveCoordinator = SessionManager.isActiveCoordinator();
    }

    // NEW: Update UI based on permissions
    private void updateUIForPermissions() {
        if (!isActiveCoordinator) {
            gradeNameField.setDisable(true);
            gradeNameField.setTooltip(new Tooltip("Only active coordinators can add grades"));

            // Show message if user doesn't have permission
            showAlert("Access Denied", "Only active coordinators can add grades.");
        } else {
            gradeNameField.setDisable(false);
            gradeNameField.setTooltip(null);
        }
    }

    @FXML
    private void handleSave() {
        // NEW: Check if coordinator has edit permissions
        if (!isActiveCoordinator) {
            showAlert("Access Denied", "Only active coordinators can add grades.");
            return;
        }

        String gradeName = gradeNameField.getText().trim();

        if (gradeName.isEmpty()) {
            showAlert("Validation Error", "Please enter a grade name.");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Check if grade already exists
            if (gradeExists(conn, gradeName)) {
                showAlert("Duplicate Grade", "A grade with this name already exists.");
                return;
            }

            // Insert new grade
            String sql = "INSERT INTO grades (grade_name, school_id) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, gradeName);
                stmt.setObject(2, schoolId);
                stmt.executeUpdate();
            }

            showAlert("Success", "Grade added successfully!");
            closeWindow();

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to add grade: " + e.getMessage());
        }
    }

    private boolean gradeExists(Connection conn, String gradeName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM grades WHERE grade_name = ? AND school_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, gradeName);
            stmt.setObject(2, schoolId);
            try (var rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) gradeNameField.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}