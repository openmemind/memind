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

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.prompt.PromptBuilderSupport;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;

/**
 * InsightPoint generation prompt builder (LEAF level).
 *
 * <p>Assembles section constants in fixed order, replaces placeholders, and appends the Input
 * block. User input (existingPoints, newItems) is injected via string concatenation (not
 * placeholder replacement) to prevent prompt injection.
 */
public final class InsightLeafPrompts {

    private InsightLeafPrompts() {}

    private static final String DEFAULT_OBJECTIVE =
            """
            You are a Memory Insight Synthesizer. Analyze memory items belonging to one \
            semantic group and produce higher-level insight points — patterns, themes, and \
            conclusions that emerge from COMBINING multiple items together.

            A valid insight is something a reader could NOT learn from any single memory \
            item alone. If a point merely restates one item, it is NOT an insight.\
            """;

    // ===== CONTEXT =====

    private static final String DEFAULT_CONTEXT =
            """
            # Context

            You are generating insight points for one semantic group within an insight \
            dimension. Your output becomes a LEAF node in a larger insight tree, where \
            multiple LEAFs are later aggregated into a BRANCH summary.

            Current insight dimension: "{{insight_type}}"
            Dimension description: {{insight_description}}
            Current group: "{{group_name}}"

            This means:
            - The insight dimension defines WHAT aspect of the user to analyze \
            (e.g., "identity" = who the user is; "directives" = durable collaboration rules; \
            "playbooks" = reusable handling methods; "resolutions" = resolved problem knowledge).
            - The group is a semantic cluster WITHIN that dimension \
            (e.g., under "identity", groups might be "professional_background", \
            "personal_interests").
            - Focus on patterns WITHIN this group. Cross-group synthesis happens at \
            the BRANCH level, not here.
            - Aim for the right granularity: richer than individual items, but specific \
            enough to remain meaningful when aggregated upstream.\
            """;

    // ===== WORKFLOW (includes Core Principles, Steps, Type Decision Logic) =====

    private static final String FULL_REWRITE_WORKFLOW =
            """
            # Core Principles
            1. Full Replacement: Output the COMPLETE current-state list. Not delta patches. \
            If an existing point is still valid, it MUST appear in your output.
            2. Synthesis Over Restatement: Each point must add value beyond any single item. \
            Cluster related items and produce ONE synthesized point per cluster.
            3. Source Tracking: Every point MUST list ALL contributing sourceItemIds — both \
            inherited from existing points and newly added.
            4. Atomicity: Each point covers exactly ONE coherent theme. If you find yourself \
            chaining unrelated facts with "Additionally" or semicolons, SPLIT them.
            5. Brevity: 1-3 sentences per point. Enough to convey the synthesis, concise \
            enough to be scannable.
            6. Plain Text Only: Point content MUST be plain text. No markdown, no bullet \
            lists, no headers.
            7. Language: Output MUST match the input language exactly.

            # Workflow

            ## Step 1 — Parse & Analyze
            - Understand each existing point's content, type, confidence, and sourceItemIds.
            - Read ALL new items together. Identify recurring themes, temporal patterns, \
            causal links, and contradictions.
            - Filter noise: ignore trivial one-time events or exact duplicates.

            ## Step 2 — Synthesize (Full Rewrite)
            - Start from existing points as baseline, then reshape:
              - Still-valid existing points: keep and merge new supporting sourceItemIds.
              - Strengthened by new items: rewrite with updated understanding.
              - Fresh patterns from new items: create new synthesized points.
              - Contradicted by new items: rewrite to reflect temporal evolution; do NOT \
            silently drop historical context.
              - Overlapping points: merge into one with combined sourceItemIds.
              - Fully superseded points with no historical value: drop.
            - Before adding any point, ask: "Could a reader learn this from just ONE item?" \
            If yes → combine it with related items or skip it.

            ## Step 3 — Validate
            Before outputting, verify each point against this checklist:
            - Could a reader learn this from just ONE item? If yes → not an insight, fix it.
            - Does every point carry complete sourceItemIds after merge?
            - Are there duplicate or overlapping points? If yes → merge.
            - Is each point ≤ approximately {{target_length}} tokens?
            - Is the output valid JSON with no markdown fences?

            # Type Decision Logic

            For each point, determine its type:

            | Ask yourself                                                    | Type      |
            |-----------------------------------------------------------------|-----------|
            | Does this integrate facts from multiple items into one theme?   | SUMMARY   |
            | Does this infer a conclusion not directly stated in any item?   | REASONING |

            Guidelines:
            - SUMMARY = "what is true" (consolidated from multiple sources)
            - REASONING = "what it means / why / what's next" (inferred from evidence)
            - When in doubt, prefer REASONING — insights that reveal WHY are more valuable.\
            """;

