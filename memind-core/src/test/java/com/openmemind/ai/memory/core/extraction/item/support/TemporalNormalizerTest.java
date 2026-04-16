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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TemporalNormalizerTest {

    private TimeZone originalTimeZone;

    @BeforeEach
    void setSystemZone() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @AfterEach
    void restoreSystemZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void normalizeDayExpressionBuildsHalfOpenInterval() {
        var time =
                new MemoryItemExtractionResponse.ExtractedTime(
                        "昨天", "2026-04-14T16:00:00Z", null, "day");

        var temporal =
                TemporalNormalizer.normalize(time, null, Instant.parse("2026-04-16T10:00:00Z"));

        assertThat(temporal.occurredStart()).isEqualTo(Instant.parse("2026-04-14T16:00:00Z"));
        assertThat(temporal.occurredEnd()).isEqualTo(Instant.parse("2026-04-15T16:00:00Z"));
        assertThat(temporal.granularity()).isEqualTo("day");
        assertThat(temporal.compatibilityOccurredAt())
                .isEqualTo(Instant.parse("2026-04-14T16:00:00Z"));
    }

    @Test
    void normalizeLegacyOccurredAtKeepsUnknownGranularityAndNoSyntheticEnd() {
        var temporal =
                TemporalNormalizer.normalize(
                        null, "2026-04-15T10:15:00Z", Instant.parse("2026-04-16T10:00:00Z"));

        assertThat(temporal.occurredStart()).isEqualTo(Instant.parse("2026-04-15T10:15:00Z"));
        assertThat(temporal.occurredEnd()).isNull();
        assertThat(temporal.granularity()).isEqualTo("unknown");
        assertThat(temporal.compatibilityOccurredAt())
                .isEqualTo(Instant.parse("2026-04-15T10:15:00Z"));
    }

    @Test
    void normalizeRangeRejectsReversedInterval() {
        var time =
                new MemoryItemExtractionResponse.ExtractedTime(
                        "周三到周一", "2026-04-16T00:00:00Z", "2026-04-14T00:00:00Z", "range");

        var temporal =
                TemporalNormalizer.normalize(time, null, Instant.parse("2026-04-16T10:00:00Z"));

        assertThat(temporal.occurredStart()).isNull();
        assertThat(temporal.occurredEnd()).isNull();
        assertThat(temporal.compatibilityOccurredAt()).isNull();
    }

    @Test
    void normalizeChineseMonthYearExpressionBuildsMonthBucket() {
        var time = new MemoryItemExtractionResponse.ExtractedTime("今年3月", null, null, "month");

        var temporal =
                TemporalNormalizer.normalize(time, null, Instant.parse("2026-04-16T10:00:00Z"));

        assertThat(temporal.occurredStart()).isEqualTo(Instant.parse("2026-02-28T16:00:00Z"));
        assertThat(temporal.occurredEnd()).isEqualTo(Instant.parse("2026-03-31T16:00:00Z"));
        assertThat(temporal.granularity()).isEqualTo("month");
        assertThat(temporal.compatibilityOccurredAt()).isNull();
    }

    @Test
    void normalizeRelativeDayUsesSystemZoneBeforePersistingUtc() {
        var time = new MemoryItemExtractionResponse.ExtractedTime("昨天", null, null, "day");

        var temporal =
                TemporalNormalizer.normalize(time, null, Instant.parse("2026-04-15T17:00:00Z"));

        assertThat(temporal.occurredStart()).isEqualTo(Instant.parse("2026-04-14T16:00:00Z"));
        assertThat(temporal.occurredEnd()).isEqualTo(Instant.parse("2026-04-15T16:00:00Z"));
        assertThat(temporal.granularity()).isEqualTo("day");
        assertThat(temporal.compatibilityOccurredAt())
                .isEqualTo(Instant.parse("2026-04-14T16:00:00Z"));
    }

    @Test
    void normalizeMisalignedMonthBucketRecomputesCanonicalSystemZoneInterval() {
        var time =
                new MemoryItemExtractionResponse.ExtractedTime(
                        "今年3月", "2026-03-01T00:00:00Z", "2026-04-01T00:00:00Z", "month");

        var temporal =
                TemporalNormalizer.normalize(time, null, Instant.parse("2026-04-16T10:00:00Z"));

        assertThat(temporal.occurredStart()).isEqualTo(Instant.parse("2026-02-28T16:00:00Z"));
        assertThat(temporal.occurredEnd()).isEqualTo(Instant.parse("2026-03-31T16:00:00Z"));
        assertThat(temporal.granularity()).isEqualTo("month");
        assertThat(temporal.compatibilityOccurredAt()).isNull();
    }
}
