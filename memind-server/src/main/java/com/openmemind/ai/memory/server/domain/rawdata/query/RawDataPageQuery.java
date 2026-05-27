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
package com.openmemind.ai.memory.server.domain.rawdata.query;

import java.time.Instant;
import java.util.List;

public record RawDataPageQuery(
        int pageNo,
        int pageSize,
        String userId,
        String agentId,
        Instant startTimeFrom,
        Instant startTimeTo,
        List<String> types,
        List<String> sourceClients,
        List<String> orderBy) {

    public RawDataPageQuery {
        types = types == null ? List.of() : List.copyOf(types);
        sourceClients = sourceClients == null ? List.of() : List.copyOf(sourceClients);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
    }

    public static RawDataPageQuery of(
            int pageNo,
            int pageSize,
            String userId,
            String agentId,
            Instant startTimeFrom,
            Instant startTimeTo) {
        return new RawDataPageQuery(
                pageNo,
                pageSize,
                userId,
                agentId,
                startTimeFrom,
                startTimeTo,
                List.of(),
                List.of(),
                List.of("start_time DESC", "created_at DESC"));
    }

    public static RawDataPageQuery openApi(
            int pageNo,
            int pageSize,
            String userId,
            String agentId,
            Instant startTimeFrom,
            Instant startTimeTo,
            List<String> types,
            List<String> sourceClients) {
        return new RawDataPageQuery(
                pageNo,
                pageSize,
                userId,
                agentId,
                startTimeFrom,
                startTimeTo,
                types,
                sourceClients,
                List.of("start_time DESC", "created_at DESC"));
    }
}
