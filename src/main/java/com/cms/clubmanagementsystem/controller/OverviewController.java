package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.StatsService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.TenantContext;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.UUID;

public class OverviewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(OverviewController.class);

    @FXML private Label totalClubsLabel;
    @FXML private Label totalMembersLabel;
    @FXML private Label totalTeachersLabel;
    @FXML private Label totalLearnersLabel;
    @FXML private Label todayAttendanceLabel;
    @FXML private Label attendanceThisWeekLabel;
    @FXML private Label attendancePercentageLabel;
    @FXML private Label weeklyAttendanceRateLabel;
    @FXML private Label participationRateLabel;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label systemStatusLabel;
    @FXML private ScrollPane scrollPane;

    private UUID currentSchoolId;
    private Timeline refreshTimeline;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private boolean isFirstLoad = true;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("=== OVERVIEW CONTROLLER INITIALIZATION ===");

        currentSchoolId = SessionManager.getCurrentSchoolId();

        if (currentSchoolId == null) {
            logger.error("❌ CURRENT SCHOOL ID IS NULL - Cannot load stats");
            setSystemStatus("🔴 System Error: No School ID", "status-offline");
        } else {
            logger.info("✅ School ID found: {}", currentSchoolId);
            setupAutoRefresh();
            setupTooltips();

            // Initial load with fade-in animation
            Platform.runLater(() -> {
                if (isFirstLoad) {
                    fadeInContent();
                    isFirstLoad = false;
                }
                loadStats();
            });
        }
    }

    private void setupTooltips() {
        setTooltip(totalClubsLabel, "Number of active clubs currently offered by the school");
        setTooltip(totalMembersLabel, "Total number of learners currently enrolled in clubs");
        setTooltip(totalTeachersLabel, "Number of teachers actively involved in club activities");
        setTooltip(totalLearnersLabel, "Total number of learners in the school");
        setTooltip(todayAttendanceLabel, "Number of learners who attended club sessions today");
        setTooltip(attendanceThisWeekLabel, "Total attendance across all club sessions this week");
        setTooltip(attendancePercentageLabel, "Percentage of expected attendees who participated in today's club sessions");
        setTooltip(weeklyAttendanceRateLabel, "Average attendance rate across all club sessions this week");
        setTooltip(participationRateLabel, "Percentage of school learners participating in club activities");
    }

    private void setTooltip(Label label, String text) {
        if (label != null) {
            Tooltip tooltip = new Tooltip(text);
            tooltip.setShowDelay(Duration.millis(300));
            label.setTooltip(tooltip);
        }
    }

    private void fadeInContent() {
        FadeTransition fadeTransition = new FadeTransition(Duration.seconds(0.8), scrollPane.getContent());
        fadeTransition.setFromValue(0);
        fadeTransition.setToValue(1);
        fadeTransition.play();
    }

    private void setupAutoRefresh() {
        // Refresh every 60 seconds
        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(60), event -> loadStats())
        );
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        setSystemStatus("🟢 System status: Online", "status-online");
    }

    public void loadStats() {
        // First check if database is healthy
        if (!DatabaseConnector.isHealthy()) {
            Platform.runLater(() -> {
                setSystemStatus("🔴 Database Offline - Check Connection", "status-offline");
                setErrorStateOnLabels();
            });
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            UUID currentSchoolId = SessionManager.getCurrentSchoolId();
            UUID currentUserId = SessionManager.getCurrentUserId();

            if (currentSchoolId != null && currentUserId != null) {
                TenantContext.setTenant(conn, currentSchoolId.toString(), currentUserId.toString());
            }

            StatsService statsService = new StatsService();
            StatsService.DashboardStats stats = statsService.getDashboardStats(conn, currentSchoolId);
            double attendancePercentage = statsService.getTodayAttendancePercentage(conn, currentSchoolId);

            Platform.runLater(() -> {
                updateStatsLabels(stats, attendancePercentage);
                updateTimestamp();
                animateStatUpdate();
                setSystemStatus("🟢 System status: Online", "status-online");

                if (scrollPane != null) {
                    scrollPane.requestLayout();
                }
            });

        } catch (SQLException e) {
            handleDatabaseError(e);
        } catch (Exception e) {
            logger.error("Unexpected error in loadStats", e);
            Platform.runLater(() -> {
                setSystemStatus("⚠️ System Error: Unexpected Issue", "status-offline");
                setErrorStateOnLabels();
            });
        }
    }

    private void updateStatsLabels(StatsService.DashboardStats stats, double attendancePercentage) {
        setLabelTextSafely(totalClubsLabel, formatNumber(stats.getTotalClubs()));
        setLabelTextSafely(totalMembersLabel, formatNumber(stats.getTotalMembers()));
        setLabelTextSafely(totalTeachersLabel, formatNumber(stats.getTotalTeachers()));
        setLabelTextSafely(totalLearnersLabel, formatNumber(stats.getTotalLearners()));
        setLabelTextSafely(todayAttendanceLabel, formatNumber(stats.getTodayAttendance()));
        setLabelTextSafely(attendanceThisWeekLabel, formatNumber(stats.getAttendanceThisWeek()));
        setLabelTextSafely(attendancePercentageLabel, String.format("%.1f%%", attendancePercentage));
        setLabelTextSafely(weeklyAttendanceRateLabel, String.format("%.1f%%", stats.getWeeklyAttendanceRate()));
        setLabelTextSafely(participationRateLabel, String.format("%.1f%%", stats.getProgramParticipationRate()));
    }

    private String formatNumber(int number) {
        if (number >= 1000) {
            return String.format("%.1fk", number / 1000.0);
        }
        return String.valueOf(number);
    }

    private void setLabelTextSafely(Label label, String text) {
        if (label != null) {
            label.setText(text);
        }
    }

    private void updateTimestamp() {
        if (lastUpdatedLabel != null) {
            lastUpdatedLabel.setText("Last updated: " + timeFormat.format(new Date()));
        }
    }

    private void setSystemStatus(String text, String styleClass) {
        if (systemStatusLabel != null) {
            systemStatusLabel.setText(text);
            systemStatusLabel.getStyleClass().removeAll("status-online", "status-offline", "status-error");
            systemStatusLabel.getStyleClass().add(styleClass);
        }
    }

    private void handleDatabaseError(SQLException e) {
        logger.error("Database error in loadStats", e);
        Platform.runLater(() -> {
            setSystemStatus("🔴 System status: Offline - Database Error", "status-offline");
            setErrorStateOnLabels();
        });
    }

    private void setErrorStateOnLabels() {
        Label[] statLabels = {
                totalClubsLabel, totalMembersLabel, totalTeachersLabel, totalLearnersLabel,
                todayAttendanceLabel, attendanceThisWeekLabel, attendancePercentageLabel,
                weeklyAttendanceRateLabel, participationRateLabel
        };

        for (Label label : statLabels) {
            if (label != null) {
                label.setText("N/A");
                label.getStyleClass().add("status-error");
            }
        }
    }

    private void animateStatUpdate() {
        Label[] statLabels = {
                totalClubsLabel, totalMembersLabel, totalTeachersLabel, totalLearnersLabel,
                todayAttendanceLabel, attendanceThisWeekLabel, attendancePercentageLabel,
                weeklyAttendanceRateLabel, participationRateLabel
        };

        for (Label label : statLabels) {
            if (label != null) {
                label.getStyleClass().add("stat-update-animation");

                PauseTransition pause = new PauseTransition(Duration.millis(300));
                pause.setOnFinished(event -> {
                    if (label != null) {
                        label.getStyleClass().remove("stat-update-animation");
                    }
                });
                pause.play();
            }
        }
    }

    @FXML
    private void handleManualRefresh() {
        // Add refresh animation
        if (scrollPane != null && scrollPane.getContent() != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), scrollPane.getContent());
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0.7);
            fadeOut.setOnFinished(e -> loadStats());
            fadeOut.play();
        } else {
            loadStats();
        }
    }

    public void stopAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }
}