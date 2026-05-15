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

import json
import tempfile
import types
import unittest
from unittest import mock

from memind_hermes.provider import MemindMemoryProvider, register


class FakeClient:
    def __init__(self):
        self.retrieve_calls = []
        self.extract_calls = []
        self.health_calls = 0
        self.health_result = types.SimpleNamespace(status="UP")
        self.retrieve_result = {
            "insights": [{"id": "1", "text": "User likes Rust", "tier": "ROOT"}],
            "items": [],
        }
        self.extract_result = types.SimpleNamespace(status="SUCCESS")

    def health(self):
        self.health_calls += 1
        return self.health_result

    def retrieve(self, user_id, agent_id, query, strategy="SIMPLE", trace=False):
        self.retrieve_calls.append(
            {
                "user_id": user_id,
                "agent_id": agent_id,
                "query": query,
                "strategy": strategy,
                "trace": trace,
            }
        )
        return self.retrieve_result

    def extract(self, user_id, agent_id, raw_content, source_client=None):
        self.extract_calls.append(
            {
                "user_id": user_id,
                "agent_id": agent_id,
                "raw_content": raw_content,
                "source_client": source_client,
            }
        )
        return self.extract_result


class ProviderTest(unittest.TestCase):
    def provider(self, client=None, config=None):
        cfg = {
            "memindApiUrl": "http://127.0.0.1:8366",
            "memindApiToken": None,
            "userId": "u",
            "agentId": "hermes",
            "agentIdMode": "fixed",
            "sourceClient": "hermes",
            "memoryMode": "hybrid",
            "autoRetrieve": True,
            "autoIngest": True,
            "retrieveStrategy": "SIMPLE",
            "retrieveMaxEntries": 8,
            "retrieveMaxChars": 6000,
            "retrieveContextTurns": 1,
            "retrievePromptPreamble": "P",
            "ingestionRoles": ["user", "assistant"],
            "debug": False,
        }
        if config:
            cfg.update(config)
        return MemindMemoryProvider(config_loader=lambda: cfg, client_factory=lambda cfg: client or FakeClient())

    def test_register_registers_memory_provider(self):
        class Ctx:
            def __init__(self):
                self.provider = None

            def register_memory_provider(self, provider):
                self.provider = provider

        ctx = Ctx()
        register(ctx)

        self.assertIsInstance(ctx.provider, MemindMemoryProvider)

    def test_initialize_resolves_identity(self):
        provider = self.provider()

        provider.initialize("s1", cwd="/tmp/project")

        self.assertEqual(provider._session_id, "s1")
        self.assertEqual(provider._identity["userId"], "u")
        self.assertEqual(provider._identity["agentId"], "hermes")

    def test_is_available_does_not_call_memind_health(self):
        client = FakeClient()

        self.assertTrue(self.provider(client=client).is_available())
        self.assertEqual(client.health_calls, 0)
        self.assertEqual(client.retrieve_calls, [])
        self.assertEqual(client.extract_calls, [])

    def test_is_available_rejects_invalid_url(self):
        self.assertFalse(self.provider(config={"memindApiUrl": "not-a-url"}).is_available())

    def test_is_available_returns_true_with_injected_client_factory(self):
        self.assertTrue(self.provider().is_available())

    def test_prefetch_retrieves_and_formats_context(self):
        client = FakeClient()
        provider = self.provider(client=client)
        provider.initialize("s1", cwd="/tmp/project")

        context = provider.prefetch("favorite language")

        self.assertIn("<memind_memories>", context)
        self.assertIn("User likes Rust", context)
        self.assertEqual(client.retrieve_calls[0]["strategy"], "SIMPLE")

    def test_prefetch_disabled_in_tools_mode(self):
        client = FakeClient()
        provider = self.provider(client=client, config={"memoryMode": "tools"})
        provider.initialize("s1", cwd="/tmp/project")

        self.assertEqual(provider.prefetch("favorite language"), "")
        self.assertEqual(client.retrieve_calls, [])

    def test_queue_prefetch_sets_result_and_thread(self):
        provider = self.provider()
        provider.initialize("s1", cwd="/tmp/project")

        provider.queue_prefetch("favorite language")
        provider._prefetch_thread.join(timeout=2)

        self.assertIn("User likes Rust", provider._prefetch_result)

    def test_sync_turn_extracts_conversation_on_background_thread(self):
        client = FakeClient()
        provider = self.provider(client=client)
        provider.initialize("s1", cwd="/tmp/project")

        provider.sync_turn("hello <memind_memories>x</memind_memories>", "hi")
        provider._sync_thread.join(timeout=2)

        self.assertEqual(len(client.extract_calls), 1)
        call = client.extract_calls[0]
        self.assertEqual(call["source_client"], "hermes")
        dumped = call["raw_content"].model_dump(by_alias=True, exclude_none=True)
        self.assertEqual(dumped["type"], "conversation")
        self.assertNotIn("memind_memories", dumped["messages"][0]["content"][0]["text"])

    def test_sync_turn_respects_ingestion_roles(self):
        client = FakeClient()
        provider = self.provider(client=client, config={"ingestionRoles": ["user"]})
        provider.initialize("s1", cwd="/tmp/project")

        provider.sync_turn("remember espresso", "ok")
        provider._sync_thread.join(timeout=2)

        dumped = client.extract_calls[0]["raw_content"].model_dump(by_alias=True, exclude_none=True)
        self.assertEqual(len(dumped["messages"]), 1)
        self.assertEqual(dumped["messages"][0]["role"], "USER")

    def test_on_pre_compress_inserts_context(self):
        provider = self.provider()
        provider.initialize("s1", cwd="/tmp/project")
        messages = [{"role": "user", "content": "current prompt"}]

        provider.on_pre_compress(messages, session_id="s1")

        self.assertEqual(messages[0]["role"], "user")
        self.assertIn("[Memind context before compaction]", messages[0]["content"])

    def test_on_pre_compress_uses_retrieve_context_turns(self):
        client = FakeClient()
        provider = self.provider(client=client, config={"retrieveContextTurns": 1})
        provider.initialize("s1", cwd="/tmp/project")
        messages = [
            {"role": "user", "content": "old user"},
            {"role": "assistant", "content": "old assistant"},
            {"role": "user", "content": "recent user"},
            {"role": "assistant", "content": "recent assistant"},
        ]

        provider.on_pre_compress(messages, session_id="s1")

        query = client.retrieve_calls[0]["query"]
        self.assertNotIn("old user", query)
        self.assertIn("recent user", query)
        self.assertIn("recent assistant", query)

    def test_on_pre_compress_does_not_duplicate_existing_memind_context(self):
        client = FakeClient()
        provider = self.provider(client=client)
        provider.initialize("s1", cwd="/tmp/project")
        messages = [
            {"role": "user", "content": "[Memind context before compaction]\n<memind_memories>x</memind_memories>"},
            {"role": "user", "content": "current prompt"},
        ]

        provider.on_pre_compress(messages, session_id="s1")

        self.assertEqual(len(messages), 2)
        self.assertEqual(client.retrieve_calls, [])

    def test_on_memory_write_extracts_standalone_text(self):
        client = FakeClient()
        provider = self.provider(client=client)
        provider.initialize("s1", cwd="/tmp/project")

        provider.on_memory_write("add", "MEMORY.md", "remember espresso")
        provider._sync_thread.join(timeout=2)

        dumped = client.extract_calls[0]["raw_content"].model_dump(by_alias=True, exclude_none=True)
        self.assertEqual(dumped["messages"][0]["role"], "USER")
        self.assertEqual(dumped["messages"][0]["content"][0]["text"], "remember espresso")

    def test_get_config_schema_and_save_config(self):
        provider = self.provider()

        self.assertTrue(provider.get_config_schema())
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch("memind_hermes.provider.save_provider_config") as save:
                provider.save_config({"userId": "u"}, tmp)
        save.assert_called_once_with({"userId": "u"})

    def test_context_mode_hides_tools(self):
        provider = self.provider(config={"memoryMode": "context"})
        provider.initialize("s1", cwd="/tmp/project")

        self.assertEqual(provider.get_tool_schemas(), [])

    def test_tools_mode_exposes_tools(self):
        provider = self.provider(config={"memoryMode": "tools"})
        provider.initialize("s1", cwd="/tmp/project")

        names = [tool["name"] for tool in provider.get_tool_schemas()]
        self.assertEqual(names, ["memind_retrieve", "memind_extract_text"])

    def test_handle_retrieve_tool_returns_json_string(self):
        client = FakeClient()
        provider = self.provider(client=client)
        provider.initialize("s1", cwd="/tmp/project")

        result = json.loads(provider.handle_tool_call("memind_retrieve", {"query": "rust"}))

        self.assertIn("insights", result)
        self.assertEqual(client.retrieve_calls[0]["query"], "rust")

    def test_handle_retrieve_tool_rejects_invalid_strategy(self):
        provider = self.provider()
        provider.initialize("s1", cwd="/tmp/project")

        result = json.loads(provider.handle_tool_call("memind_retrieve", {"query": "rust", "strategy": "bad"}))

        self.assertIn("error", result)

    def test_handle_extract_text_uses_user_message_content(self):
        client = FakeClient()
        provider = self.provider(client=client)
        provider.initialize("s1", cwd="/tmp/project")

        result = json.loads(provider.handle_tool_call("memind_extract_text", {"text": "remember espresso"}))

        self.assertEqual(result["status"], "SUCCESS")
        dumped = client.extract_calls[0]["raw_content"].model_dump(by_alias=True, exclude_none=True)
        self.assertEqual(dumped["messages"][0]["role"], "USER")
        self.assertEqual(dumped["messages"][0]["content"][0]["text"], "remember espresso")


if __name__ == "__main__":
    unittest.main()
