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
package com.openmemind.ai.memory.plugin.rawdata.document.config;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import java.util.Objects;

/**
 * Governance options for document ingestion, split by text-like and binary profiles.
 *
 * <p>{@code wholeDocumentMaxTokens} is the threshold for keeping a document as a single segment
 * during ingestion. It is not the hard parsed-content limit.
 */
public record DocumentExtractionOptions(
        SourceLimitOptions textLikeSourceLimit,
        SourceLimitOptions binarySourceLimit,
        ParsedContentLimitOptions textLikeParsedLimit,
        ParsedContentLimitOptions binaryParsedLimit,
        int wholeDocumentMaxTokens,
        TokenChunkingOptions textLikeChunking,
        TokenChunkingOptions binaryChunking,
        int textLikeMinChunkTokens,
        int binaryMinChunkTokens,
        int pdfMaxMergedPages,
        boolean llmCaptionEnabled,
        int captionConcurrency,
        int fallbackCaptionMaxLength) {

    public DocumentExtractionOptions {
        Objects.requireNonNull(textLikeSourceLimit, "textLikeSourceLimit");
        Objects.requireNonNull(binarySourceLimit, "binarySourceLimit");
        Objects.requireNonNull(textLikeParsedLimit, "textLikeParsedLimit");
        Objects.requireNonNull(binaryParsedLimit, "binaryParsedLimit");
        if (wholeDocumentMaxTokens <= 0) {
            throw new IllegalArgumentException("wholeDocumentMaxTokens must be positive");
        }
        if (wholeDocumentMaxTokens > textLikeParsedLimit.maxTokens()
                || wholeDocumentMaxTokens > binaryParsedLimit.maxTokens()) {
            throw new IllegalArgumentException(
                    "wholeDocumentMaxTokens must not exceed parsed content token limits");
        }
        Objects.requireNonNull(textLikeChunking, "textLikeChunking");
        Objects.requireNonNull(binaryChunking, "binaryChunking");
        if (textLikeMinChunkTokens <= 0 || binaryMinChunkTokens <= 0) {
            throw new IllegalArgumentException("minChunkTokens must be positive");
        }
        if (pdfMaxMergedPages <= 0) {
            throw new IllegalArgumentException("pdfMaxMergedPages must be positive");
        }
        if (captionConcurrency <= 0) {
            throw new IllegalArgumentException("captionConcurrency must be positive");
        }
        if (fallbackCaptionMaxLength <= 0) {
            throw new IllegalArgumentException("fallbackCaptionMaxLength must be positive");
        }
    }

    public static DocumentExtractionOptions defaults() {
        return new DocumentExtractionOptions(
                new SourceLimitOptions(2L * 1024 * 1024),
                new SourceLimitOptions(20L * 1024 * 1024),
                new ParsedContentLimitOptions(100_000, null, null, null),
                new ParsedContentLimitOptions(100_000, null, null, null),
                12_000,
                new TokenChunkingOptions(1200, 1800),
                new TokenChunkingOptions(1200, 1800),
                300,
                300,
                3,
                true,
                4,
                240);
    }
}
