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
        for event in [
            "SessionStart",
            "UserPromptSubmit",
            "PreToolUse",
            "PostToolUse",
            "Notification",
            "SubagentStop",
            "PreCompact",
            "Stop",
            "SessionEnd",
        ]:
            self.assertIn(event, hooks)
            event_hooks = hooks[event]
            self.assertIsInstance(event_hooks, list)
            self.assertNotIn("matcher", event_hooks[0])
            self.assertIn("hooks", event_hooks[0])
        stop_command = hooks["Stop"][0]["hooks"][0]
        pre_tool_hook = hooks["PreToolUse"][0]["hooks"][0]
        self.assertTrue(stop_command["async"])
        self.assertNotIn("async", pre_tool_hook)
        self.assertLessEqual(pre_tool_hook["timeout"], 5)
        self.assertTrue(hooks["PostToolUse"][0]["hooks"][0]["async"])
        self.assertTrue(hooks["Notification"][0]["hooks"][0]["async"])
        self.assertTrue(hooks["SubagentStop"][0]["hooks"][0]["async"])
        self.assertEqual(hooks["Notification"][0]["hooks"][0]["timeout"], 5)
        self.assertEqual(hooks["SubagentStop"][0]["hooks"][0]["timeout"], 5)

    def test_default_settings(self):
        settings = json.loads((ROOT / "settings.json").read_text())
        self.assertEqual(settings["retrieveContextTurns"], 0)
        self.assertFalse(settings["autoPromptContext"])
        self.assertEqual(settings["promptContextProjectMinEntries"], 4)
        self.assertEqual(settings["promptContextGlobalFallbackEntries"], 3)
        self.assertEqual(settings["promptContextGlobalFallbackMinScore"], 0.65)
        self.assertTrue(settings["autoIngestAgentTimeline"])
        self.assertNotIn("agentIdMode", settings)
        self.assertNotIn("autoIngest", settings)
        self.assertNotIn("ingestionRoles", settings)
        self.assertNotIn("ingestionMaxMessagesPerHook", settings)
        self.assertEqual(settings["stateMaxAgeDays"], 14)


if __name__ == "__main__":
    unittest.main()
