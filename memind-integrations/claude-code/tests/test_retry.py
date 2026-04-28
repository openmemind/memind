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
import os
import tempfile
import time
import unittest
from pathlib import Path

from scripts.lib.retry import RetrySpool


class RetrySpoolTest(unittest.TestCase):
    def test_enqueue_writes_owner_only_payload(self):
        with tempfile.TemporaryDirectory() as tmp:
            spool = RetrySpool(Path(tmp))
            path = spool.enqueue({"kind": "add-message", "payload": {"x": 1}})
            self.assertEqual(json.loads(path.read_text())["kind"], "add-message")
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_cleanup_discards_oldest_over_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            spool = RetrySpool(Path(tmp))
            first = spool.enqueue({"kind": "add-message", "payload": {"n": 1}})
            time.sleep(0.001)
            second = spool.enqueue({"kind": "add-message", "payload": {"n": 2}})
            spool.cleanup(max_files=1, max_age_days=7)
            self.assertFalse(first.exists())
            self.assertTrue(second.exists())

    def test_claim_complete_and_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            spool = RetrySpool(Path(tmp))
            original = spool.enqueue({"kind": "commit", "payload": {"userId": "u"}})
            claimed = spool.claim_next()
            self.assertFalse(original.exists())
            self.assertTrue(claimed.exists())
            self.assertEqual(spool.load_claimed(claimed)["kind"], "commit")
            spool.release(claimed)
            self.assertIsNotNone(spool.claim_next())

    def test_cleanup_discards_old_payloads(self):
        with tempfile.TemporaryDirectory() as tmp:
            spool = RetrySpool(Path(tmp))
            path = spool.enqueue({"kind": "commit"})
            old_time = time.time() - 10 * 86400
            os.utime(path, (old_time, old_time))
            spool.cleanup(max_files=20, max_age_days=7)
            self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
