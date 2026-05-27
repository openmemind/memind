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

import tempfile
import unittest
from pathlib import Path

from scripts.lib.identity import project_slug, resolve_identity


class IdentityTest(unittest.TestCase):
    def test_resolve_identity_uses_fixed_agent_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            identity = resolve_identity({"agentId": "coding-agent", "userId": "u"}, {"cwd": tmp})
        self.assertEqual(identity["userId"], "u")
        self.assertEqual(identity["agentId"], "coding-agent")

    def test_resolve_identity_defaults_to_shared_coding_agent(self):
        with tempfile.TemporaryDirectory() as tmp:
            identity = resolve_identity({"userId": "u"}, {"cwd": tmp})
        self.assertEqual(identity["agentId"], "coding-agent")

    def test_project_slug_falls_back_to_path_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            slug = project_slug(Path(tmp))
        self.assertIn("-", slug)
        self.assertLessEqual(len(slug.rsplit("-", 1)[1]), 12)


if __name__ == "__main__":
    unittest.main()
