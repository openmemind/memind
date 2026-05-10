# Memind Java Client Design Spec

## Overview

为 memind 提供官方 Java Client SDK，封装 memind server 的 Public API，让 Java 开发者（AI Agent 开发者、Spring Boot 应用开发者等）能够便捷地与 memind 进行交互。

## 目标

- 提供类型安全、易用的 Java API 来调用 memind server
- 支持同步和异步（CompletableFuture）两种调用模式
- 提供 Spring Boot Starter 实现开箱即用的自动配置
- 最低 Java 17，利用 record 等现代特性
- 版本号与 memind server 保持同步（当前 0.2.0-SNAPSHOT）

## 项目结构

```
memind-clients/                              # 非 Maven 模块，所有语言 SDK 的统一入口
└── java/                                    # Java client 独立 Maven 项目
    ├── pom.xml                              # 父 POM (groupId: org.openmemind)
    ├── memind-client/                       # 核心 client 模块
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/org/openmemind/client/
    │       │   ├── MemindClient.java
    │       │   ├── MemindClientConfig.java
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
    │       │   │       ├── Role.java
    │       │   │       └── Strategy.java
    │       │   └── internal/
    │       │       └── MemindHttpClient.java
    │       └── test/java/org/openmemind/client/
    └── memind-client-spring-boot-starter/
        ├── pom.xml
        └── src/main/java/org/openmemind/client/spring/
            ├── MemindClientAutoConfiguration.java
            └── MemindClientProperties.java
```

## 依赖策略

### memind-client（核心模块）

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDK 17+ HttpClient | 内置 | HTTP 通信 |
| Jackson-databind | 2.18.x | JSON 序列化/反序列化 |

### memind-client-spring-boot-starter

| 依赖 | 版本 | 用途 |
|------|------|------|
| memind-client | ${project.version} | 核心功能 |
| spring-boot-autoconfigure | 3.x | 自动配置 |
| spring-boot-configuration-processor | 3.x | 配置元数据生成 |

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
public class MemindClient implements AutoCloseable {

    // === 同步 API ===

    /** 添加消息到记忆缓冲区 */
    public void addMessage(AddMessageRequest request);

    /** 提取记忆（从缓冲区中的消息提取结构化记忆） */
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

    public void close();
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

// ExtractMemoryRequest - rawContent 为必填，支持多态（ConversationContent 等）
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
) { ... }
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
public sealed interface ContentBlock permits TextBlock, ImageBlock, AudioBlock, VideoBlock {}
public record TextBlock(String text) implements ContentBlock {}
public record ImageBlock(Source source) implements ContentBlock {}
public record AudioBlock(Source source) implements ContentBlock {}
public record VideoBlock(Source source) implements ContentBlock {}

// Source - 媒体来源
public sealed interface Source permits UrlSource, Base64Source {}
public record UrlSource(String url) implements Source {}
public record Base64Source(String mediaType, String data) implements Source {}

// RawContent - 原始内容（多态，通过 type 字段区分）
public abstract class RawContent {
    public abstract String contentType();
}

// ConversationContent - 对话内容（最常用的 RawContent 子类型）
public class ConversationContent extends RawContent {
    private final List<Message> messages;
    public static ConversationContent of(List<Message> messages) { ... }
    public String contentType() { return "CONVERSATION"; }
}
```

## 内部实现

### HTTP 层

```java
// 内部类，不暴露给用户
class MemindHttpClient implements AutoCloseable {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiToken;       // nullable
    private final Duration connectTimeout;
    private final Duration readTimeout;

    /** 异步 POST 请求 */
    <T> CompletableFuture<T> post(String path, Object requestBody, TypeReference<ApiResult<T>> responseType);

    /** 异步 GET 请求 */
    <T> CompletableFuture<T> get(String path, TypeReference<ApiResult<T>> responseType);
}
```

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

- 使用 Jackson ObjectMapper，配置：
  - `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false`（前向兼容）
  - `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`（ISO-8601 格式）
  - 注册 `JavaTimeModule` 处理 `Instant`
- ContentBlock 的多态反序列化通过 `@JsonTypeInfo` + `@JsonSubTypes` 处理

## 异常体系

```java
// 基类（unchecked）
public class MemindClientException extends RuntimeException {
    public MemindClientException(String message) { ... }
    public MemindClientException(String message, Throwable cause) { ... }
}

// 服务端返回错误
public class MemindApiException extends MemindClientException {
    private final int httpStatus;
    private final String errorCode;    // "bad_request", "not_found", "conflict", etc.
    private final String errorMessage;
    private final String traceId;
}

// 网络连接失败
public class MemindConnectionException extends MemindClientException { ... }

// 请求超时
public class MemindTimeoutException extends MemindClientException { ... }
```

## Spring Boot Starter

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
- 验证异常映射逻辑

### 集成测试

- 使用 WireMock 模拟 memind server
- 验证完整的 HTTP 请求/响应链路
- 验证认证 header 的正确传递
- 验证超时和错误场景

### Spring Boot Starter 测试

- 使用 `@SpringBootTest` 验证自动配置
- 验证 `@ConditionalOnMissingBean` 允许用户覆盖
- 验证配置属性绑定

## 发布策略

- groupId: `org.openmemind`
- artifactId: `memind-client` / `memind-client-spring-boot-starter`
- 版本: 与 memind server 同步（0.2.0-SNAPSHOT）
- 发布到 Maven Central（复用现有的 release workflow）

