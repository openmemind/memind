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
        self.assertEqual(DEFAULT_SETTINGS["retrieveContextTurns"], 0)
        self.assertEqual(DEFAULT_SETTINGS["ingestionMode"], "add-message")
        self.assertEqual(DEFAULT_SETTINGS["ingestionMaxMessagesPerHook"], 20)
        self.assertEqual(DEFAULT_SETTINGS["stateMaxAgeDays"], 14)

    def test_environment_overrides(self):
        with tempfile.TemporaryDirectory() as tmp:
            plugin_root = Path(tmp)
            (plugin_root / "settings.json").write_text(json.dumps({"retrieveMaxEntries": 3}))
            env = {
                "MEMIND_API_URL": "http://memind.example",
                "MEMIND_AUTO_RETRIEVE": "false",
                "MEMIND_INGESTION_ROLES": "user,assistant",
                "MEMIND_STATE_MAX_AGE_DAYS": "30",
            }
            with patch.dict(os.environ, env, clear=False):
                config = load_config(plugin_root=plugin_root, user_config_path=plugin_root / "missing.json")
            self.assertEqual(config["memindApiUrl"], "http://memind.example")
            self.assertFalse(config["autoRetrieve"])
            self.assertEqual(config["ingestionRoles"], ["user", "assistant"])
            self.assertEqual(config["stateMaxAgeDays"], 30)
            self.assertEqual(config["retrieveMaxEntries"], 3)


if __name__ == "__main__":
    unittest.main()
