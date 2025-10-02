package com.cms.clubmanagementsystem.model;

import java.util.UUID;

public class ClubInfo {
    private UUID clubId;
    private String clubName;

    public ClubInfo(UUID clubId, String clubName) {
        this.clubId = clubId;
        this.clubName = clubName;
    }

    public UUID getClubId() { return clubId; }
    public String getClubName() { return clubName; }

    @Override
    public String toString() {
        return clubName;
    }
}