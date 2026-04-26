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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize;

import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.time.Instant;
import java.util.List;

/**
 * Store-agnostic normalized mention emitted by {@link EntityHintNormalizer}.
 */
public record NormalizedEntityMentionCandidate(
        long itemId,
        String memoryKey,
        String rawName,
        String rawTypeLabel,
        String normalizedName,
        String displayName,
        GraphEntityType entityType,
        String preResolutionEntityKey,
        float salience,
        List<EntityAliasObservation> aliasObservations,
        Instant createdAt) {

    public NormalizedEntityMentionCandidate {
        aliasObservations = aliasObservations == null ? List.of() : List.copyOf(aliasObservations);
    }
}
