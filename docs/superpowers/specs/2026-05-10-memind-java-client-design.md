# Memind Java Client Design Spec

## Overview

为 memind 提供官方 Java Client SDK，封装 memind server 的 Public API，让 Java 开发者（AI Agent 开发者、Spring Boot 应用开发者等）能够便捷地与 memind 进行交互。

## 目标

- 提供类型安全、易用的 Java API 来调用 memind server
- 支持同步和异步（CompletableFuture）两种调用模式
- 提供 Spring Boot Starter 实现开箱即用的自动配置
- 最低 Java 17，利用 record、sealed interface 等现代特性
- 版本号与 memind server 保持同步（当前 0.2.0-SNAPSHOT）
- 线程安全，可在多线程环境中共享单个 client 实例

## 项目结构

```
memind-clients/                              # 非 Maven 模块，所有语言 SDK 的统一入口
└── java/                                    # Java client 独立 Maven 项目
    ├── pom.xml                              # 父 POM (groupId: com.openmemind.ai)
    ├── memind-client/                       # 核心 client 模块
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/openmemind/ai/client/
    │       │   ├── MemindClient.java
    │       │   ├── exception/
    │       │   │   ├── MemindClientException.java
    │       │   │   ├── MemindApiException.java
    │       │   │   ├── MemindConnectionException.java
    │       │   │   └── MemindTimeoutException.java
    │       │   ├── model/
    │       │   │   ├── request/
    │       │   │   │   ├── AddMessageRequest.java
    │       │   │   │   ├── ExtractMemoryRequest.java
    │       │   │   │   ├── CommitMemoryRequest.java
    │       │   │   │   └── RetrieveMemoryRequest.java
    │       │   │   ├── response/
    │       │   │   │   ├── RetrieveMemoryResponse.java
    │       │   │   │   ├── HealthResponse.java
    │       │   │   │   └── RetrievalTraceView.java
    │       │   │   └── common/
    │       │   │       ├── Message.java
    │       │   │       ├── ContentBlock.java
    │       │   │       ├── RawContent.java
    │       │   │       ├── ConversationContent.java
    │       │   │       ├── MapRawContent.java
    │       │   │       ├── Role.java
    │       │   │       └── Strategy.java
    │       │   └── internal/
    │       │       ├── MemindHttpClient.java
    │       │       └── RawContentSerializer.java
    │       └── test/java/com/openmemind/ai/client/
    └── memind-client-spring-boot-starter/
        ├── pom.xml
        └── src/
            ├── main/java/com/openmemind/ai/client/spring/
            │   ├── MemindClientAutoConfiguration.java
            │   └── MemindClientProperties.java
            └── main/resources/
                └── META-INF/spring/
                    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 依赖策略

### memind-client（核心模块）

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDK 17+ HttpClient | 内置 | HTTP 通信 |
| Jackson-databind | 2.18.x (`com.fasterxml.jackson`) | JSON 序列化/反序列化 |
| Jackson-datatype-jsr310 | 2.18.x | Instant 等时间类型支持 |
| SLF4J API | 2.0.x | 日志门面（不绑定具体实现） |

**Jackson 版本说明**：memind server 使用 Jackson 3.x（`tools.jackson` 包名），但 client 选择 Jackson 2.x，原因：
- 目标用户大多在 Spring Boot 3.x 生态，默认使用 Jackson 2.x
- 避免与用户项目的 Jackson 版本冲突
- client 作为独立项目，不需要与 server 使用相同的 Jackson 版本
- JSON wire format 兼容，不影响通信

### memind-client-spring-boot-starter

| 依赖 | 版本 | 用途 |
|------|------|------|
| memind-client | ${project.version} | 核心功能 |
| spring-boot-autoconfigure | 3.2+ | 自动配置 |
| spring-boot-configuration-processor | 3.2+ | 配置元数据生成（optional） |

**Spring Boot 版本说明**：Starter 支持 Spring Boot 3.2+（不要求 4.x），覆盖主流用户群体。

## API 端点映射

| Client 方法 | HTTP Method | Server 路径 |
|-------------|-------------|-------------|
| `addMessage()` | POST | `/open/v1/memory/add-message` |
| `extract()` | POST | `/open/v1/memory/extract` |
| `commit()` | POST | `/open/v1/memory/commit` |
| `retrieve()` | POST | `/open/v1/memory/retrieve` |
| `health()` | GET | `/open/v1/health` |

注意：`health` 端点在 `/open/v1/health`，不在 `/open/v1/memory/` 路径下。

## API 设计

### 客户端构建

```java
// 自部署（无认证）
MemindClient client = MemindClient.builder()
    .baseUrl("http://localhost:8366")
    .build();

