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

from memind_hermes.identity import project_slug, resolve_identity


class IdentityTest(unittest.TestCase):
    def test_project_mode_appends_stable_project_slug(self):
        with tempfile.TemporaryDirectory() as tmp:
            identity = resolve_identity(
                {"agentId": "hermes", "agentIdMode": "project", "userId": None, "sourceClient": "hermes"},
                cwd=tmp,
                session_id="s1",
            )

        self.assertTrue(identity["userId"].startswith("local__"))
        self.assertTrue(identity["agentId"].startswith("hermes__"))
        self.assertEqual(identity["sourceClient"], "hermes")

    def test_fixed_mode_uses_agent_id_as_is(self):
        identity = resolve_identity(
            {"agentId": "shared", "agentIdMode": "fixed", "userId": "u", "sourceClient": "hermes"},
            cwd="/tmp/project",
            session_id="s1",
        )

        self.assertEqual(identity["userId"], "u")
        self.assertEqual(identity["agentId"], "shared")

    def test_session_mode_uses_session_id(self):
        identity = resolve_identity(
            {"agentId": "hermes", "agentIdMode": "session", "userId": "u", "sourceClient": "hermes"},
            cwd="/tmp/project",
            session_id="abc/def",
        )

        self.assertEqual(identity["agentId"], "hermes__session-abc_def")

    def test_project_slug_is_stable(self):
        with tempfile.TemporaryDirectory() as tmp:
            first = project_slug(Path(tmp))
            second = project_slug(Path(tmp))

        self.assertEqual(first, second)
        self.assertIn("-", first)


if __name__ == "__main__":
    unittest.main()
