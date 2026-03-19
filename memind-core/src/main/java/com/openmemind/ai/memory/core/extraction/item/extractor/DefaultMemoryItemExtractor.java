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

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Router-based memory item extractor that delegates to content-type-specific strategies.
 *
 * <p>Selects the appropriate {@link ItemExtractionStrategy} based on the content type
 * from the extraction config. Falls back to the default strategy when no specific
 * strategy is registered for the content type.
 */
public class DefaultMemoryItemExtractor implements MemoryItemExtractor {

    private final ItemExtractionStrategy defaultStrategy;
    private final Map<String, ItemExtractionStrategy> strategies;

    /**
     * @param defaultStrategy fallback strategy when no content-type match is found
     * @param strategies map of content-type key to strategy (e.g. "TOOL_CALL" -> toolCallStrategy)
     */
    public DefaultMemoryItemExtractor(
            ItemExtractionStrategy defaultStrategy,
            Map<String, ItemExtractionStrategy> strategies) {
        this.defaultStrategy = defaultStrategy;
        this.strategies = strategies;
    }

    @Override
    public Mono<List<ExtractedMemoryEntry>> extract(
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            ItemExtractionConfig config) {

        var key = config.contentType();
        var strategy = strategies.getOrDefault(key, defaultStrategy);
        return strategy.extract(segments, insightTypes, config);
    }
}
