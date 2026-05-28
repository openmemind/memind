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

import importlib
import sys
import types
import unittest
from types import SimpleNamespace
from unittest import mock


class _FakeAsyncMemory:
    def __init__(self):
        self.calls = []

    async def extract(self, **kwargs):
        self.calls.append(("extract", kwargs))
        return SimpleNamespace(status="SUCCESS", raw_data_ids=["rd-1"])

    async def add_message(self, **kwargs):
        self.calls.append(("add_message", kwargs))
        return None

    async def commit(self, **kwargs):
        self.calls.append(("commit", kwargs))
        return None


class _FakeAsyncMemindClient:
    instances = []

    def __init__(self, *, base_url=None, api_token=None, timeout=None, max_retries=None):
        self.base_url = base_url
        self.api_token = api_token
        self.timeout = timeout
        self.max_retries = max_retries
        self.memory = _FakeAsyncMemory()
        self.closed = False
        _FakeAsyncMemindClient.instances.append(self)

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        self.closed = True

    async def health(self):
        return SimpleNamespace(status="UP")


class _FakeSyncMemory:
    def __init__(self):
        self.calls = []

    def retrieve(self, **kwargs):
        self.calls.append(("retrieve", kwargs))
        return SimpleNamespace(
            model_dump=lambda by_alias=True: {
                "status": "success",
                "items": [{"id": "1", "text": "remember espresso"}],
                "insights": [],
            }
        )

    def query_items(self, request):
        self.calls.append(("query_items", request))
        return SimpleNamespace(items=[SimpleNamespace(id="it-1", text="Use cargo test")])

    def query_raw_data(self, request):
        self.calls.append(("query_raw_data", request))
        return SimpleNamespace(raw_data=[SimpleNamespace(id="rd-1", caption="Fixed Codex hook")])


class _FakeSyncMemindClient:
    instances = []

    def __init__(self, *, base_url=None, api_token=None, timeout=None, max_retries=None):
        self.base_url = base_url
        self.api_token = api_token
        self.timeout = timeout
        self.max_retries = max_retries
        self.memory = _FakeSyncMemory()
        self.closed = False
        _FakeSyncMemindClient.instances.append(self)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        self.closed = True


class _Strategy(str):
    SIMPLE = "SIMPLE"
    DEEP = "DEEP"

    def __new__(cls, value):
        return str.__new__(cls, value)


class _MetadataCondition:
    def __init__(self, **kwargs):
        self.path = kwargs.get("path")
        self.op = kwargs.get("op")
        self.value = kwargs.get("value")


class _MetadataFilter:
    def __init__(self, all=None, any=None, not_=None, **kwargs):
        excluded = kwargs.get("not", not_)
        self.all = [_MetadataCondition(**item) for item in (all or [])]
        self.any = [_MetadataCondition(**item) for item in (any or [])]
        self.not_ = [_MetadataCondition(**item) for item in (excluded or [])]


class _RetrieveIncludeOptions:
    def __init__(
        self,
        raw_data_metadata=None,
        rawDataMetadata=None,
        raw_data_segment=None,
        rawDataSegment=None,
    ):
        self.raw_data_metadata = (
            raw_data_metadata if raw_data_metadata is not None else rawDataMetadata
        )
        self.raw_data_segment = (
            raw_data_segment if raw_data_segment is not None else rawDataSegment
        )


class _TimeRange:
    def __init__(self, field=None, from_=None, to=None, **kwargs):
        self.field = field
        self.from_ = kwargs.get("from", from_)
        self.to = to


class _RawDataQueryIncludeOptions:
    def __init__(self, segment=None, metadata=None):
        self.segment = segment
        self.metadata = metadata


class _QueryMemoryItemsRequest:
    def __init__(self, metadata_filter=None, **kwargs):
        self.__dict__.update(kwargs)
        self.metadata_filter = _MetadataFilter(**metadata_filter) if isinstance(metadata_filter, dict) else metadata_filter


class _QueryMemoryRawDataRequest:
    def __init__(self, metadata_filter=None, include=None, **kwargs):
        self.__dict__.update(kwargs)
        self.metadata_filter = _MetadataFilter(**metadata_filter) if isinstance(metadata_filter, dict) else metadata_filter
        self.include = include


def _fake_memind_module():
    module = types.ModuleType("memind")
    module.AsyncMemindClient = _FakeAsyncMemindClient
    module.MemindClient = _FakeSyncMemindClient
    module.Strategy = _Strategy
    module.MetadataFilter = _MetadataFilter
    module.RetrieveIncludeOptions = _RetrieveIncludeOptions
    module.TimeRange = _TimeRange
    module.QueryMemoryItemsRequest = _QueryMemoryItemsRequest
    module.QueryMemoryRawDataRequest = _QueryMemoryRawDataRequest
    module.RawDataQueryIncludeOptions = _RawDataQueryIncludeOptions
    return module


