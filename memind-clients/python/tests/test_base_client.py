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

from typing import Any, TypeVar

import httpx
import pytest
from pydantic import BaseModel

from memind._base_client import BaseClient
from memind._exceptions import (
    MemindAPIError,
    MemindAuthenticationError,
    MemindError,
    MemindRateLimitError,
)
from memind.types.health import HealthResponse

T = TypeVar("T")


class RequiredFieldResponse(BaseModel):
    required_field: str


class InspectableClient(BaseClient):
    def headers(self) -> dict[str, str]:
        return self._build_headers()

    def url(self, path: str) -> str:
        return self._build_url(path)

    def process(self, response: httpx.Response, response_type: type[T] | None) -> T | None:
        return self._process_response(response, response_type)


def make_response(status_code: int, payload: dict[str, Any]) -> httpx.Response:
    return httpx.Response(
        status_code,
        json=payload,
        request=httpx.Request("GET", "https://x"),
    )


def test_base_url_required(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("MEMIND_BASE_URL", raising=False)
    with pytest.raises(MemindError, match="base_url"):
        InspectableClient()


def test_blank_constructor_base_url_does_not_fall_back_to_env(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("MEMIND_BASE_URL", "https://env.example.test")
    with pytest.raises(MemindError, match="base_url"):
        InspectableClient(base_url=" ")


def test_negative_max_retries_is_rejected() -> None:
    with pytest.raises(MemindError, match="max_retries"):
        InspectableClient(base_url="https://api.example.test", max_retries=-1)


def test_base_url_can_come_from_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEMIND_BASE_URL", "https://api.example.test/")
    client = InspectableClient()
    assert client.url("/health") == "https://api.example.test/open/v1/health"


def test_constructor_config_overrides_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MEMIND_BASE_URL", "https://env.example.test")
    client = InspectableClient(base_url="https://arg.example.test/base/")
    assert client.url("memory/retrieve") == "https://arg.example.test/base/open/v1/memory/retrieve"


def test_headers_include_user_agent_and_optional_auth() -> None:
    client = InspectableClient(base_url="https://api.example.test", api_token=" sk-test ")
    headers = client.headers()
    assert headers["Accept"] == "application/json"
    assert headers["Content-Type"] == "application/json"
    assert headers["Authorization"] == "Bearer sk-test"
    assert headers["User-Agent"].startswith("memind-python/")


def test_headers_skip_empty_token() -> None:
    client = InspectableClient(base_url="https://api.example.test", api_token=" ")
    assert "Authorization" not in client.headers()


def test_process_success_response_returns_typed_data() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = make_response(
        200,
        {"data": {"status": "UP", "service": "memind-server"}},
    )
    result = client.process(response, HealthResponse)
    assert isinstance(result, HealthResponse)
    assert result.status == "UP"


def test_process_success_response_without_data_returns_none() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = make_response(200, {"data": None})
    assert client.process(response, None) is None


def test_process_success_response_data_validation_error_is_api_error() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = make_response(200, {"data": {"unexpected": "value"}})
    with pytest.raises(MemindAPIError) as exc_info:
        client.process(response, RequiredFieldResponse)

    assert exc_info.value.status_code == 200
    assert exc_info.value.error_code == "parse_error"
    assert exc_info.value.body == {"unexpected": "value"}


def test_process_api_error_raises_api_error() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = make_response(
        400,
        {"error": {"code": "bad_request", "message": "query is required"}},
    )
    with pytest.raises(MemindAPIError) as exc_info:
        client.process(response, HealthResponse)

    assert exc_info.value.status_code == 400
    assert exc_info.value.error_code == "bad_request"
    assert exc_info.value.body is not None
    assert exc_info.value.body["error"]["message"] == "query is required"


def test_process_authentication_error() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = make_response(401, {"error": {"code": "unauthorized", "message": "bad token"}})
    with pytest.raises(MemindAuthenticationError):
        client.process(response, None)


def test_process_rate_limit_error() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = httpx.Response(
        429,
        json={"error": {"code": "rate_limited", "message": "slow down"}},
        headers={"Retry-After": "3"},
        request=httpx.Request("POST", "https://x"),
    )
    with pytest.raises(MemindRateLimitError) as exc_info:
        client.process(response, None)

    assert exc_info.value.retry_after == 3.0


def test_process_rate_limit_error_accepts_http_date_retry_after() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = httpx.Response(
        429,
        json={"error": {"code": "rate_limited", "message": "slow down"}},
        headers={"Retry-After": "Wed, 21 Oct 2099 07:28:00 GMT"},
        request=httpx.Request("POST", "https://x"),
    )
    with pytest.raises(MemindRateLimitError) as exc_info:
        client.process(response, None)

    assert exc_info.value.retry_after is not None
    assert exc_info.value.retry_after > 0


def test_process_parse_error() -> None:
    client = InspectableClient(base_url="https://api.example.test")
    response = httpx.Response(502, content=b"not json", request=httpx.Request("GET", "https://x"))
    with pytest.raises(MemindAPIError) as exc_info:
        client.process(response, None)

    assert exc_info.value.error_code == "parse_error"
