package com.cms.clubmanagementsystem.model;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    private int totalRecords;
    private int successfulImports;
    private int failedImports;
    private List<LearnerImportDTO> failedRecords;

    // Add constructor
    public ImportResult() {
        this.failedRecords = new ArrayList<>();
    }

    // Getters and setters
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getSuccessfulImports() { return successfulImports; }
    public void setSuccessfulImports(int successfulImports) {
        this.successfulImports = successfulImports;
    }

    public int getFailedImports() { return failedImports; }
    public void setFailedImports(int failedImports) {
        this.failedImports = failedImports;
    }

    public List<LearnerImportDTO> getFailedRecords() { return failedRecords; }
    public void setFailedRecords(List<LearnerImportDTO> failedRecords) {
        this.failedRecords = failedRecords;
    }
}