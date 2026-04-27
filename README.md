<p align="center">
  <img src="./docs/images/memind-banner.png" alt="Memind banner">
</p>

<p align="center">
  <strong>Memory that thinks. Context that evolves.</strong>
</p>

<p align="center">
  <a href="#highlights">Highlights</a> ·
  <a href="#overview">Overview</a> ·
  <a href="#quick-start">Quick Start</a> ·
  <a href="#examples">Examples</a> ·
  <a href="#benchmark">Benchmark</a>
</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-orange" alt="License"></a>
  <a href="./README.md"><img src="https://img.shields.io/badge/English-Click-yellow" alt="English"></a>
  <a href="./README_zh.md"><img src="https://img.shields.io/badge/简体中文-点击查看-orange" alt="简体中文"></a>
  <a href="https://github.com/openmemind/memind"><img src="https://img.shields.io/github/stars/openmemind/memind?style=social" alt="GitHub Stars"></a>
</p>

<p align="center">
  <a href="#"><img src="https://img.shields.io/badge/memind-0.1.0-0A7AFF" alt="memind 0.1.0"></a>
  <a href="#"><img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21"></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen" alt="Spring Boot 4.0"></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring%20AI-2.0-green" alt="Spring AI 2.0"></a>
</p>

---

<a id="highlights"></a>

## 🏆 Highlights

**Memind** achieves **state-of-the-art results across all three benchmarks**: LoCoMo, LongMemEval, and PersonaMem.

