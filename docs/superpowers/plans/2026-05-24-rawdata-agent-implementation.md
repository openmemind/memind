# rawdata-agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a canonical `rawdata-agent` RawData plugin so Memind can ingest coding-agent timelines, derive episode evidence, extract AGENT memories, and feed existing Insight Tree, graph, and retrieval paths.

**Architecture:** Implement `agent_timeline` as a new RawContent plugin, not a parallel observation database. The plugin produces deterministic `agent_episode` segments, AGENT-scoped `TOOL`, `RESOLUTION`, `PLAYBOOK`, and `DIRECTIVE` items, and optional current-core graph hints through `ExtractedMemoryEntry.graphHints()`.

**Tech Stack:** Java 21, Maven, Reactor, Spring Boot auto-configuration, Jackson raw content subtype registration, Memind core RawData/MemoryItem/Insight/Graph APIs, Python/Java/TypeScript clients, Python Claude Code/Codex integrations.

---

## Source Spec

Implement against [2026-05-24-rawdata-agent-design.md](/Users/zhengyate/dev/openmemind/memind/docs/superpowers/specs/2026-05-24-rawdata-agent-design.md). If this plan and the spec conflict, pause and update the plan before coding.

## Implementation Order

1. Core support: `tools` insight type, migration-safe default reconciliation, shared graph hint converter, retrieval metadata.
2. New `memind-plugin-rawdata-agent` module: schema, redaction, episode assembly, processor, extractor.
3. Spring Boot starter and server registration.
4. Client convenience wrappers and typed response metadata.
5. Claude Code and Codex timeline capture and retrieval formatting.
6. Cross-store, integration, and evaluation verification.

## File Structure

### Core

- Modify `memind-core/src/main/java/com/openmemind/ai/memory/core/data/DefaultInsightTypes.java`
  - Add built-in AGENT `tools` branch insight type.
- Modify `memind-core/src/test/java/com/openmemind/ai/memory/core/data/DefaultInsightTypesTest.java`
  - Assert `tools` exists, maps to `tool`, and is AGENT scoped.
- Create `memind-core/src/main/java/com/openmemind/ai/memory/core/store/insight/DefaultInsightTypeReconciler.java`
  - Idempotently adds missing built-in insight types without deleting or overwriting user-defined types.
- Create `memind-core/src/test/java/com/openmemind/ai/memory/core/store/insight/DefaultInsightTypeReconcilerTest.java`
  - Proves missing `tools` is inserted and customized existing insight types are preserved.
- Modify `memind-core/src/main/java/com/openmemind/ai/memory/core/store/InMemoryMemoryStore.java`
  - Use the reconciler at startup.
- Modify each JDBC store constructor:
  - `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-sqlite/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java`
  - `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-mysql/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java`
  - `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-postgresql/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java`
  - Replace seed-only behavior with migration-safe reconciliation.
- Add tests in each store test class proving existing stores receive `tools`:
  - `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-sqlite/src/test/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStoreTest.java`
  - `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-mysql/src/test/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStoreTest.java`
  - `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-postgresql/src/test/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStoreTest.java`
- Create `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/support/ExtractedGraphHintConverter.java`
  - Shared, public support converter from `MemoryItemExtractionResponse.ExtractedItem` entities/causal relations to `ExtractedGraphHints`.
- Modify `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/strategy/LlmItemExtractionStrategy.java`
  - Use `ExtractedGraphHintConverter` instead of private local conversion helpers.
- Add tests:
  - `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/support/ExtractedGraphHintConverterTest.java`
  - Update existing `LlmItemExtractionStrategy` tests if private helper expectations move.
- Modify `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/scoring/ScoredResult.java`
  - Add optional category and metadata for ITEM results while keeping existing constructors.
- Modify item retrieval paths that construct ITEM `ScoredResult`:
  - `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/tier/ItemTierRetriever.java`
  - `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/SimpleRetrievalStrategy.java`
  - `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/DeepRetrievalStrategy.java`
  - `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/temporal/DefaultTemporalItemChannel.java`
  - `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistant.java`
  - `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngine.java`
  - Preserve metadata through rerank/merge copies.
- Modify server/client retrieval views to expose returned item category and metadata:
  - `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/memory/response/RetrieveMemoryResponse.java`
  - `memind-server/src/main/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationService.java`
  - `memind-clients/python/src/memind/types/memory.py`
  - `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/response/RetrieveMemoryResponse.java`
  - `memind-clients/typescript/src/types/memory.ts`

### rawdata-agent Plugin

- Create module:
  - `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/pom.xml`
- Modify module lists:
  - `memind-plugins/memind-plugin-rawdatas/pom.xml`
  - Root/parent module list only if it explicitly enumerates rawdata child modules.
- Create package root `com.openmemind.ai.memory.plugin.rawdata.agent`.
- Create:
  - `AgentRawContentTypeRegistrar.java`
  - `content/AgentTimelineContent.java`
  - `model/AgentTimeline.java`
  - `model/AgentEvent.java`
  - `model/AgentEventKind.java`
  - `model/AgentEventStatus.java`
  - `model/AgentProject.java`
  - `model/AgentGitContext.java`
  - `model/AgentEpisode.java`
  - `model/AgentCommand.java`
  - `model/AgentFileReference.java`
  - `model/AgentToolCall.java`
  - `model/AgentOutcome.java`
  - `config/AgentChunkingOptions.java`
  - `config/AgentExtractionOptions.java`
  - `config/AgentPrivacyOptions.java`
  - `config/AgentRawDataOptions.java`
  - `privacy/SecretPatternRedactor.java`
  - `privacy/AgentEventRedactor.java`
  - `chunk/AgentEpisodeAssembler.java`
  - `chunk/AgentSegmentFormatter.java`
  - `chunk/AgentTimelineChunker.java`
  - `caption/AgentCaptionGenerator.java`
  - `processor/AgentTimelineContentProcessor.java`
  - `item/AgentItemExtractionStrategy.java`
  - `item/AgentItemPrompts.java`
  - `item/AgentMemoryItemFactory.java`
  - `plugin/AgentRawDataPlugin.java`
- Create tests under matching paths for each public behavior.

### rawdata-agent Starter

- Create:
  - `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-agent-starter/pom.xml`
  - `src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/autoconfigure/AgentRawDataAutoConfiguration.java`
  - `src/main/java/com/openmemind/ai/memory/plugin/rawdata/agent/autoconfigure/AgentRawDataProperties.java`
  - `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - `src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/autoconfigure/AgentRawDataAutoConfigurationTest.java`
- Modify:
  - `memind-plugins/memind-plugin-spring-boot-starters/pom.xml`
  - `memind-server/pom.xml`
  - `memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerApplicationTest.java`

### Clients

- Python:
  - `memind-clients/python/src/memind/types/message.py`
  - `memind-clients/python/src/memind/types/memory.py`
  - `memind-clients/python/src/memind/resources/memory.py`
  - `memind-clients/python/src/memind/resources/async_memory.py`
  - Tests in `memind-clients/python/tests/`.
- Java:
  - Keep `MapRawContent` path documented.
  - Optionally add typed classes under `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/`.
  - Tests in `memind-clients/java/memind-client/src/test/java/com/openmemind/ai/client/model/common/`.
- TypeScript:
  - Add `AgentTimelineContent` type aliases in `memind-clients/typescript/src/types/message.ts`.
  - Add serialization tests.

### Claude Code and Codex Integrations

- Shared concepts are implemented independently in each integration because current directories are standalone.
- Claude Code:
  - `memind-integrations/claude-code/hooks/hooks.json`
  - `memind-integrations/claude-code/scripts/pre_tool_use.py`
  - `memind-integrations/claude-code/scripts/post_tool_use.py`
  - `memind-integrations/claude-code/scripts/lib/agent_timeline.py`
  - `memind-integrations/claude-code/scripts/lib/state.py`
  - `memind-integrations/claude-code/scripts/lib/client.py`
  - `memind-integrations/claude-code/scripts/ingest.py`
  - `memind-integrations/claude-code/scripts/pre_compact.py`
  - `memind-integrations/claude-code/scripts/session_end.py`
  - `memind-integrations/claude-code/scripts/retrieve.py`
  - Tests under `memind-integrations/claude-code/tests/`.
- Codex:
  - `memind-integrations/codex/hooks/hooks.json`
  - `memind-integrations/codex/scripts/pre_tool_use.py`
  - `memind-integrations/codex/scripts/post_tool_use.py`
  - `memind-integrations/codex/scripts/lib/agent_timeline.py`
  - `memind-integrations/codex/scripts/lib/state.py`
  - `memind-integrations/codex/scripts/lib/client.py`
  - `memind-integrations/codex/scripts/ingest.py`
  - `memind-integrations/codex/scripts/retrieve.py`
  - Tests under `memind-integrations/codex/tests/`.

---

## Tasks

### Task 1: Add Built-In `tools` Insight Type

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/data/DefaultInsightTypes.java`
- Modify: `memind-core/src/test/java/com/openmemind/ai/memory/core/data/DefaultInsightTypesTest.java`

