package com.openmemind.ai.memory.core.prompt.extraction.insight;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.List;

/**
 * ROOT-mode interaction guide prompt builder.
 *
 * <p>Generates prescriptive interaction directives for the AI agent based on cross-dimensional
 * BRANCH analysis. Unlike {@link RootSynthesisPrompts} which describes who the user is, this
 * prompt tells the AI how to interact with the user.
 *
 * <p>Output uses three directive dimensions: communication_style, domain_strategy, and boundary.
 * All points must be imperative sentences directly embeddable into a system prompt.
 */
public final class InteractionGuideSynthesisPrompts {

    private InteractionGuideSynthesisPrompts() {}

    // ===== Section name constants =====

    public static final String NAME_OBJECTIVE = "objective";
    public static final String NAME_CONTEXT = "context";
    public static final String NAME_WORKFLOW = "workflow";
    public static final String NAME_OUTPUT = "output";
    public static final String NAME_EXAMPLES = "examples";

    // ===== OBJECTIVE =====

    private static final String OBJECTIVE =
            """
            You are an interaction directive engine. Your task is to analyze all BRANCH-level \
            insight summaries and produce prescriptive interaction directives for the AI agent. \
            These directives tell the AI HOW to interact with this user — they are NOT \
            descriptions of who the user is.

            Key distinction:
            - profile ROOT describes the user (descriptive): "The user prefers concise answers."
            - interaction ROOT tells the AI what to do (prescriptive): "Keep responses under \
            3 paragraphs. Lead with the answer, then explain only if needed."\
            """;

    // ===== CONTEXT =====

    private static final String CONTEXT =
            """
            # Context

            You are generating the "{{root_type_name}}" ROOT node in the insight tree.
            ROOT type description: {{root_description}}

            Your position in the insight tree:
            - Upstream (your input): BRANCH nodes. Each BRANCH is a cross-group synthesis \
            within a single insight dimension. They are already higher-level abstractions, \
            not raw memory items.
            - Downstream (your output consumer): The AI agent's system prompt. Your \
            directives will be embedded directly — they must be self-contained imperative \
            instructions.

            The 6 BRANCH dimensions you may receive:
            - identity: Who the user IS — stable traits, professional background, skills
            - preferences: What the user LIKES/DISLIKES/VALUES — subjective opinions
            - relationships: The user's social network — family, colleagues, dynamics
            - experiences: What is HAPPENING — time-bound projects, goals, situations
            - behavior: What the user DOES REPEATEDLY — habits, routines, patterns
            - procedural: Reusable HOW-TO knowledge — procedures, recipes, agent directives

            Your job is to read ACROSS all these dimensions and derive actionable interaction \
            directives. A single directive often draws evidence from 2-3 BRANCH dimensions.

            Sibling ROOT — "profile" — handles the descriptive portrait of who the user is. \
            You handle the prescriptive side: what the AI should DO differently for this user.\
            """;

    // ===== WORKFLOW (includes Core Principles, Steps, Dimension Decision Logic) =====

