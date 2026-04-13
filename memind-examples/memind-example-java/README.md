# Memind Java Examples

This module keeps each example as an independent `main` class so you can run a
single scenario directly from your IDE or with Maven.

## Examples

- `quickstart`: basic `addMessages` + `retrieve`
- `foresight`: foresight extraction and display
- `insight`: multi-batch extraction with deeper synthesized retrieval
- `agent`: agent-only extraction, insight flushing, and agent-scoped retrieval
- `tool`: tool call reporting and tool statistics
- `document`: parser-backed ingestion of a bundled multi-document release knowledge pack

## Configuration

Open
[`src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java`](src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java)
first.

It is the single place that shows:

- OpenAI defaults and override keys
- rerank defaults and override keys
- JDBC/store defaults and override keys
- runtime directory defaults

### Sensitive Values

Prefer environment variables or system properties for secrets:

- `OPENAI_API_KEY`
- `RERANK_API_KEY` when rerank is enabled

### Minimal Setup

For the default SQLite-backed examples:

1. Set `OPENAI_API_KEY`
2. Optionally set `MEMIND_EXAMPLES_RERANK_ENABLED=true` and `RERANK_API_KEY`
3. Run one of the examples below

## Run From Maven

Quickstart:

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.quickstart.QuickStartExample
```

Foresight:

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.foresight.ForesightExample
```

Insight Tree:

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.insight.InsightTreeExample
```

Agent Scope:

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.agent.AgentScopeMemoryExample
```

Tool Memory:

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.tool.ToolMemoryExample
```

Document Memory:

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.document.DocumentMemoryExample
```

The built-in document example ingests a realistic billing-release knowledge pack:

- `release-readiness.md`
- `migration-runbook.md`
- `incident-retrospective.html`
- `service-ownership.csv`
- `weekend-handoff.txt`

The example runs both direct fact lookup and cross-document retrieval over that corpus.

If you want to swap in your own Markdown, HTML, CSV, TXT, or PDF inputs, edit
[`DocumentMemoryExample.java`](src/main/java/com/openmemind/ai/memory/example/java/document/DocumentMemoryExample.java)
and replace the defaults returned by `defaultDocuments()` and `defaultQueryCases()`.

## Runtime Data

By default, example runtime data is written under:

```text
target/example-runtime/<scenario>
```

You can override the runtime root or JDBC URL through `ExampleSettings`.
