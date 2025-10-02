package com.cms.clubmanagementsystem.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ExcelReportGenerator {

    public static String generateAttendanceReport(List<Map<String, Object>> reportData,
                                                  String reportTitle,
                                                  String timePeriod,
                                                  String schoolName) throws IOException {
        // Create reports directory if it doesn't exist
        Path reportsDir = Paths.get("reports");
        if (!Files.exists(reportsDir)) {
            Files.createDirectories(reportsDir);
        }

        // Generate filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "attendance_report_" + timestamp + ".xlsx";
        String filePath = reportsDir.resolve(filename).toString();

        // Create workbook and sheet
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Attendance Report");

        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);

        int rowNum = 0;

        // Add title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(reportTitle);
        titleCell.setCellStyle(titleStyle);

        // Add metadata
        Row schoolRow = sheet.createRow(rowNum++);
        schoolRow.createCell(0).setCellValue("School: " + schoolName);

        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(0).setCellValue("Time Period: " + timePeriod);

        Row dateRow = sheet.createRow(rowNum++);
        dateRow.createCell(0).setCellValue("Generated: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        rowNum++; // Empty row

        // Add table headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Club Name", "Total Records", "Attendance Rate (%)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add table data
        for (Map<String, Object> row : reportData) {
            Row dataRow = sheet.createRow(rowNum++);

            dataRow.createCell(0).setCellValue(row.get("club_name").toString());
            dataRow.createCell(1).setCellValue(row.get("total_records").toString());

            Cell rateCell = dataRow.createCell(2);
            double attendanceRate = (Double) row.get("attendance_rate");
            rateCell.setCellValue(attendanceRate);
            rateCell.setCellStyle(dataStyle);
        }

        // Add summary
        if (!reportData.isEmpty()) {
            rowNum++; // Empty row

            Row summaryHeaderRow = sheet.createRow(rowNum++);
            summaryHeaderRow.createCell(0).setCellValue("Summary Statistics");
            summaryHeaderRow.getCell(0).setCellStyle(headerStyle);

            double avgAttendance = reportData.stream()
                    .mapToDouble(row -> (Double) row.get("attendance_rate"))
                    .average()
                    .orElse(0.0);

            Row totalClubsRow = sheet.createRow(rowNum++);
            totalClubsRow.createCell(0).setCellValue("Total Clubs:");
            totalClubsRow.createCell(1).setCellValue(reportData.size());

            Row avgAttendanceRow = sheet.createRow(rowNum++);
            avgAttendanceRow.createCell(0).setCellValue("Average Attendance Rate:");
            Cell avgCell = avgAttendanceRow.createCell(1);
            avgCell.setCellValue(avgAttendance);
            avgCell.setCellStyle(dataStyle);
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write to file
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            workbook.write(outputStream);
        }

        workbook.close();

        return filePath;
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }
}