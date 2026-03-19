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
import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.query.QueryRewriter;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default Memory Retriever
 *
 * <p>Orchestration process:
 * Cache check → Query rewriting → Strategy dispatch → Cache writing → Timeout protection → Error fallback
 *
 */
public class DefaultMemoryRetriever implements MemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryRetriever.class);

    private final Map<String, RetrievalStrategy> strategies;
    private final RetrievalCache cache;
    private final MemoryStore store;
    private final MemoryTextSearch textSearch; // nullable
    private final QueryRewriter queryRewriter; // nullable

    public DefaultMemoryRetriever(RetrievalCache cache, MemoryStore store) {
        this(cache, store, null, null);
    }

    public DefaultMemoryRetriever(
            RetrievalCache cache, MemoryStore store, MemoryTextSearch textSearch) {
        this(cache, store, textSearch, null);
    }

    public DefaultMemoryRetriever(
            RetrievalCache cache,
            MemoryStore store,
            MemoryTextSearch textSearch,
            QueryRewriter queryRewriter) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.textSearch = textSearch; // nullable
        this.queryRewriter = queryRewriter; // nullable
        this.strategies = new HashMap<>();
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

        if (request.query() == null || request.query().isBlank()) {
            return Mono.just(RetrievalResult.empty("none", ""));
        }

        RetrievalConfig config = request.config();
        if (config == null) {
            throw new IllegalArgumentException(
                    "RetrievalRequest.config must not be null — use RetrievalRequest.of(memoryId,"
                            + " query, strategy)");
        }

        if (!store.hasItems(request.memoryId())) {
            log.debug(
                    "No memory entries, skipping retrieval process: memoryId={}",
                    request.memoryId().toIdentifier());
            return Mono.just(RetrievalResult.empty(config.strategyName(), request.query()));
        }

        String queryHash = HashUtils.sha256(request.query());
        String configHash = HashUtils.sha256(config.toString());

        // 1. Cache check (using original query hash)
        if (config.enableCache()) {
            var cached = cache.get(request.memoryId(), queryHash, configHash);
            if (cached.isPresent()) {
                log.debug("Cache hit: query={}", request.query());
                return Mono.just(cached.get());
            }
        }

        // 2. Query rewriting
        Mono<String> rewrittenMono;
        if (queryRewriter != null
                && request.conversationHistory() != null
                && !request.conversationHistory().isEmpty()) {
            rewrittenMono =
                    queryRewriter
                            .rewrite(
                                    request.memoryId(),
                                    request.query(),
                                    request.conversationHistory())
                            .onErrorResume(
                                    e -> {
                                        log.warn("Query rewriting failed, using original query", e);
                                        return Mono.just(request.query());
                                    });
        } else {
            rewrittenMono = Mono.just(request.query());
        }

        return rewrittenMono.flatMap(
                rewritten -> {
                    // 3. Build QueryContext
                    QueryContext context =
                            new QueryContext(
                                    request.memoryId(),
                                    request.query(),
                                    rewritten.equals(request.query()) ? null : rewritten,
                                    request.conversationHistory(),
                                    request.metadata(),
                                    request.scope(),
                                    request.categories());

                    // 4. Strategy dispatch
                    RetrievalStrategy strategy = strategies.get(config.strategyName());
                    if (strategy == null) {
                        log.warn("Strategy not found: {}", config.strategyName());
                        return Mono.just(
                                RetrievalResult.empty(config.strategyName(), request.query()));
                    }

                    return strategy.retrieve(context, config)
                            // 5. Cache writing
                            .doOnSuccess(
                                    result -> {
                                        if (config.enableCache()
                                                && result != null
                                                && !result.isEmpty()) {
                                            cache.put(
                                                    request.memoryId(),
                                                    queryHash,
                                                    configHash,
                                                    result);
                                        }
                                    })
                            // 6. Timeout protection
                            .timeout(config.timeout())
                            // 7. Error fallback
                            .onErrorResume(
                                    e -> {
                                        log.warn(
                                                "Retrieval failed, returning empty result:"
                                                        + " query={}",
                                                request.query(),
                                                e);
                                        return Mono.just(
                                                RetrievalResult.empty(
                                                        config.strategyName(), request.query()));
                                    });
                });
    }

    @Override
    public void onDataChanged(MemoryId memoryId) {
        if (memoryId != null) {
            cache.invalidate(memoryId);
            if (textSearch != null) {
                textSearch.invalidate(memoryId);
            }
            // Notify strategy of internal cache invalidation
            strategies.values().forEach(s -> s.onDataChanged(memoryId));
            log.debug("Cache and index invalidated: memoryId={}", memoryId.toIdentifier());
        }
    }
}
