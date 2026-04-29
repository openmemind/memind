import tempfile
import unittest
from pathlib import Path

from scripts.lib.identity import project_slug, resolve_identity


class IdentityTest(unittest.TestCase):
    def test_resolve_identity_uses_codex_project_agent(self):
        with tempfile.TemporaryDirectory() as tmp:
            identity = resolve_identity({"agentId": "codex", "agentIdMode": "project", "userId": "u"}, {"cwd": tmp})
        self.assertEqual(identity["userId"], "u")
        self.assertTrue(identity["agentId"].startswith("codex__"))

    def test_project_slug_falls_back_to_path_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            slug = project_slug(Path(tmp))
        self.assertIn("-", slug)
        self.assertLessEqual(len(slug.rsplit("-", 1)[1]), 12)


if __name__ == "__main__":
    unittest.main()
