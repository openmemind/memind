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

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
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

    // ===== Section name constants =====

    public static final String NAME_OBJECTIVE = "objective";
    public static final String NAME_CONTEXT = "context";
    public static final String NAME_WORKFLOW = "workflow";
    public static final String NAME_OUTPUT = "output";
    public static final String NAME_EXAMPLES = "examples";

    // ===== OBJECTIVE =====

    private static final String OBJECTIVE =
            """
            You are a BRANCH aggregation engine. Your task is to synthesize all LEAF-level \
            insight points under a single insight dimension into a comprehensive BRANCH-level \
            summary. Each LEAF represents a different semantic group that has already been \
            individually analyzed — your job is to find cross-group patterns, resolve \
            contradictions, and produce a unified higher-level view.\
            """;

    // ===== CONTEXT =====

    private static final String CONTEXT =
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

    // ===== WORKFLOW (includes Core Principles, Steps, Type Decision Logic) =====

    private static final String WORKFLOW =
            """
            # Core Principles
            1. Full Replacement: Output the COMPLETE current-state list. Not delta patches. \
            If an existing BRANCH point is still valid, it MUST appear in your output.
            2. Cross-Group Synthesis: Each point must integrate information from multiple \
            LEAF groups. A point that only restates one LEAF is NOT a BRANCH insight.
            3. Source Tracking: Every point MUST list ALL contributing sourceItemIds — \
            merge the sourceItemIds from all relevant LEAF points to maintain the \
            traceability chain back to original memory items.
            4. Conflict Resolution: When LEAFs contradict existing BRANCH data, newer LEAF \
            data takes priority. Frame changes historically (e.g., "previously..., \
            recently shifted to...").
            5. Atomicity: Each point covers exactly ONE coherent cross-group theme.
            6. Brevity: 1-3 sentences per point. Broad enough to capture the cross-group \
            pattern, concise enough to be scannable.
            7. Plain Text Only: No markdown, no bullet lists, no headers in point content.
            8. Language: Output MUST match the input LEAF language exactly.

            # Workflow

            ## Step 1 — Parse & Analyze
            - Read each LEAF insight and understand its key points within its group context.
            - Compare LEAF facts with existing BRANCH points.
            - Identify:
              - Cross-group themes: multiple groups pointing to the same broader pattern.
              - Contradictions: groups presenting conflicting information.
              - Complementary details: groups that together paint a richer picture.
              - Evolving trends: newer LEAFs updating older BRANCH knowledge.

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

    // ===== OUTPUT FORMAT =====

    private static final String OUTPUT =
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
                  "point_reason": "LEAF group A shows X, LEAF group B shows Y. Together they reveal Z. Type=SUMMARY because this consolidates cross-group facts."
                },
                {
                  "type": "REASONING",
                  "content": "Cross-group inference...",
                  "confidence": 0.70,
                  "sourceItemIds": ["10", "11", "20", "21"],
                  "point_reason": "Group A's X combined with Group B's Y suggests Z, which neither group states alone. Type=REASONING because this is a cross-group inference."
                }
              ]
            }

            Field descriptions:
            - `type`: "SUMMARY" or "REASONING" (see Type Decision Logic above).
            - `content`: Plain text aggregated statement (1-3 sentences).
            - `confidence`:
              For SUMMARY:
                - 0.90+: Theme confirmed across 3+ LEAF groups.
                - 0.80-0.89: Clear pattern from 2 LEAF groups.
                - 0.70-0.79: Reasonable consolidation, groups loosely related.
              For REASONING:
                - 0.80+: Strong cross-group inference supported by multiple LEAFs.
                - 0.65-0.79: Reasonable inference connecting 2+ groups.
                - < 0.65: Speculative — include only if highly valuable.
            - `sourceItemIds`: Array of strings. MUST merge sourceItemIds from all \
            contributing LEAF points to maintain traceability to original items.
            - `point_reason`: CRITICAL. Explain: (1) which LEAF groups contribute what, \
            (2) what cross-group pattern emerges, (3) why this type was chosen. \
            This field is for reasoning only and will NOT be stored.\
            """;

    // ===== EXAMPLES =====

    private static final String EXAMPLES =
            """
            # Examples

            ## Example 1 — Cross-group synthesis

            Input:
            Existing BRANCH Points: (none)
            LEAF Insights:
            1. [group=career_background] [SUMMARY] User has 8 years of backend engineering \
            experience and led a team of 5 at their previous company (sources: [10, 14])
            2. [group=career_background] [REASONING] Career trajectory suggests user is \
            moving toward engineering management (sources: [10, 14])
            3. [group=education] [SUMMARY] User graduated from MIT with a CS degree in 2015 \
            (sources: [11])
            4. [group=language_proficiency] [SUMMARY] User is fluent in English and Mandarin \
            (sources: [12])
            5. [group=certifications] [SUMMARY] User holds AWS Solutions Architect \
            certification (sources: [13])

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User is a senior backend engineer (8 years experience, MIT CS \
            2015) with team leadership experience, AWS certification, and bilingual \
            English-Mandarin proficiency — a well-rounded technical profile with both \
            depth and breadth",
                  "confidence": 0.90,
                  "sourceItemIds": ["10", "11", "12", "13", "14"],
                  "point_reason": "All 4 LEAF groups (career, education, language, \
            certifications) contribute different facets of the same professional identity. \
            Together they reveal a comprehensive technical profile that no single group \
            captures. Type=SUMMARY because this consolidates cross-group facts."
                },
                {
                  "type": "REASONING",
                  "content": "The combination of strong technical background (MIT CS, 8 years \
            backend), leadership experience (team of 5), cloud certification (AWS SA), and \
            bilingual ability positions user well for senior technical leadership roles in \
            international or cross-border engineering organizations",
                  "confidence": 0.75,
                  "sourceItemIds": ["10", "11", "12", "13", "14"],
                  "point_reason": "Career group shows leadership trajectory, education shows \
            strong foundation, certifications show cloud expertise, language shows \
            international capability. No single LEAF concludes 'international tech \
            leadership' but the combination strongly suggests it. Type=REASONING because \
            this is a cross-group career inference."
                }
              ]
            }

            Note: 5 LEAF points from 4 groups became 2 BRANCH points. The BRANCH elevates \
            beyond any single group's view.

            ## Bad Example 1: Copy-paste from LEAFs (WRONG)

            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User has 8 years of backend engineering experience and led a \
            team of 5",
                  "sourceItemIds": ["10", "14"]
                },
                {
                  "type": "SUMMARY",
                  "content": "User graduated from MIT with a CS degree in 2015",
                  "sourceItemIds": ["11"]
                },
                {
                  "type": "SUMMARY",
                  "content": "User is fluent in English and Mandarin",
                  "sourceItemIds": ["12"]
                }
              ]
            }

            -> Wrong: Each point restates exactly one LEAF. No cross-group synthesis \
            happened. These are LEAF-level facts, not BRANCH insights. Combine them into \
            broader themes.

            ## Bad Example 2: Lost cross-group connections (WRONG)

            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User has a strong technical background with MIT education and \
            8 years of experience",
                  "sourceItemIds": ["10", "11"]
                }
              ]
            }

            -> Wrong: Only combined 2 of 4 groups. The language proficiency and \
            certification groups are completely ignored. A BRANCH must consider ALL \
            available LEAFs.

            ## Example 2 — Incremental update with contradiction

            Input:
            Existing BRANCH Points:
            - [SUMMARY] User is a fully remote backend engineer who values schedule \
            flexibility (confidence: 0.85, sources: [10, 11])

            LEAF Insights:
            1. [group=work_style] [SUMMARY] User transitioned to hybrid schedule, 3 days \
            in office. In-office collaboration improved project velocity (sources: \
            [10, 11, 55, 57])
            2. [group=work_style] [REASONING] User is adapting to hybrid model long-term \
            rather than resisting it (sources: [56, 58])
            3. [group=career_growth] [SUMMARY] User was recently promoted to tech lead, \
            now responsible for architecture decisions (sources: [60, 61])

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User transitioned from fully remote to hybrid work (3 days in \
            office) coinciding with a promotion to tech lead. The shift to in-office \
            presence aligns with the increased need for in-person collaboration in the \
            leadership role",
                  "confidence": 0.85,
                  "sourceItemIds": ["10", "11", "55", "57", "60", "61"],
                  "point_reason": "Work_style LEAF shows hybrid transition, career_growth \
            LEAF shows promotion to tech lead. The timing correlation between these two \
            groups suggests the work style change is connected to the role change — \
            neither LEAF makes this connection. Type=SUMMARY because the correlation is \
            factual (both happened together)."
                },
                {
                  "type": "REASONING",
                  "content": "The willingness to adapt to hybrid work despite preferring \
            remote, combined with taking on a leadership role, suggests user prioritizes \
            career advancement over personal comfort preferences",
                  "confidence": 0.70,
                  "sourceItemIds": ["10", "11", "56", "58", "60", "61"],
                  "point_reason": "Work_style LEAF shows adaptation despite discomfort \
            (exhausting commute), career_growth LEAF shows ambition (promotion, \
            architecture decisions). Connecting these groups reveals a values hierarchy \
            not stated in either. Type=REASONING because 'prioritizes career over \
            comfort' is an inference."
                }
              ]
            }

            Note: The existing BRANCH point about "fully remote" was superseded — the new \
            points reflect the temporal evolution rather than silently dropping the history.\
            """;

    // ==================== Public API ====================

    public static PromptTemplate build(
            MemoryInsightType insightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> leafInsights,
            int targetTokens) {

        var description =
                insightType.description() != null ? insightType.description() : insightType.name();

        var userPromptContent =
                buildUserPrompt(insightType, existingPoints, leafInsights, targetTokens);

        return PromptTemplate.builder("branch-aggregation")
                .section(NAME_OBJECTIVE, OBJECTIVE)
                .section(NAME_CONTEXT, CONTEXT)
                .section(NAME_WORKFLOW, WORKFLOW)
                .section(NAME_OUTPUT, OUTPUT)
                .section(NAME_EXAMPLES, EXAMPLES)
                .variable("insight_type_name", insightType.name())
                .variable("insight_type_description", description)
                .userPrompt(userPromptContent)
                .build();
    }

    // ==================== Internal Helpers ====================

    private static String buildUserPrompt(
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
            for (var p : existingPoints) {
                sb.append("- [")
                        .append(p.type())
                        .append("] ")
                        .append(p.content())
                        .append(" (confidence: ")
                        .append(p.confidence())
                        .append(", sources: ")
                        .append(p.sourceItemIds())
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
}
