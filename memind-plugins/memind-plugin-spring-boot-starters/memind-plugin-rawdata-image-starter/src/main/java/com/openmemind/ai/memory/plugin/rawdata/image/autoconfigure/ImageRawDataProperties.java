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
package com.openmemind.ai.memory.plugin.rawdata.image.autoconfigure;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.rawdata.image")
public class ImageRawDataProperties {

    private static final ImageExtractionOptions DEFAULT_EXTRACTION =
            ImageExtractionOptions.defaults();

    private boolean enabled = true;
    private boolean parserEnabled = true;
    private final ImageExtractionProperties extraction = new ImageExtractionProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isParserEnabled() {
        return parserEnabled;
    }

    public void setParserEnabled(boolean parserEnabled) {
        this.parserEnabled = parserEnabled;
    }

    public ImageExtractionProperties getExtraction() {
        return extraction;
    }

    public ImageExtractionOptions extractionOptions() {
        return extraction.toOptions();
    }

    public static final class ImageExtractionProperties {

        private final SourceLimitProperties sourceLimit =
                new SourceLimitProperties(DEFAULT_EXTRACTION.sourceLimit());
        private final ParsedContentLimitProperties parsedLimit =
                new ParsedContentLimitProperties(DEFAULT_EXTRACTION.parsedLimit());

        public SourceLimitProperties getSourceLimit() {
            return sourceLimit;
        }

        public ParsedContentLimitProperties getParsedLimit() {
            return parsedLimit;
        }

        ImageExtractionOptions toOptions() {
            return new ImageExtractionOptions(sourceLimit.toOptions(), parsedLimit.toOptions());
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
        private java.time.Duration maxDuration;

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

        public java.time.Duration getMaxDuration() {
            return maxDuration;
        }

        public void setMaxDuration(java.time.Duration maxDuration) {
            this.maxDuration = maxDuration;
        }

        ParsedContentLimitOptions toOptions() {
            return new ParsedContentLimitOptions(maxTokens, maxSections, maxPages, maxDuration);
        }
    }
}
