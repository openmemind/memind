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
import static org.mockito.Mockito.verifyNoInteractions;

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemoryExtractorPluginOwnedRawContentTest {

    @Test
    void unknownPluginOwnedRawContentUsesGenericMissingProcessorError() {
        RawDataExtractStep rawDataStep = Mockito.mock(RawDataExtractStep.class);
        MemoryItemExtractStep itemStep = Mockito.mock(MemoryItemExtractStep.class);
        InsightExtractStep insightStep = Mockito.mock(InsightExtractStep.class);

        MemoryExtractor extractor =
                new MemoryExtractor(
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
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        ExtractionResult result = extractor.extract(pluginOwnedRequest()).block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage())
                .contains("No processor registered for raw content type: TEST_PLUGIN");
        verifyNoInteractions(rawDataStep, itemStep, insightStep);
    }

    private static ExtractionRequest pluginOwnedRequest() {
        return ExtractionRequest.of(DefaultMemoryId.of("u1", "a1"), new TestPluginContent());
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
