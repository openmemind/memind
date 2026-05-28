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
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.lib.config import DEFAULT_SETTINGS, load_config, parse_bool, parse_int, parse_list

ROOT = Path(__file__).resolve().parents[1]


class ConfigTest(unittest.TestCase):
    def test_parse_bool(self):
        self.assertTrue(parse_bool("true"))
        self.assertTrue(parse_bool("1"))
        self.assertTrue(parse_bool("YES"))
        self.assertFalse(parse_bool("false"))
        self.assertFalse(parse_bool("0"))
        self.assertFalse(parse_bool("no"))

    def test_parse_int_requires_positive(self):
        self.assertEqual(parse_int("14", "stateMaxAgeDays"), 14)
        with self.assertRaises(ValueError):
            parse_int("0", "stateMaxAgeDays")

    def test_parse_list(self):
        self.assertEqual(parse_list("user,assistant"), ["user", "assistant"])
        self.assertEqual(parse_list(" user , assistant ,,"), ["user", "assistant"])

    def test_defaults_match_spec(self):
        self.assertEqual(DEFAULT_SETTINGS["agentId"], "coding-agent")
        self.assertEqual(DEFAULT_SETTINGS["retrieveContextTurns"], 0)
        self.assertEqual(DEFAULT_SETTINGS["sourceClient"], "claude-code")
        self.assertFalse(DEFAULT_SETTINGS["autoPromptContext"])
        self.assertEqual(DEFAULT_SETTINGS["promptContextProjectMinEntries"], 4)
        self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackEntries"], 3)
        self.assertEqual(DEFAULT_SETTINGS["promptContextGlobalFallbackMinScore"], 0.65)
        self.assertTrue(DEFAULT_SETTINGS["autoSessionContext"])
        self.assertEqual(DEFAULT_SETTINGS["sessionContextRecentSessions"], 3)
        self.assertEqual(DEFAULT_SETTINGS["sessionContextMaxItems"], 6)
        self.assertEqual(DEFAULT_SETTINGS["sessionContextMaxChars"], 6000)
        self.assertTrue(DEFAULT_SETTINGS["autoToolContext"])
        self.assertEqual(DEFAULT_SETTINGS["toolContextMaxChars"], 3500)
        self.assertEqual(DEFAULT_SETTINGS["toolContextEntryMaxChars"], 520)
        self.assertEqual(DEFAULT_SETTINGS["toolContextMaxItems"], 6)
        self.assertEqual(DEFAULT_SETTINGS["toolContextMinExactItems"], 2)
        self.assertTrue(DEFAULT_SETTINGS["autoIngestAgentTimeline"])
        self.assertNotIn("agentIdMode", DEFAULT_SETTINGS)
        self.assertNotIn("autoIngest", DEFAULT_SETTINGS)
        self.assertNotIn("ingestionRoles", DEFAULT_SETTINGS)
        self.assertNotIn("ingestionMaxMessagesPerHook", DEFAULT_SETTINGS)
        self.assertEqual(DEFAULT_SETTINGS["stateMaxAgeDays"], 14)

    def test_environment_overrides(self):
        with tempfile.TemporaryDirectory() as tmp:
            plugin_root = Path(tmp)
            (plugin_root / "settings.json").write_text(json.dumps({"retrieveMaxEntries": 3}))
            env = {
                "MEMIND_API_URL": "http://memind.example",
                "MEMIND_AUTO_RETRIEVE": "false",
                "MEMIND_AUTO_SESSION_CONTEXT": "false",
                "MEMIND_SESSION_CONTEXT_RECENT_SESSIONS": "4",
                "MEMIND_SESSION_CONTEXT_MAX_ITEMS": "5",
                "MEMIND_SESSION_CONTEXT_MAX_CHARS": "3000",
                "MEMIND_RETRIEVE_MAX_ENTRIES": "9",
                "MEMIND_RETRIEVE_MAX_CHARS": "7000",
                "MEMIND_AUTO_INGEST_AGENT_TIMELINE": "false",
                "MEMIND_STATE_MAX_AGE_DAYS": "30",
            }
            with patch.dict(os.environ, env, clear=False):
                config = load_config(plugin_root=plugin_root, user_config_path=plugin_root / "missing.json")
            self.assertEqual(config["memindApiUrl"], "http://memind.example")
            self.assertFalse(config["autoRetrieve"])
            self.assertFalse(config["autoSessionContext"])
            self.assertEqual(config["sessionContextRecentSessions"], 4)
            self.assertEqual(config["sessionContextMaxItems"], 5)
            self.assertEqual(config["sessionContextMaxChars"], 3000)
            self.assertEqual(config["retrieveMaxEntries"], 9)
            self.assertEqual(config["retrieveMaxChars"], 7000)
            self.assertFalse(config["autoIngestAgentTimeline"])
            self.assertNotIn("agentIdMode", config)
            self.assertNotIn("ingestionRoles", config)
            self.assertEqual(config["stateMaxAgeDays"], 30)

    def test_tool_context_env_overrides(self):
        config = load_config(
            plugin_root=ROOT,
            user_config_path=Path("/no/such/file"),
            env={
                "CLAUDE_PLUGIN_ROOT": str(ROOT),
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
        config = load_config(
            plugin_root=ROOT,
            user_config_path=Path("/no/such/file"),
            env={
                "CLAUDE_PLUGIN_ROOT": str(ROOT),
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
