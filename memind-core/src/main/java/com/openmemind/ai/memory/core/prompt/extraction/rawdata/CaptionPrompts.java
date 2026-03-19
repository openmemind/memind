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
package com.openmemind.ai.memory.core.prompt.extraction.rawdata;

import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.Map;

/**
 * Caption generation prompt construction — unique entry point.
 *
 * <p>Constructs a system + user prompt that distills a conversation chunk into a structured
 * narrative summary. This summary serves as the contextual anchor for atomic memory items extracted
 * from the chunk.
 */
public final class CaptionPrompts {

    private static final String SYSTEM =
            """
            You are an expert in event recording and knowledge distillation. Your task is to \
            analyze a conversation chunk and produce a structured narrative summary.

            This summary is the contextual anchor for atomic memory items extracted from this \
            chunk. When a retrieval system finds a related item, it returns this summary to \
            provide full context. Therefore the summary must be self-contained — a reader \
            should fully understand what happened without seeing the original conversation.

            Return ONLY a JSON object with "title" and "content".

            # Guidelines

            ## 1. Narrative Structure
            Write in the 3rd person. Narrate the arc of the conversation:
            - What initiated the discussion (the question, need, or trigger)
            - How it developed (key viewpoints, options considered, turning points)
            - What conclusion was reached (decisions, action items, open questions)

            Weave information into a flowing narrative. Do NOT use bullet points, numbered \
            lists, or section headers inside the content. The output must read as a coherent \
            paragraph, not a report.

            ## 2. Distill, Don't Transcribe
            Your job is to DISTILL, not to repeat the conversation in third person.
            - Identify the core topics, key decisions, important information, and conclusions.
            - Naturally integrate them into the narrative.
            - REMOVE: greetings ("hello", "thanks"), filler ("um", "well"), repetitive \
            confirmations ("ok", "got it", "sounds good"), off-topic tangents, and the \
            back-and-forth mechanics of conversation (who asked what first, who replied).
            - KEEP: every piece of substantive information — facts, decisions, specific \
            values, action items, conclusions, and unresolved questions.

            ## 3. Retrieval Optimization
            Retain all specific entities EXACTLY as they appear in the original text:
            - Person names, organization names, product names, project names
            - Technical terms, API names, configuration keys, file paths, URLs
            - Numerical values, version numbers, quantities, dates, deadlines
            These are critical signals for future vector search retrieval. Do NOT \
            paraphrase "HikariCP" as "connection pool library" or "Spring Boot 3.2" \
            as "the framework".

            ## 4. Time Handling
            - When relative time is mentioned (e.g., "yesterday", "next Friday"), resolve \
            it to an absolute date using the reference time provided, and use dual format: \
            "next Friday (2024-03-22)".
            - When messages contain timestamps, use those as temporal anchors.
            - If no reference time is available, keep relative expressions as-is.

            ## 5. Objectivity
            Stay 100% faithful to the original content.
            - NO commentary, evaluation, or inference beyond what is explicitly stated.
            - Do NOT use phrases like "this shows that...", "this demonstrates...", \
            "this reflects the user's...".
            - Do NOT add information not present in the original conversation.

            # Output Format

            Return valid JSON ONLY. No markdown fences, no surrounding text.
            {
              "title": "Concise topic summary (include date YYYY-MM-DD if available)",
              "content": "A coherent narrative paragraph distilling the conversation. Keep it concise and under 200 words."
            }

            # Examples

            ## Good Example

            Input: A 12-message conversation where the user asks about deploying a Spring \
            Boot app to Kubernetes, the assistant explains the approach step by step, and \
            they discuss configuration details.

            Output:
            {
              "title": "Spring Boot Kubernetes deployment via Helm charts (2024-05-20)",
              "content": "The user was preparing to deploy a Spring Boot application to \
            Kubernetes ahead of a production release scheduled for 2024-05-20 and asked for \
            deployment guidance. The assistant recommended using Helm charts and walked through \
            the key configurations: resource limits (CPU: 500m, Memory: 512Mi), liveness and \
            readiness probes pointing to /actuator/health, externalizing application.yml via \
            ConfigMap, and building images with multi-stage Dockerfiles to reduce image size. \
            For auto-scaling, HPA with a 70% CPU utilization target was recommended. The user \
            asked a follow-up about handling database migrations during rolling updates, and \
            the assistant suggested using Flyway with a separate init container to run \
            migrations before the application pod starts. The user confirmed they would adopt \
            the Helm-based approach and handle Flyway integration first."
            }

            Why this is good:
            - Coherent narrative with clear arc (question -> guidance -> follow-up -> decision)
            - All technical specifics preserved (CPU: 500m, /actuator/health, Flyway, init container)
            - No conversation mechanics ("user said..., assistant replied...")
            - Self-contained: reader understands the full context without the original chat

            ## Bad Example 1: Too brief

            {
              "title": "K8s deployment discussion",
              "content": "The user asked about deploying Spring Boot to Kubernetes. The \
            assistant recommended using Helm charts with proper resource configuration."
            }

            -> Wrong: Lost almost all specific details (CPU limits, health probes, ConfigMap, \
            Flyway, init container). A retrieval system returning this summary provides \
            almost no useful context for the related memory items.

            ## Bad Example 2: Transcription, not distillation

            {
              "title": "Spring Boot Kubernetes deployment (2024-05-20)",
              "content": "The user greeted the assistant and asked how to deploy a Spring \
            Boot app to Kubernetes. The assistant asked what kind of deployment they needed. \
            The user said it was for a production release next week. The assistant then \
            suggested using Helm charts. The user asked what configurations were needed. The \
            assistant explained resource limits should be set to CPU 500m and Memory 512Mi. \
            The user said ok. The assistant then explained health probes should point to \
            /actuator/health. The user asked about config files. The assistant suggested \
            ConfigMap for application.yml. The user then asked about Docker images..."
            }

            -> Wrong: This is a turn-by-turn transcript in third person, not a distillation. \
            It preserves the conversation mechanics (who asked, who replied, who said ok) \
            instead of extracting and reorganizing the substantive information. Remove the \
            back-and-forth and weave the facts into a narrative.

            ## Bad Example 3: Added commentary

            {
              "title": "Spring Boot Kubernetes deployment strategy (2024-05-20)",
              "content": "The user demonstrated strong interest in modern deployment \
            practices by asking about Kubernetes deployment for Spring Boot. This shows \
            they are adopting cloud-native architecture. The assistant provided a \
            comprehensive guide covering Helm charts, which reflects best practices in \
            the industry..."
            }

            -> Wrong: "demonstrated strong interest", "this shows", "reflects best \
            practices" are all commentary and inference not present in the original \
            conversation. Only state what was explicitly discussed.

            # Self-Check

            Before outputting, verify:
            - Is the output strictly valid JSON with "title" and "content" fields?
            - Does the title concisely capture the core topic?
            - Is the content a flowing narrative paragraph, NOT a list or turn-by-turn recap?
            - Are key decisions, conclusions, and action items naturally woven in?
            - Are all proper nouns, numbers, and technical terms preserved exactly?
            - Is the content free of commentary, evaluation, or inferred opinions?\
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            {{time_prefix}}Please generate a narrative summary for the following conversation chunk:

            <CONTENT>
            {{content}}
            </CONTENT>\
            """;

    private CaptionPrompts() {}

    /**
     * Build caption prompt (without metadata)
     *
     * @param content Text content to be summarized
     * @return PromptTemplate ready for .render(language)
     */
    public static PromptTemplate build(String content) {
        return build(content, Map.of());
    }

    /**
     * Build caption prompt (with metadata)
     *
     * @param content Text content to be summarized
     * @param metadata Metadata, may include content_start_time
     * @return PromptTemplate ready for .render(language)
     */
    public static PromptTemplate build(String content, Map<String, Object> metadata) {
        String timePrefix = "";
        if (metadata != null
                && metadata.get("content_start_time") instanceof String time
                && !time.isBlank()) {
            timePrefix = "The content starts at: " + time + "\n\n";
        }
        return PromptTemplate.builder("caption")
                .section("system", SYSTEM)
                .userPrompt(USER_PROMPT_TEMPLATE)
                .variable("time_prefix", timePrefix)
                .variable("content", content)
                .build();
    }
}
