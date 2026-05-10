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

from memind._exceptions import (
    MemindAPIError,
    MemindAuthenticationError,
    MemindConnectionError,
    MemindError,
    MemindRateLimitError,
    MemindTimeoutError,
)


class TestMemindError:
    def test_base_exception(self) -> None:
        err = MemindError("something went wrong")
        assert str(err) == "something went wrong"
        assert isinstance(err, Exception)

    def test_api_error(self) -> None:
        err = MemindAPIError(
            "Not Found",
            status_code=404,
            error_code="RESOURCE_NOT_FOUND",
            trace_id="trace-123",
            body={"code": "RESOURCE_NOT_FOUND", "message": "Not Found"},
        )
        assert err.status_code == 404
        assert err.error_code == "RESOURCE_NOT_FOUND"
        assert err.trace_id == "trace-123"
        assert err.body == {"code": "RESOURCE_NOT_FOUND", "message": "Not Found"}
        assert isinstance(err, MemindError)

    def test_authentication_error(self) -> None:
        err = MemindAuthenticationError("Unauthorized", status_code=401, error_code="UNAUTHORIZED")
        assert err.status_code == 401
        assert isinstance(err, MemindAPIError)
        assert isinstance(err, MemindError)

    def test_rate_limit_error(self) -> None:
        err = MemindRateLimitError(
            "Too Many Requests",
            status_code=429,
            error_code="RATE_LIMITED",
            retry_after=2.5,
        )
        assert err.status_code == 429
        assert err.retry_after == 2.5
        assert isinstance(err, MemindAPIError)

    def test_connection_error(self) -> None:
        err = MemindConnectionError("Connection refused")
        assert isinstance(err, MemindError)
        assert not isinstance(err, MemindAPIError)

    def test_timeout_error(self) -> None:
        err = MemindTimeoutError("Request timed out")
        assert isinstance(err, MemindError)
        assert not isinstance(err, MemindAPIError)
