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
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.prompt.PromptBuilderSupport;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;

/**
 * BRANCH aggregation prompt builder.
 *
 * <p>Synthesizes all LEAF insights under the same InsightType into a BRANCH-level aggregated
 * summary. The BRANCH captures the cross-group view across all semantic groups within a single
 * insight dimension.
 */
public final class BranchAggregationPrompts {

    private BranchAggregationPrompts() {}

    private static final String SYSTEM_OBJECTIVE =
            """
            You are a BRANCH aggregation engine. Your task is to synthesize all LEAF-level \
            insight points under a single insight dimension into BRANCH-level insights. \
            Each LEAF represents a different semantic group that has already been \
            individually analyzed — your job is to find cross-group patterns, resolve \
            contradictions, and update the BRANCH into a coherent higher-level view.\
            """;

    private static final String SYSTEM_CONTEXT =
            """
            # Context

            You are aggregating LEAF insights for the insight dimension: "{{insight_type_name}}"
            Dimension description: {{insight_type_description}}

            Your output is a BRANCH node in the insight tree:
            - Upstream (your input): LEAF nodes, each covering one semantic group within \
            this dimension. LEAFs contain group-level synthesis — they are already richer \
            than raw memory items.
            - Downstream (your output consumer): ROOT synthesis, which combines ALL BRANCH \
            dimensions into a comprehensive user portrait.

            This means:
            - Focus on cross-group patterns WITHIN this dimension. What emerges when you \
            look at all groups together that no single group reveals alone?
            - Elevate, don't copy. BRANCH points should be broader and more abstract than \
            LEAF points.
            - Do NOT attempt a full user portrait — that is the ROOT layer's job.\
            """;

    private static final String FULL_REWRITE_WORKFLOW =
            """
            # Core Principles
            1. Full Replacement: Output the COMPLETE current-state list. Not delta patches. \
            If an existing BRANCH point is still valid, it MUST appear in your output.
            2. Cross-Group Synthesis: Each point must integrate information from multiple \
            LEAF groups. A point that only restates one LEAF is NOT a BRANCH insight.
            3. Source Tracking: Every point MUST list ALL contributing sourceItemIds — merge \
            the sourceItemIds from all relevant LEAF points to maintain traceability.
            4. Conflict Resolution: When LEAFs contradict existing BRANCH data, newer LEAF \
            data takes priority. Frame changes historically instead of silently dropping them.
            5. Atomicity: Each point covers exactly ONE coherent cross-group theme.
            6. Brevity: 1-3 sentences per point.
            7. Plain Text Only: No markdown, no bullet lists, no headers in point content.
            8. Language: Output MUST match the input LEAF language exactly.

            # Workflow

            ## Step 1 — Parse & Analyze
            - Read each LEAF insight and understand its key points within its group context.
            - Compare LEAF facts with existing BRANCH points.
            - Identify cross-group themes, contradictions, complementary details, and evolving trends.

            ## Step 2 — Synthesize (Full Rewrite)
            - Start from existing BRANCH points as baseline, then reshape:
              - Still-valid existing points: keep and merge new sourceItemIds from LEAFs.
              - Updated by newer LEAFs: rewrite to reflect temporal evolution.
              - Fresh cross-group patterns: create new synthesized points.
              - Overlapping points: merge into one with combined sourceItemIds.
              - Fully superseded points: drop.
            - Before adding any point, ask: "Does this integrate multiple LEAF groups?" \
            If it only restates one LEAF → not a BRANCH insight, skip or combine.

            ## Step 3 — Validate
            Before outputting, verify each point against this checklist:
            - Does this point integrate information from 2+ LEAF groups?
            - Are sourceItemIds merged from all contributing LEAFs?
            - Are there duplicate or overlapping points? If yes → merge.
            - Is the output valid JSON with no markdown fences?

            # Type Decision Logic

            For each point, determine its type:

            | Ask yourself                                                          | Type      |
            |-----------------------------------------------------------------------|-----------|
            | Does this consolidate facts from multiple LEAF groups into one theme?  | SUMMARY   |
            | Does this infer a cross-group conclusion not stated in any LEAF?       | REASONING |

            Guidelines:
            - SUMMARY = "what is true across groups" (consolidated cross-group facts)
            - REASONING = "what it means when you connect the groups" (cross-group inference)
            - When in doubt, prefer REASONING — cross-group insights that reveal WHY are \
            more valuable at the BRANCH level.\
            """;

