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
package com.openmemind.ai.memory.plugin.rawdata.audio.config;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import java.time.Duration;
import java.util.Objects;

/**
 * Governance options for audio ingestion.
 */
public record AudioExtractionOptions(
        SourceLimitOptions sourceLimit,
        ParsedContentLimitOptions parsedLimit,
        int wholeTranscriptMaxTokens,
        TokenChunkingOptions chunking) {

    public AudioExtractionOptions {
        sourceLimit = Objects.requireNonNull(sourceLimit, "sourceLimit");
        parsedLimit = Objects.requireNonNull(parsedLimit, "parsedLimit");
        chunking = Objects.requireNonNull(chunking, "chunking");
        if (wholeTranscriptMaxTokens <= 0) {
            throw new IllegalArgumentException("wholeTranscriptMaxTokens must be positive");
        }
        if (wholeTranscriptMaxTokens > parsedLimit.maxTokens()) {
            throw new IllegalArgumentException(
                    "wholeTranscriptMaxTokens must not exceed parsedLimit.maxTokens");
        }
    }

    public static AudioExtractionOptions defaults() {
        return new AudioExtractionOptions(
                new SourceLimitOptions(25L * 1024 * 1024),
                new ParsedContentLimitOptions(18_000, null, null, Duration.ofMinutes(30)),
                4_000,
                new TokenChunkingOptions(800, 1000));
    }
}
