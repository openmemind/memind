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
package com.openmemind.ai.memory.core;

import com.openmemind.ai.memory.core.buffer.ConversationBufferLocks;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.RerankOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThreadRuntimeStatus;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.context.ContextWindow;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.thread.MemoryThreadLayer;
import com.openmemind.ai.memory.core.extraction.thread.ThreadMaterializationPolicyFactory;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistConfigMapper;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Memory default implementation
 *
 * <p>Internally combines MemoryExtractor + MemoryRetriever + MemoryStore,
 * unifying all memory operations into a single facade.
 *
 */
public class DefaultMemory implements Memory {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemory.class);

    private final MemoryExtractor extractor;
    private final MemoryRetriever retriever;
    private final MemoryStore memoryStore;
    private final MemoryBuffer memoryBuffer;
    private final MemoryVector vector;
    private final InsightLayer insightLayer;
    private final AutoCloseable lifecycle;
    private final MemoryBuildOptions buildOptions;
    private final MemoryThreadLayer memoryThreadLayer;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultMemory(
            MemoryExtractor extractor,
            MemoryRetriever retriever,
            MemoryStore memoryStore,
            MemoryBuffer memoryBuffer,
            MemoryVector vector,
            InsightLayer insightLayer,
            AutoCloseable lifecycle,
            MemoryBuildOptions buildOptions) {
        this(
                extractor,
                retriever,
                memoryStore,
                memoryBuffer,
                vector,
                insightLayer,
                lifecycle,
                buildOptions,
                null);
    }

    public DefaultMemory(
            MemoryExtractor extractor,
            MemoryRetriever retriever,
            MemoryStore memoryStore,
            MemoryBuffer memoryBuffer,
            MemoryVector vector,
            InsightLayer insightLayer,
            AutoCloseable lifecycle,
            MemoryBuildOptions buildOptions,
            MemoryThreadLayer memoryThreadLayer) {
        this.extractor = Objects.requireNonNull(extractor, "extractor must not be null");
        this.retriever = Objects.requireNonNull(retriever, "retriever must not be null");
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.memoryBuffer = Objects.requireNonNull(memoryBuffer, "memoryBuffer must not be null");
        this.vector = Objects.requireNonNull(vector, "vector must not be null");
        this.insightLayer = insightLayer;
        this.lifecycle = lifecycle;
        this.buildOptions = Objects.requireNonNull(buildOptions, "buildOptions must not be null");
        this.memoryThreadLayer = memoryThreadLayer;
    }

    // ===== Generic extraction =====

    @Override
    public Mono<ExtractionResult> extract(ExtractionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var config =
                ExtractionConfig.defaults().equals(request.config())
                        ? defaultExtractionConfig()
                        : request.config();
        return extractor.extract(request.withConfig(config));
    }

    @Override
    public Mono<ExtractionResult> extract(MemoryId memoryId, RawContent content) {
        return extract(memoryId, content, defaultExtractionConfig());
    }

    @Override
    public Mono<ExtractionResult> extract(
            MemoryId memoryId, RawContent content, ExtractionConfig config) {
        var request = ExtractionRequest.of(memoryId, content).withConfig(config);
        return extractor.extract(request);
    }

    // ===== Dialogue memory extraction =====

    @Override
    public Mono<ExtractionResult> addMessages(MemoryId memoryId, List<Message> messages) {
        return extract(memoryId, new ConversationContent(messages));
    }

    @Override
    public Mono<ExtractionResult> addMessages(
            MemoryId memoryId, List<Message> messages, ExtractionConfig config) {
        return extract(memoryId, new ConversationContent(messages), config);
    }

    @Override
    public Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message) {
        return extractor.addMessage(memoryId, message, defaultExtractionConfig());
    }

    @Override
    public Mono<ExtractionResult> addMessage(
            MemoryId memoryId, Message message, ExtractionConfig config) {
        return extractor.addMessage(memoryId, message, config);
    }

    // ===== Context =====

    @Override
    public Mono<ContextWindow> getContext(ContextRequest request) {
        Objects.requireNonNull(request, "request is required");

        RecentConversationBuffer recentConversationBuffer = memoryBuffer.recentConversationBuffer();
        var bufferKey = request.memoryId().toIdentifier();
        List<Message> messages =
                trimRecentMessages(
                        recentConversationBuffer.loadRecent(
                                bufferKey, request.recentMessageLimit()),
                        request.maxTokens());
        int bufferTokens = TokenUtils.countTokens(messages);

        if (!request.includeMemories() || messages.isEmpty()) {
            return Mono.just(ContextWindow.bufferOnly(messages, bufferTokens));
        }

        String query = buildQueryFromRecentMessages(messages);
        return retriever
                .retrieve(
                        new RetrievalRequest(
                                request.memoryId(),
                                query,
                                List.of(),
                                defaultRetrievalConfig(request.strategy()),
                                Map.of(),
                                null,
                                null))
                .map(
                        memories -> {
                            int memoriesTokens = TokenUtils.countTokens(memories.formattedResult());
                            return new ContextWindow(
                                    messages, memories, bufferTokens + memoriesTokens);
                        });
    }

    @Override
    public Mono<ExtractionResult> commit(MemoryId memoryId) {
        return commit(memoryId, defaultExtractionConfig());
    }

    @Override
    public Mono<ExtractionResult> commit(MemoryId memoryId, ExtractionConfig config) {
        return commit(memoryId, config, null);
    }

    @Override
    public Mono<ExtractionResult> commit(MemoryId memoryId, String sourceClient) {
        return commit(memoryId, defaultExtractionConfig(), sourceClient);
    }

    @Override
    public Mono<ExtractionResult> commit(
            MemoryId memoryId, ExtractionConfig config, String sourceClient) {
        Objects.requireNonNull(memoryId, "memoryId is required");
        Objects.requireNonNull(config, "config is required");

        PendingConversationBuffer pendingConversationBuffer =
                memoryBuffer.pendingConversationBuffer();
        var bufferKey = memoryId.toIdentifier();
        List<Message> messages =
                ConversationBufferLocks.withLock(
                        bufferKey, () -> List.copyOf(pendingConversationBuffer.drain(bufferKey)));

        if (messages.isEmpty()) {
            return Mono.just(
                    ExtractionResult.success(
                            memoryId,
                            com.openmemind.ai.memory.core.extraction.result.RawDataResult.empty(),
                            com.openmemind.ai.memory.core.extraction.result.MemoryItemResult
                                    .empty(),
                            com.openmemind.ai.memory.core.extraction.result.InsightResult.empty(),
                            Duration.ZERO));
        }

        if (sourceClient != null && !sourceClient.isBlank()) {
            messages = applySourceClient(messages, sourceClient);
        }
        return extract(memoryId, new ConversationContent(messages), config);
    }

    private static String buildQueryFromRecentMessages(List<Message> messages) {
        int tail = Math.min(messages.size(), 5);
        return messages.subList(messages.size() - tail, messages.size()).stream()
                .map(Message::textContent)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "));
    }

    private static List<Message> applySourceClient(List<Message> messages, String sourceClient) {
        return messages.stream()
                .map(
                        message ->
                                message.sourceClient() == null
                                        ? message.withSourceClient(sourceClient)
                                        : message)
                .toList();
    }

    private static List<Message> trimRecentMessages(List<Message> messages, int maxTokens) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (TokenUtils.countTokens(messages) <= maxTokens) {
            return List.copyOf(messages);
        }

        List<Message> retained = new ArrayList<>();
        int totalTokens = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            int messageTokens = TokenUtils.countTokens(List.of(message));
            if (!retained.isEmpty() && totalTokens + messageTokens > maxTokens) {
                break;
            }
            retained.add(message);
            totalTokens += messageTokens;
        }

        Collections.reverse(retained);
        return List.copyOf(retained);
    }

    // ===== Memory retrieval =====

    @Override
    public Mono<RetrievalResult> retrieve(
            MemoryId memoryId, String query, RetrievalConfig.Strategy strategy) {
        return retriever.retrieve(
                new RetrievalRequest(
                        memoryId,
                        query,
                        List.of(),
                        defaultRetrievalConfig(strategy),
                        Map.of(),
                        null,
                        null));
    }

    @Override
    public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
        return retriever.retrieve(request);
    }

    private ExtractionConfig defaultExtractionConfig() {
        var extraction = buildOptions.extraction();
        return new ExtractionConfig(
                extraction.insight().enabled(),
                extraction.common().defaultScope(),
                extraction.item().foresightEnabled(),
                extraction.common().timeout(),
                extraction.common().language());
    }

    private RetrievalConfig defaultRetrievalConfig(RetrievalConfig.Strategy strategy) {
        var retrieval = buildOptions.retrieval();
        return switch (strategy) {
            case SIMPLE -> {
                var graph = retrieval.simple().graphAssist();
                var temporal = retrieval.simple().temporalRetrieval();
                var base =
                        RetrievalConfig.simple(
                                new SimpleStrategyConfig(
                                        retrieval.simple().keywordSearchEnabled(),
                                        new SimpleStrategyConfig.TemporalRetrievalConfig(
                                                temporal.enabled(),
                                                temporal.maxWindowCandidates(),
                                                temporal.channelWeight(),
                                                temporal.timeout()),
                                        new SimpleStrategyConfig.GraphAssistConfig(
                                                graph.enabled(),
                                                graph.mode(),
                                                graph.maxSeedItems(),
                                                graph.maxExpandedItems(),
                                                graph.maxSemanticNeighborsPerSeed(),
                                                graph.maxTemporalNeighborsPerSeed(),
                                                graph.maxCausalNeighborsPerSeed(),
                                                graph.maxEntitySiblingItemsPerSeed(),
                                                graph.maxItemsPerEntity(),
                                                graph.graphChannelWeight(),
                                                graph.minLinkStrength(),
                                                graph.minMentionConfidence(),
                                                graph.protectDirectTopK(),
                                                graph.semanticEvidenceDecayFactor(),
                                                graph.timeout()),
                                        MemoryThreadAssistConfigMapper.toSimpleConfig(
                                                buildOptions)));
                yield applyCache(
                        base.withTier1(copyTier(base.tier1(), retrieval.simple().insightTopK()))
                                .withTier2(copyTier(base.tier2(), retrieval.simple().itemTopK()))
                                .withTier3(copyTier(base.tier3(), retrieval.simple().rawDataTopK()))
                                .withScoring(retrieval.advanced().scoring())
                                .withTimeout(retrieval.simple().timeout()),
                        retrieval.common().cacheEnabled());
            }
            case DEEP -> {
                var base = RetrievalConfig.deep();
                var baseStrategy = (DeepStrategyConfig) base.strategyConfig();
                var graph = retrieval.deep().graphAssist();
                var strategyConfig =
                        new DeepStrategyConfig(
                                new DeepStrategyConfig.QueryExpansionConfig(
                                        retrieval.deep().queryExpansion().maxExpandedQueries()),
                                new DeepStrategyConfig.SufficiencyConfig(
                                        retrieval.deep().sufficiency().itemTopK()),
                                baseStrategy.tier2InitTopK(),
                                baseStrategy.bm25InitTopK(),
                                baseStrategy.minScore(),
                                new DeepStrategyConfig.GraphAssistConfig(
                                        graph.enabled(),
                                        graph.mode(),
                                        graph.maxSeedItems(),
                                        graph.maxExpandedItems(),
                                        graph.maxSemanticNeighborsPerSeed(),
                                        graph.maxTemporalNeighborsPerSeed(),
                                        graph.maxCausalNeighborsPerSeed(),
                                        graph.maxEntitySiblingItemsPerSeed(),
                                        graph.maxItemsPerEntity(),
                                        graph.graphChannelWeight(),
                                        graph.minLinkStrength(),
                                        graph.minMentionConfidence(),
                                        graph.protectDirectTopK(),
                                        graph.semanticEvidenceDecayFactor(),
                                        graph.timeout()),
                                MemoryThreadAssistConfigMapper.toDeepConfig(buildOptions));
                var tier3 =
                        retrieval.deep().rawDataEnabled()
                                ? new RetrievalConfig.TierConfig(
                                        true,
                                        retrieval.deep().rawDataTopK(),
                                        base.tier3().minScore(),
                                        base.tier3().truncation())
                                : RetrievalConfig.TierConfig.disabled();
                yield applyCache(
                        RetrievalConfig.deep(strategyConfig)
                                .withTier1(copyTier(base.tier1(), retrieval.deep().insightTopK()))
                                .withTier2(copyTier(base.tier2(), retrieval.deep().itemTopK()))
                                .withTier3(tier3)
                                .withRerank(toRerankConfig(retrieval.advanced().rerank()))
                                .withScoring(retrieval.advanced().scoring())
                                .withTimeout(retrieval.deep().timeout()),
                        retrieval.common().cacheEnabled());
            }
        };
    }

    private RetrievalConfig applyCache(RetrievalConfig config, boolean enabled) {
        return enabled ? config : config.withoutCache();
    }

    private RetrievalConfig.TierConfig copyTier(RetrievalConfig.TierConfig base, int topK) {
        return new RetrievalConfig.TierConfig(
                base.enabled(), topK, base.minScore(), base.truncation());
    }

    private RetrievalConfig.RerankConfig toRerankConfig(RerankOptions options) {
        return switch (options.mode()) {
            case DISABLED -> RetrievalConfig.RerankConfig.disabled();
            case PURE -> RetrievalConfig.RerankConfig.pure(options.topK());
            case BLEND ->
                    new RetrievalConfig.RerankConfig(
                            true,
                            true,
                            options.top3Weight(),
                            options.top10Weight(),
                            options.otherWeight(),
                            options.topK());
        };
    }

    @Override
    public Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
        var requestedIds = List.copyOf(itemIds);
        if (requestedIds.isEmpty()) {
            return Mono.<Void>empty();
        }

        return Mono.fromCallable(
                        () -> memoryStore.itemOperations().getItemsByIds(memoryId, requestedIds))
                .map(
                        items ->
                                items.stream()
                                        .map(MemoryItem::vectorId)
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .toList())
                .flatMap(
                        vectorIds ->
                                vectorIds.isEmpty()
                                        ? Mono.<Void>empty()
                                        : vector.deleteBatch(memoryId, vectorIds))
                .then(
                        Mono.fromRunnable(
                                () ->
                                        memoryStore
                                                .threadOperations()
                                                .markRebuildRequired(memoryId, "item deletion")))
                .then(
                        Mono.<Void>fromRunnable(
                                () ->
                                        memoryStore
                                                .itemOperations()
                                                .deleteItems(memoryId, requestedIds)))
                .doOnSuccess(ignored -> retriever.onDataChanged(memoryId));
    }

    @Override
    public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
        var requestedIds = List.copyOf(insightIds);
        if (requestedIds.isEmpty()) {
            return Mono.<Void>empty();
        }

        return Mono.<Void>fromRunnable(
                        () ->
                                memoryStore
                                        .insightOperations()
                                        .deleteInsights(memoryId, requestedIds))
                .doOnSuccess(ignored -> retriever.onDataChanged(memoryId));
    }

    @Override
    public Mono<Void> invalidate(MemoryId memoryId) {
        return Mono.<Void>fromRunnable(() -> retriever.onDataChanged(memoryId));
    }

    @Override
    public void flushInsights(MemoryId memoryId, String language) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (insightLayer == null) {
            return;
        }
        if (language == null || language.isBlank()) {
            insightLayer.flush(memoryId);
            return;
        }
        insightLayer.flush(memoryId, language);
    }

    @Override
    public void flushMemoryThreads(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (memoryThreadLayer == null) {
            return;
        }
        memoryThreadLayer.flush(memoryId);
    }

    @Override
    public void rebuildMemoryThreads(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (memoryThreadLayer == null) {
            return;
        }
        memoryThreadLayer.rebuild(memoryId);
    }

    @Override
    public MemoryThreadRuntimeStatus getThreadRuntimeStatus(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (memoryThreadLayer != null) {
            return memoryThreadLayer.getThreadRuntimeStatus(memoryId);
        }
        if (!buildOptions.memoryThread().enabled()) {
            return MemoryThreadRuntimeStatus.disabled("memoryThread disabled");
        }
        if (!buildOptions.memoryThread().derivation().enabled()) {
            return MemoryThreadRuntimeStatus.disabled("memoryThread derivation disabled");
        }
        memoryStore
                .threadOperations()
                .ensureRuntime(
                        memoryId,
                        ThreadMaterializationPolicyFactory.from(buildOptions.memoryThread())
                                .version());
        return MemoryThreadRuntimeStatus.fromRuntimeState(
                memoryStore.threadOperations().getRuntime(memoryId).orElse(null), true, true, null);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || lifecycle == null) {
            return;
        }

        try {
            lifecycle.close();
        } catch (Exception e) {
            log.warn("Failed to close memory lifecycle", e);
        }
    }
}