    private static final String POINT_OPS_WORKFLOW =
            """
            # Core Principles
            1. Point Operations Only: Output operations against the EXISTING point list. \
            Do NOT rewrite the entire list.
            2. Preserve Stable Points: If an existing point is still valid and unchanged, \
            emit NO operation for it.
            3. Prefer UPDATE Over DELETE: When a point remains directionally valid but needs \
            refinement, use UPDATE. Use DELETE only when a point is truly obsolete or merged \
            away.
            4. Synthesis Over Restatement: Each new or updated point must add value beyond any \
            single item. Cluster related items and produce ONE synthesized point per cluster.
            5. Source Tracking: Every emitted point MUST list ALL contributing sourceItemIds — \
            both inherited from existing points and newly added.
            6. Atomicity: Each point covers exactly ONE coherent theme. If you find yourself \
            chaining unrelated facts with "Additionally" or semicolons, SPLIT them.
            7. Brevity: 1-3 sentences per point. Enough to convey the synthesis, concise \
            enough to be scannable.
            8. Plain Text Only: Point content MUST be plain text. No markdown, no bullet \
            lists, no headers.
            9. Language: Output MUST match the input language exactly.

            # Workflow

            ## Step 1 — Parse & Analyze
            - Understand each existing point's content, type, confidence, and sourceItemIds.
            - Read ALL new items together. Identify:
              - Recurring themes: multiple items about the same topic or domain.
              - Temporal patterns: changes, progressions, or trends over time.
              - Causal links: item A explains or causes item B.
              - Contradictions: new info conflicting with existing knowledge.
            - Filter noise: ignore trivial one-time events or exact duplicates.

            ## Step 2 — Emit Operations
            - Start from existing points as baseline, then decide the MINIMAL set of changes:
              - Still-valid existing point: emit nothing.
              - Strengthened or corrected existing point: emit UPDATE targeting that point id.
              - Fresh pattern from new items: emit ADD with a new synthesized point.
              - Contradicted point with no remaining value: emit DELETE targeting that point id.
              - Merge scenario: UPDATE the kept point with merged content/sourceItemIds, then \
            DELETE the absorbed point.
            - Before emitting ADD or UPDATE, ask: "Could a reader learn this from just ONE item?" \
            If yes → combine it with related items or skip it.
            - If nothing changes after analysis, return `"operations": []`.

            ## Step 3 — Validate
            Before outputting, verify each operation against this checklist:
            - UPDATE and DELETE target an existing point id from the input.
            - ADD does not specify a target id.
            - Every emitted point includes complete sourceItemIds after merge/update.
            - Could a reader learn an emitted point from just ONE item? If yes → not an insight.
            - Are there duplicate or overlapping emitted points? If yes → merge first.
            - Is each emitted point ≤ approximately {{target_length}} tokens?
            - Is the output valid JSON with no markdown fences?

            # Type Decision Logic

            For each point, determine its type:

            | Ask yourself                                                    | Type      |
            |-----------------------------------------------------------------|-----------|
            | Does this integrate facts from multiple items into one theme?   | SUMMARY   |
            | Does this infer a conclusion not directly stated in any item?   | REASONING |

            Guidelines:
            - SUMMARY = "what is true" (consolidated from multiple sources)
            - REASONING = "what it means / why / what's next" (inferred from evidence)
            - When in doubt, prefer REASONING — insights that reveal WHY are more valuable.\
            """;

    // ===== OUTPUT FORMAT =====

