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
 * ROOT synthesis prompt builder with 4-dimension analysis framework.
 *
 * <p>Receives multiple BRANCH summaries (each representing a different insight dimension), and
 * generates a ROOT-level deep synthesis using convergence / tension / trajectory / causation
 * dimensions.
 */
public final class RootSynthesisPrompts {

    private RootSynthesisPrompts() {}

    static final String OBJECTIVE =
            """
            You are a cross-dimensional portrait engine. Your task is to analyze all \
            BRANCH-level insight summaries and produce a ROOT-level deep synthesis that \
            reveals cross-dimensional patterns invisible at the BRANCH level.

            You use four analytical dimensions:
            - convergence: Where 2+ BRANCHes independently point to the same conclusion
            - tension: Where BRANCHes reveal contradictions, trade-offs, or inconsistencies
            - trajectory: Cross-BRANCH temporal trends indicating life direction or phase shifts
            - causation: Cross-BRANCH cause→effect chains connecting different life domains\
            """;

    // ===== CONTEXT =====

    static final String CONTEXT =
            """
            # Context

            You are generating the "{{root_type_name}}" ROOT node in the insight tree.
            ROOT type description: {{root_description}}

            Your position in the insight tree:
            - Upstream (your input): BRANCH nodes. Each BRANCH is a cross-group synthesis \
            within a single insight dimension. They are already higher-level abstractions, \
            not raw memory items.
            - Downstream (your output consumer): Applications that need a holistic \
            understanding of who the user is — used for personalization, recommendations, \
            and context-aware responses.

            The 8 BRANCH dimensions you may receive:
            - identity: Who the user IS — stable traits, professional background, skills
            - preferences: What the user LIKES/DISLIKES/VALUES — subjective opinions
            - relationships: The user's social network — family, colleagues, dynamics
            - experiences: What is HAPPENING — time-bound projects, goals, situations
            - behavior: What the user DOES REPEATEDLY — habits, routines, patterns
            - directives: Durable interaction rules, boundaries, and collaboration constraints
            - playbooks: Reusable workflows and task-handling methods
            - resolutions: Resolved problem knowledge, fixes, and durable conclusions

            Your job is to read ACROSS all these dimensions and find patterns that no \
            single BRANCH reveals alone. Each point must draw evidence from 2+ BRANCHes.

            Sibling ROOT — "interaction" — handles prescriptive directives for the AI agent. \
            You handle the descriptive side: a deep portrait of who the user is.\
            """;

    // ===== WORKFLOW (includes Core Principles, Steps, Dimension Decision Logic) =====

    static final String WORKFLOW =
            """
            # Core Principles
            1. Cross-Dimensional Only: Every point MUST reference evidence from 2+ BRANCH \
            dimensions. Single-BRANCH observations belong at the BRANCH level, not ROOT.
            2. Full Replacement: Output the COMPLETE current-state list. Not delta patches. \
            If an existing ROOT point is still valid, it MUST appear in your output.
            3. Evidence-Based: The point_reason field must cite which BRANCH dimensions \
            support the conclusion.
            4. Hedged Language: Use "suggests", "indicates", "likely" unless evidence is \
            overwhelming. ROOT-level inferences are inherently uncertain.
            5. Atomicity: Each point covers exactly ONE cross-dimensional thesis.
            6. Brevity: 1-3 sentences per point. Substantive but scannable.
            7. Plain Text Only: No markdown formatting in point content.
            8. Language: Output MUST match the input BRANCH language exactly.

            # Workflow

            ## Step 1 — Parse & Map
            - Read each BRANCH summary and note key facts, recent changes, and implied signals.
            - Build a mental map of cross-BRANCH connections.

            ## Step 2 — Four-Dimension Scan
            For each analytical dimension, scan for patterns:

            Convergence:
            - Which conclusions do 2+ BRANCHes independently support from different angles?
            - Example: identity shows "prefers async communication" + behavior shows \
            "responds to messages in batches" → convergence on async-first work style.

            Tension:
            - Where do BRANCHes reveal contradictions or trade-offs?
            - Example: preferences says "wants to exercise daily" but experiences shows \
            "gym visits declining" → tension between aspiration and behavior.
            - Name the conflicting BRANCH dimensions explicitly.

            Trajectory:
            - What temporal trends span multiple BRANCHes?
            - Must reference time-related signals (frequency changes, new/dropped activities, \
            phase transitions).

            Causation:
            - What cause→effect chains connect different life domains?
            - Example: identity "promoted to manager" → relationships "less time with family" \
            → behavior "increased stress" → experiences "started meditation".
            - Name both cause and effect BRANCH dimensions explicitly.

            ## Step 3 — Validate
            Before outputting, verify each point against this checklist:
            - Does this point draw evidence from 2+ BRANCH dimensions?
            - Is the dimension label correct per the decision logic?
            - Are there duplicate or overlapping points? If yes → merge.
            - Is the output valid JSON with no markdown fences?

            # Dimension Decision Logic

            For each point, determine its dimension:

            | Ask yourself                                                              | Dimension   |
            |---------------------------------------------------------------------------|-------------|
            | Do 2+ BRANCHes independently confirm the same conclusion?                 | convergence |
            | Do BRANCHes reveal a contradiction, trade-off, or inconsistency?           | tension     |
            | Is there a temporal trend visible only when connecting multiple BRANCHes?  | trajectory  |
            | Can I trace a cause→effect chain across BRANCH dimensions?                | causation   |

            Confidence guidance per dimension:
            - convergence: 0.75+ (strong multi-source agreement)
            - tension: 0.60-0.80 (contradictions are real but interpretation varies)
            - trajectory: 0.65-0.80 (trends need sufficient time signals)
            - causation: 0.55-0.75 (causal inference is inherently uncertain)\
            """;

