package com.cms.clubmanagementsystem.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class ClubAttendanceSummary {
    private SimpleStringProperty clubName;
    private SimpleStringProperty teacherName;
    private SimpleIntegerProperty totalLearners;
    private SimpleIntegerProperty presentCount;
    private SimpleIntegerProperty absentCount;
    private SimpleStringProperty dailyRate;
    private SimpleStringProperty monthlyRate;
    private SimpleStringProperty termRate;
    private SimpleStringProperty sessionTime;

    public ClubAttendanceSummary() {
        this.clubName = new SimpleStringProperty();
        this.teacherName = new SimpleStringProperty();
        this.totalLearners = new SimpleIntegerProperty();
        this.presentCount = new SimpleIntegerProperty();
        this.absentCount = new SimpleIntegerProperty();
        this.dailyRate = new SimpleStringProperty();
        this.monthlyRate = new SimpleStringProperty();
        this.termRate = new SimpleStringProperty();
        this.sessionTime = new SimpleStringProperty();
    }

    // Getters and Setters
    public String getClubName() { return clubName.get(); }
    public void setClubName(String clubName) { this.clubName.set(clubName); }
    public SimpleStringProperty clubNameProperty() { return clubName; }

    public String getTeacherName() { return teacherName.get(); }
    public void setTeacherName(String teacherName) { this.teacherName.set(teacherName); }
    public SimpleStringProperty teacherNameProperty() { return teacherName; }

    public int getTotalLearners() { return totalLearners.get(); }
    public void setTotalLearners(int totalLearners) { this.totalLearners.set(totalLearners); }
    public SimpleIntegerProperty totalLearnersProperty() { return totalLearners; }

    public int getPresentCount() { return presentCount.get(); }
    public void setPresentCount(int presentCount) { this.presentCount.set(presentCount); }
    public SimpleIntegerProperty presentCountProperty() { return presentCount; }

    public int getAbsentCount() { return absentCount.get(); }
    public void setAbsentCount(int absentCount) { this.absentCount.set(absentCount); }
    public SimpleIntegerProperty absentCountProperty() { return absentCount; }

    public String getDailyRate() { return dailyRate.get(); }
    public void setDailyRate(String dailyRate) { this.dailyRate.set(dailyRate); }
    public SimpleStringProperty dailyRateProperty() { return dailyRate; }

    public String getMonthlyRate() { return monthlyRate.get(); }
    public void setMonthlyRate(String monthlyRate) { this.monthlyRate.set(monthlyRate); }
    public SimpleStringProperty monthlyRateProperty() { return monthlyRate; }

    public String getTermRate() { return termRate.get(); }
    public void setTermRate(String termRate) { this.termRate.set(termRate); }
    public SimpleStringProperty termRateProperty() { return termRate; }

    public String getSessionTime() { return sessionTime.get(); }
    public void setSessionTime(String sessionTime) { this.sessionTime.set(sessionTime); }
    public SimpleStringProperty sessionTimeProperty() { return sessionTime; }
}