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

import json
from typing import Any

from memind._models import MemindModel
from memind.types.common import ApiResult, Role, Strategy
from memind.types.health import HealthResponse
from memind.types.memory import (
    AddMessageRequest,
    AddMessageResponse,
    CommitMemoryRequest,
    ExtractMemoryRequest,
    ExtractMemoryResponse,
    MetadataCondition,
    MetadataFilter,
    QueryMemoryItemsRequest,
    QueryMemoryItemsResponse,
    QueryMemoryRawDataRequest,
    QueryMemoryRawDataResponse,
    RawDataQueryIncludeOptions,
    RetrieveIncludeOptions,
    RetrieveMemoryRequest,
    RetrieveMemoryResponse,
    TimeRange,
)
from memind.types.message import (
    Base64Source,
    ConversationContent,
    ImageBlock,
    MapRawContent,
    Message,
    TextBlock,
    UrlSource,
)


class TestMemindModel:
    def test_camel_case_alias(self) -> None:
        class Sample(MemindModel):
            user_id: str
            agent_id: str

        obj = Sample(user_id="u1", agent_id="a1")
        assert obj.model_dump(by_alias=True) == {"userId": "u1", "agentId": "a1"}

    def test_populate_by_name(self) -> None:
        class Sample(MemindModel):
            user_id: str

        obj = Sample(user_id="u1")
        assert obj.user_id == "u1"

    def test_from_camel_case_json(self) -> None:
        class Sample(MemindModel):
            user_id: str
            source_client: str | None = None

        obj = Sample.model_validate({"userId": "u1", "sourceClient": "web"})
        assert obj.user_id == "u1"
        assert obj.source_client == "web"

    def test_extra_fields_ignored(self) -> None:
        class Sample(MemindModel):
            user_id: str

        obj = Sample.model_validate({"userId": "u1", "unknownField": "ignored"})
        assert obj.user_id == "u1"

    def test_exclude_none(self) -> None:
        class Sample(MemindModel):
            user_id: str
            optional_field: str | None = None

        obj = Sample(user_id="u1")
        dumped = obj.model_dump(by_alias=True, exclude_none=True)
        assert "optionalField" not in dumped


class TestCommonTypes:
    def test_role_values(self) -> None:
        assert Role.USER == "USER"
        assert Role.ASSISTANT == "ASSISTANT"

    def test_strategy_values(self) -> None:
        assert Strategy.SIMPLE == "SIMPLE"
        assert Strategy.DEEP == "DEEP"

    def test_strategy_json_serialization(self) -> None:
        assert json.loads(json.dumps(Strategy.SIMPLE)) == "SIMPLE"

    def test_success_result(self) -> None:
        data: dict[str, Any] = {
            "data": {"status": "UP", "service": "memind-server"},
        }
        result = ApiResult[dict[str, str]].model_validate(data)
        assert result.data == {"status": "UP", "service": "memind-server"}
        assert result.is_success()

    def test_unknown_fields_ignored(self) -> None:
        data: dict[str, Any] = {"data": None, "extraField": "ignored"}
        result = ApiResult[None].model_validate(data)
        assert result.data is None


