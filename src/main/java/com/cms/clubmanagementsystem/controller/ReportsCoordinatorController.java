package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.Report;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

public class ReportsCoordinatorController implements Initializable {

    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private ComboBox<String> timePeriodCombo;
    @FXML private ComboBox<String> clubCombo;
    @FXML private ComboBox<String> formatCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private HBox dateRangeBox;

    @FXML private VBox analyticsSection;
    @FXML private Label overallAttendanceLabel;
    @FXML private Label attendanceTrendLabel;
    @FXML private Label activeClubsLabel;
    @FXML private Label enrolledLearnersLabel;

    // Chart containers instead of direct chart references
    @FXML private VBox attendanceTrendChartContainer;
    @FXML private VBox clubPerformanceChartContainer;
    @FXML private VBox sessionDayChartContainer;
    @FXML private VBox teacherEngagementChartContainer;

    @FXML private TableView<Report> reportsTable;

    private ObservableList<Report> recentReports = FXCollections.observableArrayList();
    private Map<String, UUID> clubMap = new HashMap<>(); // Changed to UUID

    // Chart instances
    private LineChart<String, Number> attendanceTrendChart;
    private BarChart<String, Number> clubPerformanceChart;
    private PieChart sessionDayChart;
    private BarChart<String, Number> teacherEngagementChart;

    private Map<String, String> reportFilePaths = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComboBoxes();
        setupTableColumns();
        loadClubData();
        loadRecentReports();