- [ ] **Step 1: Write failing tests for `tools`**

Add assertions:

```java
@Test
@DisplayName("all() should expose tools as an agent branch insight type")
void allShouldExposeToolsAgentBranchInsightType() {
    assertThat(DefaultInsightTypes.all())
            .extracting(MemoryInsightType::name)
            .contains("tools");
}

@Test
@DisplayName("tools should map to tool category and AGENT scope")
void toolsShouldMapToToolCategoryAndAgentScope() {
    assertThat(DefaultInsightTypes.tools().categories()).containsExactly("tool");
    assertThat(DefaultInsightTypes.tools().scope()).isEqualTo(MemoryScope.AGENT);
    assertThat(DefaultInsightTypes.tools().insightAnalysisMode())
            .isEqualTo(com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode.BRANCH);
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultInsightTypesTest test
```

Expected: compilation fails because `DefaultInsightTypes.tools()` does not exist.

- [ ] **Step 3: Add `DefaultInsightTypes.tools()`**

Add after `resolutions()`:

```java
public static MemoryInsightType tools() {
    return new MemoryInsightType(
            28L,
            "tools",
            "Tool and command usage patterns. Group by stable tool name, command family,"
                    + " invocation pattern, validation command, or repeated failure mode.",
            null,
            List.of("tool"),
            DEFAULT_TARGET_TOKENS,
            null,
            null,
            null,
            InsightAnalysisMode.BRANCH,
            null,
            MemoryScope.AGENT);
}
```

Update `all()` to include `tools()` between `resolutions()` and root types.

- [ ] **Step 4: Run the test**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultInsightTypesTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/data/DefaultInsightTypes.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/data/DefaultInsightTypesTest.java
git commit -m "feat(core): add tools agent insight type"
```

### Task 2: Reconcile Built-In Insight Types for Existing Stores

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/store/insight/DefaultInsightTypeReconciler.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/store/insight/DefaultInsightTypeReconcilerTest.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/store/InMemoryMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-sqlite/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-mysql/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-postgresql/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java`
- Test: store-specific existing test classes.

- [ ] **Step 1: Write reconciler tests**

Create `DefaultInsightTypeReconcilerTest`:

```java
class DefaultInsightTypeReconcilerTest {

    @Test
    void insertsMissingBuiltInTypesOnly() {
        var ops = new InMemoryInsightOperations();
        ops.upsertInsightTypes(List.of(DefaultInsightTypes.identity()));

        DefaultInsightTypeReconciler.reconcile(ops);

        assertThat(ops.getInsightType("identity")).isPresent();
        assertThat(ops.getInsightType("tools")).isPresent();
    }

    @Test
    void preservesExistingCustomizedType() {
        var ops = new InMemoryInsightOperations();
        var customized =
                DefaultInsightTypes.tools().withTargetTokens(1234);
        ops.upsertInsightTypes(List.of(customized));

        DefaultInsightTypeReconciler.reconcile(ops);

        assertThat(ops.getInsightType("tools").orElseThrow().targetTokens())
                .isEqualTo(1234);
    }
}
```

Import:

```java
import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import org.junit.jupiter.api.Test;
```

- [ ] **Step 2: Run the failing reconciler test**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultInsightTypeReconcilerTest test
```

Expected: compilation fails because `DefaultInsightTypeReconciler` does not exist.

- [ ] **Step 3: Implement reconciler**

Create:

```java
package com.openmemind.ai.memory.core.store.insight;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import java.util.List;

public final class DefaultInsightTypeReconciler {

    private DefaultInsightTypeReconciler() {}

    public static void reconcile(InsightOperations operations) {
        if (operations == null) {
            return;
        }
        List<MemoryInsightType> missing =
                DefaultInsightTypes.all().stream()
                        .filter(type -> operations.getInsightType(type.name()).isEmpty())
                        .toList();
        if (!missing.isEmpty()) {
            operations.upsertInsightTypes(missing);
        }
    }
}
```

- [ ] **Step 4: Use reconciler in stores**

Change each store constructor from:

```java
if (initResult.createdInsightTypeTable()) {
    upsertInsightTypes(DefaultInsightTypes.all());
}
```

to:

```java
DefaultInsightTypeReconciler.reconcile(this);
```

For `InMemoryMemoryStore`, replace direct `DefaultInsightTypes.all()` seeding with:

```java
DefaultInsightTypeReconciler.reconcile(insightOperations);
```

Add imports where needed.

- [ ] **Step 5: Add store tests**

In each JDBC store test, add a test equivalent to:

```java
@Test
void constructorReconcilesMissingBuiltInInsightTypesForExistingStore() {
    var store = newStore();
    assertThat(store.getInsightType("tools")).isPresent();

    removeInsightTypeForUpgradeSimulation("tools");

    var reopened = reopenStoreWithSameDatabase();

    assertThat(reopened.getInsightType("tools")).isPresent();
}
```

Also add a preservation test:

```java
@Test
void constructorDoesNotOverwriteExistingCustomizedBuiltInInsightType() {
    var store = newStore();
    store.upsertInsightTypes(
            List.of(DefaultInsightTypes.tools().withTargetTokens(1234)));

    var reopened = reopenStoreWithSameDatabase();

    assertThat(reopened.getInsightType("tools")).isPresent();
    assertThat(reopened.getInsightType("tools").orElseThrow().targetTokens())
            .isEqualTo(1234);
}
```

Use the helper methods already present in each store test class. If a class does not have `reopenStoreWithSameDatabase()`, use the same datasource instance to construct a second store.

For SQLite, implement the upgrade simulation with the test datasource:

```java
new NamedParameterJdbcTemplate(dataSource)
        .getJdbcOperations()
        .update("DELETE FROM memory_insight_type WHERE name = ?", "tools");
```

For MySQL/PostgreSQL, use the same database helper style already used in the test class, but keep the SQL equivalent:

```sql
DELETE FROM memory_insight_type WHERE name = 'tools'
```

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl memind-core -Dtest=DefaultInsightTypeReconcilerTest test
mvn -pl memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-sqlite -Dtest=SqliteMemoryStoreTest test
mvn -pl memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-mysql -Dtest=MysqlMemoryStoreTest test
mvn -pl memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-postgresql -Dtest=PostgresqlMemoryStoreTest test
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/store/insight/DefaultInsightTypeReconciler.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/store/insight/DefaultInsightTypeReconcilerTest.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/store/InMemoryMemoryStore.java \
  memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-sqlite/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java \
  memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-mysql/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java \
  memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-postgresql/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java \
  memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-sqlite/src/test/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStoreTest.java \
  memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-mysql/src/test/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStoreTest.java \
  memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-postgresql/src/test/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStoreTest.java
git commit -m "fix(core): reconcile default insight types on startup"
```

### Task 3: Extract Shared Graph Hint Conversion

**Files:**
- Create: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/support/ExtractedGraphHintConverter.java`
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/support/ExtractedGraphHintConverterTest.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/strategy/LlmItemExtractionStrategy.java`

- [ ] **Step 1: Write converter tests**

Create:

