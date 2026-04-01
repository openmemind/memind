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
package com.openmemind.ai.memory.benchmark.core.checkpoint;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record CheckpointState(Map<String, Set<String>> completedItems) {

    public CheckpointState() {
        this(new LinkedHashMap<>());
    }

    public void markCompleted(String stage, String itemId) {
        completedItems.computeIfAbsent(stage, ignored -> new LinkedHashSet<>()).add(itemId);
    }

    public boolean isCompleted(String stage, String itemId) {
        return completedItems.getOrDefault(stage, Set.of()).contains(itemId);
    }

    public Set<String> getCompleted(String stage) {
        return Set.copyOf(completedItems.getOrDefault(stage, Set.of()));
    }
}