def _load_client_class():
    import scripts.lib.client as client_module

    return importlib.reload(client_module).MemindClient


class ClientTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        _FakeAsyncMemindClient.instances = []
        _FakeSyncMemindClient.instances = []

    async def test_async_methods_use_official_client_with_codex_source(self):
        with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
            MemindClient = _load_client_class()
            client = MemindClient("http://127.0.0.1:8366", "token", timeout=10, max_retries=0)
            health = await client.health()
            response = await client.extract(
                "u",
                "a",
                {"type": "conversation", "messages": []},
                "codex",
            )
            await client.add_message("u", "a", {"role": "USER", "content": []}, "codex")
            await client.commit("u", "a", "codex")

        self.assertEqual(health.status, "UP")
        self.assertEqual(response.status, "SUCCESS")
        self.assertEqual(len(_FakeAsyncMemindClient.instances), 4)
        for instance in _FakeAsyncMemindClient.instances:
            self.assertEqual(instance.base_url, "http://127.0.0.1:8366")
            self.assertEqual(instance.api_token, "token")
            self.assertEqual(instance.timeout, 10)
            self.assertEqual(instance.max_retries, 0)
            self.assertTrue(instance.closed)
        extract_call = _FakeAsyncMemindClient.instances[1].memory.calls[0][1]
        self.assertEqual(extract_call["source_client"], "codex")

    def test_retrieve_uses_official_sync_client(self):
        with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
            MemindClient = _load_client_class()
            client = MemindClient("http://127.0.0.1:8366", timeout=12, max_retries=0)
            result = client.retrieve("u", "a", "query", "SIMPLE", False)

        self.assertEqual(result.model_dump(by_alias=True)["items"][0]["text"], "remember espresso")
        self.assertTrue(_FakeSyncMemindClient.instances[0].closed)

    def test_retrieve_passes_structured_filters(self):
        with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
            MemindClient = _load_client_class()
            client = MemindClient("http://memind", "token", timeout=1, max_retries=0)
            result = client.retrieve(
                "u",
                "a",
                "payment context",
                "SIMPLE",
                False,
                scope="AGENT",
                categories=["resolution", "tool"],
                metadata_filter={
                    "all": [{"path": "projectSlug", "op": "eq", "value": "payment"}],
                    "any": [{"path": "files", "op": "contains", "value": "src/payment/calc.ts"}],
                },
                include={"rawDataMetadata": True},
            )

        self.assertIsNotNone(result)
        instance = _FakeSyncMemindClient.instances[0]
        retrieve_call = instance.memory.calls[0][1]
        self.assertEqual(retrieve_call["scope"], "AGENT")
        self.assertEqual(retrieve_call["categories"], ["resolution", "tool"])
        self.assertEqual(retrieve_call["metadata_filter"].all[0].path, "projectSlug")
        self.assertEqual(retrieve_call["metadata_filter"].any[0].path, "files")
        self.assertTrue(retrieve_call["include"].raw_data_metadata)

    def test_query_wrappers_use_official_sync_query_models(self):
        with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
            MemindClient = _load_client_class()
            client = MemindClient("http://127.0.0.1:8366", timeout=12, max_retries=0)
            items = client.query_items(
                "u",
                "a",
                categories=["playbook"],
                raw_data_types=["agent_timeline"],
                metadata_filter={"all": [{"path": "projectSlug", "op": "eq", "value": "memind"}]},
                limit=5,
            )
            raw_data = client.query_raw_data(
                "u",
                "a",
                types=["agent_timeline"],
                metadata_filter={"all": [{"path": "projectSlug", "op": "eq", "value": "memind"}]},
                include={"metadata": True, "segment": False},
                limit=3,
            )

        self.assertEqual(items.items[0].id, "it-1")
        self.assertEqual(raw_data.raw_data[0].id, "rd-1")
        item_request = _FakeSyncMemindClient.instances[0].memory.calls[0][1]
        raw_data_request = _FakeSyncMemindClient.instances[1].memory.calls[0][1]
        self.assertEqual(item_request.user_id, "u")
        self.assertEqual(item_request.categories, ["playbook"])
        self.assertEqual(item_request.metadata_filter.all[0].value, "memind")
        self.assertEqual(raw_data_request.types, ["agent_timeline"])
        self.assertFalse(raw_data_request.include.segment)

    async def test_missing_official_client_import_propagates_to_hook_fail_open_boundary(self):
        with mock.patch.dict(sys.modules, {"memind": None}):
            MemindClient = _load_client_class()
            client = MemindClient("http://127.0.0.1:8366", timeout=10, max_retries=0)
            with self.assertRaises(ModuleNotFoundError):
                await client.extract("u", "a", {"type": "conversation", "messages": []}, "codex")


if __name__ == "__main__":
    unittest.main()
