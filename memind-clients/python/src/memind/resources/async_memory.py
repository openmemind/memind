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

from typing import TYPE_CHECKING, Any, TypeVar

from memind.types.common import Strategy
from memind.types.memory import (
    AddMessageRequest,
    AddMessageResponse,
    CommitMemoryRequest,
    ExtractMemoryRequest,
    ExtractMemoryResponse,
    MetadataFilter,
    QueryMemoryItemsRequest,
    QueryMemoryItemsResponse,
    QueryMemoryRawDataRequest,
    QueryMemoryRawDataResponse,
    RetrieveIncludeOptions,
    RetrieveMemoryRequest,
    RetrieveMemoryResponse,
    TimeRange,
)
from memind.types.message import MapRawContent, Message, RawContentValue

if TYPE_CHECKING:
    from memind._async_client import AsyncMemindClient


class AsyncMemoryResource:
    def __init__(self, client: AsyncMemindClient) -> None:
        self._client = client

    async def extract(
        self,
        request: ExtractMemoryRequest | None = None,
        *,
        user_id: str | None = None,
        agent_id: str | None = None,
        raw_content: RawContentValue | None = None,
        source_client: str | None = None,
    ) -> ExtractMemoryResponse:
        payload = request or _build_extract_request(
            user_id=user_id,
            agent_id=agent_id,
            raw_content=raw_content,
            source_client=source_client,
        )
        result = await self._client._post("/memory/sync/extract", payload, ExtractMemoryResponse)
        assert result is not None
        return result

    async def extract_agent_timeline(
        self,
        *,
        user_id: str,
        agent_id: str,
        timeline: dict[str, Any],
        source_client: str | None = None,
    ) -> ExtractMemoryResponse:
        raw_content = MapRawContent.model_validate({"type": "agent_timeline", **timeline})
        return await self.extract(
            user_id=user_id,
            agent_id=agent_id,
            raw_content=raw_content,
            source_client=source_client,
        )

    async def add_message(
        self,
        request: AddMessageRequest | None = None,
        *,
        user_id: str | None = None,
        agent_id: str | None = None,
        message: Message | None = None,
        source_client: str | None = None,
    ) -> None:
        payload = request or AddMessageRequest(
            user_id=_required(user_id, "user_id"),
            agent_id=_required(agent_id, "agent_id"),
            message=_required(message, "message"),
            source_client=source_client,
        )
        await self._client._post("/memory/sync/add-message", payload, AddMessageResponse)

    async def commit(
        self,
        request: CommitMemoryRequest | None = None,
        *,
        user_id: str | None = None,
        agent_id: str | None = None,
        source_client: str | None = None,
    ) -> None:
        payload = request or CommitMemoryRequest(
            user_id=_required(user_id, "user_id"),
            agent_id=_required(agent_id, "agent_id"),
            source_client=source_client,
        )
        await self._client._post("/memory/sync/commit", payload, ExtractMemoryResponse)

    async def retrieve(
        self,
        request: RetrieveMemoryRequest | None = None,
        *,
        user_id: str | None = None,
        agent_id: str | None = None,
        query: str | None = None,
        strategy: Strategy | None = None,
        trace: bool | None = None,
        scope: str | None = None,
        categories: list[str] | None = None,
        time_range: TimeRange | None = None,
        metadata_filter: MetadataFilter | None = None,
        include: RetrieveIncludeOptions | None = None,
    ) -> RetrieveMemoryResponse:
        payload = request or RetrieveMemoryRequest(
            user_id=_required(user_id, "user_id"),
            agent_id=_required(agent_id, "agent_id"),
            query=_required(query, "query"),
            strategy=_required(strategy, "strategy"),
            trace=trace,
            scope=scope,
            categories=categories,
            time_range=time_range,
            metadata_filter=metadata_filter,
            include=include,
        )
        result = await self._client._post(
            "/memory/retrieve",
            payload,
            RetrieveMemoryResponse,
            retry=True,
        )
        assert result is not None
        return result

    async def query_items(
        self,
        request: QueryMemoryItemsRequest,
    ) -> QueryMemoryItemsResponse:
        result = await self._client._post(
            "/memory/items/query", request, QueryMemoryItemsResponse, retry=True
        )
        assert result is not None
        return result

    async def query_raw_data(
        self,
        request: QueryMemoryRawDataRequest,
    ) -> QueryMemoryRawDataResponse:
        result = await self._client._post(
            "/memory/raw-data/query", request, QueryMemoryRawDataResponse, retry=True
        )
        assert result is not None
        return result


T = TypeVar("T")


def _required(value: T | None, name: str) -> T:
    if value is None:
        raise TypeError(f"{name} is required")
    return value


def _build_extract_request(
    *,
    user_id: str | None,
    agent_id: str | None,
    raw_content: RawContentValue | None,
    source_client: str | None,
) -> ExtractMemoryRequest:
    if raw_content is None:
        raise TypeError("raw_content is required")
    return ExtractMemoryRequest(
        user_id=_required(user_id, "user_id"),
        agent_id=_required(agent_id, "agent_id"),
        raw_content=raw_content,
        source_client=source_client,
    )
