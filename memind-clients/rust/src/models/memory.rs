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
        }
    }

    pub fn trace(mut self, trace: bool) -> Self {
        self.trace = Some(trace);
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
