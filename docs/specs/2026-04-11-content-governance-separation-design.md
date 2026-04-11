# Feature: Content Governance Separation

**Version**: 0.2.0-SNAPSHOT  
**Date**: 2026-04-11  
**Status**: Proposed

> 本设计是 [2026-04-08-multimodal-rawdata-design.md](/Users/zhengyate/dev/openmemind/memind/docs/specs/2026-04-08-multimodal-rawdata-design.md) 的治理补充，专门解决 `contentProfile` 被同时拿来做描述和核心治理路由的问题。

## Problem

当前多模态设计里，`contentProfile` 同时承担了两类职责：

1. 描述内容形态，例如 `document.markdown`
2. 驱动核心治理，例如 source size limit、parsed-content limit、chunking policy 选择

这会带来两个问题：

1. `contentProfile` 是开放字符串，插件一旦输出更细的 profile，例如 `document.pdf.tika`、`audio.whisper.transcript`，核心治理代码就容易失效或出现隐式回退。
2. direct content、file/url parser、历史数据回放三条路径，对“谁有权决定治理类型”没有统一定义，metadata 很容易变成谁都能写、谁都能改的弱约束。

## Goals

1. 保留 `contentProfile` 的开放描述能力，允许插件表达更细粒度内容形态。
2. 引入一个闭集治理键，专门负责核心策略路由。
3. 明确 direct content、parser-backed content、legacy data 三条路径的权威来源。
4. 保持核心实现尽量简单，不再引入额外的运行时分类对象。

## Non-Goals

1. 不在这一轮把 `contentProfile` 变成受控枚举。
2. 不让 downstream 依赖任意插件自定义 profile 才能工作。
3. 不新增独立的 runtime classification aggregate；继续沿用 `ParserResolution` + metadata normalization。

## Core Model

### 1. `ContentGovernanceType`

新增闭集枚举，仅用于核心治理决策：

- `DOCUMENT_TEXT_LIKE`
- `DOCUMENT_BINARY`
- `IMAGE_CAPTION_OCR`
- `AUDIO_TRANSCRIPT`

它负责以下决策：

- source byte limit 选择
- parsed-content limit 选择
- document chunking family 选择（text-like / binary）

### 2. `contentProfile`

`contentProfile` 继续保留为开放字符串，只做描述，不再直接决定核心治理。

典型内置 profile：

- `document.markdown`
- `document.html`
- `document.text`
- `document.binary`
- `image.caption-ocr`
- `audio.transcript`

插件可定义更细粒度 profile，例如：

- `document.pdf.tika`
- `document.docx.tika`
- `audio.whisper.transcript`

但这些 profile 只能影响“结构感知优化”，不能直接替代治理键。

### 3. Builtin Mapping

核心内置 profile 和治理类型之间保留固定映射：

| Builtin `contentProfile` | `ContentGovernanceType` |
|---|---|
| `document.markdown` | `DOCUMENT_TEXT_LIKE` |
| `document.html` | `DOCUMENT_TEXT_LIKE` |
| `document.text` | `DOCUMENT_TEXT_LIKE` |
| `document.binary` | `DOCUMENT_BINARY` |
| `image.caption-ocr` | `IMAGE_CAPTION_OCR` |
| `audio.transcript` | `AUDIO_TRANSCRIPT` |

这个映射只服务两个场景：

1. direct content 的默认 profile 派生
2. 历史数据缺失 `governanceType` 时的 legacy fallback

## Authority Rules

### 1. Direct Content Path

适用于 `ExtractionRequest.document/image/audio(...)`。

权威来源：

- `governanceType` 由 core 基于 `RawContent` 运行时类型和基础字段推导
- `contentProfile` 若调用方未提供，则由 core 派生默认值

规则：

1. 调用方提供的 metadata 不是 `governanceType` 的权威来源。
2. 若 metadata 中显式提供了 `governanceType`，只能作为一致性校验；与 core 推导结果不一致时必须报错，不能静默覆盖。
3. 若 metadata 中显式提供了 `contentProfile`：
   - 当它是 builtin profile 时，必须与推导出的 `governanceType` 相容；
   - 当它是非 builtin profile 时，可作为描述字段保留，但不能改变治理路由。

推导规则需要固定并集中在 core：

- `DocumentContent`
  - `text/markdown` / `text/html` / `text/plain` / `text/csv` -> `DOCUMENT_TEXT_LIKE`
  - 其他非空 document MIME -> `DOCUMENT_BINARY`
  - MIME 缺失时，`sections` 非空 -> `DOCUMENT_BINARY`，否则 -> `DOCUMENT_TEXT_LIKE`
- `ImageContent` -> `IMAGE_CAPTION_OCR`
- `AudioContent` -> `AUDIO_TRANSCRIPT`

这意味着 direct content 可以携带更细的描述 profile，但核心治理仍然由 core 自己决定。

### 2. Parser-Backed File/URL Path

适用于 `ExtractionRequest.file(...)` 和 `ExtractionRequest.url(...)`。

权威来源：

- `governanceType` 来自 `ParserResolution.capability().governanceType()`
- `contentProfile` 的默认值来自 `ParserResolution.capability().contentProfile()`，但 parser 可以在 parse 后细化最终描述 profile

规则：

1. parser registration 的 `governanceType` 是权威来源，不信任 parser 输出 metadata 中的 `governanceType`。
2. parse 完成后，core 必须权威写入或校验：
   - `parserId`
   - `governanceType`
