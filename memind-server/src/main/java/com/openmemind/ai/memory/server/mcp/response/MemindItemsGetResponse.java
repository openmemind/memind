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
package com.openmemind.ai.memory.server.mcp.response;

import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryItemsResponse;
import java.util.List;

public record MemindItemsGetResponse(
        List<QueryMemoryItemsResponse.MemoryItemView> items, List<String> missingItemIds) {

    public MemindItemsGetResponse {
        items = items == null ? List.of() : List.copyOf(items);
        missingItemIds = missingItemIds == null ? List.of() : List.copyOf(missingItemIds);
    }
}
