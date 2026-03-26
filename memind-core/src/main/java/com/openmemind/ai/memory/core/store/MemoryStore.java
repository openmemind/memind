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

import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
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
