/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.core.extraction.item.support;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic temporal normalization for memory-item extraction.
 */
public final class TemporalNormalizer {

    private static final Set<String> BUCKET_GRANULARITIES = Set.of("day", "week", "month", "year");
    private static final DateTimeFormatter ENGLISH_MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter ENGLISH_SHORT_MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern ISO_MONTH_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern ISO_YEAR_PATTERN = Pattern.compile("^(\\d{4})$");
    private static final Pattern CHINESE_YEAR_MONTH_PATTERN =
            Pattern.compile("^(?:(今年)|(\\d{4})年)\\s*(\\d{1,2})月$");

    private TemporalNormalizer() {}

    public static ExtractedTemporal normalize(
            MemoryItemExtractionResponse.ExtractedTime time,
            String legacyOccurredAt,
            Instant referenceTime) {
        if (hasStructuredTemporal(time)) {
            return normalizeStructuredTime(time, referenceTime, ZoneId.systemDefault());
        }

        Instant occurredAt = parseInstant(legacyOccurredAt);
        if (occurredAt != null) {
            return new ExtractedTemporal(occurredAt, null, "unknown", null, occurredAt);
        }
        return empty(null, null);
    }

    static ExtractedTemporal normalizeStructuredTime(
            MemoryItemExtractionResponse.ExtractedTime time,
            Instant referenceTime,
            ZoneId systemZone) {
        String expression = normalizeBlank(time.expression());
        String granularity = normalizeGranularity(time.granularity());
        Instant start = parseInstant(time.start());
        Instant end = parseInstant(time.end());

        if (granularity == null && start != null && end != null) {
            granularity = inferGranularityFromBoundaries(start, end, systemZone);
        }
        if (granularity == null && expression != null) {
            granularity = inferGranularityFromExpression(expression);
        }

        if (start != null && end != null) {
            return normalizeStartEnd(
                    start, end, granularity, expression, referenceTime, systemZone);
        }
        if (start != null) {
            return normalizeStartOnly(start, granularity, expression, referenceTime, systemZone);
        }
        if (expression != null) {
            return parseExpression(expression, granularity, referenceTime, systemZone);
        }
        return empty(null, null);
    }

    private static ExtractedTemporal normalizeStartEnd(
            Instant start,
            Instant end,
            String granularity,
            String expression,
            Instant referenceTime,
            ZoneId systemZone) {
        if (!start.isBefore(end)) {
            return empty(expression, granularity);
        }
        if ("point".equals(granularity)) {
            return point(start, expression, "point");
        }
        if ("range".equals(granularity)) {
            return interval(start, end, expression, "range");
        }
        if (BUCKET_GRANULARITIES.contains(granularity)) {
            if (isCanonicalBucket(start, end, granularity, systemZone)) {
                return interval(start, end, expression, granularity);
            }
            if (expression != null) {
                ExtractedTemporal reparsed =
                        parseExpression(expression, granularity, referenceTime, systemZone);
                if (reparsed.occurredStart() != null && reparsed.occurredEnd() != null) {
                    return reparsed;
                }
            }
            return empty(expression, granularity);
        }
        return interval(start, end, expression, granularity != null ? granularity : "range");
    }

    private static ExtractedTemporal normalizeStartOnly(
            Instant start,
            String granularity,
            String expression,
            Instant referenceTime,
            ZoneId systemZone) {
        if ("point".equals(granularity) || granularity == null) {
            return point(start, expression, granularity);
        }
        if (BUCKET_GRANULARITIES.contains(granularity)) {
            if (isCanonicalBucketStart(start, granularity, systemZone)) {
                return interval(
                        start,
                        advanceBucket(start, granularity, systemZone),
                        expression,
                        granularity);
            }
            if (expression != null) {
                return parseExpression(expression, granularity, referenceTime, systemZone);
            }
            return empty(expression, granularity);
        }
        return point(start, expression, granularity);
    }