    // ===== OUTPUT FORMAT =====

    static final String OUTPUT =
            """
            # Output Format

            Return ONLY a raw JSON object. No markdown fences. No surrounding text.
            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "Cross-dimensional thesis supported by multiple BRANCHes...",
                  "sourcePointRefs": [
                    { "insightId": 11, "pointId": "pt_branch_1" },
                    { "insightId": 12, "pointId": "pt_branch_2" }
                  ],
                  "metadata": {
                    "dimension": "convergence"
                  },
                  "point_reason": "identity BRANCH shows X, behavior BRANCH shows Y. These independently confirm Z. Dimension=convergence because two BRANCHes point to the same conclusion from different angles."
                }
              ]
            }

            Field descriptions:
            - `type`: "SUMMARY" for cross-dimensional factual observations, "REASONING" for \
            emergent inferences. Most ROOT points will be REASONING.
            - `content`: The synthesis text (1-3 sentences). Same language as BRANCHes.
            - `sourcePointRefs`: Array of `{ "insightId": number, "pointId": string }`. \
            MUST reference every contributing BRANCH point directly.
            - `metadata.dimension` (REQUIRED): One of "convergence", "tension", "trajectory", \
            or "causation".
            - `point_reason`: CRITICAL. Explain: (1) which BRANCH dimensions provide what \
            evidence, (2) what cross-dimensional pattern emerges, (3) why this dimension \
            was chosen. This field is for reasoning only and will NOT be stored.\
            """;

    // ===== EXAMPLES =====

