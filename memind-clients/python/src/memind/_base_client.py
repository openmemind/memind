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

import os
from typing import Any, TypeVar

import httpx
from pydantic import TypeAdapter, ValidationError

from memind._constants import (
    API_PREFIX,
    DEFAULT_CONNECT_TIMEOUT,
    DEFAULT_MAX_RETRIES,
    DEFAULT_READ_TIMEOUT,
    ENV_API_TOKEN,
    ENV_BASE_URL,
)
from memind._exceptions import (
    MemindAPIError,
    MemindAuthenticationError,
    MemindError,
    MemindRateLimitError,
)
from memind._http import RetryConfig, _retry_after_delay
from memind._version import __version__
from memind.types.common import ApiResult

T = TypeVar("T")


class BaseClient:
    def __init__(
        self,
        *,
        base_url: str | None = None,
        api_token: str | None = None,
        timeout: float | httpx.Timeout | None = None,
        max_retries: int = DEFAULT_MAX_RETRIES,
    ) -> None:
        resolved_base_url = base_url if base_url is not None else os.getenv(ENV_BASE_URL)
        if resolved_base_url is None or not resolved_base_url.strip():
            raise MemindError("base_url is required; pass base_url or set MEMIND_BASE_URL")
        if max_retries < 0:
            raise MemindError("max_retries must be non-negative")

        self._base_url = resolved_base_url.strip().rstrip("/")
        self._api_token = _normalize_token(
            api_token if api_token is not None else os.getenv(ENV_API_TOKEN)
        )
        self._timeout = _normalize_timeout(timeout)
        self._retry_config = RetryConfig(max_retries=max_retries)
        self._closed = False

    def _build_url(self, path: str) -> str:
        normalized_path = path if path.startswith("/") else f"/{path}"
        return f"{self._base_url}{API_PREFIX}{normalized_path}"

    def _build_headers(self) -> dict[str, str]:
        headers = {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "User-Agent": f"memind-python/{__version__}",
        }
        if self._api_token is not None:
            headers["Authorization"] = f"Bearer {self._api_token}"
        return headers

    def _serialize_body(self, body: Any) -> Any:
        if hasattr(body, "model_dump"):
            return body.model_dump(by_alias=True, exclude_none=True)
        return body

    def _process_response(
        self, response: httpx.Response, response_type: type[T] | None
    ) -> T | None:
        body = _response_json(response)
        try:
            result = ApiResult[Any].model_validate(body)
        except ValidationError as exc:
            raise MemindAPIError(
                f"Failed to parse response: {response.text}",
                status_code=response.status_code,
                error_code="parse_error",
                body=body if isinstance(body, dict) else None,
            ) from exc

        if 200 <= response.status_code < 300 and result.is_success():
            if response_type is None or result.data is None:
                return None
            try:
                return TypeAdapter(response_type).validate_python(result.data)
            except ValidationError as exc:
                raise MemindAPIError(
                    f"Failed to parse response data: {response.text}",
                    status_code=response.status_code,
                    error_code="parse_error",
                    body=result.data if isinstance(result.data, dict) else None,
                ) from exc

        message = (
            result.error.message
            if result.error is not None
            else f"Memind API error: HTTP {response.status_code}"
        )
        raise self._build_api_error(response, result, body, message)

    def _ensure_open(self) -> None:
        if self._closed:
            raise MemindError("Client has been closed")

    def _mark_closed(self) -> None:
        self._closed = True

    def _build_api_error(
        self,
        response: httpx.Response,
        result: ApiResult[Any],
        body: Any,
        message: str,
    ) -> MemindAPIError:
        body_dict = body if isinstance(body, dict) else None
        error = result.error
        error_code = error.code if error is not None else None
        error_message = error.message if error is not None else message
        if response.status_code == 401:
            return MemindAuthenticationError(
                error_message,
                status_code=response.status_code,
                error_code=error_code,
                body=body_dict,
            )
        if response.status_code == 429:
            return MemindRateLimitError(
                error_message,
                status_code=response.status_code,
                error_code=error_code,
                body=body_dict,
                retry_after=_retry_after_seconds(response),
            )
        return MemindAPIError(
            error_message,
            status_code=response.status_code,
            error_code=error_code,
            body=body_dict,
        )


def _normalize_token(token: str | None) -> str | None:
    if token is None:
        return None
    normalized = token.strip()
    return normalized or None


def _normalize_timeout(timeout: float | httpx.Timeout | None) -> httpx.Timeout:
    if isinstance(timeout, httpx.Timeout):
        return timeout
    if timeout is not None:
        return httpx.Timeout(timeout)
    return httpx.Timeout(DEFAULT_READ_TIMEOUT, connect=DEFAULT_CONNECT_TIMEOUT)


def _response_json(response: httpx.Response) -> Any:
    try:
        return response.json()
    except ValueError as exc:
        raise MemindAPIError(
            f"Failed to parse response: {response.text}",
            status_code=response.status_code,
            error_code="parse_error",
        ) from exc


def _retry_after_seconds(response: httpx.Response) -> float | None:
    return _retry_after_delay(response)
