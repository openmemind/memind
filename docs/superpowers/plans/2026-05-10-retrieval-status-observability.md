# Retrieval Status Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface retrieval failure status to callers via a `RetrievalStatus` enum on `RetrievalResult` and API response, so consumers can distinguish "no memories" from "retrieval failed."

**Architecture:** Add a `RetrievalStatus` enum (`SUCCESS`/`EMPTY`/`DEGRADED`) as a field on `RetrievalResult`. The `of()` factory determines status from content at construction time. `DefaultMemoryRetriever.onErrorResume` uses `degraded()` instead of `empty()`. API response exposes the status string. Python scripts inject a notice on degraded.

**Tech Stack:** Java 21 (records, enums), Reactor (Mono), Python 3, Maven, JUnit 5, AssertJ

---

### Task 1: Add `RetrievalStatus` enum and update `RetrievalResult`

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/RetrievalStatus.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/RetrievalResult.java`

- [ ] **Step 1: Create `RetrievalStatus` enum**

```java
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.openmemind.ai.memory.core.retrieval;

/**
 * Indicates the outcome of a retrieval operation.
 *
 * <ul>
 *   <li>{@link #SUCCESS} — retrieval completed normally, results found
 *   <li>{@link #EMPTY} — retrieval completed normally, no matching results
 *   <li>{@link #DEGRADED} — retrieval encountered an error, returning fallback empty result
 * </ul>
 */
public enum RetrievalStatus {
    SUCCESS,
    EMPTY,
    DEGRADED
}
```

- [ ] **Step 2: Modify `RetrievalResult` record — add `status` field and factories**

Replace the full record definition in `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/RetrievalResult.java`:

```java
/**
 * Retrieval result (three-layer structure)
 *
 * @param items     Item original text scoring result list (sorted by finalScore in descending order)
 * @param insights  Insight result list (sorted by LLM, no scores)
 * @param rawData   RawData caption aggregation result list (sorted by finalScore in descending order)
 * @param evidences Tier1 key sentences extracted when sufficient; empty list when insufficient
 * @param strategy  Name of the strategy used
 * @param query     Actual query text used (may have been rewritten)
 * @param status    Outcome of the retrieval operation
 */
