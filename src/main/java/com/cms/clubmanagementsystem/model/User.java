package com.cms.clubmanagementsystem.model;

import java.util.UUID;

public class User {
    private UUID userId;
    private String username;
    private String email;
    private String passwordHash;
    private boolean isActive;
    private UUID schoolId;
    private String role;

    public User(UUID userId, String username, String email, String passwordHash,
                boolean isActive, UUID schoolId, String role) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.isActive = isActive;
        this.schoolId = schoolId;
        this.role = role;
    }

    // Getters and Setters
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return isActive; }
    public UUID getSchoolId() { return schoolId; }
    public String getRole() { return role; }

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setActive(boolean active) { isActive = active; }
    public void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }
    public void setRole(String role) { this.role = role;

    }

}