    public static final String FULL_REWRITE_OUTPUT =
            """
            # Output Format

            Return ONLY a raw JSON object. No markdown fences. No surrounding text.
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "Synthesized observation integrating multiple memory items...",
                  "confidence": 0.85,
                  "sourceItemIds": ["42", "43", "45"],
                  "point_reason": "Explain which items contribute what and why this is a stable synthesized point."
                },
                {
                  "type": "REASONING",
                  "content": "Inference or pattern derived from combining multiple facts...",
                  "confidence": 0.70,
                  "sourceItemIds": ["10", "11", "15"],
                  "point_reason": "Explain the supporting evidence chain and why this is an inference."
                }
              ]
            }

            Field descriptions:
            - `type`: "SUMMARY" or "REASONING" (see Type Decision Logic above).
            - `content`: Plain text synthesized statement (1-3 sentences).
            - `confidence`: Confidence score in [0, 1].
            - `sourceItemIds`: Array of strings. Updated points MUST include both existing and new item IDs.
            - `point_reason`: Reasoning-only field that explains synthesis logic; it will NOT be stored.\
            """;

    public static final String POINT_OPS_OUTPUT =
            """
            # Output Format

            Return ONLY a raw JSON object. No markdown fences. No surrounding text.
            {
              "operations": [
                {
                  "op": "UPDATE",
                  "targetPointId": "pt_existing_1",
                  "point": {
                    "pointId": "pt_existing_1",
                    "type": "SUMMARY",
                    "content": "Updated synthesized observation integrating multiple memory items...",
                    "confidence": 0.85,
                    "sourceItemIds": ["42", "43", "45"]
                  },
                  "reason": "This point remains valid but new items refine it with stronger evidence."
                },
                {
                  "op": "ADD",
                  "point": {
                    "type": "REASONING",
                    "content": "Inference or pattern derived from combining multiple facts...",
                    "confidence": 0.70,
                    "sourceItemIds": ["10", "11", "15"]
                  },
                  "reason": "New cross-item pattern not covered by any existing point."
                },
                {
                  "op": "DELETE",
                  "targetPointId": "pt_existing_2",
                  "reason": "This point is obsolete or fully absorbed by another point."
                }
              ]
            }

            Field descriptions:
            - `op`: "ADD", "UPDATE", or "DELETE".
            - `targetPointId`: Required for UPDATE/DELETE. Must equal an existing point's `pointId`.
            - `point`: Required for ADD/UPDATE.
            - `reason`: Short explanation of why this operation is needed.
            - `point.type`: "SUMMARY" or "REASONING" (see Type Decision Logic above).
            - `point.content`: Plain text synthesized statement (1-3 sentences).
            - `point.pointId`: For UPDATE, keep it identical to `targetPointId`. For ADD, omit it.
            - `point.confidence`:
              For SUMMARY:
                - 0.90+: Theme confirmed across 3+ items with strong consistency.
                - 0.80-0.89: Clear pattern from 2+ items.
                - 0.70-0.79: Reasonable consolidation, some items only loosely related.
              For REASONING:
                - 0.80+: Strong causal/logical chain supported by multiple items.
                - 0.65-0.79: Reasonable inference from available evidence.
                - < 0.65: Speculative — include only if highly valuable.
            - `point.sourceItemIds`: Array of strings. SUMMARY should have 2+ IDs. \
            Updated points MUST include both existing and new item IDs.
            - Return `"operations": []` when analysis finds no durable change.\
            """;

    // ===== EXAMPLES =====

    private static final String FULL_REWRITE_EXAMPLES =
            """
            # Examples

            ## Example 1 — First-time synthesis

            Input:
            Existing Points: (none)
            New items:
            - id: "42", content: User loves spicy Sichuan food
            - id: "44", content: User recently tried a new Thai restaurant and loved the pad thai
            - id: "45", content: User mentioned cooking Sichuan dishes at home on weekends

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User is enthusiastic about Asian cuisine, especially Sichuan and Thai food, and also cooks Sichuan dishes at home.",
                  "confidence": 0.90,
                  "sourceItemIds": ["42", "44", "45"],
                  "point_reason": "These items reveal one durable cuisine preference pattern."
                }
              ]
            }

            ## Example 2 — Incremental rewrite

            Input:
            Existing Points:
            - [SUMMARY] User prefers working from home and values schedule flexibility (confidence: 0.85, sources: [10, 11])

            New items:
            - id: "55", content: User recently started going to the office 3 days a week
            - id: "57", content: User said the in-office collaboration has improved project velocity

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User transitioned from fully remote work to a hybrid schedule. They still value flexibility, but now see in-office collaboration as improving project velocity.",
                  "confidence": 0.90,
                  "sourceItemIds": ["10", "11", "55", "57"],
                  "point_reason": "The existing point remains valid but must be rewritten with the new evidence."
                }
              ]
            }\
            """;

