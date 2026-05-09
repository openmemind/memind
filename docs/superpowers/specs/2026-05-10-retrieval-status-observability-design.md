# Retrieval Status Observability

**Issue:** [#46](https://github.com/openmemind/memind/issues/46)
**Date:** 2026-05-10
**Status:** Approved

## Problem

`DefaultMemoryRetriever` converts all retrieval errors into `RetrievalResult.empty()` via `onErrorResume`. Callers cannot distinguish "no relevant memories exist" from "the retrieval subsystem failed." This makes infrastructure failures invisible to consumers of the retrieval API.

## Design Principle

Minimal invasion: one enum + one field. No new abstraction layers, no interface signature changes, no behavioral changes to existing callers.

## Solution

### 1. Core Model Layer

**New enum** `RetrievalStatus` in `com.openmemind.ai.memory.core.retrieval`:

```java
public enum RetrievalStatus {
    SUCCESS,   // Retrieval completed normally, results found
    EMPTY,     // Retrieval completed normally, no matching results
    DEGRADED   // Retrieval encountered an error, returning fallback empty result
}
```

**Modified `RetrievalResult`** — add `status` field:

```java
public record RetrievalResult(
        List<ScoredResult> items,
        List<InsightResult> insights,
        List<RawDataResult> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalStatus status) {

    public static RetrievalResult empty(String strategy, String query) {
        return new RetrievalResult(List.of(), List.of(), List.of(), List.of(),
                strategy, query, RetrievalStatus.EMPTY);
    }

    public static RetrievalResult degraded(String strategy, String query) {
        return new RetrievalResult(List.of(), List.of(), List.of(), List.of(),
                strategy, query, RetrievalStatus.DEGRADED);
    }

    public RetrievalResult withStatus(RetrievalStatus newStatus) {
        return new RetrievalResult(items, insights, rawData, evidences, strategy, query, newStatus);
    }
}
```

### 2. Status Assignment in DefaultMemoryRetriever

**Error path** — use `degraded()` factory:

```java
.onErrorResume(e -> {
    log.warn("Retrieval failed, returning degraded result: query={}", request.query(), e);
    return Mono.just(RetrievalResult.degraded(config.strategyName(), effectiveQuery));
});
```

**Normal path** — infer status from result content after strategy execution:

```java
.map(result -> result.withStatus(
        result.isEmpty() ? RetrievalStatus.EMPTY : RetrievalStatus.SUCCESS))
```

This keeps status assignment centralized in `DefaultMemoryRetriever`. Individual strategy implementations do not need modification.

### 3. Metrics Simplification

`RetrievalMetricsSupport.summary()` reads status directly from result instead of inferring:

```java
String status = result == null ? "empty" : result.status().name().toLowerCase();
```

### 4. API Response Layer

**Modified `RetrieveMemoryResponse`** — add top-level `status` field:

```java
public record RetrieveMemoryResponse(
        String status,  // "success" | "empty" | "degraded"
        List<RetrievedItemView> items,
        List<RetrievedInsightView> insights,
        List<RetrievedRawDataView> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalTraceView trace) {}
```

Mapping in `OpenMemoryApplicationService.toRetrieveResponse()`:

```java
result.status().name().toLowerCase()
```

**API response examples:**

```json
// Success
{"status": "success", "items": [...], "insights": [...], ...}

// Empty (normal, no matches)
{"status": "empty", "items": [], "insights": [], ...}

// Degraded (error occurred)
{"status": "degraded", "items": [], "insights": [], ...}
```

### 5. Python Integration Layer

Both `claude-code/scripts/retrieve.py` and `codex/scripts/retrieve.py`:

- Read `status` from API response data
- When `status == "degraded"`, inject a notice into the prompt context
- When degraded with no data at all, still output the notice

```python
# In _format_context:
degraded_notice = ""
if data.get("status") == "degraded":
    degraded_notice = "\n[Note: Memory retrieval encountered an error. Results may be incomplete.]\n"

# In main, when context is empty but status is degraded:
if not context and data.get("status") == "degraded":
    context = "<memind_memories>\n[Note: Memory retrieval encountered an error. Results may be incomplete.]\n</memind_memories>"
```

The `except Exception` block in `main()` remains unchanged — it handles client-side errors (network unreachable, etc.) which are a different concern from server-side degradation.

## Backward Compatibility

- API response adds a new field; no existing fields removed or modified
- `RetrievalResult.empty()` behavior unchanged (returns EMPTY status)
- Python scripts gracefully handle missing `status` field (`data.get("status")` returns None, no notice triggered)
- `MemoryRetriever` interface signature unchanged
- Individual `RetrievalStrategy` implementations unchanged
- Trace system unchanged (provides complementary fine-grained diagnostics)

## Files Changed

| File | Change |
|------|--------|
| `RetrievalStatus.java` (new) | Three-state enum |
| `RetrievalResult.java` | Add `status` field + `degraded()` factory + `withStatus()` |
| `DefaultMemoryRetriever.java` | `onErrorResume` uses `degraded()`, normal path uses `withStatus()` |
| `RetrievalMetricsSupport.java` | Read from `result.status()` directly |
| `RetrieveMemoryResponse.java` | Add top-level `status` field |
| `OpenMemoryApplicationService.java` | Map status to response |
| `claude-code/scripts/retrieve.py` | Read status, inject notice on degraded |
| `codex/scripts/retrieve.py` | Same as above |

## Testing Strategy

- **Unit tests for `DefaultMemoryRetriever`:** Verify normal path returns `SUCCESS`/`EMPTY`, error path returns `DEGRADED`
- **API integration tests:** Verify `status` field correctly serialized in response
- **Python script tests:** Verify degraded status triggers notice text in output