    private static ExtractedTemporal parseExpression(
            String expression, String granularity, Instant referenceTime, ZoneId systemZone) {
        String normalizedExpression = expression.trim();

        if (matchesAny(normalizedExpression, "昨天", "today", "yesterday", "tomorrow", "今天", "明天")) {
            return resolveRelativeDay(normalizedExpression, referenceTime, systemZone);
        }
        if (matchesAny(
                normalizedExpression,
                "上周",
                "这周",
                "本周",
                "下周",
                "last week",
                "this week",
                "next week")) {
            return resolveRelativeWeek(normalizedExpression, referenceTime, systemZone);
        }
        if (matchesAny(
                normalizedExpression,
                "上个月",
                "这个月",
                "本月",
                "下个月",
                "last month",
                "this month",
                "next month")) {
            return resolveRelativeMonth(normalizedExpression, referenceTime, systemZone);
        }

        Matcher isoDateMatcher = ISO_DATE_PATTERN.matcher(normalizedExpression);
        if (isoDateMatcher.matches()) {
            LocalDate localDate = LocalDate.parse(normalizedExpression);
            return dateBucket(localDate, systemZone, "day", expression);
        }

        Matcher isoMonthMatcher = ISO_MONTH_PATTERN.matcher(normalizedExpression);
        if (isoMonthMatcher.matches()) {
            YearMonth yearMonth = YearMonth.parse(normalizedExpression);
            return monthBucket(yearMonth, systemZone, expression);
        }

        Matcher chineseYearMonthMatcher = CHINESE_YEAR_MONTH_PATTERN.matcher(normalizedExpression);
        if (chineseYearMonthMatcher.matches()) {
            Integer year = null;
            if (chineseYearMonthMatcher.group(1) != null) {
                if (referenceTime == null) {
                    return empty(expression, granularity != null ? granularity : "month");
                }
                year = referenceTime.atZone(systemZone).getYear();
            }
            if (chineseYearMonthMatcher.group(2) != null) {
                year = Integer.parseInt(chineseYearMonthMatcher.group(2));
            }
            int month = Integer.parseInt(chineseYearMonthMatcher.group(3));
            return monthBucket(YearMonth.of(year, month), systemZone, expression);
        }

        YearMonth parsedEnglishMonth = parseEnglishMonthYear(normalizedExpression);
        if (parsedEnglishMonth != null) {
            return monthBucket(parsedEnglishMonth, systemZone, expression);
        }

        Matcher isoYearMatcher = ISO_YEAR_PATTERN.matcher(normalizedExpression);
        if (isoYearMatcher.matches()) {
            return yearBucket(Year.parse(normalizedExpression), systemZone, expression);
        }

        return empty(expression, granularity);
    }

    private static ExtractedTemporal resolveRelativeDay(
            String expression, Instant referenceTime, ZoneId systemZone) {
        if (referenceTime == null) {
            return empty(expression, "day");
        }
        LocalDate referenceDate = referenceTime.atZone(systemZone).toLocalDate();
        LocalDate targetDate =
                switch (expression.toLowerCase(Locale.ROOT)) {
                    case "昨天", "yesterday" -> referenceDate.minusDays(1);
                    case "明天", "tomorrow" -> referenceDate.plusDays(1);
                    default -> referenceDate;
                };
        return dateBucket(targetDate, systemZone, "day", expression);
    }

    private static ExtractedTemporal resolveRelativeWeek(
            String expression, Instant referenceTime, ZoneId systemZone) {
        if (referenceTime == null) {
            return empty(expression, "week");
        }
        LocalDate referenceDate = referenceTime.atZone(systemZone).toLocalDate();
        LocalDate monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate targetMonday =
                switch (expression.toLowerCase(Locale.ROOT)) {
                    case "上周", "last week" -> monday.minusWeeks(1);
                    case "下周", "next week" -> monday.plusWeeks(1);
                    default -> monday;
                };

        ZonedDateTime start = targetMonday.atStartOfDay(systemZone);
        return interval(start.toInstant(), start.plusWeeks(1).toInstant(), expression, "week");
    }

    private static ExtractedTemporal resolveRelativeMonth(
            String expression, Instant referenceTime, ZoneId systemZone) {
        if (referenceTime == null) {
            return empty(expression, "month");
        }
        YearMonth referenceMonth = YearMonth.from(referenceTime.atZone(systemZone));
        YearMonth targetMonth =
                switch (expression.toLowerCase(Locale.ROOT)) {
                    case "上个月", "last month" -> referenceMonth.minusMonths(1);
                    case "下个月", "next month" -> referenceMonth.plusMonths(1);
                    default -> referenceMonth;
                };
        return monthBucket(targetMonth, systemZone, expression);
    }

    private static ExtractedTemporal dateBucket(
            LocalDate localDate, ZoneId systemZone, String granularity, String expression) {
        ZonedDateTime start = localDate.atStartOfDay(systemZone);
        return interval(start.toInstant(), start.plusDays(1).toInstant(), expression, granularity);
    }

