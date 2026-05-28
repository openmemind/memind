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

from memind._client import MemindClient
from memind._exceptions import MemindAPIError, MemindError
from memind.types import (
    ConversationContent,
    ExtractMemoryResponse,
    Message,
    MetadataCondition,
    MetadataFilter,
    QueryMemoryItemsRequest,
    QueryMemoryRawDataRequest,
    RawDataQueryIncludeOptions,
    RetrieveIncludeOptions,
    RetrieveMemoryRequest,
    Strategy,
    TimeRange,
)


def test_health_returns_response(httpx_mock) -> None:
    httpx_mock.add_response(
        method="GET",
        url="https://api.example.test/open/v1/health",
        json={"data": {"status": "UP", "service": "memind-server"}},
    )

    with MemindClient(base_url="https://api.example.test") as client:
        health = client.health()

    assert health.status == "UP"
    request = httpx_mock.get_request()
    assert request.headers["User-Agent"].startswith("memind-python/")


def test_add_message_sends_payload_and_auth_header(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/add-message",
        json={"data": {"triggered": False}},
    )

    client = MemindClient(base_url="https://api.example.test", api_token="sk-test")
    client.memory.add_message(user_id="u1", agent_id="a1", message=Message.user("hello"))

    request = httpx_mock.get_request()
    assert request.headers["Authorization"] == "Bearer sk-test"
    assert b'"userId":"u1"' in request.content
    assert b'"message":{"role":"USER"' in request.content
    client.close()


def test_extract_sends_raw_content(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/extract",
        json={
            "data": {
                "status": "SUCCESS",
                "rawDataIds": ["rd-1"],
                "itemIds": [101],
                "insightIds": [],
                "insightPending": False,
                "durationMillis": 12,
            },
        },
    )

    client = MemindClient(base_url="https://api.example.test")
    response = client.memory.extract(
        user_id="u1",
        agent_id="a1",
        raw_content=ConversationContent(messages=[Message.user("hello")]),
    )

    assert isinstance(response, ExtractMemoryResponse)
    assert response.status == "SUCCESS"
    assert response.raw_data_ids == ["rd-1"]
    assert b'"rawContent":{"type":"conversation"' in httpx_mock.get_request().content
    client.close()


def test_extract_agent_timeline_sends_map_raw_content(httpx_mock) -> None:
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

    client = MemindClient(base_url="https://api.example.test")
    client.memory.extract_agent_timeline(
        user_id="u1",
        agent_id="a1",
        timeline={
            "sourceClient": "claude-code",
            "sessionId": "s",
            "agentTurnId": "s-agent-turn-1-1",
            "timelineId": "t",
            "events": [],
        },
        source_client="claude-code",
    )

    content = httpx_mock.get_request().content
    assert b'"rawContent":{"type":"agent_timeline"' in content
    assert b'"sessionId":"s"' in content
    assert b'"sourceClient":"claude-code"' in content
    client.close()


def test_commit_sends_payload(httpx_mock) -> None:
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

    client = MemindClient(base_url="https://api.example.test")
    client.memory.commit(user_id="u1", agent_id="a1")

    assert b'"userId":"u1"' in httpx_mock.get_request().content
    client.close()


def test_extract_partial_success_is_returned(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/extract",
        json={
            "data": {
                "status": "PARTIAL_SUCCESS",
                "rawDataIds": ["rd-1"],
                "itemIds": [],
                "insightIds": [],
                "insightPending": False,
                "errorMessage": "insight failed",
            },
        },
    )

    client = MemindClient(base_url="https://api.example.test")
    response = client.memory.extract(
        user_id="u1",
        agent_id="a1",
        raw_content=ConversationContent(messages=[Message.user("hello")]),
    )

    assert response.status == "PARTIAL_SUCCESS"
    assert response.error_message == "insight failed"
    client.close()


def test_extract_failure_envelope_raises_api_error(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/extract",
        status_code=500,
        json={
            "error": {"code": "extraction_failed", "message": "extract failed"},
        },
    )

    client = MemindClient(base_url="https://api.example.test")
    with pytest.raises(MemindAPIError) as exc_info:
        client.memory.extract(
            user_id="u1",
            agent_id="a1",
            raw_content=ConversationContent(messages=[Message.user("hello")]),
        )

    assert exc_info.value.status_code == 500
    assert exc_info.value.error_code == "extraction_failed"
    client.close()


def test_retrieve_accepts_expanded_parameters(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/retrieve",
        json={
            "data": {
                "status": "success",
                "items": [{"id": "1", "text": "likes coffee", "vectorScore": 0.9}],
                "insights": [],
                "rawData": [],
                "evidences": [],
                "strategy": "SIMPLE",
                "query": "coffee",
            },
        },
    )

    client = MemindClient(base_url="https://api.example.test")
    result = client.memory.retrieve(
        user_id="u1",
        agent_id="a1",
        query="coffee",
        strategy=Strategy.SIMPLE,
        trace=True,
        scope="ALL",
        categories=["profile"],
        time_range=TimeRange(field="occurredAt", from_="2026-01-01T00:00:00Z"),
        metadata_filter=MetadataFilter(
            all=[MetadataCondition(path="project", op="eq", value="memind")]
        ),
        include=RetrieveIncludeOptions(raw_data_metadata=True),
    )

    assert result.items[0].text == "likes coffee"
    content = httpx_mock.get_request().content
    assert b'"trace":true' in content
    assert b'"scope":"ALL"' in content
    assert b'"categories":["profile"]' in content
    assert b'"metadataFilter":{"all":[{"path":"project","op":"eq","value":"memind"}]}' in content
    client.close()