    private static final String POINT_OPS_WORKFLOW =
            """
            # Core Principles
            1. Point Operations Only: Output operations against the EXISTING BRANCH point \
            list. Do NOT rewrite the entire list.
            2. Preserve Stable Points: If an existing BRANCH point is still valid and \
            unchanged, emit NO operation for it.
            3. Prefer UPDATE Over DELETE: When a BRANCH point remains directionally valid \
            but needs refinement, use UPDATE. Use DELETE only when a point is truly obsolete \
            or fully absorbed.
            4. Cross-Group Synthesis: Each emitted point must integrate information from \
            multiple LEAF groups. A point that only restates one LEAF is NOT a BRANCH \
            insight.
            5. Source Tracking: Every emitted point MUST list ALL contributing \
            sourceItemIds — merge the sourceItemIds from all relevant LEAF points to \
            maintain traceability back to original memory items.
            6. Conflict Resolution: When LEAFs contradict existing BRANCH data, newer LEAF \
            data takes priority. Frame changes historically (e.g., "previously..., \
            recently shifted to...").
            7. Atomicity: Each point covers exactly ONE coherent cross-group theme.
            8. Brevity: 1-3 sentences per point. Broad enough to capture the cross-group \
            pattern, concise enough to be scannable.
            9. Plain Text Only: No markdown, no bullet lists, no headers in point content.
            10. Language: Output MUST match the input LEAF language exactly.

            # Workflow

            ## Step 1 — Parse & Analyze
            - Read each existing BRANCH point's content, type, confidence, and sourceItemIds.
            - Read each LEAF insight point in its group context.
            - Compare structured LEAF facts with existing BRANCH points.
            - Identify:
              - Cross-group themes: multiple groups pointing to the same broader pattern.
              - Contradictions: groups presenting conflicting information.
              - Complementary details: groups that together paint a richer picture.
              - Evolving trends: newer LEAFs updating older BRANCH knowledge.

            ## Step 2 — Emit Operations
            - Start from existing BRANCH points as baseline, then decide the MINIMAL set \
            of changes:
              - Still-valid existing point: emit nothing.
              - Existing point refined by newer LEAF evidence: emit UPDATE targeting that \
            point id.
              - Fresh cross-group pattern: emit ADD with a new synthesized point.
              - Overlapping existing points: UPDATE the kept point with merged content and \
            sourceItemIds, then DELETE the absorbed point.
              - Fully obsolete point with no remaining value: emit DELETE.
            - Before emitting ADD or UPDATE, ask: "Does this integrate multiple LEAF \
            groups?" If it only restates one LEAF → not a BRANCH insight, skip or combine.
            - If nothing changes after analysis, return `"operations": []`.

            ## Step 3 — Validate
            Before outputting, verify each operation against this checklist:
            - UPDATE and DELETE target an existing BRANCH point id from the input.
            - ADD does not specify a target id.
            - Does every emitted point integrate information from 2+ LEAF groups?
            - Are sourceItemIds merged from all contributing LEAFs?
            - Are there duplicate or overlapping emitted points? If yes → merge first.
            - Is the output valid JSON with no markdown fences?

            # Type Decision Logic

            For each point, determine its type:

            | Ask yourself                                                          | Type      |
            |-----------------------------------------------------------------------|-----------|
            | Does this consolidate facts from multiple LEAF groups into one theme?  | SUMMARY   |
            | Does this infer a cross-group conclusion not stated in any LEAF?       | REASONING |

            Guidelines:
            - SUMMARY = "what is true across groups" (consolidated cross-group facts)
            - REASONING = "what it means when you connect the groups" (cross-group inference)
            - When in doubt, prefer REASONING — cross-group insights that reveal WHY are \
            more valuable at the BRANCH level.\
            """;

