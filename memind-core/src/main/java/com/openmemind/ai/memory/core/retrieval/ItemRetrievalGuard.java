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
package com.openmemind.ai.memory.core.retrieval;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;

/**
 * Shared retrieval-side policy gate for item candidates.
 */
public final class ItemRetrievalGuard {

    private ItemRetrievalGuard() {}

    public static boolean allows(MemoryItem item, QueryContext context) {
        if (item == null) {
            return false;
        }
        if (context.scope() != null && !context.scope().equals(item.scope())) {
            return false;
        }
        if (context.categories() != null
                && item.category() != null
                && !context.categories().contains(item.category())) {
            return false;
        }
        return ForesightFilter.isNotExpired(item);
    }
}
