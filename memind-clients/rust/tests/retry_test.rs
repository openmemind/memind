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
    AddMessageRequest, CommitMemoryRequest, ExtractMemoryRequest, MemindClient, RawContent,
    RequestOptions, RetrieveMemoryRequest,
};
use serde_json::json;
use wiremock::matchers::{method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

#[tokio::test]
async fn retrieve_retries_on_503_by_default() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/open/v1/memory/retrieve"))
        .respond_with(
            ResponseTemplate::new(503)
                .append_header("Retry-After", "0")
                .set_body_json(json!({
                    "error": {"code": "unavailable", "message": "try again"}
                })),
        )
        .expect(3)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client
        .memory()
        .retrieve(RetrieveMemoryRequest::new(
            "u1",
            "a1",
            "what",
            memind::Strategy::Simple,
        ))
        .await
        .unwrap_err();
    assert!(matches!(err, memind::MemindError::Api(_)));
}

#[tokio::test]
async fn health_retries_on_503_by_default() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .respond_with(
            ResponseTemplate::new(503)
                .append_header("Retry-After", "0")
                .set_body_json(json!({
                    "error": {"code": "unavailable", "message": "try again"}
                })),
        )
        .expect(3)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client.health().await.unwrap_err();
    assert!(matches!(err, memind::MemindError::Api(_)));
}

#[tokio::test]
async fn add_message_does_not_retry_by_default() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/open/v1/memory/sync/add-message"))
        .respond_with(ResponseTemplate::new(503).set_body_json(json!({
            "error": {"code": "unavailable", "message": "try again"}
        })))
        .expect(1)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client
        .memory()
        .add_message(AddMessageRequest::new(
            "u1",
            "a1",
            memind::Message::user("hello"),
        ))
        .await
        .unwrap_err();
    assert!(matches!(err, memind::MemindError::Api(_)));
}

#[tokio::test]
async fn extract_and_commit_do_not_retry_by_default() {
    let server = MockServer::start().await;
    for endpoint in [
        "/open/v1/memory/sync/extract",
        "/open/v1/memory/sync/commit",
    ] {
        Mock::given(method("POST"))
            .and(path(endpoint))
            .respond_with(ResponseTemplate::new(503).set_body_json(json!({
                "error": {"code": "unavailable", "message": "try again"}
            })))
            .expect(1)
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
        .extract(ExtractMemoryRequest::new("u1", "a1", raw))
        .await
        .unwrap_err();
    assert!(matches!(extract_err, memind::MemindError::Api(_)));

    let commit_err = client
        .memory()
        .commit(CommitMemoryRequest::new("u1", "a1"))
        .await
        .unwrap_err();
    assert!(matches!(commit_err, memind::MemindError::Api(_)));
}

#[tokio::test]
async fn mutating_operation_retries_when_per_request_max_retries_is_set() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/open/v1/memory/sync/commit"))
        .respond_with(
            ResponseTemplate::new(503)
                .append_header("Retry-After", "0")
                .set_body_json(json!({
                    "error": {"code": "unavailable", "message": "try again"}
                })),
        )
        .expect(2)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client
        .memory()
        .commit_with_options(
            CommitMemoryRequest::new("u1", "a1"),
            RequestOptions::new().max_retries(1),
        )
        .await
        .unwrap_err();
    assert!(matches!(err, memind::MemindError::Api(_)));
}

#[tokio::test]
async fn per_request_max_retries_zero_disables_retrieve_retry() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/open/v1/memory/retrieve"))
        .respond_with(ResponseTemplate::new(503).set_body_json(json!({
            "error": {"code": "unavailable", "message": "try again"}
        })))
        .expect(1)
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client
        .memory()
        .retrieve_with_options(
            RetrieveMemoryRequest::new("u1", "a1", "what", memind::Strategy::Simple),
            RequestOptions::new().max_retries(0),
        )
        .await
        .unwrap_err();
    assert!(matches!(err, memind::MemindError::Api(_)));
}