    static final String EXAMPLES =
            """
            # Examples

            ## Example 1 — English — Multi-dimension portrait

            Input:
            BRANCH Summaries:
            1. [type=identity] Senior engineer, 55-60h/week. Recently promoted to tech lead. \
            Prefers async communication, blocks calendar for deep work.
            2. [type=experiences] Gym visits dropped from 4x to 1x/week over 2 months. Started \
            a weekend woodworking hobby 3 months ago.
            3. [type=relationships] Partner mentioned wanting more weekend time together. \
            Close friend moved to another city last month.
            4. [type=preferences] Enjoys detailed technical discussions. Dislikes small talk. \
            Recently started reading philosophy books.

            Output:
            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "Identity and experiences independently confirm a shift toward \
            solitary, deep-focus pursuits: async communication preference and calendar blocking \
            at work mirrors the adoption of woodworking (a solo, concentration-intensive hobby) \
            outside work. This convergence suggests a personality-level need for uninterrupted \
            cognitive engagement rather than a temporary phase.",
                  "sourcePointRefs": [
                    { "insightId": 1, "pointId": "pt_branch_identity" },
                    { "insightId": 2, "pointId": "pt_branch_experiences" },
                    { "insightId": 4, "pointId": "pt_branch_preferences" }
                  ],
                  "metadata": { "dimension": "convergence" },
                  "point_reason": "identity BRANCH: async preference + deep work blocking. \
            experiences BRANCH: adopted solo woodworking hobby. preferences BRANCH: enjoys \
            detailed discussions, dislikes small talk. Three dimensions independently point \
            to deep-focus personality. Dimension=convergence because multiple BRANCHes confirm \
            the same conclusion."
                },
                {
                  "type": "REASONING",
                  "content": "A tension exists between the user's increasing withdrawal into \
            solo activities (woodworking replacing gym, async over sync communication) and the \
            partner's explicit request for more shared weekend time.",
                  "sourcePointRefs": [
                    { "insightId": 1, "pointId": "pt_branch_identity" },
                    { "insightId": 2, "pointId": "pt_branch_experiences" },
                    { "insightId": 3, "pointId": "pt_branch_relationships" }
                  ],
                  "metadata": { "dimension": "tension" },
                  "point_reason": "experiences BRANCH: solo hobbies increasing. relationships \
            BRANCH: partner wants more shared time. These two dimensions directly contradict \
            — user is moving toward solitude while partner needs togetherness. \
            Dimension=tension because BRANCHes reveal a trade-off."
                },
                {
                  "type": "REASONING",
                  "content": "Over the past 2-3 months, a trajectory toward introversion is \
            visible: declining gym visits replaced by solo woodworking, friend relocation \
            reducing social contact, and a new interest in philosophy books.",
                  "sourcePointRefs": [
                    { "insightId": 2, "pointId": "pt_branch_experiences" },
                    { "insightId": 3, "pointId": "pt_branch_relationships" },
                    { "insightId": 4, "pointId": "pt_branch_preferences" }
                  ],
                  "metadata": { "dimension": "trajectory" },
                  "point_reason": "experiences BRANCH: gym decline + woodworking adoption \
            (2-3 month window). relationships BRANCH: friend moved away. preferences BRANCH: \
            new philosophy interest. All time-bounded changes pointing in the same direction. \
            Dimension=trajectory because this is a temporal trend across BRANCHes."
                },
                {
                  "type": "REASONING",
                  "content": "The tech lead promotion (identity) likely caused the exercise \
            decline (experiences): increased meeting load consumed gym time, which may be \
            contributing to the partner's dissatisfaction (relationships).",
                  "sourcePointRefs": [
                    { "insightId": 1, "pointId": "pt_branch_identity" },
                    { "insightId": 2, "pointId": "pt_branch_experiences" },
                    { "insightId": 3, "pointId": "pt_branch_relationships" }
                  ],
                  "metadata": { "dimension": "causation" },
                  "point_reason": "identity BRANCH: promotion to tech lead, 55-60h/week. \
            experiences BRANCH: gym dropped from 4x to 1x. relationships BRANCH: partner \
            dissatisfied. Causal chain: promotion → more hours → less gym → less weekend \
            availability → partner tension. Dimension=causation because this traces a \
            cause→effect chain across 3 dimensions."
                }
              ]
            }

            ## Bad Example 1: Single-BRANCH observation (WRONG)

            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "The user recently started woodworking as a weekend hobby.",
                  "metadata": { "dimension": "convergence" }
                }
              ]
            }

            -> Wrong: This restates a single experiences BRANCH fact. No cross-dimensional \
            analysis. ROOT points must draw from 2+ BRANCHes. This belongs at the BRANCH level.

            ## Bad Example 2: Wrong dimension label (WRONG)

            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "The user's gym visits declined while woodworking increased, \
            suggesting a shift in leisure priorities.",
                  "metadata": { "dimension": "causation" }
                }
              ]
            }

            -> Wrong: This describes a temporal trend (trajectory), not a cause→effect chain. \
            Causation requires naming a cause BRANCH and an effect BRANCH with a clear \
            mechanism. "Gym down, woodworking up" is a trend, not a causal explanation.

            ## Example 2 — Chinese

            Input:
            BRANCH Summaries:
            1. [type=identity] 28岁UI设计师，公司刚推行混合办公，每周到岗2天。正在学习3D建模。
            2. [type=preferences] 经常关注杭州的咖啡店和远程办公攻略。喜欢极简设计风格。
            3. [type=relationships] 男朋友在杭州工作，异地半年。每周视频通话4-5次。
            4. [type=experiences] 想在2年内买房。最近开始研究杭州的楼盘。

            Output:
            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "identity、preferences和experiences三个维度独立指向搬去杭州的意向：\
            混合办公降低了通勤约束（identity），关注杭州生活内容说明心理准备已经开始（preferences），\
            研究杭州楼盘将买房计划具体化到了杭州（experiences）。",
                  "sourcePointRefs": [
                    { "insightId": 1, "pointId": "pt_branch_identity" },
                    { "insightId": 2, "pointId": "pt_branch_preferences" },
                    { "insightId": 4, "pointId": "pt_branch_experiences" }
                  ],
                  "metadata": { "dimension": "convergence" },
                  "point_reason": "identity BRANCH: 混合办公减少到岗要求。preferences BRANCH: \
            关注杭州咖啡店和远程办公。experiences BRANCH: 研究杭州楼盘。三个维度从不同角度指向\
            同一结论——计划搬去杭州。Dimension=convergence 因为多维度独立确认同一结论。"
                },
                {
                  "type": "REASONING",
                  "content": "异地恋（relationships）很可能是买房目标聚焦杭州的根本原因：每周4-5次\
            视频通话说明情感需求强烈，直接驱动了从模糊的买房意向到具体研究杭州楼盘（experiences）的\
            转变，而混合办公（identity）消除了职业上的搬迁障碍。",
                  "sourcePointRefs": [
                    { "insightId": 1, "pointId": "pt_branch_identity" },
                    { "insightId": 3, "pointId": "pt_branch_relationships" },
                    { "insightId": 4, "pointId": "pt_branch_experiences" }
                  ],
                  "metadata": { "dimension": "causation" },
                  "point_reason": "relationships BRANCH: 异地恋，高频视频通话。experiences BRANCH: \
            买房目标聚焦杭州。identity BRANCH: 混合办公降低搬迁成本。因果链：异地恋情感需求 → \
            买房目标锁定杭州 → 混合办公消除障碍。Dimension=causation 因为这是跨维度因果链。"
                }
              ]
            }\
            """;

