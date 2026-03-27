<p align="center">
  <h1 align="center">Memind Memory</h1>
</p>

<p align="center">
  <strong>Self-evolving cognitive memory for AI agents — not just storage, but understanding.</strong>
</p>

<p align="center">
  <a href="./README.md"><img src="https://img.shields.io/badge/English-Click-yellow" alt="English"></a>
  <a href="./README_zh.md"><img src="https://img.shields.io/badge/简体中文-点击查看-orange" alt="简体中文"></a>
  <a href="https://github.com/openmemind/memind"><img src="https://img.shields.io/github/stars/openmemind-ai/memind?style=social" alt="GitHub Stars"></a>
</p>

<p align="center">
  <a href="#"><img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21"></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen" alt="Spring Boot 4.0"></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring%20AI-2.0-green" alt="Spring AI 2.0"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-orange" alt="License"></a>
  <a href="#"><img src="https://img.shields.io/badge/Maven%20Central-coming%20soon-lightgrey" alt="Maven Central"></a>
</p>

---

memind is a **hierarchical cognitive memory system** for AI agents, built natively in Java. It goes beyond simple key-value memory — memind automatically extracts, organizes, and evolves knowledge from conversations into a structured **Insight Tree**, enabling agents to truly understand and remember.

It tackles the core problems of agent memory: **flat, unstructured storage** (memories are isolated facts with no relationships) and **no knowledge evolution** (memories never grow or consolidate).

---

## Why memind?

| Traditional Memory Systems | memind |
|---------------------------|--------|
| 🗄️ Flat key-value storage | 🌳 Hierarchical Insight Tree (Leaf → Branch → Root) |
| 📝 Store raw facts only | 🧠 Self-evolving cognition — items are analyzed into multi-level insights |
| 🔍 Single-level retrieval | 🎯 Multi-granularity retrieval (detail → summary → profile) |
| 💰 Requires expensive models | 🏆 SOTA performance with gpt-4o-mini |
| 🔧 Manual memory management | ⚡ Fully automatic extraction pipeline |

---

## Highlights

### 🌳 Insight Tree — Hierarchical Knowledge, Not Flat Storage

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

### 🏆 SOTA with Lightweight Model

Achieved **86.88% overall** on the LoCoMo benchmark using only **gpt-4o-mini** — a lightweight, cost-effective model. This proves that intelligent memory architecture matters more than brute-force model power.

### ☕ Java-Native — First SOTA Memory for the Java Ecosystem

The first Java-based AI memory system to achieve SOTA-level performance. Built with **Spring Boot 4.0** and **Spring AI 2.0**, memind integrates naturally into Java/Kotlin enterprise stacks with a one-line Maven dependency.

---

## Architecture

memind processes conversations through a multi-stage pipeline, from raw dialogue to structured knowledge:

![Architecture](docs/images/mermaid-architecture.png)

### Two-Scope Memory

memind maintains separate memory scopes for comprehensive agent cognition:

| Scope | Categories | Purpose |
|-------|-----------|---------|
| **USER** | Profile, Behavior, Event | User identity, preferences, relationships, experiences |
| **AGENT** | Tool, Procedural | Tool usage patterns, reusable procedures, learned workflows |

### Dual Retrieval Strategies

| Strategy | How it works | Best for |
|----------|-------------|----------|
| **Simple** | Vector search + BM25 keyword matching, merged via RRF (Reciprocal Rank Fusion), with adaptive truncation | Low-latency, cost-sensitive scenarios |
| **Deep** | LLM-assisted query expansion, sufficiency checking, and reranking | Complex queries requiring reasoning |

---

## Benchmark

### LoCoMo

