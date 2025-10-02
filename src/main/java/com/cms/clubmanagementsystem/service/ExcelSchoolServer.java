package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.utils.EnvLoader;
import com.cms.clubmanagementsystem.utils.NotificationUtil;
import com.cms.clubmanagementsystem.utils.SessionManager;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ExcelSchoolServer {
    private static final Logger logger = LoggerFactory.getLogger(ExcelSchoolServer.class);

    // Configuration constants
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    private static final int MAX_FILE_READ_RETRIES = 3;
    private static final int FILE_READ_RETRY_DELAY_MS = 1000;
    private static final int MAX_LOAD_RETRIES = 3;
    private static final int LOAD_RETRY_DELAY_MS = 2000;
    private static final int FILE_WATCHER_DELAY_MS = 5000; // Increased to 5 seconds
    private static final int MAX_LOCK_RETRIES = 5; // Added max lock retries
    private static final int LOCK_RETRY_DELAY_MS = 3000; // 3 seconds between lock retries
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final Path excelFilePath;
    private final String webhookUrl;
    private final String apiKey;
    private final String hmacSecret;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final LearnerService learnerService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Map<String, LearnerRecord> currentLearners = Collections.synchronizedMap(new HashMap<>());
    private final AtomicReference<Long> lastModifiedTime = new AtomicReference<>(0L);
    private final AtomicReference<Long> fileSize = new AtomicReference<>(0L);

    public ExcelSchoolServer(String excelFilePath, String webhookUrl, String apiKey, LearnerService learnerService) {
        this.excelFilePath = validateAndNormalizePath(excelFilePath);
        this.webhookUrl = validateWebhookUrl(webhookUrl);
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.learnerService = Objects.requireNonNull(learnerService, "LearnerService cannot be null");

        loadEnvironmentVariables();

        this.hmacSecret = EnvLoader.get("WEBHOOK_HMAC_SECRET");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(this::createDaemonThread);
        this.httpClient = createHttpClient();

        logInitializationStatus();
    }

    private Path validateAndNormalizePath(String filePath) {
        Objects.requireNonNull(filePath, "Excel file path cannot be null");
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            logger.warn("Excel file does not exist at initialization: {}", path);
        }
        return path;
    }

    private String validateWebhookUrl(String url) {
        Objects.requireNonNull(url, "Webhook URL cannot be null");
        if (!url.startsWith("http")) {
            throw new IllegalArgumentException("Webhook URL must start with http/https");
        }
        return url;
    }

    private void loadEnvironmentVariables() {
        try {
            EnvLoader.loadEnv();
        } catch (Exception e) {
            logger.warn("Failed to load environment variables: {}", e.getMessage());
        }
    }

    private Thread createDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "ExcelSchoolServer-Scheduler");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) ->
                logger.error("Uncaught exception in scheduler thread {}", t.getName(), e));
        return thread;
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private void logInitializationStatus() {
        logger.info("Excel School Server initialized:");
        logger.info("  File: {}", excelFilePath);
        logger.info("  Webhook URL: {}", webhookUrl);
        logger.info("  HMAC Signature: {}", hmacSecret != null ? "ENABLED" : "DISABLED");
        logger.info("  Lock retry settings: {} attempts with {}ms delay", MAX_LOCK_RETRIES, LOCK_RETRY_DELAY_MS);
    }

    public synchronized void start() {
        if (isRunning.getAndSet(true)) {
            logger.warn("Excel School Server is already running");
            return;
        }

        boolean canImport = SessionManager.canImportExcel();
        SessionManager.logImportAttempt(canImport);

        if (!canImport) {
            logger.info("❌ User not authorized to import Excel - failing silently");
            isRunning.set(false);
            return; // Silent failure - no notification
        }

        logger.info("Starting Excel School Server monitor for file: {}", excelFilePath);

        try {
            // Initial load with retry
            loadExcelDataWithRetryOnLock();

            // Schedule periodic checks
            scheduler.scheduleAtFixedRate(this::checkForChanges, 0, DEFAULT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

            // Set up file system watcher
            startFileWatcher();

            logger.info("Excel School Server started successfully");

        } catch (Exception e) {
            isRunning.set(false);
            logger.error("Failed to start Excel School Server", e);
            throw new RuntimeException("Failed to start Excel School Server", e);
        }
    }

    public synchronized void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }

        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            logger.info("Excel School Server stopped gracefully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Server stop interrupted", e);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    private void loadExcelData() {
        logger.info("🔄 Attempting to load Excel data from: {}", excelFilePath);

        if (!isRunning.get()) {
            logger.debug("Server not running, skipping load");
            return;
        }

        try {
            if (!Files.exists(excelFilePath)) {
                logger.warn("❌ Excel file does not exist: {}", excelFilePath);
                return;
            }

            FileTime currentModifiedTime = Files.getLastModifiedTime(excelFilePath);
            long currentSize = Files.size(excelFilePath);

            // Check if file has actually changed
            if (currentModifiedTime.toMillis() == lastModifiedTime.get() && currentSize == fileSize.get()) {
                logger.debug("No changes detected in Excel file");
                return;
            }

            logger.info("File changed detected, reading Excel data...");
            Map<String, LearnerRecord> newLearners = readExcelFileWithRetry();

            // Update state only after successful read
            lastModifiedTime.set(currentModifiedTime.toMillis());
            fileSize.set(currentSize);

            logger.info("Read {} learners from Excel file", newLearners.size());
            if (!newLearners.isEmpty()) {
                logger.debug("Sample records: {}", newLearners.values().stream().limit(3).toList());
            }

            detectChanges(newLearners);
            currentLearners.clear();
            currentLearners.putAll(newLearners);

            logger.info("Successfully processed {} learners from Excel", currentLearners.size());

        } catch (IOException e) {
            logger.error("Error loading Excel file: {}", excelFilePath, e);
        } catch (Exception e) {
            logger.error("Unexpected error loading Excel data", e);
        }
    }

    // NEW METHOD: Load Excel data with lock retry logic
    private void loadExcelDataWithRetryOnLock() {
        for (int attempt = 1; attempt <= MAX_LOCK_RETRIES; attempt++) {
            if (!isFileLocked()) {
                loadExcelData();
                return;
            }
            logger.info("File locked, waiting for attempt {}/{}", attempt, MAX_LOCK_RETRIES);
            try {
                Thread.sleep(LOCK_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Lock retry interrupted");
                return;
            }
        }
        logger.warn("Could not load Excel file - file remains locked after {} attempts", MAX_LOCK_RETRIES);
    }

    private Map<String, LearnerRecord> readExcelFile() throws IOException {
        logger.info("📖 Reading Excel file: {}", excelFilePath);
        Map<String, LearnerRecord> learners = new LinkedHashMap<>();
        Set<String> duplicateAdmissionNumbers = new HashSet<>(); // Track duplicates
        Set<Integer> duplicateRows = new HashSet<>(); // Track rows with duplicates

        try (FileInputStream file = new FileInputStream(excelFilePath.toFile());
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IOException("Excel file contains no sheets");
            }

            int rowCount = sheet.getPhysicalNumberOfRows();
            logger.info("📊 Excel file has {} rows", rowCount);

            Iterator<Row> rowIterator = sheet.iterator();
            int rowNumber = 0;
            int validRecords = 0;
            int duplicateRecords = 0;

            // Skip header row
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                rowNumber++;
                logger.info("📋 Headers: {}", getHeaderNames(headerRow));
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNumber++;

                try {
                    LearnerRecord record = parseLearnerRow(row);
                    if (record != null && !record.admissionNumber.trim().isEmpty()) {
                        // Check for duplicates - use case-insensitive comparison
                        String normalizedAdmission = record.admissionNumber.trim().toLowerCase();

                        if (learners.containsKey(normalizedAdmission)) {
                            duplicateAdmissionNumbers.add(record.admissionNumber);
                            duplicateRows.add(rowNumber);
                            duplicateRecords++;

                            LearnerRecord existing = learners.get(normalizedAdmission);
                            logger.warn("❌ DUPLICATE admission number in row {}: {} (already exists in row {})",
                                    rowNumber, record.admissionNumber, getRowForAdmission(learners, normalizedAdmission));
                            logger.warn("   Existing: {} | New: {}", existing.fullName, record.fullName);

                            // 🔥 CRITICAL: Keep the first occurrence, skip subsequent duplicates
                            continue;
                        }

                        learners.put(normalizedAdmission, record);
                        validRecords++;
                        if (validRecords <= 3) {
                            logger.debug("📝 Record {}: {}", validRecords, record);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("❌ Error parsing row {}: {}", rowNumber, e.getMessage());
                }
            }

            // Log duplicates summary
            if (!duplicateAdmissionNumbers.isEmpty()) {
                logger.warn("⚠️  Found {} duplicate records with {} unique admission numbers: {}",
                        duplicateRecords, duplicateAdmissionNumbers.size(), duplicateAdmissionNumbers);
                logger.warn("   Duplicate rows: {}", duplicateRows);
            }

            logger.info("✅ Parsed {} valid records from {} rows ({} duplicates skipped)",
                    validRecords, rowNumber - 1, duplicateRecords);

        } catch (Exception e) {
            logger.error("❌ Failed to read Excel file: {}", e.getMessage(), e);
            throw new IOException("Failed to read Excel file: " + e.getMessage(), e);
        }

        return learners;
    }

    // Helper method to find which row a duplicate was first found in
    private int getRowForAdmission(Map<String, LearnerRecord> learners, String admissionNumber) {
        int count = 1;
        for (LearnerRecord record : learners.values()) {
            if (record.admissionNumber.trim().equalsIgnoreCase(admissionNumber)) {
                return count + 1; // +1 for header row
            }
            count++;
        }
        return -1;
    }

    private String getHeaderNames(Row headerRow) {
        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(getCellStringValue(cell));
        }
        return String.join(", ", headers);
    }

    private LearnerRecord parseLearnerRow(Row row) {
        if (row == null) {
            return null;
        }

        String admissionNumber = getCellStringValue(row.getCell(0)).trim();
        if (admissionNumber.isEmpty()) {
            return null;
        }

        String fullName = getCellStringValue(row.getCell(1)).trim();
        String gradeName = getCellStringValue(row.getCell(2)).trim();
        String dateJoined = getCellStringValue(row.getCell(3)).trim();
        String gender = getCellStringValue(row.getCell(4)).trim();
        String status = getCellStringValue(row.getCell(5)).trim();

        // 🔥 Normalize date format
        String normalizedDate = normalizeDate(dateJoined);

        // Log for debugging
        logger.debug("Raw date '{}' normalized to '{}' for {}", dateJoined, normalizedDate, admissionNumber);

        return new LearnerRecord(admissionNumber, fullName, gradeName, normalizedDate, gender, status);
    }

    // 🔥 ADD THIS METHOD: Normalize various date formats to YYYY-MM-DD
    private String normalizeDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return "";
        }

        dateString = dateString.trim();

        try {
            // Handle Excel serial numbers (like 44562 for 2022-01-01)
            if (dateString.matches("\\d+\\.?\\d*")) {
                try {
                    double excelSerial = Double.parseDouble(dateString);
                    Date date = DateUtil.getJavaDate(excelSerial);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    return sdf.format(date);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid Excel serial date: {}", dateString);
                }
            }

            // Handle MM/dd/yyyy format (like 2/2/2020)
            if (dateString.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy");
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date date = inputFormat.parse(dateString);
                return outputFormat.format(date);
            }

            // Handle dd/MM/yyyy format (like 2/2/2020)
            if (dateString.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                SimpleDateFormat inputFormat = new SimpleDateFormat("d/M/yyyy");
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date date = inputFormat.parse(dateString);
                return outputFormat.format(date);
            }

            // Handle yyyy-MM-dd format (already correct)
            if (dateString.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return dateString;
            }

            logger.warn("Unrecognized date format: {}", dateString);
            return dateString; // Return as-is if we can't parse it

        } catch (Exception e) {
            logger.warn("Error normalizing date '{}': {}", dateString, e.getMessage());
            return dateString; // Return original if parsing fails
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        try {
            CellType cellType = cell.getCellType();
            if (cellType == CellType.FORMULA) {
                cellType = cell.getCachedFormulaResultType();
            }

            switch (cellType) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        // Use proper date formatting instead of toString()
                        return formatDateCell(cell);
                    } else {
                        // Handle numbers as integers if they are whole numbers
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == Math.floor(numericValue)) {
                            return String.valueOf((int) numericValue);
                        }
                        return String.valueOf(numericValue);
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                default:
                    return "";
            }
        } catch (Exception e) {
            logger.warn("Error reading cell value", e);
            return "";
        }
    }

    private String formatDateCell(Cell cell) {
        try {
            // First try to format as proper date
            Date date = cell.getDateCellValue();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.format(date);
        } catch (Exception e) {
            logger.warn("Error formatting date cell, trying numeric fallback", e);
            try {
                // Fallback: return the raw numeric value (Excel serial date)
                return String.valueOf(cell.getNumericCellValue());
            } catch (Exception ex) {
                logger.error("Failed to get numeric value from date cell", ex);
                return "";
            }
        }
    }

    private void detectChanges(Map<String, LearnerRecord> newLearners) {
        List<LearnerRecord> changes = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        Set<String> duplicateAdmissionNumbers = new HashSet<>(); // Track duplicates

        int newCount = 0;
        int updatedCount = 0;
        int removedCount = 0;
        int skippedCount = 0;

        // 🔥 Validate new data first
        logger.info("Validating {} new learner records...", newLearners.size());
        for (Map.Entry<String, LearnerRecord> entry : newLearners.entrySet()) {
            String admissionNumber = entry.getKey();
            LearnerRecord newRecord = entry.getValue();

            // Skip invalid records
            if (!isValidRecord(newRecord)) {
                logger.warn("Skipping invalid record: {}", admissionNumber);
                skippedCount++;
                continue;
            }

            // Check for duplicates in the current Excel file
            if (processed.contains(admissionNumber)) {
                duplicateAdmissionNumbers.add(admissionNumber);
                logger.warn("❌ DUPLICATE admission number detected in Excel: {}", admissionNumber);
                continue; // Skip processing this duplicate
            }

            processed.add(admissionNumber);

            LearnerRecord oldRecord = currentLearners.get(admissionNumber);
            if (oldRecord == null) {
                changes.add(newRecord);
                newCount++;
                sendWebhook("new_student", admissionNumber, newRecord);
                logger.info("NEW student detected: {}", admissionNumber);
            } else if (!oldRecord.equals(newRecord)) {
                changes.add(newRecord);
                updatedCount++;
                sendWebhook("student_updated", admissionNumber, newRecord);
                logger.info("UPDATED student detected: {}", admissionNumber);
            }
        }

        // Detect removed learners
        for (String admissionNumber : currentLearners.keySet()) {
            if (!processed.contains(admissionNumber)) {
                LearnerRecord oldRecord = currentLearners.get(admissionNumber);
                removedCount++;
                sendWebhook("student_removed", admissionNumber, oldRecord);
                logger.info("REMOVED student detected: {}", admissionNumber);
            }
        }

        // Log duplicates summary
        if (!duplicateAdmissionNumbers.isEmpty()) {
            logger.warn("⚠️  Found {} duplicate admission numbers in Excel file: {}",
                    duplicateAdmissionNumbers.size(), duplicateAdmissionNumbers);
        }

        logger.info("Change detection: {} new, {} updated, {} removed, {} duplicates, {} invalid skipped",
                newCount, updatedCount, removedCount, duplicateAdmissionNumbers.size(), skippedCount);

        // Process changes in database
        if (!changes.isEmpty()) {
            logger.info("Processing {} changes in database", changes.size());
            processChangesInDatabase(changes);
        } else {
            logger.info("No database changes to process");
        }
    }

    // Validate record before processing
    private boolean isValidRecord(LearnerRecord record) {
        if (record == null) return false;
        if (record.admissionNumber == null || record.admissionNumber.trim().isEmpty()) return false;
        if (record.fullName == null || record.fullName.trim().isEmpty()) return false;
        if (record.gradeName == null || record.gradeName.trim().isEmpty()) return false;

        // Validate date format (should be YYYY-MM-DD after normalization)
        if (record.dateJoinedSchool != null && !record.dateJoinedSchool.isEmpty()) {
            if (!record.dateJoinedSchool.matches("\\d{4}-\\d{2}-\\d{2}")) {
                logger.warn("Invalid date format for {}: {}", record.admissionNumber, record.dateJoinedSchool);
                return false;
            }
        }

        return true;
    }

    // Add this method to your LearnerService or ExcelSchoolServer

    public void cleanupDatabaseDuplicates(UUID schoolId) {
        try {
            int duplicatesRemoved = learnerService.cleanupDuplicates(schoolId);
            logger.info("Removed {} duplicate records from database", duplicatesRemoved);
        } catch (Exception e) {
            logger.error("Error cleaning up database duplicates", e);
        }
    }

    private void processChangesInDatabase(List<LearnerRecord> changes) {
        try {
            if (!SessionManager.canImportExcel()) {
                logger.info("❌ User not authorized to process database changes - failing silently");
                return; // Silent failure - no notification
            }

            logger.info("Attempting to process {} records in database", changes.size());

            // 🔥 Clean up duplicates first
            UUID schoolId = SessionManager.getCurrentSchoolId();
            if (schoolId != null) {
                cleanupDatabaseDuplicates(schoolId);
            }

            // Log raw date strings for debugging
            logger.info("Raw date strings from Excel:");
            for (LearnerRecord record : changes) {
                logger.info("  {}: '{}'", record.admissionNumber, record.dateJoinedSchool);
            }

            // Convert to list and log sample data
            List<ExcelSchoolServer.LearnerRecord> excelRecords = new ArrayList<>(changes);
            if (!excelRecords.isEmpty()) {
                LearnerRecord sample = excelRecords.get(0);
                logger.debug("Sample record for DB processing: {}", sample);
            }

            // Call the learner service
            learnerService.processExcelData(excelRecords);

            logger.info("✅ Successfully processed {} changes in database", changes.size());

        } catch (Exception e) {
            logger.error("❌ Failed to process changes in database", e);
            // Log the specific error details
            if (e.getCause() != null) {
                logger.error("Root cause: {}", e.getCause().getMessage());
            }
        }
    }

    public void forceReload() {
        if (!SessionManager.canImportExcel()) {
            logger.info("❌ User not authorized to manually reload Excel - failing silently");
            return; // Silent failure - no notification
        }

        logger.info("Manual reload triggered by coordinator");
        loadExcelDataWithRetryOnLock();
    }

    private void showAccessDeniedNotification() {
        Platform.runLater(() -> {
            String title = "Access Denied";
            String message = "Only active coordinators can import Excel files.\n\n" +
                    "Your role: " + SessionManager.getCurrentUserRole() + "\n" +
                    "Active coordinator: " + SessionManager.isActiveCoordinator();

            // Use your notification system
            NotificationUtil.showNotification(title, message);

            // Also log to console for debugging
            logger.warn("User attempted Excel import without proper permissions. " +
                            "User role: {}, Active coordinator: {}",
                    SessionManager.getCurrentUserRole(),
                    SessionManager.isActiveCoordinator());
        });
    }

    private void checkForChanges() {
        if (!isRunning.get()) {
            return;
        }

        try {
            if (!Files.exists(excelFilePath)) {
                logger.debug("Excel file no longer exists: {}", excelFilePath);
                return;
            }

            if (isFileLocked()) {
                logger.trace("Excel file is locked, skipping change check");
                return;
            }

            long currentModifiedTime = Files.getLastModifiedTime(excelFilePath).toMillis();
            long currentSize = Files.size(excelFilePath);

            if (currentModifiedTime != lastModifiedTime.get() || currentSize != fileSize.get()) {
                logger.info("Excel file change detected, reloading data");
                loadExcelDataWithRetryOnLock(); // Use the lock-retry version
            }
        } catch (IOException e) {
            logger.error("Error checking Excel file for changes", e);
        }
    }

    private void startFileWatcher() {
        Thread watcherThread = new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path directory = excelFilePath.getParent();

                if (directory != null) {
                    directory.register(watchService,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE);

                    logger.info("Watching directory for Excel file changes: {}", directory);

                    while (isRunning.get()) {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changedPath = (Path) event.context();
                            if (changedPath.equals(excelFilePath.getFileName())) {
                                handleFileChangeEvent();
                            }
                        }
                        if (!key.reset()) {
                            logger.warn("Watch key no longer valid");
                            break;
                        }
                    }
                }
            } catch (ClosedWatchServiceException e) {
                logger.debug("File watcher service closed");
            } catch (IOException | InterruptedException e) {
                if (isRunning.get()) {
                    logger.error("File watcher error", e);
                }
                Thread.currentThread().interrupt();
            }
        });

        watcherThread.setDaemon(true);
        watcherThread.setName("Excel-File-Watcher");
        watcherThread.setUncaughtExceptionHandler((t, e) ->
                logger.error("Uncaught exception in file watcher thread", e));
        watcherThread.start();
    }

    private void handleFileChangeEvent() {
        logger.info("Excel file change detected via file system watcher");
        try {
            Thread.sleep(FILE_WATCHER_DELAY_MS);
            loadExcelDataWithRetryOnLock(); // Use the lock-retry version
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendWebhook(String eventType, String admissionNumber, LearnerRecord learner) {
        if (!isRunning.get()) {
            return;
        }

        // 🔥 ADD THIS CONDITION: Skip webhooks for local Excel imports
        if (webhookUrl.contains("localhost") || webhookUrl.contains("127.0.0.1")) {
            logger.debug("Skipping webhook for local operation: {}", eventType);
            return;
        }

        try {
            JsonObject payload = createWebhookPayload(eventType, admissionNumber, learner);
            String payloadString = payload.toString();

            HttpRequest request = buildWebhookRequest(payloadString);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug("Successfully sent {} webhook for {}", eventType, admissionNumber);
            } else {
                logger.warn("Webhook for {} returned status: {}", admissionNumber, response.statusCode());
            }

        } catch (IOException e) {
            logger.error("Network error sending webhook for {}: {}", admissionNumber, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Webhook sending interrupted for {}", admissionNumber);
        } catch (Exception e) {
            logger.error("Failed to send webhook for {}: {}", admissionNumber, e.getMessage());
        }
    }

    private JsonObject createWebhookPayload(String eventType, String admissionNumber, LearnerRecord learner) {
        JsonObject payload = new JsonObject();
        payload.addProperty("event_type", eventType);
        payload.addProperty("admission_number", admissionNumber);
        payload.addProperty("full_name", learner.fullName);
        payload.addProperty("grade_name", learner.gradeName);
        payload.addProperty("date_joined_school", learner.dateJoinedSchool);
        payload.addProperty("gender", learner.gender);
        payload.addProperty("status", learner.status);
        payload.addProperty("timestamp", System.currentTimeMillis());
        payload.addProperty("source", "excel-school-server");
        return payload;
    }

    private HttpRequest buildWebhookRequest(String payloadString) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .header("User-Agent", "Excel-School-Server/1.0")
                .header("X-Event-Source", "excel-file")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payloadString));

        if (hmacSecret != null && !hmacSecret.trim().isEmpty()) {
            String signature = calculateHmacSignature(payloadString, hmacSecret);
            builder.header("X-Hub-Signature-256", signature);
        }

        return builder.build();
    }

    private String calculateHmacSignature(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + bytesToHex(hmacData);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Error calculating HMAC signature", e);
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    // Retry mechanisms
    private Map<String, LearnerRecord> readExcelFileWithRetry() throws IOException {
        for (int attempt = 1; attempt <= MAX_FILE_READ_RETRIES; attempt++) {
            try {
                return readExcelFile();
            } catch (IOException e) {
                if (attempt == MAX_FILE_READ_RETRIES) {
                    throw e;
                }
                logger.warn("Failed to read Excel file (attempt {}/{}), retrying...", attempt, MAX_FILE_READ_RETRIES);
                sleepWithInterruptHandling(FILE_READ_RETRY_DELAY_MS);
            }
        }
        return Collections.emptyMap();
    }

    private void loadExcelDataWithRetry() {
        for (int attempt = 1; attempt <= MAX_LOAD_RETRIES; attempt++) {
            try {
                loadExcelData();
                return;
            } catch (Exception e) {
                if (attempt == MAX_LOAD_RETRIES) {
                    logger.error("Failed to load Excel file after {} attempts", MAX_LOAD_RETRIES, e);
                    return;
                }
                logger.warn("Failed to load Excel file (attempt {}/{}), retrying...", attempt, MAX_LOAD_RETRIES);
                sleepWithInterruptHandling(LOAD_RETRY_DELAY_MS);
            }
        }
    }

    private void sleepWithInterruptHandling(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted", e);
        }
    }

    private boolean isFileLocked() {
        try {
            // Method 1: Check for .lock file
            Path lockFile = excelFilePath.resolveSibling(excelFilePath.getFileName() + ".lock");
            if (Files.exists(lockFile)) {
                logger.debug("Found lock file: {}", lockFile);
                return true;
            }

            // Method 2: SAFEST - Use FileChannel with tryLock (non-destructive)
            try (FileChannel channel = FileChannel.open(excelFilePath, StandardOpenOption.READ)) {
                FileLock lock = channel.tryLock(0, Long.MAX_VALUE, true); // Shared lock
                if (lock != null) {
                    lock.release();
                    return false; // File is not locked
                }
                return true; // File is locked (could not acquire lock)
            } catch (IOException e) {
                if (e.getMessage().contains("being used by another process") ||
                        e.getMessage().contains("The process cannot access the file")) {
                    logger.debug("File is locked by another process: {}", excelFilePath);
                    return true;
                }
                logger.warn("Error checking file lock: {}", e.getMessage());
                return true; // Assume locked on error
            }

        } catch (Exception e) {
            logger.warn("Error checking file lock status for {}: {}", excelFilePath, e.getMessage());
            return true; // Assume locked if we can't determine status
        }
    }

    public boolean isHealthy() {
        return Files.exists(excelFilePath) &&
                Files.isReadable(excelFilePath) &&
                !isFileLocked() &&
                isRunning.get();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("fileExists", Files.exists(excelFilePath));
        status.put("fileReadable", Files.isReadable(excelFilePath));
        status.put("fileLocked", isFileLocked());
        status.put("lastModified", lastModifiedTime.get());
        status.put("fileSize", fileSize.get());
        status.put("currentRecords", currentLearners.size());
        status.put("monitoringActive", isRunning.get());
        status.put("serverRunning", isRunning.get());
        status.put("webhookUrl", webhookUrl);
        status.put("lockRetryAttempts", MAX_LOCK_RETRIES);
        status.put("lockRetryDelayMs", LOCK_RETRY_DELAY_MS);
        return status;
    }

    public static class LearnerRecord {
        public final String admissionNumber;
        public final String fullName;
        public final String gradeName;
        public final String dateJoinedSchool;
        public final String gender;
        public final String status;

        public LearnerRecord(String admissionNumber, String fullName, String gradeName,
                             String dateJoinedSchool, String gender, String status) {
            this.admissionNumber = admissionNumber;
            this.fullName = fullName;
            this.gradeName = gradeName;
            this.dateJoinedSchool = dateJoinedSchool;
            this.gender = gender;
            this.status = status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LearnerRecord that = (LearnerRecord) o;
            return Objects.equals(admissionNumber, that.admissionNumber) &&
                    Objects.equals(fullName, that.fullName) &&
                    Objects.equals(gradeName, that.gradeName) &&
                    Objects.equals(dateJoinedSchool, that.dateJoinedSchool) &&
                    Objects.equals(gender, that.gender) &&
                    Objects.equals(status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(admissionNumber, fullName, gradeName, dateJoinedSchool, gender);
        }

        @Override
        public String toString() {
            return "LearnerRecord{" +
                    "admissionNumber='" + admissionNumber + '\'' +
                    ", fullName='" + fullName + '\'' +
                    ", gradeName='" + gradeName + '\'' +
                    ", dateJoinedSchool='" + dateJoinedSchool + '\'' +
                    ", gender='" + gender + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }

}