    private static final String WORKFLOW =
            """
            # Core Principles
            1. Prescriptive, Not Descriptive: Every point MUST be an imperative instruction. \
            "The user prefers short answers" is WRONG. "Keep responses under 3 paragraphs" \
            is CORRECT.
            2. Full Replacement: Output the COMPLETE current-state list. Not delta patches. \
            If an existing directive is still valid, it MUST appear in your output.
            3. Evidence-Based: Each directive must be grounded in specific BRANCH evidence. \
            The point_reason field must cite which BRANCH dimensions support it.
            4. Directly Embeddable: Each directive must work as a standalone system prompt \
            instruction — no context explanation needed.
            5. Atomicity: One directive per point. Don't combine unrelated instructions.
            6. Brevity: 1-2 sentences per directive. Actionable, not explanatory.
            7. Plain Text Only: No markdown formatting in point content.
            8. Language: Output MUST match the input BRANCH language exactly.

            # Workflow

            ## Step 1 — Parse & Cross-Reference
            - Read each BRANCH summary and extract signals relevant to interaction:
              - identity/preferences → communication tone, detail depth, formality
              - behavior → response structure, pacing, format expectations
              - experiences → context-sensitive strategies (current projects, deadlines)
              - procedural → agent directives the user has explicitly given
              - relationships → social context that affects communication
            - Cross-reference signals across dimensions. The strongest directives come from \
            convergent evidence (e.g., preferences says "likes code examples" + behavior \
            says "sends code snippets in every message" → strong signal).

            ## Step 2 — Derive Directives
            For each directive, determine its dimension using the decision logic below. \
            Write it as an imperative sentence directly embeddable into a system prompt.

            Compare with existing directives:
            - Still valid: keep (possibly refine wording with new evidence).
            - Contradicted by newer BRANCH data: rewrite to reflect the change.
            - New pattern discovered: add new directive.
            - No longer supported by evidence: drop.

            ## Step 3 — Validate
            Before outputting, verify each directive against this checklist:
            - Is it an imperative sentence? ("Use...", "Avoid...", "When X, do Y...") \
            If it reads as a description of the user → REWRITE as an instruction.
            - Does it have a dimension label?
            - Is it specific enough to be actionable? "Be helpful" is NOT actionable.
            - Are there duplicate or overlapping directives? If yes → merge.
            - Is the output valid JSON with no markdown fences?

            # Dimension Decision Logic

            For each directive, determine its dimension:

            | Ask yourself                                                             | Dimension           |
            |--------------------------------------------------------------------------|---------------------|
            | Is this about HOW to communicate (tone, format, verbosity, structure)?    | communication_style |
            | Is this a strategy specific to a topic domain or expertise area?          | domain_strategy     |
            | Is this a DO or DO NOT behavioral rule?                                   | boundary            |

            Guidelines:
            - communication_style = general communication preferences that apply across topics. \
            Examples: "Use bullet points", "Keep responses concise", "Match the user's \
            formality level"
            - domain_strategy = differentiated handling for specific topic areas. \
            Examples: "For Go questions, lead with code", "For management discussions, \
            use structured frameworks"
            - boundary = explicit behavioral rules, especially from user feedback or \
            procedural directives. \
            Examples: "Never ask clarifying questions when intent is clear", \
            "Always provide code examples with configuration changes"
            - When a directive could fit multiple dimensions, choose the most specific one. \
            "Use code examples" (general) → communication_style. \
            "For Spring Boot questions, use code examples" (domain-specific) → domain_strategy.\
            """;

    // ===== OUTPUT FORMAT =====

    private static final String OUTPUT =
            """
            # Output Format

            Return ONLY a raw JSON object. No markdown fences. No surrounding text.
            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "Keep responses under 3 paragraphs. Lead with the answer, then explain only if needed.",
                  "confidence": 0.85,
                  "sourceItemIds": [],
                  "metadata": {
                    "dimension": "communication_style"
                  },
                  "point_reason": "behavior BRANCH shows user gets impatient with long responses. preferences BRANCH shows user values conciseness. Convergent evidence from 2 dimensions → high confidence. Dimension=communication_style because this is about response length/structure."
                }
              ]
            }

            Field descriptions:
            - `type`: Always "REASONING" (all interaction directives are derived from \
            cross-BRANCH analysis).
            - `content`: Imperative instruction text (1-2 sentences). Same language as \
            BRANCHes.
            - `confidence`:
              - 0.85+: Directive supported by convergent evidence from 2+ BRANCHes, or \
              directly stated by user in procedural BRANCH.
              - 0.70-0.84: Reasonable inference from clear signals in 1-2 BRANCHes.
              - 0.60-0.69: Speculative but potentially valuable. Include sparingly.
              - boundary DO NOT rules require 0.80+ (higher bar for restrictions).
            - `sourceItemIds`: Always empty `[]` at ROOT level.
            - `metadata.dimension` (REQUIRED): One of "communication_style", \
            "domain_strategy", or "boundary".
            - `point_reason`: CRITICAL. Explain: (1) which BRANCH dimensions provide what \
            evidence, (2) what interaction strategy you derived, (3) why this dimension \
            was chosen. This field is for reasoning only and will NOT be stored.\
            """;

