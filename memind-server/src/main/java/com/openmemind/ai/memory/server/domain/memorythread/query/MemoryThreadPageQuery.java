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
package com.openmemind.ai.memory.server.domain.memorythread.query;

import java.util.List;

public record MemoryThreadPageQuery(
        int pageNo,
        int pageSize,
        String userId,
        String agentId,
        String status,
        List<String> orderBy) {

    public static MemoryThreadPageQuery of(
            int pageNo, int pageSize, String userId, String agentId, String status) {
        return new MemoryThreadPageQuery(
                pageNo,
                pageSize,
                userId,
                agentId,
                status,
                List.of("last_event_at DESC", "updated_at DESC", "id DESC"));
    }
}
