// src/main/java/com/cms/clubmanagementsystem/utils/ApiKeyGenerator.java
package com.cms.clubmanagementsystem.utils;

public class ApiKeyGenerator {

    public static String generateApiKey() {
        try {
            java.security.SecureRandom random = new java.security.SecureRandom();
            byte[] bytes = new byte[32]; // 256 bits
            random.nextBytes(bytes);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate API key", e);
        }
    }

    public static void main(String[] args) {
        String apiKey = generateApiKey();
        System.out.println("Generated API Key: " + apiKey);
        System.out.println("Add this to your .env file:");
        System.out.println("WEBHOOK_API_KEY=" + apiKey);
    }
}