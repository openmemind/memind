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
package com.openmemind.ai.memory.core.store.item;

/**
 * Capability metadata for item-store read paths used by replay and rebuild code.
 *
 * <p>These flags describe store-side pushdown, not merely API availability. Implementations that
 * inherit correctness-first default methods backed by {@link ItemOperations#listItems(MemoryId)}
 * should keep the default disabled capabilities.
 */
public record ItemOperationsCapabilities(
        boolean boundedIdLookup, boolean boundedRangeReads, boolean maxItemIdLookup) {

    public static ItemOperationsCapabilities defaults() {
        return new ItemOperationsCapabilities(false, false, false);
    }
}
