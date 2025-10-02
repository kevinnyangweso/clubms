package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.ClubAttendanceSummary;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

public class AttendanceSummaryController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceSummaryController.class);

    private static final String DAILY_PERIOD = "Daily";
    private static final String MONTHLY_PERIOD = "Monthly";
    private static final String TERM_PERIOD = "Term";

    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> periodComboBox;
    @FXML private ComboBox<String> monthComboBox;
    @FXML private ComboBox<Integer> yearComboBox;
    @FXML private ComboBox<Integer> termComboBox;
    @FXML private Label totalClubsLabel;
    @FXML private Label activeSessionsLabel;
    @FXML private Label totalLearnersLabel;
    @FXML private TableView<ClubAttendanceSummary> attendanceTable;
    @FXML private Label avgAttendanceLabel;
    @FXML private Label resultsCountLabel;
    @FXML private VBox monthContainer;
    @FXML private VBox yearContainer;
    @FXML private VBox termContainer;

    private ObservableList<ClubAttendanceSummary> attendanceData = FXCollections.observableArrayList();
    private Map<String, LocalDate[]> termDates = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            setupPeriodComboBox();
            setupMonthComboBox();
            setupYearComboBox();
            setupTermComboBox();
            setupTermDates();

            datePicker.setValue(LocalDate.now());
            periodComboBox.setValue(DAILY_PERIOD);

            setupEventListeners();
            setupTableStyling(); // Add this line
            loadAttendanceData();

        } catch (Exception e) {
            logger.error("Error initializing AttendanceSummaryController", e);
            showAlert("Initialization Error", "Failed to initialize attendance dashboard: " + e.getMessage());
        }
    }

    private void setupTableStyling() {
        // Set up custom row factory for better selection styling
        attendanceTable.setRowFactory(tv -> new TableRow<ClubAttendanceSummary>() {
            @Override
            protected void updateItem(ClubAttendanceSummary item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                } else {
                    // Clear any existing styles
                    setStyle("");

                    // Apply alternating row colors
                    if (getIndex() % 2 == 0) {
                        setStyle("-fx-background-color: #fafafa; -fx-text-fill: #2c3e50;");
                    } else {
                        setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50;");
                    }

                    // Style for when row is selected
                    if (isSelected()) {
                        setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                    }
                }
            }
        });

        // Apply table styling after the table is rendered
        Platform.runLater(() -> {
            applyTableStyling();
        });
    }

    private void applyTableStyling() {
        // Set table style to ensure readable text
        attendanceTable.setStyle("""
            -fx-control-inner-background: white;
            -fx-background-color: white;
            -fx-border-color: #e9ecef;
            -fx-border-radius: 8;
            -fx-table-cell-border-color: transparent;
        """);

        // Safely apply column header styling
        try {
            // Wait a bit for the table to be fully rendered
            Thread.sleep(100);
            var columnHeader = attendanceTable.lookup(".column-header-background");
            if (columnHeader != null) {
                columnHeader.setStyle("""
                    -fx-background-color: #f8f9fa;
                    -fx-border-color: #e9ecef #e9ecef #e9ecef #e9ecef;
                    -fx-border-width: 0 0 1 0;
                """);
            }
        } catch (Exception e) {
            logger.debug("Could not apply column header styling: {}", e.getMessage());
            // This is not critical, so we can continue
        }
    }

    private void setupEventListeners() {
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && DAILY_PERIOD.equals(periodComboBox.getValue())) {
                loadAttendanceData();
            }
        });

        periodComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateControlVisibility();
            if (newValue != null) {
                loadAttendanceData();
            }
        });

        monthComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && MONTHLY_PERIOD.equals(periodComboBox.getValue())) {
                loadAttendanceData();
            }
        });

        yearComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && MONTHLY_PERIOD.equals(periodComboBox.getValue())) {
                loadAttendanceData();
            }
        });

        termComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && TERM_PERIOD.equals(periodComboBox.getValue())) {
                loadAttendanceData();
            }
        });
    }

    private void setupPeriodComboBox() {
        periodComboBox.setItems(FXCollections.observableArrayList(DAILY_PERIOD, MONTHLY_PERIOD, TERM_PERIOD));
    }

    private void setupMonthComboBox() {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        monthComboBox.setItems(FXCollections.observableArrayList(months));
        monthComboBox.setValue(getCurrentMonth());
    }

    private void setupYearComboBox() {
        int currentYear = LocalDate.now().getYear();
        ObservableList<Integer> years = FXCollections.observableArrayList();
        for (int year = currentYear - 2; year <= currentYear + 1; year++) {
            years.add(year);
        }
        yearComboBox.setItems(years);
        yearComboBox.setValue(currentYear);
    }

    private void setupTermComboBox() {
        termComboBox.setItems(FXCollections.observableArrayList(1, 2, 3));
        termComboBox.setValue(getCurrentTerm());
    }

    private void setupTermDates() {
        int currentYear = LocalDate.now().getYear();
        termDates.put("1_" + currentYear, new LocalDate[]{
                LocalDate.of(currentYear, 1, 15), LocalDate.of(currentYear, 4, 15)
        });
        termDates.put("2_" + currentYear, new LocalDate[]{
                LocalDate.of(currentYear, 5, 15), LocalDate.of(currentYear, 8, 15)
        });
        termDates.put("3_" + currentYear, new LocalDate[]{
                LocalDate.of(currentYear, 9, 15), LocalDate.of(currentYear, 12, 15)
        });
    }

    private void updateControlVisibility() {
        String period = periodComboBox.getValue();
        if (period == null) return;

        boolean isDaily = DAILY_PERIOD.equals(period);
        boolean isMonthly = MONTHLY_PERIOD.equals(period);
        boolean isTerm = TERM_PERIOD.equals(period);

        datePicker.setVisible(isDaily);
        datePicker.setManaged(isDaily);

        monthContainer.setVisible(isMonthly);
        monthContainer.setManaged(isMonthly);

        yearContainer.setVisible(isMonthly);
        yearContainer.setManaged(isMonthly);

        termContainer.setVisible(isTerm);
        termContainer.setManaged(isTerm);
    }

    private void updateStatistics(int totalActiveClubs, int activeSessions, int totalLearners, double avgAttendance) {
        Platform.runLater(() -> {
            totalClubsLabel.setText(String.valueOf(totalActiveClubs));
            activeSessionsLabel.setText(String.valueOf(activeSessions));
            totalLearnersLabel.setText(String.valueOf(totalLearners));
            avgAttendanceLabel.setText(String.format("%.1f%%", avgAttendance));
            resultsCountLabel.setText(String.format("(%d clubs)", totalActiveClubs));
        });
    }

    private String getCurrentMonth() {
        return LocalDate.now().getMonth().toString();
    }

    private int getCurrentTerm() {
        int month = LocalDate.now().getMonthValue();
        if (month >= 1 && month <= 4) return 1;
        if (month >= 5 && month <= 8) return 2;
        return 3;
    }

    @FXML
    private void loadAttendanceData() {
        attendanceData.clear();

        String period = periodComboBox.getValue();
        if (period == null) {
            logger.warn("No period selected for attendance data loading");
            return;
        }

        try (Connection conn = DatabaseConnector.getConnection()) {
            int totalActiveClubs = countActiveClubs(conn);

            AttendanceLoadResult result = switch (period) {
                case DAILY_PERIOD -> loadDailyAttendance(conn, datePicker.getValue());
                case MONTHLY_PERIOD -> loadMonthlyAttendance(conn, monthComboBox.getValue(), yearComboBox.getValue());
                case TERM_PERIOD -> loadTermAttendance(conn, termComboBox.getValue(), yearComboBox.getValue());
                default -> new AttendanceLoadResult(0, 0, 0.0);
            };

            updateStatistics(totalActiveClubs, result.activeSessions(), result.totalLearners(), result.avgAttendance());

            logger.info("Loaded attendance data for {} period: {} clubs, {} sessions, {} learners, {:.1f}% attendance",
                    period, totalActiveClubs, result.activeSessions(), result.totalLearners(), result.avgAttendance());

        } catch (SQLException e) {
            logger.error("Error loading attendance data for period: {}", period, e);
            showAlert("Database Error", "Failed to load attendance data: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error loading attendance data", e);
            showAlert("Error", "An unexpected error occurred: " + e.getMessage());
        }
    }

    private int countActiveClubs(Connection conn) throws SQLException {
        String countQuery = "SELECT COUNT(*) as active_club_count FROM clubs WHERE school_id = ? AND is_active = true";
        try (PreparedStatement stmt = conn.prepareStatement(countQuery)) {
            stmt.setObject(1, SessionManager.getCurrentSchoolId());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("active_club_count") : 0;
        }
    }

    private AttendanceLoadResult loadDailyAttendance(Connection conn, LocalDate date) throws SQLException {
        if (date == null) date = LocalDate.now();

        String query = """
            SELECT 
                c.club_id,
                c.club_name,
                u.full_name as teacher_name,
                COUNT(DISTINCT cl.learner_id) as total_learners,
                COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as daily_present,
                COUNT(CASE WHEN ar.status IN ('absent', 'late') THEN 1 END) as daily_absent,
                CONCAT(cs.start_time, ' - ', cs.end_time) as session_time,
                asess.session_date
            FROM clubs c
            LEFT JOIN club_teachers ct ON c.club_id = ct.club_id AND ct.school_id = ?
            LEFT JOIN users u ON ct.teacher_id = u.user_id
            LEFT JOIN club_learners cl ON c.club_id = cl.club_id AND cl.is_active = true AND cl.school_id = ?
            LEFT JOIN attendance_sessions asess ON c.club_id = asess.club_id AND asess.session_date = ? AND asess.school_id = ?
            LEFT JOIN attendance_records ar ON asess.session_id = ar.session_id AND ar.school_id = ?
            LEFT JOIN club_schedules cs ON c.club_id = cs.club_id AND cs.is_active = true AND cs.school_id = ?
            WHERE c.school_id = ? AND c.is_active = true
            GROUP BY c.club_id, c.club_name, u.full_name, cs.start_time, cs.end_time, asess.session_date
            ORDER BY c.club_name
            """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            UUID schoolId = SessionManager.getCurrentSchoolId();
            stmt.setObject(1, schoolId);
            stmt.setObject(2, schoolId);
            stmt.setObject(3, date);
            stmt.setObject(4, schoolId);
            stmt.setObject(5, schoolId);
            stmt.setObject(6, schoolId);
            stmt.setObject(7, schoolId);

            return processDailyResultSet(stmt, date);
        }
    }

    private AttendanceLoadResult processDailyResultSet(PreparedStatement stmt, LocalDate date) throws SQLException {
        ResultSet rs = stmt.executeQuery();
        int activeSessions = 0;
        int totalLearners = 0;

        while (rs.next()) {
            ClubAttendanceSummary summary = createClubAttendanceSummary(rs);

            int dailyPresent = rs.getInt("daily_present");
            int dailyAbsent = rs.getInt("daily_absent");
            summary.setPresentCount(dailyPresent);
            summary.setAbsentCount(dailyAbsent);
            summary.setDailyRate(calculateRate(dailyPresent, dailyAbsent));

            String clubId = rs.getString("club_id");
            summary.setMonthlyRate(loadMonthlyRate(clubId, date));
            summary.setTermRate(loadTermRate(clubId, date));

            attendanceData.add(summary);

            if (rs.getDate("session_date") != null) {
                activeSessions++;
            }
            totalLearners += summary.getTotalLearners();
        }

        Platform.runLater(() -> {
            attendanceTable.setItems(attendanceData);
        });

        double avgAttendance = calculateOverallAverageAttendance();
        return new AttendanceLoadResult(activeSessions, totalLearners, avgAttendance);
    }

    private String loadMonthlyRate(String clubId, LocalDate date) {
        if (clubId == null) return "0%";

        String query = """
            SELECT 
                COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as present_count,
                COUNT(CASE WHEN ar.status IN ('absent', 'late') THEN 1 END) as absent_count
            FROM attendance_sessions asess 
            JOIN attendance_records ar ON asess.session_id = ar.session_id 
            WHERE asess.club_id = ? 
            AND EXTRACT(MONTH FROM asess.session_date) = ?
            AND EXTRACT(YEAR FROM asess.session_date) = ?
            AND asess.school_id = ?
            """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, UUID.fromString(clubId));
            stmt.setInt(2, date.getMonthValue());
            stmt.setInt(3, date.getYear());
            stmt.setObject(4, SessionManager.getCurrentSchoolId());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int present = rs.getInt("present_count");
                int absent = rs.getInt("absent_count");
                return calculateRate(present, absent);
            }
        } catch (Exception e) {
            logger.warn("Error loading monthly rate for club {}: {}", clubId, e.getMessage());
        }
        return "0%";
    }

    private String loadTermRate(String clubId, LocalDate date) {
        if (clubId == null) return "0%";

        LocalDate[] termRange = getTermDateRange(getCurrentTerm(), date.getYear());
        if (termRange == null) return "0%";

        String query = """
            SELECT 
                COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as present_count,
                COUNT(CASE WHEN ar.status IN ('absent', 'late') THEN 1 END) as absent_count
            FROM attendance_sessions asess 
            JOIN attendance_records ar ON asess.session_id = ar.session_id 
            WHERE asess.club_id = ? 
            AND asess.session_date BETWEEN ? AND ?
            AND asess.school_id = ?
            """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setObject(1, UUID.fromString(clubId));
            stmt.setObject(2, termRange[0]);
            stmt.setObject(3, termRange[1]);
            stmt.setObject(4, SessionManager.getCurrentSchoolId());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int present = rs.getInt("present_count");
                int absent = rs.getInt("absent_count");
                return calculateRate(present, absent);
            }
        } catch (Exception e) {
            logger.warn("Error loading term rate for club {}: {}", clubId, e.getMessage());
        }
        return "0%";
    }

    private AttendanceLoadResult loadMonthlyAttendance(Connection conn, String month, Integer year) throws SQLException {
        if (month == null || year == null) {
            return new AttendanceLoadResult(0, 0, 0.0);
        }

        int monthNumber = getMonthNumber(month);
        YearMonth yearMonth = YearMonth.of(year, monthNumber);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        String query = """
            SELECT 
                c.club_id,
                c.club_name,
                u.full_name as teacher_name,
                COUNT(DISTINCT cl.learner_id) as total_learners,
                COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as monthly_present,
                COUNT(CASE WHEN ar.status IN ('absent', 'late') THEN 1 END) as monthly_absent,
                CONCAT(cs.start_time, ' - ', cs.end_time) as session_time
            FROM clubs c
            LEFT JOIN club_teachers ct ON c.club_id = ct.club_id AND ct.school_id = ?
            LEFT JOIN users u ON ct.teacher_id = u.user_id
            LEFT JOIN club_learners cl ON c.club_id = cl.club_id AND cl.is_active = true AND cl.school_id = ?
            LEFT JOIN attendance_sessions asess ON c.club_id = asess.club_id 
                AND asess.session_date BETWEEN ? AND ? AND asess.school_id = ?
            LEFT JOIN attendance_records ar ON asess.session_id = ar.session_id AND ar.school_id = ?
            LEFT JOIN club_schedules cs ON c.club_id = cs.club_id AND cs.is_active = true AND cs.school_id = ?
            WHERE c.school_id = ? AND c.is_active = true
            GROUP BY c.club_id, c.club_name, u.full_name, cs.start_time, cs.end_time
            ORDER BY c.club_name
            """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            UUID schoolId = SessionManager.getCurrentSchoolId();
            stmt.setObject(1, schoolId);
            stmt.setObject(2, schoolId);
            stmt.setObject(3, startDate);
            stmt.setObject(4, endDate);
            stmt.setObject(5, schoolId);
            stmt.setObject(6, schoolId);
            stmt.setObject(7, schoolId);
            stmt.setObject(8, schoolId);

            return processMonthlyResultSet(stmt);
        }
    }

    private AttendanceLoadResult processMonthlyResultSet(PreparedStatement stmt) throws SQLException {
        ResultSet rs = stmt.executeQuery();
        int totalLearners = 0;

        while (rs.next()) {
            ClubAttendanceSummary summary = createClubAttendanceSummary(rs);

            int monthlyPresent = rs.getInt("monthly_present");
            int monthlyAbsent = rs.getInt("monthly_absent");
            summary.setPresentCount(monthlyPresent);
            summary.setAbsentCount(monthlyAbsent);
            summary.setDailyRate("N/A");
            summary.setMonthlyRate(calculateRate(monthlyPresent, monthlyAbsent));
            summary.setTermRate("N/A");

            attendanceData.add(summary);
            totalLearners += summary.getTotalLearners();
        }

        Platform.runLater(() -> {
            attendanceTable.setItems(attendanceData);
        });

        double avgAttendance = calculateOverallAverageAttendance();
        return new AttendanceLoadResult(0, totalLearners, avgAttendance);
    }

    private AttendanceLoadResult loadTermAttendance(Connection conn, Integer term, Integer year) throws SQLException {
        if (term == null || year == null) {
            return new AttendanceLoadResult(0, 0, 0.0);
        }

        LocalDate[] termRange = getTermDateRange(term, year);
        if (termRange == null) return new AttendanceLoadResult(0, 0, 0.0);

        String query = """
            SELECT 
                c.club_id,
                c.club_name,
                u.full_name as teacher_name,
                COUNT(DISTINCT cl.learner_id) as total_learners,
                COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as term_present,
                COUNT(CASE WHEN ar.status IN ('absent', 'late') THEN 1 END) as term_absent,
                CONCAT(cs.start_time, ' - ', cs.end_time) as session_time
            FROM clubs c
            LEFT JOIN club_teachers ct ON c.club_id = ct.club_id AND ct.school_id = ?
            LEFT JOIN users u ON ct.teacher_id = u.user_id
            LEFT JOIN club_learners cl ON c.club_id = cl.club_id AND cl.is_active = true AND cl.school_id = ?
            LEFT JOIN attendance_sessions asess ON c.club_id = asess.club_id 
                AND asess.session_date BETWEEN ? AND ? AND asess.school_id = ?
            LEFT JOIN attendance_records ar ON asess.session_id = ar.session_id AND ar.school_id = ?
            LEFT JOIN club_schedules cs ON c.club_id = cs.club_id AND cs.is_active = true AND cs.school_id = ?
            WHERE c.school_id = ? AND c.is_active = true
            GROUP BY c.club_id, c.club_name, u.full_name, cs.start_time, cs.end_time
            ORDER BY c.club_name
            """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            UUID schoolId = SessionManager.getCurrentSchoolId();
            stmt.setObject(1, schoolId);
            stmt.setObject(2, schoolId);
            stmt.setObject(3, termRange[0]);
            stmt.setObject(4, termRange[1]);
            stmt.setObject(5, schoolId);
            stmt.setObject(6, schoolId);
            stmt.setObject(7, schoolId);
            stmt.setObject(8, schoolId);

            return processTermResultSet(stmt);
        }
    }

    private AttendanceLoadResult processTermResultSet(PreparedStatement stmt) throws SQLException {
        ResultSet rs = stmt.executeQuery();
        int totalLearners = 0;

        while (rs.next()) {
            ClubAttendanceSummary summary = createClubAttendanceSummary(rs);

            int termPresent = rs.getInt("term_present");
            int termAbsent = rs.getInt("term_absent");
            summary.setPresentCount(termPresent);
            summary.setAbsentCount(termAbsent);
            summary.setDailyRate("N/A");
            summary.setMonthlyRate("N/A");
            summary.setTermRate(calculateRate(termPresent, termAbsent));

            attendanceData.add(summary);
            totalLearners += summary.getTotalLearners();
        }

        Platform.runLater(() -> {
            attendanceTable.setItems(attendanceData);
        });

        double avgAttendance = calculateOverallAverageAttendance();
        return new AttendanceLoadResult(0, totalLearners, avgAttendance);
    }

    private ClubAttendanceSummary createClubAttendanceSummary(ResultSet rs) throws SQLException {
        ClubAttendanceSummary summary = new ClubAttendanceSummary();
        summary.setClubName(rs.getString("club_name"));
        summary.setTeacherName(rs.getString("teacher_name"));
        summary.setTotalLearners(rs.getInt("total_learners"));
        summary.setSessionTime(rs.getString("session_time"));
        return summary;
    }

    private double calculateOverallAverageAttendance() {
        if (attendanceData.isEmpty()) return 0.0;

        double totalRate = 0;
        int clubsWithAttendance = 0;

        for (ClubAttendanceSummary club : attendanceData) {
            int present = club.getPresentCount();
            int absent = club.getAbsentCount();
            int totalRecords = present + absent;

            if (totalRecords > 0) {
                double clubRate = (double) present / totalRecords * 100;
                totalRate += clubRate;
                clubsWithAttendance++;
            }
        }

        return clubsWithAttendance > 0 ? totalRate / clubsWithAttendance : 0.0;
    }

    private String calculateRate(int present, int absent) {
        int total = present + absent;
        return total > 0 ? String.format("%.1f%%", (double) present / total * 100) : "0%";
    }

    private int getMonthNumber(String monthName) {
        try {
            return java.time.Month.valueOf(monthName.toUpperCase()).getValue();
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid month name: {}", monthName);
            return 1; // Default to January
        }
    }

    private LocalDate[] getTermDateRange(int term, int year) {
        String key = term + "_" + year;
        return termDates.getOrDefault(key, new LocalDate[]{
                LocalDate.of(year, (term-1)*4 + 1, 1),
                LocalDate.of(year, term*4, 30)
        });
    }

    @FXML
    private void loadTodayAttendance() {
        datePicker.setValue(LocalDate.now());
        periodComboBox.setValue(DAILY_PERIOD);
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Record to hold attendance load results
    private record AttendanceLoadResult(int activeSessions, int totalLearners, double avgAttendance) {}
}