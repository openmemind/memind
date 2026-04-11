# Feature: Multimodal RawData Support

**Version**: 0.2.0  
**Date**: 2026-04-08  
**Status**: Approved

## Overview

扩展 memind 的 RawData 层，支持文档（PDF/DOCX/PPTX/XLSX）、图片、音频等多模态内容类型。保持"轻量处理 + 外部集成"定位：memind 核心处理解析后的文本结果，实际文件解析通过可选的 ContentParser 插件或外部服务完成。

多模态接入后的治理补充设计见 [2026-04-11-content-governance-separation-design.md](/Users/zhengyate/dev/openmemind/memind/docs/specs/2026-04-11-content-governance-separation-design.md)，其中定义了 `contentProfile` 与 `governanceType` 的职责分离、权威来源和兼容策略。

## Design Principles

1. **轻量核心**：memind-core 不内置文件解析能力，只定义接口
2. **框架复用 + 策略分发**：公共管道骨架不变，normalize/chunk/caption 按 contentType 分发
3. **双路径输入**：用户可传入已解析文本（主路径），也可传入原始文件字节（需配置 ContentParser）
4. **原始文件可回溯**：通过 `memory_resource` 保存持久化引用元数据，`ResourceStore` 仅作为可选原始字节后端，支持从 RawData/Item/Insight 追溯到源文件

## Data Models

### New RawContent Implementations

三个新的 `RawContent` 实现，和现有的 `ConversationContent`、`ToolCallContent` 平级：

#### DocumentContent

用于文档类型：PDF、DOCX、PPTX、XLSX 等。

| Field | Type | Description |
|-------|------|-------------|
| title | String | 文档标题 |
| mimeType | String | MIME 类型，如 "application/pdf" |
| parsedText | String | 解析后的文本内容（必填） |
| sections | List\<DocumentSection\> | 可选，按章节/页结构化 |
| sourceUri | String | 原始文件的 Resource URI |
| metadata | Map\<String, Object\> | 页数、作者、创建日期等 |

`DocumentSection` record:
| Field | Type | Description |
|-------|------|-------------|
| title | String | 章节标题（可选） |
| content | String | 章节文本内容 |
| index | int | 章节序号（页码或序号） |
| metadata | Map\<String, Object\> | 章节级元数据 |

`contentType()` → `ContentTypes.DOCUMENT`  
`toContentString()` → 返回 `parsedText`  
`getContentId()` → 基于 parsedText 的 SHA-256 hash

#### ImageContent

用于图片类型。

| Field | Type | Description |
|-------|------|-------------|
| mimeType | String | MIME 类型，如 "image/png"（可选） |
| description | String | 图片描述/caption（必填） |
| ocrText | String | OCR 提取的文本（可选） |
| sourceUri | String | Resource URI |
| metadata | Map\<String, Object\> | 尺寸、格式等 |

`contentType()` → `ContentTypes.IMAGE`  
`toContentString()` → 返回 `description` + "\n" + `ocrText`（如果有）  
`getContentId()` → 基于 `toContentString()` 的 SHA-256 hash（即 description + ocrText 的文本指纹）

#### AudioContent

用于音频类型。

| Field | Type | Description |
|-------|------|-------------|
| mimeType | String | MIME 类型，如 "audio/mpeg"（可选） |
| transcript | String | 转录文本（必填） |
| segments | List\<TranscriptSegment\> | 可选，带时间戳的分段转录 |
| sourceUri | String | Resource URI |
| metadata | Map\<String, Object\> | 时长、格式等 |

`TranscriptSegment` record:
| Field | Type | Description |
|-------|------|-------------|
| text | String | 段落文本 |
| startTime | Duration | 开始时间 |
| endTime | Duration | 结束时间 |
| speaker | String | 说话人（可选） |

`contentType()` → `ContentTypes.AUDIO`  
`toContentString()` → 返回 `transcript`  
`getContentId()` → 基于 transcript 的 SHA-256 hash

说明：`RawContent.getContentId()` 只作为文本内容指纹，并应与 `toContentString()` 暴露给下游提取的文本保持一致；多模态 RawData 的最终幂等键必须再叠加归一化后的源标识（如 `resourceId`、`sourceUri`、`storageUri` 或 `fileName + checksum`），避免不同来源但文本相同的输入塌缩到同一条 raw data。

### ContentTypes Constants

```java
class ContentTypes {
    static final String CONVERSATION = "CONVERSATION";
    static final String TOOL_CALL = "TOOL_CALL";
    static final String DOCUMENT = "DOCUMENT";   // new
    static final String IMAGE = "IMAGE";         // new
    static final String AUDIO = "AUDIO";         // new
}
```

Jackson subtype 名称保持为小写：`conversation` / `tool_call` / `document` / `image` / `audio`。

### MemoryRawData Extension

新增字段：

