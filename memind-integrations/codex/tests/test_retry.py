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
            spool.enqueue({"kind": "ingestion-batch", "operations": []})
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
