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
package com.openmemind.ai.memory.core.extraction.insight.tree;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.operation.PointOperationResolver;
import com.openmemind.ai.memory.core.extraction.insight.support.InsightPointIdentityManager;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.utils.IdUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Insight Shared Tree Reorganizer
 *
 * <p>Manages a globally shared three-layer tree structure:
 * <ul>
 *   <li>LEAF — one for each group, updated in place</li>
 *   <li>BRANCH — one for each BRANCH-mode InsightType, aggregates all LEAFs of that type</li>
 *   <li>ROOT — one for each ROOT-mode InsightType, deeply synthesizes across InsightTypes</li>
 * </ul>
 *
 * <p>Single entry point {@link #onLeafUpdated}, called by InsightBuildScheduler after LEAF creation/update.
 * Full link synchronization blocking, designed to run on virtual threads.
 *
 */
public class InsightTreeReorganizer {

    private static final Logger log = LoggerFactory.getLogger(InsightTreeReorganizer.class);

    private final InsightGenerator generator;
    private final MemoryVector vector;
    private final MemoryStore store;
    private final BubbleTrackerStore bubbleTracker;
    private final IdUtils.SnowflakeIdGenerator idGenerator;
    private final InsightPointIdentityManager pointIdentityManager;

    /**
     * Fixed-size strip lock, bucketed by memoryId hash, to avoid ConcurrentHashMap infinite growth
     */
    private static final int LOCK_STRIPES = 16;

    private final ReentrantLock[] rootLocks;

    /**
     * Tracks in-flight ROOT re-summarize virtual threads per memoryId, used by drainRootTasks
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Thread>> pendingRootThreads =
            new ConcurrentHashMap<>();

    {
        rootLocks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            rootLocks[i] = new ReentrantLock();
        }
    }

    public InsightTreeReorganizer(
            InsightGenerator generator,
            MemoryVector vector,
            MemoryStore store,
            BubbleTrackerStore bubbleTracker,
            IdUtils.SnowflakeIdGenerator idGenerator) {
        this(
                generator,
                vector,
                store,
                bubbleTracker,
                idGenerator,
                new InsightPointIdentityManager());
    }

    public InsightTreeReorganizer(
            InsightGenerator generator,
            MemoryVector vector,
            MemoryStore store,
            BubbleTrackerStore bubbleTracker,
            IdUtils.SnowflakeIdGenerator idGenerator,
            InsightPointIdentityManager pointIdentityManager) {
        this.generator = Objects.requireNonNull(generator);
        this.vector = Objects.requireNonNull(vector);
        this.store = Objects.requireNonNull(store);
        this.bubbleTracker = Objects.requireNonNull(bubbleTracker);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.pointIdentityManager = Objects.requireNonNull(pointIdentityManager);
    }

    /**
     * Wait for all in-flight ROOT re-summarize threads of the given memoryId to finish.
     *
     * <p>Only called during flush. Has no effect on the normal async path.
     */
    public void drainRootTasks(MemoryId memoryId, long timeout, TimeUnit unit) {
        var queue = pendingRootThreads.remove(memoryId.toIdentifier());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        for (var t : queue) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                log.warn("drainRootTasks timed out [memoryId={}]", memoryId.toIdentifier());
                break;
            }
            try {
                t.join(remainingNanos / 1_000_000, (int) (remainingNanos % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Force re-summarize a BRANCH that has LEAFs but no summary yet.
     *
     * <p>Only used in flush scenarios to compensate for BRANCHes whose bubble dirty count
     * never reached the threshold. Has no effect on the normal pipeline path.
     *
     * @param memoryId    target memoryId
     * @param insightType the BRANCH-mode InsightType to check and summarize
     * @param language    output language hint
     */
    public void forceResummarizeBranchIfEmpty(
            MemoryId memoryId, MemoryInsightType insightType, String language) {
        var branch =
                store.insightOperations()
                        .getBranchByType(memoryId, insightType.name())
                        .orElse(null);
        if (branch == null) {
            return;
        }
        if (branch.points() != null && !branch.points().isEmpty()) {
            return;
        }

        var allLeafs =
                store.insightOperations().getInsightsByType(memoryId, insightType.name()).stream()
                        .filter(i -> InsightTier.LEAF.equals(i.tier()))
                        .toList();
        if (allLeafs.isEmpty()) {
            return;
        }

        log.debug(
                "forceResummarizeBranchIfEmpty: BRANCH [type={}] has {} leafs but no summary,"
                        + " forcing re-summarize",
                insightType.name(),
                allLeafs.size());

        var config = insightType.resolveTreeConfig();
        var updatedBranch =
                resummarizeBranch(
                        memoryId,
                        insightType.name(),
                        insightType,
                        branch,
                        allLeafs,
                        config,
                        language);

        if (!pointsChanged(branch, updatedBranch)) {
            return;
        }

        var rootLock = rootLocks[Math.floorMod(memoryId.toIdentifier().hashCode(), LOCK_STRIPES)];
        rootLock.lock();
        try {
            var rootCtx = queryRootContext(memoryId);
            bubbleAndMaybeResummarizeRoots(memoryId, updatedBranch, rootCtx, language);
        } finally {
            rootLock.unlock();
        }
    }

    /**
     * Entry point after batch LEAF creation/update
     *
     * <p>Batch processing for multiple LEAFs to avoid repeating high-cost operations like ensureBranchLinkedToRoots for each LEAF.
     * Each LEAF is counted once as dirty (maintaining bubble counting semantics), but DB queries and lock operations are only executed once.
     *
     * @param builtLeafs All LEAFs built in this round
     */
    public void onLeafsUpdated(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            List<MemoryInsight> builtLeafs,
            InsightTreeConfig config) {
        onLeafsUpdated(memoryId, insightTypeName, insightType, builtLeafs, config, null);
    }

    /**
     * Entry point after batch LEAF creation/update (with language)
     */
    public void onLeafsUpdated(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            List<MemoryInsight> builtLeafs,
            InsightTreeConfig config,
            String language) {

        if (builtLeafs.isEmpty()) {
            return;
        }

        var dirtyKey = branchBubbleKey(memoryId, insightTypeName);

        int branchDirtyCount = bubbleTracker.incrementAndGet(dirtyKey, builtLeafs.size());

        // 1. Query all LEAFs at once (eliminate duplicate queries)
        var allLeafs =
                store.insightOperations().getInsightsByType(memoryId, insightTypeName).stream()
                        .filter(i -> InsightTier.LEAF.equals(i.tier()))
                        .toList();

        // 2. Get or create BRANCH
        var existingBranch = store.insightOperations().getBranchByType(memoryId, insightTypeName);
        var branch =
                existingBranch.orElseGet(() -> newBranch(memoryId, insightTypeName, insightType));

        // 3. Always link LEAF → BRANCH
        var linkedBranch = batchLinkLeafsToBranch(memoryId, allLeafs, branch);

        // 3.5 Ensure BRANCH→ROOT link (query once, reuse for bubble phase)
        var rootLock = rootLocks[Math.floorMod(memoryId.toIdentifier().hashCode(), LOCK_STRIPES)];
        rootLock.lock();
        RootContext rootCtx;
        try {
            rootCtx = queryRootContext(memoryId);
            linkBranchToAllRoots(memoryId, linkedBranch, rootCtx);
        } finally {
            rootLock.unlock();
        }

        // 4. Only re-summarize when bubble threshold is met
        if (branchDirtyCount < config.branchBubbleThreshold()) {
            log.debug(
                    "BRANCH [type={}] dirtyCount did not reach threshold, skipping re-summarize",
                    insightTypeName);
            return;
        }

        // 5. re-summarize (reuse allLeafs, no need to query again)
        var updatedBranch =
                resummarizeBranch(
                        memoryId,
                        insightTypeName,
                        insightType,
                        linkedBranch,
                        allLeafs,
                        config,
                        language);

        bubbleTracker.reset(dirtyKey);

        if (!pointsChanged(linkedBranch, updatedBranch)) {
            return;
        }

        // 6. Reuse rootCtx for bubble and possible ROOT re-summarize
        rootLock.lock();
        try {
            bubbleAndMaybeResummarizeRoots(memoryId, updatedBranch, rootCtx, language);
        } finally {
            rootLock.unlock();
        }
    }

    /**
     * Single entry point after LEAF creation/update (single LEAF version)
     *
     * <p>Delegates to {@link #onLeafsUpdated} for backward compatibility.
     */
    public void onLeafUpdated(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            MemoryInsight leaf,
            InsightTreeConfig config) {
        onLeafsUpdated(memoryId, insightTypeName, insightType, List.of(leaf), config);
    }

    // ===== Internal Process =====

    /**
     * Constructs a new BRANCH memory object, does not write to DB; first persistence is completed by the caller batchLinkLeafsToBranch
     */
    private MemoryInsight newBranch(
            MemoryId memoryId, String insightTypeName, MemoryInsightType insightType) {
        var now = Instant.now();
        log.info("Creating BRANCH [type={}]", insightTypeName);
        MemoryScope branchScope =
                insightType.scope() != null ? insightType.scope() : MemoryScope.USER;
        return new MemoryInsight(
                idGenerator.nextId(),
                memoryId.toIdentifier(),
                insightTypeName,
                branchScope,
                "branch-" + insightTypeName,
                insightType.categories(),
                List.of(),
                null,
                0.0f,
                now,
                null,
                now,
                now,
                InsightTier.BRANCH,
                null,
                List.of(),
                1);
    }

    /**
     * Batch links all LEAFs to BRANCH, single saveInsights replaces N+1
     */
    private MemoryInsight batchLinkLeafsToBranch(
            MemoryId memoryId, List<MemoryInsight> allLeafs, MemoryInsight branch) {
        var existingChildIds = new LinkedHashSet<>(branch.childInsightIds());
        var updatedLeafs = new ArrayList<MemoryInsight>();

        for (var leaf : allLeafs) {
            existingChildIds.add(leaf.id());
            if (!branch.id().equals(leaf.parentInsightId())) {
                updatedLeafs.add(leaf.withParentInsightId(branch.id()));
            }
        }

        var updatedBranch = branch.withChildInsightIds(List.copyOf(existingChildIds));
        var toSave = new ArrayList<MemoryInsight>();
        toSave.add(updatedBranch);
        toSave.addAll(updatedLeafs);
        store.insightOperations().upsertInsights(memoryId, toSave);

        return updatedBranch;
    }

    /**
     * Shared query result for ROOT-related operations
     */
    private record RootContext(
            List<MemoryInsight> allBranches, List<MemoryInsightType> rootTypes) {}

    /**
     * Query once within strip lock, result shared by link and bubble logic
     */
    private RootContext queryRootContext(MemoryId memoryId) {
        var allBranches = store.insightOperations().getInsightsByTier(memoryId, InsightTier.BRANCH);
        var configuredTypes = store.insightOperations().listInsightTypes();
        var rootTypes =
                configuredTypes.stream()
                        .filter(t -> t.insightAnalysisMode() == InsightAnalysisMode.ROOT)
                        .toList();
        return new RootContext(allBranches, rootTypes);
    }

    /**
     * Ensure BRANCH is linked to all ROOTs (pure link, no bubble tracking)
     */
    private void linkBranchToAllRoots(MemoryId memoryId, MemoryInsight branch, RootContext ctx) {
        for (var rootType : ctx.rootTypes()) {
            var config = rootType.resolveTreeConfig();
            if (ctx.allBranches().size() < config.minBranchesForRoot()) {
                continue;
            }
            var root = ensureRoot(memoryId, rootType, ctx.allBranches());
            linkBranchToRoot(memoryId, branch, root);
        }
    }

    /**
     * Context for ROOT that needs re-summarize (lightweight — no stale snapshots)
     */
    private record PendingRootResummarize(
            MemoryInsightType rootType, InsightTreeConfig config, String rootKey) {}

    /**
     * After BRANCH re-summarize, bubble-track all ROOTs and maybe trigger re-summarize
     */
    private void bubbleAndMaybeResummarizeRoots(
            MemoryId memoryId, MemoryInsight branch, RootContext ctx, String language) {
        List<PendingRootResummarize> pendingList = new ArrayList<>();
        for (var rootType : ctx.rootTypes()) {
            var config = rootType.resolveTreeConfig();
            if (ctx.allBranches().size() < config.minBranchesForRoot()) {
                log.debug(
                        "BRANCH count {} < minBranchesForRoot {}, skipping ROOT [type={}]",
                        ctx.allBranches().size(),
                        config.minBranchesForRoot(),
                        rootType.name());
                continue;
            }
            var root = ensureRoot(memoryId, rootType, ctx.allBranches());
            linkBranchToRoot(memoryId, branch, root);

            var rootKey = rootBubbleKey(memoryId, rootType.name());
            int rootDirtyCount = bubbleTracker.incrementAndGet(rootKey);

            if (rootDirtyCount < config.rootBubbleThreshold()) {
                log.debug(
                        "ROOT [type={}] dirtyCount did not reach threshold, skipping re-summarize",
                        rootType.name());
                continue;
            }
            pendingList.add(new PendingRootResummarize(rootType, config, rootKey));
        }
        // Fire-and-forget re-summarize outside of lock (tracked for flush drain)
        for (var p : pendingList) {
            var t = Thread.ofVirtual().start(() -> resummarizeRootAndReset(memoryId, p, language));
            pendingRootThreads
                    .computeIfAbsent(memoryId.toIdentifier(), k -> new ConcurrentLinkedQueue<>())
                    .add(t);
        }
    }

    /**
     * Re-summarize a single ROOT with fresh data, reset dirty count only after success
     */
    private void resummarizeRootAndReset(
            MemoryId memoryId, PendingRootResummarize p, String language) {
        try {
            var freshRoot =
                    store.insightOperations()
                            .getRootByType(memoryId, p.rootType().name())
                            .orElse(null);
            if (freshRoot == null) {
                return;
            }
            var freshBranches =
                    store.insightOperations().getInsightsByTier(memoryId, InsightTier.BRANCH);
            resummarizeRoot(memoryId, p.rootType(), freshRoot, freshBranches, p.config(), language);
            bubbleTracker.reset(p.rootKey());
        } catch (Exception e) {
            log.warn(
                    "ROOT re-summarize failed [type={}]: {}",
                    p.rootType().name(),
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Find or create the ROOT node of the specified ROOT InsightType
     */
    private MemoryInsight ensureRoot(
            MemoryId memoryId, MemoryInsightType rootType, List<MemoryInsight> allBranches) {
        return store.insightOperations()
                .getRootByType(memoryId, rootType.name())
                .orElseGet(
                        () -> {
                            var now = Instant.now();
                            var childIds = allBranches.stream().map(MemoryInsight::id).toList();
                            var root =
                                    new MemoryInsight(
                                            idGenerator.nextId(),
                                            memoryId.toIdentifier(),
                                            rootType.name(),
                                            rootType.scope() != null
                                                    ? rootType.scope()
                                                    : inferScope(allBranches),
                                            "root-" + rootType.name(),
                                            List.of(),
                                            List.of(),
                                            null,
                                            0.0f,
                                            now,
                                            null,
                                            now,
                                            now,
                                            InsightTier.ROOT,
                                            null,
                                            childIds,
                                            1);
                            store.insightOperations().upsertInsights(memoryId, List.of(root));
                            log.info(
                                    "Creating ROOT [type={}, id={}], containing {} BRANCHES",
                                    rootType.name(),
                                    root.id(),
                                    allBranches.size());
                            return root;
                        });
    }

    /**
     * Set ROOT→BRANCH link (idempotent, does not update BRANCH's parentInsightId)
     */
    private void linkBranchToRoot(MemoryId memoryId, MemoryInsight branch, MemoryInsight root) {
        var latestRoot = store.insightOperations().getInsight(memoryId, root.id()).orElse(root);
        var childIds = new ArrayList<>(latestRoot.childInsightIds());

        if (childIds.contains(branch.id())) {
            return; // Already linked
        }

        childIds.add(branch.id());
        store.insightOperations()
                .upsertInsights(memoryId, List.of(latestRoot.withChildInsightIds(childIds)));
        log.debug(
                "Linking BRANCH [id={}] → ROOT [type={}, id={}]",
                branch.id(),
                root.type(),
                root.id());
    }

    // ===== Re-summarize =====

    private MemoryInsight resummarizeBranch(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            MemoryInsight branch,
            List<MemoryInsight> leafInsights,
            InsightTreeConfig config) {
        return resummarizeBranch(
                memoryId, insightTypeName, insightType, branch, leafInsights, config, null);
    }

    private MemoryInsight resummarizeBranch(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            MemoryInsight branch,
            List<MemoryInsight> leafInsights,
            InsightTreeConfig config,
            String language) {

        if (leafInsights.isEmpty()) {
            return branch;
        }

        log.info(
                "Re-summarize BRANCH [type={}, id={}, leaves={}]",
                insightTypeName,
                branch.id(),
                leafInsights.size());

        branch = normalizeExistingInsightIfNeeded(memoryId, branch);
        var existingPoints = branch.points() != null ? branch.points() : List.<InsightPoint>of();

        var opsMono =
                generator.generateBranchPointOps(
                        insightType,
                        existingPoints,
                        leafInsights,
                        insightType.targetTokens(),
                        language);
        var opsResponse = opsMono != null ? opsMono.block() : null;

        if (opsResponse == null) {
            return resummarizeBranchWithFullRewrite(
                    memoryId, insightTypeName, insightType, branch, leafInsights, language);
        }

        var normalizedOps =
                pointIdentityManager.normalizeGeneratedOperations(
                        existingPoints, opsResponse.operations());
        var resolved = PointOperationResolver.resolve(existingPoints, normalizedOps);
        if (resolved.fallbackRequired()) {
            return resummarizeBranchWithFullRewrite(
                    memoryId, insightTypeName, insightType, branch, leafInsights, language);
        }
        if (resolved.noop()) {
            return branch;
        }

        return embedAndSaveIfChanged(memoryId, branch, resolved.points(), InsightTier.BRANCH);
    }

    private MemoryInsight resummarizeBranchWithFullRewrite(
            MemoryId memoryId,
            String insightTypeName,
            MemoryInsightType insightType,
            MemoryInsight branch,
            List<MemoryInsight> leafInsights,
            String language) {
        var response =
                generator
                        .generateBranchSummary(
                                insightType,
                                branch.points() != null ? branch.points() : List.of(),
                                leafInsights,
                                insightType.targetTokens(),
                                language)
                        .block();

        if (response == null || response.points().isEmpty()) {
            log.warn(
                    "BRANCH re-summarize: LLM generated result is empty [type={}]",
                    insightTypeName);
            return branch;
        }

        var points =
                pointIdentityManager.reusePointIdsForFullRewrite(
                        branch.points() != null ? branch.points() : List.of(), response.points());

        return embedAndSaveIfChanged(memoryId, branch, points, InsightTier.BRANCH);
    }

    private MemoryInsight resummarizeRoot(
            MemoryId memoryId,
            MemoryInsightType rootType,
            MemoryInsight root,
            List<MemoryInsight> allBranches,
            InsightTreeConfig config) {
        return resummarizeRoot(memoryId, rootType, root, allBranches, config, null);
    }

    private MemoryInsight resummarizeRoot(
            MemoryId memoryId,
            MemoryInsightType rootType,
            MemoryInsight root,
            List<MemoryInsight> allBranches,
            InsightTreeConfig config,
            String language) {

        if (allBranches.isEmpty()) {
            return root;
        }

        log.info(
                "Re-summarize ROOT [type={}, id={}, branches={}]",
                rootType.name(),
                root.id(),
                allBranches.size());

        root = normalizeExistingInsightIfNeeded(memoryId, root);
        var response =
                generator
                        .generateRootSynthesis(
                                rootType,
                                root.pointsContent(),
                                allBranches,
                                config.rootTargetTokens(),
                                language)
                        .block();

        if (response == null || response.points().isEmpty()) {
            log.warn("ROOT re-summarize: LLM generated result is empty");
            return root;
        }

        var points = pointIdentityManager.normalizePersistedPoints(response.points());

        return embedAndSave(memoryId, root, points, InsightTier.ROOT);
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

    private MemoryInsight embedAndSave(
            MemoryId memoryId, MemoryInsight insight, List<InsightPoint> points, InsightTier tier) {
        var content =
                points.stream()
                        .map(InsightPoint::content)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");

        List<Float> embedding;
        try {
            embedding = vector.embed(content).block();
        } catch (Exception e) {
            log.warn("{} embedding failed: {}", tier, e.getMessage());
            embedding = insight.summaryEmbedding();
        }

        var now = Instant.now();
        float confidence =
                points.isEmpty()
                        ? 0f
                        : (float)
                                points.stream()
                                        .mapToDouble(InsightPoint::confidence)
                                        .average()
                                        .orElse(0.0);
        var updated =
                insight.withPoints(points)
                        .withConfidence(confidence)
                        .withSummaryEmbedding(embedding)
                        .withLastReasonedAt(now)
                        .withUpdatedAt(now)
                        .withVersion(insight.version() + 1);
        store.insightOperations().upsertInsights(memoryId, List.of(updated));
        return updated;
    }

    private MemoryInsight embedAndSaveIfChanged(
            MemoryId memoryId, MemoryInsight insight, List<InsightPoint> points, InsightTier tier) {
        return pointsChanged(insight, points)
                ? embedAndSave(memoryId, insight, points, tier)
                : insight;
    }

    private boolean pointsChanged(MemoryInsight insight, List<InsightPoint> points) {
        var existingPoints = insight.points() != null ? insight.points() : List.<InsightPoint>of();
        var candidatePoints = points != null ? points : List.<InsightPoint>of();
        return !existingPoints.equals(candidatePoints);
    }

    private boolean pointsChanged(MemoryInsight before, MemoryInsight after) {
        var afterPoints = after != null ? after.points() : List.<InsightPoint>of();
        return pointsChanged(before, afterPoints);
    }

    // ===== Utility Methods =====

    private static String branchBubbleKey(MemoryId memoryId, String insightTypeName) {
        return memoryId.toIdentifier() + "::" + insightTypeName;
    }

    private static String rootBubbleKey(MemoryId memoryId, String rootTypeName) {
        return memoryId.toIdentifier() + "::root::" + rootTypeName;
    }

    static MemoryScope inferScope(List<MemoryInsight> children) {
        var scopes = children.stream().map(MemoryInsight::scope).distinct().toList();
        return scopes.size() == 1 ? scopes.get(0) : MemoryScope.USER;
    }
}
