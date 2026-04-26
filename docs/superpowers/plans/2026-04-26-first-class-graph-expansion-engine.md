# First-Class GraphExpansionEngine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor SIMPLE graph retrieval so `GraphExpansionEngine` is a first-class graph-only expansion core shared by `DefaultGraphItemChannel` and `DefaultRetrievalGraphAssistant`.

**Architecture:** Move low-level graph traversal, candidate collection, scoring, fanout guard, and stats production out of `DefaultRetrievalGraphAssistant` into `GraphExpansionEngine`. Keep assistant behavior as a fusion adapter over engine output, and keep `DefaultGraphItemChannel` as the reactive channel boundary with `GraphQueryBudgetContext` opened on the boundedElastic worker thread.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ, Mockito, Reactor `Mono` / `StepVerifier`, memind `MemoryStore`, `GraphOperations`, `RetrievalGraphSettings`.

---

## File Structure

**Core implementation files**

- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngine.java`
  - Change constructor dependency from `RetrievalGraphAssistant` to `MemoryStore`.
  - Own materialization, graph candidate collection, ranking, stats, and direct-id exclusion.
  - Remain synchronous: no Reactor, no `.block()`, no `GraphQueryBudgetContext`.

- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionResult.java`
  - Rename `rawCandidateCount` to `dedupedCandidateCount`.
  - Keep `enabled()` accessor unless changing it is unavoidable; semantic name remains graph-enabled.

- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannel.java`
  - Keep reactive boundary.
  - Compute one `effectiveTimeout` with `shorterPositive(settings.timeout(), config.timeout())`.
  - Open `GraphQueryBudgetContext` inside the `Mono.fromCallable(...)` lambda, around `engine.expand(...)`.

- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistant.java`
  - Add constructor accepting `GraphExpansionEngine`.
  - Keep public `DefaultRetrievalGraphAssistant(MemoryStore store)` constructor.
  - Replace low-level graph expansion internals with a call to `GraphExpansionEngine.expand(...)`.
  - Keep ASSIST / EXPAND fusion behavior unchanged.
  - Continue opening `GraphQueryBudgetContext` inside assistant `Mono.fromCallable(...)`.

- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryRetrievalAssembler.java`
  - Build one default `GraphExpansionEngine` from `MemoryStore`.
  - Pass it to the default traced assistant and graph item channel.
  - Keep `RetrievalGraphAssistant` as the public extension point; do not change its interface.

**Test files**

- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngineTest.java`
  - Move low-level expansion tests here from assistant test.
  - Cover semantic, temporal, causal, entity sibling, fanout, guard, scoring, direct exclusion, and stats.

- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannelTest.java`
  - Update construction to `new GraphExpansionEngine(memoryStore)`.
  - Update `rawCandidateCount()` assertions to `dedupedCandidateCount()`.
  - Add ThreadLocal budget test for `GraphQueryBudgetContext` inside the worker lambda.
  - Keep timeout/error boundary tests using fake store/ops rather than adding a public engine interface.

- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistantTest.java`
  - Keep fusion behavior tests in this file.
  - Keep existing low-level assistant tests as integration coverage for this refactor unless they fail or duplicate new engine-only assertions exactly.
  - Add explicit overlap no-boost and stats-copying assertions for assistant-owned vs engine-owned behavior.

---

## Task 1: Rename GraphExpansionResult Candidate Count

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionResult.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannelTest.java`

- [ ] **Step 1: Update the failing test assertion name**

In `DefaultGraphItemChannelTest.returnsGraphOnlyCandidatesAndExcludesSeeds`, replace:

```java
assertThat(result.rawCandidateCount()).isEqualTo(1);
```

with:

```java
assertThat(result.dedupedCandidateCount()).isEqualTo(1);
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultGraphItemChannelTest#returnsGraphOnlyCandidatesAndExcludesSeeds test
```

Expected: compile failure because `dedupedCandidateCount()` does not exist on `GraphExpansionResult`.

- [ ] **Step 3: Rename the record component**

In `GraphExpansionResult.java`, change the record component:

```java
int rawCandidateCount,
```

to:

```java
int dedupedCandidateCount,
```

Keep the constructor and static factories structurally the same:

```java
public record GraphExpansionResult(
        List<ScoredResult> graphItems,
        boolean enabled,
        boolean degraded,
        boolean timedOut,
        int seedCount,
        int linkExpansionCount,
        int entityExpansionCount,
        int dedupedCandidateCount,
        int overlapCount,
        int skippedOverFanoutEntityCount) {

    public GraphExpansionResult {
        graphItems = graphItems == null ? List.of() : List.copyOf(graphItems);
    }

    public static GraphExpansionResult empty(boolean enabled) {
        return new GraphExpansionResult(List.of(), enabled, false, false, 0, 0, 0, 0, 0, 0);
    }

    public static GraphExpansionResult degraded(boolean enabled, boolean timedOut) {
        return new GraphExpansionResult(List.of(), enabled, true, timedOut, 0, 0, 0, 0, 0, 0);
    }
}
```

- [ ] **Step 4: Verify all call sites compile**

Run:

```bash
rg "rawCandidateCount" memind-core/src/main/java memind-core/src/test/java
```

Expected: no output.

- [ ] **Step 5: Run focused test**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultGraphItemChannelTest#returnsGraphOnlyCandidatesAndExcludesSeeds test
```