| Field | Type | Description |
|-------|------|-------------|
| resourceId | String | `MemoryResource.id`（多模态有值，Conversation/ToolCall 为 null） |
| mimeType | String | 原始文件 MIME 类型（可选） |

### MemoryResource

新增持久化资源元数据模型，对应 `memory_resource` 表：

| Field | Type | Description |
|-------|------|-------------|
| id | String | 业务 ID，被 `MemoryRawData.resourceId` 引用 |
| memoryId | String | 所属 MemoryId |
| sourceUri | String | 源引用 URI（外部 URL、本地路径或对象存储 URI） |
| storageUri | String | `ResourceStore` 实际写入后的存储 URI（可选） |
| fileName | String | 原始文件名 |
| mimeType | String | MIME 类型 |
| checksum | String | 文件校验和（可选） |
| sizeBytes | Long | 文件大小（字节） |
| metadata | Map\<String, Object\> | 附加资源元数据 |
| createdAt | Instant | 创建时间 |

## Resource Storage Layer

关系型 `memory_resource` 是权威的引用元数据层；`ResourceStore` 只负责原始字节存取。

### ResourceStore Interface

```java
interface ResourceStore {
    Mono<ResourceRef> store(MemoryId memoryId, String fileName, byte[] data,
                           String mimeType, Map<String, Object> metadata);
    Mono<byte[]> retrieve(ResourceRef ref);
    Mono<Void> delete(ResourceRef ref);
    Mono<Boolean> exists(ResourceRef ref);
}
```

### ResourceRef Record

| Field | Type | Description |
|-------|------|-------------|
| id | String | UUID |
| memoryId | String | 所属 MemoryId |
| fileName | String | 原始文件名 |
| mimeType | String | MIME 类型 |
| storageUri | String | 存储 URI（"file:///..." 或 "s3://..."） |
| size | long | 文件大小（字节） |
| createdAt | Instant | 创建时间 |

### Implementations

1. **LocalFileResourceStore**（memind-core 内置默认实现）
   - 存储路径：`{baseDir}/{memoryId}/{resourceId}/{fileName}`
   - 默认 baseDir 可通过配置指定
   - 零依赖，适合本地开发和单机部署

2. **S3ResourceStore**（memind-plugin-resource-s3 插件）
   - 存储键：`{prefix}/{memoryId}/{resourceId}/{fileName}`
   - 配置项：bucket、region、endpoint、credentials

### MemoryStore Extension

```java
interface MemoryStore {
    RawDataOperations rawDataOperations();
    ItemOperations itemOperations();
    InsightOperations insightOperations();
    ResourceOperations resourceOperations();
    default ResourceStore resourceStore() { return null; }  // new, optional
    default void upsertRawDataWithResources(...) { ... }
}
```

## Content Parser (Optional)

### ContentParser Interface

```java
interface ContentParser {
    Set<String> supportedMimeTypes();
    Mono<RawContent> parse(byte[] data, String fileName, String mimeType);
}
```

- memind-core 只定义接口，不内置实现
- 提供 `memind-plugin-parser-tika` 插件（Apache Tika 实现）
- 用户可自实现 ContentParser 对接任意外部服务

### Usage Patterns

**主路径（推荐）**：用户通过外部服务解析后，直接构造 RawContent 传入

```java
DocumentContent doc = DocumentContent.of("Report Title", "application/pdf", parsedText)
    .withSections(sections)
    .withSourceUri("https://example.com/report.pdf");
memory.extract(ExtractionRequest.document(memoryId, doc)).block();
```

**便捷路径**：用户配置 ContentParser 后，传入原始文件字节

```java
memory.extract(ExtractionRequest.file(memoryId, "report.pdf", fileBytes, "application/pdf")).block();
// 内部自动：parse → optional store bytes → normalize metadata → construct RawContent request → 在 RawData 阶段持久化 MemoryResource / MemoryRawData
```

补偿边界：
- 若失败发生在 raw-data/resource 持久化成功之前，可以 best-effort 删除刚写入的 blob
- 一旦 `memory_resource` / `memory_raw_data` 已成功持久化，后续 item/insight 失败不得回删 blob，否则会制造悬挂引用

## Extraction Pipeline Changes

### Chunker Strategy Dispatch

新增三个 TextChunker 实现，通过 contentType 分发：

| ContentType | Chunker | 策略 |
|-------------|---------|------|
| conversation | ConversationChunker | 按消息窗口/LLM 边界检测分块 |
| tool_call | ToolCallChunker | 按工具调用分块 |
| document | DocumentChunker | 按章节/段落分块，尊重 sections 结构 |
| image | ImageChunker | 单块（description + ocrText 合并） |
| audio | AudioChunker | 按时间段分块（有 segments 时）或按 token 长度 |

### Pipeline Flow（不变）

