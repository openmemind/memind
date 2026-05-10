#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

from __future__ import annotations

from typing import Any


class MemindError(Exception):
    """Base exception for all memind client errors."""


class MemindAPIError(MemindError):
    """Raised when the API returns a non-success response."""

    def __init__(
        self,
        message: str,
        *,
        status_code: int,
        error_code: str | None = None,
        trace_id: str | None = None,
        body: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.error_code = error_code
        self.trace_id = trace_id
        self.body = body


class MemindAuthenticationError(MemindAPIError):
    """Raised on 401 Unauthorized responses."""


class MemindRateLimitError(MemindAPIError):
    """Raised on 429 Too Many Requests responses."""

    def __init__(
        self,
        message: str,
        *,
        status_code: int = 429,
        error_code: str | None = None,
        trace_id: str | None = None,
        body: dict[str, Any] | None = None,
        retry_after: float | None = None,
    ) -> None:
        super().__init__(
            message,
            status_code=status_code,
            error_code=error_code,
            trace_id=trace_id,
            body=body,
        )
        self.retry_after = retry_after


class MemindConnectionError(MemindError):
    """Raised when a network connection cannot be established."""


class MemindTimeoutError(MemindError):
    """Raised when a request times out."""
