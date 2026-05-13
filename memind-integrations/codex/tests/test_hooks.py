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
        self.assertLess(context.index("[insight:1] root"), context.index("[insight:2] branch"))
        self.assertNotIn("leaf", context)
        self.assertLess(context.index("[item:11] high"), context.index("[item:10] low"))

    def test_format_context_includes_degraded_notice_without_results(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        from retrieve import _format_context

        context = _format_context({"status": "degraded"}, {"retrievePromptPreamble": ""})

        self.assertIn("Memory retrieval encountered an error", context)

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

    def test_ingest_uses_extract_sync_and_ignores_commit_flag_in_reliable_mode(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest

        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write(json.dumps({"role": "user", "content": "remember this"}) + "\n")
            transcript = Path(handle.name)
        try:
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "autoIngest": True,
                "ingestionRoles": ["user", "assistant"],
                "ingestionMaxMessagesPerHook": 20,
                "ingestRetrySpool": False,
                "sourceClient": "codex",
                "agentId": "codex",
                "agentIdMode": "global",
                "userId": "u",
                "commitOnStop": True,
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
            self.assertEqual(result["submitted"], 1)
            self.assertFalse(result["committed"])
            client.extract.assert_awaited_once()
            client.add_message.assert_not_awaited()
            client.commit.assert_not_awaited()
        finally:
            transcript.unlink()

    def test_ingest_does_not_mark_submitted_or_commit_when_extract_fails_without_spool(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest
        from scripts.lib.content import extract_messages
        from scripts.lib.state import SessionStateStore

        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write(json.dumps({"role": "user", "content": "first"}) + "\n")
            handle.write(json.dumps({"role": "assistant", "content": "second"}) + "\n")
            transcript = Path(handle.name)
        try:
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "autoIngest": True,
                "ingestionRoles": ["user", "assistant"],
                "ingestionMaxMessagesPerHook": 20,
                "ingestRetrySpool": False,
                "sourceClient": "codex",
                "agentId": "codex",
                "agentIdMode": "global",
                "userId": "u",
                "commitOnStop": True,
            }
            with tempfile.TemporaryDirectory() as tmp:
                with mock.patch.object(ingest, "state_root", return_value=Path(tmp) / "state"):
                    with mock.patch.object(ingest, "retry_root", return_value=Path(tmp) / "retry"):
                        with mock.patch.object(ingest, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.extract = mock.AsyncMock(side_effect=RuntimeError("down"))
                            client.add_message = mock.AsyncMock(return_value=None)
                            client.commit = mock.AsyncMock(return_value=None)
                            result = ingest.ingest_messages(config, {"session_id": "s1", "transcript_path": str(transcript), "cwd": tmp})
                messages = extract_messages(transcript, ["user", "assistant"])
                with SessionStateStore(Path(tmp) / "state").locked("s1") as state:
                    self.assertFalse(state.is_submitted(messages[0]["fingerprint"]))
                    self.assertFalse(state.is_submitted(messages[1]["fingerprint"]))
            self.assertEqual(result["submitted"], 0)
            self.assertFalse(result["committed"])
            client.extract.assert_awaited_once()
            client.add_message.assert_not_awaited()
            client.commit.assert_not_awaited()
        finally:
            transcript.unlink()

    def test_ingest_spools_failed_extract_payload(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest

        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write(json.dumps({"role": "user", "content": "first"}) + "\n")
            handle.write(json.dumps({"role": "assistant", "content": "second"}) + "\n")
            transcript = Path(handle.name)
        try:
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "autoIngest": True,
                "ingestionRoles": ["user", "assistant"],
                "ingestionMaxMessagesPerHook": 20,
                "ingestRetrySpool": True,
                "sourceClient": "codex",
                "agentId": "codex",
                "agentIdMode": "global",
                "userId": "u",
                "commitOnStop": True,
            }
            with tempfile.TemporaryDirectory() as tmp:
                retry_dir = Path(tmp) / "retry"
                with mock.patch.object(ingest, "state_root", return_value=Path(tmp) / "state"):
                    with mock.patch.object(ingest, "retry_root", return_value=retry_dir):
                        with mock.patch.object(ingest, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.extract = mock.AsyncMock(side_effect=RuntimeError("down"))
                            client.add_message = mock.AsyncMock(return_value=None)
                            client.commit = mock.AsyncMock(return_value=None)
                            result = ingest.ingest_messages(config, {"session_id": "s1", "transcript_path": str(transcript), "cwd": tmp})
                payload_files = list(retry_dir.glob("*.json"))
                self.assertEqual(len(payload_files), 1)
                payload = json.loads(payload_files[0].read_text())
            self.assertEqual(result["submitted"], 0)
            self.assertFalse(result["committed"])
            self.assertEqual(payload["kind"], "extract")
            self.assertEqual(payload["rawContent"]["type"], "conversation")
            self.assertEqual(len(payload["rawContent"]["messages"]), 2)
            self.assertEqual(len(payload["fingerprints"]), 2)
            self.assertNotIn("commitOnSuccess", payload)
            client.extract.assert_awaited_once()
            client.add_message.assert_not_awaited()
            client.commit.assert_not_awaited()
        finally:
            transcript.unlink()

    def test_ingest_spools_full_extract_payload_on_partial_success(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import ingest

        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write(json.dumps({"role": "user", "content": "first"}) + "\n")
            transcript = Path(handle.name)
        try:
            config = {
                "memindApiUrl": "http://127.0.0.1:8366",
                "memindApiToken": None,
                "autoIngest": True,
                "ingestionRoles": ["user", "assistant"],
                "ingestionMaxMessagesPerHook": 20,
                "ingestRetrySpool": True,
                "sourceClient": "codex",
                "agentId": "codex",
                "agentIdMode": "global",
                "userId": "u",
                "commitOnStop": False,
            }
            with tempfile.TemporaryDirectory() as tmp:
                retry_dir = Path(tmp) / "retry"
                with mock.patch.object(ingest, "state_root", return_value=Path(tmp) / "state"):
                    with mock.patch.object(ingest, "retry_root", return_value=retry_dir):
                        with mock.patch.object(ingest, "MemindClient") as client_cls:
                            client = client_cls.return_value
                            client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="PARTIAL_SUCCESS"))
                            client.add_message = mock.AsyncMock(return_value=None)
                            client.commit = mock.AsyncMock(return_value=None)
                            result = ingest.ingest_messages(config, {"session_id": "s1", "transcript_path": str(transcript), "cwd": tmp})
                payload = json.loads(next(retry_dir.glob("*.json")).read_text())
            self.assertEqual(result["submitted"], 0)
            self.assertEqual(payload["kind"], "extract")
            self.assertEqual(payload["rawContent"]["type"], "conversation")
            self.assertEqual(len(payload["fingerprints"]), 1)
        finally:
            transcript.unlink()

    def test_session_start_fail_open_when_memind_unavailable(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {
                "CODEX_PLUGIN_ROOT": str(ROOT),
                "PYTHONPATH": str(ROOT),
                "MEMIND_API_URL": "http://127.0.0.1:9",
            }
            output = self.run_hook("session_start.py", {"cwd": tmp, "session_id": "s1"}, env=env)
        self.assertEqual(output, {"continue": True, "suppressOutput": True})

    def test_session_start_replays_ingestion_batch_before_commit(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool

        with tempfile.TemporaryDirectory() as tmp:
            retry_root = Path(tmp) / "retry"
            state_root = Path(tmp) / "state"
            RetrySpool(retry_root).enqueue(
                {
                    "kind": "ingestion-batch",
                    "userId": "u",
                    "agentId": "a",
                    "sourceClient": "codex",
                    "operations": [
                        {
                            "kind": "add-message",
                            "userId": "u",
                            "agentId": "a",
                            "sourceClient": "codex",
                            "message": {"role": "USER", "content": [{"type": "text", "text": "hello"}]},
                        }
                    ],
                    "sessionKey": "s1",
                    "fingerprints": ["fp1"],
                    "commitOnSuccess": True,
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
                        events = []

                        async def add_message_side_effect(*args, **kwargs):
                            events.append("add_message")

                        async def commit_side_effect(*args, **kwargs):
                            events.append("commit")

                        client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                        client.add_message = mock.AsyncMock(side_effect=add_message_side_effect)
                        client.commit = mock.AsyncMock(side_effect=commit_side_effect)
                        session_start.run_session_start(config)
        self.assertEqual(events, ["add_message", "commit"])

    def test_session_start_replays_extract_payload_and_marks_fingerprints(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool
        from scripts.lib.state import SessionStateStore

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
                self.assertTrue(state.is_submitted("fp1"))
            self.assertEqual(list(retry_root.glob("*.json")), [])
            client.extract.assert_awaited_once()
            client.add_message.assert_not_awaited()
            client.commit.assert_not_awaited()

    def test_session_start_keeps_extract_payload_when_replay_is_not_success(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool
        from scripts.lib.state import SessionStateStore

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
            with mock.patch.object(session_start, "retry_root", return_value=retry_root):
                with mock.patch.object(session_start, "state_root", return_value=state_root):
                    with mock.patch.object(session_start, "MemindClient") as client_cls:
                        client = client_cls.return_value
                        client.health = mock.AsyncMock(return_value=types.SimpleNamespace(status="UP"))
                        client.extract = mock.AsyncMock(return_value=types.SimpleNamespace(status="PARTIAL_SUCCESS"))
                        client.add_message = mock.AsyncMock(return_value=None)
                        client.commit = mock.AsyncMock(return_value=None)
                        session_start.run_session_start(config)
            with SessionStateStore(state_root).locked("s1") as state:
                self.assertFalse(state.is_submitted("fp1"))
            self.assertEqual(len(list(retry_root.glob("*.json"))), 1)
            client.extract.assert_awaited_once()

    def test_session_start_skips_replayed_message_already_submitted_by_later_stop(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        import session_start
        from scripts.lib.retry import RetrySpool
        from scripts.lib.state import SessionStateStore

        with tempfile.TemporaryDirectory() as tmp:
            retry_root = Path(tmp) / "retry"
            state_root = Path(tmp) / "state"
            SessionStateStore(state_root).mark_submitted("s1", ["fp1"])
            RetrySpool(retry_root).enqueue(
                {
                    "kind": "ingestion-batch",
                    "userId": "u",
                    "agentId": "a",
                    "sourceClient": "codex",
                    "operations": [
                        {
                            "kind": "add-message",
                            "userId": "u",
                            "agentId": "a",
                            "sourceClient": "codex",
                            "message": {"role": "USER", "content": [{"type": "text", "text": "hello"}]},
                        }
                    ],
                    "sessionKey": "s1",
                    "fingerprints": ["fp1"],
                    "commitOnSuccess": True,
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
        client.add_message.assert_not_awaited()
        client.commit.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