```java
class ExtractedGraphHintConverterTest {

    @Test
    void convertsEntitiesAndCausalRelations() {
        var item =
                new MemoryItemExtractionResponse.ExtractedItem(
                        "content",
                        0.9f,
                        null,
                        null,
                        List.of("resolutions"),
                        Map.of(),
                        "resolution",
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedEntity(
                                        "src/payment/calc.ts", "object", 1.5f)),
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedCausalRelation(
                                        0, 1, "enabled_by", -1.0f)));

        ExtractedGraphHints hints = ExtractedGraphHintConverter.from(item);

        assertThat(hints.entities()).hasSize(1);
        assertThat(hints.entities().getFirst().name()).isEqualTo("src/payment/calc.ts");
        assertThat(hints.entities().getFirst().salience()).isEqualTo(1.0f);
        assertThat(hints.causalRelations()).hasSize(1);
        assertThat(hints.causalRelations().getFirst().relationType()).isEqualTo("enabled_by");
        assertThat(hints.causalRelations().getFirst().strength()).isEqualTo(0.0f);
    }

    @Test
    void dropsBlankEntitiesAndIncompleteCausalRelations() {
        var item =
                new MemoryItemExtractionResponse.ExtractedItem(
                        "content",
                        0.9f,
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        "tool",
                        List.of(new MemoryItemExtractionResponse.ExtractedEntity(" ", "object", 0.5f)),
                        List.of(new MemoryItemExtractionResponse.ExtractedCausalRelation(null, 1, "enabled_by", 0.5f)));

        ExtractedGraphHints hints = ExtractedGraphHintConverter.from(item);

        assertThat(hints.entities()).isEmpty();
        assertThat(hints.causalRelations()).isEmpty();
    }
}
```

Imports:

```java
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
```

- [ ] **Step 2: Run failing test**

Run:

```bash
mvn -pl memind-core -Dtest=ExtractedGraphHintConverterTest test
```

Expected: compilation fails because converter does not exist.

- [ ] **Step 3: Implement converter**

Move the conversion logic from `LlmItemExtractionStrategy` into a public final support class. The class must expose:

```java
public static ExtractedGraphHints from(MemoryItemExtractionResponse.ExtractedItem item)
```

and:

```java
public static List<ExtractedGraphHints.ExtractedEntityHint> toEntityHints(
        List<MemoryItemExtractionResponse.ExtractedEntity> entities)
public static List<ExtractedGraphHints.ExtractedCausalRelationHint> toCausalHints(
        List<MemoryItemExtractionResponse.ExtractedCausalRelation> causalRelations)
```

Keep clamp behavior: null stays null, values clamp to `[0.0, 1.0]`. Preserve alias observation conversion through `EntityAliasClass.fromWireValue(observation.aliasClass())`.

- [ ] **Step 4: Update `LlmItemExtractionStrategy`**

Replace:

```java
new ExtractedGraphHints(toEntityHints(item.entities()), toCausalHints(item.causalRelations()))
```

with:

```java
ExtractedGraphHintConverter.from(item)
```

Remove now-unused private conversion helpers from `LlmItemExtractionStrategy`.

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl memind-core -Dtest=ExtractedGraphHintConverterTest,LlmItemExtractionStrategyTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/support/ExtractedGraphHintConverter.java \
  memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/item/support/ExtractedGraphHintConverterTest.java \
  memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/item/strategy/LlmItemExtractionStrategy.java
git commit -m "refactor(core): share graph hint conversion"
```

### Task 4: Expose Item Category and Metadata in Retrieval Responses

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/scoring/ScoredResult.java`
- Modify: item retriever and merge/rerank paths that copy `ScoredResult`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/domain/memory/response/RetrieveMemoryResponse.java`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/service/memory/OpenMemoryApplicationService.java`
- Modify: client response types in Python, Java, TypeScript.
- Tests: existing retrieval/server/client tests.

- [ ] **Step 1: Write failing server response test**

Add or update a server service test so a retrieved item with category `TOOL` and metadata `{"toolName":"Bash"}` serializes as:

```json
{
  "id": "1",
  "text": "Use npm test payment",
  "category": "tool",
  "metadata": {"toolName": "Bash"}
}
```

Use existing `OpenMemoryApplicationService` tests if present. Otherwise add assertions to the nearest retrieve response serialization test.

- [ ] **Step 2: Extend `ScoredResult`**

Change record to:

```java
public record ScoredResult(
        SourceType sourceType,
        String sourceId,
        String text,
        float vectorScore,
        double finalScore,
        Instant occurredAt,
        String category,
        Map<String, Object> metadata) {
```

Add compatible constructors matching current signatures and defaulting category to `null`, metadata to `Map.of()`.

- [ ] **Step 3: Populate item metadata**

Where `ScoredResult` is constructed from `MemoryItem`, pass:

```java
item.category() == null ? null : item.category().categoryName()
item.metadata()
```

Where `ScoredResult` is copied by rerank, scoring, graph expansion, or time decay, preserve `category()` and `metadata()`.

Update every constructor/copy site discovered by:

```bash
rg -n "new ScoredResult|withOccurredAt|ScoredResult\\(" memind-core/src/main/java memind-server/src/main/java -g'*.java'
```

At minimum, cover these existing classes:

```text
memind-core/src/main/java/com/openmemind/ai/memory/core/llm/rerank/LlmReranker.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/scoring/ResultMerger.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/scoring/RawDataAggregator.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/scoring/TimeDecay.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/thread/ThreadAssistMemberRanker.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/tier/ItemTierRetriever.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/temporal/DefaultTemporalItemChannel.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/DefaultRetrievalGraphAssistant.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/graph/GraphExpansionEngine.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/SimpleRetrievalStrategy.java
memind-core/src/main/java/com/openmemind/ai/memory/core/retrieval/strategy/DeepRetrievalStrategy.java
```

Any constructor from INSIGHT or RAW_DATA can keep `category = null` and `metadata = Map.of()`. Any copy of an ITEM result must preserve the original category and metadata.

- [ ] **Step 4: Extend server response**

Change:

```java
public record RetrievedItemView(
        String id, String text, float vectorScore, double finalScore, Instant occurredAt) {}
```

to include:

```java
String category,
Map<String, Object> metadata
```

Update `toRetrievedItemView(...)` to pass `item.category()` and `item.metadata()`.

- [ ] **Step 5: Extend clients**

Python `RetrievedItem`:

```python
category: str | None = None
metadata: dict[str, Any] = Field(default_factory=dict)
```

Java `RetrievedItem`:

```java
String category, Map<String, Object> metadata
```

TypeScript `RetrievedItem`:

```ts
category?: string
metadata?: Record<string, unknown>
```

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl memind-core -Dtest='*Retrieval*Test,*Reranker*Test' test
mvn -pl memind-server test
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 --extra dev pytest memind-clients/python/tests -q
mvn -pl memind-clients/java/memind-client test
PATH=/Users/zhengyate/.nvm/versions/node/v22.22.0/bin:$PATH COREPACK_HOME=/tmp/memind-corepack pnpm --dir memind-clients/typescript test
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add memind-core memind-server memind-clients/python memind-clients/java/memind-client memind-clients/typescript
git commit -m "feat(retrieval): expose item category metadata"
```

### Task 5: Scaffold `memind-plugin-rawdata-agent`

**Files:**
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/pom.xml`
- Modify: `memind-plugins/memind-plugin-rawdatas/pom.xml`
- Create registrar/content/model/config classes.
- Tests under `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/`

- [ ] **Step 1: Add module POM**

Use `memind-plugin-rawdata-toolcall/pom.xml` as the template. Artifact:

```xml
<artifactId>memind-plugin-rawdata-agent</artifactId>
<name>Memind - Agent RawData Plugin</name>
```

Dependencies: `memind-core`, JUnit Jupiter, AssertJ, Mockito, Reactor Test.

- [ ] **Step 2: Add module to parent**

Add:

```xml
<module>memind-plugin-rawdata-agent</module>
```

to `memind-plugins/memind-plugin-rawdatas/pom.xml`.

- [ ] **Step 3: Write failing registrar/content tests**

Create `AgentTimelineContentTest` verifying:

```java
AgentTimelineContent content = new AgentTimelineContent(
        "claude-code",
        "1.0",
        "session-123",
        "timeline-123",
        project,
        events);

assertThat(content.contentType()).isEqualTo("AGENT_TIMELINE");
assertThat(content.toContentString()).contains("Goal:", "npm test payment");
AgentTimelineContent duplicate = new AgentTimelineContent(
        "claude-code",
        "1.0",
        "session-123",
        "timeline-123",
        project,
        events);
assertThat(content.getContentId()).isEqualTo(duplicate.getContentId());
```

