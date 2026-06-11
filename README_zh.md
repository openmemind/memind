<p align="center">
  <img src="./docs/images/memind-banner.png" alt="Memind banner">
</p>

<h1 align="center">Memind</h1>

<h3 align="center">
  <em>让记忆形成理解，让上下文持续进化。</em>
</h3>

<p align="center">
  让 AI 系统从每一次对话、工具调用、文档和已解决任务中持续学习的记忆层。
</p>

<p align="center">
  Memind 将原始上下文沉淀为结构化记忆和可复用经验，
  持续组织成 Memory Graph、Memory Thread 和不断演化的 Insight Tree，
  再通过 REST、MCP、多语言 SDK，以及面向主流 Agent 的官方插件，
  召回正确上下文。
</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-orange" alt="License"></a>
  <a href="#"><img src="https://img.shields.io/badge/Version-0.2.0-0A7AFF" alt="Version 0.2.0"></a>
  <a href="#"><img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21"></a>
  <a href="./README.md"><img src="https://img.shields.io/badge/English-Click-yellow" alt="English"></a>
  <a href="https://github.com/openmemind/memind"><img src="https://img.shields.io/github/stars/openmemind/memind?style=social" alt="GitHub Stars"></a>
</p>

<p align="center">
  <a href="#benchmark"><img src="./docs/images/badges/locomo.svg" alt="LoCoMo rank #1 among listed baselines" height="38"></a>
  <a href="#benchmark"><img src="./docs/images/badges/longmemeval.svg" alt="LongMemEval rank #1 among listed baselines" height="38"></a>
  <a href="#benchmark"><img src="./docs/images/badges/personamem.svg" alt="PersonaMem rank #1 among listed baselines" height="38"></a>
  <a href="#mcp-server"><img src="./docs/images/badges/mcp-tools.svg" alt="11+ MCP tools" height="38"></a>
  <a href="#official-api-clients"><img src="./docs/images/badges/sdks.svg" alt="5 SDKs" height="38"></a>
  <a href="#agent-integrations"><img src="./docs/images/badges/agent-plugins.svg" alt="4 agent plugins" height="38"></a>
</p>

<p align="center">
  <a href="#highlights">亮点</a> ·
  <a href="#quick-start">快速开始</a> ·
  <a href="#mcp-server">MCP Server</a> ·
  <a href="#agent-integrations">Agent 集成</a> ·
  <a href="#benchmark">基准测试</a>
</p>

---

<a id="highlights"></a>

## 🏆 亮点

**Memind** 在三项主流长记忆基准测试上都取得了 **SOTA** 结果：**LoCoMo**、**LongMemEval** 和 **PersonaMem**。

