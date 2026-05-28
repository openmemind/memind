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
import tempfile
import unittest
from pathlib import Path

from scripts.lib.config import DEFAULT_SETTINGS, load_config, parse_bool, parse_list


class ConfigTest(unittest.TestCase):
    def test_defaults_are_codex_specific(self):
        config = load_config(plugin_root=Path(__file__).resolve().parents[1], user_config_path="/missing", env={})
        self.assertEqual(config["agentId"], "coding-agent")
        self.assertEqual(config["sourceClient"], "codex")
        self.assertEqual(config["retrieveContextTurns"], 0)
        self.assertFalse(DEFAULT_SETTINGS["autoPromptContext"])
        self.assertEqual(DEFAULT_SETTINGS["promptContextProjectMinEntries"], 4)
        self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackEntries"], 3)
        self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackMinScore"], 0.65)
        self.assertTrue(config["autoSessionContext"])
        self.assertEqual(config["sessionContextRecentSessions"], 3)
        self.assertEqual(config["sessionContextMaxItems"], 6)
        self.assertEqual(config["sessionContextMaxChars"], 6000)
        self.assertTrue(DEFAULT_SETTINGS["autoToolContext"])
        self.assertEqual(DEFAULT_SETTINGS["toolContextMaxChars"], 3500)
        self.assertEqual(DEFAULT_SETTINGS["toolContextEntryMaxChars"], 520)
        self.assertEqual(DEFAULT_SETTINGS["toolContextMaxItems"], 6)
        self.assertEqual(DEFAULT_SETTINGS["toolContextMinExactItems"], 2)
        self.assertNotIn("agentIdMode", config)
        self.assertNotIn("commitOnStop", config)

    def test_user_config_and_env_override_settings(self):
        with tempfile.TemporaryDirectory() as tmp:
            user_config = Path(tmp) / "codex.json"
            user_config.write_text(json.dumps({"agentId": "custom"}))
            env = {
                "MEMIND_API_URL": "http://example.test",
                "MEMIND_RETRIEVE_CONTEXT_TURNS": "2",
                "MEMIND_AUTO_SESSION_CONTEXT": "false",
                "MEMIND_SESSION_CONTEXT_RECENT_SESSIONS": "4",
                "MEMIND_SESSION_CONTEXT_MAX_ITEMS": "5",
                "MEMIND_SESSION_CONTEXT_MAX_CHARS": "3000",
            }
            config = load_config(
                plugin_root=Path(__file__).resolve().parents[1],
                user_config_path=user_config,
                env=env,
            )
        self.assertEqual(config["agentId"], "custom")
        self.assertEqual(config["memindApiUrl"], "http://example.test")
        self.assertEqual(config["retrieveContextTurns"], 2)
        self.assertFalse(config["autoSessionContext"])
        self.assertEqual(config["sessionContextRecentSessions"], 4)
        self.assertEqual(config["sessionContextMaxItems"], 5)
        self.assertEqual(config["sessionContextMaxChars"], 3000)
        self.assertNotIn("agentIdMode", config)
        self.assertNotIn("commitOnStop", config)
        self.assertNotIn("ingestionRoles", config)

    def test_parse_helpers(self):
        self.assertTrue(parse_bool("yes"))
        self.assertFalse(parse_bool("0"))
        self.assertEqual(parse_list(" user, assistant ,, "), ["user", "assistant"])

    def test_tool_context_env_overrides(self):
        config = load_config(
            plugin_root=Path(__file__).resolve().parents[1],
            user_config_path=Path("/no/such/file"),
            env={
                "CODEX_PLUGIN_ROOT": str(Path(__file__).resolve().parents[1]),
                "MEMIND_AUTO_TOOL_CONTEXT": "false",
                "MEMIND_TOOL_CONTEXT_MAX_CHARS": "2500",
                "MEMIND_TOOL_CONTEXT_ENTRY_MAX_CHARS": "400",
                "MEMIND_TOOL_CONTEXT_MAX_ITEMS": "4",
                "MEMIND_TOOL_CONTEXT_MIN_EXACT_ITEMS": "1",
            },
        )

        self.assertFalse(config["autoToolContext"])
        self.assertEqual(config["toolContextMaxChars"], 2500)
        self.assertEqual(config["toolContextEntryMaxChars"], 400)
        self.assertEqual(config["toolContextMaxItems"], 4)
        self.assertEqual(config["toolContextMinExactItems"], 1)

    def test_prompt_context_env_overrides(self):
        root = Path(__file__).resolve().parents[1]
        config = load_config(
            plugin_root=root,
            user_config_path=Path("/no/such/file"),
            env={
                "CODEX_PLUGIN_ROOT": str(root),
                "MEMIND_AUTO_PROMPT_CONTEXT": "true",
                "MEMIND_PROMPT_CONTEXT_PROJECT_MIN_ENTRIES": "2",
                "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_ENTRIES": "1",
                "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_MIN_SCORE": "0.5",
            },
        )

        self.assertTrue(config["autoPromptContext"])
        self.assertEqual(config["promptContextProjectMinEntries"], 2)
        self.assertEqual(config["promptContextGlobalFallbackEntries"], 1)
        self.assertEqual(config["promptContextGlobalFallbackMinScore"], 0.5)


if __name__ == "__main__":
    unittest.main()
