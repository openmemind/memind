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

use memind::{
    AddMessageRequest, CommitMemoryRequest, ContentBlock, ExtractMemoryRequest,
    ExtractMemoryResponse, ExtractStatus, Message, MetadataCondition, MetadataFilter,
    QueryMemoryItemsRequest, QueryMemoryRawDataRequest, RawContent, RawDataQueryIncludeOptions,
    RetrieveIncludeOptions, RetrieveMemoryRequest, Role, Source, Strategy, TimeRange,
};
use serde_json::json;

#[test]
fn message_user_matches_wire_json() {
    let message = Message::user("hello");
    let value = serde_json::to_value(message).unwrap();
    assert_eq!(
        value,
        json!({
            "role": "USER",
            "content": [{"type": "text", "text": "hello"}]
        })
    );
}

#[test]
fn message_assistant_matches_wire_json() {
    let message = Message::assistant("hi");
    let value = serde_json::to_value(message).unwrap();
    assert_eq!(
        value,
        json!({
            "role": "ASSISTANT",
            "content": [{"type": "text", "text": "hi"}]
        })
    );
}

#[test]
fn content_block_and_source_tags_match_server() {
    let image = ContentBlock::image(Source::base64("image/png", "abcd"));
    let value = serde_json::to_value(image).unwrap();
    assert_eq!(
        value,
        json!({
            "type": "image",
            "source": {"type": "base64", "media_type": "image/png", "data": "abcd"}
        })
    );
}

#[test]
fn raw_content_conversation_serializes_flat_type_and_messages() {
    let raw = RawContent::conversation(vec![Message::user("remember this")]).unwrap();
    let value = serde_json::to_value(raw).unwrap();
    assert_eq!(
        value,
        json!({
            "type": "conversation",
            "messages": [
                {"role": "USER", "content": [{"type": "text", "text": "remember this"}]}
            ]
        })
    );
}

#[test]
fn raw_content_object_serializes_flat_fields() {
    let mut fields = serde_json::Map::new();
    fields.insert("title".to_string(), json!("Runbook"));
    fields.insert("body".to_string(), json!("steps"));

    let raw = RawContent::object("document", fields).unwrap();
    let value = serde_json::to_value(raw).unwrap();

    assert_eq!(
        value,
        json!({"type": "document", "title": "Runbook", "body": "steps"})
    );
}

#[test]
fn raw_content_object_rejects_invalid_type_inputs() {
    assert!(RawContent::object(" ", serde_json::Map::new()).is_err());
    assert!(RawContent::object("conversation", serde_json::Map::new()).is_err());

    let mut fields = serde_json::Map::new();
    fields.insert("type".to_string(), json!("bad"));
    assert!(RawContent::object("document", fields).is_err());
}

#[test]
fn raw_content_deserializes_unknown_object_without_plugin_type() {
    let raw: RawContent =
        serde_json::from_value(json!({"type": "tool-call", "name": "search"})).unwrap();

    match raw {
        RawContent::Object {
            content_type,
            fields,
        } => {
            assert_eq!(content_type, "tool-call");
            assert_eq!(fields.get("name"), Some(&json!("search")));
        }
        RawContent::Conversation { .. } => panic!("expected object raw content"),
    }
}

#[test]
fn source_validation_rejects_blank_media_inputs() {
    let message = Message {
        role: Role::User,
        content: vec![ContentBlock::image(Source::url(" "))],
        timestamp: None,
        user_name: None,
        source_client: None,
    };
    assert!(RawContent::conversation(vec![message]).is_err());

    let message = Message {
        role: Role::User,
        content: vec![ContentBlock::audio(Source::base64(" ", ""))],
        timestamp: None,
        user_name: None,
        source_client: None,
    };
    assert!(RawContent::conversation(vec![message]).is_err());
}

