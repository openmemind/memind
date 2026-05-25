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
package com.openmemind.ai.memory.plugin.rawdata.agent.item;

import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.List;
import java.util.Map;

/**
 * Prompt builder for agent episode memory extraction.
 */
public final class AgentItemPrompts {

    private static final String SYSTEM =
            """
            You extract durable memory items from one deterministic coding-agent \
            episode. The episode has already been parsed from raw agent events; do not invent \
            events, tools, files, or outcomes that are not present in the input.

            Categories are limited to: {{categories}}.

            Evidence rules:
            - Every item must include metadata.evidenceEventIds.
            - evidenceEventIds must be a non-empty subset of the provided episode event IDs.
            - Prefer the smallest evidence set that proves the memory.

            Category rules:
            - profile: stable facts or enduring preferences about the user.
            - behavior: recurring user habits or repeated collaboration/work patterns.
            - event: time-bound user/project situations, current work, decisions, or milestones.
            - tool: concrete command or tool usage knowledge grounded in observed tool events.
            - resolution: resolved problem knowledge only; metadata must include problem and \
            fix or conclusion.
            - playbook: reusable workflow only; metadata must include trigger, at least two \
            steps, and expectedOutcome.
            - directive: durable instruction, collaboration boundary, or stable agent behavior \
            rule that should be reused in later sessions.

            Graph rules:
            - Entity types must use the current core graph vocabulary only: person, \
            organization, place, object, concept, other, special.
            - Causal relations must use only caused_by, enabled_by, motivated_by.
            - Causal relation indexes must reference item indexes in this response.

            Return only a JSON object matching:
            {
              "items": [
                {
                  "content": "durable memory sentence",
                  "confidence": 0.0,
                  "occurredAt": null,
                  "insightTypes": [
                    "identity|preferences|relationships|behavior|experiences|tools|resolutions|playbooks|directives"
                  ],
                  "metadata": {
                    "evidenceEventIds": ["event-id"],
                    "...": "category-specific fields"
                  },
                  "category": "profile|behavior|event|tool|resolution|playbook|directive",
                  "entities": [
                    {"name": "entity", "entityType": "object", "salience": 0.8}
                  ],
                  "causalRelations": [
                    {"causeIndex": 0, "effectIndex": 1, "relationType": "enabled_by", \
            "strength": 0.8}
                  ]
                }
              ]
            }
            """;

    private static final String USER_PROMPT =
            """
            # Episode Metadata

            episodeId: {{episode_id}}
            sourceClient: {{source_client}}
            sessionId: {{session_id}}
            timelineId: {{timeline_id}}
            outcome: {{outcome}}
            files: {{files}}
            commands: {{commands}}
            toolNames: {{tool_names}}
            failureSignals: {{failure_signals}}
            eventIds: {{event_ids}}

            # Episode Text

            {{episode_text}}
            """;

    private AgentItemPrompts() {}

    public static PromptTemplate build(ParsedSegment segment, List<String> categories) {
        Map<String, Object> metadata = segment.metadata() == null ? Map.of() : segment.metadata();
        return PromptTemplate.builder("agent-item")
                .section("system", SYSTEM)
                .userPrompt(USER_PROMPT)
                .variable("categories", String.join(", ", categories))
                .variable("episode_id", string(metadata.get("episodeId")))
                .variable("source_client", string(metadata.get("sourceClient")))
                .variable("session_id", string(metadata.get("sessionId")))
                .variable("timeline_id", string(metadata.get("timelineId")))
                .variable("outcome", string(metadata.get("outcome")))
                .variable("files", formatList(metadata.get("files")))
                .variable("commands", formatList(metadata.get("commands")))
                .variable("tool_names", formatList(metadata.get("toolNames")))
                .variable("failure_signals", formatList(metadata.get("failureSignals")))
                .variable("event_ids", formatList(metadata.get("eventIds")))
                .variable("episode_text", segment.text() == null ? "" : segment.text())
                .build();
    }

    private static String formatList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "[]";
        }
        return list.stream()
                .map(AgentItemPrompts::string)
                .filter(item -> !item.isBlank())
                .toList()
                .toString();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
