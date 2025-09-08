package com.cms.clubmanagementsystem.model;

import java.time.LocalDate;
import java.util.UUID;

public class LearnerImportDTO {
    private String admissionNumber;
    private String fullName;
    private String gradeName;
    private UUID gradeId;
    private LocalDate dateJoined;
    private String gender; // Added gender field
    private boolean isValid;
    private String errorMessage;

    // Getters and setters
    public String getAdmissionNumber() { return admissionNumber; }
    public void setAdmissionNumber(String admissionNumber) { this.admissionNumber = admissionNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }

    public UUID getGradeId() { return gradeId; }
    public void setGradeId(UUID gradeId) { this.gradeId = gradeId; }

    public LocalDate getDateJoined() { return dateJoined; }
    public void setDateJoined(LocalDate dateJoined) { this.dateJoined = dateJoined; }

    // Added gender getter and setter
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public boolean isValid() { return isValid; }
    public void setValid(boolean valid) { isValid = valid; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}