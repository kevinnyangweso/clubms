package com.cms.clubmanagementsystem.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Report {
    private String name;
    private String type;
    private LocalDateTime generatedAt;
    private String format;

    public Report(String name, String type, LocalDateTime generatedAt, String format) {
        this.name = name;
        this.type = type;
        this.generatedAt = generatedAt;
        this.format = format;
    }

    // Getters - these must match the PropertyValueFactory names in FXML
    public String getName() { return name; }
    public String getType() { return type; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getFormat() { return format; }

    // Formatted getters for table display
    public String getFormattedDate() {
        return generatedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
    }
}