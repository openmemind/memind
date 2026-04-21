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
package com.openmemind.ai.memory.core.store;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.GraphOperationsCapabilities;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.InMemoryItemGraphCommitOperations;
import com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.store.resource.InMemoryResourceOperations;
import com.openmemind.ai.memory.core.store.resource.ResourceOperations;
import com.openmemind.ai.memory.core.store.thread.InMemoryThreadProjectionStore;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;

/**
 * In-memory implementation of {@link MemoryStore}.
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final RawDataOperations rawDataOperations = new InMemoryRawDataOperations();
    private final InMemoryItemOperations itemOperations = new InMemoryItemOperations();
    private final InsightOperations insightOperations = new InMemoryInsightOperations();
    private final InMemoryGraphOperations graphOperations = new InMemoryGraphOperations();
    private final InMemoryExtractionCommitState extractionCommitState =
            new InMemoryExtractionCommitState();
    private final ItemGraphCommitOperations itemGraphCommitOperations =
            new InMemoryItemGraphCommitOperations(
                    extractionCommitState, itemOperations, graphOperations);
    private final ThreadProjectionStore threadOperations = new InMemoryThreadProjectionStore();
    private final ResourceOperations resourceOperations = new InMemoryResourceOperations();

    public InMemoryMemoryStore() {
        insightOperations.upsertInsightTypes(DefaultInsightTypes.all());
    }

    @Override
    public RawDataOperations rawDataOperations() {
        return rawDataOperations;
    }

    @Override
    public ItemOperations itemOperations() {
        return itemOperations;
    }

    @Override
    public InsightOperations insightOperations() {
        return insightOperations;
    }

    @Override
    public GraphOperations graphOperations() {
        return graphOperations;
    }

    @Override
    public ItemGraphCommitOperations itemGraphCommitOperations() {
        return itemGraphCommitOperations;
    }

    @Override
    public GraphOperationsCapabilities graphOperationsCapabilities() {
        return new GraphOperationsCapabilities() {
            @Override
            public boolean supportsBoundedEntityKeyLookup() {
                return true;
            }

            @Override
            public boolean supportsHistoricalAliasLookup() {
                return true;
            }

            @Override
            public boolean supportsBoundedAdjacencyLookup() {
                return true;
            }

            @Override
            public boolean supportsStoreSideCooccurrenceRebuild() {
                return true;
            }
        };
    }

    @Override
    public ThreadProjectionStore threadOperations() {
        return threadOperations;
    }

    @Override
    public ResourceOperations resourceOperations() {
        return resourceOperations;
    }
}
