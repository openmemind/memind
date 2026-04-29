import json
import tempfile
import unittest
from pathlib import Path

from scripts.lib.config import load_config, parse_bool, parse_list


class ConfigTest(unittest.TestCase):
    def test_defaults_are_codex_specific(self):
        config = load_config(plugin_root=Path(__file__).resolve().parents[1], user_config_path="/missing", env={})
        self.assertEqual(config["agentId"], "codex")
        self.assertEqual(config["sourceClient"], "codex")
        self.assertFalse(config["commitOnStop"])
        self.assertEqual(config["retrieveContextTurns"], 0)

    def test_user_config_and_env_override_settings(self):
        with tempfile.TemporaryDirectory() as tmp:
            user_config = Path(tmp) / "codex.json"
            user_config.write_text(json.dumps({"agentId": "custom", "commitOnStop": True}))
            env = {
                "MEMIND_API_URL": "http://example.test",
                "MEMIND_COMMIT_ON_STOP": "false",
                "MEMIND_RETRIEVE_CONTEXT_TURNS": "2",
                "MEMIND_INGESTION_ROLES": "user,assistant",
            }
            config = load_config(
                plugin_root=Path(__file__).resolve().parents[1],
                user_config_path=user_config,
                env=env,
            )
        self.assertEqual(config["agentId"], "custom")
        self.assertEqual(config["memindApiUrl"], "http://example.test")
        self.assertFalse(config["commitOnStop"])
        self.assertEqual(config["retrieveContextTurns"], 2)
        self.assertEqual(config["ingestionRoles"], ["user", "assistant"])

    def test_parse_helpers(self):
        self.assertTrue(parse_bool("yes"))
        self.assertFalse(parse_bool("0"))
        self.assertEqual(parse_list(" user, assistant ,, "), ["user", "assistant"])


if __name__ == "__main__":
    unittest.main()
