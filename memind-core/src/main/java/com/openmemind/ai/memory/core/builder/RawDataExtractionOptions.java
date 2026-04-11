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
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import java.util.Objects;

public record RawDataExtractionOptions(
        ConversationChunkingConfig conversation,
        DocumentExtractionOptions document,
        ImageExtractionOptions image,
        AudioExtractionOptions audio,
        ToolCallChunkingOptions toolCall,
        CommitDetectorConfig commitDetection,
        int vectorBatchSize) {

    public static final int DEFAULT_VECTOR_BATCH_SIZE = 64;

    public RawDataExtractionOptions {
        conversation = Objects.requireNonNull(conversation, "conversation");
        document = Objects.requireNonNull(document, "document");
        image = Objects.requireNonNull(image, "image");
        audio = Objects.requireNonNull(audio, "audio");
        toolCall = Objects.requireNonNull(toolCall, "toolCall");
        commitDetection = Objects.requireNonNull(commitDetection, "commitDetection");
        if (vectorBatchSize <= 0) {
            throw new IllegalArgumentException("vectorBatchSize must be > 0");
        }
    }

    public static RawDataExtractionOptions defaults() {
        return new RawDataExtractionOptions(
                ConversationChunkingConfig.DEFAULT,
                DocumentExtractionOptions.defaults(),
                ImageExtractionOptions.defaults(),
                AudioExtractionOptions.defaults(),
                ToolCallChunkingOptions.defaults(),
                CommitDetectorConfig.defaults(),
                DEFAULT_VECTOR_BATCH_SIZE);
    }
}