    private static final String FULL_REWRITE_OUTPUT =
            """
            # Output Format

            Return ONLY a raw JSON object. No markdown fences. No surrounding text.
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "Cross-group factual synthesis...",
                  "confidence": 0.85,
                  "sourceItemIds": ["42", "43", "45", "50", "51"],
                  "point_reason": "Explain which LEAF groups contribute what and why this is a stable cross-group synthesis."
                },
                {
                  "type": "REASONING",
                  "content": "Cross-group inference...",
                  "confidence": 0.70,
                  "sourceItemIds": ["10", "11", "20", "21"],
                  "point_reason": "Explain the cross-group evidence chain and why this is an inference."
                }
              ]
            }

            Field descriptions:
            - `type`: "SUMMARY" or "REASONING" (see Type Decision Logic above).
            - `content`: Plain text aggregated statement (1-3 sentences).
            - `confidence`: Confidence score in [0, 1].
            - `sourceItemIds`: Array of strings. MUST merge sourceItemIds from all contributing LEAF points.
            - `point_reason`: Reasoning-only field for explaining synthesis logic; it will NOT be stored.\
            """;

    private static final String POINT_OPS_OUTPUT =
            """
            # Output Format

            Return ONLY a raw JSON object. No markdown fences. No surrounding text.
            {
              "operations": [
                {
                  "op": "UPDATE",
                  "targetPointId": "pt_branch_1",
                  "point": {
                    "pointId": "pt_branch_1",
                    "type": "SUMMARY",
                    "content": "Cross-group factual synthesis...",
                    "confidence": 0.85,
                    "sourceItemIds": ["42", "43", "45", "50", "51"]
                  },
                  "reason": "This point remains valid but needs to incorporate new cross-group evidence."
                },
                {
                  "op": "ADD",
                  "point": {
                    "type": "REASONING",
                    "content": "Cross-group inference...",
                    "confidence": 0.70,
                    "sourceItemIds": ["10", "11", "20", "21"]
                  },
                  "reason": "This is a new cross-group conclusion not covered by any existing BRANCH point."
                }
              ]
            }

            Field descriptions:
            - `op`: "ADD", "UPDATE", or "DELETE".
            - `targetPointId`: Required for UPDATE/DELETE. Must equal an existing point's \
            `pointId`.
            - `point`: Required for ADD/UPDATE.
            - `reason`: Short explanation of why this operation is needed.
            - `point.type`: "SUMMARY" or "REASONING" (see Type Decision Logic above).
            - `point.content`: Plain text aggregated statement (1-3 sentences).
            - `point.pointId`: For UPDATE, keep it identical to `targetPointId`. For ADD, omit it.
            - `point.confidence`:
              For SUMMARY:
                - 0.90+: Theme confirmed across 3+ LEAF groups.
                - 0.80-0.89: Clear pattern from 2 LEAF groups.
                - 0.70-0.79: Reasonable consolidation, groups loosely related.
              For REASONING:
                - 0.80+: Strong cross-group inference supported by multiple LEAFs.
                - 0.65-0.79: Reasonable inference connecting 2+ groups.
                - < 0.65: Speculative — include only if highly valuable.
            - `point.sourceItemIds`: Array of strings. MUST merge sourceItemIds from all \
            contributing LEAF points to maintain traceability to original items.
            - Return `"operations": []` when analysis finds no durable BRANCH change.\
            """;