    // ===== EXAMPLES =====

    private static final String EXAMPLES =
            """
            # Examples

            ## Example 1 — Cross-BRANCH interaction directives

            Input:
            BRANCH Summaries:
            1. [type=identity] Senior backend engineer with 8 years Go and PostgreSQL \
            experience. Led a team of 5.
            2. [type=preferences] Prefers code examples over verbal explanations. Dislikes \
            being asked clarifying questions when intent is obvious. Values conciseness.
            3. [type=behavior] Responds in short bursts, rarely writes paragraphs. Gets \
            impatient when responses exceed 3 paragraphs. Sends follow-up questions immediately.
            4. [type=procedural] User instructed the agent: "stop being so verbose". Praised \
            a response that started with a code block.

            Output:
            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "Lead every technical response with a code block or concrete \
            example. Place explanation after the code, not before.",
                  "confidence": 0.90,
                  "sourceItemIds": [],
                  "metadata": { "dimension": "communication_style" },
                  "point_reason": "preferences BRANCH: prefers code over verbal explanations. \
            procedural BRANCH: praised code-first response. behavior BRANCH: sends short \
            bursts, wants quick answers. Three dimensions converge on code-first format. \
            Dimension=communication_style because this is about response structure."
                },
                {
                  "type": "REASONING",
                  "content": "Keep responses under 3 paragraphs. Use bullet points for \
            multi-part answers instead of prose.",
                  "confidence": 0.88,
                  "sourceItemIds": [],
                  "metadata": { "dimension": "communication_style" },
                  "point_reason": "behavior BRANCH: gets impatient beyond 3 paragraphs. \
            procedural BRANCH: told agent 'stop being verbose'. preferences BRANCH: values \
            conciseness. Strong convergence. Dimension=communication_style because this is \
            about verbosity."
                },
                {
                  "type": "REASONING",
                  "content": "When the user asks a Go or PostgreSQL question, provide working \
            code first with inline comments. Skip high-level architecture explanations unless \
            explicitly requested.",
                  "confidence": 0.82,
                  "sourceItemIds": [],
                  "metadata": { "dimension": "domain_strategy" },
                  "point_reason": "identity BRANCH: senior Go/PostgreSQL engineer (expert \
            level, no need for basics). preferences BRANCH: code over verbal. Combined: for \
            this specific domain, go straight to working code. Dimension=domain_strategy \
            because this is domain-specific handling."
                },
                {
                  "type": "REASONING",
                  "content": "Never ask clarifying questions when the user's intent is \
            reasonably clear. Make a best-effort attempt and note assumptions at the end.",
                  "confidence": 0.85,
                  "sourceItemIds": [],
                  "metadata": { "dimension": "boundary" },
                  "point_reason": "preferences BRANCH: dislikes clarifying questions when \
            intent is obvious. behavior BRANCH: sends follow-ups immediately (self-corrects). \
            This is a clear DO NOT rule. Dimension=boundary because this is a behavioral \
            restriction."
                }
              ]
            }

            ## Bad Example 1: Descriptive statements instead of directives (WRONG)

            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "The user prefers concise answers and dislikes verbose responses.",
                  "metadata": { "dimension": "communication_style" }
                },
                {
                  "type": "REASONING",
                  "content": "The user is a senior Go developer who values code examples.",
                  "metadata": { "dimension": "domain_strategy" }
                }
              ]
            }

            -> Wrong: These DESCRIBE the user. They belong in the "profile" ROOT, not here. \
            Interaction directives must be imperative: "Keep responses under 3 paragraphs", \
            not "The user prefers concise answers".

            ## Bad Example 2: Vague directives without evidence (WRONG)

            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "Be helpful and responsive.",
                  "confidence": 0.90,
                  "metadata": { "dimension": "communication_style" }
                },
                {
                  "type": "REASONING",
                  "content": "Adapt to the user's needs.",
                  "confidence": 0.85,
                  "metadata": { "dimension": "boundary" }
                }
              ]
            }

            -> Wrong: These are generic platitudes, not user-specific directives. Every \
            directive must be grounded in specific BRANCH evidence. "Be helpful" applies \
            to every user — it provides zero personalization value.

            ## Example 2 — Chinese input

            Input:
            BRANCH Summaries:
            1. [type=identity] 产品经理，负责用户增长方向。经常需要数据分析支持。
            2. [type=behavior] 习惯一次发送长消息，描述多个问题。回复后会逐个确认每个问题。
            3. [type=preferences] 偏好结构化回答，如编号列表。对模糊回答表示不满。
            4. [type=procedural] 反馈说"回答太笼统了，我需要具体步骤"。

            Output:
            {
              "points": [
                {
                  "type": "REASONING",
                  "content": "用编号列表回答所有多部分问题。对用户消息中的每个子问题编号并逐一回复，\
            确保不遗漏。",
                  "confidence": 0.88,
                  "sourceItemIds": [],
                  "metadata": { "dimension": "communication_style" },
                  "point_reason": "behavior BRANCH: 一次发多个问题并逐个确认。preferences BRANCH: \
            偏好编号列表。两个维度收敛于结构化编号回复。Dimension=communication_style 因为这是关于\
            回复格式。"
                },
                {
                  "type": "REASONING",
                  "content": "回答数据分析相关问题时，提供具体的指标定义、计算公式或SQL示例，而非\
            概念性描述。",
                  "confidence": 0.82,
                  "sourceItemIds": [],
                  "metadata": { "dimension": "domain_strategy" },
                  "point_reason": "identity BRANCH: 产品经理，需要数据分析支持。procedural BRANCH: \
            要求具体步骤而非笼统回答。针对数据分析这个特定领域的策略。Dimension=domain_strategy \
            因为这是领域特定处理。"
                },
                {
                  "type": "REASONING",
                  "content": "永远不要给出模糊建议。每个回答必须包含至少一个具体的、可执行的步骤或示例。",
                  "confidence": 0.90,
                  "sourceItemIds": [],
                  "metadata": { "dimension": "boundary" },
                  "point_reason": "procedural BRANCH: 明确反馈'回答太笼统'。preferences BRANCH: \
            对模糊回答不满。这是一条明确的 DO NOT 规则。Dimension=boundary 因为这是行为限制。"
                }
              ]
            }\
            """;

