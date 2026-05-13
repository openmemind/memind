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

use std::time::SystemTime;

use reqwest::header::{
    HeaderMap, HeaderValue, ACCEPT, AUTHORIZATION, CONTENT_TYPE, RETRY_AFTER, USER_AGENT,
};
use reqwest::{Method, StatusCode};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use url::Url;

use crate::client::ClientInner;
use crate::config::{reject_sdk_header, validate_timeout};
use crate::retry::{
    apply_jitter, compute_delay, is_retryable_status, parse_retry_after, RetryPolicy,
};
use crate::{MemindApiError, MemindError, RequestOptions, Result};

#[cfg(feature = "tracing")]
macro_rules! trace_debug {
    ($($arg:tt)*) => {
        tracing::debug!($($arg)*);
    };
}

#[cfg(not(feature = "tracing"))]
macro_rules! trace_debug {
    ($($arg:tt)*) => {};
}

#[cfg(feature = "tracing")]
macro_rules! trace_warn {
    ($($arg:tt)*) => {
        tracing::warn!($($arg)*);
    };
}

#[cfg(not(feature = "tracing"))]
macro_rules! trace_warn {
    ($($arg:tt)*) => {};
}

pub(crate) async fn get_json<TResp>(
    inner: &ClientInner,
    path: &str,
    options: RequestOptions,
    default_retries: u32,
) -> Result<TResp>
where
    TResp: DeserializeOwned,
{
    send_json::<(), TResp>(inner, Method::GET, path, None, options, default_retries).await
}

pub(crate) async fn post_json<TReq, TResp>(
    inner: &ClientInner,
    path: &str,
    body: &TReq,
    options: RequestOptions,
    default_retries: u32,
) -> Result<TResp>
where
    TReq: Serialize + ?Sized,
    TResp: DeserializeOwned,
{
    send_json(
        inner,
        Method::POST,
        path,
        Some(body),
        options,
        default_retries,
    )
    .await
}

async fn send_json<TReq, TResp>(
    inner: &ClientInner,
    method: Method,
    path: &str,
    body: Option<&TReq>,
    options: RequestOptions,
    default_retries: u32,
) -> Result<TResp>
where
    TReq: Serialize + ?Sized,
    TResp: DeserializeOwned,
{
    let url = build_url(inner, path)?;
    let headers = build_headers(inner, body.is_some(), &options)?;
    let max_retries = options.max_retries_override().unwrap_or(default_retries);
    let timeout = validate_timeout(
        options.timeout_override().unwrap_or(inner.config.timeout),
        "request timeout",
    )
    .map_err(MemindError::invalid_request)?;
    let policy = RetryPolicy::new(max_retries);
    let mut attempt = 0;

    loop {
        trace_debug!(
            method = %method,
            url = %url,
            attempt = attempt,
            "sending memind request"
        );
        let mut request = inner
            .http_client
            .request(method.clone(), url.clone())
            .headers(headers.clone())
            .timeout(timeout);

        if let Some(body) = body {
            request = request.json(body);
        }

        match request.send().await {
            Ok(response) => {
                let status = response.status();
                trace_debug!(
                    status = status.as_u16(),
                    attempt = attempt,
                    "received memind response"
                );
                let headers = response.headers().clone();
                let retry_after = headers
                    .get(RETRY_AFTER)
                    .and_then(|value| value.to_str().ok())
                    .and_then(|value| parse_retry_after(value, SystemTime::now()));
                let text = response.text().await.map_err(reqwest_error_to_memind)?;

                if attempt < policy.max_retries && is_retryable_status(status.as_u16()) {
                    trace_warn!(
                        status = status.as_u16(),
                        attempt = attempt,
                        "retrying memind response"
                    );
                    sleep_before_retry(&policy, attempt, retry_after).await;
                    attempt += 1;
                    continue;
                }

                return parse_response(status, &headers, text);
            }
            Err(err) => {
                let retryable_transport = err.is_timeout() || err.is_connect();
                if attempt < policy.max_retries && retryable_transport {
                    trace_warn!(
                        error = %err,
                        attempt = attempt,
                        "retrying memind transport error"
                    );
                    sleep_before_retry(&policy, attempt, None).await;
                    attempt += 1;
                    continue;
                }
                return Err(reqwest_error_to_memind(err));
            }
        }
    }
}

pub(crate) fn build_url(inner: &ClientInner, path: &str) -> Result<Url> {
    let normalized_path = path.trim_start_matches('/');
    inner
        .config
        .api_base_url
        .join(normalized_path)
        .map_err(|err| MemindError::Config {
            message: format!(
                "failed to join base_url {} with request path {path}",
                inner.config.base_url
            ),
            source: Some(Box::new(err)),
        })
}

fn build_headers(
    inner: &ClientInner,
    has_body: bool,
    options: &RequestOptions,
) -> Result<HeaderMap> {
    validate_user_headers(&inner.config.extra_headers)?;
    validate_user_headers(options.header_overrides())?;

    let mut headers = HeaderMap::new();
    headers.insert(ACCEPT, HeaderValue::from_static("application/json"));
    headers.insert(
        USER_AGENT,
        HeaderValue::from_str(&inner.config.user_agent).map_err(|err| {
            MemindError::invalid_request(format!("invalid user agent header: {err}"))
        })?,
    );
    if has_body {
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));
    }
    if let Some(token) = &inner.config.api_token {
        headers.insert(
            AUTHORIZATION,
            HeaderValue::from_str(&format!("Bearer {token}")).map_err(|err| {
                MemindError::invalid_request(format!("invalid authorization header: {err}"))
            })?,
        );
    }
    for (name, value) in &inner.config.extra_headers {
        headers.insert(name.clone(), value.clone());
    }
    for (name, value) in options.header_overrides() {
        headers.insert(name.clone(), value.clone());
    }
    Ok(headers)
}

