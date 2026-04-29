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
        self.assertEqual(set(hooks), {"SessionStart", "UserPromptSubmit", "Stop"})
        for event in ["SessionStart", "UserPromptSubmit", "Stop"]:
            self.assertIsInstance(hooks[event], list)
            self.assertNotIn("matcher", hooks[event][0])
            self.assertIn("hooks", hooks[event][0])
            command_hook = hooks[event][0]["hooks"][0]
            self.assertEqual(command_hook["type"], "command")
            self.assertIn("${CODEX_PLUGIN_ROOT}/scripts/", command_hook["command"])
            self.assertNotIn("async", command_hook)

    def test_default_settings_match_spec(self):
        settings = json.loads((ROOT / "settings.json").read_text())
        self.assertEqual(settings["agentId"], "codex")
        self.assertEqual(settings["sourceClient"], "codex")
        self.assertFalse(settings["commitOnStop"])
        self.assertEqual(settings["retrieveContextTurns"], 0)
        self.assertEqual(settings["ingestionMode"], "add-message")
        self.assertEqual(settings["ingestionMaxMessagesPerHook"], 20)
        self.assertEqual(settings["stateMaxAgeDays"], 14)


if __name__ == "__main__":
    unittest.main()
