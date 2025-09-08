package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.LearnerImportDTO;
import com.cms.clubmanagementsystem.model.ImportResult;
import com.cms.clubmanagementsystem.model.Student;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import javafx.scene.control.Alert;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Date;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.awt.*;
import java.io.*;
import java.sql.*;

public class LearnersController {
    private UUID currentSchoolId;

    // Remove connection parameter from constructor
    public LearnersController(UUID schoolId) {
        this.currentSchoolId = schoolId;
    }

    public void generateCSVTemplate() {
        try {
            String[] headers = {
                    "admission_number",
                    "full_name",
                    "grade_name",
                    "date_joined_school (YYYY-MM-DD)",
                    "gender (optional)"
            };

            String filename = "learner_import_template.csv";

            FileWriter writer = new FileWriter(filename);
            writer.write(String.join(",", headers) + "\n");

            // Add various valid date examples
            writer.write("STU001,John Doe,Grade 4,2024-01-15,Male\n");
            writer.write("STU002,Jane Smith,Grade 5,2024-09-01,Female\n");
            writer.write("STU003,Mike Brown,PP2,2023-08-28,\n");
            writer.write("STU004,Sarah Johnson,Grade 3,2024-03-10,Female\n");
            writer.write("STU005,David Wilson,Grade 6,2024-12-25,Male\n");
            writer.write("STU006,Emily Davis,Grade 2,2024-06-30,Female\n");

            writer.close();

            Desktop.getDesktop().open(new File(filename));
        } catch (IOException e) {
            showError("Template generation failed: " + e.getMessage());
        }
    }

    // Update this existing method in your LearnersController
    public List<LearnerImportDTO> parseCSVFile(File csvFile) {
        List<LearnerImportDTO> learners = new ArrayList<>();
        Map<String, UUID> gradeMap = loadGradeMappings();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean firstLine = true;
            int lineNumber = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++;

                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }

                String[] values = line.split(",");
                if (values.length >= 4) {
                    LearnerImportDTO learner = new LearnerImportDTO();
                    learner.setAdmissionNumber(values[0].trim());
                    learner.setFullName(values[1].trim());
                    learner.setGradeName(values[2].trim());
                    learner.setDateJoined(parseDate(values[3].trim(), lineNumber));

                    // Handle optional gender field (5th column if exists)
                    if (values.length >= 5) {
                        String genderValue = values[4].trim();
                        if (!genderValue.isEmpty()) {
                            // Normalize gender values
                            if (genderValue.equalsIgnoreCase("m") || genderValue.equalsIgnoreCase("male")) {
                                learner.setGender("Male");
                            } else if (genderValue.equalsIgnoreCase("f") || genderValue.equalsIgnoreCase("female")) {
                                learner.setGender("Female");
                            } else {
                                learner.setGender(genderValue); // Keep as-is
                            }
                        } else {
                            learner.setGender("Not specified"); // Default for empty values
                        }
                    } else {
                        learner.setGender("Not specified"); // Default if column doesn't exist
                    }

                    // Map grade name to grade_id
                    learner.setGradeId(gradeMap.get(learner.getGradeName().toLowerCase()));
                    learner.setValid(learner.getGradeId() != null);

                    learners.add(learner);
                } else {
                    LearnerImportDTO invalidLearner = new LearnerImportDTO();
                    invalidLearner.setValid(false);
                    invalidLearner.setErrorMessage("Line " + lineNumber + ": Insufficient columns. Expected at least 4 columns.");
                    learners.add(invalidLearner);
                }
            }
        } catch (IOException e) {
            showError("CSV parsing error: " + e.getMessage());
        }
        return learners;
    }

    private Map<String, UUID> loadGradeMappings() {
        Map<String, UUID> gradeMap = new HashMap<>();
        String sql = "SELECT grade_id, grade_name FROM grades WHERE school_id = ?";

        // Get connection locally instead of using instance variable
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, currentSchoolId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                gradeMap.put(rs.getString("grade_name").toLowerCase(),
                        (UUID) rs.getObject("grade_id"));
            }
        } catch (SQLException e) {
            showError("Failed to load grades: " + e.getMessage());
        }
        return gradeMap;
    }

    // For adding individual students
    public boolean addSingleLearner(String admissionNumber, String fullName,
                                    UUID gradeId, LocalDate joinDate, String gender) {
        String sql = "INSERT INTO learners (admission_number, full_name, grade_id, " +
                "school_id, date_joined_school, gender) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, admissionNumber);
            pstmt.setString(2, fullName);
            pstmt.setObject(3, gradeId);
            pstmt.setObject(4, currentSchoolId);
            pstmt.setDate(5, Date.valueOf(joinDate));
            pstmt.setString(6, gender);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) { // Unique violation
                showError("Admission number already exists: " + admissionNumber);
            } else {
                showError("Database error: " + e.getMessage());
            }
            return false;
        }
    }

    // Add these methods to your existing LearnersController class

    public ObservableList<Student> loadAllStudents() {
        ObservableList<Student> students = FXCollections.observableArrayList();

        String query = "SELECT l.learner_id, l.admission_number, l.full_name, " +
                "l.grade_id, g.grade_name, l.date_joined_school, l.gender, l.is_active " +
                "FROM learners l " +
                "LEFT JOIN grades g ON l.grade_id = g.grade_id " +
                "WHERE l.school_id = ? " +
                "ORDER BY l.full_name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setObject(1, currentSchoolId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                // Debug each row from the database
                String admissionNo = rs.getString("admission_number");
                String fullName = rs.getString("full_name");
                String gradeName = rs.getString("grade_name");

                // Handle gender properly - check if it's null in the database
                String gender = rs.getString("gender");
                if (rs.wasNull()) { // This checks if the database value was actually NULL
                    gender = null;
                }

                LocalDate dateJoined = rs.getDate("date_joined_school").toLocalDate();

                System.out.println("DB ROW: " + admissionNo + " | " + fullName + " | " +
                        gradeName + " | Gender: '" + gender + "' | " + dateJoined);

                Student student = new Student(
                        (UUID) rs.getObject("learner_id"),
                        admissionNo,
                        fullName,
                        (UUID) rs.getObject("grade_id"),
                        gradeName,
                        dateJoined,
                        gender, // Pass the handled gender value
                        rs.getBoolean("is_active")
                );
                students.add(student);
            }

        } catch (SQLException e) {
            showError("Failed to load students: " + e.getMessage());
            e.printStackTrace();
        }

        return students;
    }

    public FilteredList<Student> createSearchFilter(ObservableList<Student> students, String searchText) {
        FilteredList<Student> filteredData = new FilteredList<>(students, p -> true);

        if (searchText != null && !searchText.isEmpty()) {
            String lowerCaseFilter = searchText.toLowerCase();
            filteredData.setPredicate(student -> {
                if (student.getAdmissionNumber().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (student.getFullName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (student.getGradeName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (student.getGender() != null &&
                        student.getGender().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        }

        return filteredData;
    }

    public boolean toggleStudentStatus(UUID learnerId, boolean currentStatus) {
        String sql = "UPDATE learners SET is_active = ? WHERE learner_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBoolean(1, !currentStatus);
            pstmt.setObject(2, learnerId);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            showError("Failed to update student status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateStudent(Student student) {
        String sql = "UPDATE learners SET admission_number = ?, full_name = ?, " +
                "grade_id = ?, date_joined_school = ?, gender = ? WHERE learner_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, student.getAdmissionNumber());
            pstmt.setString(2, student.getFullName());
            pstmt.setObject(3, student.getGradeId());
            pstmt.setDate(4, Date.valueOf(student.getDateJoined()));
            pstmt.setString(5, student.getGender());
            pstmt.setObject(6, student.getLearnerId());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            showError("Failed to update student: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteStudent(UUID learnerId) {
        String sql = "DELETE FROM learners WHERE learner_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, learnerId);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            showError("Failed to delete student: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void showImportResults(ImportResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import Results");
        alert.setHeaderText("Import completed");

        String content = String.format(
                "Total records: %d\nSuccessful: %d\nFailed: %d",
                result.getTotalRecords(),
                result.getSuccessfulImports(),
                result.getFailedImports()
        );

        if (!result.getFailedRecords().isEmpty()) {
            content += "\n\nFailed records saved to: import_errors.csv";
            exportFailedRecords(result.getFailedRecords());
        }

        alert.setContentText(content);
        alert.showAndWait();
    }

    private LocalDate parseDate(String dateString, int lineNumber) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        String trimmedDate = dateString.trim();

        try {
            // Primary format: YYYY-MM-DD (ISO format) - RECOMMENDED
            try {
                return LocalDate.parse(trimmedDate, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                // Try other formats but prioritize YYYY-MM-DD
            }

            // Support other common formats but with warning
            DateTimeFormatter[] alternativeFormatters = {
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("M/d/yyyy")
            };

            for (DateTimeFormatter formatter : alternativeFormatters) {
                try {
                    LocalDate date = LocalDate.parse(trimmedDate, formatter);
                    // Warn about using non-standard format but still accept it
                    System.out.println("Warning: Line " + lineNumber + " - Used alternative date format: " + trimmedDate +
                            ". Recommended: YYYY-MM-DD");
                    return date;
                } catch (DateTimeParseException e) {
                    // Try next format
                }
            }

            // If no format works, throw detailed exception
            throw new DateTimeParseException(
                    "Unsupported date format. Please use YYYY-MM-DD format.",
                    trimmedDate, 0
            );

        } catch (DateTimeParseException e) {
            String errorMsg = String.format(
                    "Line %d: Invalid date format '%s'. Please use YYYY-MM-DD format. Example: 2024-09-06",
                    lineNumber, trimmedDate
            );
            showError(errorMsg);
            return null;
        }
    }

    public void showDateFormatHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Date Format Help");
        alert.setHeaderText("Required Date Format: YYYY-MM-DD");
        alert.setContentText(
                "Please use the following date format in your CSV file:\n\n" +
                        "✅ CORRECT FORMAT (Recommended):\n" +
                        "• 2024-09-06 (September 6, 2024)\n" +
                        "• 2024-01-15 (January 15, 2024)\n" +
                        "• 2023-12-25 (December 25, 2023)\n\n" +

                        "⚠️  ACCEPTED BUT NOT RECOMMENDED:\n" +
                        "• 06/09/2024 (DD/MM/YYYY)\n" +
                        "• 9/6/2024 (M/D/YYYY)\n" +
                        "• 09/06/2024 (MM/DD/YYYY)\n\n" +

                        "❌ UNACCEPTABLE FORMATS:\n" +
                        "• Sep 6, 2024\n" +
                        "• 6th September 2024\n" +
                        "• 2024-Sep-06\n\n" +

                        "TIP: Use the template file to ensure correct formatting!"
        );
        alert.showAndWait();
    }

    private void exportFailedRecords(List<LearnerImportDTO> failedRecords) {
        try (FileWriter writer = new FileWriter("import_errors.csv")) {
            writer.write("admission_number,full_name,grade_name,date_joined,error_message\n");
            for (LearnerImportDTO record : failedRecords) {
                writer.write(String.format("%s,%s,%s,%s,%s\n",
                        record.getAdmissionNumber(),
                        record.getFullName(),
                        record.getGradeName(),
                        record.getDateJoined(),
                        record.getErrorMessage()
                ));
            }
        } catch (IOException e) {
            showError("Failed to export error report: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