    private static final String FULL_REWRITE_EXAMPLES =
            """
            # Examples

            ## Example 1 — Cross-group synthesis

            Input:
            Existing BRANCH Points: (none)
            LEAF Insights:
            1. [group=career_background] [SUMMARY] User has 8 years of backend engineering experience (sources: [10, 14])
            2. [group=education] [SUMMARY] User graduated from MIT with a CS degree in 2015 (sources: [11])
            3. [group=language_proficiency] [SUMMARY] User is fluent in English and Mandarin (sources: [12])

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User is a senior technical professional with strong education, deep backend experience, and bilingual communication ability.",
                  "confidence": 0.90,
                  "sourceItemIds": ["10", "11", "12", "14"],
                  "point_reason": "Multiple LEAF groups contribute different facets of one higher-level identity theme."
                }
              ]
            }

            ## Example 2 — Incremental rewrite

            Input:
            Existing BRANCH Points:
            - [SUMMARY] User is a fully remote backend engineer who values schedule flexibility (confidence: 0.85, sources: [10, 11])

            LEAF Insights:
            1. [group=work_style] [SUMMARY] User transitioned to hybrid schedule, 3 days in office (sources: [10, 11, 55, 57])
            2. [group=career_growth] [SUMMARY] User was recently promoted to tech lead (sources: [60, 61])

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User transitioned from fully remote to hybrid work while stepping into a tech lead role, suggesting their work style is adapting to broader leadership responsibilities.",
                  "confidence": 0.85,
                  "sourceItemIds": ["10", "11", "55", "57", "60", "61"],
                  "point_reason": "The old BRANCH point remains directionally useful but must be rewritten using the new cross-group evidence."
                }
              ]
            }\
            """;

    private static final String POINT_OPS_EXAMPLES =
            """
            # Examples

            ## Example 1 — Cross-group synthesis

            Input:
            Existing BRANCH Points: (none)
            LEAF Insights:
            G1.P1 [SUMMARY] User has 8 years of backend engineering experience and led a \
            team of 5 at their previous company (sourceItemIds: [10, 14])
            G1.P2 [REASONING] Career trajectory suggests user is moving toward engineering \
            management (sourceItemIds: [10, 14])
            G2.P1 [SUMMARY] User graduated from MIT with a CS degree in 2015 \
            (sourceItemIds: [11])
            G3.P1 [SUMMARY] User is fluent in English and Mandarin \
            (sourceItemIds: [12])
            G4.P1 [SUMMARY] User holds AWS Solutions Architect certification \
            (sourceItemIds: [13])

            Output:
            {
              "operations": [
                {
                  "op": "ADD",
                  "point": {
                    "type": "SUMMARY",
                    "content": "User is a senior backend engineer (8 years experience, MIT \
            CS 2015) with team leadership experience, AWS certification, and bilingual \
            English-Mandarin proficiency — a well-rounded technical profile with both depth \
            and breadth",
                    "confidence": 0.90,
                    "sourceItemIds": ["10", "11", "12", "13", "14"]
                  },
                  "reason": "All 4 LEAF groups contribute different facets of the same \
            professional identity, so this is a new BRANCH-level cross-group synthesis."
                },
                {
                  "op": "ADD",
                  "point": {
                    "type": "REASONING",
                    "content": "The combination of strong technical background (MIT CS, 8 \
            years backend), leadership experience (team of 5), cloud certification (AWS \
            SA), and bilingual ability positions user well for senior technical leadership \
            roles in international or cross-border engineering organizations",
                    "confidence": 0.75,
                    "sourceItemIds": ["10", "11", "12", "13", "14"]
                  },
                  "reason": "No single LEAF concludes 'international tech leadership', but \
            connecting the groups reveals that cross-group inference."
                }
              ]
            }

            Note: 5 LEAF points from 4 groups became 2 BRANCH points. The BRANCH elevates \
            beyond any single group's view.

            ## Example 2 — Incremental update with contradiction

            Input:
            Existing BRANCH Points:
            - pointId: pt_branch_1
              type: SUMMARY
              content: User is a fully remote backend engineer who values schedule flexibility
              confidence: 0.85
              sourceItemIds: ["10", "11"]

            LEAF Insights:
            G1.P1 [SUMMARY] User transitioned to hybrid schedule, 3 days in office. \
            In-office collaboration improved project velocity (sourceItemIds: ["10", \
            "11", "55", "57"])
            G1.P2 [REASONING] User is adapting to hybrid model long-term rather than \
            resisting it (sourceItemIds: ["56", "58"])
            G2.P1 [SUMMARY] User was recently promoted to tech lead, now responsible for \
            architecture decisions (sourceItemIds: ["60", "61"])

            Output:
            {
              "operations": [
                {
                  "op": "UPDATE",
                  "targetPointId": "pt_branch_1",
                  "point": {
                    "pointId": "pt_branch_1",
                    "type": "SUMMARY",
                    "content": "User transitioned from fully remote to hybrid work (3 days \
            in office) coinciding with a promotion to tech lead. The shift to in-office \
            presence aligns with the increased need for in-person collaboration in the \
            leadership role",
                    "confidence": 0.85,
                    "sourceItemIds": ["10", "11", "55", "57", "60", "61"]
                  },
                  "reason": "P1 remains directionally valid but needs to reflect the new \
            hybrid-work and leadership evidence."
                },
                {
                  "op": "ADD",
                  "point": {
                    "type": "REASONING",
                    "content": "The willingness to adapt to hybrid work despite preferring \
            remote, combined with taking on a leadership role, suggests user prioritizes \
            career advancement over personal comfort preferences",
                    "confidence": 0.70,
                    "sourceItemIds": ["10", "11", "56", "58", "60", "61"]
                  },
                  "reason": "Connecting the work-style and career-growth groups reveals a \
            new values-level inference not represented by P1."
                }
              ]
            }

            Note: The existing BRANCH point about "fully remote" was superseded — the new \
            points reflect the temporal evolution rather than silently dropping the history.\
            """;

