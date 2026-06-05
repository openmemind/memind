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
package com.openmemind.ai.memory.core.retrieval;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.admission.DefaultRetrievalAdmissionPolicy;
import com.openmemind.ai.memory.core.retrieval.admission.RetrievalAdmissionDecision;
import com.openmemind.ai.memory.core.retrieval.admission.RetrievalAdmissionOptions;
import com.openmemind.ai.memory.core.retrieval.admission.RetrievalAdmissionPolicy;
import com.openmemind.ai.memory.core.retrieval.admission.RetrievalAdmissionResult;
import com.openmemind.ai.memory.core.retrieval.query.LongQueryCondenser;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.query.QueryRewriter;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default Memory Retriever
 *
 * <p>Orchestration process:
 * Query rewriting -> Strategy dispatch -> Timeout protection -> Error fallback
 *
 */
public class DefaultMemoryRetriever implements MemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryRetriever.class);

    private final Map<String, RetrievalStrategy> strategies;
    private final MemoryStore memoryStore;
    private final MemoryTextSearch textSearch; // nullable
    private final QueryRewriter queryRewriter; // nullable
    private final RetrievalAdmissionPolicy admissionPolicy;
    private final RetrievalAdmissionOptions admissionOptions;
    private final LongQueryCondenser longQueryCondenser; // nullable for legacy constructors

    public DefaultMemoryRetriever(MemoryStore memoryStore) {
        this(memoryStore, null, null);
    }

    public DefaultMemoryRetriever(MemoryStore memoryStore, MemoryTextSearch textSearch) {
        this(memoryStore, textSearch, null);
    }

    public DefaultMemoryRetriever(
            MemoryStore memoryStore, MemoryTextSearch textSearch, QueryRewriter queryRewriter) {
        this(
                memoryStore,
                textSearch,
                queryRewriter,
                defaultAdmissionPolicy(),
                defaultAdmissionOptions(),
                null);
    }

    public DefaultMemoryRetriever(
            MemoryStore memoryStore,
            MemoryTextSearch textSearch,
            QueryRewriter queryRewriter,
            LongQueryCondenser longQueryCondenser) {
        this(
                memoryStore,
                textSearch,
                queryRewriter,
                defaultAdmissionPolicy(),
                defaultAdmissionOptions(),
                longQueryCondenser);
    }

    public DefaultMemoryRetriever(
            MemoryStore memoryStore,
            MemoryTextSearch textSearch,
            QueryRewriter queryRewriter,
            RetrievalAdmissionPolicy admissionPolicy,
            RetrievalAdmissionOptions admissionOptions,
            LongQueryCondenser longQueryCondenser) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.textSearch = textSearch; // nullable
        this.queryRewriter = queryRewriter; // nullable
        this.admissionPolicy =
                Objects.requireNonNull(admissionPolicy, "admissionPolicy must not be null");
        this.admissionOptions =
                Objects.requireNonNull(admissionOptions, "admissionOptions must not be null");
        this.longQueryCondenser = longQueryCondenser;
        this.strategies = new ConcurrentHashMap<>();
    }

    private static RetrievalAdmissionOptions defaultAdmissionOptions() {
        return RetrievalAdmissionOptions.defaults();
    }

    private static RetrievalAdmissionPolicy defaultAdmissionPolicy() {
        return new DefaultRetrievalAdmissionPolicy(defaultAdmissionOptions());
    }

    @Override
    public void registerStrategy(RetrievalStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        strategies.put(strategy.name(), strategy);
        log.debug("Registered strategy: {}", strategy.name());
    }

    @Override
    public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.memoryId(), "memoryId must not be null");

        RetrievalConfig config = request.config();
        if (config == null) {
            throw new IllegalArgumentException(
                    "RetrievalRequest.config must not be null — use RetrievalRequest.of(memoryId,"
                            + " query, strategy)");
        }

        RetrievalAdmissionResult admission = admissionPolicy.evaluate(request);
        if (admission.decision() == RetrievalAdmissionDecision.SKIP
                || admission.decision() == RetrievalAdmissionDecision.REJECT) {
            logAdmissionRejection(admission);
            return Mono.just(RetrievalResult.empty(config.strategyName(), request.query()));
        }

        if (!memoryStore.itemOperations().hasItems(request.memoryId())) {
            log.debug(
                    "No memory entries, skipping retrieval process: memoryId={}",
                    request.memoryId().toIdentifier());
            return Mono.just(RetrievalResult.empty(config.strategyName(), request.query()));
        }

        return condenseAdmittedQuery(request, admission)
                .flatMap(
                        outcome ->
                                outcome.<Mono<RetrievalResult>>map(
                                                admitted ->
                                                        retrieveAdmitted(request, config, admitted))
                                        .orElseGet(
                                                () ->
                                                        Mono.just(
                                                                RetrievalResult.empty(
                                                                        config.strategyName(),
                                                                        request.query()))));
    }

    private Mono<Optional<AdmissionOutcome>> condenseAdmittedQuery(
            RetrievalRequest request, RetrievalAdmissionResult admission) {
        if (admission.decision() == RetrievalAdmissionDecision.ADMIT) {
            return Mono.just(Optional.of(new AdmissionOutcome(request.query(), false)));
        }
        if (admission.decision() != RetrievalAdmissionDecision.QUERY_TOO_LONG) {
            return Mono.just(Optional.empty());
        }
        if (longQueryCondenser == null) {
            log.warn(
                    "Retrieval admission QUERY_TOO_LONG has no LongQueryCondenser wired;"
                            + " this should only happen for legacy direct retriever construction");
            return Mono.just(Optional.empty());
        }
        return longQueryCondenser
                .condense(
                        request.memoryId(),
                        request.query(),
                        safeConversationHistory(request.conversationHistory()),
                        admissionOptions.maxQueryTokens())
                .flatMap(
                        condensed -> {
                            RetrievalRequest condensedRequest =
                                    new RetrievalRequest(
                                            request.memoryId(),
                                            condensed,
                                            safeConversationHistory(request.conversationHistory()),
                                            request.config(),
                                            request.metadata(),
                                            request.scope(),
                                            request.categories());
                            RetrievalAdmissionResult condensedAdmission =
                                    admissionPolicy.evaluate(condensedRequest);
                            if (condensedAdmission.decision() == RetrievalAdmissionDecision.ADMIT) {
                                return Mono.just(
                                        Optional.of(new AdmissionOutcome(condensed, true)));
                            }
                            log.debug(
                                    "Condensed query rejected by admission: decision={}, reason={},"
                                            + " chars={}, tokens={}",
                                    condensedAdmission.decision(),
                                    condensedAdmission.reason(),
                                    condensedAdmission.charCount(),
                                    condensedAdmission.tokenCount());
                            return Mono.just(Optional.<AdmissionOutcome>empty());
                        })
                .switchIfEmpty(
                        Mono.fromSupplier(
                                () -> {
                                    log.debug(
                                            "Retrieval admission QUERY_TOO_LONG could not be"
                                                    + " condensed: reason={}, chars={}, tokens={},"
                                                    + " maxChars={}, maxTokens={}",
                                            admission.reason(),
                                            admission.charCount(),
                                            admission.tokenCount(),
                                            admissionOptions.maxQueryChars(),
                                            admissionOptions.maxQueryTokens());
                                    return Optional.empty();
                                }));
    }

    private Mono<RetrievalResult> retrieveAdmitted(
            RetrievalRequest request, RetrievalConfig config, AdmissionOutcome outcome) {
        String effectiveQuery = outcome.effectiveQuery();
        // 2. Query rewriting
        Mono<String> rewrittenMono;
        var conversationHistory = safeConversationHistory(request.conversationHistory());
        if (!outcome.condensed() && queryRewriter != null && !conversationHistory.isEmpty()) {
            rewrittenMono =
                    queryRewriter
                            .rewrite(request.memoryId(), effectiveQuery, conversationHistory)
                            .onErrorResume(
                                    e -> {
                                        log.warn("Query rewriting failed, using original query", e);
                                        return Mono.just(effectiveQuery);
                                    });
        } else {
            rewrittenMono = Mono.just(effectiveQuery);
        }

        return rewrittenMono.flatMap(
                rewritten -> {
                    // 3. Build QueryContext
                    QueryContext context =
                            new QueryContext(
                                    request.memoryId(),
                                    request.query(),
                                    rewritten.equals(request.query()) ? null : rewritten,
                                    conversationHistory,
                                    request.metadata(),
                                    request.scope(),
                                    request.categories());

                    // 4. Strategy dispatch
                    RetrievalStrategy strategy = strategies.get(config.strategyName());
                    if (strategy == null) {
                        var availableStrategies = strategies.keySet().stream().sorted().toList();
                        log.warn(
                                "Strategy not found: requested={}, available={}",
                                config.strategyName(),
                                availableStrategies);
                        return Mono.error(
                                new IllegalStateException(
                                        "No retrieval strategy registered for '"
                                                + config.strategyName()
                                                + "'. Registered strategies: "
                                                + availableStrategies));
                    }

                    return strategy.retrieve(context, config)
                            .timeout(config.timeout())
                            .onErrorResume(
                                    e -> {
                                        log.warn(
                                                "Retrieval failed, returning degraded result:"
                                                        + " query={}",
                                                request.query(),
                                                e);
                                        return Mono.just(
                                                RetrievalResult.degraded(
                                                        config.strategyName(), effectiveQuery));
                                    });
                });
    }

    private static List<String> safeConversationHistory(List<String> conversationHistory) {
        return conversationHistory == null ? List.of() : conversationHistory;
    }

    private static void logAdmissionRejection(RetrievalAdmissionResult admission) {
        log.debug(
                "Retrieval admission {}: reason={}, chars={}, tokens={}",
                admission.decision(),
                admission.reason(),
                admission.charCount(),
                admission.tokenCount());
    }

    private record AdmissionOutcome(String effectiveQuery, boolean condensed) {}

    @Override
    public void onDataChanged(MemoryId memoryId) {
        if (memoryId != null) {
            if (textSearch != null) {
                textSearch.invalidate(memoryId);
            }
            strategies.values().forEach(s -> s.onDataChanged(memoryId));
            log.debug("Retrieval data change notification handled: memoryId={}", memoryId);
        }
    }
}
