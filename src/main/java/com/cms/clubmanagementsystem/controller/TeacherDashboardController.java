package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ResourceBundle;
import java.util.UUID;

public class TeacherDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label dashboardTitleLabel; // ← NEW: For personalized title
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

                welcomeLabel.setText("Welcome " + capitalizedUsername +
                        ", you have been assigned to " + capitalizedClubName + " club.");
                System.out.println("DEBUG - Set welcome message with club: " + capitalizedClubName);

            } else {
                welcomeLabel.setText("Welcome " + capitalizedUsername +
                        "! Please contact administrator for club assignment.");
                System.out.println("DEBUG - Set welcome message WITHOUT club (assignedClubName is null/empty)");
            }
        } else {
            welcomeLabel.setText("Welcome!");
            System.out.println("DEBUG - Set generic welcome message (username is null/empty)");
        }
    }

    private void loadTeacherAssignment() {
        UUID userId = SessionManager.getCurrentUserId();
        System.out.println("Loading teacher assignment for user: " + userId);

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT c.club_id, c.club_name FROM users u " +
                    "JOIN clubs c ON u.club_id = c.club_id " +
                    "WHERE u.user_id = ? AND u.is_active = true AND c.is_active = true";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    assignedClubId = (UUID) rs.getObject("club_id");
                    assignedClubName = rs.getString("club_name");
                    System.out.println("FOUND CLUB: " + assignedClubName + " (ID: " + assignedClubId + ")");

                    String capitalizedClubName = assignedClubName.substring(0, 1).toUpperCase() + assignedClubName.substring(1).toLowerCase();
                    dashboardTitleLabel.setText(capitalizedClubName + " Dashboard");
                    System.out.println("Set dashboard title to: " + capitalizedClubName + " Dashboard");
                } else {
                    System.out.println("NO CLUB FOUND - This shouldn't happen based on our debug!");
                    dashboardTitleLabel.setText("Teacher Dashboard");
                }
            }
        } catch (Exception e) {
            System.out.println("Error in loadTeacherAssignment: " + e.getMessage());
            dashboardTitleLabel.setText("Teacher Dashboard");
            e.printStackTrace();
        }
    }

    private void loadClubStatistics() {
        if (assignedClubId == null) {
            totalLearnersLabel.setText("0");
            attendanceTodayLabel.setText("0/0");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Load total learners
            String learnersSql = "SELECT COUNT(*) FROM club_enrollments WHERE club_id = ? AND is_active = true";
            try (PreparedStatement ps = conn.prepareStatement(learnersSql)) {
                ps.setObject(1, assignedClubId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totalLearnersLabel.setText(String.valueOf(rs.getInt(1)));
                }
            }

            // Load today's attendance (placeholder - implement based on your schema)
            loadTodaysAttendance();

        } catch (Exception e) {
            showError("Error loading club statistics");
            e.printStackTrace();
        }
    }

    private void loadTodaysAttendance() {
        // Placeholder - implement your attendance logic here
        attendanceTodayLabel.setText("0/0");

        // Example implementation (adjust based on your schema):
        /*
        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT COUNT(*) FROM attendance WHERE club_id = ? AND date = CURRENT_DATE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, assignedClubId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int presentToday = rs.getInt(1);
                    attendanceTodayLabel.setText(presentToday + "/" + totalLearners);
                }
            }
        } catch (Exception e) {
            attendanceTodayLabel.setText("Error");
        }
        */
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
        // Implement logout redirect to log in screen
        System.out.println("Teacher logout");
    }

    private void showError(String message) {
        // Implement error display (could use Alert dialog)
        System.err.println("Error: " + message);
    }
}