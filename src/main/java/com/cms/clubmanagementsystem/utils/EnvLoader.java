package com.cms.clubmanagementsystem.utils;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Utility class for loading and accessing environment variables from a .env file.
 * Ensures secrets and configuration values are centralized.
 */
public final class EnvLoader {

    private static final String LOG_PREFIX = "[EnvLoader] ";
    private static boolean loaded = false;

    // Private constructor to prevent instantiation
    private EnvLoader() {}

    /**
     * Loads the environment variables from the .env file into System properties.
     * Prevents reloading if already loaded.
     */
    public static void loadEnv() {
        if (loaded) return;

        try {
            Dotenv dotenv = Dotenv.configure()
                    .filename(".env") // Project root
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();

            dotenv.entries().forEach(entry ->
                    System.setProperty(entry.getKey(), entry.getValue())
            );

            loaded = true;
            System.out.println(LOG_PREFIX + "Environment variables loaded successfully.");
        } catch (Exception e) {
            System.err.println(LOG_PREFIX + "Failed to load .env file: " + e.getMessage());
        }
    }

    /**
     * Retrieves the value of an environment variable.
     *
     * @param key The environment variable name
     * @return The value, or null if not found
     */
    public static String get(String key) {
        if (!loaded) {
            loadEnv();
        }
        return System.getProperty(key);
    }

    /**
     * Retrieves the value of an environment variable with a default fallback.
     *
     * @param key The environment variable name
     * @param defaultValue The default value to return if the key is not found
     * @return The value, or the defaultValue if not found
     */
    public static String get(String key, String defaultValue) {
        if (!loaded) {
            loadEnv();
        }
        String value = System.getProperty(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}