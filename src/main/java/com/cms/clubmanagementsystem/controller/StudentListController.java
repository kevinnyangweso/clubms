package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.Learner;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.EnvLoader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;

// Imports for Excel functionality
import com.cms.clubmanagementsystem.service.LearnerService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javafx.concurrent.Task;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StudentListController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(StudentListController.class);

    @FXML private TableView<Learner> studentTable;
    @FXML private TableColumn<Learner, String> colAdmissionNo;
    @FXML private TableColumn<Learner, String> colFullName;
    @FXML private TableColumn<Learner, String> colGrade;
    @FXML private TableColumn<Learner, String> colGender;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private Button refreshExcelButton;

    private ObservableList<Learner> learnerList;
    private LearnerService learnerService;
    private UUID currentSchoolId;
    private String excelFilePath;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentSchoolId = SessionManager.getCurrentSchoolId();

        // Load Excel file path from environment
        excelFilePath = EnvLoader.get("EXCEL_FILE_PATH");
        if (excelFilePath == null || excelFilePath.trim().isEmpty()) {
            excelFilePath = "learners.xlsx"; // Default fallback
        }

        learnerService = new LearnerService(currentSchoolId);

        // Check if Excel file exists
        File excelFile = new File(excelFilePath);
        if (!excelFile.exists()) {
            refreshExcelButton.setDisable(true);
            showAlert("Excel File Not Found",
                    "Excel file not found at: " + excelFilePath +
                            "\n\nPlease make sure the file exists or update the EXCEL_FILE_PATH in your environment configuration.");
        }

        setupButtonPermissions();

        if (!SessionManager.canImportExcel()) {
            refreshExcelButton.setStyle("-fx-padding: 5 15; -fx-background-color: #bdc3c7; -fx-text-fill: #7f8c8d;");
        } else {
            refreshExcelButton.setStyle("-fx-padding: 5 15; -fx-background-color: #27ae60; -fx-text-fill: white;");
        }

        setupTableColumns();
        loadStudents();
        setupSearchFilter();
    }

    private void setupButtonPermissions() {
        boolean canImport = SessionManager.canImportExcel();

        // Disable refresh from Excel button if not authorized
        if (refreshExcelButton != null) {
            refreshExcelButton.setDisable(!canImport);

            // Add tooltip to explain why button is disabled
            if (!canImport) {
                refreshExcelButton.setTooltip(new Tooltip("Only active coordinators can refresh from Excel"));
            } else {
                refreshExcelButton.setTooltip(null);
            }
        }

        logger.debug("Button permissions - Can import: {}, User role: {}",
                canImport, SessionManager.getCurrentUserRole());
    }

    private void setupTableColumns() {
        colAdmissionNo.setCellValueFactory(new PropertyValueFactory<>("admissionNumber"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colGrade.setCellValueFactory(new PropertyValueFactory<>("gradeName"));

        // Gender column - plain text without colors
        colGender.setCellValueFactory(new PropertyValueFactory<>("gender"));
        colGender.setCellFactory(column -> new TableCell<Learner, String>() {
            @Override
            protected void updateItem(String gender, boolean empty) {
                super.updateItem(gender, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setStyle("");
                } else {
                    if (gender == null || gender.trim().isEmpty()) {
                        setText("Not specified");
                        setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                    } else {
                        setText(gender);
                        setStyle("");
                    }
                }
            }
        });
    }

    private void loadStudents() {
        learnerList = loadAllStudentsFromDatabase();

        learnerList.sort(Comparator
                .comparing(Learner::getGradeName)
                .thenComparing(Learner::getFullName));

        studentTable.setItems(learnerList);

        if (learnerList.isEmpty()) {
            showNoDataPlaceholder();
        }
    }

    private void showNoDataPlaceholder() {
        Label placeholder = new Label("No students found in the database.\n\n" +
                "Possible reasons:\n" +
                "• No students have been imported yet\n" +
                "• Database connection issue\n" +
                "• School ID mismatch\n\n" +
                "Try importing students first or check database connection.");
        placeholder.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px; -fx-alignment: center;");
        studentTable.setPlaceholder(placeholder);
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            FilteredList<Learner> filteredData = createSearchFilter(newValue);
            SortedList<Learner> sortedData = new SortedList<>(filteredData);

            sortedData.setComparator(Comparator
                    .comparing(Learner::getGradeName)
                    .thenComparing(Learner::getFullName));

            studentTable.setItems(sortedData);

            studentTable.getSortOrder().setAll(colGrade);
            studentTable.sort();
        });
    }

    @FXML
    private void handleSearch() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            loadStudents();
        }
    }

    @FXML
    private void handleRefresh() {
        loadStudents();
        searchField.clear();

        studentTable.getSortOrder().setAll(colGrade);
        studentTable.sort();
    }

    @FXML
    private void handleViewStudentDetails() {
        Learner selectedLearner = studentTable.getSelectionModel().getSelectedItem();
        if (selectedLearner != null) {
            showStudentDetails(selectedLearner);
        } else {
            showAlert("Selection Required", "Please select a student to view details.");
        }
    }

    private void showStudentDetails(Learner learner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Student Details");
        alert.setHeaderText(learner.getFullName() + " (" + learner.getAdmissionNumber() + ")");

        String content = "Admission Number: " + learner.getAdmissionNumber() + "\n" +
                "Full Name: " + learner.getFullName() + "\n" +
                "Grade: " + learner.getGradeName() + "\n" +
                "Date Joined: " + learner.getDateJoinedSchool() + "\n" +
                "Gender: " + (learner.getGender() != null ? learner.getGender() : "Not specified");

        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleRefreshFromExcel() {
        if (!SessionManager.canImportExcel()) {
            logger.info("❌ User not authorized to refresh from Excel");
            return; // Silent failure - no notification
        }

        try {
            File excelFile = new File(excelFilePath);
            if (!excelFile.exists()) {
                showAlert("File Not Found", "Excel file not found at: " + excelFilePath);
                return;
            }

            Alert progressDialog = new Alert(Alert.AlertType.INFORMATION);
            progressDialog.setTitle("Importing from Excel");
            progressDialog.setHeaderText("Please wait while we import data from Excel...");
            progressDialog.setContentText("This may take a few moments.");
            progressDialog.show();

            Task<Void> importTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    List<ExcelLearnerRecord> excelRecords = readExcelFile();
                    processExcelData(excelRecords);
                    return null;
                }
            };

            importTask.setOnSucceeded(e -> {
                progressDialog.close();
                loadStudents();
                showAlert("Import Complete", "Successfully imported data from Excel file.");
            });

            importTask.setOnFailed(e -> {
                progressDialog.close();
                showAlert("Import Failed", "Failed to import data from Excel. Please check the file format and try again.");
            });

            new Thread(importTask).start();

        } catch (Exception e) {
            showAlert("Error", "Failed to refresh from Excel. Please try again.");
        }
    }

    private List<ExcelLearnerRecord> readExcelFile() {
        List<ExcelLearnerRecord> records = new ArrayList<>();

        try (FileInputStream file = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                ExcelLearnerRecord record = parseExcelRow(row);
                if (record != null) {
                    records.add(record);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to read Excel file", e);
        }

        return records;
    }

    private ExcelLearnerRecord parseExcelRow(Row row) {
        if (row == null) {
            return null;
        }

        String admissionNumber = getSafeCellStringValue(row, 0).trim();
        if (admissionNumber.isEmpty()) {
            return null;
        }

        String fullName = getSafeCellStringValue(row, 1).trim();
        String gradeName = getSafeCellStringValue(row, 2).trim();
        String dateJoined = getSafeCellStringValue(row, 3).trim();
        String gender = getSafeCellStringValue(row, 4).trim();

        return new ExcelLearnerRecord(
                admissionNumber, fullName, gradeName, dateJoined, gender
        );
    }

    private String getSafeCellStringValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            return "";
        }
        return getCellStringValue(cell);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        try {
            CellType cellType = cell.getCellType();
            if (cellType == CellType.FORMULA) {
                cellType = cell.getCachedFormulaResultType();
            }

            switch (cellType) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == Math.floor(numericValue)) {
                            return String.valueOf((int) numericValue);
                        }
                        return String.valueOf(numericValue);
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case BLANK:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private void processExcelData(List<ExcelLearnerRecord> excelRecords) {
        if (excelRecords.isEmpty()) {
            return;
        }

        int importedCount = 0;
        int errorCount = 0;

        for (ExcelLearnerRecord record : excelRecords) {
            try {
                // Resolve grade ID from grade name
                UUID gradeId = resolveGradeIdFromName(record.gradeName);
                if (gradeId == null) {
                    errorCount++;
                    continue;
                }

                // Parse date
                LocalDate dateJoined = parseDate(record.dateJoined);

                // Create or update learner in database
                boolean success = importOrUpdateLearner(
                        record.admissionNumber,
                        record.fullName,
                        gradeId,
                        dateJoined,
                        record.gender
                );

                if (success) {
                    importedCount++;
                } else {
                    errorCount++;
                }

            } catch (Exception e) {
                errorCount++;
            }
        }

        // Show simple summary to user
        int finalImportedCount = importedCount;
        Platform.runLater(() -> {
            showAlert("Import Complete",
                    String.format("Successfully imported %d learners from Excel.", finalImportedCount));
        });
    }

    private UUID resolveGradeIdFromName(String gradeName) {
        try {
            java.lang.reflect.Method method = learnerService.getClass().getMethod("resolveGradeIdFromName", String.class);
            return (UUID) method.invoke(learnerService, gradeName);
        } catch (Exception e) {
            return createGradeIfNotExists(gradeName);
        }
    }

    private UUID createGradeIfNotExists(String gradeName) {
        String checkSql = "SELECT grade_id FROM grades WHERE grade_name = ? AND school_id = ?";

        try (var conn = com.cms.clubmanagementsystem.utils.DatabaseConnector.getConnection();
             var pstmt = conn.prepareStatement(checkSql)) {

            pstmt.setString(1, gradeName);
            pstmt.setObject(2, currentSchoolId);

            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return (UUID) rs.getObject("grade_id");
            }
        } catch (Exception e) {
            return null;
        }

        UUID newGradeId = UUID.randomUUID();
        String insertSql = "INSERT INTO grades (grade_id, grade_name, school_id) VALUES (?, ?, ?)";

        try (var conn = com.cms.clubmanagementsystem.utils.DatabaseConnector.getConnection();
             var pstmt = conn.prepareStatement(insertSql)) {

            pstmt.setObject(1, newGradeId);
            pstmt.setString(2, gradeName);
            pstmt.setObject(3, currentSchoolId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                return newGradeId;
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private boolean importOrUpdateLearner(String admissionNumber, String fullName, UUID gradeId,
                                          LocalDate dateJoined, String gender) {
        boolean learnerExists = checkIfLearnerExists(admissionNumber);

        if (learnerExists) {
            String updateSql = """
            UPDATE learners SET 
                full_name = ?, 
                grade_id = ?, 
                date_joined_school = ?, 
                gender = ?,
                created_at = CURRENT_TIMESTAMP
            WHERE admission_number = ? AND school_id = ?
            """;

            try (var conn = com.cms.clubmanagementsystem.utils.DatabaseConnector.getConnection();
                 var pstmt = conn.prepareStatement(updateSql)) {

                pstmt.setString(1, fullName);
                pstmt.setObject(2, gradeId);
                pstmt.setDate(3, java.sql.Date.valueOf(dateJoined));
                pstmt.setString(4, gender != null && !gender.trim().isEmpty() ? gender : "Not specified");
                pstmt.setString(5, admissionNumber);
                pstmt.setObject(6, currentSchoolId);

                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;

            } catch (Exception e) {
                return false;
            }
        } else {
            String insertSql = """
            INSERT INTO learners (learner_id, admission_number, full_name, grade_id, 
                school_id, date_joined_school, gender, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

            try (var conn = com.cms.clubmanagementsystem.utils.DatabaseConnector.getConnection();
                 var pstmt = conn.prepareStatement(insertSql)) {

                pstmt.setObject(1, UUID.randomUUID());
                pstmt.setString(2, admissionNumber);
                pstmt.setString(3, fullName);
                pstmt.setObject(4, gradeId);
                pstmt.setObject(5, currentSchoolId);
                pstmt.setDate(6, java.sql.Date.valueOf(dateJoined));
                pstmt.setString(7, gender != null && !gender.trim().isEmpty() ? gender : "Not specified");

                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;

            } catch (Exception e) {
                return false;
            }
        }
    }

    private boolean checkIfLearnerExists(String admissionNumber) {
        String sql = "SELECT COUNT(*) FROM learners WHERE admission_number = ? AND school_id = ?";

        try (var conn = com.cms.clubmanagementsystem.utils.DatabaseConnector.getConnection();
             var pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, admissionNumber);
            pstmt.setObject(2, currentSchoolId);

            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return LocalDate.now();
        }

        try {
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("M/d/yyyy"),
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                    DateTimeFormatter.ofPattern("MM-dd-yyyy")
            };

            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDate.parse(dateString, formatter);
                } catch (Exception e) {
                    // Try next format
                }
            }

            try {
                double excelSerialDate = Double.parseDouble(dateString);
                LocalDate baseDate = LocalDate.of(1899, 12, 30);
                return baseDate.plusDays((long) excelSerialDate);
            } catch (NumberFormatException nfe) {
                // Not a numeric date
            }

            return LocalDate.now();

        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    // Inner class for Excel records - removed status field
    private static class ExcelLearnerRecord {
        public final String admissionNumber;
        public final String fullName;
        public final String gradeName;
        public final String dateJoined;
        public final String gender;

        public ExcelLearnerRecord(String admissionNumber, String fullName, String gradeName,
                                  String dateJoined, String gender) {
            this.admissionNumber = admissionNumber;
            this.fullName = fullName;
            this.gradeName = gradeName;
            this.dateJoined = dateJoined;
            this.gender = gender;
        }
    }

    /**
     * Search and Filter functionality
     */
    public FilteredList<Learner> createSearchFilter(String searchText) {
        ObservableList<Learner> allLearners = loadAllStudentsFromDatabase();
        FilteredList<Learner> filteredData = new FilteredList<>(allLearners, p -> true);

        if (searchText != null && !searchText.isEmpty()) {
            String lowerCaseFilter = searchText.toLowerCase();
            filteredData.setPredicate(learner -> {
                return learner.getAdmissionNumber().toLowerCase().contains(lowerCaseFilter) ||
                        learner.getFullName().toLowerCase().contains(lowerCaseFilter) ||
                        (learner.getGradeName() != null &&
                                learner.getGradeName().toLowerCase().contains(lowerCaseFilter)) ||
                        (learner.getGender() != null &&
                                learner.getGender().toLowerCase().contains(lowerCaseFilter));
            });
        }

        return filteredData;
    }

    /**
     * Load all students from database
     */
    public ObservableList<Learner> loadAllStudentsFromDatabase() {
        ObservableList<Learner> learners = FXCollections.observableArrayList();

        String query = "SELECT l.learner_id, l.admission_number, l.full_name, " +
                "l.grade_id, l.school_id, l.date_joined_school, " +
                "l.created_at, l.gender, g.grade_name " +
                "FROM learners l " +
                "LEFT JOIN grades g ON l.grade_id = g.grade_id " +
                "WHERE l.school_id = ? " +
                "ORDER BY l.full_name";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setObject(1, currentSchoolId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Learner learner = new Learner(
                        (UUID) rs.getObject("learner_id"),
                        rs.getString("admission_number"),
                        rs.getString("full_name"),
                        (UUID) rs.getObject("grade_id"),
                        rs.getString("grade_name"),
                        (UUID) rs.getObject("school_id"),
                        rs.getDate("date_joined_school").toLocalDate(),
                        rs.getTimestamp("created_at") != null ?
                                rs.getTimestamp("created_at").toInstant().atZone(ZoneId.systemDefault()) : null,
                        rs.getString("gender")
                );
                learners.add(learner);
            }

        } catch (SQLException e) {
            showError("Failed to load students. Please check your database connection.");
        }

        return learners;
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
}