public record RetrievalResult(
        List<ScoredResult> items,
        List<InsightResult> insights,
        List<RawDataResult> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalStatus status) {
```

Add factory methods (replace existing `empty` method and add `of` and `degraded`):

```java
    /**
     * Standard factory for strategy implementations.
     * Determines status from content: EMPTY if no data, SUCCESS otherwise.
     */
    public static RetrievalResult of(
            List<ScoredResult> items,
            List<InsightResult> insights,
            List<RawDataResult> rawData,
            List<String> evidences,
            String strategy,
            String query) {
        boolean empty = (items == null || items.isEmpty())
                && (insights == null || insights.isEmpty())
                && (rawData == null || rawData.isEmpty());
        return new RetrievalResult(
                items, insights, rawData, evidences, strategy, query,
                empty ? RetrievalStatus.EMPTY : RetrievalStatus.SUCCESS);
    }

    /** Empty result (normal — no matching memories) */
    public static RetrievalResult empty(String strategy, String query) {
        return new RetrievalResult(
                List.of(), List.of(), List.of(), List.of(), strategy, query, RetrievalStatus.EMPTY);
    }

    /** Degraded result (error occurred, fallback empty) */
    public static RetrievalResult degraded(String strategy, String query) {
        return new RetrievalResult(
                List.of(), List.of(), List.of(), List.of(), strategy, query, RetrievalStatus.DEGRADED);
    }
```

- [ ] **Step 3: Fix all `new RetrievalResult(...)` call sites in tests**

These 4 files use the canonical constructor directly and need the `status` parameter appended:

`memind-server/src/test/.../OpenMemoryApplicationServiceTest.java:159`:
```java
        memory.retrieveResult =
                RetrievalResult.of(
                        List.of(
                                new ScoredResult(
                                        ScoredResult.SourceType.ITEM,
                                        "item-1",
                                        "loves coffee",
                                        0.82F,
                                        0.91,
                                        Instant.parse("2026-03-30T10:00:00Z"))),
                        List.of(
                                new RetrievalResult.InsightResult(
                                        "insight-1", "prefers concise answers", InsightTier.LEAF)),
                        List.of(
                                new RetrievalResult.RawDataResult(
                                        "rd-1", "user mentioned coffee", 0.76, List.of("item-1"))),
                        List.of("user likes coffee"),
                        "SIMPLE",
                        "coffee");
```

`memind-core/src/test/.../DefaultMemoryContextTest.java:170` — change `new RetrievalResult(...)` to `RetrievalResult.of(...)`.

`memind-core/src/test/.../CaffeineRetrievalCacheTest.java:48` — change `new RetrievalResult(...)` to `RetrievalResult.of(...)`.

`memind-core/src/test/.../ContextWindowTest.java:118` — change `new RetrievalResult(...)` to `RetrievalResult.of(...)`.

- [ ] **Step 4: Compile to verify**

Run: `mvn compile -pl memind-core,memind-server -q`
Expected: BUILD SUCCESS (no compilation errors)

- [ ] **Step 5: Run existing tests to verify no regressions**

Run: `mvn test -pl memind-core -Dtest="DefaultMemoryRetrieverTest,CaffeineRetrievalCacheTest,DefaultMemoryContextTest" -q`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/RetrievalStatus.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/RetrievalResult.java \
        memind-core/src/test/ \
        memind-server/src/test/
git commit -m "feat(retrieval): add RetrievalStatus enum and update RetrievalResult

Add SUCCESS/EMPTY/DEGRADED status to RetrievalResult. The of() factory
determines status from content at construction time. Migrate all direct
constructor usages to of() or empty() factories.

Refs: #46"
```

---

### Task 2: Update strategy implementations and DefaultMemoryRetriever

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/SimpleRetrievalStrategy.java:577`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/DeepRetrievalStrategy.java:801`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/DefaultMemoryRetriever.java:322-331`

- [ ] **Step 1: Write test for DEGRADED status on retrieval error**

Add to `memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/DefaultMemoryRetrieverTest.java`:

```java
    @Test
    @DisplayName("strategy error should return DEGRADED status instead of EMPTY")
    void strategyErrorShouldReturnDegradedStatus() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var itemOperations = mock(ItemOperations.class);
        var strategy = mock(RetrievalStrategy.class);
        when(store.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.hasItems(memoryId)).thenReturn(true);
        when(cache.get(eq(memoryId), anyString(), anyString())).thenReturn(Optional.empty());
        when(strategy.name()).thenReturn("simple");
        when(strategy.retrieve(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("vector store unavailable")));

        var retriever = new DefaultMemoryRetriever(cache, store);
        retriever.registerStrategy(strategy);

        var result =
                retriever
                        .retrieve(
                                RetrievalRequest.of(
                                        memoryId, "hello", RetrievalConfig.Strategy.SIMPLE))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.status()).isEqualTo(RetrievalStatus.DEGRADED);
    }

    @Test
    @DisplayName("successful retrieval with results should have SUCCESS status")
    void successfulRetrievalWithResultsShouldHaveSuccessStatus() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var itemOperations = mock(ItemOperations.class);
        var strategy = mock(RetrievalStrategy.class);
        when(store.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.hasItems(memoryId)).thenReturn(true);
        when(cache.get(eq(memoryId), anyString(), anyString())).thenReturn(Optional.empty());
        when(strategy.name()).thenReturn("simple");
        when(strategy.retrieve(any(), any()))
                .thenReturn(
                        Mono.just(
                                RetrievalResult.of(
                                        List.of(
                                                new ScoredResult(
                                                        ScoredResult.SourceType.ITEM,
                                                        "item-1",
                                                        "test",
                                                        0.9F,
                                                        0.9,
                                                        null)),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        "simple",
                                        "hello")));

        var retriever = new DefaultMemoryRetriever(cache, store);
        retriever.registerStrategy(strategy);

        var result =
                retriever
                        .retrieve(
                                RetrievalRequest.of(
                                        memoryId, "hello", RetrievalConfig.Strategy.SIMPLE))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(RetrievalStatus.SUCCESS);
    }

    @Test
    @DisplayName("successful retrieval with no results should have EMPTY status")
    void successfulRetrievalWithNoResultsShouldHaveEmptyStatus() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var itemOperations = mock(ItemOperations.class);
        var strategy = mock(RetrievalStrategy.class);
        when(store.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.hasItems(memoryId)).thenReturn(true);
        when(cache.get(eq(memoryId), anyString(), anyString())).thenReturn(Optional.empty());
        when(strategy.name()).thenReturn("simple");
        when(strategy.retrieve(any(), any()))
                .thenReturn(
                        Mono.just(
                                RetrievalResult.of(
                                        List.of(), List.of(), List.of(), List.of(),
                                        "simple", "hello")));

        var retriever = new DefaultMemoryRetriever(cache, store);
        retriever.registerStrategy(strategy);

        var result =
                retriever
                        .retrieve(
                                RetrievalRequest.of(
                                        memoryId, "hello", RetrievalConfig.Strategy.SIMPLE))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(RetrievalStatus.EMPTY);
    }
