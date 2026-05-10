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
    CommitMemoryRequest,
    ExtractMemoryRequest,
    RetrieveMemoryRequest,
    RetrieveMemoryResponse,
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
            "code": "200",
            "message": None,
            "data": {"status": "UP", "service": "memind-server"},
            "timestamp": "2026-01-01T00:00:00Z",
            "traceId": "trace-1",
        }
        result = ApiResult[dict[str, str]].model_validate(data)
        assert result.code == "200"
        assert result.data == {"status": "UP", "service": "memind-server"}
        assert result.trace_id == "trace-1"
        assert result.is_success()

    def test_unknown_fields_ignored(self) -> None:
        data: dict[str, Any] = {"code": "200", "data": None, "extraField": "ignored"}
        result = ApiResult[None].model_validate(data)
        assert result.code == "200"


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
                {"rawDataId": "rd-1", "caption": "chat", "maxScore": 0.9, "itemIds": ["item-1"]}
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
        assert resp.evidences == ["evidence-1"]

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
