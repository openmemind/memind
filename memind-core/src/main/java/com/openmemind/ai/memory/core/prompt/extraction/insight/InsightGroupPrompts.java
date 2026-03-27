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
package com.openmemind.ai.memory.core.prompt.extraction.insight;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.List;

/**
 * Insight semantic grouping prompt builder.
 *
 * <p>Assigns memory items into thematic groups under a given insight dimension. Each group clusters
 * items sharing the same specific sub-topic. User input (items, existingGroupNames) is injected via
 * string concatenation (not placeholder replacement) to prevent prompt injection.
 */
public final class InsightGroupPrompts {

    private InsightGroupPrompts() {}

    // ===== Section definitions =====

    private static final String OBJECTIVE =
            """
            You are a semantic grouping engine. Assign memory items into thematic groups \
            under a given insight dimension.

            Primary goal: namespace stability, not novelty.
            Prefer reusing an existing group whenever it is a reasonable semantic fit.
            Create a new group only when the items form a genuinely distinct enduring \
            sub-theme that is not already represented.
            """;

    private static final String CONTEXT =
            """
            # Context

            You are grouping items for the insight dimension: "{{insight_type_name}}"
            Dimension description: {{insight_type_description}}

            This means:
            - The dimension description defines the semantic scope of this namespace.
            - Treat existing groups as the default namespace.
            - Existing groups are reusable semantic anchors, not loose suggestions.
            - A valid group name is a stable reusable sub-theme under this insight \
            dimension.
            - A group name is NOT a session heading, event label, tactic, summary \
            sentence, or stitched phrase.
            """;

    private static final String WORKFLOW =
            """
            # Grouping Principles

            1. Exclusivity
            - Each item MUST be assigned to exactly ONE group.

            2. High Cohesion
            - Items in the same group must share one clear enduring theme.
            - Test: Can you describe what ALL items in this group have in common using one specific phrase? If not, the group is too broad.

            3. Granularity
            - Group size is determined by semantic cohesion, not by count.
            - A single-item group is acceptable when the theme is genuinely distinct.
            - Do not merge different sub-themes just to reduce the number of groups.

            4. Theme Over Framing
            - Group by the enduring topic, not by tone, phrasing, one specific \
            scenario, or response style.
            - Prefer stable theme labels like "Incident Communication" over framing \
            labels like "How to explain outages clearly".

            5. Reuse Before Create
            - For each item, first try to place it into an existing group.
            - Reuse an existing group whenever the item reasonably fits its enduring \
            theme.
            - Create a new group only if NO existing group accurately captures the \
            item's enduring sub-theme.

            6. Naming
            - New group names must be natural, standalone theme labels that can be \
            reused for future items.
            - Prefer concise noun phrases, not sentence fragments or compressed \
            summaries.
            - Avoid session-heading, tactic, event, or stitched labels.
            - Do not repeat the insight dimension name as a group name.

            7. Language
            - Existing group names are fixed identifiers. Copy them EXACTLY as provided.
            - Do NOT translate reused group names.
            - New group names must follow the requested output language when one is \
            provided.
            - If no output language is provided, new group names should follow the \
            dominant item language.

            # Workflow

            ## Step 1 - Read the namespace
            - Review the dimension description.
            - Review existing groups first.
            - Ask whether each item can reasonably reuse an existing group.

            ## Step 2 - Evaluate each item
            - If an item fits an existing group, assign it there.
            - If multiple items share a new enduring sub-theme, create one new group \
            for them.
            - If an item is genuinely distinct, create a single-item group.

            ## Step 3 - Create a new group only when ALL are true
            - No existing group accurately fits.
            - The theme is reusable for future similar items.
            - The theme can be stated as one clear phrase.
            - The name is a theme label, not a session heading, event label, tactic, \
            or stitched phrase.

            ## Step 4 - Validate
            - Every item ID appears exactly once.
            - Every group has one clear enduring theme.
            - Existing group names are copied exactly.
            - New group names follow the requested output language.
            """;

    private static final String OUTPUT =
            """
            # Output Format

            Return ONLY a raw JSON object. No markdown fences. No surrounding text.
            {
              "assignments": [
                {
                  "groupName": "Exact Existing or Specific New Group Name",
                  "itemIds": ["id1", "id2"],
                  "reason": "Brief explanation of why these items share the same sub-topic"
                }
              ]
            }

            Field descriptions:
            - `groupName`: Exact existing group name copied as provided, or a new stable reusable sub-theme label.
            - `itemIds`: Array of item ID strings assigned to this group.
            - `reason`: CRITICAL. Brief explanation of the enduring shared theme.
            - This validates your grouping logic. For reasoning only, will NOT be stored.
            """;

