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

from scripts.lib.state import SessionStateStore


class StateTest(unittest.TestCase):
    def test_marks_and_loads_submitted_fingerprints(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStateStore(Path(tmp))
            with store.locked("session-1") as state:
                state.mark_submitted(["a", "b"])
            with store.locked("session-1") as state:
                self.assertTrue(state.is_submitted("a"))
                self.assertFalse(state.is_submitted("c"))

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
