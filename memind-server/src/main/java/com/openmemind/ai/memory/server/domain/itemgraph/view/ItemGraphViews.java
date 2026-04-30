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
package com.openmemind.ai.memory.server.domain.itemgraph.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ItemGraphViews {

    private ItemGraphViews() {}

    public record NamedCount(String name, long count) {}

    public record SummaryView(
            long entityCount,
            long aliasCount,
            long mentionCount,
            long itemLinkCount,
            long cooccurrenceCount,
            List<NamedCount> graphBatchCountByState,
            List<NamedCount> itemLinkCountByType,
            List<NamedCount> entityCountByType) {}

    public record EntityView(
            Integer id,
            String memoryId,
            String userId,
            String agentId,
            String entityKey,
            String displayName,
            String entityType,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {}

    public record AliasView(
            Integer id,
            String memoryId,
            String userId,
            String agentId,
            String entityKey,
            String entityType,
            String normalizedAlias,
            Integer evidenceCount,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {}

    public record MentionView(
            Integer id,
            String memoryId,
            String userId,
            String agentId,
            Long itemId,
            String entityKey,
            Float confidence,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {}

    public record ItemLinkView(
            Integer id,
            String memoryId,
            String userId,
            String agentId,
            Long sourceItemId,
            Long targetItemId,
            String linkType,
            String relationCode,
            String evidenceSource,
            Double strength,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {}

    public record CooccurrenceView(
            Integer id,
            String memoryId,
            String userId,
            String agentId,
            String leftEntityKey,
            String rightEntityKey,
            Integer cooccurrenceCount,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {}

    public record BatchView(
            Integer id,
            String memoryId,
            String userId,
            String agentId,
            String extractionBatchId,
            String state,
            String errorMessage,
            Boolean retryPromotionSupported,
            Instant createdAt,
            Instant updatedAt) {}
}
