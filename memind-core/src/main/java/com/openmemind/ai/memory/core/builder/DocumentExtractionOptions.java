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

import java.util.Objects;

/**
 * Governance options for document ingestion, split by text-like and binary profiles.
 */
public record DocumentExtractionOptions(
        SourceLimitOptions textLikeSourceLimit,
        SourceLimitOptions binarySourceLimit,
        ParsedContentLimitOptions textLikeParsedLimit,
        ParsedContentLimitOptions binaryParsedLimit,
        TokenChunkingOptions textLikeChunking,
        TokenChunkingOptions binaryChunking) {

    public DocumentExtractionOptions {
        Objects.requireNonNull(textLikeSourceLimit, "textLikeSourceLimit");
        Objects.requireNonNull(binarySourceLimit, "binarySourceLimit");
        Objects.requireNonNull(textLikeParsedLimit, "textLikeParsedLimit");
        Objects.requireNonNull(binaryParsedLimit, "binaryParsedLimit");
        Objects.requireNonNull(textLikeChunking, "textLikeChunking");
        Objects.requireNonNull(binaryChunking, "binaryChunking");
    }

    public static DocumentExtractionOptions defaults() {
        return new DocumentExtractionOptions(
                new SourceLimitOptions(2L * 1024 * 1024),
                new SourceLimitOptions(20L * 1024 * 1024),
                new ParsedContentLimitOptions(20_000, null, null, null),
                new ParsedContentLimitOptions(30_000, null, null, null),
                new TokenChunkingOptions(800, 1200),
                new TokenChunkingOptions(1000, 1400));
    }
}
