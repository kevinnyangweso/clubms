package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.LearnerImportDTO;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class CoordinatorDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label schoolInfoLabel;
    @FXML private Label totalClubsLabel;
    @FXML private Label totalTeachersLabel;
    @FXML private Label totalLearnersLabel;
    @FXML private StackPane contentArea;

    // Navigation buttons
    @FXML private Button overviewButton;
    @FXML private Button clubManagementButton;
    @FXML private Button teacherManagementButton;
    @FXML private Button studentsButton;
    @FXML private Button attendanceSummaryButton;
    @FXML private Button reportsButton;
    @FXML private Button coordinatorManagementButton;

    private ContextMenu studentsContextMenu;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUserInfo();
        loadOverview();
        loadStatistics();
        setupStudentsContextMenu();
    }

    private void setupStudentsContextMenu() {
        studentsContextMenu = new ContextMenu();

        MenuItem formatRequirementsItem = new MenuItem("See Format Requirements");
        formatRequirementsItem.setOnAction(e -> showExcelFormatInfo());

        MenuItem viewStudentsItem = new MenuItem("View Students");
        viewStudentsItem.setOnAction(e -> viewStudents());

        studentsContextMenu.getItems().addAll(formatRequirementsItem, viewStudentsItem);
    }

    @FXML
    private void showStudentsOptions() {
        // Show context menu below the students button
        studentsContextMenu.show(studentsButton, Side.BOTTOM, 0, 0);
    }

    private void showExcelFormatInfo() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Excel File Format Requirements");
        alert.setHeaderText("Required Columns for Student Import");
        alert.setGraphic(null);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        Text title = new Text("Your Excel file must contain these exact column names:");
        title.setFont(Font.font("System", FontWeight.BOLD, 12));

        Text columns = new Text(
                "• admission_number (Text)\n" +
                        "• full_name (Text)\n" +
                        "• grade_name (Text)\n" +
                        "• date_joined_school (Date: YYYY-MM-DD)\n" +
                        "• gender (Text: Male/Female/Other - optional)\n"
        );
        columns.setFont(Font.font("System", 12));

        Text note = new Text("Important: The first row must be column headers. Save your file as .xlsx format.");
        note.setFont(Font.font("System", FontWeight.NORMAL, 10));
        note.setStyle("-fx-fill: #666;");

        content.getChildren().addAll(title, columns, note);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        alert.showAndWait();
    }

    private void viewStudents() {
        // Load the student list view
        loadModule("/fxml/student-list.fxml");
        clearButtonStyles();
        studentsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    private void setupUserInfo() {
        String username = SessionManager.getCurrentUsername();
        String schoolName = SessionManager.getCurrentSchoolName();
        welcomeLabel.setText("Welcome, " + username);
        schoolInfoLabel.setText("School: " + schoolName);
    }

    private void loadOverview() {
        loadModule("/fxml/overview.fxml");
        clearButtonStyles();
        if (overviewButton != null) {
            overviewButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        }
    }

    private void loadStatistics() {
        String query = "SELECT " +
                "(SELECT COUNT(*) FROM clubs WHERE is_active = true) as club_count, " +
                "(SELECT COUNT(DISTINCT teacher_id) FROM club_teachers) as teacher_count, " +
                "(SELECT COUNT(*) FROM learners) as learner_count";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                totalClubsLabel.setText("Clubs: " + rs.getInt("club_count"));
                totalTeachersLabel.setText("Teachers: " + rs.getInt("teacher_count"));
                totalLearnersLabel.setText("Learners: " + rs.getInt("learner_count"));
            }

        } catch (SQLException e) {
            System.err.println("Error loading statistics: " + e.getMessage());

            totalClubsLabel.setText("Clubs: N/A");
            totalTeachersLabel.setText("Teachers: N/A");
            totalLearnersLabel.setText("Learners: N/A");
        }
    }

    @FXML
    private void showOverview() {
        loadModule("/fxml/overview.fxml");
        clearButtonStyles();
        overviewButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showCoordinatorManagement() {
        loadModuleWithController("/fxml/coordinator-management.fxml");
        clearButtonStyles();
        coordinatorManagementButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showClubManagement() {
        loadModuleWithController("/fxml/club-management.fxml");
        clearButtonStyles();
        clubManagementButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showAttendanceSummary() {
        loadModule("/fxml/attendance-summary.fxml");
        clearButtonStyles();
        attendanceSummaryButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showTeacherManagement() {
        loadModuleWithController("/fxml/teacher-management.fxml");
        clearButtonStyles();
        teacherManagementButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    @FXML
    private void showReports() {
        loadModule("/fxml/reports.fxml");
        clearButtonStyles();
        reportsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
    }

    // NEW METHOD: Show club creation form in modal dialog
    @FXML
    private void showClubCreationForm() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/club-creation.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Create New Club");
            stage.setScene(new Scene(root, 900, 650));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(clubManagementButton.getScene().getWindow());
            stage.showAndWait();

            // Refresh clubs after modal closes
            showClubManagement();

        } catch (IOException e) {
            System.err.println("Error loading club creation form: " + e.getMessage());
            showAlert("Error", "Failed to load club creation form.");
        }
    }

    private void loadModule(String fxmlPath) {
        try {
            URL fxmlUrl = getClass().getResource(fxmlPath);
            if (fxmlUrl == null) {
                System.err.println("CRITICAL ERROR: FXML NOT FOUND - " + fxmlPath);
                showAlert("Error", "Feature not available: " + fxmlPath + " not found");
                return;
            }

            System.out.println("Loading FXML: " + fxmlPath);
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent module = loader.load();
            contentArea.getChildren().setAll(module);
            System.out.println("Successfully loaded: " + fxmlPath);
        } catch (IOException e) {
            System.err.println("ERROR LOADING " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
            showAlert("Load Error", "Failed to load: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("UNEXPECTED ERROR with " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadModuleWithController(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent module = loader.load();
            contentArea.getChildren().setAll(module);
        } catch (IOException e) {
            System.err.println("Error loading module: " + fxmlPath);
            e.printStackTrace();
        }
    }

    private void clearButtonStyles() {
        Button[] buttons = {overviewButton, clubManagementButton, teacherManagementButton,
                studentsButton, attendanceSummaryButton, reportsButton, coordinatorManagementButton};
        for (Button button : buttons) {
            if (button != null) {
                button.setStyle("-fx-background-color: transparent; -fx-text-fill: #ecf0f1;");
            }
        }
    }

    private void showImportPreviewDialog(List<LearnerImportDTO> learners) {
        // Implement preview dialog
        showErrorAlert("Preview dialog not implemented yet");
    }

    private void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleLogout() {
        SessionManager.closeSession();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Login - Club Management System");
            stage.show();
        } catch (IOException e) {
            System.err.println("Error loading login screen: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}