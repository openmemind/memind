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
package com.openmemind.ai.memory.plugin.rawdata.audio.autoconfigure;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.audio.config.AudioExtractionOptions;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.rawdata.audio")
public class AudioRawDataProperties {

    private static final AudioExtractionOptions DEFAULT_EXTRACTION =
            AudioExtractionOptions.defaults();

    private boolean enabled = true;
    private final AudioExtractionProperties extraction = new AudioExtractionProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AudioExtractionProperties getExtraction() {
        return extraction;
    }

    public AudioExtractionOptions extractionOptions() {
        return extraction.toOptions();
    }

    public static final class AudioExtractionProperties {

        private final SourceLimitProperties sourceLimit =
                new SourceLimitProperties(DEFAULT_EXTRACTION.sourceLimit());
        private final ParsedContentLimitProperties parsedLimit =
                new ParsedContentLimitProperties(DEFAULT_EXTRACTION.parsedLimit());
        private final TokenChunkingProperties chunking =
                new TokenChunkingProperties(DEFAULT_EXTRACTION.chunking());

        public SourceLimitProperties getSourceLimit() {
            return sourceLimit;
        }

        public ParsedContentLimitProperties getParsedLimit() {
            return parsedLimit;
        }

        public TokenChunkingProperties getChunking() {
            return chunking;
        }

        AudioExtractionOptions toOptions() {
            return new AudioExtractionOptions(
                    sourceLimit.toOptions(), parsedLimit.toOptions(), chunking.toOptions());
        }
    }

    public static final class SourceLimitProperties {

        private long maxBytes;

        private SourceLimitProperties(SourceLimitOptions defaults) {
            this.maxBytes = defaults.maxBytes();
        }

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        SourceLimitOptions toOptions() {
            return new SourceLimitOptions(maxBytes);
        }
    }

    public static final class ParsedContentLimitProperties {

        private int maxTokens;
        private Integer maxSections;
        private Integer maxPages;
        private Duration maxDuration;

        private ParsedContentLimitProperties(ParsedContentLimitOptions defaults) {
            this.maxTokens = defaults.maxTokens();
            this.maxSections = defaults.maxSections();
            this.maxPages = defaults.maxPages();
            this.maxDuration = defaults.maxDuration();
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Integer getMaxSections() {
            return maxSections;
        }

        public void setMaxSections(Integer maxSections) {
            this.maxSections = maxSections;
        }

        public Integer getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(Integer maxPages) {
            this.maxPages = maxPages;
        }

        public Duration getMaxDuration() {
            return maxDuration;
        }

        public void setMaxDuration(Duration maxDuration) {
            this.maxDuration = maxDuration;
        }

        ParsedContentLimitOptions toOptions() {
            return new ParsedContentLimitOptions(maxTokens, maxSections, maxPages, maxDuration);
        }
    }

    public static final class TokenChunkingProperties {

        private int targetTokens;
        private int hardMaxTokens;

        private TokenChunkingProperties(TokenChunkingOptions defaults) {
            this.targetTokens = defaults.targetTokens();
            this.hardMaxTokens = defaults.hardMaxTokens();
        }

        public int getTargetTokens() {
            return targetTokens;
        }

        public void setTargetTokens(int targetTokens) {
            this.targetTokens = targetTokens;
        }

        public int getHardMaxTokens() {
            return hardMaxTokens;
        }

        public void setHardMaxTokens(int hardMaxTokens) {
            this.hardMaxTokens = hardMaxTokens;
        }

        TokenChunkingOptions toOptions() {
            return new TokenChunkingOptions(targetTokens, hardMaxTokens);
        }
    }
}
