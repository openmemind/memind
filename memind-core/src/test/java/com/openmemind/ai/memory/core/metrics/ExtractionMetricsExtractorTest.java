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
package com.openmemind.ai.memory.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtractionMetricsExtractorTest {

    @Test
    void extractsRawDataSegmentItemAndInsightCounts() {
        var result =
                ExtractionResult.success(
                        DefaultMemoryId.of("user", "agent"),
                        new RawDataResult(
                                List.of(), List.of(parsedSegment(), parsedSegment()), false),
                        MemoryItemResult.empty(),
                        InsightResult.empty(),
                        Duration.ofMillis(10));

        ExtractionMetrics metrics = ExtractionMetricsExtractor.extract(result, "core");

        assertThat(metrics.status()).isEqualTo("success");
        assertThat(metrics.rawDataCount()).isZero();
        assertThat(metrics.segmentCount()).isEqualTo(2);
        assertThat(metrics.newItemCount()).isZero();
        assertThat(metrics.reinforcedItemCount()).isNull();
        assertThat(metrics.insightCount()).isZero();
        assertThat(metrics.graphRelationCount()).isZero();
        assertThat(metrics.source()).isEqualTo("core");
    }

    @Test
    void sumsFinalRelationStatsWithoutMixingLowerLevelCreatedLinkCounters() {
        var stats =
                ItemGraphMaterializationResult.Stats.empty()
                        .withFinalRelationStats(
                                new ItemGraphMaterializationResult.Stats.FinalRelationStats(
                                        1, 2, 3, 4));
        var graphResult = new ItemGraphMaterializationResult(stats);
        var result =
                ExtractionResult.success(
                        DefaultMemoryId.of("user", "agent"),
                        RawDataResult.empty(),
                        new MemoryItemResult(List.of(), List.of(), graphResult),
                        InsightResult.empty(),
                        Duration.ofMillis(10));

        ExtractionMetrics metrics = ExtractionMetricsExtractor.extract(result, "core");

        assertThat(metrics.graphEntityCount()).isEqualTo(0);
        assertThat(metrics.graphMentionCount()).isEqualTo(0);
        assertThat(metrics.graphRelationCount()).isEqualTo(10);
    }

    @Test
    void handlesFailedExtractionWithoutGraphOrItemResults() {
        var result =
                ExtractionResult.failed(DefaultMemoryId.of("user", "agent"), Duration.ZERO, "boom");

        ExtractionMetrics metrics = ExtractionMetricsExtractor.extract(result, "api");

        assertThat(metrics.status()).isEqualTo("failed");
        assertThat(metrics.rawDataCount()).isZero();
        assertThat(metrics.segmentCount()).isNull();
        assertThat(metrics.newItemCount()).isZero();
        assertThat(metrics.reinforcedItemCount()).isNull();
        assertThat(metrics.graphRelationCount()).isNull();
        assertThat(metrics.source()).isEqualTo("api");
    }

    private static ParsedSegment parsedSegment() {
        return new ParsedSegment("segment", null, 0, 7, null, Map.of());
    }
}