        // Add listeners
        timePeriodCombo.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> handleTimePeriodChange(newValue)
        );
    }

    private void setupComboBoxes() {
        // Report types
        reportTypeCombo.setItems(FXCollections.observableArrayList(
                "Attendance Summary",
                "Club Performance",
                "Learner Enrollment",
                "Teacher Engagement",
                "Session Frequency",
                "Comprehensive Report"
        ));

        // Time periods
        timePeriodCombo.setItems(FXCollections.observableArrayList(
                "Last 7 days",
                "Last 30 days",
                "Last 3 months",
                "Current Term",
                "Custom Range"
        ));

        // Formats
        formatCombo.setItems(FXCollections.observableArrayList("PDF", "Excel"));
        formatCombo.getSelectionModel().selectFirst();
    }

    private void setupTableColumns() {
        // Setup button column for actions
        TableColumn<Report, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(150);

        Callback<TableColumn<Report, Void>, TableCell<Report, Void>> cellFactory =
                new Callback<>() {
                    @Override
                    public TableCell<Report, Void> call(final TableColumn<Report, Void> param) {
                        return new TableCell<>() {
                            private final Button downloadBtn = new Button("Download");
                            private final Button deleteBtn = new Button("Delete");
                            private final HBox pane = new HBox(downloadBtn, deleteBtn);

                            {
                                downloadBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10px;");
                                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
                                pane.setSpacing(5);

                                downloadBtn.setOnAction(event -> {
                                    Report report = getTableView().getItems().get(getIndex());
                                    downloadReport(report);
                                });

                                deleteBtn.setOnAction(event -> {
                                    Report report = getTableView().getItems().get(getIndex());
                                    deleteReport(report);
                                });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty) {
                                    setGraphic(null);
                                } else {
                                    setGraphic(pane);
                                }
                            }
                        };
                    }

                };
        actionCol.setCellFactory(cellFactory);

        // Add the action column if not already in table
        if (reportsTable.getColumns().size() < 5) {
            reportsTable.getColumns().add(actionCol);
        }
    }

    private void initializeCharts() {
        // Initialize charts only once
        if (attendanceTrendChart == null) {
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel("Date");
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel("Attendance Rate (%)");
            attendanceTrendChart = new LineChart<>(xAxis, yAxis);
            attendanceTrendChart.setTitle("Attendance Trend");
            attendanceTrendChart.setPrefSize(400, 250);
            attendanceTrendChartContainer.getChildren().add(attendanceTrendChart);
        }

        if (clubPerformanceChart == null) {
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel("Clubs");
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel("Attendance Rate (%)");
            clubPerformanceChart = new BarChart<>(xAxis, yAxis);
            clubPerformanceChart.setTitle("Club Performance");
            clubPerformanceChart.setPrefSize(400, 250);
            clubPerformanceChartContainer.getChildren().add(clubPerformanceChart);
        }

        if (sessionDayChart == null) {
            sessionDayChart = new PieChart();
            sessionDayChart.setTitle("Session Frequency by Day");
            sessionDayChart.setPrefSize(400, 250);
            sessionDayChartContainer.getChildren().add(sessionDayChart);
        }

        if (teacherEngagementChart == null) {
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel("Teachers");
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel("Sessions Conducted");
            teacherEngagementChart = new BarChart<>(xAxis, yAxis);
            teacherEngagementChart.setTitle("Teacher Engagement");
            teacherEngagementChart.setPrefSize(400, 250);
            teacherEngagementChartContainer.getChildren().add(teacherEngagementChart);
        }
    }

    private void loadClubData() {
        String query = "SELECT club_id, club_name FROM clubs WHERE school_id = ? AND is_active = true ORDER BY club_name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            ObservableList<String> clubs = FXCollections.observableArrayList();
            clubs.add("All Clubs");

            while (rs.next()) {
                UUID clubId = rs.getObject("club_id", UUID.class); // Get as UUID
                String clubName = rs.getString("club_name");
                clubs.add(clubName);
                clubMap.put(clubName, clubId);
            }

            clubCombo.setItems(clubs);
            clubCombo.getSelectionModel().selectFirst();

        } catch (SQLException e) {
            showAlert("Error", "Failed to load club data: " + e.getMessage());
        }
    }

    private void loadRecentReports() {
        String query = "SELECT report_name, report_type, generated_at, format " +
                "FROM reports WHERE school_id = ? ORDER BY generated_at DESC LIMIT 10";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();

            recentReports.clear();
            while (rs.next()) {
                Report report = new Report(
                        rs.getString("report_name"),
                        rs.getString("report_type"),
                        rs.getTimestamp("generated_at").toLocalDateTime(),
                        rs.getString("format")
                );
                recentReports.add(report);
            }

            reportsTable.setItems(recentReports);

        } catch (SQLException e) {
            System.err.println("Error loading recent reports: " + e.getMessage());
        }
    }

    @FXML
    private void generateReport() {
        String reportType = reportTypeCombo.getValue();
        String timePeriod = timePeriodCombo.getValue();
        String club = clubCombo.getValue();
        String format = formatCombo.getValue();

        if (reportType == null || timePeriod == null) {
            showAlert("Validation Error", "Please select report type and time period.");
            return;
        }

        try {
            // Generate report based on type
            switch (reportType) {
                case "Attendance Summary":
                    generateAttendanceReport(timePeriod, club, format);
                    break;
                case "Club Performance":
                    generateClubPerformanceReport(timePeriod, club, format);
                    break;
                case "Learner Enrollment":
                    generateEnrollmentReport(timePeriod, club, format);
                    break;
                case "Teacher Engagement":
                    generateTeacherReport(timePeriod, club, format);
                    break;
                case "Session Frequency":
                    generateSessionReport(timePeriod, club, format);
                    break;
                case "Comprehensive Report":
                    generateComprehensiveReport(timePeriod, club, format);
                    break;
            }

            // Save report record
            saveReportRecord(reportType, timePeriod, club, format);
            loadRecentReports();

            showAlert("Success", "Report generated successfully!");

        } catch (Exception e) {
            showAlert("Error", "Failed to generate report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void generateAttendanceReport(String timePeriod, String club, String format) {
        // Implementation for attendance report
        String query = buildAttendanceQuery(timePeriod, club);

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            setQueryParameters(stmt, timePeriod, club);
            ResultSet rs = stmt.executeQuery();

            // Process results and generate report file
            System.out.println("Generating Attendance Report...");

            // For now, just display results in console
            while (rs.next()) {
                System.out.printf("Club: %s, Attendance Rate: %.2f%%%n",
                        rs.getString("club_name"), rs.getDouble("attendance_rate"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    private String buildAttendanceQuery(String timePeriod, String club) {
        StringBuilder query = new StringBuilder(
                "SELECT c.club_name, " +
                        "COUNT(DISTINCT ar.record_id) as total_records, " +
                        "SUM(CASE WHEN ar.status = 'present' THEN 1 ELSE 0 END) as present_count, " +
                        "ROUND(SUM(CASE WHEN ar.status = 'present' THEN 1 ELSE 0 END) * 100.0 / COUNT(ar.record_id), 2) as attendance_rate " +
                        "FROM attendance_records ar " +
                        "JOIN attendance_sessions s ON ar.session_id = s.session_id " +
                        "JOIN clubs c ON s.club_id = c.club_id " +
                        "WHERE c.school_id = ? AND s.session_date BETWEEN ? AND ? "
        );

        if (club != null && !club.equals("All Clubs")) {
            query.append("AND c.club_id = ? ");
        }

        query.append("GROUP BY c.club_name ORDER BY attendance_rate DESC");

        return query.toString();
    }

    private void setQueryParameters(PreparedStatement stmt, String timePeriod, String club) throws SQLException {
        LocalDate[] dateRange = calculateDateRange(timePeriod);
        int paramIndex = 1;

        // Set school_id (UUID)
        stmt.setObject(paramIndex++, SessionManager.getCurrentSchoolId(), Types.OTHER);

        // Set date range
        stmt.setDate(paramIndex++, Date.valueOf(dateRange[0]));
        stmt.setDate(paramIndex++, Date.valueOf(dateRange[1]));

        // Set club_id if specified (UUID)
        if (club != null && !club.equals("All Clubs")) {
            UUID clubId = clubMap.get(club);
            if (clubId != null) {
                stmt.setObject(paramIndex, clubId, Types.OTHER);
            }
        }
    }

    private LocalDate[] calculateDateRange(String timePeriod) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        switch (timePeriod) {
            case "Last 7 days":
                startDate = endDate.minusDays(7);
                break;
            case "Last 30 days":
                startDate = endDate.minusDays(30);
                break;
            case "Last 3 months":
                startDate = endDate.minusMonths(3);
                break;
            case "Custom Range":
                startDate = startDatePicker.getValue();
                endDate = endDatePicker.getValue();
                if (startDate == null || endDate == null) {
                    throw new IllegalArgumentException("Please select both start and end dates for custom range.");
                }
                break;
            default:
                startDate = endDate.minusMonths(1);
        }

        return new LocalDate[]{startDate, endDate};
    }

    private void generateClubPerformanceReport(String timePeriod, String club, String format) {
        System.out.println("Generating Club Performance Report...");
    }

    private void generateEnrollmentReport(String timePeriod, String club, String format) {
        System.out.println("Generating Enrollment Report...");
    }

    private void generateTeacherReport(String timePeriod, String club, String format) {
        System.out.println("Generating Teacher Engagement Report...");
    }

    private void generateSessionReport(String timePeriod, String club, String format) {
        System.out.println("Generating Session Frequency Report...");
    }

    private void generateComprehensiveReport(String timePeriod, String club, String format) {
        System.out.println("Generating Comprehensive Report...");
    }

    private void saveReportRecord(String reportType, String timePeriod, String club, String format) {
        String query = "INSERT INTO reports (report_name, report_type, format, school_id, generated_by, start_date, end_date) " +
                "VALUES (?, ?::report_type, ?::report_format, ?, ?, ?, ?)";

        LocalDate[] dateRange = calculateDateRange(timePeriod);
        String reportName = reportType + " - " + timePeriod +
                (club != null && !club.equals("All Clubs") ? " - " + club : "");

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, reportName);
            stmt.setString(2, mapReportType(reportType));
            stmt.setString(3, format.toLowerCase()); // Changed to lowercase
            stmt.setObject(4, SessionManager.getCurrentSchoolId(), Types.OTHER);
            stmt.setObject(5, SessionManager.getCurrentUserId(), Types.OTHER);
            stmt.setDate(6, Date.valueOf(dateRange[0]));
            stmt.setDate(7, Date.valueOf(dateRange[1]));

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error saving report record: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String mapReportType(String reportType) {
        switch (reportType) {
            case "Attendance Summary": return "weekly";
            case "Club Performance": return "monthly";
            case "Learner Enrollment": return "termly";
            case "Teacher Engagement": return "monthly";
            case "Session Frequency": return "weekly";
            case "Comprehensive Report": return "annual";
            default: return "monthly";
        }
    }

    @FXML
    private void showAnalytics() {
        analyticsSection.setVisible(true);
        initializeCharts(); // Initialize charts when analytics section is shown
        loadAnalyticsData();
    }

    private void loadAnalyticsData() {
        loadOverallMetrics();
        loadAttendanceTrendChart();
        loadClubPerformanceChart();
        loadSessionDayChart();
        loadTeacherEngagementChart();
    }

    private void loadOverallMetrics() {
        String query =
                "SELECT " +
                        "(SELECT COUNT(*) FROM clubs WHERE school_id = ? AND is_active = true) as active_clubs, " +
                        "(SELECT COUNT(*) FROM club_enrollments WHERE school_id = ? AND is_active = true) as enrolled_learners, " +
                        "(SELECT ROUND(AVG(CASE WHEN ar.status = 'present' THEN 1 ELSE 0 END) * 100, 2) " +
                        " FROM attendance_records ar " +
                        " JOIN attendance_sessions s ON ar.session_id = s.session_id " +
                        " WHERE s.school_id = ? AND s.session_date >= CURRENT_DATE - INTERVAL '30 days') as attendance_rate";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            UUID schoolId = SessionManager.getCurrentSchoolId();
            stmt.setObject(1, schoolId, Types.OTHER);
            stmt.setObject(2, schoolId, Types.OTHER);
            stmt.setObject(3, schoolId, Types.OTHER);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                activeClubsLabel.setText(String.valueOf(rs.getInt("active_clubs")));
                enrolledLearnersLabel.setText(String.valueOf(rs.getInt("enrolled_learners")));

                double attendanceRate = rs.getDouble("attendance_rate");
                if (!rs.wasNull()) {
                    overallAttendanceLabel.setText(String.format("%.1f%%", attendanceRate));

                    // Simple trend calculation
                    double previousRate = calculatePreviousPeriodAttendance();
                    double trend = attendanceRate - previousRate;
                    String trendText = String.format("%s %.1f%%",
                            trend >= 0 ? "↑" : "↓", Math.abs(trend));
                    attendanceTrendLabel.setText(trendText);
                    attendanceTrendLabel.setStyle(trend >= 0 ?
                            "-fx-text-fill: #27ae60;" : "-fx-text-fill: #e74c3c;");
                } else {
                    overallAttendanceLabel.setText("N/A");
                    attendanceTrendLabel.setText("No data");
                }
            }

        } catch (SQLException e) {
            System.err.println("Error loading overall metrics: " + e.getMessage());
        }
    }

    private double calculatePreviousPeriodAttendance() {
        // Simplified implementation
        return 75.0;
    }

    private void loadAttendanceTrendChart() {
        if (attendanceTrendChart == null) return;

        attendanceTrendChart.getData().clear();

        String query =
                "SELECT DATE(s.session_date) as session_day, " +
                        "ROUND(AVG(CASE WHEN ar.status = 'present' THEN 1 ELSE 0 END) * 100, 2) as rate " +
                        "FROM attendance_sessions s " +
                        "LEFT JOIN attendance_records ar ON s.session_id = ar.session_id " +
                        "WHERE s.school_id = ? AND s.session_date >= CURRENT_DATE - INTERVAL '30 days' " +
                        "GROUP BY session_day ORDER BY session_day";

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Attendance Rate");

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId(), Types.OTHER);
            ResultSet rs = stmt.executeQuery();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");

            while (rs.next()) {
                LocalDate date = rs.getDate("session_day").toLocalDate();
                double rate = rs.getDouble("rate");

                if (!rs.wasNull()) {
                    series.getData().add(new XYChart.Data<>(
                            date.format(formatter), rate
                    ));
                }
            }

            attendanceTrendChart.getData().add(series);

        } catch (SQLException e) {
            System.err.println("Error loading attendance trend: " + e.getMessage());
        }
    }

    private void loadClubPerformanceChart() {
        if (clubPerformanceChart == null) return;

        clubPerformanceChart.getData().clear();

        String query =
                "SELECT c.club_name, " +
                        "ROUND(AVG(CASE WHEN ar.status = 'present' THEN 1 ELSE 0 END) * 100, 2) as attendance_rate " +
                        "FROM clubs c " +
                        "LEFT JOIN attendance_sessions s ON c.club_id = s.club_id AND s.session_date >= CURRENT_DATE - INTERVAL '30 days' " +
                        "LEFT JOIN attendance_records ar ON s.session_id = ar.session_id " +
                        "WHERE c.school_id = ? AND c.is_active = true " +
                        "GROUP BY c.club_id, c.club_name " +
                        "ORDER BY attendance_rate DESC NULLS LAST " +
                        "LIMIT 10";

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Attendance Rate");

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId(), Types.OTHER);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String clubName = rs.getString("club_name");
                double rate = rs.getDouble("attendance_rate");

                if (!rs.wasNull()) {
                    series.getData().add(new XYChart.Data<>(clubName, rate));
                }
            }

            clubPerformanceChart.getData().add(series);

        } catch (SQLException e) {
            System.err.println("Error loading club performance: " + e.getMessage());
        }
    }

    private void loadSessionDayChart() {
        if (sessionDayChart == null) return;

        sessionDayChart.getData().clear();

        String query =
                "SELECT cs.meeting_day, COUNT(*) as session_count " +
                        "FROM club_schedules cs " +
                        "JOIN clubs c ON cs.club_id = c.club_id " +
                        "WHERE c.school_id = ? AND cs.is_active = true " +
                        "GROUP BY cs.meeting_day " +
                        "ORDER BY session_count DESC";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId(), Types.OTHER);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String day = rs.getString("meeting_day");
                int count = rs.getInt("session_count");

                sessionDayChart.getData().add(new PieChart.Data(day + " (" + count + ")", count));
            }

        } catch (SQLException e) {
            System.err.println("Error loading session day chart: " + e.getMessage());
        }
    }

    private void loadTeacherEngagementChart() {
        if (teacherEngagementChart == null) return;

        teacherEngagementChart.getData().clear();

        String query =
                "SELECT u.full_name, COUNT(DISTINCT s.session_id) as sessions_conducted " +
                        "FROM users u " +
                        "JOIN club_teachers ct ON u.user_id = ct.teacher_id " +
                        "LEFT JOIN attendance_sessions s ON ct.club_id = s.club_id AND s.session_date >= CURRENT_DATE - INTERVAL '30 days' " +
                        "WHERE u.school_id = ? AND u.role = 'teacher' AND u.is_active = true " +
                        "GROUP BY u.user_id, u.full_name " +
                        "ORDER BY sessions_conducted DESC " +
                        "LIMIT 8";

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Sessions Conducted");

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, SessionManager.getCurrentSchoolId(), Types.OTHER);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String teacherName = rs.getString("full_name");
                int sessions = rs.getInt("sessions_conducted");

                series.getData().add(new XYChart.Data<>(teacherName, sessions));
            }

            teacherEngagementChart.getData().add(series);

        } catch (SQLException e) {
            System.err.println("Error loading teacher engagement: " + e.getMessage());
        }
    }

    @FXML
    private void refreshData() {
        loadAnalyticsData();
        loadRecentReports();
        showAlert("Refreshed", "Data has been refreshed successfully.");
    }

    private void handleTimePeriodChange(String newValue) {
        if ("Custom Range".equals(newValue)) {
            dateRangeBox.setVisible(true);
        } else {
            dateRangeBox.setVisible(false);
        }
    }

    private void downloadReport(Report report) {
        showAlert("Download", "Downloading report: " + report.getName());
        // Implementation for downloading the report file
    }

    private void deleteReport(Report report) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Report");
        confirm.setContentText("Are you sure you want to delete the report: " + report.getName() + "?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Delete from database
                String query = "DELETE FROM reports WHERE report_name = ? AND school_id = ?";
                try (Connection conn = DatabaseConnector.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(query)) {

                    stmt.setString(1, report.getName());
                    stmt.setObject(2, SessionManager.getCurrentSchoolId(), Types.OTHER);
                    stmt.executeUpdate();

                    loadRecentReports();
                    showAlert("Success", "Report deleted successfully!");

                } catch (SQLException e) {
                    showAlert("Error", "Failed to delete report: " + e.getMessage());
                }
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}