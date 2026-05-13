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

use std::sync::Arc;
use std::time::Duration;

use reqwest::header::{HeaderMap, HeaderName, HeaderValue};

use crate::config::{
    normalize_base_url, normalize_token, reject_sdk_header, validate_timeout, ClientConfig,
    DEFAULT_MAX_RETRIES, DEFAULT_TIMEOUT, ENV_API_TOKEN, ENV_BASE_URL,
};
use crate::models::HealthResponse;
use crate::resources::MemoryClient;
use crate::{http, MemindError, RequestOptions, Result};

#[derive(Clone, Default)]
pub struct ClientBuilder {
    base_url: Option<String>,
    use_base_url_env: bool,
    api_token: Option<String>,
    use_api_token_env: bool,
    timeout: Option<Duration>,
    max_retries: Option<u32>,
    user_agent: Option<String>,
    extra_headers: HeaderMap,
    http_client: Option<reqwest::Client>,
}

impl std::fmt::Debug for ClientBuilder {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ClientBuilder")
            .field("base_url", &self.base_url)
            .field("use_base_url_env", &self.use_base_url_env)
            .field("api_token_configured", &self.api_token.is_some())
            .field("use_api_token_env", &self.use_api_token_env)
            .field("timeout", &self.timeout)
            .field("max_retries", &self.max_retries)
            .field("user_agent", &self.user_agent)
            .field("extra_header_count", &self.extra_headers.len())
            .field("custom_http_client", &self.http_client.is_some())
            .finish()
    }
}

#[derive(Clone)]
pub struct MemindClient {
    pub(crate) inner: Arc<ClientInner>,
    memory: MemoryClient,
}

impl std::fmt::Debug for MemindClient {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MemindClient")
            .field("base_url", &self.inner.config.base_url)
            .field(
                "api_token_configured",
                &self.inner.config.api_token.is_some(),
            )
            .finish_non_exhaustive()
    }
}

pub(crate) struct ClientInner {
    pub http_client: reqwest::Client,
    pub config: ClientConfig,
}

impl std::fmt::Debug for ClientInner {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ClientInner")
            .field("config", &self.config)
            .field("http_client", &"<reqwest::Client>")
            .finish()
    }
}

impl MemindClient {
    pub fn builder() -> ClientBuilder {
        ClientBuilder::default()
    }

    pub fn from_env() -> Result<Self> {
        Self::builder()
            .base_url_from_env()
            .api_token_from_env()
            .build()
    }

    pub fn base_url(&self) -> &str {
        &self.inner.config.base_url
    }

    pub fn memory(&self) -> &MemoryClient {
        &self.memory
    }

    pub async fn health(&self) -> Result<HealthResponse> {
        self.health_with_options(RequestOptions::new()).await
    }

    pub async fn health_with_options(&self, options: RequestOptions) -> Result<HealthResponse> {
        http::get_json(
            &self.inner,
            "/health",
            options,
            self.inner.config.max_retries,
        )
        .await
    }
}

impl ClientBuilder {
    pub fn base_url(mut self, base_url: impl Into<String>) -> Self {
        self.base_url = Some(base_url.into());
        self
    }

    pub fn base_url_from_env(mut self) -> Self {
        self.use_base_url_env = true;
        self
    }

    pub fn api_token(mut self, api_token: impl Into<String>) -> Self {
        self.api_token = Some(api_token.into());
        self
    }

    pub fn api_token_from_env(mut self) -> Self {
        self.use_api_token_env = true;
        self
    }

    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }

    pub fn max_retries(mut self, max_retries: u32) -> Self {
        self.max_retries = Some(max_retries);
        self
    }

    pub fn user_agent(mut self, user_agent: impl Into<String>) -> Self {
        self.user_agent = Some(user_agent.into());
        self
    }

    pub fn extra_header(mut self, name: HeaderName, value: HeaderValue) -> Result<Self> {
        reject_sdk_header(&name).map_err(MemindError::config)?;
        self.extra_headers.insert(name, value);
        Ok(self)
    }

    pub fn http_client(mut self, client: reqwest::Client) -> Self {
        self.http_client = Some(client);
        self
    }

    pub fn build(self) -> Result<MemindClient> {
        let base_url_source = self
            .base_url
            .or_else(|| {
                self.use_base_url_env
                    .then(|| std::env::var(ENV_BASE_URL).unwrap_or_default())
            })
            .unwrap_or_else(|| std::env::var(ENV_BASE_URL).unwrap_or_default());
        let (base_url, api_base_url) = normalize_base_url(base_url_source)?;

        let api_token_source = self
            .api_token
            .or_else(|| {
                self.use_api_token_env
                    .then(|| std::env::var(ENV_API_TOKEN).ok())
                    .flatten()
            })
            .or_else(|| std::env::var(ENV_API_TOKEN).ok());
        let api_token = normalize_token(api_token_source);

        let user_agent = self
            .user_agent
            .map(|value| value.trim().to_string())
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| format!("memind-rust/{}", env!("CARGO_PKG_VERSION")));

        let config = ClientConfig {
            base_url,
            api_base_url,
            api_token,
            timeout: validate_timeout(self.timeout.unwrap_or(DEFAULT_TIMEOUT), "client timeout")
                .map_err(MemindError::config)?,
            max_retries: self.max_retries.unwrap_or(DEFAULT_MAX_RETRIES),
            user_agent,
            extra_headers: self.extra_headers,
        };

        let http_client = match self.http_client {
            Some(client) => client,
            None => reqwest::Client::builder()
                .timeout(config.timeout)
                .build()
                .map_err(|err| MemindError::Config {
                    message: "failed to build HTTP client".to_string(),
                    source: Some(Box::new(err)),
                })?,
        };

        let inner = Arc::new(ClientInner {
            http_client,
            config,
        });
        let memory = MemoryClient::new(Arc::clone(&inner));
        Ok(MemindClient { inner, memory })
    }
}
