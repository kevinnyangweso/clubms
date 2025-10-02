package com.cms.clubmanagementsystem.utils;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PdfReportGenerator {

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
        String filename = "attendance_report_" + timestamp + ".pdf";
        String filePath = reportsDir.resolve(filename).toString();

        // Initialize PDF writer and document
        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Create fonts
        PdfFont headerFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Add title
        Paragraph title = new Paragraph(reportTitle)
                .setFont(headerFont)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(title);

        // Add report metadata
        Paragraph metadata = new Paragraph()
                .setFont(normalFont)
                .setFontSize(10)
                .add("School: " + schoolName + "\n")
                .add("Time Period: " + timePeriod + "\n")
                .add("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        document.add(metadata);

        document.add(new Paragraph("\n"));

        // Create table
        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 25, 25}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(20);

        // Add table headers
        table.addHeaderCell(createHeaderCell("Club Name", headerFont));
        table.addHeaderCell(createHeaderCell("Total Records", headerFont));
        table.addHeaderCell(createHeaderCell("Attendance Rate", headerFont));

        // Add table data
        for (Map<String, Object> row : reportData) {
            table.addCell(createCell(row.get("club_name").toString(), normalFont));
            table.addCell(createCell(row.get("total_records").toString(), normalFont));
            table.addCell(createCell(String.format("%.2f%%", row.get("attendance_rate")), normalFont));
        }

        document.add(table);

        // Add summary
        if (!reportData.isEmpty()) {
            document.add(new Paragraph("\n"));
            double avgAttendance = reportData.stream()
                    .mapToDouble(row -> (Double) row.get("attendance_rate"))
                    .average()
                    .orElse(0.0);

            Paragraph summary = new Paragraph("Summary Statistics")
                    .setFont(headerFont)
                    .setFontSize(12)
                    .setMarginTop(20);
            document.add(summary);

            Paragraph stats = new Paragraph()
                    .setFont(normalFont)
                    .setFontSize(10)
                    .add(String.format("Total Clubs: %d\n", reportData.size()))
                    .add(String.format("Average Attendance Rate: %.2f%%", avgAttendance));
            document.add(stats);
        }

        document.close();

        return filePath;
    }

    private static Cell createHeaderCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(12))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setBold();
    }

    private static Cell createCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(10))
                .setTextAlignment(TextAlignment.CENTER);
    }
}