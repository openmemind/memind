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

from typing import Any

from pydantic import Field

from memind._models import MemindModel
from memind.types.common import Strategy
from memind.types.message import Message, RawContentValue


class ExtractMemoryRequest(MemindModel):
    user_id: str
    agent_id: str
    raw_content: RawContentValue
    source_client: str | None = None


class AddMessageRequest(MemindModel):
    user_id: str
    agent_id: str
    message: Message
    source_client: str | None = None


class CommitMemoryRequest(MemindModel):
    user_id: str
    agent_id: str
    source_client: str | None = None


class ExtractMemoryResponse(MemindModel):
    status: str
    raw_data_ids: list[str] = Field(default_factory=list)
    item_ids: list[int] = Field(default_factory=list)
    insight_ids: list[int] = Field(default_factory=list)
    insight_pending: bool = False
    duration_millis: int | None = None
    error_message: str | None = None


class AddMessageResponse(MemindModel):
    triggered: bool
    result: ExtractMemoryResponse | None = None


class RetrieveMemoryRequest(MemindModel):
    user_id: str
    agent_id: str
    query: str
    strategy: Strategy
    trace: bool | None = None
    scope: str | None = None
    categories: list[str] | None = None
    time_range: TimeRange | None = None
    metadata_filter: MetadataFilter | None = None
    include: RetrieveIncludeOptions | None = None


class TimeRange(MemindModel):
    field: str | None = None
    from_: str | None = Field(default=None, alias="from")
    to: str | None = None


class MetadataCondition(MemindModel):
    path: str
    op: str
    value: Any | None = None


class MetadataFilter(MemindModel):
    all: list[MetadataCondition] | None = None
    any: list[MetadataCondition] | None = None
    not_: list[MetadataCondition] | None = Field(default=None, alias="not")


class RetrieveIncludeOptions(MemindModel):
    raw_data_metadata: bool | None = None
    raw_data_segment: bool | None = None


class RawDataQueryIncludeOptions(MemindModel):
    segment: bool | None = None
    metadata: bool | None = None


class QueryMemoryItemsRequest(MemindModel):
    user_id: str
    agent_id: str
    scope: str | None = None
    categories: list[str] | None = None
    source_clients: list[str] | None = None
    raw_data_types: list[str] | None = None
    time_range: TimeRange | None = None
    metadata_filter: MetadataFilter | None = None
    limit: int | None = None
    cursor: str | None = None


class QueryMemoryRawDataRequest(MemindModel):
    user_id: str
    agent_id: str
    types: list[str] | None = None
    source_clients: list[str] | None = None
    time_range: TimeRange | None = None
    metadata_filter: MetadataFilter | None = None
    include: RawDataQueryIncludeOptions | None = None
    limit: int | None = None
    cursor: str | None = None


class RetrievedItem(MemindModel):
    id: str
    text: str
    vector_score: float = 0.0
    final_score: float = 0.0
    occurred_at: str | None = None
    category: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class RetrievedInsight(MemindModel):
    id: str
    text: str
    tier: str | None = None


class RetrievedRawData(MemindModel):
    raw_data_id: str
    caption: str | None = None
    max_score: float = 0.0
    item_ids: list[str] | None = None
    type: str | None = None
    source_client: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)
    start_time: str | None = None
    end_time: str | None = None
    created_at: str | None = None


class StageView(MemindModel):
    stage: str | None = None
    tier: str | None = None
    method: str | None = None
    status: str | None = None
    input_count: int | None = None
    candidate_count: int | None = None
    result_count: int | None = None
    degraded: bool = False
    skipped: bool = False
    started_at: str | None = None
    duration_millis: int | None = None
    attributes: dict[str, Any] | None = None
    candidates: list[dict[str, Any]] | None = None


class MergeView(MemindModel):
    input_count: int = 0
    output_count: int = 0
    deduplicated_count: int = 0
    source_count: int = 0
    status: str | None = None


class FinalView(MemindModel):
    strategy: str | None = None
    status: str | None = None
    item_count: int = 0
    insight_count: int = 0
    raw_data_count: int = 0
    evidence_count: int = 0


class RetrievalTraceView(MemindModel):
    trace_id: str | None = None
    started_at: str | None = None
    completed_at: str | None = None
    truncated: bool | None = None
    stages: list[StageView] = Field(default_factory=list)
    merge: MergeView | None = None
    final_results: FinalView | None = None


class RetrieveMemoryResponse(MemindModel):
    status: str | None = None
    items: list[RetrievedItem] = Field(default_factory=list)
    insights: list[RetrievedInsight] = Field(default_factory=list)
    raw_data: list[RetrievedRawData] = Field(default_factory=list)
    evidences: list[str] = Field(default_factory=list)
    strategy: str | None = None
    query: str | None = None
    trace: RetrievalTraceView | None = None


class MemoryItem(MemindModel):
    id: str
    text: str
    scope: str | None = None
    category: str | None = None
    type: str | None = None
    raw_data_id: str | None = None
    raw_data_type: str | None = None
    source_client: str | None = None
    occurred_at: str | None = None
    observed_at: str | None = None
    created_at: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class QueryMemoryItemsResponse(MemindModel):
    items: list[MemoryItem] = Field(default_factory=list)
    next_cursor: str | None = None


class MemoryRawData(MemindModel):
    id: str
    type: str | None = None
    source_client: str | None = None
    caption: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)
    segment: dict[str, Any] | None = None
    start_time: str | None = None
    end_time: str | None = None
    created_at: str | None = None


class QueryMemoryRawDataResponse(MemindModel):
    raw_data: list[MemoryRawData] = Field(default_factory=list)
    next_cursor: str | None = None
