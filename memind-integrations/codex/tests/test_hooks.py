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
import os
import json
import subprocess
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]


class HookTest(unittest.TestCase):
    def run_hook(self, script, payload, env=None):
        merged_env = os.environ.copy()
        merged_env.update(env or {})
        merged_env.setdefault("CODEX_PLUGIN_ROOT", str(ROOT))
        merged_env.setdefault("PYTHONPATH", str(ROOT))
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / script)],
            input=json.dumps(payload),
            capture_output=True,
            text=True,
            env=merged_env,
            check=True,
            timeout=5,
        )
        return json.loads(result.stdout)

    def test_format_context_prioritizes_tiers_and_scores(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        from retrieve import _format_context

        data = {
            "insights": [
                {"id": "3", "text": "leaf", "tier": "LEAF"},
                {"id": "2", "text": "branch", "tier": "BRANCH"},
                {"id": "1", "text": "root", "tier": "ROOT"},
            ],
            "items": [
                {"id": "10", "text": "low", "finalScore": 0.1},
                {"id": "11", "text": "high", "finalScore": 0.9},
            ],
        }
        context = _format_context(data, {"retrieveMaxEntries": 4, "retrieveMaxChars": 1000, "retrievePromptPreamble": "P"})
        self.assertIn("## Insights", context)
        self.assertIn("## Memory Items", context)
        self.assertLess(context.index("[insight:1 root] root"), context.index("[insight:2 branch] branch"))
        self.assertNotIn("leaf", context)
        self.assertLess(context.index("[item:11 memory] high"), context.index("[item:10 memory] low"))

    def test_format_context_includes_degraded_notice_without_results(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        from retrieve import _format_context

        context = _format_context({"status": "degraded"}, {"retrievePromptPreamble": ""})

        self.assertIn("Memory retrieval encountered an error", context)

    def test_format_context_groups_agent_memory_categories(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        from retrieve import _format_context

        data = {
            "items": [
                {
                    "id": "1",
                    "text": "Use npm test payment",
                    "category": "tool",
                    "metadata": {"toolName": "Bash"},
                },
                {
                    "id": "2",
                    "text": "Payment rounding mismatch was fixed",
                    "category": "resolution",
                    "metadata": {},
                },
                {
                    "id": "3",
                    "text": "When payment tests fail with rounding mismatch, inspect policy, edit calc.ts, then run npm test payment.",
                    "category": "playbook",
                    "metadata": {},
                },
                {
                    "id": "4",
                    "text": "Do not change public API",
                    "category": "directive",
                    "metadata": {},
                },
            ],
            "insights": [],
        }

        context = _format_context(
            data,
            {"retrieveMaxEntries": 8, "retrieveMaxChars": 1000, "retrievePromptPreamble": ""},
        )

        self.assertIn("## Agent Playbooks", context)
        self.assertIn("## Resolved Problems", context)
        self.assertIn("## Tool Notes", context)
        self.assertIn("## Directives", context)
        self.assertLess(context.index("## Directives"), context.index("## Resolved Problems"))
        self.assertLess(context.index("## Resolved Problems"), context.index("## Agent Playbooks"))
        self.assertNotIn("## Memory Items", context)

    def test_retrieve_default_does_not_call_memind_but_buffers_prompt(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import retrieve

        retrieve = importlib.reload(retrieve)

        config = {
            "sourceClient": "codex",
            "autoRetrieve": True,
            "autoPromptContext": False,
            "retrieveContextTurns": 0,
        }

        with tempfile.TemporaryDirectory() as tmp:
            state_dir = Path(tmp) / "state"
            with mock.patch.object(retrieve, "state_root", return_value=state_dir):
                with mock.patch.object(retrieve, "load_config", return_value=config):
                    with mock.patch.object(retrieve, "MemindClient") as client_cls:
                        result = retrieve.handle_user_prompt_submit(
                            {
                                "hook_event_name": "UserPromptSubmit",
                                "cwd": tmp,
                                "session_id": "s1",
                                "user_prompt": "Fix payment tests",
                            }
                        )

            self.assertEqual(result, {"continue": True})
            client_cls.assert_not_called()
            state_file = next(state_dir.glob("*.json"))
            event = json.loads(state_file.read_text())["agentEvents"][0]
            self.assertEqual(event["kind"], "user_prompt")
            self.assertEqual(event["text"], "Fix payment tests")

    def test_retrieve_prompt_context_enabled_uses_project_first_context(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import retrieve

        retrieve = importlib.reload(retrieve)

        config = {
            "sourceClient": "codex",
            "memindApiUrl": "http://127.0.0.1:8366",
            "memindApiToken": None,
            "autoRetrieve": True,
            "autoPromptContext": True,
            "retrieveContextTurns": 0,
            "retrieveStrategy": "SIMPLE",
            "retrieveMaxEntries": 8,
            "retrieveMaxChars": 6000,
            "retrievePromptPreamble": "Relevant memories from Memind.",
            "promptContextProjectMinEntries": 4,
            "promptContextGlobalFallbackEntries": 3,
            "promptContextGlobalFallbackMinScore": 0.65,
        }

        class FakeClient:
            pass

        with tempfile.TemporaryDirectory() as tmp:
            state_dir = Path(tmp) / "state"
            with mock.patch.object(retrieve, "state_root", return_value=state_dir):
                with mock.patch.object(retrieve, "load_config", return_value=config):
                    with mock.patch.object(retrieve, "resolve_identity", return_value={"userId": "u", "agentId": "a"}):
                        with mock.patch.object(retrieve, "project_slug", return_value="memind-main"):
                            with mock.patch.object(retrieve, "MemindClient", return_value=FakeClient()):
                                with mock.patch.object(
                                    retrieve,
                                    "build_prompt_context",
                                    return_value={
                                        "projectSlug": "memind-main",
                                        "mode": "project-first",
                                        "items": [
                                            {
                                                "id": "dir-1",
                                                "text": "Keep ids stable.",
                                                "category": "directive",
                                                "memindContextSource": "project",
                                            }
                                        ],
                                        "insights": [],
                                    },
                                ) as build_context:
                                    result = retrieve.handle_user_prompt_submit(
                                        {
                                            "hook_event_name": "UserPromptSubmit",
                                            "cwd": tmp,
                                            "session_id": "s1",
                                            "user_prompt": "Fix payment tests",
                                        }
                                    )

        build_context.assert_called_once()
        args = build_context.call_args.args
        self.assertEqual(args[3], "memind-main")
        context = result["hookSpecificOutput"]["additionalContext"]
        self.assertIn('<memind_memories project="memind-main" mode="project-first">', context)
        self.assertIn("[item:dir-1 directive, project]", context)

    def test_retrieve_fail_open_when_memind_unavailable(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {
                "CODEX_PLUGIN_ROOT": str(ROOT),
                "PYTHONPATH": str(ROOT),
                "MEMIND_API_URL": "http://127.0.0.1:9",
            }
            output = self.run_hook("retrieve.py", {"cwd": tmp, "session_id": "s1", "prompt": "hello"}, env=env)
        self.assertEqual(output, {"continue": True})

    def test_ingest_without_transcript_fails_open(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {"CODEX_PLUGIN_ROOT": str(ROOT), "PYTHONPATH": str(ROOT)}
            output = self.run_hook("ingest.py", {"cwd": tmp, "session_id": "s1"}, env=env)
        self.assertEqual(output, {"continue": True})

    def test_pre_tool_use_fails_open_and_buffers_event(self):
        with tempfile.TemporaryDirectory() as tmp:
            state_dir = Path(tmp) / "state"
            env = {
                "CODEX_PLUGIN_ROOT": str(ROOT),
                "PYTHONPATH": str(ROOT),
                "MEMIND_CODEX_STATE_ROOT": str(state_dir),
            }
            output = self.run_hook(
                "pre_tool_use.py",
                {
                    "hook_event_name": "PreToolUse",
                    "cwd": tmp,
                    "session_id": "s1",
                    "tool_name": "Bash",
                    "tool_input": {"command": "cargo test"},
                },
                env=env,
            )
            self.assertEqual(output, {"continue": True})
            state_file = next(state_dir.glob("*.json"))
            event = json.loads(state_file.read_text())["agentEvents"][0]
            self.assertEqual(event["kind"], "test_result")
            self.assertEqual(event["status"], "running")

    def test_pre_tool_use_injects_tool_context_when_memind_returns_matches(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import pre_tool_use
        from scripts.lib.state import SessionStateStore, state_key

        pre_tool_use = importlib.reload(pre_tool_use)
        config = {
            "memindApiUrl": "http://127.0.0.1:8366",
            "memindApiToken": None,
            "sourceClient": "codex",
            "agentId": "coding-agent",
            "userId": "u",
            "autoRetrieve": True,
            "autoToolContext": True,
            "toolContextMaxItems": 6,
            "toolContextMinExactItems": 1,
            "toolContextMaxChars": 3500,
            "toolContextEntryMaxChars": 520,
            "retrieveStrategy": "SIMPLE",
        }

        with tempfile.TemporaryDirectory() as tmp:
            state_dir = Path(tmp) / "state"
            store = SessionStateStore(state_dir)
            session_key = state_key({"session_id": "s1"})
            with store.locked(session_key) as state:
                turn_id, turn_seq = state.start_agent_turn(session_key)
                state.append_agent_event(
                    {
                        "eventId": "prompt",
                        "seq": 1,
                        "kind": "user_prompt",
                        "text": "Fix payment tests",
                        "metadata": {"turnId": turn_id, "turnSeq": turn_seq},
                    }
                )

            class FakeClient:
                def query_items(self, **kwargs):
                    return types.SimpleNamespace(
                        items=[
                            types.SimpleNamespace(
                                id="res-1",
                                text="rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.",
                                category="resolution",
                                created_at="2026-05-27T10:00:00Z",
                                metadata={
                                    "projectSlug": "tmp-project",
                                    "files": ["src/payment/calc.ts"],
                                },
                            )
                        ]
                    )

                def query_raw_data(self, **kwargs):
                    return types.SimpleNamespace(raw_data=[])

                def retrieve(self, *args, **kwargs):
                    return types.SimpleNamespace(items=[], insights=[], raw_data=[])

            with mock.patch.object(pre_tool_use, "state_root", return_value=state_dir):
                with mock.patch.object(pre_tool_use, "load_config", return_value=config):
                    with mock.patch.object(pre_tool_use, "resolve_identity", return_value={"userId": "u", "agentId": "coding-agent"}):
                        with mock.patch.object(pre_tool_use, "MemindClient", return_value=FakeClient()):
                            output = pre_tool_use.handle_pre_tool_use(
                                {
                                    "hook_event_name": "PreToolUse",
                                    "cwd": tmp,
                                    "session_id": "s1",
                                    "tool_name": "Edit",
                                    "tool_input": {"file_path": "src/payment/calc.ts"},
                                    "timestamp": "2026-05-28T10:00:00Z",
                                }
                            )

        self.assertIn("hookSpecificOutput", output)
        context = output["hookSpecificOutput"]["additionalContext"]
        self.assertIn("<memind_tool_context", context)
        self.assertIn("## Prior Resolutions", context)
        self.assertIn("rounding mismatch", context)

    def test_pre_tool_use_context_disabled_still_buffers_event(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import pre_tool_use

        pre_tool_use = importlib.reload(pre_tool_use)
        config = {
            "sourceClient": "codex",
            "autoRetrieve": True,
            "autoToolContext": False,
        }

        with tempfile.TemporaryDirectory() as tmp:
            state_dir = Path(tmp) / "state"
            with mock.patch.object(pre_tool_use, "state_root", return_value=state_dir):
                with mock.patch.object(pre_tool_use, "load_config", return_value=config):
                    output = pre_tool_use.handle_pre_tool_use(
                        {
                            "hook_event_name": "PreToolUse",
                            "cwd": tmp,
                            "session_id": "s1",
                            "tool_name": "Edit",
                            "tool_input": {"file_path": "src/payment/calc.ts"},
                        }
                    )

            self.assertEqual(output, {"continue": True})
            state_file = next(state_dir.glob("*.json"))
            event = json.loads(state_file.read_text())["agentEvents"][0]
            self.assertEqual(event["kind"], "file_edit")

    def test_post_tool_use_fails_open_and_buffers_event(self):
        with tempfile.TemporaryDirectory() as tmp:
            state_dir = Path(tmp) / "state"
            env = {
                "CODEX_PLUGIN_ROOT": str(ROOT),
                "PYTHONPATH": str(ROOT),
                "MEMIND_CODEX_STATE_ROOT": str(state_dir),
            }
            output = self.run_hook(
                "post_tool_use.py",
                {
                    "hook_event_name": "PostToolUse",
                    "cwd": tmp,
                    "session_id": "s1",
                    "tool_name": "Bash",
                    "tool_input": {"command": "cargo test"},
                    "tool_response": {"exit_code": 0},
                },
                env=env,
            )
            self.assertEqual(output, {"continue": True})
            state_file = next(state_dir.glob("*.json"))
            event = json.loads(state_file.read_text())["agentEvents"][0]
            self.assertEqual(event["kind"], "test_result")
            self.assertEqual(event["status"], "success")

    def test_retrieve_buffers_user_prompt_event_before_memory_lookup(self):
        with tempfile.TemporaryDirectory() as tmp:
            state_dir = Path(tmp) / "state"
            env = {
                "CODEX_PLUGIN_ROOT": str(ROOT),
                "PYTHONPATH": str(ROOT),
                "MEMIND_CODEX_STATE_ROOT": str(state_dir),
                "MEMIND_API_URL": "http://127.0.0.1:9",
            }
            output = self.run_hook(
                "retrieve.py",
                {
                    "hook_event_name": "UserPromptSubmit",
                    "cwd": tmp,
                    "session_id": "s1",
                    "prompt": "Fix payment tests",
                    "timestamp": "2026-05-24T10:00:00Z",
                },
                env=env,
            )
            self.assertEqual(output, {"continue": True})
            state_file = next(state_dir.glob("*.json"))
            state = json.loads(state_file.read_text())
            event = state["agentEvents"][0]
            self.assertEqual(event["kind"], "user_prompt")
            self.assertEqual(event["text"], "Fix payment tests")
            self.assertEqual(event["metadata"]["turnSeq"], 1)
            self.assertTrue(event["metadata"]["turnId"].startswith("s1-turn-"))

    def test_ingest_ignores_transcript_when_no_agent_events(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest

        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write(json.dumps({"role": "user", "content": "remember this"}) + "\n")
            transcript = Path(handle.name)
        try:
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "autoIngestAgentTimeline": True,
                "ingestRetrySpool": False,
                "sourceClient": "codex",
                "agentId": "coding-agent",
                "userId": "u",
            }
            with tempfile.TemporaryDirectory() as tmp:
                with mock.patch.object(ingest, "state_root", return_value=Path(tmp) / "state"):
                    with mock.patch.object(ingest, "retry_root", return_value=Path(tmp) / "retry"):
                        with mock.patch.object(ingest, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                            client.add_message = mock.AsyncMock(return_value=None)
                            client.commit = mock.AsyncMock(return_value=None)
                            result = ingest.ingest_messages(config, {"session_id": "s1", "transcript_path": str(transcript), "cwd": tmp})
            self.assertEqual(result["agentEventsSubmitted"], 0)
            self.assertFalse(result["committed"])
            client.extract.assert_not_awaited()
            client.add_message.assert_not_awaited()
            client.commit.assert_not_awaited()
        finally:
            transcript.unlink()

    def test_ingest_flushes_agent_timeline_and_clears_events_on_success(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest
        from scripts.lib.state import SessionStateStore

        config = {
            "memindApiUrl": "http://127.0.0.1:8366",
            "memindApiToken": None,
            "autoIngestAgentTimeline": True,
            "ingestRetrySpool": True,
            "sourceClient": "codex",
            "agentId": "coding-agent",
            "userId": "u",
        }
        with tempfile.TemporaryDirectory() as tmp:
            state_root = Path(tmp) / "state"
            transcript = Path(tmp) / "transcript.jsonl"
            transcript.write_text(
                json.dumps(
                    {
                        "role": "assistant",
                        "content": [
                            {
                                "type": "output_text",
                                "text": "Updated calc.ts and payment tests now pass.",
                            }
                        ],
                    }
                )
                + "\n"
            )
            with SessionStateStore(state_root).locked("s1") as state:
                state.append_agent_event(
                    {
                        "eventId": "e1",
                        "seq": 1,
                        "kind": "user_prompt",
                        "text": "Fix payment tests",
                        "metadata": {"turnId": "s1-turn-1", "turnSeq": 1},
                    }
                )
                state.append_agent_event(
                    {
                        "eventId": "e2",
                        "seq": 2,
                        "kind": "command",
                        "command": "cargo test",
                        "metadata": {"turnId": "s1-turn-1", "turnSeq": 1},
                    }
                )
            with mock.patch.object(ingest, "state_root", return_value=state_root):
                with mock.patch.object(ingest, "retry_root", return_value=Path(tmp) / "retry"):
                    with mock.patch.object(ingest, "MemindClient") as client_cls:
                        client = client_cls.return_value
                        client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                        result = ingest.ingest_messages(
                            config,
                            {
                                "hook_event_name": "Stop",
                                "session_id": "s1",
                                "cwd": tmp,
                                "transcript_path": str(transcript),
                                "timestamp": "2026-05-24T10:04:00Z",
                            },
                        )
            self.assertEqual(result["agentEventsSubmitted"], 4)
            client.extract.assert_awaited_once()
            raw_content = client.extract.await_args.args[2]
            self.assertEqual(raw_content["type"], "agent_timeline")
            self.assertEqual(raw_content["sessionId"], "s1")
            self.assertEqual(raw_content["events"][0]["eventId"], "e1")
            self.assertEqual(
                [event["kind"] for event in raw_content["events"]],
                ["user_prompt", "command", "assistant_message", "stop"],
            )
            self.assertEqual(raw_content["agentTurnId"], "s1-turn-1")
            self.assertEqual(raw_content["metadata"]["turnId"], "s1-turn-1")
            with SessionStateStore(state_root).locked("s1") as state:
                self.assertEqual(state.agent_events(), [])
                self.assertTrue(state.is_empty())

    def test_ingest_spools_agent_timeline_on_partial_success(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest
        from scripts.lib.state import SessionStateStore

        config = {
            "memindApiUrl": "http://127.0.0.1:8366",
            "memindApiToken": None,
            "autoIngestAgentTimeline": True,
            "ingestRetrySpool": True,
            "sourceClient": "codex",
            "agentId": "coding-agent",
            "userId": "u",
        }
        with tempfile.TemporaryDirectory() as tmp:
            state_root = Path(tmp) / "state"
            retry_root = Path(tmp) / "retry"
            with SessionStateStore(state_root).locked("s1") as state:
                state.append_agent_event(
                    {"eventId": "e1", "seq": 1, "kind": "command", "command": "cargo test"}
                )
            with mock.patch.object(ingest, "state_root", return_value=state_root):
                with mock.patch.object(ingest, "retry_root", return_value=retry_root):
                    with mock.patch.object(ingest, "MemindClient") as client_cls:
                        client = client_cls.return_value
                        client.extract = mock.AsyncMock(
                            return_value=types.SimpleNamespace(status="PARTIAL_SUCCESS")
                        )
                        ingest.ingest_messages(config, {"session_id": "s1", "cwd": tmp})
            payload = json.loads(next(retry_root.glob("*.json")).read_text())
            self.assertEqual(payload["kind"], "extract")
            self.assertEqual(payload["sessionKey"], "s1")
            self.assertEqual(payload["eventIds"], ["e1"])
            self.assertEqual(payload["rawContent"]["type"], "agent_timeline")
            self.assertEqual(payload["rawContent"]["events"][0]["eventId"], "e1")
            with SessionStateStore(state_root).locked("s1") as state:
                self.assertEqual(len(state.agent_events()), 1)

    def test_session_start_fail_open_when_memind_unavailable(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {
                "CODEX_PLUGIN_ROOT": str(ROOT),
                "PYTHONPATH": str(ROOT),
                "MEMIND_API_URL": "http://127.0.0.1:9",
            }
            output = self.run_hook("session_start.py", {"cwd": tmp, "session_id": "s1"}, env=env)
        self.assertEqual(output, {"continue": True, "suppressOutput": True})

    def test_session_start_discards_unsupported_retry_payload(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool

        with tempfile.TemporaryDirectory() as tmp:
            retry_root = Path(tmp) / "retry"
            state_root = Path(tmp) / "state"
            RetrySpool(retry_root).enqueue(
                {
                    "kind": "unsupported",
                    "userId": "u",
                    "agentId": "a",
                    "sourceClient": "codex",
                    "sessionKey": "s1",
                }
            )
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "ingestRetryMaxFiles": 20,
                "ingestRetryMaxAgeDays": 7,
                "stateMaxAgeDays": 14,
                "debug": False,
            }
            with mock.patch.object(session_start, "retry_root", return_value=retry_root):
                with mock.patch.object(session_start, "state_root", return_value=state_root):
                    with mock.patch.object(session_start, "MemindClient") as client_cls:
                        client = client_cls.return_value
                        client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                        client.add_message = mock.AsyncMock(return_value=None)
                        client.commit = mock.AsyncMock(return_value=None)
                        session_start.run_session_start(config)
            self.assertEqual(list(retry_root.glob("*.json")), [])
        client.add_message.assert_not_awaited()
        client.commit.assert_not_awaited()

    def test_session_start_discards_legacy_conversation_extract_payload(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool

        with tempfile.TemporaryDirectory() as tmp:
            retry_root = Path(tmp) / "retry"
            state_root = Path(tmp) / "state"
            RetrySpool(retry_root).enqueue(
                {
                    "kind": "extract",
                    "userId": "u",
                    "agentId": "a",
                    "sourceClient": "codex",
                    "sessionKey": "s1",
                    "rawContent": {
                        "type": "conversation",
                        "messages": [
                            {"role": "USER", "content": [{"type": "text", "text": "hello"}]}
                        ],
                    },
                }
            )
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "ingestRetryMaxFiles": 20,
                "ingestRetryMaxAgeDays": 7,
                "stateMaxAgeDays": 14,
                "debug": False,
            }
            with mock.patch.object(session_start, "retry_root", return_value=retry_root):
                with mock.patch.object(session_start, "state_root", return_value=state_root):
                    with mock.patch.object(session_start, "MemindClient") as client_cls:
                        client = client_cls.return_value
                        client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                        client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                        client.add_message = mock.AsyncMock(return_value=None)
                        client.commit = mock.AsyncMock(return_value=None)
                        session_start.run_session_start(config)
            self.assertEqual(list(retry_root.glob("*.json")), [])
            client.extract.assert_not_awaited()
            client.add_message.assert_not_awaited()
            client.commit.assert_not_awaited()

    def test_session_start_replays_agent_timeline_payload_and_clears_event_ids(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool
        from scripts.lib.state import SessionStateStore

        with tempfile.TemporaryDirectory() as tmp:
            retry_root = Path(tmp) / "retry"
            state_root = Path(tmp) / "state"
            with SessionStateStore(state_root).locked("s1") as state:
                state.append_agent_event({"eventId": "e1", "seq": 1, "kind": "command"})
                state.append_agent_event({"eventId": "e2", "seq": 2, "kind": "tool_result"})
            RetrySpool(retry_root).enqueue(
                {
                    "kind": "extract",
                    "userId": "u",
                    "agentId": "a",
                    "sourceClient": "codex",
                    "sessionKey": "s1",
                    "eventIds": ["e1"],
                    "rawContent": {
                        "type": "agent_timeline",
                        "sourceClient": "codex",
                        "sessionId": "s1",
                        "timelineId": "s1-agent-1-1",
                        "events": [{"eventId": "e1", "seq": 1, "kind": "command"}],
                    },
                }
            )
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "ingestRetryMaxFiles": 20,
                "ingestRetryMaxAgeDays": 7,
                "stateMaxAgeDays": 14,
                "debug": False,
            }
            with mock.patch.object(session_start, "retry_root", return_value=retry_root):
                with mock.patch.object(session_start, "state_root", return_value=state_root):
                    with mock.patch.object(session_start, "MemindClient") as client_cls:
                        client = client_cls.return_value
                        client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                        client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                        client.add_message = mock.AsyncMock(return_value=None)
                        client.commit = mock.AsyncMock(return_value=None)
                        session_start.run_session_start(config)
            with SessionStateStore(state_root).locked("s1") as state:
                self.assertEqual(
                    state.agent_events(),
                    [{"eventId": "e2", "seq": 2, "kind": "tool_result"}],
                )
            self.assertEqual(list(retry_root.glob("*.json")), [])
            client.extract.assert_awaited_once()

    def test_session_start_injects_project_context_when_available(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start

        config = {
            "memindApiUrl": "http://127.0.0.1:8366",
            "memindApiToken": None,
            "sourceClient": "codex",
            "userId": "u",
            "agentId": "a",
            "autoSessionContext": True,
            "sessionContextRecentSessions": 1,
            "sessionContextMaxItems": 3,
            "sessionContextMaxChars": 4000,
            "ingestRetryMaxFiles": 20,
            "ingestRetryMaxAgeDays": 7,
            "stateMaxAgeDays": 14,
            "debug": False,
        }

        with tempfile.TemporaryDirectory() as tmp:
            retry_root = Path(tmp) / "retry"
            state_root = Path(tmp) / "state"
            with mock.patch.object(session_start, "retry_root", return_value=retry_root):
                with mock.patch.object(session_start, "state_root", return_value=state_root):
                    with mock.patch.object(session_start, "project_slug", return_value="memind-main"):
                        with mock.patch.object(session_start, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                            client.query_raw_data.return_value = types.SimpleNamespace(
                                raw_data=[
                                    types.SimpleNamespace(
                                        id="rd-1",
                                        caption="Implemented Codex SessionStart context.",
                                        metadata={},
                                    )
                                ]
                            )
                            client.query_items.side_effect = [
                                types.SimpleNamespace(
                                    items=[
                                        types.SimpleNamespace(
                                            id="it-1",
                                            category="directive",
                                            text="Keep Codex and Claude Code aligned.",
                                        )
                                    ]
                                ),
                                types.SimpleNamespace(items=[]),
                                types.SimpleNamespace(items=[]),
                                types.SimpleNamespace(items=[]),
                            ]
                            output = session_start.run_session_start(
                                config,
                                {"cwd": tmp, "session_id": "s1"},
                            )

        self.assertIn("hookSpecificOutput", output)
        hook_output = output["hookSpecificOutput"]
        self.assertEqual(hook_output["hookEventName"], "SessionStart")
        self.assertIn("## Continue From", hook_output["additionalContext"])
        self.assertIn("[rawdata:rd-1] Implemented Codex SessionStart context.", hook_output["additionalContext"])
        self.assertIn("[item:it-1 directive] Keep Codex and Claude Code aligned.", hook_output["additionalContext"])
        query_raw_call = client.query_raw_data.call_args.kwargs
        self.assertEqual(query_raw_call["metadata_filter"]["all"][0]["value"], "memind-main")


if __name__ == "__main__":
    unittest.main()
