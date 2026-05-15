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

from memind_hermes.config import (
    DEFAULT_SETTINGS,
    config_schema,
    load_config,
    parse_bool,
    parse_int,
    parse_list,
    save_config,
)


class ConfigTest(unittest.TestCase):
    def test_parse_bool(self):
        self.assertTrue(parse_bool("true"))
        self.assertTrue(parse_bool("1"))
        self.assertTrue(parse_bool("YES"))
        self.assertFalse(parse_bool("false"))
        self.assertFalse(parse_bool("0"))
        self.assertFalse(parse_bool("no"))

    def test_parse_int(self):
        self.assertEqual(parse_int("8", "retrieveMaxEntries"), 8)
        with self.assertRaises(ValueError):
            parse_int("0", "retrieveMaxEntries")

    def test_parse_list(self):
        self.assertEqual(parse_list("user, assistant,,"), ["user", "assistant"])

    def test_defaults_match_settings(self):
        self.assertEqual(DEFAULT_SETTINGS["sourceClient"], "hermes")
        self.assertEqual(DEFAULT_SETTINGS["memoryMode"], "hybrid")
        self.assertEqual(DEFAULT_SETTINGS["retrieveStrategy"], "SIMPLE")

    def test_load_order_settings_user_env(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            user_config = root / "hermes.json"
            (root / "settings.json").write_text(json.dumps({"retrieveMaxEntries": 3}))
            user_config.write_text(json.dumps({"retrieveMaxEntries": 5, "memoryMode": "context"}))
            env = {
                "MEMIND_RETRIEVE_MAX_ENTRIES": "7",
                "MEMIND_MEMORY_MODE": "tools",
                "MEMIND_AUTO_RETRIEVE": "false",
                "MEMIND_INGESTION_ROLES": "user,assistant",
            }

            cfg = load_config(plugin_root=root, user_config_path=user_config, env=env)

        self.assertEqual(cfg["retrieveMaxEntries"], 7)
        self.assertEqual(cfg["memoryMode"], "tools")
        self.assertFalse(cfg["autoRetrieve"])
        self.assertEqual(cfg["ingestionRoles"], ["user", "assistant"])

    def test_invalid_strategy_falls_back_to_simple(self):
        cfg = load_config(env={"MEMIND_RETRIEVE_STRATEGY": "bad"})

        self.assertEqual(cfg["retrieveStrategy"], "SIMPLE")

    def test_invalid_memory_mode_falls_back_to_hybrid(self):
        cfg = load_config(env={"MEMIND_MEMORY_MODE": "bad"})

        self.assertEqual(cfg["memoryMode"], "hybrid")

    def test_save_config_writes_memind_user_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            save_config(
                {"memindApiUrl": "http://memind.test", "memindApiToken": "secret", "userId": "u"},
                home=home,
            )
            path = home / ".memind" / "hermes.json"
            saved = json.loads(path.read_text())

        self.assertEqual(saved["memindApiUrl"], "http://memind.test")
        self.assertEqual(saved["memindApiToken"], "secret")
        self.assertEqual(saved["userId"], "u")

    def test_config_schema_has_core_fields(self):
        schema = config_schema()
        keys = {item["key"] for item in schema}

        self.assertIn("memindApiUrl", keys)
        self.assertIn("memindApiToken", keys)
        self.assertIn("userId", keys)
        self.assertIn("agentId", keys)
        self.assertIn("memoryMode", keys)


if __name__ == "__main__":
    unittest.main()