class TestMessageTypes:
    def test_url_source_serialization(self) -> None:
        source = UrlSource(url="https://example.com/img.png")
        dumped = source.model_dump(by_alias=True, exclude_none=True)
        assert dumped == {"type": "url", "url": "https://example.com/img.png"}

    def test_base64_source_media_type_alias(self) -> None:
        source = Base64Source(media_type="image/png", data="abc123")
        dumped = source.model_dump(by_alias=True, exclude_none=True)
        assert dumped == {"type": "base64", "media_type": "image/png", "data": "abc123"}

    def test_base64_source_from_json(self) -> None:
        data = {"type": "base64", "media_type": "image/png", "data": "abc123"}
        source = Base64Source.model_validate(data)
        assert source.media_type == "image/png"
        assert source.data == "abc123"

    def test_text_block(self) -> None:
        block = TextBlock(text="hello")
        dumped = block.model_dump(by_alias=True, exclude_none=True)
        assert dumped == {"type": "text", "text": "hello"}

    def test_content_block_discriminated_union_from_json(self) -> None:
        msg = Message.model_validate(
            {
                "role": "USER",
                "content": [
                    {"type": "text", "text": "hello"},
                    {
                        "type": "image",
                        "source": {"type": "url", "url": "https://img.com/a.png"},
                    },
                ],
            }
        )
        assert isinstance(msg.content[0], TextBlock)
        assert isinstance(msg.content[1], ImageBlock)
        assert isinstance(msg.content[1].source, UrlSource)

    def test_user_factory(self) -> None:
        msg = Message.user("hello")
        assert msg.role == Role.USER
        assert len(msg.content) == 1
        assert isinstance(msg.content[0], TextBlock)
        assert msg.content[0].text == "hello"

    def test_assistant_factory(self) -> None:
        msg = Message.assistant("hi there", timestamp="2026-01-01T00:00:00Z")
        assert msg.role == Role.ASSISTANT
        assert msg.timestamp == "2026-01-01T00:00:00Z"

    def test_message_serialization(self) -> None:
        msg = Message.user("hello")
        dumped = msg.model_dump(by_alias=True, exclude_none=True)
        assert dumped["role"] == "USER"
        assert dumped["content"] == [{"type": "text", "text": "hello"}]
        assert "userName" not in dumped
        assert "timestamp" not in dumped

    def test_conversation_content(self) -> None:
        content = ConversationContent(messages=[Message.user("hi")])
        dumped = content.model_dump(by_alias=True, exclude_none=True)
        assert dumped["type"] == "conversation"
        assert len(dumped["messages"]) == 1

    def test_map_raw_content_serialization(self) -> None:
        content = MapRawContent(type="document", properties={"title": "Test", "body": "Content"})
        dumped = content.model_dump(by_alias=True, exclude_none=True)
        assert dumped == {"type": "document", "title": "Test", "body": "Content"}

    def test_map_raw_content_from_json(self) -> None:
        data = {"type": "document", "title": "Test", "body": "Content"}
        content = MapRawContent.model_validate(data)
        assert content.type == "document"
        assert content.properties == {"title": "Test", "body": "Content"}


