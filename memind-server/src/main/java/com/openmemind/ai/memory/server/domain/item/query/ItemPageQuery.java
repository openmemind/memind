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
        List<String> categories,
        String type,
        String rawDataId,
        List<String> sourceClients,
        List<String> rawDataTypes,
        java.time.Instant occurredAtFrom,
        java.time.Instant occurredAtTo,
        List<String> orderBy) {

    public ItemPageQuery {
        categories = categories == null ? List.of() : List.copyOf(categories);
        sourceClients = sourceClients == null ? List.of() : List.copyOf(sourceClients);
        rawDataTypes = rawDataTypes == null ? List.of() : List.copyOf(rawDataTypes);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
    }

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
                List.of(),
                type,
                rawDataId,
                List.of(),
                List.of(),
                null,
                null,
                List.of("observed_at DESC", "created_at DESC", "biz_id DESC"));
    }

    public static ItemPageQuery openApi(
            int pageNo,
            int pageSize,
            String userId,
            String agentId,
            String scope,
            List<String> categories,
            List<String> sourceClients,
            List<String> rawDataTypes,
            java.time.Instant occurredAtFrom,
            java.time.Instant occurredAtTo) {
        return new ItemPageQuery(
                pageNo,
                pageSize,
                userId,
                agentId,
                scope,
                null,
                categories,
                null,
                null,
                sourceClients,
                rawDataTypes,
                occurredAtFrom,
                occurredAtTo,
                List.of("observed_at DESC", "created_at DESC", "biz_id DESC"));
    }
}
