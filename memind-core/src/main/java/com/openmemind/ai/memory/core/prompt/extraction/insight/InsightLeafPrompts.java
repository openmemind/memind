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
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.List;
import java.util.Map;

/**
 * InsightPoint generation prompt builder (LEAF level).
 *
 * <p>Assembles section constants in fixed order, replaces placeholders, and appends the Input
 * block. User input (existingPoints, newItems) is injected via string concatenation (not
 * placeholder replacement) to prevent prompt injection.
 *
 * <p>Supports section overrides via {@link MemoryInsightType#summaryPrompt()}.
 */
public final class InsightLeafPrompts {

    private InsightLeafPrompts() {}

    // ===== Public section name constants (for use by external callers) =====

    public static final String NAME_OBJECTIVE = "objective";
    public static final String NAME_CONTEXT = "context";
    public static final String NAME_WORKFLOW = "workflow";
    public static final String NAME_OUTPUT = "output";
    public static final String NAME_EXAMPLES = "examples";

    // ===== OBJECTIVE =====

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

    private static final String DEFAULT_WORKFLOW =
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
            - Read ALL new items together. Identify:
              - Recurring themes: multiple items about the same topic or domain.
              - Temporal patterns: changes, progressions, or trends over time.
              - Causal links: item A explains or causes item B.
              - Contradictions: new info conflicting with existing knowledge.
            - Filter noise: ignore trivial one-time events or exact duplicates.

            ## Step 2 — Synthesize (Full Rewrite)
            - Start from existing points as baseline, then reshape:
              - Still-valid existing points: keep and merge new supporting sourceItemIds.
              - Strengthened by new items: rewrite with updated understanding.
              - Fresh patterns from new items: create new synthesized points.
              - Contradicted by new items: rewrite to reflect temporal evolution, \
            do NOT silently drop historical context.
              - Overlapping points: merge into one with combined sourceItemIds.
              - Fully superseded points with no historical value: drop.
            - Before adding any point, ask: "Could a reader learn this from just ONE item?" \
            If yes → combine it with related items or skip it.

            ## Step 3 — Validate
            Before outputting, verify each point against this checklist:
            - Could a reader learn this from just ONE item? If yes → not an insight, fix it.
            - Does every SUMMARY reference 2+ sourceItemIds?
            - Does every REASONING synthesize across multiple evidence sources?
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

    // ===== OUTPUT FORMAT =====

