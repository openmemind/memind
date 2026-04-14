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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ToolCallStatsAggregator")
class ToolCallStatsAggregatorTest {

    @Nested
    @DisplayName("aggregate")
    class Aggregate {

        @Test
        @DisplayName("Empty list should return zero stats")
        void emptyListReturnsZeroStats() {
            var stats = ToolCallStatsAggregator.aggregate(List.of(), 20);
            assertThat(stats.totalCalls()).isZero();
            assertThat(stats.successRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("When all succeed, the success rate should be 1.0")
        void allSuccessReturnsFullRate() {
            var records = List.of(makeRecord("success", 1000L), makeRecord("success", 2000L));
            var stats = ToolCallStatsAggregator.aggregate(records, 20);
            assertThat(stats.totalCalls()).isEqualTo(2);
            assertThat(stats.successRate()).isEqualTo(1.0);
            assertThat(stats.avgTimeCost()).isCloseTo(1500.0, within(0.01));
        }

        @Test
        @DisplayName("recentN limit should only analyze the most recent N records")
        void recentNLimitsAnalysis() {
            var records =
                    List.of(
                            makeRecord("error", 1000L),
                            makeRecord("success", 2000L),
                            makeRecord("success", 3000L));
            var stats = ToolCallStatsAggregator.aggregate(records, 2);
            assertThat(stats.totalCalls()).isEqualTo(3);
            assertThat(stats.recentCallsAnalyzed()).isEqualTo(2);
            assertThat(stats.successRate()).isEqualTo(1.0);
        }
    }

    private static ToolCallRecord makeRecord(String status, long durationMs) {
        return new ToolCallRecord(
                "tool", "input", "output", status, durationMs, 10, 5, "hash", Instant.now());
    }
}
