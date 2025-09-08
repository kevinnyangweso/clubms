package com.cms.clubmanagementsystem.model;

import java.time.LocalTime;
import java.util.UUID;

public class ClubSchedule {
    private UUID scheduleId;
    private UUID clubId;
    private UUID gradeId;
    private UUID classGroupId;
    private String meetingDay;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
    private boolean isActive;

    // Additional fields for display (not stored in database)
    private String gradeName;
    private String className;

    // Constructors
    public ClubSchedule() {}

    public ClubSchedule(UUID gradeId, UUID classGroupId, String meetingDay,
                        LocalTime startTime, LocalTime endTime, String venue) {
        this.gradeId = gradeId;
        this.classGroupId = classGroupId;
        this.meetingDay = meetingDay;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venue = venue;
        this.isActive = true;
    }

    // Getters and Setters
    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public UUID getGradeId() { return gradeId; }
    public void setGradeId(UUID gradeId) { this.gradeId = gradeId; }

    public UUID getClassGroupId() { return classGroupId; }
    public void setClassGroupId(UUID classGroupId) { this.classGroupId = classGroupId; }

    public String getMeetingDay() { return meetingDay; }
    public void setMeetingDay(String meetingDay) { this.meetingDay = meetingDay; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    // Helper method for display
    public String getTimeRange() {
        return startTime + " - " + endTime;
    }

    public String getTargetDisplay() {
        StringBuilder target = new StringBuilder();
        if (gradeName != null) {
            target.append(gradeName);
        }
        if (className != null) {
            if (target.length() > 0) target.append(" - ");
            target.append(className);
        }
        if (target.length() == 0) {
            target.append("All Grades/Classes");
        }
        return target.toString();
    }
}