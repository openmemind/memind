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
package com.openmemind.ai.memory.core.extraction.insight.support;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.InsightPointRef;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class InsightPointEvidenceNormalizer {

    public List<InsightPoint> normalizeLeafPoints(List<InsightPoint> points) {
        return safe(points).stream()
                .map(
                        point ->
                                new InsightPoint(
                                        point.pointId(),
                                        point.type(),
                                        point.content(),
                                        normalizeSourceItemIds(point.sourceItemIds()),
                                        List.of(),
                                        point.metadata()))
                .toList();
    }

    public List<InsightPoint> normalizeBranchPoints(
            List<InsightPoint> points, List<MemoryInsight> leafInsights) {
        return normalizeDirectRefs(points, leafInsights, InsightTier.LEAF);
    }

    public List<InsightPoint> normalizeRootPoints(
            List<InsightPoint> points, List<MemoryInsight> branchInsights) {
        return normalizeDirectRefs(points, branchInsights, InsightTier.BRANCH);
    }

    private List<InsightPoint> normalizeDirectRefs(
            List<InsightPoint> points,
            List<MemoryInsight> evidenceInsights,
            InsightTier expectedTier) {
        var validRefs = buildValidRefIndex(evidenceInsights, expectedTier);
        return safe(points).stream()
                .map(
                        point -> {
                            var refs = normalizeRefs(point.sourcePointRefs());
                            for (var ref : refs) {
                                var pointIds = validRefs.get(ref.insightId());
                                if (pointIds == null || !pointIds.contains(ref.pointId())) {
                                    throw new IllegalArgumentException(
                                            "Dangling sourcePointRef: " + ref);
                                }
                            }
                            return new InsightPoint(
                                    point.pointId(),
                                    point.type(),
                                    point.content(),
                                    List.of(),
                                    refs,
                                    point.metadata());
                        })
                .toList();
    }

    private Map<Long, Set<String>> buildValidRefIndex(
            List<MemoryInsight> evidenceInsights, InsightTier expectedTier) {
        return safeInsights(evidenceInsights).stream()
                .filter(insight -> matchesTier(insight, expectedTier))
                .collect(
                        Collectors.toMap(
                                MemoryInsight::id,
                                insight ->
                                        safe(insight.points()).stream()
                                                .map(InsightPoint::pointId)
                                                .filter(
                                                        pointId ->
                                                                pointId != null
                                                                        && !pointId.isBlank())
                                                .collect(
                                                        Collectors.toCollection(
                                                                LinkedHashSet::new)),
                                (left, right) -> left,
                                java.util.LinkedHashMap::new));
    }

    private boolean matchesTier(MemoryInsight insight, InsightTier expectedTier) {
        if (expectedTier == InsightTier.LEAF) {
            return insight.tier() == null || insight.tier() == InsightTier.LEAF;
        }
        return insight.tier() == expectedTier;
    }

    private List<String> normalizeSourceItemIds(List<String> sourceItemIds) {
        return safe(sourceItemIds).stream().distinct().sorted().toList();
    }

    private List<InsightPointRef> normalizeRefs(List<InsightPointRef> refs) {
        return safe(refs).stream()
                .distinct()
                .sorted(
                        Comparator.comparing(InsightPointRef::insightId)
                                .thenComparing(InsightPointRef::pointId))
                .toList();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private List<MemoryInsight> safeInsights(List<MemoryInsight> insights) {
        return insights == null ? List.of() : List.copyOf(insights);
    }
}
