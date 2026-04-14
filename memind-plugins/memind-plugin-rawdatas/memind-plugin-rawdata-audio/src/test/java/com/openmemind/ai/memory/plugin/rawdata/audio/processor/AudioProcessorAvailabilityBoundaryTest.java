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
package com.openmemind.ai.memory.plugin.rawdata.audio.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AudioProcessorAvailabilityBoundaryTest {

    @Test
    void directAudioContentFailsFastWhenAudioPluginProcessorIsMissing() {
        AtomicBoolean rawDataCalled = new AtomicBoolean(false);
        RawDataExtractStep rawDataStep =
                (memoryId, content, contentType, metadata) -> {
                    rawDataCalled.set(true);
                    return Mono.error(new AssertionError("rawDataStep should not be called"));
                };
        MemoryItemExtractStep itemStep =
                (memoryId, rawDataResult, config) ->
                        Mono.error(new AssertionError("itemStep should not be called"));
        InsightExtractStep insightStep =
                (memoryId, memoryItemResult) ->
                        Mono.error(new AssertionError("insightStep should not be called"));

        DefaultMemoryExtractor extractor =
                new DefaultMemoryExtractor(
                        rawDataStep,
                        itemStep,
                        insightStep,
                        null,
                        null,
                        null,
                        null,
                        new RawContentProcessorRegistry(List.of()),
                        null,
                        null,
                        null,
                        RawDataIngestionPolicyRegistry.empty(),
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        ExtractionResult result =
                extractor
                        .extract(
                                ExtractionRequest.of(
                                        DefaultMemoryId.of("u1", "a1"),
                                        AudioContent.of("meeting transcript")))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage())
                .contains("No processor registered for raw content type")
                .contains("AUDIO");
        assertThat(rawDataCalled).isFalse();
    }
}
