import os
import tempfile
import time
import unittest
from pathlib import Path

from scripts.lib.state import SessionStateStore, state_key


class StateTest(unittest.TestCase):
    def test_state_key_prefers_session_id(self):
        self.assertEqual(state_key({"session_id": "abc/def"}), "abc_def")

    def test_state_key_falls_back_to_transcript_path(self):
        key = state_key({"transcript_path": "/tmp/codex/transcript.jsonl"})
        self.assertTrue(key.startswith("transcript-"))

    def test_marks_and_loads_submitted_fingerprints(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStateStore(Path(tmp))
            with store.locked("session-1") as state:
                state.mark_submitted(["a", "b"])
            with store.locked("session-1") as state:
                self.assertTrue(state.is_submitted("a"))
                self.assertFalse(state.is_submitted("c"))

    def test_mark_submitted_persists_immediately(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStateStore(Path(tmp))
            store.mark_submitted("session-1", ["a"])
            with store.locked("session-1") as state:
                self.assertTrue(state.is_submitted("a"))

    def test_cleanup_removes_old_state(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            store = SessionStateStore(root)
            with store.locked("old") as state:
                state.mark_submitted(["x"])
            old_file = root / "old.json"
            old_time = time.time() - 30 * 86400
            os.utime(old_file, (old_time, old_time))
            removed = store.cleanup(max_age_days=14)
            self.assertEqual(removed, 1)
            self.assertFalse(old_file.exists())


if __name__ == "__main__":
    unittest.main()
