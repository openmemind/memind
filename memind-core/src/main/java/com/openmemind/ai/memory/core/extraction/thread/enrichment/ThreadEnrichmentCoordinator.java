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
package com.openmemind.ai.memory.core.extraction.thread.enrichment;

import com.openmemind.ai.memory.core.builder.MemoryThreadEnrichmentOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.extraction.thread.ThreadReplaySuccessContext;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentAppendResult;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.SerializationFeature;

/**
 * Write-side coordinator for optional replay-safe thread enrichment.
 */
public final class ThreadEnrichmentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ThreadEnrichmentCoordinator.class);
    private static final Comparator<ThreadEnrichmentResult> RESULT_ORDER =
            Comparator.comparing(ThreadEnrichmentResult::eventType)
                    .thenComparing(ThreadEnrichmentResult::meaningful)
                    .thenComparing(ThreadEnrichmentResult::basisEventKey)
                    .thenComparing(result -> canonicalJson(result.payloadJson()));

    private final ThreadEnrichmentInputStore inputStore;
    private final ThreadReplayScheduler replayScheduler;
    private final ThreadEnrichmentAssistant assistant;
    private final MemoryThreadEnrichmentOptions options;
    private final ThreadEnrichmentDebouncer debouncer;
    private final Scheduler asyncScheduler;
    private final Clock clock;

    public ThreadEnrichmentCoordinator(
            ThreadEnrichmentInputStore inputStore,
            ThreadReplayScheduler replayScheduler,
            ThreadEnrichmentAssistant assistant,
            MemoryThreadEnrichmentOptions options) {
        this(
                inputStore,
                replayScheduler,
                assistant,
                options,
                Schedulers.boundedElastic(),
                Clock.systemUTC());
    }

    public ThreadEnrichmentCoordinator(
            ThreadEnrichmentInputStore inputStore,
            ThreadReplayScheduler replayScheduler,
            ThreadEnrichmentAssistant assistant,
            MemoryThreadEnrichmentOptions options,
            Scheduler asyncScheduler,
            Clock clock) {
        this.inputStore = Objects.requireNonNull(inputStore, "inputStore");
        this.replayScheduler = Objects.requireNonNull(replayScheduler, "replayScheduler");
        this.assistant = Objects.requireNonNull(assistant, "assistant");
        this.options = options != null ? options : MemoryThreadEnrichmentOptions.defaults();
        this.debouncer = new ThreadEnrichmentDebouncer();
        this.asyncScheduler = Objects.requireNonNull(asyncScheduler, "asyncScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void afterSuccessfulReplay(ThreadReplaySuccessContext context) {
        Objects.requireNonNull(context, "context");
        MemoryId memoryId = context.memoryId();
        List<MemoryThreadProjection> threads = context.threads();
        long cutoffItemId = context.replayCutoffItemId();
        Instant effectiveNow = context.finalizedAt() != null ? context.finalizedAt() : clock.instant();
        if (!options.enabled() || cutoffItemId <= 0L || threads == null || threads.isEmpty()) {
            return;
        }

        Mono.fromRunnable(
                        () ->
                                processReplaySuccess(
                                        memoryId,
                                        context.threads(),
                                        context.events(),
                                        context.replayCutoffItemId(),
                                        context.materializationPolicyVersion(),
                                        effectiveNow))
                .subscribeOn(asyncScheduler)
                .subscribe(
                        ignored -> {},
                        error ->
                                log.warn(
                                        "Thread enrichment coordinator failed open for memoryId={} cutoff={}",
                                        memoryId,
                                        context.replayCutoffItemId(),
                                        error));
    }

    private void processReplaySuccess(
            MemoryId memoryId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> itemBackedEvents,
            long cutoffItemId,
            String materializationPolicyVersion,
            Instant now) {
        Map<String, List<MemoryThreadEvent>> eventsByThreadKey =
                (itemBackedEvents == null ? List.<MemoryThreadEvent>of() : itemBackedEvents).stream()
                        .filter(ThreadEnrichmentDebouncer::isItemBacked)
                        .collect(
                                Collectors.groupingBy(
                                        MemoryThreadEvent::threadKey,
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        for (MemoryThreadProjection thread : threads) {
            List<MemoryThreadEvent> threadEvents =
                    eventsByThreadKey.getOrDefault(thread.threadKey(), List.of()).stream()
                            .sorted(
                                    Comparator.comparingLong(MemoryThreadEvent::eventSeq)
                                            .thenComparing(MemoryThreadEvent::eventKey))
                            .toList();
            ThreadEnrichmentDebouncer.Evaluation evaluation =
                    debouncer.evaluate(thread, threadEvents, now, options);
            if (!evaluation.eligible()) {
                continue;
            }

            Mono<List<ThreadEnrichmentResult>> enrichment =
                    assistant.enrich(thread, threadEvents);
            if (hasPositiveTimeout(options.timeout())) {
                enrichment = enrichment.timeout(options.timeout(), Mono.just(List.of()));
            }

            List<ThreadEnrichmentResult> proposed =
                    enrichment.onErrorResume(
                                    error -> {
                                        log.warn(
                                                "Thread enrichment assistant failed open for memoryId={} threadKey={}",
                                                memoryId,
                                                thread.threadKey(),
                                                error);
                                        return Mono.just(List.of());
                                    })
                            .blockOptional()
                            .orElse(List.of());
            if (proposed.isEmpty()) {
                continue;
            }

            List<MemoryThreadEnrichmentInput> inputs =
                    toAuthoritativeInputs(
                            memoryId,
                            thread,
                            threadEvents,
                            cutoffItemId,
                            materializationPolicyVersion,
                            evaluation.meaningfulEventCount(),
                            now,
                            proposed);
            if (inputs.isEmpty()) {
                continue;
            }

            try {
                ThreadEnrichmentAppendResult appendResult =
                        inputStore.appendRunAndEnqueueReplay(memoryId, cutoffItemId, inputs);
                if (appendResult == ThreadEnrichmentAppendResult.INSERTED) {
                    replayScheduler.scheduleReplay(memoryId, cutoffItemId);
                }
            } catch (RuntimeException error) {
                log.warn(
                        "Thread enrichment acceptance failed open for memoryId={} threadKey={} cutoff={}",
                        memoryId,
                        thread.threadKey(),
                        cutoffItemId,
                        error);
            }
        }
    }

    private List<MemoryThreadEnrichmentInput> toAuthoritativeInputs(
            MemoryId memoryId,
            MemoryThreadProjection thread,
            List<MemoryThreadEvent> itemBackedEvents,
            long cutoffItemId,
            String materializationPolicyVersion,
            long meaningfulEventCount,
            Instant now,
            List<ThreadEnrichmentResult> proposedResults) {
        Set<String> itemBackedEventKeys =
                itemBackedEvents.stream().map(MemoryThreadEvent::eventKey).collect(Collectors.toSet());
        String inputRunKey =
                deterministicRunKey(
                        thread.threadKey(),
                        cutoffItemId,
                        meaningfulEventCount,
                        materializationPolicyVersion);
        List<ThreadEnrichmentResult> ordered =
                proposedResults.stream().sorted(RESULT_ORDER).toList();

        java.util.ArrayList<MemoryThreadEnrichmentInput> inputs = new java.util.ArrayList<>();
        int entrySeq = 0;
        for (ThreadEnrichmentResult result : ordered) {
            MemoryThreadEvent basisEvent = itemBackedEvents.stream()
                    .filter(event -> Objects.equals(event.eventKey(), result.basisEventKey()))
                    .findFirst()
                    .orElse(null);
            if (!itemBackedEventKeys.contains(result.basisEventKey()) || basisEvent == null) {
                continue;
            }
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>(result.payloadJson());
            payload.put("eventType", result.eventType());
            payload.put("meaningful", result.meaningful());
            payload.put("basisEventKey", result.basisEventKey());

            LinkedHashMap<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("sourceType", "THREAD_LLM");
            provenance.put("threadKey", thread.threadKey());
            List<Long> supportingItemIds = supportingItemIds(basisEvent);
            if (!supportingItemIds.isEmpty()) {
                provenance.put("supportingItemIds", supportingItemIds);
            }

            inputs.add(
                    new MemoryThreadEnrichmentInput(
                            memoryId.toIdentifier(),
                            thread.threadKey(),
                            inputRunKey,
                            entrySeq++,
                            cutoffItemId,
                            meaningfulEventCount,
                            materializationPolicyVersion,
                            Map.copyOf(payload),
                            Map.copyOf(provenance),
                            now));
        }
        return List.copyOf(inputs);
    }

    private static boolean hasPositiveTimeout(java.time.Duration timeout) {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }

    private static List<Long> supportingItemIds(MemoryThreadEvent basisEvent) {
        Object sources = basisEvent.eventPayloadJson().get("sources");
        if (!(sources instanceof List<?> sourceList)) {
            return List.of();
        }
        java.util.ArrayList<Long> itemIds = new java.util.ArrayList<>();
        for (Object source : sourceList) {
            if (!(source instanceof Map<?, ?> sourceMap)) {
                continue;
            }
            if (!"ITEM".equals(String.valueOf(sourceMap.get("sourceType")))) {
                continue;
            }
            Object itemId = sourceMap.get("itemId");
            if (itemId instanceof Number number) {
                itemIds.add(number.longValue());
            }
        }
        return List.copyOf(itemIds);
    }

    static String deterministicRunKey(
            String threadKey,
            long basisCutoffItemId,
            long basisMeaningfulEventCount,
            String materializationPolicyVersion) {
        return threadKey
                + "|"
                + basisCutoffItemId
                + "|"
                + basisMeaningfulEventCount
                + "|"
                + materializationPolicyVersion;
    }

    private static String canonicalJson(Map<String, Object> value) {
        try {
            return JsonUtils.newMapper()
                    .writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value == null ? Map.of() : value);
        } catch (tools.jackson.core.JacksonException error) {
            throw new IllegalStateException("Failed to canonicalize enrichment payload JSON", error);
        }
    }
}