class TestHealthAndMemoryModels:
    def test_health_response_from_json(self) -> None:
        data = {"status": "UP", "service": "memind-server"}
        resp = HealthResponse.model_validate(data)
        assert resp.status == "UP"
        assert resp.service == "memind-server"

    def test_extract_memory_request(self) -> None:
        req = ExtractMemoryRequest(
            user_id="u1",
            agent_id="a1",
            raw_content=ConversationContent(messages=[Message.user("hi")]),
        )
        dumped = req.model_dump(by_alias=True, exclude_none=True)
        assert dumped["userId"] == "u1"
        assert dumped["agentId"] == "a1"
        assert dumped["rawContent"]["type"] == "conversation"
        assert dumped["rawContent"]["messages"][0]["role"] == "USER"
        assert dumped["rawContent"]["messages"][0]["content"][0]["text"] == "hi"
        assert "sourceClient" not in dumped

    def test_extract_memory_response_aliases(self) -> None:
        response = ExtractMemoryResponse.model_validate(
            {
                "status": "SUCCESS",
                "rawDataIds": ["rd-1"],
                "itemIds": [101],
                "insightIds": [201],
                "insightPending": True,
                "durationMillis": 12,
                "errorMessage": None,
            }
        )

        assert response.raw_data_ids == ["rd-1"]
        assert response.item_ids == [101]
        assert response.insight_pending is True

    def test_add_message_request(self) -> None:
        req = AddMessageRequest(
            user_id="u1",
            agent_id="a1",
            message=Message.user("hello"),
            source_client="python-sdk",
        )
        dumped = req.model_dump(by_alias=True, exclude_none=True)
        assert dumped["userId"] == "u1"
        assert dumped["message"]["role"] == "USER"
        assert dumped["sourceClient"] == "python-sdk"

    def test_add_message_response_aliases(self) -> None:
        response = AddMessageResponse.model_validate(
            {
                "triggered": True,
                "result": {
                    "status": "SUCCESS",
                    "rawDataIds": ["rd-1"],
                    "itemIds": [],
                    "insightIds": [],
                    "insightPending": False,
                },
            }
        )

        assert response.triggered is True
        assert response.result is not None
        assert response.result.status == "SUCCESS"
        assert response.result.raw_data_ids == ["rd-1"]

    def test_commit_memory_request(self) -> None:
        req = CommitMemoryRequest(user_id="u1", agent_id="a1")
        dumped = req.model_dump(by_alias=True, exclude_none=True)
        assert dumped == {"userId": "u1", "agentId": "a1"}

    def test_retrieve_memory_request(self) -> None:
        req = RetrieveMemoryRequest(
            user_id="u1", agent_id="a1", query="coffee", strategy=Strategy.DEEP, trace=True
        )
        dumped = req.model_dump(by_alias=True, exclude_none=True)
        assert dumped["strategy"] == "DEEP"
        assert dumped["trace"] is True

    def test_retrieve_memory_request_with_filters(self) -> None:
        req = RetrieveMemoryRequest(
            user_id="u1",
            agent_id="a1",
            query="recent decisions",
            strategy=Strategy.DEEP,
            scope="ALL",
            categories=["resolution", "playbook"],
            time_range=TimeRange(field="occurredAt", from_="2026-01-01T00:00:00Z"),
            metadata_filter=MetadataFilter(
                all=[MetadataCondition(path="project", op="eq", value="memind")],
                not_=[MetadataCondition(path="archived", op="exists")],
            ),
            include=RetrieveIncludeOptions(raw_data_metadata=True, raw_data_segment=False),
        )
        dumped = req.model_dump(by_alias=True, exclude_none=True)
        assert dumped["scope"] == "ALL"
        assert dumped["categories"] == ["resolution", "playbook"]
        assert dumped["timeRange"]["from"] == "2026-01-01T00:00:00Z"
        assert dumped["metadataFilter"]["all"][0]["path"] == "project"
        assert dumped["metadataFilter"]["not"][0]["op"] == "exists"
        assert dumped["include"] == {"rawDataMetadata": True, "rawDataSegment": False}

    def test_retrieve_memory_response(self) -> None:
        data = {
            "status": "OK",
            "items": [
                {
                    "id": "item-1",
                    "text": "likes coffee",
                    "vectorScore": 0.95,
                    "finalScore": 0.88,
                    "occurredAt": "2026-01-01T00:00:00Z",
                }
            ],
            "insights": [{"id": "ins-1", "text": "prefers hot drinks", "tier": "CORE"}],
            "rawData": [
                {
                    "rawDataId": "rd-1",
                    "caption": "chat",
                    "maxScore": 0.9,
                    "itemIds": ["item-1"],
                    "type": "agent_timeline",
                    "sourceClient": "claude-code",
                    "metadata": {"sessionId": "s1"},
                    "startTime": "2026-01-01T00:00:00Z",
                    "endTime": "2026-01-01T00:01:00Z",
                    "createdAt": "2026-01-01T00:02:00Z",
                }
            ],
            "evidences": ["evidence-1"],
            "strategy": "SIMPLE",
            "query": "coffee",
        }
        resp = RetrieveMemoryResponse.model_validate(data)
        assert resp.status == "OK"
        assert len(resp.items) == 1
        assert resp.items[0].id == "item-1"
        assert resp.items[0].vector_score == 0.95
        assert resp.items[0].final_score == 0.88
        assert resp.items[0].occurred_at == "2026-01-01T00:00:00Z"
        assert resp.insights[0].tier == "CORE"
        assert resp.raw_data[0].raw_data_id == "rd-1"
        assert resp.raw_data[0].type == "agent_timeline"
        assert resp.raw_data[0].source_client == "claude-code"
        assert resp.raw_data[0].metadata == {"sessionId": "s1"}
        assert resp.evidences == ["evidence-1"]

    def test_query_items_models(self) -> None:
        req = QueryMemoryItemsRequest(
            user_id="u1",
            agent_id="a1",
            scope="ALL",
            categories=["resolution"],
            source_clients=["claude-code"],
            raw_data_types=["agent_timeline"],
            time_range=TimeRange(field="occurredAt", to="2026-05-01T00:00:00Z"),
            metadata_filter=MetadataFilter(
                any=[MetadataCondition(path="repo", op="contains", value="memind")]
            ),
            limit=10,
            cursor="item-9",
        )
        dumped = req.model_dump(by_alias=True, exclude_none=True)
        assert dumped["sourceClients"] == ["claude-code"]
        assert dumped["rawDataTypes"] == ["agent_timeline"]
        assert dumped["timeRange"]["to"] == "2026-05-01T00:00:00Z"

        response = QueryMemoryItemsResponse.model_validate(
            {
                "items": [
                    {
                        "id": "101",
                        "text": "Run Java tests before pushing.",
                        "scope": "AGENT",
                        "category": "playbook",
                        "type": "FACT",
                        "rawDataId": "rd-1",
                        "rawDataType": "agent_timeline",
                        "sourceClient": "claude-code",
                        "occurredAt": "2026-05-01T00:00:00Z",
                        "observedAt": "2026-05-01T00:01:00Z",
                        "createdAt": "2026-05-01T00:02:00Z",
                        "metadata": {"repo": "memind"},
                    }
                ],
                "nextCursor": "101",
            }
        )
        assert response.items[0].raw_data_type == "agent_timeline"
        assert response.items[0].metadata == {"repo": "memind"}
        assert response.next_cursor == "101"

    def test_query_raw_data_models(self) -> None:
        req = QueryMemoryRawDataRequest(
            user_id="u1",
            agent_id="a1",
            types=["agent_timeline"],
            source_clients=["codex"],
            include=RawDataQueryIncludeOptions(segment=True, metadata=True),
            limit=5,
        )
        dumped = req.model_dump(by_alias=True, exclude_none=True)
        assert dumped["types"] == ["agent_timeline"]
        assert dumped["include"] == {"segment": True, "metadata": True}

        response = QueryMemoryRawDataResponse.model_validate(
            {
                "rawData": [
                    {
                        "id": "rd-1",
                        "type": "agent_timeline",
                        "sourceClient": "codex",
                        "caption": "Fixed retry test.",
                        "metadata": {"sessionId": "s1"},
                        "segment": {"events": []},
                        "startTime": "2026-05-01T00:00:00Z",
                        "endTime": "2026-05-01T00:01:00Z",
                        "createdAt": "2026-05-01T00:02:00Z",
                    }
                ],
                "nextCursor": None,
            }
        )
        assert response.raw_data[0].id == "rd-1"
        assert response.raw_data[0].segment == {"events": []}

    def test_retrieve_memory_response_with_trace(self) -> None:
        data = {
            "status": "OK",
            "items": [],
            "insights": [],
            "rawData": [],
            "evidences": [],
            "strategy": "DEEP",
            "query": "test",
            "trace": {
                "traceId": "t-1",
                "startedAt": "2026-01-01T00:00:00Z",
                "completedAt": "2026-01-01T00:00:01Z",
                "truncated": False,
                "stages": [
                    {
                        "stage": "vector_search",
                        "method": "embedding",
                        "status": "COMPLETED",
                        "inputCount": 1,
                        "candidateCount": 10,
                        "resultCount": 5,
                        "degraded": False,
                        "skipped": False,
                        "durationMillis": 120,
                    }
                ],
                "merge": {
                    "inputCount": 5,
                    "outputCount": 4,
                    "deduplicatedCount": 1,
                    "sourceCount": 2,
                    "status": "COMPLETED",
                },
                "finalResults": {
                    "strategy": "DEEP",
                    "status": "COMPLETED",
                    "itemCount": 4,
                    "insightCount": 2,
                    "rawDataCount": 1,
                    "evidenceCount": 3,
                },
            },
        }
        resp = RetrieveMemoryResponse.model_validate(data)
        assert resp.trace is not None
        assert resp.trace.trace_id == "t-1"
        assert len(resp.trace.stages) == 1
        assert resp.trace.stages[0].duration_millis == 120
        assert resp.trace.merge is not None
        assert resp.trace.merge.deduplicated_count == 1
        assert resp.trace.final_results is not None
        assert resp.trace.final_results.item_count == 4

    def test_response_ignores_unknown_fields(self) -> None:
        data = {
            "status": "OK",
            "items": [],
            "insights": [],
            "rawData": [],
            "evidences": [],
            "strategy": "SIMPLE",
            "query": "test",
            "futureField": "should be ignored",
        }
        resp = RetrieveMemoryResponse.model_validate(data)
        assert resp.status == "OK"
