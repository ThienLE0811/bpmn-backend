package com.example.bpmn.engine;

import com.example.bpmn.exception.AppException;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed ISO-8601 repeating interval, as used by a BPMN {@code timeCycle} (e.g. {@code R3/PT10M}
 * for 3 repeats of 10 minutes, {@code R/PT10M} for an unbounded repeat). Only the
 * {@code R<n?>/<duration>} form is supported - the alternate ISO-8601 forms with a start/end
 * date instead of a duration are not.
 */
public record TimerCycle(Integer repeatCount, Duration interval) {
    private static final Pattern PATTERN = Pattern.compile("^R(\\d*)/(P.*)$");

    /** {@code repeatCount} is null when the cycle is unbounded (e.g. {@code R/PT10M}). */
    public TimerCycle {
        if (repeatCount != null && repeatCount <= 0) {
            throw new AppException("timeCycle repeat count must be positive, got: " + repeatCount, 400);
        }
    }

    public static TimerCycle parse(String raw) {
        if (raw == null) {
            throw new AppException("timeCycle value is missing", 400);
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new AppException("Invalid timeCycle value: " + raw
                    + " - expected an ISO-8601 repeating interval, e.g. R3/PT10M or R/PT10M", 400);
        }
        String countPart = matcher.group(1);
        Integer repeatCount = countPart.isEmpty() ? null : Integer.parseInt(countPart);
        try {
            Duration interval = Duration.parse(matcher.group(2));
            return new TimerCycle(repeatCount, interval);
        } catch (java.time.format.DateTimeParseException e) {
            throw new AppException("Invalid timeCycle duration in: " + raw, 400);
        }
    }
}