```

Add import at top of test file:
```java
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
```

- [ ] **Step 2: Run new tests to verify they fail**

Run: `mvn test -pl memind-core -Dtest="DefaultMemoryRetrieverTest#strategyErrorShouldReturnDegradedStatus+successfulRetrievalWithResultsShouldHaveSuccessStatus+successfulRetrievalWithNoResultsShouldHaveEmptyStatus" -q`
Expected: `strategyErrorShouldReturnDegradedStatus` FAILS (returns EMPTY instead of DEGRADED). The other two should pass (since `of()` already sets correct status).

- [ ] **Step 3: Update `SimpleRetrievalStrategy`**

In `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/SimpleRetrievalStrategy.java:577`, change:

```java
// Before:
return new RetrievalResult(
        sortedItems, insights, rawDataResults, List.of(), name(), context.searchQuery());

// After:
return RetrievalResult.of(
        sortedItems, insights, rawDataResults, List.of(), name(), context.searchQuery());
```

- [ ] **Step 4: Update `DeepRetrievalStrategy`**

In `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/DeepRetrievalStrategy.java:801`, change:

```java
// Before:
return new RetrievalResult(
        sortedItems,
        insights,
        aggregation.rawDataResults(),
        evidences,
        name(),
        context.searchQuery());

// After:
return RetrievalResult.of(
        sortedItems,
        insights,
        aggregation.rawDataResults(),
        evidences,
        name(),
        context.searchQuery());
```

- [ ] **Step 5: Update `DefaultMemoryRetriever.onErrorResume`**

In `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/DefaultMemoryRetriever.java:322-331`, change:

```java
// Before:
.onErrorResume(
        e -> {
            log.warn(
                    "Retrieval failed, returning empty result:"
                            + " query={}",
                    request.query(),
                    e);
            return Mono.just(
                    RetrievalResult.empty(
                            config.strategyName(), effectiveQuery));
        });

// After:
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
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -pl memind-core -Dtest="DefaultMemoryRetrieverTest" -q`
Expected: All tests pass including the 3 new ones.

- [ ] **Step 7: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/SimpleRetrievalStrategy.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/DeepRetrievalStrategy.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/DefaultMemoryRetriever.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/retrieval/DefaultMemoryRetrieverTest.java
git commit -m "feat(retrieval): use DEGRADED status on error, migrate strategies to of()

DefaultMemoryRetriever.onErrorResume now returns RetrievalResult.degraded()
instead of empty(). Strategies use RetrievalResult.of() which determines
SUCCESS/EMPTY from content automatically.

Refs: #46"
```

---

### Task 3: Update `RetrievalMetricsSupport`

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/metrics/RetrievalMetricsSupport.java:24`

- [ ] **Step 1: Update `summary()` to read status from result**

In `memind-core/src/main/java/com/openmemind/ai/memory/core/metrics/RetrievalMetricsSupport.java:24`, change:

```java
// Before:
String status = result == null || result.isEmpty() ? "empty" : "success";

