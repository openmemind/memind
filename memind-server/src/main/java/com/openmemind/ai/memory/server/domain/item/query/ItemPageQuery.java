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
package com.openmemind.ai.memory.server.domain.item.query;

import java.util.List;

public record ItemPageQuery(
        int pageNo,
        int pageSize,
        String userId,
        String agentId,
        String scope,
        String category,
        String type,
        String rawDataId,
        List<String> orderBy) {

    public static ItemPageQuery of(
            int pageNo,
            int pageSize,
            String userId,
            String agentId,
            String scope,
            String category,
            String type,
            String rawDataId) {
        return new ItemPageQuery(
                pageNo,
                pageSize,
                userId,
                agentId,
                scope,
                category,
                type,
                rawDataId,
                List.of("observed_at DESC", "created_at DESC", "biz_id DESC"));
    }
}
