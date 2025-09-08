package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.model.ImportResult;
import com.cms.clubmanagementsystem.model.LearnerImportDTO;
import com.cms.clubmanagementsystem.service.LearnerService;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class LearnerImportController {

    @FXML private Label selectedFileLabel;
    @FXML private TableView<?> previewTable;
    @FXML private ProgressBar importProgress;
    @FXML private Label statusLabel;
    @FXML private Button importButton;

    private File selectedFile;
    private UUID currentSchoolId;

    @FXML
    public void initialize() {
        currentSchoolId = SessionManager.getCurrentSchoolId();
        importButton.setDisable(true);
    }

    @FXML
    private void handleFileSelection() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Learners CSV File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            selectedFileLabel.setText("Selected: " + selectedFile.getName());
            importButton.setDisable(false);
            statusLabel.setText("File ready for import");

            // Parse and show preview
            try {
                LearnersController learnersController = new LearnersController(currentSchoolId);
                List<LearnerImportDTO> parsedLearners = learnersController.parseCSVFile(selectedFile);
                statusLabel.setText("Found " + parsedLearners.size() + " learners to import");

            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Parse Error", "Failed to parse CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleImport() {
        if (selectedFile == null) {
            showAlert(Alert.AlertType.ERROR, "No File", "Please select a CSV file first");
            return;
        }

        try {
            LearnersController learnersController = new LearnersController(currentSchoolId);
            List<LearnerImportDTO> parsedLearners = learnersController.parseCSVFile(selectedFile);

            // Use the service for actual import
            LearnerService learnerService = new LearnerService(currentSchoolId);

            // Show progress
            statusLabel.setText("Importing " + parsedLearners.size() + " learners...");
            importProgress.setVisible(true);
            importProgress.setProgress(-1); // Indeterminate progress

            // Run import in background thread
            new Thread(() -> {
                try {
                    // actual import
                    ImportResult result = learnerService.importLearners(parsedLearners);

                    // Simulate import delay
                    Thread.sleep(2000);

                    // Update UI on JavaFX thread
                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText("Successfully imported " + parsedLearners.size() + " learners!");
                        importProgress.setProgress(1.0);
                        showAlert(Alert.AlertType.INFORMATION, "Import Complete",
                                "Ready to import " + parsedLearners.size() + " learners. Database integration ready!");
                    });

                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText("Import failed!");
                        showAlert(Alert.AlertType.ERROR, "Import Error", e.getMessage());
                    });
                }
            }).start();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Import Error", e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}