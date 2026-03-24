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
package com.openmemind.ai.memory.core.store.insight;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link InsightOperations}.
 */
public class InMemoryInsightOperations implements InsightOperations {

    private final Map<String, MemoryInsightType> insightTypeStore = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, MemoryInsight>> insightStore = new ConcurrentHashMap<>();

    private String key(MemoryId id) {
        return id.toIdentifier();
    }

    @Override
    public void upsertInsightTypes(List<MemoryInsightType> insightTypes) {
        if (insightTypes == null || insightTypes.isEmpty()) {
            return;
        }
        insightTypes.forEach(insightType -> insightTypeStore.put(insightType.name(), insightType));
    }

    @Override
    public Optional<MemoryInsightType> getInsightType(String insightType) {
        return Optional.ofNullable(insightTypeStore.get(insightType));
    }

    @Override
    public List<MemoryInsightType> listInsightTypes() {
        return new ArrayList<>(insightTypeStore.values());
    }

    @Override
    public void upsertInsights(MemoryId id, List<MemoryInsight> insights) {
        if (insights == null || insights.isEmpty()) {
            return;
        }
        Map<Long, MemoryInsight> map =
                insightStore.computeIfAbsent(key(id), k -> new ConcurrentHashMap<>());
        insights.forEach(insight -> map.put(insight.id(), insight));
    }

    @Override
    public Optional<MemoryInsight> getInsight(MemoryId id, Long insightId) {
        return Optional.ofNullable(insightStore.getOrDefault(key(id), Map.of()).get(insightId));
    }

    @Override
    public List<MemoryInsight> listInsights(MemoryId id) {
        return new ArrayList<>(insightStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public List<MemoryInsight> getInsightsByType(MemoryId id, String insightType) {
        return insightStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> Objects.equals(i.type(), insightType))
                .toList();
    }

    @Override
    public List<MemoryInsight> getInsightsByTier(MemoryId id, InsightTier tier) {
        return insightStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> tier.equals(i.tier()))
                .toList();
    }

    @Override
    public Optional<MemoryInsight> getLeafByGroup(MemoryId id, String type, String group) {
        return getInsightsByType(id, type).stream()
                .filter(i -> InsightTier.LEAF.equals(i.tier()) && Objects.equals(group, i.group()))
                .findFirst();
    }

    @Override
    public Optional<MemoryInsight> getBranchByType(MemoryId id, String type) {
        return getInsightsByType(id, type).stream()
                .filter(i -> InsightTier.BRANCH.equals(i.tier()))
                .findFirst();
    }

    @Override
    public Optional<MemoryInsight> getRootByType(MemoryId id, String type) {
        return getInsightsByType(id, type).stream()
                .filter(i -> InsightTier.ROOT.equals(i.tier()))
                .findFirst();
    }

    @Override
    public void deleteInsights(MemoryId id, Collection<Long> insightIds) {
        if (insightIds == null || insightIds.isEmpty()) {
            return;
        }
        Map<Long, MemoryInsight> map = insightStore.get(key(id));
        if (map == null) {
            return;
        }
        insightIds.forEach(map::remove);
    }
}
