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
    def test_plugin_json_has_required_codex_metadata(self):
        manifest = json.loads((ROOT / ".codex-plugin" / "plugin.json").read_text())
        self.assertEqual(manifest["name"], "memind")
        self.assertEqual(manifest["version"], "0.1.0")
        self.assertEqual(manifest["license"], "Apache-2.0")
        self.assertIn("codex", manifest["keywords"])
        self.assertNotIn("hooks", manifest)
        self.assertEqual(manifest["interface"]["displayName"], "Memind")

    def test_hooks_json_shape(self):
        hooks = json.loads((ROOT / "hooks" / "hooks.json").read_text())["hooks"]
        self.assertEqual(
            set(hooks), {"SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "Stop"}
        )
        self.assertNotIn("PreCompact", hooks)
        self.assertNotIn("SessionEnd", hooks)
        self.assertNotIn("Notification", hooks)
        self.assertNotIn("SubagentStop", hooks)
        for event in ["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "Stop"]:
            self.assertIsInstance(hooks[event], list)
            self.assertNotIn("matcher", hooks[event][0])
            self.assertIn("hooks", hooks[event][0])
            command_hook = hooks[event][0]["hooks"][0]
            self.assertEqual(command_hook["type"], "command")
            self.assertIn("${CODEX_PLUGIN_ROOT}/scripts/", command_hook["command"])
            self.assertNotIn("async", command_hook)
        self.assertNotIn("async", hooks["PreToolUse"][0]["hooks"][0])

    def test_default_settings_match_spec(self):
        settings = json.loads((ROOT / "settings.json").read_text())
        self.assertEqual(settings["agentId"], "coding-agent")
        self.assertEqual(settings["sourceClient"], "codex")
        self.assertTrue(settings["autoIngestAgentTimeline"])
        self.assertEqual(settings["retrieveContextTurns"], 0)
        self.assertFalse(settings["autoPromptContext"])
        self.assertEqual(settings["promptContextProjectMinEntries"], 4)
        self.assertEqual(settings["promptContextGlobalFallbackEntries"], 3)
        self.assertEqual(settings["promptContextGlobalFallbackMinScore"], 0.65)
        self.assertNotIn("agentIdMode", settings)
        self.assertNotIn("commitOnStop", settings)
        self.assertNotIn("autoIngest", settings)
        self.assertNotIn("ingestionRoles", settings)
        self.assertNotIn("ingestionMaxMessagesPerHook", settings)
        self.assertEqual(settings["stateMaxAgeDays"], 14)


if __name__ == "__main__":
    unittest.main()