Create `AgentRawContentTypeRegistrarTest` verifying subtype:

```java
assertThat(new AgentRawContentTypeRegistrar().subtypes())
        .containsEntry("agent_timeline", AgentTimelineContent.class);
```

- [ ] **Step 4: Implement model records/classes**

Use immutable records where possible:

```java
public record AgentEvent(
        String id,
        Integer seq,
        AgentEventKind kind,
        Instant occurredAt,
        String text,
        String toolName,
        String input,
        String output,
        AgentEventStatus status,
        Long durationMs,
        String path,
        String operation,
        String command,
        Integer exitCode,
        Map<String, Object> metadata) {}
```

`text` is required for `user_prompt`, `assistant_message`, `error`, and summary-like events. It must not be hidden inside `metadata`; the episode assembler uses `user_prompt.text` as the primary goal signal.

`AgentEventKind` enum values must map lower snake JSON values:

```java
USER_PROMPT, ASSISTANT_MESSAGE, TOOL_CALL, TOOL_RESULT, COMMAND, FILE_READ,
FILE_EDIT, TEST_RESULT, PERMISSION_REQUEST, ERROR, STOP, SESSION_END, TASK_COMPLETED
```

Use Jackson annotations or string parsing consistent with existing project style.

- [ ] **Step 5: Implement `AgentTimelineContent`**

Requirements:

- `TYPE = "AGENT_TIMELINE"`.
- JSON raw subtype remains `"agent_timeline"` through registrar.
- `getContentId()` hashes canonical identity:

```text
sourceClient | sessionId | timelineId | ordered event IDs | normalized event content hash
```

- `toContentString()` returns deterministic compact text.
- `user_prompt.text` survives Jackson round-trip and appears in `toContentString()` as the episode goal source.
- Missing optional fields are tolerated.
- Events are sorted by `seq`, then `occurredAt`, then `id`.

- [ ] **Step 6: Run module tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/pom.xml \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent
git commit -m "feat(rawdata-agent): scaffold agent timeline content"
```

### Task 6: Implement Privacy Redaction

**Files:**
- Create: `config/AgentPrivacyOptions.java`
- Create: `privacy/SecretPatternRedactor.java`
- Create: `privacy/AgentEventRedactor.java`
- Tests: `SecretPatternRedactorTest.java`, `AgentEventRedactorTest.java`

- [ ] **Step 1: Write redaction tests**

Test cases:

```java
assertThat(redactor.redact("Authorization: Bearer abc.def.ghi").text())
        .contains("[REDACTED:bearer_token]");
assertThat(redactor.redact("DATABASE_URL=postgres://u:p@example/db").text())
        .contains("[REDACTED:database_url]");
assertThat(redactor.redact("-----BEGIN PRIVATE KEY-----\nabc").text())
        .contains("[REDACTED:private_key]");
```

For event redaction:

```java
AgentEvent event = commandWithOutput("npm test", "ok ".repeat(5000));
AgentEvent redacted = eventRedactor.redact(event);
assertThat(redacted.output()).hasSizeLessThanOrEqualTo(4000);
assertThat(redacted.metadata()).containsEntry("redacted", true);
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=SecretPatternRedactorTest,AgentEventRedactorTest test
```

Expected: compilation fails.

- [ ] **Step 3: Implement privacy options**

Defaults:

```java
redactSecrets = true
maxInputChars = 2000
maxOutputChars = 4000
captureFileContent = false
denyPathPatterns = List.of(".env", "*.pem", "*.key")
allowPathPatterns = List.of()
```

- [ ] **Step 4: Implement redactors**

Rules:

- Redact bearer/API tokens, common secret env vars, database URLs with credentials, private key blocks, cloud credentials.
- Truncate `input` and `output`.
- Drop file contents by default.
- Add metadata:

```java
"redacted": true
"redactionKinds": List.of("bearer_token")
```

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=SecretPatternRedactorTest,AgentEventRedactorTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java
git commit -m "feat(rawdata-agent): redact sensitive agent events"
```

### Task 7: Implement Episode Assembly and Segment Formatting

**Files:**
- Create: `chunk/AgentEpisodeAssembler.java`
- Create: `chunk/AgentSegmentFormatter.java`
- Create: `chunk/AgentTimelineChunker.java`
- Create: `caption/AgentCaptionGenerator.java`
- Tests: chunk/formatter/caption tests.

- [ ] **Step 1: Write episode boundary tests**

Use events:

```text
e1 user_prompt
e2 command failed
e3 file_edit
e4 command success
e5 stop
```

Assert one episode with:

```java
episode.goal() == "Fix payment tests"
episode.outcome() == AgentOutcome.SUCCESS
episode.eventIds() == ["e1","e2","e3","e4","e5"]
episode.files() contains "src/payment/calc.ts"
episode.commands() contains "npm test payment"
episode.failureSignals() contains "rounding mismatch"
```

Add secondary boundary tests:

- new `user_prompt` closes previous episode.
- 31 minute gap splits episodes.
- event count over max splits.
- oversized episode splits into `investigation`, `implementation`, `validation`, `handoff`.

- [ ] **Step 2: Write formatter test**

Assert formatted text contains:

```text
Goal: Fix payment tests.
Outcome: success
Files: src/payment/calc.ts
Commands:
- npm test payment -> failed: rounding mismatch
- npm test payment -> success
Evidence:
- e2:
- e4:
```

Assert metadata contains:

```java
segmentType = "agent_episode"
episodeId
phase
sourceClient
sessionId
timelineId
files
commands
toolNames
failureSignals
eventIds
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentEpisodeAssemblerTest,AgentSegmentFormatterTest,AgentTimelineChunkerTest test
```

Expected: compilation fails.

- [ ] **Step 4: Implement assembler**

Rules:

- Sort stable by `seq`, `occurredAt`, `id`.
- Primary start: `user_prompt`.
- Primary end: `stop`, `session_end`, `task_completed`.
- New `user_prompt` closes previous open episode.
- Secondary boundaries: `maxEventGap`, `maxEventsPerEpisode`, target token estimate, `taskId/subtaskId` change.
- Episode ID:

```java
HashUtils.sampledSha256(sourceClient + "|" + sessionId + "|" + firstEventId + "|" + lastEventId + "|" + eventIds)
```

- [ ] **Step 5: Implement formatter/chunker/caption**

`AgentTimelineChunker` must:

- Redact before segment creation.
- Assemble episodes.
- Format deterministic segment content.
- Attach `SegmentRuntimeContext(start, end, null, sourceClient)`.

`AgentCaptionGenerator` should return a deterministic short caption:

```text
Agent episode: <goal> -> <outcome> (<file/command summary>)
```

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentEpisodeAssemblerTest,AgentSegmentFormatterTest,AgentTimelineChunkerTest,AgentCaptionGeneratorTest test
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java
git commit -m "feat(rawdata-agent): assemble agent episodes"
```

### Task 8: Implement Processor and Plugin Registration

**Files:**
- Create: `processor/AgentTimelineContentProcessor.java`
- Create: `plugin/AgentRawDataPlugin.java`
- Tests: `AgentTimelineContentProcessorTest.java`, `AgentRawDataPluginTest.java`

- [ ] **Step 1: Write processor tests**

Assert:

```java
assertThat(processor.contentClass()).isEqualTo(AgentTimelineContent.class);
assertThat(processor.contentType()).isEqualTo(AgentTimelineContent.TYPE);
assertThat(processor.allowedCategories()).containsExactlyInAnyOrderElementsOf(MemoryCategory.agentCategories());
assertThat(processor.usesSourceIdentity()).isTrue();
assertThat(processor.supportsInsight()).isTrue();
assertThat(processor.itemExtractionStrategy()).isInstanceOf(AgentItemExtractionStrategy.class);
```

- [ ] **Step 2: Write plugin tests**

Assert:

```java
RawDataPlugin plugin = new AgentRawDataPlugin();
assertThat(plugin.pluginId()).isEqualTo("rawdata-agent");
assertThat(plugin.typeRegistrars()).extracting(RawContentTypeRegistrar::subtypes)
        .anySatisfy(map -> assertThat(map).containsKey("agent_timeline"));
