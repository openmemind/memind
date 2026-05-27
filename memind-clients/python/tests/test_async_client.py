#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

from __future__ import annotations

import pytest

from memind._async_client import AsyncMemindClient
from memind._exceptions import MemindAPIError, MemindError
from memind.types import (
    ConversationContent,
    Message,
    QueryMemoryItemsRequest,
    QueryMemoryRawDataRequest,
    Strategy,
)


@pytest.mark.asyncio
async def test_async_health_returns_response(httpx_mock) -> None:
    httpx_mock.add_response(
        method="GET",
        url="https://api.example.test/open/v1/health",
        json={"data": {"status": "UP", "service": "memind-server"}},
    )

    async with AsyncMemindClient(base_url="https://api.example.test") as client:
        health = await client.health()

    assert health.status == "UP"


@pytest.mark.asyncio
async def test_async_memory_methods_send_payloads(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/extract",
        json={
            "data": {
                "status": "SUCCESS",
                "rawDataIds": ["rd-1"],
                "itemIds": [],
                "insightIds": [],
                "insightPending": False,
            },
        },
    )
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/add-message",
        json={"data": {"triggered": False}},
    )
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/commit",
        json={
            "data": {
                "status": "SUCCESS",
                "rawDataIds": [],
                "itemIds": [],
                "insightIds": [],
                "insightPending": False,
            },
        },
    )

    client = AsyncMemindClient(base_url="https://api.example.test")
    extract_response = await client.memory.extract(
        user_id="u1",
        agent_id="a1",
        raw_content=ConversationContent(messages=[Message.user("hello")]),
    )
    await client.memory.add_message(user_id="u1", agent_id="a1", message=Message.user("hello"))
    await client.memory.commit(user_id="u1", agent_id="a1")
    await client.close()

    requests = httpx_mock.get_requests()
    assert len(requests) == 3
    assert extract_response.status == "SUCCESS"
    assert b'"rawContent":{"type":"conversation"' in requests[0].content
    assert b'"message":{"role":"USER"' in requests[1].content
    assert b'"userId":"u1"' in requests[2].content


@pytest.mark.asyncio
async def test_async_extract_agent_timeline_sends_map_raw_content(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/extract",
        json={
            "data": {
                "status": "SUCCESS",
                "rawDataIds": ["rd-1"],
                "itemIds": [],
                "insightIds": [],
                "insightPending": False,
            },
        },
    )

    client = AsyncMemindClient(base_url="https://api.example.test")
    await client.memory.extract_agent_timeline(
        user_id="u1",
        agent_id="a1",
        timeline={
            "sourceClient": "claude-code",
            "sessionId": "s",
            "timelineId": "t",
            "events": [],
        },
        source_client="claude-code",
    )
    await client.close()

    content = httpx_mock.get_request().content
    assert b'"rawContent":{"type":"agent_timeline"' in content
    assert b'"sessionId":"s"' in content
    assert b'"sourceClient":"claude-code"' in content


@pytest.mark.asyncio
async def test_async_retrieve_returns_response(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/retrieve",
        json={
            "data": {
                "status": "success",
                "items": [{"id": "1", "text": "likes coffee"}],
                "insights": [],
                "rawData": [],
                "evidences": [],
                "strategy": "DEEP",
                "query": "coffee",
            },
        },
    )

    client = AsyncMemindClient(base_url="https://api.example.test")
    result = await client.memory.retrieve(
        user_id="u1", agent_id="a1", query="coffee", strategy=Strategy.DEEP
    )
    await client.close()

    assert result.items[0].text == "likes coffee"


@pytest.mark.asyncio
async def test_async_query_methods_return_structured_results(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/items/query",
        json={
            "data": {
                "items": [{"id": "101", "text": "Run targeted tests.", "metadata": {}}],
                "nextCursor": None,
            }
        },
    )
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/raw-data/query",
        json={
            "data": {
                "rawData": [{"id": "rd-1", "type": "agent_timeline", "metadata": {}}],
                "nextCursor": None,
            }
        },
    )

    client = AsyncMemindClient(base_url="https://api.example.test")
    items = await client.memory.query_items(QueryMemoryItemsRequest(user_id="u1", agent_id="a1"))
    raw_data = await client.memory.query_raw_data(
        QueryMemoryRawDataRequest(user_id="u1", agent_id="a1")
    )
    await client.close()

    assert items.items[0].id == "101"
    assert raw_data.raw_data[0].id == "rd-1"


@pytest.mark.asyncio
async def test_async_api_error_is_raised_unwrapped(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/retrieve",
        status_code=400,
        json={"error": {"code": "bad_request", "message": "query is required"}},
    )

    client = AsyncMemindClient(base_url="https://api.example.test")
    with pytest.raises(MemindAPIError):
        await client.memory.retrieve(
            user_id="u1", agent_id="a1", query="", strategy=Strategy.SIMPLE
        )
    await client.close()


@pytest.mark.asyncio
async def test_async_close_then_call_raises_memind_error() -> None:
    client = AsyncMemindClient(base_url="https://api.example.test")
    await client.close()

    with pytest.raises(MemindError, match="closed"):
        await client.health()


@pytest.mark.asyncio
async def test_async_mutating_post_methods_do_not_retry_by_default(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/add-message",
        status_code=503,
        json={"error": {"code": "unavailable", "message": "try later"}},
    )

    client = AsyncMemindClient(base_url="https://api.example.test", max_retries=2)
    with pytest.raises(MemindAPIError) as exc_info:
        await client.memory.add_message(user_id="u1", agent_id="a1", message=Message.user("hello"))

    assert exc_info.value.status_code == 503
    assert len(httpx_mock.get_requests()) == 1
    await client.close()


@pytest.mark.asyncio
async def test_async_retrieve_retries_by_default(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/retrieve",
        status_code=503,
        json={"error": {"code": "unavailable", "message": "try later"}},
    )
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/retrieve",
        json={"data": {"items": [], "insights": [], "rawData": [], "evidences": []}},
    )

    client = AsyncMemindClient(base_url="https://api.example.test", max_retries=1)
    result = await client.memory.retrieve(
        user_id="u1",
        agent_id="a1",
        query="coffee",
        strategy=Strategy.SIMPLE,
    )

    assert result.items == []
    assert len(httpx_mock.get_requests()) == 2
    await client.close()
