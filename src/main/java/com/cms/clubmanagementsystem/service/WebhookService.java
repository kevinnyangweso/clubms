package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.EnvLoader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static spark.Spark.*;

public class WebhookService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final String[] SUPPORTED_EVENTS = {"new_student", "student_updated", "student_removed"};

    private final int port;
    private final String apiKey;
    private final boolean requireAuth;
    private final boolean enableHmacValidation;
    private final String hmacSecret;
    private final Map<String, Integer> retryCounts = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public WebhookService(int port) {
        this.port = port;

        // Load security configurations
        this.apiKey = EnvLoader.get("WEBHOOK_API_KEY");
        this.requireAuth = apiKey != null && !apiKey.trim().isEmpty();
        this.hmacSecret = EnvLoader.get("WEBHOOK_HMAC_SECRET");
        this.enableHmacValidation = hmacSecret != null && !hmacSecret.trim().isEmpty();

        logSecurityConfiguration();
    }

    private void logSecurityConfiguration() {
        if (enableHmacValidation) {
            logger.info("Webhook HMAC validation enabled");
        }

        if (requireAuth) {
            logger.info("Webhook API authentication enabled");
        } else {
            logger.warn("Webhook API authentication disabled - no WEBHOOK_API_KEY found in environment");
        }
    }

    public interface WebhookListener {
        void onWebhookReceived(String eventType, String admissionNumber);
    }

    public void start(WebhookListener listener) {
        try {
            configureServer();
            setupRoutes(listener);
            awaitInitialization();
            logger.info("Webhook service started successfully on port {} - Authentication: {}", port, requireAuth);
        } catch (Exception e) {
            logger.error("Failed to start webhook service on port {}", port, e);
            throw new RuntimeException("Could not start webhook listener", e);
        }
    }

    private void configureServer() {
        port(port);
        // Configure server timeouts
        Spark.threadPool(10, 2, 30000);
    }

    private void setupRoutes(WebhookListener listener) {
        setupSecurityFilters();
        setupWebhookEndpoint(listener);
        setupHealthEndpoint();
        setupMetricsEndpoint();
        setupRetryEndpoint(listener);
    }

    private void setupSecurityFilters() {
        // API Key authentication
        if (requireAuth) {
            before("/webhook", this::validateApiKey);
        }

        // Content type validation
        before("/webhook", this::validateContentType);

        // HMAC validation
        if (enableHmacValidation) {
            before("/webhook", this::validateHmacSignature);
        }
    }

    private void validateApiKey(spark.Request req, spark.Response res) {
        String clientApiKey = req.headers("X-API-Key");

        if (clientApiKey == null || clientApiKey.trim().isEmpty()) {
            logger.warn("Unauthorized webhook attempt - missing API key from {}", getClientInfo(req));
            res.status(401);
            halt("{\"error\": \"API key required\", \"code\": \"MISSING_API_KEY\"}");
        }

        if (!apiKey.equals(clientApiKey)) {
            logger.warn("Unauthorized webhook attempt - invalid API key from {}", getClientInfo(req));
            res.status(401);
            halt("{\"error\": \"Invalid API key\", \"code\": \"INVALID_API_KEY\"}");
        }

        logger.debug("API key validation successful for {}", getClientInfo(req));
    }

    private void validateContentType(spark.Request req, spark.Response res) {
        res.type("application/json");

        String contentType = req.headers("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            res.status(400);
            halt("{\"error\": \"Content-Type must be application/json\", \"code\": \"INVALID_CONTENT_TYPE\"}");
        }
    }

    private void validateHmacSignature(spark.Request req, spark.Response res) {
        String signature = req.headers("X-Hub-Signature-256");
        if (signature == null || signature.trim().isEmpty()) {
            logger.warn("Missing HMAC signature from {}", getClientInfo(req));
            res.status(401);
            halt("{\"error\": \"HMAC signature required\", \"code\": \"MISSING_SIGNATURE\"}");
        }

        if (!validateHmacSignature(req.body(), signature)) {
            logger.warn("Invalid HMAC signature from {}", getClientInfo(req));
            res.status(401);
            halt("{\"error\": \"Invalid signature\", \"code\": \"INVALID_SIGNATURE\"}");
        }
    }

    private void setupWebhookEndpoint(WebhookListener listener) {
        post("/webhook", (req, res) -> {
            long startTime = System.currentTimeMillis();
            String clientInfo = getClientInfo(req);

            try {
                // Check for duplicate requests
                String idempotencyKey = req.headers("Idempotency-Key");
                if (idempotencyKey != null && !idempotencyKey.trim().isEmpty() && isDuplicateRequest(idempotencyKey)) {
                    logger.info("Duplicate webhook request detected with idempotency key: {}", idempotencyKey);
                    res.status(200);
                    return "{\"status\": \"ok\", \"message\": \"Duplicate request ignored\"}";
                }

                // Process the webhook
                return processWebhook(req, res, listener, startTime, clientInfo);
            } catch (Exception e) {
                logger.error("Unexpected error processing webhook from {}", clientInfo, e);
                res.status(500);
                return "{\"error\": \"Internal server error\", \"code\": \"INTERNAL_ERROR\"}";
            }
        });
    }

    private String processWebhook(spark.Request req, spark.Response res, WebhookListener listener,
                                  long startTime, String clientInfo) {
        String payload = req.body();
        logger.debug("Received webhook payload from {}: {}", clientInfo, payload);

        if (payload == null || payload.trim().isEmpty()) {
            res.status(400);
            return "{\"error\": \"Empty payload\", \"code\": \"EMPTY_PAYLOAD\"}";
        }

        JsonObject json;
        try {
            json = gson.fromJson(payload, JsonObject.class);
        } catch (JsonSyntaxException e) {
            logger.error("Invalid JSON payload from {}", clientInfo, e);
            res.status(400);
            return "{\"error\": \"Invalid JSON format\", \"code\": \"INVALID_JSON\"}";
        }

        if (!isValidPayload(json)) {
            res.status(400);
            return "{\"error\": \"Invalid payload format\", \"code\": \"INVALID_PAYLOAD\"}";
        }

        String eventType = json.get("event_type").getAsString();
        String admissionNumber = json.get("admission_number").getAsString();

        if (!isValidEventType(eventType)) {
            res.status(400);
            return "{\"error\": \"Invalid event type: " + eventType + "\", \"code\": \"INVALID_EVENT_TYPE\"}";
        }

        // Process the webhook on FX thread
        Platform.runLater(() -> {
            try {
                listener.onWebhookReceived(eventType, admissionNumber);
            } catch (Exception e) {
                logger.error("Error in webhook listener from {}", clientInfo, e);
            }
        });

        long processingTime = System.currentTimeMillis() - startTime;
        logger.info("Webhook processed successfully from {} in {} ms - Event: {}, Admission: {}",
                clientInfo, processingTime, eventType, admissionNumber);

        res.status(200);
        return String.format("{\"status\": \"ok\", \"message\": \"Webhook processed successfully\", \"processing_time_ms\": %d}",
                processingTime);
    }

    private void setupHealthEndpoint() {
        get("/health", (req, res) -> {
            res.type("application/json");
            return String.format("{\"status\": \"ok\", \"service\": \"webhook\", \"port\": %d, \"authentication\": %b, \"hmac_validation\": %b}",
                    port, requireAuth, enableHmacValidation);
        });
    }

    private void setupMetricsEndpoint() {
        get("/metrics", (req, res) -> {
            res.type("application/json");
            return String.format("{\"endpoints\": [\"/webhook\", \"/health\", \"/metrics\", \"/webhook/retry\"], " +
                            "\"authentication_required\": %b, \"hmac_validation_enabled\": %b, \"retry_counts\": %d}",
                    requireAuth, enableHmacValidation, retryCounts.size());
        });
    }

    private void setupRetryEndpoint(WebhookListener listener) {
        post("/webhook/retry", (req, res) -> {
            String retryId = req.headers("X-Retry-ID");
            if (retryId != null) {
                int retryCount = retryCounts.getOrDefault(retryId, 0);
                if (retryCount >= MAX_RETRY_ATTEMPTS) {
                    res.status(410); // Gone - stop retrying
                    return "{\"status\": \"error\", \"message\": \"Max retries exceeded\"}";
                }
                retryCounts.put(retryId, retryCount + 1);
            }

            // Process the webhook normally
            long startTime = System.currentTimeMillis();
            return processWebhook(req, res, listener, startTime, getClientInfo(req));
        });
    }

    private boolean isDuplicateRequest(String idempotencyKey) {
        // Implement proper duplicate detection with short-lived cache
        // For production, consider using a distributed cache like Redis
        // or a database with TTL for tracking idempotency keys
        return false; // Simplified implementation
    }

    private boolean validateHmacSignature(String payload, String signature) {
        try {
            String expectedSignature = "sha256=" + calculateHmac(payload, hmacSecret);
            return MessageDigest.isEqual(expectedSignature.getBytes(), signature.getBytes());
        } catch (Exception e) {
            logger.error("Error validating HMAC signature", e);
            return false;
        }
    }

    private String calculateHmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacData);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public boolean registerWithSchoolServer(String schoolWebhookUrl, String callbackUrl) {
        try {
            JsonObject registrationPayload = new JsonObject();
            registrationPayload.addProperty("url", callbackUrl);
            registrationPayload.addProperty("events", String.join(",", SUPPORTED_EVENTS));

            if (requireAuth) {
                registrationPayload.addProperty("secret", apiKey);
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schoolWebhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(registrationPayload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Successfully registered webhook with school server");
                return true;
            } else {
                logger.error("Failed to register webhook. Status: {}, Response: {}",
                        response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error registering webhook with school server", e);
            return false;
        }
    }

    public boolean unregisterFromSchoolServer(String schoolWebhookUrl, String callbackUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            String encodedUrl = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schoolWebhookUrl + "?url=" + encodedUrl))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .DELETE()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Successfully unregistered webhook from school server");
                return true;
            } else {
                logger.error("Failed to unregister webhook. Status: {}, Response: {}",
                        response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error unregistering webhook from school server", e);
            return false;
        }
    }

    private boolean isValidPayload(JsonObject json) {
        return json != null &&
                json.has("event_type") &&
                json.has("admission_number") &&
                !json.get("admission_number").getAsString().isEmpty();
    }

    private boolean isValidEventType(String eventType) {
        for (String supportedEvent : SUPPORTED_EVENTS) {
            if (supportedEvent.equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    private String getClientInfo(spark.Request req) {
        String ip = req.ip();
        String userAgent = req.headers("User-Agent");
        return String.format("IP: %s, User-Agent: %s", ip, userAgent != null ? userAgent : "Unknown");
    }

    public void stop() {
        try {
            Spark.stop();
            // Wait for server to stop gracefully
            Spark.awaitStop();
            logger.info("Webhook service stopped");
        } catch (Exception e) {
            logger.warn("Error during webhook service shutdown", e);
        }
    }

    public boolean isAuthenticationEnabled() {
        return requireAuth;
    }

    public boolean isHmacValidationEnabled() {
        return enableHmacValidation;
    }

    public static String generateApiKey() {
        try {
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate API key", e);
        }
    }

    public static String generateHmacSecret() {
        return generateApiKey(); // Reuse the same secure generation method
    }
}