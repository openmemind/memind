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

import httpx
import pytest

from memind._exceptions import MemindConnectionError, MemindTimeoutError
from memind._http import RetryConfig, async_request_with_retries, request_with_retries


def test_sync_request_retries_503_then_returns_success(httpx_mock) -> None:
    sleeps: list[float] = []
    httpx_mock.add_response(status_code=503, json={"code": "unavailable"})
    httpx_mock.add_response(status_code=200, json={"code": "success", "data": {"ok": True}})

    with httpx.Client() as client:
        response = request_with_retries(
            client,
            "GET",
            "https://api.example.test/open/v1/health",
            retry_config=RetryConfig(max_retries=2, jitter=0.0),
            sleep=sleeps.append,
        )

    assert response.status_code == 200
    assert sleeps == [0.5]


def test_retry_logs_without_payload_or_authorization(httpx_mock, caplog) -> None:
    sleeps: list[float] = []
    httpx_mock.add_response(status_code=503, json={"code": "unavailable"})
    httpx_mock.add_response(status_code=200, json={"code": "success"})

    with httpx.Client() as client:
        with caplog.at_level("WARNING", logger="memind"):
            request_with_retries(
                client,
                "POST",
                "https://api.example.test/open/v1/memory/retrieve",
                retry_config=RetryConfig(max_retries=1, jitter=0.0),
                sleep=sleeps.append,
                headers={"Authorization": "Bearer secret-token"},
                json={"query": "private payload"},
            )

    log_text = caplog.text
    assert (
        "Retrying POST https://api.example.test/open/v1/memory/retrieve after HTTP 503" in log_text
    )
    assert "secret-token" not in log_text
    assert "private payload" not in log_text


def test_sync_request_does_not_retry_when_disabled(httpx_mock) -> None:
    sleeps: list[float] = []
    httpx_mock.add_response(status_code=503, json={"code": "unavailable"})

    with httpx.Client() as client:
        response = request_with_retries(
            client,
            "POST",
            "https://api.example.test/open/v1/memory/add-message",
            retry_config=RetryConfig(max_retries=0, jitter=0.0),
            sleep=sleeps.append,
            json={"message": {"role": "USER", "content": [{"type": "text", "text": "hello"}]}},
        )

    assert response.status_code == 503
    assert sleeps == []
    assert len(httpx_mock.get_requests()) == 1


def test_sync_request_respects_retry_after_header(httpx_mock) -> None:
    sleeps: list[float] = []
    httpx_mock.add_response(status_code=429, headers={"Retry-After": "2"})
    httpx_mock.add_response(status_code=200, json={"code": "success"})

    with httpx.Client() as client:
        response = request_with_retries(
            client,
            "POST",
            "https://api.example.test/open/v1/memory/retrieve",
            retry_config=RetryConfig(max_retries=2, jitter=0.0),
            sleep=sleeps.append,
            json={"query": "coffee"},
        )

    assert response.status_code == 200
    assert sleeps == [2.0]


def test_sync_request_ignores_invalid_retry_after_header(httpx_mock) -> None:
    sleeps: list[float] = []
    httpx_mock.add_response(status_code=429, headers={"Retry-After": "not-a-date"})
    httpx_mock.add_response(status_code=200, json={"code": "success"})

    with httpx.Client() as client:
        response = request_with_retries(
            client,
            "POST",
            "https://api.example.test/open/v1/memory/retrieve",
            retry_config=RetryConfig(max_retries=2, jitter=0.0),
            sleep=sleeps.append,
            json={"query": "coffee"},
        )

    assert response.status_code == 200
    assert sleeps == [0.5]


def test_sync_request_timeout_maps_to_memind_timeout(httpx_mock) -> None:
    request = httpx.Request("GET", "https://api.example.test/open/v1/health")
    httpx_mock.add_exception(httpx.ReadTimeout("timed out", request=request))
    httpx_mock.add_exception(httpx.ReadTimeout("timed out", request=request))

    with httpx.Client() as client:
        with pytest.raises(MemindTimeoutError) as exc_info:
            request_with_retries(
                client,
                "GET",
                "https://api.example.test/open/v1/health",
                retry_config=RetryConfig(max_retries=1, jitter=0.0),
                sleep=lambda _delay: None,
            )

    assert "Request timed out" in str(exc_info.value)
    assert isinstance(exc_info.value.__cause__, httpx.ReadTimeout)


def test_sync_request_transport_error_maps_to_connection_error(httpx_mock) -> None:
    request = httpx.Request("GET", "https://api.example.test/open/v1/health")
    httpx_mock.add_exception(httpx.ConnectError("connection refused", request=request))

    with httpx.Client() as client:
        with pytest.raises(MemindConnectionError) as exc_info:
            request_with_retries(
                client,
                "GET",
                "https://api.example.test/open/v1/health",
                retry_config=RetryConfig(max_retries=0),
            )

    assert "Connection failed" in str(exc_info.value)
    assert isinstance(exc_info.value.__cause__, httpx.ConnectError)


@pytest.mark.asyncio
async def test_async_request_retries_503_then_returns_success(httpx_mock) -> None:
    sleeps: list[float] = []

    async def record_sleep(delay: float) -> None:
        sleeps.append(delay)

    httpx_mock.add_response(status_code=503, json={"code": "unavailable"})
    httpx_mock.add_response(status_code=200, json={"code": "success", "data": {"ok": True}})

    async with httpx.AsyncClient() as client:
        response = await async_request_with_retries(
            client,
            "GET",
            "https://api.example.test/open/v1/health",
            retry_config=RetryConfig(max_retries=2, jitter=0.0),
            sleep=record_sleep,
        )

    assert response.status_code == 200
    assert sleeps == [0.5]
