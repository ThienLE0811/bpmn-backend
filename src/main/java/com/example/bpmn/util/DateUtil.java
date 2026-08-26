package com.example.bpmn.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Common utility for date and time formatting and parsing.
 */
public class DateUtil {
    public static final String DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss";
    public static final String DATE_PATTERN = "dd/MM/yyyy";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private DateUtil() {
    }

    /**
     * Format LocalDateTime to "dd/MM/yyyy HH:mm:ss"
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * Parse text in format "dd/MM/yyyy HH:mm:ss" to LocalDateTime
     */
    public static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
    }

    /**
     * Format LocalDate to "dd/MM/yyyy"
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DATE_FORMATTER);
    }

    /**
     * Parse text in format "dd/MM/yyyy" to LocalDate
     */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text, DATE_FORMATTER);
    }
}