// SaaS 版本（带 token）
MemindClient client = MemindClient.builder()
    .baseUrl("https://api.memind.ai")
    .apiToken("mk-xxxxxxxxxxxx")
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(30))
    .build();
```

### 核心方法

```java
/**
 * Memind Java Client - 线程安全，可在多线程环境中共享单个实例。
 * 底层基于 JDK HttpClient 异步实现，同步方法内部调用异步后 join()。
 */
public class MemindClient implements AutoCloseable {

    // === 同步 API ===

    /** 添加消息到记忆缓冲区 */
    public void addMessage(AddMessageRequest request);

    /** 提取记忆（从 rawContent 提取结构化记忆） */
    public void extract(ExtractMemoryRequest request);

    /** 提交记忆（触发 insight 生成等后处理） */
    public void commit(CommitMemoryRequest request);

    /** 检索记忆 */
    public RetrieveMemoryResponse retrieve(RetrieveMemoryRequest request);

    /** 健康检查 */
    public HealthResponse health();

    // === 异步 API ===

    public CompletableFuture<Void> addMessageAsync(AddMessageRequest request);
    public CompletableFuture<Void> extractAsync(ExtractMemoryRequest request);
    public CompletableFuture<Void> commitAsync(CommitMemoryRequest request);
    public CompletableFuture<RetrieveMemoryResponse> retrieveAsync(RetrieveMemoryRequest request);
    public CompletableFuture<HealthResponse> healthAsync();

    // === 生命周期 ===

    /** 关闭底层 HTTP 连接资源 */
    public void close();

    // === Builder ===

    public static Builder builder() { ... }
}
```

### 使用示例

```java
try (MemindClient client = MemindClient.builder()
        .baseUrl("http://localhost:8366")
        .build()) {

    // 添加对话消息
    client.addMessage(AddMessageRequest.builder()
        .userId("user-1")
        .agentId("agent-1")
        .message(Message.user("今天天气真好"))
        .build());

    client.addMessage(AddMessageRequest.builder()
        .userId("user-1")
        .agentId("agent-1")
        .message(Message.assistant("是的，阳光明媚，适合出门散步"))
        .build());

    // 提取记忆（从 rawContent 直接提取）
    client.extract(ExtractMemoryRequest.builder()
        .userId("user-1")
        .agentId("agent-1")
        .rawContent(ConversationContent.of(List.of(
            Message.user("今天天气真好"),
            Message.assistant("是的，阳光明媚")
        )))
        .build());

    // 提交记忆
    client.commit(CommitMemoryRequest.builder()
        .userId("user-1")
        .agentId("agent-1")
        .build());

    // 检索记忆
    RetrieveMemoryResponse response = client.retrieve(RetrieveMemoryRequest.builder()
        .userId("user-1")
        .agentId("agent-1")
        .query("天气相关的记忆")
        .strategy(Strategy.SIMPLE)
        .build());

    System.out.println("检索到 " + response.items().size() + " 条记忆");
}
```

## 数据模型

### 请求模型

所有请求模型使用 Builder 模式构建，字段与 server 端 DTO 一一对应。

```java
// AddMessageRequest
public record AddMessageRequest(String userId, String agentId, Message message, String sourceClient) {
    public static Builder builder() { ... }
}

// ExtractMemoryRequest - rawContent 为必填，支持多态
public record ExtractMemoryRequest(String userId, String agentId, RawContent rawContent, String sourceClient) {
    public static Builder builder() { ... }
}

// CommitMemoryRequest
public record CommitMemoryRequest(String userId, String agentId, String sourceClient) {
    public static Builder builder() { ... }
}

// RetrieveMemoryRequest
public record RetrieveMemoryRequest(String userId, String agentId, String query, Strategy strategy, Boolean trace) {
    public static Builder builder() { ... }
}
```

### 响应模型

```java
// RetrieveMemoryResponse
public record RetrieveMemoryResponse(
    String status,
    List<RetrievedItem> items,
    List<RetrievedInsight> insights,
    List<RetrievedRawData> rawData,
    List<String> evidences,
    String strategy,
    String query,
    RetrievalTraceView trace
) {
    public record RetrievedItem(String id, String text, float vectorScore, double finalScore, Instant occurredAt) {}
    public record RetrievedInsight(String id, String text, String tier) {}
    public record RetrievedRawData(String rawDataId, String caption, double maxScore, List<String> itemIds) {}
}

