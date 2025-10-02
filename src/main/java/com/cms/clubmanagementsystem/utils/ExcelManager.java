package com.cms.clubmanagementsystem.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExcelManager {

    public static void addLearnerToExcel(String excelFilePath, String admissionNumber,
                                         String fullName, String gradeName,
                                         String dateJoinedSchool, String gender) {
        try {
            File file = new File(excelFilePath);
            Workbook workbook;
            Sheet sheet;

            if (file.exists()) {
                workbook = new XSSFWorkbook(new FileInputStream(file));
                sheet = workbook.getSheetAt(0);
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("Learners");

                // Create header row
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("admission_number");
                headerRow.createCell(1).setCellValue("full_name");
                headerRow.createCell(2).setCellValue("grade_name");
                headerRow.createCell(3).setCellValue("date_joined_school");
                headerRow.createCell(4).setCellValue("gender");
            }

            // Create new row
            int lastRowNum = sheet.getLastRowNum();
            Row newRow = sheet.createRow(lastRowNum + 1);

            newRow.createCell(0).setCellValue(admissionNumber);
            newRow.createCell(1).setCellValue(fullName);
            newRow.createCell(2).setCellValue(gradeName);
            newRow.createCell(3).setCellValue(dateJoinedSchool);
            newRow.createCell(4).setCellValue(gender);

            // Write to file
            try (FileOutputStream outputStream = new FileOutputStream(excelFilePath)) {
                workbook.write(outputStream);
            }

            workbook.close();

        } catch (IOException e) {
            throw new RuntimeException("Failed to add learner to Excel: " + e.getMessage(), e);
        }
    }

    public static void removeLearnerFromExcel(String excelFilePath, String admissionNumber) {
        try {
            File file = new File(excelFilePath);
            if (!file.exists()) {
                return;
            }

            Workbook workbook = new XSSFWorkbook(new FileInputStream(file));
            Sheet sheet = workbook.getSheetAt(0);
            List<Row> rowsToKeep = new ArrayList<>();

            // Keep header and all rows except the one with matching admission number
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowsToKeep.add(rowIterator.next()); // Keep header
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Cell admissionCell = row.getCell(0);
                if (admissionCell != null &&
                        admissionCell.getCellType() == CellType.STRING &&
                        !admissionCell.getStringCellValue().equals(admissionNumber)) {
                    rowsToKeep.add(row);
                }
            }

            // Create new sheet with kept rows
            Workbook newWorkbook = new XSSFWorkbook();
            Sheet newSheet = newWorkbook.createSheet("Learners");

            for (int i = 0; i < rowsToKeep.size(); i++) {
                Row oldRow = rowsToKeep.get(i);
                Row newRow = newSheet.createRow(i);

                for (int j = 0; j < 5; j++) { // Assuming 5 columns
                    Cell oldCell = oldRow.getCell(j);
                    if (oldCell != null) {
                        Cell newCell = newRow.createCell(j);
                        setCellValue(newCell, oldCell);
                    }
                }
            }

            // Write to file
            try (FileOutputStream outputStream = new FileOutputStream(excelFilePath)) {
                newWorkbook.write(outputStream);
            }

            workbook.close();
            newWorkbook.close();

        } catch (IOException e) {
            throw new RuntimeException("Failed to remove learner from Excel: " + e.getMessage(), e);
        }
    }

    public static void updateLearnerInExcel(String excelFilePath, String admissionNumber,
                                            String fullName, String gradeName,
                                            String dateJoinedSchool, String gender) {
        try {
            File file = new File(excelFilePath);
            if (!file.exists()) {
                return;
            }

            Workbook workbook = new XSSFWorkbook(new FileInputStream(file));
            Sheet sheet = workbook.getSheetAt(0);
            boolean found = false;

            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowIterator.next(); // Skip header
            }

            while (rowIterator.hasNext() && !found) {
                Row row = rowIterator.next();
                Cell admissionCell = row.getCell(0);

                if (admissionCell != null &&
                        admissionCell.getCellType() == CellType.STRING &&
                        admissionCell.getStringCellValue().equals(admissionNumber)) {

                    // Update the row
                    row.getCell(1).setCellValue(fullName);
                    row.getCell(2).setCellValue(gradeName);
                    row.getCell(3).setCellValue(dateJoinedSchool);
                    row.getCell(4).setCellValue(gender);

                    found = true;
                }
            }

            if (found) {
                // Write to file
                try (FileOutputStream outputStream = new FileOutputStream(excelFilePath)) {
                    workbook.write(outputStream);
                }
            }

            workbook.close();

        } catch (IOException e) {
            throw new RuntimeException("Failed to update learner in Excel: " + e.getMessage(), e);
        }
    }

    private static void setCellValue(Cell newCell, Cell oldCell) {
        switch (oldCell.getCellType()) {
            case STRING:
                newCell.setCellValue(oldCell.getStringCellValue());
                break;
            case NUMERIC:
                newCell.setCellValue(oldCell.getNumericCellValue());
                break;
            case BOOLEAN:
                newCell.setCellValue(oldCell.getBooleanCellValue());
                break;
            case FORMULA:
                newCell.setCellFormula(oldCell.getCellFormula());
                break;
            default:
                newCell.setCellValue("");
        }
    }

    public static List<String[]> readAllLearners(String excelFilePath) {
        List<String[]> learners = new ArrayList<>();

        try {
            File file = new File(excelFilePath);
            if (!file.exists()) {
                return learners;
            }

            Workbook workbook = new XSSFWorkbook(new FileInputStream(file));
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String[] learnerData = new String[5];

                for (int i = 0; i < 5; i++) {
                    Cell cell = row.getCell(i);
                    learnerData[i] = (cell != null) ? cell.toString() : "";
                }

                learners.add(learnerData);
            }

            workbook.close();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read learners from Excel: " + e.getMessage(), e);
        }

        return learners;
    }
}