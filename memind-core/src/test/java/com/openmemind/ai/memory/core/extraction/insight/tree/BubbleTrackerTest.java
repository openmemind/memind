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
package com.openmemind.ai.memory.core.extraction.insight.tree;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BubbleTracker")
class BubbleTrackerTest {

    private BubbleTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new BubbleTracker();
    }

    @Nested
    @DisplayName("Branch dirty tracking")
    class BranchTracking {

        @Test
        @DisplayName("Do not trigger re-summarize when below threshold")
        void shouldNotBubbleWhenBelowThreshold() {
            tracker.markDirty("test-memory::type-a");
            tracker.markDirty("test-memory::type-a");
            assertThat(tracker.shouldResummarize("test-memory::type-a", 5)).isFalse();
        }

        @Test
        @DisplayName("Should trigger re-summarize when threshold reached")
        void shouldBubbleWhenThresholdReached() {
            for (int i = 0; i < 5; i++) {
                tracker.markDirty("test-memory::type-a");
            }
            assertThat(tracker.shouldResummarize("test-memory::type-a", 5)).isTrue();
        }

        @Test
        @DisplayName("Dirty count should reset to zero after re-summarize")
        void shouldResetAfterResummarize() {
            for (int i = 0; i < 5; i++) {
                tracker.markDirty("test-memory::type-a");
            }
            tracker.reset("test-memory::type-a");
            assertThat(tracker.shouldResummarize("test-memory::type-a", 5)).isFalse();
            assertThat(tracker.getDirtyCount("test-memory::type-a")).isZero();
        }

        @Test
        @DisplayName("Dirty count of different nodes should be independent")
        void shouldTrackIndependently() {
            tracker.markDirty("test-memory::type-a");
            tracker.markDirty("test-memory::type-a");
            tracker.markDirty("test-memory::type-b");

            assertThat(tracker.getDirtyCount("test-memory::type-a")).isEqualTo(2);
            assertThat(tracker.getDirtyCount("test-memory::type-b")).isEqualTo(1);
        }

        @Test
        @DisplayName("Dirty count of untracked node should be 0")
        void shouldReturnZeroForUntrackedNode() {
            assertThat(tracker.getDirtyCount("unknown-key")).isZero();
            assertThat(tracker.shouldResummarize("unknown-key", 5)).isFalse();
        }
    }
}
