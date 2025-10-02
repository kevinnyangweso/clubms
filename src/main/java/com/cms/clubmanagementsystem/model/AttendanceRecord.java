package com.cms.clubmanagementsystem.model;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.UUID;

public class AttendanceRecord {
    public enum AttendanceStatus {
        PRESENT, ABSENT, LATE
    }

    private UUID learnerId;
    private SimpleStringProperty admissionNumber;
    private SimpleStringProperty learnerName;
    private SimpleStringProperty gradeName;
    private SimpleObjectProperty<AttendanceStatus> status;

    public AttendanceRecord(UUID learnerId, String admissionNumber, String learnerName, String gradeName, AttendanceStatus status) {
        this.learnerId = learnerId;
        this.admissionNumber = new SimpleStringProperty(admissionNumber);
        this.learnerName = new SimpleStringProperty(learnerName);
        this.gradeName = new SimpleStringProperty(gradeName);
        this.status = new SimpleObjectProperty<>(status);
    }

    // Getters and Setters
    public UUID getLearnerId() { return learnerId; }
    public void setLearnerId(UUID learnerId) { this.learnerId = learnerId; }

    public String getAdmissionNumber() { return admissionNumber.get(); }
    public void setAdmissionNumber(String admissionNumber) { this.admissionNumber.set(admissionNumber); }
    public SimpleStringProperty admissionNumberProperty() { return admissionNumber; }

    public String getLearnerName() { return learnerName.get(); }
    public void setLearnerName(String learnerName) { this.learnerName.set(learnerName); }
    public SimpleStringProperty learnerNameProperty() { return learnerName; }

    public String getGradeName() { return gradeName.get(); }
    public void setGradeName(String gradeName) { this.gradeName.set(gradeName); }
    public SimpleStringProperty gradeNameProperty() { return gradeName; }

    public AttendanceStatus getStatus() { return status.get(); }
    public void setStatus(AttendanceStatus status) { this.status.set(status); }
    public SimpleObjectProperty<AttendanceStatus> statusProperty() { return status; }
}