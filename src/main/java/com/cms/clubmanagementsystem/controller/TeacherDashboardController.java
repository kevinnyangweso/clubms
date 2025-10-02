package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.EventBus;
import com.cms.clubmanagementsystem.utils.EventTypes;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Month;
import java.util.ResourceBundle;
import java.util.UUID;

import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeacherDashboardController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(TeacherDashboardController.class);

    @FXML private Label welcomeLabel;
    @FXML private Label dashboardTitleLabel;
    @FXML private Label totalLearnersLabel;
    @FXML private Label attendanceTodayLabel;
    @FXML private ComboBox<String> yearComboBox; // Added for year selection
    @FXML private ComboBox<String> termComboBox; // For term selection
    @FXML private Tab attendanceTab;
    @FXML private Tab learnersTab;

    private UUID assignedClubId;
    private String assignedClubName;
    private int selectedYear; // Stores the user-selected year
    private int selectedTerm; // Stores the user-selected term
    private LearnersTabController learnersTabController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing TeacherDashboardController");
        // Initialize yearComboBox
        if (yearComboBox != null) {
            yearComboBox.setItems(FXCollections.observableArrayList("2024", "2025", "2026"));
            selectedYear = LocalDate.now().getYear(); // Default to current year
            yearComboBox.setValue(String.valueOf(selectedYear));
            yearComboBox.setOnAction(event -> {
                String selected = yearComboBox.getValue();
                if (selected != null) {
                    selectedYear = Integer.parseInt(selected);
                    logger.info("Selected year changed to: {}", selectedYear);
                    loadClubStatistics();
                }
            });
        } else {
            logger.error("yearComboBox is null - check FXML binding");
            selectedYear = LocalDate.now().getYear(); // Fallback to current year
        }

        // Initialize termComboBox
        if (termComboBox != null) {
            termComboBox.setItems(FXCollections.observableArrayList("Term 1", "Term 2", "Term 3"));
            selectedTerm = getCurrentTerm(); // Default to current term
            termComboBox.setValue("Term " + selectedTerm);
            termComboBox.setOnAction(event -> {
                String selected = termComboBox.getValue();
                if (selected != null) {
                    selectedTerm = Integer.parseInt(selected.replace("Term ", ""));
                    logger.info("Selected term changed to: {}", selectedTerm);
                    loadClubStatistics();
                }
            });
        } else {
            logger.error("termComboBox is null - check FXML binding");
            selectedTerm = getCurrentTerm(); // Fallback to current term
        }

        loadTeacherAssignment();
        setupUserInfo();
        loadClubStatistics();
        setupEventListeners();
        setupCleanupListener();
        initializeAttendanceTab();
        initializeLearnersTab();

        startAttendanceRefreshTimer();
    }

    private void initializeAttendanceTab() {
        if (attendanceTab != null) {
            // Add a listener to load content when the tab is selected
            attendanceTab.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue && assignedClubId != null) {
                    loadAttendanceTabContent();
                }
            });
        }
    }

    private void initializeLearnersTab() {
        if (learnersTab != null) {
            learnersTab.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue && assignedClubId != null) {
                    loadLearnersTabContent();
                }
            });
        }
    }

    private void loadLearnersTabContent() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/learners-tab.fxml"));
            Parent learnersContent = loader.load();

            learnersTabController = loader.getController();
            learnersTabController.setClubId(assignedClubId);
            learnersTabController.setClubName(assignedClubName);
            learnersTabController.setYearAndTerm(selectedYear, selectedTerm);

            // Clear existing content and add new content
            VBox tabContent = (VBox) learnersTab.getContent();
            tabContent.getChildren().clear();
            tabContent.getChildren().add(learnersContent);

        } catch (IOException e) {
            logger.error("Error loading learners tab content: {}", e.getMessage(), e);
            showError("Failed to load learners data: " + e.getMessage());
        }
    }


    private void loadAttendanceTabContent() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/attendance-tab.fxml"));
            Parent attendanceContent = loader.load();

            AttendanceTabController attendanceTabController = loader.getController();
            attendanceTabController.setClubId(assignedClubId);
            attendanceTabController.setClubName(assignedClubName);
            attendanceTabController.setYearAndTerm(selectedYear, selectedTerm);

            // Clear existing content and add new content
            VBox tabContent = (VBox) attendanceTab.getContent();
            tabContent.getChildren().clear();
            tabContent.getChildren().add(attendanceContent);

        } catch (IOException e) {
            logger.error("Error loading attendance tab content: {}", e.getMessage(), e);
            showError("Failed to load attendance data: " + e.getMessage());
        }
    }

    private void setupEventListeners() {
        EventBus.subscribe(EventTypes.ENROLLMENT_ADDED, this::handleEnrollmentChanged);
        EventBus.subscribe(EventTypes.ENROLLMENT_WITHDRAWN, this::handleEnrollmentChanged);
        EventBus.subscribe(EventTypes.ENROLLMENT_CHANGED, this::handleEnrollmentChanged);
        EventBus.subscribe(EventTypes.CLUB_STATS_UPDATED, this::handleEnrollmentChanged);
        logger.info("TeacherDashboardController subscribed to enrollment events");
    }

    private void setupCleanupListener() {
        welcomeLabel.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        newWindow.setOnCloseRequest(event -> cleanupEventListeners());
                    }
                });
                if (oldScene != null) {
                    cleanupEventListeners();
                }
            }
        });
    }

    private void cleanupEventListeners() {
        logger.info("Cleaning up event listeners for TeacherDashboardController");
        EventBus.unsubscribe(EventTypes.ENROLLMENT_ADDED);
        EventBus.unsubscribe(EventTypes.ENROLLMENT_WITHDRAWN);
        EventBus.unsubscribe(EventTypes.ENROLLMENT_CHANGED);
        EventBus.unsubscribe(EventTypes.CLUB_STATS_UPDATED);
    }

    private void handleEnrollmentChanged(Object data) {
        logger.info("Enrollment change detected with data: {}, refreshing dashboard", data);
        loadClubStatistics();
    }

    private void setupUserInfo() {
        String username = SessionManager.getCurrentUsername();
        logger.debug("setupUserInfo(): username={}, assignedClubName={}", username, assignedClubName);

        if (username != null && !username.isEmpty()) {
            String capitalizedUsername = username.substring(0, 1).toUpperCase() +
                    username.substring(1).toLowerCase();

            if (assignedClubName != null && !assignedClubName.isEmpty()) {
                String capitalizedClubName = assignedClubName.substring(0, 1).toUpperCase() +
                        assignedClubName.substring(1).toLowerCase();

                if (welcomeLabel != null) {
                    welcomeLabel.setText("Welcome " + capitalizedUsername +
                            ", you have been assigned to " + capitalizedClubName + " club.");
                    logger.debug("Set welcome message with club: {}", capitalizedClubName);
                }
            } else {
                if (welcomeLabel != null) {
                    welcomeLabel.setText("Welcome " + capitalizedUsername +
                            "! Please contact administrator for club assignment.");
                    logger.debug("Set welcome message WITHOUT club (assignedClubName is null/empty)");
                }
            }
        } else {
            if (welcomeLabel != null) {
                welcomeLabel.setText("Welcome!");
                logger.debug("Set generic welcome message (username is null/empty)");
            }
        }
    }

    @FXML
    private void handleEnrollment() {
        try {
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
            logger.error("Error loading enrollment management: {}", e.getMessage(), e);
        }
    }

    private void loadTeacherAssignment() {
        UUID userId = SessionManager.getCurrentUserId();
        logger.info("Loading teacher assignment for user: {}, school: {}", userId, SessionManager.getCurrentSchoolId());

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
                logger.debug("Executing teacher assignment query");
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    assignedClubId = (UUID) rs.getObject("club_id");
                    assignedClubName = rs.getString("club_name");
                    logger.info("FOUND CLUB: {} (ID: {})", assignedClubName, assignedClubId);

                    String capitalizedClubName = assignedClubName.substring(0, 1).toUpperCase() +
                            assignedClubName.substring(1).toLowerCase();
                    if (dashboardTitleLabel != null) {
                        dashboardTitleLabel.setText(capitalizedClubName + " Dashboard");
                        logger.debug("Set dashboard title to: {} Dashboard", capitalizedClubName);
                    }
                } else {
                    logger.warn("NO CLUB FOUND for user: {}", userId);
                    if (dashboardTitleLabel != null) {
                        dashboardTitleLabel.setText("Teacher Dashboard");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in loadTeacherAssignment: {}", e.getMessage(), e);
            if (dashboardTitleLabel != null) {
                dashboardTitleLabel.setText("Teacher Dashboard");
            }
        }
    }

    private void loadClubStatistics() {
        logger.info("Starting loadClubStatistics for club ID: {}, year: {}, term: {}", assignedClubId, selectedYear, selectedTerm);
        if (assignedClubId == null) {
            logger.warn("No club assigned, setting total learners to 0");
            Platform.runLater(() -> {
                if (totalLearnersLabel != null) {
                    totalLearnersLabel.setText("0");
                    logger.debug("Set totalLearnersLabel to 0 (no club assigned)");
                } else {
                    logger.error("totalLearnersLabel is null");
                }
                if (attendanceTodayLabel != null) {
                    attendanceTodayLabel.setText("0/0 (No club)");
                }
            });
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            logger.debug("Database connection established");
            String learnersSql = """
                SELECT COUNT(*) as total_learners 
                FROM club_enrollments 
                WHERE club_id = ? 
                AND is_active = true
                AND academic_year = ?
                AND term_number = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(learnersSql)) {
                ps.setObject(1, assignedClubId);
                ps.setInt(2, selectedYear);
                ps.setInt(3, selectedTerm);
                logger.debug("Executing query with club_id: {}, year: {}, term: {}", assignedClubId, selectedYear, selectedTerm);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    final int totalLearners = rs.getInt("total_learners");
                    logger.info("Found {} total active learners for club {}, year {}, term {}", totalLearners, assignedClubId, selectedYear, selectedTerm);
                    Platform.runLater(() -> {
                        if (totalLearnersLabel != null) {
                            totalLearnersLabel.setText(String.valueOf(totalLearners));
                            logger.debug("Updated total learners label to: {}", totalLearners);
                        }
                    });

                    // Load today's attendance with the total learners count
                    loadTodaysAttendance(totalLearners);
                } else {
                    logger.warn("No results returned for club enrollment count");
                    Platform.runLater(() -> {
                        if (totalLearnersLabel != null) {
                            totalLearnersLabel.setText("0");
                            logger.debug("Set totalLearnersLabel to 0 (no results)");
                        }
                        if (attendanceTodayLabel != null) {
                            attendanceTodayLabel.setText("0/0 (No enrollments)");
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Error loading club statistics: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                if (totalLearnersLabel != null) {
                    totalLearnersLabel.setText("Error");
                }
                if (attendanceTodayLabel != null) {
                    attendanceTodayLabel.setText("Error loading data");
                }
            });
        }
    }

    private void loadTodaysAttendance(int totalLearners) {
        if (assignedClubId == null) {
            Platform.runLater(() -> {
                if (attendanceTodayLabel != null) {
                    attendanceTodayLabel.setText("0/0 (No club)");
                }
            });
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Check if there's an attendance session for today
            String sessionCheckSql = """
            SELECT session_id FROM attendance_sessions 
            WHERE club_id = ? AND session_date = ?
            LIMIT 1
        """;

            UUID todaySessionId = null;
            try (PreparedStatement ps = conn.prepareStatement(sessionCheckSql)) {
                ps.setObject(1, assignedClubId);
                ps.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    todaySessionId = (UUID) rs.getObject("session_id");
                    logger.debug("Found attendance session for today: {}", todaySessionId);
                }
            }

            if (todaySessionId == null) {
                // No session today
                Platform.runLater(() -> {
                    if (attendanceTodayLabel != null) {
                        if (totalLearners > 0) {
                            attendanceTodayLabel.setText("0/" + totalLearners + " (No session today)");
                        } else {
                            attendanceTodayLabel.setText("0/0 (No session today)");
                        }
                    }
                });
                return;
            }

            // Get today's attendance count - ONLY for currently enrolled learners
            String attendanceSql = """
            SELECT 
                COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as present_count,
                COUNT(ar.record_id) as marked_count
            FROM attendance_records ar
            JOIN club_enrollments ce ON ar.learner_id = ce.learner_id
            WHERE ar.session_id = ?
            AND ce.club_id = ?
            AND ce.is_active = true
            AND ce.academic_year = ?
            AND ce.term_number = ?
        """;

            try (PreparedStatement ps = conn.prepareStatement(attendanceSql)) {
                ps.setObject(1, todaySessionId);
                ps.setObject(2, assignedClubId);
                ps.setInt(3, selectedYear);
                ps.setInt(4, selectedTerm);

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int presentCount = rs.getInt("present_count");
                    int markedCount = rs.getInt("marked_count");

                    logger.debug("Attendance stats - Present: {}, Marked: {}, Total: {}",
                            presentCount, markedCount, totalLearners);

                    Platform.runLater(() -> {
                        if (attendanceTodayLabel != null) {
                            if (markedCount == 0) {
                                // Session exists but no attendance marked yet for enrolled learners
                                attendanceTodayLabel.setText("0/" + totalLearners + " (Not taken)");
                            } else if (markedCount < totalLearners) {
                                // Partial attendance taken
                                double percentage = (double) presentCount / totalLearners * 100;
                                attendanceTodayLabel.setText(String.format("%d/%d (%.1f%%) - Partial",
                                        presentCount, totalLearners, percentage));
                            } else {
                                // Full attendance taken
                                double percentage = (double) presentCount / totalLearners * 100;
                                attendanceTodayLabel.setText(String.format("%d/%d (%.1f%%)",
                                        presentCount, totalLearners, percentage));
                            }
                        }
                    });
                } else {
                    // No attendance records found for enrolled learners
                    Platform.runLater(() -> {
                        if (attendanceTodayLabel != null) {
                            attendanceTodayLabel.setText("0/" + totalLearners + " (Not taken)");
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Error loading today's attendance: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                if (attendanceTodayLabel != null) {
                    attendanceTodayLabel.setText("Error loading");
                }
            });
        }
    }

    private void startAttendanceRefreshTimer() {
        // Refresh attendance every 5 minutes to keep it current
        Timeline refreshTimer = new Timeline(
                new KeyFrame(Duration.minutes(5),
                        e -> {
                            if (assignedClubId != null) {
                                logger.debug("Refreshing today's attendance data");
                                // Re-fetch total learners first, then update attendance
                                loadClubStatistics();
                            }
                        })
        );
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private int getCurrentTerm() {
        java.time.Month currentMonth = java.time.LocalDate.now().getMonth();
        if (currentMonth.getValue() <= 4) {
            return 1;
        } else if (currentMonth.getValue() <= 8) {
            return 2;
        } else {
            return 3;
        }
    }

    @FXML
    private void handleTakeAttendance() {
        if (assignedClubId == null) {
            showError("No club assigned. Please contact administrator.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/attendance-management.fxml"));
            Parent root = loader.load();

            AttendanceController controller = loader.getController();
            controller.setClubId(assignedClubId);
            controller.setClubName(assignedClubName);
            controller.setYearAndTerm(selectedYear, selectedTerm);

            Stage stage = new Stage();
            stage.setTitle("Take Attendance - " + assignedClubName);
            stage.setScene(new Scene(root, 1000, 700));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(welcomeLabel.getScene().getWindow());
            stage.showAndWait();

            // Refresh statistics after attendance is taken
            loadClubStatistics();
        } catch (IOException e) {
            showError("Failed to load attendance management: " + e.getMessage());
            logger.error("Error loading attendance management: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleViewLearners() {
        if (assignedClubId == null) {
            showError("No club assigned. Please contact administrator.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/learners-view.fxml"));
            Parent root = loader.load();

            LearnersViewController controller = loader.getController();

            // Set parameters in the correct order
            controller.setClubId(assignedClubId);
            controller.setClubName(assignedClubName);
            controller.setYearAndTerm(selectedYear, selectedTerm); // This should trigger data loading

            Stage stage = new Stage();
            stage.setTitle(String.format("Learners in %s Club - Year %d Term %d",
                    assignedClubName, selectedYear, selectedTerm));
            stage.setScene(new Scene(root, 900, 600));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(welcomeLabel.getScene().getWindow());
            stage.showAndWait();
        } catch (IOException e) {
            showError("Failed to load learners view: " + e.getMessage());
            logger.error("Error loading learners view: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleViewReports() {
        if (assignedClubId == null) {
            showError("No club assigned. Please contact administrator.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/reports-view.fxml"));
            Parent root = loader.load();

            ReportsController controller = loader.getController();
            controller.setClubId(assignedClubId);
            controller.setClubName(assignedClubName);
            controller.setYearAndTerm(selectedYear, selectedTerm); // This will trigger automatic report generation

            Stage stage = new Stage();
            stage.setTitle(String.format("Club Reports - %s (Year %d Term %d)",
                    assignedClubName, selectedYear, selectedTerm));
            stage.setScene(new Scene(root, 1000, 700));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(welcomeLabel.getScene().getWindow());
            stage.showAndWait();
        } catch (IOException e) {
            showError("Failed to load reports view: " + e.getMessage());
            logger.error("Error loading reports view: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.closeSession();
        logger.info("Teacher logout");

        try {
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Club Management System - Login");
            stage.centerOnScreen();
        } catch (IOException e) {
            logger.error("Error loading login screen: {}", e.getMessage(), e);
            Platform.exit();
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static class Learner {
        private final SimpleStringProperty fullName;
        private final SimpleStringProperty admissionNumber;
        private final SimpleStringProperty grade;

        public Learner(String fullName, String admissionNumber, String grade) {
            this.fullName = new SimpleStringProperty(fullName);
            this.admissionNumber = new SimpleStringProperty(admissionNumber);
            this.grade = new SimpleStringProperty(grade);
        }

        public String getFullName() { return fullName.get(); }
        public String getAdmissionNumber() { return admissionNumber.get(); }
        public String getGrade() { return grade.get(); }
    }

    public static class AttendanceRecord {
        private final SimpleStringProperty fullName;
        private final SimpleStringProperty status;

        public AttendanceRecord(String learnerId, String fullName, String status) {
            this.fullName = new SimpleStringProperty(fullName);
            this.status = new SimpleStringProperty(status);
        }

        public String getFullName() { return fullName.get(); }
        public String getStatus() { return status.get(); }
        public void setStatus(String status) { this.status.set(status); }
    }
}