package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class CoordinatorDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label schoolInfoLabel;
    @FXML private Label totalClubsLabel;
    @FXML private Label totalTeachersLabel;
    @FXML private Label totalLearnersLabel;
    @FXML private StackPane contentArea;

    // Navigation buttons
    @FXML private Button overviewButton;
    @FXML private Button clubManagementButton;
    @FXML private Button teacherManagementButton;
    @FXML private Button enrollmentButton;
    @FXML private Button attendanceButton;
    @FXML private Button reportsButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUserInfo();
        loadOverview(); // Load default view
        loadStatistics(); // Load initial stats
    }

    private void setupUserInfo() {
        String username = SessionManager.getCurrentUsername();
        String schoolName = SessionManager.getCurrentSchoolName();

        welcomeLabel.setText("Welcome, " + username);
        schoolInfoLabel.setText("School: " + schoolName);
    }

    private void loadOverview() {
        loadModule("/fxml/overview.fxml");
        clearButtonStyles();
        if (overviewButton != null) {
            overviewButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        }
    }

    private void loadStatistics() {
        // TODO: Load actual statistics from database
        totalClubsLabel.setText("Clubs: 12");
        totalTeachersLabel.setText("Teachers: 8");
        totalLearnersLabel.setText("Learners: 145");
    }

    @FXML
    private void showOverview() {
        loadModule("/fxml/overview.fxml");
        clearButtonStyles();
        overviewButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showClubManagement() {
        loadModule("/fxml/club-management.fxml");
        clearButtonStyles();
        clubManagementButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showTeacherManagement() {
        loadModule("/fxml/teacher-management.fxml");
        clearButtonStyles();
        teacherManagementButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showEnrollment() {
        loadModule("/fxml/enrollment-management.fxml");
        clearButtonStyles();
        enrollmentButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showAttendance() {
        loadModule("/fxml/attendance.fxml");
        clearButtonStyles();
        attendanceButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showReports() {
        loadModule("/fxml/reports.fxml");
        clearButtonStyles();
        reportsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    private void loadModule(String fxmlPath) {
        try {
            Parent module = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentArea.getChildren().setAll(module);
        } catch (IOException e) {
            System.err.println("Error loading module: " + fxmlPath);
            e.printStackTrace();
            // Show error message to user
        }
    }

    private void clearButtonStyles() {
        Button[] buttons = {overviewButton, clubManagementButton, teacherManagementButton,
                enrollmentButton, attendanceButton, reportsButton};
        for (Button button : buttons) {
            button.setStyle("-fx-background-color: transparent; -fx-text-fill: #ecf0f1;");
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.closeSession();
        // Redirect to login screen
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Login - Club Management System");
            stage.show();
        } catch (IOException e) {
            System.err.println("Error loading login screen: " + e.getMessage());
        }
    }
}