Evaluation on the [LoCoMo](https://github.com/snap-research/locomo) benchmark using **gpt-4o-mini**:

| Method | Single Hop | Multi Hop | Temporal | Open Domain | Overall |
|--------|-----------|-----------|----------|-------------|---------|
| **memind (gpt-4o-mini)** | **91.56** | **83.33** | **82.24** | **71.88** | **86.88** |

> memind achieves SOTA-level performance using only gpt-4o-mini — a lightweight, cost-effective model.

---

## Quick Start

### Installation

Build and install locally:

```bash
git clone https://github.com/openmemind-ai/memind.git
cd memind
mvn clean install
```

Then add the Spring Boot Starter to your project's `pom.xml`:

```xml
<dependency>
  <groupId>com.openmemind.ai</groupId>
  <artifactId>memind-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Configuration

Configure in `application.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: text-embedding-3-small

memind:
  store:
    type: sqlite
    sqlite:
      path: ./data/memind.db
```

### Usage

```java
// Create a memory identity (user + agent)
MemoryId memoryId = DefaultMemoryId.of("user-1", "my-agent");

// Extract knowledge from conversations
memory.addMessages(memoryId, messages).block();

// Retrieve relevant memories
var result = memory.retrieve(memoryId, "What does the user prefer?",
        RetrievalConfig.Strategy.SIMPLE).block();
```

### Pure Java Bootstrap

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

JdbcMemoryAccess jdbc = JdbcStore.sqlite("./data/memind.db");

Memory memory = Memory.builder()
        .chatClient(new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build()))
        .store(jdbc.store())
        .textSearch(jdbc.textSearch())
        .vector(SpringAiFileVector.file("./data/vector-store.json", embeddingModel))
        .options(MemoryBuildOptions.builder()
                .insightBuild(new InsightBuildConfig(2, 2, 8, 2))
                .build())
        .build();
```

---

## Examples

Clone and run examples to see memind in action:

```bash
git clone https://github.com/openmemind-ai/memind.git
cd memind
```

Examples now live under `memind-examples/` and share one data directory at `memind-examples/data`.

Configure `OPENAI_API_KEY`, then run one of the Spring Boot examples:

```bash
# Basic extract + retrieve
mvn -pl memind-examples/memind-example-spring-boot -am spring-boot:run \
  -Dspring-boot.run.mainClass=com.openmemind.ai.memory.example.springboot.quickstart.QuickStartExample
```

Pure Java examples are available in `memind-examples/memind-example-java`. Run the same scenario mains from your IDE, or invoke them with Maven Exec Plugin using the fully qualified class names below.

They now use the same object-first builder approach shown above.

| Runtime | Example | Main Class | Description |
|---------|---------|------------|-------------|
| Spring Boot | **QuickStart** | `com.openmemind.ai.memory.example.springboot.quickstart.QuickStartExample` | Basic extract + retrieve flow |
| Spring Boot | **Agent Scope** | `com.openmemind.ai.memory.example.springboot.agent.AgentScopeMemoryExample` | Agent-scope extraction, insight tree flush, and retrieval for directives, playbooks, and resolutions |
| Spring Boot | **Insight** | `com.openmemind.ai.memory.example.springboot.insight.InsightTreeExample` | Insight Tree multi-tier generation (Leaf → Branch → Root) |
| Spring Boot | **Foresight** | `com.openmemind.ai.memory.example.springboot.foresight.ForesightExample` | Predictive memory — anticipate user needs |
| Spring Boot | **Tool** | `com.openmemind.ai.memory.example.springboot.tool.ToolMemoryExample` | Tool call tracking and aggregated tool statistics |
| Pure Java | **QuickStart** | `com.openmemind.ai.memory.example.java.quickstart.QuickStartExample` | Object-first builder with direct Spring AI runtime objects |
| Pure Java | **Agent Scope** | `com.openmemind.ai.memory.example.java.agent.AgentScopeMemoryExample` | Object-first agent-scope extraction with insight tree flush and targeted retrieval |
| Pure Java | **Insight** | `com.openmemind.ai.memory.example.java.insight.InsightTreeExample` | Object-first builder with custom `MemoryBuildOptions` |
| Pure Java | **Foresight** | `com.openmemind.ai.memory.example.java.foresight.ForesightExample` | Pure Java foresight extraction and retrieval |
| Pure Java | **Tool** | `com.openmemind.ai.memory.example.java.tool.ToolMemoryExample` | Pure Java tool call tracking and aggregated statistics |

---

## Core Capabilities

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
| **Integration** | Spring Boot Starter | Auto-configuration with `memind-spring-boot-starter` |
| | Plugin Architecture | Pluggable store (SQLite, MySQL) and tracing (OpenTelemetry) |

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.0, Spring AI 2.0 |
| Data Store | SQLite (default), MySQL (plugin) |
| Vector Store | Qdrant, JSON file (for examples) |
| Observability | OpenTelemetry, Micrometer |
| Build | Maven |

---

## Contributing

Contributions are welcome! Feel free to open an [issue](https://github.com/openmemind/memind/issues) or submit a pull request.

## License

[Apache License 2.0](LICENSE)