    // ==================== Public API ====================

    public static PromptTemplate build(
            MemoryInsightType rootInsightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> branchInsights,
            int targetTokens) {
        return build(
                PromptRegistry.EMPTY,
                rootInsightType,
                existingPoints,
                branchInsights,
                targetTokens);
    }

    public static PromptTemplate buildDefault() {
        return defaultBuilder().build();
    }

    public static PromptTemplate buildPreview() {
        var rootInsightType = DefaultInsightTypes.profile();
        return defaultBuilder()
                .variable("root_type_name", rootInsightType.name())
                .variable(
                        "root_description", PromptBuilderSupport.descriptionOrName(rootInsightType))
                .build();
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            MemoryInsightType rootInsightType,
            List<InsightPoint> existingPoints,
            List<MemoryInsight> branchInsights,
            int targetTokens) {

        var userPromptContent = buildUserPrompt(existingPoints, branchInsights, targetTokens);
        var builder =
                registry.hasOverride(PromptType.ROOT_SYNTHESIS)
                        ? PromptTemplate.builder("root-synthesis")
                                .section("system", registry.getOverride(PromptType.ROOT_SYNTHESIS))
                        : defaultBuilder();

        return builder.variable("root_type_name", rootInsightType.name())
                .variable(
                        "root_description", PromptBuilderSupport.descriptionOrName(rootInsightType))
                .userPrompt(userPromptContent)
                .build();
    }

    private static PromptTemplate.Builder defaultBuilder() {
        return PromptBuilderSupport.coreSections(
                "root-synthesis", OBJECTIVE, CONTEXT, WORKFLOW, OUTPUT, EXAMPLES);
    }

    private static String buildUserPrompt(
            List<InsightPoint> existingPoints,
            List<MemoryInsight> branchInsights,
            int targetTokens) {

        var sb = new StringBuilder();
        sb.append("# Token Budget\n");
        sb.append(targetTokens).append("\n");

        if (existingPoints != null && !existingPoints.isEmpty()) {
            sb.append("\n# Existing ROOT Points\n");
            for (var point : existingPoints) {
                sb.append("- pointId: ")
                        .append(point.pointId())
                        .append("\n  type: ")
                        .append(point.type())
                        .append("\n  content: ")
                        .append(point.content())
                        .append("\n  sourcePointRefs: ")
                        .append(point.sourcePointRefs())
                        .append("\n");
            }
        }

        sb.append("\n# BRANCH Points\n");
        for (int i = 0; i < branchInsights.size(); i++) {
            var branch = branchInsights.get(i);
            var points = branch.points() != null ? branch.points() : List.<InsightPoint>of();
            for (var point : points) {
                sb.append(i + 1)
                        .append(". insightId=")
                        .append(branch.id())
                        .append(" pointId=")
                        .append(point.pointId())
                        .append(" [type=")
                        .append(branch.type())
                        .append("] [pointType=")
                        .append(point.type())
                        .append("] ")
                        .append(point.content())
                        .append("\n   sourcePointRefs: ")
                        .append(point.sourcePointRefs())
                        .append("\n   metadata: ")
                        .append(point.metadata())
                        .append("\n");
            }
        }

        return sb.toString();
    }
}
