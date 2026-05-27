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

use std::sync::{Mutex, OnceLock};
use std::time::Duration;

use memind::{
    AddMessageRequest, CommitMemoryRequest, ExtractMemoryRequest, MemindClient, MemindError,
    QueryMemoryItemsRequest, QueryMemoryRawDataRequest, RawContent, RequestOptions,
    RetrieveMemoryRequest,
};
use reqwest::header::{HeaderName, HeaderValue, AUTHORIZATION, CONTENT_TYPE, USER_AGENT};
use serde_json::json;
use wiremock::matchers::{header, method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

static ENV_LOCK: OnceLock<Mutex<()>> = OnceLock::new();

#[test]
fn builder_requires_base_url() {
    let _guard = ENV_LOCK.get_or_init(|| Mutex::new(())).lock().unwrap();
    std::env::remove_var("MEMIND_BASE_URL");

    let result = MemindClient::builder().build();
    assert!(matches!(result, Err(MemindError::Config { .. })));
}

#[test]
fn debug_output_does_not_expose_api_token() {
    let client = MemindClient::builder()
        .base_url("https://api.example.test")
        .api_token("secret-token")
        .build()
        .unwrap();

    let output = format!("{client:?}");
    assert!(output.contains("api_token_configured"));
    assert!(!output.contains("secret-token"));
}

#[test]
fn debug_output_does_not_expose_builder_or_request_header_secrets() {
    let builder = MemindClient::builder()
        .base_url("https://api.example.test")
        .api_token("secret-token")
        .extra_header(
            HeaderName::from_static("x-client-secret"),
            HeaderValue::from_static("secret-token"),
        )
        .unwrap();
    let output = format!("{builder:?}");
    assert!(output.contains("api_token_configured"));
    assert!(output.contains("extra_header_count"));
    assert!(!output.contains("secret-token"));

    let options = RequestOptions::new()
        .header(
            HeaderName::from_static("x-client-secret"),
            HeaderValue::from_static("secret-token"),
        )
        .unwrap();
    let output = format!("{options:?}");
    assert!(output.contains("header_count"));
    assert!(!output.contains("secret-token"));
}

#[test]
fn builder_reads_base_url_and_token_from_environment() {
    let _guard = ENV_LOCK.get_or_init(|| Mutex::new(())).lock().unwrap();
    std::env::set_var("MEMIND_BASE_URL", "https://api.example.test/");
    std::env::set_var("MEMIND_API_TOKEN", " token-1 ");

    let client = MemindClient::from_env().expect("env client");
    assert_eq!(client.base_url(), "https://api.example.test");

    std::env::remove_var("MEMIND_BASE_URL");
    std::env::remove_var("MEMIND_API_TOKEN");
}

#[test]
fn builder_rejects_sdk_owned_extra_headers() {
    let err = MemindClient::builder()
        .base_url("https://api.example.test")
        .extra_header(AUTHORIZATION, HeaderValue::from_static("Bearer nope"))
        .unwrap_err();

    assert!(matches!(err, MemindError::Config { .. }));
}

#[test]
fn builder_rejects_zero_timeout_on_build() {
    let err = MemindClient::builder()
        .base_url("https://api.example.test")
        .timeout(Duration::ZERO)
        .build()
        .unwrap_err();

    assert!(matches!(err, MemindError::Config { .. }));
}

#[test]
fn builder_rejects_base_url_with_query_or_fragment() {
    for base_url in [
        "https://api.example.test?region=us",
        "https://api.example.test#fragment",
    ] {
        let err = MemindClient::builder()
            .base_url(base_url)
            .build()
            .unwrap_err();
        assert!(matches!(err, MemindError::Config { .. }), "{base_url}");
    }
}

#[test]
fn request_options_rejects_sdk_owned_headers() {
    for header in [AUTHORIZATION, CONTENT_TYPE, USER_AGENT] {
        let err = RequestOptions::new()
            .header(header, HeaderValue::from_static("bad"))
            .unwrap_err();
        assert!(matches!(err, MemindError::InvalidRequest { .. }));
    }
}

#[test]
fn request_options_accepts_custom_headers_and_overrides() {
    let options = RequestOptions::new()
        .timeout(Duration::from_secs(10))
        .max_retries(0)
        .header(
            HeaderName::from_static("x-client-trace"),
            HeaderValue::from_static("trace-1"),
        )
        .expect("custom header");

    let output = format!("{options:?}");
    assert!(output.contains("timeout"));
    assert!(output.contains("max_retries"));
    assert!(output.contains("header_count"));
}

#[tokio::test]
async fn health_calls_open_v1_health_and_parses_success_envelope() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .and(header("accept", "application/json"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "data": {"status": "ok", "service": "memind"}
        })))
        .expect(1)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .api_token(" token-1 ")
        .build()
        .unwrap();

    let health = client.health().await.unwrap();
    assert_eq!(health.status, "ok");
    assert_eq!(health.service, "memind");
}

