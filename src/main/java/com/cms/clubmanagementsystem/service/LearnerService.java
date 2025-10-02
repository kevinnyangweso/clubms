package com.cms.clubmanagementsystem.service;

import com.cms.clubmanagementsystem.controller.EnrollmentController.LearnerInfo;
import com.cms.clubmanagementsystem.model.ImportResult;
import com.cms.clubmanagementsystem.model.Learner;
import com.cms.clubmanagementsystem.model.LearnerImportDTO;
import com.cms.clubmanagementsystem.utils.DatabaseConnector;
import com.cms.clubmanagementsystem.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class LearnerService {
    private static final Logger logger = LoggerFactory.getLogger(LearnerService.class);

    // Configuration constants
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy")
    };

    private static final Map<String, String> MONTH_MAP = createMonthMap();
    private final UUID currentSchoolId;

    public LearnerService(UUID schoolId) {
        this.currentSchoolId = Objects.requireNonNull(schoolId, "School ID cannot be null");
        logger.debug("LearnerService initialized for school ID: {}", schoolId);
    }

    private static Map<String, String> createMonthMap() {
        return Map.ofEntries(
                Map.entry("Jan", "01"), Map.entry("Feb", "02"), Map.entry("Mar", "03"),
                Map.entry("Apr", "04"), Map.entry("May", "05"), Map.entry("Jun", "06"),
                Map.entry("Jul", "07"), Map.entry("Aug", "08"), Map.entry("Sep", "09"),
                Map.entry("Oct", "10"), Map.entry("Nov", "11"), Map.entry("Dec", "12")
        );
    }

    private Connection getConnection() throws SQLException {
        return DatabaseConnector.getConnection();
    }

    public List<LearnerInfo> getActiveLearners(Connection conn, UUID schoolId) throws SQLException {
        String sql = """
            SELECT l.learner_id, l.admission_number, l.full_name, g.grade_name 
            FROM learners l 
            JOIN grades g ON l.grade_id = g.grade_id 
            WHERE l.school_id = ? 
            ORDER BY g.grade_name,l.full_name
            """;

        return executeLearnerQuery(conn, sql, schoolId, null);
    }

    public List<LearnerInfo> searchLearners(Connection conn, UUID schoolId, String searchTerm) throws SQLException {
        String sql = """
            SELECT l.learner_id, l.admission_number, l.full_name, g.grade_name 
            FROM learners l 
            JOIN grades g ON l.grade_id = g.grade_id 
            WHERE l.school_id = ? 
            AND (l.full_name ILIKE ? OR l.admission_number ILIKE ?) 
            ORDER BY g.grade_name,l.full_name
            """;

        return executeLearnerQuery(conn, sql, schoolId, searchTerm);
    }

    private List<LearnerInfo> executeLearnerQuery(Connection conn, String sql, UUID schoolId, String searchTerm)
            throws SQLException {
        List<LearnerInfo> learners = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, schoolId);

            if (searchTerm != null) {
                String likeTerm = "%" + searchTerm + "%";
                stmt.setString(2, likeTerm);
                stmt.setString(3, likeTerm);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    learners.add(mapResultSetToLearnerInfo(rs));
                }
            }
        }

        return learners;
    }

    private LearnerInfo mapResultSetToLearnerInfo(ResultSet rs) throws SQLException {
        UUID learnerId = (UUID) rs.getObject("learner_id");
        String admissionNumber = rs.getString("admission_number");
        String fullName = rs.getString("full_name");
        String gradeName = rs.getString("grade_name");

        return new LearnerInfo(learnerId, admissionNumber, fullName, gradeName);
    }

    public ImportResult importLearners(List<LearnerImportDTO> learners) {
        ImportResult result = new ImportResult();
        result.setTotalRecords(learners.size());

        if (learners.isEmpty()) {
            logger.warn("Attempted to import empty list of learners");
            return result;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try (Connection connection = getConnection()) {
                connection.setAutoCommit(false);
                result = processImportBatch(connection, learners, result);
                connection.commit();
                break; // Success, break out of retry loop
            } catch (SQLException e) {
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    logger.error("Failed to import learners after {} attempts", MAX_RETRY_ATTEMPTS, e);
                    throw new RuntimeException("Failed to import learners: " + e.getMessage(), e);
                }
                logger.warn("Import attempt {} failed, retrying...", attempt);
                sleepWithInterruptHandling(RETRY_DELAY_MS);
            }
        }

        return result;
    }

    private ImportResult processImportBatch(Connection connection, List<LearnerImportDTO> learners, ImportResult result) {
        String sql = """
    INSERT INTO learners (learner_id, admission_number, full_name, grade_id, 
    school_id, date_joined_school, gender, created_at) 
    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) 
    ON CONFLICT (admission_number, school_id) 
    DO UPDATE SET full_name = EXCLUDED.full_name, grade_id = EXCLUDED.grade_id, 
    date_joined_school = EXCLUDED.date_joined_school, gender = EXCLUDED.gender,
    created_at = learners.created_at
    """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            Set<String> processedAdmissionNumbers = new HashSet<>();
            List<String> duplicateAdmissionNumbers = new ArrayList<>();

            for (LearnerImportDTO learner : learners) {
                if (isLearnerValid(learner)) {
                    try {
                        // Check for duplicates in the current batch
                        if (processedAdmissionNumbers.contains(learner.getAdmissionNumber())) {
                            duplicateAdmissionNumbers.add(learner.getAdmissionNumber());
                            logger.warn("Duplicate admission number in Excel file: {}", learner.getAdmissionNumber());
                        }
                        processedAdmissionNumbers.add(learner.getAdmissionNumber());

                        setBatchParameters(pstmt, learner);
                        pstmt.addBatch();
                        result.setSuccessfulImports(result.getSuccessfulImports() + 1);
                    } catch (Exception e) {
                        handleLearnerError(learner, result, "Database error: " + e.getMessage(), e);
                    }
                } else {
                    result.getFailedRecords().add(learner);
                }
            }

            // Log duplicates at the end
            if (!duplicateAdmissionNumbers.isEmpty()) {
                logger.warn("Found {} duplicate admission numbers in Excel file: {}",
                        duplicateAdmissionNumbers.size(), duplicateAdmissionNumbers);
            }

            // Execute the batch
            try {
                int[] batchResults = pstmt.executeBatch();
            } catch (SQLException e) {
                handleBatchException(connection, e, "Batch execution failed");
            }

        } catch (SQLException e) {
            handleBatchException(connection, e, "Import transaction failed");
        }

        return result;
    }

    private List<LearnerImportDTO> prepareBatch(PreparedStatement pstmt, List<LearnerImportDTO> learners, ImportResult result)
            throws SQLException {
        List<LearnerImportDTO> validLearnersInBatch = new ArrayList<>();

        for (LearnerImportDTO learner : learners) {
            if (isLearnerValid(learner)) {
                try {
                    setBatchParameters(pstmt, learner);
                    pstmt.addBatch();
                    validLearnersInBatch.add(learner);
                } catch (Exception e) {
                    handleLearnerError(learner, result, "Database error: " + e.getMessage(), e);
                }
            } else {
                result.getFailedRecords().add(learner);
            }
        }

        return validLearnersInBatch;
    }

    public int cleanupDuplicates(UUID schoolId) {
        try {
            logger.info("Cleaning up duplicate records in database for school: {}", schoolId);

            String cleanupSql = """
            DELETE FROM learners 
            WHERE learner_id NOT IN (
                SELECT MAX(learner_id) 
                FROM learners 
                WHERE school_id = ?
                GROUP BY admission_number
            ) AND school_id = ?
            """;

            try (Connection conn = DatabaseConnector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(cleanupSql)) {

                stmt.setObject(1, schoolId);
                stmt.setObject(2, schoolId);

                return stmt.executeUpdate();
            }

        } catch (Exception e) {
            logger.error("Error cleaning up database duplicates", e);
            return 0;
        }
    }

    private void executeBatch(PreparedStatement pstmt, List<LearnerImportDTO> validLearners, ImportResult result) throws SQLException {
        try {
            int[] batchResults = pstmt.executeBatch();
            validateBatchResults(batchResults, validLearners, result);
            result.setSuccessfulImports(result.getSuccessfulImports() + (validLearners.size() - countFailures(batchResults)));
        } catch (BatchUpdateException batchException) {
            handleBatchUpdateException(pstmt.getConnection(), batchException, validLearners, result);
        } catch (SQLException e) {
            handleBatchException(pstmt.getConnection(), e, "SQL exception during batch execution");
        }
    }

    private int countFailures(int[] batchResults) {
        int failureCount = 0;
        for (int result : batchResults) {
            if (result == Statement.EXECUTE_FAILED) {
                failureCount++;
            }
        }
        return failureCount;
    }

    private boolean isLearnerValid(LearnerImportDTO learner) {
        return validateLearnerManually(learner);
    }

    private boolean validateLearnerManually(LearnerImportDTO learner) {
        learner.setErrorMessage(null);
        List<String> errors = new ArrayList<>();

        if (learner.getAdmissionNumber() == null || learner.getAdmissionNumber().trim().isEmpty()) {
            errors.add("Admission number is required");
        }

        if (learner.getFullName() == null || learner.getFullName().trim().isEmpty()) {
            errors.add("Full name is required");
        }

        if (learner.getGradeName() == null || learner.getGradeName().trim().isEmpty()) {
            errors.add("Grade name is required");
        } else if (learner.getGradeId() == null) {
            errors.add("Grade ID could not be resolved for: " + learner.getGradeName());
        }

        if (learner.getDateJoined() == null) {
            errors.add("Date joined is required");
        }

        // Removed status validation since app no longer handles learner status

        if (!errors.isEmpty()) {
            learner.setErrorMessage(String.join("; ", errors));
            return false;
        }

        return true;
    }

    private void setBatchParameters(PreparedStatement pstmt, LearnerImportDTO learner) throws SQLException {
        pstmt.setObject(1, UUID.randomUUID());
        pstmt.setString(2, learner.getAdmissionNumber());
        pstmt.setString(3, learner.getFullName());
        pstmt.setObject(4, learner.getGradeId());
        pstmt.setObject(5, currentSchoolId);
        pstmt.setDate(6, Date.valueOf(learner.getDateJoined()));
        pstmt.setString(7, Optional.ofNullable(learner.getGender())
                .filter(g -> !g.trim().isEmpty())
                .orElse("Not specified"));
        // Removed is_active parameter
    }

    private void validateBatchResults(int[] batchResults, List<LearnerImportDTO> batch, ImportResult result) {
        for (int i = 0; i < batchResults.length; i++) {
            if (batchResults[i] == Statement.EXECUTE_FAILED) {
                LearnerImportDTO failedLearner = batch.get(i);
                handleLearnerError(failedLearner, result, "Batch execution failed", null);
            }
        }
    }

    private void handleBatchUpdateException(Connection connection, BatchUpdateException batchException,
                                            List<LearnerImportDTO> batch, ImportResult result) {
        rollbackConnection(connection);

        int[] updateCounts = batchException.getUpdateCounts();
        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] == Statement.EXECUTE_FAILED) {
                LearnerImportDTO failedLearner = batch.get(i);
                handleLearnerError(failedLearner, result, "Batch failed: " + getRootCauseMessage(batchException), null);
            }
        }

        logger.error("Batch update failed for {} records", batch.size(), batchException);
    }

    private void handleBatchException(Connection connection, SQLException e, String errorMessage) {
        rollbackConnection(connection);
        logger.error(errorMessage, e);
    }

    private void rollbackConnection(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback();
                logger.debug("Transaction rolled back successfully");
            }
        } catch (SQLException ex) {
            logger.warn("Failed to rollback connection", ex);
        }
    }

    private void handleLearnerError(LearnerImportDTO learner, ImportResult result, String errorMessage, Exception e) {
        learner.setErrorMessage(errorMessage);
        result.getFailedRecords().add(learner);

        if (e != null) {
            logger.warn("Failed to import learner {}: {}", learner.getAdmissionNumber(), errorMessage, e);
        } else {
            logger.warn("Failed to import learner {}: {}", learner.getAdmissionNumber(), errorMessage);
        }
    }

    public List<LearnerImportDTO> convertExcelDataToLearners(List<ExcelSchoolServer.LearnerRecord> excelRecords) {
        return excelRecords.stream()
                .map(this::convertExcelRecordToDTO)
                .collect(Collectors.toList());
    }

    private LearnerImportDTO convertExcelRecordToDTO(ExcelSchoolServer.LearnerRecord record) {
        LearnerImportDTO dto = new LearnerImportDTO();
        dto.setAdmissionNumber(record.admissionNumber);
        dto.setFullName(record.fullName);
        dto.setGradeName(record.gradeName);
        dto.setGender(record.gender);
        // Removed status setting since app no longer handles learner status

        // Validate and parse date
        if (record.dateJoinedSchool == null || record.dateJoinedSchool.trim().isEmpty()) {
            dto.setErrorMessage("Date joined is required");
        } else {
            try {
                LocalDate dateJoined = parseDate(record.dateJoinedSchool);
                dto.setDateJoined(dateJoined);
            } catch (Exception e) {
                dto.setErrorMessage("Invalid date format: " + record.dateJoinedSchool);
                logger.warn("Date parsing failed for {}: {}", record.admissionNumber, e.getMessage());
            }
        }

        // Resolve grade ID
        if (record.gradeName != null && !record.gradeName.trim().isEmpty()) {
            UUID gradeId = resolveGradeIdFromName(record.gradeName.trim());
            if (gradeId != null) {
                dto.setGradeId(gradeId);
                logger.debug("Resolved grade '{}' to ID: {}", record.gradeName, gradeId);
            } else {
                dto.setErrorMessage("Grade not found: " + record.gradeName);
                logger.warn("Grade not found: '{}' for admission: {}", record.gradeName, record.admissionNumber);
            }
        } else {
            dto.setErrorMessage("Grade name is required");
        }

        return dto;
    }

    private UUID resolveGradeIdFromName(String gradeName) {
        String sql = "SELECT grade_id FROM grades WHERE grade_name = ? AND school_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, gradeName);
            pstmt.setObject(2, currentSchoolId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("grade_id");
                } else {
                    logger.info("Grade '{}' not found, creating new grade", gradeName);
                    return createGradeIfNotExists(gradeName);
                }
            }
        } catch (SQLException e) {
            logger.error("Error resolving grade ID for: {}", gradeName, e);
            return null;
        }
    }

    private UUID createGradeIfNotExists(String gradeName) {
        UUID newGradeId = UUID.randomUUID();
        String sql = """
            INSERT INTO grades (grade_id, grade_name, school_id, created_at) 
            VALUES (?, ?, ?, CURRENT_TIMESTAMP) 
            ON CONFLICT (grade_name, school_id) DO UPDATE SET grade_name = EXCLUDED.grade_name 
            RETURNING grade_id
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, newGradeId);
            pstmt.setString(2, gradeName);
            pstmt.setObject(3, currentSchoolId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("grade_id");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to create grade: {}", gradeName, e);
        }

        return null;
    }

    public List<Learner> getAllLearnersForSchool() {
        String sql = """
            SELECT l.*, g.grade_name 
            FROM learners l 
            LEFT JOIN grades g ON l.grade_id = g.grade_id 
            WHERE l.school_id = ? 
            ORDER BY g.grade_name,l.full_name
            """;

        List<Learner> learners = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, currentSchoolId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    learners.add(mapResultSetToLearner(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching learners for school: {}", currentSchoolId, e);
        }

        return learners;
    }

    private Learner mapResultSetToLearner(ResultSet rs) throws SQLException {
        Learner learner = new Learner();
        learner.setLearnerId((UUID) rs.getObject("learner_id"));
        learner.setAdmissionNumber(rs.getString("admission_number"));
        learner.setFullName(rs.getString("full_name"));
        learner.setGradeId((UUID) rs.getObject("grade_id"));
        learner.setSchoolId((UUID) rs.getObject("school_id"));

        java.sql.Date dateJoined = rs.getDate("date_joined_school");
        if (dateJoined != null) {
            learner.setDateJoinedSchool(dateJoined.toLocalDate());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            learner.setCreatedAt(createdAt.toInstant().atZone(ZoneId.systemDefault()));
        }

        learner.setGender(rs.getString("gender"));

        return learner;
    }

    public void processExcelData(List<ExcelSchoolServer.LearnerRecord> excelRecords) {
        if (!SessionManager.canImportExcel()) {
            logger.error("❌ ACCESS DENIED: User not authorized to process Excel data");
            throw new SecurityException("Only active coordinators can import Excel data");
        }

        try {
            if (!validateDatabaseState()) {
                logger.error("Database validation failed. Cannot process Excel data.");
                return;
            }

            List<LearnerImportDTO> learnerDTOs = convertExcelDataToLearners(excelRecords);

            // Separate valid and invalid records
            List<LearnerImportDTO> validRecords = new ArrayList<>();
            List<LearnerImportDTO> invalidRecords = new ArrayList<>();

            for (LearnerImportDTO dto : learnerDTOs) {
                if (isLearnerValid(dto)) {
                    validRecords.add(dto);
                } else {
                    invalidRecords.add(dto);
                    logger.warn("Invalid record skipped: {} - {}",
                            dto.getAdmissionNumber(), dto.getErrorMessage());
                }
            }

            // Sort valid records by grade
            validRecords.sort(Comparator.comparing(LearnerImportDTO::getGradeName));

            if (!validRecords.isEmpty()) {
                ImportResult result = importLearners(validRecords);
                logImportResults(result);

                // Log invalid records
                if (!invalidRecords.isEmpty()) {
                    logger.warn("Skipped {} invalid records:", invalidRecords.size());
                    for (LearnerImportDTO invalid : invalidRecords) {
                        logger.warn("  {}: {} - {}",
                                invalid.getAdmissionNumber(), invalid.getFullName(), invalid.getErrorMessage());
                    }
                }
            } else {
                logger.error("No valid records to import - all {} records failed validation", learnerDTOs.size());
                if (!invalidRecords.isEmpty()) {
                    logger.error("Validation errors:");
                    for (LearnerImportDTO invalid : invalidRecords) {
                        logger.error("  {}: {}", invalid.getAdmissionNumber(), invalid.getErrorMessage());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error processing Excel data", e);
            throw new RuntimeException("Failed to process Excel data", e);
        }
    }

    private boolean hasValidRecords(List<LearnerImportDTO> learnerDTOs) {
        return learnerDTOs.stream().anyMatch(this::isLearnerValid);
    }

    private void logImportResults(ImportResult result) {
        logger.info("Import results: {} total, {} successful, {} failed",
                result.getTotalRecords(), result.getSuccessfulImports(), result.getFailedImports());

        if (!result.getFailedRecords().isEmpty()) {
            logger.warn("Failed records details:");
            for (LearnerImportDTO failed : result.getFailedRecords()) {
                logger.warn("  {} ({}): {}",
                        failed.getAdmissionNumber(), failed.getFullName(), failed.getErrorMessage());
            }
        }
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string is empty");
        }

        String cleanedDateString = dateString.trim();

        try {
            if (cleanedDateString.contains("00:00:00")) {
                return parseExcelDateString(cleanedDateString);
            }

            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(cleanedDateString, formatter);
                } catch (DateTimeParseException e) {
                    continue;
                }
            }

            return LocalDate.parse(cleanedDateString);

        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable date: " + dateString, e);
        }
    }

    private LocalDate parseExcelDateString(String excelDateString) {
        try {
            String[] parts = excelDateString.split("\\s+");
            if (parts.length >= 5) {
                String month = parts[1];
                String day = parts[2];
                String year = parts[parts.length - 1];

                String monthNum = MONTH_MAP.getOrDefault(month, "01");
                String formattedDate = String.format("%s-%s-%s", year, monthNum, day);

                return LocalDate.parse(formattedDate);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse Excel date with custom method: {}", excelDateString, e);
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
            return LocalDate.parse(excelDateString, formatter);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse Excel date: " + excelDateString, e);
        }
    }

    public boolean validateDatabaseState() {
        try (Connection conn = getConnection()) {
            String schoolSql = "SELECT school_name FROM schools WHERE school_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(schoolSql)) {
                pstmt.setObject(1, currentSchoolId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String schoolName = rs.getString("school_name");
                        logger.info("School validation: OK - School: {}", schoolName);
                        return validateGradesExist(conn);
                    } else {
                        logger.error("School not found with ID: {}", currentSchoolId);
                        return false;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database validation failed", e);
            return false;
        }
    }

    private boolean validateGradesExist(Connection conn) throws SQLException {
        String gradeSql = "SELECT COUNT(*) as count FROM grades WHERE school_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(gradeSql)) {
            pstmt.setObject(1, currentSchoolId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int gradeCount = rs.getInt("count");
                    logger.info("Found {} grades for school", gradeCount);
                    return gradeCount > 0;
                }
            }
        }
        return false;
    }

    private void sleepWithInterruptHandling(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted", e);
        }
    }

    private String getRootCauseMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}