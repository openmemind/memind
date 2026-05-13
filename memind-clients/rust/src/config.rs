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

use std::time::Duration;

use reqwest::header::{
    HeaderMap, HeaderName, HeaderValue, ACCEPT, AUTHORIZATION, CONTENT_TYPE, USER_AGENT,
};
use url::Url;

use crate::{MemindError, Result};

pub(crate) const ENV_BASE_URL: &str = "MEMIND_BASE_URL";
pub(crate) const ENV_API_TOKEN: &str = "MEMIND_API_TOKEN";
pub(crate) const DEFAULT_TIMEOUT: Duration = Duration::from_secs(30);
pub(crate) const DEFAULT_MAX_RETRIES: u32 = 2;

#[derive(Clone)]
pub(crate) struct ClientConfig {
    pub base_url: String,
    pub api_base_url: Url,
    pub api_token: Option<String>,
    pub timeout: Duration,
    pub max_retries: u32,
    pub user_agent: String,
    pub extra_headers: HeaderMap,
}

impl std::fmt::Debug for ClientConfig {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ClientConfig")
            .field("base_url", &self.base_url)
            .field("api_token_configured", &self.api_token.is_some())
            .field("timeout", &self.timeout)
            .field("max_retries", &self.max_retries)
            .field("user_agent", &self.user_agent)
            .field("extra_header_count", &self.extra_headers.len())
            .finish()
    }
}

#[derive(Clone, Default)]
pub struct RequestOptions {
    timeout: Option<Duration>,
    max_retries: Option<u32>,
    headers: HeaderMap,
}

impl std::fmt::Debug for RequestOptions {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("RequestOptions")
            .field("timeout", &self.timeout)
            .field("max_retries", &self.max_retries)
            .field("header_count", &self.headers.len())
            .finish()
    }
}

impl RequestOptions {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }

    pub fn max_retries(mut self, max_retries: u32) -> Self {
        self.max_retries = Some(max_retries);
        self
    }

    pub fn header(mut self, name: HeaderName, value: HeaderValue) -> Result<Self> {
        reject_sdk_header(&name).map_err(MemindError::invalid_request)?;
        self.headers.insert(name, value);
        Ok(self)
    }

    pub(crate) fn timeout_override(&self) -> Option<Duration> {
        self.timeout
    }

    pub(crate) fn max_retries_override(&self) -> Option<u32> {
        self.max_retries
    }

    pub(crate) fn header_overrides(&self) -> &HeaderMap {
        &self.headers
    }
}

pub(crate) fn normalize_base_url(value: impl Into<String>) -> Result<(String, Url)> {
    let value = value.into();
    let trimmed = value.trim().trim_end_matches('/').to_string();
    if trimmed.is_empty() {
        return Err(MemindError::config(
            "base_url is required; pass base_url or set MEMIND_BASE_URL",
        ));
    }
    let parsed = Url::parse(&trimmed).map_err(|err| MemindError::Config {
        message: format!("invalid base_url {trimmed}"),
        source: Some(Box::new(err)),
    })?;
    if parsed.query().is_some() || parsed.fragment().is_some() {
        return Err(MemindError::config(
            "base_url must not include query parameters or fragments",
        ));
    }

    let mut api_base_url = parsed.clone();
    let mut path = api_base_url.path().trim_end_matches('/').to_string();
    path.push_str("/open/v1/");
    api_base_url.set_path(&path);
    Ok((trimmed, api_base_url))
}

pub(crate) fn normalize_token(value: Option<String>) -> Option<String> {
    value.and_then(|token| {
        let trimmed = token.trim().to_string();
        (!trimmed.is_empty()).then_some(trimmed)
    })
}

pub(crate) fn reject_sdk_header(name: &HeaderName) -> std::result::Result<(), String> {
    let name = name.as_str();
    if name.eq_ignore_ascii_case(ACCEPT.as_str())
        || name.eq_ignore_ascii_case(CONTENT_TYPE.as_str())
        || name.eq_ignore_ascii_case(AUTHORIZATION.as_str())
        || name.eq_ignore_ascii_case(USER_AGENT.as_str())
    {
        Err(format!("header {name} is owned by the Memind SDK"))
    } else {
        Ok(())
    }
}

pub(crate) fn validate_timeout(
    timeout: Duration,
    label: &'static str,
) -> std::result::Result<Duration, String> {
    if timeout.is_zero() {
        Err(format!("{label} must be greater than zero"))
    } else {
        Ok(timeout)
    }
}
