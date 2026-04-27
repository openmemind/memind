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
package com.openmemind.ai.memory.plugin.jdbc.internal;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public record DefaultJdbcMemoryAccess(
        MemoryStore store,
        MemoryBuffer buffer,
        MemoryTextSearch textSearch,
        BubbleTrackerStore bubbleTrackerStore,
        DataSource dataSource,
        List<AutoCloseable> ownedCloseables)
        implements JdbcMemoryAccess {

    public DefaultJdbcMemoryAccess {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(textSearch, "textSearch");
        Objects.requireNonNull(bubbleTrackerStore, "bubbleTrackerStore");
        Objects.requireNonNull(dataSource, "dataSource");
        ownedCloseables = ownedCloseables == null ? List.of() : List.copyOf(ownedCloseables);
    }

    @Override
    public void close() throws Exception {
        RuntimeException closeFailure = null;
        for (AutoCloseable closeable : ownedCloseables) {
            try {
                closeable.close();
            } catch (Exception e) {
                if (closeFailure == null) {
                    closeFailure =
                            new IllegalStateException("Failed to close JDBC memory access", e);
                } else {
                    closeFailure.addSuppressed(e);
                }
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}