#[tokio::test]
async fn health_sends_authorization_and_user_agent_headers() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .and(header("authorization", "Bearer token-1"))
        .and(header("user-agent", "memind-rust/test-suite"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "data": {"status": "ok", "service": "memind"}
        })))
        .expect(1)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .api_token(" token-1 ")
        .user_agent("memind-rust/test-suite")
        .build()
        .unwrap();

    client.health().await.unwrap();
}

#[tokio::test]
async fn blank_api_token_does_not_send_authorization_header() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .and(header("authorization", "Bearer "))
        .respond_with(ResponseTemplate::new(500))
        .expect(0)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "data": {"status": "ok", "service": "memind"}
        })))
        .expect(1)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .api_token("   ")
        .build()
        .unwrap();

    client.health().await.unwrap();
}

#[tokio::test]
async fn base_url_path_prefix_is_preserved() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/gateway/open/v1/health"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "data": {"status": "ok", "service": "memind"}
        })))
        .expect(1)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(format!("{}/gateway", server.uri()))
        .build()
        .unwrap();

    client.health().await.unwrap();
}

#[tokio::test]
async fn api_error_envelope_preserves_request_id() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .respond_with(
            ResponseTemplate::new(401)
                .append_header("X-Request-Id", "rid-1")
                .set_body_json(json!({
                    "error": {
                        "code": "unauthorized",
                        "message": "missing token",
                        "details": {"scope": "open"}
                    }
                })),
        )
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client.health().await.unwrap_err();
    match err {
        MemindError::Api(api) => {
            assert_eq!(api.status, 401);
            assert_eq!(api.code, "unauthorized");
            assert_eq!(api.request_id.as_deref(), Some("rid-1"));
        }
        other => panic!("unexpected error: {other:?}"),
    }
}

#[tokio::test]
async fn request_options_rejects_zero_timeout_at_send_time() {
    let client = MemindClient::builder()
        .base_url("https://api.example.test")
        .build()
        .unwrap();

    let err = client
        .health_with_options(RequestOptions::new().timeout(Duration::ZERO))
        .await
        .unwrap_err();

    assert!(matches!(err, MemindError::InvalidRequest { .. }));
}

#[tokio::test]
async fn malformed_json_returns_decode_error() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .respond_with(ResponseTemplate::new(200).set_body_string("{bad-json"))
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client.health().await.unwrap_err();
    assert!(matches!(err, MemindError::Decode { .. }));
}

#[tokio::test]
async fn non_json_response_returns_decode_error() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .respond_with(ResponseTemplate::new(200).set_body_string("not json"))
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client.health().await.unwrap_err();
    assert!(matches!(err, MemindError::Decode { .. }));
}

#[tokio::test]
async fn non_json_error_response_returns_http_api_error() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .respond_with(
            ResponseTemplate::new(502)
                .append_header("X-Request-Id", "rid-502")
                .set_body_string("bad gateway"),
        )
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client.health().await.unwrap_err();
    match err {
        MemindError::Api(api) => {
            assert_eq!(api.status, 502);
            assert_eq!(api.code, "http_error");
            assert_eq!(api.request_id.as_deref(), Some("rid-502"));
        }
        other => panic!("unexpected error: {other:?}"),
    }
}

