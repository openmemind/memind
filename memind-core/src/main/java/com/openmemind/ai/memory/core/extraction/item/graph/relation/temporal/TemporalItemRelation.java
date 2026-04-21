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
package com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal;

import com.openmemind.ai.memory.core.extraction.item.graph.relation.ItemGraphRelation;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.util.Objects;

/**
 * Temporal item relation with explicit subtype.
 */
public record TemporalItemRelation(
        long sourceItemId, long targetItemId, TemporalRelationCode relationCode, double strength)
        implements ItemGraphRelation {

    public TemporalItemRelation {
        validateDistinctEndpoints(sourceItemId, targetItemId);
        relationCode = Objects.requireNonNull(relationCode, "relationCode");
        strength = normalizeStrength(strength);
    }

    @Override
    public ItemLinkType linkType() {
        return ItemLinkType.TEMPORAL;
    }

    private static void validateDistinctEndpoints(long sourceItemId, long targetItemId) {
        if (sourceItemId == targetItemId) {
            throw new IllegalArgumentException("source and target item ids must differ");
        }
    }

    private static double normalizeStrength(double strength) {
        return Math.max(0.0d, Math.min(1.0d, strength));
    }
}
