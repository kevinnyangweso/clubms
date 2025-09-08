package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.Student;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;

public class StudentListController implements Initializable {

    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, String> colAdmissionNo;
    @FXML private TableColumn<Student, String> colFullName;
    @FXML private TableColumn<Student, String> colGrade;
    @FXML private TableColumn<Student, String> colGender;
    @FXML private TableColumn<Student, String> colStatus;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;

    private ObservableList<Student> studentList;
    private LearnersController learnersController;
    private UUID currentSchoolId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("DEBUG: StudentListController initializing");
        currentSchoolId = SessionManager.getCurrentSchoolId();
        System.out.println("DEBUG: Current School ID: " + currentSchoolId);

        learnersController = new LearnersController(currentSchoolId);

        // Test database connection and data
        int studentCount = getStudentCountFromDatabase();
        System.out.println("DEBUG: Database reports " + studentCount + " students");

        checkDatabaseSchema();

        setupTableColumns();
        loadStudents();
        setupSearchFilter();
        System.out.println("DEBUG: StudentListController initialized");
    }

    private int getStudentCountFromDatabase() {
        String query = "SELECT COUNT(*) as count FROM learners WHERE school_id = ?";

        try (var conn = com.cms.clubmanagementsystem.utils.DatabaseConnector.getConnection();
             var pstmt = conn.prepareStatement(query)) {

            pstmt.setObject(1, currentSchoolId);
            var rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("count");
            }

        } catch (Exception e) {
            System.err.println("ERROR getting student count: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    // Add this debug method to check database metadata
    private void checkDatabaseSchema() {
        String query = "SELECT * FROM learners WHERE school_id = ? LIMIT 1";

        try (var conn = com.cms.clubmanagementsystem.utils.DatabaseConnector.getConnection();
             var pstmt = conn.prepareStatement(query)) {

            pstmt.setObject(1, currentSchoolId);
            var rs = pstmt.executeQuery();
            var metaData = rs.getMetaData();

            System.out.println("DEBUG: Database columns in learners table:");
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                System.out.println("  " + i + ": " + metaData.getColumnName(i) +
                        " (Label: " + metaData.getColumnLabel(i) + ")");
            }

            // Also check the actual data
            if (rs.next()) {
                System.out.println("DEBUG: Sample data - Gender column value: '" +
                        rs.getString("gender") + "'");
            }

        } catch (Exception e) {
            System.err.println("ERROR checking database schema: " + e.getMessage());
        }
    }

    private void loadStudents() {
        System.out.println("DEBUG: Loading students from LearnersController...");
        studentList = learnersController.loadAllStudents(); // This should call LearnersController method
        System.out.println("DEBUG: Students loaded: " + studentList.size());

        // Detailed debug for each student including ALL fields
        for (Student student : studentList) {
            System.out.println("DEBUG: Student - " +
                    "ID: " + student.getLearnerId() +
                    ", Name: " + student.getFullName() +
                    ", Admission: " + student.getAdmissionNumber() +
                    ", Grade: " + student.getGradeName() +
                    ", Gender: '" + student.getGender() + "'" +
                    ", Date: " + student.getDateJoined() +
                    ", Active: " + student.isActive());
        }

        studentTable.setItems(studentList);

        if (studentList.isEmpty()) {
            System.out.println("DEBUG: Student list is empty!");
            showNoDataPlaceholder();
        } else {
            System.out.println("DEBUG: Table should show " + studentList.size() + " students");
        }
    }

    private void setupTableColumns() {
        System.out.println("DEBUG: Setting up table columns");

        colAdmissionNo.setCellValueFactory(new PropertyValueFactory<>("admissionNumber"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colGrade.setCellValueFactory(new PropertyValueFactory<>("gradeName"));

        // Gender column - plain text without colors
        colGender.setCellValueFactory(new PropertyValueFactory<>("gender"));
        colGender.setCellFactory(column -> new TableCell<Student, String>() {
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
                        setText(gender); // Plain text, no styling
                        setStyle(""); // No special formatting
                    }
                }
            }
        });

        // Status column - FIXED: This was missing!
        colStatus.setCellValueFactory(cellData -> {
            Student student = cellData.getValue();
            return new SimpleStringProperty(student.isActive() ? "Active" : "Inactive");
        });

        colStatus.setCellFactory(column -> new TableCell<Student, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setStyle("");
                } else {
                    Student student = (Student) getTableRow().getItem();
                    if (student.isActive()) {
                        setText("Active");
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setText("Inactive");
                        setStyle("-fx-text-fill: red;");
                    }
                }
            }
        });

        System.out.println("DEBUG: Table columns setup complete");
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
            FilteredList<Student> filteredData = learnersController.createSearchFilter(studentList, newValue);
            SortedList<Student> sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(studentTable.comparatorProperty());
            studentTable.setItems(sortedData);
        });
    }

    @FXML
    private void handleSearch() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            loadStudents(); // Reload all students if search is cleared
        }
    }

    @FXML
    private void handleRefresh() {
        System.out.println("DEBUG: Refreshing student list...");
        loadStudents();
        searchField.clear();
    }

    @FXML
    private void handleViewStudentDetails() {
        Student selectedStudent = studentTable.getSelectionModel().getSelectedItem();
        if (selectedStudent != null) {
            showStudentDetails(selectedStudent);
        } else {
            showAlert("Selection Required", "Please select a student to view details.");
        }
    }

    @FXML
    private void handleEditStudent() {
        Student selectedStudent = studentTable.getSelectionModel().getSelectedItem();
        if (selectedStudent != null) {
            editStudent(selectedStudent);
        } else {
            showAlert("Selection Required", "Please select a student to edit.");
        }
    }

    @FXML
    private void handleToggleStatus() {
        Student selectedStudent = studentTable.getSelectionModel().getSelectedItem();
        if (selectedStudent != null) {
            boolean success = learnersController.toggleStudentStatus(
                    selectedStudent.getLearnerId(), selectedStudent.isActive());

            if (success) {
                loadStudents(); // Refresh the table
                showAlert("Status Updated", "Student status has been " +
                        (selectedStudent.isActive() ? "deactivated" : "activated"));
            }
        } else {
            showAlert("Selection Required", "Please select a student to change status.");
        }
    }

    @FXML
    private void handleDeleteStudent() {
        Student selectedStudent = studentTable.getSelectionModel().getSelectedItem();
        if (selectedStudent != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Delete");
            confirm.setHeaderText("Delete Student");
            confirm.setContentText("Are you sure you want to delete " + selectedStudent.getFullName() + "?");

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    boolean success = learnersController.deleteStudent(selectedStudent.getLearnerId());
                    if (success) {
                        loadStudents(); // Refresh the table
                        showAlert("Student Deleted", selectedStudent.getFullName() + " has been deleted.");
                    }
                }
            });
        } else {
            showAlert("Selection Required", "Please select a student to delete.");
        }
    }

    private void showStudentDetails(Student student) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Student Details");
        alert.setHeaderText(student.getFullName() + " (" + student.getAdmissionNumber() + ")");

        String content = "Admission Number: " + student.getAdmissionNumber() + "\n" +
                "Full Name: " + student.getFullName() + "\n" +
                "Grade: " + student.getGradeName() + "\n" +
                "Date Joined: " + student.getDateJoined() + "\n" +
                "Gender: " + (student.getGender() != null ? student.getGender() : "Not specified") + "\n" +
                "Status: " + (student.isActive() ? "Active" : "Inactive");

        alert.setContentText(content);
        alert.showAndWait();
    }

    private void editStudent(Student student) {
        // For now, show a message that edit functionality will be implemented
        showAlert("Edit Feature", "Edit student functionality will be implemented in a future version.\n\n" +
                "Selected student: " + student.getFullName() + " (" + student.getAdmissionNumber() + ")");

        // You can implement a proper edit dialog later:
        /*
        StudentEditDialog dialog = new StudentEditDialog(student, currentSchoolId);
        Optional<Student> result = dialog.showAndWait();
        result.ifPresent(updatedStudent -> {
            boolean success = learnersController.updateStudent(updatedStudent);
            if (success) {
                loadStudents(); // Refresh table
            }
        });
        */
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}