package com.cms.clubmanagementsystem.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class TokenGenerator {
    // Configuration constants
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int DEFAULT_TOKEN_LENGTH = 32; // 256-bit tokens by default
    private static final long RANDOM_SEED_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);

    // Character sets
    private static final String NUMERIC = "0123456789";
    private static final String ALPHA_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String ALPHA_UPPER = ALPHA_LOWER.toUpperCase();
    private static final String ALPHANUMERIC = NUMERIC + ALPHA_LOWER + ALPHA_UPPER;
    private static final String SPECIAL_CHARS = "!@#$%^&*()-_=+[]{}|;:,.<>?";
    private static final String COMPLEX = ALPHANUMERIC + SPECIAL_CHARS;

    private static long lastSeedRefreshTime = System.currentTimeMillis();

    // --------------------------
    // Core Token Generation
    // --------------------------

    public static String generateToken() {
        return generateToken(DEFAULT_TOKEN_LENGTH);
    }

    public static String generateToken(int byteLength) {
        refreshRandomSeedIfNeeded();
        validateLength(byteLength);

        byte[] randomBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(randomBytes);
        return BASE64_ENCODER.encodeToString(randomBytes);
    }

    // --------------------------
    // Token Expiration Utilities
    // --------------------------

    public static Instant generateExpirationTime(int duration, ChronoUnit unit) {
        return Instant.now().plus(duration, unit);
    }

    public static boolean isTokenExpired(Instant expirationTime) {
        return Instant.now().isAfter(expirationTime);
    }

    // --------------------------
    // Hashing Methods
    // --------------------------

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return BASE64_ENCODER.encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean verifyToken(String inputToken, String storedHash) {
        return hashToken(inputToken).equals(storedHash);
    }

    // --------------------------
    // Custom Character Set Tokens
    // --------------------------

    public enum CharacterSet {
        NUMERIC,
        ALPHA_LOWER,
        ALPHA_UPPER,
        ALPHANUMERIC,
        SPECIAL,
        COMPLEX
    }

    public static String generateCustomToken(int length, CharacterSet characterSet) {
        refreshRandomSeedIfNeeded();
        validateLength(length);

        String chars;
        switch (characterSet) {
            case NUMERIC: chars = NUMERIC; break;
            case ALPHA_LOWER: chars = ALPHA_LOWER; break;
            case ALPHA_UPPER: chars = ALPHA_UPPER; break;
            case ALPHANUMERIC: chars = ALPHANUMERIC; break;
            case SPECIAL: chars = SPECIAL_CHARS; break;
            case COMPLEX: chars = COMPLEX; break;
            default: throw new IllegalArgumentException("Invalid character set");
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // --------------------------
    // Security Utilities
    // --------------------------

    private static synchronized void refreshRandomSeedIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSeedRefreshTime > RANDOM_SEED_REFRESH_INTERVAL) {
            SECURE_RANDOM.setSeed(SECURE_RANDOM.generateSeed(16));
            lastSeedRefreshTime = currentTime;
        }
    }

    private static void validateLength(int length) {
        if (length < 16) {
            throw new IllegalArgumentException("Token length must be at least 16 bytes/characters for security");
        }
    }

    // --------------------------
    // Password-Specific Generators (Matches RegisterController's requirements)
    // --------------------------

    public static String generateStrongPassword() {
        // Matches RegisterController's isPasswordStrong() requirements:
        // - 8-64 characters
        // - At least 1 uppercase
        // - At least 1 lowercase
        // - At least 1 digit
        // - At least 1 special character
        String password;
        do {
            password = generateCustomToken(12, CharacterSet.COMPLEX);
        } while (!matchesPasswordRequirements(password));

        return password;
    }

    public static boolean matchesPasswordRequirements(String password) {
        // Exactly matches RegisterController's isPasswordStrong() logic
        if (password.length() < 8 || password.length() > 64) {
            return false;
        }

        boolean hasUpper = !password.equals(password.toLowerCase());
        boolean hasLower = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}