3. `contentProfile` 按以下规则确定最终值：
   - parser metadata 缺失或为空时，回退到 `ParserResolution.capability().contentProfile()`
   - parser metadata 提供 builtin profile 时，该 profile 必须映射到与权威 `governanceType` 相同的治理类型
   - parser metadata 提供非 builtin profile 时，可作为最终描述字段保留，但不能改变治理路由
4. 若 parser 在 `RawContent.metadata()` 中返回了冲突的 `parserId` / `governanceType`，或者返回了跨治理族的 builtin profile，必须报错，不能 silently override。

这样做的原因是 source byte limit 发生在 parse 之前，治理键必须在 parser resolve 阶段就确定；而结构感知优化需要在 parse 后看到更细的描述 profile。

### 3. Legacy Stored Data Path

适用于历史 raw data 已经落库、但 metadata 中没有 `governanceType` 的情况。

读取顺序：

1. 优先读取显式 `governanceType`
2. 若缺失，则尝试用 builtin `contentProfile` 做 fallback
3. 若 `contentProfile` 是未知或插件自定义 profile，且没有 `governanceType`，则直接失败并给出明确错误

失败是有意设计，不允许把未知 profile 隐式回退到宽松治理类型。

## Parser Contract

`ContentParser` 需要同时暴露：

- `contentProfile()`
- `governanceType()`

约束：

1. 一个 parser registration 必须对应一个固定 `governanceType`。
2. `contentProfile()` 表示该 parser 在 resolve 阶段暴露的默认描述 profile，而不是 parse 后唯一允许的最终 profile。
3. parser 可以在 `RawContent.metadata().contentProfile` 中细化最终 profile，但不得改变其所属治理族。
4. 若一个实现想在 source-level governance 上区分不同格式，就必须拆成多个 parser registration；不能在同一个 registration 里做跨治理族漂移。
5. builtin default profile 可以使用默认 `governanceType()` 推导。
6. 非 builtin default profile 必须显式覆写 `governanceType()`；否则 registry 在启动阶段直接 fail fast。

这里刻意不引入更复杂的 capability matrix，因为当前 source-limit 选择只需要“每个 parser 一个静态治理类型”，而最终 `contentProfile` 细化只影响 parse 后的描述与结构优化。

## Downstream Routing Rules

### Core Governance

以下模块只允许按 `ContentGovernanceType` 路由：

- `MemoryExtractor` 的 source size limit 选择
- `ParsedContentLimitValidator`
- document chunking family 选择

### Prompt Budget Enforcement

当前 `PromptBudgetOptions` 仍保持全局配置，不在这一轮改造成按治理族分配。

这一轮的要求只有两点：

1. `SegmentBudgetEnforcer` 不得因未知或插件自定义 `contentProfile` 而失败。
2. 当需要做结构化再拆分时，markdown 仍可走 markdown-aware candidates，其余 profile 必须有稳定的通用 fallback。

### Structure-Aware Optimization

以下模块继续可以读取 `contentProfile`：

- markdown heading-aware chunking
- html/text 的结构优化
- plugin 自定义的 profile-aware fallback

但它们必须满足：

1. 结构优化失败时，仍可退回到 governance family 的通用策略。
2. 非 builtin profile 不得导致核心治理失败。

举例：

- `document.markdown` -> 走 text-like 治理 + markdown-aware chunking
- `document.pdf.tika` -> 走 binary 治理 + 通用 document binary chunking
- `audio.whisper.transcript` -> 走 audio transcript 治理 + 通用 transcript chunking

## Compatibility And Migration

### Existing Core Paths

以下情况保持兼容：

1. 旧 direct content 只依赖 builtin profile 的情况
2. 旧历史数据只有 builtin `contentProfile`、没有 `governanceType` 的情况
3. 旧 parser 使用 builtin default profile 且未覆写 `governanceType()` 的情况
4. 旧 parser 在 parse 后把 builtin text-like profile 从 `document.text` 细化为 `document.markdown` / `document.html` 的情况

### Plugin Upgrade Boundary

以下情况需要插件更新：

1. parser registration 返回非 builtin default profile
2. 但未显式声明 `governanceType()`

这类插件在 registry 初始化时应收到明确错误，例如：

`Parser document-tika uses non-builtin contentProfile document.pdf.tika and must override governanceType()`

这是有意的升级边界，不属于 silent compatibility。

## Simplicity Decision

本轮不新增独立的 runtime classification 对象，原因如下：

1. parser-backed 路径已经有 `ParserResolution + ContentCapability`
2. direct content 路径只需要在 metadata normalization 阶段一次性写入治理键
3. downstream 只需要一个统一的 `resolveRequired(metadata)` 工具即可消费

也就是说，本轮新增的复杂度仅限于：

1. 一个闭集枚举 `ContentGovernanceType`
2. 一套 builtin profile 到 governance 的映射
3. 一个集中 resolver 负责 authoritative read + legacy fallback

这比再引入一层新的 runtime aggregate 更直接，也更符合当前代码结构。

## Acceptance Criteria

1. 任意核心治理逻辑都不再直接 `switch contentProfile`。
2. 非 builtin profile 不会破坏 source limit、parsed limit 与 prompt-budget enforcement。
3. builtin profile 仍可触发 markdown 等结构感知优化。
4. direct / file / url 三条路径对 `governanceType` 的权威来源一致且可解释。
5. 对 legacy fallback 的失败场景有显式错误，而不是隐式降级。