assertThat(plugin.processors(pluginContext())).hasSize(1);
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentTimelineContentProcessorTest,AgentRawDataPluginTest test
```

Expected: compilation fails.

- [ ] **Step 4: Implement processor/plugin**

`AgentTimelineContentProcessor` must return:

```java
contentClass()       -> AgentTimelineContent.class
contentType()        -> AgentTimelineContent.TYPE
allowedCategories()  -> MemoryCategory.agentCategories()
usesSourceIdentity() -> true
supportsInsight()    -> true
```

Use:

```java
new AgentTimelineChunker(options.chunking(), options.privacy())
new AgentCaptionGenerator()
new AgentItemExtractionStrategy(context.chatClientRegistry().defaultClient(), context.promptRegistry(), options.extraction())
```

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent
git commit -m "feat(rawdata-agent): register agent rawdata processor"
```

### Task 9: Implement Deterministic Agent Item Extraction

**Files:**
- Create: `item/AgentMemoryItemFactory.java`
- Create/modify: `item/AgentItemExtractionStrategy.java`
- Tests: deterministic extractor tests.

- [ ] **Step 1: Write deterministic extraction tests**

For a successful episode with command failure then pass:

```java
List<ExtractedMemoryEntry> entries = strategy.extract(List.of(segment), DefaultInsightTypes.all(), config).block();

assertThat(entries).anySatisfy(entry -> {
    assertThat(entry.category()).isEqualTo("tool");
    assertThat(entry.insightTypes()).containsExactly("tools");
    assertThat(entry.metadata()).containsEntry("episodeId", "episode-123");
});
assertThat(entries).anySatisfy(entry -> {
    assertThat(entry.category()).isEqualTo("resolution");
    assertThat(entry.insightTypes()).containsExactly("resolutions");
    assertThat(entry.metadata()).containsKey("evidenceEventIds");
});
```

For a failed unresolved episode:

```java
assertThat(entries).noneMatch(e -> "playbook".equals(e.category()));
assertThat(entries).noneMatch(e -> "resolution".equals(e.category()));
```

For exact duplicate extraction:

```java
assertThat(first.get(0).content()).isEqualTo(second.get(0).content());
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentItemExtractionStrategyTest test
```

Expected: compilation fails or assertions fail.

- [ ] **Step 3: Implement deterministic TOOL**

Emit TOOL when segment metadata has `toolNames` or `commands`.

Canonical content examples:

```text
Use npm test payment to validate changes touching src/payment/calc.ts.
Bash command npm test payment failed once and passed once in episode episode-123.
```

Metadata:

```java
toolName, command, files, commands, toolNames, successCount, failCount,
episodeId, sessionId, timelineId, sourceClient, evidenceEventIds
```

- [ ] **Step 4: Implement conservative deterministic RESOLUTION**

Emit RESOLUTION only when:

- `failureSignals` is non-empty.
- `outcome` is `success` or `partial_success`.
- There is a later successful validation command or test result.
- There is an edit, conclusion, or successful command tying the fix/conclusion to the outcome.

Use this deterministic validation rule:

```java
failedSignalEvent.seq < validationEvent.seq
        && validationEvent.status == AgentEventStatus.SUCCESS
        && (validationEvent.kind == AgentEventKind.COMMAND
                || validationEvent.kind == AgentEventKind.TEST_RESULT)
        && validationEvent.command matches a failed command family or known validation command
```

Command family matching should normalize whitespace and strip volatile arguments before comparing. For example, `npm test payment`, `npm test -- payment`, and `pnpm test payment -- --runInBand` can be grouped by the stable test target `payment`; unrelated successful commands such as `git status` must not validate a failed test.

Canonical content:

```text
<failure signal> was resolved in <files> and validated with <commands>.
```

- [ ] **Step 5: Implement graph hints for deterministic items**

Use `ExtractedGraphHints` directly:

- file path -> `object`
- command -> `object`
- failure signal -> `concept`
- tool -> `object`

Do not emit custom coding relation names.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentItemExtractionStrategyTest test
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java
git commit -m "feat(rawdata-agent): extract deterministic agent memories"
```

### Task 10: Add LLM Agent Extraction for Playbooks and Directives

**Files:**
- Create/modify: `item/AgentItemPrompts.java`
- Modify: `item/AgentItemExtractionStrategy.java`
- Tests with mocked `StructuredChatClient`.

- [ ] **Step 1: Write mocked LLM tests**

Mock `structuredChatClient.call(messages, MemoryItemExtractionResponse.class)` to return:

```java
new MemoryItemExtractionResponse(
        List.of(
                new ExtractedItem(
                        "When payment tests fail with rounding mismatch, inspect policy, edit calc.ts, then run npm test payment.",
                        0.86f,
                        null,
                        List.of("playbooks"),
                        Map.of(
                                "trigger", "payment tests fail with rounding mismatch",
                                "steps", List.of("Inspect policy", "Edit calc.ts", "Run npm test payment"),
                                "expectedOutcome", "payment tests pass",
                                "evidenceEventIds", List.of("e3", "e4", "e5")),
                        "playbook")))
```

Assert output keeps category `playbook`, insight type `playbooks`, deterministic metadata, and evidence IDs.

Add negative tests:

- playbook with one step is dropped.
- resolution without fix is dropped.
- item with category `profile` is dropped.
- evidence ID not present in segment metadata is dropped.

- [ ] **Step 2: Run failing tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentItemExtractionStrategyLlmTest test
```

Expected: tests fail.

- [ ] **Step 3: Implement prompt builder**

Prompt must instruct:

- Categories limited to `tool`, `resolution`, `playbook`, `directive`.
- Every item must include `metadata.evidenceEventIds`.
- Playbooks require trigger, at least two steps, expected outcome.
- Resolutions require problem and fix/conclusion.
- Use current graph entity vocabulary only.
- Use causal relations only with `caused_by`, `enabled_by`, `motivated_by`.

- [ ] **Step 4: Implement LLM merge/gating**

Flow:

1. Build deterministic baseline.
2. Call LLM only when extraction options enable it and segment meets threshold.
3. Convert `MemoryItemExtractionResponse.ExtractedItem` to `ExtractedMemoryEntry`.
4. Use `ExtractedGraphHintConverter.from(item)`.
5. Merge deterministic metadata into every LLM item.
6. Drop invalid category/insight/evidence items.
7. Produce deterministic canonical content for TOOL/RESOLUTION when possible.

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentItemExtractionStrategyTest,AgentItemExtractionStrategyLlmTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java
git commit -m "feat(rawdata-agent): extract playbooks and directives"
```

### Task 11: Add rawdata-agent Pipeline Integration Tests

**Files:**
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java/com/openmemind/ai/memory/plugin/rawdata/agent/integration/AgentExtractionPipelineIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

Use `Memory.builder().rawDataPlugin(new AgentRawDataPlugin(AgentRawDataOptions.defaults()))` with a fake/no-op vector and mocked LLM where needed.

Test cases:

- Successful timeline produces TOOL item.
- Failure + edit + successful validation produces RESOLUTION item.
- Complex successful episode can produce PLAYBOOK.
- Failed unresolved episode does not produce PLAYBOOK.
- Items are AGENT categories only.
- Exact duplicate complete timeline window does not duplicate durable items.
- TOOL item metadata includes `insightTypes=["tools"]`.
- RawData metadata includes `segmentType=agent_episode`.
- RawData segment text does not include unredacted secret.

- [ ] **Step 2: Run failing integration tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent -Dtest=AgentExtractionPipelineIntegrationTest test
```

Expected: failures reveal missing wiring.

- [ ] **Step 3: Fix pipeline wiring**

Address:

- Processor registered in plugin.
- `allowedCategories()` applied.
- `supportsInsight()` true.
- `tools` insight type available.
- Duplicate complete-window behavior stable.
- Redaction happens before `Segment` persistence.

- [ ] **Step 4: Run module tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent test
```

Expected: all plugin tests pass.

- [ ] **Step 5: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/test/java \
  memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent/src/main/java
git commit -m "test(rawdata-agent): cover extraction pipeline"
```

### Task 12: Add Spring Boot Starter and Server Registration