// HealthResponse
public record HealthResponse(String status, String service) {}

// RetrievalTraceView（仅当 trace=true 时返回）
public record RetrievalTraceView(
    String traceId, Instant startedAt, Instant completedAt, boolean truncated,
    List<StageView> stages, MergeView merge, FinalView finalResults
) {
    public record StageView(String stage, String tier, String method, String status,
        Integer inputCount, Integer candidateCount, Integer resultCount,
        boolean degraded, boolean skipped, Instant startedAt, Long durationMillis) {}
    public record MergeView(int inputCount, int outputCount, int deduplicatedCount,
        int sourceCount, String status) {}
    public record FinalView(String strategy, String status, int itemCount,
        int insightCount, int rawDataCount, int evidenceCount) {}
}
```

### 通用模型

```java
// Message - 对话消息
public record Message(Role role, List<ContentBlock> content, Instant timestamp, String userName) {
    public static Message user(String text) { ... }
    public static Message user(String text, Instant timestamp) { ... }
    public static Message assistant(String text) { ... }
    public static Message assistant(String text, Instant timestamp) { ... }
}

// Role
public enum Role { USER, ASSISTANT }

// Strategy
public enum Strategy { SIMPLE, DEEP }

// ContentBlock - 消息内容块（多模态支持）
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
    @JsonSubTypes.Type(value = ImageBlock.class, name = "image"),
    @JsonSubTypes.Type(value = AudioBlock.class, name = "audio"),
    @JsonSubTypes.Type(value = VideoBlock.class, name = "video")
})
public sealed interface ContentBlock permits TextBlock, ImageBlock, AudioBlock, VideoBlock {}
public record TextBlock(String text) implements ContentBlock {}
public record ImageBlock(Source source) implements ContentBlock {}
public record AudioBlock(Source source) implements ContentBlock {}
public record VideoBlock(Source source) implements ContentBlock {}

// Source - 媒体来源
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UrlSource.class, name = "url"),
    @JsonSubTypes.Type(value = Base64Source.class, name = "base64")
})
public sealed interface Source permits UrlSource, Base64Source {}
public record UrlSource(String url) implements Source {}
public record Base64Source(String mediaType, String data) implements Source {}
```

注意：`ContentBlock` 和 `Source` 使用 `@JsonSubTypes` 静态注册是合理的，因为它们的子类型是固定的、由协议定义的。而 `RawContent` 不同，它的子类型是通过插件动态扩展的，所以使用自定义 Serializer。

### RawContent 多态设计

`RawContent` 是 memind 的核心抽象，server 端通过插件机制动态注册子类型。client 端的处理策略：

1. **内置核心类型**：提供 `ConversationContent`（最常用的对话内容类型）
2. **通用兜底类型**：提供 `MapRawContent`，允许用户以 Map 形式发送任意 RawContent

**重要**：server 端的 type name 全部为小写（`"conversation"`, `"document"`, `"audio"`, `"image"`, `"tool_call"`），client 序列化时必须使用小写。

```java
// RawContent 基类 - 非 sealed，允许未来扩展
// 序列化通过自定义 RawContentSerializer 处理，不使用 @JsonSubTypes 静态注册
@JsonSerialize(using = RawContentSerializer.class)
public abstract class RawContent {
    /** 返回 JSON type 字段的值（小写，如 "conversation"、"document"） */
    public abstract String type();
}

// ConversationContent - 对话内容（最常用）
public final class ConversationContent extends RawContent {
    private final List<Message> messages;

    public static ConversationContent of(List<Message> messages) { ... }

    @Override
    public String type() { return "conversation"; }

    public List<Message> getMessages() { return messages; }
}

// MapRawContent - 通用兜底，用于发送 client 未内置的 RawContent 类型
// 序列化时将 type 和 properties 平铺到 JSON 顶层
public final class MapRawContent extends RawContent {
    private final String type;
    private final Map<String, Object> properties;

    public static MapRawContent of(String type, Map<String, Object> properties) { ... }

    @Override
    public String type() { return type; }

