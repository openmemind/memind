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

use std::error::Error;
use std::time::Duration;

pub type Result<T> = std::result::Result<T, MemindError>;

#[derive(Debug)]
#[non_exhaustive]
pub enum MemindError {
    #[non_exhaustive]
    Config {
        message: String,
        source: Option<Box<dyn Error + Send + Sync>>,
    },
    #[non_exhaustive]
    InvalidRequest {
        message: String,
    },
    Api(Box<MemindApiError>),
    #[non_exhaustive]
    Timeout {
        message: String,
        source: Option<Box<dyn Error + Send + Sync>>,
    },
    #[non_exhaustive]
    Connection {
        message: String,
        source: Option<Box<dyn Error + Send + Sync>>,
    },
    #[non_exhaustive]
    Decode {
        message: String,
        body: Option<String>,
        source: Option<Box<dyn Error + Send + Sync>>,
    },
}

impl std::fmt::Display for MemindError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Config { message, .. } => write!(f, "configuration error: {message}"),
            Self::InvalidRequest { message } => write!(f, "invalid request: {message}"),
            Self::Api(error) => write!(f, "memind api error: {error}"),
            Self::Timeout { message, .. } => write!(f, "request timed out: {message}"),
            Self::Connection { message, .. } => write!(f, "connection error: {message}"),
            Self::Decode { message, .. } => write!(f, "decode error: {message}"),
        }
    }
}

impl Error for MemindError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        match self {
            Self::Config { source, .. }
            | Self::Timeout { source, .. }
            | Self::Connection { source, .. }
            | Self::Decode { source, .. } => source
                .as_deref()
                .map(|source| source as &(dyn Error + 'static)),
            Self::Api(error) => Some(error.as_ref()),
            Self::InvalidRequest { .. } => None,
        }
    }
}

impl MemindError {
    pub(crate) fn config(message: impl Into<String>) -> Self {
        Self::Config {
            message: message.into(),
            source: None,
        }
    }

    pub(crate) fn invalid_request(message: impl Into<String>) -> Self {
        Self::InvalidRequest {
            message: message.into(),
        }
    }
}

#[derive(Debug, Clone, thiserror::Error)]
#[error("status={status} code={code} message={message}")]
#[non_exhaustive]
pub struct MemindApiError {
    pub status: u16,
    pub code: String,
    pub message: String,
    pub details: Option<serde_json::Value>,
    pub request_id: Option<String>,
    pub retry_after: Option<Duration>,
}

impl MemindApiError {
    pub fn is_authentication_error(&self) -> bool {
        self.status == 401 || self.status == 403
    }

    pub fn is_rate_limit_error(&self) -> bool {
        self.status == 429
    }

    pub fn is_retryable(&self) -> bool {
        matches!(self.status, 408 | 429 | 500 | 502 | 503 | 504)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn api_error_helpers_classify_common_statuses() {
        let auth = MemindApiError {
            status: 401,
            code: "unauthorized".to_string(),
            message: "missing token".to_string(),
            details: None,
            request_id: Some("rid-auth".to_string()),
            retry_after: None,
        };
        assert!(auth.is_authentication_error());
        assert!(!auth.is_rate_limit_error());
        assert!(!auth.is_retryable());

        let rate = MemindApiError {
            status: 429,
            code: "rate_limited".to_string(),
            message: "slow down".to_string(),
            details: None,
            request_id: Some("rid-rate".to_string()),
            retry_after: Some(Duration::from_secs(3)),
        };
        assert!(!rate.is_authentication_error());
        assert!(rate.is_rate_limit_error());
        assert!(rate.is_retryable());
    }

    #[test]
    fn boxed_source_is_exposed_through_error_source() {
        let err = MemindError::Connection {
            message: "transport failed".to_string(),
            source: Some(Box::new(std::io::Error::new(
                std::io::ErrorKind::ConnectionReset,
                "reset",
            ))),
        };

        assert!(err.source().is_some());
        assert!(err.to_string().contains("transport failed"));
    }

    #[test]
    fn non_exhaustive_struct_variants_can_be_matched_with_rest_inside_crate() {
        let err = MemindError::InvalidRequest {
            message: "user_id must be non-blank".to_string(),
        };

        match err {
            MemindError::InvalidRequest { message, .. } => {
                assert_eq!(message, "user_id must be non-blank");
            }
            other => panic!("unexpected error: {other:?}"),
        }
    }
}
