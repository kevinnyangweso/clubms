package com.cms.clubmanagementsystem.utils;

public class EventTypes {
    public static final String ENROLLMENT_ADDED = "ENROLLMENT_ADDED";
    public static final String ENROLLMENT_WITHDRAWN = "ENROLLMENT_WITHDRAWN";
    public static final String ENROLLMENT_CHANGED = "ENROLLMENT_CHANGED";
    public static final String CLUB_STATS_UPDATED = "CLUB_STATS_UPDATED";
    public static final String USER_LOGGED_IN = "USER_LOGGED_IN";
    public static final String USER_LOGGED_OUT = "USER_LOGGED_OUT";

    private EventTypes() {
        // Private constructor to prevent instantiation
    }
}