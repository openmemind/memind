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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DefaultTemporalConstraintExtractorTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Instant REF = Instant.parse("2026-04-26T10:15:30Z");
    private final DefaultTemporalConstraintExtractor extractor =
            new DefaultTemporalConstraintExtractor(ZONE);

    @Test
    void extractsEnglishTomorrowAsFutureDay() {
        var result = extractor.extract("what is my plan tomorrow?", REF);

        assertTrue(result.isPresent());
        var constraint = result.orElseThrow();
        assertEquals(Instant.parse("2026-04-27T00:00:00Z"), constraint.startInclusive());
        assertEquals(Instant.parse("2026-04-28T00:00:00Z"), constraint.endExclusive());
        assertEquals(TemporalDirection.FUTURE, constraint.direction());
        assertEquals(TemporalGranularity.DAY, constraint.granularity());
        assertEquals(TemporalConstraintSource.RELATIVE_DAY, constraint.source());
        assertEquals(1.0d, constraint.confidence());
    }

    @Test
    void extractsChineseYesterdayAsPastDay() {
        var result = extractor.extract("我昨天做了什么", REF);

        assertTrue(result.isPresent());
        var constraint = result.orElseThrow();
        assertEquals(Instant.parse("2026-04-25T00:00:00Z"), constraint.startInclusive());
        assertEquals(Instant.parse("2026-04-26T00:00:00Z"), constraint.endExclusive());
        assertEquals(TemporalDirection.PAST, constraint.direction());
        assertEquals(TemporalGranularity.DAY, constraint.granularity());
    }

    @Test
    void returnsEmptyForUnsupportedWeekdayExpression() {
        assertTrue(extractor.extract("what did I do last Monday?", REF).isEmpty());
        assertTrue(extractor.extract("上周三我聊了什么", REF).isEmpty());
    }

    @Test
    void extractsRollingFutureWindow() {
        var result = extractor.extract("未来 3 天有什么计划", REF);

        assertTrue(result.isPresent());
        var constraint = result.orElseThrow();
        assertEquals(Instant.parse("2026-04-26T00:00:00Z"), constraint.startInclusive());
        assertEquals(Instant.parse("2026-04-29T00:00:00Z"), constraint.endExclusive());
        assertEquals(TemporalDirection.FUTURE, constraint.direction());
        assertEquals(TemporalConstraintSource.ROLLING_WINDOW, constraint.source());
    }

    @Test
    void extractsIsoDateAsRange() {
        var result = extractor.extract("show memories from 2026-03-12", REF);

        assertTrue(result.isPresent());
        var constraint = result.orElseThrow();
        assertEquals(Instant.parse("2026-03-12T00:00:00Z"), constraint.startInclusive());
        assertEquals(Instant.parse("2026-03-13T00:00:00Z"), constraint.endExclusive());
        assertEquals(TemporalDirection.RANGE, constraint.direction());
        assertEquals(TemporalConstraintSource.ISO_DATE, constraint.source());
    }
}