    // ==================== Public API ====================

    public static PromptTemplate build(
            MemoryInsightType rootInsightType,
            String existingSummary,
            List<MemoryInsight> branchInsights,
            int targetTokens) {

        var userPromptContent = buildUserPrompt(existingSummary, branchInsights, targetTokens);

        return PromptTemplate.builder("interaction-guide-synthesis")
                .section(NAME_OBJECTIVE, OBJECTIVE)
                .section(NAME_CONTEXT, CONTEXT)
                .section(NAME_WORKFLOW, WORKFLOW)
                .section(NAME_OUTPUT, OUTPUT)
                .section(NAME_EXAMPLES, EXAMPLES)
                .variable("root_type_name", rootInsightType.name())
                .variable(
                        "root_description",
                        rootInsightType.description() != null
                                ? rootInsightType.description()
                                : rootInsightType.name())
                .userPrompt(userPromptContent)
                .build();
    }

    // ==================== Internal Helpers ====================

    private static String buildUserPrompt(
            String existingSummary, List<MemoryInsight> branchInsights, int targetTokens) {

        var sb = new StringBuilder();
        sb.append("# Token Budget\n");
        sb.append(targetTokens).append("\n");

        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append("\n# Existing Directives\n");
            sb.append(existingSummary).append("\n");
        }

        sb.append("\n# BRANCH Summaries\n");
        for (int i = 0; i < branchInsights.size(); i++) {
            var branch = branchInsights.get(i);
            sb.append(i + 1)
                    .append(". [type=")
                    .append(branch.type())
                    .append("] ")
                    .append(branch.pointsContent())
                    .append("\n");
        }

        return sb.toString();
    }
}
