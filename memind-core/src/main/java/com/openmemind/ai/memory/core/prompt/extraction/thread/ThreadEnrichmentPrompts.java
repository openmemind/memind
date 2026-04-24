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
package com.openmemind.ai.memory.core.prompt.extraction.thread;

import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.util.List;
import java.util.Map;

/**
 * Prompt construction for optional replay-safe memory-thread enrichment.
 */
public final class ThreadEnrichmentPrompts {

    public static final String PROMPT_VERSION = "thread-enrichment-v1";

    private static final String OBJECTIVE =
            """
            You are a conservative memory-thread enrichment assistant.

            Your job is to improve a single already-derived memory thread without changing the \
            deterministic base derivation contract.

            Default to returning no enrichment. Only emit output when the replayed item-backed \
            evidence strongly supports either:
            1. a better thread headline, or
            2. a bounded state event that is explicitly grounded in an existing basis event.
            """;

    private static final String RULES =
            """
            # Rules

            - You MUST only reference `basisEventKey` values that already exist in the provided \
            item-backed event list.
            - Never invent new supporting evidence, dates, or participants.
            - Prefer a single headline refresh over speculative state updates.
            - If uncertain, return an empty `events` array.
            - `HEADLINE_REFRESH` must be represented as:
              - `eventType = OBSERVATION`
              - `meaningful = false`
              - `payloadJson.summaryRole = HEADLINE_REFRESH`
            - State-changing output may only use these event types:
              `UPDATE`, `STATE_CHANGE`, `BLOCKER_ADDED`, `BLOCKER_CLEARED`, `DECISION_MADE`, \
              `MILESTONE_REACHED`, `RESOLUTION_DECLARED`, `SETBACK`
            """;

    private static final String OUTPUT =
            """
            # Output Format

            Return valid JSON only:

            {
              "events": [
                {
                  "eventType": "OBSERVATION",
                  "meaningful": false,
                  "basisEventKey": "existing-event-key",
                  "payloadJson": {
                    "summary": "short grounded headline or state summary",
                    "summaryRole": "HEADLINE_REFRESH"
                  }
                }
              ]
            }
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            # Thread Projection

            {{thread_json}}

            # Item-Backed Events

            {{events_json}}\
            """;

    private ThreadEnrichmentPrompts() {}

    public static PromptTemplate build(
            MemoryThreadProjection thread, List<MemoryThreadEvent> itemBackedEvents) {
        return build(PromptRegistry.EMPTY, thread, itemBackedEvents);
    }

    public static PromptTemplate buildDefault() {
        return defaultBuilder().build();
    }

    public static PromptTemplate buildPreview() {
        return buildDefault();
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            MemoryThreadProjection thread,
            List<MemoryThreadEvent> itemBackedEvents) {
        if (thread == null) {
            throw new IllegalArgumentException("thread must not be null");
        }
        List<MemoryThreadEvent> events =
                itemBackedEvents == null ? List.of() : List.copyOf(itemBackedEvents);

        PromptTemplate.Builder builder =
                registry.hasOverride(PromptType.THREAD_ENRICHMENT)
                        ? PromptTemplate.builder("ThreadEnrichment")
                                .section(
                                        "system",
                                        registry.getOverride(PromptType.THREAD_ENRICHMENT))
                        : defaultBuilder();

        return builder.userPrompt(USER_PROMPT_TEMPLATE)
                .variable("thread_json", toJson(threadView(thread)))
                .variable(
                        "events_json",
                        toJson(events.stream().map(ThreadEnrichmentPrompts::eventView).toList()))
                .build();
    }

    private static PromptTemplate.Builder defaultBuilder() {
        return PromptTemplate.builder("ThreadEnrichment")
                .section("objective", OBJECTIVE)
                .section("rules", RULES)
                .section("output", OUTPUT);
    }

    private static Map<String, Object> threadView(MemoryThreadProjection thread) {
        java.util.LinkedHashMap<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("threadKey", thread.threadKey());
        view.put("threadType", thread.threadType().name());
        view.put("lifecycleStatus", thread.lifecycleStatus().name());
        view.put("objectState", thread.objectState().name());
        view.put("headline", thread.headline());
        view.put("snapshot", thread.snapshotJson());
        view.put("openedAt", thread.openedAt());
        view.put("lastEventAt", thread.lastEventAt());
        view.put("lastMeaningfulUpdateAt", thread.lastMeaningfulUpdateAt());
        return Map.copyOf(view);
    }

    private static Map<String, Object> eventView(MemoryThreadEvent event) {
        java.util.LinkedHashMap<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("eventKey", event.eventKey());
        view.put("eventSeq", event.eventSeq());
        view.put("eventType", event.eventType().name());
        view.put("eventTime", event.eventTime());
        view.put("meaningful", event.meaningful());
        view.put("payload", event.eventPayloadJson());
        return Map.copyOf(view);
    }

    private static String toJson(Object value) {
        try {
            return JsonUtils.newMapper().writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (tools.jackson.core.JacksonException error) {
            throw new IllegalStateException(
                    "Failed to serialize thread enrichment prompt input", error);
        }
    }
}
