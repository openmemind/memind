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
package com.openmemind.ai.memory.core.extraction.item.graph.commit;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import java.util.Objects;

/**
 * Thin adapter that applies a normalized graph write plan through store contracts.
 */
public final class GraphWritePlanApplier {

    private final GraphOperations graphOperations;

    public GraphWritePlanApplier(GraphOperations graphOperations) {
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
    }

    public void apply(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, ItemGraphWritePlan writePlan) {
        Objects.requireNonNull(writePlan, "writePlan");
        graphOperations.applyGraphWritePlan(memoryId, extractionBatchId, writePlan.normalized());
    }
}
