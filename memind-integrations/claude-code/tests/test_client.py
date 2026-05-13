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


def _fake_memind_module():
    module = types.ModuleType("memind")
    module.AsyncMemindClient = _FakeAsyncMemindClient
    module.MemindClient = _FakeSyncMemindClient
    module.Strategy = _Strategy
    return module


def _load_client_class():
    import scripts.lib.client as client_module

    return importlib.reload(client_module).MemindClient


class ClientTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        _FakeAsyncMemindClient.instances = []
        _FakeSyncMemindClient.instances = []

    async def test_async_methods_use_official_client_with_hook_budget(self):
        with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
            MemindClient = _load_client_class()
            client = MemindClient("http://127.0.0.1:8366", "token", timeout=10, max_retries=0)
            health = await client.health()
            response = await client.extract(
                "u",
                "a",
                {"type": "conversation", "messages": []},
                "claude-code",
            )
            await client.add_message("u", "a", {"role": "USER", "content": []}, "claude-code")
            await client.commit("u", "a", "claude-code")

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
        self.assertEqual(extract_call["source_client"], "claude-code")
        self.assertEqual(extract_call["raw_content"]["type"], "conversation")

    def test_retrieve_uses_official_sync_client_and_returns_model(self):
        with mock.patch.dict(sys.modules, {"memind": _fake_memind_module()}):
            MemindClient = _load_client_class()
            client = MemindClient("http://127.0.0.1:8366", timeout=12, max_retries=0)
            result = client.retrieve("u", "a", "query", "SIMPLE", False)

        self.assertEqual(result.model_dump(by_alias=True)["items"][0]["text"], "remember espresso")
        self.assertEqual(len(_FakeSyncMemindClient.instances), 1)
        instance = _FakeSyncMemindClient.instances[0]
        self.assertEqual(instance.timeout, 12)
        self.assertEqual(instance.max_retries, 0)
        self.assertTrue(instance.closed)
        retrieve_call = instance.memory.calls[0][1]
        self.assertEqual(retrieve_call["strategy"], "SIMPLE")
        self.assertFalse(retrieve_call["trace"])

    async def test_missing_official_client_import_propagates_to_hook_fail_open_boundary(self):
        with mock.patch.dict(sys.modules, {"memind": None}):
            MemindClient = _load_client_class()
            client = MemindClient("http://127.0.0.1:8366", timeout=10, max_retries=0)
            with self.assertRaises(ModuleNotFoundError):
                await client.health()


if __name__ == "__main__":
    unittest.main()
