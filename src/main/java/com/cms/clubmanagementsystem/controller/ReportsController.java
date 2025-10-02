package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.ResourceBundle;
import java.util.UUID;

public class ReportsController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ReportsController.class);

    @FXML private TableView<AttendanceReport> reportTable;
    @FXML private TableColumn<AttendanceReport, String> fullNameColumn;
    @FXML private TableColumn<AttendanceReport, String> admissionNumberColumn;
    @FXML private TableColumn<AttendanceReport, String> gradeColumn;
    @FXML private TableColumn<AttendanceReport, Integer> sessionsAttendedColumn;
    @FXML private TableColumn<AttendanceReport, Integer> totalSessionsColumn;
    @FXML private TableColumn<AttendanceReport, String> attendanceRateColumn;
    @FXML private Label reportTitleLabel;

    private UUID clubId;
    private String clubName;
    private int selectedYear;
    private int selectedTerm;
    private ObservableList<AttendanceReport> reportList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing ReportsController");

        // Initialize table columns
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        admissionNumberColumn.setCellValueFactory(new PropertyValueFactory<>("admissionNumber"));
        gradeColumn.setCellValueFactory(new PropertyValueFactory<>("grade"));
        sessionsAttendedColumn.setCellValueFactory(new PropertyValueFactory<>("sessionsAttended"));
        totalSessionsColumn.setCellValueFactory(new PropertyValueFactory<>("totalSessions"));
        attendanceRateColumn.setCellValueFactory(new PropertyValueFactory<>("attendanceRate"));

        reportTable.setItems(reportList);

        logger.info("ReportsController initialized successfully");
    }

    public void setClubId(UUID clubId) {
        this.clubId = clubId;
        logger.info("Club ID set to: {}", clubId);
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
        logger.info("Club name set to: {}", clubName);
        updateReportTitle();
    }

    public void setYearAndTerm(int year, int term) {
        this.selectedYear = year;
        this.selectedTerm = term;
        logger.info("Year set to: {}, Term set to: {}", year, term);
        updateReportTitle();
        generateReport(); // Generate report automatically when year/term are set
    }

    private void updateReportTitle() {
        if (reportTitleLabel != null && clubName != null) {
            reportTitleLabel.setText(String.format("Attendance Report for %s Club - Year %d Term %d",
                    clubName, selectedYear, selectedTerm));
        }
    }

    private void generateReport() {
        logger.info("Generating attendance report for club: {}, year: {}, term: {}",
                clubId, selectedYear, selectedTerm);

        if (clubId == null) {
            showError("No club selected. Please contact administrator.");
            return;
        }

        updateReportTitle();
        reportList.clear();

        try (Connection conn = DatabaseConnector.getConnection()) {
            // Query that only counts sessions on scheduled days
            String sql = """
            SELECT 
                l.learner_id,
                l.full_name,
                l.admission_number,
                g.grade_name,
                -- Count only sessions that occur on scheduled days
                COUNT(DISTINCT 
                    CASE WHEN cs.meeting_day IS NOT NULL THEN s.session_id END
                ) as total_sessions,
                -- Count attended sessions only on scheduled days
                COUNT(DISTINCT 
                    CASE WHEN ar.status = 'present' AND cs.meeting_day IS NOT NULL 
                         THEN s.session_id END
                ) as sessions_attended
            FROM 
                club_enrollments ce
            JOIN 
                learners l ON ce.learner_id = l.learner_id
            JOIN
                grades g ON l.grade_id = g.grade_id
            LEFT JOIN 
                attendance_sessions s ON ce.club_id = s.club_id 
                AND EXTRACT(YEAR FROM s.session_date) = ?
            LEFT JOIN 
                -- Join with club_schedules to check if session is on a scheduled day
                club_schedules cs ON (s.club_id = cs.club_id 
                                     AND (cs.grade_id = l.grade_id OR cs.grade_id IS NULL)
                                     AND cs.is_active = true
                                     AND UPPER(TO_CHAR(s.session_date, 'DY')) = cs.meeting_day::text)
            LEFT JOIN 
                attendance_records ar ON (s.session_id = ar.session_id 
                                        AND ar.learner_id = l.learner_id)
            WHERE 
                ce.club_id = ? 
                AND ce.is_active = true
                AND ce.academic_year = ?
                AND ce.term_number = ?
            GROUP BY 
                l.learner_id, l.full_name, l.admission_number, g.grade_name
            ORDER BY 
                l.full_name
        """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, selectedYear);
                ps.setObject(2, clubId);
                ps.setInt(3, selectedYear);
                ps.setInt(4, selectedTerm);

                ResultSet rs = ps.executeQuery();

                DecimalFormat df = new DecimalFormat("0.0%");
                int totalLearners = 0;
                int totalSessionsOverall = 0;
                int totalAttendanceOverall = 0;

                while (rs.next()) {
                    int sessionsAttended = rs.getInt("sessions_attended");
                    int totalSessions = rs.getInt("total_sessions");
                    String gradeName = rs.getString("grade_name");

                    String attendanceRate = "N/A";
                    if (totalSessions > 0) {
                        double rate = (double) sessionsAttended / totalSessions;
                        attendanceRate = df.format(rate);
                    }

                    reportList.add(new AttendanceReport(
                            rs.getString("full_name"),
                            rs.getString("admission_number"),
                            gradeName,
                            sessionsAttended,
                            totalSessions,
                            attendanceRate
                    ));

                    totalLearners++;
                    totalSessionsOverall += totalSessions;
                    totalAttendanceOverall += sessionsAttended;

                    logger.info("Learner: {}, Scheduled Sessions: {}/{}, Rate: {}",
                            rs.getString("full_name"), sessionsAttended, totalSessions, attendanceRate);
                }

                logger.info("Generated report for {} learners", totalLearners);

                // Add summary row
                if (totalLearners > 0) {
                    String overallRate = totalSessionsOverall > 0 ?
                            df.format((double) totalAttendanceOverall / totalSessionsOverall) : "N/A";

                    reportList.add(new AttendanceReport(
                            "TOTAL (" + totalLearners + " learners)",
                            "",
                            "",
                            totalAttendanceOverall,
                            totalSessionsOverall,
                            overallRate
                    ));
                } else {
                    logger.warn("No learners found for club {} in year {} term {}", clubId, selectedYear, selectedTerm);
                    showInformation("No enrollment data found for the selected year and term.");
                }
            }
        } catch (Exception e) {
            logger.error("Error generating report: {}", e.getMessage(), e);
            showError("Error generating report: " + e.getMessage());
        }
    }

    @FXML
    private void exportToCSV() {
        if (reportList.isEmpty()) {
            showError("No data to export. Please ensure the report has been generated.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Report to CSV");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv")
        );

        String fileName = String.format("%s_attendance_report_%d_term%d.csv",
                clubName != null ? clubName.replace(" ", "_") : "club", selectedYear, selectedTerm);
        fileChooser.setInitialFileName(fileName);

        File file = fileChooser.showSaveDialog(reportTable.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header (with grade column)
                writer.write("Full Name,Admission Number,Grade,Sessions Attended,Total Sessions,Attendance Rate\n");

                // Write data
                for (AttendanceReport report : reportList) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",%d,%d,\"%s\"\n",
                            report.getFullName(),
                            report.getAdmissionNumber(),
                            report.getGrade(),
                            report.getSessionsAttended(),
                            report.getTotalSessions(),
                            report.getAttendanceRate()));
                }

                showSuccess("Report exported successfully to: " + file.getAbsolutePath());
                logger.info("Report exported to: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Error exporting to CSV: {}", e.getMessage(), e);
                showError("Error exporting to CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    private void close() {
        logger.info("Closing reports window");
        reportTable.getScene().getWindow().hide();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInformation(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class AttendanceReport {
        private final SimpleStringProperty fullName;
        private final SimpleStringProperty admissionNumber;
        private final SimpleStringProperty grade;
        private final SimpleIntegerProperty sessionsAttended;
        private final SimpleIntegerProperty totalSessions;
        private final SimpleStringProperty attendanceRate;

        public AttendanceReport(String fullName, String admissionNumber, String grade,
                                int sessionsAttended, int totalSessions, String attendanceRate) {
            this.fullName = new SimpleStringProperty(fullName);
            this.admissionNumber = new SimpleStringProperty(admissionNumber);
            this.grade = new SimpleStringProperty(grade);
            this.sessionsAttended = new SimpleIntegerProperty(sessionsAttended);
            this.totalSessions = new SimpleIntegerProperty(totalSessions);
            this.attendanceRate = new SimpleStringProperty(attendanceRate);
        }

        public String getFullName() { return fullName.get(); }
        public String getAdmissionNumber() { return admissionNumber.get(); }
        public String getGrade() { return grade.get(); }
        public int getSessionsAttended() { return sessionsAttended.get(); }
        public int getTotalSessions() { return totalSessions.get(); }
        public String getAttendanceRate() { return attendanceRate.get(); }
    }
}