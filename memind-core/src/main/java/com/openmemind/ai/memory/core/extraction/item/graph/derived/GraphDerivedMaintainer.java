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
package com.openmemind.ai.memory.core.extraction.item.graph.derived;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.Set;

/**
 * Post-commit best-effort maintainer for derived graph data.
 */
@FunctionalInterface
public interface GraphDerivedMaintainer {

    void refresh(MemoryId memoryId, Set<String> affectedEntityKeys);

    static GraphDerivedMaintainer noOp() {
        return (memoryId, affectedEntityKeys) -> {};
    }
}
