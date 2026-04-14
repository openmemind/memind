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
package com.openmemind.ai.memory.core.store.resource;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryResource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ResourceOperations}.
 */
public class InMemoryResourceOperations implements ResourceOperations {

    private final Map<String, Map<String, MemoryResource>> resources = new ConcurrentHashMap<>();

    @Override
    public void upsertResources(MemoryId memoryId, List<MemoryResource> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, MemoryResource> scoped =
                resources.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(resource -> scoped.put(resource.id(), resource));
    }

    @Override
    public Optional<MemoryResource> getResource(MemoryId memoryId, String resourceId) {
        return Optional.ofNullable(
                resources.getOrDefault(memoryId.toIdentifier(), Map.of()).get(resourceId));
    }

    @Override
    public List<MemoryResource> listResources(MemoryId memoryId) {
        return List.copyOf(resources.getOrDefault(memoryId.toIdentifier(), Map.of()).values());
    }
}