    public Map<String, Object> getProperties() { return properties; }
}
```

**RawContentSerializer 序列化逻辑**：
```java
// 内部类，处理 RawContent 的 JSON 序列化
class RawContentSerializer extends JsonSerializer<RawContent> {
    @Override
    public void serialize(RawContent value, JsonGenerator gen, SerializerProvider provider) {
        gen.writeStartObject();
        gen.writeStringField("type", value.type());

        if (value instanceof ConversationContent conv) {
            gen.writeArrayFieldStart("messages");
            // 序列化 messages 列表...
            gen.writeEndArray();
        } else if (value instanceof MapRawContent map) {
            // 将 properties 中的每个 entry 平铺写入
            for (var entry : map.getProperties().entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
        }

        gen.writeEndObject();
    }
}
```

**使用 MapRawContent 的场景**（如发送文档内容）：
```java
// 发送文档 - type 必须与 server 端注册的 type name 一致（小写）
client.extract(ExtractMemoryRequest.builder()
    .userId("user-1")
    .agentId("agent-1")
    .rawContent(MapRawContent.of("document", Map.of(
        "fileName", "report.pdf",
        "content", "文档内容...",
        "mimeType", "application/pdf"
    )))
    .build());
```

### 模型独立性说明

client 的模型类独立定义，不依赖 memind-core。原因：
- memind-core 带有大量传递依赖（Spring AI、Reactor 等），引入会污染用户项目
- client 只需要 API 层面的 DTO 定义，不需要 core 的业务逻辑
- 通过版本号同步（client 与 server 同版本）保证 API 兼容性

## 内部实现

### HTTP 层

```java
// 内部类，不暴露给用户
class MemindHttpClient implements AutoCloseable {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiToken;       // nullable
    private final Duration readTimeout;

    /** 异步 POST 请求 */
    <T> CompletableFuture<T> post(String path, Object requestBody, TypeReference<ApiResult<T>> responseType);