    private static final String POINT_OPS_EXAMPLES =
            """
            # Examples

            ## Example 1 — First-time synthesis

            Input:
            Existing Points: (none)
            New items:
            - id: "42", content: User loves spicy Sichuan food
            - id: "44", content: User recently tried a new Thai restaurant and loved the pad thai
            - id: "45", content: User mentioned cooking Sichuan dishes at home on weekends

            Output:
            {
              "operations": [
                {
                  "op": "ADD",
                  "point": {
                    "type": "SUMMARY",
                    "content": "User is enthusiastic about Asian cuisine, especially Sichuan and Thai food, and also cooks Sichuan dishes at home.",
                    "confidence": 0.90,
                    "sourceItemIds": ["42", "44", "45"]
                  },
                  "reason": "These items form one durable synthesized preference pattern not represented by any existing point."
                }
              ]
            }

            ## Example 2 — Incremental update

            Input:
            Existing Points:
            - pointId: pt_existing_1
              type: SUMMARY
              content: User prefers working from home and values schedule flexibility
              confidence: 0.85
              sourceItemIds: ["10", "11"]

            New items:
            - id: "55", content: User recently started going to the office 3 days a week
            - id: "57", content: User said the in-office collaboration has improved project velocity

            Output:
            {
              "operations": [
                {
                  "op": "UPDATE",
                  "targetPointId": "pt_existing_1",
                  "point": {
                    "pointId": "pt_existing_1",
                    "type": "SUMMARY",
                    "content": "User transitioned from fully remote work to a hybrid schedule. They still value flexibility, but now see in-office collaboration as improving project velocity.",
                    "confidence": 0.90,
                    "sourceItemIds": ["10", "11", "55", "57"]
                  },
                  "reason": "The existing point remains directionally valid but needs to reflect the new hybrid-work evidence."
                }
              ]
            }

            ## Example 3 — No durable change

            Input:
            Existing Points:
            - pointId: pt_existing_1
              type: SUMMARY
              content: User prefers concise technical answers
              confidence: 0.90
              sourceItemIds: ["70", "71"]

            New items:
            - id: "88", content: User again asked for a concise answer format

            Output:
            {
              "operations": []
            }\
            """;

    // ==================== Public API ====================

