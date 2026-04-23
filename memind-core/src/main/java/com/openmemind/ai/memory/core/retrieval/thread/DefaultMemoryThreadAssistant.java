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
package com.openmemind.ai.memory.core.retrieval.thread;

import com.openmemind.ai.memory.core.builder.MemoryThreadLifecycleOptions;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Deterministic retrieval-time thread assist over persisted projection data.
 */
public final class DefaultMemoryThreadAssistant implements MemoryThreadAssistant {

    private final ThreadAssistSeedResolver seedResolver;
    private final ThreadAssistThreadRanker threadRanker;
    private final ThreadAssistMemberRanker memberRanker;

    public DefaultMemoryThreadAssistant(MemoryStore store) {
        this(store, MemoryThreadLifecycleOptions.defaults().dormantAfter());
    }

    public DefaultMemoryThreadAssistant(MemoryStore store, Duration dormantAfter) {
        this(
                store,
                dormantAfter,
                Clock.systemUTC(),
                new ThreadAssistSeedResolver(),
                new ThreadAssistThreadRanker(store, dormantAfter, Clock.systemUTC()),
                new ThreadAssistMemberRanker(store));
    }

    DefaultMemoryThreadAssistant(MemoryStore store, Duration dormantAfter, Clock clock) {
        this(
                store,
                dormantAfter,
                clock,
                new ThreadAssistSeedResolver(),
                new ThreadAssistThreadRanker(store, dormantAfter, clock),
                new ThreadAssistMemberRanker(store));
    }

    DefaultMemoryThreadAssistant(
            MemoryStore store,
            Duration dormantAfter,
            Clock clock,
            ThreadAssistSeedResolver seedResolver,
            ThreadAssistThreadRanker threadRanker,
            ThreadAssistMemberRanker memberRanker) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(dormantAfter, "dormantAfter");
        Objects.requireNonNull(clock, "clock");
        this.seedResolver = Objects.requireNonNull(seedResolver, "seedResolver");
        this.threadRanker = Objects.requireNonNull(threadRanker, "threadRanker");
        this.memberRanker = Objects.requireNonNull(memberRanker, "memberRanker");
    }

    @Override
    public Mono<MemoryThreadAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            RetrievalMemoryThreadSettings settings,
            List<ScoredResult> directWindow) {
        boolean enabled = settings != null && settings.enabled();
        if (!enabled || directWindow == null || directWindow.isEmpty()) {
            return Mono.just(MemoryThreadAssistResult.directOnly(directWindow, enabled));
        }

        Mono<MemoryThreadAssistResult> deterministic =
                Mono.fromCallable(() -> deterministicAssist(context, settings, directWindow))
                        .subscribeOn(Schedulers.boundedElastic());
        if (settings.timeout() != null
                && !settings.timeout().isZero()
                && !settings.timeout().isNegative()) {
            deterministic =
                    deterministic.timeout(
                            settings.timeout(),
                            Mono.just(MemoryThreadAssistResult.degraded(directWindow, true, true)));
        }
        return deterministic.onErrorResume(
                error -> Mono.just(MemoryThreadAssistResult.degraded(directWindow, true, false)));
    }

    private MemoryThreadAssistResult deterministicAssist(
            QueryContext context,
            RetrievalMemoryThreadSettings settings,
            List<ScoredResult> directWindow) {
        List<ScoredResult> directItems = ThreadAssistSeedResolver.directItems(directWindow);
        List<ScoredResult> seeds = seedResolver.seeds(directWindow);
        if (seeds.isEmpty()) {
            return MemoryThreadAssistResult.directOnly(directWindow, true);
        }

        ThreadAssistThreadRanker.RankOutcome rankOutcome =
                threadRanker.rank(context, seeds, settings);
        if (rankOutcome.rankedThreads().isEmpty()) {
            return new MemoryThreadAssistResult(
                    directWindow,
                    MemoryThreadAssistResult.Stats.success(
                            rankOutcome.seedThreadCount(), 0, 0, false));
        }

        List<ThreadAssistThreadRanker.RankedThread> selectedThreads =
                rankOutcome.rankedThreads().subList(
                        0, Math.min(settings.maxThreads(), rankOutcome.rankedThreads().size()));
        List<ScoredResult> admittedMembers =
                selectedThreads.stream()
                        .flatMap(
                                rankedThread ->
                                        memberRanker
                                                .admit(
                                                        context,
                                                        rankedThread,
                                                        directItems,
                                                        settings.maxMembersPerThread())
                                                .stream())
                        .toList();

        int pinned = Math.min(settings.protectDirectTopK(), directWindow.size());
        List<ScoredResult> combined = new java.util.ArrayList<>(directWindow.size() + admittedMembers.size());
        Set<String> usedKeys = new LinkedHashSet<>();
        for (ScoredResult pinnedDirect : directWindow.subList(0, pinned)) {
            combined.add(pinnedDirect);
            usedKeys.add(pinnedDirect.dedupKey());
        }
        for (ScoredResult admittedMember : admittedMembers) {
            if (usedKeys.add(admittedMember.dedupKey())) {
                combined.add(admittedMember);
            }
        }
        for (ScoredResult direct : directWindow.subList(pinned, directWindow.size())) {
            if (usedKeys.add(direct.dedupKey())) {
                combined.add(direct);
            }
        }

        return new MemoryThreadAssistResult(
                        combined,
                        MemoryThreadAssistResult.Stats.success(
                                rankOutcome.seedThreadCount(),
                                rankOutcome.rankedThreads().size(),
                                admittedMembers.size(),
                                false))
                .reboundTo(directWindow.size(), pinned);
    }

}
