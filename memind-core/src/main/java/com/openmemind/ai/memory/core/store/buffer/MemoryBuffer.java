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
package com.openmemind.ai.memory.core.store.buffer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Unified runtime buffer aggregate.
 */
public interface MemoryBuffer extends AutoCloseable {

    InsightBuffer insightBuffer();

    PendingConversationBuffer pendingConversationBuffer();

    RecentConversationBuffer recentConversationBuffer();

    @Override
    default void close() throws Exception {}

    static MemoryBuffer of(
            InsightBuffer insightBuffer,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer) {
        Objects.requireNonNull(insightBuffer, "insightBuffer");
        Objects.requireNonNull(pendingConversationBuffer, "pendingConversationBuffer");
        Objects.requireNonNull(recentConversationBuffer, "recentConversationBuffer");

        return new MemoryBuffer() {
            @Override
            public InsightBuffer insightBuffer() {
                return insightBuffer;
            }

            @Override
            public PendingConversationBuffer pendingConversationBuffer() {
                return pendingConversationBuffer;
            }

            @Override
            public RecentConversationBuffer recentConversationBuffer() {
                return recentConversationBuffer;
            }

            @Override
            public void close() throws Exception {
                RuntimeException closeFailure = null;
                for (AutoCloseable closeable :
                        uniqueCloseables(
                                insightBuffer,
                                pendingConversationBuffer,
                                recentConversationBuffer)) {
                    try {
                        closeable.close();
                    } catch (Exception e) {
                        if (closeFailure == null) {
                            closeFailure =
                                    new IllegalStateException("Failed to close memory buffers", e);
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