    public static PromptTemplate build(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {
        return build(
                PromptRegistry.EMPTY,
                insightType,
                groupName,
                existingPoints,
                newItems,
                targetTokens);
    }

    public static PromptTemplate buildDefault() {
        return fullRewriteBuilder().build();
    }

    public static PromptTemplate buildPreview() {
        var insightType = DefaultInsightTypes.identity();
        return fullRewriteBuilder()
                .variable("insight_type", insightType.name())
                .variable(
                        "insight_description", PromptBuilderSupport.descriptionOrName(insightType))
                .variable("group_name", "professional_background")
                .variable("target_length", String.valueOf(insightType.targetTokens()))
                .build();
    }

    public static PromptTemplate buildPointOps(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {
        return buildPointOps(
                PromptRegistry.EMPTY,
                insightType,
                groupName,
                existingPoints,
                newItems,
                targetTokens);
    }

    public static PromptTemplate buildPointOpsDefault() {
        return pointOpsBuilder().build();
    }

    public static PromptTemplate buildPointOpsPreview() {
        var insightType = DefaultInsightTypes.identity();
        return pointOpsBuilder()
                .variable("insight_type", insightType.name())
                .variable(
                        "insight_description", PromptBuilderSupport.descriptionOrName(insightType))
                .variable("group_name", "professional_background")
                .variable("target_length", String.valueOf(insightType.targetTokens()))
                .build();
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {

        var userPromptContent =
                buildFullRewriteInputBlock(
                        insightType, groupName, existingPoints, newItems, targetTokens);
        var builder =
                registry.hasOverride(PromptType.INSIGHT_LEAF)
                        ? PromptTemplate.builder("insight-point")
                                .section("system", registry.getOverride(PromptType.INSIGHT_LEAF))
                        : fullRewriteBuilder();

        return builder.variable("insight_type", insightType.name())
                .variable(
                        "insight_description", PromptBuilderSupport.descriptionOrName(insightType))
                .variable("group_name", groupName)
                .variable("target_length", String.valueOf(targetTokens))
                .userPrompt(userPromptContent)
                .build();
    }

    public static PromptTemplate buildPointOps(
            PromptRegistry registry,
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {

        var userPromptContent =
                buildPointOpsInputBlock(
                        insightType, groupName, existingPoints, newItems, targetTokens);
        var builder =
                registry.hasOverride(PromptType.INSIGHT_LEAF_POINT_OPS)
                        ? PromptTemplate.builder("insight-point-ops")
                                .section(
                                        "system",
                                        registry.getOverride(PromptType.INSIGHT_LEAF_POINT_OPS))
                        : pointOpsBuilder();

        return builder.variable("insight_type", insightType.name())
                .variable(
                        "insight_description", PromptBuilderSupport.descriptionOrName(insightType))
                .variable("group_name", groupName)
                .variable("target_length", String.valueOf(targetTokens))
                .userPrompt(userPromptContent)
                .build();
    }

    private static PromptTemplate.Builder fullRewriteBuilder() {
        return PromptBuilderSupport.coreSections(
                "insight-point",
                DEFAULT_OBJECTIVE,
                DEFAULT_CONTEXT,
                FULL_REWRITE_WORKFLOW,
                FULL_REWRITE_OUTPUT,
                FULL_REWRITE_EXAMPLES);
    }

    private static PromptTemplate.Builder pointOpsBuilder() {
        return PromptBuilderSupport.coreSections(
                "insight-point-ops",
                DEFAULT_OBJECTIVE,
                DEFAULT_CONTEXT,
                POINT_OPS_WORKFLOW,
                POINT_OPS_OUTPUT,
                POINT_OPS_EXAMPLES);
    }

    private static String buildFullRewriteInputBlock(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {

        var sb = new StringBuilder();
        sb.append("# Insight Type\n");
        sb.append("Name: ").append(insightType.name()).append("\n");
        if (insightType.description() != null) {
            sb.append("Description: ").append(insightType.description()).append("\n");
        }
        sb.append("Group: ").append(groupName).append("\n");
        sb.append("Token budget: ").append(targetTokens).append("\n");

        if (existingPoints != null && !existingPoints.isEmpty()) {
            sb.append("\n# Existing Points\n");
            for (var point : existingPoints) {
                sb.append("- [")
                        .append(point.type())
                        .append("] ")
                        .append(point.content())
                        .append(" (confidence: ")
                        .append(point.confidence())
                        .append(", sources: ")
                        .append(point.sourceItemIds())
                        .append(")\n");
            }
        }

        sb.append("\n# New Memory Items\n");
        for (var item : newItems) {
            sb.append("- id: ")
                    .append(item.id())
                    .append(", content: ")
                    .append(item.content())
                    .append("\n");
        }

        return sb.toString();
    }

    private static String buildPointOpsInputBlock(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {

        var sb = new StringBuilder();
        sb.append("# Insight Type\n");
        sb.append("Name: ").append(insightType.name()).append("\n");
        if (insightType.description() != null) {
            sb.append("Description: ").append(insightType.description()).append("\n");
        }
        sb.append("Group: ").append(groupName).append("\n");
        sb.append("Token budget: ").append(targetTokens).append("\n");

        if (existingPoints != null && !existingPoints.isEmpty()) {
            sb.append("\n# Existing Points\n");
            for (int i = 0; i < existingPoints.size(); i++) {
                var p = existingPoints.get(i);
                sb.append("- pointId: ")
                        .append(p.pointId())
                        .append("\n  type: ")
                        .append(p.type())
                        .append("\n  content: ")
                        .append(p.content())
                        .append("\n  confidence: ")
                        .append(p.confidence())
                        .append("\n  sourceItemIds: ")
                        .append(p.sourceItemIds())
                        .append("\n");
            }
        }

        sb.append("\n# New Memory Items\n");
        for (var item : newItems) {
            sb.append("- id: ")
                    .append(item.id())
                    .append(", content: ")
                    .append(item.content())
                    .append("\n");
        }

        return sb.toString();
    }
}
