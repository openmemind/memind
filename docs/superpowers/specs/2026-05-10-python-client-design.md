# Memind Python Client Design Spec

## Overview

为 memind 提供官方 Python SDK，使 Python 用户能够方便地与 memind API 交互。设计对标 openai-python / anthropic-python 的双客户端模式，提供同步和异步两种使用方式。

## 核心决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| Python 版本 | 3.10+ | 支持 `X \| Y` 联合类型语法 |
| HTTP 库 | httpx | 原生支持同步/异步，现代 API |
| 数据模型 | Pydantic v2 | 类型验证、序列化、IDE 补全 |
| 构建工具 | Hatch/Hatchling | 现代标准，单一 pyproject.toml |
| 代码位置 | memind-clients/python/ | 与 Java client 平级 |
| PyPI 包名 | memind-client | 明确表明是 client SDK |
| import 名 | memind | 简洁，与项目名一致 |
| 客户端模式 | 双客户端 (sync + async) | 行业标准，类型安全 |

## 项目结构

```
memind-clients/python/
├── pyproject.toml
├── README.md
├── LICENSE
├── src/
│   └── memind/
│       ├── __init__.py         # 公开 API 导出
│       ├── _client.py          # MemindClient (同步)
│       ├── _async_client.py    # AsyncMemindClient (异步)
│       ├── _base_client.py     # 共享逻辑基类
│       ├── _constants.py       # 默认值、版本号
│       ├── _exceptions.py      # 异常层次
│       ├── _http.py            # HTTP 传输层封装 (重试逻辑)
│       ├── types/
│       │   ├── __init__.py
│       │   ├── memory.py       # 记忆相关请求/响应模型
│       │   ├── message.py      # Message, ContentBlock, Source
│       │   ├── health.py       # HealthResponse
│       │   └── common.py       # ApiResult, Strategy 枚举
│       └── resources/
│           ├── __init__.py
│           ├── memory.py       # Memory 资源 (同步)
│           └── async_memory.py # Memory 资源 (异步)
└── tests/
    ├── conftest.py
    ├── test_client.py
    ├── test_async_client.py
    ├── test_memory.py
    └── test_models.py
```

## 数据模型

### 通用类型

```python
class Strategy(str, Enum):
    SIMPLE = "SIMPLE"
    DEEP = "DEEP"

class ApiResult[T](BaseModel):
    code: str
    message: str | None = None
    data: T | None = None
    timestamp: str | None = None  # ISO-8601 (Instant in Java)
    trace_id: str | None = None
```

### Message 体系

```python
# Source 类型 (discriminated union via "type" field)
Source = UrlSource | Base64Source

# ContentBlock 类型 (discriminated union via "type" field)
ContentBlock = TextBlock | ImageBlock | AudioBlock | VideoBlock

class Message(BaseModel):
    role: Role  # USER | ASSISTANT
    content: list[ContentBlock]
    timestamp: str | None = None
    user_name: str | None = None
    source_client: str | None = None

    @classmethod
    def user(cls, text: str, *, timestamp: str | None = None) -> "Message": ...

    @classmethod
    def assistant(cls, text: str, *, timestamp: str | None = None) -> "Message": ...
```

### RawContent 体系

```python
class RawContent(BaseModel):
    type: str

class ConversationContent(RawContent):
    type: Literal["conversation"] = "conversation"
    messages: list[Message]

class MapRawContent(RawContent):
    type: str
    properties: dict[str, str]
    # 注意：序列化时 properties 中的键值对展开为顶层字段
    # {"type": "xxx", "key1": "val1"} 而非 {"type": "xxx", "properties": {...}}
    # 需要自定义 model_serializer 实现
```

### 请求模型

```python
class ExtractMemoryRequest(BaseModel):
    user_id: str
    agent_id: str
    raw_content: RawContent
    source_client: str | None = None

class AddMessageRequest(BaseModel):
    user_id: str
    agent_id: str
    message: Message
    source_client: str | None = None

class CommitMemoryRequest(BaseModel):
    user_id: str
    agent_id: str
    source_client: str | None = None

class RetrieveMemoryRequest(BaseModel):
    user_id: str
    agent_id: str
    query: str
    strategy: Strategy
    trace: bool | None = None
```

### 响应模型

```python
class HealthResponse(BaseModel):
    status: str
    service: str

class RetrievedItem(BaseModel):
    id: str
    text: str
    vector_score: float
    final_score: float
    occurred_at: str | None = None

class RetrievedInsight(BaseModel):
    id: str
    text: str
    tier: str | None = None

class RetrievedRawData(BaseModel):
    raw_data_id: str
    caption: str | None = None
    max_score: float
    item_ids: list[str] | None = None

class RetrievalTraceView(BaseModel):
    """可观测性追踪数据，当请求 trace=True 时返回"""
    trace_id: str | None = None
    started_at: str | None = None
    completed_at: str | None = None
    truncated: bool | None = None
    stages: list[dict] = []  # 复杂嵌套结构，使用 dict 保持灵活性
    merge: dict | None = None
    final_results: dict | None = None

class RetrieveMemoryResponse(BaseModel):
    status: str | None = None
    items: list[RetrievedItem] = []
    insights: list[RetrievedInsight] = []
    raw_data: list[RetrievedRawData] = []
    evidences: list[str] = []
    strategy: str | None = None
    query: str | None = None
    trace: RetrievalTraceView | None = None  # 当请求 trace=True 时返回
```