// After:
String status = result == null ? "empty" : result.status().name().toLowerCase();
```

- [ ] **Step 2: Compile and run existing tests**

Run: `mvn test -pl memind-core -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 3: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/metrics/RetrievalMetricsSupport.java
git commit -m "refactor(metrics): read status from RetrievalResult directly

RetrievalMetricsSupport no longer infers status from isEmpty() — it reads
the authoritative status field from the result.

Refs: #46"
```

---

### Task 4: Update API response layer

**Files:**
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/memory/response/RetrieveMemoryResponse.java`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationService.java:262-302`
- Modify: `memind-server/src/test/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationServiceTest.java`

- [ ] **Step 1: Write test for status in API response**

Add to `memind-server/src/test/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationServiceTest.java`:

```java
    @Test
    void retrieveResponseIncludesStatusFromResult() {
        RecordingMemory memory = new RecordingMemory();
        memory.retrieveResult =
                RetrievalResult.of(
                        List.of(
                                new ScoredResult(
                                        ScoredResult.SourceType.ITEM,
                                        "item-1",
                                        "loves coffee",
                                        0.82F,
                                        0.91,
                                        Instant.parse("2026-03-30T10:00:00Z"))),
                        List.of(),
                        List.of(),
                        List.of(),
                        "SIMPLE",
                        "coffee");
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1));
        OpenMemoryApplicationService service = new OpenMemoryApplicationService(runtimeManager);

        var response =
                service.retrieve(
                        new RetrieveMemoryRequest(
                                "u1", "a1", "coffee", RetrievalConfig.Strategy.SIMPLE));

        assertThat(response.status()).isEqualTo("success");
    }

    @Test
    void retrieveResponseStatusIsDegradedOnError() {
        RecordingMemory memory = new RecordingMemory();
        memory.retrieveResult = RetrievalResult.degraded("SIMPLE", "coffee");
        MemoryRuntimeManager runtimeManager =
                new MemoryRuntimeManager(
                        new RuntimeHandle(memory, MemoryBuildOptions.defaults(), 1));
        OpenMemoryApplicationService service = new OpenMemoryApplicationService(runtimeManager);

        var response =
                service.retrieve(
                        new RetrieveMemoryRequest(
                                "u1", "a1", "coffee", RetrievalConfig.Strategy.SIMPLE));

        assertThat(response.status()).isEqualTo("degraded");
    }
