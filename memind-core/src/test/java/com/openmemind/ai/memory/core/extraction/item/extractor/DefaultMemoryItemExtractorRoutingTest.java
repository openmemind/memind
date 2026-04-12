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
package com.openmemind.ai.memory.core.extraction.item.extractor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("DefaultMemoryItemExtractor routing")
class DefaultMemoryItemExtractorRoutingTest {

    private static final String PLUGIN_TOOL_CALL = "PLUGIN_TOOL_CALL";

    private final ItemExtractionStrategy defaultStrategy = mock(ItemExtractionStrategy.class);
    private final ItemExtractionStrategy pluginStrategy = mock(ItemExtractionStrategy.class);

    @Nested
    @DisplayName("Content type routing")
    class ContentTypeRouting {

        @Test
        @DisplayName("Plugin-owned contentType should use the registered plugin strategy")
        void pluginOwnedContentUsesPluginStrategy() {
            var extractor =
                    new DefaultMemoryItemExtractor(
                            defaultStrategy, Map.of(PLUGIN_TOOL_CALL, pluginStrategy));

            var config =
                    new ItemExtractionConfig(
                            com.openmemind.ai.memory.core.data.enums.MemoryScope.USER,
                            PLUGIN_TOOL_CALL,
                            false,
                            "en");

            List<ExtractedMemoryEntry> expected = List.of();
            when(pluginStrategy.extract(any(), any(), any())).thenReturn(Mono.just(expected));

            StepVerifier.create(extractor.extract(List.of(), List.of(), config))
                    .expectNext(expected)
                    .verifyComplete();

            verify(pluginStrategy).extract(any(), any(), any());
            verifyNoInteractions(defaultStrategy);
        }

        @Test
        @DisplayName("CONVERSATION contentType should use default strategy")
        void conversationUsesDefaultStrategy() {
            var extractor =
                    new DefaultMemoryItemExtractor(
                            defaultStrategy, Map.of(PLUGIN_TOOL_CALL, pluginStrategy));

            var config =
                    new ItemExtractionConfig(
                            com.openmemind.ai.memory.core.data.enums.MemoryScope.USER,
                            ContentTypes.CONVERSATION,
                            false,
                            "en");

            List<ExtractedMemoryEntry> expected = List.of();
            when(defaultStrategy.extract(any(), any(), any())).thenReturn(Mono.just(expected));

            StepVerifier.create(extractor.extract(List.of(), List.of(), config))
                    .expectNext(expected)
                    .verifyComplete();

            verify(defaultStrategy).extract(any(), any(), any());
            verifyNoInteractions(pluginStrategy);
        }

        @Test
        @DisplayName("Unknown contentType should fall back to default strategy")
        void unknownContentTypeFallsBackToDefault() {
            var extractor =
                    new DefaultMemoryItemExtractor(
                            defaultStrategy, Map.of(PLUGIN_TOOL_CALL, pluginStrategy));

            var config =
                    new ItemExtractionConfig(
                            com.openmemind.ai.memory.core.data.enums.MemoryScope.USER,
                            "CUSTOM_TYPE",
                            false,
                            "en");

            List<ExtractedMemoryEntry> expected = List.of();
            when(defaultStrategy.extract(any(), any(), any())).thenReturn(Mono.just(expected));

            StepVerifier.create(extractor.extract(List.of(), List.of(), config))
                    .expectNext(expected)
                    .verifyComplete();

            verify(defaultStrategy).extract(any(), any(), any());
            verifyNoInteractions(pluginStrategy);
        }

        @Test
        @DisplayName("Custom strategy entry should be used for matching contentType")
        void customStrategyIsUsedForMatchingContentType() {
            var customStrategy = mock(ItemExtractionStrategy.class);
            var extractor =
                    new DefaultMemoryItemExtractor(
                            defaultStrategy,
                            Map.of(PLUGIN_TOOL_CALL, pluginStrategy, "MY_CUSTOM", customStrategy));

            var config =
                    new ItemExtractionConfig(
                            com.openmemind.ai.memory.core.data.enums.MemoryScope.USER,
                            "MY_CUSTOM",
                            false,
                            "en");

            List<ExtractedMemoryEntry> expected = List.of();
            when(customStrategy.extract(any(), any(), any())).thenReturn(Mono.just(expected));

            StepVerifier.create(extractor.extract(List.of(), List.of(), config))
                    .expectNext(expected)
                    .verifyComplete();

            verify(customStrategy).extract(any(), any(), any());
            verifyNoInteractions(defaultStrategy);
            verifyNoInteractions(pluginStrategy);
        }
    }
}