## 客户端 API

### 设计说明

Python client 的 resource 方法采用展开参数方式（更 Pythonic，IDE 补全更好），而非 Java 的 Request 对象方式。Request 模型类仍然保留并导出，供高级用户直接构造和传递使用。

### 同步客户端

```python
from memind import MemindClient, Strategy, Message
from memind.types import ConversationContent

# 创建客户端（参数 > 环境变量 > 默认值）
client = MemindClient(
    base_url="http://localhost:8080",  # 或 MEMIND_BASE_URL
    api_token="sk-xxx",               # 或 MEMIND_API_TOKEN
    timeout=30.0,                      # 秒
    max_retries=2,
)

# 健康检查
health = client.health()

# 记忆操作（通过 memory 命名空间）
client.memory.extract(user_id="u1", agent_id="a1", raw_content=...)
client.memory.add_message(user_id="u1", agent_id="a1", message=Message.user("..."))
client.memory.commit(user_id="u1", agent_id="a1")
result = client.memory.retrieve(user_id="u1", agent_id="a1", query="...", strategy=Strategy.SIMPLE)

# 资源管理
client.close()
# 或
with MemindClient(...) as client:
    ...
```

### 异步客户端

```python
from memind import AsyncMemindClient

async with AsyncMemindClient(base_url="...") as client:
    result = await client.memory.retrieve(
        user_id="u1", agent_id="a1", query="...", strategy=Strategy.DEEP
    )
```

### 配置优先级

1. 构造函数参数（最高）
2. 环境变量：`MEMIND_BASE_URL`, `MEMIND_API_TOKEN`
3. 默认值：timeout=30s (connect=5s, read=30s), max_retries=2

### timeout 配置

支持两种方式：
- 简单模式：`timeout=30.0`（统一超时）
- 细粒度模式：`timeout=httpx.Timeout(connect=5.0, read=30.0, write=30.0, pool=5.0)`

默认值与 Java client 对齐：connect=5s, read=30s。

## 异常层次

```
MemindError (基类, extends Exception)
├── MemindAPIError (API 错误, 含 status_code/error_code/trace_id/body)
│   ├── MemindAuthenticationError (401)
│   └── MemindRateLimitError (429, 含 retry_after)
├── MemindConnectionError (网络不可达)
└── MemindTimeoutError (超时)
```

所有异常携带足够调试信息，通过 `__cause__` 链保留底层 httpx 异常。

## 重试策略

- 重试条件：网络错误、408、429、500、502、503、504
- 退避算法：指数退避 + 抖动（0.5s → 1s → 2s）
- 429 时尊重 `Retry-After` 响应头
- 所有 POST 操作均可重试（memind API 操作幂等）
- 默认最大重试 2 次

## 内部架构

### BaseClient

共享配置解析、URL 构建、请求头构造、响应处理逻辑：
- `_build_headers()` → User-Agent + Authorization + Content-Type
- `_build_url(path)` → `{base_url}/open/v1{path}`
- `_process_response(response, response_type)` → 解析 ApiResult 包装，成功返回 data，失败抛异常

### JSON 序列化约定

- 请求：`model_dump(by_alias=True, exclude_none=True)` → camelCase JSON
- 响应：camelCase JSON → `model_validate()` → snake_case 属性
- 通过 Pydantic `ConfigDict(alias_generator=to_camel, populate_by_name=True)` 实现
- 例外：`Base64Source.media_type` 在 JSON 中为 snake_case `"media_type"`（与 Java `@JsonProperty("media_type")` 对齐），需单独配置 alias
- `MapRawContent` 序列化时需自定义 `model_serializer`，将 properties 展开为顶层字段

### 日志

- 使用标准 `logging` 模块，logger 名称：`memind`
- DEBUG：请求/响应详情
- WARNING：重试事件
- INFO：客户端生命周期事件

## 依赖

### 运行时

- `httpx >= 0.27.0` — HTTP 客户端
- `pydantic >= 2.0.0` — 数据模型

### 开发

- `pytest >= 8.0` — 测试框架
- `pytest-asyncio >= 0.23` — 异步测试
- `pytest-httpx >= 0.30` — httpx mock
- `ruff` — Linting + formatting
- `mypy` — 类型检查

## 测试策略

- 单元测试：模型序列化/反序列化、异常构造、配置解析
- 集成测试：使用 pytest-httpx mock HTTP 交互，验证完整请求/响应流程
- 异步测试：使用 pytest-asyncio 测试 AsyncMemindClient
- 覆盖率目标：>90%

## 版本与发布

- 版本号与 memind 主项目对齐：0.2.0
- 发布到 PyPI，包名 `memind-client`
- GitHub Actions CI/CD 发布流程（参考现有 Java client release workflow）
