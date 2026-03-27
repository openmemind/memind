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
            You are a semantic grouping engine. Your task is to assign memory items \
            into thematic groups under a given insight dimension. Each group clusters \
            items that share the same specific sub-topic.\
            """;

    private static final String CONTEXT =
            """
            # Context

            You are grouping items for the insight dimension: "{{insight_type_name}}"
            Dimension description: {{insight_type_description}}

            This means:
            - The dimension description tells you WHAT kind of information belongs here \
            and gives example group names as a granularity reference.
            - Existing groups (listed in the input) are semantic anchors. If a new item \
            fits an existing group, assign it there. Copy the group name EXACTLY \
            (case-sensitive).
            - If a new item does not fit any existing group, create a new one.\
            """;

    private static final String WORKFLOW =
            """
            # Grouping Principles

            1. Exclusivity: Each item MUST be assigned to EXACTLY ONE group.
            2. High Cohesion: Items in the same group must share a specific sub-topic. \
            Never merge conceptually distinct items. Never use vague names like \
            "Miscellaneous", "Other", or "General Info".
            3. Granularity: Group size is determined by semantic cohesion, not by count. \
            A group with 20 items is fine if they genuinely share the same sub-topic. \
            A single-item group is fine when the topic is genuinely distinct. The key \
            test: could you describe what ALL items in this group have in common in \
            one specific phrase? If not, the group is too broad — split it.
            4. Naming: New group names must be natural, standalone theme labels that a \
            human can understand without extra context. Prefer concise noun phrases \
            (roughly 2-6 English words or short Chinese labels), not sentence fragments \
            or compressed summaries. Use "Morning Coffee Preferences" or "开发工具偏好", \
            not "Beverages". Do NOT stitch together a broad topic and one specific example \
            into one title (bad: "自我抚慰与动物园心安"). Do NOT use metadata-like labels \
            such as dates, session notes, or record headers (bad: "2026-03-27 会话记录"). \
            Do NOT repeat the dimension name as a group name.
            5. Language: Group names MUST match the language of the items exactly. \
            Chinese items → Chinese group names. English items → English group names.

            # Workflow

            ## Step 1 — Understand Scope
            - Review the dimension description to understand what belongs here.
            - Review existing groups as semantic anchors.

            ## Step 2 — Assign Each Item
            For each item:
            - Does it match an existing group's sub-topic? → Assign to that group \
            (copy the name exactly).
            - Does it match another new item's sub-topic? → Group them together \
            under a new descriptive name.
            - Is it a distinct topic with no match? → Create a new single-item group.

            ## Step 3 — Validate
            - Every item ID appears exactly once.
            - No group name duplicates the dimension name.
            - Group names match the item language.
            - Group names are natural, standalone theme labels, not metadata or stitched titles.\
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
            - `groupName`: Exact existing group name or a new natural, standalone theme label.
            - `itemIds`: Array of item ID strings assigned to this group.
            - `reason`: CRITICAL. Briefly explain the shared sub-topic that binds these items. \
            This field is for reasoning only and will NOT be stored.\
            """;

    private static final String EXAMPLES =
            """
            # Examples

            ## Example 1 — Mixed assignment (existing + new groups)

            Input:
            Dimension: identity
            Existing groups: career_background, education
            Items:
            - id: "10", content: User has been a backend engineer for 8 years
            - id: "11", content: User graduated from MIT with a CS degree in 2015
            - id: "12", content: User is fluent in English and Mandarin
            - id: "13", content: User holds AWS Solutions Architect certification
            - id: "14", content: User led a team of 5 engineers at their previous company

            Output:
            {
              "assignments": [
                {
                  "groupName": "career_background",
                  "itemIds": ["10", "14"],
                  "reason": "Both describe professional career history: years of experience and leadership role."
                },
                {
                  "groupName": "education",
                  "itemIds": ["11"],
                  "reason": "University degree information."
                },
                {
                  "groupName": "Language Proficiency",
                  "itemIds": ["12"],
                  "reason": "Language skills are a distinct personal trait, not career or education."
                },
                {
                  "groupName": "Professional Certifications",
                  "itemIds": ["13"],
                  "reason": "Certification is distinct from formal education and career history."
                }
              ]
            }

            Note: Items 12 and 13 each got their own group because language skills and \
            certifications are genuinely different sub-topics. This is correct — do NOT \
            force them into "career_background" or "education".

            ## Bad Example 1: Too coarse (WRONG)

            {
              "assignments": [
                {
                  "groupName": "career_background",
                  "itemIds": ["10", "11", "12", "13", "14"]
                }
              ]
            }

            -> Wrong: Dumped everything into one group. Education, language skills, and \
            certifications are distinct sub-topics that deserve their own groups.

            ## Bad Example 2: Vague naming (WRONG)

            {
              "assignments": [
                {
                  "groupName": "Other Info",
                  "itemIds": ["12", "13"]
                }
              ]
            }

            -> Wrong: "Other Info" is a junk drawer. Each item has a clear sub-topic \
            (language proficiency, certifications) — name them specifically.

            ## Example 2 — Chinese items, no existing groups

            Input:
            Dimension: preferences
            Existing groups: (none)
            Items:
            - id: "20", content: 用户喜欢用 IntelliJ IDEA 而不是 VS Code
            - id: "21", content: 用户偏好深色主题的编辑器
            - id: "22", content: 用户喜欢吃川菜，尤其是麻辣火锅
            - id: "23", content: 用户不喜欢甜食
            - id: "24", content: 用户喜欢简洁的代码风格，反对过度注释

            Output:
            {
              "assignments": [
                {
                  "groupName": "开发工具偏好",
                  "itemIds": ["20", "21"],
                  "reason": "都是关于开发环境的偏好：IDE 选择和主题风格。"
                },
                {
                  "groupName": "饮食口味",
                  "itemIds": ["22", "23"],
                  "reason": "都是关于食物的喜好和厌恶。"
                },
                {
                  "groupName": "代码风格偏好",
                  "itemIds": ["24"],
                  "reason": "关于代码编写风格的偏好，与开发工具偏好是不同维度。"
                }
              ]
            }

            Note: "开发工具偏好" and "代码风格偏好" are separate groups — IDE choice and \
            coding style are different sub-topics even though both relate to development.

            ## Bad Example 3: Wrong group name language (WRONG)

            {
              "assignments": [
                {
                  "groupName": "Development Preferences",
                  "itemIds": ["20", "21", "24"]
                }
              ]
            }

            -> Wrong: Items are Chinese but group name is English. Group names must match \
            the item language. Also merged tool preferences with code style preferences.

            ## Bad Example 4: Stitched title (WRONG)

            {
              "assignments": [
                {
                  "groupName": "自我抚慰与动物园心安",
                  "itemIds": ["30", "31"]
                }
              ]
            }

            -> Wrong: This stitches together a broad topic ("自我抚慰") and one specific \
            example/context ("动物园心安"). A valid group name should be one natural, \
            standalone theme label such as "自我安抚方式" or "动物园带来的安定感", \
            depending on the actual shared topic.

            ## Bad Example 5: Metadata-like title (WRONG)

            {
              "assignments": [
                {
                  "groupName": "2026-03-27 会话记录",
                  "itemIds": ["32"]
                }
              ]
            }

            -> Wrong: Dates, session notes, and record headers are metadata, not semantic \
            group names. Use the actual topic of the item instead.\
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
            sb.append("Output Language: All groupName values MUST be written in ")
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
