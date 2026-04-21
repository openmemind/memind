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

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.store.graph.CommittedGraphView;
import java.util.List;

/**
 * Immutable committed snapshot prepared before an in-memory publish.
 */
public record InMemoryCommittedBatchView(List<MemoryItem> items, CommittedGraphView graph) {

    public InMemoryCommittedBatchView {
        items = items == null ? List.of() : List.copyOf(items);
        graph = graph == null ? CommittedGraphView.empty() : graph;
    }
}