- ☕ **首个达到 SOTA 水平的 Java 原生记忆与上下文引擎：** memind 基于 Java 原生构建，让 Java 生态也拥有可验证、可落地的长记忆能力。
- 🚀 **三项 benchmark 的已公开最高结果：** 在与 **MemOS / EverMemOS** 对齐的评测口径下，memind 在 **LoCoMo**、**LongMemEval** 和 **PersonaMem** 的已列出基线中均排名 **#1**，其中在 **LoCoMo** 和 **LongMemEval** 上超过 **EverMemOS**，在 **PersonaMem** 上超过 **MemOS**。完整分数、分项对比、上下文 Token 和评测协议见 [基准测试](#benchmark)。
- 🧩 **一套引擎，同时记住用户和 Agent：** memind 区分 USER memory 和 AGENT memory，既能记住用户画像、偏好与生活上下文，也能沉淀 Agent 指令、工具经验、可复用 playbook 和已解决任务知识，可覆盖 coding agent、本地 harness agent、chatbot、陪伴型应用、copilot 和工作流 Agent 等场景。
- 🌳 **Insight Tree 让记忆进化成理解：** memind 不只是保存孤立事实，而是持续将原始记忆提炼为 Leaf → Branch → Root 洞察，发现扁平记忆无法捕捉的模式、偏好、因果信号和高层理解。详见 [docs.openmemind.com](https://docs.openmemind.com)。
- 🔎 **多层检索召回正确上下文：** memind 会跨 Insight Tree、Memory Item、原始 source data、Memory Graph、Memory Thread、向量检索、BM25 关键词检索、时间信号，以及可选的 Deep Retrieval 查询扩展、充分性检查和重排序来召回上下文。
- 📥 **记住各种类型的上下文：** memind 不只支持对话，还可以摄取文档、图片、音频、工具调用和 Agent 时间线，并通过类型化 processor、parser、chunker、captioner 和插件专属提取策略，将它们转化为可检索记忆。
- 🕸️ **Memory Graph 连接分散上下文：** memind 会从提取出的记忆中构建实体、mention、语义链接、时间链接、因果链接、别名和共现信号，并在检索时通过 graph expansion 找回单纯向量相似度可能漏掉的相关上下文。
- 🧵 **Memory Thread 保留持续演化的任务与事件脉络：** memind 会把相关 memory items 组织成持久线程，维护时间线事件、成员关系、生命周期状态、线程 enrichment 和 retrieval-time thread assist，帮助 Agent 延续未完成工作并复用已解决任务历史。

<a id="overview"></a>

## 概览

### Memind 是什么？

Memind 是一个开源的自进化记忆与上下文引擎，面向各种 AI 应用和 Agent。

它不是向量库封装。Memind 可以从对话、文档、图片、音频、工具调用、Agent 时间线和已解决任务中捕获原始上下文，并将其转化为结构化用户记忆、可复用 Agent 经验、持续演化的 Insight、互相关联的 Memory Graph，以及具备任务脉络的 Memory Thread。

在检索阶段，Memind 会跨这些记忆层编排正确上下文，并通过 REST API、HTTP MCP tools、SDK、Java runtime API 和官方 Agent 集成暴露给上层 AI 系统。

### Memind 如何工作？

<p align="center">
  <img src="./docs/images/memind-work-pipeline.png" alt="Memind 记忆与上下文引擎工作流程">
</p>

Memind 会把原始来源、提取出的记忆、结构化理解、图关系和任务时间线连接起来。这样 AI 系统召回的不只是扁平片段，而是同时包含精确证据和高层上下文。

### Memind 适合用来做什么？

Memind 是一层通用的长期记忆与上下文引擎，几乎适用于任何需要长期上下文的 AI 系统。常见场景包括：

| 场景 | Memind 记住什么 |
|------|----------------|
| Coding Agent | 项目上下文、工具经验、已解决任务、持久指令 |
| 本地个人 Agent | 用户偏好、长期时间线、本地工作流 |
| Chatbot 与陪伴型应用 | 用户画像、关系、行为模式、生活事件 |
| 工作流 Agent | 指令、playbook、业务上下文、任务历史 |

这些只是典型示例。Memind 同样可以用于 copilot、企业助手、客服自动化、研究工具、知识工作助手，以及任何需要跨会话记住用户、任务、文档、决策、工具、时间线和历史结果的 AI 应用。

更深入的架构、配置、rawdata 插件、MCP tools、SDK 和 Agent 集成说明，请查看 [docs.openmemind.com](https://docs.openmemind.com)。

<a id="quick-start"></a>

---

## 快速开始

Memind 支持两种启动方式：

- **Docker Compose（推荐）：** 一条命令启动 `memind-server` 和管理 UI。
- **本地开发启动：** 从源码分别启动 `memind-server` 和 `memind-ui`。

### 方式一：Docker Compose（推荐）

#### 前置要求

- Docker，并已启用 Compose 插件
- 你准备使用的 chat 和 embedding 模型凭据

#### 配置 `.env`

创建本地 `.env` 文件：

```bash
cp .env.example .env
```

默认配置下，优先修改这些值：

```env
OPENAI_API_KEY=your-api-key
OPENAI_BASE_URL=https://openrouter.ai/api
OPENAI_CHAT_MODEL=openai/gpt-4o-mini
OPENAI_EMBEDDING_MODEL=openai/text-embedding-3-small
```

默认的 `openai` client 不只可以连接 OpenAI，也可以连接任何兼容 OpenAI 协议的 endpoint。
例如 OpenRouter、DeepSeek、GLM、SiliconFlow 等 provider，只要提供兼容 OpenAI 的 API，
就可以通过修改 `OPENAI_BASE_URL` 和模型名称接入。

模型名称由 provider 决定。默认值使用的是 OpenRouter 风格的模型名。如果你直接使用 OpenAI，
请使用 OpenAI 的模型名，例如 `gpt-4o-mini` 和 `text-embedding-3-small`。

#### 启动 Memind

```bash
docker compose up -d --build
```

容器启动后可以访问：

- 管理 UI：`http://localhost:8080`
- 服务健康检查：`http://localhost:8366/open/v1/health`
- Open API 基础路径：`http://localhost:8366/open/v1`
- Admin API 基础路径：`http://localhost:8366/admin/v1`
- HTTP MCP 端点：`http://localhost:8366/mcp`

#### 验证服务

```bash
curl http://localhost:8366/open/v1/health
```

健康检查只验证服务已经启动。模型凭据会在 Memind 执行 extraction、retrieval、embedding 或 rerank
调用时被真正验证。

UI 容器会把 `/open/*` 和 `/admin/*` 代理到 `memind-server`，因此浏览器可以把 UI
当作同源的本地管理控制台使用。

#### 常用命令

```bash
# 查看日志
docker compose logs -f memind-server
docker compose logs -f memind-ui

# 停止容器，但保留已持久化的记忆数据
docker compose down

# 停止容器，并删除已持久化的记忆数据
docker compose down -v
```

默认情况下，`memind-server` 会把 SQLite 数据和 fallback file vector store 存放在 Docker volume
`memind-data` 中，容器内挂载路径为 `/app/data`。

这套 Compose 配置面向本地开发和数据检查。管理 UI 没有内置认证能力，请不要直接暴露到公网。

<details>
<summary>高级配置：配置模型路由</summary>

Memind 的 AI 配置分为两层：

| 配置层 | 作用 |
|--------|------|
| `spring.ai.*` | provider 默认参数、API key、base URL 和模型 options |
| `memind.ai.*` | Memind 内部的 named chat/embedding client，以及 memory pipeline 的 slot routing |

默认服务配置定义了一个 chat client 和一个 embedding client：

```yaml
memind:
  ai:
    chat:
      default-client: openai
      clients:
        openai:
          provider: openai
    embedding:
      client: openai
      clients:
        openai:
          provider: openai
```

`memind.ai` 支持的 provider：

| 用途 | Provider |
|------|----------|
| Chat | `openai`, `anthropic`, `google`, `ollama` |
| Embedding | `openai`, `google`, `ollama` |

DeepSeek、GLM、OpenRouter、SiliconFlow 这类兼容 OpenAI 协议的服务，都使用
`provider: openai`。

如果需要更细粒度的模型路由，可以在
[`application.yml`](./memind-server/src/main/resources/application.yml) 中定义多个 named client，
再把不同 memory pipeline slot 指向不同 client：

```yaml
memind:
  ai:
    chat:
      default-client: ds
      clients:
        ds:
          provider: openai
          base-url: https://api.deepseek.com
          api-key: ${DEEPSEEK_API_KEY}
          model: deepseek-chat
        ds_reasoner:
          provider: openai
          base-url: https://api.deepseek.com
          api-key: ${DEEPSEEK_API_KEY}
          model: deepseek-reasoner
        claude:
          provider: anthropic
          api-key: ${ANTHROPIC_API_KEY}
          model: claude-sonnet-4-5
      slots:
        ITEM_EXTRACTION: ds
        INSIGHT_GENERATOR: ds_reasoner
        THREAD_ENRICHMENT: claude
```

没有单独配置的 slot 会自动使用 `default-client`。

如果使用 Docker Compose，修改 `application.yml` 后需要重新构建镜像：

```bash
docker compose up -d --build
```

完整配置说明见 [docs.openmemind.com](https://docs.openmemind.com)。

</details>

### 方式二：本地开发启动

如果你要开发 Memind 本身，或者希望直接从源码启动 Server 和 UI，使用这种方式。

#### 前置要求

- Java 21
- Maven
- Node.js 20.19+ 或 22+
- pnpm
- 可用的模型 provider key

#### 启动 `memind-server`

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-server -am spring-boot:run
```

Server 启动后可以访问：

- 服务健康检查：`http://localhost:8366/open/v1/health`
- Open API 基础路径：`http://localhost:8366/open/v1`
- Admin API 基础路径：`http://localhost:8366/admin/v1`
- HTTP MCP 端点：`http://localhost:8366/mcp`

#### 启动 `memind-ui`

另开一个终端：

```bash
cd memind-ui
pnpm install
pnpm dev
```

Vite dev server 默认启动在 `http://localhost:5173`，并会把 `/admin/*` 请求代理到
`8366` 端口上的 `memind-server`。

Java runtime 示例、SDK 使用方式和更多可运行场景见 [示例](#examples)。

<a id="mcp-server"></a>

### HTTP MCP Server

`memind-server` 内置了一个无状态 HTTP MCP server，默认路径为 `/mcp`，默认启用。它会把
Memind 记忆能力暴露给兼容 MCP 的 Agent，并复用同一套运行时、数据库、配置和日志。

Claude Code 可以这样连接本地服务：

```bash
claude mcp add --transport http memind http://localhost:8366/mcp
```

默认 MCP tools：

- 检索与上下文：`memind_compile_context`、`memind_retrieve`、`memind_recent`。
- 写入流程：`memind_extract_text`、`memind_extract_rawdata`、`memind_add_message`、`memind_commit`。
- Memory item 检查：`memind_items_search`、`memind_items_get`、`memind_items_sources`。
- Rawdata 检查：`memind_rawdata_search`、`memind_rawdata_get`。

当 Agent 需要一段精简、分组后的上下文时，优先使用 `memind_compile_context`；当它需要结构化检索结果时，使用
`memind_retrieve`。`memind_extract_text` 适合一次性的文本记忆，`memind_extract_rawdata` 适合 typed rawdata，
`memind_add_message` 加 `memind_commit` 适合对话流式记忆。

可选治理工具 `memind_forget` 默认关闭。设置 `MEMIND_MCP_GOVERNANCE_ENABLED=true` 后启用；它默认 dry-run，
要求填写非空 reason，并且只会删除匹配传入 `userId` 和 `agentId` 的 `ITEM` 或 `RAWDATA` 记录。

如果要关闭 MCP 端点，在启动 `memind-server` 前设置 `MEMIND_MCP_ENABLED=false`。

不要在没有鉴权网关或等价网络控制的情况下把 `/mcp` 直接暴露到公网。MCP tools 可以读写对应作用域下的记忆。

<a id="agent-integrations"></a>

### Agent 集成

Memind 为主流 Agent 提供官方集成：

- [`Claude Code`](./memind-integrations/claude-code)：项目长期记忆、SessionStart 连续性上下文、
  tool-aware 上下文注入，以及 coding-agent timeline 自动写入。
- [`Codex`](./memind-integrations/codex)：项目长期记忆、prompt/tool 上下文注入、带重试的
  timeline 写入，以及 Codex 会话 source tagging。
- [`OpenClaw`](./memind-integrations/openclaw)：prompt 前自动召回 Memind 记忆，并把完成后的
  OpenClaw agent 活动写成 `agent_timeline` raw data。
- [`Hermes`](./memind-integrations/hermes)：原生 Hermes memory provider，在每轮前召回相关上下文，
  并在响应后捕获完成的 Hermes 活动。

### 将 Memind 接入你的应用

先导入 memind BOM，再引入 core runtime、Spring AI 插件以及一个 JDBC 方言插件。默认的 SQLite 配置如下：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.openmemind.ai</groupId>
      <artifactId>memind-dependencies</artifactId>
      <version>0.2.0</version>
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

如果你使用 MySQL 或 PostgreSQL，把 SQLite 模块替换为 `memind-plugin-jdbc-mysql` 或 `memind-plugin-jdbc-postgresql`，然后使用对应工厂。纯 Java 场景下方言工厂会直接创建 `HikariDataSource`；Spring Boot 场景下使用 `memind-plugin-jdbc-starter`，配置 `spring.datasource.*` 以及可选的 `spring.datasource.hikari.*`，由 Boot 创建 `HikariDataSource`，starter 只消费这个连接池。

在非 Spring Boot 场景下，可以直接组装运行时对象，然后交给 `Memory.builder()`：

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

组装完成后，最基本的使用方式如下：

```java
var memoryId = DefaultMemoryId.of("user-1", "my-agent");

// messages = 你的对话历史
memory.addMessages(memoryId, messages).block();

var retrieval = memory.retrieve(
        memoryId,
        "What does the user prefer?",
        RetrievalConfig.Strategy.SIMPLE).block();
```

如果你想直接从“可运行、配置集中、默认值清晰”的版本开始，优先看
[`ExampleSettings.java`](./memind-examples/memind-example-java/src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java)
和维护中的 Java 示例目录
[`memind-examples/memind-example-java`](./memind-examples/memind-example-java)。

<a id="official-api-clients"></a>

官方 API clients：

- TypeScript：[`memind-clients/typescript`](./memind-clients/typescript)
- Python：[`memind-clients/python`](./memind-clients/python)
- Java：[`memind-clients/java`](./memind-clients/java)
- Go：[`github.com/openmemind/memind/memind-clients/go`](./memind-clients/go)
- Rust：[`memind-clients/rust`](./memind-clients/rust)

<a id="examples"></a>

---

## 示例

当前维护中的 Java 示例位于
[`memind-examples/memind-example-java`](./memind-examples/memind-example-java)。

可运行场景包括：

- `quickstart`：基础 `addMessages` + `retrieve`
- `agent`：仅 agent 侧提取、insight flush 和 agent scope 检索
- `insight`：多批次提取与更深层的综合检索
- `foresight`：foresight 提取与检索
- `tool`：工具调用上报与工具统计

默认 quickstart 的运行方式如下：

```bash
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.quickstart.QuickStartExample \
  exec:java
```

更多配置项和运行时数据说明，请查看
[`ExampleSettings.java`](./memind-examples/memind-example-java/src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java)。

<a id="benchmark"></a>

---

## 基准测试

Memind 在三项长记忆 benchmark 上进行了评测：**LoCoMo**、**LongMemEval** 和 **PersonaMem**。

**评测协议：** 回答结果使用 **GPT-4o-mini**，并按照 **MemOS** 与 **EverMemOS** 所采用的 **LLM-as-a-Judge** 方式进行评估。

基线结果在条件允许时采用对齐设置下的复现结果或公开结果。

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

> 在这组对比中，Memind 拿到了最高的 LoCoMo 总分，整体比最强基线高 **0.12%**，其中 open-domain QA 提升 **5.21%**，每题平均消耗 **1616.68** 个上下文 Token。

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

> 在这组对比中，Memind 拿到了最高的 LongMemEval 总分，整体比最强基线高 **1.20%**；其中提升最明显的是 **multi-session (+3.76%)** 和 **temporal-reasoning (+2.01%)**，每题平均消耗 **1615.11** 个上下文 Token。

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

#### Memind 在 PersonaMem 上的分项结果

| Metric | Score | Correct / Total |
|--------|------:|----------------:|
| generalizing_to_new_scenarios | 75.44% | 43 / 57 |
| provide_preference_aligned_recommendations | 80.00% | 44 / 55 |
| recall_user_shared_facts | 71.32% | 92 / 129 |
| recalling_facts_mentioned_by_the_user | 76.47% | 13 / 17 |
| recalling_the_reasons_behind_previous_updates | 88.89% | 88 / 99 |
| suggest_new_ideas | 38.71% | 36 / 93 |
| track_full_preference_evolution | 60.43% | 84 / 139 |

> 在这组对比中，Memind 拿到了最高的 PersonaMem 成绩，在 preference-aligned recommendations、user-shared facts recall 和对历史更新原因的推理上表现尤其突出，同时每题平均消耗 **2665.33** 个上下文 Token。

### 如何复现这些 benchmark

如果你想复现这里展示的 benchmark 结果，可以使用仓库中的 `memind-evaluation` 模块。

#### 1. 先准备数据集

这些 benchmark 的原始数据集 **不随仓库一起分发**。请先下载，并按下面的默认路径放好文件。若使用默认路径，后续命令无需额外指定数据集路径。

| Benchmark | 下载地址 | 默认本地路径 |
|-----------|----------|--------------|
| LoCoMo | [LoCoMo 官方仓库](https://github.com/snap-research/locomo) | `memind-evaluation/data/locomo/locomo10.json` |
| LongMemEval | [LongMemEval cleaned dataset](https://huggingface.co/datasets/xiaowu0162/longmemeval-cleaned) | `memind-evaluation/data/longmemeval/longmemeval_s_cleaned.json` |
| PersonaMem | [PersonaMem dataset](https://huggingface.co/datasets/bowen-upenn/PersonaMem) | `memind-evaluation/data/personamem/questions_32k.csv` 和 `memind-evaluation/data/personamem/shared_contexts_32k.jsonl` |

LongMemEval 和 PersonaMem 会在运行时自动转换为内部评测格式。

如果你希望使用自定义路径，可以通过以下参数覆盖：

- `--evaluation.datasets.locomo.path=...`
- `--evaluation.datasets.longmemeval.path=...`
- `--evaluation.datasets.personamem.path=...`

#### 2. 导出环境变量

```bash
export OPENAI_API_KEY=your-key
export OPENAI_BASE_URL=your-base-url
export OPENAI_CHAT_MODEL=openai/gpt-4o-mini

export EMBEDDING_API_KEY=your-key
export EMBEDDING_BASE_URL=your-base-url
export OPENAI_EMBEDDING_MODEL=openai/text-embedding-3-small

# 如果你想尽量对齐 README 中的已发布结果，建议同时配置 rerank
export RERANK_BASE_URL=your-rerank-base-url
export RERANK_API_KEY=your-rerank-key
export RERANK_MODEL=jina-reranker-v3
```

如果你只是想先跑通最小链路，可以在下面的命令后面追加 `--evaluation.system.memind.retrieval.rerank.enabled=false`。这适合做 dry run，但不会与 README 中展示的结果完全一致。

#### 3. 运行完整 benchmark

`application.yml` 默认阶段是 `search,answer,evaluate`。如果你要从头完整复现，请显式指定全流程：`add,search,answer,evaluate`。

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

#### 4. 先跑一次 smoke check

```bash
mvn -pl memind-evaluation -am -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=locomo \
  -Dspring-boot.run.arguments="--evaluation.run-name=locomo-smoke --evaluation.stages=add,search,answer,evaluate --evaluation.smoke=true --evaluation.from-conv=0 --evaluation.to-conv=1 --evaluation.clean-groups=true"
```

#### 5. 查看输出结果

每次运行都会把产物写到 `eval-data/results/<dataset>-<run-name>/`：

- `report.txt`
- `search_results.json`
- `answer_results.json`
- `eval_results.json`

如果重复使用同一个 `run-name`，评测会从 checkpoint 恢复。想要重新跑一轮独立实验，请换一个新的 `run-name`。

---

## 贡献

欢迎贡献。你可以随时提交 [issue](https://github.com/openmemind/memind/issues) 或发起 pull request。


## 社区支持
- [LINUX DO](https://linux.do/)

## 许可证

[Apache License 2.0](LICENSE)
