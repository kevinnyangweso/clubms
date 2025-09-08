package com.cms.clubmanagementsystem.model;

import java.time.LocalDate;
import java.util.UUID;

public class Student {
    private UUID learnerId;
    private String admissionNumber;
    private String fullName;
    private UUID gradeId;
    private String gradeName;
    private LocalDate dateJoined;
    private String gender; // Added gender field
    private boolean isActive;

    // Constructors
    public Student() {}

    public Student(UUID learnerId, String admissionNumber, String fullName,
                   UUID gradeId, String gradeName, LocalDate dateJoined,
                   String gender, boolean isActive) {
        this.learnerId = learnerId;
        this.admissionNumber = admissionNumber;
        this.fullName = fullName;
        this.gradeId = gradeId;
        this.gradeName = gradeName;
        this.dateJoined = dateJoined;
        this.gender = gender;
        this.isActive = isActive;

        System.out.println("STUDENT CONSTRUCTOR: " + fullName + " - Gender: '" + gender + "'");
    }

    // Getters and Setters
    public UUID getLearnerId() { return learnerId; }
    public void setLearnerId(UUID learnerId) { this.learnerId = learnerId; }

    public String getAdmissionNumber() { return admissionNumber; }
    public void setAdmissionNumber(String admissionNumber) { this.admissionNumber = admissionNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public UUID getGradeId() { return gradeId; }
    public void setGradeId(UUID gradeId) { this.gradeId = gradeId; }

    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }

    public LocalDate getDateJoined() { return dateJoined; }
    public void setDateJoined(LocalDate dateJoined) { this.dateJoined = dateJoined; }

    public String getGender() { return gender; } // Added getter
    public void setGender(String gender) { this.gender = gender; } // Added setter

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return fullName + " (" + admissionNumber + ")";
    }

}