def test_retrieve_accepts_request_object(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/retrieve",
        json={"data": {"items": [], "insights": [], "rawData": [], "evidences": []}},
    )

    client = MemindClient(base_url="https://api.example.test")
    request = RetrieveMemoryRequest(
        user_id="u1", agent_id="a1", query="coffee", strategy=Strategy.DEEP
    )
    result = client.memory.retrieve(request)

    assert result.items == []
    client.close()


def test_query_items_posts_to_open_query_endpoint(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/items/query",
        json={
            "data": {
                "items": [
                    {
                        "id": "101",
                        "text": "Use mvn -pl memind-server test.",
                        "scope": "AGENT",
                        "category": "playbook",
                        "rawDataId": "rd-1",
                        "rawDataType": "agent_timeline",
                        "sourceClient": "claude-code",
                        "metadata": {"project": "memind"},
                    }
                ],
                "nextCursor": "101",
            }
        },
    )

    client = MemindClient(base_url="https://api.example.test")
    result = client.memory.query_items(
        QueryMemoryItemsRequest(
            user_id="u1",
            agent_id="a1",
            categories=["playbook"],
            source_clients=["claude-code"],
            raw_data_types=["agent_timeline"],
            limit=10,
        )
    )

    assert result.items[0].text.startswith("Use mvn")
    assert result.next_cursor == "101"
    content = httpx_mock.get_request().content
    assert b'"sourceClients":["claude-code"]' in content
    assert b'"rawDataTypes":["agent_timeline"]' in content
    client.close()


def test_query_raw_data_posts_to_open_query_endpoint(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/raw-data/query",
        json={
            "data": {
                "rawData": [
                    {
                        "id": "rd-1",
                        "type": "agent_timeline",
                        "sourceClient": "codex",
                        "caption": "Fixed retry test.",
                        "metadata": {"sessionId": "s1"},
                        "segment": {"events": []},
                    }
                ],
                "nextCursor": None,
            }
        },
    )

    client = MemindClient(base_url="https://api.example.test")
    result = client.memory.query_raw_data(
        QueryMemoryRawDataRequest(
            user_id="u1",
            agent_id="a1",
            types=["agent_timeline"],
            source_clients=["codex"],
            include=RawDataQueryIncludeOptions(segment=True),
        )
    )

    assert result.raw_data[0].id == "rd-1"
    assert result.raw_data[0].segment == {"events": []}
    assert b'"include":{"segment":true}' in httpx_mock.get_request().content
    client.close()


def test_api_error_is_raised_unwrapped(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/retrieve",
        status_code=400,
        headers={"X-Request-Id": "rid-1"},
        json={
            "error": {
                "code": "bad_request",
                "message": "query is required",
                "details": {"fieldErrors": {"query": "must not be blank"}},
            }
        },
    )

    client = MemindClient(base_url="https://api.example.test")
    with pytest.raises(MemindAPIError) as exc_info:
        client.memory.retrieve(user_id="u1", agent_id="a1", query="", strategy=Strategy.SIMPLE)

    assert exc_info.value.status_code == 400
    assert exc_info.value.error_code == "bad_request"
    assert exc_info.value.body is not None
    assert exc_info.value.body["error"]["details"]["fieldErrors"]["query"] == "must not be blank"
    client.close()


def test_close_then_call_raises_memind_error() -> None:
    client = MemindClient(base_url="https://api.example.test")
    client.close()

    with pytest.raises(MemindError, match="closed"):
        client.health()


def test_mutating_post_methods_do_not_retry_by_default(httpx_mock) -> None:
    httpx_mock.add_response(
        method="POST",
        url="https://api.example.test/open/v1/memory/sync/add-message",
        status_code=503,
        json={"error": {"code": "unavailable", "message": "try later"}},
    )

    client = MemindClient(base_url="https://api.example.test", max_retries=2)
    with pytest.raises(MemindAPIError) as exc_info:
        client.memory.add_message(user_id="u1", agent_id="a1", message=Message.user("hello"))

    assert exc_info.value.status_code == 503
    assert len(httpx_mock.get_requests()) == 1
    client.close()


def test_retrieve_retries_by_default(httpx_mock) -> None:
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

    client = MemindClient(base_url="https://api.example.test", max_retries=1)
    result = client.memory.retrieve(
        user_id="u1", agent_id="a1", query="coffee", strategy=Strategy.SIMPLE
    )

    assert result.items == []
    assert len(httpx_mock.get_requests()) == 2
    client.close()
