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
package com.openmemind.ai.memory.core.retrieval.temporal;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.retrieval.ItemRetrievalGuard;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.TemporalItemLookupMatch;
import com.openmemind.ai.memory.core.store.item.TemporalItemLookupRequest;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class DefaultTemporalItemChannel implements TemporalItemChannel {

    private static final Logger log = LoggerFactory.getLogger(DefaultTemporalItemChannel.class);

    private final MemoryStore memoryStore;

    public DefaultTemporalItemChannel(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public Mono<TemporalItemChannelResult> retrieve(
            QueryContext context,
            RetrievalConfig config,
            Optional<TemporalConstraint> temporalConstraint,
            TemporalItemChannelSettings settings) {
        var effectiveSettings =
                settings != null ? settings : TemporalItemChannelSettings.defaults();
        boolean constraintPresent = temporalConstraint != null && temporalConstraint.isPresent();
        if (!effectiveSettings.enabled() || !constraintPresent || memoryStore == null) {
            return Mono.just(
                    TemporalItemChannelResult.empty(
                            effectiveSettings.enabled(), constraintPresent));
        }

        var constraint = temporalConstraint.orElseThrow();
        return Mono.fromCallable(
                        () -> retrieveBlocking(context, config, constraint, effectiveSettings))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(shorterPositive(effectiveSettings.timeout(), config.timeout()))
                .onErrorResume(
                        TimeoutException.class,
                        error ->
                                Mono.just(
                                        TemporalItemChannelResult.degraded(
                                                effectiveSettings.enabled(), true)))
                .onErrorResume(
                        error -> {
                            log.warn("Simple: Temporal item channel failed", error);
                            return Mono.just(
                                    TemporalItemChannelResult.degraded(
                                            effectiveSettings.enabled(), true));
                        });
    }

    private TemporalItemChannelResult retrieveBlocking(
            QueryContext context,
            RetrievalConfig config,
            TemporalConstraint constraint,
            TemporalItemChannelSettings settings) {
        var request =
                new TemporalItemLookupRequest(
                        constraint.startInclusive(),
                        constraint.endExclusive(),
                        constraint.direction(),
                        context.scope(),
                        context.categories(),
                        Set.of(),
                        settings.maxWindowCandidates(),
                        Set.of());
        var matches =
                memoryStore.itemOperations().listTemporalItemMatches(context.memoryId(), request);
        var items =
                matches.stream()
                        .filter(match -> ItemRetrievalGuard.allows(match.item(), context))
                        .map(match -> toScoredResult(constraint, match))
                        .sorted(
                                Comparator.comparingDouble(ScoredResult::finalScore)
                                        .reversed()
                                        .thenComparingLong(
                                                result -> parseItemId(result.sourceId())))
                        .limit(config.tier2().topK())
                        .toList();
        return new TemporalItemChannelResult(
                items, settings.enabled(), true, false, matches.size());
    }

    private ScoredResult toScoredResult(
            TemporalConstraint constraint, TemporalItemLookupMatch match) {
        MemoryItem item = match.item();
        return new ScoredResult(
                ScoredResult.SourceType.ITEM,
                String.valueOf(item.id()),
                item.content(),
                0f,
                temporalProximity(constraint, match),
                match.anchor());
    }

    private double temporalProximity(TemporalConstraint constraint, TemporalItemLookupMatch match) {
        long windowMillis =
                Duration.between(constraint.startInclusive(), constraint.endExclusive()).toMillis();
        if (windowMillis <= 0L) {
            return 1.0d;
        }
        long midpoint = constraint.startInclusive().toEpochMilli() + windowMillis / 2L;
        long distance = Math.abs(match.anchor().toEpochMilli() - midpoint);
        double halfWindow = Math.max(1.0d, windowMillis / 2.0d);
        return Math.max(0.0d, 1.0d - Math.min(distance / halfWindow, 1.0d));
    }

    private Duration shorterPositive(Duration first, Duration second) {
        if (first == null || first.isZero() || first.isNegative()) {
            return second;
        }
        if (second == null || second.isZero() || second.isNegative()) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    private long parseItemId(String sourceId) {
        try {
            return Long.parseLong(sourceId);
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