    private static final String EXAMPLES =
            """
            # Examples

            ## Example 1 - Reuse existing groups before creating new ones

            Input:
            Dimension: collaboration
            Existing groups: Meeting Coordination, Data Privacy
            Items:
            - id: "10", content: The team needs a concise way to bring recurring planning meetings back to the agenda.
            - id: "11", content: Review comments often sit unanswered for several days.
            - id: "12", content: Incident updates should clearly state status, impact, and next checkpoint.
            - id: "13", content: Customer call recordings should not be shared outside the support team without consent.
            - id: "14", content: Internal docs should use direct step-by-step wording instead of long narrative paragraphs.

            Output:
            {
              "assignments": [
                {
                  "groupName": "Meeting Coordination",
                  "itemIds": ["10"],
                  "reason": "This item is about steering meetings back to the agenda, which matches the existing coordination theme."
                },
                {
                  "groupName": "Review Turnaround",
                  "itemIds": ["11"],
                  "reason": "This item is about how quickly review responses happen, which is a distinct reusable execution theme."
                },
                {
                  "groupName": "Incident Communication",
                  "itemIds": ["12"],
                  "reason": "This item is about how operational status updates are communicated during incidents."
                },
                {
                  "groupName": "Data Privacy",
                  "itemIds": ["13"],
                  "reason": "This item fits the existing privacy theme because it is about sharing sensitive recordings with proper consent."
                },
                {
                  "groupName": "Documentation Style",
                  "itemIds": ["14"],
                  "reason": "This item is about recurring preferences for how documentation should be written."
                }
              ]
            }

            Why this is correct:
            - Reused existing groups where they were a real semantic fit.
            - Created new groups only for distinct enduring sub-themes.
            - Used reusable theme labels, not one-off summaries.

            ## Example 2 - Requested language applies to new names, not reused names

            Requested output language: Spanish
            Input:
            Dimension: collaboration
            Existing groups: Meeting Coordination
            Items:
            - id: "20", content: The team needs a short phrase to move a meeting back to the agenda.
            - id: "21", content: Review responses are often delayed until the next sprint.

            Output:
            {
              "assignments": [
                {
                  "groupName": "Meeting Coordination",
                  "itemIds": ["20"],
                  "reason": "[same explanation written in Spanish]"
                },
                {
                  "groupName": "[same theme written in Spanish]",
                  "itemIds": ["21"],
                  "reason": "[same explanation written in Spanish]"
                }
              ]
            }

            Why this is correct:
            - The reused existing group stays exactly as provided.
            - Only the newly created group name follows the requested output language.

            ## Bad Example 1 - Too broad

            {
              "assignments": [
                {
                  "groupName": "Team Communication",
                  "itemIds": ["10", "11", "12", "13", "14"]
                }
              ]
            }

            Wrong because this merges meeting flow, review timing, incident updates, \
            privacy, and documentation into one loose bucket.

            ## Bad Example 2 - Wrong label type

            {
              "assignments": [
                {
                  "groupName": "Weekly Sync Notes",
                  "itemIds": ["10"]
                },
                {
                  "groupName": "How to explain outages clearly",
                  "itemIds": ["12"]
                },
                {
                  "groupName": "Privacy and review delays",
                  "itemIds": ["11", "13"]
                }
              ]
            }

            Wrong because these are a session heading, a tactic sentence, and a \
            stitched label instead of stable reusable theme names.

            # Final reminders

            - Reuse existing groups whenever reasonable.
            - New groups must be stable long-term sub-themes.
            - Avoid session-heading, tactic, event, or stitched labels.
            - Match output language for new groups; copy existing groups exactly.
            - Every item must appear exactly once.
            """;

    // ==================== Public API ====================

    public static PromptTemplate build(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames) {
        return build(insightType, items, existingGroupNames, null);
    }

    public static PromptTemplate build(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String language) {

        var userPromptContent = buildUserPrompt(insightType, items, existingGroupNames, language);

        var description =
                insightType.description() != null ? insightType.description() : insightType.name();

        return PromptTemplate.builder("insight-group")
                .section("objective", OBJECTIVE)
                .section("context", CONTEXT)
                .section("workflow", WORKFLOW)
                .section("output", OUTPUT)
                .section("examples", EXAMPLES)
                .variable("insight_type_name", insightType.name())
                .variable("insight_type_description", description)
                .userPrompt(userPromptContent)
                .build();
    }

    private static String buildUserPrompt(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String language) {

        var sb = new StringBuilder();
        if (language != null && !language.isBlank()) {
            sb.append("Requested Output Language for NEW group names: ")
                    .append(language)
                    .append("\n")
                    .append("Existing group names must be copied exactly as provided.\n")
                    .append("Do NOT translate reused group names.\n")
                    .append("Any NEW group name that you create MUST be written in ")
                    .append(language)
                    .append(".\n\n");
        }
        sb.append("# Insight Dimension\n");
        sb.append("Name: ").append(insightType.name()).append("\n");
        if (insightType.description() != null) {
            sb.append("Description: ").append(insightType.description()).append("\n");
        }

        sb.append("\n# Existing Groups\n");
        if (existingGroupNames.isEmpty()) {
            sb.append("(none)\n");
        } else {
            existingGroupNames.forEach(g -> sb.append("- ").append(g).append("\n"));
        }

        sb.append("\n# Memory Items\n");
        for (var item : items) {
            sb.append("- id: ")
                    .append(item.id())
                    .append(", content: ")
                    .append(item.content())
                    .append("\n");
        }

        return sb.toString();
    }
}
