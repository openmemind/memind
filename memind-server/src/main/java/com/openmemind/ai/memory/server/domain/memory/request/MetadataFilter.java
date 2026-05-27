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
package com.openmemind.ai.memory.server.domain.memory.request;

import java.util.List;

public record MetadataFilter(List<Condition> all, List<Condition> any, List<Condition> not) {

    public MetadataFilter {
        all = all == null ? List.of() : List.copyOf(all);
        any = any == null ? List.of() : List.copyOf(any);
        not = not == null ? List.of() : List.copyOf(not);
    }

    public boolean isEmpty() {
        return all.isEmpty() && any.isEmpty() && not.isEmpty();
    }

    public com.openmemind.ai.memory.core.retrieval.filter.MetadataFilter toCoreFilter() {
        return new com.openmemind.ai.memory.core.retrieval.filter.MetadataFilter(
                toCoreConditions(all), toCoreConditions(any), toCoreConditions(not));
    }

    private static List<com.openmemind.ai.memory.core.retrieval.filter.MetadataFilter.Condition>
            toCoreConditions(List<Condition> conditions) {
        return conditions.stream()
                .map(
                        condition ->
                                new com.openmemind.ai.memory.core.retrieval.filter.MetadataFilter
                                        .Condition(
                                        condition.path(), condition.op(), condition.value()))
                .toList();
    }

    public record Condition(String path, String op, Object value) {}
}
