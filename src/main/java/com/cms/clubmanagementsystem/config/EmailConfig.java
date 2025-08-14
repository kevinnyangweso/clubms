package com.cms.clubmanagementsystem.config;

import io.github.cdimascio.dotenv.Dotenv;

public class EmailConfig {
    private static final Dotenv dotenv = Dotenv.configure().load();

    // Gmail SMTP Configuration
    public static final String SMTP_HOST = dotenv.get("SMTP_HOST", "smtp.gmail.com");
    public static final int SMTP_PORT = Integer.parseInt(dotenv.get("SMTP_PORT", "587"));
    public static final boolean SMTP_AUTH = true;
    public static final boolean SMTP_STARTTLS = true;

    // Credentials
    public static final String SMTP_USERNAME = dotenv.get("SMTP_USERNAME");
    public static final String SMTP_PASSWORD = dotenv.get("SMTP_PASSWORD");
    public static final String SMTP_FROM = dotenv.get("SMTP_FROM", SMTP_USERNAME);

    static {
        if (SMTP_USERNAME == null || SMTP_PASSWORD == null) {
            throw new IllegalStateException(
                    "Email credentials not configured. Please check your .env file"
            );
        }
    }
}