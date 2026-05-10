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

from types import TracebackType
from typing import Any, TypeVar

import httpx

from memind._base_client import BaseClient
from memind._http import RetryConfig, request_with_retries
from memind.resources.memory import MemoryResource
from memind.types.health import HealthResponse

T = TypeVar("T")


class MemindClient(BaseClient):
    def __init__(
        self,
        *,
        base_url: str | None = None,
        api_token: str | None = None,
        timeout: float | httpx.Timeout | None = None,
        max_retries: int = 2,
        http_client: httpx.Client | None = None,
    ) -> None:
        super().__init__(
            base_url=base_url,
            api_token=api_token,
            timeout=timeout,
            max_retries=max_retries,
        )
        self._http_client = http_client or httpx.Client(timeout=self._timeout)
        self.memory = MemoryResource(self)

    def health(self) -> HealthResponse:
        result = self._get("/health", HealthResponse)
        assert result is not None
        return result

    def close(self) -> None:
        if not self._closed:
            self._http_client.close()
            self._mark_closed()

    def __enter__(self) -> MemindClient:
        self._ensure_open()
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        self.close()

    def _get(self, path: str, response_type: type[T] | None) -> T | None:
        self._ensure_open()
        response = request_with_retries(
            self._http_client,
            "GET",
            self._build_url(path),
            retry_config=self._retry_config,
            headers=self._build_headers(),
        )
        return self._process_response(response, response_type)

    def _post(
        self,
        path: str,
        body: Any,
        response_type: type[T] | None,
        *,
        retry: bool = False,
    ) -> T | None:
        self._ensure_open()
        retry_config = self._retry_config if retry else RetryConfig(max_retries=0)
        response = request_with_retries(
            self._http_client,
            "POST",
            self._build_url(path),
            retry_config=retry_config,
            headers=self._build_headers(),
            json=self._serialize_body(body),
        )
        return self._process_response(response, response_type)
