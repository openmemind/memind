#!/usr/bin/env python3
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

"""Run rawdata-agent fixtures against a running memind-server."""

from __future__ import annotations

import argparse
import copy
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_BASE_URL = "http://127.0.0.1:8366"
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"


class FixtureError(RuntimeError):
    pass


@dataclass(frozen=True)
class HttpClient:
    base_url: str
    timeout: float

    def get(self, path: str, params: dict[str, str] | None = None) -> dict[str, Any]:
        if params:
            path = path + "?" + urllib.parse.urlencode(params)
        return self._request("GET", path, None)

    def post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self._request("POST", path, payload)

    def _request(self, method: str, path: str, payload: dict[str, Any] | None) -> dict[str, Any]:
        url = self.base_url.rstrip("/") + path
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=body,
            method=method,
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                text = response.read().decode("utf-8")
                return json.loads(text) if text else {}
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise FixtureError(f"{method} {url} failed with HTTP {exc.code}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise FixtureError(
                f"Cannot reach memind-server at {self.base_url}. "
                "Start the server first or pass --base-url."
            ) from exc


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run rawdata-agent evaluation fixtures against memind-server."
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--fixtures-dir", type=Path, default=FIXTURE_DIR)
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument(
        "--run-id",
        default=f"run-{int(time.time())}",
        help="Suffix used to isolate fixture user ids. Use an empty value to keep fixture ids.",
    )
    args = parser.parse_args()

    client = HttpClient(args.base_url, args.timeout)
    try:
        client.get("/open/v1/health")
        fixtures = load_fixtures(args.fixtures_dir)
        for fixture in fixtures:
            run_fixture(client, isolate_fixture(fixture, args.run_id))
    except FixtureError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print(f"PASS: {len(fixtures)} rawdata-agent fixture(s) passed")
    return 0


def load_fixtures(fixtures_dir: Path) -> list[dict[str, Any]]:
    if not fixtures_dir.exists():
        raise FixtureError(f"Fixtures directory does not exist: {fixtures_dir}")
    fixtures = []
    for path in sorted(fixtures_dir.glob("*.json")):
        try:
            fixtures.append(json.loads(path.read_text(encoding="utf-8")))
        except json.JSONDecodeError as exc:
            raise FixtureError(f"Invalid JSON in {path}: {exc}") from exc
    if not fixtures:
        raise FixtureError(f"No fixture JSON files found in {fixtures_dir}")
    return fixtures


def isolate_fixture(fixture: dict[str, Any], run_id: str) -> dict[str, Any]:
    if not run_id:
        return fixture
    isolated = copy.deepcopy(fixture)
    request = isolated.get("request") or {}
    if "userId" in request:
        request["userId"] = f"{request['userId']}-{run_id}"
    return isolated


def run_fixture(client: HttpClient, fixture: dict[str, Any]) -> None:
    name = fixture.get("name") or "<unnamed>"
    request = fixture.get("request")
    expectations = fixture.get("expectations") or {}
    if not isinstance(request, dict):
        raise FixtureError(f"{name}: fixture request must be an object")

    first = extract(client, request, name)
    expected_categories = list(expectations.get("expectedCategories") or [])
    assert_categories(client, name, request, first, expected_categories)

    second = extract(client, request, name)
    first_count = len(first.get("itemIds") or [])
    second_count = len(second.get("itemIds") or [])
    duplicate_rate = 0.0 if first_count == 0 else 1.0 - (second_count / first_count)
    print(f"{name}: duplicate item rate {duplicate_rate:.2%}")

    for query_expectation in expectations.get("queries") or []:
        assert_retrieve(client, request, query_expectation, name)


def extract(client: HttpClient, payload: dict[str, Any], fixture_name: str) -> dict[str, Any]:
    response = client.post("/open/v1/memory/sync/extract", payload)
    data = response.get("data") or {}
    status = data.get("status")
    if status != "SUCCESS":
        raise FixtureError(f"{fixture_name}: extract status was {status!r}, response={response}")
    return data


def assert_categories(
    client: HttpClient,
    fixture_name: str,
    request: dict[str, Any],
    extract_data: dict[str, Any],
    expected_categories: list[str],
) -> None:
    if not expected_categories:
        return
    item_ids = extract_data.get("itemIds") or []
    if not item_ids:
        raise FixtureError(f"{fixture_name}: extract returned no itemIds")
    response = client.get(
        "/admin/v1/items",
        {"userId": request["userId"], "agentId": request["agentId"], "pageSize": "100"},
    )
    items = (response.get("data") or {}).get("items") or []
    categories = {item.get("category") for item in items if item.get("category")}
    for category in expected_categories:
        if category not in categories:
            raise FixtureError(
                f"{fixture_name}: missing category {category!r}; got {sorted(categories)}"
            )


def assert_retrieve(
    client: HttpClient,
    extract_request: dict[str, Any],
    expectation: dict[str, Any],
    fixture_name: str,
) -> None:
    query = expectation.get("query")
    if not query:
        raise FixtureError(f"{fixture_name}: query expectation is missing query")
    payload = {
        "userId": extract_request["userId"],
        "agentId": extract_request["agentId"],
        "query": query,
        "strategy": "SIMPLE",
    }
    response = client.post("/open/v1/memory/retrieve", payload)
    data = response.get("data") or {}
    items = data.get("items") or []
    haystack = json.dumps(items, ensure_ascii=False)

    for phrase in expectation.get("mustContain") or []:
        if phrase not in haystack:
            raise FixtureError(
                f"{fixture_name}: query {query!r} did not return phrase {phrase!r}"
            )

    categories = {item.get("category") for item in items if item.get("category")}
    for category in expectation.get("expectedCategories") or []:
        if category not in categories:
            raise FixtureError(
                f"{fixture_name}: query {query!r} missing category {category!r}; "
                f"got {sorted(categories)}"
            )


if __name__ == "__main__":
    raise SystemExit(main())