    private static ExtractedTemporal monthBucket(
            YearMonth yearMonth, ZoneId systemZone, String expression) {
        ZonedDateTime start = yearMonth.atDay(1).atStartOfDay(systemZone);
        return interval(start.toInstant(), start.plusMonths(1).toInstant(), expression, "month");
    }

    private static ExtractedTemporal yearBucket(Year year, ZoneId systemZone, String expression) {
        ZonedDateTime start = year.atDay(1).atStartOfDay(systemZone);
        return interval(start.toInstant(), start.plusYears(1).toInstant(), expression, "year");
    }

    private static boolean isCanonicalBucket(
            Instant start, Instant end, String granularity, ZoneId zone) {
        if (!isCanonicalBucketStart(start, granularity, zone)) {
            return false;
        }
        return advanceBucket(start, granularity, zone).equals(end);
    }

    private static boolean isCanonicalBucketStart(Instant start, String granularity, ZoneId zone) {
        ZonedDateTime zonedStart = start.atZone(zone);
        if (!LocalTime.MIDNIGHT.equals(zonedStart.toLocalTime())) {
            return false;
        }
        return switch (granularity) {
            case "day" -> true;
            case "week" -> zonedStart.getDayOfWeek() == DayOfWeek.MONDAY;
            case "month" -> zonedStart.getDayOfMonth() == 1;
            case "year" ->
                    zonedStart.getDayOfMonth() == 1 && zonedStart.getMonth() == Month.JANUARY;
            default -> false;
        };
    }

    private static Instant advanceBucket(Instant start, String granularity, ZoneId zone) {
        ZonedDateTime zonedStart = start.atZone(zone);
        return switch (granularity) {
            case "day" -> zonedStart.plusDays(1).toInstant();
            case "week" -> zonedStart.plusWeeks(1).toInstant();
            case "month" -> zonedStart.plusMonths(1).toInstant();
            case "year" -> zonedStart.plusYears(1).toInstant();
            default -> start;
        };
    }

    private static String inferGranularityFromBoundaries(Instant start, Instant end, ZoneId zone) {
        for (String granularity : BUCKET_GRANULARITIES) {
            if (isCanonicalBucket(start, end, granularity, zone)) {
                return granularity;
            }
        }
        return start.isBefore(end) ? "range" : null;
    }

    private static String inferGranularityFromExpression(String expression) {
        String normalized = expression.toLowerCase(Locale.ROOT);
        if (normalized.contains("周") || normalized.contains("week")) {
            return normalized.contains("到") || normalized.contains("to") ? "range" : "week";
        }
        if (normalized.contains("月")
                || normalized.contains("month")
                || ISO_MONTH_PATTERN.matcher(normalized).matches()) {
            return "month";
        }
        if (normalized.contains("年") || ISO_YEAR_PATTERN.matcher(normalized).matches()) {
            return "year";
        }
        if (normalized.contains("天")
                || normalized.contains("day")
                || matchesAny(normalized, "昨天", "今天", "明天", "yesterday", "today", "tomorrow")) {
            return "day";
        }
        return null;
    }

    private static YearMonth parseEnglishMonthYear(String expression) {
        try {
            return YearMonth.parse(expression, ENGLISH_MONTH_FORMAT);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return YearMonth.parse(expression, ENGLISH_SHORT_MONTH_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static ExtractedTemporal interval(
            Instant start, Instant end, String expression, String granularity) {
        return new ExtractedTemporal(
                start, end, granularity, expression, compatibilityOccurredAt(granularity, start));
    }

    private static ExtractedTemporal point(
            Instant occurredAt, String expression, String granularity) {
        return new ExtractedTemporal(
                occurredAt,
                null,
                granularity != null ? granularity : "point",
                expression,
                compatibilityOccurredAt("point", occurredAt));
    }

    private static ExtractedTemporal empty(String expression, String granularity) {
        return new ExtractedTemporal(null, null, granularity, expression, null);
    }

    private static Instant compatibilityOccurredAt(String granularity, Instant start) {
        if (start == null) {
            return null;
        }
        return switch (granularity) {
            case "point", "day", "unknown" -> start;
            default -> null;
        };
    }

    private static boolean hasStructuredTemporal(MemoryItemExtractionResponse.ExtractedTime time) {
        return time != null
                && (normalizeBlank(time.expression()) != null
                        || normalizeBlank(time.start()) != null
                        || normalizeBlank(time.end()) != null
                        || normalizeBlank(time.granularity()) != null);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String normalizeGranularity(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean matchesAny(String candidate, String... values) {
        String normalized = candidate.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (normalized.equals(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
