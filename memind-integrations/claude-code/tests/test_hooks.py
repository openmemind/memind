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
        merged_env = dict(env or {})
        merged_env.setdefault("CLAUDE_PLUGIN_ROOT", str(ROOT))
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
        self.assertLess(context.index("[insight:1] root"), context.index("[insight:2] branch"))
        self.assertNotIn("leaf", context)
        self.assertLess(context.index("[item:11] high"), context.index("[item:10] low"))

    def test_format_context_includes_degraded_notice_without_results(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        from retrieve import _format_context

        context = _format_context({"status": "degraded"}, {"retrievePromptPreamble": ""})

        self.assertIn("Memory retrieval encountered an error", context)

    def test_ingest_without_transcript_fails_open(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {"CLAUDE_PLUGIN_ROOT": tmp, "PYTHONPATH": str(ROOT)}
            output = self.run_hook("ingest.py", {"cwd": tmp, "session_id": "s1"}, env=env)
        self.assertEqual(output, {"continue": True})

    def test_pre_compact_without_transcript_fails_open(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {"CLAUDE_PLUGIN_ROOT": tmp, "PYTHONPATH": str(ROOT)}
            output = self.run_hook("pre_compact.py", {"cwd": tmp, "session_id": "s1"}, env=env)
        self.assertEqual(output, {"continue": True})

    def test_session_end_without_transcript_fails_open(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {"CLAUDE_PLUGIN_ROOT": tmp, "PYTHONPATH": str(ROOT)}
            output = self.run_hook("session_end.py", {"cwd": tmp, "session_id": "s1"}, env=env)
        self.assertEqual(output, {"continue": True})

    def test_retrieve_fail_open_when_memind_unavailable(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {
                "CLAUDE_PLUGIN_ROOT": tmp,
                "PYTHONPATH": str(ROOT),
                "MEMIND_API_URL": "http://127.0.0.1:9",
            }
            output = self.run_hook("retrieve.py", {"cwd": tmp, "session_id": "s1", "prompt": "hello"}, env=env)
        self.assertEqual(output, {"continue": True})

    def test_session_start_fail_open_when_memind_unavailable(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {
                "CLAUDE_PLUGIN_ROOT": tmp,
                "PYTHONPATH": str(ROOT),
                "MEMIND_API_URL": "http://127.0.0.1:9",
            }
            output = self.run_hook("session_start.py", {"cwd": tmp, "session_id": "s1"}, env=env)
        self.assertEqual(output, {"continue": True, "suppressOutput": True})

    def test_ingest_uses_extract_sync_payload_and_marks_submitted_only_on_success(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest

        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write(
                json.dumps(
                    {
                        "type": "user",
                        "uuid": "msg-1",
                        "timestamp": "2026-05-11T00:00:00Z",
                        "message": {"content": "remember espresso"},
                    }
                )
                + "\n"
            )
            transcript = Path(handle.name)
        try:
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "autoIngest": True,
                "ingestionRoles": ["user", "assistant"],
                "ingestionMaxMessagesPerHook": 20,
                "ingestRetrySpool": True,
                "sourceClient": "claude-code",
                "agentId": "claude-code",
                "agentIdMode": "global",
                "userId": "u",
            }
            with tempfile.TemporaryDirectory() as tmp:
                with mock.patch.object(ingest, "state_root", return_value=Path(tmp) / "state"):
                    with mock.patch.object(ingest, "retry_root", return_value=Path(tmp) / "retry"):
                        with mock.patch.object(ingest, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                            client.commit = mock.AsyncMock(return_value=None)
                            result = ingest.ingest_messages(
                                config,
                                {
                                    "session_id": "s1",
                                    "transcript_path": str(transcript),
                                    "cwd": tmp,
                                },
                                commit=True,
                            )
            self.assertEqual(result["submitted"], 1)
            self.assertFalse(result["committed"])
            client.extract.assert_awaited_once()
            client.commit.assert_not_awaited()
            raw_content = client.extract.await_args.args[2]
            self.assertEqual(raw_content["type"], "conversation")
            self.assertEqual(raw_content["messages"][0]["role"], "USER")
        finally:
            transcript.unlink()

    def test_ingest_spools_full_extract_payload_on_partial_success(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest

        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write(
                json.dumps(
                    {
                        "type": "user",
                        "uuid": "msg-1",
                        "timestamp": "2026-05-11T00:00:00Z",
                        "message": {"content": "remember espresso"},
                    }
                )
                + "\n"
            )
            transcript = Path(handle.name)
        try:
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "autoIngest": True,
                "ingestionRoles": ["user", "assistant"],
                "ingestionMaxMessagesPerHook": 20,
                "ingestRetrySpool": True,
                "sourceClient": "claude-code",
                "agentId": "claude-code",
                "agentIdMode": "global",
                "userId": "u",
            }
            with tempfile.TemporaryDirectory() as tmp:
                retry_dir = Path(tmp) / "retry"
                with mock.patch.object(ingest, "state_root", return_value=Path(tmp) / "state"):
                    with mock.patch.object(ingest, "retry_root", return_value=retry_dir):
                        with mock.patch.object(ingest, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="PARTIAL_SUCCESS"))
                            client.commit = mock.AsyncMock(return_value=None)
                            result = ingest.ingest_messages(
                                config,
                                {
                                    "session_id": "s1",
                                    "transcript_path": str(transcript),
                                    "cwd": tmp,
                                },
                                commit=False,
                            )
                payload = json.loads(next(retry_dir.glob("*.json")).read_text())
            self.assertEqual(result["submitted"], 0)
            self.assertEqual(payload["kind"], "extract")
            self.assertEqual(payload["rawContent"]["type"], "conversation")
            self.assertEqual(len(payload["fingerprints"]), 1)
        finally:
            transcript.unlink()

    def test_session_start_replays_extract_payload_and_marks_fingerprints(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool
        from scripts.lib.state import SessionStateStore

        with tempfile.TemporaryDirectory() as tmp:
            retry_dir = Path(tmp) / "retry"
            state_dir = Path(tmp) / "state"
            RetrySpool(retry_dir).enqueue(
                {
                    "kind": "extract",
                    "userId": "u",
                    "agentId": "a",
                    "sourceClient": "claude-code",
                    "sessionId": "s1",
                    "fingerprints": ["fp1"],
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
            with mock.patch.object(session_start, "load_config", return_value=config):
                with mock.patch.object(session_start, "retry_root", return_value=retry_dir):
                    with mock.patch.object(session_start, "state_root", return_value=state_dir):
                        with mock.patch.object(session_start, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                            client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="SUCCESS"))
                            client.add_message = mock.AsyncMock(return_value=None)
                            client.commit = mock.AsyncMock(return_value=None)
                            session_start.main()
            with SessionStateStore(state_dir).locked("s1") as state:
                self.assertTrue(state.is_submitted("fp1"))
            self.assertEqual(list(retry_dir.glob("*.json")), [])
            client.extract.assert_awaited_once()
            client.add_message.assert_not_awaited()
            client.commit.assert_not_awaited()

    def test_session_start_keeps_extract_payload_when_replay_is_not_success(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool
        from scripts.lib.state import SessionStateStore

        with tempfile.TemporaryDirectory() as tmp:
            retry_dir = Path(tmp) / "retry"
            state_dir = Path(tmp) / "state"
            RetrySpool(retry_dir).enqueue(
                {
                    "kind": "extract",
                    "userId": "u",
                    "agentId": "a",
                    "sourceClient": "claude-code",
                    "sessionId": "s1",
                    "fingerprints": ["fp1"],
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
            with mock.patch.object(session_start, "load_config", return_value=config):
                with mock.patch.object(session_start, "retry_root", return_value=retry_dir):
                    with mock.patch.object(session_start, "state_root", return_value=state_dir):
                        with mock.patch.object(session_start, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                            client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="PARTIAL_SUCCESS"))
                            client.add_message = mock.AsyncMock(return_value=None)
                            client.commit = mock.AsyncMock(return_value=None)
                            session_start.main()
            with SessionStateStore(state_dir).locked("s1") as state:
                self.assertFalse(state.is_submitted("fp1"))
            self.assertEqual(len(list(retry_dir.glob("*.json"))), 1)
            client.extract.assert_awaited_once()


if __name__ == "__main__":
    unittest.main()