    public static PromptTemplate build(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {
        return build(PromptRegistry.EMPTY, insightType, existingPoints, leafInsights, targetTokens);
    }

    public static PromptTemplate buildDefault() {
        return fullRewriteBuilder().build();
    }

    public static PromptTemplate buildPreview() {
        var insightType = DefaultInsightTypes.identity();
        return fullRewriteBuilder()
                .variable("insight_type_name", insightType.name())
                .variable(
                        "insight_type_description",
                        PromptBuilderSupport.descriptionOrName(insightType))
                .build();
    }

    public static PromptTemplate buildPointOps(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {
        return buildPointOps(
                PromptRegistry.EMPTY, insightType, existingPoints, leafInsights, targetTokens);
    }

    public static PromptTemplate buildPointOpsDefault() {
        return pointOpsBuilder().build();
    }

    public static PromptTemplate buildPointOpsPreview() {
        var insightType = DefaultInsightTypes.identity();
        return pointOpsBuilder()
                .variable("insight_type_name", insightType.name())
                .variable(
                        "insight_type_description",
                        PromptBuilderSupport.descriptionOrName(insightType))
                .build();
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {
        var userPromptContent =
                buildFullRewriteUserPrompt(insightType, existingPoints, leafInsights, targetTokens);
        var builder =
                registry.hasOverride(PromptType.BRANCH_AGGREGATION)
                        ? PromptTemplate.builder("branch-aggregation")
                                .section(
                                        "system",
                                        registry.getOverride(PromptType.BRANCH_AGGREGATION))
                        : fullRewriteBuilder();

        return builder.variable("insight_type_name", insightType.name())
                .variable(
                        "insight_type_description",
                        PromptBuilderSupport.descriptionOrName(insightType))
                .userPrompt(userPromptContent)
                .build();
    }

    public static PromptTemplate buildPointOps(
            PromptRegistry registry,
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {
        var userPromptContent =
                buildPointOpsUserPrompt(insightType, existingPoints, leafInsights, targetTokens);
        var builder =
                registry.hasOverride(PromptType.BRANCH_AGGREGATION_POINT_OPS)
                        ? PromptTemplate.builder("branch-aggregation-point-ops")
                                .section(
                                        "system",
                                        registry.getOverride(
                                                PromptType.BRANCH_AGGREGATION_POINT_OPS))
                        : pointOpsBuilder();

        return builder.variable("insight_type_name", insightType.name())
                .variable(
                        "insight_type_description",
                        PromptBuilderSupport.descriptionOrName(insightType))
                .userPrompt(userPromptContent)
                .build();
    }

    private static PromptTemplate.Builder fullRewriteBuilder() {
        return PromptBuilderSupport.coreSections(
                "branch-aggregation",
                SYSTEM_OBJECTIVE,
                SYSTEM_CONTEXT,
                FULL_REWRITE_WORKFLOW,
                FULL_REWRITE_OUTPUT,
                FULL_REWRITE_EXAMPLES);
    }

    private static PromptTemplate.Builder pointOpsBuilder() {
        return PromptBuilderSupport.coreSections(
                "branch-aggregation-point-ops",
                SYSTEM_OBJECTIVE,
                SYSTEM_CONTEXT,
                POINT_OPS_WORKFLOW,
                POINT_OPS_OUTPUT,
                POINT_OPS_EXAMPLES);
    }

    private static String buildFullRewriteUserPrompt(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {

        var sb = new StringBuilder();
        sb.append("# Insight Dimension\n");
        sb.append("Name: ").append(insightType.name()).append("\n");
        if (insightType.description() != null) {
            sb.append("Description: ").append(insightType.description()).append("\n");
        }
        sb.append("Token budget: ").append(targetTokens).append("\n");

        if (existingPoints != null && !existingPoints.isEmpty()) {
            sb.append("\n# Existing BRANCH Points\n");
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

        sb.append("\n# LEAF Insights\n");
        for (int i = 0; i < leafInsights.size(); i++) {
            var leaf = leafInsights.get(i);
            sb.append(i + 1)
                    .append(". [group=")
                    .append(leaf.group())
                    .append("] ")
                    .append(leaf.pointsContent())
                    .append("\n");
        }

        return sb.toString();
    }

    private static String buildPointOpsUserPrompt(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {

        var sb = new StringBuilder();
        sb.append("# Insight Dimension\n");
        sb.append("Name: ").append(insightType.name()).append("\n");
        if (insightType.description() != null) {
            sb.append("Description: ").append(insightType.description()).append("\n");
        }
        sb.append("Token budget: ").append(targetTokens).append("\n");

        if (existingPoints != null && !existingPoints.isEmpty()) {
            sb.append("\n# Existing BRANCH Points\n");
            for (int i = 0; i < existingPoints.size(); i++) {
                var point = existingPoints.get(i);
                sb.append("- pointId: ")
                        .append(point.pointId())
                        .append("\n  type: ")
                        .append(point.type())
                        .append("\n  content: ")
                        .append(point.content())
                        .append("\n  confidence: ")
                        .append(point.confidence())
                        .append("\n  sourceItemIds: ")
                        .append(point.sourceItemIds())
                        .append("\n");
            }
        }

        sb.append("\n# LEAF Insights\n");
        for (int i = 0; i < leafInsights.size(); i++) {
            var leaf = leafInsights.get(i);
            var groupIndex = i + 1;
            sb.append("G").append(groupIndex).append(". group=").append(leaf.group()).append("\n");
            var points = leaf.points() != null ? leaf.points() : List.<InsightPoint>of();
            for (int j = 0; j < points.size(); j++) {
                var point = points.get(j);
                sb.append("G")
                        .append(groupIndex)
                        .append(".P")
                        .append(j + 1)
                        .append(" [")
                        .append(point.type())
                        .append("] ")
                        .append(point.content())
                        .append("\n    confidence: ")
                        .append(point.confidence())
                        .append("\n    sourceItemIds: ")
                        .append(point.sourceItemIds())
                        .append("\n");
            }
        }

        return sb.toString();
    }
}
