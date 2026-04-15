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
package com.openmemind.ai.memory.core.extraction.insight.scheduler;

import com.openmemind.ai.memory.core.buffer.BufferEntry;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupRouter;
import com.openmemind.ai.memory.core.extraction.insight.operation.PointOperationResolver;
import com.openmemind.ai.memory.core.extraction.insight.support.InsightPointIdentityManager;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.NoopMemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import com.openmemind.ai.memory.core.utils.IdUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.io.Closeable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Insight Build Scheduler
 *
 * <p>Multi-stage pipeline: Buffer → Group → Build Leaf → Tree Reorganize.
 * Asynchronously executed using virtual threads, serializing the same memoryId+insightTypeName.
 *
 */
public class InsightBuildScheduler implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(InsightBuildScheduler.class);

    private final InsightBuffer bufferStore;
    private final MemoryStore store;
    private final InsightGenerator generator;
    private final InsightGroupClassifier groupClassifier;
    private final InsightGroupRouter groupRouter;
    private final InsightTreeReorganizer treeReorganizer;
    private final MemoryVector memoryVector;
    private final IdUtils.SnowflakeIdGenerator idGenerator;
    private final InsightBuildConfig config;
    private final InsightPointIdentityManager pointIdentityManager;
    private final MemoryObserver observer;

    private final ExecutorService executor;
    private final Semaphore semaphore;

    /**
     * Fixed-size strip lock, bucketed by lockKey hash to avoid ConcurrentHashMap infinite growth
     */
    private static final int LOCK_STRIPES = 32;

    private final ReentrantLock[] lockStripes;

    {
        lockStripes = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            lockStripes[i] = new ReentrantLock();
        }
    }

    private volatile boolean closed = false;

    private record ActiveKey(MemoryId memoryId, String insightTypeName, String language) {}

    /**
     * Records all submitted (memoryId, insightTypeName, language) combinations, used for flush on close
     */
    private final Set<ActiveKey> activeKeys = ConcurrentHashMap.newKeySet();

    /**
     * Tracks in-flight pipeline futures for each memoryId, awaitPending is used to wait
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<CompletableFuture<Void>>>
            pendingFutures = new ConcurrentHashMap<>();

    public InsightBuildScheduler(
            InsightBuffer bufferStore,
            MemoryStore store,
            InsightGenerator generator,
            InsightGroupClassifier groupClassifier,
            InsightGroupRouter groupRouter,
            InsightTreeReorganizer treeReorganizer,
            MemoryVector memoryVector,
            IdUtils.SnowflakeIdGenerator idGenerator,
            InsightBuildConfig config) {
        this(
                bufferStore,
                store,
                generator,
                groupClassifier,
                groupRouter,
                treeReorganizer,
                memoryVector,
                idGenerator,
                config,
                new InsightPointIdentityManager(),
                null);
    }

    public InsightBuildScheduler(
            InsightBuffer bufferStore,
            MemoryStore store,
            InsightGenerator generator,
            InsightGroupClassifier groupClassifier,
            InsightGroupRouter groupRouter,
            InsightTreeReorganizer treeReorganizer,
            MemoryVector memoryVector,
            IdUtils.SnowflakeIdGenerator idGenerator,
            InsightBuildConfig config,
            MemoryObserver observer) {
        this(
                bufferStore,
                store,
                generator,
                groupClassifier,
                groupRouter,
                treeReorganizer,
                memoryVector,
                idGenerator,
                config,
                new InsightPointIdentityManager(),
                observer);
    }

    public InsightBuildScheduler(
            InsightBuffer bufferStore,
            MemoryStore store,
            InsightGenerator generator,
            InsightGroupClassifier groupClassifier,
            InsightGroupRouter groupRouter,
            InsightTreeReorganizer treeReorganizer,
            MemoryVector memoryVector,
            IdUtils.SnowflakeIdGenerator idGenerator,
            InsightBuildConfig config,
            InsightPointIdentityManager pointIdentityManager,
            MemoryObserver observer) {
        this.bufferStore = Objects.requireNonNull(bufferStore);
        this.store = Objects.requireNonNull(store);
        this.generator = Objects.requireNonNull(generator);
        this.groupClassifier = Objects.requireNonNull(groupClassifier);
        this.groupRouter = Objects.requireNonNull(groupRouter);
        this.treeReorganizer = treeReorganizer;
        this.memoryVector = memoryVector;
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.config = Objects.requireNonNull(config);
        this.pointIdentityManager = Objects.requireNonNull(pointIdentityManager);
        this.observer = observer != null ? observer : new NoopMemoryObserver();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.semaphore = new Semaphore(config.concurrency());
    }

    /**
     * Submit build task (fire-and-forget)
     */
    public void submit(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        if (closed) {
            log.warn(
                    "Scheduler is closed, ignoring submission [memoryId={}, type={}]",
                    memoryId.toIdentifier(),
                    insightTypeName);
            return;
        }
        var future = new CompletableFuture<Void>();
        pendingFutures
                .computeIfAbsent(memoryId.toIdentifier(), k -> new ConcurrentLinkedQueue<>())
                .add(future);
        executor.submit(
                () -> {
                    try {
                        runPipeline(memoryId, insightTypeName, itemIds, false);
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
        activeKeys.add(new ActiveKey(memoryId, insightTypeName, null));
    }

    /**
     * Submit build task with language hint (fire-and-forget)
     */
    public void submit(
            MemoryId memoryId, String insightTypeName, List<Long> itemIds, String language) {
        if (closed) {
            log.warn(
                    "Scheduler is closed, ignoring submission [memoryId={}, type={}]",
                    memoryId.toIdentifier(),
                    insightTypeName);
            return;
        }
        var future = new CompletableFuture<Void>();
        pendingFutures
                .computeIfAbsent(memoryId.toIdentifier(), k -> new ConcurrentLinkedQueue<>())
                .add(future);
        executor.submit(
                () -> {
                    try {
                        runPipeline(memoryId, insightTypeName, itemIds, false, language);
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
        activeKeys.add(new ActiveKey(memoryId, insightTypeName, language));
    }

    /**
     * Synchronously flush with language hint
     */
    public void flushSync(MemoryId memoryId, String insightTypeName, String language) {
        if (closed) {
            return;
        }
        if (!bufferStore.hasWork(memoryId, insightTypeName)) {
            log.debug("flushSync skipped: no pending work [type={}]", insightTypeName);
            return;
        }
        runPipeline(memoryId, insightTypeName, List.of(), true, language);
    }

    /**
     * Wait for all submitted pipelines of the specified memoryId to complete
     *
     * <p>Call before {@link #flushSync} to ensure all submitted buffers are written to disk.
     *
     * @param memoryId target memoryId
     * @param timeout  maximum wait time
     * @param unit     time unit
     */
    public void awaitPending(MemoryId memoryId, long timeout, TimeUnit unit) {
        var queue = pendingFutures.get(memoryId.toIdentifier());
        if (queue == null || queue.isEmpty()) {
            return;
        }

        var futures = queue.toArray(new CompletableFuture[0]);
        try {
            CompletableFuture.allOf(futures).get(timeout, unit);
        } catch (Exception e) {
            log.warn(
                    "awaitPending timeout or partially failed [memoryId={}, pending={}]: {}",
                    memoryId.toIdentifier(),
                    futures.length,
                    e.getMessage());
        }
        queue.removeIf(CompletableFuture::isDone);
        if (queue.isEmpty()) {
            pendingFutures.remove(memoryId.toIdentifier(), queue);
        }
    }

    /**
     * Synchronously flush the remaining buffer entries of the specified insightType (executed on the calling thread, bypassing threshold guard)
     *
     * <p>Should call {@link #awaitPending} first to ensure all submits are completed.
     */
    public void flushSync(MemoryId memoryId, String insightTypeName) {
        if (closed) {
            return;
        }
        if (!bufferStore.hasWork(memoryId, insightTypeName)) {
            log.debug("flushSync skipped: no pending work [type={}]", insightTypeName);
            return;
        }
        runPipeline(memoryId, insightTypeName, List.of(), true);
    }

    /**
     * Asynchronously flush the remaining buffer entries of the specified insightType (bypassing threshold guard)
     *
     * @deprecated Prefer using {@link #awaitPending} + {@link #flushSync} to avoid race conditions
     */
    @Deprecated
    public void flush(MemoryId memoryId, String insightTypeName) {
        if (closed) {
            return;
        }
        executor.submit(() -> runPipeline(memoryId, insightTypeName, List.of(), true));
    }

    // ===== Pipeline =====

    private void runPipeline(
            MemoryId memoryId, String insightTypeName, List<Long> itemIds, boolean force) {
        runPipeline(memoryId, insightTypeName, itemIds, force, null);
    }

    private void runPipeline(
            MemoryId memoryId,
            String insightTypeName,
            List<Long> itemIds,
            boolean force,
            String language) {
        observer.observeMono(
                        ObservationContext.<Void>of(
                                MemorySpanNames.EXTRACTION_INSIGHT_PIPELINE,
                                Map.of(
                                        MemoryAttributes.MEMORY_ID,
                                        memoryId.toIdentifier(),
                                        MemoryAttributes.EXTRACTION_INSIGHT_TYPE,
                                        insightTypeName)),
                        () ->
                                Mono.fromRunnable(
                                        () ->
                                                doRunPipeline(
                                                        memoryId,
                                                        insightTypeName,
                                                        itemIds,
                                                        force,
                                                        language)))
                .block();
    }

    private void doRunPipeline(
            MemoryId memoryId,
            String insightTypeName,
            List<Long> itemIds,
            boolean force,
            String language) {
        var lockKey = memoryId.toIdentifier() + "::" + insightTypeName;
        var lock = lockStripes[Math.floorMod(lockKey.hashCode(), LOCK_STRIPES)];

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        List<MemoryInsight> builtLeafs = List.of();
        MemoryInsightType insightType = null;

        try {
            // Phase 1-3: Execute within semaphore + lock
            lock.lock();
            try {
                // Phase 1: Buffer
                if (!itemIds.isEmpty()) {
                    bufferStore.append(memoryId, insightTypeName, itemIds);
                    log.debug(
                            "Phase 1 Buffer completed [type={}, items={}]",
                            insightTypeName,
                            itemIds.size());
                }

                insightType =
                        store.insightOperations().getInsightType(insightTypeName).orElse(null);
                if (insightType == null) {
                    log.warn(
                            "InsightType does not exist [type={}]，skipping subsequent phases",
                            insightTypeName);
                    return;
                }

                // Phase 2: Group
                try {
                    phaseGroup(memoryId, insightTypeName, insightType, force, language);
                } catch (Exception e) {
                    log.error(
                            "Phase 2 Group failed [type={}]: {}",
                            insightTypeName,
                            e.getMessage(),
                            e);
                }

                // Phase 3: Build Leaf
                try {
                    builtLeafs =
                            phaseBuildLeaf(memoryId, insightTypeName, insightType, force, language);
                } catch (Exception e) {
                    log.error(
                            "Phase 3 Build failed [type={}]: {}",
                            insightTypeName,
                            e.getMessage(),
                            e);
                }
            } finally {
                lock.unlock();
            }
        } finally {
            semaphore.release(); // Release after Phase 1-3 is complete, no longer occupying Phase 4
        }

        // Phase 4: Tree Reorganize (executed outside semaphore, does not block other pipelines)
        if (treeReorganizer != null && !builtLeafs.isEmpty()) {
            phaseTreeReorganize(memoryId, insightTypeName, insightType, builtLeafs, language);
        }
    }

    // ===== Phase 2: Group =====

    private void phaseGroup(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            boolean force,
            String language) {
        var ctx = bufferStore.getUngroupedContext(memoryId, insightTypeName);
        var ungroupedEntries = ctx.ungroupedEntries();
        if (ungroupedEntries.isEmpty()) {
            return;
        }
        if (!force && ungroupedEntries.size() < config.groupingThreshold()) {
            log.debug(
                    "Phase 2 Group skipped: ungrouped={} < threshold={}",
                    ungroupedEntries.size(),
                    config.groupingThreshold());
            return;
        }

        var ungroupedItemIds = ungroupedEntries.stream().map(BufferEntry::itemId).toList();
        var items = store.itemOperations().getItemsByIds(memoryId, ungroupedItemIds);

        var existingGroupNames = ctx.existingGroupNames().stream().toList();

        var nullCategoryItems = items.stream().filter(item -> item.category() == null).toList();
        var categorizedItems =
                items.stream()
                        .filter(item -> item.category() != null)
                        .collect(Collectors.groupingBy(MemoryItem::category));

        Map<String, List<MemoryItem>> mergedGroups = new LinkedHashMap<>();

        if (!nullCategoryItems.isEmpty()) {
            var groupResult =
                    groupClassifier
                            .classify(insightType, nullCategoryItems, existingGroupNames, language)
                            .block();
            if (groupResult != null) {
                mergedGroups.putAll(groupResult);
            }
        }

        for (var bucket : categorizedItems.entrySet()) {
            var category = bucket.getKey();
            var bucketItems = bucket.getValue();
            var groupResult =
                    groupRouter
                            .group(insightType, category, bucketItems, existingGroupNames, language)
                            .block();
            if (groupResult != null) {
                mergedGroups.putAll(groupResult);
            }
        }

        if (mergedGroups.isEmpty()) {
            log.warn("Phase 2 Group: LLM grouping result is empty [type={}]", insightTypeName);
            return;
        }

        for (var entry : mergedGroups.entrySet()) {
            var groupItemIds = entry.getValue().stream().map(MemoryItem::id).toList();
            bufferStore.assignGroup(memoryId, insightTypeName, groupItemIds, entry.getKey());
        }
        log.debug(
                "Phase 2 Group completed [type={}, groups={}]",
                insightTypeName,
                mergedGroups.size());
    }

    // ===== Phase 3: Build Leaf =====

    private List<MemoryInsight> phaseBuildLeaf(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            boolean force,
            String language) {
        var unbuiltByGroup = bufferStore.getUnbuiltByGroup(memoryId, insightTypeName);
        var builtLeafs = new ArrayList<MemoryInsight>();

        for (var entry : unbuiltByGroup.entrySet()) {
            var groupName = entry.getKey();
            var unbuiltEntries = entry.getValue();
            if (!force && unbuiltEntries.size() < config.buildThreshold()) {
                log.debug(
                        "Phase 3 Build skipped [type={}, group={}, unbuilt={} < threshold={}]",
                        insightTypeName,
                        groupName,
                        unbuiltEntries.size(),
                        config.buildThreshold());
                continue;
            }

            var leaf =
                    buildLeafForGroup(
                            memoryId,
                            insightTypeName,
                            insightType,
                            groupName,
                            unbuiltEntries,
                            language);
            if (leaf != null) {
                builtLeafs.add(leaf);
            }
        }
        return builtLeafs;
    }

    private MemoryInsight buildLeafForGroup(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            String groupName,
            List<BufferEntry> unbuiltEntries,
            String language) {

        var unbuiltItemIds = unbuiltEntries.stream().map(BufferEntry::itemId).toList();
        var items = store.itemOperations().getItemsByIds(memoryId, unbuiltItemIds);

        // Find existing LEAF (at most one)
        var existingLeaf =
                store.insightOperations()
                        .getLeafByGroup(memoryId, insightTypeName, groupName)
                        .orElse(null);
        existingLeaf = normalizeExistingInsightIfNeeded(memoryId, existingLeaf);
        var existingPoints =
                existingLeaf != null && existingLeaf.points() != null
                        ? existingLeaf.points()
                        : List.<InsightPoint>of();

        var opsResponse =
                generator
                        .generateLeafPointOps(
                                insightType,
                                groupName,
                                existingPoints,
                                items,
                                insightType.targetTokens(),
                                null,
                                language)
                        .block();

        if (opsResponse == null) {
            return buildLeafWithFullRewrite(
                    memoryId,
                    insightTypeName,
                    insightType,
                    groupName,
                    items,
                    unbuiltItemIds,
                    existingLeaf,
                    existingPoints,
                    language);
        }

        var normalizedOps =
                pointIdentityManager.normalizeGeneratedOperations(
                        existingPoints, opsResponse.operations());
        var resolved = PointOperationResolver.resolve(existingPoints, normalizedOps);
        if (resolved.fallbackRequired()) {
            return buildLeafWithFullRewrite(
                    memoryId,
                    insightTypeName,
                    insightType,
                    groupName,
                    items,
                    unbuiltItemIds,
                    existingLeaf,
                    existingPoints,
                    language);
        }
        if (resolved.noop()) {
            bufferStore.markBuilt(memoryId, insightTypeName, unbuiltItemIds);
            return null;
        }

        var points = resolved.points();
        if (existingLeaf != null && points.equals(existingLeaf.points())) {
            bufferStore.markBuilt(memoryId, insightTypeName, unbuiltItemIds);
            return null;
        }

        return saveLeafInsight(
                memoryId,
                insightTypeName,
                insightType,
                groupName,
                items,
                unbuiltItemIds,
                existingLeaf,
                points);
    }

    private MemoryInsight buildLeafWithFullRewrite(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            String groupName,
            List<MemoryItem> items,
            List<Long> unbuiltItemIds,
            MemoryInsight existingLeaf,
            List<InsightPoint> existingPoints,
            String language) {
        var response =
                generator
                        .generatePoints(
                                insightType,
                                groupName,
                                existingPoints,
                                items,
                                insightType.targetTokens(),
                                null,
                                language)
                        .block();
        if (response == null || response.points().isEmpty()) {
            log.warn(
                    "Phase 3 Build: LLM generation result is empty [type={}, group={}]",
                    insightTypeName,
                    groupName);
            bufferStore.markBuilt(memoryId, insightTypeName, unbuiltItemIds);
            return null;
        }

        var points =
                pointIdentityManager.reusePointIdsForFullRewrite(existingPoints, response.points());
        if (existingLeaf != null && points.equals(existingLeaf.points())) {
            bufferStore.markBuilt(memoryId, insightTypeName, unbuiltItemIds);
            return null;
        }

        return saveLeafInsight(
                memoryId,
                insightTypeName,
                insightType,
                groupName,
                items,
                unbuiltItemIds,
                existingLeaf,
                points);
    }

    private MemoryInsight normalizeExistingInsightIfNeeded(
            MemoryId memoryId, MemoryInsight insight) {
        if (insight == null || insight.points() == null || insight.points().isEmpty()) {
            return insight;
        }
        var normalized = pointIdentityManager.normalizePersistedPoints(insight.points());
        if (normalized.equals(insight.points())) {
            return insight;
        }
        var updated = insight.withPoints(normalized).withUpdatedAt(Instant.now());
        store.insightOperations().upsertInsights(memoryId, List.of(updated));
        return updated;
    }

    private MemoryInsight saveLeafInsight(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            String groupName,
            List<MemoryItem> items,
            List<Long> unbuiltItemIds,
            MemoryInsight existingLeaf,
            List<InsightPoint> points) {
        var now = Instant.now();
        MemoryInsight leafInsight;
        if (existingLeaf != null) {
            leafInsight =
                    existingLeaf
                            .withPoints(points)
                            .withConfidence(computeConfidence(points))
                            .withLastReasonedAt(now)
                            .withSummaryEmbedding(embedPoints(points))
                            .withUpdatedAt(now)
                            .withVersion(existingLeaf.version() + 1);
        } else {
            MemoryScope leafScope =
                    items.stream()
                            .map(MemoryItem::category)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .map(MemoryCategory::scope)
                            .orElse(MemoryScope.USER);
            leafInsight =
                    new MemoryInsight(
                            idGenerator.nextId(),
                            memoryId.toIdentifier(),
                            insightTypeName,
                            leafScope,
                            groupName,
                            insightType.categories(),
                            points,
                            groupName,
                            computeConfidence(points),
                            now,
                            embedPoints(points),
                            now,
                            now,
                            InsightTier.LEAF,
                            null,
                            List.of(),
                            1);
        }

        store.insightOperations().upsertInsights(memoryId, List.of(leafInsight));
        bufferStore.markBuilt(memoryId, insightTypeName, unbuiltItemIds);
        log.debug(
                "Phase 3 Build completed [type={}, group={}, points={}, version={}]",
                insightTypeName,
                groupName,
                points.size(),
                leafInsight.version());

        return leafInsight;
    }

    // ===== Phase 4: Tree Reorganize =====

    private void phaseTreeReorganize(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            List<MemoryInsight> builtLeafs,
            String language) {
        try {
            observer.<Void>observeMono(
                            ObservationContext.<Void>of(
                                    MemorySpanNames.EXTRACTION_INSIGHT_TREE_REORGANIZE,
                                    Map.of(
                                            MemoryAttributes.MEMORY_ID,
                                            memoryId.toIdentifier(),
                                            MemoryAttributes.EXTRACTION_INSIGHT_TYPE,
                                            insightTypeName,
                                            MemoryAttributes.EXTRACTION_INSIGHT_LEAF_COUNT,
                                            builtLeafs.size())),
                            () ->
                                    Mono.fromRunnable(
                                            () -> {
                                                var treeConfig = insightType.resolveTreeConfig();
                                                treeReorganizer.onLeafsUpdated(
                                                        memoryId,
                                                        insightTypeName,
                                                        insightType,
                                                        builtLeafs,
                                                        treeConfig,
                                                        language);
                                            }))
                    .block();
            log.debug(
                    "Phase 4 Tree Reorganize completed [type={}, leafs={}]",
                    insightTypeName,
                    builtLeafs.size());
        } catch (Exception e) {
            log.warn(
                    "Phase 4 Tree Reorganize failed [type={}]: {}",
                    insightTypeName,
                    e.getMessage(),
                    e);
        }
    }

    // ===== Helpers =====

    private List<Float> embedPoints(List<InsightPoint> points) {
        if (memoryVector == null || points == null || points.isEmpty()) {
            return null;
        }
        try {
            var content =
                    points.stream()
                            .map(InsightPoint::content)
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("");
            return memoryVector.embed(content).block();
        } catch (Exception e) {
            log.warn("Embedding calculation failed: {}", e.getMessage());
            return null;
        }
    }

    private float computeConfidence(List<InsightPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0.0f;
        }
        return (float) points.stream().mapToDouble(InsightPoint::confidence).average().orElse(0.0);
    }

    /**
     * Delegates to {@link InsightTreeReorganizer#forceResummarizeBranchIfEmpty} for use in flush.
     */
    public void forceResummarizeBranchIfEmpty(
            MemoryId memoryId, MemoryInsightType insightType, String language) {
        if (treeReorganizer != null) {
            treeReorganizer.forceResummarizeBranchIfEmpty(memoryId, insightType, language);
        }
    }

    /**
     * Delegates to {@link InsightTreeReorganizer#drainRootTasks} for use in flush.
     */
    public void drainRootTasks(MemoryId memoryId, long timeout, TimeUnit unit) {
        if (treeReorganizer != null) {
            treeReorganizer.drainRootTasks(memoryId, timeout, unit);
        }
    }

    @Override
    public void close() {
        closed = true;

        // First synchronously flush all known (memoryId, insightTypeName) remaining buffers
        for (var key : activeKeys) {
            try {
                runPipeline(key.memoryId(), key.insightTypeName(), List.of(), true, key.language());
            } catch (Exception e) {
                log.warn(
                        "close flush failed [memoryId={}, type={}]: {}",
                        key.memoryId().toIdentifier(),
                        key.insightTypeName(),
                        e.getMessage());
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pendingFutures.clear();
    }
}
