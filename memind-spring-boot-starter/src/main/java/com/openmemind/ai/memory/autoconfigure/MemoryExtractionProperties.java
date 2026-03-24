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
package com.openmemind.ai.memory.autoconfigure;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig.ConversationSegmentStrategy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * memind extraction configuration properties
 *
 */
@ConfigurationProperties(prefix = "memind.extraction")
public class MemoryExtractionProperties {

    private Duration timeout = Duration.ofMinutes(10);
    private Chunking chunking = new Chunking();
    private Boundary boundary = new Boundary();
    private InsightBuild insightBuild = new InsightBuild();

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Chunking getChunking() {
        return chunking;
    }

    public void setChunking(Chunking chunking) {
        this.chunking = chunking;
    }

    public Boundary getBoundary() {
        return boundary;
    }

    public void setBoundary(Boundary boundary) {
        this.boundary = boundary;
    }

    public InsightBuild getInsightBuild() {
        return insightBuild;
    }

    public void setInsightBuild(InsightBuild insightBuild) {
        this.insightBuild = insightBuild;
    }

    public static class Chunking {
        private static final ConversationChunkingConfig DEFAULTS =
                MemoryBuildOptions.defaults().conversationChunking();

        private ConversationSegmentStrategy strategy = DEFAULTS.strategy();
        private int messagesPerChunk = DEFAULTS.messagesPerChunk();
        private int minMessagesPerSegment = DEFAULTS.minMessagesPerSegment();

        public ConversationSegmentStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(ConversationSegmentStrategy strategy) {
            this.strategy = strategy;
        }

        public int getMessagesPerChunk() {
            return messagesPerChunk;
        }

        public void setMessagesPerChunk(int messagesPerChunk) {
            this.messagesPerChunk = messagesPerChunk;
        }

        public int getMinMessagesPerSegment() {
            return minMessagesPerSegment;
        }

        public void setMinMessagesPerSegment(int v) {
            this.minMessagesPerSegment = v;
        }
    }

    public static class Boundary {
        private static final CommitDetectorConfig DEFAULTS = CommitDetectorConfig.defaults();

        private int maxMessages = DEFAULTS.maxMessages();
        private int maxTokens = DEFAULTS.maxTokens();
        private int minMessagesForLlm = DEFAULTS.minMessagesForLlm();

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getMinMessagesForLlm() {
            return minMessagesForLlm;
        }

        public void setMinMessagesForLlm(int v) {
            this.minMessagesForLlm = v;
        }
    }

    public static class InsightBuild {
        private static final InsightBuildConfig DEFAULTS =
                MemoryBuildOptions.defaults().insightBuild();

        private int groupingThreshold = DEFAULTS.groupingThreshold();
        private int buildThreshold = DEFAULTS.buildThreshold();
        private int concurrency = DEFAULTS.concurrency();
        private int maxRetries = DEFAULTS.maxRetries();

        public int getGroupingThreshold() {
            return groupingThreshold;
        }

        public void setGroupingThreshold(int v) {
            this.groupingThreshold = v;
        }

        public int getBuildThreshold() {
            return buildThreshold;
        }

        public void setBuildThreshold(int v) {
            this.buildThreshold = v;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