Expected: test passes.

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionResult.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannelTest.java
git commit -m "refactor: rename graph expansion candidate count"
```

---

## Task 2: Build GraphExpansionEngine Low-Level Tests

**Files:**
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngineTest.java`

- [ ] **Step 1: Create the test skeleton and shared fixtures**

Create `GraphExpansionEngineTest.java` with this starting content:

```java
package com.openmemind.ai.memory.core.retrieval.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GraphExpansionEngineTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();
    private static final RetrievalConfig CONFIG = RetrievalConfig.simple();
    private static final QueryContext CONTEXT =
            new QueryContext(
                    MEMORY_ID, "what changed this week", null, List.of(), Map.of(), null, null);
    private static final SimpleStrategyConfig.GraphAssistConfig SETTINGS =
            new SimpleStrategyConfig.GraphAssistConfig(
                    true,
                    RetrievalGraphMode.ASSIST,
                    2,
                    6,
                    2,
                    2,
                    2,
                    2,
                    2,
                    0.35d,
                    0.55d,
                    0.70f,
                    2,
                    0.5d,
                    Duration.ofMillis(200));
    private static final Instant NOW = Instant.parse("2026-04-17T00:00:00Z");

    @Mock private MemoryStore store;
    @Mock private ItemOperations itemOperations;

    private InMemoryGraphOperations graphOperations;
    private Map<Long, MemoryItem> itemsById;
    private GraphExpansionEngine engine;

    @BeforeEach
    void setUp() {
        graphOperations = new InMemoryGraphOperations();
        itemsById = new ConcurrentHashMap<>();
        seedItems();
        seedGraph();

        lenient().when(store.itemOperations()).thenReturn(itemOperations);
        lenient().when(store.graphOperations()).thenReturn(graphOperations);
        lenient()
                .when(itemOperations.getItemsByIds(eq(MEMORY_ID), any(Collection.class)))
                .thenAnswer(
                        invocation ->
                                invocation.<Collection<Long>>getArgument(1).stream()
                                        .map(itemsById::get)
                                        .filter(java.util.Objects::nonNull)
                                        .toList());

        engine = new GraphExpansionEngine(store);
    }

    private List<ScoredResult> directSeeds() {
        return List.of(scored("101", 1.0d), scored("102", 0.9d), scored("103", 0.8d));
    }

    private List<ScoredResult> directWithOverlap() {
        return List.of(scored("101", 1.0d), scored("102", 0.9d));
    }

    private static ScoredResult scored(String sourceId, double score) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM,
                sourceId,
                "result " + sourceId,
                (float) score,
                score,
                NOW);
    }

    private MemoryItem item(long id, String content, Instant occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.WORK,
                MemoryCategory.FACT,
                null,
                null,
                null,
                null,
                occurredAt,
                null,
                Map.of(),
                occurredAt,
                MemoryItemType.FACT);
    }

    private ItemLink link(long sourceId, long targetId, ItemLinkType type, double strength) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceId,
                targetId,
                type,
                defaultRelationCode(type),
                defaultEvidenceSource(type),
                strength,
                Map.of(),
                NOW);
    }

    private static String defaultRelationCode(ItemLinkType type) {
        return switch (type) {
            case SEMANTIC -> null;
            case TEMPORAL -> "before";
            case CAUSAL -> "caused_by";
        };
    }

    private static String defaultEvidenceSource(ItemLinkType type) {
        return type == ItemLinkType.SEMANTIC ? "vector_search" : null;
    }

    private void seedItems() {
        itemsById.put(101L, item(101L, "rolled out the OpenAI migration", Instant.parse("2026-04-16T10:00:00Z")));
        itemsById.put(102L, item(102L, "documented the deployment outcome", Instant.parse("2026-04-16T11:00:00Z")));
        itemsById.put(103L, item(103L, "older direct tail item", Instant.parse("2026-04-10T10:00:00Z")));
        itemsById.put(201L, item(201L, "the migration caused a latency drop", Instant.parse("2026-04-16T12:00:00Z")));
        itemsById.put(301L, item(301L, "temporal neighbor", Instant.parse("2026-04-16T13:00:00Z")));
        itemsById.put(401L, item(401L, "OpenAI sibling item A", Instant.parse("2026-04-16T14:00:00Z")));
        itemsById.put(402L, item(402L, "OpenAI sibling item B", Instant.parse("2026-04-16T15:00:00Z")));
        itemsById.put(403L, item(403L, "OpenAI sibling item C", Instant.parse("2026-04-16T16:00:00Z")));
    }

    private void seedGraph() {
        graphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 201L, ItemLinkType.CAUSAL, 0.95d),
                        link(101L, 102L, ItemLinkType.SEMANTIC, 0.91d),
                        link(102L, 301L, ItemLinkType.TEMPORAL, 0.90d)));
        graphOperations.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(101L, "organization:openai", 0.95f),
                        mention(401L, "organization:openai", 0.95f),
                        mention(402L, "organization:openai", 0.95f),
                        mention(403L, "organization:openai", 0.95f)));
    }

    private ItemEntityMention mention(long itemId, String entityKey, float confidence) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, confidence, Map.of(), NOW);
    }
}
```

