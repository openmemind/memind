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

import tempfile
import unittest
from pathlib import Path

from scripts.lib.retry import RetrySpool


class RetryTest(unittest.TestCase):
    def test_enqueue_claim_complete(self):
        with tempfile.TemporaryDirectory() as tmp:
            spool = RetrySpool(Path(tmp))
            path = spool.enqueue({"kind": "commit", "userId": "u"})
            self.assertEqual(oct(path.stat().st_mode & 0o777), "0o600")
            claimed = spool.claim_next()
            self.assertTrue(str(claimed).endswith(".claimed"))
            self.assertEqual(spool.load_claimed(claimed)["kind"], "commit")
            spool.complete(claimed)
            self.assertFalse(claimed.exists())

    def test_release_returns_claim_to_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            spool = RetrySpool(Path(tmp))
            spool.enqueue({"kind": "extract", "rawContent": {"type": "agent_timeline"}})
            claimed = spool.claim_next()
            spool.release(claimed)
            self.assertEqual(len(list(Path(tmp).glob("*.json"))), 1)

    def test_cleanup_limits_file_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            spool = RetrySpool(Path(tmp))
            for i in range(3):
                spool.enqueue({"kind": "commit", "index": i})
            spool.cleanup(max_files=2, max_age_days=7)
            self.assertEqual(len(list(Path(tmp).glob("*.json"))), 2)


if __name__ == "__main__":
    unittest.main()
