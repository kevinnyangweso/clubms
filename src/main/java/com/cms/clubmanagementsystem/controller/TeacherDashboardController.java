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
import java.util.ResourceBundle;
import java.util.UUID;

public class TeacherDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label dashboardTitleLabel; // ← NEW: For personalized title
    @FXML private Label clubInfoLabel;
    @FXML private Label totalLearnersLabel;
    @FXML private Label attendanceTodayLabel;

    private UUID assignedClubId;
    private String assignedClubName;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUserInfo();
        loadTeacherAssignment();
        loadClubStatistics();
    }

    private void setupUserInfo() {
        String username = SessionManager.getCurrentUsername();
        welcomeLabel.setText("Welcome, " + username);
    }

    private void loadTeacherAssignment() {
        UUID userId = SessionManager.getCurrentUserId();

        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT c.club_id, c.club_name FROM users u " +
                    "JOIN clubs c ON u.club_id = c.club_id " +
                    "WHERE u.user_id = ? AND u.is_active = true";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    assignedClubId = (UUID) rs.getObject("club_id");
                    assignedClubName = rs.getString("club_name");

                    // Update UI with club information
                    clubInfoLabel.setText("Assigned to: " + assignedClubName);
                    dashboardTitleLabel.setText(assignedClubName + " Dashboard"); // ← Personalized title
                } else {
                    clubInfoLabel.setText("No club assigned!");
                    clubInfoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }
        } catch (Exception e) {
            showError("Error loading teacher assignment");
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
            String learnersSql = "SELECT COUNT(*) FROM learner_clubs WHERE club_id = ? AND is_active = true";
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