- [ ] **Step 2: Add disabled and empty-seed tests**

Add these methods inside `GraphExpansionEngineTest`:

```java
@Test
void disabledSettingsReturnsEmpty() {
    var result = engine.expand(CONTEXT, CONFIG, SETTINGS.withEnabled(false), directSeeds());

    assertThat(result.enabled()).isFalse();
    assertThat(result.graphItems()).isEmpty();
    assertThat(result.seedCount()).isZero();
}

@Test
void emptySeedsReturnsEmpty() {
    var result = engine.expand(CONTEXT, CONFIG, SETTINGS, List.of());

    assertThat(result.enabled()).isTrue();
    assertThat(result.graphItems()).isEmpty();
    assertThat(result.seedCount()).isZero();
}
```

- [ ] **Step 3: Add graph expansion/direct exclusion test**

Add this method:

```java
@Test
void expandsGraphCandidatesAndExcludesDirectInputIds() {
    var result = engine.expand(CONTEXT, CONFIG, SETTINGS, directSeeds());

    assertThat(result.graphItems())
            .extracting(ScoredResult::sourceId)
            .contains("201", "301")
            .doesNotContain("101", "102", "103");
    assertThat(result.seedCount()).isEqualTo(2);
    assertThat(result.linkExpansionCount()).isGreaterThanOrEqualTo(2);
    assertThat(result.dedupedCandidateCount()).isGreaterThanOrEqualTo(2);
}
```

- [ ] **Step 4: Add overlap diagnostic test**

Add this method:

```java
@Test
void tracksDirectOverlapWithoutReturningOverlapItems() {
    var oneSeed = SETTINGS.withMaxSeedItems(1);

    var result = engine.expand(CONTEXT, CONFIG, oneSeed, directWithOverlap());

    assertThat(result.overlapCount()).isEqualTo(1);
    assertThat(result.graphItems()).extracting(ScoredResult::sourceId).doesNotContain("102");
}
```

- [ ] **Step 5: Add entity fanout guard test**

Add this method:

```java
@Test
void skipsOverFanoutEntityExpansion() {
    var result = engine.expand(CONTEXT, CONFIG, SETTINGS, directSeeds());

    assertThat(result.skippedOverFanoutEntityCount()).isEqualTo(1);
    assertThat(result.graphItems()).extracting(ScoredResult::sourceId).doesNotContain("401", "402", "403");
}
```

- [ ] **Step 6: Run tests to verify current engine fails**

Run:

```bash
mvn -pl memind-core -Dtest=GraphExpansionEngineTest test
```

Expected: compile failure because `new GraphExpansionEngine(store)` does not exist yet, or test failures because the current engine still wraps `RetrievalGraphAssistant`.

---

## Task 3: Implement GraphExpansionEngine as Pure Core

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngine.java`
- Reference: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistant.java`

- [ ] **Step 1: Replace constructor dependency**

In `GraphExpansionEngine.java`, replace the field and constructor:

