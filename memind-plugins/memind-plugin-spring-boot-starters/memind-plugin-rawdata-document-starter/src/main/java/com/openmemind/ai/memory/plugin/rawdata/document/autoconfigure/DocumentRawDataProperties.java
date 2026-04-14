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
package com.openmemind.ai.memory.plugin.rawdata.document.autoconfigure;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.rawdata.document")
public class DocumentRawDataProperties {

    private static final DocumentExtractionOptions DEFAULT_EXTRACTION =
            DocumentExtractionOptions.defaults();

    private boolean enabled = true;
    private boolean nativeTextEnabled = true;
    private boolean tikaEnabled = true;
    private final DocumentExtractionProperties extraction = new DocumentExtractionProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isNativeTextEnabled() {
        return nativeTextEnabled;
    }

    public void setNativeTextEnabled(boolean nativeTextEnabled) {
        this.nativeTextEnabled = nativeTextEnabled;
    }

    public boolean isTikaEnabled() {
        return tikaEnabled;
    }

    public void setTikaEnabled(boolean tikaEnabled) {
        this.tikaEnabled = tikaEnabled;
    }

    public DocumentExtractionProperties getExtraction() {
        return extraction;
    }

    public DocumentExtractionOptions extractionOptions() {
        return extraction.toOptions();
    }

    public static final class DocumentExtractionProperties {

        private final SourceLimitProperties textLikeSourceLimit =
                new SourceLimitProperties(DEFAULT_EXTRACTION.textLikeSourceLimit());
        private final SourceLimitProperties binarySourceLimit =
                new SourceLimitProperties(DEFAULT_EXTRACTION.binarySourceLimit());
        private final ParsedContentLimitProperties textLikeParsedLimit =
                new ParsedContentLimitProperties(DEFAULT_EXTRACTION.textLikeParsedLimit());
        private final ParsedContentLimitProperties binaryParsedLimit =
                new ParsedContentLimitProperties(DEFAULT_EXTRACTION.binaryParsedLimit());
        private int wholeDocumentMaxTokens = DEFAULT_EXTRACTION.wholeDocumentMaxTokens();
        private final TokenChunkingProperties textLikeChunking =
                new TokenChunkingProperties(DEFAULT_EXTRACTION.textLikeChunking());
        private final TokenChunkingProperties binaryChunking =
                new TokenChunkingProperties(DEFAULT_EXTRACTION.binaryChunking());
        private int textLikeMinChunkTokens = DEFAULT_EXTRACTION.textLikeMinChunkTokens();
        private int binaryMinChunkTokens = DEFAULT_EXTRACTION.binaryMinChunkTokens();
        private int pdfMaxMergedPages = DEFAULT_EXTRACTION.pdfMaxMergedPages();
        private boolean llmCaptionEnabled = DEFAULT_EXTRACTION.llmCaptionEnabled();
        private int captionConcurrency = DEFAULT_EXTRACTION.captionConcurrency();
        private int fallbackCaptionMaxLength = DEFAULT_EXTRACTION.fallbackCaptionMaxLength();

        public SourceLimitProperties getTextLikeSourceLimit() {
            return textLikeSourceLimit;
        }

        public SourceLimitProperties getBinarySourceLimit() {
            return binarySourceLimit;
        }

        public ParsedContentLimitProperties getTextLikeParsedLimit() {
            return textLikeParsedLimit;
        }

        public ParsedContentLimitProperties getBinaryParsedLimit() {
            return binaryParsedLimit;
        }

        public TokenChunkingProperties getTextLikeChunking() {
            return textLikeChunking;
        }

        public TokenChunkingProperties getBinaryChunking() {
            return binaryChunking;
        }

        public int getWholeDocumentMaxTokens() {
            return wholeDocumentMaxTokens;
        }

        public void setWholeDocumentMaxTokens(int wholeDocumentMaxTokens) {
            this.wholeDocumentMaxTokens = wholeDocumentMaxTokens;
        }

        public int getTextLikeMinChunkTokens() {
            return textLikeMinChunkTokens;
        }

        public void setTextLikeMinChunkTokens(int textLikeMinChunkTokens) {
            this.textLikeMinChunkTokens = textLikeMinChunkTokens;
        }

        public int getBinaryMinChunkTokens() {
            return binaryMinChunkTokens;
        }

        public void setBinaryMinChunkTokens(int binaryMinChunkTokens) {
            this.binaryMinChunkTokens = binaryMinChunkTokens;
        }

        public int getPdfMaxMergedPages() {
            return pdfMaxMergedPages;
        }

        public void setPdfMaxMergedPages(int pdfMaxMergedPages) {
            this.pdfMaxMergedPages = pdfMaxMergedPages;
        }

        public boolean isLlmCaptionEnabled() {
            return llmCaptionEnabled;
        }

        public void setLlmCaptionEnabled(boolean llmCaptionEnabled) {
            this.llmCaptionEnabled = llmCaptionEnabled;
        }

        public int getCaptionConcurrency() {
            return captionConcurrency;
        }

        public void setCaptionConcurrency(int captionConcurrency) {
            this.captionConcurrency = captionConcurrency;
        }

        public int getFallbackCaptionMaxLength() {
            return fallbackCaptionMaxLength;
        }

        public void setFallbackCaptionMaxLength(int fallbackCaptionMaxLength) {
            this.fallbackCaptionMaxLength = fallbackCaptionMaxLength;
        }

        DocumentExtractionOptions toOptions() {
            return new DocumentExtractionOptions(
                    textLikeSourceLimit.toOptions(),
                    binarySourceLimit.toOptions(),
                    textLikeParsedLimit.toOptions(),
                    binaryParsedLimit.toOptions(),
                    wholeDocumentMaxTokens,
                    textLikeChunking.toOptions(),
                    binaryChunking.toOptions(),
                    textLikeMinChunkTokens,
                    binaryMinChunkTokens,
                    pdfMaxMergedPages,
                    llmCaptionEnabled,
                    captionConcurrency,
                    fallbackCaptionMaxLength);
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