**Files:**
- Create starter module and autoconfiguration files.
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/pom.xml`
- Modify: `memind-server/pom.xml`
- Modify: `memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerApplicationTest.java`

- [ ] **Step 1: Write starter test**

`AgentRawDataAutoConfigurationTest` should assert:

```java
assertThat(context).hasSingleBean(RawDataPlugin.class);
assertThat(context.getBean("agentRawDataPlugin")).isInstanceOf(AgentRawDataPlugin.class);
mapper.readValue("{\"type\":\"agent_timeline\",\"sourceClient\":\"claude-code\",\"sessionId\":\"s\",\"timelineId\":\"t\",\"events\":[]}", RawContent.class)
        .isInstanceOf(AgentTimelineContent.class);
```

Add disabled property test:

```java
.withPropertyValues("memind.rawdata.agent.enabled=false")
```

and assert no plugin and unsupported raw content type.

- [ ] **Step 2: Run failing starter test**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-agent-starter test
```

Expected: module does not exist.

- [ ] **Step 3: Implement starter**

Properties prefix:

```text
memind.rawdata.agent
```

Expose:

- `enabled`
- `chunking.target-episode-tokens`
- `chunking.hard-max-tokens`
- `chunking.max-events-per-episode`
- `chunking.max-event-gap`
- `extraction.extract-tool`
- `extraction.extract-resolution`
- `extraction.extract-playbook`
- `extraction.extract-directive`
- `extraction.extract-on-every-tool`
- `extraction.min-events-for-extraction`
- `extraction.min-events-for-playbook`
- `extraction.require-success-for-playbook`
- `privacy.redact-secrets`
- `privacy.max-input-chars`
- `privacy.max-output-chars`
- `privacy.capture-file-content`
- `privacy.deny-path-patterns`

Autoconfiguration bean:

```java
@Bean("agentRawDataPlugin")
@ConditionalOnMissingBean(name = "agentRawDataPlugin")
RawDataPlugin agentRawDataPlugin(AgentRawDataProperties properties) {
    return new AgentRawDataPlugin(properties.toOptions());
}
```

- [ ] **Step 4: Register server dependency**

Add to `memind-server/pom.xml`:

```xml
<dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-plugin-rawdata-agent-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

Update `MemindServerApplicationTest` to assert `agentRawDataPlugin` exists and `/extract` ObjectMapper accepts `agent_timeline`.

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-agent-starter test
mvn -pl memind-server -Dtest=MemindServerApplicationTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-plugins/memind-plugin-spring-boot-starters/pom.xml \
  memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-agent-starter \
  memind-server/pom.xml \
  memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerApplicationTest.java
git commit -m "feat(rawdata-agent): add spring boot starter"
```

### Task 13: Add JDBC JSON Codec Coverage

**Files:**
- Modify: `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-core/pom.xml`
- Modify: `memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-core/src/test/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodecTest.java`

- [ ] **Step 1: Add dependency**

Add test/runtime dependency matching `toolcall`:

```xml
<dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-plugin-rawdata-agent</artifactId>
    <version>${revision}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Add codec test**

Test:

```java
void codecRoundTripsAgentTimelineWhenPluginSubtypeIsExplicitlyRegistered() {
    var mapper = JsonCodec.createDefaultObjectMapper();
    RawContentJackson.registerAll(mapper, List.of(new AgentRawContentTypeRegistrar()));
    var content = sampleAgentTimelineContent();

    String json = mapper.writeValueAsString(content);
    RawContent restored = mapper.readValue(json, RawContent.class);

    assertThat(restored).isInstanceOf(AgentTimelineContent.class);
}
```

- [ ] **Step 3: Run test**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-core -Dtest=JsonCodecTest test
```

Expected: passes.

- [ ] **Step 4: Commit**

```bash
git add memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-core/pom.xml \
  memind-plugins/memind-plugin-jdbc/memind-plugin-jdbc-core/src/test/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodecTest.java
git commit -m "test(jdbc): cover agent timeline json codec"
```

### Task 14: Add Client Convenience APIs

**Files:**
- Python client resource/types/tests.
- Java client typed model or documented `MapRawContent` tests.
- TypeScript types/tests.

- [ ] **Step 1: Python typed request test**

Add tests:

```python
def test_extract_agent_timeline_sends_map_raw_content(httpx_mock):
    client.memory.extract_agent_timeline(
        user_id="u",
        agent_id="a",
        timeline={
            "sourceClient": "claude-code",
            "sessionId": "s",
            "timelineId": "t",
            "events": [],
        },
        source_client="claude-code",
    )
    payload = sent_json()
    assert payload["rawContent"]["type"] == "agent_timeline"
    assert payload["rawContent"]["sessionId"] == "s"
```

Add async equivalent.

- [ ] **Step 2: Implement Python convenience wrapper**

In sync/async resources:

```python
def extract_agent_timeline(self, *, user_id: str, agent_id: str, timeline: dict[str, Any], source_client: str | None = None) -> ExtractMemoryResponse:
    raw_content = {"type": "agent_timeline", **timeline}
    return self.extract(user_id=user_id, agent_id=agent_id, raw_content=raw_content, source_client=source_client)
```

Do not block early adopters on typed models.

- [ ] **Step 3: Java serialization test**

At minimum, add a test documenting:

```java
MapRawContent.of("agent_timeline", Map.of("sessionId", "s", "timelineId", "t", "events", List.of()))
```

serializes with `"type":"agent_timeline"`.

If adding typed classes, add `AgentTimelineContent`, `AgentEvent`, and tests.

- [ ] **Step 4: TypeScript type/serialization test**

Add:

```ts
const raw: RawContentValue = {
  type: 'agent_timeline',
  sessionId: 's',
  timelineId: 't',
  events: [],
}
```

Assert request serialization preserves the payload.

- [ ] **Step 5: Run client tests**

Run:

```bash
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 --extra dev pytest memind-clients/python/tests -q
mvn -pl memind-clients/java/memind-client test
PATH=/Users/zhengyate/.nvm/versions/node/v22.22.0/bin:$PATH COREPACK_HOME=/tmp/memind-corepack pnpm --dir memind-clients/typescript test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add memind-clients/python memind-clients/java/memind-client memind-clients/typescript
git commit -m "feat(clients): add agent timeline helpers"
```

### Task 15: Add Claude Code Timeline Capture

**Files:**
- Modify/create Claude Code integration files listed in File Structure.

- [ ] **Step 1: Write timeline unit tests**

Create `test_agent_timeline.py`:

```python
def test_normalizes_post_tool_use_to_command_event():
    event = normalize_hook_event({
        "hook_event_name": "PostToolUse",
        "session_id": "s",
        "tool_name": "Bash",
        "tool_input": {"command": "npm test payment"},
        "tool_response": {"exit_code": 1, "stdout": "rounding mismatch"},
        "timestamp": "2026-05-24T10:00:00Z",
    }, seq=1)
    assert event["kind"] == "command"
    assert event["command"] == "npm test payment"
    assert event["status"] == "failed"
```

Test redaction before spool:

```python
assert "sk-" not in json.dumps(event)
assert "[REDACTED" in json.dumps(event)
```

Test flush payload:

```python
payload = build_timeline_payload(
    config={"sourceClient": "claude-code"},
    identity={"userId": "u", "agentId": "a"},
    session_id="s",
    events=[event],
    hook_input={"cwd": "/tmp/project"},
)
assert payload["type"] == "agent_timeline"
assert payload["sessionId"] == "s"
assert payload["events"][0]["seq"] == 1
```

- [ ] **Step 2: Implement `agent_timeline.py`**

Functions:

- `normalize_hook_event(hook_input: dict, seq: int) -> dict`
- `append_event(state, event)`
- `build_timeline_payload(config, identity, session_id, events, hook_input) -> dict`
- `redact_text(text: str) -> tuple[str, list[str]]`
- `event_id(source_client, session_id, seq, hook_input) -> str`

Store only normalized, redacted fields.

- [ ] **Step 3: Extend `SessionState`**

Add:

```python
self.data.setdefault("agentEvents", [])
self.data.setdefault("nextAgentSeq", 1)
```

Methods:

