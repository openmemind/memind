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

use std::time::{Duration, SystemTime};

#[derive(Clone, Debug)]
pub struct RetryPolicy {
    pub max_retries: u32,
    pub initial_delay: Duration,
    pub max_delay: Duration,
    pub jitter_ratio: f64,
}

impl RetryPolicy {
    pub fn new(max_retries: u32) -> Self {
        Self {
            max_retries,
            initial_delay: Duration::from_millis(500),
            max_delay: Duration::from_secs(2),
            jitter_ratio: 0.25,
        }
    }
}

pub fn is_retryable_status(status: u16) -> bool {
    matches!(status, 408 | 429 | 500 | 502 | 503 | 504)
}

pub fn parse_retry_after(value: &str, now: SystemTime) -> Option<Duration> {
    if let Ok(seconds) = value.trim().parse::<u64>() {
        return Some(Duration::from_secs(seconds));
    }
    let retry_at = httpdate::parse_http_date(value.trim()).ok()?;
    retry_at.duration_since(now).ok()
}

pub fn compute_delay(policy: &RetryPolicy, attempt: u32) -> Duration {
    let multiplier = 2u32.saturating_pow(attempt);
    let base = policy.initial_delay.saturating_mul(multiplier);
    base.min(policy.max_delay)
}

pub(crate) fn apply_jitter(delay: Duration, ratio: f64) -> Duration {
    if ratio <= 0.0 {
        return delay;
    }
    let millis = delay.as_millis() as f64;
    let spread = millis * ratio;
    let low = (millis - spread).max(0.0);
    let high = millis + spread;
    let sampled = fastrand::f64() * (high - low) + low;
    Duration::from_millis(sampled.round() as u64)
}

#[cfg(test)]
mod tests {
    use super::{compute_delay, is_retryable_status, parse_retry_after, RetryPolicy};
    use std::time::{Duration, SystemTime};

    #[test]
    fn retryable_statuses_match_spec() {
        for status in [408, 429, 500, 502, 503, 504] {
            assert!(is_retryable_status(status), "status {status}");
        }
        for status in [400, 401, 403, 404, 409, 422] {
            assert!(!is_retryable_status(status), "status {status}");
        }
    }

    #[test]
    fn retry_after_parses_delta_seconds_and_http_date() {
        let now = SystemTime::UNIX_EPOCH + Duration::from_secs(1_700_000_000);
        let later = httpdate::fmt_http_date(now + Duration::from_secs(5));

        assert_eq!(parse_retry_after("3", now), Some(Duration::from_secs(3)));
        assert_eq!(parse_retry_after(&later, now), Some(Duration::from_secs(5)));
        assert_eq!(parse_retry_after("", now), None);
        assert_eq!(parse_retry_after("not a date", now), None);
    }

    #[test]
    fn compute_delay_caps_exponential_backoff() {
        let policy = RetryPolicy {
            max_retries: 2,
            initial_delay: Duration::from_millis(500),
            max_delay: Duration::from_secs(2),
            jitter_ratio: 0.0,
        };

        assert_eq!(compute_delay(&policy, 0), Duration::from_millis(500));
        assert_eq!(compute_delay(&policy, 1), Duration::from_secs(1));
        assert_eq!(compute_delay(&policy, 3), Duration::from_secs(2));
    }
}
