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
package com.openmemind.ai.memory.core.builder;

import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig.ConversationSegmentStrategy;
import java.util.Objects;

/**
 * Core-owned runtime defaults used during memory bootstrap.
 */
public final class MemoryBuildOptions {

    private static final ConversationChunkingConfig DEFAULT_CONVERSATION_CHUNKING =
            new ConversationChunkingConfig(10, ConversationSegmentStrategy.FIXED_SIZE, 20);
    private static final InsightBuildConfig DEFAULT_INSIGHT_BUILD =
            new InsightBuildConfig(3, 2, 8, 2);
    private static final CommitDetectorConfig DEFAULT_BOUNDARY_DETECTOR =
            CommitDetectorConfig.defaults();

    private final ConversationChunkingConfig conversationChunking;
    private final InsightBuildConfig insightBuild;
    private final CommitDetectorConfig boundaryDetector;

    private MemoryBuildOptions(Builder builder) {
        this.conversationChunking =
                Objects.requireNonNull(builder.conversationChunking, "conversationChunking");
        this.insightBuild = Objects.requireNonNull(builder.insightBuild, "insightBuild");
        this.boundaryDetector =
                Objects.requireNonNull(builder.boundaryDetector, "boundaryDetector");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MemoryBuildOptions defaults() {
        return builder().build();
    }

    public ConversationChunkingConfig conversationChunking() {
        return conversationChunking;
    }

    public InsightBuildConfig insightBuild() {
        return insightBuild;
    }

    public CommitDetectorConfig boundaryDetector() {
        return boundaryDetector;
    }

    public static final class Builder {

        private ConversationChunkingConfig conversationChunking = DEFAULT_CONVERSATION_CHUNKING;
        private InsightBuildConfig insightBuild = DEFAULT_INSIGHT_BUILD;
        private CommitDetectorConfig boundaryDetector = DEFAULT_BOUNDARY_DETECTOR;

        private Builder() {}

        public Builder conversationChunking(ConversationChunkingConfig conversationChunking) {
            this.conversationChunking =
                    Objects.requireNonNull(conversationChunking, "conversationChunking");
            return this;
        }

        public Builder insightBuild(InsightBuildConfig insightBuild) {
            this.insightBuild = Objects.requireNonNull(insightBuild, "insightBuild");
            return this;
        }

        public Builder boundaryDetector(CommitDetectorConfig boundaryDetector) {
            this.boundaryDetector = Objects.requireNonNull(boundaryDetector, "boundaryDetector");
            return this;
        }

        public MemoryBuildOptions build() {
            return new MemoryBuildOptions(this);
        }
    }
}