    /** 异步 GET 请求 */
    <T> CompletableFuture<T> get(String path, TypeReference<ApiResult<T>> responseType);
}
```

### 请求 Headers

每个请求自动附加：
- `Content-Type: application/json`
- `Accept: application/json`
- `User-Agent: memind-java-client/<version>`（标识 SDK 版本）
- `Authorization: Bearer <token>`（仅当配置了 apiToken 时）

### 认证机制

- 如果配置了 `apiToken`，每个请求自动添加 `Authorization: Bearer <token>` header
- 未配置则不添加，兼容无认证的自部署场景

### 响应处理

- HTTP 2xx + ApiResult.code 为 "200" 或 "success" → 返回 `data` 字段
- HTTP 2xx + ApiResult.code 为错误码 → 抛出 `MemindApiException`
- HTTP 4xx/5xx → 尝试解析错误体，抛出 `MemindApiException`
- 网络连接失败 → 抛出 `MemindConnectionException`
- 请求超时 → 抛出 `MemindTimeoutException`

### JSON 序列化

- 使用 Jackson 2.x ObjectMapper（`com.fasterxml.jackson`），配置：
  - `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false`（前向兼容，server 新增字段不会导致 client 报错）
  - `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`（ISO-8601 格式）
  - 注册 `JavaTimeModule` 处理 `Instant`
  - `JsonInclude.Include.NON_NULL`（不序列化 null 字段）
- ContentBlock / Source 的多态序列化通过 `@JsonTypeInfo` + `@JsonSubTypes` 处理（子类型固定）
- RawContent 的序列化通过自定义 `RawContentSerializer` 处理（子类型动态扩展）

### 线程安全

`MemindClient` 是线程安全的：
- 底层 `HttpClient` 是线程安全的（JDK 文档保证）
- `ObjectMapper` 在配置完成后是线程安全的
- 所有字段均为 final，无可变状态

用户可以创建一个 `MemindClient` 实例，在整个应用生命周期中共享使用。

### 资源管理

- `close()` 是幂等的：多次调用不会抛出异常
- 关闭后调用任何 API 方法将抛出 `IllegalStateException`
- 在 Spring Boot Starter 中，Bean 销毁时自动调用 `close()`（通过 `@Bean(destroyMethod = "close")`）

### 重试策略

client 不内置重试逻辑。原因：
- 重试策略因场景而异（幂等性、退避算法、最大次数等）
- 用户可以通过 Resilience4j、Spring Retry 等成熟框架自行包装
- 保持 SDK 简单，不引入额外复杂度

### 日志

- 使用 SLF4J 作为日志门面，不绑定具体实现
- DEBUG 级别：记录请求 URL、响应状态码
- TRACE 级别：记录请求/响应 body（注意不记录 apiToken）
- 用户项目中引入 logback/log4j2 即可看到日志

## 异常体系

```java
// 基类（unchecked）
public class MemindClientException extends RuntimeException {
    public MemindClientException(String message) { ... }
    public MemindClientException(String message, Throwable cause) { ... }
}

// 服务端返回错误（HTTP 4xx/5xx 或 ApiResult.code 非成功）
public class MemindApiException extends MemindClientException {
    private final int httpStatus;
    private final String errorCode;    // "bad_request", "not_found", "conflict", "service_unavailable", "internal_error"
    private final String errorMessage;
    private final String traceId;      // nullable, 来自 ApiResult.traceId
}

// 网络连接失败（DNS 解析失败、连接拒绝等）
public class MemindConnectionException extends MemindClientException { ... }

// 请求超时（连接超时或读取超时）
public class MemindTimeoutException extends MemindClientException { ... }
```

## Spring Boot Starter

### 兼容性

- 支持 Spring Boot 3.2+（包括 3.2、3.3、3.4 等主流版本）
- 不要求 Spring Boot 4.x（server 用 4.x 不影响 client starter）

### 配置属性

```yaml
memind:
  client:
    base-url: http://localhost:8366    # 必填
    api-token:                          # 可选，SaaS 场景使用
    connect-timeout: 5s                 # 默认 5 秒
    read-timeout: 30s                   # 默认 30 秒
```

### 自动配置

需要在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册：
```
com.openmemind.ai.client.spring.MemindClientAutoConfiguration
```

```java
@AutoConfiguration
@EnableConfigurationProperties(MemindClientProperties.class)
@ConditionalOnClass(MemindClient.class)
public class MemindClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "memind.client", name = "base-url")
    public MemindClient memindClient(MemindClientProperties properties) {
        MemindClient.Builder builder = MemindClient.builder()
            .baseUrl(properties.getBaseUrl());

        if (properties.getApiToken() != null) {
            builder.apiToken(properties.getApiToken());
        }
        if (properties.getConnectTimeout() != null) {
            builder.connectTimeout(properties.getConnectTimeout());
        }
        if (properties.getReadTimeout() != null) {
            builder.readTimeout(properties.getReadTimeout());
        }

        return builder.build();
    }
}
```

### 使用方式

```java
@Service
public class MyAgentService {

    private final MemindClient memindClient;

    public MyAgentService(MemindClient memindClient) {
        this.memindClient = memindClient;
    }

    public String chat(String userId, String userMessage) {
        // 检索相关记忆
        RetrieveMemoryResponse memories = memindClient.retrieve(
            RetrieveMemoryRequest.builder()
                .userId(userId)
                .agentId("my-agent")
                .query(userMessage)
                .strategy(Strategy.SIMPLE)
                .build());

        // 使用记忆增强 LLM 上下文...
        return enhancedResponse;
    }
}
```

## 测试策略

### 单元测试

- Mock `MemindHttpClient`，验证 `MemindClient` 的请求构建和响应解析逻辑
- 验证 Builder 的参数校验（baseUrl 必填等）
- 验证异常映射逻辑（各种 HTTP 状态码 → 对应异常类型）
- 验证 JSON 序列化/反序列化（特别是多态类型）

### 集成测试

- 使用 WireMock 模拟 memind server
- 验证完整的 HTTP 请求/响应链路
- 验证认证 header 的正确传递
- 验证超时和错误场景
- 验证 User-Agent header

### Spring Boot Starter 测试

- 使用 `@SpringBootTest` 验证自动配置
- 验证 `@ConditionalOnMissingBean` 允许用户覆盖
- 验证 `@ConditionalOnProperty` 在缺少 base-url 时不创建 Bean
- 验证配置属性绑定（Duration 解析等）

## 发布策略

- groupId: `com.openmemind.ai`
- artifactId: `memind-client` / `memind-client-spring-boot-starter`
- 版本: 与 memind server 同步（0.2.0-SNAPSHOT）
- 发布到 Maven Central（复用现有的 release workflow）
- Java client 有独立的 Maven 构建，不纳入 server 的父 POM modules

