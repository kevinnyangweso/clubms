package com.cms.clubmanagementsystem.controller;

import com.cms.clubmanagementsystem.service.WebhookService;
import com.cms.clubmanagementsystem.utils.EnvLoader;
import com.cms.clubmanagementsystem.utils.NotificationUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public class WebhookController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final WebhookService webhookService;
    private final ObservableList<WebhookEvent> webhookEvents = FXCollections.observableArrayList();
    private final Preferences preferences = Preferences.userNodeForPackage(WebhookController.class);

    @FXML private TextField webhookUrlField;
    @FXML private TextField apiKeyField;
    @FXML private TextField callbackUrlField;
    @FXML private TextField portField;
    @FXML private CheckBox enableWebhooksCheckbox;
    @FXML private CheckBox enableHmacCheckbox;
    @FXML private Label statusLabel;
    @FXML private Label authStatusLabel;
    @FXML private Label hmacStatusLabel;
    @FXML private TableView<WebhookEvent> eventsTable;
    @FXML private TableColumn<WebhookEvent, String> timeColumn;
    @FXML private TableColumn<WebhookEvent, String> eventTypeColumn;
    @FXML private TableColumn<WebhookEvent, String> studentIdColumn;
    @FXML private TableColumn<WebhookEvent, String> statusColumn;
    @FXML private VBox advancedSettingsBox;
    @FXML private Button toggleAdvancedButton;

    public WebhookController() {
        this.webhookService = new WebhookService(getConfiguredPort());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        loadSavedSettings();
        updateStatusLabels();
        setupAdvancedSettingsToggle();
    }

    private void setupTableColumns() {
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("time"));
        eventTypeColumn.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        studentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        eventsTable.setItems(webhookEvents);
    }

    private void loadSavedSettings() {
        String webhookUrl = preferences.get("webhook_url", "");
        String apiKey = preferences.get("webhook_api_key", "");
        String callbackUrl = preferences.get("webhook_callback_url",
                "http://localhost:" + getConfiguredPort() + "/webhook");
        boolean enabled = preferences.getBoolean("webhooks_enabled", false);
        boolean hmacEnabled = preferences.getBoolean("webhook_hmac_enabled", false);

        webhookUrlField.setText(webhookUrl);
        apiKeyField.setText(apiKey);
        callbackUrlField.setText(callbackUrl);
        portField.setText(String.valueOf(getConfiguredPort()));
        enableWebhooksCheckbox.setSelected(enabled);
        enableHmacCheckbox.setSelected(hmacEnabled);
    }

    private int getConfiguredPort() {
        String portStr = EnvLoader.get("WEBHOOK_LISTENER_PORT", "8080");
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid port configuration, using default 8080", e);
            return 8080;
        }
    }

    private void setupAdvancedSettingsToggle() {
        advancedSettingsBox.setVisible(false);
        advancedSettingsBox.setManaged(false);
    }

    @FXML
    private void handleToggleAdvanced() {
        boolean isVisible = !advancedSettingsBox.isVisible();
        advancedSettingsBox.setVisible(isVisible);
        advancedSettingsBox.setManaged(isVisible);
        toggleAdvancedButton.setText(isVisible ? "Hide Advanced Settings" : "Show Advanced Settings");
    }

    @FXML
    private void handleSave() {
        try {
            // Save settings to preferences
            preferences.put("webhook_url", webhookUrlField.getText());
            preferences.put("webhook_api_key", apiKeyField.getText());
            preferences.put("webhook_callback_url", callbackUrlField.getText());
            preferences.putBoolean("webhooks_enabled", enableWebhooksCheckbox.isSelected());
            preferences.putBoolean("webhook_hmac_enabled", enableHmacCheckbox.isSelected());

            // Update environment variables
            System.setProperty("SCHOOL_WEBHOOK_URL", webhookUrlField.getText());
            System.setProperty("WEBHOOK_API_KEY", apiKeyField.getText());
            System.setProperty("WEBHOOK_HMAC_SECRET", apiKeyField.getText()); // Using API key as HMAC secret for simplicity

            updateStatusLabels();
            statusLabel.setText("Settings saved successfully!");
            logger.info("Webhook settings saved");
        } catch (Exception e) {
            statusLabel.setText("Error saving settings: " + e.getMessage());
            logger.error("Error saving webhook settings", e);
        }
    }

    @FXML
    private void handleTest() {
        if (webhookUrlField.getText().isEmpty()) {
            statusLabel.setText("Please enter a webhook URL first");
            return;
        }

        try {
            boolean success = webhookService.registerWithSchoolServer(
                    webhookUrlField.getText(),
                    callbackUrlField.getText()
            );

            if (success) {
                statusLabel.setText("Webhook test successful!");
                addEventToTable("test", "N/A", "Success");
                logger.info("Webhook test successful");
            } else {
                statusLabel.setText("Webhook test failed. Check URL and API key.");
                addEventToTable("test", "N/A", "Failed");
                logger.warn("Webhook test failed");
            }
        } catch (Exception e) {
            statusLabel.setText("Error testing webhook: " + e.getMessage());
            addEventToTable("test", "N/A", "Error");
            logger.error("Error testing webhook", e);
        }
    }

    @FXML
    private void handleGenerateApiKey() {
        try {
            String apiKey = WebhookService.generateApiKey();
            apiKeyField.setText(apiKey);
            statusLabel.setText("New API key generated!");
            logger.info("Generated new API key");
        } catch (Exception e) {
            statusLabel.setText("Error generating API key: " + e.getMessage());
            logger.error("Error generating API key", e);
        }
    }

    @FXML
    private void handleClearEvents() {
        webhookEvents.clear();
        statusLabel.setText("Event log cleared");
    }

    private void updateStatusLabels() {
        boolean authEnabled = !apiKeyField.getText().isEmpty();
        boolean hmacEnabled = enableHmacCheckbox.isSelected();

        authStatusLabel.setText(authEnabled ? "ENABLED" : "DISABLED");
        authStatusLabel.setStyle(authEnabled ? "-fx-text-fill: green;" : "-fx-text-fill: red;");

        hmacStatusLabel.setText(hmacEnabled ? "ENABLED" : "DISABLED");
        hmacStatusLabel.setStyle(hmacEnabled ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
    }

    public void addIncomingWebhookEvent(String eventType, String studentId) {
        addEventToTable(eventType, studentId, "Received");
    }

    private void addEventToTable(String eventType, String studentId, String status) {
        Platform.runLater(() -> {
            WebhookEvent event = new WebhookEvent(
                    LocalDateTime.now().format(TIME_FORMATTER),
                    eventType,
                    studentId,
                    status
            );

            webhookEvents.add(0, event); // Add to beginning of list

            // Limit to 100 events to prevent memory issues
            if (webhookEvents.size() > 100) {
                webhookEvents.remove(webhookEvents.size() - 1);
            }
        });
    }

    // Method to be called from Main class when webhooks are received
    public void onWebhookReceived(String eventType, String admissionNumber) {
        addIncomingWebhookEvent(eventType, admissionNumber);
        NotificationUtil.showNotification("Webhook Received",
                "Event: " + eventType + ", Student: " + admissionNumber);
    }

    // Model class for table events
    public static class WebhookEvent {
        private final String time;
        private final String eventType;
        private final String studentId;
        private final String status;

        public WebhookEvent(String time, String eventType, String studentId, String status) {
            this.time = time;
            this.eventType = eventType;
            this.studentId = studentId;
            this.status = status;
        }

        public String getTime() { return time; }
        public String getEventType() { return eventType; }
        public String getStudentId() { return studentId; }
        public String getStatus() { return status; }
    }
}