#[tokio::test]
async fn memory_methods_call_expected_endpoints() {
    let server = MockServer::start().await;
    for (endpoint, data) in [
        ("/open/v1/memory/sync/extract", json!({"status": "SUCCESS"})),
        (
            "/open/v1/memory/sync/add-message",
            json!({"triggered": false}),
        ),
        ("/open/v1/memory/sync/commit", json!({"status": "SUCCESS"})),
        (
            "/open/v1/memory/retrieve",
            json!({"items": [], "insights": [], "rawData": [], "evidences": []}),
        ),
        (
            "/open/v1/memory/items/query",
            json!({"items": [{"id": "101", "text": "Run targeted tests.", "rawDataType": "agent_timeline", "sourceClient": "claude-code", "metadata": {"project": "memind"}}], "nextCursor": "101"}),
        ),
        (
            "/open/v1/memory/raw-data/query",
            json!({"rawData": [{"id": "rd-1", "type": "agent_timeline", "sourceClient": "codex", "caption": "Fixed retry test.", "metadata": {"sessionId": "s1"}, "segment": {"events": []}}]}),
        ),
    ] {
        Mock::given(method("POST"))
            .and(path(endpoint))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({ "data": data })))
            .expect(1)
            .mount(&server)
            .await;
    }

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let raw = RawContent::conversation(vec![memind::Message::user("remember")]).unwrap();
    client
        .memory()
        .extract(ExtractMemoryRequest::new("u1", "a1", raw))
        .await
        .unwrap();
    client
        .memory()
        .add_message(AddMessageRequest::new(
            "u1",
            "a1",
            memind::Message::user("hello"),
        ))
        .await
        .unwrap();
    client
        .memory()
        .commit(CommitMemoryRequest::new("u1", "a1"))
        .await
        .unwrap();
    client
        .memory()
        .retrieve(RetrieveMemoryRequest::new(
            "u1",
            "a1",
            "what",
            memind::Strategy::Simple,
        ))
        .await
        .unwrap();
    let items = client
        .memory()
        .query_items(
            QueryMemoryItemsRequest::new("u1", "a1")
                .category("playbook")
                .source_client("claude-code")
                .raw_data_type("agent_timeline")
                .limit(10),
        )
        .await
        .unwrap();
    assert_eq!(
        items.items[0].raw_data_type.as_deref(),
        Some("agent_timeline")
    );
    assert_eq!(items.next_cursor.as_deref(), Some("101"));

    let raw_data = client
        .memory()
        .query_raw_data(
            QueryMemoryRawDataRequest::new("u1", "a1")
                .raw_data_type("agent_timeline")
                .source_client("codex"),
        )
        .await
        .unwrap();
    assert_eq!(raw_data.raw_data[0].id, "rd-1");
    assert!(raw_data.raw_data[0].segment.is_some());
}

#[tokio::test]
async fn partial_success_extract_is_successful_response() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/open/v1/memory/sync/extract"))
        .respond_with(ResponseTemplate::new(200).set_body_json(json!({
            "data": {
                "status": "PARTIAL_SUCCESS",
                "rawDataIds": ["r1"],
                "itemIds": [],
                "insightIds": [],
                "insightPending": true
            }
        })))
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let raw = RawContent::conversation(vec![memind::Message::user("remember")]).unwrap();
    let response = client
        .memory()
        .extract(ExtractMemoryRequest::new("u1", "a1", raw))
        .await
        .unwrap();

    assert_eq!(response.status, memind::ExtractStatus::PartialSuccess);
    assert!(response.insight_pending);
}

#[tokio::test]
async fn memory_methods_validate_identity_before_sending() {
    let server = MockServer::start().await;
    for endpoint in [
        "/open/v1/memory/sync/extract",
        "/open/v1/memory/sync/add-message",
        "/open/v1/memory/sync/commit",
        "/open/v1/memory/retrieve",
    ] {
        Mock::given(method("POST"))
            .and(path(endpoint))
            .respond_with(ResponseTemplate::new(500))
            .expect(0)
            .mount(&server)
            .await;
    }

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let raw = RawContent::conversation(vec![memind::Message::user("remember")]).unwrap();
    let extract_err = client
        .memory()
        .extract(ExtractMemoryRequest::new(" ", "a1", raw))
        .await
        .unwrap_err();
    assert!(matches!(extract_err, MemindError::InvalidRequest { .. }));

    let add_message_err = client
        .memory()
        .add_message(AddMessageRequest::new(
            "u1",
            " ",
            memind::Message::user("hello"),
        ))
        .await
        .unwrap_err();
    assert!(matches!(
        add_message_err,
        MemindError::InvalidRequest { .. }
    ));

    let commit_err = client
        .memory()
        .commit(CommitMemoryRequest::new("", "a1"))
        .await
        .unwrap_err();
    assert!(matches!(commit_err, MemindError::InvalidRequest { .. }));

    let retrieve_err = client
        .memory()
        .retrieve(RetrieveMemoryRequest::new(
            "u1",
            "a1",
            " ",
            memind::Strategy::Simple,
        ))
        .await
        .unwrap_err();
    assert!(matches!(retrieve_err, MemindError::InvalidRequest { .. }));
}