- `append_agent_event(event)`
- `agent_events()`
- `clear_agent_events(event_ids)`
- `next_agent_seq()`

Add a soft buffer cap to prevent very long sessions from growing state without bound:

```python
MAX_AGENT_EVENTS = 500

def append_agent_event(self, event):
    events = list(self.data.get("agentEvents", []))
    if any(existing.get("eventId") == event.get("eventId") for existing in events):
        return
    events.append(event)
    if len(events) > MAX_AGENT_EVENTS:
        events = events[-MAX_AGENT_EVENTS:]
        self.data["agentEventsTruncated"] = True
    self.data["agentEvents"] = events
    self.data["updatedAt"] = time.time()
```

This cap is a local integration guard only. Server-side episode size is still controlled by `maxEventsPerEpisode`, `maxEventGap`, and chunking options.

Keep transcript submitted fingerprints behavior unchanged.

- [ ] **Step 4: Add hook scripts and hooks.json entries**

Add `PreToolUse` and `PostToolUse` entries:

```json
"PreToolUse": [{"hooks": [{"type": "command", "command": "python3 \"${CLAUDE_PLUGIN_ROOT}/scripts/pre_tool_use.py\"", "timeout": 5, "async": true}]}],
"PostToolUse": [{"hooks": [{"type": "command", "command": "python3 \"${CLAUDE_PLUGIN_ROOT}/scripts/post_tool_use.py\"", "timeout": 5, "async": true}]}]
```

Scripts must fail open and print:

```json
{"continue": true}
```

- [ ] **Step 5: Flush timeline in existing flush scripts**

In `ingest.py`, after transcript extraction attempt, flush buffered agent events when:

- `config.get("autoIngestAgentTimeline", True)` is true.
- Events exist.

Call:

```python
await client.extract(identity["userId"], identity["agentId"], timeline_payload, source_client)
```

On non-success/exception, spool a full extract payload:

```json
{
  "kind": "extract",
  "userId": "u",
  "agentId": "a",
  "sourceClient": "claude-code",
  "sessionId": "s",
  "eventIds": ["e1", "e2"],
  "rawContent": {
    "type": "agent_timeline",
    "sourceClient": "claude-code",
    "sessionId": "s",
    "agentTurnId": "s-agent-turn-1-1",
    "timelineId": "s-stop",
    "events": [
      {"eventId": "e1", "seq": 1, "kind": "command", "command": "npm test payment", "status": "failed"}
    ]
  }
}
```

Only clear events after `status == "SUCCESS"`:

```python
state.clear_agent_events([event["eventId"] for event in events])
```

Update `session_start.py` retry replay so a successful `agent_timeline` extract clears buffered event IDs:

```python
if payload.get("sessionId") and payload.get("eventIds"):
    with SessionStateStore(state_root()).locked(payload["sessionId"]) as state:
        state.clear_agent_events(payload["eventIds"])
```

Keep existing transcript `fingerprints` replay behavior unchanged.

Repeat flush path in `pre_compact.py` and `session_end.py`.

- [ ] **Step 6: Run Claude Code tests**

Run:

```bash
PYTHONPATH=memind-integrations/claude-code/scripts python3 -m unittest discover -s memind-integrations/claude-code/tests -v
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add memind-integrations/claude-code
git commit -m "feat(claude-code): capture agent timelines"
```

### Task 16: Add Codex Timeline Capture

**Files:**
- Modify/create Codex integration files listed in File Structure.

- [ ] **Step 1: Port Claude Code timeline tests to Codex**

Use Codex-specific environment names and payload fields. Tests must cover:

- `PreToolUse` / `PostToolUse` fail open.
- Event normalization.
- Stable event IDs and sequence numbers.
- Stop flush sends `agent_timeline`.
- Retry spool stores full timeline payload.
- Retry replay clears `agentEvents` by `eventIds` only after Memind returns `SUCCESS`.

- [ ] **Step 2: Implement Codex timeline buffer**

Mirror Claude Code implementation, but keep Codex-specific hook root:

```text
~/.memind/codex/state
~/.memind/codex/retry
```

and plugin env:

```text
CODEX_PLUGIN_ROOT
```

Codex retry payloads use `sessionKey` instead of Claude Code `sessionId` when the existing state store uses `state_key(hook_input)`:

```json
{
  "kind": "extract",
  "userId": "u",
  "agentId": "a",
  "sourceClient": "codex",
  "sessionKey": "codex-session-key",
  "eventIds": ["e1", "e2"],
  "rawContent": {
    "type": "agent_timeline",
    "sourceClient": "codex",
    "sessionId": "codex-session-key",
    "timelineId": "codex-session-key-stop",
    "events": []
  }
}
```

Update Codex `session_start.py` retry replay equivalent so a successful `agent_timeline` extract calls:

```python
store.clear_agent_events(payload["sessionKey"], payload["eventIds"])
```

or the matching locked-state helper if the implementation keeps the same `SessionStateStore.locked(...)` shape as Claude Code.

- [ ] **Step 3: Update hooks.json**

Add supported hooks:

```json
"PreToolUse": [
  {
    "hooks": [
      {
        "type": "command",
        "command": "python3 \"${CODEX_PLUGIN_ROOT}/scripts/pre_tool_use.py\"",
        "timeout": 5
      }
    ]
  }
],
"PostToolUse": [
  {
    "hooks": [
      {
        "type": "command",
        "command": "python3 \"${CODEX_PLUGIN_ROOT}/scripts/post_tool_use.py\"",
        "timeout": 5
      }
    ]
  }
]
```

Keep `Stop` as primary flush.

- [ ] **Step 4: Run Codex tests**

Run:

```bash
PYTHONPATH=memind-integrations/codex/scripts python3 -m unittest discover -s memind-integrations/codex/tests -v
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add memind-integrations/codex
git commit -m "feat(codex): capture agent timelines"
```

### Task 17: Format Retrieved AGENT Memories for Coding Hooks

**Files:**
- Modify: `memind-integrations/claude-code/scripts/retrieve.py`
- Modify: `memind-integrations/codex/scripts/retrieve.py`
- Tests: `test_hooks.py` in both integrations.

- [ ] **Step 1: Write formatting tests**

Input:

```python
data = {
    "items": [
        {"id": "1", "text": "Use npm test payment", "category": "tool", "metadata": {"toolName": "Bash"}},
        {"id": "2", "text": "Payment rounding mismatch was fixed", "category": "resolution", "metadata": {}},
        {"id": "3", "text": "When payment tests fail with rounding mismatch, inspect policy, edit calc.ts, then run npm test payment.", "category": "playbook", "metadata": {}},
        {"id": "4", "text": "Do not change public API", "category": "directive", "metadata": {}},
    ],
    "insights": [],
}
```

Assert formatted context contains:

```text
## Agent Playbooks
## Resolved Problems
## Tool Notes
## Directives
```

and no unrelated in-app explanation text.

- [ ] **Step 2: Implement formatter**

Change `_format_context` to group item categories:

- `playbook` -> `## Agent Playbooks`
- `resolution` -> `## Resolved Problems`
- `tool` -> `## Tool Notes`
- `directive` -> `## Directives`

Keep existing insight-first behavior for non-agent results. Limit by `retrieveMaxEntries` and `retrieveMaxChars`.

- [ ] **Step 3: Run tests**

Run:

```bash
PYTHONPATH=memind-integrations/claude-code/scripts python3 -m unittest discover -s memind-integrations/claude-code/tests -v
PYTHONPATH=memind-integrations/codex/scripts python3 -m unittest discover -s memind-integrations/codex/tests -v
```

Expected: tests pass.

- [ ] **Step 4: Commit**

```bash
git add memind-integrations/claude-code/scripts/retrieve.py \
  memind-integrations/claude-code/tests/test_hooks.py \
  memind-integrations/codex/scripts/retrieve.py \
  memind-integrations/codex/tests/test_hooks.py
git commit -m "feat(integrations): format agent memories"
```

### Task 18: Documentation and Examples

**Files:**
- Modify: `memind-integrations/claude-code/README.md`
- Modify: `memind-integrations/codex/README.md`
- Modify: `memind-clients/python/README.md`
- Modify: `memind-clients/java/memind-client/README.md` if present, otherwise nearest Java client docs.
- Modify: `memind-clients/typescript/README.md`
- Create: `docs/superpowers/specs/2026-05-24-rawdata-agent-design.md` updates only if implementation discoveries changed design.