```java
private final RetrievalGraphAssistant graphAssistant;

public GraphExpansionEngine(RetrievalGraphAssistant graphAssistant) {
    this.graphAssistant = Objects.requireNonNull(graphAssistant, "graphAssistant");
}
```

with:

```java
private final MemoryStore store;

public GraphExpansionEngine(MemoryStore store) {
    this.store = Objects.requireNonNull(store, "store");
}
```

Add imports copied from `DefaultRetrievalGraphAssistant` that are needed by moved low-level logic:

```java
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.retrieval.ItemRetrievalGuard;
import com.openmemind.ai.memory.core.retrieval.scoring.TimeDecay;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
```

- [ ] **Step 2: Copy static constants and helper records**

Move these from `DefaultRetrievalGraphAssistant` to `GraphExpansionEngine`:

```java
private static final List<ItemLinkType> SUPPORTED_LINK_TYPES =
        List.of(ItemLinkType.SEMANTIC, ItemLinkType.TEMPORAL, ItemLinkType.CAUSAL);
private static final Comparator<SeedAdjacentLink> SEED_ADJACENT_LINK_ORDER =
        Comparator.comparing(SeedAdjacentLink::overlap)
                .reversed()
                .thenComparing(SeedAdjacentLink::family, relationFamilyOrder())
                .thenComparing(SeedAdjacentLink::strength, Comparator.reverseOrder())
                .thenComparingLong(SeedAdjacentLink::neighborItemId)
                .thenComparingLong(link -> link.link().sourceItemId())
                .thenComparingLong(link -> link.link().targetItemId());
private static final Map<RelationFamily, Double> DEFAULT_RELATION_WEIGHTS =
        Map.of(
                RelationFamily.SEMANTIC, 1.00d,
                RelationFamily.TEMPORAL, 0.90d,
                RelationFamily.CAUSAL, 0.95d,
                RelationFamily.ENTITY_SIBLING, 0.85d);
```

Also move these nested types from assistant to engine:

```java
private record MaterializedSeed(MemoryItem item, double seedRelevance) {}

private record SeedAdjacentLink(
        ItemLink link,
        long seedItemId,
        long neighborItemId,
        RelationFamily family,
        boolean overlap,
        double strength) {}

private record GraphCandidate(
        long itemId, double score, String content, java.time.Instant occurredAt) {}

private enum RelationFamily {
    SEMANTIC,
    TEMPORAL,
    CAUSAL,
    ENTITY_SIBLING
}

private record GraphCandidateBundle(
        Map<Long, GraphCandidate> candidates,
        int linkExpansionCount,
        int entityExpansionCount,
        int overlapCount,
        int skippedOverFanoutEntityCount) {}

private record ReverseMentionBundle(
        Map<String, List<ItemEntityMention>> mentionsByEntityKey,
        Set<String> overFanoutEntityKeys) {

    private static ReverseMentionBundle empty() {
        return new ReverseMentionBundle(Map.of(), Set.of());
    }
}
```

Move `GraphCandidateAccumulator` as-is from assistant to engine.

- [ ] **Step 3: Implement pure expand method**

Replace the current `expand(...)` body with:

```java
public GraphExpansionResult expand(
        QueryContext context,
        RetrievalConfig config,
        RetrievalGraphSettings settings,
        List<ScoredResult> seeds) {
    boolean enabled = settings != null && settings.enabled();
    if (!enabled || seeds == null || seeds.isEmpty() || store == null) {
        return GraphExpansionResult.empty(enabled);
    }

    var directIds =
            seeds.stream()
                    .map(ScoredResult::sourceId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
    var materializedSeeds = materializeEligibleSeeds(context, seeds, settings.maxSeedItems());
    if (materializedSeeds.isEmpty()) {
        return GraphExpansionResult.empty(true);
    }

    var candidateBundle = collectGraphCandidates(context, config, settings, materializedSeeds, directIds);
    var rankedGraph = rankGraphCandidates(candidateBundle.candidates()).stream()
            .filter(candidate -> !directIds.contains(candidate.sourceId()))
            .toList();

    return new GraphExpansionResult(
            rankedGraph,
            true,
            false,
            false,
            materializedSeeds.size(),
            candidateBundle.linkExpansionCount(),
            candidateBundle.entityExpansionCount(),
            candidateBundle.candidates().size(),
            candidateBundle.overlapCount(),
            candidateBundle.skippedOverFanoutEntityCount());
}
```

- [ ] **Step 4: Move low-level helper methods**

Move these methods from `DefaultRetrievalGraphAssistant` to `GraphExpansionEngine` without behavior changes:

