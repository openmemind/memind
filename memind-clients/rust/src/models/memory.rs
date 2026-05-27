// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use serde::{Deserialize, Serialize};
use time::OffsetDateTime;

use super::{ExtractStatus, Message, RawContent, Strategy};
use crate::{MemindError, Result};

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExtractMemoryRequest {
    pub user_id: String,
    pub agent_id: String,
    pub raw_content: RawContent,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source_client: Option<String>,
}

impl ExtractMemoryRequest {
    pub fn new(
        user_id: impl Into<String>,
        agent_id: impl Into<String>,
        raw_content: RawContent,
    ) -> Self {
        Self {
            user_id: user_id.into(),
            agent_id: agent_id.into(),
            raw_content,
            source_client: None,
        }
    }

    pub fn source_client(mut self, source_client: impl Into<String>) -> Self {
        self.source_client = normalize_optional_string(source_client.into());
        self
    }

    pub(crate) fn validate(&self) -> Result<()> {
        validate_identity(&self.user_id, &self.agent_id)?;
        self.raw_content.validate()
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AddMessageRequest {
    pub user_id: String,
    pub agent_id: String,
    pub message: Message,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source_client: Option<String>,
}

impl AddMessageRequest {
    pub fn new(user_id: impl Into<String>, agent_id: impl Into<String>, message: Message) -> Self {
        Self {
            user_id: user_id.into(),
            agent_id: agent_id.into(),
            message,
            source_client: None,
        }
    }

    pub fn source_client(mut self, source_client: impl Into<String>) -> Self {
        self.source_client = normalize_optional_string(source_client.into());
        self
    }

    pub(crate) fn validate(&self) -> Result<()> {
        validate_identity(&self.user_id, &self.agent_id)?;
        self.message.validate()
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CommitMemoryRequest {
    pub user_id: String,
    pub agent_id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source_client: Option<String>,
}

impl CommitMemoryRequest {
    pub fn new(user_id: impl Into<String>, agent_id: impl Into<String>) -> Self {
        Self {
            user_id: user_id.into(),
            agent_id: agent_id.into(),
            source_client: None,
        }
    }

    pub fn source_client(mut self, source_client: impl Into<String>) -> Self {
        self.source_client = normalize_optional_string(source_client.into());
        self
    }

    pub(crate) fn validate(&self) -> Result<()> {
        validate_identity(&self.user_id, &self.agent_id)
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrieveMemoryRequest {
    pub user_id: String,
    pub agent_id: String,
    pub query: String,
    pub strategy: Strategy,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub trace: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scope: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub categories: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub time_range: Option<TimeRange>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata_filter: Option<MetadataFilter>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub include: Option<RetrieveIncludeOptions>,
}

impl RetrieveMemoryRequest {
    pub fn new(
        user_id: impl Into<String>,
        agent_id: impl Into<String>,
        query: impl Into<String>,
        strategy: Strategy,
    ) -> Self {
        Self {
            user_id: user_id.into(),
            agent_id: agent_id.into(),
            query: query.into(),
            strategy,
            trace: None,
            scope: None,
            categories: Vec::new(),
            time_range: None,
            metadata_filter: None,
            include: None,
        }
    }

    pub fn trace(mut self, trace: bool) -> Self {
        self.trace = Some(trace);
        self
    }

    pub fn scope(mut self, scope: impl Into<String>) -> Self {
        self.scope = normalize_optional_string(scope.into());
        self
    }

    pub fn category(mut self, category: impl Into<String>) -> Self {
        let category = category.into();
        if let Some(category) = normalize_optional_string(category) {
            self.categories.push(category);
        }
        self
    }

    pub fn categories<I, S>(mut self, categories: I) -> Self
    where
        I: IntoIterator<Item = S>,
        S: Into<String>,
    {
        self.categories = categories
            .into_iter()
            .filter_map(|category| normalize_optional_string(category.into()))
            .collect();
        self
    }

    pub fn time_range(mut self, time_range: TimeRange) -> Self {
        self.time_range = Some(time_range);
        self
    }

    pub fn metadata_filter(mut self, metadata_filter: MetadataFilter) -> Self {
        self.metadata_filter = Some(metadata_filter);
        self
    }

    pub fn include(mut self, include: RetrieveIncludeOptions) -> Self {
        self.include = Some(include);
        self
    }

    pub(crate) fn validate(&self) -> Result<()> {
        validate_identity(&self.user_id, &self.agent_id)?;
        if self.query.trim().is_empty() {
            return Err(MemindError::invalid_request("query must be non-blank"));
        }
        Ok(())
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TimeRange {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub field: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub from: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub to: Option<String>,
}

impl TimeRange {
    pub fn new(field: impl Into<String>) -> Self {
        Self {
            field: normalize_optional_string(field.into()),
            from: None,
            to: None,
        }
    }

    pub fn from(mut self, from: impl Into<String>) -> Self {
        self.from = normalize_optional_string(from.into());
        self
    }

    pub fn to(mut self, to: impl Into<String>) -> Self {
        self.to = normalize_optional_string(to.into());
        self
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MetadataCondition {
    pub path: String,
    pub op: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub value: Option<serde_json::Value>,
}

impl MetadataCondition {
    pub fn new(
        path: impl Into<String>,
        op: impl Into<String>,
        value: impl Into<serde_json::Value>,
    ) -> Self {
        Self {
            path: path.into(),
            op: op.into(),
            value: Some(value.into()),
        }
    }

    pub fn flag(path: impl Into<String>, op: impl Into<String>) -> Self {
        Self {
            path: path.into(),
            op: op.into(),
            value: None,
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MetadataFilter {
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub all: Vec<MetadataCondition>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub any: Vec<MetadataCondition>,
    #[serde(default, rename = "not", skip_serializing_if = "Vec::is_empty")]
    pub not_: Vec<MetadataCondition>,
}

impl MetadataFilter {
    pub fn all(all: Vec<MetadataCondition>) -> Self {
        Self {
            all,
            any: Vec::new(),
            not_: Vec::new(),
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrieveIncludeOptions {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub raw_data_metadata: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub raw_data_segment: Option<bool>,
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RawDataQueryIncludeOptions {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub segment: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata: Option<bool>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryMemoryItemsRequest {
    pub user_id: String,
    pub agent_id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scope: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub categories: Vec<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub source_clients: Vec<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub raw_data_types: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub time_range: Option<TimeRange>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata_filter: Option<MetadataFilter>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub limit: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cursor: Option<String>,
}

impl QueryMemoryItemsRequest {
    pub fn new(user_id: impl Into<String>, agent_id: impl Into<String>) -> Self {
        Self {
            user_id: user_id.into(),
            agent_id: agent_id.into(),
            scope: None,
            categories: Vec::new(),
            source_clients: Vec::new(),
            raw_data_types: Vec::new(),
            time_range: None,
            metadata_filter: None,
            limit: None,
            cursor: None,
        }
    }

    pub fn category(mut self, category: impl Into<String>) -> Self {
        if let Some(category) = normalize_optional_string(category.into()) {
            self.categories.push(category);
        }
        self
    }

    pub fn source_client(mut self, source_client: impl Into<String>) -> Self {
        if let Some(source_client) = normalize_optional_string(source_client.into()) {
            self.source_clients.push(source_client);
        }
        self
    }

    pub fn raw_data_type(mut self, raw_data_type: impl Into<String>) -> Self {
        if let Some(raw_data_type) = normalize_optional_string(raw_data_type.into()) {
            self.raw_data_types.push(raw_data_type);
        }
        self
    }

    pub fn limit(mut self, limit: u32) -> Self {
        self.limit = Some(limit);
        self
    }

    pub(crate) fn validate(&self) -> Result<()> {
        validate_identity(&self.user_id, &self.agent_id)?;
        validate_limit(self.limit)
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryMemoryRawDataRequest {
    pub user_id: String,
    pub agent_id: String,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub types: Vec<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub source_clients: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub time_range: Option<TimeRange>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata_filter: Option<MetadataFilter>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub include: Option<RawDataQueryIncludeOptions>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub limit: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cursor: Option<String>,
}

impl QueryMemoryRawDataRequest {
    pub fn new(user_id: impl Into<String>, agent_id: impl Into<String>) -> Self {
        Self {
            user_id: user_id.into(),
            agent_id: agent_id.into(),
            types: Vec::new(),
            source_clients: Vec::new(),
            time_range: None,
            metadata_filter: None,
            include: None,
            limit: None,
            cursor: None,
        }
    }

    pub fn raw_data_type(mut self, raw_data_type: impl Into<String>) -> Self {
        if let Some(raw_data_type) = normalize_optional_string(raw_data_type.into()) {
            self.types.push(raw_data_type);
        }
        self
    }

    pub fn source_client(mut self, source_client: impl Into<String>) -> Self {
        if let Some(source_client) = normalize_optional_string(source_client.into()) {
            self.source_clients.push(source_client);
        }
        self
    }

    pub fn include(mut self, include: RawDataQueryIncludeOptions) -> Self {
        self.include = Some(include);
        self
    }

    pub(crate) fn validate(&self) -> Result<()> {
        validate_identity(&self.user_id, &self.agent_id)?;
        validate_limit(self.limit)
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExtractMemoryResponse {
    pub status: ExtractStatus,
    #[serde(default)]
    pub raw_data_ids: Vec<String>,
    #[serde(default)]
    pub item_ids: Vec<i64>,
    #[serde(default)]
    pub insight_ids: Vec<i64>,
    #[serde(default)]
    pub insight_pending: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub duration_millis: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error_message: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AddMessageResponse {
    pub triggered: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub result: Option<ExtractMemoryResponse>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrieveMemoryResponse {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
    #[serde(default)]
    pub items: Vec<RetrievedItem>,
    #[serde(default)]
    pub insights: Vec<RetrievedInsight>,
    #[serde(default)]
    pub raw_data: Vec<RetrievedRawData>,
    #[serde(default)]
    pub evidences: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub strategy: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub query: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub trace: Option<RetrievalTraceView>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrievedItem {
    pub id: String,
    pub text: String,
    #[serde(default)]
    pub vector_score: f64,
    #[serde(default)]
    pub final_score: f64,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub occurred_at: Option<OffsetDateTime>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub category: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata: Option<serde_json::Map<String, serde_json::Value>>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrievedInsight {
    pub id: String,
    pub text: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tier: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrievedRawData {
    pub raw_data_id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub caption: Option<String>,
    #[serde(default)]
    pub max_score: f64,
    #[serde(default)]
    pub item_ids: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub r#type: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source_client: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata: Option<serde_json::Map<String, serde_json::Value>>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub start_time: Option<OffsetDateTime>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub end_time: Option<OffsetDateTime>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub created_at: Option<OffsetDateTime>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryMemoryItemsResponse {
    #[serde(default)]
    pub items: Vec<MemoryItem>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub next_cursor: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MemoryItem {
    pub id: String,
    pub text: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scope: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub category: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub r#type: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub raw_data_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub raw_data_type: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source_client: Option<String>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub occurred_at: Option<OffsetDateTime>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub observed_at: Option<OffsetDateTime>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub created_at: Option<OffsetDateTime>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata: Option<serde_json::Map<String, serde_json::Value>>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryMemoryRawDataResponse {
    #[serde(default)]
    pub raw_data: Vec<MemoryRawData>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub next_cursor: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MemoryRawData {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub r#type: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source_client: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub caption: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub metadata: Option<serde_json::Map<String, serde_json::Value>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub segment: Option<serde_json::Map<String, serde_json::Value>>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub start_time: Option<OffsetDateTime>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub end_time: Option<OffsetDateTime>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub created_at: Option<OffsetDateTime>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrievalTraceView {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub trace_id: Option<String>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub started_at: Option<OffsetDateTime>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub completed_at: Option<OffsetDateTime>,
    #[serde(default)]
    pub truncated: bool,
    #[serde(default)]
    pub stages: Vec<StageView>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub merge: Option<MergeView>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub final_results: Option<FinalView>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StageView {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub stage: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tier: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub method: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub input_count: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub candidate_count: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub result_count: Option<i64>,
    #[serde(default)]
    pub degraded: bool,
    #[serde(default)]
    pub skipped: bool,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub started_at: Option<OffsetDateTime>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub duration_millis: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub attributes: Option<serde_json::Map<String, serde_json::Value>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub candidates: Option<Vec<serde_json::Value>>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MergeView {
    #[serde(default)]
    pub input_count: i64,
    #[serde(default)]
    pub output_count: i64,
    #[serde(default)]
    pub deduplicated_count: i64,
    #[serde(default)]
    pub source_count: i64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FinalView {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub strategy: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
    #[serde(default)]
    pub item_count: i64,
    #[serde(default)]
    pub insight_count: i64,
    #[serde(default)]
    pub raw_data_count: i64,
    #[serde(default)]
    pub evidence_count: i64,
}

fn normalize_optional_string(value: String) -> Option<String> {
    let trimmed = value.trim().to_string();
    (!trimmed.is_empty()).then_some(trimmed)
}

fn validate_identity(user_id: &str, agent_id: &str) -> Result<()> {
    if user_id.trim().is_empty() {
        return Err(MemindError::invalid_request("user_id must be non-blank"));
    }
    if agent_id.trim().is_empty() {
        return Err(MemindError::invalid_request("agent_id must be non-blank"));
    }
    Ok(())
}

fn validate_limit(limit: Option<u32>) -> Result<()> {
    if let Some(limit) = limit {
        if !(1..=100).contains(&limit) {
            return Err(MemindError::invalid_request(
                "limit must be between 1 and 100",
            ));
        }
    }
    Ok(())
}
