# Memind Python Client

Official Python client for the Memind memory engine API.

## Installation

```bash
pip install memind
```

## Synchronous Usage

```python
from memind import MemindClient, Message, Strategy
from memind.types import ConversationContent

with MemindClient(base_url="http://localhost:8080") as client:
    health = client.health()

    response = client.memory.extract(
        user_id="user-1",
        agent_id="agent-1",
        raw_content=ConversationContent(messages=[Message.user("Remember that I prefer concise answers.")]),
    )
    print(response.status)

    result = client.memory.retrieve(
        user_id="user-1",
        agent_id="agent-1",
        query="What does the user like?",
        strategy=Strategy.SIMPLE,
        trace=True,
    )
```

`memory.extract()` uses Memind's synchronous extraction endpoint and returns `ExtractMemoryResponse`. Treat only
`status == "SUCCESS"` as safe to clear caller-owned retry payloads; `PARTIAL_SUCCESS` is surfaced so applications
can keep or re-enqueue the original payload.

## Asynchronous Usage

```python
from memind import AsyncMemindClient, Strategy

async with AsyncMemindClient(base_url="http://localhost:8080") as client:
    result = await client.memory.retrieve(
        user_id="user-1",
        agent_id="agent-1",
        query="What does the user like?",
        strategy=Strategy.DEEP,
    )
```

## Configuration

Configuration precedence:

1. Constructor arguments
2. Environment variables: `MEMIND_BASE_URL`, `MEMIND_API_TOKEN`
3. Defaults: connect timeout `5s`, read timeout `30s`, max retries `2`

`base_url` is required. `api_token` is optional; when omitted no `Authorization` header is sent.

## Development

```bash
pip install -e ".[dev]"
ruff format .
ruff check .
mypy src
pytest --cov=memind --cov-fail-under=90 -q
python -m build
twine check dist/*
```
