package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;
import java.util.UUID;

public class TeacherDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label dashboardTitleLabel;
    @FXML private Label totalLearnersLabel;
    @FXML private Label attendanceTodayLabel;

    private UUID assignedClubId;
    private String assignedClubName;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadTeacherAssignment();
        setupUserInfo();
        loadClubStatistics();
    }

    private void setupUserInfo() {
        String username = SessionManager.getCurrentUsername();
        System.out.println("DEBUG - setupUserInfo(): username=" + username + ", assignedClubName=" + assignedClubName);

        if (username != null && !username.isEmpty()) {
            String capitalizedUsername = username.substring(0, 1).toUpperCase() +
                    username.substring(1).toLowerCase();

            if (assignedClubName != null && !assignedClubName.isEmpty()) {
                String capitalizedClubName = assignedClubName.substring(0, 1).toUpperCase() +
                        assignedClubName.substring(1).toLowerCase();

                if (welcomeLabel != null) {
                    welcomeLabel.setText("Welcome " + capitalizedUsername +
                            ", you have been assigned to " + capitalizedClubName + " club.");
                }
                System.out.println("DEBUG - Set welcome message with club: " + capitalizedClubName);

            } else {
                if (welcomeLabel != null) {
                    welcomeLabel.setText("Welcome " + capitalizedUsername +
                            "! Please contact administrator for club assignment.");
                }
                System.out.println("DEBUG - Set welcome message WITHOUT club (assignedClubName is null/empty)");
            }
        } else {
            if (welcomeLabel != null) {
                welcomeLabel.setText("Welcome!");
            }
            System.out.println("DEBUG - Set generic welcome message (username is null/empty)");
        }
    }

    // UPDATED: Handle enrollment - No need to pass club information
    @FXML
    private void handleEnrollment() {
        // The EnrollmentController now automatically loads the teacher's assigned club
        try {
            // Load the enrollment management interface
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/enrollment-management.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Enrollment Management");
            stage.setScene(new Scene(root, 900, 600));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(welcomeLabel.getScene().getWindow());
            stage.showAndWait();

        } catch (IOException e) {
            showError("Failed to load enrollment management: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadTeacherAssignment() {
        UUID userId = SessionManager.getCurrentUserId();
        System.out.println("Loading teacher assignment for user: " + userId);

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = """
                SELECT c.club_id, c.club_name 
                FROM clubs c
                JOIN club_teachers ct ON c.club_id = ct.club_id
                WHERE ct.teacher_id = ? 
                AND c.is_active = true
                AND c.school_id = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);
                ps.setObject(2, SessionManager.getCurrentSchoolId());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    assignedClubId = (UUID) rs.getObject("club_id");
                    assignedClubName = rs.getString("club_name");
                    System.out.println("FOUND CLUB: " + assignedClubName + " (ID: " + assignedClubId + ")");

                    String capitalizedClubName = assignedClubName.substring(0, 1).toUpperCase() + assignedClubName.substring(1).toLowerCase();
                    if (dashboardTitleLabel != null) {
                        dashboardTitleLabel.setText(capitalizedClubName + " Dashboard");
                    }
                    System.out.println("Set dashboard title to: " + capitalizedClubName + " Dashboard");
                } else {
                    System.out.println("NO CLUB FOUND - This shouldn't happen based on our debug!");
                    if (dashboardTitleLabel != null) {
                        dashboardTitleLabel.setText("Teacher Dashboard");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error in loadTeacherAssignment: " + e.getMessage());
            if (dashboardTitleLabel != null) {
                dashboardTitleLabel.setText("Teacher Dashboard");
            }
            e.printStackTrace();
        }
    }

    private void loadClubStatistics() {
        if (assignedClubId == null) {
            if (totalLearnersLabel != null) {
                totalLearnersLabel.setText("0");
            }
            if (attendanceTodayLabel != null) {
                attendanceTodayLabel.setText("0/0");
            }
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Load total learners
            String learnersSql = "SELECT COUNT(*) FROM club_enrollments WHERE club_id = ? AND is_active = true";
            try (PreparedStatement ps = conn.prepareStatement(learnersSql)) {
                ps.setObject(1, assignedClubId);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && totalLearnersLabel != null) {
                    totalLearnersLabel.setText(String.valueOf(rs.getInt(1)));
                }
            }

            // Load today's attendance
            loadTodaysAttendance();

        } catch (Exception e) {
            showError("Error loading club statistics");
            e.printStackTrace();
        }
    }

    private void loadTodaysAttendance() {
        // Placeholder implementation
        if (attendanceTodayLabel != null) {
            attendanceTodayLabel.setText("0/0");
        }
    }

    @FXML
    private void handleTakeAttendance() {
        if (assignedClubId == null) {
            showError("No club assigned. Please contact administrator.");
            return;
        }
        System.out.println("Take attendance for club: " + assignedClubName);
        // Implement attendance taking logic
    }

    @FXML
    private void handleViewLearners() {
        if (assignedClubId == null) {
            showError("No club assigned. Please contact administrator.");
            return;
        }
        System.out.println("View learners for club: " + assignedClubName);
        // Implement learner view logic
    }

    @FXML
    private void handleViewReports() {
        if (assignedClubId == null) {
            showError("No club assigned. Please contact administrator.");
            return;
        }
        System.out.println("View reports for club: " + assignedClubName);
        // Implement reports logic
    }

    @FXML
    private void handleLogout() {
        SessionManager.closeSession();
        System.out.println("Teacher logout");

        // Redirect to login screen
        try {
            // Get the current stage from any UI component
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();

            // Load the login FXML - use the correct path
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            // Set up the new scene
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Club Management System - Login");
            stage.centerOnScreen();

        } catch (IOException e) {
            System.err.println("Error loading login screen: " + e.getMessage());
            e.printStackTrace();

            // If navigation fails, close the application gracefully
            Platform.exit();
        }
    }

    private void showError(String message) {
        System.err.println("Error: " + message);
        // Could implement Alert dialog here
    }
}