```text
materializeEligibleSeeds
collectGraphCandidates
indexAdjacentLinksBySeed
loadReverseMentionBundle
rankGraphCandidates
seedAdjacentLink
toRelationFamily
relationFamilyOrder
maxNeighborsPerSeed
baseScore
hasEntitySignal
isSpecialEntity
parseItemId
```

Keep method bodies identical except for references to the engine's `store` field.

- [ ] **Step 5: Run engine tests**

Run:

```bash
mvn -pl memind-core -Dtest=GraphExpansionEngineTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngine.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngineTest.java
git commit -m "refactor: make graph expansion engine first class"
```

---

## Task 4: Refactor DefaultRetrievalGraphAssistant into Fusion Adapter

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistant.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistantTest.java`

- [ ] **Step 1: Add engine field and constructors**

In `DefaultRetrievalGraphAssistant`, replace:

```java
private final MemoryStore store;

public DefaultRetrievalGraphAssistant(MemoryStore store) {
    this.store = store;
}
```

with:

```java
private final GraphExpansionEngine graphExpansionEngine;

public DefaultRetrievalGraphAssistant(MemoryStore store) {
    this(new GraphExpansionEngine(store));
}

public DefaultRetrievalGraphAssistant(GraphExpansionEngine graphExpansionEngine) {
    this.graphExpansionEngine = Objects.requireNonNull(graphExpansionEngine, "graphExpansionEngine");
}
```

Keep `Objects` import.

- [ ] **Step 2: Update assist null/disabled handling**

In `assist(...)`, replace the store null check:

```java
if (!enabled || directItems == null || directItems.isEmpty() || store == null) {
```

with:

```java
if (!enabled || directItems == null || directItems.isEmpty()) {
```

- [ ] **Step 3: Replace expandAndFuse implementation**

Replace the first half of `expandAndFuse(...)` with:

```java
private RetrievalGraphAssistResult expandAndFuse(
        QueryContext context,
        RetrievalConfig config,
        RetrievalGraphSettings graphSettings,
        List<ScoredResult> directItems) {
    var graphResult = graphExpansionEngine.expand(context, config, graphSettings, directItems);
    if (graphResult.graphItems().isEmpty() && graphResult.seedCount() == 0) {
        return RetrievalGraphAssistResult.directOnly(directItems, true);
    }

    var rankedGraph = graphResult.graphItems();
    var finalItems =
            switch (graphSettings.mode()) {
                case ASSIST -> fuseAssistMode(graphSettings, directItems, rankedGraph);
                case EXPAND -> fuseExpandMode(graphSettings, directItems, rankedGraph);
            };
    var directIds =
            directItems.stream()
                    .map(ScoredResult::sourceId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
    int admittedGraphCandidateCount =
            (int)
                    finalItems.stream()
                            .map(ScoredResult::sourceId)
                            .filter(sourceId -> !directIds.contains(sourceId))
                            .count();

    return new RetrievalGraphAssistResult(
            finalItems,
            new RetrievalGraphAssistResult.GraphAssistStats(
                    graphResult.enabled(),
                    graphResult.degraded(),
                    graphResult.timedOut(),
                    graphResult.seedCount(),
                    graphResult.linkExpansionCount(),
                    graphResult.entityExpansionCount(),
                    graphResult.dedupedCandidateCount(),
                    admittedGraphCandidateCount,
                    countDisplacedDirectItems(directItems, finalItems, directItems.size()),
                    graphResult.overlapCount(),
                    graphResult.skippedOverFanoutEntityCount()));
}
```

- [ ] **Step 4: Remove low-level expansion methods from assistant**

Delete these from `DefaultRetrievalGraphAssistant` after confirming they now exist in `GraphExpansionEngine`:

```text
SUPPORTED_LINK_TYPES
SEED_ADJACENT_LINK_ORDER
DEFAULT_RELATION_WEIGHTS
materializeEligibleSeeds
collectGraphCandidates
indexAdjacentLinksBySeed
loadReverseMentionBundle
rankGraphCandidates
seedAdjacentLink
toRelationFamily
relationFamilyOrder
maxNeighborsPerSeed
baseScore
hasEntitySignal
isSpecialEntity
parseItemId
MaterializedSeed
SeedAdjacentLink
GraphCandidateAccumulator
GraphCandidate
RelationFamily
GraphCandidateBundle
ReverseMentionBundle
```

Keep fusion-only methods in assistant:

```text
fuseAssistMode
fuseExpandMode
buildFusionCandidates
boundedGraphBonus
graphOnlyScore
requireItemId
rescore
countDisplacedDirectItems
```

- [ ] **Step 5: Make overlap no-boost behavior explicit**

This refactor intentionally changes assistant fusion so direct-overlap graph candidates are tracked for diagnostics but are not returned in `GraphExpansionResult.graphItems()`. As a result, `buildFusionCandidates(...)` no longer receives overlap candidates and does not apply `boundedGraphBonus(...)` to direct items. This matches the design: overlap is diagnostic only, not a direct-score boost.

Add a dedicated test that places the overlapping direct item in the unprotected tail. This test fails before the refactor because `buildFusionCandidates(...)` can apply `boundedGraphBonus(...)` when overlap candidates are present in `rankedGraph`; it passes after `GraphExpansionEngine` excludes direct ids from `graphItems()`.

```java
@Test
void unprotectedDirectOverlapIsTrackedButNotBoosted() {
    var settings = OVERLAP_CONFIG.graphAssist().withProtectDirectTopK(0);

    StepVerifier.create(assistant.assist(CONTEXT, CONFIG, settings, directWithOverlap()))
            .assertNext(
                    result -> {
                        var overlappingDirect =
                                result.items().stream()
                                        .filter(item -> item.sourceId().equals("102"))
                                        .findFirst()
                                        .orElseThrow();
                        assertThat(overlappingDirect.finalScore()).isEqualTo(0.9d);
                        assertThat(result.stats().overlapCount()).isEqualTo(1);
                    })
            .verifyComplete();
}
```

Keep the existing `overlappingDirectCandidatesAreTrackedButNeverBoosted` test for pinned-prefix behavior and duplicate protection.

- [ ] **Step 6: Add assistant stats-copy test**

In `DefaultRetrievalGraphAssistantTest`, add:

```java
@Test
void assistantCopiesEngineStatsAndComputesFusionStats() {
    StepVerifier.create(assistant.assist(CONTEXT, CONFIG, SIMPLE_CONFIG.graphAssist(), directSeeds()))
            .assertNext(
                    result -> {
                        assertThat(result.stats().seedCount()).isGreaterThan(0);
                        assertThat(result.stats().linkExpansionCount()).isGreaterThan(0);
                        assertThat(result.stats().dedupedCandidateCount()).isGreaterThan(0);
                        assertThat(result.stats().admittedGraphCandidateCount()).isGreaterThan(0);
                        assertThat(result.stats().displacedDirectCount()).isGreaterThanOrEqualTo(0);
                    })
            .verifyComplete();
}
```

- [ ] **Step 7: Run assistant tests**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultRetrievalGraphAssistantTest test
```

Expected: tests pass.

- [ ] **Step 8: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistant.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistantTest.java
git commit -m "refactor: make graph assistant reuse expansion engine"
```

---

## Task 5: Update GraphItemChannel Budget Context and Tests

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannel.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannelTest.java`

- [ ] **Step 1: Update channel implementation**

In `DefaultGraphItemChannel.retrieve(...)`, replace:

```java
return Mono.fromCallable(() -> engine.expand(context, config, settings, seeds))
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(shorterPositive(settings.timeout(), config.timeout()))
```

with:

```java
Duration effectiveTimeout = shorterPositive(settings.timeout(), config.timeout());
return Mono.fromCallable(
                () -> {
                    try (var ignored =
                            com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext
                                    .open(effectiveTimeout)) {
                        return engine.expand(context, config, settings, seeds);
                    }
                })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(effectiveTimeout)
```

Then add an import and simplify the fully qualified name:

```java
import com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext;
```

Final lambda should be:

```java
Duration effectiveTimeout = shorterPositive(settings.timeout(), config.timeout());
return Mono.fromCallable(
                () -> {
                    try (var ignored = GraphQueryBudgetContext.open(effectiveTimeout)) {
                        return engine.expand(context, config, settings, seeds);
                    }
                })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(effectiveTimeout)
```

- [ ] **Step 2: Update channel tests to use store-backed engines**

Replace all tests that construct `new GraphExpansionEngine((RetrievalGraphAssistant) ...)`.

For disabled/no-seeds tests, create this helper:

```java
private static GraphExpansionEngine noOpEngine() {
    return new GraphExpansionEngine(new com.openmemind.ai.memory.core.store.InMemoryMemoryStore());
}
```

Then use:

```java
var channel = new DefaultGraphItemChannel(noOpEngine());
```

Replace `returnsGraphOnlyCandidatesAndExcludesSeeds` with a store-backed graph fixture:

```java
@Test
void returnsGraphOnlyCandidatesAndExcludesSeeds() {
    var store = new com.openmemind.ai.memory.core.store.InMemoryMemoryStore();
    var occurredAt = java.time.Instant.parse("2026-04-17T00:00:00Z");
    var seedItem = item(1L, "seed", occurredAt);
    var graphItem = item(2L, "graph", occurredAt);
    store.itemOperations().insertItems(MEMORY_ID, List.of(seedItem, graphItem));
    store.graphOperations()
            .upsertItemLinks(
                    MEMORY_ID,
                    List.of(
                            new com.openmemind.ai.memory.core.store.graph.ItemLink(
                                    MEMORY_ID.toIdentifier(),
                                    1L,
                                    2L,
                                    com.openmemind.ai.memory.core.store.graph.ItemLinkType.SEMANTIC,
                                    null,
                                    "vector_search",
                                    0.90d,
                                    Map.of(),
                                    occurredAt)));
    var channel = new DefaultGraphItemChannel(new GraphExpansionEngine(store));

    var result =
            channel.retrieve(CONTEXT, CONFIG, ENABLED_SETTINGS, List.of(seed("1"))).block();

    assertThat(result).isNotNull();
    assertThat(result.graphItems()).extracting(ScoredResult::sourceId).containsExactly("2");
    assertThat(result.seedCount()).isEqualTo(1);
    assertThat(result.dedupedCandidateCount()).isEqualTo(1);
}
```

Add this helper to `DefaultGraphItemChannelTest`:

```java
private static MemoryItem item(long id, String content, java.time.Instant occurredAt) {
    return new MemoryItem(
            id,
            MEMORY_ID.toIdentifier(),
            content,
            MemoryScope.WORK,
            MemoryCategory.FACT,
            null,
            null,
            null,
            null,
            occurredAt,
            null,
            Map.of(),
            occurredAt,
            MemoryItemType.FACT);
}
```

Replace `assistantFailureReturnsDegradedEmptyResult` with a store-backed failure test:

```java
@Test
void engineFailureReturnsDegradedEmptyResult() {
    MemoryStore store = mock(MemoryStore.class);
    when(store.itemOperations()).thenThrow(new IllegalStateException("boom"));
    var channel = new DefaultGraphItemChannel(new GraphExpansionEngine(store));

    var result =
            channel.retrieve(CONTEXT, CONFIG, ENABLED_SETTINGS, List.of(seed("1"))).block();

    assertThat(result).isNotNull();
    assertThat(result.degraded()).isTrue();
    assertThat(result.timedOut()).isFalse();
    assertThat(result.graphItems()).isEmpty();
}
```

- [ ] **Step 3: Add Mockito imports for ThreadLocal budget test**

Add these imports to `DefaultGraphItemChannelTest`:

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
```

- [ ] **Step 4: Add ThreadLocal budget test**

Add this test to `DefaultGraphItemChannelTest`:

```java
@Test
void opensGraphQueryBudgetContextInsideWorkerThread() {
    var observedTimeout = new AtomicReference<Duration>();
    MemoryStore store = mock(MemoryStore.class);
    ItemOperations itemOperations = mock(ItemOperations.class);
    GraphOperations graphOperations = mock(GraphOperations.class);
    var occurredAt = Instant.parse("2026-04-17T00:00:00Z");
    var seedItem =
            new MemoryItem(
                    1L,
                    MEMORY_ID.toIdentifier(),
                    "seed",
                    MemoryScope.WORK,
                    MemoryCategory.FACT,
                    null,
                    null,
                    null,
                    null,
                    occurredAt,
                    null,
                    Map.of(),
                    occurredAt,
                    MemoryItemType.FACT);

    when(store.itemOperations()).thenReturn(itemOperations);
    when(store.graphOperations()).thenReturn(graphOperations);
    when(itemOperations.getItemsByIds(eq(MEMORY_ID), any(Collection.class)))
            .thenReturn(List.of(seedItem));
    when(graphOperations.listItemEntityMentions(eq(MEMORY_ID), any(Collection.class)))
            .thenReturn(List.of());
    when(graphOperations.listAdjacentItemLinks(eq(MEMORY_ID), any(Collection.class), any(Collection.class)))
            .thenAnswer(
                    invocation -> {
                        observedTimeout.set(GraphQueryBudgetContext.currentTimeout().orElse(null));
                        return List.of();
                    });

    var channel = new DefaultGraphItemChannel(new GraphExpansionEngine(store));
    var settings = ENABLED_SETTINGS.withTimeout(Duration.ofMillis(123));

    channel.retrieve(CONTEXT, CONFIG, settings, List.of(seed("1"))).block();

    assertThat(observedTimeout.get()).isEqualTo(Duration.ofMillis(123));
}
```

This test proves `GraphQueryBudgetContext.open(...)` runs on the same worker thread as the graph store read triggered by `engine.expand(...)`.

- [ ] **Step 5: Run channel tests**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultGraphItemChannelTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannel.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultGraphItemChannelTest.java
git commit -m "fix: apply graph query budget in graph channel"
```

---

## Task 6: Update Assembler Wiring

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryRetrievalAssembler.java`

- [ ] **Step 1: Change default graph wiring**

In `assemble(...)`, replace:

```java
RetrievalGraphAssistant graphAssistant = buildGraphAssistant(context);
var graphItemChannel =
        new DefaultGraphItemChannel(new GraphExpansionEngine(graphAssistant));
```

with:

```java
var graphExpansionEngine = new GraphExpansionEngine(context.memoryStore());
RetrievalGraphAssistant graphAssistant = buildGraphAssistant(context, graphExpansionEngine);
var graphItemChannel = new DefaultGraphItemChannel(graphExpansionEngine);
```

- [ ] **Step 2: Update buildGraphAssistant signature**

Find `buildGraphAssistant` in `MemoryRetrievalAssembler` and change its signature from:

```java
private RetrievalGraphAssistant buildGraphAssistant(MemoryAssemblyContext context) {
```

to:

```java
private RetrievalGraphAssistant buildGraphAssistant(
        MemoryAssemblyContext context, GraphExpansionEngine graphExpansionEngine) {
```

- [ ] **Step 3: Preserve the existing traced assistant behavior**

Replace the current `buildGraphAssistant(...)` body with:

```java
private RetrievalGraphAssistant buildGraphAssistant(
        MemoryAssemblyContext context, GraphExpansionEngine graphExpansionEngine) {
    return new TracingRetrievalGraphAssistant(
            new DefaultRetrievalGraphAssistant(graphExpansionEngine),
            context.memoryObserver());
}
```

Do not change the `RetrievalGraphAssistant` interface. This keeps custom assistant compatibility at the interface boundary even though the current assembler only constructs the default traced assistant.

- [ ] **Step 4: Run compile-focused tests**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultMemoryContextTest test
```

Expected: tests pass and assembler compiles.

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/builder/MemoryRetrievalAssembler.java
git commit -m "refactor: share graph expansion engine in retrieval assembler"
```

---

## Task 7: Full Graph Test Sweep

**Files:**
- Verify only; no planned production changes.

- [ ] **Step 1: Run all graph retrieval tests**

Run:

```bash
mvn -pl memind-core -Dtest='GraphExpansionEngineTest,DefaultGraphItemChannelTest,DefaultRetrievalGraphAssistantTest' test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run package-level related tests**

Run:

```bash
mvn -pl memind-core -Dtest='*Graph*Test,*Retrieval*Test' test
```

Expected: all selected tests pass. If unrelated tests are included and fail for pre-existing reasons, capture the exact failing test names and run the narrower graph suite again before proceeding.

- [ ] **Step 3: Inspect dependency direction**

Run:

```bash
rg "RetrievalGraphAssistant" memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngine.java
```

Expected: no output.

Run:

```bash
rg "new GraphExpansionEngine\(graphAssistant|assist\(context, config, settings, seeds\)" memind-core/src/main/java memind-core/src/test/java
```

Expected: no output.

- [ ] **Step 4: Commit test-only cleanups if needed**

If Task 7 required test cleanups, commit them:

```bash
git add memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/graph
git commit -m "test: align graph retrieval tests with expansion engine"
```

If no files changed, skip this commit.

---

## Task 8: Full Verification

**Files:**
- Verify only.

- [ ] **Step 1: Run core module tests**

Run:

```bash
mvn -pl memind-core test
```

Expected: build success; currently this module has 1000+ tests and should report zero failures/errors.

- [ ] **Step 2: Run full reactor tests**

Run:

```bash
mvn test
```

Expected: full reactor `BUILD SUCCESS`.

- [ ] **Step 3: Check working tree**

Run:

```bash
git status --short
```

Expected: no output after all commits, or only intentionally uncommitted files that are documented before handoff.

- [ ] **Step 4: Final handoff summary**

Report:

```text
Implemented first-class GraphExpansionEngine.
Verification:
- mvn -pl memind-core test: PASS
- mvn test: PASS
Commits:
- <list commit hashes and messages>
```

Do not claim success unless both verification commands were run in the current session and passed.
