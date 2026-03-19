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
package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link MemoryItemExtractStep}.
 *
 * <p>Records the number of items and the number of new items as result attributes.
 */
public class TracingMemoryItemExtractStep extends TracingSupport implements MemoryItemExtractStep {

    private final MemoryItemExtractStep delegate;

    public TracingMemoryItemExtractStep(MemoryItemExtractStep delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<MemoryItemResult> extract(
            MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config) {
        return trace(
                MemorySpanNames.EXTRACTION_ITEM,
                Map.of(MemoryAttributes.MEMORY_ID, memoryId.toIdentifier()),
                r ->
                        Map.of(
                                MemoryAttributes.EXTRACTION_ITEM_COUNT,
                                r.newCount(),
                                MemoryAttributes.EXTRACTION_NEW_ITEM_COUNT,
                                r.newItems().size()),
                () -> delegate.extract(memoryId, rawDataResult, config));
    }
}
