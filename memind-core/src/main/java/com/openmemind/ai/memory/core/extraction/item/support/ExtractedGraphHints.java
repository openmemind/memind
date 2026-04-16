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
package com.openmemind.ai.memory.core.extraction.item.support;

import java.util.List;

/**
 * Structured graph hints extracted alongside a memory item.
 */
public record ExtractedGraphHints(
        List<ExtractedEntityHint> entities, List<ExtractedCausalRelationHint> causalRelations) {

    public ExtractedGraphHints {
        entities = entities == null ? List.of() : List.copyOf(entities);
        causalRelations = causalRelations == null ? List.of() : List.copyOf(causalRelations);
    }

    public static ExtractedGraphHints empty() {
        return new ExtractedGraphHints(List.of(), List.of());
    }

    /**
     * Raw entity hint emitted by the extraction model.
     */
    public record ExtractedEntityHint(String name, String entityType, Float salience) {}

    /**
     * Raw backward-looking causal hint emitted by the extraction model.
     */
    public record ExtractedCausalRelationHint(
            Integer targetIndex, String relationType, Float strength) {}
}
