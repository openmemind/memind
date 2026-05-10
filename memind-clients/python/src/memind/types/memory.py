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


class RetrieveMemoryRequest(MemindModel):
    user_id: str
    agent_id: str
    query: str
    strategy: Strategy
    trace: bool | None = None


class RetrievedItem(MemindModel):
    id: str
    text: str
    vector_score: float = 0.0
    final_score: float = 0.0
    occurred_at: str | None = None


class RetrievedInsight(MemindModel):
    id: str
    text: str
    tier: str | None = None


class RetrievedRawData(MemindModel):
    raw_data_id: str
    caption: str | None = None
    max_score: float = 0.0
    item_ids: list[str] | None = None


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
