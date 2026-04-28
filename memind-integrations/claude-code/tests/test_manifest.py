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
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class ManifestTest(unittest.TestCase):
    def test_plugin_json_has_no_hooks_field(self):
        manifest = json.loads((ROOT / ".claude-plugin" / "plugin.json").read_text())
        self.assertEqual(manifest["name"], "memind-memory")
        self.assertNotIn("hooks", manifest)

    def test_hooks_json_shape(self):
        hooks = json.loads((ROOT / "hooks" / "hooks.json").read_text())["hooks"]
        for event in ["SessionStart", "UserPromptSubmit", "PreCompact", "Stop", "SessionEnd"]:
            self.assertIn(event, hooks)
            event_hooks = hooks[event]
            self.assertIsInstance(event_hooks, list)
            self.assertNotIn("matcher", event_hooks[0])
            self.assertIn("hooks", event_hooks[0])
        stop_command = hooks["Stop"][0]["hooks"][0]
        self.assertTrue(stop_command["async"])

    def test_default_settings(self):
        settings = json.loads((ROOT / "settings.json").read_text())
        self.assertEqual(settings["retrieveContextTurns"], 0)
        self.assertEqual(settings["ingestionMode"], "add-message")
        self.assertEqual(settings["ingestionMaxMessagesPerHook"], 20)
        self.assertEqual(settings["stateMaxAgeDays"], 14)


if __name__ == "__main__":
    unittest.main()
