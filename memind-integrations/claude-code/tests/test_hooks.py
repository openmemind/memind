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
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
