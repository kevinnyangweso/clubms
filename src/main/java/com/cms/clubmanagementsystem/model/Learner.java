package com.cms.clubmanagementsystem.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

public class Learner {
    private UUID learnerId;
    private String admissionNumber;
    private String fullName;
    private UUID gradeId;
    private String gradeName;
    private UUID schoolId;
    private LocalDate dateJoinedSchool;
    private ZonedDateTime createdAt;
    private String gender;

    // Default constructor
    public Learner() {}

    // Full constructor (removed isActive parameter)
    public Learner(UUID learnerId, String admissionNumber, String fullName, UUID gradeId,
                   String gradeName, UUID schoolId, LocalDate dateJoinedSchool,
                   ZonedDateTime createdAt, String gender) {
        this.learnerId = learnerId;
        this.admissionNumber = admissionNumber;
        this.fullName = fullName;
        this.gradeId = gradeId;
        this.gradeName = gradeName;
        this.schoolId = schoolId;
        this.dateJoinedSchool = dateJoinedSchool;
        this.createdAt = createdAt;
        this.gender = gender;
    }

    // Constructor for Excel import (without UUID)
    public Learner(String admissionNumber, String fullName, UUID gradeId,
                   String gradeName, UUID schoolId, LocalDate dateJoinedSchool, String gender) {
        this.learnerId = UUID.randomUUID();
        this.admissionNumber = admissionNumber;
        this.fullName = fullName;
        this.gradeId = gradeId;
        this.gradeName = gradeName;
        this.schoolId = schoolId;
        this.dateJoinedSchool = dateJoinedSchool;
        this.createdAt = ZonedDateTime.now();
        this.gender = gender;
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

    public UUID getSchoolId() { return schoolId; }
    public void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }

    public LocalDate getDateJoinedSchool() { return dateJoinedSchool; }
    public void setDateJoinedSchool(LocalDate dateJoinedSchool) { this.dateJoinedSchool = dateJoinedSchool; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    @Override
    public String toString() {
        return fullName + " (" + admissionNumber + ")";
    }
}