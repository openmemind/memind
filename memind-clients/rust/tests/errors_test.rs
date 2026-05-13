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

use memind::MemindClient;
use serde_json::json;
use wiremock::matchers::{method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

#[tokio::test]
async fn api_error_variant_can_be_matched_from_integration_tests() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/open/v1/health"))
        .respond_with(ResponseTemplate::new(400).set_body_json(json!({
            "error": {
                "code": "invalid_request",
                "message": "user_id must be non-blank"
            }
        })))
        .mount(&server)
        .await;

    let client = MemindClient::builder()
        .base_url(server.uri())
        .build()
        .unwrap();
    let err = client.health().await.unwrap_err();

    match err {
        memind::MemindError::Api(api_error) => assert_eq!(api_error.status, 400),
        other => panic!("unexpected error: {other:?}"),
    }
}
