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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

class MemoryExtractorRequestResolverTest {

    @Test
    void extractShouldDelegateRequestResolutionToInjectedResolver() {
        RawDataExtractStep rawDataStep = Mockito.mock(RawDataExtractStep.class);
        MemoryItemExtractStep itemStep = Mockito.mock(MemoryItemExtractStep.class);
        InsightExtractStep insightStep = Mockito.mock(InsightExtractStep.class);
        ExtractionRequestResolver requestResolver = Mockito.mock(ExtractionRequestResolver.class);

        var memoryId = DefaultMemoryId.of("u1", "a1");
        var content = new TestPluginContent();
        when(requestResolver.resolve(any()))
                .thenReturn(
                        Mono.just(
                                new ResolvedExtractionRequest(
                                        memoryId,
                                        content,
                                        "TEST_PLUGIN",
                                        Map.of("resolved", true),
                                        ExtractionConfig.defaults(),
                                        null)));
        when(rawDataStep.extract(any(), any(), eq("TEST_PLUGIN"), any(), any()))
                .thenReturn(Mono.just(RawDataResult.empty()));

        var extractor =
                new MemoryExtractor(
                        rawDataStep,
                        itemStep,
                        insightStep,
                        null,
                        null,
                        null,
                        null,
                        requestResolver,
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        var result = extractor.extract(ExtractionRequest.text(memoryId, "ignored")).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        verify(requestResolver).resolve(any());
        verify(rawDataStep).extract(any(), any(), eq("TEST_PLUGIN"), any(), any());
    }

    private static final class TestPluginContent extends RawContent {

        @Override
        public String contentType() {
            return "TEST_PLUGIN";
        }

        @Override
        public String toContentString() {
            return "plugin";
        }

        @Override
        public String getContentId() {
            return "plugin";
        }
    }
}