- ☕ **The first Java-native SOTA memory and context engine for AI agents:** built natively in Java, memind brings state-of-the-art long-memory performance into the Java ecosystem.
- 🚀 **Highest reported results across all three benchmarks:** achieved **86.88%** on **LoCoMo**, **84.20%** on **LongMemEval**, and **67.91%** on **PersonaMem** under aligned **MemOS / EverMemOS-style** evaluation.
- 📈 **Stronger than the strongest published baselines:** surpassed **EverMemOS** on **LoCoMo** and **LongMemEval**, and exceeded **MemOS** on **PersonaMem**.
- 🌳 **Insight Tree turns memory into structured understanding:** instead of flat fact storage, memind organizes memory into hierarchical knowledge that evolves over time. See [Insight Tree](#insight-tree).
- 🔬 **Full benchmark details:** see the [Benchmark](#benchmark) section for complete tables, category-level comparisons, context tokens, and evaluation protocol.

## Overview

### What is Memind?

Memind is a hierarchical cognitive memory and context engine for AI agents, built natively in Java.

Instead of treating memory as a flat collection of isolated facts, Memind continuously extracts, organizes, and evolves knowledge from conversations into a structured **Insight Tree**.

It tackles two core problems of agent memory: **flat, unstructured storage** (memories remain disconnected facts with no higher-level organization) and **no knowledge evolution** (memories accumulate, but never consolidate into deeper understanding).

The result is a long-term memory and context layer that helps agents retain context, build structured understanding over time, and recall knowledge at multiple levels of abstraction.

### Core Design

#### Insight Tree

The Insight Tree is memind's core innovation. Unlike traditional memory systems that store isolated facts, memind **progressively distills knowledge** through three tiers — each tier sees patterns the previous one cannot:

| Tier | Input | What it produces |
|------|-------|-----------------|
| 🍃 **Leaf** | Grouped memory items | Insights within a single semantic group |
| 🌿 **Branch** | Multiple leaves | Cross-group patterns within one dimension |
| 🌳 **Root** | Multiple branches | Cross-dimensional insights invisible at lower levels |

**Example — understanding a user named Li Wei through conversations:**

> 🍃 **Leaf** (from career_background group):
> "Li Wei has 8 years of backend experience — 3 years at Alibaba, then led an 8-person team at a fintech company, designing a core trading system with Java 17 + Spring Cloud + Kafka."
>
> 🌿 **Branch** (integrating career + education + certifications):
> "Li Wei is a senior backend architect with deep distributed systems expertise, combining Zhejiang University CS training, large-scale Alibaba experience, and hands-on fintech system design — a well-rounded technical profile with both depth and breadth."
>
> 🌳 **Root** (cross-dimensional — identity × preferences × behavior):
> "Li Wei's preference for functional programming and high code quality (80% test coverage), combined with conservative tech adoption (requires 2+ years production validation), reveals a personality oriented toward long-term code maintainability over rapid innovation — suggesting recommendations should emphasize stability and proven patterns over cutting-edge tools."

Each tier reveals something the previous one couldn't see. Leaves know facts. Branches see patterns. Roots understand the person.

#### Two-Scope Memory

memind maintains separate memory scopes for comprehensive agent cognition:

| Scope | Categories | Purpose |
|-------|-----------|---------|
| **USER** | Profile, Behavior, Event | User identity, preferences, relationships, experiences |
| **AGENT** | Tool, Directive, Playbook, Resolution | Tool usage experience, durable instructions, reusable workflows, resolved problem knowledge |

#### Dual Retrieval Strategies

| Strategy | How it works | Best for |
|----------|-------------|----------|
| **Simple** | Vector search + BM25 keyword matching, merged via RRF (Reciprocal Rank Fusion), with adaptive truncation | Low-latency, cost-sensitive scenarios |
| **Deep** | LLM-assisted query expansion, sufficiency checking, and reranking | Complex queries requiring reasoning |

Retrieval admission is always enabled: blank queries, pure punctuation/symbol inputs, and pure emoji inputs return empty retrieval results before search. In the standard `Memory.builder()` path, oversized queries are handled by LLM long-query condensation; if condensation fails or remains invalid, retrieval returns an empty result.

#### Core Capabilities

| Category | Capability | Description |
|----------|-----------|-------------|
| **Extraction** | Conversation Segmentation | Automatic boundary detection and segmentation for streaming messages |
| | Memory Item Extraction | Extract structured facts with deduplication across 5 categories |
| | Insight Tree Construction | Hierarchical knowledge building: Leaf → Branch → Root |
| | Foresight Prediction | Predict future user needs based on conversation patterns |
| | Tool Call Statistics | Track tool usage patterns and success rates |
| **Retrieval** | Simple Strategy | Vector + BM25 hybrid search with RRF fusion and adaptive truncation |
| | Deep Strategy | LLM-assisted query expansion, sufficiency checking, and reranking |
| | Intent Routing | Automatically determine whether retrieval is needed |
| | Multi-granularity | Retrieve from any Insight Tree tier based on query needs |
| **Integration** | Pure Java Runtime | `memind-core` plus plugins assembled through `Memory.builder()` |
| | Spring Boot Infrastructure Starters | Optional infrastructure wiring with `memind-plugin-ai-spring-ai-starter` and `memind-plugin-jdbc-starter` |
| | Plugin Architecture | Pluggable store (SQLite, MySQL) and tracing (OpenTelemetry) |

---

## Quick Start

This quick start covers the default **pure Java + OpenAI + SQLite** path.

### Prerequisites

- Java 21
- Maven
- `OPENAI_API_KEY`

### Run the quickstart example

Clone the repository, set your API key, and run the maintained Java quickstart example:

```bash
git clone https://github.com/openmemind/memind.git
cd memind
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.quickstart.QuickStartExample
```

This gives you the fastest path to a working end-to-end setup. For additional runnable scenarios,
see the [Examples](#examples) section.

### Embed Memind in your app

Import the memind BOM first, then add the core runtime, the Spring AI plugin, and one JDBC
dialect plugin. For the default SQLite setup:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.openmemind.ai</groupId>
      <artifactId>memind-dependencies</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-core</artifactId>
  </dependency>
  <dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-plugin-ai-spring-ai</artifactId>
  </dependency>
  <dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-plugin-jdbc-sqlite</artifactId>
  </dependency>
</dependencies>
```

If you use MySQL or PostgreSQL instead, replace the SQLite module with
`memind-plugin-jdbc-mysql` or `memind-plugin-jdbc-postgresql`, then use the matching factory.
The factories create a `HikariDataSource` directly for plain Java usage. In Spring Boot, use
`memind-plugin-jdbc-starter` and configure `spring.datasource.*` plus optional
`spring.datasource.hikari.*`; Boot creates the `HikariDataSource` and the starter consumes it.

Outside Spring Boot, assemble the runtime objects directly and pass them into
`Memory.builder()`:

```java
OpenAiApi openAiApi = OpenAiApi.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .baseUrl(System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com"))
        .build();

OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
        .observationRegistry(ObservationRegistry.NOOP)
        .build();

EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
        openAiApi,
        MetadataMode.NONE,
        OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build());

JdbcMemoryAccess jdbc = SqliteJdbcPlugin.create("./data/memind.db");

Memory memory = Memory.builder()
        .chatClient(new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build()))
        .store(jdbc.store())
        .buffer(jdbc.buffer())
        .textSearch(jdbc.textSearch())
        .bubbleTrackerStore(jdbc.bubbleTrackerStore())
        .vector(SpringAiFileVector.file("./data/vector-store.json", embeddingModel))
        .options(MemoryBuildOptions.builder()
                .extraction(new ExtractionOptions(
                        ExtractionCommonOptions.defaults(),
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults(),
                        new InsightExtractionOptions(true, new InsightBuildConfig(2, 2, 8, 2))))
                .retrieval(RetrievalOptions.defaults())
                .build())
        .build();
```

Once the runtime is assembled, use it like this:

```java
var memoryId = DefaultMemoryId.of("user-1", "my-agent");

// messages = your conversation history
memory.addMessages(memoryId, messages).block();

var retrieval = memory.retrieve(
        memoryId,
        "What does the user prefer?",
        RetrievalConfig.Strategy.SIMPLE).block();
```

For a runnable version with centralized configuration defaults, start with
`memind-examples/memind-example-java/README.md` and
`memind-examples/memind-example-java/src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java`.

---

## Examples

The maintained Java examples live in
[`memind-examples/memind-example-java`](./memind-examples/memind-example-java).

Available scenarios:

- `quickstart`: basic `addMessages` + `retrieve`
- `agent`: agent-only extraction, insight flushing, and agent-scoped retrieval
- `insight`: multi-batch extraction with deeper synthesized retrieval
- `foresight`: foresight extraction and retrieval
- `tool`: tool call reporting and tool statistics

Run the default quickstart example:

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.quickstart.QuickStartExample \
  exec:java
```

For full setup, all runnable Maven commands, configuration knobs, and runtime data details, see
[`memind-examples/memind-example-java/README.md`](./memind-examples/memind-example-java/README.md)
and
[`ExampleSettings.java`](./memind-examples/memind-example-java/src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java).

---

## Benchmark

Memind is evaluated on three long-memory benchmarks: **LoCoMo**, **LongMemEval**, and **PersonaMem**.

**Evaluation protocol:** benchmark responses are evaluated with **GPT-4o-mini** under the **LLM-as-a-Judge** setup used by **MemOS** and **EverMemOS**.

Baseline results are reproduced or quoted from published systems under aligned settings where possible.
### LoCoMo
| Model | Single Hop | Multi Hop | Temporal | Open Domain | Overall | Context Tokens |
|-------|-----------:|----------:|---------:|------------:|--------:|---------------:|
| MIRIX | 68.22% | 54.26% | 68.54% | 46.88% | 64.33% | — |
| Mem0 | 73.33% | 58.75% | 52.34% | 45.83% | 64.57% | 1.17k |
| Zep | 66.23% | 52.12% | 54.82% | 33.33% | 59.22% | 2.7k |
| MemoBase | 73.12% | 64.65% | 81.20% | 53.12% | 72.01% | 2102 |
| Supermemory | 67.30% | 51.12% | 31.77% | 42.67% | 55.34% | 500 |
| MemU | 66.34% | 63.12% | 27.10% | 50.00% | 56.55% | 617 |
| MemOS | 81.09% | 67.49% | 75.18% | 55.90% | 75.80% | 2640 |
| ReMe | 89.89% | 82.98% | 83.80% | 71.88% | 86.23% | — |
| EverMemOS | 91.08% | 86.17% | 81.93% | 66.67% | 86.76% | 2.5k |
| **Memind** | **91.56% (+0.48%)** | **83.33% (-2.84%)** | **82.24% (+0.31%)** | **71.88% (+5.21%)** | **86.88% (+0.12%)** | **1616.68** |

> Memind delivers the strongest overall LoCoMo result in this comparison, leading the strongest baseline by **+0.12% overall** and **+5.21% on open-domain QA** while using **1616.68** context tokens per answered question.

### LongMemEval
| Model | single-session-preference | single-session-assistant | temporal-reasoning | multi-session | knowledge-update | single-session-user | overall | Context Tokens |
|-------|--------------------------:|-------------------------:|-------------------:|--------------:|-----------------:|--------------------:|--------:|---------------------:|
| MIRIX | 53.33% | 63.63% | 25.56% | 30.07% | 52.56% | 72.85% | 43.49% | — |
| Mem0 | 90.00% | 26.78% | 72.18% | 63.15% | 66.67% | 82.86% | 66.40% | 1066 |
| Zep | 53.30% | 75.00% | 54.10% | 47.40% | 74.40% | 92.90% | 63.80% | 1.6k |
| MemoBase | 80.00% | 23.21% | 75.93% | 66.91% | 89.74% | 92.85% | 72.40% | 1541 |
| Supermemory | 90.00% | 58.92% | 44.36% | 52.63% | 55.12% | 85.71% | 58.40% | 428 |
| MemU | 76.67% | 19.64% | 17.29% | 42.10% | 41.02% | 67.14% | 38.40% | 523 |
| MemOS | 96.67% | 67.86% | 77.44% | 70.67% | 74.26% | 95.71% | 77.80% | 1432 |
| EverMemOS | 93.33% | 85.71% | 77.44% | 73.68% | 89.74% | 97.14% | 83.00% | 2.8k |
| **Memind** | **95.56% (-1.11%)** | **87.50% (+1.79%)** | **79.45% (+2.01%)** | **77.44% (+3.76%)** | **88.46% (-1.28%)** | **93.81% (-3.33%)** | **84.20% (+1.20%)** | **1615.11** |

> Memind delivers the strongest overall LongMemEval result in this comparison, leading the strongest baseline by **+1.20% overall**, with the clearest gains in **multi-session (+3.76%)** and **temporal-reasoning (+2.01%)** while using **1615.11** context tokens per answered question.

### PersonaMem
| Model | 4-Option Accuracy | Context Tokens |
|-------|------------------:|---------------------:|
| MIRIX | 38.30% | — |
| Mem0 | 43.12% | 140 |
| Zep | 57.83% | 1657 |
| MemoBase | 58.89% | 2092 |
| MemU | 56.83% | 496 |
| Supermemory | 53.88% | 204 |
| MemOS | 61.17% | 1423.93 |
| **Memind** | **67.91%** | **2665.33** |

#### Memind Category-Level Results on PersonaMem

| Metric | Score | Correct / Total |
|--------|------:|----------------:|
| generalizing_to_new_scenarios | 75.44% | 43 / 57 |
| provide_preference_aligned_recommendations | 80.00% | 44 / 55 |
| recall_user_shared_facts | 71.32% | 92 / 129 |
| recalling_facts_mentioned_by_the_user | 76.47% | 13 / 17 |
| recalling_the_reasons_behind_previous_updates | 88.89% | 88 / 99 |
| suggest_new_ideas | 38.71% | 36 / 93 |
| track_full_preference_evolution | 60.43% | 84 / 139 |

> Memind delivers the strongest PersonaMem result in this comparison, with especially strong gains in preference-aligned recommendations, recall of user-shared facts, and reasoning over prior updates while using an average of 2665.33 tokens per answered question.

### Reproducing the Benchmarks

The `memind-evaluation` module is the reference pipeline used to reproduce the benchmark artifacts in this repository.

#### 1. Download the datasets first

The raw benchmark datasets are **not bundled** in this repository. You need to download them first and place them under the expected paths below. If you keep the default paths, the commands in the next steps work without extra dataset arguments.

| Benchmark | Download | Expected local path |
|-----------|----------|---------------------|
| LoCoMo | [Official LoCoMo repository](https://github.com/snap-research/locomo) | `memind-evaluation/data/locomo/locomo10.json` |
| LongMemEval | [LongMemEval cleaned dataset](https://huggingface.co/datasets/xiaowu0162/longmemeval-cleaned) | `memind-evaluation/data/longmemeval/longmemeval_s_cleaned.json` |
| PersonaMem | [PersonaMem dataset](https://huggingface.co/datasets/bowen-upenn/PersonaMem) | `memind-evaluation/data/personamem/questions_32k.csv` and `memind-evaluation/data/personamem/shared_contexts_32k.jsonl` |

LongMemEval and PersonaMem are converted automatically into the internal evaluation format at runtime.

If you want to use a custom dataset location instead of the default paths above, override it with:

- `--evaluation.datasets.locomo.path=...`
- `--evaluation.datasets.longmemeval.path=...`
- `--evaluation.datasets.personamem.path=...`

#### 2. Export credentials

```bash
export OPENAI_API_KEY=your-key
export OPENAI_BASE_URL=your-base-url
export OPENAI_CHAT_MODEL=openai/gpt-4o-mini

export EMBEDDING_API_KEY=your-key
export EMBEDDING_BASE_URL=your-base-url
export OPENAI_EMBEDDING_MODEL=openai/text-embedding-3-small

# Recommended if you want to match the published retrieval setup
export RERANK_BASE_URL=your-rerank-base-url
export RERANK_API_KEY=your-rerank-key
export RERANK_MODEL=jina-reranker-v3
```

If you want a minimal run without rerank, append `--evaluation.system.memind.retrieval.rerank.enabled=false` to the commands below. That is useful for a dry run, but it will not match the reported results.

#### 3. Run a full benchmark

The evaluation app defaults to `search,answer,evaluate` in `application.yml`. For a fresh reproduction, explicitly run the full pipeline with `add,search,answer,evaluate`.

```bash
# LoCoMo
mvn -pl memind-evaluation -am -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=locomo \
  -Dspring-boot.run.arguments="--evaluation.run-name=locomo-readme --evaluation.stages=add,search,answer,evaluate --evaluation.clean-groups=true"

# LongMemEval
mvn -pl memind-evaluation -am -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=longmemeval \
  -Dspring-boot.run.arguments="--evaluation.run-name=longmemeval-readme --evaluation.stages=add,search,answer,evaluate --evaluation.clean-groups=true"

# PersonaMem
mvn -pl memind-evaluation -am -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=personamem \
  -Dspring-boot.run.arguments="--evaluation.run-name=personamem-readme --evaluation.stages=add,search,answer,evaluate --evaluation.clean-groups=true"
```

#### 4. Run a smoke check first

```bash
mvn -pl memind-evaluation -am -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=locomo \
  -Dspring-boot.run.arguments="--evaluation.run-name=locomo-smoke --evaluation.stages=add,search,answer,evaluate --evaluation.smoke=true --evaluation.from-conv=0 --evaluation.to-conv=1 --evaluation.clean-groups=true"
```

#### 5. Inspect the outputs

Each run writes its artifacts to `eval-data/results/<dataset>-<run-name>/`:

- `report.txt`
- `search_results.json`
- `answer_results.json`
- `eval_results.json`

Re-running the same `run-name` resumes from checkpoints. Use a new `run-name` for an independent run.

---

## Contributing

Contributions are welcome! Feel free to open an [issue](https://github.com/openmemind/memind/issues) or submit a pull request.

## Community
- [LINUX DO](https://linux.do/)

## License

[Apache License 2.0](LICENSE)