- [ ] **Step 1: Add raw JSON example**

Document:

```json
{
  "userId": "local__alice",
  "agentId": "claude-code__project_hash",
  "sourceClient": "claude-code",
  "rawContent": {
    "type": "agent_timeline",
    "sourceClient": "claude-code",
    "sessionId": "session-123",
    "timelineId": "timeline-123",
    "events": []
  }
}
```

- [ ] **Step 2: Document configuration**

Include:

```properties
memind.rawdata.agent.enabled=true
memind.rawdata.agent.privacy.redact-secrets=true
memind.rawdata.agent.extraction.extract-on-every-tool=false
```

- [ ] **Step 3: Document limitations**

State:

- Exact duplicate complete windows are idempotent.
- Arbitrary overlapping partial windows are adapter responsibility in v1.
- File content capture is disabled by default.
- `rawdata-toolcall` remains supported.
- If `rawdata-toolcall` and `rawdata-agent` ingest the same tool activity, v1 may create semantically overlapping TOOL items. This is acceptable compatibility behavior; do not add cross-plugin suppression in v1. Users who want one canonical coding-agent path should enable `rawdata-agent` for full agent timelines and keep `rawdata-toolcall` for pure legacy tool-call logs.

- [ ] **Step 4: Run docs formatting check**

Run:

```bash
git diff --check -- docs memind-integrations memind-clients
```

Expected: no whitespace errors.

- [ ] **Step 5: Commit**

```bash
git add docs memind-integrations memind-clients
git commit -m "docs: describe agent timeline ingestion"
```

### Task 19: End-to-End Server Acceptance Tests

**Files:**
- Modify/create server integration tests:
  - `memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerIntegrationTest.java`
  - Or a new focused `AgentTimelineOpenApiIntegrationTest.java`.

- [ ] **Step 1: Add Open API ingest test**

POST to sync extract with:

```json
{
  "userId": "u",
  "agentId": "a",
  "sourceClient": "claude-code",
  "rawContent": {
    "type": "agent_timeline",
    "sourceClient": "claude-code",
    "sessionId": "s",
    "agentTurnId": "s-agent-turn-1-5",
    "timelineId": "t",
    "events": [
      {"eventId":"e1","seq":1,"kind":"user_prompt","text":"Fix payment tests","occurredAt":"2026-05-24T10:00:00Z"},
      {"eventId":"e2","seq":2,"kind":"command","toolName":"Bash","command":"npm test payment","status":"failed","output":"rounding mismatch","occurredAt":"2026-05-24T10:01:00Z"},
      {"eventId":"e3","seq":3,"kind":"file_edit","path":"src/payment/calc.ts","operation":"modify","occurredAt":"2026-05-24T10:02:00Z"},
      {"eventId":"e4","seq":4,"kind":"command","toolName":"Bash","command":"npm test payment","status":"success","occurredAt":"2026-05-24T10:03:00Z"},
      {"eventId":"e5","seq":5,"kind":"stop","occurredAt":"2026-05-24T10:04:00Z"}
    ]
  }
}
```

Assert:

- response status is success.
- at least one item id exists.
- admin item read path shows category `tool` or `resolution`.
- rawdata metadata contains `segmentType=agent_episode`.

- [ ] **Step 2: Add duplicate submission test**

Submit the same request twice. Assert durable item count for that memory does not increase on second submit.

- [ ] **Step 3: Add retrieval formatting metadata test**

Retrieve query:

```text
How should payment tests be validated?
```

Assert returned item includes:

```json
"category": "tool",
"metadata": {"commands": ["npm test payment"]}
```

- [ ] **Step 4: Run server integration tests**

Run:

```bash
mvn -pl memind-server -Dtest=AgentTimelineOpenApiIntegrationTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add memind-server/src/test/java
git commit -m "test(server): cover agent timeline open api"
```

### Task 20: Evaluation Fixtures

**Files:**
- Create: `evaluation/rawdata-agent/fixtures/auth-jwt-fix.json`
- Create: `evaluation/rawdata-agent/fixtures/payment-rounding-fix.json`
- Create: `evaluation/rawdata-agent/fixtures/project-directive.json`
- Create: `evaluation/rawdata-agent/README.md`
- Create: `evaluation/rawdata-agent/run-fixtures.py` if evaluation scripts are accepted in repo.

- [ ] **Step 1: Add fixtures**

Each fixture contains:

- request payload.
- expected categories.
- expected retrieval query.
- expected key phrases.

Example expected:

```json
{
  "expectedCategories": ["tool", "resolution"],
  "queries": [
    {
      "query": "How do I validate payment calculation changes?",
      "mustContain": ["npm test payment"]
    }
  ]
}
```

- [ ] **Step 2: Add runner**

Runner should:

1. POST fixture payload.
2. Retrieve query.
3. Check expected phrases/categories.
4. Print duplicate item rate.

- [ ] **Step 3: Run fixture tests**

Run:

```bash
python3 evaluation/rawdata-agent/run-fixtures.py --base-url http://127.0.0.1:8366
```

Expected when server is running: all fixtures pass. If no server is running, runner exits with a clear message and non-zero status.

- [ ] **Step 4: Commit**

```bash
git add evaluation/rawdata-agent
git commit -m "test(eval): add rawdata-agent fixtures"
```

### Task 21: Full Verification

**Files:** all changed files.

- [ ] **Step 1: Run formatting and whitespace checks**

Run:

```bash
git diff --check
mvn spotless:check
```

Expected: no whitespace or formatting errors.

- [ ] **Step 2: Run Maven tests**

Run:

```bash
mvn test
```

Expected: all Java tests pass.

- [ ] **Step 3: Run Python client tests**

Run:

```bash
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 --extra dev pytest memind-clients/python/tests -q
```

Expected: all Python client tests pass.

- [ ] **Step 4: Run integration Python tests**

Run:

```bash
PYTHONPATH=memind-integrations/claude-code/scripts python3 -m unittest discover -s memind-integrations/claude-code/tests -v
PYTHONPATH=memind-integrations/codex/scripts python3 -m unittest discover -s memind-integrations/codex/tests -v
```

Expected: all integration tests pass.

- [ ] **Step 5: Run TypeScript tests**

Run:

```bash
PATH=/Users/zhengyate/.nvm/versions/node/v22.22.0/bin:$PATH COREPACK_HOME=/tmp/memind-corepack pnpm --dir memind-clients/typescript test
```

Expected: all TypeScript client tests pass.

- [ ] **Step 6: Build package**

Run:

```bash
mvn -DskipTests package
```

Expected: package succeeds.

- [ ] **Step 7: Final commit**

```bash
git status --short
git add memind-core \
  memind-plugins/memind-plugin-rawdatas \
  memind-plugins/memind-plugin-spring-boot-starters \
  memind-plugins/memind-plugin-jdbc \
  memind-server \
  memind-clients \
  memind-integrations \
  docs \
  evaluation/rawdata-agent
git commit -m "feat: add rawdata agent memory support"
```

Only commit if all prior verification steps pass. Before committing, run `git diff --cached --name-only` and unstage any unrelated user changes with `git restore --staged <path>`.

---

## Acceptance Checklist

- [ ] `rawContent.type = "agent_timeline"` works through normal extraction endpoint.
- [ ] `AgentTimelineContentProcessor` returns AGENT categories, `usesSourceIdentity=true`, and `supportsInsight=true`.
- [ ] `agent_episode` is segment metadata, not a new first-class storage model.
- [ ] Redaction happens before Segment persistence, vectorization, and item extraction.
- [ ] TOOL items map to `tools` insight type.
- [ ] Existing stores get `tools` through idempotent reconciliation.
- [ ] Graph hints reuse `ExtractedMemoryEntry.graphHints()` and current entity/causal vocabulary.
- [ ] Exact duplicate complete timeline window does not create duplicate durable items.
- [ ] Claude Code and Codex capture tool events fail-open and flush complete timeline windows.
- [ ] Retrieved AGENT memories are grouped into Playbooks, Resolved Problems, Tool Notes, and Directives.
- [ ] `rawdata-toolcall` still works unchanged.
