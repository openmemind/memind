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
package com.openmemind.ai.memory.plugin.rawdata.image.config;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import java.util.Objects;

/**
 * Governance options for image ingestion.
 */
public record ImageExtractionOptions(
        SourceLimitOptions sourceLimit,
        ParsedContentLimitOptions parsedLimit,
        TokenChunkingOptions chunking,
        int captionOcrMergeMaxTokens) {

    public ImageExtractionOptions {
        sourceLimit = Objects.requireNonNull(sourceLimit, "sourceLimit");
        parsedLimit = Objects.requireNonNull(parsedLimit, "parsedLimit");
        chunking = Objects.requireNonNull(chunking, "chunking");
        if (captionOcrMergeMaxTokens < 0) {
            throw new IllegalArgumentException("captionOcrMergeMaxTokens must be >= 0");
        }
    }

    public static ImageExtractionOptions defaults() {
        return new ImageExtractionOptions(
                new SourceLimitOptions(10L * 1024 * 1024),
                new ParsedContentLimitOptions(4_000, null, null, null),
                new TokenChunkingOptions(800, 1200),
                400);
    }
}
