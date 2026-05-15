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

import importlib.util
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ManifestTest(unittest.TestCase):
    def test_plugin_manifest_declares_memind_provider(self):
        manifest = (ROOT / "plugin.yaml").read_text()

        self.assertIn("name: memind", manifest)
        self.assertIn("Persistent long-term memory for Hermes Agent via Memind", manifest)
        self.assertIn("on_pre_compress", manifest)
        self.assertIn("on_memory_write", manifest)

    def test_settings_defaults_match_spec(self):
        settings = json.loads((ROOT / "settings.json").read_text())

        self.assertEqual(settings["memindApiUrl"], "http://127.0.0.1:8366")
        self.assertIsNone(settings["memindApiToken"])
        self.assertIsNone(settings["userId"])
        self.assertEqual(settings["agentId"], "hermes")
        self.assertEqual(settings["agentIdMode"], "project")
        self.assertEqual(settings["sourceClient"], "hermes")
        self.assertEqual(settings["memoryMode"], "hybrid")
        self.assertTrue(settings["autoRetrieve"])
        self.assertTrue(settings["autoIngest"])
        self.assertEqual(settings["retrieveStrategy"], "SIMPLE")
        self.assertEqual(settings["retrieveMaxEntries"], 8)
        self.assertEqual(settings["retrieveMaxChars"], 6000)
        self.assertEqual(settings["retrieveContextTurns"], 1)
        self.assertEqual(settings["ingestionRoles"], ["user", "assistant"])
        self.assertFalse(settings["debug"])

    def test_plugin_root_imports_when_loaded_as_file(self):
        spec = importlib.util.spec_from_file_location("memind_hermes_plugin", ROOT / "__init__.py")
        module = importlib.util.module_from_spec(spec)
        original_path = list(sys.path)

        try:
            sys.path = [
                entry
                for entry in sys.path
                if Path(entry or ".").resolve() != ROOT
            ]
            spec.loader.exec_module(module)
        finally:
            sys.path = original_path

        self.assertTrue(hasattr(module, "register"))

    def test_readme_documents_required_operational_topics(self):
        readme = (ROOT / "README.md").read_text()

        self.assertIn("Hermes Agent", readme)
        self.assertIn("memory.provider", readme)
        self.assertIn("~/.memind/hermes.json", readme)
        self.assertIn("memoryMode", readme)
        self.assertIn("memind_retrieve", readme)
        self.assertIn("memind_extract_text", readme)
        self.assertIn("HTTP MCP", readme)
        self.assertIn("sourceClient", readme)
        self.assertIn("HTTPS", readme)
        self.assertIn("loopback", readme)
        self.assertIn("network controls", readme)


if __name__ == "__main__":
    unittest.main()
