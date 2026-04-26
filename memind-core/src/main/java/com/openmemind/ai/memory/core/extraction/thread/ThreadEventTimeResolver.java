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
package com.openmemind.ai.memory.core.extraction.thread;

import com.openmemind.ai.memory.core.data.MemoryItem;
import java.time.Instant;
import java.util.Objects;

/**
 * Shared authoritative event-time fallback chain for thread derivation.
 */
public final class ThreadEventTimeResolver {

    private ThreadEventTimeResolver() {}

    public static Instant resolve(MemoryItem item) {
        Objects.requireNonNull(item, "item");
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        return item.createdAt();
    }
}
