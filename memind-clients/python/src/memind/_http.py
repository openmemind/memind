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

import asyncio
import datetime as dt
import email.utils
import logging
import random
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

import httpx

from memind._constants import DEFAULT_MAX_RETRIES, RETRYABLE_STATUS_CODES
from memind._exceptions import MemindConnectionError, MemindTimeoutError

SyncSleep = Callable[[float], None]
AsyncSleep = Callable[[float], Awaitable[None]]
logger = logging.getLogger("memind")


@dataclass(frozen=True)
class RetryConfig:
    max_retries: int = DEFAULT_MAX_RETRIES
    initial_delay: float = 0.5
    max_delay: float = 2.0
    jitter: float = 0.25


def request_with_retries(
    client: httpx.Client,
    method: str,
    url: str,
    *,
    retry_config: RetryConfig,
    sleep: SyncSleep = time.sleep,
    **kwargs: Any,
) -> httpx.Response:
    for attempt in range(retry_config.max_retries + 1):
        try:
            logger.debug("%s %s", method, url)
            response = client.request(method, url, **kwargs)
            logger.debug("Response status: %s", response.status_code)
        except httpx.TimeoutException as exc:
            if attempt >= retry_config.max_retries:
                raise MemindTimeoutError(f"Request timed out: {method} {url}") from exc
            delay = _retry_delay(retry_config, attempt)
            logger.warning(
                "Retrying %s %s after timeout: attempt=%d delay=%s",
                method,
                url,
                attempt + 1,
                delay,
            )
            sleep(delay)
            continue
        except httpx.TransportError as exc:
            if attempt >= retry_config.max_retries:
                raise MemindConnectionError(f"Connection failed: {method} {url}") from exc
            delay = _retry_delay(retry_config, attempt)
            logger.warning(
                "Retrying %s %s after transport error: attempt=%d delay=%s",
                method,
                url,
                attempt + 1,
                delay,
            )
            sleep(delay)
            continue

        if _should_retry_response(response) and attempt < retry_config.max_retries:
            delay = _retry_delay(retry_config, attempt, response)
            logger.warning(
                "Retrying %s %s after HTTP %s: attempt=%d delay=%s",
                method,
                url,
                response.status_code,
                attempt + 1,
                delay,
            )
            sleep(delay)
            continue

        return response

    raise AssertionError("retry loop exhausted without returning or raising")


async def async_request_with_retries(
    client: httpx.AsyncClient,
    method: str,
    url: str,
    *,
    retry_config: RetryConfig,
    sleep: AsyncSleep = asyncio.sleep,
    **kwargs: Any,
) -> httpx.Response:
    for attempt in range(retry_config.max_retries + 1):
        try:
            logger.debug("%s %s", method, url)
            response = await client.request(method, url, **kwargs)
            logger.debug("Response status: %s", response.status_code)
        except httpx.TimeoutException as exc:
            if attempt >= retry_config.max_retries:
                raise MemindTimeoutError(f"Request timed out: {method} {url}") from exc
            delay = _retry_delay(retry_config, attempt)
            logger.warning(
                "Retrying %s %s after timeout: attempt=%d delay=%s",
                method,
                url,
                attempt + 1,
                delay,
            )
            await sleep(delay)
            continue
        except httpx.TransportError as exc:
            if attempt >= retry_config.max_retries:
                raise MemindConnectionError(f"Connection failed: {method} {url}") from exc
            delay = _retry_delay(retry_config, attempt)
            logger.warning(
                "Retrying %s %s after transport error: attempt=%d delay=%s",
                method,
                url,
                attempt + 1,
                delay,
            )
            await sleep(delay)
            continue

        if _should_retry_response(response) and attempt < retry_config.max_retries:
            delay = _retry_delay(retry_config, attempt, response)
            logger.warning(
                "Retrying %s %s after HTTP %s: attempt=%d delay=%s",
                method,
                url,
                response.status_code,
                attempt + 1,
                delay,
            )
            await sleep(delay)
            continue

        return response

    raise AssertionError("retry loop exhausted without returning or raising")


def _should_retry_response(response: httpx.Response) -> bool:
    return response.status_code in RETRYABLE_STATUS_CODES


def _retry_delay(
    retry_config: RetryConfig,
    attempt: int,
    response: httpx.Response | None = None,
) -> float:
    retry_after = _retry_after_delay(response) if response is not None else None
    if retry_after is not None:
        return retry_after

    delay = float(min(retry_config.initial_delay * (2**attempt), retry_config.max_delay))
    if retry_config.jitter <= 0:
        return delay
    return delay + random.uniform(0.0, retry_config.jitter)


def _retry_after_delay(response: httpx.Response) -> float | None:
    header = response.headers.get("Retry-After")
    if not header:
        return None

    try:
        return float(max(float(header), 0.0))
    except ValueError:
        try:
            parsed = email.utils.parsedate_to_datetime(header)
        except (TypeError, ValueError):
            return None
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=dt.timezone.utc)
        delta_seconds = (parsed - dt.datetime.now(dt.timezone.utc)).total_seconds()
        return float(max(delta_seconds, 0.0))
