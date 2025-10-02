package com.cms.clubmanagementsystem.model;

import java.util.UUID;

public class Club {
    private UUID clubId;
    private String clubName;

    public Club(UUID clubId, String clubName) {
        this.clubId = clubId;
        this.clubName = clubName;
    }

    // Getters and Setters
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    @Override
    public String toString() {
        return clubName;
    }
}