#[test]
fn extract_status_serializes_known_and_unknown_values() {
    assert_eq!(
        serde_json::to_value(ExtractStatus::Success).unwrap(),
        "SUCCESS"
    );
    assert_eq!(
        serde_json::to_value(ExtractStatus::PartialSuccess).unwrap(),
        "PARTIAL_SUCCESS"
    );
    assert_eq!(
        serde_json::to_value(ExtractStatus::Unknown("QUEUED".to_string())).unwrap(),
        "QUEUED"
    );
}

#[test]
fn extract_status_deserializes_unknown_values() {
    let status: ExtractStatus = serde_json::from_value(json!("QUEUED")).unwrap();
    assert_eq!(status, ExtractStatus::Unknown("QUEUED".to_string()));
}

#[test]
fn extract_response_defaults_missing_arrays_and_flags() {
    let response: ExtractMemoryResponse =
        serde_json::from_value(json!({"status": "SUCCESS"})).unwrap();

    assert_eq!(response.status, ExtractStatus::Success);
    assert!(response.raw_data_ids.is_empty());
    assert!(response.item_ids.is_empty());
    assert!(response.insight_ids.is_empty());
    assert!(!response.insight_pending);
}

#[test]
fn request_models_serialize_camel_case() {
    let raw = RawContent::conversation(vec![Message::user("hello")]).unwrap();
    let extract = ExtractMemoryRequest::new("u1", "a1", raw).source_client(" rust ");
    let value = serde_json::to_value(extract).unwrap();
    assert_eq!(value["userId"], "u1");
    assert_eq!(value["agentId"], "a1");
    assert_eq!(value["sourceClient"], "rust");

    let commit = CommitMemoryRequest::new("u1", "a1");
    let value = serde_json::to_value(commit).unwrap();
    assert_eq!(value, json!({"userId": "u1", "agentId": "a1"}));
}

#[test]
fn retrieve_request_serializes_strategy_and_trace() {
    let request = RetrieveMemoryRequest::new("u1", "a1", "what", Strategy::Deep)
        .trace(true)
        .scope("ALL")
        .category("resolution")
        .time_range(TimeRange::new("occurredAt").from("2026-01-01T00:00:00Z"))
        .metadata_filter(MetadataFilter::all(vec![MetadataCondition::new(
            "project", "eq", "memind",
        )]))
        .include(RetrieveIncludeOptions {
            raw_data_metadata: Some(true),
            raw_data_segment: Some(false),
        });
    let value = serde_json::to_value(request).unwrap();
    assert_eq!(
        value,
        json!({
            "userId": "u1",
            "agentId": "a1",
            "query": "what",
            "strategy": "DEEP",
            "trace": true,
            "scope": "ALL",
            "categories": ["resolution"],
            "timeRange": {"field": "occurredAt", "from": "2026-01-01T00:00:00Z"},
            "metadataFilter": {"all": [{"path": "project", "op": "eq", "value": "memind"}]},
            "include": {"rawDataMetadata": true, "rawDataSegment": false}
        })
    );
}

#[test]
fn structured_query_requests_serialize_camel_case() {
    let items = QueryMemoryItemsRequest::new("u1", "a1")
        .category("playbook")
        .source_client("claude-code")
        .raw_data_type("agent_timeline")
        .limit(10);
    let value = serde_json::to_value(items).unwrap();
    assert_eq!(value["sourceClients"], json!(["claude-code"]));
    assert_eq!(value["rawDataTypes"], json!(["agent_timeline"]));

    let raw_data = QueryMemoryRawDataRequest::new("u1", "a1")
        .raw_data_type("agent_timeline")
        .source_client("codex")
        .include(RawDataQueryIncludeOptions {
            segment: Some(true),
            metadata: Some(false),
        });
    let value = serde_json::to_value(raw_data).unwrap();
    assert_eq!(value["types"], json!(["agent_timeline"]));
    assert_eq!(
        value["include"],
        json!({"segment": true, "metadata": false})
    );
}

#[test]
fn add_message_request_omits_blank_source_client() {
    let request = AddMessageRequest::new("u1", "a1", Message::user("hi")).source_client("   ");
    let value = serde_json::to_value(request).unwrap();
    assert!(value.get("sourceClient").is_none());
}
