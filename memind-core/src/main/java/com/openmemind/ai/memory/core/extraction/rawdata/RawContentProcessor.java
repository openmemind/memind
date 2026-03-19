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
package com.openmemind.ai.memory.core.extraction.rawdata;

import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.TruncateCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Extension point for processing custom {@link RawContent} types.
 *
 * <p>Implement this interface and register as a Spring Bean to support a new content type.
 * The framework automatically discovers processors and routes content to the correct one.
 *
 * @param <T> the concrete RawContent type this processor handles
 */
public interface RawContentProcessor<T extends RawContent> {

    /** The RawContent class this processor handles. */
    Class<T> contentClass();

    /** Content type identifier (e.g. "CONVERSATION"). Must match RawContent.contentType(). */
    String contentType();

    /** Split content into segments. Configuration should be injected at construction time. */
    Mono<List<Segment>> chunk(T content);

    /** Caption generator for segments. Override to provide custom caption logic. */
    default CaptionGenerator captionGenerator() {
        return new TruncateCaptionGenerator();
    }

    /** Item extraction strategy. Override to provide custom extraction logic. Returns null to use framework default. */
    default ItemExtractionStrategy itemExtractionStrategy() {
        return null;
    }

    /** Whether items from this content type participate in insight building. */
    default boolean supportsInsight() {
        return true;
    }
}
