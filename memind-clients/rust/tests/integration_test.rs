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
    ExtractMemoryRequest, MemindClient, Message, RawContent, RetrieveMemoryRequest, Strategy,
};

fn integration_client() -> Option<MemindClient> {
    std::env::var("MEMIND_BASE_URL").ok().and_then(|base_url| {
        MemindClient::builder()
            .base_url(base_url)
            .api_token_from_env()
            .build()
            .ok()
    })
}

#[tokio::test]
async fn real_server_health_when_configured() {
    let Some(client) = integration_client() else {
        eprintln!("skipping integration test because MEMIND_BASE_URL is not set");
        return;
    };

    let health = client.health().await.expect("health");
    assert!(!health.status.is_empty());
}

#[tokio::test]
async fn real_server_extract_retrieve_when_enabled() {
    if std::env::var("MEMIND_FULL_INTEGRATION").ok().as_deref() != Some("1") {
        eprintln!("skipping full integration test because MEMIND_FULL_INTEGRATION=1 is not set");
        return;
    }
    let Some(client) = integration_client() else {
        eprintln!("skipping full integration test because MEMIND_BASE_URL is not set");
        return;
    };

    let unique = format!(
        "rust-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    );
    let raw = RawContent::conversation(vec![Message::user("I prefer concise answers.")]).unwrap();
    let extraction = client
        .memory()
        .extract(ExtractMemoryRequest::new(&unique, "agent-rust", raw))
        .await
        .expect("extract");

    assert!(matches!(
        extraction.status,
        memind::ExtractStatus::Success | memind::ExtractStatus::PartialSuccess
    ));

    let retrieved = client
        .memory()
        .retrieve(RetrieveMemoryRequest::new(
            &unique,
            "agent-rust",
            "What does the user prefer?",
            Strategy::Simple,
        ))
        .await
        .expect("retrieve");

    let _ = retrieved;
}
