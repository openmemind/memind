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
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionDiagnostics;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.util.List;

/**
 * Normalized graph primitives ready for persistence.
 */
public record ResolvedGraphBatch(
        List<GraphEntity> entities,
        List<GraphEntityAlias> entityAliases,
        List<ItemEntityMention> mentions,
        List<ItemLink> itemLinks,
        GraphEntityNormalizationDiagnostics diagnostics,
        EntityResolutionDiagnostics resolutionDiagnostics) {

    public ResolvedGraphBatch {
        entities = entities == null ? List.of() : List.copyOf(entities);
        entityAliases = entityAliases == null ? List.of() : List.copyOf(entityAliases);
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
        itemLinks = itemLinks == null ? List.of() : List.copyOf(itemLinks);
        diagnostics =
                diagnostics == null ? GraphEntityNormalizationDiagnostics.empty() : diagnostics;
        resolutionDiagnostics =
                resolutionDiagnostics == null
                        ? EntityResolutionDiagnostics.empty()
                        : resolutionDiagnostics;
    }

    public ResolvedGraphBatch(
            List<GraphEntity> entities,
            List<ItemEntityMention> mentions,
            List<ItemLink> itemLinks,
            GraphEntityNormalizationDiagnostics diagnostics,
            EntityResolutionDiagnostics resolutionDiagnostics) {
        this(entities, List.of(), mentions, itemLinks, diagnostics, resolutionDiagnostics);
    }

    public ResolvedGraphBatch(
            List<GraphEntity> entities,
            List<ItemEntityMention> mentions,
            List<ItemLink> itemLinks,
            GraphEntityNormalizationDiagnostics diagnostics) {
        this(
                entities,
                List.of(),
                mentions,
                itemLinks,
                diagnostics,
                EntityResolutionDiagnostics.empty());
    }

    public ResolvedGraphBatch withEntityAliases(List<GraphEntityAlias> plannedAliases) {
        return new ResolvedGraphBatch(
                entities, plannedAliases, mentions, itemLinks, diagnostics, resolutionDiagnostics);
    }

    public static ResolvedGraphBatch empty() {
        return new ResolvedGraphBatch(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                GraphEntityNormalizationDiagnostics.empty(),
                EntityResolutionDiagnostics.empty());
    }
}