fn validate_user_headers(headers: &HeaderMap) -> Result<()> {
    for name in headers.keys() {
        reject_sdk_header(name).map_err(MemindError::invalid_request)?;
    }
    Ok(())
}

fn parse_response<TResp>(status: StatusCode, headers: &HeaderMap, text: String) -> Result<TResp>
where
    TResp: DeserializeOwned,
{
    let envelope: ApiEnvelope<serde_json::Value> = match serde_json::from_str(&text) {
        Ok(envelope) => envelope,
        Err(err) if status.is_success() => {
            return Err(MemindError::Decode {
                message: "failed to parse response envelope".to_string(),
                body: Some(text),
                source: Some(Box::new(err)),
            });
        }
        Err(_) => return Err(MemindError::Api(Box::new(http_error(status, headers)))),
    };

    if let Some(error) = envelope.error {
        return Err(MemindError::Api(Box::new(api_error_from_payload(
            status, headers, error,
        ))));
    }

    if status.is_success() {
        let data = envelope.data.ok_or_else(|| MemindError::Decode {
            message: "response envelope missing data".to_string(),
            body: Some(text.clone()),
            source: None,
        })?;
        return serde_json::from_value(data).map_err(|err| MemindError::Decode {
            message: "failed to decode response data".to_string(),
            body: Some(text),
            source: Some(Box::new(err)),
        });
    }

    Err(MemindError::Api(Box::new(http_error(status, headers))))
}

fn api_error_from_payload(
    status: StatusCode,
    headers: &HeaderMap,
    payload: ApiErrorPayload,
) -> MemindApiError {
    MemindApiError {
        status: status.as_u16(),
        code: payload.code,
        message: payload.message,
        details: payload.details,
        request_id: header_to_string(headers, "x-request-id"),
        retry_after: retry_after(headers),
    }
}

fn http_error(status: StatusCode, headers: &HeaderMap) -> MemindApiError {
    MemindApiError {
        status: status.as_u16(),
        code: "http_error".to_string(),
        message: status
            .canonical_reason()
            .unwrap_or("HTTP request failed")
            .to_string(),
        details: None,
        request_id: header_to_string(headers, "x-request-id"),
        retry_after: retry_after(headers),
    }
}

fn header_to_string(headers: &HeaderMap, name: &'static str) -> Option<String> {
    headers
        .get(name)
        .and_then(|value| value.to_str().ok())
        .map(ToOwned::to_owned)
}

fn retry_after(headers: &HeaderMap) -> Option<std::time::Duration> {
    headers
        .get(RETRY_AFTER)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| parse_retry_after(value, SystemTime::now()))
}

async fn sleep_before_retry(
    policy: &RetryPolicy,
    attempt: u32,
    retry_after: Option<std::time::Duration>,
) {
    let delay = retry_after
        .unwrap_or_else(|| apply_jitter(compute_delay(policy, attempt), policy.jitter_ratio));
    if !delay.is_zero() {
        tokio::time::sleep(delay).await;
    }
}

fn reqwest_error_to_memind(err: reqwest::Error) -> MemindError {
    if err.is_timeout() {
        MemindError::Timeout {
            message: err.to_string(),
            source: Some(Box::new(err)),
        }
    } else {
        MemindError::Connection {
            message: err.to_string(),
            source: Some(Box::new(err)),
        }
    }
}

#[derive(Debug, Deserialize)]
struct ApiEnvelope<T> {
    #[serde(default)]
    data: Option<T>,
    #[serde(default)]
    error: Option<ApiErrorPayload>,
}

#[derive(Debug, Deserialize)]
struct ApiErrorPayload {
    code: String,
    message: String,
    #[serde(default)]
    details: Option<serde_json::Value>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::TcpListener;
    use std::time::Duration;

    #[tokio::test]
    async fn reqwest_timeout_maps_to_timeout_error() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let addr = listener.local_addr().unwrap();
        std::thread::spawn(move || {
            let (_stream, _) = listener.accept().unwrap();
            std::thread::sleep(Duration::from_secs(2));
        });

        let err = reqwest::Client::builder()
            .timeout(Duration::from_millis(25))
            .build()
            .unwrap()
            .get(format!("http://{addr}"))
            .send()
            .await
            .unwrap_err();

        assert!(matches!(
            reqwest_error_to_memind(err),
            MemindError::Timeout { .. }
        ));
    }

    #[tokio::test]
    async fn reqwest_non_timeout_maps_to_connection_error() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let addr = listener.local_addr().unwrap();
        drop(listener);

        let err = reqwest::Client::new()
            .get(format!("http://{addr}"))
            .send()
            .await
            .unwrap_err();

        assert!(!err.is_timeout());
        assert!(matches!(
            reqwest_error_to_memind(err),
            MemindError::Connection { .. }
        ));
    }
}
