<p align="center">
  <img src="./docs/images/memind-banner.png" alt="Memind banner">
</p>

<p align="center">
  <strong>能思考的记忆，能进化的上下文。</strong>
</p>

<p align="center">
  <a href="#highlights">亮点</a> ·
  <a href="#overview">概览</a> ·
  <a href="#quick-start">快速开始</a> ·
  <a href="#examples">示例</a> ·
  <a href="#benchmark">基准测试</a>
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

## 🏆 亮点

**Memind** 在三项长记忆基准测试上都取得了 **SOTA** 结果：**LoCoMo**、**LongMemEval** 和 **PersonaMem**。

- ☕ **首个达到 SOTA 水平的 Java 原生记忆与上下文引擎：** memind 基于 Java 原生构建，让 Java 生态也拥有可验证、可落地的长记忆能力。
- 🚀 **三项 benchmark 的已公开最高结果：** 在与 **MemOS / EverMemOS** 对齐的评测口径下，**LoCoMo** 达到 **86.88%**，**LongMemEval** 达到 **84.20%**，**PersonaMem** 达到 **67.91%**。
- 📈 **超过当前最强已发布基线：** 在 **LoCoMo** 和 **LongMemEval** 上超过 **EverMemOS**，在 **PersonaMem** 上超过 **MemOS**。
- 🌳 **Insight Tree 把“记忆存储”变成“结构化理解”：** memind 不只是堆事实，而是把知识组织成会持续演化的层级结构。详见 [Insight Tree](#insight-tree)。
- 🔬 **完整 benchmark 结果：** 详见 [基准测试](#benchmark) 区域，包括完整对比表、分项结果、上下文 Token 和评测协议。

<a id="overview"></a>

## 概览

### Memind 是什么？

Memind 是一个面向 AI Agent 的层级认知记忆与上下文引擎，基于 Java 原生构建。

它不把记忆看作一堆彼此孤立的事实，而是持续从对话中提取、组织并演化知识，最终沉淀为结构化的 **Insight Tree**。

它要解决的是 Agent 记忆里最常见的两个问题：一是**存储扁平、缺少结构**，二是**知识只会累积、不会生长**。

最终，memind 提供的是一层长期记忆与上下文基础设施，让 Agent 能持续保留上下文、逐步形成理解，并按不同抽象层级召回知识。

### 核心设计

<a id="insight-tree"></a>

#### Insight Tree

Insight Tree 是 memind 的核心机制。传统记忆系统往往只保存零散事实；memind 则把记忆逐级提炼为三层结构，而每一层都能看见上一层无法直接得出的模式。

| 层级 | 输入 | 产出 |
|------|------|------|
| 🍃 **Leaf** | 分组后的记忆条目 | 单个语义组内的洞察 |
| 🌿 **Branch** | 多个 Leaf | 同一维度内的跨组模式 |
| 🌳 **Root** | 多个 Branch | 低层级无法看见的跨维度洞察 |

**示例：通过对话理解一位名叫李伟的用户**

> 🍃 **Leaf**（来自 `career_background` 分组）  
> “李伟有 8 年后端经验，先在阿里巴巴工作 3 年，之后在一家金融科技公司带领 8 人团队，设计基于 Java 17 + Spring Cloud + Kafka 的核心交易系统。”
>
> 🌿 **Branch**（整合 career + education + certifications）  
> “李伟是一位资深后端架构师，具备深厚的分布式系统能力，结合了浙江大学计算机科学背景、阿里巴巴大规模系统经验，以及金融科技系统设计实战，技术深度与广度兼备。”
>
> 🌳 **Root**（跨维度：identity × preferences × behavior）  
> “李伟偏好函数式编程和高代码质量（80% 测试覆盖率），同时在技术采纳上较为保守（要求至少 2 年生产验证）。这说明他更关注长期可维护性，而不是追逐短期的新技术热点。因此，给他的建议应优先强调稳定性和成熟方案，而非前沿工具。”

Leaf 知道事实，Branch 看到模式，Root 才开始理解这个人。

#### 双作用域记忆

memind 将记忆划分为两个彼此独立的作用域，让 Agent 同时具备“理解用户”和“沉淀自身经验”的能力：

| 作用域 | 类别 | 作用 |
|-------|------|------|
| **USER** | Profile, Behavior, Event | 用户身份、偏好、关系与经历 |
| **AGENT** | Tool, Directive, Playbook, Resolution | 工具使用经验、持久指令、可复用工作流、已解决问题的经验沉淀 |

#### 双检索策略

memind 提供两种检索策略，分别覆盖“低延迟、低成本”和“更强推理能力”这两类场景：

| 策略 | 工作方式 | 适用场景 |
|------|----------|----------|
| **Simple** | 向量检索 + BM25 关键词匹配，通过 RRF（Reciprocal Rank Fusion）融合，并做自适应截断 | 低延迟、成本敏感场景 |
| **Deep** | LLM 辅助的查询扩展、充分性检查与重排序 | 需要推理的复杂查询 |

#### 核心能力

| 类别 | 能力 | 说明 |
|------|------|------|
| **Extraction** | Conversation Segmentation | 自动识别流式消息边界并完成分段 |
| | Memory Item Extraction | 在 5 个类别上提取结构化事实，并自动去重 |
| | Insight Tree Construction | 执行 Leaf → Branch → Root 的层级知识构建 |
| | Foresight Prediction | 基于对话模式预测用户未来需求 |
| | Tool Call Statistics | 跟踪工具使用模式与成功率 |
| **Retrieval** | Simple Strategy | 基于向量 + BM25 的混合检索，配合 RRF 融合与自适应截断 |
| | Deep Strategy | LLM 辅助的查询扩展、充分性检查与重排序 |
| | Intent Routing | 自动判断当前查询是否需要触发检索 |
| | Multi-granularity | 按查询需求从 Insight Tree 的不同层级召回信息 |
| **Integration** | Pure Java Runtime | 通过 `memind-core` 与插件，使用 `Memory.builder()` 进行装配 |
| | Spring Boot Infrastructure Starters | 通过 `memind-plugin-ai-spring-ai-starter` 与 `memind-plugin-jdbc-starter` 提供可选基础设施接入 |
| | Plugin Architecture | 支持可插拔的存储（SQLite、MySQL）与 tracing（OpenTelemetry） |

<a id="quick-start"></a>

---

## 快速开始

这一节展示默认的 **纯 Java + OpenAI + SQLite** 接入方式。

### 前置要求

- Java 21
- Maven
- `OPENAI_API_KEY`

### 运行 quickstart 示例

克隆仓库，设置 API Key，然后直接运行维护中的 Java quickstart 示例：

```bash
git clone https://github.com/openmemind/memind.git
cd memind
OPENAI_API_KEY=your-key \
mvn -pl memind-examples/memind-example-java -am -DskipTests exec:java \
  -Dexec.mainClass=com.openmemind.ai.memory.example.java.quickstart.QuickStartExample
```

这是打通端到端链路最快的方式。更多可运行场景见 [示例](#examples)。

### 将 Memind 接入你的应用

先导入 memind BOM，再引入 core runtime、Spring AI 插件、JDBC 插件，以及数据库对应的 JDBC Driver。默认的 SQLite 配置如下：

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
    <artifactId>memind-plugin-jdbc</artifactId>
  </dependency>
  <dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
  </dependency>
</dependencies>
```

如果你使用 MySQL 或 PostgreSQL，只需把 `sqlite-jdbc` 替换为对应驱动，并将 `JdbcStore.sqlite(...)` 改成 `JdbcStore.mysql(...)` 或 `JdbcStore.postgresql(...)`。

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

JdbcMemoryAccess jdbc = JdbcStore.sqlite("./data/memind.db");

Memory memory = Memory.builder()
        .chatClient(new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build()))
        .store(jdbc.store())
        .buffer(jdbc.buffer())
        .textSearch(jdbc.textSearch())
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

如果你想直接从“可运行、配置集中、默认值清晰”的版本开始，优先看：

- `memind-examples/memind-example-java/README.md`
- `memind-examples/memind-example-java/src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java`

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

更多配置项、完整命令和运行时数据说明，请查看：

- [`memind-examples/memind-example-java/README.md`](./memind-examples/memind-example-java/README.md)
- [`ExampleSettings.java`](./memind-examples/memind-example-java/src/main/java/com/openmemind/ai/memory/example/java/support/ExampleSettings.java)

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

## 许可证

[Apache License 2.0](LICENSE)
