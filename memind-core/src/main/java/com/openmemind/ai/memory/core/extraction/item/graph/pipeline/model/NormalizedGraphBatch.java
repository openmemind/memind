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
package com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model;

import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.GraphEntityNormalizationDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.util.List;

/**
 * Store-agnostic normalization output passed to the materializer-layer resolver.
 */
public record NormalizedGraphBatch(
        List<NormalizedEntityMentionCandidate> candidates,
        List<ItemLink> itemLinks,
        GraphEntityNormalizationDiagnostics diagnostics) {

    public NormalizedGraphBatch {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        itemLinks = itemLinks == null ? List.of() : List.copyOf(itemLinks);
        diagnostics =
                diagnostics == null ? GraphEntityNormalizationDiagnostics.empty() : diagnostics;
    }

    public static NormalizedGraphBatch empty() {
        return new NormalizedGraphBatch(
                List.of(), List.of(), GraphEntityNormalizationDiagnostics.empty());
    }
}
