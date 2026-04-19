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
package com.openmemind.ai.memory.core.extraction.item.graph.link.temporal;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.store.item.TemporalCandidateRequest;

/**
 * Internal temporal probe policy derived from graph options.
 */
record TemporalProbePolicy(int overlapLimit, int beforeLimit, int afterLimit) {

    static TemporalProbePolicy fromOptions(ItemGraphOptions options) {
        int pairCap = Math.max(1, options.maxTemporalLinksPerItem());
        int overlapLimit = Math.max(4, Math.min(16, pairCap));
        int neighborLimit = Math.max(8, Math.min(32, pairCap * 2));
        return new TemporalProbePolicy(overlapLimit, neighborLimit, neighborLimit);
    }

    TemporalCandidateRequest toRequest(MemoryItem item, TemporalWindow window) {
        return new TemporalCandidateRequest(
                item.id(),
                window.start(),
                window.endOrAnchor(),
                window.anchor(),
                item.type(),
                item.category(),
                overlapLimit,
                beforeLimit,
                afterLimit);
    }
}