    public static final String OUTPUT =
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
                  "point_reason": "Items 42, 43, 45 all describe aspects of X. Combined they reveal Y. Type=SUMMARY because this consolidates facts, not infers."
                },
                {
                  "type": "REASONING",
                  "content": "Inference or pattern derived from combining multiple facts...",
                  "confidence": 0.70,
                  "sourceItemIds": ["10", "11", "15"],
                  "point_reason": "Item 10 shows A, item 11 shows B, together they suggest C which is not stated anywhere. Type=REASONING because this is an inference."
                }
              ]
            }

            Field descriptions:
            - `type`: "SUMMARY" or "REASONING" (see Type Decision Logic above).
            - `content`: Plain text synthesized statement (1-3 sentences).
            - `confidence`:
              For SUMMARY:
                - 0.90+: Theme confirmed across 3+ items with strong consistency.
                - 0.80-0.89: Clear pattern from 2+ items.
                - 0.70-0.79: Reasonable consolidation, some items only loosely related.
              For REASONING:
                - 0.80+: Strong causal/logical chain supported by multiple items.
                - 0.65-0.79: Reasonable inference from available evidence.
                - < 0.65: Speculative — include only if highly valuable.
            - `sourceItemIds`: Array of strings. SUMMARY should have 2+ IDs. \
            Updated points MUST include both existing and new item IDs.
            - `point_reason`: CRITICAL. Explain: (1) which items contribute what information, \
            (2) what new understanding emerges from combining them, \
            (3) why this type (SUMMARY/REASONING) was chosen. \
            This field is for reasoning only and will NOT be stored.\
            """;

    // ===== EXAMPLES =====

    private static final String DEFAULT_EXAMPLES =
            """
            # Examples

            ## Example 1 — First-time synthesis

            Input:
            Existing Points: (none)
            New items:
            - id: "42", content: User loves spicy Sichuan food
            - id: "43", content: User is allergic to shellfish
            - id: "44", content: User recently tried a new Thai restaurant and loved the pad thai
            - id: "45", content: User mentioned cooking Sichuan dishes at home on weekends
            - id: "46", content: User had ramen for lunch today

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User is enthusiastic about Asian cuisine — actively enjoys \
            Sichuan food and Thai food, and cooks Sichuan dishes at home on weekends",
                  "confidence": 0.90,
                  "sourceItemIds": ["42", "44", "45"],
                  "point_reason": "Items 42 (loves Sichuan), 44 (loves Thai), 45 (cooks Sichuan \
            at home) together show a consistent pattern of Asian cuisine enthusiasm beyond any \
            single item. Type=SUMMARY because this consolidates a factual pattern."
                },
                {
                  "type": "SUMMARY",
                  "content": "User has a shellfish allergy, which is an important dietary \
            constraint for any food-related recommendations",
                  "confidence": 0.95,
                  "sourceItemIds": ["43"],
                  "point_reason": "Item 43 is a critical health constraint with unique retrieval \
            value. Single-source is acceptable because it carries multi-faceted implications \
            (safety, recommendations, restaurant choices). Type=SUMMARY because this is a \
            factual elevation."
                },
                {
                  "type": "REASONING",
                  "content": "User likely prefers bold, flavorful cuisines and may enjoy \
            exploring other spice-forward food cultures such as Indian or Korean",
                  "confidence": 0.65,
                  "sourceItemIds": ["42", "44"],
                  "point_reason": "Item 42 (spicy Sichuan) + item 44 (Thai) suggest a preference \
            for bold flavors. Neither item alone implies interest in Indian/Korean, but the \
            pattern does. Type=REASONING because this infers a broader preference not directly \
            stated."
                }
              ]
            }

            Note: Item 46 (ramen for lunch) is a trivial one-time event — absorbed into the \
            broader pattern, not its own point.

            ## Bad Example 1: 1-to-1 restatement (WRONG)

            Input: Same as Example 1

            Output (WRONG):
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User loves spicy Sichuan food",
                  "sourceItemIds": ["42"]
                },
                {
                  "type": "SUMMARY",
                  "content": "User is allergic to shellfish",
                  "sourceItemIds": ["43"]
                },
                {
                  "type": "SUMMARY",
                  "content": "User tried a Thai restaurant and loved pad thai",
                  "sourceItemIds": ["44"]
                }
              ]
            }

            -> Wrong: Each point restates exactly one item. No synthesis happened. These are \
            NOT insights — they are copies. Cluster items 42+44+45 into one cuisine pattern.

            ## Bad Example 2: Unrelated facts merged (WRONG)

            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User loves Sichuan food, is allergic to shellfish, and had ramen \
            for lunch",
                  "sourceItemIds": ["42", "43", "46"]
                }
              ]
            }

            -> Wrong: Three unrelated facts forced into one point. A food preference, a health \
            constraint, and a trivial lunch event have no thematic connection. Split or drop.

            ## Bad Example 3: Wrong type assignment (WRONG)

            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "User enjoys Sichuan food and Thai food and cooks at home on \
            weekends",
                  "sourceItemIds": ["42", "44", "45"]
                }
              ]
            }

            -> Wrong: This is a factual consolidation (what IS true), not an inference (what it \
            MEANS). Should be SUMMARY. REASONING requires deriving a conclusion not directly \
            stated in any item.

            ## Example 2 — Incremental update

            Input:
            Existing Points:
            - [SUMMARY] User prefers working from home and values schedule flexibility \
            (confidence: 0.85, sources: [10, 11])

            New items:
            - id: "55", content: User recently started going to the office 3 days a week
            - id: "56", content: User mentioned the new commute is exhausting
            - id: "57", content: User said the in-office collaboration has improved project velocity
            - id: "58", content: User is considering moving closer to the office

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "User transitioned from fully remote to a hybrid schedule \
            (3 days in office). While previously preferring remote work, the in-office \
            collaboration has improved project velocity, creating a tension between \
            productivity and comfort",
                  "confidence": 0.90,
                  "sourceItemIds": ["10", "11", "55", "57"],
                  "point_reason": "Existing point (sources 10,11) established remote preference. \
            Items 55 (hybrid switch) and 57 (improved velocity) update this picture. The \
            evolution from remote-only to hybrid with mixed feelings is a richer insight than \
            either alone. Type=SUMMARY because this consolidates the factual trajectory."
                },
                {
                  "type": "REASONING",
                  "content": "The exhausting commute combined with considering a move closer to \
            the office suggests user is adapting to the hybrid model long-term rather than \
            resisting it",
                  "confidence": 0.75,
                  "sourceItemIds": ["56", "58"],
                  "point_reason": "Item 56 (exhausting commute) alone suggests dissatisfaction, \
            but item 58 (considering moving closer) reframes it as adaptation. Neither item \
            alone implies long-term commitment to hybrid. Type=REASONING because 'adapting \
            long-term' is an inference."
                }
              ]
            }

            Note: 4 new items became part of 2 synthesized points, not 4 separate ones. The \
            existing point was updated to reflect the evolution.

            ## Example 3 — Chinese input

            Input:
            Existing Points: (none)
            New items:
            - id: "30", content: 用户最近在学习Python课程
            - id: "31", content: 用户在公司负责数据分析工作
            - id: "32", content: 用户抱怨Excel处理大数据太慢
            - id: "33", content: 用户问了一个pandas数据清洗的问题
            - id: "34", content: 用户说周末在看机器学习入门书

            Output:
            {
              "points": [
                {
                  "type": "SUMMARY",
                  "content": "用户正在从Excel转向Python技术栈进行数据分析，已在学习Python课程并积极使用pandas进行数据清洗",
                  "confidence": 0.90,
                  "sourceItemIds": ["30", "31", "32", "33"],
                  "point_reason": "Items 30(学Python), 31(负责数据分析), 32(Excel太慢), 33(用pandas) \
            共同描绘了一个从Excel到Python的技术栈迁移过程。任何单个item都无法呈现这个完整画面。\
            Type=SUMMARY因为这是事实整合。"
                },
                {
                  "type": "REASONING",
                  "content": "结合工作中的数据分析需求、Python学习和周末阅读机器学习书籍，用户可能正在规划向数据科学/机器学习方向的职业发展",
                  "confidence": 0.70,
                  "sourceItemIds": ["31", "34", "30"],
                  "point_reason": "Item 31(数据分析工作) + 30(学Python) + 34(看ML书) 三者结合暗示职业发展方向，\
            但没有任何item直接说明这一点。Type=REASONING因为'职业规划'是推理得出的结论。"
                }
              ]
            }\
            """;

    // ==================== Public API ====================

    public static PromptTemplate build(
            MemoryInsightType insightType,
            String groupName,
            List<InsightPoint> existingPoints,
            List<MemoryItem> newItems,
            int targetTokens) {

        var userPromptContent =
                buildInputBlock(insightType, groupName, existingPoints, newItems, targetTokens);

        var description =
                insightType.description() != null ? insightType.description() : insightType.name();

        var builder =
                PromptTemplate.builder("insight-point")
                        .section(NAME_OBJECTIVE, DEFAULT_OBJECTIVE)
                        .section(NAME_CONTEXT, DEFAULT_CONTEXT)
                        .section(NAME_WORKFLOW, DEFAULT_WORKFLOW)
                        .section(NAME_OUTPUT, OUTPUT)
                        .section(NAME_EXAMPLES, DEFAULT_EXAMPLES)
                        .variable("insight_type", insightType.name())
                        .variable("insight_description", description)
                        .variable("group_name", groupName)
                        .variable("target_length", String.valueOf(targetTokens))
                        .userPrompt(userPromptContent);

        var template = builder.build();

        // Apply summaryPrompt overrides from InsightType
        return applyOverrides(template, insightType.summaryPrompt());
    }

    // ==================== Internal Helpers ====================

    /**
     * Apply summaryPrompt section overrides from the InsightType.
     *
     * <p>If a key maps to null or empty, the section is removed. Otherwise, it is replaced.
     */
    private static PromptTemplate applyOverrides(
            PromptTemplate template, Map<String, String> summaryPrompt) {
        if (summaryPrompt == null) {
            return template;
        }
        var result = template;
        for (var entry : summaryPrompt.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                result = result.withoutSection(entry.getKey());
            } else {
                result = result.withSection(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static String buildInputBlock(
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
