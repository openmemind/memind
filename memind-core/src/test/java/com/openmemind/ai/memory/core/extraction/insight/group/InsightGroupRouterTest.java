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
package com.openmemind.ai.memory.core.extraction.insight.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightGroupRouter")
class InsightGroupRouterTest {

    @Mock InsightGroupClassifier llmClassifier;
    @InjectMocks InsightGroupRouter router;

    @Nested
    @DisplayName("LlmClassify Strategy")
    class LlmClassifyTest {
        @Test
        @DisplayName("Should delegate to InsightGroupClassifier")
        void shouldDelegateToClassifier() {
            var insightType = createInsightType();
            var items = List.<MemoryItem>of();
            var expected = Map.<String, List<MemoryItem>>of("group1", items);

            when(llmClassifier.classify(any(), any(), any(), any()))
                    .thenReturn(Mono.just(expected));

            StepVerifier.create(router.group(insightType, MemoryCategory.PROFILE, items, List.of()))
                    .assertNext(result -> assertThat(result).isEqualTo(expected))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("MetadataField Strategy")
    class MetadataFieldTest {
        @Test
        @DisplayName("Should group by metadata field")
        void shouldGroupByMetadataField() {
            var insightType = createInsightType();
            var item1 = createItemWithMetadata(Map.of("toolName", "web_search"));
            var item2 = createItemWithMetadata(Map.of("toolName", "web_search"));
            var item3 = createItemWithMetadata(Map.of("toolName", "calculator"));

            StepVerifier.create(
                            router.group(
                                    insightType,
                                    MemoryCategory.TOOL,
                                    List.of(item1, item2, item3),
                                    List.of()))
                    .assertNext(
                            result -> {
                                assertThat(result).containsKeys("web_search", "calculator");
                                assertThat(result.get("web_search")).hasSize(2);
                                assertThat(result.get("calculator")).hasSize(1);
                            })
                    .verifyComplete();

            verifyNoInteractions(llmClassifier);
        }

        @Test
        @DisplayName("Should fall back to unknown group when metadata field is missing")
        void shouldFallbackToUnknown() {
            var insightType = createInsightType();
            var item = createItemWithMetadata(Map.of());

            StepVerifier.create(
                            router.group(
                                    insightType, MemoryCategory.TOOL, List.of(item), List.of()))
                    .assertNext(result -> assertThat(result).containsKey("unknown"))
                    .verifyComplete();
        }
    }

    private MemoryInsightType createInsightType() {
        return new MemoryInsightType(
                1L, "test", "desc", null, List.of(), 400, null, null, null, null, null, null);
    }

    private MemoryItem createItemWithMetadata(Map<String, Object> metadata) {
        return new MemoryItem(
                1L, "m1", "content", null, null, null, null, null, null, null, null, metadata, null,
                null);
    }
}