```

Add import:
```java
import com.openmemind.ai.memory.core.retrieval.RetrievalStatus;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl memind-server -Dtest="OpenMemoryApplicationServiceTest#retrieveResponseIncludesStatusFromResult+retrieveResponseStatusIsDegradedOnError" -q`
Expected: FAIL — `RetrieveMemoryResponse` does not have `status()` method yet.

- [ ] **Step 3: Add `status` field to `RetrieveMemoryResponse`**

Modify `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/memory/response/RetrieveMemoryResponse.java`:

```java
public record RetrieveMemoryResponse(
        String status,
        List<RetrievedItemView> items,
        List<RetrievedInsightView> insights,
        List<RetrievedRawDataView> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalTraceView trace) {

    public RetrieveMemoryResponse(
            String status,
            List<RetrievedItemView> items,
            List<RetrievedInsightView> insights,
            List<RetrievedRawDataView> rawData,
            List<String> evidences,
            String strategy,
            String query) {
        this(status, items, insights, rawData, evidences, strategy, query, null);
    }
```

- [ ] **Step 4: Update `toRetrieveResponse` in `OpenMemoryApplicationService`**

In `memind-server/src/main/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationService.java:264`, add `result.status().name().toLowerCase()` as the first argument:

```java
    private static RetrieveMemoryResponse toRetrieveResponse(
            RetrievalResult result, BoundedRetrievalTraceCollector traceCollector) {
        return new RetrieveMemoryResponse(
                result.status().name().toLowerCase(),
                result.items() == null
                        ? List.of()
                        : result.items().stream()
                                .map(OpenMemoryApplicationService::toRetrievedItemView)
                                .toList(),
                // ... rest unchanged ...
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl memind-server -Dtest="OpenMemoryApplicationServiceTest" -q`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add memind-server/src/main/java/com/openmemind/ai/memory/server/domain/memory/response/RetrieveMemoryResponse.java \
        memind-server/src/main/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationService.java \
        memind-server/src/test/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationServiceTest.java
git commit -m "feat(api): expose retrieval status in API response

Add top-level 'status' field to RetrieveMemoryResponse with values
success/empty/degraded. Backward compatible — new field only.

Refs: #46"
```

---

### Task 5: Update Python integration scripts

**Files:**
- Modify: `memind-integrations/claude-code/scripts/retrieve.py`
- Modify: `memind-integrations/codex/scripts/retrieve.py`

- [ ] **Step 1: Update `claude-code/scripts/retrieve.py`**

In `_format_context` function, before the final return, add degraded notice:

```python
def _format_context(data, config):
    max_entries = int(config.get("retrieveMaxEntries", 8))
    max_chars = int(config.get("retrieveMaxChars", 6000))
    tier_rank = {"ROOT": 0, "BRANCH": 1, "LEAF": 2}

    insights = [insight for insight in (data.get("insights") or []) if insight.get("text")]
    insights.sort(key=lambda insight: (tier_rank.get(str(insight.get("tier", "LEAF")).upper(), 2), str(insight.get("id", ""))))
    high_level = [insight for insight in insights if str(insight.get("tier", "")).upper() in {"ROOT", "BRANCH"}]
    selected_insights = (high_level or insights)[: min(3, max_entries)]

    remaining = max_entries - len(selected_insights)
    items = [item for item in (data.get("items") or []) if item.get("text")]
    items.sort(
        key=lambda item: item.get("finalScore") if item.get("finalScore") is not None else item.get("vectorScore", 0),
        reverse=True,
    )
    selected_items = items[: max(0, remaining)]

    sections = []
    if selected_insights:
        sections.append("## Insights")
        sections.extend(f"- [insight:{insight.get('id')}] {insight.get('text')}" for insight in selected_insights)
    if selected_items:
        if sections:
            sections.append("")
        sections.append("## Memory Items")
        sections.extend(f"- [item:{item.get('id')}] {item.get('text')}" for item in selected_items)
    if not sections:
        return ""
    body = "\n".join(sections)[:max_chars]
    degraded_notice = ""
    if data.get("status") == "degraded":
        degraded_notice = "\n[Note: Memory retrieval encountered an error. Results may be incomplete.]\n"
    return f"<memind_memories>\n{config.get('retrievePromptPreamble') or ''}\n{body}{degraded_notice}\n</memind_memories>"
```

In `main()` function, add degraded handling after `context = _format_context(...)`:

```python
    context = _format_context(envelope.get("data") or {}, config)
    if not context and (envelope.get("data") or {}).get("status") == "degraded":
        context = "<memind_memories>\n[Note: Memory retrieval encountered an error. Results may be incomplete.]\n</memind_memories>"
    if not context:
        print(json.dumps({"continue": True}))
        return
```

- [ ] **Step 2: Update `codex/scripts/retrieve.py`**

Apply the same changes as Step 1 to `memind-integrations/codex/scripts/retrieve.py`. The file structure is identical.

- [ ] **Step 3: Verify Python syntax**

Run: `python3 -c "import ast; ast.parse(open('memind-integrations/claude-code/scripts/retrieve.py').read())"`
Run: `python3 -c "import ast; ast.parse(open('memind-integrations/codex/scripts/retrieve.py').read())"`
Expected: No output (no syntax errors)

- [ ] **Step 4: Commit**

```bash
git add memind-integrations/claude-code/scripts/retrieve.py \
        memind-integrations/codex/scripts/retrieve.py
git commit -m "feat(integrations): surface degraded retrieval status in prompt

When the server returns status=degraded, inject a notice into the
prompt context so the LLM knows memory results may be incomplete.

Refs: #46"
```

---

### Task 6: Full build verification

- [ ] **Step 1: Run full project build and tests**

Run: `mvn clean verify -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: Verify no untracked files or missed changes**

Run: `git status`
Expected: Clean working tree (all changes committed)
