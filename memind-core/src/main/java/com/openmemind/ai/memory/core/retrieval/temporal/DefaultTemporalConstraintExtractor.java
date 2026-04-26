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
package com.openmemind.ai.memory.core.retrieval.temporal;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class DefaultTemporalConstraintExtractor implements TemporalConstraintExtractor {

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern EN_ROLLING =
            Pattern.compile("\\b(past|last|next)\\s+(\\d{1,3})\\s+days?\\b");
    private static final Pattern ZH_ROLLING = Pattern.compile("(过去|近|未来)\\s*(\\d{1,3})\\s*天");

    private final ZoneId zoneId;

    public DefaultTemporalConstraintExtractor() {
        this(ZoneId.systemDefault());
    }

    public DefaultTemporalConstraintExtractor(ZoneId zoneId) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
    }

    @Override
    public Optional<TemporalConstraint> extract(String query, Instant referenceTime) {
        Objects.requireNonNull(referenceTime, "referenceTime");
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        LocalDate referenceDate = LocalDate.ofInstant(referenceTime, zoneId);

        Optional<TemporalConstraint> isoDate = extractIsoDate(normalized, referenceTime);
        if (isoDate.isPresent()) {
            return isoDate;
        }
        Optional<TemporalConstraint> rolling =
                extractRolling(normalized, referenceTime, referenceDate);
        if (rolling.isPresent()) {
            return rolling;
        }
        Optional<TemporalConstraint> day =
                extractRelativeDay(normalized, referenceTime, referenceDate);
        if (day.isPresent()) {
            return day;
        }
        Optional<TemporalConstraint> week =
                extractRelativeWeek(normalized, referenceTime, referenceDate);
        if (week.isPresent()) {
            return week;
        }
        Optional<TemporalConstraint> month =
                extractRelativeMonth(normalized, referenceTime, referenceDate);
        if (month.isPresent()) {
            return month;
        }
        if (containsAny(normalized, "recent", "recently", "lately", "最近")) {
            return Optional.of(
                    constraint(
                            referenceDate.minusDays(7),
                            referenceDate.plusDays(1),
                            referenceTime,
                            TemporalGranularity.RECENT,
                            TemporalDirection.PAST,
                            TemporalConstraintSource.RECENT,
                            0.7d));
        }
        return Optional.empty();
    }

    private Optional<TemporalConstraint> extractIsoDate(String query, Instant referenceTime) {
        var matcher = ISO_DATE.matcher(query);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            LocalDate date =
                    LocalDate.of(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3)));
            return Optional.of(
                    constraint(
                            date,
                            date.plusDays(1),
                            referenceTime,
                            TemporalGranularity.DAY,
                            TemporalDirection.RANGE,
                            TemporalConstraintSource.ISO_DATE,
                            1.0d));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<TemporalConstraint> extractRolling(
            String query, Instant referenceTime, LocalDate referenceDate) {
        var en = EN_ROLLING.matcher(query);
        if (en.find()) {
            int days = Integer.parseInt(en.group(2));
            if (days <= 0) {
                return Optional.empty();
            }
            boolean future = "next".equals(en.group(1));
            return Optional.of(rollingConstraint(referenceDate, referenceTime, days, future));
        }
        var zh = ZH_ROLLING.matcher(query);
        if (zh.find()) {
            int days = Integer.parseInt(zh.group(2));
            if (days <= 0) {
                return Optional.empty();
            }
            boolean future = "未来".equals(zh.group(1));
            return Optional.of(rollingConstraint(referenceDate, referenceTime, days, future));
        }
        return Optional.empty();
    }

    private TemporalConstraint rollingConstraint(
            LocalDate referenceDate, Instant referenceTime, int days, boolean future) {
        LocalDate start = future ? referenceDate : referenceDate.minusDays(days - 1L);
        LocalDate end = future ? referenceDate.plusDays(days) : referenceDate.plusDays(1);
        return constraint(
                start,
                end,
                referenceTime,
                TemporalGranularity.RANGE,
                future ? TemporalDirection.FUTURE : TemporalDirection.PAST,
                TemporalConstraintSource.ROLLING_WINDOW,
                1.0d);
    }

    private Optional<TemporalConstraint> extractRelativeDay(
            String query, Instant referenceTime, LocalDate referenceDate) {
        if (containsAny(query, "today", "今天")) {
            return Optional.of(
                    dayConstraint(referenceDate, referenceTime, TemporalDirection.PRESENT));
        }
        if (containsAny(query, "yesterday", "昨天")) {
            return Optional.of(
                    dayConstraint(
                            referenceDate.minusDays(1), referenceTime, TemporalDirection.PAST));
        }
        if (query.contains("前天")) {
            return Optional.of(
                    dayConstraint(
                            referenceDate.minusDays(2), referenceTime, TemporalDirection.PAST));
        }
        if (containsAny(query, "tomorrow", "明天")) {
            return Optional.of(
                    dayConstraint(
                            referenceDate.plusDays(1), referenceTime, TemporalDirection.FUTURE));
        }
        if (query.contains("后天")) {
            return Optional.of(
                    dayConstraint(
                            referenceDate.plusDays(2), referenceTime, TemporalDirection.FUTURE));
        }
        return Optional.empty();
    }

    private TemporalConstraint dayConstraint(
            LocalDate date, Instant referenceTime, TemporalDirection direction) {
        return constraint(
                date,
                date.plusDays(1),
                referenceTime,
                TemporalGranularity.DAY,
                direction,
                TemporalConstraintSource.RELATIVE_DAY,
                1.0d);
    }

    private Optional<TemporalConstraint> extractRelativeWeek(
            String query, Instant referenceTime, LocalDate referenceDate) {
        LocalDate weekStart =
                referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (query.contains("this week") || containsStandaloneChinese(query, "本周")) {
            return Optional.of(weekConstraint(weekStart, referenceTime, TemporalDirection.PRESENT));
        }
        if (query.contains("last week") || containsStandaloneChinese(query, "上周")) {
            return Optional.of(
                    weekConstraint(weekStart.minusWeeks(1), referenceTime, TemporalDirection.PAST));
        }
        if (query.contains("next week") || containsStandaloneChinese(query, "下周")) {
            return Optional.of(
                    weekConstraint(
                            weekStart.plusWeeks(1), referenceTime, TemporalDirection.FUTURE));
        }
        return Optional.empty();
    }

    private TemporalConstraint weekConstraint(
            LocalDate start, Instant referenceTime, TemporalDirection direction) {
        return constraint(
                start,
                start.plusWeeks(1),
                referenceTime,
                TemporalGranularity.WEEK,
                direction,
                TemporalConstraintSource.RELATIVE_WEEK,
                1.0d);
    }

    private Optional<TemporalConstraint> extractRelativeMonth(
            String query, Instant referenceTime, LocalDate referenceDate) {
        YearMonth month = YearMonth.from(referenceDate);
        if (query.contains("this month") || containsStandaloneChinese(query, "本月")) {
            return Optional.of(monthConstraint(month, referenceTime, TemporalDirection.PRESENT));
        }
        if (query.contains("last month") || containsStandaloneChinese(query, "上个月")) {
            return Optional.of(
                    monthConstraint(month.minusMonths(1), referenceTime, TemporalDirection.PAST));
        }
        if (query.contains("next month") || containsStandaloneChinese(query, "下个月")) {
            return Optional.of(
                    monthConstraint(month.plusMonths(1), referenceTime, TemporalDirection.FUTURE));
        }
        return Optional.empty();
    }

    private TemporalConstraint monthConstraint(
            YearMonth month, Instant referenceTime, TemporalDirection direction) {
        return constraint(
                month.atDay(1),
                month.plusMonths(1).atDay(1),
                referenceTime,
                TemporalGranularity.MONTH,
                direction,
                TemporalConstraintSource.RELATIVE_MONTH,
                1.0d);
    }

    private TemporalConstraint constraint(
            LocalDate start,
            LocalDate end,
            Instant referenceTime,
            TemporalGranularity granularity,
            TemporalDirection direction,
            TemporalConstraintSource source,
            double confidence) {
        return new TemporalConstraint(
                start.atStartOfDay(zoneId).toInstant(),
                end.atStartOfDay(zoneId).toInstant(),
                referenceTime,
                granularity,
                direction,
                source,
                confidence);
    }

    private static boolean containsAny(String query, String... patterns) {
        for (String pattern : patterns) {
            if (query.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsStandaloneChinese(String query, String phrase) {
        int index = query.indexOf(phrase);
        if (index < 0) {
            return false;
        }
        int end = index + phrase.length();
        if (end >= query.length()) {
            return true;
        }
        char next = query.charAt(end);
        return next != '一'
                && next != '二'
                && next != '三'
                && next != '四'
                && next != '五'
                && next != '六'
                && next != '日'
                && next != '天';
    }
}
