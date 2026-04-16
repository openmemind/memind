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
package com.openmemind.ai.memory.core.extraction.insight.graph;

import com.openmemind.ai.memory.core.builder.InsightGraphAssistOptions;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Formats compact graph hint blocks for prompt injection under a hard character budget.
 */
public final class GraphPromptContextFormatter {

    private final InsightGraphAssistOptions options;

    public GraphPromptContextFormatter(InsightGraphAssistOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public String formatGroupingHints(
            List<GroupingClusterHint> clusters, List<String> relationHints) {
        if ((clusters == null || clusters.isEmpty())
                && (relationHints == null || relationHints.isEmpty())) {
            return "";
        }

        StringBuilder builder = new StringBuilder("GraphGroupingHints:\n");
        if (clusters != null) {
            int index = 1;
            for (GroupingClusterHint cluster : clusters) {
                if (cluster == null) {
                    continue;
                }
                builder.append("- cluster_")
                        .append(index++)
                        .append(": items=")
                        .append(cluster.itemIds())
                        .append(", entities=")
                        .append(cluster.representativeEntities())
                        .append(", relations=")
                        .append(cluster.relationSummary())
                        .append('\n');
            }
        }
        appendRelationHints(builder, relationHints);
        return clamp(builder.toString());
    }

    public String formatEvidenceHints(
            String groupName,
            List<Long> orderedItemIds,
            List<String> representativeEntities,
            List<String> relationHints) {
        if ((orderedItemIds == null || orderedItemIds.isEmpty())
                && (representativeEntities == null || representativeEntities.isEmpty())
                && (relationHints == null || relationHints.isEmpty())) {
            return "";
        }

        StringBuilder builder = new StringBuilder("GraphEvidenceHints:\n");
        if (groupName != null && !groupName.isBlank()) {
            builder.append("- group: ").append(groupName).append('\n');
        }
        if (orderedItemIds != null && !orderedItemIds.isEmpty()) {
            builder.append("- suggested_item_order: ").append(orderedItemIds).append('\n');
        }
        if (representativeEntities != null && !representativeEntities.isEmpty()) {
            builder.append("- representative_entities: ")
                    .append(representativeEntities)
                    .append('\n');
        }
        appendRelationHints(builder, relationHints);
        return clamp(builder.toString());
    }

    private void appendRelationHints(StringBuilder builder, List<String> relationHints) {
        if (relationHints == null || relationHints.isEmpty()) {
            return;
        }
        builder.append("- local_relations: ");
        StringJoiner joiner = new StringJoiner("; ");
        relationHints.forEach(joiner::add);
        builder.append(joiner).append('\n');
    }

    private String clamp(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.length() <= options.maxContextChars()) {
            return normalized;
        }
        int boundary = normalized.lastIndexOf('\n', options.maxContextChars());
        if (boundary > 0) {
            return normalized.substring(0, boundary).trim();
        }
        return normalized.substring(0, options.maxContextChars()).trim();
    }

    public record GroupingClusterHint(
            List<Long> itemIds, List<String> representativeEntities, String relationSummary) {

        public GroupingClusterHint {
            itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
            representativeEntities =
                    representativeEntities == null
                            ? List.of()
                            : List.copyOf(representativeEntities);
            relationSummary = relationSummary == null ? "none" : relationSummary;
        }
    }
}
