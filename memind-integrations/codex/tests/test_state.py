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

    def test_agent_events_are_deduplicated_and_clear_by_event_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStateStore(Path(tmp))
            with store.locked("session-1") as state:
                self.assertEqual(state.next_agent_seq(), 1)
                self.assertEqual(state.next_agent_seq(), 2)
                state.append_agent_event({"eventId": "e1", "seq": 1})
                state.append_agent_event({"eventId": "e1", "seq": 1})
                state.append_agent_event({"eventId": "e2", "seq": 2})
                state.clear_agent_events(["e1"])
            with store.locked("session-1") as state:
                self.assertEqual(state.agent_events(), [{"eventId": "e2", "seq": 2}])

    def test_agent_event_buffer_has_soft_cap(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStateStore(Path(tmp))
            with store.locked("session-1") as state:
                for index in range(501):
                    state.append_agent_event({"eventId": f"e{index}", "seq": index})
            with store.locked("session-1") as state:
                events = state.agent_events()
                self.assertEqual(len(events), 500)
                self.assertEqual(events[0]["eventId"], "e1")
                self.assertTrue(state.data["agentEventsTruncated"])

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
