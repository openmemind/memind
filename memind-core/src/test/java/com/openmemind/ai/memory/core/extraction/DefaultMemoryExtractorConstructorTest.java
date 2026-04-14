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
package com.openmemind.ai.memory.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.extraction.step.SegmentProcessor;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultMemoryExtractorConstructorTest {

    @Test
    void onlySupportedPublicConstructorsRemain() {
        assertThat(publicConstructorSignatures())
                .containsExactlyInAnyOrder(
                        List.of(
                                RawDataExtractStep.class,
                                MemoryItemExtractStep.class,
                                InsightExtractStep.class,
                                SegmentProcessor.class,
                                ContextCommitDetector.class,
                                PendingConversationBuffer.class,
                                RecentConversationBuffer.class),
                        List.of(
                                RawDataExtractStep.class,
                                MemoryItemExtractStep.class,
                                InsightExtractStep.class,
                                SegmentProcessor.class,
                                ContextCommitDetector.class,
                                PendingConversationBuffer.class,
                                RecentConversationBuffer.class,
                                RawContentProcessorRegistry.class,
                                ContentParserRegistry.class,
                                ResourceStore.class,
                                ResourceFetcher.class,
                                RawDataIngestionPolicyRegistry.class,
                                RawDataExtractionOptions.class,
                                ItemExtractionOptions.class));
    }

    private List<List<Class<?>>> publicConstructorSignatures() {
        return Arrays.stream(DefaultMemoryExtractor.class.getConstructors())
                .map(Constructor::getParameterTypes)
                .map(Arrays::asList)
                .toList();
    }
}