```
RawContent → RawDataExtractStep (选择 Chunker)
  → normalize → chunk → caption → vectorize → store
  → MemoryItemExtractStep（主体复用，仅做多模态兼容调整）
  → InsightExtractStep（主体复用，仅做多模态兼容调整）
```

下游 Item/Insight 提取主体仍保持通用，不需要额外分叉一套多模态专用管道，因为所有 RawContent 的 `toContentString()` 都返回文本；但仍需做以下兼容调整：

1. Unified item extraction prompt 改为 content-neutral wording，并将 `<Conversation>` 包装替换为 `<SourceText>`。
2. user-scoped `DefaultInsightTypes` 接受 `DOCUMENT` / `IMAGE` / `AUDIO`，agent-scoped 默认能力仍保持 conversation-only。
3. `ParsedSegment.metadata()` 必须继续透传到 `MemoryItem.metadata()`，以保留 `resourceId`、`sourceUri`、`mimeType` 等来源追溯信息。

### ExtractionRequest Extension

新增便利工厂方法：

```java
static ExtractionRequest document(MemoryId id, DocumentContent content)
static ExtractionRequest image(MemoryId id, ImageContent content)
static ExtractionRequest audio(MemoryId id, AudioContent content)
static ExtractionRequest file(MemoryId id, String fileName, byte[] data, String mimeType)
```

直接多模态工厂方法需要在 `ExtractionRequest.document/image/audio(...)` 内标准化 metadata：
- 从 `content.metadata()` 的防御性副本开始
- 当 `sourceUri` / `mimeType` 存在时写入请求 metadata
- `ImageContent` / `AudioContent` 因此需要显式暴露可选 `mimeType`

`file(...)` 路径在 parser 返回 `RawContent` 后也要沿用同一套 metadata 归一化策略，保留 parsed content metadata，而不是只保留文件级字段。

## Database Changes

### memory_raw_data Table

新增列：
- `resource_id VARCHAR(64)` — `memory_resource.biz_id`（nullable）
- `mime_type VARCHAR(128)` — MIME 类型（nullable）

多模态 RawData 的 `content_id` 不是单纯文本哈希；它应由文本内容指纹与源标识共同确定。

### New Table: memory_resource

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT / BIGSERIAL / INTEGER PK | 代理主键，沿用现有 store 风格 |
| biz_id | VARCHAR(64) | 资源业务 ID |
| user_id | VARCHAR(64) | 用户维度 |
| agent_id | VARCHAR(64) | Agent 维度 |
| memory_id | VARCHAR(256) NOT NULL | 所属 MemoryId |
| source_uri | VARCHAR(1024) | 归一化源 URI |
| file_name | VARCHAR(512) | 原始文件名 |
| mime_type | VARCHAR(128) | MIME 类型 |
| storage_uri | VARCHAR(1024) | 存储 URI |
| checksum | VARCHAR(128) | 校验和 |
| size_bytes | BIGINT | 文件大小 |
| metadata | TEXT (JSON) | 附加元数据 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| deleted | BOOLEAN / TINYINT | 软删除标记 |

索引：`uk_resource_biz_id (user_id, agent_id, biz_id)`、`idx_resource_memory_id (user_id, agent_id)`、`idx_raw_data_resource_id (user_id, agent_id, resource_id)`

## Configuration

### RawDataExtractionOptions Extension

```java
record RawDataExtractionOptions(
    ConversationChunkingConfig chunking,
    TextChunkingConfig documentChunking,
    TextChunkingConfig audioChunking,
    CommitDetectorConfig commitDetection,
    ContentParser contentParser
)
```

## Module Structure

| Module | Content |
|--------|---------|
| memind-core | RawContent 接口和三个新实现、ContentTypes、ResourceStore 接口、ResourceRef、ContentParser 接口、LocalFileResourceStore、MemoryResource、ResourceOperations、三个新 Chunker、MemoryRawData 扩展 |
| memind-plugin-jdbc | 数据库迁移脚本（`memory_resource` + 新列）、JDBC 版 ResourceOperations |
| memind-plugin-resource-s3 | S3ResourceStore 实现（新模块） |
| memind-plugin-parser-tika | Apache Tika ContentParser 实现（新模块） |

## Testing Strategy

1. 单元测试：每个新 RawContent 的 toContentString()、getContentId() 测试
2. 单元测试：每个新 Chunker 的分块逻辑
3. 集成测试：LocalFileResourceStore 的存储/检索/删除
4. 集成测试：`memory_resource` 与 `memory_raw_data` 的一致性持久化
5. 集成测试：完整管道测试（Document/Image/Audio 从输入到 Item/Insight 提取），并验证来源 metadata 能从 `ParsedSegment` 透传到 `MemoryItem.metadata()`
6. 可选：memind-plugin-parser-tika 的 PDF/DOCX 解析测试
