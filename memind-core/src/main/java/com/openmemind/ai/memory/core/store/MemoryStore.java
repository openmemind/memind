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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.GraphOperationsCapabilities;
import com.openmemind.ai.memory.core.store.graph.NoOpGraphOperations;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.store.resource.ResourceOperations;
import com.openmemind.ai.memory.core.store.thread.MemoryThreadOperations;
import com.openmemind.ai.memory.core.store.thread.NoOpMemoryThreadOperations;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Unified memory storage aggregate.
 */
public interface MemoryStore extends AutoCloseable {

    RawDataOperations rawDataOperations();

    ItemOperations itemOperations();

    InsightOperations insightOperations();

    default GraphOperations graphOperations() {
        return NoOpGraphOperations.INSTANCE;
    }

    default GraphOperationsCapabilities graphOperationsCapabilities() {
        return GraphOperationsCapabilities.NONE;
    }

    default MemoryThreadOperations threadOperations() {
        return NoOpMemoryThreadOperations.INSTANCE;
    }

    default ResourceOperations resourceOperations() {
        throw new IllegalStateException(
                "ResourceOperations is required for multimodal persistence; use MemoryStore.of(...,"
                        + " resourceOperations, resourceStore)");
    }

    default ResourceStore resourceStore() {
        return null;
    }

    default void upsertRawDataWithResources(
            MemoryId memoryId, List<MemoryResource> resources, List<MemoryRawData> rawDataList) {
        if (resources != null && !resources.isEmpty()) {
            resourceOperations().upsertResources(memoryId, resources);
        }
        if (rawDataList != null && !rawDataList.isEmpty()) {
            rawDataOperations().upsertRawData(memoryId, rawDataList);
        }
    }

    @Override
    default void close() throws Exception {}

    static MemoryStore of(
            RawDataOperations rawDataOperations,
            ItemOperations itemOperations,
            InsightOperations insightOperations) {
        Objects.requireNonNull(rawDataOperations, "rawDataOperations");
        Objects.requireNonNull(itemOperations, "itemOperations");
        Objects.requireNonNull(insightOperations, "insightOperations");

        return new MemoryStore() {

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
            public void close() throws Exception {
                RuntimeException closeFailure = null;
                for (AutoCloseable closeable :
                        uniqueCloseables(rawDataOperations, itemOperations, insightOperations)) {
                    try {
                        closeable.close();
                    } catch (Exception e) {
                        if (closeFailure == null) {
                            closeFailure =
                                    new IllegalStateException("Failed to close memory store", e);
                        } else {
                            closeFailure.addSuppressed(e);
                        }
                    }
                }
                if (closeFailure != null) {
                    throw closeFailure;
                }
            }
        };
    }

    static MemoryStore of(
            RawDataOperations rawDataOperations,
            ItemOperations itemOperations,
            InsightOperations insightOperations,
            ResourceOperations resourceOperations,
            ResourceStore resourceStore) {
        Objects.requireNonNull(rawDataOperations, "rawDataOperations");
        Objects.requireNonNull(itemOperations, "itemOperations");
        Objects.requireNonNull(insightOperations, "insightOperations");
        Objects.requireNonNull(resourceOperations, "resourceOperations");

        return new MemoryStore() {

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
            public ResourceOperations resourceOperations() {
                return resourceOperations;
            }

            @Override
            public ResourceStore resourceStore() {
                return resourceStore;
            }

            @Override
            public void close() throws Exception {
                RuntimeException closeFailure = null;
                for (AutoCloseable closeable :
                        uniqueCloseables(
                                rawDataOperations,
                                itemOperations,
                                insightOperations,
                                resourceOperations,
                                resourceStore)) {
                    try {
                        closeable.close();
                    } catch (Exception e) {
                        if (closeFailure == null) {
                            closeFailure =
                                    new IllegalStateException("Failed to close memory store", e);
                        } else {
                            closeFailure.addSuppressed(e);
                        }
                    }
                }
                if (closeFailure != null) {
                    throw closeFailure;
                }
            }
        };
    }

    private static List<AutoCloseable> uniqueCloseables(Object... candidates) {
        List<AutoCloseable> ordered = new ArrayList<>();
        IdentityHashMap<AutoCloseable, Boolean> seen = new IdentityHashMap<>();
        for (Object candidate : candidates) {
            if (!(candidate instanceof AutoCloseable closeable)) {
                continue;
            }
            if (seen.put(closeable, Boolean.TRUE) == null) {
                ordered.add(closeable);
            }
        }
        return ordered;
    }
}
